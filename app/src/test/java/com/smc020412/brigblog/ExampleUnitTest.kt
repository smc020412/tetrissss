package com.smc020412.brigblog

import com.smc020412.brigblog.game.Board
import com.smc020412.brigblog.game.GameConstants
import com.smc020412.brigblog.game.GameEngine
import com.smc020412.brigblog.game.GameState
import com.smc020412.brigblog.game.Piece
import com.smc020412.brigblog.game.PieceType
import com.smc020412.brigblog.game.SurvivalAttackKind
import com.smc020412.brigblog.game.SurvivalAttackObject
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
        assertEquals(42, dropped.score)
        assertNotNull(dropped.currentPiece)
    }

    @Test
    fun lockingPieceStartsLineClearWhenRowIsFilled() {
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
        val finished = engine.finishLineClear(locked)

        assertTrue(locked.isClearingLines)
        assertEquals(listOf(bottomY), locked.lastClearedRows)
        assertEquals(1, locked.lines)
        assertEquals(100, locked.score)
        assertFalse(finished.isClearingLines)
        assertTrue(finished.board.cells.flatten().all { it == null })
        assertNotNull(finished.currentPiece)
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
