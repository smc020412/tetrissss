package com.smc020412.brigblog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val GameOverReferenceHeightDp = 560f

@Composable
fun GameOverPanel(
    score: Int,
    rank: Int?,
    scores: List<Int>,
    title: String = "Game Over",
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val availableScale = (maxHeight.value / GameOverReferenceHeightDp).coerceIn(0.50f, 1f)
        val gameOverScale = minOf(LocalGameUiScale.current, availableScale)

        CompositionLocalProvider(LocalGameUiScale provides gameOverScale) {
            BoardPanel(
                outerPadding = scaledDp(6f),
                contentPadding = scaledDp(7f),
                itemSpacing = scaledDp(3f)
            ) {
                Text(
                    text = title.uppercase(),
                    modifier = Modifier.fillMaxWidth(),
                    style = scaledTextStyle(MaterialTheme.typography.headlineSmall),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = "Score $score",
                    modifier = Modifier.fillMaxWidth(),
                    style = scaledTextStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = rank?.let { "Rank $it" } ?: "Rank -",
                    modifier = Modifier.fillMaxWidth(),
                    style = scaledTextStyle(MaterialTheme.typography.bodyMedium),
                    color = GameColors.Accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(scaledDp(0f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(10) { index ->
                        val value = scores.getOrNull(index)
                        val isCurrentScore = value == score && rank == index + 1
                        Surface(
                            color = if (isCurrentScore) GameColors.Accent.copy(alpha = 0.18f) else GameColors.Panel.copy(alpha = 0f),
                            contentColor = GameColors.Text,
                            shape = MaterialTheme.shapes.small,
                            border = if (isCurrentScore) BorderStroke(scaledDp(1f), GameColors.Accent.copy(alpha = 0.92f)) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = scaledDp(6f), vertical = scaledDp(0f)),
                                horizontalArrangement = Arrangement.spacedBy(scaledDp(8f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    modifier = Modifier.width(scaledDp(30f)),
                                    style = scaledTextStyle(MaterialTheme.typography.bodyMedium),
                                    fontWeight = if (isCurrentScore) FontWeight.Black else FontWeight.Normal,
                                    color = when {
                                        isCurrentScore -> GameColors.Accent
                                        value == null -> GameColors.MutedText
                                        else -> GameColors.Text
                                    },
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = value?.toString().orEmpty(),
                                    modifier = Modifier.width(scaledDp(76f)),
                                    style = scaledTextStyle(MaterialTheme.typography.bodyMedium),
                                    fontWeight = if (isCurrentScore) FontWeight.Black else FontWeight.Normal,
                                    color = when {
                                        isCurrentScore -> GameColors.Accent
                                        value == null -> GameColors.MutedText
                                        else -> GameColors.Text
                                    },
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = onRestart,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaledDp(34f))
                ) {
                    Text("Restart", style = scaledTextStyle(MaterialTheme.typography.labelMedium), maxLines = 1)
                }
                Spacer(Modifier.height(scaledDp(4f)))
                TextButton(
                    onClick = onMainMenu,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaledDp(30f))
                ) {
                    Text("Main Menu", style = scaledTextStyle(MaterialTheme.typography.labelMedium), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun RestartConfirmPanel(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BoardPanel {
        Text("Restart?", style = scaledTextStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
        Text("Current score will not be saved.", style = scaledTextStyle(MaterialTheme.typography.bodySmall), color = GameColors.MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(6f))) {
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("Yes", style = scaledTextStyle(MaterialTheme.typography.labelSmall), maxLines = 1)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("No", style = scaledTextStyle(MaterialTheme.typography.labelSmall), maxLines = 1)
            }
        }
    }
}

@Composable
fun MainMenuConfirmPanel(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BoardPanel {
        Text("Main Menu?", style = scaledTextStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
        Text("Current game progress will be lost.", style = scaledTextStyle(MaterialTheme.typography.bodySmall), color = GameColors.MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(6f))) {
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("Yes", style = scaledTextStyle(MaterialTheme.typography.labelSmall), maxLines = 1)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("No", style = scaledTextStyle(MaterialTheme.typography.labelSmall), maxLines = 1)
            }
        }
    }
}

@Composable
fun SettingsPanel(
    volume: Float,
    hapticEnabled: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onHapticToggle: () -> Unit,
    onOpenControlsEditor: () -> Unit,
    onMainMenu: () -> Unit
) {
    BoardPanel {
        Text("Menu", style = scaledTextStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
        Text("Sound volume", style = scaledTextStyle(MaterialTheme.typography.bodySmall), color = GameColors.MutedText)
        Slider(value = volume, onValueChange = onVolumeChange)
        SettingsActionButton(
            label = if (hapticEnabled) "Haptic On" else "Haptic Off",
            onClick = onHapticToggle,
            tone = if (hapticEnabled) SettingsActionTone.Accent else SettingsActionTone.Neutral
        )
        SettingsActionButton(
            label = "Edit Controls",
            onClick = onOpenControlsEditor,
            tone = SettingsActionTone.AccentSubtle
        )
        SettingsActionButton(
            label = "Main Menu",
            onClick = onMainMenu,
            tone = SettingsActionTone.Danger
        )
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(6f))) {
            SettingsActionButton(
                label = "Resume",
                onClick = onResume,
                tone = SettingsActionTone.Accent,
                modifier = Modifier.weight(1.25f)
            )
            SettingsActionButton(
                label = "Restart",
                onClick = onRestart,
                tone = SettingsActionTone.Warning,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PrivacyPolicyLink(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = scaledDp(1f))
    ) {
        Text(
            text = "Privacy Policy",
            style = scaledTextStyle(MaterialTheme.typography.labelSmall),
            color = GameColors.MutedText
        )
    }
}

@Composable
fun ControlsEditorPanel(
    placements: List<ControlPlacement>,
    selectedAction: ControlAction,
    onButtonSizeChange: (ControlAction, Float) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    BoardPanel {
        val normalizedPlacements = DefaultControlLayout.normalized(placements)
        val selectedPlacement = normalizedPlacements.firstOrNull { it.action == selectedAction } ?: normalizedPlacements.first()

        Text("Controls", style = scaledTextStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
        Text("Tap a button below, then adjust its size.", style = scaledTextStyle(MaterialTheme.typography.bodySmall), color = GameColors.MutedText)
        Text(
            text = "Selected ${selectedPlacement.action.controlLabel()}",
            style = scaledTextStyle(MaterialTheme.typography.labelSmall),
            fontWeight = FontWeight.Bold
        )
        Text(
            "Size ${selectedPlacement.sizeDp.toInt()}",
            style = scaledTextStyle(MaterialTheme.typography.bodySmall),
            color = GameColors.MutedText
        )
        Slider(
            value = selectedPlacement.sizeDp,
            onValueChange = { size -> onButtonSizeChange(selectedPlacement.action, size) },
            valueRange = 48f..96f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(6f))) {
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset", style = scaledTextStyle(MaterialTheme.typography.labelSmall), maxLines = 1)
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("Done", style = scaledTextStyle(MaterialTheme.typography.labelSmall), maxLines = 1)
            }
        }
    }
}

private fun ControlAction.controlLabel(): String =
    when (this) {
        ControlAction.MoveLeft -> "Left"
        ControlAction.MoveRight -> "Right"
        ControlAction.SoftDrop -> "Down"
        ControlAction.HardDrop -> "Hard Drop"
        ControlAction.RotateLeft -> "Rotate -"
        ControlAction.RotateRight -> "Rotate +"
        ControlAction.Hold -> "Hold"
    }

@Composable
private fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: SettingsActionTone = SettingsActionTone.Neutral
) {
    val borderColor = when (tone) {
        SettingsActionTone.Accent,
        SettingsActionTone.AccentSubtle -> GameColors.Accent
        SettingsActionTone.Warning -> GameColors.Warning
        SettingsActionTone.Danger -> GameColors.Danger
        SettingsActionTone.Neutral -> GameColors.Grid
    }
    val containerColor = when (tone) {
        SettingsActionTone.Accent -> GameColors.Accent.copy(alpha = 0.18f)
        SettingsActionTone.AccentSubtle -> GameColors.Accent.copy(alpha = 0.10f)
        SettingsActionTone.Warning -> GameColors.Warning.copy(alpha = 0.14f)
        SettingsActionTone.Danger -> GameColors.Danger.copy(alpha = 0.14f)
        SettingsActionTone.Neutral -> GameColors.Board.copy(alpha = 0.42f)
    }
    val contentColor = when (tone) {
        SettingsActionTone.Warning -> GameColors.Warning
        SettingsActionTone.Danger -> GameColors.Danger
        else -> GameColors.Text
    }
    val borderAlpha = if (tone == SettingsActionTone.Neutral) 0.72f else 0.95f

    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(scaledDp(1f), borderColor.copy(alpha = borderAlpha)),
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = label,
            style = scaledTextStyle(MaterialTheme.typography.labelSmall),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private enum class SettingsActionTone {
    Accent,
    AccentSubtle,
    Warning,
    Danger,
    Neutral
}

@Composable
private fun BoardPanel(
    outerPadding: androidx.compose.ui.unit.Dp = scaledDp(12f),
    contentPadding: androidx.compose.ui.unit.Dp = scaledDp(12f),
    itemSpacing: androidx.compose.ui.unit.Dp = scaledDp(7f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = GameColors.Panel.copy(alpha = 0.94f),
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(outerPadding)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            content = content
        )
    }
}
