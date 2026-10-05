package com.jackson4rocks.oneglyph

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class GlyphStep(
    val brightness: Int,
    val durationMs: Long
)

object GlyphPatterns {
    val single = listOf(
        GlyphStep(4095, 200),
        GlyphStep(0, 180)
    )

    val double = listOf(
        GlyphStep(4095, 160),
        GlyphStep(0, 120),
        GlyphStep(4095, 160),
        GlyphStep(0, 220)
    )

    val fast = listOf(
        GlyphStep(4095, 90),
        GlyphStep(0, 80),
        GlyphStep(4095, 90),
        GlyphStep(0, 80),
        GlyphStep(4095, 90),
        GlyphStep(0, 220)
    )

    val slow = listOf(
        GlyphStep(2600, 650),
        GlyphStep(0, 450)
    )

    val heartbeat = listOf(
        GlyphStep(4095, 110),
        GlyphStep(0, 90),
        GlyphStep(4095, 180),
        GlyphStep(0, 650)
    )

    val cameraCountdown = listOf(
        GlyphStep(4095, 120),
        GlyphStep(0, 180),
        GlyphStep(4095, 120),
        GlyphStep(0, 180),
        GlyphStep(4095, 120),
        GlyphStep(0, 900)
    )

    val chargingStart = listOf(
        GlyphStep(1200, 220),
        GlyphStep(2300, 260),
        GlyphStep(3600, 360),
        GlyphStep(4095, 520),
        GlyphStep(0, 300)
    )

    fun scale(pattern: List<GlyphStep>, brightness: Int): List<GlyphStep> {
        return pattern.map { step ->
            step.copy(
                brightness = if (step.brightness == 0) {
                    0
                } else {
                    brightness.coerceIn(0, 4095)
                }
            )
        }
    }
}

class PatternStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "oneglyph_patterns",
            Context.MODE_PRIVATE
        )

    fun loadComposer(): MutableList<GlyphStep> {
        val raw = prefs.getString("composer", null)
            ?: return mutableListOf(
                GlyphStep(4095, 180),
                GlyphStep(0, 150),
                GlyphStep(2600, 380)
            )

        val parsed = raw.split("|").mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 2) return@mapNotNull null

            val brightness = parts[0].toIntOrNull()
                ?: return@mapNotNull null

            val duration = parts[1].toLongOrNull()
                ?: return@mapNotNull null

            GlyphStep(
                brightness.coerceIn(0, 4095),
                duration.coerceIn(40, 3000)
            )
        }

        return if (parsed.isEmpty()) {
            mutableListOf(GlyphStep(4095, 180))
        } else {
            parsed.toMutableList()
        }
    }

    fun saveComposer(steps: List<GlyphStep>) {
        val raw = steps.joinToString("|") {
            it.brightness.toString() + "," + it.durationMs
        }

        prefs.edit()
            .putString("composer", raw)
            .apply()
    }

    fun notificationRemindersEnabled(): Boolean =
        prefs.getBoolean("notification_reminders", false)

    fun setNotificationRemindersEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("notification_reminders", enabled)
            .apply()
    }

    fun reminderIntervalMinutes(): Int =
        prefs.getInt("reminder_interval", 5)

    fun setReminderIntervalMinutes(minutes: Int) {
        prefs.edit()
            .putInt(
                "reminder_interval",
                minutes.coerceIn(1, 15)
            )
            .apply()
    }

    fun chargingEnabled(): Boolean =
        prefs.getBoolean("charging_effect", false)

    fun setChargingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("charging_effect", enabled)
            .apply()
    }

    fun visualizerEnabled(): Boolean =
        prefs.getBoolean("music_visualizer", false)

    fun setVisualizerEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("music_visualizer", enabled)
            .apply()
    }
}

data class MediaPlaybackInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

