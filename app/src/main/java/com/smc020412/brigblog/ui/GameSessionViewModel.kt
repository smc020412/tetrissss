package com.smc020412.brigblog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smc020412.brigblog.game.Board
import com.smc020412.brigblog.game.GameConstants
import com.smc020412.brigblog.game.GameEngine
import com.smc020412.brigblog.game.GameState
import com.smc020412.brigblog.game.Piece
import com.smc020412.brigblog.game.PieceType
import com.smc020412.brigblog.game.SurvivalAttackKind
import com.smc020412.brigblog.game.SurvivalAttackObject

class GameSessionViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    val engine = GameEngine()

    private val restoredGame = savedStateHandle.get<String>(KEY_GAME_STATE)
        ?.let(GameStateCodec::decode)

    private var _game by mutableStateOf(restoredGame ?: engine.createGame())
    var game: GameState
        get() = _game
        set(value) {
            _game = value
            savedStateHandle[KEY_GAME_STATE] = GameStateCodec.encode(value)
        }

    var currentRank by mutableStateOf(savedStateHandle.get<Int?>(KEY_CURRENT_RANK))
    var lockElapsed by mutableLongStateOf(savedStateHandle.get<Long>(KEY_LOCK_ELAPSED) ?: 0L)
    var fallElapsed by mutableLongStateOf(savedStateHandle.get<Long>(KEY_FALL_ELAPSED) ?: 0L)
    var lineClearElapsed by mutableLongStateOf(savedStateHandle.get<Long>(KEY_LINE_CLEAR_ELAPSED) ?: 0L)
    var survivalThreatElapsed by mutableLongStateOf(savedStateHandle.get<Long>(KEY_SURVIVAL_THREAT_ELAPSED) ?: 0L)
    var pendingSurvivalAttacks by mutableStateOf(
        savedStateHandle.get<String>(KEY_PENDING_ATTACKS)
            ?.split(',')
            ?.mapNotNull(::survivalAttackKindOrNull)
            .orEmpty()
    )
    var timeRemainingMs by mutableLongStateOf(savedStateHandle.get<Long>(KEY_TIME_REMAINING) ?: TimeLeaderboardDuration.NinetySeconds.durationMs)
    var showRestartConfirm by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_RESTART_CONFIRM) ?: false)
    var showMainMenuConfirm by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_MAIN_MENU_CONFIRM) ?: false)
    var showSettings by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_SETTINGS) ?: false)
    var showMainMenuSettings by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_MAIN_MENU_SETTINGS) ?: false)
    var showMainMenuLeaderboard by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_MAIN_MENU_LEADERBOARD) ?: false)
    var showMainMenuTimeSetup by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_MAIN_MENU_TIME_SETUP) ?: false)
    var showControlsEditor by mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_CONTROLS_EDITOR) ?: false)
    var soundVolume by mutableFloatStateOf(savedStateHandle.get<Float>(KEY_SOUND_VOLUME) ?: 0.75f)
    var hapticEnabled by mutableStateOf(savedStateHandle.get<Boolean>(KEY_HAPTIC_ENABLED) ?: true)
    var controlsEditMode by mutableStateOf(savedStateHandle.get<Boolean>(KEY_CONTROLS_EDIT_MODE) ?: false)
    var persistentSettingsLoaded by mutableStateOf(false)
    var appMode by mutableStateOf(savedStateHandle.get<String>(KEY_APP_MODE)
        ?.let(::appModeOrNull) ?: AppMode.MainMenu)
    var selectedTimeDuration by mutableStateOf(savedStateHandle.get<String>(KEY_TIME_DURATION)
        ?.let(::timeDurationOrNull) ?: TimeLeaderboardDuration.NinetySeconds)
    var timeOver by mutableStateOf(savedStateHandle.get<Boolean>(KEY_TIME_OVER) ?: false)

    init {
        if (restoredGame != null && appMode != AppMode.MainMenu && !game.isGameOver) {
            game = game.copy(isPaused = true)
            showSettings = true
        }
    }

    fun persistUiState() {
        savedStateHandle[KEY_CURRENT_RANK] = currentRank
        savedStateHandle[KEY_LOCK_ELAPSED] = lockElapsed
        savedStateHandle[KEY_FALL_ELAPSED] = fallElapsed
        savedStateHandle[KEY_LINE_CLEAR_ELAPSED] = lineClearElapsed
        savedStateHandle[KEY_SURVIVAL_THREAT_ELAPSED] = survivalThreatElapsed
        savedStateHandle[KEY_PENDING_ATTACKS] = pendingSurvivalAttacks.joinToString(",") { it.name }
        savedStateHandle[KEY_TIME_REMAINING] = timeRemainingMs
        savedStateHandle[KEY_SHOW_RESTART_CONFIRM] = showRestartConfirm
        savedStateHandle[KEY_SHOW_MAIN_MENU_CONFIRM] = showMainMenuConfirm
        savedStateHandle[KEY_SHOW_SETTINGS] = showSettings
        savedStateHandle[KEY_SHOW_MAIN_MENU_SETTINGS] = showMainMenuSettings
        savedStateHandle[KEY_SHOW_MAIN_MENU_LEADERBOARD] = showMainMenuLeaderboard
        savedStateHandle[KEY_SHOW_MAIN_MENU_TIME_SETUP] = showMainMenuTimeSetup
        savedStateHandle[KEY_SHOW_CONTROLS_EDITOR] = showControlsEditor
        savedStateHandle[KEY_SOUND_VOLUME] = soundVolume
        savedStateHandle[KEY_HAPTIC_ENABLED] = hapticEnabled
        savedStateHandle[KEY_CONTROLS_EDIT_MODE] = controlsEditMode
        savedStateHandle[KEY_APP_MODE] = appMode.name
        savedStateHandle[KEY_TIME_DURATION] = selectedTimeDuration.name
        savedStateHandle[KEY_TIME_OVER] = timeOver
    }

    private companion object {
        const val KEY_GAME_STATE = "game_state"
        const val KEY_CURRENT_RANK = "current_rank"
        const val KEY_LOCK_ELAPSED = "lock_elapsed"
        const val KEY_FALL_ELAPSED = "fall_elapsed"
        const val KEY_LINE_CLEAR_ELAPSED = "line_clear_elapsed"
        const val KEY_SURVIVAL_THREAT_ELAPSED = "survival_threat_elapsed"
        const val KEY_PENDING_ATTACKS = "pending_attacks"
        const val KEY_TIME_REMAINING = "time_remaining"
        const val KEY_SHOW_RESTART_CONFIRM = "show_restart_confirm"
        const val KEY_SHOW_MAIN_MENU_CONFIRM = "show_main_menu_confirm"
        const val KEY_SHOW_SETTINGS = "show_settings"
        const val KEY_SHOW_MAIN_MENU_SETTINGS = "show_main_menu_settings"
        const val KEY_SHOW_MAIN_MENU_LEADERBOARD = "show_main_menu_leaderboard"
        const val KEY_SHOW_MAIN_MENU_TIME_SETUP = "show_main_menu_time_setup"
        const val KEY_SHOW_CONTROLS_EDITOR = "show_controls_editor"
        const val KEY_SOUND_VOLUME = "sound_volume"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_CONTROLS_EDIT_MODE = "controls_edit_mode"
        const val KEY_APP_MODE = "app_mode"
        const val KEY_TIME_DURATION = "time_duration"
        const val KEY_TIME_OVER = "time_over"
    }
}

