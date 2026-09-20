package com.tagsmith.core.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tagsmith.core.nfc.Feedback

/**
 * The haptic moment. A tap you can feel through the back of the phone matters
 * more than any animation when the card is out of sight behind the handset.
 */
class SystemFeedback(context: Context) : Feedback {

    private val appContext = context.applicationContext

    @Volatile var hapticOnDetect: Boolean = true
    @Volatile var hapticOnSuccess: Boolean = true
    @Volatile var soundsEnabled: Boolean = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** The two predefined effects this app uses, resolved only where they exist. */
    private enum class Tick(val fallbackMs: Long) { LIGHT(18), HEAVY(60) }

    override fun tagDetected() {
        if (hapticOnDetect) buzz(Tick.LIGHT)
        if (soundsEnabled) tone(ToneGenerator.TONE_PROP_BEEP, 90)
    }

    override fun success() {
        if (hapticOnSuccess) doubleBuzz()
        if (soundsEnabled) tone(ToneGenerator.TONE_PROP_ACK, 140)
    }

    override fun failure() {
        if (hapticOnSuccess || hapticOnDetect) buzz(Tick.HEAVY)
        if (soundsEnabled) tone(ToneGenerator.TONE_PROP_NACK, 200)
    }

    private fun buzz(tick: Tick) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(
                    when (tick) {
                        Tick.LIGHT -> VibrationEffect.EFFECT_TICK
                        Tick.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                    }
                )
            } else {
                VibrationEffect.createOneShot(tick.fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        }
    }

    /** Two quick ticks — the "that landed" signature, distinct from a detect. */
    private fun doubleBuzz() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 24, 70, 44), -1)
            )
        }
    }

    private fun tone(type: Int, durationMs: Int) {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).apply {
                startTone(type, durationMs)
                // The generator holds an audio track; release it once the tone ends.
                android.os.Handler(appContext.mainLooper).postDelayed(
                    { runCatching { release() } },
                    (durationMs + 120).toLong(),
                )
            }
        }
    }
}
