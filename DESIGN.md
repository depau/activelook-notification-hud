# Design notes

Design decisions, rationale, and gotchas for working on this project. `AGENTS.md` is the short
orientation; this file is the deep reference. `README.md` describes user-facing behavior and
on-device tuning. Where this file and `README.md`/code comments disagree, see
[§ Known discrepancies](#known-discrepancies) — some comments have gone stale as the code evolved.

## The hardware reality that shapes everything

The glasses are a **monochrome, 4-bit grayscale, 304×256 reflected display** driven over BLE by the
ActiveLook SDK. The light emitted by the displayed passes through a lens and is reflected by a tiny
mirror onto a special, slightly darker section of the glasses' lens. Three facts ripple through the
whole codebase:

1. **Coordinates are bottom-left origin on the device**, but the layout engine works in top-left
   logical space. Every primitive is flipped at the boundary, and *each primitive type anchors a
   different corner* (see `DeviceTransform`).
2. **Fonts are firmware ROM fonts, ASCII-only.** Non-ASCII (accents, emoji, CJK) must be rasterized
   to bitmaps by the phone and streamed as images. This is why there is a whole shaping/rasterizing
   pipeline.
3. **BLE is slow and lossy.** Redraws must be minimal (hence the "differ"), atomic (hence
   `holdFlush`), and the link interval is force-boosted with a hack. Animation streams glyph images
   only on the final settled frame.
4. **The display edges may be blurry/unreadable.** As documented by ActiveLook, we try to stay clear
   of the screen edges to avoid visibility problems.

BLE and the notification listener **cannot be tested on the emulator** — runtime testing needs a
physical device with glasses. JVM unit tests cover the pure engine only.

## Two layers, one Gradle module

| Package                            | Role                   | Android/SDK deps? | Tested         |
|------------------------------------|------------------------|-------------------|----------------|
| `eu.depau.glasslayout`             | layout + render engine | none (pure)       | JVM unit tests |
| `eu.depau.activelooknotifications` | the Android app        | yes               | on device      |

The split is deliberate and enforced by package boundaries: the engine is JVM-unit-testable because
it knows nothing about Android or ActiveLook. The app bridges Android-specific concerns (glyph
rasterization, icons, BLE) into the engine's abstract interfaces.

---

## The layout engine (`eu.depau.glasslayout`)

A Clay-inspired flexbox pipeline: `Element` tree → `LayoutSolver` → `RenderCommand`s (logical
coords) → `Differ` → `RenderSink` (backend draws). Everything device-specific lives only in
`activelook/`.

It was designed to work around the many limitations of the glasses built-in layout system, among
which its inability to horizontally center UI elements.

### Pure data model (`core/model/Model.kt`)

- The element tree is "pure data — no Android, no SDK, no app types." This is what makes JVM testing
  possible.
- **Images are opaque**: `ImageEl.payload`/`key` are `Any`. The backend decides how to draw them —
  `ActiveLookSink` streams a `Bitmap` via `imgStream4bppHeatShrink` but displays an `Int`/`Byte` id
  via `imgDisplay`.
- `RenderCommand`s are value types, so structural (data-class) equality *is* diff identity — no id
  bookkeeping. `bounds` must be tight/accurate because all dirty-rect math depends on it.

### Five-pass solver (`core/layout/LayoutSolver.kt`)

Order: **fit-widths → resolve-root-width → grow/shrink-widths → wrap-text + fit-heights →
resolve-root-height → grow/shrink-heights → position+emit.**

The ordering is forced by a width→height dependency: text height (line count from wrapping) can only
be computed *after* widths are final. So all width passes complete before any height pass.

- **fit-widths** (bottom-up): preferred + minimum widths. Wrapping text's `minW` = longest single
  word, so it can word-wrap but not shrink narrower than its longest word.
- **grow/shrink-widths** (top-down): distribute slack to `Grow` children or shrink over-wide ones,
  never below `minW`. **Grow remainder goes to the last child** to avoid integer-division pixel loss
  (101px / 3 → 33, 33, 35).
- **wrap + fit-heights** (bottom-up): wrap to the now-final width, height = `lineHeight +
  linePitch*(count-1)`.
- **grow/shrink-heights** (top-down): same on the height axis.
- **place + emit**: absolute positions, alignment, clipping, scroll/translate, emit commands.

Idioms encoded by tests: a `Grow` spacer bottom-anchors content; `SpaceBetween` distributes leftover
as inter-child gaps.

### Coordinate transform (`activelook/DeviceTransform.kt`) — high-gotcha

Logical = top-left origin, y down. Device = bottom-left origin, y up: `deviceY = screenH −
logicalY`. **Each primitive anchors a different corner** because the SDK does:

- **Text** (`txt`, `TOP_LR`): anchors glyph row's top-left → maps logical top-left directly; glyphs
  hang downward.
- **Image** (`imgStream`/`imgDisplay`): anchors the device low corner → must map the logical
  **bottom-right** corner (`screenW−(x+w)`, `screenH−(y+h)`). Verified against the old renderer (a
  centered icon stays centered).
- **Rects/lines**: flip every corner, then normalize so x0≤x1, y0≤y1.

There is no safe-area math here — the safe margin is baked into the logical layout as padding
(`MARGIN_X`, `TOP_MARGIN`, `BOTTOM_MARGIN` in `Const.kt`), because the screen's extreme edges are
overscan/outside the reflector and unreliable.

### Font resolution (`activelook/FontResolver.kt`)

Tokens (`Small`/`Medium`/`Large`) carry a *desired* pixel height. On connect the device reports its
actual ROM font list; resolution picks the font with **minimum absolute height difference**. Until
the list arrives, per-token fallbacks are used. `fonts` is `@Volatile` (cross-thread access).

### Differ (`core/diff/Differ.kt`)

A pure value-identity differ producing a `DiffPlan` (dirty rects to erase + commands to redraw).

- **Symmetric difference as a multiset** so duplicate identical commands are handled.
- **Dirty rects** = bounds of changed commands, clipped to screen, merged when within `mergeSlop`.
- **Redraw set** = *every* command intersecting a dirty rect (not just changed ones), sorted by `z`
  — because erasing a rect wipes unchanged overlapping neighbours, which must be redrawn.
- **`z` is painter's order** (FillRect 0 < BorderRect 1 < Line 2 < Image 3 < Text 4) and must
  survive partial redraws so layering stays correct.
- **Full-redraw fallback**: if dirty-rect count > `maxDirtyRects` or dirty area ≥
  `fullRedrawThresholdPct`, redraw everything. First frame is always full; identical frames are
  no-ops.

### Text measurement vs shaping (`core/text/`)

Two separate abstractions, both reasons rooted in ASCII-only ROM fonts:

- `TextMeasurer` is token-based so the **core never sees device font ids**; exposes `measureWidth`,
  `lineHeight`, `linePitch` (top-to-top, distinct from cell height), `wrap`, `ellipsize`. Abstract
  because real glyph metrics need Android rasterization; `FakeMeasurer` substitutes in tests.
- `TextShaper`/`AsciiTextShaper` transliterates Unicode → ASCII (NFD + drop combining marks, é→e; a
  `SPECIALS` map for …→`...`, ß→ss, €→EUR, smart quotes/dashes) and **collapses anything else to a
  single space** rather than emit a missing-glyph mark. (The app layer's `InlineShaper` goes
  further — it keeps accents/emoji as rasterized glyph images instead.)

### Clipping, scroll, translate

- **`scrollY` vs `translateY`**: both shift *children* (`contentY = y + padding.top − scrollY +
  translateY`), but the node's own box is **not** translated. This lets a clip+translate container
  hold a fixed clip region while content slides inside it. `scrollY` = scroll viewports;
  `translateY` = slide animations.
- Clip pruning is whole-element granularity, with a deliberate **hard top edge**: a command whose
  `bounds.top` is above `clip.top` is dropped entirely, so content sliding up never bleeds above the
  status bar.
- **Ellipsize**: over-wide single lines get `...` only if `ellipsize` is set. Wrapping text over
  `maxLines` joins the dropped remainder onto the last kept line and ellipsizes it — one ellipsis
  signals "more below."

### Device sink (`activelook/ActiveLookSink.kt`)

- Partial redraws happen inside one `holdFlush(HOLD→FLUSH)` so updates are **atomic / flicker-free
  **.
- **`clear()` is never used for redraws** — the SDK doesn't hold it, so it would flash. Full redraws
  erase via a screen-sized `rectf`. `clear()` is reserved for explicit blanking on disconnect.
- **Color is sticky device state**: `setColor` only re-issues `g.color` when it changes; `lastColor`
  resets to −1 at frame start.
- Setting `glasses` triggers `invalidate()` → full redraw on (re)connect.

---

## The rendering bridge (`eu.depau.activelooknotifications.glasses`)

Bridges Android-specific concerns into the engine. `GlassesRenderer` is a façade keeping the
imperative API `DisplayController` already calls; each render builds an `Element` tree, solves it,
and presents through the partial-redraw sink.

### Font metric calibration (`GlassesTextMetrics.kt`) — high-gotcha

ROM glyphs are reproduced on the phone using the bundled `SSP-SemiBold-Spacing.otf`. The reported
font "height" H is the rasterization ppem, and the OTF's typo-ascent − descent = its em (1000
units), so setting `Paint.textSize = H` reproduces glasses glyph advances **exactly**.

