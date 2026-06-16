This file provides guidance to coding agents when working with code in this repository.

## About this project

An Android app that mirrors selected notifications to [ActiveLook](https://www.activelook.net/) AR
glasses. A foreground service holds the BLE connection and renders a HUD: status bar, idle clock,
and
an animated notification flow (app splash → peek → scrollable full view) driven by the glasses' tap
gesture. Phone UI is Jetpack Compose + Material You. See `README.md` for behaviour, the coordinate
model, and on-device tuning notes. **`DESIGN.md` is the deep reference** — design decisions,
rationale, gotchas, and known stale-comment discrepancies; read it before non-trivial changes.

## Commands

```bash
./gradlew :app:assembleDebug             # build both flavors (see below)
./gradlew :app:testFossDebugUnitTest     # all JVM unit tests
./gradlew :app:testFossDebugUnitTest --tests "eu.depau.glasslayout.LayoutSolverTest"   # single class
./gradlew :app:installFossDebug          # install on a connected device
```

Two product flavors (dimension `license`): **foss** (default, no proprietary deps) and **nonfree**
(adds the Garmin ConnectIQ Mobile SDK for auto-pause during Garmin workouts). Flavor-specific code
lives in `src/foss/` and `src/nonfree/` (see `service/GarminBridge.kt`); use `…NonfreeDebug…` task
variants for the nonfree build. The watch-side Connect IQ data field is a separate Monkey C project
under `connectiq-datafield/`.

BLE and the notification listener **do not work on the emulator** — runtime testing needs a physical
device with the glasses. The ActiveLook SDK comes from JitPack (
`com.github.activelook:android-sdk`),
already declared. Unit tests run on the JVM and cover the layout engine only (no Android device).

## Architecture

Two layers in one Gradle module, under separate packages:

### `eu.depau.glasslayout` — the layout engine (pure, JVM-testable)

A Clay-inspired flexbox layout pipeline that knows nothing about ActiveLook or Android, so it can
be tested entirely on the JVM without a physical device or emulator. It works around the glasses'
built-in layout limitations, such as the lack of horizontal centering.

- `core/dsl/Dsl.kt` — declarative `column`/`row`/`box`/`text`/`image` builder DSL.
- `core/model/` — the element tree (`Container`, `TextEl`, sizing: `Fit`/`Grow`/`Fixed`/`Percent`).
- `core/layout/LayoutSolver.kt` — five-pass solver (fit-widths → grow widths → wrap+fit-heights →
  grow heights → position+emit) producing positioned `RenderCommand`s in **logical** coordinates,
  given a `TextMeasurer`.
- `core/diff/Differ.kt` — diffs successive command lists so only changed primitives are redrawn.
- `core/text/` + `core/render/RenderSink.kt` — abstract text measurement and the draw-command sink.
- `activelook/` — the backend adapter: `DeviceTransform` maps logical coords to the glasses'
  bottom-left origin / 304×256 space, `FontResolver` picks the closest firmware ROM font per size,
  `ActiveLookSink` emits the actual SDK draw calls.

To change *what* the HUD shows, edit the screen builders (`glasses/HudScreens.kt`). To change *how*
layout/text/coordinates behave, work in `glasslayout`.

### `eu.depau.activelooknotifications` — the app

- `service/NotifGlassService.kt` — foreground service: BLE connect, fast-reconnect, wiring.
- `display/DisplayController.kt` — the HUD state machine: a single-consumer coroutine reading a
  `Channel` of events (timers, tap gestures, new notifications), so they never race.
- `glasses/` — screen builders + Android-backed glyph rasterization/measurement bridging into the
  engine; `IconRasterizer` turns app icons into monochrome bitmaps.
- `notif/` — `NotificationListener` (a `NotificationListenerService`) + `NotifRepository` singleton.
- `data/` — DataStore settings + installed-app list. `phone/` — battery & signal. `ui/` — Compose.

**Cross-process detail:** the notification listener and the service are separate process components.
They communicate only through the `NotifRepository` singleton's `SharedFlow` — not direct calls.

## Things to know before editing

- All timings, coordinates, fonts and feature flags live in `Const.kt`. Pixel offsets there are
  tuned
  on-device; see README "On-device fine-tuning" before nudging them.
- Glasses coordinate space: origin bottom-left, x 0→304 (152 = center), y 0→256; `txt(...)` anchors
  the text's top-left and glyphs hang downward, so body lines descend with *decreasing* y. Image and
  rect primitives anchor *different* corners — see `DeviceTransform` / DESIGN.md before touching it.
- The service calls `cfgSet("ALooK")` on connect to pin our config — other ActiveLook apps (e.g.
  Engo) leave a different config active, so this guarantees the renderer's expected fonts are
  present.
- The three rasterizers emit "on" pixels as `0xF0F0F0` (240), not white — 255 overflows the 4-bit
  nibble to off. And font `textSize` must equal the ROM ppem, not be derived from Android ascent.

## Rules

- Whenever you make changes that invalidate what is stated AGENTS.md/CLAUDE.md, in DESIGN.md,
  README.md, comments or other kinds of documentation, you must always update them as well.
    - Note: `CLAUDE.md` is a symlink to `AGENTS.md`; there is no need to read or edit both.
- While surgical changes can be useful, whenever you see opportunities to clean things up by
  performing a refactor, ask the user for their preference. In general, a refactor is to be
  preferred when the alternatives introduce more technical debt.
- Whenever you make changes that result in a piece of code to no longer have any uses, remove it. We
  use Git for version control, removed pieces of code are still available in the repository for
  reintroduction.
- When modifying code for which tests exist, be sure to add or update tests as long as they are
  useful to prevent regressions. If you see high reward testing opportunities, ask the user if they
  want to add them.
