package dev.codingcoffee.cleansms.util

import android.content.ContentResolver
import android.content.res.AssetManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageLoader {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(uri: Uri, maxPx: Int): ImageBitmap? = cache.get("$uri@$maxPx")

    suspend fun load(resolver: ContentResolver, uri: Uri, maxPx: Int): ImageBitmap? =
        decode("$uri@$maxPx", maxPx) { ImageDecoder.createSource(resolver, uri) }

    suspend fun loadAsset(assets: AssetManager, path: String, maxPx: Int): ImageBitmap? =
        decode("asset:$path@$maxPx", maxPx) { ImageDecoder.createSource(assets, path) }

    private suspend fun decode(key: String, maxPx: Int, source: () -> ImageDecoder.Source): ImageBitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(source()) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxPx) {
                        val scale = maxPx.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }.asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }
}

@Composable
fun rememberImage(uri: Uri?, maxPx: Int): ImageBitmap? {
    val resolver = LocalContext.current.contentResolver
    val image by produceState(uri?.let { ImageLoader.peek(it, maxPx) }, uri, maxPx) {
        if (uri != null && value == null) value = ImageLoader.load(resolver, uri, maxPx)
    }
    return image
}
