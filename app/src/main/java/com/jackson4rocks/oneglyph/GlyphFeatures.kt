package com.jackson4rocks.oneglyph

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.audiofx.Visualizer
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class GlyphStep(
    val brightness: Int,
    val durationMs: Long
)

enum class OneGlyphTheme {
    DARK,
    LIGHT
}

data class MediaPlaybackInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

object GlyphPatterns {
    val blink = listOf(
        GlyphStep(4095, 190),
        GlyphStep(0, 190)
    )

    val doubleBlink = listOf(
        GlyphStep(4095, 150),
        GlyphStep(0, 130),
        GlyphStep(4095, 150),
        GlyphStep(0, 220)
    )

    val heartbeat = listOf(
        GlyphStep(4095, 100),
        GlyphStep(0, 90),
        GlyphStep(4095, 180),
        GlyphStep(0, 650)
    )

    val slowPulse = listOf(
        GlyphStep(2300, 500),
        GlyphStep(0, 420)
    )

    fun repeated(
        onMs: Long,
        offMs: Long,
        count: Int,
        brightness: Int = 4095
    ): List<GlyphStep> {
        val result = ArrayList<GlyphStep>(count * 2)

        repeat(count.coerceIn(1, 16)) {
            result += GlyphStep(
                brightness.coerceIn(0, 4095),
                onMs.coerceAtLeast(35L)
            )
            result += GlyphStep(
                0,
                offMs.coerceAtLeast(35L)
            )
        }

        return result
    }
}

class PatternStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "oneglyph_settings",
            Context.MODE_PRIVATE
        )

    fun appEnabled(): Boolean =
        prefs.getBoolean("app_enabled", true)

    fun setAppEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("app_enabled", enabled)
            .apply()
    }

    fun theme(): OneGlyphTheme =
        when (prefs.getString("theme", "DARK")) {
            "LIGHT" -> OneGlyphTheme.LIGHT
            else -> OneGlyphTheme.DARK
        }

    fun setTheme(theme: OneGlyphTheme) {
        prefs.edit()
            .putString("theme", theme.name)
            .apply()
    }

    fun backgroundMode(): String =
        prefs.getString(
            "background_mode",
            "none"
        ).orEmpty().ifBlank {
            "none"
        }

    fun setBackgroundMode(
        mode: String
    ) {
        prefs.edit()
            .putString(
                "background_mode",
                mode
            )
            .commit()
    }

    fun chargingEnabled(): Boolean =
        prefs.getBoolean("charging_enabled", true)

    fun setChargingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("charging_enabled", enabled)
            .apply()
    }

    fun lowBatteryAlertEnabled(): Boolean =
        prefs.getBoolean("low_battery_alert", true)

    fun setLowBatteryAlertEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("low_battery_alert", enabled)
            .apply()
    }

    fun notificationFlashEnabled(): Boolean =
        prefs.getBoolean("notification_flash", true)

    fun setNotificationFlashEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("notification_flash", enabled)
            .apply()
    }

    fun metronomeBpm(): Int =
        prefs.getInt("metronome_bpm", 96)
            .coerceIn(30, 240)

    fun setMetronomeBpm(bpm: Int) {
        prefs.edit()
            .putInt(
                "metronome_bpm",
                bpm.coerceIn(30, 240)
            )
            .apply()
    }

    fun chargeTarget(): Int =
        prefs.getInt("charge_target", 80)
            .coerceIn(80, 100)

    fun setChargeTarget(percent: Int) {
        prefs.edit()
            .putInt(
                "charge_target",
                percent.coerceIn(80, 100)
            )
            .apply()
    }

    fun cameraSeconds(): Int =
        prefs.getInt("camera_seconds", 5)
            .coerceIn(3, 10)

    fun setCameraSeconds(seconds: Int) {
        prefs.edit()
            .putInt(
                "camera_seconds",
                seconds.coerceIn(3, 10)
            )
            .apply()
    }

    fun loadComposer(): MutableList<GlyphStep> {
        val raw = prefs.getString(
            "composer",
            null
        )

        if (raw.isNullOrBlank()) {
            return mutableListOf(
                GlyphStep(4095, 170),
                GlyphStep(0, 150),
                GlyphStep(2600, 350),
                GlyphStep(0, 250)
            )
        }

        val parsed =
            raw.split("|").mapNotNull { item ->
                val parts = item.split(",")
                if (parts.size != 2) {
                    return@mapNotNull null
                }

                val brightness =
                    parts[0].toIntOrNull()
                        ?: return@mapNotNull null

                val duration =
                    parts[1].toLongOrNull()
                        ?: return@mapNotNull null

                GlyphStep(
                    brightness.coerceIn(
                        0,
                        4095
                    ),
                    duration.coerceIn(
                        40,
                        3000
                    )
                )
            }

        return if (parsed.isEmpty()) {
            mutableListOf(
                GlyphStep(4095, 180)
            )
        } else {
            parsed.take(8).toMutableList()
        }
    }

    fun saveComposer(
        steps: List<GlyphStep>
    ) {
        prefs.edit()
            .putString(
                "composer",
                steps.joinToString("|") {
                    it.brightness.toString() +
                        "," +
                        it.durationMs
                }
            )
            .apply()
    }
}

