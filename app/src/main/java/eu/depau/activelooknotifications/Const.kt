package eu.depau.activelooknotifications

import com.activelook.activelooksdk.types.ImgStreamFormat

/**
 * All tunable constants live here so behaviour and layout are easy to change in one place.
 *
 * Coordinate note: the ActiveLook display is 304x256 px. The SDK's [com.activelook.activelooksdk.Glasses.txt]
 * takes the BOTTOM-LEFT corner of the text as its anchor in [com.activelook.activelooksdk.types.Rotation.TOP_LR],
 * and Y grows upward. Font heights and exact Y offsets below are first guesses and must be
 * calibrated on-device (see [GlassesLayout.centerX] and the FONT_* ids).
 */
object Const {
    // --- Timeouts (milliseconds) ---
    /** How long the app icon + name splash is shown before the peek. */
    const val APP_NAME_DURATION_MS = 1_500L

    /** How long the title + first body line peek stays before going idle. */
    const val PEEK_TIMEOUT_MS = 5_000L

    /** How long the full (opened) notification stays before going idle. */
    const val OPEN_TIMEOUT_MS = 10_000L

    /** Idle clock tick; the renderer only repaints when the displayed minute changes. */
    const val CLOCK_REFRESH_MS = 1_000L

    /** Ignore gesture taps that arrive closer together than this (sensor can double-fire). */
    const val GESTURE_DEBOUNCE_MS = 300L

    /** Identical re-posts of the same notification within this window are ignored. */
    const val DEDUP_WINDOW_MS = 1_500L

    /** Delay between glasses reconnection attempts. */
    const val RECONNECT_DELAY_MS = 5_000L

    // --- Glasses display geometry (px) ---
    // Coordinate model (from the ActiveLook reference): origin bottom-left, y measured upward
    // (0..256), but txt(x, y) anchors the TOP of the glyph row, so text hangs DOWNWARD from y.
    // Thus the status row sits near y=252 (top of screen) and successive body lines have
    // DECREASING y. Width estimation uses the real font heights read from the glasses at connect.
    const val SCREEN_W = 304
    const val SCREEN_H = 256
    const val CENTER_X = 152

    // Horizontal safe margin: the extreme left/right of the buffer is in overscan / outside the
    // reflector and not reliably visible, so keep content well inside. Tune per the debug border.
    const val MARGIN_X = 24

    // --- Logical layout (engine coords: origin top-left, x→right, y→down; flipped to device in the sink) ---
    // The top/bottom edges of the reflector are not reliably visible (like the left/right), so keep a
    // generous vertical safe margin too — not just the horizontal MARGIN_X.
    const val TOP_MARGIN = 14         // logical inset from the top edge (status bar row)
    const val BOTTOM_MARGIN = 14
    const val STATUS_CONTENT_GAP = 6  // gap below the status bar before content
    const val ICON_NAME_GAP = 8       // app-present: gap between icon and name
    const val SCROLLBAR_GAP = 4       // open: gap between body and scrollbar
    const val SCROLLBAR_W = 3         // open: scrollbar thickness
    const val MAX_LINE_W = 250        // default body wrap width

    // Status bar icon spacing.
    const val STATUS_ITEM_GAP =
        10    // between the glasses-battery group and the phone-battery group
    const val STATUS_PCT_GAP = 5      // between a battery icon/widget and its "%" text
    const val STATUS_LABEL_GAP = 4    // between the "5G"/"LTE" label and the cellular signal icon

    /** Extra pixels added to a font's height to get its line pitch. */
    const val LINE_GAP = 4

    /** Vertical gap between a title and the content below it (clock→date, title→body). */
    const val TITLE_BODY_GAP = 8

    /** Calibration scalar applied to measured text widths (1.0 = exact; tune on-device if needed). */
    const val WIDTH_CAL = 1.0f

    // --- Fonts ---
    // The 4 firmware ROM fonts (per the ActiveLook API doc) are full-ASCII:
    //   id0 h24, id1 h24, id2 h35, id3 h49.
    // The renderer reads the actual list on connect and picks the font whose height best matches
    // these desired sizes; the fallbacks below are used only if the list can't be read.
    // NOTE: we intentionally do NOT call cfgSet("ALooK") — that config replaces some fonts with
    // number-only glyph sets, which would stop letters (e.g. app/notification titles) rendering.
    const val DESIRED_SMALL_H = 24
    const val DESIRED_MEDIUM_H = 35
    const val DESIRED_LARGE_H = 49

    const val FALLBACK_SMALL: Byte = 0
    const val FALLBACK_MEDIUM: Byte = 2
    const val FALLBACK_LARGE: Byte = 3
    const val FALLBACK_SMALL_H = 24
    const val FALLBACK_MEDIUM_H = 35
    const val FALLBACK_LARGE_H = 49

    /** Max drawing color (4-bit grayscale, 0..15). */
    const val COLOR_WHITE: Byte = 0x0F

    // --- Icons ---
    const val ICON_SIZE = 48
    const val ICON_THRESHOLD = 128
    val ICON_FORMAT: ImgStreamFormat = ImgStreamFormat.MONO_4BPP_HEATSHRINK

    // --- Animation (per-frame redraw of content at an offset Y; text only) ---
    /** Frames used for a slide/scroll animation. Set to 1 for instant (no animation). */
    const val ANIM_FRAMES = 4
    const val ANIM_FRAME_MS = 35L
    /** How far (px) content slides during a transition/scroll animation. */
    const val ANIM_TRAVEL = 40

    // --- Defaults / feature flags ---
    const val DEFAULT_BRIGHTNESS = 12
    const val SHOW_ICON = true
    const val SHOW_ONGOING = false
    const val INTERRUPT_OPEN_ON_NEW = true
    const val ANIMATE_TRANSITIONS = true
    const val SHOW_STATUS_SEPARATOR = false
    const val SHOW_SCROLLBAR = true

    /**
     * Open a throwaway 2nd GATT to the glasses purely to request CONNECTION_PRIORITY_HIGH, raising
     * the shared ACL link's connection interval (~50ms→~15ms). Speeds up redraws and avoids the
     * SDK's "Could not write rx" retry storm / dropped batches — especially vs an Android-phone
     * emulator (slow peripheral interval), but also faster image/config uploads on real glasses.
     */
    const val BOOST_CONNECTION_PRIORITY = true

    // --- Layout-engine partial-redraw knobs ---
    const val MAX_DIRTY_RECTS = 4
    const val MERGE_SLOP = 8
    const val FULL_REDRAW_PCT = 70
}
