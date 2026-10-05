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

        const val ACTION_KEEP_ALIVE =
            "com.jackson4rocks.oneglyph.action.KEEP_ALIVE"

        const val ACTION_STOP =
            "com.jackson4rocks.oneglyph.action.STOP"

        private const val PREFS =
            "oneglyph_background"

        private const val TOY_ENABLED =
            "toy_enabled"

        fun startToy(context: Context) {
            val intent =
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(ACTION_TOY_ON)

            start(
                context,
                intent
            )
        }

        fun stopToy(context: Context) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(ACTION_TOY_OFF)
            )
        }

        fun ensureRunning(context: Context) {
            if (!PatternStore(context).appEnabled()) {
                return
            }

            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(ACTION_KEEP_ALIVE)
            )
        }

        fun stopService(context: Context) {
            start(
                context,
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                ).setAction(ACTION_STOP)
            )
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

    private var toyRunning =
        false

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

        toyRunning =
            getSharedPreferences(
                PREFS,
                MODE_PRIVATE
            ).getBoolean(
                TOY_ENABLED,
                false
            )

        if (toyRunning) {
            startToyLoop()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_TOY_ON -> {
                saveToyState(true)
                startToyLoop()
            }

            ACTION_TOY_OFF -> {
                saveToyState(false)
                stopToyLoop()
            }

            ACTION_KEEP_ALIVE -> {
                if (!PatternStore(this).appEnabled()) {
                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (!::controller.isInitialized) {
                    controller =
                        GlyphController(
                            applicationContext
                        )
                }

                if (toyRunning) {
                    startToyLoop()
                }
            }

            ACTION_STOP -> {
                saveToyState(false)
                stopToyLoop()

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {
        if (toyRunning) {
            try {
                val restart =
                    Intent(
                        applicationContext,
                        GlyphBackgroundService::class.java
                    ).setAction(
                        ACTION_TOY_ON
                    )

                if (
                    Build.VERSION.SDK_INT >= 26
                ) {
                    startForegroundService(
                        restart
                    )
                } else {
                    startService(restart)
                }
            } catch (e: Throwable) {
                android.util.Log.w(
                    "OneGlyph",
                    "Could not request background service restart",
                    e
                )
            }
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {
        if (PatternStore(this).appEnabled()) {
            try {
                val restart =
                    Intent(
                        applicationContext,
                        GlyphBackgroundService::class.java
                    ).setAction(
                        ACTION_KEEP_ALIVE
                    )

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(restart)
                } else {
                    startService(restart)
                }
            } catch (e: Throwable) {
                android.util.Log.w(
                    "OneGlyph",
                    "Could not restart background service",
                    e
                )
            }
        }

        super.onTaskRemoved(rootIntent)
    }

    private fun startToyLoop {
        if (!toyRunning) {
            toyRunning = true
        }

        controller.fastFlashLoop()
    }

    private fun stopToyLoop() {
        toyRunning = false
        controller.stopPattern()
    }

    private fun saveToyState(
        enabled: Boolean
    ) {
        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                TOY_ENABLED,
                enabled
            )
            .apply()
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
                setSound(null, null)
                enableVibration(false)
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
                Notification.Builder(this)
            }

        return builder
            .setSmallIcon(
                R.drawable.ic_launcher_monochrome
            )
            .setContentTitle(
                "Keep Blinking!"
            )
            .setOngoing(true)
            .setCategory(
                Notification.CATEGORY_SERVICE
            )
            .build()
    }

    override fun onDestroy() {
        try {
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
