package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.SignalWifi0Bar
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.createBitmap

/**
 * Rasterizes Material Symbols (Rounded) [ImageVector]s from `material-icons-extended` into small
 * **monochrome** [Bitmap]s for the glasses' 4bpp image commands — no asset bundling, no Context.
 * Each path is filled white, anti-aliased, then alpha-thresholded to ON (gray 0xF0F0F0 → 4bpp level
 * 15) / OFF, matching [AndroidGlyphRasterizer]/[IconRasterizer]. Results are cached so an unchanged
 * level returns the **same** Bitmap instance and the renderer's Differ skips the redraw.
 */
class StatusIcons {

    private val cache = LruCache<String, Bitmap>(16)

    fun smartphone(px: Int): Bitmap = get("phone", px) { Icons.Rounded.Smartphone }

    fun bluetooth(px: Int): Bitmap = get("bt", px) { Icons.Rounded.Bluetooth }

    fun wifi(level: Int, px: Int): Bitmap = get("wifi$level", px) {
        when (level.coerceIn(0, 4)) {
            0 -> Icons.Rounded.SignalWifi0Bar
            1 -> Icons.Rounded.NetworkWifi1Bar
            2 -> Icons.Rounded.NetworkWifi2Bar
            3 -> Icons.Rounded.NetworkWifi3Bar
            else -> Icons.Rounded.SignalWifi4Bar
        }
    }

    private inline fun get(key: String, px: Int, vector: () -> ImageVector): Bitmap {
        val ck = "$key@$px"
        cache.get(ck)?.let { return it }
        val bmp = rasterize(vector(), px)
        cache.put(ck, bmp)
        return bmp
    }

    private fun rasterize(image: ImageVector, size: Int): Bitmap {
        val src = createBitmap(size, size)
        val canvas = Canvas(src)
        canvas.scale(size / image.viewportWidth, size / image.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        renderGroup(image.root, canvas, paint)

        val pixels = IntArray(size * size)
        src.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            pixels[i] = if (Color.alpha(pixels[i]) >= ALPHA_THRESHOLD) ON else OFF
        }
        val out = createBitmap(size, size)
        out.setPixels(pixels, 0, size, 0, 0, size, size)
        src.recycle()
        return out
    }

    /** Walk the vector tree, applying group transforms (matches Compose's group matrix order). */
    private fun renderGroup(group: VectorGroup, canvas: Canvas, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        if (group.clipPathData.isNotEmpty()) {
            canvas.clipPath(PathParser().addPathNodes(group.clipPathData).toPath().asAndroidPath())
        }
        for (node: VectorNode in group) {
            when (node) {
                is VectorGroup -> renderGroup(node, canvas, paint)
                is VectorPath -> canvas.drawPath(
                    PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(), paint,
                )
            }
        }
        canvas.restore()
    }

    companion object {
        private const val ON = 0xFFF0F0F0.toInt()
        private const val OFF = Color.BLACK
        private const val ALPHA_THRESHOLD = 96
    }
}
