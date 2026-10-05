package com.jackson4rocks.oneglyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stock Nothing OS Glyph controller.
 *
 * Phone (3a) Lite exposes one Glyph Light dot and does not expose that dot
 * through the ordinary app-facing LightsManager list. Nothing's stock Glyph
 * broker owns the privileged LightsManager session, so OneGlyph talks to that
 * broker through its exported IGlyphService interface instead.
 */
class GlyphController(context: Context) : AutoCloseable {
    companion object {
        private const val TAG = "OneGlyph"

        private const val NOTHING_PACKAGE = "com.nothing.thirdparty"
        private const val NOTHING_SERVICE = "com.nothing.thirdparty.GlyphService"
        private const val BIND_ACTION = "com.nothing.thirdparty.bind_glyphservice"
        private const val DESCRIPTOR = "com.nothing.thirdparty.IGlyphService"

        // IGlyphService transaction codes.
        private const val TX_SET_FRAME_COLORS = 1
        private const val TX_OPEN_SESSION = 2
        private const val TX_CLOSE_SESSION = 3
        private const val TX_REGISTER_SDK = 5

        private const val API_KEY = "test"
        private const val GALAXIAN_MODEL = "A001T"
        private const val MAX_BRIGHTNESS = 4096

        // Phone (3a) Lite has a single Glyph Light dot.
        private const val FRAME_LENGTH = 1
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var ready = false

    @Volatile
    private var binding = false

    private var statusListener: ((String) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            binding = false
            scope.launch {
                try {
                    val targetDevice = if (Build.MODEL.isNullOrBlank()) {
                        GALAXIAN_MODEL
                    } else {
                        Build.MODEL
                    }

                    val registered = transactRegisterSdk(service, targetDevice)
                    if (!registered) {
                        ready = false
                        postStatus("Nothing Glyph service rejected registration")
                        return@launch
                    }

                    transactNoArgs(service, TX_OPEN_SESSION)
                    ready = true
                    postStatus("Ready • Stock Glyph • $targetDevice")
                } catch (e: Exception) {
                    ready = false
                    Log.e(TAG, "Failed to initialize Nothing Glyph service", e)
                    postStatus("Glyph service error: ${e.message ?: "unknown error"}")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            ready = false
            binder = null
            binding = false
            postStatus("Glyph service disconnected")
        }

        override fun onBindingDied(name: ComponentName) {
            ready = false
            binder = null
            binding = false
            postStatus("Glyph service binding died")
        }

        override fun onNullBinding(name: ComponentName) {
            ready = false
            binder = null
            binding = false
            postStatus("Nothing Glyph service returned no binder")
        }
    }

    init {
        connect()
    }

    fun setStatusListener(listener: ((String) -> Unit)?) {
        statusListener = listener
    }

    fun connect() {
        if (binding || ready) return

        val intent = Intent(BIND_ACTION).apply {
            component = ComponentName(NOTHING_PACKAGE, NOTHING_SERVICE)
        }

        try {
            binding = appContext.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )

            if (binding) {
                postStatus("Connecting to Stock Glyph…")
            } else {
                postStatus("Could not bind to Nothing Glyph service")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Glyph service bind denied", e)
            postStatus("Glyph service permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Glyph service bind failed", e)
            postStatus("Glyph service unavailable")
        }
    }

    fun isReady(): Boolean = ready

    fun targetDevice(): String = Build.MODEL.ifBlank { GALAXIAN_MODEL }

    fun setBrightness(brightness: Int) {
        stopPatternOnly()
        sendAsync(brightness.coerceIn(0, MAX_BRIGHTNESS))
    }

    fun off() {
        sendAsync(0)
    }

    fun blink(brightness: Int, onMs: Long = 180, offMs: Long = 180) {
        stopPatternOnly()
        val value = brightness.coerceIn(0, MAX_BRIGHTNESS)
        scope.launch {
            while (isActive && ready) {
                sendFrame(value)
                delay(onMs)
                sendFrame(0)
                delay(offMs)
            }
        }
    }

    fun pulse(brightness: Int, periodMs: Long = 1100) {
        stopPatternOnly()
        val value = brightness.coerceIn(0, MAX_BRIGHTNESS)
        scope.launch {
            val steps = 24
            while (isActive && ready) {
                for (i in 0..steps) {
                    if (!isActive || !ready) break
                    val wave = (1.0 - kotlin.math.cos(i * kotlin.math.PI / steps)) / 2.0
                    sendFrame((value * wave).toInt())
                    delay(periodMs / (steps * 2))
                }
                for (i in steps downTo 0) {
                    if (!isActive || !ready) break
                    val wave = (1.0 - kotlin.math.cos(i * kotlin.math.PI / steps)) / 2.0
                    sendFrame((value * wave).toInt())
                    delay(periodMs / (steps * 2))
                }
            }
        }
    }

    fun heartbeat(brightness: Int) {
        stopPatternOnly()
        val value = brightness.coerceIn(0, MAX_BRIGHTNESS)
        scope.launch {
            while (isActive && ready) {
                sendFrame(value)
                delay(120)
                sendFrame(0)
                delay(100)
                sendFrame(value)
                delay(180)
                sendFrame(0)
                delay(700)
            }
        }
    }

    fun stopPattern() {
        stopPatternOnly()
        sendAsync(0)
    }

    private fun stopPatternOnly() {
        scope.coroutineContext.cancelChildren()
    }

    private fun sendAsync(brightness: Int) {
        scope.launch {
            sendFrame(brightness)
        }
    }

    private fun sendFrame(brightness: Int) {
        val service = binder ?: return
        if (!ready) return

        val value = brightness.coerceIn(0, MAX_BRIGHTNESS)
        val frame = IntArray(FRAME_LENGTH) { value }

        try {
            val request = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                request.writeInterfaceToken(DESCRIPTOR)
                request.writeIntArray(frame)
                service.transact(TX_SET_FRAME_COLORS, request, reply, 0)
                reply.readException()
            } finally {
                request.recycle()
                reply.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "setFrameColors failed", e)
            ready = false
            postStatus("Glyph write failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun transactNoArgs(service: IBinder, code: Int) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            service.transact(code, request, reply, 0)
            reply.readException()
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun transactRegisterSdk(service: IBinder, targetDevice: String): Boolean {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeString(API_KEY)
            request.writeString(targetDevice)
            service.transact(TX_REGISTER_SDK, request, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun postStatus(message: String) {
        val listener = statusListener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.invoke(message)
        } else {
            android.os.Handler(Looper.getMainLooper()).post {
                listener.invoke(message)
            }
        }
    }

    override fun close() {
        stopPatternOnly()
        val service = binder
        if (service != null && ready) {
            scope.launch {
                try {
                    transactNoArgs(service, TX_CLOSE_SESSION)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close Glyph session", e)
                }
            }
        }

        ready = false
        binder = null
        binding = false
        try {
            appContext.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        scope.cancel()
    }
}
