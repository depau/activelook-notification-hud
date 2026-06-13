package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import com.activelook.activelooksdk.Glasses
import com.activelook.activelooksdk.types.FontInfo
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.display.NotifItem
import eu.depau.activelooknotifications.phone.StatusInfo
import eu.depau.glasslayout.activelook.ActiveLookSink
import eu.depau.glasslayout.activelook.DeviceTransform
import eu.depau.glasslayout.activelook.FontResolver
import eu.depau.glasslayout.activelook.ResolvedFont
import eu.depau.glasslayout.core.diff.Differ
import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.geom.LSize
import eu.depau.glasslayout.core.layout.LayoutSolver
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.render.RenderCommand
import eu.depau.glasslayout.core.text.AsciiTextShaper

/**
 * Façade over the glasslayout engine. Keeps the imperative API the [eu.depau.activelooknotifications.display.DisplayController]
 * already calls (`renderIdle/AppPresent/Peek/Open`, `wrapBody`, `visibleBodyLines`, `setFonts`,
 * `setBrightness`, `showIcon`, `debugBorder`, `glasses`, `clearScreen`). Internally each render builds
 * a core [Element] tree, solves it, and presents it through the partial-redraw [ActiveLookSink].
 */
class GlassesRenderer(metrics: GlassesTextMetrics) {

    private val fonts = FontResolver(
        desiredHeights = mapOf(
            FontToken.Small to Const.DESIRED_SMALL_H,
            FontToken.Medium to Const.DESIRED_MEDIUM_H,
            FontToken.Large to Const.DESIRED_LARGE_H,
        ),
        fallback = mapOf(
            FontToken.Small to ResolvedFont(Const.FALLBACK_SMALL, Const.FALLBACK_SMALL_H),
            FontToken.Medium to ResolvedFont(Const.FALLBACK_MEDIUM, Const.FALLBACK_MEDIUM_H),
            FontToken.Large to ResolvedFont(Const.FALLBACK_LARGE, Const.FALLBACK_LARGE_H),
        ),
    )
    private val measurer = GlassesTextMeasurer(metrics, fonts)
    private val solver = LayoutSolver(measurer)
    private val sink = ActiveLookSink(
        differ = Differ(
            screen = LRect(0, 0, Const.SCREEN_W, Const.SCREEN_H),
            maxDirtyRects = Const.MAX_DIRTY_RECTS,
            mergeSlop = Const.MERGE_SLOP,
            fullRedrawThresholdPct = Const.FULL_REDRAW_PCT,
        ),
        transform = DeviceTransform(Const.SCREEN_W, Const.SCREEN_H),
        fonts = fonts,
    )

    @Volatile
    var showIcon: Boolean = Const.SHOW_ICON

    @Volatile
    var debugBorder: Boolean = false

    /** Optional observer of every presented frame (the full logical command list) — for the on-phone preview. */
    @Volatile
    var frameSink: ((List<RenderCommand>) -> Unit)? = null

    var glasses: Glasses?
        get() = sink.glasses
        set(value) { sink.glasses = value }

    fun setFonts(list: List<FontInfo>) {
        fonts.setFonts(list)
        sink.invalidate()
    }

    fun setBrightness(level: Int) {
        runCatching { sink.glasses?.luma(level.coerceIn(0, 15).toByte()) }
    }

    /** Full hardware clear + reset diff cache (used on disconnect). */
    fun clearScreen() = sink.blank()

    // --- States (same signatures the controller already calls) ---

    fun renderIdle(status: StatusInfo, contentYOffset: Int = 0) =
        present(HudScreens.idle(status, contentYOffset))

    fun renderAppPresent(notif: NotifItem, iconBitmap: Bitmap?, status: StatusInfo, contentYOffset: Int = 0) =
        present(HudScreens.appPresent(notif, if (showIcon) iconBitmap else null, status, contentYOffset))

    fun renderPeek(notif: NotifItem, status: StatusInfo, contentYOffset: Int = 0) =
        present(HudScreens.peek(notif, status, contentYOffset))

    fun renderOpen(notif: NotifItem, lines: List<String>, offset: Int, status: StatusInfo, contentYOffset: Int = 0) {
        val scrollPx = offset * measurer.linePitch(FontToken.Small)
        val showScrollbar = Const.SHOW_SCROLLBAR && lines.size > visibleBodyLines()
        present(HudScreens.open(notif, lines, scrollPx, showScrollbar, contentYOffset, status))
    }

    /** Word-wrap a notification body to the open-view body width. */
    fun wrapBody(text: String): List<String> =
        measurer.wrap(AsciiTextShaper.toAscii(text), FontToken.Small, bodyWidth())

    /** How many body lines fit below the title in the open view. */
    fun visibleBodyLines(): Int {
        val statusH = measurer.lineHeight(FontToken.Small)
        val titleH = measurer.lineHeight(FontToken.Medium)
        val pitch = measurer.linePitch(FontToken.Small)
        val cell = measurer.lineHeight(FontToken.Small)
        val bodyAreaH = Const.SCREEN_H - Const.TOP_MARGIN - Const.BOTTOM_MARGIN -
            statusH - Const.STATUS_CONTENT_GAP - titleH - Const.STATUS_CONTENT_GAP
        return (((bodyAreaH - cell) / pitch) + 1).coerceAtLeast(1)
    }

    private fun bodyWidth(): Int =
        Const.SCREEN_W - 2 * Const.MARGIN_X - Const.SCROLLBAR_GAP - Const.SCROLLBAR_W

    private fun present(root: Element) {
        var cmds = solver.solve(root, LSize(Const.SCREEN_W, Const.SCREEN_H))
        if (debugBorder) cmds = cmds + debugBorderCommands()
        sink.present(cmds)
        frameSink?.invoke(cmds)
    }

    private fun debugBorderCommands(): List<RenderCommand> {
        val w = Const.SCREEN_W
        val h = Const.SCREEN_H
        val c = Const.COLOR_WHITE.toInt()
        return listOf(
            RenderCommand.BorderRect(LRect(0, 0, w, h), c, 1),
            RenderCommand.Line(w / 2, 0, w / 2, h, c),
            RenderCommand.Line(0, h / 2, w, h / 2, c),
        )
    }
}
