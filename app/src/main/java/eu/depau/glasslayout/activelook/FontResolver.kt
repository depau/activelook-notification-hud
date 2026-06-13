package eu.depau.glasslayout.activelook

import com.activelook.activelooksdk.types.FontInfo
import eu.depau.glasslayout.core.model.FontToken
import kotlin.math.abs

/** A resolved ActiveLook ROM font: its command id and its pixel height. */
data class ResolvedFont(val id: Byte, val heightPx: Int)

/**
 * Maps abstract [FontToken]s to concrete ActiveLook ROM fonts. On connect, the device's font list is
 * supplied via [setFonts]; the closest-height font to each token's desired size is chosen. Until then
 * (or if the list is empty) the per-token fallback is used. This is the former `pickFont`/`heightOf`.
 */
class FontResolver(
    private val desiredHeights: Map<FontToken, Int>,
    private val fallback: Map<FontToken, ResolvedFont>,
) {
    @Volatile
    private var fonts: List<FontInfo> = emptyList()

    fun setFonts(list: List<FontInfo>) {
        fonts = list
    }

    fun resolve(token: FontToken): ResolvedFont {
        val list = fonts
        if (list.isEmpty()) return fallback.getValue(token)
        val desired = desiredHeights.getValue(token)
        val f = list.minByOrNull { abs(it.height - desired) } ?: return fallback.getValue(token)
        return ResolvedFont(f.id.toByte(), f.height)
    }
}
