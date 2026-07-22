package com.smc020412.brigblog.game

import kotlin.random.Random

private const val FallingAttackCellsPerSecond = 18f
private const val RisingAttackCellsPerSecond = 8f

class SurvivalAttackEngine(
    private val random: Random,
    private val collides: (Board, Piece) -> Boolean
) {
    fun start(state: GameState, kind: SurvivalAttackKind): GameState {
        if (state.isGameOver) return state

        val nextObjects = when (kind) {
            SurvivalAttackKind.RisingGarbage -> listOf(
                SurvivalAttackObject(
                    kind = kind,
                    x = 0,
                    y = state.board.height.toFloat(),
                    cells = createRisingGarbageCells(state.board.width)
                )
            )
            SurvivalAttackKind.FallingGarbage -> plannedFallingColumns(state.board.cells).mapIndexed { index, x ->
                SurvivalAttackObject(
                    kind = kind,
                    x = x,
                    y = -1f - index * 0.42f
                )
            }
        }

        return state.copy(attackObjects = state.attackObjects + nextObjects)
    }

    fun tick(state: GameState, deltaMs: Long): GameState {
        if (state.isGameOver || state.attackObjects.isEmpty()) return state

        var boardCells = state.board.cells.map { it.toMutableList() }.toMutableList()
        val nextObjects = mutableListOf<SurvivalAttackObject>()
        var causedGameOver = false
        val fallingStep = FallingAttackCellsPerSecond * deltaMs / 1000f
        val risingStep = RisingAttackCellsPerSecond * deltaMs / 1000f

        state.attackObjects.forEach { attackObject ->
            when (attackObject.kind) {
                SurvivalAttackKind.FallingGarbage -> {
                    val x = attackObject.x.coerceIn(0, state.board.width - 1)
                    val nextY = attackObject.y + fallingStep
                    val landingY = safeFallingAttackY(boardCells, x)

                    if (landingY == null) {
                        causedGameOver = true
                    } else if (nextY >= landingY) {
                        boardCells[landingY][x] = PieceType.Garbage
                    } else {
                        nextObjects += attackObject.copy(x = x, y = nextY)
                    }
                }
                SurvivalAttackKind.RisingGarbage -> {
                    val nextY = attackObject.y - risingStep
                    if (nextY <= state.board.height - 1f) {
                        causedGameOver = causedGameOver || boardCells.firstOrNull()?.any { it != null } == true
                        val row = attackObject.cells
                            .take(state.board.width)
                            .let { cells -> cells + List(state.board.width - cells.size) { false } }
                            .map { filled -> if (filled) PieceType.Garbage else null }
                        boardCells = (boardCells.drop(1) + listOf(row)).map { it.toMutableList() }.toMutableList()
                    } else {
                        nextObjects += attackObject.copy(y = nextY)
                    }
                }
            }
        }

        val nextBoard = state.board.copy(cells = boardCells)
        val resolvedPiece = pushCurrentPieceAboveBoardCollision(nextBoard, state.currentPiece)
        val attackMovedCurrentPiece = resolvedPiece != state.currentPiece
        return state.copy(
            board = nextBoard,
            currentPiece = resolvedPiece,
            isGameOver = state.isGameOver || causedGameOver,
            attackObjects = nextObjects,
            lastActionWasRotation = if (attackMovedCurrentPiece) false else state.lastActionWasRotation,
            lastRotationKickIndex = if (attackMovedCurrentPiece) null else state.lastRotationKickIndex
        )
    }

    private fun pushCurrentPieceAboveBoardCollision(board: Board, currentPiece: Piece?): Piece? {
        var candidate = currentPiece ?: return null
        repeat(board.height + 4) {
            if (!collides(board, candidate)) return candidate
            candidate = candidate.copy(y = candidate.y - 1)
        }
        return candidate
    }

    private fun createRisingGarbageCells(width: Int): List<Boolean> {
        val minFilledCount = 8.coerceAtMost(width - 1)
        val maxFilledCount = (width - 1).coerceAtLeast(minFilledCount)
        val filledCount = random.nextInt(minFilledCount, maxFilledCount + 1)
        val filledColumns = (0 until width).shuffled(random).take(filledCount).toSet()
        return List(width) { x -> x in filledColumns }
    }

    private fun plannedFallingColumns(boardCells: List<List<PieceType?>>): List<Int> {
        val width = boardCells.firstOrNull()?.size ?: return emptyList()
        val columnCounts = mutableMapOf<Int, Int>()
        val plannedBoard = boardCells.map { it.toMutableList() }.toMutableList()
        return buildList {
            repeat(6) {
                val availableColumns = (0 until width).filter { x ->
                    (columnCounts[x] ?: 0) < 2 && canPlaceFallingAttack(plannedBoard, x)
                }
                if (availableColumns.isEmpty()) return@buildList
                val x = availableColumns.random(random)
                val y = safeFallingAttackY(plannedBoard, x) ?: return@buildList
                plannedBoard[y][x] = PieceType.Garbage
                columnCounts[x] = (columnCounts[x] ?: 0) + 1
                add(x)
            }
        }
    }

    private fun canPlaceFallingAttack(boardCells: List<List<PieceType?>>, x: Int): Boolean {
        val y = safeFallingAttackY(boardCells, x) ?: return false
        return y in boardCells.indices && x in boardCells[y].indices && boardCells[y][x] == null
    }

    private fun safeFallingAttackY(boardCells: List<List<PieceType?>>, x: Int): Int? {
        val topOccupiedY = boardCells.indices.firstOrNull { y -> boardCells[y][x] != null }
        val physicalLandingY = if (topOccupiedY == null) boardCells.size - 1 else topOccupiedY - 1
        if (physicalLandingY !in boardCells.indices) return null

        for (y in physicalLandingY downTo 0) {
            if (boardCells[y][x] == null && !wouldCreateCompleteLine(boardCells, x, y)) return y
        }
        return null
    }

    private fun wouldCreateCompleteLine(boardCells: List<List<PieceType?>>, x: Int, y: Int): Boolean =
        boardCells[y].indices.all { column -> boardCells[y][column] != null || column == x }
}

enum class SurvivalAttackKind {
    RisingGarbage,
    FallingGarbage
}