> **Do NOT** derive `textSize` from Android `descent − ascent` (hhea ≈ 1257/em) — that
> under-measures
> width by ~20% and breaks centering/pagination. `WIDTH_CAL` absorbs per-glyph rounding.

`linePitch = H + LINE_GAP`. `ellipsize` uses ASCII `...` (no `…` in the ROM font).

### The ON-pixel value (all three rasterizers) — high-gotcha

`IconRasterizer`, `AndroidGlyphRasterizer`, and `StatusIcons` independently emit "on" pixels as gray
**`0xF0F0F0` (240)**, never 255. Pure white rounds up and **overflows the 4-bit nibble** to level 0
(off). 240 maps cleanly to level 15. (A shared constant would be cleaner — currently triplicated.)

### Inline shaping (`Inline.kt` / `InlineShaper.kt`)

The app's shaper is **pure and JVM-testable** (`Glyph.payload` is `Any`, no `android.graphics`). It
segments text into grapheme clusters and classifies each: ASCII → native text run; a small `PUNCT`
map transliterates *only* punctuation niceties (smart quotes, ellipsis, dashes, bullet→`*`);
**accents are deliberately NOT transliterated** — they and emoji become rasterized glyph runs. Then
word-wrap (hard-splitting over-long ASCII words), `maxLines` truncation with `...`, and `coalesce`
merges consecutive ASCII runs. Requirements pinned by `InlineShaperTest`:

