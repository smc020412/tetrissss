package com.smc020412.brigblog.game

// 게임 전체의 고정값을 저장.
object GameConstants {
    const val BOARD_WIDTH = 10
    const val BOARD_HEIGHT = 23
    const val PREVIEW_COUNT = 5

    const val MOVE_REPEAT_DELAY_MS = 110L
    const val MOVE_REPEAT_RATE_MS = 31L

    const val LOCK_DELAY_MS = 500L
    const val LOCK_RESET_LIMIT = 15
    const val LINE_CLEAR_ANIMATION_MS = 220L

    const val INITIAL_GRAVITY_MS = 680L
    const val MIN_GRAVITY_MS = 50L
    const val MAX_SPEED_LEVEL = 20
}
