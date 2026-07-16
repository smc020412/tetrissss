package com.smc020412.brigblog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
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
import kotlinx.coroutines.withTimeoutOrNull

private const val MoveHorizontalRepeatDelayMs = 110L
private const val SoftDropRepeatDelayMs = 110L
private const val ReferenceControlsWidthDp = 411f
private const val ReferenceControlsHeightDp = 245f
private const val PillWidthScale = 1.16f

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
            ControlAction.MoveLeft to ControlBinding("◀", onMoveLeft, repeatable = true, repeatDelayMs = MoveHorizontalRepeatDelayMs, shape = PadButtonShape.Pill),
            ControlAction.MoveRight to ControlBinding("▶", onMoveRight, repeatable = true, repeatDelayMs = MoveHorizontalRepeatDelayMs, shape = PadButtonShape.Pill),
            ControlAction.HardDrop to ControlBinding("⇩", onHardDrop, repeatable = false, shape = PadButtonShape.Round),
            ControlAction.SoftDrop to ControlBinding("↓", onSoftDrop, repeatable = true, repeatDelayMs = SoftDropRepeatDelayMs, shape = PadButtonShape.Round),
            ControlAction.Hold to ControlBinding("⇄", onHold, repeatable = false, shape = PadButtonShape.Round),
            ControlAction.RotateRight to ControlBinding("↻", onRotateRight, repeatable = false, shape = PadButtonShape.Round),
            ControlAction.RotateLeft to ControlBinding("↺", onRotateLeft, repeatable = false, shape = PadButtonShape.Round)
        )
    }
    val normalized = DefaultControlLayout.normalized(placements)
    var externallyPressedAction by remember { mutableStateOf<ControlAction?>(null) }
    val currentActions by rememberUpdatedState(actions)

    Surface(
        color = Color(0xFF121824),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, GameColors.Grid.copy(alpha = 0.92f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val controlScale = minOf(
                maxWidth.value / ReferenceControlsWidthDp,
                maxHeight.value / ReferenceControlsHeightDp
            ).coerceIn(0.62f, 1.12f)
            val buttonFrames = normalized.map { placement ->
            val binding = actions.getValue(placement.action)
            val renderedSizeDp = placement.sizeDp * controlScale
            val buttonWidthDp = renderedSizeDp * binding.widthScale * placement.widthScale
            val buttonHeightDp = renderedSizeDp * binding.heightScale * placement.heightScale
            val buttonWidthPx = with(density) { buttonWidthDp.dp.toPx() }
            val buttonHeightPx = with(density) { buttonHeightDp.dp.toPx() }
            val x = placement.xRatio * widthPx - buttonWidthPx / 2f
            val y = placement.yRatio * heightPx - buttonHeightPx / 2f

            ControlButtonFrame(
                placement = placement,
                binding = binding,
                widthDp = buttonWidthDp,
                heightDp = buttonHeightDp,
                leftPx = x,
                topPx = y,
                rightPx = x + buttonWidthPx,
                bottomPx = y + buttonHeightPx
            )
        }
            val currentButtonFrames by rememberUpdatedState(buttonFrames)
            fun controlActionAt(x: Float, y: Float): ControlAction? =
            currentButtonFrames.firstOrNull { frame ->
                x >= frame.leftPx &&
                    x <= frame.rightPx &&
                    y >= frame.topPx &&
                    y <= frame.bottomPx
            }?.placement?.action
            fun pressControl(action: ControlAction) {
            currentActions.getValue(action).onPress()
        }
            fun latestControlAction(
            pointerActions: Map<PointerId, ControlPointerState>
        ): Pair<PointerId, ControlAction>? =
            pointerActions.maxByOrNull { it.value.updatedAtMillis }?.let { entry ->
                entry.key to entry.value.action
            }
            fun repeatDelayFor(action: ControlAction): Long =
            currentActions.getValue(action).repeatDelayMs
            fun isRepeatable(action: ControlAction): Boolean =
            currentActions.getValue(action).repeatable

            Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, editMode) {
                    if (!enabled || editMode) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var activeAction = controlActionAt(down.position.x, down.position.y) ?: return@awaitEachGesture
                        var activePointerId = down.id
                        var pointerTime = down.uptimeMillis
                        var nextRepeatAt = down.uptimeMillis + repeatDelayFor(activeAction)
                        val pointerActions = mutableMapOf(
                            down.id to ControlPointerState(
                                action = activeAction,
                                updatedAtMillis = down.uptimeMillis
                            )
                        )

                        down.consume()
                        externallyPressedAction = activeAction
                        pressControl(activeAction)

                        while (true) {
                            val waitMs = (nextRepeatAt - pointerTime).coerceAtLeast(1L)
                            val event = withTimeoutOrNull(waitMs) {
                                awaitPointerEvent(pass = PointerEventPass.Initial)
                            }

                            if (event == null) {
                                if (isRepeatable(activeAction)) {
                                    pressControl(activeAction)
                                }
                                pointerTime = nextRepeatAt
                                nextRepeatAt += GameConstants.MOVE_REPEAT_RATE_MS
                                continue
                            }

                            var activeChanged = false
                            var shouldPressActiveChange = false
                            event.changes.forEach { change ->
                                pointerTime = maxOf(pointerTime, change.uptimeMillis)
                                if (!change.pressed) {
                                    val removed = pointerActions.remove(change.id)
                                    if (removed != null) {
                                        change.consume()
                                        if (change.id == activePointerId) {
                                            val latest = latestControlAction(pointerActions)
                                            if (latest != null) {
                                                activePointerId = latest.first
                                                activeAction = latest.second
                                                activeChanged = true
                                                shouldPressActiveChange = isRepeatable(activeAction)
                                            }
                                        }
                                    }
                                    return@forEach
                                }

                                val hoveredAction = controlActionAt(change.position.x, change.position.y)
                                if (hoveredAction != null) {
                                    val previous = pointerActions[change.id]
                                    if (previous?.action != hoveredAction) {
                                        pointerActions[change.id] = ControlPointerState(
                                            action = hoveredAction,
                                            updatedAtMillis = change.uptimeMillis
                                        )
                                        activePointerId = change.id
                                        activeAction = hoveredAction
                                        activeChanged = true
                                        shouldPressActiveChange = true
                                    } else {
                                        pointerActions[change.id] = previous.copy(updatedAtMillis = change.uptimeMillis)
                                    }
                                    change.consume()
                                }
                            }

                            if (pointerActions.isEmpty()) break

                            if (activeChanged) {
                                externallyPressedAction = activeAction
                                if (shouldPressActiveChange) {
                                    pressControl(activeAction)
                                }
                                nextRepeatAt = pointerTime + repeatDelayFor(activeAction)
                            } else if (pointerTime >= nextRepeatAt && isRepeatable(activeAction)) {
                                pressControl(activeAction)
                                nextRepeatAt = pointerTime + GameConstants.MOVE_REPEAT_RATE_MS
                            }
                        }

                        externallyPressedAction = null
                    }
                }
            ) {
                buttonFrames.forEach { frame ->
                val placement = frame.placement
                val binding = frame.binding
                val x = frame.leftPx.roundToInt()
                val y = frame.topPx.roundToInt()

                    PadButton(
                    action = placement.action,
                    symbol = binding.symbol,
                    onPress = binding.onPress,
                    repeatable = binding.repeatable,
                    repeatDelayMs = binding.repeatDelayMs,
                    pressHandledExternally = true,
                    enabled = enabled,
                    editMode = editMode,
                    selected = editMode && selectedAction == placement.action,
                    forcePressed = externallyPressedAction == placement.action,
                    shape = binding.shape,
                    widthDp = frame.widthDp,
                    heightDp = frame.heightDp,
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
}

@Composable
private fun PadButton(
    action: ControlAction,
    symbol: String,
    onPress: () -> Unit,
    repeatable: Boolean,
    repeatDelayMs: Long,
    pressHandledExternally: Boolean,
    enabled: Boolean,
    editMode: Boolean,
    selected: Boolean,
    forcePressed: Boolean,
    shape: PadButtonShape,
    widthDp: Float,
    heightDp: Float,
    onSelect: () -> Unit,
    onDragBy: (Float, Float) -> Unit,
    ratioStep: (Float, Float) -> Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentOnPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val visuallyPressed = pressed || forcePressed
    val containerShape = when (shape) {
        PadButtonShape.Round -> CircleShape
        PadButtonShape.Pill -> RoundedCornerShape(18.dp)
    }
    val buttonModifier = modifier.width(widthDp.dp).height(heightDp.dp)

    Surface(
        color = when {
            selected -> GameColors.Accent.copy(alpha = 0.34f)
            editMode -> GameColors.Warning.copy(alpha = 0.22f)
            visuallyPressed -> GameColors.Grid
            else -> GameColors.Panel
        },
        contentColor = GameColors.Text,
        shape = containerShape,
        border = if (selected) {
            BorderStroke(2.dp, GameColors.Accent)
        } else {
            BorderStroke(1.dp, GameColors.Accent.copy(alpha = if (visuallyPressed) 0.58f else 0.28f))
        },
        tonalElevation = if (pressed) 1.dp else 5.dp,
        shadowElevation = if (visuallyPressed) 1.dp else 6.dp,
        modifier = buttonModifier
            .scale(if (visuallyPressed) 0.96f else 1f)
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
                    if (!enabled || pressHandledExternally) return@pointerInput
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
                .background(if (visuallyPressed) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.08f))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            val symbolColor = if (selected) GameColors.Text else if (editMode) GameColors.Warning else GameColors.Accent
            when (action) {
                ControlAction.SoftDrop -> DropChevronIcon(
                    doubleChevron = false,
                    color = symbolColor
                )
                ControlAction.HardDrop -> DropChevronIcon(
                    doubleChevron = true,
                    color = symbolColor
                )
                else -> Text(
                    text = symbol,
                    style = if (symbol.length > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = symbolColor,
                    maxLines = 1,
                    modifier = Modifier.scale(
                        when (action) {
                            ControlAction.Hold,
                            ControlAction.RotateLeft,
                            ControlAction.RotateRight -> 1.20f
                            else -> 1f
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun DropChevronIcon(
    doubleChevron: Boolean,
    color: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(13.dp)
    ) {
        val stroke = minOf(size.width, size.height) * 0.105f
        val chevronWidth = size.width * 0.58f
        val chevronHeight = if (doubleChevron) size.height * 0.24f else size.height * 0.31f
        val topOffsets = if (doubleChevron) {
            listOf(size.height * 0.18f, size.height * 0.56f)
        } else {
            listOf(size.height * 0.32f)
        }

        topOffsets.forEach { top ->
            val left = Offset((size.width - chevronWidth) / 2f, top)
            val point = Offset(size.width / 2f, top + chevronHeight)
            val right = Offset((size.width + chevronWidth) / 2f, top)
            drawLine(color, left, point, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, point, right, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

private data class ControlBinding(
    val symbol: String,
    val onPress: () -> Unit,
    val repeatable: Boolean,
    val repeatDelayMs: Long = GameConstants.MOVE_REPEAT_DELAY_MS,
    val shape: PadButtonShape,
    val widthScale: Float = if (shape == PadButtonShape.Pill) PillWidthScale else 1f,
    val heightScale: Float = 1f
)

private data class ControlButtonFrame(
    val placement: ControlPlacement,
    val binding: ControlBinding,
    val widthDp: Float,
    val heightDp: Float,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float
)

private data class ControlPointerState(
    val action: ControlAction,
    val updatedAtMillis: Long
)

private enum class PadButtonShape {
    Round,
    Pill
}
