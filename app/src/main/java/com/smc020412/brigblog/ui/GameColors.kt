package com.smc020412.brigblog.ui

import androidx.compose.ui.graphics.Color
import com.smc020412.brigblog.game.PieceType

object GameColors {
    val Background = Color(0xFF111318)
    val Panel = Color(0xFF1B1F2A)
    val Board = Color(0xFF080A0F)
    val Grid = Color(0xFF202635)
    val Text = Color(0xFFECEFF7)
    val MutedText = Color(0xFFAAB1C0)
    val Accent = Color(0xFF8FD6FF)
    val Warning = Color(0xFFFFD166)
    val Danger = Color(0xFFFF5D73)
    val Heat = Color(0xFFFF8A3D)
    val Overheat = Color(0xFFFF4D5E)

    fun piece(type: PieceType): Color =
        when (type) {
            PieceType.I -> Color(0xFF44D7FF)
            PieceType.J -> Color(0xFF5F7CFF)
            PieceType.L -> Color(0xFFFFAA3D)
            PieceType.O -> Color(0xFFFFD84D)
            PieceType.S -> Color(0xFF4DE17E)
            PieceType.T -> Color(0xFFC678FF)
            PieceType.Z -> Color(0xFFFF5E68)
            PieceType.Garbage -> Color(0xFFECEFF7)
        }
}
