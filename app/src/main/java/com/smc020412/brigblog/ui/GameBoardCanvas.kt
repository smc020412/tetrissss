package com.smc020412.brigblog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.smc020412.brigblog.game.GameConstants
import com.smc020412.brigblog.game.GameEngine
import com.smc020412.brigblog.game.GameState
import com.smc020412.brigblog.game.Piece
import com.smc020412.brigblog.game.PieceType
import com.smc020412.brigblog.game.SurvivalAttackKind

@Composable
fun GameBoardCanvas(
    state: GameState,
    engine: GameEngine,
    lineClearProgress: Float = 1f,
    hardDropImpact: HardDropImpact? = null,
    hardDropImpactProgress: Float = 1f,
    noiseTimeMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val cell = minOf(
            size.width / GameConstants.BOARD_WIDTH,
            size.height / GameConstants.BOARD_HEIGHT
        )
        val boardWidth = cell * GameConstants.BOARD_WIDTH
        val boardHeight = cell * GameConstants.BOARD_HEIGHT
        val origin = Offset((size.width - boardWidth) / 2f, (size.height - boardHeight) / 2f)

        drawRect(GameColors.Board, origin, Size(boardWidth, boardHeight))

        val shiftingTargets = state.lineClearShiftBlocks
            .associateBy { block -> block.x to block.targetY }

        state.board.cells.forEachIndexed { y, row ->
            row.forEachIndexed { x, type ->
                if (type != null) {
                    val shiftingBlock = shiftingTargets[x to y]
                    if (shiftingBlock?.type == type) {
                        return@forEachIndexed
                    } else if (type == PieceType.Garbage) {
                        drawGarbageBlock(origin, cell, x, y, noiseTimeMs)
                    } else {
                        drawBlock(origin, cell, x, y, GameColors.piece(type))
                    }
                }
            }
        }

        state.clearingBlocks.forEach { block ->
            drawClearingBlock(
                origin = origin,
                cell = cell,
                x = block.x,
                y = block.y,
                color = GameColors.piece(block.type),
                progress = lineClearProgress
            )
        }
        state.lineClearShiftBlocks.forEach { block ->
            drawLineClearShiftBlock(
                origin = origin,
                cell = cell,
                block = block,
                progress = lineClearProgress,
                noiseTimeMs = noiseTimeMs
            )
        }

        engine.ghostPiece(state)?.let { ghost ->
            drawPiece(engine, ghost, origin, cell, alpha = 0.22f)
        }

        state.currentPiece?.let { piece ->
            drawPiece(engine, piece, origin, cell, alpha = 1f)
        }

        state.attackObjects.forEach { attackObject ->
            when (attackObject.kind) {
                SurvivalAttackKind.FallingGarbage -> drawAnimatedGarbageBlock(
                    origin = origin,
                    cell = cell,
                    x = attackObject.x,
                    y = attackObject.y,
                    noiseTimeMs = noiseTimeMs
                )
                SurvivalAttackKind.RisingGarbage -> drawRisingAttackRow(
                    origin = origin,
                    cell = cell,
                    y = attackObject.y,
                    cells = attackObject.cells,
                    noiseTimeMs = noiseTimeMs
                )
            }
        }

        hardDropImpact?.let { impact ->
            drawHardDropImpact(
                origin = origin,
                cell = cell,
                impact = impact,
                progress = hardDropImpactProgress
            )
        }

        for (x in 0..GameConstants.BOARD_WIDTH) {
            val px = origin.x + x * cell
            drawLine(
                color = GameColors.Grid.copy(alpha = 0.45f),
                start = Offset(px, origin.y),
                end = Offset(px, origin.y + boardHeight),
                strokeWidth = 1f
            )
        }

        for (y in 0..GameConstants.BOARD_HEIGHT) {
            val py = origin.y + y * cell
            drawLine(
                color = GameColors.Grid.copy(alpha = 0.45f),
                start = Offset(origin.x, py),
                end = Offset(origin.x + boardWidth, py),
                strokeWidth = 1f
            )
        }

        drawRect(
            color = GameColors.Accent.copy(alpha = 0.86f),
            topLeft = origin,
            size = Size(boardWidth, boardHeight),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = maxOf(2f, cell * 0.08f))
        )

        if (engine.dangerLevel(state) > 0f) {
            drawRect(
                color = GameColors.Danger.copy(alpha = 0.08f + engine.dangerLevel(state) * 0.12f),
                topLeft = origin,
                size = Size(boardWidth, boardHeight)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPiece(
    engine: GameEngine,
    piece: Piece,
    origin: Offset,
    cell: Float,
    alpha: Float
) {
    engine.getCells(piece).forEach { block ->
        if (block.y >= 0) {
            drawBlock(origin, cell, block.x, block.y, GameColors.piece(piece.type), alpha)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlock(
    origin: Offset,
    cell: Float,
    x: Int,
    y: Int,
    color: Color,
    alpha: Float = 1f
) {
    val inset = maxOf(1f, cell * 0.08f)
    val topLeft = Offset(origin.x + x * cell + inset, origin.y + y * cell + inset)
    val size = Size(cell - inset * 2f, cell - inset * 2f)
    drawRect(color.copy(alpha = alpha), topLeft, size)
    drawRect(Color.White.copy(alpha = alpha * 0.16f), topLeft, Size(size.width, maxOf(1f, size.height * 0.12f)))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGarbageBlock(
    origin: Offset,
    cell: Float,
    x: Int,
    y: Int,
    noiseTimeMs: Long
) {
    drawGarbageNoise(origin, cell, x, y.toFloat(), noiseTimeMs)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnimatedGarbageBlock(
    origin: Offset,
    cell: Float,
    x: Int,
    y: Float,
    noiseTimeMs: Long
) {
    drawGarbageNoise(origin, cell, x, y, noiseTimeMs)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGarbageNoise(
    origin: Offset,
    cell: Float,
    x: Int,
    y: Float,
    noiseTimeMs: Long
) {
    val inset = maxOf(1f, cell * 0.08f)
    val topLeft = Offset(origin.x + x * cell + inset, origin.y + y * cell + inset)
    val size = Size(cell - inset * 2f, cell - inset * 2f)
    val frame = (noiseTimeMs / 56L).toInt()
    val grid = if (size.width >= 34f) 8 else 6
    val pixelW = size.width / grid
    val pixelH = size.height / grid

    drawRect(Color(0xFF12151A), topLeft, size)
    repeat(grid) { py ->
        repeat(grid) { px ->
            val value = garbageNoiseValue(px, py, x, y.toInt(), frame)
            drawRect(
                color = when {
                    value > 232 -> Color(0xFFFFFFFF)
                    value > 174 -> Color(0xFFC5CBD4)
                    value > 92 -> Color(0xFF747D89)
                    value > 32 -> Color(0xFF3B424C)
                    else -> Color(0xFF171B20)
                },
                topLeft = Offset(topLeft.x + px * pixelW, topLeft.y + py * pixelH),
                size = Size(pixelW + 0.35f, pixelH + 0.35f)
            )
        }
    }
    drawRect(Color.White.copy(alpha = 0.20f), topLeft, Size(size.width, maxOf(1f, size.height * 0.035f)))
}

private fun garbageNoiseValue(px: Int, py: Int, blockX: Int, blockY: Int, frame: Int): Int {
    var hash = px * 0x1F1F1F1F
    hash = hash xor (py * 0x45D9F3B)
    hash = hash xor (blockX * 0x27D4EB2D)
    hash = hash xor (blockY * 0x165667B1)
    hash = hash xor (frame * 0x9E3779B9.toInt())
    hash = hash xor (hash ushr 16)
    hash *= 0x7FEB352D
    hash = hash xor (hash ushr 15)
    return hash and 0xFF
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRisingAttackRow(
    origin: Offset,
    cell: Float,
    y: Float,
    cells: List<Boolean>,
    noiseTimeMs: Long
) {
    cells.take(GameConstants.BOARD_WIDTH).forEachIndexed { x, filled ->
        if (filled) {
            drawAnimatedGarbageBlock(
                origin = origin,
                cell = cell,
                x = x,
                y = y,
                noiseTimeMs = noiseTimeMs + x * 23L
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClearingBlock(
    origin: Offset,
    cell: Float,
    x: Int,
    y: Int,
    color: Color,
    progress: Float
) {
    val sweepDelay = x * 0.035f
    val localProgress = ((progress.coerceIn(0f, 1f) - sweepDelay) / 0.72f).coerceIn(0f, 1f)

    if (localProgress >= 1f) return

    val popScale = if (localProgress < 0.22f) {
        1f + 0.12f * (localProgress / 0.22f)
    } else {
        1.12f * (1f - ((localProgress - 0.22f) / 0.78f))
    }.coerceIn(0f, 1.12f)
    val alpha = (1f - localProgress).coerceIn(0f, 1f)
    val flash = if (localProgress < 0.18f) 0.28f * (1f - localProgress / 0.18f) else 0f
    val baseSize = cell * 0.84f
    val blockSize = baseSize * popScale
    val center = Offset(origin.x + x * cell + cell / 2f, origin.y + y * cell + cell / 2f)
    val topLeft = Offset(center.x - blockSize / 2f, center.y - blockSize / 2f)

    drawRect(
        color = color.copy(alpha = alpha),
        topLeft = topLeft,
        size = Size(blockSize, blockSize)
    )
    if (flash > 0f) {
        drawRect(
            color = Color.White.copy(alpha = flash),
            topLeft = topLeft,
            size = Size(blockSize, blockSize)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineClearShiftBlock(
    origin: Offset,
    cell: Float,
    block: com.smc020412.brigblog.game.LineClearShiftBlock,
    progress: Float,
    noiseTimeMs: Long
) {
    val descentProgress = ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f)
    val y = block.sourceY + (block.targetY - block.sourceY) * descentProgress
    if (block.type == PieceType.Garbage) {
        drawGarbageNoise(origin, cell, block.x, y, noiseTimeMs)
        return
    }

    val inset = maxOf(1f, cell * 0.08f)
    val topLeft = Offset(origin.x + block.x * cell + inset, origin.y + y * cell + inset)
    val size = Size(cell - inset * 2f, cell - inset * 2f)
    val color = GameColors.piece(block.type)

    drawRect(color, topLeft, size)
    drawRect(Color.White.copy(alpha = 0.16f), topLeft, Size(size.width, maxOf(1f, size.height * 0.12f)))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHardDropImpact(
    origin: Offset,
    cell: Float,
    impact: HardDropImpact,
    progress: Float
) {
    if (impact.blocks.isEmpty()) return

    val t = progress.coerceIn(0f, 1f)
    val alpha = (1f - t).coerceIn(0f, 1f)
    val minX = impact.blocks.minOf { it.x }
    val maxX = impact.blocks.maxOf { it.x }
    val landingY = impact.blocks.maxOf { it.y }
    val impactCenterX = origin.x + (minX + maxX + 1) * cell / 2f
    val scanProgress = (t / 0.62f).coerceIn(0f, 1f)
    val scanWidth = (maxX - minX + 1) * cell * (0.56f + scanProgress * 0.98f)
    val scanY = origin.y + (landingY + 0.92f) * cell

    drawRect(
        color = Color.White.copy(alpha = alpha * (0.90f - scanProgress * 0.30f)),
        topLeft = Offset(impactCenterX - scanWidth / 2f, scanY),
        size = Size(scanWidth, maxOf(2.5f, cell * 0.15f))
    )
    drawRect(
        color = GameColors.Accent.copy(alpha = alpha * 0.48f),
        topLeft = Offset(impactCenterX - scanWidth * 0.42f, scanY + cell * 0.17f),
        size = Size(scanWidth * 0.84f, maxOf(1.5f, cell * 0.065f))
    )

    impact.blocks.forEachIndexed { blockIndex, block ->
        val blockTopLeft = Offset(origin.x + block.x * cell, origin.y + block.y * cell)
        val landingFlash = ((0.28f - t) / 0.28f).coerceIn(0f, 1f)
        drawRect(
            color = Color.White.copy(alpha = landingFlash * 0.46f),
            topLeft = blockTopLeft,
            size = Size(cell, cell)
        )

        repeat(5) { particleIndex ->
            val spread = particleIndex - 2
            val travel = (t / 0.92f).coerceIn(0f, 1f)
            val startX = origin.x + (block.x + 0.5f) * cell
            val startY = origin.y + (block.y + 0.88f) * cell
            val direction = spread * 0.23f + if (blockIndex % 2 == 0) -0.06f else 0.06f
            val x = startX + direction * cell * (0.55f + travel * 2.45f)
            val y = startY - cell * (0.08f + travel * (0.60f + particleIndex * 0.095f)) + travel * travel * cell * 0.32f
            val sparkle = if ((blockIndex + particleIndex) % 2 == 0) Color.White else GameColors.piece(block.type)
            val particleSize = cell * (0.26f + (4 - particleIndex) * 0.045f) * (1f - travel * 0.28f)

            drawRect(
                color = sparkle.copy(alpha = alpha * (1f - particleIndex * 0.075f)),
                topLeft = Offset(x - particleSize / 2f, y - particleSize / 2f),
                size = Size(particleSize, particleSize)
            )
        }
    }
}
