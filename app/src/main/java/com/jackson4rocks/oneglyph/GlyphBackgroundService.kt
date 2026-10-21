package com.jackson4rocks.oneglyph

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Stock-only background Glyph service.
 *
 * Single owner of [GlyphController]; UI and receivers send it intents
 * (toy / music-sync / one-shot patterns). Status is shared with the UI
 * process through a small file because the service runs in
 * `:oneglyph_bg`.
 */
class GlyphBackgroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "oneglyph_background"
        private const val NOTIFICATION_ID = 1001
        private const val RECOVERY_REQUEST_CODE = 17401
        private const val RECOVERY_DELAY_MS = 15_000L

        private const val HEARTBEAT_PREF = "background_heartbeat"
        private const val HEARTBEAT_KEY = "last_elapsed"
        private const val HEARTBEAT_STALE_MS = 12_000L

        private const val GLYPH_STATUS_STALE_MS = 15_000L
        private const val BACKEND_STATUS_FILE = "glyph_backend.status"

        private const val NOTHING_GLYPH_PACKAGE = "com.nothing.thirdparty"

        const val ACTION_TOY_ON = "com.jackson4rocks.oneglyph.action.TOY_ON"
        const val ACTION_TOY_OFF = "com.jackson4rocks.oneglyph.action.TOY_OFF"
        const val ACTION_MUSIC_SYNC_ON = "com.jackson4rocks.oneglyph.action.MUSIC_SYNC_ON"
        const val ACTION_MUSIC_SYNC_OFF = "com.jackson4rocks.oneglyph.action.MUSIC_SYNC_OFF"
        const val ACTION_KEEP_ALIVE = "com.jackson4rocks.oneglyph.action.KEEP_ALIVE"
        const val ACTION_STOP = "com.jackson4rocks.oneglyph.action.STOP"
        const val ACTION_PLAY_PATTERN = "com.jackson4rocks.oneglyph.action.PLAY_PATTERN"
        const val ACTION_PLAY_LONG = "com.jackson4rocks.oneglyph.action.PLAY_LONG"
        const val ACTION_STOP_PATTERN = "com.jackson4rocks.oneglyph.action.STOP_PATTERN"
        const val ACTION_METRONOME_START = "com.jackson4rocks.oneglyph.action.METRONOME_START"
        const val ACTION_METRONOME_STOP = "com.jackson4rocks.oneglyph.action.METRONOME_STOP"
        const val ACTION_BEAT_TEST = "com.jackson4rocks.oneglyph.action.BEAT_TEST"

        private const val EXTRA_BRIGHTNESSES = "brightnesses"
        private const val EXTRA_DURATIONS = "durations"
        private const val EXTRA_REPEAT = "repeat"
        private const val EXTRA_BPM = "bpm"

        private const val PREFS = "oneglyph_background"
        private const val MODE = "mode"
        private const val LEGACY_TOY_ENABLED = "toy_enabled"

        private const val MODE_NONE = "none"
        private const val MODE_TOY = "toy"
        private const val MODE_MUSIC_SYNC = "music_sync"
        private const val MODE_METRONOME = "metronome"

        fun isToyEnabled(context: Context): Boolean =
            readMode(context) == MODE_TOY

        fun isMusicSyncEnabled(context: Context): Boolean =
            readMode(context) == MODE_MUSIC_SYNC

        fun startToy(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_TOY_ON))
        }

        fun stopToy(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_TOY_OFF))
        }

        fun startMusicSync(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_MUSIC_SYNC_ON))
        }

        fun stopMusicSync(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_MUSIC_SYNC_OFF))
        }

        fun ensureRunning(context: Context) {
            if (!PatternStore(context).appEnabled()) return
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_KEEP_ALIVE))
        }

        fun stopService(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_STOP))
        }

        fun playPattern(context: Context, steps: List<GlyphStep>, repeat: Int = 1) {
            val clean = steps.filter { it.durationMs > 0L }.take(32)
            if (clean.isEmpty()) return
            start(
                context,
                Intent(context, GlyphBackgroundService::class.java).apply {
                    action = ACTION_PLAY_PATTERN
                    putIntegerArrayListExtra(EXTRA_BRIGHTNESSES, ArrayList(clean.map { it.brightness }))
                    putExtra(EXTRA_DURATIONS, clean.map { it.durationMs }.toLongArray())
                    putExtra(EXTRA_REPEAT, repeat.coerceIn(1, 32))
                },
            )
        }

        fun stopPattern(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_STOP_PATTERN))
        }

        /**
         * One-shot pattern longer than the 32-step cap (morse, talking
         * clock, battery meter, breathing). Played once, uncapped to
         * 256 steps.
         */
        fun playLongPattern(context: Context, steps: List<GlyphStep>) {
            val clean = steps.filter { it.durationMs > 0L }.take(256)
            if (clean.isEmpty()) return
            start(
                context,
                Intent(context, GlyphBackgroundService::class.java).apply {
                    action = ACTION_PLAY_LONG
                    putIntegerArrayListExtra(EXTRA_BRIGHTNESSES, ArrayList(clean.map { it.brightness }))
                    putExtra(EXTRA_DURATIONS, clean.map { it.durationMs }.toLongArray())
                },
            )
        }

        fun startMetronome(context: Context, bpm: Int) {
            start(
                context,
                Intent(context, GlyphBackgroundService::class.java).apply {
                    action = ACTION_METRONOME_START
                    putExtra(EXTRA_BPM, bpm.coerceIn(30, 240))
                },
            )
        }

        fun stopMetronome(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_METRONOME_STOP))
        }

        fun isMetronomeEnabled(context: Context): Boolean =
            readMode(context) == MODE_METRONOME

        fun beatTest(context: Context) {
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_BEAT_TEST))
        }

        fun hasGlyphService(context: Context): Boolean =
            try {
                context.packageManager.getPackageInfo(NOTHING_GLYPH_PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Throwable) {
                false
            }

        fun isGlyphReady(context: Context): Boolean =
            cachedBackendReady(context) || hasGlyphService(context)

        fun cachedBackendLabel(context: Context, fallback: String = ""): String =
            readBackendStatusFile(context)?.label ?: fallback

        fun cachedBackendReady(context: Context): Boolean {
            val status = readBackendStatusFile(context) ?: return false
            if (!status.ready) return false
            val now = SystemClock.elapsedRealtime()
            return now >= status.updated && now - status.updated <= GLYPH_STATUS_STALE_MS
        }

        /**
         * Per-check breakdown behind the header READY/LINKING dot.
         */
        fun linkDetail(context: Context): String {
            val pkg = if (hasGlyphService(context)) "FOUND" else "MISSING"
            val service = if (cachedBackendReady(context)) "READY" else "NOT READY"
            return "GLYPH PKG: $pkg • SERVICE: $service"
        }

        fun publishBackendStatus(context: Context, ready: Boolean, label: String) {
            // File-based, not SharedPreferences: prefs instances are cached
            // per process, so the UI process would never see writes from
            // the :oneglyph_bg service process.
            val prev = readBackendStatusFile(context)
            writeStatusFile(
                context,
                ready,
                label,
                SystemClock.elapsedRealtime(),
                prev?.beatReady ?: false,
                prev?.beatLabel ?: "",
                prev?.beatUpdated ?: 0L,
            )
        }

        /**
         * Beat-engine state for the UI's engine line. Identical
         * consecutive reports are throttled: pause ticks arrive every
         * 350ms and must not rewrite the file each time.
         */
        fun publishBeatStatus(context: Context, listening: Boolean, label: String) {
            val now = SystemClock.elapsedRealtime()
            if (label == lastEngineMsg && now - lastEngineMs < 10_000L) return
            lastEngineMsg = label
            lastEngineMs = now
            val prev = readBackendStatusFile(context)
            writeStatusFile(
                context,
                prev?.ready ?: false,
                prev?.label ?: "",
                prev?.updated ?: 0L,
                listening,
                label.replace("\n", " "),
                now,
            )
        }

        /** Called on every detected beat; throttled to 1 write/sec. */
        fun publishBeatPulse(context: Context) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBeatWriteMs < 1000L) return
            lastBeatWriteMs = now
            publishBeatStatusForced(context, true, "LISTENING", now)
        }

        private fun publishBeatStatusForced(context: Context, listening: Boolean, label: String, now: Long) {
            lastEngineMsg = label
            lastEngineMs = now
            val prev = readBackendStatusFile(context)
            writeStatusFile(
                context,
                prev?.ready ?: false,
                prev?.label ?: "",
                prev?.updated ?: 0L,
                listening,
                label,
                now,
            )
        }

        /** Human-readable engine line for the UI; proves live beats. */
        fun beatStatusText(context: Context): String {
            val s = readBackendStatusFile(context) ?: return "ENGINE • STOPPED"
            if (!s.beatReady) return "ENGINE • " + s.beatLabel.ifBlank { "STOPPED" }
            val ago = (SystemClock.elapsedRealtime() - s.beatUpdated) / 1000.0
            return "ENGINE • LISTENING • last beat %.1f s ago".format(ago)
        }

        private fun writeStatusFile(
            context: Context,
            ready: Boolean,
            label: String,
            updated: Long,
            beatReady: Boolean,
            beatLabel: String,
            beatUpdated: Long,
        ) {
            try {
                val dir = context.filesDir
                val tmp = java.io.File(dir, "$BACKEND_STATUS_FILE.tmp")
                tmp.writeText(
                    "ready=" + (if (ready) "1" else "0") +
                        "\nlabel=" + label.replace("\n", " ") +
                        "\nupdated=" + updated +
                        "\nbeat_ready=" + (if (beatReady) "1" else "0") +
                        "\nbeat_label=" + beatLabel.replace("\n", " ") +
                        "\nbeat_updated=" + beatUpdated + "\n",
                )
                tmp.renameTo(java.io.File(dir, BACKEND_STATUS_FILE))
            } catch (_: Throwable) {
            }
        }

        @Volatile
        private var lastEngineMsg = ""
        @Volatile
        private var lastEngineMs = 0L
        @Volatile
        private var lastBeatWriteMs = 0L

        private data class BackendStatusFile(
            val ready: Boolean,
            val label: String,
            val updated: Long,
            val beatReady: Boolean = false,
            val beatLabel: String = "",
            val beatUpdated: Long = 0L,
        )

        private fun readBackendStatusFile(context: Context): BackendStatusFile? =
            try {
                var ready = false
                var label = ""
                var updated = 0L
                var beatReady = false
                var beatLabel = ""
                var beatUpdated = 0L
                java.io.File(context.filesDir, BACKEND_STATUS_FILE).forEachLine { line ->
                    when {
                        line.startsWith("ready=") ->
                            ready = line.substringAfter("=").trim() == "1"
                        line.startsWith("label=") ->
                            label = line.substringAfter("=").trim()
                        line.startsWith("updated=") ->
                            updated = line.substringAfter("=").trim().toLongOrNull() ?: 0L
                        line.startsWith("beat_ready=") ->
                            beatReady = line.substringAfter("=").trim() == "1"
                        line.startsWith("beat_label=") ->
                            beatLabel = line.substringAfter("=").trim()
                        line.startsWith("beat_updated=") ->
                            beatUpdated = line.substringAfter("=").trim().toLongOrNull() ?: 0L
                    }
                }
                BackendStatusFile(ready, label, updated, beatReady, beatLabel, beatUpdated)
            } catch (_: Throwable) {
                null
            }

        fun currentMode(context: Context): String = readMode(context)

        fun heartbeatIsFresh(context: Context): Boolean {
            val now = SystemClock.elapsedRealtime()
            val last = context.getSharedPreferences(HEARTBEAT_PREF, Context.MODE_PRIVATE)
                .getLong(HEARTBEAT_KEY, 0L)
            return last > 0L && now >= last && now - last <= HEARTBEAT_STALE_MS
        }

        fun ensureRunningFromRecovery(context: Context) {
            if (!PatternStore(context).appEnabled()) {
                cancelRecovery(context)
                return
            }
            if (readMode(context) == MODE_NONE) {
                cancelRecovery(context)
                return
            }
            start(context, Intent(context, GlyphBackgroundService::class.java).setAction(ACTION_KEEP_ALIVE))
        }

        fun scheduleRecovery(context: Context, delayMs: Long = RECOVERY_DELAY_MS) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, GlyphRecoveryReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                RECOVERY_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(5_000L)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                }
            } catch (_: SecurityException) {
                try {
                    alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                } catch (_: Throwable) {
                }
            }
        }

        fun cancelRecovery(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, GlyphRecoveryReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                RECOVERY_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarm.cancel(pending)
            pending.cancel()
        }

        private fun readMode(context: Context): String {
            val canonical = PatternStore(context.applicationContext).backgroundMode()
            if (canonical == MODE_TOY || canonical == MODE_MUSIC_SYNC || canonical == MODE_METRONOME) return canonical

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val oldMode = prefs.getString(MODE, null)
            if (oldMode == MODE_TOY || oldMode == MODE_MUSIC_SYNC) {
                saveMode(context, oldMode)
                return oldMode
            }
            if (prefs.getBoolean(LEGACY_TOY_ENABLED, false)) {
                saveMode(context, MODE_TOY)
                return MODE_TOY
            }
            saveMode(context, MODE_NONE)
            return MODE_NONE
        }

        private fun saveMode(context: Context, mode: String) {
            PatternStore(context.applicationContext).setBackgroundMode(mode)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(MODE, mode)
                .putBoolean(LEGACY_TOY_ENABLED, mode == MODE_TOY)
                .commit()
        }

        private fun start(context: Context, intent: Intent) {
            // minSdk is 35, so startForegroundService always exists.
            context.startForegroundService(intent)
        }
    }

    private lateinit var controller: GlyphController
    private var currentMode = MODE_NONE
    private var musicSync: BeatSyncController? = null

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable =
        object : Runnable {
            override fun run() {
                writeHeartbeat()
                publishControllerStatus()
                heartbeatHandler.postDelayed(this, 4_000L)
            }
        }

    private fun publishControllerStatus() {
        if (!::controller.isInitialized) return
        try {
            publishBackendStatus(this, controller.isReady(), controller.backendLabel())
        } catch (_: Throwable) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!PatternStore(this).appEnabled()) {
            stopSelf()
            return
        }
        controller = GlyphController(applicationContext)
        publishControllerStatus()
        currentMode = readMode(this)
        if (currentMode != MODE_NONE) {
            writeHeartbeat()
            scheduleRecovery(this)
        }
        heartbeatHandler.post(heartbeatRunnable)
        restoreMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        writeHeartbeat()
        if (!PatternStore(this).appEnabled()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_TOY_ON -> setMode(MODE_TOY)
            ACTION_TOY_OFF -> if (currentMode == MODE_TOY) setMode(MODE_NONE)
            ACTION_MUSIC_SYNC_ON -> setMode(MODE_MUSIC_SYNC)
            ACTION_MUSIC_SYNC_OFF -> if (currentMode == MODE_MUSIC_SYNC) setMode(MODE_NONE)
            ACTION_KEEP_ALIVE, null -> restoreMode()
            ACTION_PLAY_PATTERN -> {
                val brightnesses = intent?.getIntegerArrayListExtra(EXTRA_BRIGHTNESSES)
                val durations = intent?.getLongArrayExtra(EXTRA_DURATIONS)
                if (brightnesses != null && durations != null && brightnesses.size == durations.size) {
                    controller.playPattern(
                        brightnesses.indices.map { index -> GlyphStep(brightnesses[index], durations[index]) },
                        intent?.getIntExtra(EXTRA_REPEAT, 1) ?: 1,
                    )
                }
            }
            ACTION_PLAY_LONG -> {
                val brightnesses = intent?.getIntegerArrayListExtra(EXTRA_BRIGHTNESSES)
                val durations = intent?.getLongArrayExtra(EXTRA_DURATIONS)
                if (brightnesses != null && durations != null && brightnesses.size == durations.size) {
                    controller.playPattern(
                        brightnesses.indices.map { index -> GlyphStep(brightnesses[index], durations[index]) },
                        repeat = 1,
                        maxSteps = 256,
                    )
                }
            }
            ACTION_METRONOME_START -> {
                PatternStore(this).setBackgroundMode(MODE_METRONOME)
                setMode(MODE_METRONOME)
                startMetronomeLoop(intent?.getIntExtra(EXTRA_BPM, 96) ?: 96)
            }
            ACTION_METRONOME_STOP -> if (currentMode == MODE_METRONOME) setMode(MODE_NONE)
            ACTION_BEAT_TEST -> {
                // Single synthetic beat: verifies the Glyph path
                // independent of the audio engine.
                controller.playPattern(
                    listOf(
                        GlyphStep(3200, 70),
                        GlyphStep(0, 90),
                    )
                )
            }
            ACTION_STOP_PATTERN -> controller.stopPattern()
            ACTION_STOP -> {
                setMode(MODE_NONE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (currentMode != MODE_NONE) scheduleRecovery(this) else cancelRecovery(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.d(
            "OneGlyph",
            "UI task removed; scheduling background recovery for mode: $currentMode",
        )
        if (currentMode != MODE_NONE) {
            writeHeartbeat()
            scheduleRecovery(this, 5_000L)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun restoreMode() {
        when (currentMode) {
            MODE_TOY -> startToyLoop()
            MODE_MUSIC_SYNC -> startMusicSyncLoop()
            MODE_METRONOME -> startMetronomeLoop(PatternStore(this).metronomeBpm())
            else -> {
                stopToyLoop()
                stopMusicSyncLoop()
            }
        }
    }

    private fun setMode(mode: String) {
        currentMode = mode
        saveMode(this, mode)
        when (mode) {
            MODE_TOY -> {
                stopMusicSyncLoop()
                startToyLoop()
            }
            MODE_MUSIC_SYNC -> {
                stopToyLoop()
                startMusicSyncLoop()
            }
            MODE_METRONOME -> {
                stopToyLoop()
                stopMusicSyncLoop()
            }
            else -> {
                stopToyLoop()
                stopMusicSyncLoop()
                cancelRecovery(this)
            }
        }
        if (mode != MODE_NONE) {
            writeHeartbeat()
            scheduleRecovery(this)
        }
    }

    private fun startToyLoop() {
        musicSync?.close()
        musicSync = null
        controller.fastFlashLoop()
        android.util.Log.d("OneGlyph", "Dot Toy enabled persistently.")
    }

    private fun stopToyLoop() {
        if (::controller.isInitialized) controller.stopPattern()
    }

    private fun startMetronomeLoop(bpm: Int) {
        musicSync?.close()
        musicSync = null
        PatternStore(this).setMetronomeBpm(bpm)
        controller.metronomeLoop(bpm)
        android.util.Log.d("OneGlyph", "Metronome enabled persistently at $bpm BPM.")
    }

    private fun startMusicSyncLoop() {
        musicSync?.start()
            ?: run {
                musicSync = BeatSyncController(
                    applicationContext,
                    controller,
                    onState = { info ->
                        android.util.Log.d(
                            "OneGlyph",
                            if (info?.isPlaying == true) {
                                "Music Sync following playback."
                            } else if (info != null) {
                                "Music Sync paused."
                            } else {
                                "Music Sync waiting for media."
                            },
                        )
                    },
                    onEngine = { message ->
                        publishBeatStatus(
                            this,
                            message == "LISTENING",
                            message
                        )
                        if (message != "LISTENING") {
                            android.util.Log.w(
                                "OneGlyph",
                                "Music Sync: $message"
                            )
                        }
                    },
                    onPulse = {
                        publishBeatPulse(this)
                    },
                )
                musicSync?.start()
            }
        android.util.Log.d("OneGlyph", "Music Sync enabled persistently.")
    }

    private fun stopMusicSyncLoop() {
        publishBeatStatus(this, false, "STOPPED")
        musicSync?.close()
        musicSync = null
    }

    private fun createNotificationChannel() {
        // minSdk is 35: NotificationChannel always exists.
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "OneGlyph background", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps OneGlyph running in the background."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        // minSdk is 35: the channel-based constructor always exists.
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Keep Blinking!")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        try {
            if (currentMode != MODE_NONE) scheduleRecovery(this, 5_000L)
            musicSync?.close()
            musicSync = null
            if (::controller.isInitialized) {
                try {
                    controller.stopPattern()
                    controller.close()
                } catch (_: Exception) {
                }
                publishBackendStatus(this, false, "GLYPH SERVICE STOPPED")
            }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun writeHeartbeat() {
        getSharedPreferences(HEARTBEAT_PREF, MODE_PRIVATE)
            .edit()
            .putLong(HEARTBEAT_KEY, SystemClock.elapsedRealtime())
            .apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class GlyphRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        try {
            val appContext = context.applicationContext
            if (!PatternStore(appContext).appEnabled()) {
                GlyphBackgroundService.cancelRecovery(appContext)
                return
            }
            if (GlyphBackgroundService.currentMode(appContext) == "none") {
                GlyphBackgroundService.cancelRecovery(appContext)
                return
            }
            if (!GlyphBackgroundService.heartbeatIsFresh(appContext)) {
                try {
                    GlyphBackgroundService.ensureRunningFromRecovery(appContext)
                } catch (e: Throwable) {
                    android.util.Log.w("OneGlyph", "Background recovery failed", e)
                }
            }
            GlyphBackgroundService.scheduleRecovery(appContext)
        } finally {
            pendingResult.finish()
        }
    }
}
