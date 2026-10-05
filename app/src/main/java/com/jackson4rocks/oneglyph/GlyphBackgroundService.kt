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
            context.stopService(
                Intent(
                    context,
                    GlyphBackgroundService::class.java
                )
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

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
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

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
                stopSelf()
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

    private fun startToyLoop() {
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
                "OneGlyph background effects",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Keeps continuous OneGlyph effects running."
                setShowBadge(false)
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
                "OneGlyph"
            )
            .setContentText(
                "Background Glyph effect is running."
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
