package com.allvideodownloader.app.ui

import android.content.Context
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.allvideodownloader.app.data.model.LibraryVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small in-memory thumbnail cache built on `ContentResolver.loadThumbnail`, which asks
 * MediaStore for a frame instead of decoding the whole video. Keeps the app dependency-free
 * (no image-loading library) while the Library list still scrolls smoothly.
 */
object ThumbnailLoader {

    private val cache = LruCache<Long, ImageBitmap>(80)

    suspend fun load(context: Context, video: LibraryVideo): ImageBitmap? {
        cache.get(video.id)?.let { return it }

        val image = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver
                    .loadThumbnail(video.uri, Size(480, 270), null)
                    .asImageBitmap()
            }.getOrNull()
        } ?: return null

        cache.put(video.id, image)
        return image
    }

    fun evict(id: Long) {
        cache.remove(id)
    }
}
