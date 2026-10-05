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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class GlyphController(context: Context) : AutoCloseable {
    companion object {
        private const val TAG = "OneGlyph"
        private const val NOTHING_PACKAGE = "com.nothing.thirdparty"
        private const val NOTHING_SERVICE = "com.nothing.thirdparty.GlyphService"
        private const val BIND_ACTION = "com.nothing.thirdparty.bind_glyphservice"
        private const val DESCRIPTOR = "com.nothing.thirdparty.IGlyphService"

        private const val TX_SET_FRAME_COLORS = 1
        private const val TX_OPEN_SESSION = 2
        private const val TX_CLOSE_SESSION = 3
        private const val TX_REGISTER_SDK = 5

        private const val API_KEY = "test"
        private const val GALAXIAN_MODEL = "A001T"
        private const val MAX_BRIGHTNESS = 4095
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readySignal = CompletableDeferred<Unit>()

    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var ready = false

    @Volatile
    private var binding = false

    private var statusListener: ((String) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder
        ) {
            binder = service
            binding = false

            scope.launch {
                try {
                    val targetDevice = Build.MODEL.ifBlank { GALAXIAN_MODEL }
                    val registered = transactRegisterSdk(
                        service,
                        targetDevice
                    )

                    if (!registered) {
                        ready = false
                        postStatus("GLYPH SERVICE REJECTED")
                        return@launch
                    }

                    transactNoArgs(service, TX_OPEN_SESSION)
                    ready = true
                    readySignal.complete(Unit)
                    postStatus(
                        "READY • STOCK GLYPH • " + targetDevice
                    )
                } catch (e: Exception) {
                    ready = false
                    postStatus(
                        "GLYPH ERROR • " +
                            (e.message ?: "UNKNOWN")
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            ready = false
            binder = null
            binding = false
            postStatus("GLYPH SERVICE DISCONNECTED")
        }

        override fun onBindingDied(name: ComponentName) {
            ready = false
            binder = null
            binding = false
            postStatus("GLYPH BINDING DIED")
        }

        override fun onNullBinding(name: ComponentName) {
            ready = false
            binder = null
            binding = false
            postStatus(
                "GLYPH SERVICE RETURNED NO BINDER"
            )
        }
    }

    init {
        connect()
    }

    fun setStatusListener(
        listener: ((String) -> Unit)?
    ) {
        statusListener = listener
    }

    fun connect() {
        if (binding || ready) return

        val intent = Intent(BIND_ACTION).apply {
            component = ComponentName(
                NOTHING_PACKAGE,
                NOTHING_SERVICE
            )
        }

        try {
            binding = appContext.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )

            postStatus(
                if (binding) {
                    "CONNECTING • STOCK GLYPH"
                } else {
                    "GLYPH SERVICE NOT AVAILABLE"
                }
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Glyph service bind denied", e)
            postStatus("GLYPH PERMISSION DENIED")
        } catch (e: Exception) {
            Log.e(TAG, "Glyph service bind failed", e)
            postStatus("GLYPH SERVICE ERROR")
        }
    }

    suspend fun awaitReady(timeoutMs: Long = 2500): Boolean {
        if (ready) return true

        return withTimeoutOrNull(timeoutMs) {
            readySignal.await()
            true
        } ?: false
    }

    fun isReady(): Boolean = ready

    fun targetDevice(): String =
        Build.MODEL.ifBlank { GALAXIAN_MODEL }

    fun setBrightness(brightness: Int) {
        stopPatternOnly()
        sendFrame(brightness)
    }

    fun off() {
        stopPatternOnly()
        sendFrame(0)
    }

    fun playPattern(
        steps: List<GlyphStep>,
        repeat: Int = 1
    ) {
        stopPatternOnly()

        val clean = steps
            .filter { it.durationMs > 0 }
            .take(32)

        if (clean.isEmpty()) return

        scope.launch {
            repeat(repeat.coerceIn(1, 32)) {
                for (step in clean) {
                    if (!isActive || !ready) {
                        return@launch
                    }

                    sendFrame(step.brightness)
                    delay(step.durationMs)
                }
            }

            sendFrame(0)
        }
    }

    fun blink(brightness: Int) {
        playPattern(
            listOf(
                GlyphStep(brightness, 180),
                GlyphStep(0, 180)
            ),
            repeat = 3
        )
    }

    fun fastFlashLoop(
        brightness: Int = 4095,
        onMs: Long = 70,
        offMs: Long = 70
    ) {
        stopPatternOnly()

        scope.launch {
            while (isActive && ready) {
                sendFrame(
                    brightness.coerceIn(
                        0,
                        MAX_BRIGHTNESS
                    )
                )
                delay(onMs.coerceAtLeast(35L))

                sendFrame(0)

                delay(offMs.coerceAtLeast(35L))
            }

            sendFrame(0)
        }
    }

    fun pulse(
        brightness: Int,
        periodMs: Long = 1100
    ) {
        stopPatternOnly()

        scope.launch {
            val steps = 20

            repeat(2) {
                for (i in 0..steps) {
                    if (!isActive || !ready) {
                        return@launch
                    }

                    val wave =
                        (1.0 - kotlin.math.cos(
                            i * kotlin.math.PI / steps
                        )) / 2.0

                    sendFrame((brightness * wave).toInt())
                    delay(periodMs / (steps * 2))
                }

                for (i in steps downTo 0) {
                    if (!isActive || !ready) {
                        return@launch
                    }

                    val wave =
                        (1.0 - kotlin.math.cos(
                            i * kotlin.math.PI / steps
                        )) / 2.0

                    sendFrame((brightness * wave).toInt())
                    delay(periodMs / (steps * 2))
                }
            }

            sendFrame(0)
        }
    }

    fun heartbeat(brightness: Int) {
        playPattern(
            GlyphPatterns.heartbeat.map {
                it.copy(
                    brightness = if (it.brightness == 0) {
                        0
                    } else {
                        brightness
                    }
                )
            }
        )
    }

    fun stopPattern() {
        stopPatternOnly()
        sendFrame(0)
    }

    private fun stopPatternOnly() {
        scope.coroutineContext.cancelChildren()
    }

    private fun sendFrame(brightness: Int) {
        val service = binder ?: return
        if (!ready) return

        val frame = intArrayOf(
            brightness.coerceIn(0, MAX_BRIGHTNESS)
        )

        try {
            val request = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                request.writeInterfaceToken(DESCRIPTOR)
                request.writeIntArray(frame)

                service.transact(
                    TX_SET_FRAME_COLORS,
                    request,
                    reply,
                    0
                )

                reply.readException()
            } finally {
                request.recycle()
                reply.recycle()
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "setFrameColors failed",
                e
            )

            ready = false
            postStatus("GLYPH WRITE FAILED")
        }
    }

    private fun transactNoArgs(
        service: IBinder,
        code: Int
    ) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            request.writeInterfaceToken(DESCRIPTOR)
            service.transact(
                code,
                request,
                reply,
                0
            )
            reply.readException()
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun transactRegisterSdk(
        service: IBinder,
        targetDevice: String
    ): Boolean {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()

        return try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeString(API_KEY)
            request.writeString(targetDevice)

            service.transact(
                TX_REGISTER_SDK,
                request,
                reply,
                0
            )

            reply.readException()
            reply.readInt() != 0
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun postStatus(message: String) {
        val listener = statusListener ?: return

        if (Looper.myLooper() ==
            Looper.getMainLooper()
        ) {
            listener.invoke(message)
        } else {
            android.os.Handler(
                Looper.getMainLooper()
            ).post {
                listener.invoke(message)
            }
        }
    }

    override fun close() {
        stopPatternOnly()

        val service = binder

        if (service != null && ready) {
            try {
                transactNoArgs(
                    service,
                    TX_CLOSE_SESSION
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to close Glyph session",
                    e
                )
            }
        }

        ready = false
        binder = null
        binding = false

        try {
            appContext.unbindService(
                serviceConnection
            )
        } catch (_: Exception) {
        }

        scope.cancel()
    }
}
