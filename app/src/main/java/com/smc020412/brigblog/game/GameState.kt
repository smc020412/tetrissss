package com.smc020412.brigblog.game

data class ClearingBlock(
    val x: Int,
    val y: Int,
    val type: PieceType
)

data class LineClearShiftBlock(
    val x: Int,
    val sourceY: Int,
    val targetY: Int,
    val type: PieceType
)

data class SurvivalAttackObject(
    val kind: SurvivalAttackKind,
    val x: Int,
    val y: Float,
    val cells: List<Boolean> = emptyList()
)

enum class TSpinType {
    None,
    Mini,
    Full
}

data class GameState(
    val board: Board,
    val queue: List<PieceType>,
    val currentPiece: Piece?,
    val heldPiece: PieceType?,
    val canHold: Boolean,
    val score: Int,
    val lines: Int,
    val level: Int,
    val isGameOver: Boolean,
    val isPaused: Boolean,
    val lastClearedRows: List<Int>,
    val lastDropDistance: Int,
    val lockResetCount: Int = 0,
    val clearingBlocks: List<ClearingBlock> = emptyList(),
    val lineClearShiftBlocks: List<LineClearShiftBlock> = emptyList(),
    val attackObjects: List<SurvivalAttackObject> = emptyList(),
    val comboCount: Int = 0,
    val backToBackCount: Int = 0,
    val heatLevel: Float = 0f,
    val lastActionWasRotation: Boolean = false,
    val lastRotationKickIndex: Int? = null,
    val lastClearResult: ClearResult? = null
) {
    val isClearingLines: Boolean
        get() = clearingBlocks.isNotEmpty() || lineClearShiftBlocks.isNotEmpty()
}
