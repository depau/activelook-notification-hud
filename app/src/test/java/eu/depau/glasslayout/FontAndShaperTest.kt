package eu.depau.glasslayout

import com.activelook.activelooksdk.types.FontInfo
import eu.depau.glasslayout.activelook.FontResolver
import eu.depau.glasslayout.activelook.ResolvedFont
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.text.AsciiTextShaper
import org.junit.Assert.assertEquals
import org.junit.Test

class FontAndShaperTest {

    private fun resolver() = FontResolver(
        desiredHeights = mapOf(FontToken.Small to 24, FontToken.Medium to 35, FontToken.Large to 49),
        fallback = mapOf(
            FontToken.Small to ResolvedFont(0, 24),
            FontToken.Medium to ResolvedFont(2, 35),
            FontToken.Large to ResolvedFont(3, 49),
        ),
    )

    @Test fun fontResolverUsesFallbackWhenEmpty() {
        val r = resolver()
        assertEquals(ResolvedFont(2, 35), r.resolve(FontToken.Medium))
    }

    @Test fun fontResolverPicksClosestHeight() {
        val r = resolver()
        r.setFonts(listOf(FontInfo(0, 24), FontInfo(1, 24), FontInfo(2, 38), FontInfo(3, 49)))
        assertEquals(38, r.resolve(FontToken.Medium).heightPx) // closest to 35
        assertEquals(2.toByte(), r.resolve(FontToken.Medium).id)
        assertEquals(49, r.resolve(FontToken.Large).heightPx)
        assertEquals(24, r.resolve(FontToken.Small).heightPx)
    }

    @Test fun shaperTransliteratesAccents() {
        assertEquals("cafe", AsciiTextShaper.toAscii("café"))
        assertEquals("aeiou", AsciiTextShaper.toAscii("àèìòù"))
        assertEquals("Reunion", AsciiTextShaper.toAscii("Réunion"))
    }

    @Test fun shaperReplacesSpecials() {
        assertEquals("a...b", AsciiTextShaper.toAscii("a…b"))
        assertEquals("5EUR", AsciiTextShaper.toAscii("5€"))
        assertEquals("ss", AsciiTextShaper.toAscii("ß"))
        assertEquals("\"hi\"", AsciiTextShaper.toAscii("“hi”"))
    }

    @Test fun shaperDropsEmojiAndCollapsesSpaces() {
        assertEquals("hi there", AsciiTextShaper.toAscii("hi 😀 there"))
    }
}
