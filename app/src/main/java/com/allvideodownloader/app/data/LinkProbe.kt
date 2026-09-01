package com.allvideodownloader.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Sends a cheap HEAD request before enqueueing a download so the UI can show the real
 * file name, size and content type — and can refuse links that are clearly not videos.
 *
 * Deliberately built on [HttpURLConnection] to keep the app free of networking dependencies.
 */
object LinkProbe {

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    data class Info(
        val finalUrl: String,
        val contentType: String?,
        val contentLength: Long,
        val suggestedFileName: String?
    ) {
        val isVideoContentType: Boolean
            get() = contentType?.startsWith("video/", ignoreCase = true) == true ||
                contentType.equals("application/mp4", ignoreCase = true) ||
                contentType.equals("application/x-matroska", ignoreCase = true) ||
                contentType.equals("application/octet-stream", ignoreCase = true)
    }

    suspend fun probe(url: String): Result<Info> = withContext(Dispatchers.IO) {
        runCatching { probeBlocking(url) }
    }

    private fun probeBlocking(startUrl: String): Info {
        var current = startUrl
        repeat(MAX_REDIRECTS) {
            val connection = open(current, "HEAD")
            try {
                val code = connection.responseCode

                // Some CDNs reject HEAD outright; fall back to a one-byte ranged GET.
                if (code == 405 || code == 501 || code == 403 || code == 400) {
                    return rangedGet(current)
                }

                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect with no Location header")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }

                if (code !in 200..299) throw IOException("Server returned HTTP $code")

                return Info(
                    finalUrl = current,
                    contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase(),
                    contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: -1L,
                    suggestedFileName = fileNameFrom(connection.getHeaderField("Content-Disposition"))
                )
            } finally {
                runCatching { connection.disconnect() }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun rangedGet(url: String): Info {
        val connection = open(url, "GET").apply {
            instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=0-0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Server returned HTTP $code")

            // "bytes 0-0/123456" -> 123456
            val total = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')
                ?.trim()
                ?.toLongOrNull()
                ?: -1L

            return Info(
                finalUrl = connection.url?.toString() ?: url,
                contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase(),
                contentLength = total,
                suggestedFileName = fileNameFrom(connection.getHeaderField("Content-Disposition"))
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
        }

    /** Extracts a file name from `Content-Disposition`, handling both plain and RFC 5987 forms. */
    private fun fileNameFrom(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val extended = Regex("filename\\*\\s*=\\s*[^']*''([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
        val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
        val raw = (extended ?: plain)?.trim()?.trim('"') ?: return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return Uri.parse("file:///$decoded").lastPathSegment?.takeIf { it.isNotBlank() }
    }
}