private object GameStateCodec {
    fun encode(state: GameState): String = listOf(
        state.board.cells.flatten().joinToString("") { it?.code()?.toString() ?: "_" },
        state.queue.joinToString("") { it.code().toString() },
        state.currentPiece?.let { "${it.type.code()},${it.rotation},${it.x},${it.y}" } ?: "-",
        state.heldPiece?.code()?.toString() ?: "-",
        state.canHold, state.score, state.lines, state.level, state.isGameOver, state.isPaused,
        state.lastDropDistance, state.lockResetCount,
        state.attackObjects.joinToString(";") { attack ->
            "${attack.kind.name},${attack.x},${attack.y},${attack.cells.joinToString("") { if (it) "1" else "0" }}"
        },
        state.comboCount,
        state.backToBackCount,
        state.heatLevel,
        state.lastActionWasRotation,
        state.lastRotationKickIndex ?: -1
    ).joinToString("|")

    fun decode(encoded: String): GameState? = runCatching {
        val parts = encoded.split("|")
        require(parts.size == 13 || parts.size == 17 || parts.size == 18)
        require(parts[0].length == GameConstants.BOARD_WIDTH * GameConstants.BOARD_HEIGHT)
        val board = Board(
            GameConstants.BOARD_WIDTH,
            GameConstants.BOARD_HEIGHT,
            List(GameConstants.BOARD_HEIGHT) { y ->
                List(GameConstants.BOARD_WIDTH) { x -> parts[0][y * GameConstants.BOARD_WIDTH + x].pieceTypeOrNull() }
            }
        )
        GameState(
            board = board,
            queue = parts[1].mapNotNull { it.pieceTypeOrNull() },
            currentPiece = parts[2].takeUnless { it == "-" }?.decodePiece(),
            heldPiece = parts[3].firstOrNull()?.pieceTypeOrNull(),
            canHold = parts[4].toBooleanStrict(),
            score = parts[5].toInt(),
            lines = parts[6].toInt(),
            level = parts[7].toInt(),
            isGameOver = parts[8].toBooleanStrict(),
            isPaused = parts[9].toBooleanStrict(),
            lastClearedRows = emptyList(),
            lastDropDistance = parts[10].toInt(),
            lockResetCount = parts[11].toInt(),
            attackObjects = parts[12].takeIf { it.isNotEmpty() }
                ?.split(';')?.mapNotNull(::decodeAttack).orEmpty(),
            comboCount = parts.getOrNull(13)?.toInt() ?: 0,
            backToBackCount = parts.getOrNull(14)?.toInt() ?: 0,
            heatLevel = parts.getOrNull(15)?.toFloat() ?: 0f,
            lastActionWasRotation = parts.getOrNull(16)?.toBooleanStrict() ?: false,
            lastRotationKickIndex = parts.getOrNull(17)?.toIntOrNull()?.takeIf { it >= 0 }
        )
    }.getOrNull()

