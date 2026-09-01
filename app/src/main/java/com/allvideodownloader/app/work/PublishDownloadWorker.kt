package com.allvideodownloader.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.allvideodownloader.app.R
import com.allvideodownloader.app.data.DownloadManagerSource
import com.allvideodownloader.app.data.MediaStorePublisher
import com.allvideodownloader.app.util.AppEvents
import com.allvideodownloader.app.util.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runs after DownloadManager reports success: copies the staged file into
 * Movies/AllVideoDownloader through MediaStore, then drops the staging copy.
 *
 * This is a Worker rather than inline receiver code because copying a large file takes longer
 * than a broadcast is allowed to live, and WorkManager will retry if the process dies midway.
 */
class PublishDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return@withContext Result.failure()

        val downloads = DownloadManagerSource(applicationContext)
        val completed = downloads.completedDownload(downloadId)

        if (completed == null) {
            // Either it failed, or the row/file vanished. Nothing to publish.
            downloads.forget(downloadId)
            return@withContext Result.success()
        }

        val fileName = FileNames.buildFileName(
            serverSuggestion = null,
            urlHint = completed.file.name,
            contentType = completed.mimeType
        )
        val mimeType = FileNames.mimeTypeFor(fileName)

        try {
            MediaStorePublisher(applicationContext).publish(completed.file, fileName, mimeType)
            // remove() deletes the DownloadManager row together with the staged file; the
            // now-empty staging folder goes with it.
            downloads.clear(downloadId)
            runCatching { completed.file.parentFile?.delete() }
            AppEvents.post(
                applicationContext.getString(
                    R.string.saved_to_folder,
                    fileName,
                    MediaStorePublisher.FOLDER_NAME
                )
            )
            Result.success()
        } catch (error: Throwable) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                downloads.forget(downloadId)
                AppEvents.post(
                    applicationContext.getString(
                        R.string.error_save_failed,
                        error.message ?: "unknown error"
                    )
                )
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val MAX_ATTEMPTS = 3

        fun uniqueName(downloadId: Long) = "publish-download-$downloadId"

        /**
         * Unique work keyed on the download ID, so the completion broadcast and the
         * start-up reconciliation pass can both ask for the same publish without
         * duplicating it.
         */
        fun enqueue(context: Context, downloadId: Long) {
            val request = OneTimeWorkRequestBuilder<PublishDownloadWorker>()
                .setInputData(workDataOf(KEY_DOWNLOAD_ID to downloadId))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueName(downloadId),
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