- Multi-codepoint emoji stay **one glyph**: ZWJ families, flags, skin-tone, VS-16.
- Unrenderable glyph (rasterize returns null) → `?` fallback.

`AndroidGlyphRasterizer` renders accented Latin via the bundled SSP (matches ROM text), emoji via
bundled monochrome **Noto Emoji** (scaled `EMOJI_SCALE=0.7`, baseline-aligned but clamped into the
cell), other scripts via system fallback. LRU-cached (64 entries) keyed `run@fontPx`.

### Icons & status (`IconRasterizer`, `StatusIcons`, `StatusBarModel`)

- `IconRasterizer`: launcher/notification `Drawable` → small mono bitmap. Pre-thresholds to pure B/W
  (composite over black, luminance, threshold `ICON_THRESHOLD`) because the SDK's own grayscale
  reduction looks muddy.
- `StatusIcons`: rasterizes Material Symbols `ImageVector`s directly (no asset bundling, no
  Context).
  **Caching is load-bearing**: an unchanged signal/battery level returns the *same* Bitmap instance
  so the differ skips the redraw.
- `StatusBarModel`: a resolved-but-not-laid-out description handed to the screen builders, keeping
  rasterization out of the layout DSL. `BatteryViz` carries a stable diff `key`. Signal bars are
  drawn from solid primitives (thin outlines were unreadable on glasses); the "5G"/"LTE" label
  carries the rest.

