package com.smc020412.brigblog.game

enum class ClearKind {
    Single,
    Double,
    Triple,
    Quad,
    TSpinMiniSingle,
    TSpinMiniDouble,
    TSpinSingle,
    TSpinDouble,
    TSpinTriple
}

data class ClearResult(
    val kind: ClearKind,
    val clearedLines: Int,
    val isPerfectClear: Boolean,
    val comboCount: Int,
    val backToBackCount: Int,
    val scoreAwarded: Int,
    val perfectClearBonus: Int,
    val usedBackToBackBonus: Boolean,
    val heatLevel: Float
) {
    val isTSpin: Boolean
        get() = kind in setOf(
            ClearKind.TSpinMiniSingle,
            ClearKind.TSpinMiniDouble,
            ClearKind.TSpinSingle,
            ClearKind.TSpinDouble,
            ClearKind.TSpinTriple
        )

    val isMiniTSpin: Boolean
        get() = kind == ClearKind.TSpinMiniSingle || kind == ClearKind.TSpinMiniDouble
}

object GameScoring {
    private val normalClearPoints = intArrayOf(0, 10, 30, 50, 80)
    private val miniTSpinClearPoints = intArrayOf(0, 20, 35)
    private val tSpinClearPoints = intArrayOf(0, 40, 70, 100)
    private val perfectClearPoints = intArrayOf(0, 50, 100, 150, 250)
    private const val ComboStepPoints = 5

    fun resolveClear(
        clearedLines: Int,
        level: Int,
        comboCount: Int,
        backToBackCount: Int,
        tSpinType: TSpinType,
        isPerfectClear: Boolean = false
    ): ClearResult {
        require(clearedLines in 1..4)

        val resolvedTSpinType = tSpinType.resolveForLineCount(clearedLines)
        val kind = clearKind(clearedLines, resolvedTSpinType)
        val isTSpin = resolvedTSpinType != TSpinType.None
        val isDifficultClear = clearedLines == 4 || isTSpin
        val nextComboCount = comboCount + 1
        val usedBackToBackBonus = isDifficultClear && backToBackCount > 0
        val nextBackToBackCount = if (isDifficultClear) backToBackCount + 1 else 0
        val baseScore = when (resolvedTSpinType) {
            TSpinType.None -> normalClearPoints.getOrElse(clearedLines) { 0 }
            TSpinType.Mini -> miniTSpinClearPoints.getOrElse(clearedLines) { 0 }
            TSpinType.Full -> tSpinClearPoints.getOrElse(clearedLines) { 0 }
        }
        val backToBackScore = if (usedBackToBackBonus) baseScore / 2 else 0
        val comboScore = (nextComboCount - 1).coerceAtLeast(0) * ComboStepPoints
        val perfectClearBonus = if (isPerfectClear) {
            perfectClearPoints.getOrElse(clearedLines) { 0 } * level
        } else {
            0
        }
        val awardedScore = (baseScore + backToBackScore + comboScore) * level + perfectClearBonus

        return ClearResult(
            kind = kind,
            clearedLines = clearedLines,
            isPerfectClear = isPerfectClear,
            comboCount = nextComboCount,
            backToBackCount = nextBackToBackCount,
            scoreAwarded = awardedScore,
            perfectClearBonus = perfectClearBonus,
            usedBackToBackBonus = usedBackToBackBonus,
            heatLevel = heatLevel(nextComboCount, nextBackToBackCount, isTSpin, usedBackToBackBonus)
        )
    }

    fun heatAfterNoClear(backToBackCount: Int): Float =
        (backToBackCount * 0.08f).coerceIn(0f, 0.32f)

    private fun TSpinType.resolveForLineCount(lines: Int): TSpinType =
        if (this == TSpinType.Mini && lines >= 3) TSpinType.Full else this

    private fun clearKind(lines: Int, tSpinType: TSpinType): ClearKind =
        when (tSpinType) {
            TSpinType.Mini -> when (lines) {
                1 -> ClearKind.TSpinMiniSingle
                2 -> ClearKind.TSpinMiniDouble
                else -> error("Mini T-Spin cannot clear more than two lines")
            }
            TSpinType.Full -> {
            when (lines) {
                1 -> ClearKind.TSpinSingle
                2 -> ClearKind.TSpinDouble
                else -> ClearKind.TSpinTriple
            }
            }
            TSpinType.None -> when (lines) {
                1 -> ClearKind.Single
                2 -> ClearKind.Double
                3 -> ClearKind.Triple
                else -> ClearKind.Quad
            }
        }

    private fun heatLevel(
        comboCount: Int,
        backToBackCount: Int,
        isTSpin: Boolean,
        usedBackToBackBonus: Boolean
    ): Float {
        val comboHeat = (comboCount - 1).coerceAtLeast(0) * 0.12f
        val backToBackHeat = backToBackCount * 0.10f
        val specialHeat = when {
            isTSpin && usedBackToBackBonus -> 0.30f
            isTSpin -> 0.22f
            usedBackToBackBonus -> 0.16f
            else -> 0f
        }
        return (0.14f + comboHeat + backToBackHeat + specialHeat).coerceIn(0f, 1f)
    }
}
