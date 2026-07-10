package com.smc020412.brigblog.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.smc020412.brigblog.R
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SampleRate = 22_050
private const val MaxAmplitude = 32_767
private const val LowPriorityCooldownMs = 28L
private const val MasterVolumeBoost = 3.2f
private const val MenuMusicGain = 0.14f
private const val SfxMaxStreams = 8

class GameAudioManager(
    context: Context
) {
    private val soundPool = SoundPool.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setMaxStreams(SfxMaxStreams)
        .build()
    private val soundIds = mutableMapOf<GameSoundEvent, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    @Volatile
    private var volume = 0.75f
    @Volatile
    private var outputGain = sliderVolumeToGain(volume)
    @Volatile
    private var menuMusicRunning = false
    @Volatile
    private var currentMenuTrack: AudioTrack? = null
    @Volatile
    private var released = false
    private var lastEventAt = 0L
    private var menuMusicThread: Thread? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(loadedSoundIds) {
                    loadedSoundIds.add(sampleId)
                }
            }
        }
        preloadSfx(context.applicationContext)
    }

    @Synchronized
    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        outputGain = sliderVolumeToGain(volume)
    }

    @Synchronized
    fun play(event: GameSoundEvent) {
        if (released) return

        val now = System.currentTimeMillis()
        if (event.priority <= GameSoundEvent.Move.priority && now - lastEventAt < LowPriorityCooldownMs) return

        val currentVolume = outputGain
        if (currentVolume <= 0.01f) return

        val soundId = soundIds[event] ?: return
        val isLoaded = synchronized(loadedSoundIds) { soundId in loadedSoundIds }
        if (!isLoaded) return

        val streamId = soundPool.play(soundId, currentVolume, currentVolume, event.priority, 0, 1f)
        if (streamId != 0) {
            lastEventAt = now
        }
    }

    @Synchronized
    fun startMenuMusic() {
        if (released) return
        if (menuMusicRunning) return

        menuMusicRunning = true
        menuMusicThread = thread(name = "brigblog-menu-bgm", isDaemon = true) {
            runCatching {
                playMenuMusicLoop()
            }.onFailure {
                menuMusicRunning = false
            }
        }
    }

    @Synchronized
    fun stopMenuMusic() {
        menuMusicRunning = false
        currentMenuTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        currentMenuTrack = null
        menuMusicThread?.interrupt()
        menuMusicThread = null
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        stopMenuMusic()
        soundPool.release()
        synchronized(loadedSoundIds) {
            loadedSoundIds.clear()
        }
        soundIds.clear()
    }

    private fun preloadSfx(context: Context) {
        for (event in GameSoundEvent.values()) {
            soundIds[event] = soundPool.load(context, event.rawResId(), event.priority)
        }
    }

    private fun playMenuMusicLoop() {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(MenuStepSampleCount * Short.SIZE_BYTES * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            currentMenuTrack = track
            track.play()
            var step = 0
            while (menuMusicRunning) {
                val chunk = buildMenuMusicStep(step % MenuBarSteps, outputGain)
                track.write(chunk, 0, chunk.size)
                step++
            }
        } finally {
            if (currentMenuTrack === track) currentMenuTrack = null
            runCatching { track.release() }
        }
    }

}

private const val MenuStepMs = 150
private const val MenuBarSteps = 16
private const val MenuStepSampleCount = SampleRate * MenuStepMs / 1000

private val MenuLeadNotes = floatArrayOf(
    392f, 493.88f, 587.33f, 493.88f,
    659.25f, 587.33f, 493.88f, 392f,
    440f, 554.37f, 659.25f, 554.37f,
    739.99f, 659.25f, 554.37f, 440f
)

private val MenuBassNotes = floatArrayOf(
    98f, 98f, 123.47f, 123.47f,
    146.83f, 146.83f, 123.47f, 123.47f,
    110f, 110f, 130.81f, 130.81f,
    164.81f, 164.81f, 130.81f, 130.81f
)

private fun buildMenuMusicStep(step: Int, masterVolume: Float): ShortArray {
    val samples = ShortArray(MenuStepSampleCount)
    val gain = masterVolume * MasterVolumeBoost * MenuMusicGain
    val safeStep = step.coerceIn(0, MenuBarSteps - 1)

    for (i in samples.indices) {
        val globalSample = safeStep * MenuStepSampleCount + i
        val stepProgress = i.toDouble() / MenuStepSampleCount
        val lead = pulseWave(MenuLeadNotes[safeStep], globalSample, duty = 0.38)
        val bass = pulseWave(MenuBassNotes[safeStep], globalSample, duty = 0.50)
        val arpeggioHz = MenuLeadNotes[(safeStep + 4) % MenuLeadNotes.size] * if (safeStep % 4 == 0) 1.5f else 1.0f
        val sparkle = pulseWave(arpeggioHz, globalSample, duty = 0.22) * if (safeStep % 2 == 0) 0.20 else 0.0
        val noteEnvelope = if (stepProgress < 0.82) 1.0 else ((1.0 - stepProgress) / 0.18).coerceIn(0.0, 1.0)
        val raw = (lead * 0.46 + bass * 0.30 + sparkle) * noteEnvelope
        val sample = (raw * gain).coerceIn(-1.0, 1.0) * MaxAmplitude
        samples[i] = sample.roundToInt().coerceIn(-MaxAmplitude, MaxAmplitude).toShort()
    }

    return samples
}

private fun pulseWave(hz: Float, sampleIndex: Int, duty: Double): Double {
    val phase = (sampleIndex * hz / SampleRate) % 1.0
    val square = if (phase < duty) 1.0 else -1.0
    return square * (0.84 + abs(phase - 0.5) * 0.16)
}

private fun sliderVolumeToGain(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return (x * x * (3f - 2f * x)).coerceIn(0f, 1f)
}

enum class GameSoundEvent(val priority: Int) {
    Move(1),
    Rotate(1),
    SoftDrop(1),
    HardDrop(2),
    Hold(2),
    Start(4),
    LineClear(3),
    GameOver(4),
    Menu(2)
}

private fun GameSoundEvent.rawResId(): Int =
    when (this) {
        GameSoundEvent.Move -> R.raw.sfx_move
        GameSoundEvent.Rotate -> R.raw.sfx_rotate
        GameSoundEvent.SoftDrop -> R.raw.sfx_soft_drop
        GameSoundEvent.HardDrop -> R.raw.sfx_hard_drop
        GameSoundEvent.Hold -> R.raw.sfx_hold
        GameSoundEvent.Start -> R.raw.sfx_start
        GameSoundEvent.LineClear -> R.raw.sfx_line_clear
        GameSoundEvent.GameOver -> R.raw.sfx_game_over
        GameSoundEvent.Menu -> R.raw.sfx_menu
    }
