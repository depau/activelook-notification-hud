package eu.depau.activelooknotifications.glasses

import android.content.Context
import android.graphics.Bitmap
import com.activelook.activelooksdk.Glasses
import com.activelook.activelooksdk.types.FontInfo
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.display.NotifItem
import eu.depau.activelooknotifications.phone.NetworkType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Façade over the glasslayout engine. Keeps the imperative API the [eu.depau.activelooknotifications.display.DisplayController]
 * already calls (`renderIdle/AppPresent/Peek/Open`, `wrapBody`, `visibleBodyLines`, `setFonts`,
 * `setBrightness`, `showIcon`, `debugBorder`, `glasses`, `clearScreen`). Internally each render builds
 * a core [Element] tree, solves it, and presents it through the partial-redraw [ActiveLookSink].
 */
class GlassesRenderer(metrics: GlassesTextMetrics, context: Context) {

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
    private val shaper = InlineShaper(metrics::measureWidth, AndroidGlyphRasterizer(context))
    private val statusIcons = StatusIcons()
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

    // --- States (same signatures the controller already calls; glyphs only on the settled frame) ---

    fun renderIdle(status: StatusInfo, contentYOffset: Int = 0) {
        val dg = contentYOffset == 0
        present(
            HudScreens.idle(
                statusModel(status, idle = true),
                shapeOne(status.time, FontToken.Large),
                shapeOne(status.date, FontToken.Small),
                dg,
                contentYOffset
            )
        )
    }

    fun renderAppPresent(notif: NotifItem, iconBitmap: Bitmap?, status: StatusInfo, contentYOffset: Int = 0) {
        val dg = contentYOffset == 0
        present(
            HudScreens.appPresent(
                statusModel(status, idle = false),
                shapeOne(notif.appName, FontToken.Medium),
                if (showIcon) iconBitmap else null,
                dg,
                contentYOffset
            )
        )
    }

    fun renderPeek(notif: NotifItem, status: StatusInfo, contentYOffset: Int = 0) {
        val dg = contentYOffset == 0
        val w = Const.SCREEN_W - 2 * Const.MARGIN_X
        val title = shapeLines(notif.title, FontToken.Medium, Const.PEEK_TITLE_LINES, w)
        val body = shapeLines(notif.sanitizedBody, FontToken.Small, Const.PEEK_BODY_LINES, w)
        present(HudScreens.peek(statusModel(status, idle = false), title, body, dg, contentYOffset))
    }

    /** Render page [page] of the gesture-opened notification list. */
    fun renderNotifList(items: List<NotifItem>, page: Int, status: StatusInfo, contentYOffset: Int = 0) {
        val dg = contentYOffset == 0
        val rows = buildListRows(items)
        val scrollPx = page * pageContentHeight()
        present(
            HudScreens.notifList(
                statusModel(status, idle = false),
                rows,
                statusIcons.bullet(Const.BULLET_SIZE),
                scrollPx,
                dg,
                contentYOffset,
            )
        )
    }

    /** Glance shown when a gesture fires with nothing posted. */
    fun renderNoNotifs(status: StatusInfo, contentYOffset: Int = 0) {
        val dg = contentYOffset == 0
        val msg = shapeOne("No notifications", FontToken.Medium)
        present(HudScreens.peek(statusModel(status, idle = false), listOf(msg), emptyList(), dg, contentYOffset))
    }

    /** Resolve the phone/glasses [StatusInfo] into icon bitmaps + a primitive battery for [HudScreens]. */
    private fun statusModel(status: StatusInfo, idle: Boolean): StatusBarModel {
        val px = fontPx(FontToken.Small)
        val glasses =
            status.glassesBattery?.let { BatteryViz("bt", statusIcons.bluetooth(px), "$it%") }
        val phone = BatteryViz("phone", statusIcons.smartphone(px), "${status.phoneBattery}%")
        val right: StatusRight = if (!idle) {
            StatusRight.Time(status.time)
        } else {
            val sig = status.signal
            when {
                sig == null || sig.networkType == NetworkType.NONE -> StatusRight.None
                sig.networkType == NetworkType.WIFI ->
                    StatusRight.Wifi("wifi${sig.bars}", statusIcons.wifi(sig.bars, px))

                else ->
                    StatusRight.Cellular(sig.bars, sig.networkType.label)
            }
        }
        return StatusBarModel(glasses, phone, right, px)
    }

