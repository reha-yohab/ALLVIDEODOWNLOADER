package com.allvideodownloader.app.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allvideodownloader.app.data.ActiveDownloadStore
import com.allvideodownloader.app.work.PublishDownloadWorker

/**
 * DownloadManager broadcasts completion to our package. We only act on IDs this app enqueued,
 * so downloads started by other apps (or by the browser) are ignored.
 *
 * This broadcast is best-effort: it never arrives if the app was force-stopped mid-download,
 * which is why `DownloadViewModel` also reconciles unpublished downloads at start-up.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return
        if (!ActiveDownloadStore(context).contains(downloadId)) return

        PublishDownloadWorker.enqueue(context, downloadId)
    }
}
