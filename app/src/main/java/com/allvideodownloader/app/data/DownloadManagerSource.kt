package com.allvideodownloader.app.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.allvideodownloader.app.R
import com.allvideodownloader.app.data.model.ActiveDownload
import com.allvideodownloader.app.data.model.DownloadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Thin wrapper over the platform [DownloadManager].
 *
 * Why the system downloader: it survives our process being killed, resumes across network
 * changes, shows its own progress notification (so the app needs no notification permission)
 * and requires no foreground service.
 *
 * Why files land in the app's own external directory first: on Android 10+ DownloadManager
 * rejects `setDestinationInExternalPublicDir(DIRECTORY_MOVIES, …)` with "Unsupported path".
 * The only permission-free destinations are the app's external dirs and public Downloads.
 * So the transfer is staged here and [MediaStorePublisher] moves it into
 * Movies/AllVideoDownloader once complete.
 */
class DownloadManagerSource(context: Context) {

    private val appContext = context.applicationContext
    private val downloadManager =
        checkNotNull(appContext.getSystemService(DownloadManager::class.java)) {
            "DownloadManager is unavailable on this device"
        }
    private val store = ActiveDownloadStore(appContext)

    data class CompletedDownload(val id: Long, val file: File, val mimeType: String?)

    fun stagingDir(): File? {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
        val dir = File(base, "incoming")
        return if (dir.isDirectory || dir.mkdirs()) dir else null
    }

    @Throws(IOException::class)
    fun enqueue(url: String, fileName: String, mimeType: String): Long {
        val dir = stagingDir() ?: throw IOException("External storage is unavailable")
        purgeStaleStaging(dir)

        // Each download gets its own staging folder so the file can keep its real name without
        // two concurrent downloads of the same URL clobbering each other.
        val slot = File(dir, UUID.randomUUID().toString().take(12))
        if (!slot.mkdirs()) throw IOException("Could not prepare the download folder")
        val target = File(slot, fileName)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription(appContext.getString(R.string.app_name))
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(true)
            .setDestinationUri(Uri.fromFile(target))

        val id = downloadManager.enqueue(request)
        store.add(id)
        return id
    }

    /** Staging folders left behind by a crash or a force-stop. */
    private fun purgeStaleStaging(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_STAGING_MS
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && child.lastModified() < cutoff) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    /** Current state of every download this app started. */
    fun snapshot(): List<ActiveDownload> {
        val ids = store.ids()
        if (ids.isEmpty()) return emptyList()

        val downloads = mutableListOf<ActiveDownload>()
        runCatching {
            downloadManager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))
                ?.use { cursor ->
                    while (cursor.moveToNext()) downloads += cursor.toActiveDownload()
                }
        }

        // Rows the user cleared from the system Downloads UI will never come back — forget them.
        val seen = downloads.mapTo(mutableSetOf()) { it.id }
        ids.filterNot { it in seen }.forEach(store::remove)

        return downloads.sortedByDescending { it.id }
    }

    /**
     * Polls [snapshot] and emits only when something actually changed. DownloadManager has no
     * progress callback, so polling is the only option; the interval backs off while idle.
     */
    fun observe(): Flow<List<ActiveDownload>> = flow {
        while (true) {
            val downloads = snapshot()
            emit(downloads)
            delay(if (downloads.isEmpty()) IDLE_POLL_MS else ACTIVE_POLL_MS)
        }
    }.distinctUntilChanged()

    /**
     * IDs we still track whose transfer has already finished, so they are waiting to be
     * published. A completion broadcast is lost if the app is force-stopped, so the app
     * re-checks this on every start rather than trusting the broadcast alone.
     */
    fun finishedIds(): List<Long> {
        val ids = store.ids()
        if (ids.isEmpty()) return emptyList()

        val finished = mutableListOf<Long>()
        runCatching {
            downloadManager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                    val statusColumn =
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(statusColumn) == DownloadManager.STATUS_SUCCESSFUL) {
                            finished += cursor.getLong(idColumn)
                        }
                    }
                }
        }
        return finished
    }

    fun completedDownload(id: Long): CompletedDownload? {        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToNext()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return null

            val localUri = cursor
                .getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                ?: return null
            val file = Uri.parse(localUri).path?.let(::File) ?: return null
            if (!file.isFile) return null

            val mediaType = cursor
                .getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))

            return CompletedDownload(id, file, mediaType)
        }
        return null
    }

    /** Cancels an in-flight download and deletes the partial file. */
    fun cancel(id: Long) {
        runCatching { downloadManager.remove(id) }
        store.remove(id)
    }

    /** Removes the DownloadManager row (and its staged file) after a successful publish. */
    fun clear(id: Long) {
        runCatching { downloadManager.remove(id) }
        store.remove(id)
    }

    fun forget(id: Long) = store.remove(id)

    private fun Cursor.toActiveDownload(): ActiveDownload {
        val id = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val title = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty()
        val status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val reason = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        val soFar = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

        return ActiveDownload(
            id = id,
            fileName = title.ifBlank { "video" },
            state = when (status) {
                DownloadManager.STATUS_PENDING -> DownloadState.PENDING
                DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.SUCCESS
                else -> DownloadState.FAILED
            },
            bytesDownloaded = soFar.coerceAtLeast(0L),
            totalBytes = total,
            statusDetail = detailFor(status, reason)
        )
    }

    private fun detailFor(status: Int, reason: Int): String? = when (status) {
        DownloadManager.STATUS_PAUSED -> when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Queued for Wi-Fi"
            else -> "Paused"
        }

        DownloadManager.STATUS_FAILED -> when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume this download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server refused the request"
            in 400..599 -> "Server returned HTTP $reason"
            else -> "Download failed"
        }

        DownloadManager.STATUS_PENDING -> "Queued"
        else -> null
    }

    private companion object {
        const val ACTIVE_POLL_MS = 700L
        const val IDLE_POLL_MS = 2_500L
        const val STALE_STAGING_MS = 7L * 24 * 60 * 60 * 1000
    }
}
