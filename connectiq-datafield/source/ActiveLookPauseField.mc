import Toybox.Activity;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

//! transmit() requires a ConnectionListener; we don't act on the result (the phone has its own
//! fail-safe), so this is a no-op.
class CommListener extends Communications.ConnectionListener {
    public function initialize() { ConnectionListener.initialize(); }
    public function onComplete() as Void {}
    public function onError() as Void {}
}

//! Tell the ActiveLook Notifications phone app to release ("workout_running") or reclaim ("workout_stopped")
//! the glasses. Called on app start/stop and as a keepalive.
function sendStandby(on as Boolean) as Void {
    Communications.transmit(on ? "workout_running" : "workout_stopped", null, new CommListener());
}

//! While this data field is loaded the watch's own ActiveLook field wants the glasses, so we keep the
//! phone in standby the whole time — regardless of whether the activity timer is running, because the
//! ActiveLook field connects as soon as the activity screen loads (before the timer starts). standby
//! on/off is bracketed by the app's onStart/onStop; compute() just re-sends "workout_running" every
//! KEEPALIVE_SEC so the phone can auto-resume if the watch drops out of range without a clean stop.
class ActiveLookPauseField extends WatchUi.SimpleDataField {

    private const KEEPALIVE_SEC = 30;
    private var _ticks as Number = 0; // compute() ticks (~1 Hz) since the last keepalive

    public function initialize() {
        SimpleDataField.initialize();
        label = "Glasses notifications";
    }

    public function compute(info as Activity.Info) as Numeric or Duration or String or Null {
        _ticks++;
        if (_ticks >= KEEPALIVE_SEC) {
            _ticks = 0;
            sendStandby(true);
        }
        return "Paused";
    }
}
