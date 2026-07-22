package com.smc020412.brigblog

import com.smc020412.brigblog.game.Cell
import com.smc020412.brigblog.game.PieceType
import com.smc020412.brigblog.game.SevenBagRandomizer
import com.smc020412.brigblog.game.SrsRotationSystem
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class SrsRotationSystemTest {
    @Test
    fun standardPiecesMatchAllEightGuidelineSrsTransitions() {
        val transitions = listOf(0 to 1, 1 to 0, 1 to 2, 2 to 1, 2 to 3, 3 to 2, 3 to 0, 0 to 3)

        transitions.forEach { (from, to) ->
            assertEquals(expectedStandardKicks(from, to), SrsRotationSystem.kicksFor(PieceType.T, from, to))
        }
    }

    @Test
    fun iPieceMatchesAllEightGuidelineSrsTransitions() {
        val transitions = listOf(0 to 1, 1 to 0, 1 to 2, 2 to 1, 2 to 3, 3 to 2, 3 to 0, 0 to 3)

        transitions.forEach { (from, to) ->
            assertEquals(expectedIKicks(from, to), SrsRotationSystem.kicksFor(PieceType.I, from, to))
        }
    }

    @Test
    fun onlyQuarterTurnsAreAcceptedByTheSrsRuleSet() {
        assertEquals(true, SrsRotationSystem.isQuarterTurn(0, 1))
        assertEquals(true, SrsRotationSystem.isQuarterTurn(0, 3))
        assertEquals(false, SrsRotationSystem.isQuarterTurn(0, 0))
        assertEquals(false, SrsRotationSystem.isQuarterTurn(0, 2))
    }
    @Test
    fun everySevenBagContainsEachPlayablePieceExactlyOnce() {
        val randomizer = SevenBagRandomizer(Random(20260720))
        val expectedPieces = setOf(PieceType.I, PieceType.J, PieceType.L, PieceType.O, PieceType.S, PieceType.T, PieceType.Z)

        repeat(32) {
            val bag = randomizer.nextBag()
            assertEquals(7, bag.size)
            assertEquals(expectedPieces, bag.toSet())
            assertEquals(7, bag.distinct().size)
        }
    }

    private fun expectedStandardKicks(from: Int, to: Int): List<Cell> = when (from to to) {
        0 to 1 -> kicks(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2)
        1 to 0, 1 to 2 -> kicks(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2)
        2 to 1 -> kicks(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2)
        2 to 3, 0 to 3 -> kicks(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2)
        3 to 2, 3 to 0 -> kicks(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2)
        else -> error("Unexpected standard SRS transition: $from -> $to")
    }

    private fun expectedIKicks(from: Int, to: Int): List<Cell> = when (from to to) {
        0 to 1 -> kicks(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2)
        1 to 0 -> kicks(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2)
        1 to 2 -> kicks(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1)
        2 to 1 -> kicks(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1)
        2 to 3 -> kicks(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2)
        3 to 2 -> kicks(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2)
        3 to 0 -> kicks(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1)
        0 to 3 -> kicks(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1)
        else -> error("Unexpected I SRS transition: $from -> $to")
    }

    private fun kicks(vararg offsets: Pair<Int, Int>): List<Cell> =
        offsets.map { (x, yUp) -> Cell(x, -yUp) }
}