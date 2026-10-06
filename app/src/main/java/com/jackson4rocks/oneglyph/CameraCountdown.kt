package com.jackson4rocks.oneglyph

import android.content.Context

/**
 * Simple Glyph-based camera countdown effect.
 *
 * The UI owns camera launching; this helper only schedules the visible
 * countdown pattern through the background Glyph controller.
 */
object CameraCountdown {
    fun start(
        context: Context,
        seconds: Int
    ) {
        val count =
            seconds.coerceIn(1, 60)

        val steps =
            buildList {
                repeat(count) {
                    add(
                        GlyphStep(
                            brightness = 4095,
                            durationMs = 140L
                        )
                    )
                    add(
                        GlyphStep(
                            brightness = 0,
                            durationMs = 860L
                        )
                    )
                }

                add(
                    GlyphStep(
                        brightness = 4095,
                        durationMs = 300L
                    )
                )
                add(
                    GlyphStep(
                        brightness = 0,
                        durationMs = 300L
                    )
                )
            }

        GlyphBackgroundService.playPattern(
            context,
            steps
        )
    }
}
