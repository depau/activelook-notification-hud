package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.phone.NetworkType
import eu.depau.activelooknotifications.phone.StatusInfo
import eu.depau.glasslayout.core.dsl.ChildrenScope
import eu.depau.glasslayout.core.dsl.Fill
import eu.depau.glasslayout.core.dsl.Fixed
import eu.depau.glasslayout.core.dsl.column
import eu.depau.glasslayout.core.dsl.row
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.Padding
import eu.depau.glasslayout.core.model.TextAlign

/**
 * Builds the four HUD states as flex element trees in logical coordinates. Text content is supplied
 * **pre-shaped** as [Inline] runs (ASCII text + glyph images) by [GlassesRenderer]; this object only
 * lays them out. The status bar stays plain ASCII. `drawGlyphs=false` (mid-animation) reserves glyph
 * width with a spacer instead of streaming the image, so layout doesn't jump between frames.
 */
object HudScreens {

    private const val W = Const.SCREEN_W
    private const val H = Const.SCREEN_H
    private val rootPadding = Padding(Const.MARGIN_X, Const.TOP_MARGIN, Const.MARGIN_X, Const.BOTTOM_MARGIN)

    fun idle(status: StatusInfo, clock: List<Inline>, date: List<Inline>, drawGlyphs: Boolean, yOffset: Int): Element =
        column(width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP) {
            statusBar(batteryText(status), signalText(status))
            column(
                width = Fill, height = Fill, main = MainAlign.Start, cross = CrossAlign.Center,
                gap = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
            ) {
                inlineLine(clock, FontToken.Large, TextAlign.Center, drawGlyphs)
                if (date.isNotEmpty()) inlineLine(date, FontToken.Small, TextAlign.Center, drawGlyphs)
            }
        }

    fun appPresent(status: StatusInfo, name: List<Inline>, icon: Bitmap?, drawGlyphs: Boolean, yOffset: Int): Element =
        column(width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP) {
            statusBar(batteryText(status), asciiTime(status))
            column(
                width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
                gap = Const.ICON_NAME_GAP, clip = true, translateY = yOffset,
            ) {
                if (icon != null) image(key = "icon", payload = icon, w = Const.ICON_SIZE, h = Const.ICON_SIZE)
                inlineLine(name, FontToken.Medium, TextAlign.Center, drawGlyphs)
            }
        }

    fun peek(status: StatusInfo, title: List<Inline>, body: List<Inline>, drawGlyphs: Boolean, yOffset: Int): Element =
        column(width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP) {
            statusBar(batteryText(status), asciiTime(status))
            column(
                width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
                gap = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
            ) {
                inlineLine(title, FontToken.Medium, TextAlign.Center, drawGlyphs)
                if (body.isNotEmpty()) inlineLine(body, FontToken.Small, TextAlign.Center, drawGlyphs)
            }
        }

    fun open(
        status: StatusInfo,
        title: List<Inline>,
        bodyLines: List<List<Inline>>,
        scrollPx: Int,
        showScrollbar: Boolean,
        drawGlyphs: Boolean,
        yOffset: Int,
    ): Element = column(width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP) {
        statusBar(batteryText(status), asciiTime(status))
        inlineLine(title, FontToken.Medium, TextAlign.Center, drawGlyphs)
        row(width = Fill, height = Fill, gap = Const.SCROLLBAR_GAP) {
            column(
                width = Fill, height = Fill, clip = true, scrollY = scrollPx,
                gap = Const.LINE_GAP, translateY = yOffset,
            ) {
                for (line in bodyLines) inlineLine(line, FontToken.Small, TextAlign.Start, drawGlyphs)
            }
            if (showScrollbar) {
                box(width = Fixed(Const.SCROLLBAR_W), height = Fill, fill = Const.COLOR_WHITE.toInt())
            }
        }
    }

    // --- helpers ---

    /** Lay out one shaped line as a row of native text + inline glyph images. */
    private fun ChildrenScope.inlineLine(line: List<Inline>, font: FontToken, align: TextAlign, drawGlyphs: Boolean) {
        row(width = Fill, gap = 0, main = mainAlignFor(align), cross = CrossAlign.Center) {
            for (run in line) {
                when (run) {
                    is Inline.Text ->
                        if (run.ascii.isNotEmpty())
                            text(run.ascii, font = font, align = TextAlign.Start, maxLines = 1, ellipsize = false)
                    is Inline.Glyph ->
                        if (drawGlyphs) image(key = run.key, payload = run.payload, w = run.widthPx, h = run.heightPx)
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

    private fun ChildrenScope.statusBar(left: String, right: String) {
        row(width = Fill, main = MainAlign.SpaceBetween) {
            text(left, font = FontToken.Small)
            if (right.isNotEmpty()) text(right, font = FontToken.Small, align = TextAlign.End)
        }
    }

    private fun batteryText(s: StatusInfo): String =
        "G:" + (s.glassesBattery?.let { "$it%" } ?: "--") + " P:${s.phoneBattery}%"

    private fun asciiTime(s: StatusInfo): String = s.time

    private fun signalText(s: StatusInfo): String {
        val sig = s.signal ?: return ""
        if (sig.networkType == NetworkType.WIFI) return sig.networkType.label
        val bars = if (sig.bars > 0) ".".repeat(sig.bars.coerceIn(0, 4)) else ""
        return listOf(sig.networkType.label, bars).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
