package com.smc020412.brigblog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smc020412.brigblog.game.GameConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MoveHorizontalRepeatDelayMs = 180L
private const val SoftDropRepeatDelayMs = 110L

@Composable
fun GameControls(
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onHold: () -> Unit,
    placements: List<ControlPlacement>,
    editMode: Boolean,
    selectedAction: ControlAction?,
    onControlSelected: (ControlAction) -> Unit,
    onPlacementDelta: (ControlAction, Float, Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val actions = remember(onMoveLeft, onMoveRight, onSoftDrop, onHardDrop, onRotateLeft, onRotateRight, onHold) {
        mapOf(
            ControlAction.MoveLeft to ControlBinding("<", onMoveLeft, repeatable = true, repeatDelayMs = MoveHorizontalRepeatDelayMs, shape = PadButtonShape.Pill),
            ControlAction.MoveRight to ControlBinding(">", onMoveRight, repeatable = true, repeatDelayMs = MoveHorizontalRepeatDelayMs, shape = PadButtonShape.Pill),
            ControlAction.HardDrop to ControlBinding("HD", onHardDrop, repeatable = false, shape = PadButtonShape.Round),
            ControlAction.SoftDrop to ControlBinding("D", onSoftDrop, repeatable = true, repeatDelayMs = SoftDropRepeatDelayMs, shape = PadButtonShape.Round),
            ControlAction.Hold to ControlBinding("H", onHold, repeatable = false, shape = PadButtonShape.Round),
            ControlAction.RotateRight to ControlBinding("R+", onRotateRight, repeatable = false, shape = PadButtonShape.Round),
            ControlAction.RotateLeft to ControlBinding("R-", onRotateLeft, repeatable = false, shape = PadButtonShape.Round)
        )
    }
    val normalized = DefaultControlLayout.normalized(placements)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        Box(modifier = Modifier.fillMaxSize()) {
            normalized.forEach { placement ->
                val binding = actions.getValue(placement.action)
                val renderedSizeDp = placement.sizeDp * LocalGameUiScale.current
                val buttonSizePx = with(density) { renderedSizeDp.dp.toPx() }
                val x = (placement.xRatio * widthPx - buttonSizePx / 2f).roundToInt()
                val y = (placement.yRatio * heightPx - buttonSizePx / 2f).roundToInt()

                PadButton(
                    symbol = binding.symbol,
                    onPress = binding.onPress,
                    repeatable = binding.repeatable,
                    repeatDelayMs = binding.repeatDelayMs,
                    enabled = enabled,
                    editMode = editMode,
                    selected = editMode && selectedAction == placement.action,
                    shape = binding.shape,
                    sizeDp = renderedSizeDp,
                    onSelect = { onControlSelected(placement.action) },
                    onDragBy = { dxRatio, dyRatio ->
                        onPlacementDelta(placement.action, dxRatio, dyRatio)
                    },
                    ratioStep = { dx, dy -> dx / widthPx to dy / heightPx },
                    modifier = Modifier.offset { IntOffset(x, y) }
                )
            }
        }
    }
}

@Composable
private fun PadButton(
    symbol: String,
    onPress: () -> Unit,
    repeatable: Boolean,
    repeatDelayMs: Long,
    enabled: Boolean,
    editMode: Boolean,
    selected: Boolean,
    shape: PadButtonShape,
    sizeDp: Float,
    onSelect: () -> Unit,
    onDragBy: (Float, Float) -> Unit,
    ratioStep: (Float, Float) -> Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentOnPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val containerShape = when (shape) {
        PadButtonShape.Round -> CircleShape
        PadButtonShape.Pill -> RoundedCornerShape(18.dp)
    }
    val buttonModifier = when (shape) {
        PadButtonShape.Round -> modifier.width(sizeDp.dp).aspectRatio(1f)
        PadButtonShape.Pill -> modifier.width((sizeDp * 1.16f).dp).height(sizeDp.dp)
    }

    Surface(
        color = when {
            selected -> GameColors.Accent.copy(alpha = 0.34f)
            editMode -> GameColors.Warning.copy(alpha = 0.22f)
            pressed -> GameColors.Grid
            else -> GameColors.Panel
        },
        contentColor = GameColors.Text,
        shape = containerShape,
        border = if (selected) BorderStroke(2.dp, GameColors.Accent) else null,
        tonalElevation = if (pressed) 1.dp else 5.dp,
        shadowElevation = if (pressed) 1.dp else 6.dp,
        modifier = buttonModifier
            .scale(if (pressed) 0.96f else 1f)
            .alpha(if (enabled || editMode) 1f else 0.42f)
            .pointerInput(editMode) {
                if (editMode) {
                    detectTapGestures(onTap = { onSelect() })
                }
            }
            .pointerInput(repeatable, enabled, editMode) {
                if (editMode) {
                    detectDragGestures(
                        onDragStart = {
                            onSelect()
                            pressed = true
                        },
                        onDragEnd = { pressed = false },
                        onDragCancel = { pressed = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val (dxRatio, dyRatio) = ratioStep(dragAmount.x, dragAmount.y)
                            onDragBy(dxRatio, dyRatio)
                        }
                    )
                } else {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            currentOnPress()
                            var repeatJob: Job? = null
                            if (repeatable) {
                                repeatJob = scope.launch {
                                    delay(repeatDelayMs)
                                    while (isActive) {
                                        currentOnPress()
                                        delay(GameConstants.MOVE_REPEAT_RATE_MS)
                                    }
                                }
                            }
                            tryAwaitRelease()
                            repeatJob?.cancel()
                            pressed = false
                        }
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (pressed) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.08f))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = if (symbol.length > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) GameColors.Text else if (editMode) GameColors.Warning else GameColors.Accent,
                maxLines = 1
            )
        }
    }
}

private data class ControlBinding(
    val symbol: String,
    val onPress: () -> Unit,
    val repeatable: Boolean,
    val repeatDelayMs: Long = GameConstants.MOVE_REPEAT_DELAY_MS,
    val shape: PadButtonShape
)

private enum class PadButtonShape {
    Round,
    Pill
}
