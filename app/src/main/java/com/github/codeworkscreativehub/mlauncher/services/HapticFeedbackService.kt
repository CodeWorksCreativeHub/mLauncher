package com.github.codeworkscreativehub.mlauncher.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.github.codeworkscreativehub.mlauncher.data.Prefs

object HapticFeedbackService {

    enum class EffectType {
        ON, OFF, SAVE, SELECT, CLICK, DEFAULT
    }

    /**
     * Trigger haptic feedback based on the effect type
     */
    fun trigger(context: Context?, effectType: EffectType) {
        if (context == null) return

        // Always use application context if storing or passing around
        val appContext = context.applicationContext
        val prefs = Prefs(appContext) // Create prefs here, do NOT store in static field

        // Don't trigger if haptic feedback is disabled in prefs
        if (!prefs.hapticFeedback) return

        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use VibratorManager for API 31+
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrationEffect = when (effectType) {
                    EffectType.ON -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    EffectType.OFF -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                    EffectType.SAVE -> VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
                    EffectType.SELECT -> VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 300, 200, 100), -1)
                    EffectType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    EffectType.DEFAULT -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                when (effectType) {
                    EffectType.ON -> vibrator.vibrate(100)
                    EffectType.OFF -> vibrator.vibrate(200)
                    EffectType.SAVE -> vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
                    EffectType.SELECT -> vibrator.vibrate(longArrayOf(0, 100, 100, 300, 200, 100), -1)
                    EffectType.CLICK -> vibrator.vibrate(50)
                    EffectType.DEFAULT -> vibrator.vibrate(50)
                }
            }
        } else {
            Log.w("HapticFeedback", "Device does not support vibration")
        }
    }
}
