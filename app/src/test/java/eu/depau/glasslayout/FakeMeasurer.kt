package eu.depau.glasslayout

import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.text.TextMeasurer

/** Deterministic measurer for tests: every glyph is [charW] px wide; heights are token-based. */
class FakeMeasurer(private val charW: Int = 10) : TextMeasurer {
    private fun h(token: FontToken) = when (token) {
        FontToken.Small -> 10
        FontToken.Medium -> 14
        FontToken.Large -> 20
    }

    override fun measureWidth(text: String, token: FontToken): Int = text.length * charW
    override fun lineHeight(token: FontToken): Int = h(token)
    override fun linePitch(token: FontToken): Int = h(token) + 2

    override fun wrap(text: String, token: FontToken, maxWidth: Int): List<String> {
        val maxChars = (maxWidth / charW).coerceAtLeast(1)
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (candidate.length <= maxChars) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) out += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    override fun ellipsize(text: String, token: FontToken, maxWidth: Int): String {
        if (measureWidth(text, token) <= maxWidth) return text
        val maxChars = (maxWidth / charW).coerceAtLeast(1)
        if (maxChars <= 3) return ".".repeat(maxChars)
        return text.take(maxChars - 3).trimEnd() + "..."
    }
}
