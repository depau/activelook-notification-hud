package eu.depau.activelooknotifications.glasses

import eu.depau.activelooknotifications.Const
import eu.depau.glasslayout.activelook.FontResolver
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.text.TextSpan
import eu.depau.glasslayout.core.text.TextSpanParser
import java.text.BreakIterator

class TextSpanParserImpl(
    private val fonts: FontResolver,
    private val raster: GlyphRasterizer,
    private val maxImageWidth: Int = Const.MAX_LINE_W,
) : TextSpanParser {

    override fun parse(text: String, font: FontToken): List<TextSpan> {
        val fontPx = fonts.resolve(font).heightPx
        return tokenize(text, fontPx)
    }

    private fun tokenize(paragraph: String, fontPx: Int): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        val bi = BreakIterator.getCharacterInstance().apply { setText(paragraph) }
        val word = StringBuilder()
        val glyph = StringBuilder()

        fun flushWord() {
            if (word.isNotEmpty()) {
                out += TextSpan.Text(word.toString())
                word.setLength(0)
            }
        }

        fun flushGlyph() {
            if (glyph.isEmpty()) return
            val run = glyph.toString()
            val w = raster.measureRun(run, fontPx)
            val payload = if (w > 0) raster.rasterize(run, fontPx) else null
            if (payload != null) {
                out += TextSpan.Image("$run@$fontPx", payload, w, fontPx)
            } else {
                out += TextSpan.Text("?")
            }
            glyph.setLength(0)
        }

        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            val cluster = paragraph.substring(start, end)
            when (val ascii = classify(cluster)) {
                null -> { // non-renderable → accumulate a glyph run (kept ≤ maxImageWidth)
                    flushWord()
                    if (glyph.isNotEmpty() && raster.measureRun(glyph.toString() + cluster, fontPx) > maxImageWidth) {
                        flushGlyph()
                    }
                    glyph.append(cluster)
                }
                " " -> {
                    flushWord()
                    flushGlyph()
                    word.append(" ")
                }
                else -> {
                    flushGlyph()
                    word.append(ascii)
                }
            }
            start = end
            end = bi.next()
        }
        flushWord()
        flushGlyph()
        return coalesce(out)
    }

    private fun classify(cluster: String): String? {
        if (cluster.all { it.isWhitespace() || it == ' ' }) {
            if (cluster.contains('\n')) return "\n"
            return " "
        }
        val sb = StringBuilder()
        for (ch in cluster) {
            when {
                ch.code in 0x20..0x7E -> sb.append(ch)
                PUNCT[ch] != null -> sb.append(PUNCT[ch])
                else -> return null // accented letter / emoji / other script → kept as a glyph image
            }
        }
        return sb.toString()
    }

    private fun coalesce(line: List<TextSpan>): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        val sb = StringBuilder()
        for (r in line) when (r) {
            is TextSpan.Text -> sb.append(r.text)
            is TextSpan.Image -> {
                if (sb.isNotEmpty()) {
                    out += TextSpan.Text(sb.toString())
                    sb.setLength(0)
                }
                out += r
            }
        }
        if (sb.isNotEmpty()) out += TextSpan.Text(sb.toString())
        return out
    }

    private companion object {
        // Only punctuation niceties are transliterated; accents are preserved (rendered as glyphs).
        val PUNCT: Map<Char, String> = mapOf(
            '…' to "...", '‘' to "'", '’' to "'", '‚' to "'", '′' to "'",
            '“' to "\"", '”' to "\"", '«' to "\"", '»' to "\"", '″' to "\"",
            '–' to "-", '—' to "-", '‐' to "-", '•' to "*", '·' to ".",
            ' ' to " ",
        )
    }
}
