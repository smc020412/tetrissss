package com.smc020412.brigblog.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smc020412.brigblog.audio.GameAudioManager
import com.smc020412.brigblog.audio.GameSoundEvent
import com.smc020412.brigblog.data.ControlLayoutRepository
import com.smc020412.brigblog.data.GameSettings
import com.smc020412.brigblog.data.GameSettingsRepository
import com.smc020412.brigblog.data.ScoreRepository
import com.smc020412.brigblog.game.GameConstants
import com.smc020412.brigblog.game.GameEngine
import com.smc020412.brigblog.game.GameState
import com.smc020412.brigblog.game.SurvivalAttackKind
import com.smc020412.brigblog.haptic.GameHapticEvent
import com.smc020412.brigblog.haptic.GameHapticManager
import kotlin.random.Random

private const val SurvivalAttackWarningMs = 820L
private const val SurvivalAttackStrikeMs = 430L
private const val SurvivalThreatChargeMs = 5_000L
private const val PrivacyPolicyUrl = "https://smc020412.github.io/gridream-policy/"
private const val HardDropImpactDurationMs = 280L

val LocalGameUiScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

@Composable
fun scaledDp(value: Float) = (value * LocalGameUiScale.current).dp

@Composable
fun scaledTextStyle(style: TextStyle): TextStyle {
    val scale = LocalGameUiScale.current
    return if (scale >= 0.99f) style else style.copy(fontSize = style.fontSize * scale)
}

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as ComponentActivity
    val session = remember(activity) { ViewModelProvider(activity)[GameSessionViewModel::class.java] }
    val engine = session.engine
    val scoresRepository = remember(context) { ScoreRepository(context) }
    val controlLayoutRepository = remember(context) { ControlLayoutRepository(context) }
    val settingsRepository = remember(context) { GameSettingsRepository(context) }
    val audio = remember(context) { GameAudioManager(context) }
    val haptic = remember(context) { GameHapticManager(context) }

    fun openPrivacyPolicy() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PrivacyPolicyUrl)))
    }

    fun loadTimeScores(): Map<TimeLeaderboardDuration, List<Int>> =
        TimeLeaderboardDuration.values().associateWith { duration ->
            scoresRepository.getTimeScores(duration.seconds)
        }

    var game by session::game
    var scores by remember { mutableStateOf(scoresRepository.getScores()) }
    var timeScores by remember { mutableStateOf(loadTimeScores()) }
    var survivalScores by remember { mutableStateOf(scoresRepository.getSurvivalScores()) }
    var currentRank by session::currentRank
    var lockElapsed by session::lockElapsed
    var fallElapsed by session::fallElapsed
    var lineClearElapsed by session::lineClearElapsed
    var hardDropImpactElapsed by remember { mutableLongStateOf(0L) }
    var hardDropImpact by remember { mutableStateOf<HardDropImpact?>(null) }
    var survivalThreatElapsed by session::survivalThreatElapsed
    var pendingSurvivalAttacks by session::pendingSurvivalAttacks
    var timeRemainingMs by session::timeRemainingMs
    var showRestartConfirm by session::showRestartConfirm
    var showMainMenuConfirm by session::showMainMenuConfirm
    var showSettings by session::showSettings
    var showMainMenuSettings by session::showMainMenuSettings
    var showMainMenuLeaderboard by session::showMainMenuLeaderboard
    var showMainMenuTimeSetup by session::showMainMenuTimeSetup
    var showControlsEditor by session::showControlsEditor
    var soundVolume by session::soundVolume
    var hapticEnabled by session::hapticEnabled
    var controlsEditMode by session::controlsEditMode
    var controlPlacements by remember { mutableStateOf(controlLayoutRepository.load()) }
    var selectedControlAction by remember { mutableStateOf(DefaultControlLayout.placements.first().action) }
    var appMode by session::appMode
    var appInForeground by remember { mutableStateOf(true) }
    var selectedTimeDuration by session::selectedTimeDuration
    var timeOver by session::timeOver

    audio.setVolume(soundVolume)
    haptic.enabled = hapticEnabled

    LaunchedEffect(session, settingsRepository) {
        if (!session.persistentSettingsLoaded) {
            val settings = settingsRepository.load()
            session.soundVolume = settings.soundVolume
            session.hapticEnabled = settings.hapticEnabled
            session.persistentSettingsLoaded = true
        }
    }

    LaunchedEffect(soundVolume, hapticEnabled, session.persistentSettingsLoaded) {
        if (session.persistentSettingsLoaded) {
            settingsRepository.save(GameSettings(soundVolume, hapticEnabled))
        }
    }

    LaunchedEffect(
        currentRank, lockElapsed, fallElapsed, lineClearElapsed, survivalThreatElapsed,
        pendingSurvivalAttacks, timeRemainingMs, showRestartConfirm, showMainMenuConfirm,
        showSettings, showMainMenuSettings, showMainMenuLeaderboard, showMainMenuTimeSetup,
        showControlsEditor, soundVolume, hapticEnabled, controlsEditMode, appMode,
        selectedTimeDuration, timeOver
    ) {
        session.persistUiState()
    }

    fun updateSoundVolume(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        soundVolume = normalized
        audio.setVolume(normalized)
    }

    DisposableEffect(audio) {
        onDispose { audio.release() }
    }

    DisposableEffect(lifecycleOwner, audio) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> appInForeground = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    appInForeground = false
                    audio.stopMenuMusic()
                }
                Lifecycle.Event.ON_DESTROY -> audio.release()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio.stopMenuMusic()
        }
    }

    fun isGameModeActive(): Boolean =
        appMode == AppMode.ClassicGame || appMode == AppMode.TimeGame || appMode == AppMode.SurvivalGame

    fun survivalThreatClearRequirement(level: Int): Int {
        val maxSpeedLevel = ((GameConstants.INITIAL_GRAVITY_MS - GameConstants.MIN_GRAVITY_MS) +
            GameConstants.GRAVITY_STEP_MS - 1) / GameConstants.GRAVITY_STEP_MS + 1
        return level.coerceIn(1, maxSpeedLevel.toInt())
    }

    fun finishGame(completedGame: GameState) {
        when (appMode) {
            AppMode.TimeGame -> {
                val saveResult = scoresRepository.saveTimeScore(selectedTimeDuration.seconds, completedGame.score)
                timeScores = timeScores.toMutableMap().apply {
                    put(selectedTimeDuration, saveResult.scores)
                }
                currentRank = saveResult.insertedRank
            }
            AppMode.SurvivalGame -> {
                val saveResult = scoresRepository.saveSurvivalScore(completedGame.score)
                survivalScores = saveResult.scores
                currentRank = saveResult.insertedRank
            }
            AppMode.ClassicGame -> {
                val saveResult = scoresRepository.saveScore(completedGame.score)
                scores = saveResult.scores
                currentRank = saveResult.insertedRank
            }
            AppMode.MainMenu -> Unit
        }
    }

    fun applyState(next: GameState, sound: GameSoundEvent? = null, pulse: GameHapticEvent? = null) {
        val previous = game
        game = next

        if (sound != null && next != previous) audio.play(sound)
        if (pulse != null && next != previous) haptic.pulse(pulse)

        val clearedLineCount = (next.lines - previous.lines).coerceAtLeast(0)
        if (clearedLineCount > 0) {
            audio.play(GameSoundEvent.LineClear)
            haptic.pulse(GameHapticEvent.LineClear)
            lineClearElapsed = 0L
            if (appMode == AppMode.SurvivalGame) {
                val clearRequirement = survivalThreatClearRequirement(next.level)
                val threatDrain = SurvivalThreatChargeMs * clearedLineCount / clearRequirement
                survivalThreatElapsed = (survivalThreatElapsed - threatDrain).coerceAtLeast(0L)
            }
        }
        if (next.isGameOver && !previous.isGameOver) {
            finishGame(next)
            audio.play(GameSoundEvent.GameOver)
            haptic.pulse(GameHapticEvent.GameOver)
        }
    }

    fun resetGameSession() {
        game = engine.createGame()
        currentRank = null
        lockElapsed = 0L
        fallElapsed = 0L
        lineClearElapsed = 0L
        hardDropImpactElapsed = 0L
        hardDropImpact = null
        survivalThreatElapsed = 0L
        pendingSurvivalAttacks = emptyList()
        timeRemainingMs = selectedTimeDuration.durationMs
        timeOver = false
        showRestartConfirm = false
        showMainMenuConfirm = false
        showSettings = false
        showMainMenuSettings = false
        showMainMenuLeaderboard = false
        showMainMenuTimeSetup = false
        showControlsEditor = false
        controlsEditMode = false
        selectedControlAction = DefaultControlLayout.placements.first().action
    }

    fun abandonGame() {
        // Incomplete games intentionally never enter a leaderboard.
        resetGameSession()
    }

    fun restartGame() {
        // Completed games were already persisted by finishGame().
        resetGameSession()
    }

    fun playerAction(
        sound: GameSoundEvent,
        pulse: GameHapticEvent = GameHapticEvent.Input,
        action: (GameState) -> GameState
    ) {
        val next = action(game)
        if (next != game) {
            if (next.lockResetCount > game.lockResetCount || !engine.isGrounded(next)) {
                lockElapsed = 0L
            }
            applyState(next, sound, pulse)
        }
    }

    fun hardDrop() {
        val landedPiece = engine.ghostPiece(game) ?: return
        val next = engine.hardDrop(game)
        if (next == game) return

        hardDropImpact = HardDropImpact(
            blocks = engine.getCells(landedPiece)
                .filter { it.y >= 0 }
                .map { block ->
                    HardDropImpactBlock(x = block.x, y = block.y, type = landedPiece.type)
                }
        )
        hardDropImpactElapsed = 0L
        if (next.lockResetCount > game.lockResetCount || !engine.isGrounded(next)) {
            lockElapsed = 0L
        }
        applyState(next, GameSoundEvent.HardDrop, GameHapticEvent.HardDrop)
    }

    fun uiAction(sound: GameSoundEvent = GameSoundEvent.Menu, action: () -> Unit) {
        audio.play(sound)
        haptic.pulse(sound.toHapticEvent())
        action()
    }

    fun closeControlsEditor(saveLayout: Boolean = true) {
        if (saveLayout) {
            controlLayoutRepository.save(controlPlacements)
        }
        controlsEditMode = false
        showControlsEditor = false
        if (isGameModeActive() && game.isPaused && !game.isGameOver) {
            showSettings = true
        }
        selectedControlAction = DefaultControlLayout.placements.first().action
    }

    fun randomSurvivalAttack(): SurvivalAttackKind =
        if (Random.nextBoolean()) SurvivalAttackKind.RisingGarbage else SurvivalAttackKind.FallingGarbage

    fun goToMainMenu() {
        abandonGame()
        appMode = AppMode.MainMenu
    }

    fun startClassicGame() {
        restartGame()
        appMode = AppMode.ClassicGame
    }

    fun startSurvivalGame() {
        restartGame()
        appMode = AppMode.SurvivalGame
    }

    fun startTimeGame(duration: TimeLeaderboardDuration) {
        selectedTimeDuration = duration
        restartGame()
        timeRemainingMs = duration.durationMs
        appMode = AppMode.TimeGame
    }

    LaunchedEffect(appInForeground) {
        if (!appInForeground && isGameModeActive() && !game.isPaused && !game.isGameOver) {
            game = game.copy(isPaused = true)
            showSettings = true
        }
    }

    BackHandler(
        enabled = appMode != AppMode.MainMenu ||
            showMainMenuSettings ||
            showMainMenuLeaderboard ||
            showMainMenuTimeSetup
    ) {
        when {
            appMode == AppMode.MainMenu && showMainMenuSettings -> showMainMenuSettings = false
            appMode == AppMode.MainMenu && showMainMenuLeaderboard -> showMainMenuLeaderboard = false
            appMode == AppMode.MainMenu && showMainMenuTimeSetup -> showMainMenuTimeSetup = false
            showMainMenuConfirm -> {
                showMainMenuConfirm = false
                if (isGameModeActive() && game.isPaused && !game.isGameOver) {
                    showSettings = true
                }
            }
            showRestartConfirm -> {
                showRestartConfirm = false
                if (isGameModeActive() && game.isPaused && !game.isGameOver) {
                    showSettings = true
                }
            }
            showControlsEditor -> closeControlsEditor()
            showSettings -> {
                showSettings = false
                if (isGameModeActive() && game.isPaused && !game.isGameOver) {
                    game = game.copy(isPaused = false)
                }
            }
            game.isGameOver -> showMainMenuConfirm = true
            isGameModeActive() -> {
                if (!game.isPaused) {
                    game = game.copy(isPaused = true)
                }
                showMainMenuConfirm = true
            }
        }
    }

    LaunchedEffect(appMode, appInForeground) {
        if (appMode == AppMode.MainMenu && appInForeground) {
            audio.startMenuMusic()
        } else {
            audio.stopMenuMusic()
        }
    }

    LaunchedEffect(Unit) {
        var lastFrame = withFrameMillis { it }
        while (true) {
            val frame = withFrameMillis { it }
            val delta = (frame - lastFrame).coerceAtMost(80L)
            lastFrame = frame

            if (hardDropImpact != null) {
                hardDropImpactElapsed += delta
                if (hardDropImpactElapsed >= HardDropImpactDurationMs) {
                    hardDropImpactElapsed = 0L
                    hardDropImpact = null
                }
            }

            val gameModeActive = appMode == AppMode.ClassicGame || appMode == AppMode.TimeGame || appMode == AppMode.SurvivalGame
            if (gameModeActive && !game.isPaused && !game.isGameOver && !showSettings && !showRestartConfirm && !showMainMenuConfirm && !showControlsEditor) {
                if (appMode == AppMode.TimeGame) {
                    timeRemainingMs = (timeRemainingMs - delta).coerceAtLeast(0L)
                    if (timeRemainingMs == 0L) {
                        timeOver = true
                        applyState(game.copy(isGameOver = true))
                        continue
                    }
                }

                if (game.isClearingLines) {
                    lineClearElapsed += delta
                    if (lineClearElapsed >= GameConstants.LINE_CLEAR_ANIMATION_MS) {
                        lineClearElapsed = 0L
                        applyState(engine.finishLineClear(game))
                    }
                } else {
                    lineClearElapsed = 0L
                }

                if (appMode == AppMode.SurvivalGame && game.attackObjects.isNotEmpty()) {
                    val attacked = engine.tickSurvivalAttacks(game, delta)
                    val finishedAttack = game.attackObjects.isNotEmpty() && attacked.attackObjects.isEmpty()
                    applyState(
                        next = attacked,
                        sound = if (finishedAttack) GameSoundEvent.HardDrop else null,
                        pulse = if (finishedAttack) GameHapticEvent.HardDrop else null
                    )
                    if (attacked.isGameOver) continue
                }

                if (appMode == AppMode.SurvivalGame && game.attackObjects.isEmpty() && pendingSurvivalAttacks.isNotEmpty()) {
                    val nextAttack = pendingSurvivalAttacks.first()
                    pendingSurvivalAttacks = pendingSurvivalAttacks.drop(1)
                    applyState(engine.startSurvivalAttack(game, nextAttack), GameSoundEvent.Menu, GameHapticEvent.Menu)
                }

                if (appMode == AppMode.SurvivalGame) {
                    survivalThreatElapsed += delta
                    if (survivalThreatElapsed >= SurvivalThreatChargeMs) {
                        survivalThreatElapsed %= SurvivalThreatChargeMs
                        pendingSurvivalAttacks = pendingSurvivalAttacks + randomSurvivalAttack()
                    }
                }

                if (engine.isGrounded(game)) {
                    fallElapsed = 0L
                    lockElapsed += delta
                    if (lockElapsed >= GameConstants.LOCK_DELAY_MS) {
                        lockElapsed = 0L
                        applyState(engine.lockCurrent(game))
                    }
                } else {
                    lockElapsed = 0L
                    fallElapsed += delta
                    val interval = engine.gravityIntervalMs(game.level)
                    if (fallElapsed >= interval) {
                        fallElapsed %= interval
                        applyState(engine.tick(game))
                    }
                }
            }
        }
    }

    Surface(
        color = GameColors.Background,
        contentColor = GameColors.Text,
        modifier = Modifier
            .fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (appMode == AppMode.MainMenu) {
                MainMenuScreen(
                    showSettings = showMainMenuSettings,
                    showLeaderboard = showMainMenuLeaderboard,
                    showTimeSetup = showMainMenuTimeSetup,
                    classicScores = scores,
                    timeScores = timeScores,
                    survivalScores = survivalScores,
                    soundVolume = soundVolume,
                    hapticEnabled = hapticEnabled,
                    onClassic = {
                        audio.stopMenuMusic()
                        uiAction(GameSoundEvent.Start) {
                            startClassicGame()
                        }
                    },
                    onTime = {
                        uiAction {
                            showMainMenuSettings = false
                            showMainMenuLeaderboard = false
                            showMainMenuTimeSetup = true
                        }
                    },
                    onSurvival = {
                        audio.stopMenuMusic()
                        uiAction(GameSoundEvent.Start) {
                            startSurvivalGame()
                        }
                    },
                    onTimeDurationSelected = { duration ->
                        audio.stopMenuMusic()
                        uiAction(GameSoundEvent.Start) {
                            startTimeGame(duration)
                        }
                    },
                    onLeaderboard = {
                        uiAction {
                            showMainMenuSettings = false
                            showMainMenuTimeSetup = false
                            showMainMenuLeaderboard = true
                        }
                    },
                    onSetting = {
                        uiAction {
                            showMainMenuLeaderboard = false
                            showMainMenuTimeSetup = false
                            showMainMenuSettings = true
                        }
                    },
                    onVolumeChange = ::updateSoundVolume,
                    onHapticToggle = { uiAction { hapticEnabled = !hapticEnabled } },
                    onPrivacyPolicy = { uiAction { openPrivacyPolicy() } },
                    onCloseSettings = { uiAction { showMainMenuSettings = false } },
                    onCloseLeaderboard = { uiAction { showMainMenuLeaderboard = false } },
                    onCloseTimeSetup = { uiAction { showMainMenuTimeSetup = false } },
                    modifier = Modifier.fillMaxSize()
                )
                return@BoxWithConstraints
            }

            // Foldables remain touch-first layouts even when unfolded; reserve the side layout for genuinely wide screens.
            val useWideLayout = maxWidth >= 900.dp && maxWidth > maxHeight
            val compact = !useWideLayout
            val panelActive = game.isPaused || game.isGameOver || showSettings || showRestartConfirm || showMainMenuConfirm || showControlsEditor
            val inputLocked = panelActive || controlsEditMode
            val lineClearProgress = if (game.isClearingLines) {
                (lineClearElapsed / GameConstants.LINE_CLEAR_ANIMATION_MS.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            val hardDropImpactProgress = if (hardDropImpact != null) {
                (hardDropImpactElapsed / HardDropImpactDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            val survivalThreatRatio = (survivalThreatElapsed / SurvivalThreatChargeMs.toFloat()).coerceIn(0f, 1f)
            val activeScores = if (appMode == AppMode.TimeGame) {
                timeScores[selectedTimeDuration].orEmpty()
            } else if (appMode == AppMode.SurvivalGame) {
                survivalScores
            } else {
                scores
            }
            val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
                GameControls(
                    onMoveLeft = { if (!inputLocked) playerAction(GameSoundEvent.Move, GameHapticEvent.Input) { engine.move(it, -1, 0) } },
                    onMoveRight = { if (!inputLocked) playerAction(GameSoundEvent.Move, GameHapticEvent.Input) { engine.move(it, 1, 0) } },
                    onSoftDrop = { if (!inputLocked) playerAction(GameSoundEvent.SoftDrop, GameHapticEvent.SoftDrop) { engine.softDrop(it) } },
                    onHardDrop = { if (!inputLocked) hardDrop() },
                    onRotateLeft = { if (!inputLocked) playerAction(GameSoundEvent.Rotate, GameHapticEvent.Rotate) { engine.rotate(it, -1) } },
                    onRotateRight = { if (!inputLocked) playerAction(GameSoundEvent.Rotate, GameHapticEvent.Rotate) { engine.rotate(it, 1) } },
                    onHold = { if (!inputLocked) playerAction(GameSoundEvent.Hold, GameHapticEvent.Hold) { engine.hold(it) } },
                    placements = controlPlacements,
                    editMode = controlsEditMode,
                    selectedAction = selectedControlAction,
                    onControlSelected = { action ->
                        selectedControlAction = action
                    },
                    onPlacementDelta = { action, dxRatio, dyRatio ->
                        selectedControlAction = action
                        val updated = DefaultControlLayout.normalized(
                            controlPlacements.map { placement ->
                                if (placement.action == action) {
                                    placement.copy(
                                        xRatio = (placement.xRatio + dxRatio).coerceIn(0.06f, 0.94f),
                                        yRatio = (placement.yRatio + dyRatio).coerceIn(0.08f, 0.92f)
                                    )
                                } else {
                                    placement
                                }
                            }
                        )
                        controlPlacements = updated
                    },
                    enabled = !panelActive,
                    modifier = controlsModifier
                )
            }
            val boardLayer: @Composable (Modifier) -> Unit = { modifier ->
                BoardStage(
                    game = game,
                    engine = engine,
                    scores = activeScores,
                    rank = currentRank,
                    gameOverTitle = if (timeOver) "Time Over" else "Game Over",
                    timeMode = appMode == AppMode.TimeGame,
                    survivalMode = appMode == AppMode.SurvivalGame,
                    timeRemainingMs = timeRemainingMs,
                    timeTotalMs = selectedTimeDuration.durationMs,
                    survivalThreatRatio = survivalThreatRatio,
                    showSettings = showSettings,
                    showRestartConfirm = showRestartConfirm,
                    showMainMenuConfirm = showMainMenuConfirm,
                    showControlsEditor = showControlsEditor,
                    soundVolume = soundVolume,
                    hapticEnabled = hapticEnabled,
                    controlsEditMode = controlsEditMode,
                    controlPlacements = controlPlacements,
                    selectedControlAction = selectedControlAction,
                    lineClearProgress = lineClearProgress,
                    hardDropImpact = hardDropImpact,
                    hardDropImpactProgress = hardDropImpactProgress,
                    noiseTimeMs = fallElapsed + lockElapsed + lineClearElapsed,
                    onResume = {
                        uiAction {
                            showSettings = false
                            game = game.copy(isPaused = false)
                        }
                    },
                    onRestartRequest = {
                        uiAction {
                            showSettings = false
                            showRestartConfirm = true
                        }
                    },
                    onRestartConfirm = { uiAction(GameSoundEvent.Start) { restartGame() } },
                    onRestartDismiss = {
                        uiAction {
                            showRestartConfirm = false
                            if (isGameModeActive() && game.isPaused && !game.isGameOver) {
                                showSettings = true
                            }
                        }
                    },
                    onMainMenuRequest = {
                        uiAction {
                            showSettings = false
                            showMainMenuConfirm = true
                        }
                    },
                    onMainMenuConfirm = { uiAction { goToMainMenu() } },
                    onMainMenuDismiss = {
                        uiAction {
                            showMainMenuConfirm = false
                            if (isGameModeActive() && game.isPaused && !game.isGameOver) {
                                showSettings = true
                            }
                        }
                    },
                    onVolumeChange = ::updateSoundVolume,
                    onHapticToggle = { uiAction { hapticEnabled = !hapticEnabled } },
                    onOpenControlsEditor = {
                        uiAction {
                            showSettings = false
                            showControlsEditor = true
                            controlsEditMode = true
                            selectedControlAction = controlPlacements.firstOrNull()?.action
                                ?: DefaultControlLayout.placements.first().action
                        }
                    },
                    onControlButtonSizeChange = { action, size ->
                        val updated = DefaultControlLayout.normalized(
                            controlPlacements.map { placement ->
                                if (placement.action == action) {
                                    placement.copy(sizeDp = size)
                                } else {
                                    placement
                                }
                            }
                        )
                        controlPlacements = updated
                    },
                    onControlsReset = {
                        uiAction {
                            controlPlacements = controlLayoutRepository.reset()
                            selectedControlAction = DefaultControlLayout.placements.first().action
                        }
                    },
                    onCloseSettings = {
                        uiAction {
                            showSettings = false
                            game = game.copy(isPaused = false)
                        }
                    },
                    onCloseControlsEditor = {
                        uiAction {
                            closeControlsEditor()
                        }
                    },
                    modifier = modifier
                )
            }

            if (compact) {
                CompactGameLayout(
                    game = game,
                    bestScore = activeScores.firstOrNull() ?: 0,
                    inputLocked = panelActive,
                    onSettings = {
                        if (!inputLocked) {
                            uiAction {
                                game = game.copy(isPaused = true)
                                showSettings = true
                            }
                        }
                    },
                    boardLayer = boardLayer,
                    controls = controls
                )
            } else {
                WideGameLayout(
                    game = game,
                    bestScore = activeScores.firstOrNull() ?: 0,
                    inputLocked = panelActive,
                    onSettings = {
                        if (!inputLocked) {
                            uiAction {
                                game = game.copy(isPaused = true)
                                showSettings = true
                            }
                        }
                    },
                    boardLayer = boardLayer,
                    controls = controls
                )
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    showSettings: Boolean,
    showLeaderboard: Boolean,
    showTimeSetup: Boolean,
    classicScores: List<Int>,
    timeScores: Map<TimeLeaderboardDuration, List<Int>>,
    survivalScores: List<Int>,
    soundVolume: Float,
    hapticEnabled: Boolean,
    onClassic: () -> Unit,
    onTime: () -> Unit,
    onSurvival: () -> Unit,
    onTimeDurationSelected: (TimeLeaderboardDuration) -> Unit,
    onLeaderboard: () -> Unit,
    onSetting: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onHapticToggle: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onCloseSettings: () -> Unit,
    onCloseLeaderboard: () -> Unit,
    onCloseTimeSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeMs by remember { mutableLongStateOf(0L) }
    val bubbles = remember { NeonBubble.create(count = 34) }

    LaunchedEffect(Unit) {
        var lastFrame = withFrameMillis { it }
        while (true) {
            val frame = withFrameMillis { it }
            timeMs += (frame - lastFrame).coerceAtMost(80L)
            lastFrame = frame
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthScale = (maxWidth.value / 420f).coerceIn(0.78f, 1f)
        val heightScale = (maxHeight.value / 920f).coerceIn(0.62f, 1f)
        val menuScale = minOf(widthScale, heightScale).coerceIn(0.62f, 1f)

        CompositionLocalProvider(LocalGameUiScale provides menuScale) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                NeonBubbleBackground(
                    bubbles = bubbles,
                    timeMs = timeMs,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = scaledDp(34f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(scaledDp(14f))
                ) {
                    PixelLogo(
                        text = "BRIGBLOG",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaledDp(76f))
                    )
                    Spacer(Modifier.height(scaledDp(10f)))
                    MainMenuButton("Survival", onClick = onSurvival)
                    MainMenuButton("Time", onClick = onTime)
                    MainMenuButton("Classic", onClick = onClassic)
                    MainMenuButton("Leaderboard", onClick = onLeaderboard)
                    MainMenuButton("Setting", onClick = onSetting)
                }

                if (showLeaderboard) {
                    MainMenuLeaderboardPanel(
                        classicScores = classicScores,
                        timeScores = timeScores,
                        survivalScores = survivalScores,
                        onClose = onCloseLeaderboard,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (showTimeSetup) {
                    MainMenuTimeSetupPanel(
                        onSelectDuration = onTimeDurationSelected,
                        onClose = onCloseTimeSetup,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (showSettings) {
                    MainMenuSettingsPanel(
                        volume = soundVolume,
                        hapticEnabled = hapticEnabled,
                        onVolumeChange = onVolumeChange,
                        onHapticToggle = onHapticToggle,
                        onPrivacyPolicy = onPrivacyPolicy,
                        onClose = onCloseSettings,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMenuLeaderboardPanel(
    classicScores: List<Int>,
    timeScores: Map<TimeLeaderboardDuration, List<Int>>,
    survivalScores: List<Int>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf(LeaderboardCategory.Classic) }
    var timeDuration by remember { mutableStateOf(TimeLeaderboardDuration.NinetySeconds) }

    Surface(
        color = GameColors.Panel.copy(alpha = 0.96f),
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth(0.88f)
            .padding(scaledDp(12f))
    ) {
        Column(
            modifier = Modifier.padding(scaledDp(14f)),
            verticalArrangement = Arrangement.spacedBy(scaledDp(10f))
        ) {
            Text(
                text = "Leaderboard",
                style = scaledTextStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(scaledDp(8f))
            ) {
                LeaderboardCategoryButton(
                    label = "Classic",
                    selected = category == LeaderboardCategory.Classic,
                    onClick = { category = LeaderboardCategory.Classic },
                    modifier = Modifier.weight(1f)
                )
                LeaderboardCategoryButton(
                    label = "Time",
                    selected = category == LeaderboardCategory.Time,
                    onClick = { category = LeaderboardCategory.Time },
                    modifier = Modifier.weight(1f)
                )
                LeaderboardCategoryButton(
                    label = "Survival",
                    selected = category == LeaderboardCategory.Survival,
                    onClick = { category = LeaderboardCategory.Survival },
                    modifier = Modifier.weight(1f)
                )
            }
            if (category == LeaderboardCategory.Time) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(scaledDp(6f))
                ) {
                    TimeLeaderboardDuration.values().toList().chunked(3).forEach { rowDurations ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(scaledDp(6f))
                        ) {
                            rowDurations.forEach { duration ->
                                LeaderboardCategoryButton(
                                    label = duration.label,
                                    selected = timeDuration == duration,
                                    onClick = { timeDuration = duration },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowDurations.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(scaledDp(4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(10) { index ->
                    val score = when (category) {
                        LeaderboardCategory.Classic -> classicScores.getOrNull(index)?.toString().orEmpty()
                        LeaderboardCategory.Time -> timeScores[timeDuration].orEmpty().getOrNull(index)?.toString().orEmpty()
                        LeaderboardCategory.Survival -> survivalScores.getOrNull(index)?.toString().orEmpty()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0) {
                                    GameColors.Board.copy(alpha = 0.34f)
                                } else {
                                    GameColors.Board.copy(alpha = 0.18f)
                                }
                            )
                            .padding(horizontal = scaledDp(10f), vertical = scaledDp(5f)),
                    ) {
                        Text(
                            text = "${index + 1}.",
                            modifier = Modifier.align(Alignment.CenterStart),
                            style = scaledTextStyle(MaterialTheme.typography.bodyMedium),
                            color = GameColors.MutedText,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = score,
                            modifier = Modifier.align(Alignment.Center),
                            style = scaledTextStyle(MaterialTheme.typography.bodyMedium),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close", style = scaledTextStyle(MaterialTheme.typography.labelSmall))
            }
        }
    }
}

@Composable
private fun MainMenuTimeSetupPanel(
    onSelectDuration: (TimeLeaderboardDuration) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GameColors.Panel.copy(alpha = 0.96f),
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth(0.88f)
            .padding(scaledDp(12f))
    ) {
        Column(
            modifier = Modifier.padding(scaledDp(14f)),
            verticalArrangement = Arrangement.spacedBy(scaledDp(10f))
        ) {
            Text("Time Attack", style = scaledTextStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
            Text(
                "Score as high as possible before time runs out.",
                style = scaledTextStyle(MaterialTheme.typography.bodySmall),
                color = GameColors.MutedText
            )
            TimeLeaderboardDuration.values().forEach { duration ->
                MainMenuSettingActionButton(
                    label = duration.label,
                    onClick = { onSelectDuration(duration) }
                )
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close", style = scaledTextStyle(MaterialTheme.typography.labelSmall))
            }
        }
    }
}

@Composable
private fun LeaderboardCategoryButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(
            width = scaledDp(1f),
            color = if (selected) GameColors.Accent.copy(alpha = 0.95f) else GameColors.Grid.copy(alpha = 0.72f)
        ),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) GameColors.Accent.copy(alpha = 0.20f) else GameColors.Board.copy(alpha = 0.42f),
            contentColor = if (selected) GameColors.Accent else GameColors.Text
        )
    ) {
        Text(
            text = label,
            style = scaledTextStyle(MaterialTheme.typography.labelSmall),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun PixelLogo(
    text: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val patterns = PixelLetterPatterns
        val gapCells = 1
        val letterWidths = text.map { patterns[it]?.firstOrNull()?.length ?: 0 }
        val totalCells = letterWidths.sum() + gapCells * (text.length - 1).coerceAtLeast(0)
        if (totalCells <= 0) return@Canvas

        val cell = minOf(size.width / totalCells, size.height / 7f)
        val logoWidth = totalCells * cell
        val logoHeight = 7f * cell
        var x = (size.width - logoWidth) / 2f
        val y = (size.height - logoHeight) / 2f
        val pixelSize = cell * 0.82f
        val glowSize = cell * 1.03f

        text.forEach { letter ->
            val pattern = patterns[letter] ?: return@forEach
            pattern.forEachIndexed { row, line ->
                line.forEachIndexed { column, block ->
                    if (block == '1') {
                        val px = x + column * cell
                        val py = y + row * cell
                        drawRect(
                            color = GameColors.Accent.copy(alpha = 0.22f),
                            topLeft = Offset(px - (glowSize - pixelSize) / 2f, py - (glowSize - pixelSize) / 2f),
                            size = Size(glowSize, glowSize)
                        )
                        drawRect(
                            color = Color(0xFFEAFBFF),
                            topLeft = Offset(px, py),
                            size = Size(pixelSize, pixelSize)
                        )
                    }
                }
            }
            x += (pattern.first().length + gapCells) * cell
        }
    }
}

private val PixelLetterPatterns = mapOf(
    'B' to listOf(
        "11110",
        "10001",
        "10001",
        "11110",
        "10001",
        "10001",
        "11110"
    ),
    'R' to listOf(
        "11110",
        "10001",
        "10001",
        "11110",
        "10100",
        "10010",
        "10001"
    ),
    'I' to listOf(
        "111",
        "010",
        "010",
        "010",
        "010",
        "010",
        "111"
    ),
    'G' to listOf(
        "01111",
        "10000",
        "10000",
        "10111",
        "10001",
        "10001",
        "01111"
    ),
    'L' to listOf(
        "10000",
        "10000",
        "10000",
        "10000",
        "10000",
        "10000",
        "11111"
    ),
    'O' to listOf(
        "01110",
        "10001",
        "10001",
        "10001",
        "10001",
        "10001",
        "01110"
    )
)

@Composable
private fun MainMenuButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(48f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = GameColors.Panel,
            contentColor = GameColors.Accent,
            disabledContainerColor = GameColors.Panel.copy(alpha = 0.42f),
            disabledContentColor = GameColors.MutedText
        )
    ) {
        Text(
            text = label,
            style = scaledTextStyle(MaterialTheme.typography.labelLarge),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MainMenuSettingsPanel(
    volume: Float,
    hapticEnabled: Boolean,
    onVolumeChange: (Float) -> Unit,
    onHapticToggle: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GameColors.Panel.copy(alpha = 0.96f),
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth(0.88f)
            .padding(scaledDp(12f))
    ) {
        Column(
            modifier = Modifier.padding(scaledDp(14f)),
            verticalArrangement = Arrangement.spacedBy(scaledDp(8f))
        ) {
            Text("Setting", style = scaledTextStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
            Text("Sound volume", style = scaledTextStyle(MaterialTheme.typography.bodySmall), color = GameColors.MutedText)
            androidx.compose.material3.Slider(value = volume, onValueChange = onVolumeChange)
            MainMenuSettingActionButton(
                label = if (hapticEnabled) "Haptic On" else "Haptic Off",
                onClick = onHapticToggle,
                highlighted = hapticEnabled
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close", style = scaledTextStyle(MaterialTheme.typography.labelSmall))
            }
            PrivacyPolicyLink(onClick = onPrivacyPolicy)
        }
    }
}

@Composable
private fun MainMenuSettingActionButton(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = scaledDp(1f),
            color = if (highlighted) GameColors.Accent.copy(alpha = 0.95f) else GameColors.Grid.copy(alpha = 0.72f)
        ),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (highlighted) GameColors.Accent.copy(alpha = 0.18f) else GameColors.Board.copy(alpha = 0.42f),
            contentColor = GameColors.Text
        )
    ) {
        Text(
            text = label,
            style = scaledTextStyle(MaterialTheme.typography.labelSmall),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun NeonBubbleBackground(
    bubbles: List<NeonBubble>,
    timeMs: Long,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFFEAFBFF),
        Color(0xFF71F7FF),
        Color(0xFFFF6EDB)
    )

    Canvas(modifier = modifier.background(GameColors.Background)) {
        bubbles.forEachIndexed { index, bubble ->
            val travel = size.height + bubble.sizePx
            val progress = ((timeMs / bubble.durationMs.toFloat()) + bubble.phase) % 1f
            val x = bubble.xRatio * size.width
            val y = size.height - progress * travel + bubble.sizePx
            drawRect(
                color = colors[index % colors.size].copy(alpha = bubble.alpha),
                topLeft = Offset(x, y),
                size = Size(bubble.sizePx, bubble.sizePx),
                style = Stroke(width = bubble.strokePx)
            )
        }
    }
}

private data class NeonBubble(
    val xRatio: Float,
    val sizePx: Float,
    val durationMs: Float,
    val phase: Float,
    val alpha: Float,
    val strokePx: Float
) {
    companion object {
        fun create(count: Int): List<NeonBubble> {
            val random = Random(20412)
            return List(count) {
                NeonBubble(
                    xRatio = random.nextFloat(),
                    sizePx = random.nextInt(16, 54).toFloat(),
                    durationMs = random.nextInt(8_500, 18_000).toFloat(),
                    phase = random.nextFloat(),
                    alpha = random.nextDouble(0.20, 0.52).toFloat(),
                    strokePx = random.nextDouble(1.2, 2.8).toFloat()
                )
            }
        }
    }
}

@Composable
private fun BoardStage(
    game: GameState,
    engine: GameEngine,
    scores: List<Int>,
    rank: Int?,
    gameOverTitle: String,
    timeMode: Boolean,
    survivalMode: Boolean,
    timeRemainingMs: Long,
    timeTotalMs: Long,
    survivalThreatRatio: Float,
    showSettings: Boolean,
    showRestartConfirm: Boolean,
    showMainMenuConfirm: Boolean,
    showControlsEditor: Boolean,
    soundVolume: Float,
    hapticEnabled: Boolean,
    controlsEditMode: Boolean,
    controlPlacements: List<ControlPlacement>,
    selectedControlAction: ControlAction,
    lineClearProgress: Float,
    hardDropImpact: HardDropImpact?,
    hardDropImpactProgress: Float,
    noiseTimeMs: Long,
    onResume: () -> Unit,
    onRestartRequest: () -> Unit,
    onRestartConfirm: () -> Unit,
    onRestartDismiss: () -> Unit,
    onMainMenuRequest: () -> Unit,
    onMainMenuConfirm: () -> Unit,
    onMainMenuDismiss: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onHapticToggle: () -> Unit,
    onOpenControlsEditor: () -> Unit,
    onControlButtonSizeChange: (ControlAction, Float) -> Unit,
    onControlsReset: () -> Unit,
    onCloseSettings: () -> Unit,
    onCloseControlsEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBoardPanel = game.isPaused || game.isGameOver || showSettings || showRestartConfirm || showMainMenuConfirm || showControlsEditor

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            timeMode -> TimeBoardFrame(
                game = game,
                engine = engine,
                timeRemainingMs = timeRemainingMs,
                timeTotalMs = timeTotalMs,
                lineClearProgress = lineClearProgress,
                hardDropImpact = hardDropImpact,
                hardDropImpactProgress = hardDropImpactProgress,
                noiseTimeMs = noiseTimeMs,
                modifier = Modifier.fillMaxSize()
            )
            survivalMode -> SurvivalBoardFrame(
                game = game,
                engine = engine,
                threatRatio = survivalThreatRatio,
                lineClearProgress = lineClearProgress,
                hardDropImpact = hardDropImpact,
                hardDropImpactProgress = hardDropImpactProgress,
                noiseTimeMs = noiseTimeMs,
                modifier = Modifier.fillMaxSize()
            )
            else -> GameBoardCanvas(
                state = game,
                engine = engine,
                lineClearProgress = lineClearProgress,
                hardDropImpact = hardDropImpact,
                hardDropImpactProgress = hardDropImpactProgress,
                noiseTimeMs = noiseTimeMs,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (hasBoardPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GameColors.Board.copy(alpha = 0.68f))
            )
        }

        when {
            showMainMenuConfirm -> MainMenuConfirmPanel(
                onConfirm = onMainMenuConfirm,
                onDismiss = onMainMenuDismiss
            )
            game.isGameOver -> GameOverPanel(
                score = game.score,
                rank = rank,
                scores = scores,
                title = gameOverTitle,
                onRestart = onRestartConfirm,
                onMainMenu = onMainMenuConfirm
            )
            showSettings -> SettingsPanel(
                volume = soundVolume,
                hapticEnabled = hapticEnabled,
                onResume = onResume,
                onRestart = onRestartRequest,
                onVolumeChange = onVolumeChange,
                onHapticToggle = onHapticToggle,
                onOpenControlsEditor = onOpenControlsEditor,
                onMainMenu = onMainMenuRequest
            )
            showControlsEditor -> ControlsEditorPanel(
                placements = controlPlacements,
                selectedAction = selectedControlAction,
                onButtonSizeChange = onControlButtonSizeChange,
                onReset = onControlsReset,
                onDone = onCloseControlsEditor
            )
            showRestartConfirm -> RestartConfirmPanel(
                onConfirm = onRestartConfirm,
                onDismiss = onRestartDismiss
            )
        }
    }
}

@Composable
private fun SurvivalBoardFrame(
    game: GameState,
    engine: GameEngine,
    threatRatio: Float,
    lineClearProgress: Float,
    hardDropImpact: HardDropImpact?,
    hardDropImpactProgress: Float,
    noiseTimeMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(scaledDp(5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SurvivalThreatGauge(ratio = threatRatio, modifier = Modifier.width(scaledDp(8f)).fillMaxHeight())
        GameBoardCanvas(
            state = game,
            engine = engine,
            lineClearProgress = lineClearProgress,
            hardDropImpact = hardDropImpact,
            hardDropImpactProgress = hardDropImpactProgress,
            noiseTimeMs = noiseTimeMs,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        SurvivalThreatGauge(ratio = threatRatio, modifier = Modifier.width(scaledDp(8f)).fillMaxHeight())
    }
}

@Composable
private fun TimeBoardFrame(
    game: GameState,
    engine: GameEngine,
    timeRemainingMs: Long,
    timeTotalMs: Long,
    lineClearProgress: Float,
    hardDropImpact: HardDropImpact?,
    hardDropImpactProgress: Float,
    noiseTimeMs: Long,
    modifier: Modifier = Modifier
) {
    val timeRatio = if (timeTotalMs <= 0L) {
        0f
    } else {
        (timeRemainingMs / timeTotalMs.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scaledDp(5f))
    ) {
        Text(
            text = formatTime(timeRemainingMs),
            style = scaledTextStyle(MaterialTheme.typography.titleMedium),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = timeGaugeColor(timeRatio),
            maxLines = 1
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(scaledDp(5f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeGauge(ratio = timeRatio, modifier = Modifier.width(scaledDp(8f)).fillMaxHeight())
            GameBoardCanvas(
                state = game,
                engine = engine,
                lineClearProgress = lineClearProgress,
                hardDropImpact = hardDropImpact,
                hardDropImpactProgress = hardDropImpactProgress,
                noiseTimeMs = noiseTimeMs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            TimeGauge(ratio = timeRatio, modifier = Modifier.width(scaledDp(8f)).fillMaxHeight())
        }
    }
}

@Composable
private fun TimeGauge(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val fillColor = timeGaugeColor(ratio)

    Canvas(modifier = modifier) {
        drawRect(color = GameColors.Grid.copy(alpha = 0.72f), size = size)
        val fillHeight = size.height * ratio.coerceIn(0f, 1f)
        drawRect(
            color = fillColor,
            topLeft = Offset(0f, size.height - fillHeight),
            size = Size(size.width, fillHeight)
        )
    }
}

@Composable
private fun SurvivalThreatGauge(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val fillColor = survivalThreatGaugeColor(ratio)

    Canvas(modifier = modifier) {
        drawRect(color = GameColors.Grid.copy(alpha = 0.72f), size = size)
        val fillHeight = size.height * ratio.coerceIn(0f, 1f)
        drawRect(
            color = fillColor,
            topLeft = Offset(0f, size.height - fillHeight),
            size = Size(size.width, fillHeight)
        )
    }
}

private fun timeGaugeColor(ratio: Float): Color =
    when {
        ratio > 0.75f -> Color(0xFF4DE17E)
        ratio > 0.50f -> GameColors.Warning
        ratio > 0.25f -> Color(0xFFFF9F1C)
        else -> GameColors.Danger
    }

private fun survivalThreatGaugeColor(ratio: Float): Color =
    when {
        ratio < 0.25f -> Color(0xFF4DE17E)
        ratio < 0.50f -> GameColors.Warning
        ratio < 0.75f -> Color(0xFFFF9F1C)
        else -> GameColors.Danger
    }

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun CompactGameLayout(
    game: GameState,
    bestScore: Int,
    inputLocked: Boolean,
    onSettings: () -> Unit,
    boardLayer: @Composable (Modifier) -> Unit,
    controls: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePanelWidth = (maxWidth * 0.27f).coerceIn(88.dp, 118.dp)
        val widthScale = sidePanelWidth.value / 118f
        val heightScale = (maxHeight.value / 900f).coerceIn(0.60f, 1f)
        val hudScale = minOf(widthScale, heightScale).coerceIn(0.60f, 1f)
        val panelCompact = hudScale < 0.92f

        CompositionLocalProvider(LocalGameUiScale provides hudScale) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(scaledDp(0f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2.34f)
                        .padding(bottom = scaledDp(0f)),
                    horizontalArrangement = Arrangement.spacedBy(scaledDp(8f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    boardLayer(
                        Modifier
                            .weight(1f)
                            .padding(top = scaledDp(10f))
                            .fillMaxHeight(0.93f)
                    )
                    SidePanel(
                        game = game,
                        bestScore = bestScore,
                        inputLocked = inputLocked,
                        compact = panelCompact,
                        scale = hudScale,
                        onSettings = onSettings,
                        modifier = Modifier
                            .align(Alignment.Top)
                            .width(sidePanelWidth)
                            .fillMaxHeight()
                    )
                }
                controls(
                    Modifier
                        .fillMaxWidth()
                        .weight(1.26f)
                        .padding(top = scaledDp(0f), bottom = scaledDp(4f))
                )
            }
        }
    }
}

@Composable
private fun WideGameLayout(
    game: GameState,
    bestScore: Int,
    inputLocked: Boolean,
    onSettings: () -> Unit,
    boardLayer: @Composable (Modifier) -> Unit,
    controls: @Composable (Modifier) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(0.2f))
        boardLayer(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SidePanel(
                game = game,
                bestScore = bestScore,
                inputLocked = inputLocked,
                onSettings = onSettings
            )
            Spacer(Modifier.weight(0.5f))
            controls(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        Spacer(Modifier.weight(0.2f))
    }
}

@Composable
private fun SidePanel(
    game: GameState,
    bestScore: Int,
    inputLocked: Boolean,
    compact: Boolean = false,
    scale: Float = 1f,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeScale = scale.coerceIn(0.60f, 1f)
    val miniHeightDp = (42f * safeScale).toInt().coerceIn(24, 42)
    val removedMenuHeightDp = 40f * safeScale
    val pauseHeightDp = 40f * safeScale
    val gap = if (compact) (5f * safeScale).dp else (6f * safeScale).dp
    val topReserveHeightDp = removedMenuHeightDp + if (compact) 5f * safeScale else 6f * safeScale

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        Spacer(Modifier.height(topReserveHeightDp.dp))
        StatsPanel(game, bestScore, compact = true)
        HoldPreview(
            pieceType = game.heldPiece,
            enabled = game.canHold,
            miniHeightDp = miniHeightDp,
            compact = compact
        )
        NextPreview(
            pieces = game.queue,
            maxPieces = 5,
            miniHeightDp = miniHeightDp,
            compact = compact
        )
        Button(
            onClick = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(pauseHeightDp.dp),
            enabled = !inputLocked,
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            MenuGlyph()
        }
    }
}

@Composable
private fun MenuGlyph() {
    Column(
        modifier = Modifier
            .width(scaledDp(22f))
            .height(scaledDp(16f)),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledDp(2.4f))
                    .background(GameColors.Text)
            )
        }
    }
}

@Composable
private fun StatsPanel(
    game: GameState,
    bestScore: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val panelColor = Color(0xFF2A2315)
    val scoreColor = GameColors.Warning
    val scoreStyle = scaledTextStyle(if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
    val labelStyle = scaledTextStyle(if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
    val statStyle = scaledTextStyle(if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)

    Surface(
        color = panelColor,
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(scaledDp(1f), scoreColor.copy(alpha = 0.40f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(if (compact) scaledDp(7f) else scaledDp(11f)),
            verticalArrangement = Arrangement.spacedBy(if (compact) scaledDp(4f) else scaledDp(7f))
        ) {
            Text(
                text = "SCORE",
                color = scoreColor,
                style = labelStyle,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = game.score.toString().padStart(6, '0'),
                color = GameColors.Text,
                style = scoreStyle,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BEST", color = GameColors.MutedText, style = statStyle, maxLines = 1)
                Text(
                    bestScore.toString().padStart(6, '0'),
                    color = scoreColor.copy(alpha = 0.92f),
                    style = statStyle,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledDp(1f))
                    .background(scoreColor.copy(alpha = 0.22f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactStat(label = "LN", value = game.lines, style = statStyle)
                CompactStat(label = "LV", value = game.level, style = statStyle)
            }
        }
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: Int,
    style: TextStyle
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(scaledDp(4f))
    ) {
        Text(label, color = GameColors.MutedText, style = style, maxLines = 1)
        Text(
            value.toString().padStart(2, '0'),
            color = GameColors.Text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            style = style,
            maxLines = 1
        )
    }
}