### Screen builders (`HudScreens.kt`)

Pure layout; receives content **pre-shaped** as `Inline` runs. Every screen is a fixed `W×H` root
column with safe-margin padding, a status bar at top, and a `clip=true` + `translateY` content
column so animation slides clipped content. Screens: `idle` (clock), `appPresent` (icon + app-name
splash), `peek` (title + first body lines), `notifList` (paginated full list with scrollbar).

**`drawGlyphs=false` during animation**: mid-animation frames reserve glyph width with a `spacer`
instead of streaming the image, so layout doesn't jump frame-to-frame and BLE isn't flooded. Glyphs
stream only on the settled frame (`drawGlyphs = (contentYOffset == 0)`).

**Pagination math (`GlassesRenderer`) must mirror the solver exactly** — row heights, gaps, and
status-bar subtraction are reproduced so page boundaries land precisely. If you change `notifList`
layout, update the pagination math in lockstep.

---

## Display state machine (`display/DisplayController.kt`)

**One single-consumer coroutine** reads `Event`s off an `UNLIMITED` channel, so timers, gestures,
status updates, and incoming notifications are **serialized — no cross-thread state mutation**. All
public methods (`onGesture`, `onNewNotification`, `updateGlassesBattery`, `updatePhoneStatus`,
`refresh`) are thread-safe because they only `trySend`.

State graph (newest-notification always interrupts → `AppPresent`):

```
Idle ──notif──▶ AppPresent ──1.5s──▶ Peek ──5s──▶ Idle
Idle/AppPresent/Peek ──gesture──▶ NotifList(0)   (or NoNotifs if nothing posted)
NotifList(p) ──gesture──▶ NotifList(p+1);  NotifList(last) ──gesture──▶ Idle;  NotifList ──10s──▶ Idle
```

- **Timer-token pattern (high-gotcha)**: each transition cancels the prior timer, increments
  `timerToken`, and launches a delay sending `Timeout(token)`. A `Timeout` is acted on only if
  `token == timerToken` — this defeats a stale queued timeout firing after a newer transition.
- **Gesture debounce** `GESTURE_DEBOUNCE_MS` (300 ms) via `elapsedRealtime()` — the sensor
  double-fires a single physical tap.
- **`openList` reads live**: pulls currently-posted notifications via
  `NotifRepository.activeProvider`
  (a pull, not the push flow) at open time; page count comes from what the renderer can fit (≥1).
- **Clock** polls every second but emits a `ClockTick` only when the minute changes.
- **Animation slides content UP into place** (y grows upward on glasses) so it never slides up into
  the fixed status bar. Background updates (`StatusUpdate`/`ClockTick`) re-render with
  `animateIn=false` so they don't re-trigger the slide.
- Started/stopped by the service on connect/disconnect; renderer is built in `onCreate` (it loads a
  font asset needing a Context).

---

## Foreground service & BLE (`service/NotifGlassService.kt`)

A foreground service of type `connectedDevice` owns the persistent BLE link and holds a
non-ref-counted `PARTIAL_WAKE_LOCK` so the connection survives doze. `START_STICKY` (Android
restarts it if killed); an explicit Disconnect returns `START_NOT_STICKY` + `stopSelf()` so it isn't
resurrected.

