package com.smc020412.brigblog

import com.smc020412.brigblog.game.Board
import com.smc020412.brigblog.game.Cell
import com.smc020412.brigblog.game.ClearKind
import com.smc020412.brigblog.game.GameConstants
import com.smc020412.brigblog.game.GameEngine
import com.smc020412.brigblog.game.GameScoring
import com.smc020412.brigblog.game.GameState
import com.smc020412.brigblog.game.Piece
import com.smc020412.brigblog.game.PieceType
import com.smc020412.brigblog.game.SurvivalAttackKind
import com.smc020412.brigblog.game.SurvivalAttackObject
import com.smc020412.brigblog.game.TSpinType
import com.smc020412.brigblog.audio.MenuMusicSessionGate
import com.smc020412.brigblog.audio.sliderVolumeToGain
import com.smc020412.brigblog.haptic.GameHapticEvent
import com.smc020412.brigblog.haptic.HapticPulseGate
import com.smc020412.brigblog.data.insertScore
import com.smc020412.brigblog.ui.AppMode
import com.smc020412.brigblog.ui.GameSessionViewModel
import com.smc020412.brigblog.ui.TimeLeaderboardDuration
import androidx.lifecycle.SavedStateHandle
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun moveUpdatesPiecePositionAndStopsAtWall() {
        val engine = GameEngine()
        val state = stateWithPiece(Piece(PieceType.O, x = 3, y = 0))

        val moved = engine.move(state, dx = -1, dy = 0)
        val blocked = engine.move(moved.copy(currentPiece = moved.currentPiece?.copy(x = -1)), dx = -1, dy = 0)

        assertEquals(2, moved.currentPiece?.x)
        assertEquals(-1, blocked.currentPiece?.x)
    }

    @Test
    fun rotateChangesPieceRotation() {
        val engine = GameEngine()
        val state = stateWithPiece(Piece(PieceType.T, rotation = 0, x = 3, y = 0))

        val rotated = engine.rotate(state, amount = 1)

        assertEquals(1, rotated.currentPiece?.rotation)
    }

    @Test
    fun holdStoresCurrentPieceAndPreventsSecondHoldBeforeLock() {
        val engine = GameEngine()
        val state = stateWithPiece(
            piece = Piece(PieceType.T, x = 3, y = 0),
            queue = listOf(PieceType.I, PieceType.J, PieceType.L, PieceType.S, PieceType.Z, PieceType.O)
        )

        val held = engine.hold(state)
        val secondHold = engine.hold(held)

        assertEquals(PieceType.T, held.heldPiece)
        assertEquals(PieceType.I, held.currentPiece?.type)
        assertFalse(held.canHold)
        assertEquals(held, secondHold)
    }

    @Test
    fun hardDropLocksPieceOnBottomAndScoresDropDistance() {
        val engine = GameEngine()
        val state = stateWithPiece(Piece(PieceType.O, x = 3, y = 0))

        val dropped = engine.hardDrop(state)

        assertEquals(PieceType.O, dropped.board.cells[21][4])
        assertEquals(PieceType.O, dropped.board.cells[21][5])
        assertEquals(PieceType.O, dropped.board.cells[22][4])
        assertEquals(PieceType.O, dropped.board.cells[22][5])
        assertEquals(21, dropped.score)
        assertNotNull(dropped.currentPiece)
    }

    @Test
    fun lineClearTemporarilyBlocksPlayerActionsUntilTheEffectEnds() {
        val engine = GameEngine()
        val bottomY = GameConstants.BOARD_HEIGHT - 1
        val board = boardWithCells { x, y ->
            when {
                y == bottomY && x !in 3..6 -> PieceType.Garbage
                y == bottomY - 1 && x == 0 -> PieceType.T
                else -> null
            }
        }
        val state = stateWithPiece(
            piece = Piece(PieceType.I, rotation = 0, x = 3, y = bottomY - 1),
            board = board
        )

        val locked = engine.lockCurrent(state)
        val finished = engine.finishLineClear(locked)
        val movedDuringClear = engine.move(locked, dx = 1, dy = 0)
        val rotatedDuringClear = engine.rotate(locked, amount = 1)
        val droppedDuringClear = engine.hardDrop(locked)
        val heldDuringClear = engine.hold(locked)
        val movedAfterClear = engine.move(finished, dx = 1, dy = 0)

        assertTrue(locked.isClearingLines)
        assertEquals(listOf(bottomY), locked.lastClearedRows)
        assertEquals(1, locked.lines)
        assertEquals(10, locked.score)
        assertEquals(PieceType.T, locked.board.cells[bottomY][0])
        assertNotNull(locked.currentPiece)
        assertEquals(1, locked.lineClearShiftBlocks.size)
        assertEquals(bottomY - 1, locked.lineClearShiftBlocks.single().sourceY)
        assertEquals(bottomY, locked.lineClearShiftBlocks.single().targetY)
        assertEquals(locked, movedDuringClear)
        assertEquals(locked, rotatedDuringClear)
        assertEquals(locked, droppedDuringClear)
        assertEquals(locked, heldDuringClear)
        assertFalse(finished.isClearingLines)
        assertEquals(PieceType.T, finished.board.cells[bottomY][0])
        assertEquals(locked.currentPiece, finished.currentPiece)
        assertNotEquals(finished.currentPiece, movedAfterClear.currentPiece)
    }

    @Test
    fun engineMarksPerfectClearOnlyAfterTheClearedBoardHasNoBlocks() {
        val engine = GameEngine()
        val bottomY = GameConstants.BOARD_HEIGHT - 1
        val board = boardWithCells { x, y ->
            if (y == bottomY && x !in 3..6) PieceType.Garbage else null
        }
        val state = stateWithPiece(
            piece = Piece(PieceType.I, rotation = 0, x = 3, y = bottomY - 1),
            board = board
        )

        val locked = engine.lockCurrent(state)

        assertTrue(locked.lastClearResult?.isPerfectClear == true)
        assertEquals(50, locked.lastClearResult?.perfectClearBonus)
        assertEquals(60, locked.score)
        assertTrue(locked.board.cells.flatten().all { it == null })
    }

    @Test
    fun groundedMovementConsumesLockResetsOnlyUpToLimit() {
        val engine = GameEngine()
        var state = groundedState()

        repeat(GameConstants.LOCK_RESET_LIMIT) { index ->
            state = engine.move(state, if (index % 2 == 0) -1 else 1, 0)
            assertEquals(index + 1, state.lockResetCount)
        }

        state = engine.move(state, -1, 0)

        assertEquals(GameConstants.LOCK_RESET_LIMIT, state.lockResetCount)
    }

    @Test
    fun lockingPieceResetsLockResetCountForNextPiece() {
        val engine = GameEngine()
        val state = groundedState().copy(lockResetCount = GameConstants.LOCK_RESET_LIMIT)

        val next = engine.lockCurrent(state)

        assertEquals(0, next.lockResetCount)
    }

    @Test
    fun gravityUsesLinearFallSpeedFromLevelOneUntilTheSpeedCap() {
        val engine = GameEngine()

        assertEquals(680L, engine.gravityIntervalMs(1))
        assertEquals(98L, engine.gravityIntervalMs(10))
        assertEquals(89L, engine.gravityIntervalMs(11))
        assertEquals(50L, engine.gravityIntervalMs(GameConstants.MAX_SPEED_LEVEL))
        assertEquals(50L, engine.gravityIntervalMs(GameConstants.MAX_SPEED_LEVEL + 1))
    }

    @Test
    fun comboAndBackToBackAwardOnlyTheQualifiedFollowUpBonus() {
        val firstQuad = GameScoring.resolveClear(
            clearedLines = 4,
            level = 1,
            comboCount = 0,
            backToBackCount = 0,
            tSpinType = TSpinType.None
        )
        val secondQuad = GameScoring.resolveClear(
            clearedLines = 4,
            level = 1,
            comboCount = firstQuad.comboCount,
            backToBackCount = firstQuad.backToBackCount,
            tSpinType = TSpinType.None
        )
        val normalClear = GameScoring.resolveClear(
            clearedLines = 1,
            level = 1,
            comboCount = secondQuad.comboCount,
            backToBackCount = secondQuad.backToBackCount,
            tSpinType = TSpinType.None
        )

        assertEquals(80, firstQuad.scoreAwarded)
        assertFalse(firstQuad.usedBackToBackBonus)
        assertEquals(125, secondQuad.scoreAwarded)
        assertTrue(secondQuad.usedBackToBackBonus)
        assertEquals(0, normalClear.backToBackCount)
    }

    @Test
    fun perfectClearAwardsALineDependentBonusOnlyWhenTheBoardIsEmptyAfterClear() {
        val perfectSingle = GameScoring.resolveClear(
            clearedLines = 1,
            level = 1,
            comboCount = 0,
            backToBackCount = 0,
            tSpinType = TSpinType.None,
            isPerfectClear = true
        )
        val normalSingle = GameScoring.resolveClear(
            clearedLines = 1,
            level = 1,
            comboCount = 0,
            backToBackCount = 0,
            tSpinType = TSpinType.None
        )
        val perfectQuad = GameScoring.resolveClear(
            clearedLines = 4,
            level = 2,
            comboCount = 0,
            backToBackCount = 0,
            tSpinType = TSpinType.None,
            isPerfectClear = true
        )

        assertTrue(perfectSingle.isPerfectClear)
        assertEquals(50, perfectSingle.perfectClearBonus)
        assertEquals(60, perfectSingle.scoreAwarded)
        assertFalse(normalSingle.isPerfectClear)
        assertEquals(0, normalSingle.perfectClearBonus)
        assertEquals(500, perfectQuad.perfectClearBonus)
        assertEquals(660, perfectQuad.scoreAwarded)
    }

    @Test
    fun miniTSpinTripleIsPromotedToAFullTSpinTripleForScoring() {
        val result = GameScoring.resolveClear(
            clearedLines = 3,
            level = 1,
            comboCount = 0,
            backToBackCount = 0,
            tSpinType = TSpinType.Mini
        )

        assertEquals(ClearKind.TSpinTriple, result.kind)
        assertFalse(result.isMiniTSpin)
        assertEquals(100, result.scoreAwarded)
    }

    @Test
    fun everyReachableTSpinTripleUsesTheFullTSpinClassification() {
        val engine = GameEngine()
        val lockY = GameConstants.BOARD_HEIGHT - 3

        // A newly-cleared triple requires a vertical T. Fill each of its three rows except
        // the T cells, then verify every horizontal placement follows the Full-T rule.
        for (rotation in listOf(1, 3)) {
            for (x in 0..(GameConstants.BOARD_WIDTH - 3)) {
                val piece = Piece(PieceType.T, rotation = rotation, x = x, y = lockY)
                val pieceCells = engine.getCells(piece).toSet()
                val board = boardWithCells { boardX, boardY ->
                    if (boardY in lockY..(lockY + 2) && Cell(boardX, boardY) !in pieceCells) {
                        PieceType.Garbage
                    } else {
                        null
                    }
                }

                val locked = engine.lockCurrent(
                    stateWithPiece(piece, board).copy(lastActionWasRotation = true)
                )

                assertEquals("rotation=$rotation, x=$x", 3, locked.lastClearedRows.size)
                assertEquals("rotation=$rotation, x=$x", ClearKind.TSpinTriple, locked.lastClearResult?.kind)
                assertFalse("rotation=$rotation, x=$x", locked.lastClearResult?.isMiniTSpin == true)
            }
        }
    }

    @Test
    fun tSpinRequiresTheLastSuccessfulActionToBeARotationAndThreeCorners() {
        val engine = GameEngine()
        val bottomY = GameConstants.BOARD_HEIGHT - 1
        val board = boardWithCells { x, y ->
            when {
                y == bottomY && x !in 3..5 -> PieceType.Garbage
                y == bottomY - 1 && x in setOf(3, 5) -> PieceType.Garbage
                else -> null
            }
        }
        val tPiece = Piece(PieceType.T, rotation = 0, x = 3, y = bottomY - 1)

        val spun = engine.lockCurrent(stateWithPiece(tPiece, board).copy(lastActionWasRotation = true))
        val ordinary = engine.lockCurrent(stateWithPiece(tPiece, board))

        assertEquals(ClearKind.TSpinSingle, spun.lastClearResult?.kind)
        assertEquals(40, spun.lastClearResult?.scoreAwarded)
        assertEquals(ClearKind.Single, ordinary.lastClearResult?.kind)
        assertEquals(10, ordinary.lastClearResult?.scoreAwarded)
    }

    @Test
    fun successfulMovementOrGravityCancelsTheTSpinRotationHistory() {
        val engine = GameEngine()
        val rotated = engine.rotate(stateWithPiece(Piece(PieceType.T, x = 3, y = 0)), amount = 1)

        val moved = engine.move(rotated, dx = 1, dy = 0)
        val fallen = engine.tick(rotated)

        assertTrue(rotated.lastActionWasRotation)
        assertFalse(moved.lastActionWasRotation)
        assertFalse(fallen.lastActionWasRotation)
    }

    @Test
    fun survivalAttackMovementCancelsTheTSpinRotationHistory() {
        val engine = GameEngine()
        val board = boardWithCells { x, y ->
            if (y == GameConstants.BOARD_HEIGHT - 2 && x in 4..5) PieceType.Garbage else null
        }
        val state = survivalState(
            board = board,
            attackObjects = listOf(
                SurvivalAttackObject(
                    kind = SurvivalAttackKind.RisingGarbage,
                    x = 0,
                    y = GameConstants.BOARD_HEIGHT.toFloat(),
                    cells = List(GameConstants.BOARD_WIDTH) { false }
                )
            )
        ).copy(
            currentPiece = Piece(PieceType.T, x = 3, y = GameConstants.BOARD_HEIGHT - 4),
            lastActionWasRotation = true
        )

        val attacked = engine.tickSurvivalAttacks(state, deltaMs = 1_000L)

        assertFalse(attacked.lastActionWasRotation)
        assertNotEquals(state.currentPiece, attacked.currentPiece)
    }

    @Test
    fun noClearResetsComboButKeepsTheBackToBackChain() {
        val engine = GameEngine()
        val state = stateWithPiece(Piece(PieceType.O, x = 3, y = GameConstants.BOARD_HEIGHT - 2)).copy(
            comboCount = 4,
            backToBackCount = 2,
            heatLevel = 0.7f
        )

        val next = engine.lockCurrent(state)

        assertEquals(0, next.comboCount)
        assertEquals(2, next.backToBackCount)
        assertEquals(0.16f, next.heatLevel)
        assertNull(next.lastClearResult)
    }

    @Test
    fun fallingGarbageDoesNotFillLineClearGapOrDisappear() {
        val engine = GameEngine()
        val gapX = 4
        val bottomY = GameConstants.BOARD_HEIGHT - 1
        val board = boardWithCells { x, y ->
            if (y == bottomY && x != gapX) PieceType.Garbage else null
        }
        val state = survivalState(
            board = board,
            attackObjects = listOf(
                SurvivalAttackObject(
                    kind = SurvivalAttackKind.FallingGarbage,
                    x = gapX,
                    y = (bottomY - 2).toFloat()
                )
            )
        )

        val next = engine.tickSurvivalAttacks(state, deltaMs = 1_000L)

        assertNull(next.board.cells[bottomY][gapX])
        assertEquals(PieceType.Garbage, next.board.cells[bottomY - 1][gapX])
        assertTrue(next.attackObjects.isEmpty())
    }

    @Test
    fun risingGarbagePushesBoardUpAndKeepsAGap() {
        val engine = GameEngine()
        val attackCells = List(GameConstants.BOARD_WIDTH) { x -> x != 2 }
        val state = survivalState(
            board = emptyBoard(),
            attackObjects = listOf(
                SurvivalAttackObject(
                    kind = SurvivalAttackKind.RisingGarbage,
                    x = 0,
                    y = GameConstants.BOARD_HEIGHT.toFloat(),
                    cells = attackCells
                )
            )
        )

        val next = engine.tickSurvivalAttacks(state, deltaMs = 1_000L)
        val bottomRow = next.board.cells[GameConstants.BOARD_HEIGHT - 1]

        assertNull(bottomRow[2])
        assertEquals(GameConstants.BOARD_WIDTH - 1, bottomRow.count { it == PieceType.Garbage })
        assertFalse(next.isGameOver)
        assertTrue(next.attackObjects.isEmpty())
    }

    @Test
    fun hapticGateLetsImportantEventsPreemptInputAndBlocksInputDuringThem() {
        val gate = HapticPulseGate()

        assertTrue(gate.tryAcquire(GameHapticEvent.Input, nowMs = 1_000L))
        assertTrue(gate.tryAcquire(GameHapticEvent.HardDrop, nowMs = 1_001L))
        assertTrue(gate.tryAcquire(GameHapticEvent.LineClear, nowMs = 1_002L))
        assertFalse(gate.tryAcquire(GameHapticEvent.Input, nowMs = 1_010L))
    }

    @Test
    fun hapticGateStillAppliesCooldownToRepeatedEvents() {
        val gate = HapticPulseGate()

        assertTrue(gate.tryAcquire(GameHapticEvent.Input, nowMs = 1_000L))
        assertFalse(gate.tryAcquire(GameHapticEvent.Input, nowMs = 1_020L))
        assertTrue(gate.tryAcquire(GameHapticEvent.Input, nowMs = 1_030L))
    }

    @Test
    fun staleMenuMusicSessionCannotStopANewerSession() {
        val sessions = MenuMusicSessionGate()
        val firstSession = sessions.start()

        sessions.stop()
        val secondSession = sessions.start()
        sessions.finish(firstSession)

        assertFalse(sessions.isActive(firstSession))
        assertTrue(sessions.isActive(secondSession))
        sessions.finish(secondSession)
        assertFalse(sessions.hasActiveSession())
    }

    @Test
    fun soundVolumeSliderUsesAClampedLinearGain() {
        assertEquals(0f, sliderVolumeToGain(-0.25f))
        assertEquals(0.25f, sliderVolumeToGain(0.25f))
        assertEquals(0.5f, sliderVolumeToGain(0.5f))
        assertEquals(0.75f, sliderVolumeToGain(0.75f))
        assertEquals(1f, sliderVolumeToGain(1.25f))
    }

    @Test
    fun gameSessionRestoresActiveGameAndPausesItSafely() {
        val handle = SavedStateHandle()
        val original = GameSessionViewModel(handle)
        original.appMode = AppMode.SurvivalGame
        original.game = stateWithPiece(
            piece = Piece(PieceType.T, rotation = 2, x = 4, y = 9),
            queue = listOf(PieceType.I, PieceType.J, PieceType.L, PieceType.S, PieceType.Z)
        ).copy(
            score = 1_200,
            lines = 17,
            level = 2,
            comboCount = 3,
            backToBackCount = 2,
            heatLevel = 0.58f,
            lastActionWasRotation = true
        )
        original.timeRemainingMs = 75_000L
        original.selectedTimeDuration = TimeLeaderboardDuration.TwoMinutes
        original.pendingSurvivalAttacks = listOf(SurvivalAttackKind.FallingGarbage)
        original.persistUiState()

        val restored = GameSessionViewModel(handle)

        assertEquals(AppMode.SurvivalGame, restored.appMode)
        assertEquals(PieceType.T, restored.game.currentPiece?.type)
        assertEquals(4, restored.game.currentPiece?.x)
        assertEquals(9, restored.game.currentPiece?.y)
        assertEquals(1_200, restored.game.score)
        assertEquals(3, restored.game.comboCount)
        assertEquals(2, restored.game.backToBackCount)
        assertEquals(0.58f, restored.game.heatLevel)
        assertTrue(restored.game.lastActionWasRotation)
        assertEquals(75_000L, restored.timeRemainingMs)
        assertEquals(TimeLeaderboardDuration.TwoMinutes, restored.selectedTimeDuration)
        assertEquals(listOf(SurvivalAttackKind.FallingGarbage), restored.pendingSurvivalAttacks)
        assertTrue(restored.game.isPaused)
        assertTrue(restored.showSettings)
    }

    @Test
    fun scoreInsertionHighlightsTheNewEntryAfterExistingTies() {
        val result = insertScore(existingScores = listOf(900, 700, 700, 500), score = 700)

        assertEquals(listOf(900, 700, 700, 700, 500), result.scores)
        assertEquals(4, result.insertedRank)
    }

    @Test
    fun scoreOutsideTopTenDoesNotReceiveARank() {
        val result = insertScore(existingScores = List(10) { 700 }, score = 700)

        assertEquals(List(10) { 700 }, result.scores)
        assertNull(result.insertedRank)
    }

    @Test
    fun tSpinMiniUsesTheBackCornerPatternAndGetsMiniScore() {
        val engine = GameEngine()
        val bottomY = GameConstants.BOARD_HEIGHT - 1
        val board = boardWithCells { x, y ->
            when {
                y == bottomY && x != 4 -> PieceType.Garbage
                y == bottomY - 1 && x == 3 -> PieceType.Garbage
                else -> null
            }
        }
        val tPiece = Piece(PieceType.T, rotation = 0, x = 3, y = bottomY - 1)

        val spun = engine.lockCurrent(stateWithPiece(tPiece, board).copy(lastActionWasRotation = true))

        assertEquals(ClearKind.TSpinMiniSingle, spun.lastClearResult?.kind)
        assertEquals(20, spun.lastClearResult?.scoreAwarded)
        assertTrue(spun.lastClearResult?.isMiniTSpin == true)
    }
    private fun groundedState(): GameState {
        return stateWithPiece(
            board = emptyBoard(),
            piece = Piece(PieceType.O, x = 3, y = GameConstants.BOARD_HEIGHT - 2),
            queue = listOf(PieceType.I, PieceType.J, PieceType.L, PieceType.S, PieceType.T, PieceType.Z),
        )
    }

    private fun stateWithPiece(
        piece: Piece,
        board: Board = emptyBoard(),
        queue: List<PieceType> = listOf(PieceType.I, PieceType.J, PieceType.L, PieceType.S, PieceType.T, PieceType.Z)
    ): GameState =
        GameState(
            board = board,
            queue = queue,
            currentPiece = piece,
            heldPiece = null,
            canHold = true,
            score = 0,
            lines = 0,
            level = 1,
            isGameOver = false,
            isPaused = false,
            lastClearedRows = emptyList(),
            lastDropDistance = 0
        )

    private fun survivalState(
        board: Board,
        attackObjects: List<SurvivalAttackObject>
    ): GameState =
        GameState(
            board = board,
            queue = listOf(PieceType.I, PieceType.J, PieceType.L, PieceType.S, PieceType.T, PieceType.Z),
            currentPiece = null,
            heldPiece = null,
            canHold = true,
            score = 0,
            lines = 0,
            level = 1,
            isGameOver = false,
            isPaused = false,
            lastClearedRows = emptyList(),
            lastDropDistance = 0,
            attackObjects = attackObjects
        )

    private fun emptyBoard(): Board =
        boardWithCells { _, _ -> null }

    private fun boardWithCells(cellAt: (x: Int, y: Int) -> PieceType?): Board =
        Board(
            width = GameConstants.BOARD_WIDTH,
            height = GameConstants.BOARD_HEIGHT,
            cells = List(GameConstants.BOARD_HEIGHT) { y ->
                List(GameConstants.BOARD_WIDTH) { x ->
                    cellAt(x, y)
                }
            }
        )
}