class GlyphMediaSessionService :
    NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        // NotificationListenerService is system-bound. Keeping the background
        // process hosted by this service gives Nothing OS a system binding to
        // the same process that owns our persistent foreground Glyph service.
        android.util.Log.d(
            "OneGlyph",
            "Media Access connected; anchoring background process."
        )

        if (
            PatternStore(this).appEnabled()
        ) {
            GlyphBackgroundService.ensureRunning(
                this
            )
        }
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val notification = sbn ?: return
        // Never flash for ongoing notifications (media players, the
        // persistent Glyph service notification itself) or our own.
        if (notification.isOngoing) return
        if (notification.packageName == packageName) return

        val store = PatternStore(this)
        if (!store.appEnabled() || !store.notificationFlashEnabled()) return

        // Never steal the dot from an active persistent mode
        // (Toy, Music Sync, Metronome): a second concurrent Glyph
        // session would stomp its frames and tear down its session.
        if (GlyphBackgroundService.currentMode(this) != "none") return

        // Debounce: one flash per burst so a chatty app doesn't
        // strobe the dot.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNotifFlashMs < 4_000L) return
        lastNotifFlashMs = now

        notifScope.launch {
            GlyphAction.playOnce(
                this@GlyphMediaSessionService,
                GlyphPatterns.repeated(
                    onMs = 160,
                    offMs = 160,
                    count = 2
                )
            )
        }
    }

    override fun onListenerDisconnected() {
        android.util.Log.w(
            "OneGlyph",
            "Media Access disconnected."
        )

        super.onListenerDisconnected()
    }

    companion object {
        @Volatile
        private var lastNotifFlashMs = 0L

        private val notifScope =
            CoroutineScope(
                Dispatchers.IO
            )
    }
}

