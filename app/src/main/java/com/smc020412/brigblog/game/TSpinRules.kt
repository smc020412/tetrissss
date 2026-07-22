package com.smc020412.brigblog.game

internal object TSpinRules {
    fun classify(
        board: Board,
        piece: Piece,
        lastActionWasRotation: Boolean,
        kickIndex: Int?
    ): TSpinType {
        if (piece.type != PieceType.T || !lastActionWasRotation) return TSpinType.None

        val pivot = Cell(piece.x + 1, piece.y + 1)
        fun isBlocked(offset: Cell): Boolean {
            val x = pivot.x + offset.x
            val y = pivot.y + offset.y
            return x !in 0 until board.width || y !in 0 until board.height || board.cells[y][x] != null
        }

        val corners = listOf(Cell(-1, -1), Cell(1, -1), Cell(-1, 1), Cell(1, 1))
        if (corners.count(::isBlocked) < 3) return TSpinType.None

        val frontCorners = when (piece.rotation) {
            0 -> listOf(Cell(-1, -1), Cell(1, -1))
            1 -> listOf(Cell(1, -1), Cell(1, 1))
            2 -> listOf(Cell(-1, 1), Cell(1, 1))
            else -> listOf(Cell(-1, -1), Cell(-1, 1))
        }
        return if (frontCorners.all(::isBlocked) || kickIndex == SrsRotationSystem.LAST_KICK_INDEX) {
            TSpinType.Full
        } else {
            TSpinType.Mini
        }
    }
}