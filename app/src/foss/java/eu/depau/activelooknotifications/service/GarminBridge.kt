package eu.depau.activelooknotifications.service

/**
 * FOSS flavor: no Garmin/proprietary dependency, so this is a no-op. The real implementation lives
 * in `src/nonfree/` and listens for the ActiveLook Pause data field's messages via the ConnectIQ
 * Mobile SDK. [SUPPORTED] is false here so the UI hides the auto-pause setting.
 */
@Suppress("UNUSED_PARAMETER")
class GarminBridge(service: NotifGlassService) {
    fun start() {}
    fun stop() {}

    companion object {
        const val SUPPORTED = false
    }
}