class MediaPlaybackWatcher(
    context: Context,
    private val onChanged:
        (MediaPlaybackInfo?) -> Unit,
    private val onAccessError:
        () -> Unit
) : AutoCloseable {
    private val appContext =
        context.applicationContext

    private val manager =
        appContext.getSystemService(
            MediaSessionManager::class.java
        )

    private val handler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )

    private var current:
        MediaController? = null

    private val component =
        ComponentName(
            appContext,
            GlyphMediaSessionService::class.java
        )

    private val ticker =
        object : Runnable {
            override fun run() {
                publish()
                handler.postDelayed(
                    this,
                    350L
                )
            }
        }

    private val sessionsListener =
        MediaSessionManager
            .OnActiveSessionsChangedListener { sessions ->
                select(
                    sessions
                )
            }

    private val callback =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(
                state: PlaybackState?
            ) {
                publish()
            }

            override fun onMetadataChanged(
                metadata: MediaMetadata?
            ) {
                publish()
            }

            override fun onSessionDestroyed() {
                current = null
                onChanged(null)
                refresh()
            }
        }

    fun start() {
        try {
            manager
                .addOnActiveSessionsChangedListener(
                    sessionsListener,
                    component,
                    handler
                )

            refresh()
            handler.post(ticker)
        } catch (_: SecurityException) {
            onAccessError()
        } catch (_: Throwable) {
            onAccessError()
        }
    }

    fun refresh() {
        try {
            select(
                manager.getActiveSessions(
                    component
                )
            )
        } catch (_: SecurityException) {
            onAccessError()
        } catch (_: Throwable) {
            onChanged(null)
        }
    }

    private fun select(
        sessions:
            List<MediaController>?
    ) {
        val preferred =
            sessions.orEmpty().firstOrNull {
                it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
            } ?: sessions.orEmpty().firstOrNull {
                it.metadata != null
            }

        if (preferred == current) {
            publish()
            return
        }

        current?.unregisterCallback(
            callback
        )

        current = preferred

        current?.registerCallback(
            callback,
            handler
        )

        publish()
    }

    private fun publish() {
        val controller =
            current ?: run {
                onChanged(null)
                return
            }

        val state =
            controller.playbackState

        val metadata =
            controller.metadata

        val duration =
            metadata?.getLong(
                MediaMetadata.METADATA_KEY_DURATION
            ) ?: 0L

        onChanged(
            MediaPlaybackInfo(
                packageName =
                    controller.packageName
                        .orEmpty(),
                title =
                    metadata?.getString(
                        MediaMetadata.METADATA_KEY_TITLE
                    ).orEmpty(),
                artist =
                    metadata?.getString(
                        MediaMetadata.METADATA_KEY_ARTIST
                    ).orEmpty(),
                isPlaying =
                    state?.state ==
                        PlaybackState.STATE_PLAYING,
                positionMs =
                    state?.position
                        ?.coerceAtLeast(0L)
                        ?: 0L,
                durationMs =
                    duration.coerceAtLeast(0L)
            )
        )
    }

    override fun close() {
        handler.removeCallbacks(ticker)

        current?.unregisterCallback(
            callback
        )
        current = null

        try {
            manager
                .removeOnActiveSessionsChangedListener(
                    sessionsListener
                )
        } catch (_: Throwable) {
        }

        onChanged(null)
    }
}

