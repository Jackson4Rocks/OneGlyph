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
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

class GlyphBackgroundService : Service() {

    companion object {
        private const val CHANNEL_ID =
            "oneglyph_background"

        private const val NOTIFICATION_ID =
            1001

        private const val RECOVERY_REQUEST_CODE =
            17401

        private const val RECOVERY_DELAY_MS =
            15_000L

        private const val HEARTBEAT_PREF =
            "background_heartbeat"

        private const val HEARTBEAT_KEY =
            "last_elapsed"

        private const val HEARTBEAT_STALE_MS =
            12_000L

        const val ACTION_TOY_ON =
            "com.jackson4rocks.oneglyph.action.TOY_ON"

        const val ACTION_TOY_OFF =
            "com.jackson4rocks.oneglyph.action.TOY_OFF"

        const val ACTION_MUSIC_SYNC_ON =
            "com.jackson4rocks.oneglyph.action.MUSIC_SYNC_ON"

        const val ACTION_MUSIC_SYNC_OFF =
            "com.jackson4rocks.oneglyph.action.MUSIC_SYNC_OFF"

        const val ACTION_KEEP_ALIVE =
            "com.jackson4rocks.oneglyph.action.KEEP_ALIVE"

        const val ACTION_STOP =
            "com.jackson4rocks.oneglyph.action.STOP"

        private const val PREFS =
            "oneglyph_background"

        private const val MODE =
            "mode"

        private const val LEGACY_TOY_ENABLED =
            "toy_enabled"

        private const val MODE_NONE =
            "none"

        private const val MODE_TOY =
            "toy"

        private const val MODE_MUSIC_SYNC =
            "music_sync"

        fun isToyEnabled(
            context: Context
        ): Boolean =
            readMode(context) == MODE_TOY

        fun isMusicSyncEnabled(
            context: Context
        ): Boolean =
            readMode(context) == MODE_MUSIC_SYNC

        fun startToy(
            context: Context
        ) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_TOY_ON
                )
            )
        }

        fun stopToy(
            context: Context
        ) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_TOY_OFF
                )
            )
        }

        fun startMusicSync(
            context: Context
        ) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_MUSIC_SYNC_ON
                )
            )
        }

        fun stopMusicSync(
            context: Context
        ) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_MUSIC_SYNC_OFF
                )
            )
        }

        fun ensureRunning(
            context: Context
        ) {
            if (
                !PatternStore(context)
                    .appEnabled()
            ) {
                return
            }

            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_KEEP_ALIVE
                )
            )
        }

        fun stopService(
            context: Context
        ) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_STOP
                )
            )
        }

        fun currentMode(
            context: Context
        ): String =
            readMode(context)

        fun heartbeatIsFresh(
            context: Context
        ): Boolean {
            val now =
                SystemClock.elapsedRealtime()

            val last =
                context.getSharedPreferences(
                    HEARTBEAT_PREF,
                    Context.MODE_PRIVATE
                ).getLong(
                    HEARTBEAT_KEY,
                    0L
                )

            return last > 0L &&
                now >= last &&
                now - last <=
                    HEARTBEAT_STALE_MS
        }

        fun ensureRunningFromRecovery(
            context: Context
        ) {
            if (
                !PatternStore(context)
                    .appEnabled()
            ) {
                cancelRecovery(
                    context
                )
                return
            }

            if (
                readMode(context) ==
                    MODE_NONE
            ) {
                cancelRecovery(
                    context
                )
                return
            }

            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(
                    ACTION_KEEP_ALIVE
                )
            )
        }

        fun scheduleRecovery(
            context: Context,
            delayMs: Long =
                RECOVERY_DELAY_MS
        ) {
            val alarm =
                context.getSystemService(
                    AlarmManager::class.java
                ) ?: return

            val intent =
                Intent(
                    context,
                    GlyphRecoveryReceiver::class.java
                )

            val pending =
                PendingIntent.getBroadcast(
                    context,
                    RECOVERY_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            val triggerAt =
                SystemClock.elapsedRealtime() +
                    delayMs.coerceAtLeast(5_000L)

            try {
                if (
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S &&
                    alarm.canScheduleExactAlarms()
                ) {
                    alarm.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pending
                    )
                } else {
                    // Fallback when exact-alarm access has not been granted.
                    // The UI requests the access when a persistent mode is enabled.
                    alarm.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pending
                    )
                }
            } catch (_: SecurityException) {
                try {
                    alarm.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pending
                    )
                } catch (_: Throwable) {
                }
            }
        }

        fun cancelRecovery(
            context: Context
        ) {
            val alarm =
                context.getSystemService(
                    AlarmManager::class.java
                ) ?: return

            val intent =
                Intent(
                    context,
                    GlyphRecoveryReceiver::class.java
                )

            val pending =
                PendingIntent.getBroadcast(
                    context,
                    RECOVERY_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            alarm.cancel(
                pending
            )

            pending.cancel()
        }

        private fun readMode(
            context: Context
        ): String {
            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val stored =
                prefs.getString(
                    MODE,
                    null
                )

            if (!stored.isNullOrBlank()) {
                return stored
            }

            // Migrate the old Dot Toy preference once.
            return if (
                prefs.getBoolean(
                    LEGACY_TOY_ENABLED,
                    false
                )
            ) {
                prefs.edit()
                    .putString(
                        MODE,
                        MODE_TOY
                    )
                    .apply()

                MODE_TOY
            } else {
                MODE_NONE
            }
        }

        private fun saveMode(
            context: Context,
            mode: String
        ) {
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(
                    MODE,
                    mode
                )
                .putBoolean(
                    LEGACY_TOY_ENABLED,
                    mode == MODE_TOY
                )
                .apply()
        }

        private fun start(
            context: Context,
            intent: Intent
        ) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(
                    intent
                )
            } else {
                context.startService(
                    intent
                )
            }
        }
    }

    private lateinit var controller:
        GlyphController

    private var currentMode =
        MODE_NONE

    private var musicSync:
        BeatSyncController? = null

    private val heartbeatHandler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )

    private val heartbeatRunnable =
        object : Runnable {
            override fun run() {
                writeHeartbeat()
                heartbeatHandler.postDelayed(
                    this,
                    4_000L
                )
            }
        }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        )

        if (!PatternStore(this).appEnabled()) {
            stopSelf()
            return
        }

        controller =
            GlyphController(
                applicationContext
            )

        currentMode =
            readMode(this)

        if (
            currentMode != MODE_NONE
        ) {
            writeHeartbeat()
            scheduleRecovery(
                this
            )
        }

        heartbeatHandler.post(
            heartbeatRunnable
        )

        restoreMode()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        writeHeartbeat()

        if (
            !PatternStore(this).appEnabled()
        ) {
            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_TOY_ON -> {
                setMode(
                    MODE_TOY
                )
            }

            ACTION_TOY_OFF -> {
                if (
                    currentMode ==
                        MODE_TOY
                ) {
                    setMode(
                        MODE_NONE
                    )
                }
            }

            ACTION_MUSIC_SYNC_ON -> {
                setMode(
                    MODE_MUSIC_SYNC
                )
            }

            ACTION_MUSIC_SYNC_OFF -> {
                if (
                    currentMode ==
                        MODE_MUSIC_SYNC
                ) {
                    setMode(
                        MODE_NONE
                    )
                }
            }

            ACTION_KEEP_ALIVE,
            null -> {
                restoreMode()
            }

            ACTION_STOP -> {
                setMode(
                    MODE_NONE
                )

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
                stopSelf()

                return START_NOT_STICKY
            }
        }

        if (
            currentMode != MODE_NONE
        ) {
            scheduleRecovery(
                this
            )
        } else {
            cancelRecovery(
                this
            )
        }

        return START_STICKY
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {
        android.util.Log.d(
            "OneGlyph",
            "UI task removed; scheduling background recovery for mode: " +
                currentMode
        )

        if (
            currentMode != MODE_NONE
        ) {
            writeHeartbeat()
            scheduleRecovery(
                this,
                5_000L
            )
        }

        super.onTaskRemoved(
            rootIntent
        )
    }

    private fun restoreMode() {
        when (
            currentMode
        ) {
            MODE_TOY ->
                startToyLoop()

            MODE_MUSIC_SYNC ->
                startMusicSyncLoop()

            else -> {
                stopToyLoop()
                stopMusicSyncLoop()
            }
        }
    }

    private fun setMode(
        mode: String
    ) {
        currentMode = mode

        saveMode(
            this,
            mode
        )

        when (mode) {
            MODE_TOY -> {
                stopMusicSyncLoop()
                startToyLoop()
            }

            MODE_MUSIC_SYNC -> {
                stopToyLoop()
                startMusicSyncLoop()
            }

            else -> {
                stopToyLoop()
                stopMusicSyncLoop()
                cancelRecovery(
                    this
                )
            }
        }

        if (
            mode != MODE_NONE
        ) {
            writeHeartbeat()
            scheduleRecovery(
                this
            )
        }
    }

    private fun startToyLoop() {
        musicSync?.close()
        musicSync = null

        controller.fastFlashLoop()

        android.util.Log.d(
            "OneGlyph",
            "Dot Toy enabled persistently."
        )
    }

    private fun stopToyLoop() {
        controller.stopPattern()
    }

    private fun startMusicSyncLoop() {
        musicSync?.start()
            ?: run {
                musicSync =
                    BeatSyncController(
                        applicationContext,
                        controller,
                        onState = { info ->
                            android.util.Log.d(
                                "OneGlyph",
                                if (
                                    info?.isPlaying == true
                                ) {
                                    "Music Sync following playback."
                                } else if (
                                    info != null
                                ) {
                                    "Music Sync paused."
                                } else {
                                    "Music Sync waiting for media."
                                }
                            )
                        },
                        onError = { message ->
                            android.util.Log.w(
                                "OneGlyph",
                                "Music Sync: " +
                                    message
                            )
                        }
                    )

                musicSync?.start()
            }

        android.util.Log.d(
            "OneGlyph",
            "Music Sync enabled persistently."
        )
    }

    private fun stopMusicSyncLoop() {
        musicSync?.close()
        musicSync = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "OneGlyph background",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Keeps OneGlyph running in the background."
                setShowBadge(false)
                setSound(
                    null,
                    null
                )
                enableVibration(
                    false
                )
            }
        )
    }

    private fun buildNotification():
        Notification {
        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(
                    this,
                    CHANNEL_ID
                )
            } else {
                Notification.Builder(
                    this
                )
            }

        return builder
            .setSmallIcon(
                R.drawable.ic_launcher_monochrome
            )
            .setContentTitle(
                "Keep Blinking!"
            )
            .setOngoing(
                true
            )
            .setCategory(
                Notification.CATEGORY_SERVICE
            )
            .build()
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(
            heartbeatRunnable
        )

        try {
            if (
                currentMode != MODE_NONE
            ) {
                scheduleRecovery(
                    this,
                    5_000L
                )
            }

            musicSync?.close()
            musicSync = null

            controller.stopPattern()
            controller.close()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    private fun writeHeartbeat() {
        getSharedPreferences(
            HEARTBEAT_PREF,
            MODE_PRIVATE
        ).edit()
            .putLong(
                HEARTBEAT_KEY,
                SystemClock.elapsedRealtime()
            )
            .apply()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}

class GlyphRecoveryReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult =
            goAsync()

        try {
            val appContext =
                context.applicationContext

            if (
                !PatternStore(appContext)
                    .appEnabled()
            ) {
                GlyphBackgroundService
                    .cancelRecovery(
                        appContext
                    )
                return
            }

            if (
                GlyphBackgroundService
                    .currentMode(
                        appContext
                    ) ==
                    "none"
            ) {
                GlyphBackgroundService
                    .cancelRecovery(
                        appContext
                    )
                return
            }

            if (
                !GlyphBackgroundService
                    .heartbeatIsFresh(
                        appContext
                    )
            ) {
                try {
                    GlyphBackgroundService
                        .ensureRunningFromRecovery(
                            appContext
                        )
                } catch (e: Throwable) {
                    android.util.Log.w(
                        "OneGlyph",
                        "Background recovery failed",
                        e
                    )
                }
            }

            GlyphBackgroundService
                .scheduleRecovery(
                    appContext
                )
        } finally {
            pendingResult.finish()
        }
    }
}