    private fun String.decodePiece(): Piece? = runCatching {
        val parts = split(',')
        require(parts.size == 4)
        Piece(parts[0].first().pieceTypeOrNull() ?: error("Unknown piece"), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
    }.getOrNull()

    private fun decodeAttack(encoded: String): SurvivalAttackObject? = runCatching {
        val parts = encoded.split(',')
        require(parts.size == 4)
        SurvivalAttackObject(SurvivalAttackKind.valueOf(parts[0]), parts[1].toInt(), parts[2].toFloat(), parts[3].map { it == '1' })
    }.getOrNull()

    private fun PieceType.code(): Char = if (this == PieceType.Garbage) 'G' else name.single()

    private fun Char.pieceTypeOrNull(): PieceType? = when (this) {
        'I' -> PieceType.I; 'J' -> PieceType.J; 'L' -> PieceType.L; 'O' -> PieceType.O
        'S' -> PieceType.S; 'T' -> PieceType.T; 'Z' -> PieceType.Z; 'G' -> PieceType.Garbage
        else -> null
    }
}

private fun appModeOrNull(value: String): AppMode? = runCatching { AppMode.valueOf(value) }.getOrNull()
private fun timeDurationOrNull(value: String): TimeLeaderboardDuration? = runCatching { TimeLeaderboardDuration.valueOf(value) }.getOrNull()
private fun survivalAttackKindOrNull(value: String): SurvivalAttackKind? = runCatching { SurvivalAttackKind.valueOf(value) }.getOrNull()
