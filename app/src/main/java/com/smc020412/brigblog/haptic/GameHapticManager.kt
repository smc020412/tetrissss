package com.smc020412.brigblog.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private const val HapticAmplitudeMultiplier = 5
private const val HapticDurationMultiplier = 1.25f

class GameHapticManager(
    context: Context
) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    var enabled: Boolean = true
    private var lastPulseAt = 0L

    fun pulse(event: GameHapticEvent) {
        if (!enabled || vibrator?.hasVibrator() != true) return

        val now = System.currentTimeMillis()
        if (event.cooldownMs > 0L && now - lastPulseAt < event.cooldownMs) return
        lastPulseAt = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(event.effect())
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(event.legacyDurationMs)
        }
    }

    private fun GameHapticEvent.effect(): VibrationEffect =
        when (this) {
            GameHapticEvent.Menu -> oneShot(10, 48)
            GameHapticEvent.Input -> oneShot(8, 54)
            GameHapticEvent.Rotate -> oneShot(12, 72)
            GameHapticEvent.SoftDrop -> oneShot(10, 62)
            GameHapticEvent.Hold -> waveform(
                timings = longArrayOf(0, 14, 18, 20),
                amplitudes = intArrayOf(0, 72, 0, 98)
            )
            GameHapticEvent.Start -> waveform(
                timings = longArrayOf(0, 18, 18, 24, 20, 30),
                amplitudes = intArrayOf(0, 70, 0, 105, 0, 138)
            )
            GameHapticEvent.HardDrop -> waveform(
                timings = longArrayOf(0, 18, 8, 26),
                amplitudes = intArrayOf(0, 150, 0, 210)
            )
            GameHapticEvent.LineClear -> waveform(
                timings = longArrayOf(0, 20, 14, 26, 12, 22),
                amplitudes = intArrayOf(0, 130, 0, 180, 0, 140)
            )
            GameHapticEvent.GameOver -> waveform(
                timings = longArrayOf(0, 34, 16, 52, 24, 90),
                amplitudes = intArrayOf(0, 190, 0, 150, 0, 230)
            )
        }

    private fun oneShot(durationMs: Long, amplitude: Int): VibrationEffect =
        VibrationEffect.createOneShot(boostedDuration(durationMs), boostedAmplitude(amplitude))

    private fun waveform(timings: LongArray, amplitudes: IntArray): VibrationEffect =
        VibrationEffect.createWaveform(
            timings.map(::boostedDuration).toLongArray(),
            amplitudes.map(::boostedAmplitude).toIntArray(),
            -1
        )

    private fun boostedAmplitude(amplitude: Int): Int =
        if (amplitude == 0) 0 else (amplitude * HapticAmplitudeMultiplier).coerceIn(1, 255)

    private fun boostedDuration(durationMs: Long): Long =
        (durationMs * HapticDurationMultiplier).toLong().coerceAtLeast(durationMs)
}

enum class GameHapticEvent(
    val legacyDurationMs: Long,
    val cooldownMs: Long = 0L
) {
    Menu(10, 28),
    Input(8, 28),
    Rotate(12, 28),
    SoftDrop(10, 28),
    Hold(52, 40),
    Start(110, 80),
    HardDrop(52, 45),
    LineClear(94, 80),
    GameOver(216, 120)
}
