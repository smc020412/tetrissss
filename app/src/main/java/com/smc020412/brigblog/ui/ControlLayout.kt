package com.smc020412.brigblog.ui

enum class ControlAction {
    MoveLeft,
    MoveRight,
    SoftDrop,
    HardDrop,
    RotateLeft,
    RotateRight,
    Hold
}

data class ControlPlacement(
    val action: ControlAction,
    val xRatio: Float,
    val yRatio: Float,
    val sizeDp: Float
)

object DefaultControlLayout {
    val placements: List<ControlPlacement> = listOf(
        ControlPlacement(ControlAction.MoveLeft, xRatio = 0.12f, yRatio = 0.45f, sizeDp = 62f),
        ControlPlacement(ControlAction.MoveRight, xRatio = 0.34f, yRatio = 0.45f, sizeDp = 62f),
        ControlPlacement(ControlAction.HardDrop, xRatio = 0.57f, yRatio = 0.11f, sizeDp = 62f),
        ControlPlacement(ControlAction.SoftDrop, xRatio = 0.57f, yRatio = 0.45f, sizeDp = 62f),
        ControlPlacement(ControlAction.Hold, xRatio = 0.82f, yRatio = 0.11f, sizeDp = 62f),
        ControlPlacement(ControlAction.RotateRight, xRatio = 0.82f, yRatio = 0.45f, sizeDp = 62f),
        ControlPlacement(ControlAction.RotateLeft, xRatio = 0.70f, yRatio = 0.79f, sizeDp = 62f)
    )

    fun normalized(placements: List<ControlPlacement>): List<ControlPlacement> {
        val byAction = placements.associateBy { it.action }
        return this.placements.map { default ->
            byAction[default.action] ?: default
        }
    }
}
