package eu.depau.glasslayout.core.model

/**
 * The immutable element tree the layout engine solves. Pure data — no Android, no SDK, no app types.
 * Images are intentionally OPAQUE here ([ImageEl.payload]/[ImageEl.key]); the renderer backend
 * decides how to draw them (e.g. stream a Bitmap or display a saved-image id).
 */

/** Abstract font size class; a [eu.depau.glasslayout.core.text.TextMeasurer] maps it to real metrics. */
enum class FontToken { Small, Medium, Large }

/** How an element is sized along one axis. */
sealed interface Sizing {
    /** Exactly [px] logical pixels. */
    data class Fixed(val px: Int) : Sizing
    /** Take a share of leftover space along the parent's main axis, by [weight]. */
    data class Grow(val weight: Int = 1) : Sizing
    /** Wrap content. */
    data object Fit : Sizing
    /** A fraction (0..1) of the parent's content size. */
    data class Percent(val frac: Float) : Sizing
}

enum class Dir { Row, Column }
enum class MainAlign { Start, Center, End, SpaceBetween }
enum class CrossAlign { Start, Center, End, Stretch }
enum class TextAlign { Start, Center, End }

data class BoxInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom

    companion object {
        val NONE = BoxInsets()
        fun all(p: Int) = BoxInsets(p, p, p, p)
        fun symH(h: Int) = BoxInsets(h, 0, h, 0)
        fun symV(v: Int) = BoxInsets(0, v, 0, v)
        fun sym(h: Int, v: Int) = BoxInsets(h, v, h, v)
    }
}

data class Border(val color: Int, val thickness: Int = 1)

sealed interface Element {
    val width: Sizing
    val height: Sizing
    val margin: BoxInsets get() = BoxInsets.NONE
    val padding: BoxInsets get() = BoxInsets.NONE
    val border: Border? get() = null
    val background: Int? get() = null
}

/**
 * A flex container; also doubles as a "box" when [background]/[border] are set. Children lay out
 * along [dir]. [clip] + [scrollY] make it a scroll viewport (children translated up by scrollY,
 * those fully outside the container's box are pruned at whole-element granularity).
 */
data class Container(
    override val width: Sizing,
    override val height: Sizing,
    val dir: Dir,
    override val padding: BoxInsets,
    val spacing: Int,
    val main: MainAlign,
    val cross: CrossAlign,
    override val background: Int? = null,
    override val border: Border? = null,
    val clip: Boolean = false,
    val scrollY: Int = 0,
    /** Logical Y translation applied to this subtree (used for slide animations). */
    val translateY: Int = 0,
    val children: List<Element> = emptyList(),
    override val margin: BoxInsets = BoxInsets.NONE,
    val suppressImages: Boolean = false,
) : Element

data class TextEl(
    override val width: Sizing,
    override val height: Sizing,
    val text: String,
    val font: FontToken,
    val color: Int,
    val align: TextAlign,
    val wrap: Boolean,
    val maxLines: Int,
    val ellipsize: Boolean,
    override val margin: BoxInsets = BoxInsets.NONE,
    override val padding: BoxInsets = BoxInsets.NONE,
    override val border: Border? = null,
    override val background: Int? = null,
) : Element

data class ImageEl(
    override val width: Sizing,
    override val height: Sizing,
    val key: Any,
    val payload: Any,
    val wPx: Int,
    val hPx: Int,
    override val margin: BoxInsets = BoxInsets.NONE,
    override val padding: BoxInsets = BoxInsets.NONE,
    override val border: Border? = null,
    override val background: Int? = null,
    val draw: Boolean = true,
) : Element

data class SpacerEl(
    override val width: Sizing,
    override val height: Sizing,
    override val margin: BoxInsets = BoxInsets.NONE,
    override val padding: BoxInsets = BoxInsets.NONE,
    override val border: Border? = null,
    override val background: Int? = null,
) : Element
