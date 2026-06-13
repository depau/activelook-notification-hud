package eu.depau.activelooknotifications.display

import android.os.SystemClock
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.glasses.GlassesRenderer
import eu.depau.activelooknotifications.phone.SignalInfo
import eu.depau.activelooknotifications.phone.StatusInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Drives the glasses display state machine. All state lives in a single consumer coroutine that
 * reads [Event]s off a channel, so timers, gestures, status updates and incoming notifications are
 * serialized — they never mutate state from multiple threads.
 *
 * State graph (newest-wins; tap advances, tap-at-end closes):
 *   Idle ──notif──▶ AppPresent ──1.5s──▶ Peek ──5s──▶ Idle
 *   Idle ──tap(lastNotif)──▶ Open(0)
 *   AppPresent/Peek ──tap──▶ Open(0)
 *   Open ──tap(more)──▶ Open(+page)   Open ──tap(end)──▶ Idle   Open ──10s──▶ Idle
 *   any ──new notif──▶ AppPresent
 */
class DisplayController(
    private val renderer: GlassesRenderer,
    private val scope: CoroutineScope,
) {
    // Live-tunable timeouts (the service pushes DataStore values here).
    @Volatile var appNameDurationMs = Const.APP_NAME_DURATION_MS
    @Volatile var peekTimeoutMs = Const.PEEK_TIMEOUT_MS
    @Volatile var openTimeoutMs = Const.OPEN_TIMEOUT_MS
    @Volatile var animate = Const.ANIMATE_TRANSITIONS

    private val _state = MutableStateFlow<DisplayState>(DisplayState.Idle)
    val state: StateFlow<DisplayState> = _state

    private val events = Channel<Event>(Channel.UNLIMITED)

    private var loopJob: Job? = null
    private var clockJob: Job? = null
    private var timerJob: Job? = null
    private var timerToken = 0L

    private var lastNotif: NotifItem? = null
    private var openLines: List<String> = emptyList()
    private var lastGestureAt = 0L

    // Status pieces gathered from the phone/glasses; time is computed fresh at render.
    @Volatile private var glassesBattery: Int? = null
    @Volatile private var phoneBattery: Int = 0
    @Volatile private var signal: SignalInfo? = null

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE MMM d", Locale.getDefault())

    private sealed interface Event {
        data class NewNotif(val item: NotifItem) : Event
        data object Gesture : Event
        data class Timeout(val token: Long) : Event
        data object StatusUpdate : Event
        data object ClockTick : Event
    }

    // --- Public API (thread-safe; just enqueues events) ---

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
        clockJob = scope.launch { runClock() }
        // Initial render.
        scope.launch { transitionTo(DisplayState.Idle) }
    }

    fun stop() {
        timerJob?.cancel()
        clockJob?.cancel()
        loopJob?.cancel()
        loopJob = null
        clockJob = null
    }

    fun onNewNotification(item: NotifItem) {
        events.trySend(Event.NewNotif(item))
    }

    fun onGesture() {
        events.trySend(Event.Gesture)
    }

    /** Repaint the current state (no animation) — e.g. after a debug/layout toggle changes. */
    fun refresh() {
        events.trySend(Event.StatusUpdate)
    }

    fun updateGlassesBattery(level: Int) {
        glassesBattery = level
        events.trySend(Event.StatusUpdate)
    }

    fun updatePhoneStatus(battery: Int, signal: SignalInfo?) {
        phoneBattery = battery
        this.signal = signal
        events.trySend(Event.StatusUpdate)
    }

    // --- Loop ---

    private suspend fun runLoop() {
        for (event in events) {
            when (event) {
                is Event.NewNotif -> {
                    lastNotif = event.item
                    openLines = emptyList()
                    transitionTo(DisplayState.AppPresent(event.item))
                }

                Event.Gesture -> handleGesture()

                is Event.Timeout -> if (event.token == timerToken) onTimeout()

                Event.StatusUpdate -> render(_state.value, animateIn = false)

                Event.ClockTick -> render(_state.value, animateIn = false)
            }
        }
    }

    private suspend fun runClock() {
        var lastMinute = -1
        while (scope.isActive) {
            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            if (minute != lastMinute) {
                lastMinute = minute
                events.trySend(Event.ClockTick)
            }
            delay(Const.CLOCK_REFRESH_MS)
        }
    }

    private suspend fun handleGesture() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < Const.GESTURE_DEBOUNCE_MS) return
        lastGestureAt = now

        when (val s = _state.value) {
            DisplayState.Idle -> lastNotif?.let { openNotif(it, 0) }
            is DisplayState.AppPresent -> openNotif(s.notif, 0)
            is DisplayState.Peek -> openNotif(s.notif, 0)
            is DisplayState.Open -> {
                val page = renderer.visibleBodyLines()
                val nextOffset = s.offset + page
                if (nextOffset >= openLines.size) {
                    transitionTo(DisplayState.Idle)
                } else {
                    transitionTo(DisplayState.Open(s.notif, nextOffset))
                }
            }
        }
    }

    private suspend fun openNotif(notif: NotifItem, offset: Int) {
        openLines = renderer.wrapBody(sanitizeBody(notif.body))
        transitionTo(DisplayState.Open(notif, offset))
    }

    private fun onTimeout() {
        when (val s = _state.value) {
            is DisplayState.AppPresent -> scope.launch { transitionTo(DisplayState.Peek(s.notif)) }
            is DisplayState.Peek -> scope.launch { transitionTo(DisplayState.Idle) }
            is DisplayState.Open -> scope.launch { transitionTo(DisplayState.Idle) }
            DisplayState.Idle -> {}
        }
    }

    private suspend fun transitionTo(state: DisplayState) {
        timerJob?.cancel()
        timerToken++
        val token = timerToken
        _state.value = state
        render(state)

        val timeout = when (state) {
            is DisplayState.AppPresent -> appNameDurationMs
            is DisplayState.Peek -> peekTimeoutMs
            is DisplayState.Open -> openTimeoutMs
            DisplayState.Idle -> null
        }
        timerJob = timeout?.let {
            scope.launch {
                delay(it)
                events.trySend(Event.Timeout(token))
            }
        }
    }

    /**
     * Render [state], animating content into place when [animate] is on. Content slides UP from a
     * small offset below its final position (contentYOffset goes from -ANIM_TRAVEL → 0). Since y
     * grows upward, a negative offset starts the content lower and it rises to rest. The status bar
     * never moves; for the Open view only the body slides (the title is fixed).
     */
    private suspend fun render(state: DisplayState, animateIn: Boolean = true) {
        val status = currentStatus()

        val frames = if (animateIn && animate && Const.ANIM_FRAMES > 1) Const.ANIM_FRAMES else 1
        for (f in 1..frames) {
            // Positive = content starts BELOW its rest position and rises into place, so it never
            // slides up into the status bar (which sits above the clipped content region).
            val offset = Const.ANIM_TRAVEL * (frames - f) / frames
            when (state) {
                DisplayState.Idle -> renderer.renderIdle(status, offset)
                is DisplayState.AppPresent -> renderer.renderAppPresent(state.notif, state.notif.iconBitmap, status, offset)
                is DisplayState.Peek -> renderer.renderPeek(state.notif, status, offset)
                is DisplayState.Open -> renderer.renderOpen(state.notif, openLines, state.offset, status, offset)
            }
            if (f < frames) delay(Const.ANIM_FRAME_MS)
        }
    }

    private fun currentStatus(): StatusInfo {
        val now = Date()
        return StatusInfo(
            glassesBattery = glassesBattery,
            phoneBattery = phoneBattery,
            time = timeFmt.format(now),
            date = dateFmt.format(now),
            signal = signal,
        )
    }

    private fun sanitizeBody(body: String): String =
        body.replace("\r\n", "\n").replace('\r', '\n')
}
