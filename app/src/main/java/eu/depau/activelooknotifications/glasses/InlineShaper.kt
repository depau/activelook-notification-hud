package eu.depau.activelooknotifications.glasses

import java.text.BreakIterator

/**
 * Shapes arbitrary Unicode text into lines of [Inline] runs for the glasses: ASCII (post Latin
 * transliteration) stays native text; every contiguous non-renderable run becomes ONE glyph image.
 *
 * Pure logic (grapheme segmentation, classification, wrapping, ellipsis) — measurement and
 * rasterization are delegated to [asciiWidth] and [GlyphRasterizer], so this is JVM-unit-testable.
 */
class InlineShaper(
    private val asciiWidth: (text: String, fontPx: Int) -> Int,
    private val raster: GlyphRasterizer,
) {
    /** Shape [text] into wrapped lines of inline runs (≤ [maxWidthPx] each, ≤ [maxLines] lines). */
    fun shape(text: String, fontPx: Int, maxWidthPx: Int, maxLines: Int): List<List<Inline>> {
        if (text.isEmpty()) return emptyList()
        val raw = ArrayList<List<Inline>>()
        for (paragraph in text.split('\n')) {
            raw += wrap(tokenize(paragraph, fontPx, maxWidthPx), fontPx, maxWidthPx)
        }
        val limited = truncate(raw, maxLines, fontPx, maxWidthPx)
        return limited.map { coalesce(it) }
    }

    private class Tok(val inline: Inline, val width: Int, val precededBySpace: Boolean)

    // --- segmentation + classification ---

    /** The ASCII a cluster contributes, " " for whitespace, or null if it must become a glyph. */
    private fun classify(cluster: String): String? {
        if (cluster.all { it.isWhitespace() || it == ' ' }) return " "
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

    private companion object {
        // Only punctuation niceties are transliterated; accents are preserved (rendered as glyphs).
        val PUNCT: Map<Char, String> = mapOf(
            '…' to "...", '‘' to "'", '’' to "'", '‚' to "'", '′' to "'",
            '“' to "\"", '”' to "\"", '«' to "\"", '»' to "\"", '″' to "\"",
            '–' to "-", '—' to "-", '‐' to "-", '•' to "*", '·' to ".",
            ' ' to " ",
        )
    }

    private fun tokenize(paragraph: String, fontPx: Int, maxWidth: Int): List<Tok> {
        val out = ArrayList<Tok>()
        val bi = BreakIterator.getCharacterInstance().apply { setText(paragraph) }
        val word = StringBuilder()
        val glyph = StringBuilder()
        var pendingSpace = false

        fun flushWord() {
            if (word.isNotEmpty()) {
                val s = word.toString()
                out += Tok(Inline.Text(s), asciiWidth(s, fontPx), pendingSpace)
                pendingSpace = false
                word.setLength(0)
            }
        }
        fun flushGlyph() {
            if (glyph.isEmpty()) return
            val run = glyph.toString()
            val w = raster.measureRun(run, fontPx)
            val payload = if (w > 0) raster.rasterize(run, fontPx) else null
            if (payload != null) {
                out += Tok(Inline.Glyph("$run@$fontPx", payload, w, fontPx), w, pendingSpace)
            } else {
                out += Tok(Inline.Text("?"), asciiWidth("?", fontPx), pendingSpace)
            }
            pendingSpace = false
            glyph.setLength(0)
        }

        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            val cluster = paragraph.substring(start, end)
            when (val ascii = classify(cluster)) {
                null -> { // non-renderable → accumulate a glyph run (kept ≤ line width)
                    flushWord()
                    if (glyph.isNotEmpty() && raster.measureRun(glyph.toString() + cluster, fontPx) > maxWidth) {
                        flushGlyph()
                    }
                    glyph.append(cluster)
                }
                " " -> { flushWord(); flushGlyph(); pendingSpace = true }
                else -> { flushGlyph(); word.append(ascii) }
            }
            start = end
            end = bi.next()
        }
        flushWord()
        flushGlyph()
        return out
    }

    // --- wrapping ---

    private fun wrap(toks: List<Tok>, fontPx: Int, maxWidth: Int): List<List<Inline>> {
        val spaceW = asciiWidth(" ", fontPx)
        val lines = ArrayList<List<Inline>>()
        var cur = ArrayList<Inline>()
        var curW = 0
        for (t in toks) {
            var sep = if (cur.isEmpty()) 0 else if (t.precededBySpace) spaceW else 0
            if (cur.isNotEmpty() && curW + sep + t.width > maxWidth) {
                lines += cur; cur = ArrayList(); curW = 0; sep = 0
            }
            if (cur.isEmpty() && t.width > maxWidth && t.inline is Inline.Text) {
                hardSplit((t.inline as Inline.Text).ascii, fontPx, maxWidth, lines).let { leftover ->
                    cur = arrayListOf(Inline.Text(leftover))
                    curW = asciiWidth(leftover, fontPx)
                }
                continue
            }
            if (sep > 0) { cur += Inline.Text(" "); curW += spaceW }
            cur += t.inline
            curW += t.width
        }
        if (cur.isNotEmpty()) lines += cur
        if (lines.isEmpty()) lines.add(emptyList())
        return lines
    }

    /** Emit full lines for an over-wide ASCII word; return the trailing leftover chunk. */
    private fun hardSplit(s: String, fontPx: Int, maxWidth: Int, lines: MutableList<List<Inline>>): String {
        val chunk = StringBuilder()
        for (ch in s) {
            if (chunk.isNotEmpty() && asciiWidth("$chunk$ch", fontPx) > maxWidth) {
                lines += listOf(Inline.Text(chunk.toString()))
                chunk.setLength(0)
            }
            chunk.append(ch)
        }
        return chunk.toString()
    }

    // --- maxLines + ellipsis ---

    private fun truncate(lines: List<List<Inline>>, maxLines: Int, fontPx: Int, maxWidth: Int): List<List<Inline>> {
        if (maxLines <= 0 || lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        val last = kept.last().toMutableList()
        val ellW = asciiWidth("...", fontPx)
        while (last.isNotEmpty() && lineWidth(last, fontPx) + ellW > maxWidth) {
            val tail = last.last()
            if (tail is Inline.Text && tail.ascii.length > 1) {
                last[last.lastIndex] = Inline.Text(tail.ascii.dropLast(1))
            } else {
                last.removeAt(last.lastIndex)
            }
        }
        last += Inline.Text("...")
        kept[kept.lastIndex] = last
        return kept
    }

    private fun lineWidth(line: List<Inline>, fontPx: Int): Int = line.sumOf {
        when (it) {
            is Inline.Text -> asciiWidth(it.ascii, fontPx)
            is Inline.Glyph -> it.widthPx
        }
    }

    /** Merge consecutive ASCII runs so a glyph-free line is a single `txt` (and drop empties). */
    private fun coalesce(line: List<Inline>): List<Inline> {
        val out = ArrayList<Inline>()
        val sb = StringBuilder()
        for (r in line) when (r) {
            is Inline.Text -> sb.append(r.ascii)
            is Inline.Glyph -> {
                if (sb.isNotEmpty()) { out += Inline.Text(sb.toString()); sb.setLength(0) }
                out += r
            }
        }
        if (sb.isNotEmpty()) out += Inline.Text(sb.toString())
        return out
    }
}
