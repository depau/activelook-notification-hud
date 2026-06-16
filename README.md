# Notification HUD for ActiveLook

Mirrors selected Android notifications to [ActiveLook](https://www.activelook.net/) AR glasses.

A foreground service connects to the glasses and renders a small heads-up display: a battery/time
status bar, an idle clock, and an animated notification flow (app splash → peek → scrollable full
view) driven by the glasses' tap gesture. The phone UI is Jetpack Compose with Material You.

## Behaviour

- **New notification** → app icon + name splash (1.5 s) → title + first body line (peek, 5 s) → idle.
- **Tap gesture** while idle opens the most recent notification (10 s). Each further tap pages the
  body down; a tap past the end closes it. Newest notification always wins (interrupts the current).
- **Status bar**: glasses + phone battery on the left; the time while a notification is showing,
  otherwise mobile signal + network type (only if `READ_PHONE_STATE` is granted).
- **Idle**: a larger centered clock below the status bar.
- The foreground-service notification has a **Disconnect** button to free the glasses for a watch.
- **Pause for workout**: the glasses lock to whoever connects first, so to let a Garmin watch's
  ActiveLook app drive them this app must release the BLE link and stop reconnecting (passive
  back-off can't help — and there's no way to *detect* a workout from the phone). Toggle it manually
  from the home-screen button or the foreground-service notification; "Resume" reconnects. See
  [Automation](#automation) to drive it from an external app.

All timings, coordinates, fonts and feature flags live in
[`Const.kt`](app/src/main/java/eu/depau/activelooknotifications/Const.kt).

## Architecture

| Area | File |
|------|------|
| Foreground service, BLE connection, fast-reconnect, wiring | `service/NotifGlassService.kt` |
| Display state machine (actor loop, timers, gestures) | `display/DisplayController.kt` |
| Glasses drawing (`holdFlush` batches, `shift` animation) | `glasses/GlassesRenderer.kt` |
| Layout math (centering, wrapping, width estimation) | `glasses/GlassesLayout.kt` |
| Icon → monochrome bitmap | `glasses/IconRasterizer.kt` |
| Notification listener + filtering/dedup | `notif/NotificationListener.kt`, `notif/NotifRepository.kt` |
| Settings (DataStore) + installed-app list | `data/` |
| Phone battery + signal | `phone/PhoneStatusProvider.kt` |
| Compose UI | `MainActivity.kt`, `ui/` |

The notification listener and the service are separate process components; they communicate through
the `NotifRepository` singleton `SharedFlow`. The display state machine is a single-consumer
coroutine reading a `Channel` of events, so timers, gestures and notifications never race.

## Glasses coordinate model (from the ActiveLook API docs)

- Origin bottom-left; **x** 0→304 left→right (152 = center), **y** 0→256 bottom→top.
- `txt(x, y, TOP_LR, …)` anchors the **top-left** of the text; glyphs flow right and hang
  **downward**. So the status row sits at y≈252 and body lines descend with *decreasing* y.
- The 4 firmware ROM fonts are full-ASCII: id0 h24, id1 h24, id2 h35, id3 h49. The app reads the
  real list on connect (`fontList`) and picks the closest match per desired size, so it adapts to
  whatever fonts the device reports. The app calls **`cfgSet("ALooK")` on connect** — this is
  necessary because other ActiveLook apps (e.g. Engo) overwrite the device config with their own
  fonts, so pinning our config guarantees the fonts the renderer expects are present.

## On-device fine-tuning

Constants in `Const.kt` are now grounded in the docs, but a few pixel offsets may still want nudging:

- **Y offsets** — `STATUS_BAR_Y`, `IDLE_CLOCK_Y`, `PEEK_*`, `OPEN_*`, `BODY_TOP_Y`.
- **Centering** — `Const.WIDTH_BIAS` over-estimates text width slightly so centered text never clips
  off the right; raise it if text still clips, lower it if it drifts left.
- **Image anchor** — `APP_ICON_TOP_Y` assumes the image (x,y) is its bottom-left; adjust if the
  splash icon sits wrong.
- **Animation** — `ANIM_FRAMES`/`ANIM_FRAME_MS`/`ANIM_TRAVEL`; set `ANIM_FRAMES=1` for instant.
- **Icons** — uses the notification's monochrome small icon; raise `ICON_THRESHOLD` or turn off
  "Show app icon" in Settings if any look muddy.

## Settings

- **Apps to mirror** — allowlist ("only selected") or denylist ("all except selected").
- **Auto-brightness (ALS)** — when on, the glasses set brightness from ambient light and the
  brightness slider is ignored; turn it off to use the slider.
- Timeouts (app splash / peek / open), brightness, show-icon, animate, auto-connect, start-on-boot.
- Mobile signal + network type on the idle screen only appears if `READ_PHONE_STATE` is granted
  (grant it from Settings; otherwise the right side of the idle bar is blank).

## Automation

"Pause for workout" can be toggled by an **explicit broadcast** to `StandbyReceiver`, so an
automation app (Tasker, MacroDroid) can engage it when an activity starts and release it when it
ends. Send one of these actions to package `eu.depau.activelooknotifications`:

- `eu.depau.activelooknotifications.action.STANDBY_ON` — release the glasses
- `eu.depau.activelooknotifications.action.STANDBY_OFF` — reconnect
- `eu.depau.activelooknotifications.action.STANDBY_TOGGLE`

(In Tasker: *Send Intent* → Action = one of the above, Target = Broadcast Receiver, Package set.)
The HUD must be running (foreground service active), and **"Allow other apps to pause"** must be
enabled in Settings (off by default), for the command to take effect.

**Garmin Connect IQ path (not built — a separate project):** since no API or notification exposes
"activity ongoing" to the phone, the only fully-automatic option is a Garmin **watch app/widget**
(not a plain *data field* — those are too sandboxed to use `Communications.transmit` reliably) that,
via the free **Connect IQ Mobile SDK**, messages this app on activity start/stop to fire the standby
broadcast above. That means a Monkey C watch project plus integrating the ConnectIQ Android library
and a shared app UUID — meaningful effort, hence left as a future option.

## Build

```
./gradlew :app:assembleDebug
```

Requires the ActiveLook SDK from JitPack (already declared) and a physical device — BLE and the
notification listener don't work on the emulator.
