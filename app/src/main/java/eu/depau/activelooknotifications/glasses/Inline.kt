package eu.depau.activelooknotifications.glasses

/**
 * A run within a shaped line of text. ASCII runs draw with the ROM font ([Text]); runs the ROM font
 * can't render (emoji, CJK, other scripts) become a monochrome image drawn inline ([Glyph]).
 *
 * [Glyph.payload] is opaque (a `Bitmap` in production) so the shaping/wrapping logic stays free of
 * `android.graphics` and JVM-unit-testable; the engine's `ImageEl`/sink treat the payload the same way.
 */
sealed interface Inline {
    /** ASCII-renderable text (post-transliteration). */
    data class Text(val ascii: String) : Inline

    /** A contiguous non-renderable run rasterized to a [widthPx] × [heightPx] mono image. */
    data class Glyph(val key: String, val payload: Any, val widthPx: Int, val heightPx: Int) : Inline
}

/**
 * Renders/measures non-ASCII runs as monochrome images. An interface so [InlineShaper]'s
 * tokenize/wrap/ellipsize logic is pure and JVM-testable (only the impl touches `android.graphics`).
 */
interface GlyphRasterizer {
    /** Pixel width [run] occupies when rendered with the system font at [fontPx]; 0 if unrenderable. */
    fun measureRun(run: String, fontPx: Int): Int

    /** Rasterize [run] to a widthPx × fontPx mono image (a `Bitmap`), or null if it can't be rendered. */
    fun rasterize(run: String, fontPx: Int): Any?
}
