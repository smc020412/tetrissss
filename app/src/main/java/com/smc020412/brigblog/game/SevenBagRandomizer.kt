package com.smc020412.brigblog.game

import kotlin.random.Random

internal class SevenBagRandomizer(
    private val random: Random
) {
    private val playablePieces = listOf(
        PieceType.I,
        PieceType.J,
        PieceType.L,
        PieceType.O,
        PieceType.S,
        PieceType.T,
        PieceType.Z
    )

    fun nextBag(): List<PieceType> = playablePieces.shuffled(random)
}