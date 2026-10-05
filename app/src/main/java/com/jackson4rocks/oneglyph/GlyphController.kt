package com.jackson4rocks.oneglyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
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

class GlyphController(context: Context) : AutoCloseable {
    companion object {
        private const val TAG =
            "OneGlyph"

        private const val NOTHING_PACKAGE =
            "com.nothing.thirdparty"

        private const val NOTHING_SERVICE =
            "com.nothing.thirdparty.GlyphService"

        private const val BIND_ACTION =
            "com.nothing.thirdparty.bind_glyphservice"

        private const val DESCRIPTOR =
            "com.nothing.thirdparty.IGlyphService"

        private const val TX_SET_FRAME_COLORS = 1
        private const val TX_OPEN_SESSION = 2
        private const val TX_CLOSE_SESSION = 3
        private const val TX_REGISTER_SDK = 5

        private const val API_KEY =
            "test"

        private const val GALAXIAN_MODEL =
            "A001T"

        private const val MAX_BRIGHTNESS =
            4095

        private const val CONNECT_RETRY_MS =
            500L

        private const val READY_TIMEOUT_MS =
            8_000L

        private const val RECONNECT_COOLDOWN_MS =
            1_500L
    }

    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    @Volatile
    private var readySignal =
        CompletableDeferred<Unit>()

    @Volatile
    private var binder:
        IBinder? = null

    @Volatile
    private var ready = false

    @Volatile
    private var binding = false

    @Volatile
    private var bound = false

    @Volatile
    private var closed = false

    @Volatile
    private var lastConnectAttempt =
        0L

    private var effectJob:
        Job? = null

