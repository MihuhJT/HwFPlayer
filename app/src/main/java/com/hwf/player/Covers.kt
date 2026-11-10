package com.hwf.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album covers embedded in the music files, cached per track (battle rounds in one album can
 * each have their own picture). A file without a cover is remembered too, so it isn't re-read.
 */
object Covers {
    private const val THUMB_PX = 96
    private const val BLOB_PX = 512

    private val thumbs = LruCache<Long, Any>(300)  // ImageBitmap or NONE
    private val blobs = LruCache<Long, Any>(6)
    private val NONE = Any()

    /** Small square for list rows. */
    suspend fun thumb(context: Context, t: Track): ImageBitmap? =
        cached(thumbs, t.id) { load(context, t.uri, THUMB_PX)?.let(::centerCrop) }

    /** Dark round cover with a soft edge, ready to draw inside the glass blob. */
    suspend fun blob(context: Context, trackId: Long, uri: Uri): ImageBitmap? =
        cached(blobs, trackId) { load(context, uri, BLOB_PX)?.let(::softCircle) }

    private suspend fun cached(cache: LruCache<Long, Any>, key: Long, make: () -> Bitmap?): ImageBitmap? {
        cache.get(key)?.let { return it as? ImageBitmap }
        val img = withContext(Dispatchers.IO) { runCatching { make() }.getOrNull()?.asImageBitmap() }
        cache.put(key, img ?: NONE)
        return img
    }

    private fun load(context: Context, uri: Uri, px: Int): Bitmap? {
        // Android's own thumbnailer first (fast, cached by the system), then the raw embedded picture
        runCatching { return context.contentResolver.loadThumbnail(uri, Size(px, px), null) }
        val bytes = MediaMetadataRetriever().run {
            try { setDataSource(context, uri); embeddedPicture } catch (_: Exception) { null } finally { release() }
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun centerCrop(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(THUMB_PX, THUMB_PX, Bitmap.Config.ARGB_8888)
        val from = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        Canvas(out).drawBitmap(src, from, Rect(0, 0, THUMB_PX, THUMB_PX), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * Prepared once per album so the blob only has to draw one image per frame:
     * center-cropped, darkened so the white controls stay readable, edge fading to transparent.
     */
    private fun softCircle(src: Bitmap): Bitmap {
        val n = BLOB_PX
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val from = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        c.drawBitmap(src, from, Rect(0, 0, n, n), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(0xA6000000.toInt())  // ~65% black on top
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                n / 2f, n / 2f, n / 2f,
                intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), mask)  // the whole square, so the corners go transparent too
        return out
    }
}

@Composable
fun rememberThumb(t: Track): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, t.id, t.uri) { value = Covers.thumb(context, t) }
}
