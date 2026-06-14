package eu.depau.activelooknotifications.glasses

interface GlyphRasterizer {
    /** Pixel width [run] occupies when rendered with the system font at [fontPx]; 0 if unrenderable. */
    fun measureRun(run: String, fontPx: Int): Int

    /** Rasterize [run] to a widthPx × fontPx mono image (a `Bitmap`), or null if it can't be rendered. */
    fun rasterize(run: String, fontPx: Int): Any?
}
