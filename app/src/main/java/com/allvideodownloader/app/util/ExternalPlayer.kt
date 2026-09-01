package com.allvideodownloader.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.allvideodownloader.app.R

/**
 * Hands playback to whichever player the user prefers.
 *
 * The app intentionally ships no bundled player: `ACTION_VIEW` inside a chooser means the user
 * picks their own app every time, and the temporary URI grant means no storage permission is
 * needed on the receiving side. This also keeps the app free of media-codec licensing concerns.
 */
object ExternalPlayer {

    /** @return true when a player accepted the intent. */
    fun play(context: Context, uri: Uri, mimeType: String): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(view, context.getString(R.string.open_with)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (notFound: ActivityNotFoundException) {
            false
        }
    }

    fun share(context: Context, uri: Uri, mimeType: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "video/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_video)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (notFound: ActivityNotFoundException) {
            false
        }
    }
}
