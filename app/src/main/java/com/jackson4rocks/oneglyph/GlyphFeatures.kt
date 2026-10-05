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

class MusicBeatVisualizer(
    private val context: Context,
    private val onFrame: (brightness: Int, beat: Boolean) -> Unit
) {
    private var visualizer: Visualizer? = null
    private val energyHistory = ArrayDeque<Double>()
    private var lastEnergy = 0.0
    private var lastBeatMs = 0L
    private var flashUntilMs = 0L

    fun start(): Boolean {
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        if (permission != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return try {
            stop()
            energyHistory.clear()
            lastEnergy = 0.0
            lastBeatMs = 0L
            flashUntilMs = 0L

            val v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange().last()

            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
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
                        if (fft.size < 8) return

                        val now = SystemClock.uptimeMillis()
                        val maxBin = minOf(
                            fft.size / 2 - 1,
                            24
                        )

                        var energy = 0.0
                        var weightTotal = 0.0

                        for (bin in 2..maxBin) {
                            val real = fft[bin * 2].toInt()
                            val imag = fft[bin * 2 + 1].toInt()
                            val magnitude = sqrt(
                                (real * real + imag * imag).toDouble()
                            )

                            // Heavier weighting on the low end where kick/snare
                            // transients are most useful for a single-dot Glyph.
                            val weight =
                                1.0 + (1.0 / bin.coerceAtLeast(1))

                            energy += magnitude * weight
                            weightTotal += weight
                        }

                        if (weightTotal <= 0.0) return

                        energy /= weightTotal

                        energyHistory.addLast(energy)
                        while (energyHistory.size > 28) {
                            energyHistory.removeFirst()
                        }

                        if (energyHistory.size < 8) {
                            lastEnergy = energy
                            val warmup =
                                ((energy / 18.0) * 900.0)
                                    .toInt()
                                    .coerceIn(320, 2600)
                            onFrame(warmup, false)
                            return
                        }

                        val baseline =
                            energyHistory
                                .dropLast(1)
                                .average()
                                .coerceAtLeast(1.0)

                        val ratio = energy / baseline
                        val rising = energy > lastEnergy * 1.08
                        val cooldownReady =
                            now - lastBeatMs >= 220L

                        val beat =
                            ratio >= 1.48 &&
                                rising &&
                                cooldownReady

                        if (beat) {
                            lastBeatMs = now
                            flashUntilMs = now + 135L
                        }

                        lastEnergy = energy

                        val normalized =
                            (ratio * 1500.0)
                                .toInt()
                                .coerceIn(380, 3200)

                        val brightness =
                            if (now < flashUntilMs) {
                                4095
                            } else {
                                normalized
                            }

                        onFrame(brightness, beat)
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
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
        energyHistory.clear()
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
