package com.allvideodownloader.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.allvideodownloader.app.data.LinkValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device because [android.net.Uri] is framework code.
 * These cases are the compliance contract — keep them passing.
 */
@RunWith(AndroidJUnit4::class)
class LinkValidatorTest {

    @Test
    fun acceptsDirectMp4Link() {
        val result = LinkValidator.validate("https://cdn.example.com/media/holiday.mp4?token=abc")
        assertTrue(result is LinkValidator.Result.Accepted)
        result as LinkValidator.Result.Accepted
        assertEquals("mp4", result.extension)
    }

    @Test
    fun rejectsYouTube() {
        val result = LinkValidator.validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(
            LinkValidator.RejectReason.BLOCKED_HOST,
            (result as LinkValidator.Result.Rejected).reason
        )
    }

    @Test
    fun rejectsYouTubeSubdomain() {
        val result = LinkValidator.validate("https://m.youtube.com/watch?v=x")
        assertTrue(result is LinkValidator.Result.Rejected)
    }

    @Test
    fun rejectsHlsPlaylist() {
        val result = LinkValidator.validate("https://cdn.example.com/live/master.m3u8")
        assertEquals(
            LinkValidator.RejectReason.STREAM_PLAYLIST,
            (result as LinkValidator.Result.Rejected).reason
        )
    }

    @Test
    fun rejectsNonHttpScheme() {
        val result = LinkValidator.validate("ftp://example.com/clip.mp4")
        assertEquals(
            LinkValidator.RejectReason.UNSUPPORTED_SCHEME,
            (result as LinkValidator.Result.Rejected).reason
        )
    }

    @Test
    fun rejectsEmptyInput() {
        assertEquals(
            LinkValidator.RejectReason.EMPTY,
            (LinkValidator.validate("   ") as LinkValidator.Result.Rejected).reason
        )
    }

    @Test
    fun acceptsExtensionlessLinkForContentTypeCheckLater() {
        val result = LinkValidator.validate("https://files.example.com/download/12345")
        assertTrue(result is LinkValidator.Result.Accepted)
        assertEquals(null, (result as LinkValidator.Result.Accepted).extension)
    }
}
