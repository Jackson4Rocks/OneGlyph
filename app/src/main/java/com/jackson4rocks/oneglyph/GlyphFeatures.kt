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
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.audiofx.Visualizer
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
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

    fun chargingEnabled(): Boolean =
        prefs.getBoolean("charging_enabled", true)

    fun setChargingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("charging_enabled", enabled)
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
    NotificationListenerService()

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

                        fluxHistory.addLast(
                            flux
                        )

                        while (
                            fluxHistory.size > 24
                        ) {
                            fluxHistory.removeFirst()
                        }

                        if (
                            fluxHistory.size < 8
                        ) {
                            return
                        }

                        val baseline =
                            fluxHistory
                                .dropLast(1)
                                .average()
                                .coerceAtLeast(
                                    1.0
                                )

                        val recentBaseline =
                            fluxHistory
                                .dropLast(1)
                                .takeLast(4)
                                .average()
                                .coerceAtLeast(
                                    1.0
                                )

                        val ratio =
                            flux /
                                baseline

                        val rising =
                            flux >
                                recentBaseline * 1.05

                        val now =
                            SystemClock
                                .uptimeMillis()

                        if (
                            ratio >= 1.5 &&
                            rising &&
                            now - lastBeatMs >=
                                170L
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
    private val onError:
        (String) -> Unit
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
                    onError(
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
            ) {
                glyph.playPattern(
                    listOf(
                        GlyphStep(
                            it,
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
        } else {
            visualizer = null
            onError(
                "Beat Sync could not access the audio output."
            )
        }
    }

    private fun stopAudio() {
        visualizer?.stop()
        visualizer = null
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

object CameraCountdown {
    fun start(
        context: Context,
        controller: GlyphController,
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

        val duration =
            seconds.coerceIn(3, 10) * 1000L

        CoroutineScope(
            Dispatchers.IO
        ).launch {
            val start =
                SystemClock
                    .uptimeMillis()

            while (
                SystemClock.uptimeMillis() -
                    start <
                    duration
            ) {
                val elapsed =
                    (
                        SystemClock.uptimeMillis() -
                            start
                    ).coerceAtLeast(
                        0L
                    )

                val progress =
                    (
                        elapsed.toFloat() /
                            duration
                    ).coerceIn(
                        0f,
                        0.999f
                    )

                val cycleMs =
                    (
                        700L -
                            progress * 560L
                    ).toLong().coerceAtLeast(
                        140L
                    )

                val onMs =
                    (
                        105L -
                            progress * 45L
                    ).toLong().coerceAtLeast(
                        55L
                    )

                controller.playPattern(
                    listOf(
                        GlyphStep(
                            2500 +
                                (1500 * progress)
                                    .toInt(),
                            onMs
                        ),
                        GlyphStep(
                            0,
                            (
                                cycleMs -
                                    onMs
                            ).coerceAtLeast(
                                55L
                            )
                        )
                    )
                )

                delay(
                    cycleMs
                )
            }

            controller.playPattern(
                listOf(
                    GlyphStep(
                        4095,
                        180
                    ),
                    GlyphStep(
                        0,
                        250
                    )
                )
            )
        }
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
