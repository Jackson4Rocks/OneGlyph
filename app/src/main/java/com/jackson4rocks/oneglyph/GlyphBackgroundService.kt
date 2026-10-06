package com.jackson4rocks.oneglyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class GlyphBackgroundService : Service() {

    companion object {
        private const val CHANNEL_ID =
            "oneglyph_background"

        private const val NOTIFICATION_ID =
            1001

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

        restoreMode()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
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

        return START_STICKY
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {
        android.util.Log.d(
            "OneGlyph",
            "UI task removed; background mode remains active: " +
                currentMode
        )

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
            }
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
        try {
            musicSync?.close()
            musicSync = null

            controller.stopPattern()
            controller.close()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
