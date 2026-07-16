package com.smc020412.brigblog.ui

import com.smc020412.brigblog.audio.GameSoundEvent
import com.smc020412.brigblog.haptic.GameHapticEvent

enum class AppMode {
    MainMenu,
    ClassicGame,
    TimeGame,
    SurvivalGame
}

internal enum class LeaderboardCategory {
    Classic,
    Time,
    Survival
}

enum class TimeLeaderboardDuration(val label: String, val seconds: Int) {
    ThirtySeconds("0:30", 30),
    OneMinute("1:00", 60),
    NinetySeconds("1:30", 90),
    TwoMinutes("2:00", 120),
    TwoAndHalfMinutes("2:30", 150),
    ThreeMinutes("3:00", 180);

    val durationMs: Long
        get() = seconds * 1_000L
}

internal fun GameSoundEvent.toHapticEvent(): GameHapticEvent =
    when (this) {
        GameSoundEvent.Move -> GameHapticEvent.Input
        GameSoundEvent.Rotate -> GameHapticEvent.Rotate
        GameSoundEvent.SoftDrop -> GameHapticEvent.SoftDrop
        GameSoundEvent.HardDrop -> GameHapticEvent.HardDrop
        GameSoundEvent.Hold -> GameHapticEvent.Hold
        GameSoundEvent.Start -> GameHapticEvent.Start
        GameSoundEvent.LineClear -> GameHapticEvent.LineClear
        GameSoundEvent.GameOver -> GameHapticEvent.GameOver
        GameSoundEvent.Menu -> GameHapticEvent.Menu
    }
