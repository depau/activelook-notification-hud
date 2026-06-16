package eu.depau.activelooknotifications.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import eu.depau.activelooknotifications.Const
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed app settings. All values are exposed as [Flow]s with constant-backed defaults,
 * plus suspend setters. The allow-set drives which packages are mirrored to the glasses.
 */
class SettingsRepository(private val context: Context) {

    private val ds get() = context.dataStore

    val allowedPackages: Flow<Set<String>> = ds.data.map { it[KEY_ALLOWED] ?: emptySet() }

    /** false = allowlist (mirror only listed apps); true = denylist (mirror all except listed). */
    val denylistMode: Flow<Boolean> = ds.data.map { it[KEY_DENYLIST] ?: false }

    /** Glasses ambient-light sensor (auto-brightness). When on, the brightness slider has no effect. */
    val ambientLightSensor: Flow<Boolean> = ds.data.map { it[KEY_ALS] ?: true }

    val appNameDurationMs: Flow<Long> = ds.data.map { it[KEY_APP_NAME_MS] ?: Const.APP_NAME_DURATION_MS }
    val peekTimeoutMs: Flow<Long> = ds.data.map { it[KEY_PEEK_MS] ?: Const.PEEK_TIMEOUT_MS }
    val openTimeoutMs: Flow<Long> = ds.data.map { it[KEY_OPEN_MS] ?: Const.OPEN_TIMEOUT_MS }
    val brightness: Flow<Int> = ds.data.map { it[KEY_BRIGHTNESS] ?: Const.DEFAULT_BRIGHTNESS }
    val showIcon: Flow<Boolean> = ds.data.map { it[KEY_SHOW_ICON] ?: Const.SHOW_ICON }
    val animateTransitions: Flow<Boolean> = ds.data.map { it[KEY_ANIMATE] ?: Const.ANIMATE_TRANSITIONS }
    val autoStartOnBoot: Flow<Boolean> = ds.data.map { it[KEY_AUTO_START_ON_BOOT] ?: true }
    val debugScreenBorder: Flow<Boolean> = ds.data.map { it[KEY_DEBUG_BORDER] ?: false }
    val hideMinimized: Flow<Boolean> = ds.data.map { it[KEY_HIDE_MINIMIZED] ?: true }

    /** Allow external apps (Tasker etc.) to toggle "pause for workout" via broadcast. */
    val allowExternalStandby: Flow<Boolean> = ds.data.map { it[KEY_ALLOW_EXTERNAL_STANDBY] ?: false }

    /** Glasses configuration to activate on connect (cfgSet). "ALooK" is the documented default. */
    val glassesConfig: Flow<String> = ds.data.map { it[KEY_GLASSES_CONFIG] ?: "ALooK" }

    /** Debug: show an on-phone preview of the rendered glasses screen on the home page. */
    val debugPreview: Flow<Boolean> = ds.data.map { it[KEY_DEBUG_PREVIEW] ?: false }
    val serializedGlasses: Flow<String> = ds.data.map { it[KEY_SERIALIZED_GLASSES] ?: "" }

    suspend fun setPackageAllowed(pkg: String, allowed: Boolean) {
        ds.edit { prefs ->
            val current = prefs[KEY_ALLOWED] ?: emptySet()
            prefs[KEY_ALLOWED] = if (allowed) current + pkg else current - pkg
        }
    }

    suspend fun setDenylistMode(value: Boolean) = ds.edit { it[KEY_DENYLIST] = value }
    suspend fun setAmbientLightSensor(value: Boolean) = ds.edit { it[KEY_ALS] = value }
    suspend fun setAppNameDurationMs(value: Long) = ds.edit { it[KEY_APP_NAME_MS] = value }
    suspend fun setPeekTimeoutMs(value: Long) = ds.edit { it[KEY_PEEK_MS] = value }
    suspend fun setOpenTimeoutMs(value: Long) = ds.edit { it[KEY_OPEN_MS] = value }
    suspend fun setBrightness(value: Int) = ds.edit { it[KEY_BRIGHTNESS] = value }
    suspend fun setShowIcon(value: Boolean) = ds.edit { it[KEY_SHOW_ICON] = value }
    suspend fun setAnimateTransitions(value: Boolean) = ds.edit { it[KEY_ANIMATE] = value }
    suspend fun setAutoStartOnBoot(value: Boolean) = ds.edit { it[KEY_AUTO_START_ON_BOOT] = value }
    suspend fun setDebugScreenBorder(value: Boolean) = ds.edit { it[KEY_DEBUG_BORDER] = value }
    suspend fun setGlassesConfig(value: String) = ds.edit { it[KEY_GLASSES_CONFIG] = value }
    suspend fun setDebugPreview(value: Boolean) = ds.edit { it[KEY_DEBUG_PREVIEW] = value }
    suspend fun setSerializedGlasses(value: String) = ds.edit { it[KEY_SERIALIZED_GLASSES] = value }
    suspend fun setHideMinimized(value: Boolean) = ds.edit { it[KEY_HIDE_MINIMIZED] = value }
    suspend fun setAllowExternalStandby(value: Boolean) = ds.edit { it[KEY_ALLOW_EXTERNAL_STANDBY] = value }

    companion object {
        private val KEY_ALLOWED = stringSetPreferencesKey("allowed_packages")
        private val KEY_DENYLIST = booleanPreferencesKey("denylist_mode")
        private val KEY_ALS = booleanPreferencesKey("ambient_light_sensor")
        private val KEY_APP_NAME_MS = longPreferencesKey("app_name_duration_ms")
        private val KEY_PEEK_MS = longPreferencesKey("peek_timeout_ms")
        private val KEY_OPEN_MS = longPreferencesKey("open_timeout_ms")
        private val KEY_BRIGHTNESS = intPreferencesKey("brightness")
        private val KEY_SHOW_ICON = booleanPreferencesKey("show_icon")
        private val KEY_ANIMATE = booleanPreferencesKey("animate_transitions")
        private val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        private val KEY_DEBUG_BORDER = booleanPreferencesKey("debug_screen_border")
        private val KEY_GLASSES_CONFIG = stringPreferencesKey("glasses_config")
        private val KEY_DEBUG_PREVIEW = booleanPreferencesKey("debug_preview")
        private val KEY_SERIALIZED_GLASSES = stringPreferencesKey("serialized_glasses")
        private val KEY_HIDE_MINIMIZED = booleanPreferencesKey("hide_minimized")
        private val KEY_ALLOW_EXTERNAL_STANDBY = booleanPreferencesKey("allow_external_standby")
    }
}
