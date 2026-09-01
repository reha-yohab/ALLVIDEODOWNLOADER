package com.allvideodownloader.app.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.allvideodownloader.app.data.model.LibraryVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Reads the app's download folder back out of MediaStore.
 *
 * Items this app created are always readable without a permission. READ_MEDIA_VIDEO is only
 * needed to see files left behind by a previous install, which is why the Library screen
 * treats it as optional and degrades instead of nagging.
 */
class VideoLibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** Android 11+ requires user confirmation for files this install does not own. */
        data class NeedsConsent(val intentSender: IntentSender) : DeleteResult
        data class Failed(val message: String) : DeleteResult
    }

    suspend fun load(): List<LibraryVideo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )

        val videos = mutableListOf<LibraryVideo>()
        runCatching {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("${MediaStorePublisher.RELATIVE_PATH}%"),
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    videos += LibraryVideo(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        ),
                        displayName = cursor.getString(nameColumn) ?: "video",
                        sizeBytes = cursor.getLong(sizeColumn),
                        durationMs = cursor.getLong(durationColumn),
                        dateAddedSeconds = cursor.getLong(dateColumn),
                        mimeType = cursor.getString(mimeColumn) ?: "video/*"
                    )
                }
            }
        }
        videos
    }

    suspend fun delete(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val removed = resolver.delete(uri, null, null)
            if (removed > 0) DeleteResult.Deleted
            else DeleteResult.Failed("The file could not be found")
        } catch (security: SecurityException) {
            val sender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    runCatching {
                        MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
                    }.getOrNull()

                security is RecoverableSecurityException ->
                    security.userAction.actionIntent.intentSender

                else -> null
            }
            if (sender != null) {
                DeleteResult.NeedsConsent(sender)
            } else {
                DeleteResult.Failed(security.message ?: "Permission denied")
            }
        }
    }

    /** Emits whenever the video collection changes, so the Library refreshes itself. */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}
