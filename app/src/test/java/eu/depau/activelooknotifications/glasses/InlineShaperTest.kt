package eu.depau.activelooknotifications.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records the runs it's asked about; each codepoint is [px] wide; returns a non-null fake payload. */
private class FakeRasterizer(private val supported: Boolean = true) : GlyphRasterizer {
    val measured = mutableListOf<String>()
    override fun measureRun(run: String, fontPx: Int): Int {
        measured += run
        return if (supported) run.codePointCount(0, run.length) * fontPx else 0
    }
    override fun rasterize(run: String, fontPx: Int): Any? = if (supported) "bmp:$run" else null
}

class InlineShaperTest {
    // Each ASCII char is `px` wide → easy width math.
    private fun shaper(r: GlyphRasterizer) = InlineShaper({ s, px -> s.length * px }, r)

    private fun firstLine(out: List<List<Inline>>) = out.firstOrNull().orEmpty()

    @Test fun pureAsciiIsOneTextRun() {
        val out = shaper(FakeRasterizer()).shape("hello world", fontPx = 10, maxWidthPx = 1000, maxLines = 1)
        assertEquals(listOf(Inline.Text("hello world")), firstLine(out))
    }

    @Test fun accentedLettersBecomeGlyphsNotTransliterated() {
        // "café": "caf" stays native; the accented "é" is kept as a glyph (rendered with SSP later).
        val line = firstLine(shaper(FakeRasterizer()).shape("café", 10, 1000, 1))
        assertEquals(2, line.size)
        assertEquals(Inline.Text("caf"), line[0])
        assertTrue(line[1] is Inline.Glyph)
    }

    @Test fun punctuationStillTransliterated() {
        // Smart quotes / ellipsis are punctuation niceties → ASCII, not glyphs.
        assertEquals(listOf(Inline.Text("\"hi\"...")), firstLine(shaper(FakeRasterizer()).shape("“hi”…", 10, 1000, 1)))
    }

    @Test fun emojiBecomesGlyphInterleavedWithText() {
        val r = FakeRasterizer()
        val out = shaper(r).shape("hi 😀 there", 10, 1000, 1)
        val line = firstLine(out)
        assertEquals(3, line.size)
        assertEquals(Inline.Text("hi "), line[0])
        assertTrue(line[1] is Inline.Glyph)
        assertEquals(Inline.Text(" there"), line[2])
    }

    @Test fun multiCodepointEmojiStaysOneGlyph() {
        // ZWJ family, flag (regional indicators), skin-tone, VS-16 — each must be ONE run.
        for (emoji in listOf("👨‍👩‍👧", "🇫🇷", "👍🏽", "✏️")) {
            val r = FakeRasterizer()
            val line = firstLine(shaper(r).shape(emoji, 10, 1000, 1))
            assertEquals("one glyph for $emoji", 1, line.size)
            assertTrue(line[0] is Inline.Glyph)
            assertTrue("rasterized as one run", r.measured.contains(emoji))
        }
    }

    @Test fun consecutiveEmojiMergeIntoOneGlyph() {
        val r = FakeRasterizer()
        val line = firstLine(shaper(r).shape("🎉🎊✨", 10, 1000, 1))
        assertEquals(1, line.size)
        assertTrue(line[0] is Inline.Glyph)
    }

    @Test fun wrapsMixedWordsAndGlyphs() {
        val r = FakeRasterizer()
        // width 10/char; line max = 60px → 6 chars. "aaa 😀 bbb" : "aaa"(30)+sp(10)+glyph(10)=50 fits; +sp+"bbb" overflows.
        val out = shaper(r).shape("aaa 😀 bbb", fontPx = 10, maxWidthPx = 60, maxLines = 10)
        assertEquals(2, out.size)
        assertEquals(Inline.Text("aaa "), out[0][0])
        assertTrue(out[0][1] is Inline.Glyph)
        assertEquals(listOf(Inline.Text("bbb")), out[1])
    }

    @Test fun maxLinesAppendsAsciiEllipsis() {
        val out = shaper(FakeRasterizer()).shape("aaaa bbbb cccc", fontPx = 10, maxWidthPx = 40, maxLines = 1)
        val line = firstLine(out)
        assertEquals(1, line.size)
        val t = line[0] as Inline.Text
        assertTrue("ends with ...", t.ascii.endsWith("..."))
        assertTrue("fits width", t.ascii.length * 10 <= 40)
    }

    @Test fun unsupportedGlyphFallsBackToQuestionMark() {
        val out = shaper(FakeRasterizer(supported = false)).shape("x 中 y", 10, 1000, 1)
        val merged = firstLine(out).filterIsInstance<Inline.Text>().joinToString("") { it.ascii }
        assertTrue("has ? fallback", merged.contains("?"))
        assertTrue("no glyphs", firstLine(out).none { it is Inline.Glyph })
    }

    @Test fun emptyTextIsEmpty() {
        assertTrue(shaper(FakeRasterizer()).shape("", 10, 100, 1).isEmpty())
    }

    @Test fun newlinesSplitParagraphs() {
        val out = shaper(FakeRasterizer()).shape("line one\nline two", 10, 1000, 10)
        assertEquals(2, out.size)
        assertEquals(listOf(Inline.Text("line one")), out[0])
        assertEquals(listOf(Inline.Text("line two")), out[1])
    }
}