class MusicBeatVisualizer(
    private val context: Context,
    private val onBeat:
        (brightness: Int) -> Unit
) {
    private var visualizer:
        Visualizer? = null

    private var previous =
        DoubleArray(32)

    private val fluxHistory =
        ArrayDeque<Double>()

    private var lastBeatMs =
        0L

    fun start(): Boolean {
        if (
            context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            stop()

            previous =
                DoubleArray(32)

            fluxHistory.clear()
            lastBeatMs = 0L

            val v =
                Visualizer(0)

            v.captureSize =
                Visualizer
                    .getCaptureSizeRange()
                    .last()

            v.setDataCaptureListener(
                object :
                    Visualizer.OnDataCaptureListener {

                    override fun
                        onWaveFormDataCapture(
                        capture: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) = Unit

                    override fun
                        onFftDataCapture(
                        capture: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {
                        val maxBin =
                            minOf(
                                30,
                                fft.size / 2 - 1
                            )

                        if (maxBin < 4) {
                            return
                        }

                        var flux = 0.0
                        var low = 0.0

                        for (bin in 2..maxBin) {
                            val real =
                                fft[bin * 2]
                                    .toInt()

                            val imag =
                                fft[bin * 2 + 1]
                                    .toInt()

                            val magnitude =
                                sqrt(
                                    (
                                        real * real +
                                            imag * imag
                                    ).toDouble()
                                )

                            val delta =
                                (
                                    magnitude -
                                        previous[
                                            bin
                                        ]
                                ).coerceAtLeast(
                                    0.0
                                )

                            val weight =
                                when {
                                    bin <= 6 ->
                                        2.0

                                    bin <= 14 ->
                                        1.35

                                    else ->
                                        0.75
                                }

                            flux +=
                                delta * weight

                            if (bin <= 10) {
                                low +=
                                    magnitude
                            }

                            previous[
                                bin
                            ] = magnitude
                        }

                        fluxHistory.addLast(flux)

                        while (fluxHistory.size > 24) {
                            fluxHistory.removeFirst()
                        }

                        if (fluxHistory.size < 8) {
                            return
                        }

                        // Manual sums: avoids dropLast/takeLast/average
                        // list allocations on every FFT callback.
                        var sum = 0.0
                        var recentSum = 0.0
                        var count = 0
                        var recentCount = 0
                        val lastIndex = fluxHistory.size - 1
                        for ((index, value) in fluxHistory.withIndex()) {
                            if (index == lastIndex) break
                            sum += value
                            count++
                            if (index >= lastIndex - 4) {
                                recentSum += value
                                recentCount++
                            }
                        }

                        val baseline = (if (count > 0) sum / count else 1.0).coerceAtLeast(1.0)
                        val recentBaseline =
                            (if (recentCount > 0) recentSum / recentCount else 1.0).coerceAtLeast(1.0)

                        val ratio =
                            flux /
                                baseline

                        val rising =
                            flux >
                                recentBaseline * 1.03

                        val now =
                            SystemClock
                                .uptimeMillis()

                        if (
                            ratio >= 1.4 &&
                            rising &&
                            now - lastBeatMs >=
                                150L
                        ) {
                            lastBeatMs = now

                            val strength =
                                (
                                    ratio *
                                        2200.0 +
                                        low * 4.0
                                )
                                    .toInt()
                                    .coerceIn(
                                        1800,
                                        4095
                                    )

                            onBeat(
                                strength
                            )
                        }
                    }
                },
                Visualizer
                    .getMaxCaptureRate() / 2,
                false,
                true
            )

            v.enabled = true
            visualizer = v

            true
        } catch (_: Throwable) {
            stop()
            false
        }
    }

    fun stop() {
        try {
            visualizer?.enabled =
                false

            visualizer?.release()
        } catch (_: Throwable) {
        }

        visualizer = null
        fluxHistory.clear()
    }
}

class BeatSyncController(
    context: Context,
    private val glyph: GlyphController,
    private val onState:
        (MediaPlaybackInfo?) -> Unit,
    private val onEngine:
        (String) -> Unit = {},
    private val onPulse:
        () -> Unit = {}
) : AutoCloseable {
    private val appContext =
        context.applicationContext

    private var watcher:
        MediaPlaybackWatcher? = null

    private var visualizer:
        MusicBeatVisualizer? = null

    private var trackKey =
        ""

    fun start() {
        if (watcher != null) {
            return
        }

        watcher =
            MediaPlaybackWatcher(
                appContext,
                onChanged = {
                    onState(it)
                    handle(it)
                },
                onAccessError = {
                    stopAudio()
                    onEngine(
                        "Turn on Media Access in Settings."
                    )
                }
            )

        watcher?.start()
    }

    fun refresh() {
        watcher?.refresh()
    }

    private fun handle(
        info: MediaPlaybackInfo?
    ) {
        if (
            info == null ||
            !info.isPlaying
        ) {
            stopAudio()
            return
        }

        val newKey =
            info.packageName +
                "|" +
                info.title +
                "|" +
                info.artist

        if (newKey != trackKey) {
            trackKey = newKey
            restart()
        } else if (
            visualizer == null
        ) {
            restart()
        }
    }

    private fun restart() {
        visualizer?.stop()

        val next =
            MusicBeatVisualizer(
                appContext
            ) { strength ->
                onPulse()
                glyph.playPattern(
                    listOf(
                        GlyphStep(
                            strength,
                            70
                        ),
                        GlyphStep(
                            0,
                            90
                        )
                    )
                )
            }

        if (next.start()) {
            visualizer = next
            onEngine("LISTENING")
        } else {
            visualizer = null
            onEngine(
                "Beat Sync could not access the audio output."
            )
        }
    }

    private fun stopAudio() {
        visualizer?.stop()
        visualizer = null
        onEngine("STOPPED")
        glyph.stopPattern()
    }

    override fun close() {
        stopAudio()
        watcher?.close()
        watcher = null
        trackKey = ""
    }
}

object GlyphAction {
    suspend fun playOnce(
        context: Context,
        steps: List<GlyphStep>
    ) {
        val store =
            PatternStore(context)

        if (!store.appEnabled()) {
            return
        }

        val controller =
            GlyphController(
                context.applicationContext
            )

        try {
            if (
                !controller.awaitReady(8_000L)
            ) {
                return
            }

            controller.playPattern(
                steps
            )

            delay(
                steps.sumOf {
                    it.durationMs
                } + 100L
            )
        } finally {
            controller.close()
        }
    }
}

/**
 * Playful one-shot dot effects: morse coder, talking digits (clock,
 * battery meter), guided breathing and SOS. All built as plain step
 * lists so they run on the stock Glyph service with no new
 * permissions.
 */
object DotFun {
    private const val UNIT = 110L
    private const val FULL = 4095

    private val morseTable = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..",
        'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....",
        'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.",
        'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..",
        '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..",
        '!' to "-.-.--"
    )

    /** Text to morse steps. Capped to 40 chars to bound the pattern. */
    fun morseSteps(text: String): List<GlyphStep> {
        val steps = ArrayList<GlyphStep>(128)
        for (char in text.uppercase().take(40)) {
            if (char == ' ') {
                // Word gap: 6 extra units (1 already follows each symbol).
                steps += GlyphStep(0, UNIT * 6)
                continue
            }
            val code = morseTable[char] ?: continue
            for (symbol in code) {
                steps += GlyphStep(
                    FULL,
                    if (symbol == '.') UNIT else UNIT * 3
                )
                steps += GlyphStep(0, UNIT)
            }
            // Letter gap: 2 extra units on top of the trailing 1.
            steps += GlyphStep(0, UNIT * 2)
        }
        return steps
    }

    private fun digitSteps(digit: Int): List<GlyphStep> {
        if (digit <= 0) {
            // Zero reads as one long glow instead of silence.
            return listOf(GlyphStep(FULL, 600), GlyphStep(0, 250))
        }
        val steps = ArrayList<GlyphStep>(digit * 2)
        repeat(digit.coerceIn(1, 12)) {
            steps += GlyphStep(FULL, 150)
            steps += GlyphStep(0, 150)
        }
        return steps
    }

    private fun separator(): GlyphStep = GlyphStep(0, 600)

    /** e.g. 3:27 -> 3 flashes, pause, 2, pause, 7. */
    fun talkingClockSteps(hour12: Int, minute: Int): List<GlyphStep> {
        val steps = ArrayList<GlyphStep>(64)
        steps += digitSteps(hour12.coerceIn(1, 12))
        steps += separator()
        steps += digitSteps((minute / 10).coerceIn(0, 5))
        steps += separator()
        steps += digitSteps((minute % 10).coerceIn(0, 9))
        return steps
    }

    /** e.g. 73% -> 7 flashes, pause, 3 flashes. */
    fun batteryMeterSteps(percent: Int): List<GlyphStep> {
        val level = percent.coerceIn(0, 100)
        val steps = ArrayList<GlyphStep>(48)
        steps += digitSteps(level / 10)
        steps += separator()
        steps += digitSteps(level % 10)
        return steps
    }

    /** 4-7-8 guided breathing, [rounds] cycles. */
    fun breathingSteps(rounds: Int = 2): List<GlyphStep> {
        val steps = ArrayList<GlyphStep>(64)
        repeat(rounds.coerceIn(1, 4)) {
            // Inhale 4s: dot swells up.
            steps += GlyphStep(800, 1000)
            steps += GlyphStep(1600, 1000)
            steps += GlyphStep(2400, 1000)
            steps += GlyphStep(3200, 1000)
            // Hold 7s: steady dim glow.
            steps += GlyphStep(1200, 7000)
            // Exhale 8s: dot sinks back down.
            steps += GlyphStep(3200, 1000)
            steps += GlyphStep(2600, 1000)
            steps += GlyphStep(2000, 1000)
            steps += GlyphStep(1400, 1000)
            steps += GlyphStep(800, 4000)
            steps += GlyphStep(0, 800)
        }
        return steps
    }

    fun sosSteps(): List<GlyphStep> {
        val steps = ArrayList<GlyphStep>(24)
        repeat(3) {
            steps += GlyphStep(FULL, 150)
            steps += GlyphStep(0, 150)
        }
        steps += GlyphStep(0, 400)
        repeat(3) {
            steps += GlyphStep(FULL, 400)
            steps += GlyphStep(0, 200)
        }
        steps += GlyphStep(0, 400)
        repeat(3) {
            steps += GlyphStep(FULL, 150)
            steps += GlyphStep(0, 150)
        }
        return steps
    }

    fun diceSuspenseSteps(): List<GlyphStep> {
        val steps = ArrayList<GlyphStep>(20)
        var gap = 90L
        repeat(8) {
            steps += GlyphStep(FULL, 90)
            steps += GlyphStep(0, gap)
            gap += 40L
        }
        return steps
    }

    fun diceResultSteps(rolled: Int): List<GlyphStep> {
        val steps = ArrayList<GlyphStep>(20)
        steps += GlyphStep(0, 300)
        repeat(rolled.coerceIn(1, 6)) {
            steps += GlyphStep(FULL, 260)
            steps += GlyphStep(0, 220)
        }
        return steps
    }
}

