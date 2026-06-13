package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import eu.depau.activelooknotifications.Const
import eu.depau.glasslayout.core.dsl.ChildrenScope
import eu.depau.glasslayout.core.dsl.Fill
import eu.depau.glasslayout.core.dsl.Fixed
import eu.depau.glasslayout.core.dsl.column
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.Padding
import eu.depau.glasslayout.core.model.TextAlign
import kotlin.math.roundToInt

/**
 * Builds the four HUD states as flex element trees in logical coordinates. Text content is supplied
 * **pre-shaped** as [Inline] runs (ASCII text + glyph images) by [GlassesRenderer]; this object only
 * lays them out. The status bar stays plain ASCII. `drawGlyphs=false` (mid-animation) reserves glyph
 * width with a spacer instead of streaming the image, so layout doesn't jump between frames.
 */
object HudScreens {

    private const val W = Const.SCREEN_W
    private const val H = Const.SCREEN_H
    private val rootPadding =
        Padding(Const.MARGIN_X, Const.TOP_MARGIN, Const.MARGIN_X, Const.BOTTOM_MARGIN)

    fun idle(
        status: StatusBarModel,
        clock: List<Inline>,
        date: List<Inline>,
        drawGlyphs: Boolean,
        yOffset: Int
    ): Element =
        column(
            width = Fixed(W),
            height = Fixed(H),
            padding = rootPadding,
            gap = Const.STATUS_CONTENT_GAP
        ) {
            statusBar(status)
            column(
                width = Fill, height = Fill, main = MainAlign.Start, cross = CrossAlign.Center,
                gap = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
            ) {
                inlineLine(clock, FontToken.Large, TextAlign.Center, drawGlyphs)
                if (date.isNotEmpty()) inlineLine(
                    date,
                    FontToken.Small,
                    TextAlign.Center,
                    drawGlyphs
                )
            }
        }

    fun appPresent(
        status: StatusBarModel,
        name: List<Inline>,
        icon: Bitmap?,
        drawGlyphs: Boolean,
        yOffset: Int
    ): Element =
        column(
            width = Fixed(W),
            height = Fixed(H),
            padding = rootPadding,
            gap = Const.STATUS_CONTENT_GAP
        ) {
            statusBar(status)
            column(
                width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
                gap = Const.ICON_NAME_GAP, clip = true, translateY = yOffset,
            ) {
                if (icon != null) image(
                    key = "icon",
                    payload = icon,
                    w = Const.ICON_SIZE,
                    h = Const.ICON_SIZE
                )
                inlineLine(name, FontToken.Medium, TextAlign.Center, drawGlyphs)
            }
        }

    fun peek(
        status: StatusBarModel,
        title: List<Inline>,
        body: List<Inline>,
        drawGlyphs: Boolean,
        yOffset: Int
    ): Element =
        column(
            width = Fixed(W),
            height = Fixed(H),
            padding = rootPadding,
            gap = Const.STATUS_CONTENT_GAP
        ) {
            statusBar(status)
            column(
                width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
                gap = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
            ) {
                inlineLine(title, FontToken.Medium, TextAlign.Center, drawGlyphs)
                if (body.isNotEmpty()) inlineLine(
                    body,
                    FontToken.Small,
                    TextAlign.Center,
                    drawGlyphs
                )
            }
        }

    fun open(
        status: StatusBarModel,
        title: List<Inline>,
        bodyLines: List<List<Inline>>,
        scrollPx: Int,
        showScrollbar: Boolean,
        drawGlyphs: Boolean,
        yOffset: Int,
    ): Element = column(
        width = Fixed(W),
        height = Fixed(H),
        padding = rootPadding,
        gap = Const.STATUS_CONTENT_GAP
    ) {
        statusBar(status)
        inlineLine(title, FontToken.Medium, TextAlign.Center, drawGlyphs)
        row(width = Fill, height = Fill, gap = Const.SCROLLBAR_GAP) {
            column(
                width = Fill, height = Fill, clip = true, scrollY = scrollPx,
                gap = Const.LINE_GAP, translateY = yOffset,
            ) {
                for (line in bodyLines) inlineLine(
                    line,
                    FontToken.Small,
                    TextAlign.Start,
                    drawGlyphs
                )
            }
            if (showScrollbar) {
                box(
                    width = Fixed(Const.SCROLLBAR_W),
                    height = Fill,
                    fill = Const.COLOR_WHITE.toInt()
                )
            }
        }
    }

