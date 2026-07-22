package com.smc020412.brigblog.game

import kotlin.math.roundToLong
import kotlin.random.Random

class GameEngine(
    private val random: Random = Random.Default
) {
    private val bagRandomizer = SevenBagRandomizer(random)
    private val survivalAttackEngine = SurvivalAttackEngine(random) { board, piece -> collides(board, piece) }

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
        return state.copy(currentPiece = moved, lastActionWasRotation = false, lastRotationKickIndex = null)
            .withLockResetCountFrom(state)
            .clearTransientEvents()
    }

    fun rotate(state: GameState, amount: Int): GameState {
        if (!canAct(state)) return state
        val current = state.currentPiece ?: return state
        val targetRotation = (current.rotation + amount + 4) % 4
        if (!SrsRotationSystem.isQuarterTurn(current.rotation, targetRotation)) return state.clearTransientEvents()
        val rotated = current.copy(rotation = targetRotation)
        val kicks = SrsRotationSystem.kicksFor(current.type, current.rotation, targetRotation)

        for ((kickIndex, kick) in kicks.withIndex()) {
            val candidate = rotated.copy(x = rotated.x + kick.x, y = rotated.y + kick.y)
            if (!collides(state.board, candidate)) {
                return state.copy(
                    currentPiece = candidate,
                    lastActionWasRotation = true,
                    lastRotationKickIndex = kickIndex
                )
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
            lastActionWasRotation = false,
            lastRotationKickIndex = null
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
            clearingBlocks = state.clearingBlocks,
            lastActionWasRotation = false,
            lastRotationKickIndex = null
        )
    }

    fun tick(state: GameState): GameState {
        if (!canAct(state)) return state
        val moved = tryMove(state, 0, 1)
            ?: return lockCurrent(state, 0)
        return state.copy(
            currentPiece = moved,
            lastActionWasRotation = false,
            lastRotationKickIndex = null
        ).clearTransientEvents()
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
        val lineClearShiftBlocks = lockedBoard.cells.flatMapIndexed { y, row ->
            val clearedBelow = clearedRows.count { clearedY -> clearedY > y }
            if (clearedBelow == 0 || y in clearedRows) {
                emptyList()
            } else {
                row.mapIndexedNotNull { x, type ->
                    type?.let {
                        LineClearShiftBlock(
                            x = x,
                            sourceY = y,
                            targetY = y + clearedBelow,
                            type = it
                        )
                    }
                }
            }
        }
        val clearCount = clearedRows.size
        val dropScore = dropDistance

        if (clearCount == 0) {
            val spawned = spawnPiece(
                state.copy(
                    board = lockedBoard,
                    currentPiece = null,
                    score = state.score + dropScore,
                    comboCount = 0,
                    heatLevel = GameScoring.heatAfterNoClear(state.backToBackCount),
                    lastClearResult = null
                )
            )

            return spawned.copy(
                lastClearedRows = emptyList(),
                lastDropDistance = dropDistance
            )
        }

        val newLines = state.lines + clearCount
        val newLevel = newLines / 10 + 1
        val clearedBoard = clearRows(lockedBoard).board
        val clearResult = GameScoring.resolveClear(
            clearedLines = clearCount,
            level = newLevel,
            comboCount = state.comboCount,
            backToBackCount = state.backToBackCount,
            tSpinType = TSpinRules.classify(
                board = state.board,
                piece = current,
                lastActionWasRotation = state.lastActionWasRotation,
                kickIndex = state.lastRotationKickIndex
            ),
            isPerfectClear = clearedBoard.cells.all { row -> row.all { it == null } }
        )

        val spawned = spawnPiece(
            state.copy(
                board = clearedBoard,
                currentPiece = null,
                score = state.score + clearResult.scoreAwarded + dropScore,
                lines = newLines,
                level = newLevel,
                lockResetCount = 0,
                clearingBlocks = clearingBlocks,
                lineClearShiftBlocks = lineClearShiftBlocks,
                comboCount = clearResult.comboCount,
                backToBackCount = clearResult.backToBackCount,
                heatLevel = clearResult.heatLevel,
                lastClearResult = clearResult
            )
        )

        return spawned.copy(
            lastClearedRows = clearedRows,
            lastDropDistance = dropDistance,
            clearingBlocks = clearingBlocks,
            lineClearShiftBlocks = lineClearShiftBlocks,
            lastClearResult = clearResult
        )
    }

    fun finishLineClear(state: GameState): GameState {
        if (!state.isClearingLines) return state

        return state.copy(
            lastClearedRows = emptyList(),
            lastDropDistance = 0,
            clearingBlocks = emptyList(),
            lineClearShiftBlocks = emptyList(),
            lastClearResult = null
        )
    }

    fun startSurvivalAttack(state: GameState, kind: SurvivalAttackKind): GameState =
        survivalAttackEngine.start(state, kind)

    fun tickSurvivalAttacks(state: GameState, deltaMs: Long): GameState =
        survivalAttackEngine.tick(state, deltaMs)

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

    fun gravityIntervalMs(level: Int): Long {
        val minimumFallSpeed = 1_000.0 / GameConstants.INITIAL_GRAVITY_MS
        val maximumFallSpeed = 1_000.0 / GameConstants.MIN_GRAVITY_MS
        val levelProgress = ((level - 1).toDouble() / (GameConstants.MAX_SPEED_LEVEL - 1))
            .coerceIn(0.0, 1.0)
        val fallSpeed = minimumFallSpeed + (maximumFallSpeed - minimumFallSpeed) * levelProgress

        return (1_000.0 / fallSpeed)
            .roundToLong()
            .coerceAtLeast(GameConstants.MIN_GRAVITY_MS)
    }

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
        bagRandomizer.nextBag()

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
            clearingBlocks = state.clearingBlocks,
            lastActionWasRotation = false,
            lastRotationKickIndex = null
        )
    }

    private fun canAct(state: GameState): Boolean =
        state.currentPiece != null && !state.isGameOver && !state.isPaused && !state.isClearingLines

    private fun tryMove(state: GameState, dx: Int, dy: Int): Piece? {
        val current = state.currentPiece ?: return null
        val moved = current.copy(x = current.x + dx, y = current.y + dy)
        return if (collides(state.board, moved)) null else moved
    }

    private data class ClearedRowsResult(
        val board: Board,
        val rows: List<Int>
    )

    private fun findClearedRows(board: Board): List<Int> =
        board.cells.mapIndexedNotNull { y, row ->
            if (row.all { it != null }) y else null
        }

    private fun clearRows(board: Board): ClearedRowsResult {
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

        return ClearedRowsResult(
            board = board.copy(cells = emptyRows + keptRows),
            rows = clearedRows
        )
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
