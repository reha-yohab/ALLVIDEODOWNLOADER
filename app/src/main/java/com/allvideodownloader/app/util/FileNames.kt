package com.allvideodownloader.app.util

import android.webkit.MimeTypeMap
import com.allvideodownloader.app.data.LinkValidator
import java.util.Locale

/** Turns whatever the server or URL gives us into a safe, playable file name. */
object FileNames {

    private const val MAX_LENGTH = 120
    private val ILLEGAL = Regex("""[\\/:*?"<>|]""")

    fun sanitize(raw: String): String {
        val cleaned = raw
            .filter { it.code >= 0x20 }
            .replace(ILLEGAL, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return if (cleaned.length > MAX_LENGTH) cleaned.takeLast(MAX_LENGTH) else cleaned
    }

    /**
     * Picks the best available name, in order: `Content-Disposition`, then the URL's last path
     * segment, then a timestamped fallback. Always ends with a video extension so that both
     * MediaStore and external players can identify the file.
     */
    fun buildFileName(
        serverSuggestion: String?,
        urlHint: String?,
        contentType: String?
    ): String {
        val base = listOfNotNull(serverSuggestion, urlHint)
            .map(::sanitize)
            .firstOrNull { it.isNotBlank() }
            ?: "video_${System.currentTimeMillis()}"

        val extension = base.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in LinkValidator.VIDEO_EXTENSIONS) return base

        val fromType = contentType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in LinkValidator.VIDEO_EXTENSIONS }

        val nameWithoutExt = if (extension.isNotEmpty() && extension.length <= 5) {
            base.substringBeforeLast('.')
        } else {
            base
        }
        return "$nameWithoutExt.${fromType ?: "mp4"}"
    }

    /** MediaStore rejects an entry whose MIME type disagrees with its file extension. */
    fun mimeTypeFor(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return when {
            fromMap != null && fromMap.startsWith("video/") -> fromMap
            extension == "mkv" -> "video/x-matroska"
            extension == "ts" -> "video/mp2t"
            extension == "flv" -> "video/x-flv"
            extension == "divx" -> "video/avi"
            else -> "video/mp4"
        }
    }
}