class MediaPlaybackWatcher(
    context: Context,
    private val onChanged: (MediaPlaybackInfo?) -> Unit,
    private val onAccessError: () -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(
            android.media.session.MediaSessionManager::class.java
        )
    private val handler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )
    private val listener =
        android.media.session.MediaSessionManager
            .OnActiveSessionsChangedListener { controllers ->
                selectController(controllers)
            }

    private var currentController:
        android.media.session.MediaController? = null

    private val callback =
        object : android.media.session.MediaController.Callback() {
            override fun onPlaybackStateChanged(
                state: android.media.session.PlaybackState?
            ) {
                publish()
            }

            override fun onMetadataChanged(
                metadata: android.media.MediaMetadata?
            ) {
                publish()
            }

            override fun onSessionDestroyed() {
                currentController = null
                onChanged(null)
                refresh()
            }
        }

    fun start() {
        try {
            manager.addOnActiveSessionsChangedListener(
                listener,
                android.content.ComponentName(
                    appContext,
                    GlyphNotificationListenerService::class.java
                ),
                0,
                handler
            )
            refresh()
        } catch (_: SecurityException) {
            onAccessError()
        } catch (_: Throwable) {
            onAccessError()
        }
    }

    fun refresh() {
        try {
            selectController(
                manager.getActiveSessions(
                    android.content.ComponentName(
                        appContext,
                        GlyphNotificationListenerService::class.java
                    )
                )
            )
        } catch (_: SecurityException) {
            onAccessError()
        } catch (_: Throwable) {
            onChanged(null)
        }
    }

    private fun selectController(
        controllers:
            List<android.media.session.MediaController>?
    ) {
        val list = controllers.orEmpty()

        val preferred =
            list.firstOrNull {
                it.playbackState?.state ==
                    android.media.session.PlaybackState.STATE_PLAYING
            } ?: list.firstOrNull {
                it.metadata != null
            }

        if (preferred === currentController) {
            publish()
            return
        }

        currentController?.unregisterCallback(callback)
        currentController = preferred
        currentController?.registerCallback(
            callback,
            handler
        )

        publish()
    }

    private fun publish() {
        val controller = currentController ?: run {
            onChanged(null)
            return
        }

        val state = controller.playbackState
        val metadata = controller.metadata

        val duration =
            metadata?.getLong(
                android.media.MediaMetadata.METADATA_KEY_DURATION
            ) ?: 0L

        onChanged(
            MediaPlaybackInfo(
                packageName = controller.packageName ?: "",
                title =
                    metadata?.getString(
                        android.media.MediaMetadata
                            .METADATA_KEY_TITLE
                    ).orEmpty(),
                artist =
                    metadata?.getString(
                        android.media.MediaMetadata
                            .METADATA_KEY_ARTIST
                    ).orEmpty(),
                isPlaying =
                    state?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING,
                positionMs =
                    state?.position?.coerceAtLeast(0L)
                        ?: 0L,
                durationMs = duration.coerceAtLeast(0L)
            )
        )
    }

    override fun close() {
        currentController?.unregisterCallback(callback)
        currentController = null

        try {
            manager.removeOnActiveSessionsChangedListener(
                listener
            )
        } catch (_: Throwable) {
        }

        onChanged(null)
    }
}

