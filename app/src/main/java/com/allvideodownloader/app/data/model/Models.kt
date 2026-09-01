package com.allvideodownloader.app.data.model

import android.net.Uri

enum class DownloadState { PENDING, RUNNING, PAUSED, SUCCESS, FAILED }

/** A download currently tracked by the system DownloadManager. */
data class ActiveDownload(
    val id: Long,
    val fileName: String,
    val state: DownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val statusDetail: String? = null
) {
    val hasKnownSize: Boolean get() = totalBytes > 0

    val progress: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** A finished video published to Movies/AllVideoDownloader and visible to other apps. */
data class LibraryVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateAddedSeconds: Long,
    val mimeType: String
)
