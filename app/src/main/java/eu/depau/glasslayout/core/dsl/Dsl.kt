package eu.depau.glasslayout.core.dsl

import eu.depau.glasslayout.core.model.Container
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Dir
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.ImageEl
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.BoxInsets
import eu.depau.glasslayout.core.model.Border
import eu.depau.glasslayout.core.model.Sizing
import eu.depau.glasslayout.core.model.SpacerEl
import eu.depau.glasslayout.core.model.TextAlign
import eu.depau.glasslayout.core.model.TextEl

/** Sizing conveniences so the DSL reads naturally (`width = Fill`, `height = Grow`, `Fixed(40)`). */
val Fit: Sizing = Sizing.Fit
val Fill: Sizing = Sizing.Grow(1)
val Grow: Sizing = Sizing.Grow(1)
fun Fixed(px: Int): Sizing = Sizing.Fixed(px)
fun grow(weight: Int): Sizing = Sizing.Grow(weight)
fun Percent(frac: Float): Sizing = Sizing.Percent(frac)

@DslMarker
annotation class GlassLayoutDsl

/** Collects child elements inside a container builder lambda. */
@GlassLayoutDsl
class ChildrenScope {
    @PublishedApi
    internal val children = mutableListOf<Element>()

    fun column(
        width: Sizing = Fit,
        height: Sizing = Fit,
        padding: BoxInsets = BoxInsets.NONE,
        spacing: Int = 0,
        main: MainAlign = MainAlign.Start,
        cross: CrossAlign = CrossAlign.Start,
        background: Int? = null,
        border: Border? = null,
        clip: Boolean = false,
        scrollY: Int = 0,
        translateY: Int = 0,
        margin: BoxInsets = BoxInsets.NONE,
        content: ChildrenScope.() -> Unit = {},
    ) {
        children += buildContainer(
            Dir.Column, width, height, padding, spacing, main, cross,
            background, border, clip, scrollY, translateY, margin, content,
        )
    }

    fun row(
        width: Sizing = Fit,
        height: Sizing = Fit,
        padding: BoxInsets = BoxInsets.NONE,
        spacing: Int = 0,
        main: MainAlign = MainAlign.Start,
        cross: CrossAlign = CrossAlign.Start,
        background: Int? = null,
        border: Border? = null,
        clip: Boolean = false,
        scrollY: Int = 0,
        translateY: Int = 0,
        margin: BoxInsets = BoxInsets.NONE,
        content: ChildrenScope.() -> Unit = {},
    ) {
        children += buildContainer(
            Dir.Row, width, height, padding, spacing, main, cross,
            background, border, clip, scrollY, translateY, margin, content,
        )
    }

    /** A box: a column container that draws a background fill and/or border. */
    fun box(
        width: Sizing = Fit,
        height: Sizing = Fit,
        padding: BoxInsets = BoxInsets.NONE,
        spacing: Int = 0,
        main: MainAlign = MainAlign.Start,
        cross: CrossAlign = CrossAlign.Start,
        background: Int? = null,
        border: Border? = null,
        margin: BoxInsets = BoxInsets.NONE,
        content: ChildrenScope.() -> Unit = {},
    ) = column(width, height, padding, spacing, main, cross, background, border, margin = margin, content = content)

    fun text(
        text: String,
        font: FontToken = FontToken.Small,
        color: Int = 15,
        width: Sizing = Fit,
        align: TextAlign = TextAlign.Start,
        wrap: Boolean = false,
        maxLines: Int = 1,
        ellipsize: Boolean = true,
        margin: BoxInsets = BoxInsets.NONE,
        padding: BoxInsets = BoxInsets.NONE,
        border: Border? = null,
        background: Int? = null,
    ) {
        children += TextEl(width, Fit, text, font, color, align, wrap, maxLines, ellipsize, margin, padding, border, background)
    }

    fun image(
        key: Any,
        payload: Any,
        w: Int,
        h: Int,
        margin: BoxInsets = BoxInsets.NONE,
        padding: BoxInsets = BoxInsets.NONE,
        border: Border? = null,
        background: Int? = null,
        draw: Boolean = true,
    ) {
        children += ImageEl(Fixed(w), Fixed(h), key, payload, w, h, margin, padding, border, background, draw)
    }

    fun spacer(
        width: Sizing = Fit,
        height: Sizing = Fit,
        margin: BoxInsets = BoxInsets.NONE,
        padding: BoxInsets = BoxInsets.NONE,
        border: Border? = null,
        background: Int? = null,
    ) {
        children += SpacerEl(width, height, margin, padding, border, background)
    }
}

private fun buildContainer(
    dir: Dir, width: Sizing, height: Sizing, padding: BoxInsets, spacing: Int,
    main: MainAlign, cross: CrossAlign, background: Int?, border: Border?,
    clip: Boolean, scrollY: Int, translateY: Int, margin: BoxInsets,
    content: ChildrenScope.() -> Unit,
): Container {
    val scope = ChildrenScope().apply(content)
    return Container(
        width, height, dir, padding, spacing, main, cross,
        background, border, clip, scrollY, translateY, scope.children, margin
    )
}

/** Root builders. */
fun column(
    width: Sizing = Fit,
    height: Sizing = Fit,
    padding: BoxInsets = BoxInsets.NONE,
    spacing: Int = 0,
    main: MainAlign = MainAlign.Start,
    cross: CrossAlign = CrossAlign.Start,
    background: Int? = null,
    border: Border? = null,
    clip: Boolean = false,
    scrollY: Int = 0,
    translateY: Int = 0,
    margin: BoxInsets = BoxInsets.NONE,
    content: ChildrenScope.() -> Unit = {},
): Element = buildContainer(
    Dir.Column, width, height, padding, spacing, main, cross,
    background, border, clip, scrollY, translateY, margin, content,
)

fun row(
    width: Sizing = Fit,
    height: Sizing = Fit,
    padding: BoxInsets = BoxInsets.NONE,
    spacing: Int = 0,
    main: MainAlign = MainAlign.Start,
    cross: CrossAlign = CrossAlign.Start,
    background: Int? = null,
    border: Border? = null,
    clip: Boolean = false,
    scrollY: Int = 0,
    translateY: Int = 0,
    margin: BoxInsets = BoxInsets.NONE,
    content: ChildrenScope.() -> Unit = {},
): Element = buildContainer(
    Dir.Row, width, height, padding, spacing, main, cross,
    background, border, clip, scrollY, translateY, margin, content,
)