class MusicBeatVisualizer(
    private val context: Context,
    private val onBeat: (brightness: Int) -> Unit
) {
    private var visualizer: Visualizer? = null
    private var previousMagnitudes =
        DoubleArray(32)

    private val onsetHistory =
        ArrayDeque<Double>()

    private var lastBeatMs = 0L

    fun start(): Boolean {
        val permission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            )

        if (permission !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            stop()

            previousMagnitudes =
                DoubleArray(32)

            onsetHistory.clear()
            lastBeatMs = 0L

            val v = Visualizer(0)

            v.captureSize =
                Visualizer
                    .getCaptureSizeRange()
                    .last()

            v.setDataCaptureListener(
                object :
                    Visualizer.OnDataCaptureListener {

                    override fun onWaveFormDataCapture(
                        capture: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        capture: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {
                        val maxBin =
                            minOf(
                                30,
                                (fft.size / 2) - 1
                            )

                        if (maxBin < 4) return

                        var flux = 0.0
                        var lowEnergy = 0.0
                        var energy = 0.0

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

                            val previous =
                                previousMagnitudes[
                                    bin
                                ]

                            val delta =
                                (
                                    magnitude -
                                        previous
                                ).coerceAtLeast(0.0)

                            // Bass and low-mid bins get more influence,
                            // which makes kick/snare transients much more
                            // useful for a single light.
                            val weight =
                                when {
                                    bin <= 6 -> 1.8
                                    bin <= 14 -> 1.35
                                    else -> 0.8
                                }

                            flux += delta * weight
                            energy +=
                                magnitude * weight

                            if (bin <= 10) {
                                lowEnergy +=
                                    magnitude
                            }

                            previousMagnitudes[
                                bin
                            ] = magnitude
                        }

                        onsetHistory.addLast(flux)

                        while (
                            onsetHistory.size > 24
                        ) {
                            onsetHistory.removeFirst()
                        }

                        if (
                            onsetHistory.size < 8
                        ) {
                            return
                        }

                        val baseline =
                            onsetHistory
                                .dropLast(1)
                                .average()
                                .coerceAtLeast(1.0)

                        val ratio =
                            flux /
                                baseline

                        val now =
                            SystemClock
                                .uptimeMillis()

                        val risingEnough =
                            flux >
                                onsetHistory
                                    .dropLast(1)
                                    .takeLast(3)
                                    .average()
                                    .coerceAtLeast(1.0)

                        val cooldown =
                            now - lastBeatMs >=
                                170L

                        val beat =
                            ratio >= 1.55 &&
                                risingEnough &&
                                cooldown

                        if (!beat) return

                        lastBeatMs = now

                        val kickBoost =
                            (
                                lowEnergy /
                                    (
                                        energy /
                                            4.0
                                    ).coerceAtLeast(
                                        1.0
                                    )
                            ).coerceIn(
                                0.6,
                                1.6
                            )

                        val strength =
                            (
                                ratio *
                                    2200.0 *
                                    kickBoost
                            )
                                .toInt()
                                .coerceIn(
                                    1800,
                                    4095
                                )

                        onBeat(strength)
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
        } catch (_: SecurityException) {
            stop()
            false
        } catch (_: UnsupportedOperationException) {
            stop()
            false
        } catch (_: Throwable) {
            stop()
            false
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }

        visualizer = null
        onsetHistory.clear()
    }
}

class BeatSyncController(
    context: Context,
    private val glyph: GlyphController,
    private val onState: (MediaPlaybackInfo?) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private val appContext =
        context.applicationContext

    private var watcher:
        MediaPlaybackWatcher? = null

    private var visualizer:
        MusicBeatVisualizer? = null

    private var lastTrackKey = ""

    fun start() {
        if (watcher != null) return

        watcher = MediaPlaybackWatcher(
            appContext,
            onChanged = { info ->
                onState(info)
                handlePlayback(info)
            },
            onAccessError = {
                stopAudio()
                onError(
                    "Turn on Media Access to let OneGlyph see what's playing."
                )
            }
        )

        watcher?.start()
    }

    private fun handlePlayback(
        info: MediaPlaybackInfo?
    ) {
        if (info == null) {
            stopAudio()
            return
        }

        val trackKey =
            info.packageName +
                "|" +
                info.title +
                "|" +
                info.artist

        if (
            info.isPlaying &&
            trackKey != lastTrackKey
        ) {
            lastTrackKey = trackKey
            restartAudio()
            return
        }

        if (info.isPlaying) {
            if (visualizer == null) {
                restartAudio()
            }
        } else {
            stopAudio()
        }
    }

    private fun restartAudio() {
        visualizer?.stop()

        val next =
            MusicBeatVisualizer(
                appContext
            ) { brightness ->
                if (glyph.isReady()) {
                    glyph.playPattern(
                        listOf(
                            GlyphStep(
                                brightness,
                                75
                            ),
                            GlyphStep(
                                0,
                                95
                            )
                        )
                    )
                }
            }

        if (next.start()) {
            visualizer = next
        } else {
            visualizer = null
            onError(
                "Audio access is needed for Beat Sync."
            )
        }
    }

    private fun stopAudio() {
        visualizer?.stop()
        visualizer = null
        glyph.stopPattern()
    }

    fun refresh() {
        watcher?.refresh()
    }

    override fun close() {
        stopAudio()
        watcher?.close()
        watcher = null
    }
}

object GlyphAction {
    suspend fun playOnce(
        context: Context,
        steps: List<GlyphStep>
    ) {
        val controller =
            GlyphController(context.applicationContext)

        try {
            if (!controller.awaitReady()) return

            controller.playPattern(steps)
            delay(
                steps.sumOf { it.durationMs } + 100L
            )
        } finally {
            controller.close()
        }
    }

    fun playRingtoneAndPattern(
        context: Context,
        steps: List<GlyphStep>,
        loops: Int = 3
    ) {
        val ringtoneUri =
            RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE
            )

        val ringtone =
            RingtoneManager.getRingtone(
                context,
                ringtoneUri
            )

        try {
            ringtone?.play()
        } catch (_: Throwable) {
        }

        CoroutineScope(Dispatchers.IO).launch {
            val controller =
                GlyphController(context.applicationContext)

            try {
                if (!controller.awaitReady()) return@launch

                repeat(loops.coerceIn(1, 6)) {
                    controller.playPattern(steps)
                    delay(
                        steps.sumOf { it.durationMs } + 120L
                    )
                }
            } finally {
                try {
                    ringtone?.stop()
                } catch (_: Throwable) {
                }

                controller.close()
            }
        }
    }
}

object NotificationReminderScheduler {
    private const val REQUEST_BASE = 7000
    private const val EXTRA_KEY = "notification_key"

    fun schedule(
        context: Context,
        key: String,
        minutes: Int
    ) {
        val alarm =
            context.getSystemService(AlarmManager::class.java)

        val intent =
            Intent(
                context,
                NotificationReminderReceiver::class.java
            ).putExtra(EXTRA_KEY, key)

        val requestCode =
            REQUEST_BASE +
                (key.hashCode() and 0x3fff)

        val pending =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() +
                minutes.coerceIn(1, 15) * 60_000L,
            pending
        )
    }

    fun cancel(
        context: Context,
        key: String
    ) {
        val alarm =
            context.getSystemService(AlarmManager::class.java)

        val requestCode =
            REQUEST_BASE +
                (key.hashCode() and 0x3fff)

        val intent =
            Intent(
                context,
                NotificationReminderReceiver::class.java
            )

        val pending =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarm.cancel(pending)
        pending.cancel()
    }

    fun keyFrom(intent: Intent): String? =
        intent.getStringExtra(EXTRA_KEY)
}