- **`shouldBeConnected` is the source of truth for intent-to-connect**, kept separate from momentary
  BLE state. Reconnect logic only fires when the user actually wants a connection.
- **Fast-reconnect**: the paired `SerializedGlasses` is Java-serialized + Base64'd into DataStore;
  on reconnect the SDK connects directly (skipping a scan), falling back to a scan on any failure.
- **Auto-connect** hops to the main thread (`handler.post`) because BLE/FGS calls require it, and
  re-checks `shouldBeConnected` inside the post to avoid a double-connect race.
- **Reconnect loop** uses token-grouped `handler.postAtTime` (`RECONNECT_TOKEN`) so callbacks cancel
  as a unit; re-checks state before acting. Disconnect handling guards reference identity
  (`connectedGlasses === glasses`) so a stale callback can't clobber a newer connection.
- **`forgetGlasses`** clears the saved device and rescans — lets you switch between real glasses and
  an emulator without wiping app data.
- **`cfgSet("ALooK")` on connect** pins our config so the renderer's expected fonts/images are
  present — other ActiveLook apps (e.g. Engo) leave a different config active on the glasses.
  Re-applied when the config setting changes. `"ALooK"` is the documented default.
- **Connection-priority hack (`boostConnectionPriority`, gated by `BOOST_CONNECTION_PRIORITY`)**:
  the SDK exposes no way to set the connection interval, so the app opens a **throwaway second GATT
  ** to the same address purely to call `requestConnectionPriority(CONNECTION_PRIORITY_HIGH)`. The
  interval is a property of the shared ACL link, so the SDK's own connection inherits the faster
  interval (~50ms→~15ms), speeding redraws and avoiding "Could not write rx" retry storms.
  Best-effort; needs `BLUETOOTH_CONNECT` on API 31+. The throwaway GATT is held and closed on
  teardown.
- **ALS vs manual brightness**: when the ambient-light sensor is on it auto-controls brightness and
  overrides `luma()`. Turning ALS off must immediately re-apply the slider via `luma()`, or the
  slider appears dead.

---

## Notifications (`notif/`)

**Cross-process by design**: `NotificationListener` is a system-bound `NotificationListenerService`
running in a separate process from the service. It cannot share live memory with the service, so:

- It keeps its **own** copy of the allow-set by collecting DataStore directly — toggling an app in
  the picker takes effect with no service restart.
- It communicates with the service **only** through the `NotifRepository` singleton — never direct
  calls.

`NotifRepository` is a process-wide `object` with a `MutableSharedFlow` (`extraBufferCapacity = 16`,
`DROP_OLDEST`) so a notification burst never blocks the listener. `lastNotif` (volatile) caches the
most recent item for an idle tap; `activeProvider` (volatile) is the live-list pull callback, null
when no listener is connected.

Filtering (`mapToItem`): drops own-package, `FLAG_GROUP_SUMMARY` (avoids roll-up dupes),
`FLAG_ONGOING_EVENT` (unless `SHOW_ONGOING`), and empty title+body. Allowlist vs denylist is one
expression. **Dedup**: a tiny `key → (contentHash, timestamp)` map suppresses identical re-posts
within `DEDUP_WINDOW_MS`, self-pruning past 64 entries. The live snapshot (`snapshotActive`) reuses
the filter but **skips dedup** (point-in-time), sorts newest-first, caps at `LIST_MAX_NOTIFS`, and
guards `activeNotifications` in try/catch (it throws before the listener connects). Body extraction
priority: `EXTRA_BIG_TEXT` → `EXTRA_TEXT` → `EXTRA_TEXT_LINES`. The small (monochrome) icon is
preferred over the launcher icon — it's a silhouette ideal for mono glasses.

---

## Settings, phone status, permissions