object CameraCountdown {    fun start(
        context: Context,
        seconds: Int
    ) {
        val store =
            PatternStore(context)

        if (!store.appEnabled()) {
            return
        }

        try {
            context.startActivity(
                Intent(
                    android.provider.MediaStore
                        .INTENT_ACTION_STILL_IMAGE_CAMERA
                )
            )
        } catch (_: Throwable) {
            return
        }

        val count =
            seconds.coerceIn(3, 10)

        val steps =
            buildList {
                repeat(count) {
                    add(
                        GlyphStep(
                            brightness = 2500,
                            durationMs = 105L
                        )
                    )
                    add(
                        GlyphStep(
                            brightness = 0,
                            durationMs = 595L
                        )
                    )
                }

                add(
                    GlyphStep(
                        brightness = 4095,
                        durationMs = 180L
                    )
                )
                add(
                    GlyphStep(
                        brightness = 0,
                        durationMs = 250L
                    )
                )
            }

        GlyphBackgroundService.playPattern(
            context,
            steps
        )
    }
}

private val chargingScope =
    CoroutineScope(
        Dispatchers.IO
    )

object ChargingMonitor {
    private const val CHECK_ACTION =
        "com.jackson4rocks.oneglyph.CHECK_CHARGING"

    private const val REQUEST_CODE =
        9043

