package eu.depau.activelooknotifications.glasses

import eu.depau.glasslayout.activelook.FontResolver
import eu.depau.glasslayout.activelook.ResolvedFont
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.text.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSpanParserImplTest {
    private val fonts = FontResolver(
        desiredHeights = mapOf(FontToken.Small to 10),
        fallback = mapOf(FontToken.Small to ResolvedFont(0, 10))
    )

    private fun parser(r: GlyphRasterizer) = TextSpanParserImpl(fonts, r)

    @Test fun pureAsciiIsOneTextRun() {
        val out = parser(FakeRasterizer()).parse("hello world", FontToken.Small)
        assertEquals(listOf(TextSpan.Text("hello world")), out)
    }

    @Test fun accentedLettersBecomeGlyphsNotTransliterated() {
        val out = parser(FakeRasterizer()).parse("café", FontToken.Small)
        assertEquals(2, out.size)
        assertEquals(TextSpan.Text("caf"), out[0])
        assertTrue(out[1] is TextSpan.Image)
    }

    @Test fun punctuationStillTransliterated() {
        assertEquals(listOf(TextSpan.Text("\"hi\"...")), parser(FakeRasterizer()).parse("“hi”…", FontToken.Small))
    }

    @Test fun emojiBecomesGlyphInterleavedWithText() {
        val r = FakeRasterizer()
        val out = parser(r).parse("hi 😀 there", FontToken.Small)
        assertEquals(3, out.size)
        assertEquals(TextSpan.Text("hi "), out[0])
        assertTrue(out[1] is TextSpan.Image)
        assertEquals(TextSpan.Text(" there"), out[2])
    }

    @Test fun multiCodepointEmojiStaysOneGlyph() {
        for (emoji in listOf("👨‍👩‍👧", "🇫🇷", "👍🏽", "✏️")) {
            val r = FakeRasterizer()
            val out = parser(r).parse(emoji, FontToken.Small)
            assertEquals("one glyph for $emoji", 1, out.size)
            assertTrue(out[0] is TextSpan.Image)
            assertTrue("rasterized as one run", r.measured.contains(emoji))
        }
    }

    @Test fun consecutiveEmojiMergeIntoOneGlyph() {
        val r = FakeRasterizer()
        val out = parser(r).parse("🎉🎊✨", FontToken.Small)
        assertEquals(1, out.size)
        assertTrue(out[0] is TextSpan.Image)
    }

    @Test fun unsupportedGlyphFallsBackToQuestionMark() {
        val out = parser(FakeRasterizer(supported = false)).parse("x 中 y", FontToken.Small)
        val merged = out.filterIsInstance<TextSpan.Text>().joinToString("") { it.text }
        assertTrue("has ? fallback", merged.contains("?"))
        assertTrue("no glyphs", out.none { it is TextSpan.Image })
    }

    @Test fun emptyTextIsEmpty() {
        assertTrue(parser(FakeRasterizer()).parse("", FontToken.Small).isEmpty())
    }
}

private class FakeRasterizer(private val supported: Boolean = true) : GlyphRasterizer {
    val measured = mutableListOf<String>()
    override fun measureRun(run: String, fontPx: Int): Int {
        measured += run
        return if (supported) run.codePointCount(0, run.length) * fontPx else 0
    }
    override fun rasterize(run: String, fontPx: Int): Any? = if (supported) "bmp:$run" else null
}
