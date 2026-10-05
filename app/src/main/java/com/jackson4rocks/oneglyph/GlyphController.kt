package com.jackson4rocks.oneglyph

import android.content.Context
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GlyphLight(
    val id: Int,
    val ordinal: Int,
    val type: Int,
)

class GlyphController(context: Context) : AutoCloseable {
    companion object {
        private const val TAG = "OneGlyph"
        val preferredIds = listOf(115, 109, 108, 118, 117, 113, 106, 103, 102)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lightsManager: LightsManager? =
        appContext.getSystemService(LightsManager::class.java)

    private var session: LightsManager.LightsSession? = null
    private var selected: Light? = null

    fun availableLights(): List<GlyphLight> {
        return try {
            lightsManager?.lights
                ?.map { GlyphLight(it.id, it.ordinal, it.type) }
                ?.sortedBy { it.id }
                ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "CONTROL_DEVICE_LIGHTS was not granted", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enumerate device lights", e)
            emptyList()
        }
    }

    fun selectLight(id: Int): Boolean {
        val manager = lightsManager ?: return false
        return try {
            val light = manager.lights.firstOrNull { it.id == id } ?: return false
            selected = light
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to select light " + id, e)
            false
        }
    }

    fun autoSelect(): Int? {
        val lights = availableLights()
        val preferred = preferredIds.firstNotNullOfOrNull { wanted ->
            lights.firstOrNull { it.id == wanted }
        }
        val fallback = lights.firstOrNull { it.id !in setOf(0, 1) }
        val picked = preferred ?: fallback

        selected = picked?.let { light ->
            try {
                lightsManager?.lights?.firstOrNull { it.id == light.id }
            } catch (e: Exception) {
                null
            }
        }
        return picked?.id
    }

    fun setBrightness(brightness: Int) {
        val light = selected ?: return
        send(light, brightnessToRgb(brightness))
    }

    fun off() {
        selected?.let { send(it, 0) }
    }

    fun blink(brightness: Int, onMs: Long = 180, offMs: Long = 180) {
        stopPattern()
        val light = selected ?: return
        scope.launch {
            while (isActive) {
                send(light, brightnessToRgb(brightness))
                delay(onMs)
                send(light, 0)
                delay(offMs)
            }
        }
    }

    fun pulse(brightness: Int, periodMs: Long = 1100) {
        stopPattern()
        val light = selected ?: return
        scope.launch {
            val steps = 24
            while (isActive) {
                for (i in 0..steps) {
                    if (!isActive) break
                    val wave = (1.0 - kotlin.math.cos(i * kotlin.math.PI / steps)) / 2.0
                    send(light, brightnessToRgb((brightness * wave).toInt()))
                    delay(periodMs / (steps * 2))
                }
                for (i in steps downTo 0) {
                    if (!isActive) break
                    val wave = (1.0 - kotlin.math.cos(i * kotlin.math.PI / steps)) / 2.0
                    send(light, brightnessToRgb((brightness * wave).toInt()))
                    delay(periodMs / (steps * 2))
                }
            }
        }
    }

    fun heartbeat(brightness: Int) {
        stopPattern()
        val light = selected ?: return
        scope.launch {
            while (isActive) {
                send(light, brightnessToRgb(brightness))
                delay(120)
                send(light, 0)
                delay(100)
                send(light, brightnessToRgb(brightness))
                delay(180)
                send(light, 0)
                delay(700)
            }
        }
    }

    fun stopPattern() {
        scope.coroutineContext.cancelChildren()
        off()
    }

    private fun brightnessToRgb(brightness: Int): Int {
        val value = brightness.coerceIn(0, 255)
        return (value shl 16) or (value shl 8) or value
    }

    private fun send(light: Light, color: Int) {
        val manager = lightsManager ?: return
        try {
            if (session == null) {
                session = manager.openSession()
            }

            val request = LightsRequest.Builder()
                .addLight(
                    light,
                    LightState.Builder()
                        .setColor(color)
                        .build()
                )
                .build()

            session?.requestLights(request)
        } catch (e: SecurityException) {
            Log.e(TAG, "OneGlyph does not have control of device lights", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Light " + light.id + " rejected the request", e)
        } catch (e: Exception) {
            Log.e(TAG, "Glyph request failed", e)
        }
    }

    override fun close() {
        try {
            stopPattern()
            session?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close lights session", e)
        } finally {
            session = null
            scope.cancel()
        }
    }
}
