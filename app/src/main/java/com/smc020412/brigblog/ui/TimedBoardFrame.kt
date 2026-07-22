package com.smc020412.brigblog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.smc020412.brigblog.game.ClearResult
import com.smc020412.brigblog.game.GameEngine
import com.smc020412.brigblog.game.GameState

enum class BoardTimerDirection {
    Countdown,
    Charge
}

data class BoardTimerState(
    val progress: Float,
    val text: String,
    val direction: BoardTimerDirection
)

@Composable
fun TimedBoardFrame(
    game: GameState,
    engine: GameEngine,
    timer: BoardTimerState,
    lineClearProgress: Float,
    clearFeedback: ClearResult?,
    clearFeedbackProgress: Float,
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
        BoardTimerGauge(timer, Modifier.width(scaledDp(8f)).fillMaxHeight())
        GameBoardCanvas(
            state = game,
            engine = engine,
            lineClearProgress = lineClearProgress,
            clearFeedback = clearFeedback,
            clearFeedbackProgress = clearFeedbackProgress,
            hardDropImpact = hardDropImpact,
            hardDropImpactProgress = hardDropImpactProgress,
            noiseTimeMs = noiseTimeMs,
            timerUnderlay = timer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        BoardTimerGauge(timer, Modifier.width(scaledDp(8f)).fillMaxHeight())
    }
}

@Composable
private fun BoardTimerGauge(
    timer: BoardTimerState,
    modifier: Modifier = Modifier
) {
    val ratio = timer.progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        drawRect(color = GameColors.Grid.copy(alpha = 0.72f), size = size)
        val fillHeight = size.height * ratio
        drawRect(
            color = timer.color(),
            topLeft = Offset(0f, size.height - fillHeight),
            size = Size(size.width, fillHeight)
        )
    }
}

fun BoardTimerState.color(): Color =
    when (direction) {
        BoardTimerDirection.Countdown -> when {
            progress > 0.75f -> Color(0xFF4DE17E)
            progress > 0.50f -> GameColors.Warning
            progress > 0.25f -> Color(0xFFFF9F1C)
            else -> GameColors.Danger
        }
        BoardTimerDirection.Charge -> when {
            progress < 0.25f -> Color(0xFF4DE17E)
            progress < 0.50f -> GameColors.Warning
            progress < 0.75f -> Color(0xFFFF9F1C)
            else -> GameColors.Danger
        }
    }
