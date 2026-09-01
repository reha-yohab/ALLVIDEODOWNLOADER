package com.allvideodownloader.app.data

import android.net.Uri

/**
 * First line of defence for both correctness and Google Play compliance.
 *
 * The app only accepts direct links to video files. It refuses hosts whose terms of service
 * forbid third-party downloading, which is what keeps the app inside Play's
 * "Misrepresentation / Intellectual property" and "Device and Network Abuse" policies.
 */
object LinkValidator {

    /** Extensions we are confident DownloadManager can fetch as a single playable file. */
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp", "3g2",
        "flv", "ts", "mpg", "mpeg", "mpe", "ogv", "wmv", "asf", "divx"
    )

    /** Adaptive-streaming manifests. A plain HTTP download of these yields an unplayable text file. */
    private val PLAYLIST_EXTENSIONS = setOf("m3u8", "m3u", "mpd", "f4m", "ism", "ismc")

    /**
     * Hosts this app refuses to download from.
     *
     * These services prohibit third-party downloading in their terms of service. Google Play
     * rejects (and removes) apps that facilitate it — YouTube in particular is called out
     * explicitly in the Play "Misrepresentation" and YouTube API policies.
     *
     * Editing this list is easy, but removing entries puts the app at real risk of
     * suspension. Add hosts here, do not take them away.
     */
    val BLOCKED_HOSTS = listOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com", "ytimg.com", "googlevideo.com",
        "facebook.com", "fb.watch", "fbcdn.net", "messenger.com",
        "instagram.com", "cdninstagram.com",
        "tiktok.com", "tiktokcdn.com", "tiktokv.com",
        "twitter.com", "x.com", "twimg.com",
        "netflix.com", "nflxvideo.net",
        "primevideo.com", "amazonvideo.com",
        "hulu.com", "disneyplus.com", "hbomax.com", "max.com",
        "spotify.com", "soundcloud.com",
        "vimeo.com", "dailymotion.com", "twitch.tv",
        "hotstar.com", "zee5.com", "sonyliv.com", "jiocinema.com"
    )

    enum class RejectReason { EMPTY, MALFORMED, UNSUPPORTED_SCHEME, BLOCKED_HOST, STREAM_PLAYLIST }

    sealed interface Result {
        /**
         * @param url the trimmed link to hand to DownloadManager
         * @param fileNameHint last path segment, when it looks like a file name
         * @param extension lower-case extension taken from the path, or null when absent
         */
        data class Accepted(
            val url: String,
            val fileNameHint: String?,
            val extension: String?
        ) : Result

        data class Rejected(val reason: RejectReason, val detail: String? = null) : Result
    }

    fun validate(rawInput: String): Result {
        val input = rawInput.trim()
        if (input.isEmpty()) return Result.Rejected(RejectReason.EMPTY)

        val uri = runCatching { Uri.parse(input) }.getOrNull()
            ?: return Result.Rejected(RejectReason.MALFORMED)

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.Rejected(
                if (scheme.isNullOrBlank()) RejectReason.MALFORMED else RejectReason.UNSUPPORTED_SCHEME
            )
        }

        val host = uri.host?.lowercase()?.removePrefix("www.")
        if (host.isNullOrBlank() || !host.contains('.')) {
            return Result.Rejected(RejectReason.MALFORMED)
        }

        blockedHostFor(host)?.let { blocked ->
            return Result.Rejected(RejectReason.BLOCKED_HOST, blocked)
        }

        val lastSegment = uri.lastPathSegment?.takeIf { it.isNotBlank() && it != "/" }
        val extension = lastSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }

        if (extension != null && extension in PLAYLIST_EXTENSIONS) {
            return Result.Rejected(RejectReason.STREAM_PLAYLIST, extension)
        }

        return Result.Accepted(
            url = input,
            fileNameHint = lastSegment,
            extension = extension
        )
    }

    /** Returns the matching blocked host (for the error message) or null when the host is fine. */
    fun blockedHostFor(host: String): String? {
        val normalized = host.lowercase().removePrefix("www.")
        return BLOCKED_HOSTS.firstOrNull { blocked ->
            normalized == blocked || normalized.endsWith(".$blocked")
        }
    }

    fun looksLikeVideoExtension(extension: String?): Boolean =
        extension != null && extension in VIDEO_EXTENSIONS
}
