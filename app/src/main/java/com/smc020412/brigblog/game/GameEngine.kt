package com.smc020412.brigblog.game

import kotlin.math.max
import kotlin.random.Random

private const val FallingAttackCellsPerSecond = 18f
private const val RisingAttackCellsPerSecond = 8f

class GameEngine(
    private val random: Random = Random.Default
) {
    private val playablePieces = listOf(
        PieceType.I,
        PieceType.J,
        PieceType.L,
        PieceType.O,
        PieceType.S,
        PieceType.T,
        PieceType.Z
    )
    private val scoreByLines = intArrayOf(0, 100, 300, 500, 800)
    private val basicKicks = listOf(
        Cell(0, 0), Cell(-1, 0), Cell(1, 0), Cell(0, -1), Cell(-2, 0), Cell(2, 0), Cell(0, 1)
    )
    private val iKicks = listOf(
        Cell(0, 0), Cell(-2, 0), Cell(1, 0), Cell(-1, 0), Cell(2, 0), Cell(0, -1), Cell(0, 1)
    )

    fun createGame(): GameState {
        val board = createEmptyBoard()
        val queue = ensureQueue(emptyList(), GameConstants.PREVIEW_COUNT + 1)
        return spawnPiece(
            GameState(
                board = board,
                queue = queue,
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
                clearingBlocks = emptyList()
            )
        )
    }

    fun move(state: GameState, dx: Int, dy: Int): GameState {
        if (!canAct(state)) return state
        val moved = tryMove(state, dx, dy)
            ?: return state.clearTransientEvents()
        return state.copy(currentPiece = moved)
            .withLockResetCountFrom(state)
            .clearTransientEvents()
    }

    fun rotate(state: GameState, amount: Int): GameState {
        if (!canAct(state)) return state
        val current = state.currentPiece ?: return state
        val rotated = current.copy(rotation = (current.rotation + amount + 4) % 4)
        val kicks = if (current.type == PieceType.I) iKicks else basicKicks

        for (kick in kicks) {
            val candidate = rotated.copy(x = rotated.x + kick.x, y = rotated.y + kick.y)
            if (!collides(state.board, candidate)) {
                return state.copy(currentPiece = candidate)
                    .withLockResetCountFrom(state)
                    .clearTransientEvents()
            }
        }

        return state.clearTransientEvents()
    }

    fun softDrop(state: GameState): GameState {
        if (!canAct(state)) return state
        val moved = tryMove(state, 0, 1)
            ?: return state.clearTransientEvents()
        return state.copy(
            currentPiece = moved,
            score = state.score + 1
        ).copy(lockResetCount = if (isGrounded(state)) state.lockResetCount else 0)
            .clearTransientEvents()
    }

    fun hardDrop(state: GameState): GameState {
        if (!canAct(state)) return state
        var distance = 0
        var current = state.currentPiece ?: return state

        while (true) {
            val next = current.copy(y = current.y + 1)
            if (collides(state.board, next)) break
            current = next
            distance += 1
        }

        return lockCurrent(state.copy(currentPiece = current), distance)
    }

    fun hold(state: GameState): GameState {
        if (!canAct(state) || !state.canHold) return state
        val current = state.currentPiece ?: return state
        val heldType = current.type

        if (state.heldPiece == null) {
            val spawned = spawnPiece(state.copy(heldPiece = heldType, currentPiece = null))
            return spawned.copy(heldPiece = heldType, canHold = false)
        }

        val swapped = makePiece(state.heldPiece)
        return state.copy(
            heldPiece = heldType,
            currentPiece = swapped,
            canHold = false,
            isGameOver = collides(state.board, swapped),
            lastClearedRows = emptyList(),
            lastDropDistance = 0,
            lockResetCount = 0,
            clearingBlocks = emptyList()
        )
    }

    fun tick(state: GameState): GameState {
        if (!canAct(state)) return state
        val moved = tryMove(state, 0, 1)
            ?: return lockCurrent(state, 0)
        return state.copy(currentPiece = moved).clearTransientEvents()
    }

    fun lockCurrent(state: GameState, dropDistance: Int = 0): GameState {
        if (!canAct(state)) return state
        val current = state.currentPiece ?: return state
        val cells = getCells(current)

        if (cells.any { it.y < 0 }) {
            return state.copy(isGameOver = true, lastDropDistance = dropDistance)
        }

        val boardCells = state.board.cells.map { it.toMutableList() }.toMutableList()
        cells.forEach { cell ->
            boardCells[cell.y][cell.x] = current.type
        }

        val lockedBoard = Board(state.board.width, state.board.height, boardCells)
        val clearedRows = findClearedRows(lockedBoard)
        val clearingBlocks = clearedRows.flatMap { y ->
            lockedBoard.cells[y].mapIndexedNotNull { x, type ->
                type?.let { ClearingBlock(x = x, y = y, type = it) }
            }
        }
        val clearCount = clearedRows.size
        val dropScore = dropDistance * 2

        if (clearCount == 0) {
            val spawned = spawnPiece(
                state.copy(
                    board = lockedBoard,
                    currentPiece = null,
                    score = state.score + dropScore
                )
            )

            return spawned.copy(
                lastClearedRows = emptyList(),
                lastDropDistance = dropDistance
            )
        }

        val newLines = state.lines + clearCount
        val newLevel = newLines / 10 + 1
        val clearScore = scoreByLines.getOrElse(clearCount) { 0 } * newLevel

        return state.copy(
            board = lockedBoard,
            currentPiece = null,
            score = state.score + clearScore + dropScore,
            lines = newLines,
            level = newLevel,
            canHold = false,
            lastClearedRows = clearedRows,
            lastDropDistance = dropDistance,
            lockResetCount = 0,
            clearingBlocks = clearingBlocks
        )
    }

    fun finishLineClear(state: GameState): GameState {
        if (!state.isClearingLines) return state

        val cleared = clearRows(state.board)
        return spawnPiece(
            state.copy(
                board = cleared.board,
                currentPiece = null,
                lastClearedRows = emptyList(),
                lastDropDistance = 0,
                clearingBlocks = emptyList()
            )
        )
    }

    fun startSurvivalAttack(state: GameState, kind: SurvivalAttackKind): GameState {
        if (state.isGameOver) return state

        val nextObjects = when (kind) {
            SurvivalAttackKind.RisingGarbage -> {
                listOf(
                    SurvivalAttackObject(
                        kind = kind,
                        x = 0,
                        y = state.board.height.toFloat(),
                        cells = createRisingGarbageCells(state.board.width)
                    )
                )
            }
            SurvivalAttackKind.FallingGarbage -> {
                plannedFallingColumns(state.board.cells).mapIndexed { index, x ->
                    SurvivalAttackObject(
                        kind = kind,
                        x = x,
                        y = -1f - index * 0.42f
                    )
                }
            }
        }

        return state.copy(attackObjects = state.attackObjects + nextObjects)
    }

    fun tickSurvivalAttacks(state: GameState, deltaMs: Long): GameState {
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
                    val targetY = state.board.height - 1f

                    if (nextY <= targetY) {
                        causedGameOver = causedGameOver || boardCells.firstOrNull()?.any { it != null } == true
                        val row = attackObject.cells
                            .take(state.board.width)
                            .let { cells ->
                                cells + List(state.board.width - cells.size) { false }
                            }
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

        return state.copy(
            board = nextBoard,
            currentPiece = resolvedPiece,
            isGameOver = state.isGameOver || causedGameOver,
            attackObjects = nextObjects
        )
    }

    fun isGrounded(state: GameState): Boolean =
        canAct(state) && tryMove(state, 0, 1) == null

    fun ghostPiece(state: GameState): Piece? {
        var current = state.currentPiece ?: return null
        while (true) {
            val next = current.copy(y = current.y + 1)
            if (collides(state.board, next)) return current
            current = next
        }
    }

    fun dangerLevel(state: GameState): Float {
        val highest = state.board.cells.indexOfFirst { row -> row.any { it != null } }
        if (highest == -1 || highest >= 8) return 0f
        return (8 - highest) / 8f
    }

    fun gravityIntervalMs(level: Int): Long =
        max(
            GameConstants.MIN_GRAVITY_MS,
            GameConstants.INITIAL_GRAVITY_MS - (level - 1) * GameConstants.GRAVITY_STEP_MS
        )

    fun getCells(piece: Piece): List<Cell> =
        PieceShapes.shapes.getValue(piece.type)[piece.rotation].map { cell ->
            Cell(cell.x + piece.x, cell.y + piece.y)
        }

    fun collides(board: Board, piece: Piece): Boolean =
        getCells(piece).any { cell ->
            cell.x < 0 ||
                cell.x >= board.width ||
                cell.y >= board.height ||
                (cell.y >= 0 && board.cells[cell.y][cell.x] != null)
        }

    private fun createEmptyBoard(): Board =
        Board(
            width = GameConstants.BOARD_WIDTH,
            height = GameConstants.BOARD_HEIGHT,
            cells = List(GameConstants.BOARD_HEIGHT) {
                List<PieceType?>(GameConstants.BOARD_WIDTH) { null }
            }
        )

    private fun createBag(): List<PieceType> =
        playablePieces.shuffled(random)

    private fun ensureQueue(queue: List<PieceType>, minCount: Int): List<PieceType> {
        val nextQueue = queue.toMutableList()
        while (nextQueue.size < minCount) {
            nextQueue.addAll(createBag())
        }
        return nextQueue
    }

    private fun makePiece(type: PieceType): Piece =
        Piece(type = type, rotation = 0, x = 3, y = -1)

    private fun spawnPiece(state: GameState): GameState {
        val queue = ensureQueue(state.queue, GameConstants.PREVIEW_COUNT + 1)
        val current = makePiece(queue.first())
        val remaining = ensureQueue(queue.drop(1), GameConstants.PREVIEW_COUNT)

        return state.copy(
            currentPiece = current,
            queue = remaining,
            canHold = true,
            isGameOver = state.isGameOver || collides(state.board, current),
            lastClearedRows = emptyList(),
            lastDropDistance = 0,
            lockResetCount = 0,
            clearingBlocks = state.clearingBlocks
        )
    }

    private fun canAct(state: GameState): Boolean =
        state.currentPiece != null && !state.isGameOver && !state.isPaused && !state.isClearingLines

    private fun tryMove(state: GameState, dx: Int, dy: Int): Piece? {
        val current = state.currentPiece ?: return null
        val moved = current.copy(x = current.x + dx, y = current.y + dy)
        return if (collides(state.board, moved)) null else moved
    }

    private data class ClearResult(
        val board: Board,
        val rows: List<Int>
    )

    private fun findClearedRows(board: Board): List<Int> =
        board.cells.mapIndexedNotNull { y, row ->
            if (row.all { it != null }) y else null
        }

    private fun clearRows(board: Board): ClearResult {
        val keptRows = mutableListOf<List<PieceType?>>()
        val clearedRows = findClearedRows(board)

        board.cells.forEachIndexed { y, row ->
            if (y !in clearedRows) {
                keptRows.add(row)
            }
        }

        val emptyRows = List(board.height - keptRows.size) {
            List<PieceType?>(board.width) { null }
        }

        return ClearResult(
            board = board.copy(cells = emptyRows + keptRows),
            rows = clearedRows
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

private fun canPlaceFallingAttack(
    boardCells: List<List<PieceType?>>,
    x: Int
): Boolean {
    val y = safeFallingAttackY(boardCells, x) ?: return false
    return y in boardCells.indices &&
        x in boardCells[y].indices &&
        boardCells[y][x] == null
}

    private fun landingYForColumn(boardCells: List<List<PieceType?>>, x: Int): Int {
        val topOccupiedY = boardCells.indices.firstOrNull { y ->
            boardCells[y][x] != null
        }
        return if (topOccupiedY == null) {
            boardCells.size - 1
        } else {
            topOccupiedY - 1
        }
    }

private fun safeFallingAttackY(
    boardCells: List<List<PieceType?>>,
    x: Int
): Int? {
    val physicalLandingY = landingYForColumn(boardCells, x)
    if (physicalLandingY !in boardCells.indices) return null

    for (y in physicalLandingY downTo 0) {
        if (x in boardCells[y].indices &&
            boardCells[y][x] == null &&
            !wouldCreateCompleteLine(boardCells, x, y)
        ) {
            return y
        }
    }

    return null
}

private fun wouldCreateCompleteLine(
    boardCells: List<List<PieceType?>>,
    x: Int,
    y: Int
): Boolean {
    if (y !in boardCells.indices) return false
    val row = boardCells[y]
    if (x !in row.indices || row[x] != null) return false
    return row.indices.all { column ->
        row[column] != null || column == x
    }
}

    private fun GameState.clearTransientEvents(): GameState =
        copy(lastClearedRows = emptyList(), lastDropDistance = 0)

    private fun GameState.withLockResetCountFrom(previous: GameState): GameState {
        if (!isGrounded(previous)) return copy(lockResetCount = 0)
        if (!isGrounded(this)) return copy(lockResetCount = previous.lockResetCount)
        val nextCount = (previous.lockResetCount + 1)
            .coerceAtMost(GameConstants.LOCK_RESET_LIMIT)
        return copy(lockResetCount = nextCount)
    }
}

enum class SurvivalAttackKind {
    RisingGarbage,
    FallingGarbage
}