    private fun shapeOne(text: String, font: FontToken): List<Inline> =
        shaper.shape(text, fontPx(font), Const.SCREEN_W - 2 * Const.MARGIN_X, 1).firstOrNull() ?: emptyList()

    private fun shapeLines(text: String, font: FontToken, maxLines: Int, widthPx: Int): List<List<Inline>> =
        shaper.shape(text, fontPx(font), widthPx, maxLines)

    private fun fontPx(font: FontToken): Int = fonts.resolve(font).heightPx

    // --- Notification list: row construction + pagination height math ---

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun listContentWidth(): Int = Const.SCREEN_W - 2 * Const.MARGIN_X

    /** Pre-shape the whole list into [ListRow]s (heights become deterministic for pagination). */
    private fun buildListRows(items: List<NotifItem>): List<ListRow> {
        val w = listContentWidth()
        // App name shares the header row with the icon + " HH:mm"; ellipsize it to the leftover.
        val appNameW = (w - Const.LIST_ICON_SIZE - 2 * Const.LIST_HEADER_GAP -
            measurer.measureWidth(" 00:00", FontToken.Small)).coerceAtLeast(1)
        val rows = ArrayList<ListRow>()
        rows += ListRow.Sep
        for (it in items) {
            val time = timeFmt.format(Date(it.postTime))
            val appName = shaper.shape(it.appName, fontPx(FontToken.Small), appNameW, 1).firstOrNull() ?: emptyList()
            rows += ListRow.Header(it.listIconBitmap, appName, time)
            for (line in shapeLines(it.title, FontToken.Small, Const.LIST_MAX_TITLE_LINES, w)) {
                rows += ListRow.Line(line, FontToken.Small)
            }
            for (line in shapeLines(it.sanitizedBody, FontToken.Small, Const.LIST_MAX_BODY_LINES, w)) {
                rows += ListRow.Line(line, FontToken.Small)
            }
            rows += ListRow.Sep
        }
        rows += ListRow.Bullet
        return rows
    }

    /** Logical height of one row — MUST mirror the layout the solver produces for [HudScreens.notifList]. */
    private fun rowHeight(r: ListRow): Int = when (r) {
        ListRow.Sep -> Const.SEP_H
        is ListRow.Header -> maxOf(Const.LIST_ICON_SIZE, measurer.lineHeight(FontToken.Small))
        is ListRow.Line -> measurer.lineHeight(r.font)
        ListRow.Bullet -> Const.BULLET_SIZE
    }

    /** Total list height = Σ row heights + the uniform inter-row gap (matches the scroll column). */
    private fun totalContentHeight(rows: List<ListRow>): Int {
        if (rows.isEmpty()) return 0
        return rows.sumOf { rowHeight(it) } + Const.LIST_GAP * (rows.size - 1)
    }

    /** Clip-viewport height for the list (screen minus margins minus the kept status bar + its gap). */
    private fun pageContentHeight(): Int =
        Const.SCREEN_H - Const.TOP_MARGIN - Const.BOTTOM_MARGIN -
            measurer.lineHeight(FontToken.Small) - Const.STATUS_CONTENT_GAP

    fun notifListPageCount(items: List<NotifItem>): Int {
        val total = totalContentHeight(buildListRows(items))
        val page = pageContentHeight().coerceAtLeast(1)
        return ((total + page - 1) / page).coerceAtLeast(1)
    }

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