    private var statusListener:
        ((String) -> Unit)? = null

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder
            ) {
                if (closed) {
                    return
                }

                binder = service
                binding = false
                bound = true

                val waiter =
                    readySignal

                scope.launch {
                    try {
                        val targetDevice =
                            Build.MODEL.ifBlank {
                                GALAXIAN_MODEL
                            }

                        val registered =
                            transactRegisterSdk(
                                service,
                                targetDevice
                            )

                        if (!registered) {
                            throw IllegalStateException(
                                "STOCK GLYPH SERVICE REJECTED"
                            )
                        }

                        transactNoArgs(
                            service,
                            TX_OPEN_SESSION
                        )

                        ready = true
                        waiter.complete(Unit)

                        postStatus(
                            "READY • STOCK GLYPH • " +
                                targetDevice
                        )
                    } catch (e: Throwable) {
                        ready = false
                        binder = null

                        if (!waiter.isCompleted) {
                            waiter.completeExceptionally(
                                e
                            )
                        }

                        postStatus(
                            "GLYPH ERROR • " +
                                (
                                    e.message
                                        ?: "UNKNOWN"
                                )
                        )

                        safeUnbind()
                        scheduleReconnect()
                    }
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName
            ) {
                ready = false
                binder = null
                binding = false
                bound = false

                resetReadySignal()

                postStatus(
                    "GLYPH SERVICE DISCONNECTED"
                )

                scheduleReconnect()
            }

            override fun onBindingDied(
                name: ComponentName
            ) {
                ready = false
                binder = null
                binding = false
                bound = false

                resetReadySignal()

                postStatus(
                    "GLYPH BINDING DIED"
                )

                scheduleReconnect()
            }

            override fun onNullBinding(
                name: ComponentName
            ) {
                ready = false
                binder = null
                binding = false
                bound = false

                resetReadySignal()

                postStatus(
                    "GLYPH SERVICE RETURNED NO BINDER"
                )

                scheduleReconnect()
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
        if (
            closed ||
            binding ||
            ready
        ) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - lastConnectAttempt <
                RECONNECT_COOLDOWN_MS
        ) {
            return
        }

        lastConnectAttempt = now

        if (readySignal.isCompleted) {
            readySignal =
                CompletableDeferred()
        }

        val intent =
            Intent(BIND_ACTION).apply {
                component =
                    ComponentName(
                        NOTHING_PACKAGE,
                        NOTHING_SERVICE
                    )
            }

        try {
            binding = true

            val result =
                appContext.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )

            if (!result) {
                binding = false
                bound = false

                if (!readySignal.isCompleted) {
                    readySignal.completeExceptionally(
                        IllegalStateException(
                            "bindService returned false"
                        )
                    )
                }

                postStatus(
                    "GLYPH SERVICE NOT AVAILABLE"
                )

                scheduleReconnect()
                return
            }

            bound = true

            postStatus(
                "CONNECTING • STOCK GLYPH"
            )
        } catch (e: SecurityException) {
            binding = false
            bound = false

            if (!readySignal.isCompleted) {
                readySignal.completeExceptionally(
                    e
                )
            }

            Log.e(
                TAG,
                "Glyph service bind denied",
                e
            )

            postStatus(
                "GLYPH PERMISSION DENIED"
            )

            scheduleReconnect()
        } catch (e: Throwable) {
            binding = false
            bound = false

            if (!readySignal.isCompleted) {
                readySignal.completeExceptionally(
                    e
                )
            }

            Log.e(
                TAG,
                "Glyph service bind failed",
                e
            )

            postStatus(
                "GLYPH SERVICE ERROR"
            )

            scheduleReconnect()
        }
    }

    suspend fun awaitReady(
        timeoutMs: Long = READY_TIMEOUT_MS
    ): Boolean {
        if (ready) {
            return true
        }

        val deadline =
            SystemClock.elapsedRealtime() +
                timeoutMs.coerceAtLeast(250L)

        while (
            !closed &&
            SystemClock.elapsedRealtime() <
                deadline
        ) {
            if (ready) {
                return true
            }

            connect()

            val remaining =
                (
                    deadline -
                        SystemClock.elapsedRealtime()
                ).coerceAtLeast(1L)

            val waiter =
                readySignal

            try {
                val ok =
                    withTimeoutOrNull(
                        remaining.coerceAtMost(
                            1_000L
                        )
                    ) {
                        waiter.await()
                        true
                    } ?: false

                if (ok && ready) {
                    return true
                }
            } catch (_: Throwable) {
                // A failed bind is retried until the deadline.
            }

            delay(40L)
        }

        return ready
    }

    fun isReady(): Boolean =
        ready

    fun targetDevice(): String =
        Build.MODEL.ifBlank {
            GALAXIAN_MODEL
        }

    fun setBrightness(
        brightness: Int
    ) {
        stopPatternOnly()

        effectJob =
            scope.launch {
                if (
                    awaitReady()
                ) {
                    sendFrame(
                        brightness
                    )
                }
            }
    }

    fun off() {
        stopPatternOnly()

        effectJob =
            scope.launch {
                if (
                    awaitReady()
                ) {
                    sendFrame(0)
                }
            }
    }

    fun playPattern(
        steps: List<GlyphStep>,
        repeat: Int = 1
    ) {
        stopPatternOnly()

        val clean =
            steps
                .filter {
                    it.durationMs > 0
                }
                .take(32)

        if (clean.isEmpty()) {
            return
        }

        effectJob =
            scope.launch {
                if (
                    !awaitReady()
                ) {
                    postStatus(
                        "GLYPH NOT READY"
                    )
                    return@launch
                }

                repeat(
                    repeat.coerceIn(
                        1,
                        32
                    )
                ) {
                    for (step in clean) {
                        if (!isActive) {
                            return@launch
                        }

                        if (
                            !sendFrame(
                                step.brightness
                            )
                        ) {
                            return@launch
                        }

                        delay(
                            step.durationMs
                        )
                    }
                }

                sendFrame(0)
            }
    }

    fun blink(
        brightness: Int
    ) {
        playPattern(
            listOf(
                GlyphStep(
                    brightness,
                    180
                ),
                GlyphStep(
                    0,
                    180
                )
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

        effectJob =
            scope.launch {
                if (
                    !awaitReady()
                ) {
                    postStatus(
                        "GLYPH NOT READY"
                    )
                    return@launch
                }

                while (isActive) {
                    if (
                        !sendFrame(
                            brightness.coerceIn(
                                0,
                                MAX_BRIGHTNESS
                            )
                        )
                    ) {
                        return@launch
                    }

                    delay(
                        onMs.coerceAtLeast(35L)
                    )

                    if (
                        !sendFrame(0)
                    ) {
                        return@launch
                    }

                    delay(
                        offMs.coerceAtLeast(35L)
                    )
                }
            }
    }

    fun pulse(
        brightness: Int,
        periodMs: Long = 1100
    ) {
        stopPatternOnly()

        effectJob =
            scope.launch {
                if (
                    !awaitReady()
                ) {
                    return@launch
                }

                val steps = 20

                repeat(2) {
                    for (i in 0..steps) {
                        if (
                            !isActive
                        ) {
                            return@launch
                        }

                        val wave =
                            (
                                1.0 -
                                    kotlin.math.cos(
                                        i *
                                            kotlin.math.PI /
                                            steps
                                    )
                            ) /
                                2.0

                        if (
                            !sendFrame(
                                (
                                    brightness *
                                        wave
                                ).toInt()
                            )
                        ) {
                            return@launch
                        }

                        delay(
                            periodMs /
                                (steps * 2)
                        )
                    }

                    for (
                        i in steps downTo 0
                    ) {
                        if (
                            !isActive
                        ) {
                            return@launch
                        }

                        val wave =
                            (
                                1.0 -
                                    kotlin.math.cos(
                                        i *
                                            kotlin.math.PI /
                                            steps
                                    )
                            ) /
                                2.0

                        if (
                            !sendFrame(
                                (
                                    brightness *
                                        wave
                                ).toInt()
                            )
                        ) {
                            return@launch
                        }

                        delay(
                            periodMs /
                                (steps * 2)
                        )
                    }
                }

                sendFrame(0)
            }
    }

    fun heartbeat(
        brightness: Int
    ) {
        playPattern(
            GlyphPatterns.heartbeat.map {
                it.copy(
                    brightness =
                        if (
                            it.brightness == 0
                        ) {
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

        if (ready) {
            sendFrame(0)
        }
    }

    private fun stopPatternOnly() {
        effectJob?.cancel()
        effectJob = null
    }

    private fun sendFrame(
        brightness: Int
    ): Boolean {
        val service =
            binder

        if (
            service == null ||
            !ready
        ) {
            return false
        }

        val frame =
            intArrayOf(
                brightness.coerceIn(
                    0,
                    MAX_BRIGHTNESS
                )
            )

        try {
            val request =
                Parcel.obtain()

            val reply =
                Parcel.obtain()

            try {
                request.writeInterfaceToken(
                    DESCRIPTOR
                )

                request.writeIntArray(
                    frame
                )

                val transacted =
                    service.transact(
                        TX_SET_FRAME_COLORS,
                        request,
                        reply,
                        0
                    )

                if (!transacted) {
                    throw IllegalStateException(
                        "Binder transaction returned false"
                    )
                }

                reply.readException()
            } finally {
                request.recycle()
                reply.recycle()
            }

            return true
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "setFrameColors failed",
                e
            )

            ready = false
            binder = null
            resetReadySignal()

            postStatus(
                "GLYPH WRITE FAILED"
            )

            scheduleReconnect()

            return false
        }
    }

    private fun transactNoArgs(
        service: IBinder,
        code: Int
    ) {
        val request =
            Parcel.obtain()

        val reply =
            Parcel.obtain()

        try {
            request.writeInterfaceToken(
                DESCRIPTOR
            )

            val transacted =
                service.transact(
                    code,
                    request,
                    reply,
                    0
                )

            if (!transacted) {
                throw IllegalStateException(
                    "Binder transaction returned false"
                )
            }

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
        val request =
            Parcel.obtain()

        val reply =
            Parcel.obtain()

        return try {
            request.writeInterfaceToken(
                DESCRIPTOR
            )

            request.writeString(
                API_KEY
            )

            request.writeString(
                targetDevice
            )

            val transacted =
                service.transact(
                    TX_REGISTER_SDK,
                    request,
                    reply,
                    0
                )

            if (!transacted) {
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
        if (
            readySignal.isCompleted
        ) {
            readySignal =
                CompletableDeferred()
        }
    }

    private fun scheduleReconnect() {
        if (closed) {
            return
        }

        scope.launch {
            delay(
                CONNECT_RETRY_MS
            )

            if (
                !closed &&
                !ready &&
                !binding
            ) {
                connect()
            }
        }
    }

    private fun safeUnbind() {
        if (!bound) {
            return
        }

        try {
            appContext.unbindService(
                serviceConnection
            )
        } catch (_: Throwable) {
        } finally {
            bound = false
            binding = false
        }
    }

    private fun postStatus(
        message: String
    ) {
        val listener =
            statusListener
                ?: return

        if (
            Looper.myLooper() ==
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
        if (closed) {
            return
        }

        closed = true
        stopPatternOnly()

        val service =
            binder

        if (
            service != null &&
            ready
        ) {
            try {
                transactNoArgs(
                    service,
                    TX_CLOSE_SESSION
                )
            } catch (e: Throwable) {
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

        safeUnbind()

        scope.cancel()
    }
}
