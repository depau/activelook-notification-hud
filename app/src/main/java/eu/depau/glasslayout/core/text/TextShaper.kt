package eu.depau.glasslayout.core.text

import java.text.Normalizer

/**
 * A future-facing seam: a piece of text shaped into a run of segments. Today only [Text] is
 * produced; emoji/special glyphs will later become [Glyph] segments rendered as overlaid images.
 */
sealed interface Segment {
    /** ASCII-renderable text drawn with the ROM font. */
    data class Text(val text: String) : Segment

    /** A glyph the ROM font can't render (emoji/CJK) — to be drawn as an image later. */
    data class Glyph(val key: String) : Segment
}

interface TextShaper {
    /** Tier 1: render everything as one ASCII-safe string (no segment splitting yet). */
    fun toAscii(text: String): String
}

/**
 * Transliterates arbitrary text to the ROM fonts' renderable ASCII range (0x20–0x7E).
 *
 * - Latin accents are stripped to their base letter (é→e, à→a) via Unicode NFD + combining-mark removal.
 * - A small map handles common non-decomposable symbols (…→..., ß→ss, €→EUR, smart quotes/dashes→ASCII).
 * - Anything still outside ASCII (emoji, CJK, …) becomes a single space (collapsed), so text stays
 *   legible instead of rendering as the font's missing-glyph mark.
 */
object AsciiTextShaper : TextShaper {

    private val SPECIALS: Map<Char, String> = mapOf(
        '…' to "...",
        'ß' to "ss", 'æ' to "ae", 'Æ' to "AE", 'œ' to "oe", 'Œ' to "OE", 'ø' to "o", 'Ø' to "O",
        'đ' to "d", 'Đ' to "D", 'ł' to "l", 'Ł' to "L", 'þ' to "th", 'Þ' to "Th",
        '€' to "EUR", '£' to "GBP", '¥' to "JPY", '©' to "(c)", '®' to "(r)", '™' to "(tm)",
        '“' to "\"", '”' to "\"", '„' to "\"", '«' to "\"", '»' to "\"",
        '‘' to "'", '’' to "'", '‚' to "'", '′' to "'", '″' to "\"",
        '–' to "-", '—' to "-", '‐' to "-", '•' to "*", '·' to ".", '°' to "deg",
        ' ' to " ", // non-breaking space
    )

    override fun toAscii(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        // Decompose so accents become base letter + combining mark, which we then drop.
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        for (c in decomposed) {
            when {
                c == '\n' || c == '\t' -> sb.append(' ')
                c.code in 0x20..0x7E -> sb.append(c)
                c.code in 0x0300..0x036F -> {} // combining diacritical mark — drop
                SPECIALS.containsKey(c) -> sb.append(SPECIALS[c])
                else -> sb.append(' ') // unrenderable (emoji/CJK) — future: a Glyph image
            }
        }
        return sb.toString().replace(MULTISPACE, " ").trim()
    }

    private val MULTISPACE = Regex(" {2,}")
}
