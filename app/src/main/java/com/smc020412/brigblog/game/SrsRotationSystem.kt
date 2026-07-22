package com.smc020412.brigblog.game

/**
 * Guideline SRS kick data. Source values use the conventional coordinate system
 * where positive Y points upward; [kick] converts them once for the board's Y-down grid.
 */
internal object SrsRotationSystem {
    const val LAST_KICK_INDEX = 4

    private data class Transition(val from: Int, val to: Int)

    private fun transition(from: Int, to: Int) = Transition(from, to)
    private fun kick(x: Int, yUp: Int) = Cell(x, -yUp)
    private fun kicks(vararg offsets: Pair<Int, Int>) = offsets.map { (x, yUp) -> kick(x, yUp) }

    private val standardKicks = mapOf(
        transition(0, 1) to kicks(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
        transition(1, 0) to kicks(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
        transition(1, 2) to kicks(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
        transition(2, 1) to kicks(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
        transition(2, 3) to kicks(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
        transition(3, 2) to kicks(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
        transition(3, 0) to kicks(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
        transition(0, 3) to kicks(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2)
    )

    private val iKicks = mapOf(
        transition(0, 1) to kicks(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2),
        transition(1, 0) to kicks(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2),
        transition(1, 2) to kicks(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1),
        transition(2, 1) to kicks(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1),
        transition(2, 3) to kicks(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2),
        transition(3, 2) to kicks(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2),
        transition(3, 0) to kicks(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1),
        transition(0, 3) to kicks(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1)
    )

    fun isQuarterTurn(fromRotation: Int, toRotation: Int): Boolean =
        fromRotation in 0..3 && toRotation in 0..3 && (toRotation - fromRotation + 4) % 4 in setOf(1, 3)
    fun kicksFor(type: PieceType, fromRotation: Int, toRotation: Int): List<Cell> {
        require(isQuarterTurn(fromRotation, toRotation))

        return when (type) {
            PieceType.O -> listOf(Cell(0, 0))
            PieceType.I -> iKicks.getValue(transition(fromRotation, toRotation))
            else -> standardKicks.getValue(transition(fromRotation, toRotation))
        }
    }
}