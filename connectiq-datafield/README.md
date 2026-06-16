# ActiveLook Pause — Connect IQ data field

A tiny Garmin Connect IQ **data field** that frees the ActiveLook glasses for the watch while a
Garmin activity is recording. Add it to the same activities you've added the Engo/ActiveLook data
field to.

## How it works

The field keeps the phone in standby for the **whole time it's loaded on an activity** — not just
while the timer runs — because the watch's own ActiveLook field grabs the glasses as soon as the
activity screen opens, before you press start.

- app `onStart` (field loaded) → `Communications.transmit("workout_running")`
- app `onStop` (activity ended) → `Communications.transmit("workout_stopped")`
- `compute()` re-sends `"workout_running"` every 30 s as a keepalive

Garmin Connect Mobile relays the message to the **ActiveLook Notifications** phone app (the `nonfree`
build flavor), which enters/exits standby — releasing or reclaiming the glasses. The keepalive lets
the phone auto-resume if the watch drops out of BLE range (or is killed) without a clean `onStop`.

App UUID: `BFD5D3459FD642ECA9DE0A05E91CD16D` — the phone app registers the same UUID. Keep them in
sync if you regenerate it.

## Requirements

- Connect IQ SDK 9.x (`monkeyc` on `PATH`, or the VS Code *Monkey C* extension).
- A watch at **CIQ System 5 / firmware API ≥ 5.0.0** — `Communications` in a foreground data field
  needs API 5.0.0. The manifest lists every datafield-capable device at API ≥ 5.0.0; trim
  `<iq:products>` to your watch to shrink the build if you want.

## Build & sideload

```bash
# One-time developer key (any RSA key in PKCS#8 DER works):
openssl genrsa -out developer_key.pem 4096
openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key -nocrypt

# Build a sideloadable .iq for your device (e.g. fr955):
monkeyc -f monkey.jungle -o ActiveLookPause.iq -y developer_key -d fr955

# Or run in the simulator + ADB-tether a phone companion to test transmit().
```

Install the `.iq` via Garmin Express / the store upload, then add the field to an activity's data
screens.