    private const val ACTIVE =
        "charging_active"

    private const val MILESTONE =
        "charging_milestone"

    fun sync(
        context: Context
    ) {
        chargingScope.launch {
            syncInternal(
                context,
                triggerConnectEffect = false
            )
        }
    }

    suspend fun onPowerConnectedAndWait(
        context: Context
    ) {
        syncInternal(
            context,
            triggerConnectEffect = true
        )
    }

    fun onPowerDisconnected(
        context: Context
    ) {
        cancelCheck(context)

        storePrefs(context)
            .edit()
            .putBoolean(ACTIVE, false)
            .putBoolean(MILESTONE, false)
            .apply()
    }

    fun onBoot(
        context: Context
    ) {
        sync(context)
    }

    suspend fun checkAndWait(
        context: Context
    ) {
        syncInternal(
            context,
            triggerConnectEffect = false
        )
    }

    private suspend fun syncInternal(
        context: Context,
        triggerConnectEffect: Boolean
    ) {
        val store =
            PatternStore(context)

        val battery =
            context.registerReceiver(
                null,
                IntentFilter(
                    Intent.ACTION_BATTERY_CHANGED
                )
            )

        val plugged =
            battery?.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                0
            ) ?: 0

        if (
            !store.appEnabled() ||
            !store.chargingEnabled() ||
            plugged == 0
        ) {
            cancelCheck(context)

            storePrefs(context)
                .edit()
                .putBoolean(ACTIVE, false)
                .putBoolean(MILESTONE, false)
                .apply()

            return
        }

