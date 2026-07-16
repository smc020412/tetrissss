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
    val sizeDp: Float,
    val widthScale: Float = 1f,
    val heightScale: Float = 1f
)

object DefaultControlLayout {
    private const val DefaultButtonSizeDp = 75f

    val placements: List<ControlPlacement> = listOf(
        ControlPlacement(ControlAction.MoveLeft, xRatio = 0.16f, yRatio = 0.45f, sizeDp = DefaultButtonSizeDp, heightScale = 1.5f),
        ControlPlacement(ControlAction.MoveRight, xRatio = 0.38f, yRatio = 0.45f, sizeDp = DefaultButtonSizeDp, heightScale = 1.5f),
        ControlPlacement(ControlAction.HardDrop, xRatio = 0.61f, yRatio = 0.18f, sizeDp = DefaultButtonSizeDp),
        ControlPlacement(ControlAction.SoftDrop, xRatio = 0.61f, yRatio = 0.45f, sizeDp = DefaultButtonSizeDp),
        ControlPlacement(ControlAction.Hold, xRatio = 0.86f, yRatio = 0.18f, sizeDp = DefaultButtonSizeDp),
        ControlPlacement(ControlAction.RotateRight, xRatio = 0.86f, yRatio = 0.45f, sizeDp = DefaultButtonSizeDp),
        ControlPlacement(ControlAction.RotateLeft, xRatio = 0.74f, yRatio = 0.72f, sizeDp = DefaultButtonSizeDp)
    )

    fun normalized(placements: List<ControlPlacement>): List<ControlPlacement> {
        val byAction = placements.associateBy { it.action }
        return this.placements.map { default ->
            byAction[default.action]?.let { saved ->
                default.copy(
                    xRatio = saved.xRatio,
                    yRatio = saved.yRatio,
                    sizeDp = saved.sizeDp
                )
            } ?: default
        }
    }
}
