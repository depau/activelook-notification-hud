import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

//! Data field whose only job is to tell the ActiveLook Notifications phone app to release the
//! glasses while this activity is loaded. onStart/onStop bracket the standby; the field keeps it
//! alive (see ActiveLookPauseField).
class ActiveLookPauseApp extends Application.AppBase {

    public function initialize() {
        AppBase.initialize();
    }

    //! Activity loaded the field → release the glasses for the watch's ActiveLook field.
    public function onStart(state as Dictionary?) as Void {
        $.sendStandby(true);
    }

    //! Activity ended → hand the glasses back to the phone.
    public function onStop(state as Dictionary?) as Void {
        $.sendStandby(false);
    }

    public function getInitialView() as [Views] or [Views, InputDelegates] {
        return [ new $.ActiveLookPauseField() ];
    }
}
