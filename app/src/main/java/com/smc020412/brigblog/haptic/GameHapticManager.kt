package com.smc020412.brigblog.haptic

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

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
    private val pulseGate = HapticPulseGate()

    fun pulse(event: GameHapticEvent) {
        if (!enabled || vibrator?.hasVibrator() != true) return

        if (!pulseGate.tryAcquire(event, SystemClock.elapsedRealtime())) return

        vibrator.vibrate(event.effect())
    }

    private fun GameHapticEvent.effect(): VibrationEffect =
        when (this) {
            GameHapticEvent.Menu -> oneShot(10, 65)
            GameHapticEvent.Input -> oneShot(8, 75)
            GameHapticEvent.Rotate -> oneShot(12, 100)
            GameHapticEvent.SoftDrop -> oneShot(10, 85)
            GameHapticEvent.Hold -> waveform(
                timings = longArrayOf(0, 14, 18, 20),
                amplitudes = intArrayOf(0, 105, 0, 145)
            )
            GameHapticEvent.Start -> waveform(
                timings = longArrayOf(0, 18, 18, 24, 20, 30),
                amplitudes = intArrayOf(0, 115, 0, 165, 0, 210)
            )
            GameHapticEvent.HardDrop -> waveform(
                timings = longArrayOf(0, 18, 8, 26),
                amplitudes = intArrayOf(0, 185, 0, 250)
            )
            GameHapticEvent.LineClear -> waveform(
                timings = longArrayOf(0, 20, 14, 26, 12, 22),
                amplitudes = intArrayOf(0, 150, 0, 205, 0, 165)
            )
            GameHapticEvent.GameOver -> waveform(
                timings = longArrayOf(0, 34, 16, 52, 24, 90),
                amplitudes = intArrayOf(0, 210, 0, 160, 0, 245)
            )
        }

    private fun oneShot(durationMs: Long, amplitude: Int): VibrationEffect =
        VibrationEffect.createOneShot(boostedDuration(durationMs), boundedAmplitude(amplitude))

    private fun waveform(timings: LongArray, amplitudes: IntArray): VibrationEffect =
        VibrationEffect.createWaveform(
            timings.map(::boostedDuration).toLongArray(),
            amplitudes.map(::boundedAmplitude).toIntArray(),
            -1
        )

    private fun boundedAmplitude(amplitude: Int): Int =
        if (amplitude == 0) 0 else amplitude.coerceIn(1, 255)

    private fun boostedDuration(durationMs: Long): Long =
        (durationMs * HapticDurationMultiplier).toLong().coerceAtLeast(durationMs)
}

enum class GameHapticEvent(
    val durationMs: Long,
    val cooldownMs: Long = 0L,
    val priority: Int
) {
    Menu(10, 28, 1),
    Input(8, 28, 1),
    Rotate(12, 28, 1),
    SoftDrop(10, 28, 1),
    Hold(52, 40, 2),
    Start(110, 80, 3),
    HardDrop(52, 45, 3),
    LineClear(94, 80, 4),
    GameOver(216, 120, 5)
}

/**
 * Keeps repeated low-priority input from replacing a stronger game-event haptic.
 */
internal class HapticPulseGate {
    private val lastPulseByEvent = mutableMapOf<GameHapticEvent, Long>()
    private var activePulse: ActivePulse? = null

    fun tryAcquire(event: GameHapticEvent, nowMs: Long): Boolean {
        val lastEventPulse = lastPulseByEvent[event]
        if (lastEventPulse != null && nowMs - lastEventPulse < event.cooldownMs) {
            return false
        }

        activePulse?.let { active ->
            if (nowMs >= active.endsAtMs) {
                activePulse = null
            } else if (event.priority <= active.priority) {
                return false
            }
        }

        lastPulseByEvent[event] = nowMs
        activePulse = ActivePulse(
            priority = event.priority,
            endsAtMs = nowMs + estimatedActiveDurationMs(event)
        )
        return true
    }

    private fun estimatedActiveDurationMs(event: GameHapticEvent): Long =
        (event.durationMs * HapticDurationMultiplier).toLong().coerceAtLeast(event.durationMs)

    private data class ActivePulse(
        val priority: Int,
        val endsAtMs: Long
    )
}
