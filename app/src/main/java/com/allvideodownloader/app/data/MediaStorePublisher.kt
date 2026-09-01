package com.allvideodownloader.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.allvideodownloader.app.util.FileNames
import java.io.File
import java.io.IOException

/**
 * Publishes a finished download into the shared video collection at
 * `Movies/AllVideoDownloader`.
 *
 * Going through MediaStore is what lets the app write to public storage on Android 10+ with
 * **no storage permission at all**, keeps the file visible to Gallery and other players, and
 * leaves it on the device after an uninstall.
 */
class MediaStorePublisher(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    @Throws(IOException::class)
    fun publish(source: File, preferredName: String, mimeType: String): Uri {
        if (!source.isFile) throw IOException("Downloaded file is missing")

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val displayName = uniqueDisplayName(preferredName)

        // DATE_ADDED / DATE_MODIFIED are deliberately absent: MediaProvider owns them and
        // rejects or silently overwrites values supplied by the caller.
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore refused to create the entry")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER) }
            } ?: throw IOException("Could not open the destination file")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null
            )
            return uri
        } catch (error: Throwable) {
            // Never leave a half-written pending row behind — it would be invisible but real.
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    /**
     * MediaStore would otherwise silently rename collisions; deciding the final name here keeps
     * the Library list predictable and matches what the user sees in the notification.
     */
    private fun uniqueDisplayName(preferredName: String): String {
        val safeName = FileNames.sanitize(preferredName).ifBlank { "video.mp4" }
        val existing = existingNames()
        if (safeName !in existing) return safeName

        val stem = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"

        var counter = 1
        while (counter < 1000) {
            val candidate = "$stem ($counter)$suffix"
            if (candidate !in existing) return candidate
            counter++
        }
        return "$stem ${System.currentTimeMillis()}$suffix"
    }

    private fun existingNames(): Set<String> {
        val names = mutableSetOf<String>()
        runCatching {
            resolver.query(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("$RELATIVE_PATH%"),
                null
            )?.use { cursor ->
                val column = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) cursor.getString(column)?.let(names::add)
            }
        }
        return names
    }

    companion object {
        const val FOLDER_NAME = "AllVideoDownloader"

        /** MediaStore stores RELATIVE_PATH with a trailing separator. */
        const val RELATIVE_PATH = "Movies/$FOLDER_NAME/"

        private const val DEFAULT_BUFFER = 128 * 1024
    }
}
