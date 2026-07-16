package com.smc020412.brigblog.ui

import com.smc020412.brigblog.game.PieceType

/** A visual-only snapshot of the piece at the moment it lands. */
data class HardDropImpact(
    val blocks: List<HardDropImpactBlock>
)

data class HardDropImpactBlock(
    val x: Int,
    val y: Int,
    val type: PieceType
)
