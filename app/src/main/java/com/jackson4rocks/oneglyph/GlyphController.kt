package com.jackson4rocks.oneglyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stock-only Glyph controller for Nothing Phone (3a) Lite.
 *
 * Talks exclusively to the stock Nothing Glyph service
 * (`com.nothing.thirdparty`) via Binder. No sysfs, no `su`
 * fallback, no custom-ROM paths.
 */
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

        private const val FALLBACK_MODEL = "A001T"
        const val MAX_BRIGHTNESS = 4095

        private const val CONNECT_RETRY_MS = 500L
        private const val READY_TIMEOUT_MS = 8_000L
        private const val RECONNECT_COOLDOWN_MS = 1_500L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val targetDevice: String =
        Build.MODEL.ifBlank { FALLBACK_MODEL }

    @Volatile
    private var readySignal = CompletableDeferred<Unit>()

    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var ready = false

    @Volatile
    private var binding = false

    @Volatile
    private var bound = false

    @Volatile
    private var closed = false

    @Volatile
    private var lastConnectAttempt = 0L

    private var effectJob: Job? = null
    private var statusListener: ((String) -> Unit)? = null

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (closed) return

                binder = service
                binding = false
                bound = true

                val waiter = readySignal
                scope.launch {
                    try {
                        if (!transactRegisterSdk(service, targetDevice)) {
                            throw IllegalStateException("STOCK GLYPH SERVICE REJECTED")
                        }
                        transactNoArgs(service, TX_OPEN_SESSION)
                        ready = true
                        waiter.complete(Unit)
                        postStatus("READY • STOCK GLYPH • $targetDevice")
                    } catch (e: Throwable) {
                        ready = false
                        binder = null
                        if (!waiter.isCompleted) waiter.completeExceptionally(e)
                        postStatus("GLYPH ERROR • ${e.message ?: "UNKNOWN"}")
                        safeUnbind()
                        scheduleReconnect()
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                onBindingLost("GLYPH SERVICE DISCONNECTED")
            }

            override fun onBindingDied(name: ComponentName) {
                onBindingLost("GLYPH BINDING DIED")
            }

            override fun onNullBinding(name: ComponentName) {
                onBindingLost("GLYPH SERVICE RETURNED NO BINDER")
            }

            private fun onBindingLost(message: String) {
                ready = false
                binder = null
                binding = false
                bound = false
                resetReadySignal()
                postStatus(message)
                scheduleReconnect()
            }
        }

    init {
        connect()
    }

    fun setStatusListener(listener: ((String) -> Unit)?) {
        statusListener = listener
    }

    fun connect() {
        if (closed || binding || ready) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastConnectAttempt < RECONNECT_COOLDOWN_MS) return
        lastConnectAttempt = now

        if (readySignal.isCompleted) readySignal = CompletableDeferred()

        val intent = Intent(BIND_ACTION).apply {
            component = ComponentName(NOTHING_PACKAGE, NOTHING_SERVICE)
        }

        try {
            binding = true
            val result = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!result) {
                onBindFailed(IllegalStateException("bindService returned false"), "GLYPH SERVICE NOT AVAILABLE")
                return
            }
            bound = true
            postStatus("CONNECTING • STOCK GLYPH")
        } catch (e: SecurityException) {
            Log.e(TAG, "Glyph service bind denied", e)
            onBindFailed(e, "GLYPH PERMISSION DENIED")
        } catch (e: Throwable) {
            Log.e(TAG, "Glyph service bind failed", e)
            onBindFailed(e, "GLYPH SERVICE ERROR")
        }
    }

    private fun onBindFailed(e: Throwable, status: String) {
        binding = false
        bound = false
        if (!readySignal.isCompleted) readySignal.completeExceptionally(e)
        postStatus(status)
        scheduleReconnect()
    }

    suspend fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS): Boolean {
        if (ready) return true
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(250L)
        while (!closed && SystemClock.elapsedRealtime() < deadline) {
            if (ready) return true
            connect()
            val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            val waiter = readySignal
            try {
                val ok = withTimeoutOrNull(remaining.coerceAtMost(1_000L)) {
                    waiter.await()
                    true
                } ?: false
                if (ok && ready) return true
            } catch (_: Throwable) {
                // Retried until the deadline.
            }
            delay(40L)
        }
        return ready
    }

    fun isReady(): Boolean = ready

    fun backendLabel(): String =
        if (ready) "READY • STOCK GLYPH" else "NO BACKEND"

    fun playPattern(steps: List<GlyphStep>, repeat: Int = 1, maxSteps: Int = 32) {
        stopPatternOnly()
        val clean = steps.filter { it.durationMs > 0 }.take(maxSteps.coerceIn(1, 256))
        if (clean.isEmpty()) return
        effectJob = scope.launch {
            if (!awaitReady()) {
                postStatus("GLYPH NOT READY")
                return@launch
            }
            repeat(repeat.coerceIn(1, 32)) {
                for (step in clean) {
                    if (!isActive) return@launch
                    if (!sendFrame(step.brightness)) return@launch
                    delay(step.durationMs)
                }
            }
            sendFrame(0)
        }
    }

    fun fastFlashLoop(brightness: Int = MAX_BRIGHTNESS, onMs: Long = 70, offMs: Long = 70) {
        stopPatternOnly()
        effectJob = scope.launch {
            if (!awaitReady()) {
                postStatus("GLYPH NOT READY")
                return@launch
            }
            val level = brightness.coerceIn(0, MAX_BRIGHTNESS)
            val on = onMs.coerceAtLeast(35L)
            val off = offMs.coerceAtLeast(35L)
            while (isActive) {
                if (!sendFrame(level)) return@launch
                delay(on)
                if (!sendFrame(0)) return@launch
                delay(off)
            }
        }
    }

    fun stopPattern() {
        stopPatternOnly()
        if (ready) sendFrame(0)
    }

    /**
     * Steady metronome with an accented downbeat every 4 beats.
     * Runs until [stopPattern] cancels it.
     */
    fun metronomeLoop(bpm: Int) {
        stopPatternOnly()
        effectJob = scope.launch {
            if (!awaitReady()) {
                postStatus("GLYPH NOT READY")
                return@launch
            }
            val interval = (60_000L / bpm.coerceIn(30, 240)).coerceAtLeast(100L)
            val on = 70L
            var beat = 0
            while (isActive) {
                val level = if (beat % 4 == 0) MAX_BRIGHTNESS else 2000
                if (!sendFrame(level)) return@launch
                delay(on)
                if (!sendFrame(0)) return@launch
                delay((interval - on).coerceAtLeast(50L))
                beat++
            }
        }
    }

    private fun stopPatternOnly() {
        effectJob?.cancel()
        effectJob = null
    }

    private fun sendFrame(brightness: Int): Boolean {
        val service = binder
        if (service == null || !ready) return false
        val frame = intArrayOf(brightness.coerceIn(0, MAX_BRIGHTNESS))
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeIntArray(frame)
            if (!service.transact(TX_SET_FRAME_COLORS, request, reply, 0)) {
                throw IllegalStateException("Binder transaction returned false")
            }
            reply.readException()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "setFrameColors failed", e)
            ready = false
            binder = null
            resetReadySignal()
            postStatus("GLYPH WRITE FAILED")
            scheduleReconnect()
            return false
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun transactNoArgs(service: IBinder, code: Int) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            if (!service.transact(code, request, reply, 0)) {
                throw IllegalStateException("Binder transaction returned false")
            }
            reply.readException()
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun transactRegisterSdk(service: IBinder, target: String): Boolean {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeString(API_KEY)
            request.writeString(target)
            if (!service.transact(TX_REGISTER_SDK, request, reply, 0)) {
                false
            } else {
                reply.readException()
                reply.readInt() != 0
            }
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun resetReadySignal() {
        if (readySignal.isCompleted) readySignal = CompletableDeferred()
    }

    private fun scheduleReconnect() {
        if (closed) return
        scope.launch {
            delay(CONNECT_RETRY_MS)
            if (!closed && !ready && !binding) connect()
        }
    }

    private fun safeUnbind() {
        if (!bound) return
        try {
            appContext.unbindService(serviceConnection)
        } catch (_: Throwable) {
        } finally {
            bound = false
            binding = false
        }
    }

    private fun postStatus(message: String) {
        val listener = statusListener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener(message)
        } else {
            mainHandler.post { listener(message) }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stopPatternOnly()
        val service = binder
        if (service != null && ready) {
            try {
                transactNoArgs(service, TX_CLOSE_SESSION)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to close Glyph session", e)
            }
        }
        ready = false
        binder = null
        binding = false
        safeUnbind()
        scope.cancel()
    }
}
