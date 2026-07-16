package com.smc020412.brigblog.data

import android.content.Context

private const val DefaultSoundVolume = 0.75f

data class GameSettings(
    val soundVolume: Float = DefaultSoundVolume,
    val hapticEnabled: Boolean = true
)

class GameSettingsRepository(
    context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): GameSettings {
        val storedVolume = preferences.getFloat(KEY_SOUND_VOLUME, DefaultSoundVolume)
        return GameSettings(
            soundVolume = storedVolume.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: DefaultSoundVolume,
            hapticEnabled = preferences.getBoolean(KEY_HAPTIC_ENABLED, true)
        )
    }

    fun save(settings: GameSettings) {
        preferences.edit()
            .putFloat(KEY_SOUND_VOLUME, settings.soundVolume.coerceIn(0f, 1f))
            .putBoolean(KEY_HAPTIC_ENABLED, settings.hapticEnabled)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "brigblog_settings"
        const val KEY_SOUND_VOLUME = "sound_volume"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    }
}
