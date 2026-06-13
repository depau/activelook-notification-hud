package eu.depau.activelooknotifications.phone

/** Network kind shown on the right of the idle status bar. */
enum class NetworkType(val label: String) {
    NONE(""),
    GPRS("G"),
    EDGE("E"),
    THREE_G("3G"),
    HSPA("H"),
    LTE("LTE"),
    FIVE_G("5G"),
    WIFI("WiFi"),
}

/** Optional cellular signal info; null when READ_PHONE_STATE is not granted. */
data class SignalInfo(
    val networkType: NetworkType,
    /** 0..4 bars. */
    val bars: Int,
)

/** Everything the status bar needs, gathered from the phone + glasses. */
data class StatusInfo(
    val glassesBattery: Int?,
    val phoneBattery: Int,
    val time: String,
    val date: String,
    val signal: SignalInfo?,
)
