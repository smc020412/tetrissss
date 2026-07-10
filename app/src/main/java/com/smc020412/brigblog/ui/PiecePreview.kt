package com.smc020412.brigblog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.smc020412.brigblog.game.Cell
import com.smc020412.brigblog.game.PieceShapes
import com.smc020412.brigblog.game.PieceType

@Composable
fun HoldPreview(
    pieceType: PieceType?,
    enabled: Boolean = true,
    miniHeightDp: Int = 42,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    PreviewPanel(title = "HOLD", compact = compact, modifier = modifier) {
        MiniPiece(pieceType, enabled = enabled, heightDp = miniHeightDp)
    }
}

@Composable
fun NextPreview(
    pieces: List<PieceType>,
    maxPieces: Int = 5,
    miniHeightDp: Int = 42,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    PreviewPanel(title = "NEXT", compact = compact, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) scaledDp(6f) else scaledDp(10f))) {
            pieces.take(maxPieces).forEach { MiniPiece(it, heightDp = miniHeightDp) }
        }
    }
}

@Composable
private fun PreviewPanel(
    title: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = GameColors.Panel,
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(if (compact) scaledDp(6f) else scaledDp(10f)),
            verticalArrangement = Arrangement.spacedBy(if (compact) scaledDp(4f) else scaledDp(8f))
        ) {
            Text(
                title,
                style = scaledTextStyle(if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium),
                color = GameColors.MutedText,
                maxLines = 1
            )
            content()
        }
    }
}

@Composable
private fun MiniPiece(
    type: PieceType?,
    enabled: Boolean = true,
    heightDp: Int = 42,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .aspectRatio(2f)
            .alpha(if (enabled) 1f else 0.35f)
    ) {
        if (type == null) return@Canvas

        val cells = PieceShapes.shapes.getValue(type)[0]
        val minX = cells.minOf(Cell::x)
        val maxX = cells.maxOf(Cell::x)
        val minY = cells.minOf(Cell::y)
        val maxY = cells.maxOf(Cell::y)
        val pieceWidth = maxX - minX + 1
        val pieceHeight = maxY - minY + 1
        val cell = minOf(size.width / 4.2f, size.height / 2.4f)
        val origin = Offset(
            (size.width - pieceWidth * cell) / 2f,
            (size.height - pieceHeight * cell) / 2f
        )

        cells.forEach { block ->
            val x = block.x - minX
            val y = block.y - minY
            val inset = maxOf(1f, cell * 0.1f)
            drawRect(
                color = GameColors.piece(type),
                topLeft = Offset(origin.x + x * cell + inset, origin.y + y * cell + inset),
                size = Size(cell - inset * 2f, cell - inset * 2f)
            )
        }
    }
}
