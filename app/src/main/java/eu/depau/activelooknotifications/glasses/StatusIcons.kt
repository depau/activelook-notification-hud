package eu.depau.activelooknotifications.glasses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.SignalWifi0Bar
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SignalCellular0Bar
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material.icons.rounded._5g
import androidx.compose.material.icons.rounded.LteMobiledata
import androidx.compose.material.icons.rounded.LtePlusMobiledata
import androidx.compose.material.icons.rounded._3gMobiledata
import androidx.compose.material.icons.rounded.EMobiledata
import androidx.compose.material.icons.rounded.GMobiledata
import androidx.compose.material.icons.rounded.HMobiledata
import androidx.compose.material.icons.rounded.HPlusMobiledata
import androidx.compose.material.icons.rounded.RMobiledata
import eu.depau.activelooknotifications.R
import eu.depau.activelooknotifications.phone.NetworkType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * Rasterizes Material Symbols (Rounded) [ImageVector]s from `material-icons-extended` into small
 * **monochrome** [Bitmap]s for the glasses' 4bpp image commands — no asset bundling, no Context.
 * Each path is filled white, anti-aliased, then alpha-thresholded to ON (gray 0xF0F0F0 → 4bpp level
 * 15) / OFF, matching [AndroidGlyphRasterizer]/[IconRasterizer]. Results are cached so an unchanged
 * level returns the **same** Bitmap instance and the renderer's Differ skips the redraw.
 */
class StatusIcons(private val context: Context) {

    private val cache = LruCache<String, Bitmap>(16)

    fun smartphone(px: Int): Bitmap = get("phone", px) { Icons.Rounded.Smartphone }

    /** ActiveLook glasses logo (wide vector drawable; [px] is its width, height follows aspect). */
    fun glasses(px: Int): Bitmap {
        val ck = "glasses@$px"
        cache.get(ck)?.let { return it }
        val d = ContextCompat.getDrawable(context, R.drawable.ic_glasses)!!
        val height = (px.toFloat() * d.intrinsicHeight / d.intrinsicWidth).roundToInt().coerceAtLeast(1)
        val src = createBitmap(px, height)
        d.setBounds(0, 0, px, height)
        d.draw(Canvas(src))
        val out = toMono(src)
        cache.put(ck, out)
        return out
    }

    fun roaming(px: Int): Bitmap = get("roaming", px) { Icons.Rounded.RMobiledata }

    fun wifi(level: Int, px: Int): Bitmap = get("wifi$level", px) {
        when (level.coerceIn(0, 4)) {
            0 -> Icons.Rounded.SignalWifi0Bar
            1 -> Icons.Rounded.NetworkWifi1Bar
            2 -> Icons.Rounded.NetworkWifi2Bar
            3 -> Icons.Rounded.NetworkWifi3Bar
            else -> Icons.Rounded.SignalWifi4Bar
        }
    }

    fun cellularType(type: NetworkType, px: Int): Bitmap = get("celltype_${type.name}", px) {
        when (type) {
            NetworkType.GPRS -> Icons.Rounded.GMobiledata
            NetworkType.EDGE -> Icons.Rounded.EMobiledata
            NetworkType.THREE_G -> Icons.Rounded._3gMobiledata
            NetworkType.HSPA -> Icons.Rounded.HMobiledata
            NetworkType.HSPA_PLUS -> Icons.Rounded.HPlusMobiledata
            NetworkType.LTE -> Icons.Rounded.LteMobiledata
            NetworkType.LTE_PLUS -> Icons.Rounded.LtePlusMobiledata
            NetworkType.FIVE_G -> Icons.Rounded._5g
            else -> Icons.Rounded.GMobiledata
        }
    }

    fun cellularSignal(level: Int, noInternet: Boolean, px: Int): Bitmap {
        val key = if (noInternet) "cellsig_noinet_$level" else "cellsig_$level"
        return get(key, px) {
            if (noInternet) {
                when (level.coerceIn(0, 4)) {
                    0 -> Icons.Rounded.SignalCellularConnectedNoInternet0Bar
                    else -> Icons.Rounded.SignalCellularConnectedNoInternet4Bar
                }
            } else {
                when (level.coerceIn(0, 4)) {
                    0 -> Icons.Rounded.SignalCellular0Bar
                    1 -> Icons.Rounded.SignalCellularAlt1Bar
                    2 -> Icons.Rounded.SignalCellularAlt2Bar
                    3 -> Icons.Rounded.SignalCellularAlt
                    else -> Icons.Rounded.SignalCellular4Bar
                }
            }
        }
    }

    /** A solid filled circle (end-of-list bullet), [px] in diameter, mono. Cached. */
    fun bullet(px: Int): Bitmap {
        val ck = "bullet@$px"
        cache.get(ck)?.let { return it }
        val out = createBitmap(px, px)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ON }
        canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
        val pixels = IntArray(px * px)
        out.getPixels(pixels, 0, px, 0, 0, px, px)
        for (i in pixels.indices) {
            pixels[i] = if (Color.alpha(pixels[i]) >= ALPHA_THRESHOLD) ON else OFF
        }
        out.setPixels(pixels, 0, px, 0, 0, px, px)
        cache.put(ck, out)
        return out
    }

    private inline fun get(key: String, px: Int, vector: () -> ImageVector): Bitmap {
        val ck = "$key@$px"
        cache.get(ck)?.let { return it }
        val bmp = rasterize(vector(), px)
        cache.put(ck, bmp)
        return bmp
    }

    /** Rasterize to [height] px, scaling uniformly so the aspect ratio is preserved. */
    private fun rasterize(image: ImageVector, height: Int): Bitmap {
        val scale = height / image.viewportHeight
        val width = (image.viewportWidth * scale).roundToInt().coerceAtLeast(1)
        val src = createBitmap(width, height)
        val canvas = Canvas(src)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        renderGroup(image.root, canvas, paint)
        return toMono(src)
    }

    /** Alpha-threshold an anti-aliased [src] to ON (0xF0F0F0) / OFF; recycles [src]. */
    private fun toMono(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            pixels[i] = if (Color.alpha(pixels[i]) >= ALPHA_THRESHOLD) ON else OFF
        }
        val out = createBitmap(w, h)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
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
