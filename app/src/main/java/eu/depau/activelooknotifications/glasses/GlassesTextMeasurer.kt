package eu.depau.activelooknotifications.glasses

import eu.depau.glasslayout.activelook.FontResolver
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.text.TextMeasurer

/**
 * Bridges the engine's token-based [TextMeasurer] to the app's pixel-height [GlassesTextMetrics]:
 * a [FontToken] is resolved to a concrete ROM-font height via [FontResolver], then measured with the
 * real glasses font.
 */
class GlassesTextMeasurer(
    private val metrics: GlassesTextMetrics,
    private val fonts: FontResolver,
) : TextMeasurer {

    private fun h(token: FontToken): Int = fonts.resolve(token).heightPx

    override fun measureWidth(text: String, token: FontToken): Int = metrics.measureWidth(text, h(token))
    override fun lineHeight(token: FontToken): Int = metrics.cellHeight(h(token))
    override fun linePitch(token: FontToken): Int = metrics.linePitch(h(token))
    override fun wrap(text: String, token: FontToken, maxWidth: Int): List<String> =
        metrics.wrap(text, h(token), maxWidth)
    override fun ellipsize(text: String, token: FontToken, maxWidth: Int): String =
        metrics.ellipsize(text, h(token), maxWidth)
}