- **`SettingsRepository`** (Preferences DataStore `"settings"`): each value a `Flow` with a
  `Const`-backed default. Notable defaults: allowlist mode (denylist off), **ALS on by default**,
  `auto_connect` on, `glasses_config` = `"ALooK"`. Timeouts are live-tunable — the `Const` values
  are only defaults, pushed to the controller via `@Volatile` fields on settings change.
- **`InstalledAppsRepository`**: launcher-resolvable activities on IO, excludes self, filters system
  apps unless requested. ⚠️ Its doc claims it also merges already-allowed packages "so a selected
  app never vanishes," but that merge is **not actually implemented** — an allowed app with no
  launcher entry won't appear in the picker.
- **`PhoneStatusProvider`**: battery always available (sticky `ACTION_BATTERY_CHANGED`); **signal
  gated on `READ_PHONE_STATE`** — without it the right side of the idle bar is blank. Uses
  `TelephonyDisplayInfo.getOverrideNetworkType` so 5G-NSA shows as 5G. `TelephonyCallback` on API
  31+, `PhoneStateListener` below. WiFi RSSI needs no phone-state permission (read from
  `ConnectivityManager`).
- **Manifest**: BLE perms split by SDK (legacy capped at 30, `BLUETOOTH_SCAN` with
  `neverForLocation`, `BLUETOOTH_CONNECT`); `FOREGROUND_SERVICE_CONNECTED_DEVICE`;
  `READ_PHONE_STATE`/`ACCESS_NETWORK_STATE` optional; `QUERY_ALL_PACKAGES` for the picker. The
  listener is `exported=false` with the notification-listener bind permission. Separate-process
  behavior is inherent to `NotificationListenerService`, not a manifest `process=` attribute.
- **UI/onboarding**: gated until both runtime perms and notification access are granted (phone-state
  is optional/skippable). Notification access can't be a runtime permission — opens
  `ACTION_NOTIFICATION_LISTENER_SETTINGS`. **Hidden debug menu**: tap the version string 7× to
  unlock the debug border, on-phone preview, glasses-config selector, and raw glasses info.
- **`GlassesPreview`**: on-phone debug mirror rendering the engine's logical `RenderCommand`s as
  yellow-on-black (mimicking the optics), using the same SSP font and drawing an opaque black cell
  behind text (the glasses' text background isn't transparent).

---

## `Const.kt` — the tuning surface

Every timing, coordinate, font size, animation, and feature flag lives here. Pixel offsets are
device-calibrated first guesses — read README "On-device fine-tuning" before nudging them. Groups:

- **Timeouts**: splash 1.5s / peek 5s / open 10s, clock refresh 1s, gesture debounce 300ms, dedup
  window 1.5s, reconnect 5s. (The three display timeouts are live-tunable via settings.)
- **Geometry / safe margins**: `SCREEN_W=304`, `SCREEN_H=256`, `CENTER_X=152`, `MARGIN_X=24`,
  `TOP/BOTTOM_MARGIN=14` — edges are unreliable overscan.
- **Layout**: status/content gaps, scrollbar, peek/list line caps (`LIST_MAX_BODY_LINES` is
  explicitly anti-DoS), `LIST_ICON_SIZE=24` (≈Small font, distinct from `ICON_SIZE=48`),
  `WIDTH_CAL=1.0` (per-glyph rounding calibration).
- **Fonts**: desired Small/Medium/Large = 24/35/49, fallback ids 0/2/3.
- **Icons**: `ICON_SIZE=48`, `ICON_THRESHOLD=128`, 4bpp heatshrink.
- **Animation**: `ANIM_FRAMES=4` (1 = instant), `ANIM_FRAME_MS=35`, `ANIM_TRAVEL=40`.
- **Feature flags**: brightness 12, show-icon, interrupt-open-on-new, animate, scrollbar,
  `BOOST_CONNECTION_PRIORITY`.
- **Differ knobs**: `MAX_DIRTY_RECTS=4`, `MERGE_SLOP=8`, `FULL_REDRAW_PCT=70`.
