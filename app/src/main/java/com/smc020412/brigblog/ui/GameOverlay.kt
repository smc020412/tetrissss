package com.smc020412.brigblog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GameOverPanel(
    score: Int,
    rank: Int?,
    scores: List<Int>,
    title: String = "Game Over",
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    BoardPanel {
        Text(
            text = title.uppercase(),
            modifier = Modifier.fillMaxWidth(),
            style = scaledTextStyle(MaterialTheme.typography.headlineSmall),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Score $score",
            modifier = Modifier.fillMaxWidth(),
            style = scaledTextStyle(MaterialTheme.typography.titleMedium),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = rank?.let { "Rank $it" } ?: "Rank -",
            modifier = Modifier.fillMaxWidth(),
            style = scaledTextStyle(MaterialTheme.typography.bodyMedium),
            color = GameColors.Accent,
            textAlign = TextAlign.Center
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(scaledDp(2f)),
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
                        modifier = Modifier.padding(horizontal = scaledDp(8f), vertical = scaledDp(2f)),
                        horizontalArrangement = Arrangement.spacedBy(scaledDp(10f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            modifier = Modifier.width(scaledDp(34f)),
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
                            modifier = Modifier.width(scaledDp(84f)),
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
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text("Restart", style = scaledTextStyle(MaterialTheme.typography.labelMedium))
        }
        TextButton(onClick = onMainMenu, modifier = Modifier.fillMaxWidth()) {
            Text("Main Menu", style = scaledTextStyle(MaterialTheme.typography.labelMedium))
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
            highlighted = hapticEnabled
        )
        SettingsActionButton(
            label = "Edit Controls",
            onClick = onOpenControlsEditor
        )
        SettingsActionButton(
            label = "Main Menu",
            onClick = onMainMenu,
            danger = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(6f))) {
            SettingsActionButton(
                label = "Resume",
                onClick = onResume,
                highlighted = true,
                modifier = Modifier.weight(1.25f)
            )
            SettingsActionButton(
                label = "Restart",
                onClick = onRestart,
                modifier = Modifier.weight(1f)
            )
        }
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
            valueRange = 44f..86f
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
    highlighted: Boolean = false,
    danger: Boolean = false
) {
    val borderColor = when {
        danger -> GameColors.Danger
        highlighted -> GameColors.Accent
        else -> GameColors.Grid
    }
    val containerColor = when {
        danger -> GameColors.Danger.copy(alpha = 0.14f)
        highlighted -> GameColors.Accent.copy(alpha = 0.18f)
        else -> GameColors.Board.copy(alpha = 0.42f)
    }
    val contentColor = when {
        danger -> GameColors.Danger
        highlighted -> GameColors.Text
        else -> GameColors.Text
    }

    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(scaledDp(1f), borderColor.copy(alpha = if (highlighted || danger) 0.95f else 0.72f)),
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

@Composable
private fun BoardPanel(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = GameColors.Panel.copy(alpha = 0.94f),
        contentColor = GameColors.Text,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(scaledDp(12f))
    ) {
        Column(
            modifier = Modifier.padding(scaledDp(12f)),
            verticalArrangement = Arrangement.spacedBy(scaledDp(7f)),
            content = content
        )
    }
}