        val prefs =
            storePrefs(context)

        val wasActive =
            prefs.getBoolean(
                ACTIVE,
                false
            )

        prefs.edit()
            .putBoolean(
                ACTIVE,
                true
            )
            .apply()

        if (
            triggerConnectEffect &&
            !wasActive
        ) {
            GlyphAction.playOnce(
                context,
                GlyphPatterns.repeated(
                    onMs = 180,
                    offMs = 180,
                    count = 4
                )
            )
        }

        val level =
            battery?.getIntExtra(
                BatteryManager.EXTRA_LEVEL,
                0
            ) ?: 0

        val scale =
            battery?.getIntExtra(
                BatteryManager.EXTRA_SCALE,
                100
            ) ?: 100

        val percent =
            if (scale > 0) {
                level * 100 / scale
            } else {
                0
            }

        val target =
            store.chargeTarget()

        val done =
            prefs.getBoolean(
                MILESTONE,
                false
            )

        if (
            !done &&
            percent >= target
        ) {
            prefs.edit()
                .putBoolean(
                    MILESTONE,
                    true
                )
                .apply()

            GlyphAction.playOnce(
                context,
                GlyphPatterns.repeated(
                    onMs = 120,
                    offMs = 110,
                    count = 9
                )
            )
        }

        scheduleCheck(
            context,
            60_000L
        )
    }

    private fun storePrefs(
        context: Context
    ) =
        context.getSharedPreferences(
            "oneglyph_charging",
            Context.MODE_PRIVATE
        )

    private fun scheduleCheck(
        context: Context,
        delayMs: Long
    ) {
        val alarm =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ChargingCheckReceiver::class.java
            ).setAction(
                CHECK_ACTION
            )

        val pending =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() +
                delayMs,
            pending
        )
    }

    private fun cancelCheck(
        context: Context
    ) {
        val alarm =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ChargingCheckReceiver::class.java
            ).setAction(
                CHECK_ACTION
            )

        val pending =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarm.cancel(
            pending
        )

        pending.cancel()
    }
}

class ChargingEffectReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult =
            goAsync()

        chargingScope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED ->
                        ChargingMonitor
                            .onPowerConnectedAndWait(
                                context
                            )

                    Intent.ACTION_POWER_DISCONNECTED ->
                        ChargingMonitor
                            .onPowerDisconnected(
                                context
                            )

                    Intent.ACTION_BOOT_COMPLETED ->
                        ChargingMonitor
                            .onBoot(
                                context
                            )

                    Intent.ACTION_BATTERY_LOW -> {
                        val store = PatternStore(context)

                        if (
                            store.appEnabled() &&
                            store.lowBatteryAlertEnabled()
                        ) {
                            GlyphAction.playOnce(
                                context,
                                DotFun.sosSteps()
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ChargingCheckReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult =
            goAsync()

        chargingScope.launch {
            try {
                ChargingMonitor
                    .checkAndWait(
                        context
                    )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