    // --- helpers ---

    /** Lay out one shaped line as a row of native text + inline glyph images. */
    private fun ChildrenScope.inlineLine(
        line: List<Inline>,
        font: FontToken,
        align: TextAlign,
        drawGlyphs: Boolean
    ) {
        row(width = Fill, gap = 0, main = mainAlignFor(align), cross = CrossAlign.Center) {
            for (run in line) {
                when (run) {
                    is Inline.Text ->
                        if (run.ascii.isNotEmpty())
                            text(
                                run.ascii,
                                font = font,
                                align = TextAlign.Start,
                                maxLines = 1,
                                ellipsize = false
                            )

                    is Inline.Glyph ->
                        if (drawGlyphs) image(
                            key = run.key,
                            payload = run.payload,
                            w = run.widthPx,
                            h = run.heightPx
                        )
                        else spacer(width = Fixed(run.widthPx), height = Fixed(run.heightPx))
                }
            }
        }
    }

    private fun mainAlignFor(a: TextAlign) = when (a) {
        TextAlign.Start -> MainAlign.Start
        TextAlign.Center -> MainAlign.Center
        TextAlign.End -> MainAlign.End
    }

    private fun ChildrenScope.statusBar(m: StatusBarModel) {
        row(width = Fill, main = MainAlign.SpaceBetween, cross = CrossAlign.Center) {
            row(gap = Const.STATUS_ITEM_GAP, cross = CrossAlign.Center) {        // left: batteries
                m.glasses?.let { batteryViz(it, m.fontPx) }
                batteryViz(m.phone, m.fontPx)
            }
            when (val r =
                m.right) {                                            // right: signal / time
                is StatusRight.Wifi ->
                    image(key = r.key, payload = r.icon, w = m.fontPx, h = m.fontPx)

                is StatusRight.Cellular -> row(
                    gap = Const.STATUS_LABEL_GAP,
                    cross = CrossAlign.Center
                ) {
                    text(r.typeLabel, font = FontToken.Small)
                    signalBars(r.level, m.fontPx)
                }

                is StatusRight.Time -> text(r.text, font = FontToken.Small, align = TextAlign.End)
                StatusRight.None -> {}
            }
        }
    }

    private fun ChildrenScope.batteryViz(b: BatteryViz, fontPx: Int) {
        row(gap = Const.STATUS_PCT_GAP, cross = CrossAlign.Center) {
            image(key = b.key, payload = b.bitmap, w = fontPx, h = fontPx)
            if (b.percent.isNotEmpty()) text(b.percent, font = FontToken.Small)
        }
    }

    /**
     * Cellular signal as chunky solid ascending bars (thin outlines were unreadable on the glasses):
     * only the active [level] bars are drawn, each a bold solid block of increasing height,
     * bottom-aligned. [level] in 0..4. The "5G"/"LTE" label beside it conveys the rest.
     */
    private fun ChildrenScope.signalBars(level: Int, fontPx: Int) {
        val white = Const.COLOR_WHITE.toInt()
        val barW = (fontPx * 0.22f).roundToInt().coerceAtLeast(4)
        val gap = (fontPx * 0.13f).roundToInt().coerceAtLeast(2)
        val maxH = fontPx
        row(height = Fixed(maxH), gap = gap, cross = CrossAlign.End) {
            for (i in 0 until level.coerceIn(0, 4)) {
                val barH = (maxH * (0.4f + 0.6f * (i + 1) / 4f)).roundToInt().coerceAtLeast(4)
                box(width = Fixed(barW), height = Fixed(barH), fill = white, borderThick = 0)
            }
        }
    }
}