class GlyphNotificationListenerService :
    NotificationListenerService() {

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        if (sbn.packageName == packageName) return

        val store = PatternStore(this)
        if (!store.notificationRemindersEnabled()) {
            return
        }

        NotificationReminderScheduler.schedule(
            this,
            sbn.key,
            store.reminderIntervalMinutes()
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {
        NotificationReminderScheduler.cancel(
            this,
            sbn.key
        )
    }
}

class NotificationReminderReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val key =
                    NotificationReminderScheduler.keyFrom(
                        intent
                    ) ?: return@launch

                val store = PatternStore(context)
                if (!store.notificationRemindersEnabled()) {
                    return@launch
                }

                val controller =
                    GlyphController(
                        context.applicationContext
                    )

                try {
                    if (controller.awaitReady()) {
                        controller.playPattern(
                            GlyphPatterns.double
                        )

                        delay(550L)

                        NotificationReminderScheduler.schedule(
                            context,
                            key,
                            store.reminderIntervalMinutes()
                        )
                    }
                } finally {
                    controller.close()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ChargingEffectReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action != Intent.ACTION_POWER_CONNECTED &&
            intent.action != Intent.ACTION_POWER_DISCONNECTED
        ) {
            return
        }

        val store = PatternStore(context)
        if (!store.chargingEnabled()) return

        CoroutineScope(Dispatchers.IO).launch {
            val pattern =
                if (
                    intent.action ==
                    Intent.ACTION_POWER_CONNECTED
                ) {
                    GlyphPatterns.chargingStart
                } else {
                    GlyphPatterns.single
                }

            GlyphAction.playOnce(
                context,
                pattern
            )
        }
    }
}
