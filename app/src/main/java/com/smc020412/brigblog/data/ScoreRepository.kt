package com.smc020412.brigblog.data

import android.content.Context

data class ScoreSaveResult(
    val scores: List<Int>,
    val insertedRank: Int?
)

class ScoreRepository(
    context: Context
) {
    private val preferences = context.getSharedPreferences("brigblog_scores", Context.MODE_PRIVATE)

    fun getScores(): List<Int> =
        preferences.getString(KEY_SCORES, "")
            .orEmpty()
            .split(",")
            .mapNotNull { value -> value.toIntOrNull() }
            .sortedDescending()
            .take(MAX_SCORES)

    fun getBestScore(): Int =
        getScores().firstOrNull() ?: 0

    fun saveScore(score: Int): ScoreSaveResult =
        saveScoreForKey(KEY_SCORES, score)

    fun getTimeScores(durationSeconds: Int): List<Int> =
        getScoresForKey(timeScoreKey(durationSeconds))

    fun saveTimeScore(durationSeconds: Int, score: Int): ScoreSaveResult =
        saveScoreForKey(timeScoreKey(durationSeconds), score)

    fun getSurvivalScores(): List<Int> =
        getScoresForKey(KEY_SURVIVAL_SCORES)

    fun saveSurvivalScore(score: Int): ScoreSaveResult =
        saveScoreForKey(KEY_SURVIVAL_SCORES, score)

    private fun saveScoreForKey(key: String, score: Int): ScoreSaveResult {
        val existingScores = getScoresForKey(key)
        if (score <= 0) return ScoreSaveResult(existingScores, insertedRank = null)

        val result = insertScore(existingScores, score)
        preferences.edit()
            .putString(key, result.scores.joinToString(","))
            .apply()
        return result
    }

    private fun getScoresForKey(key: String): List<Int> =
        preferences.getString(key, "")
            .orEmpty()
            .split(",")
            .mapNotNull { value -> value.toIntOrNull() }
            .sortedDescending()
            .take(MAX_SCORES)

    companion object {
        const val MAX_SCORES = 10
        private const val KEY_SCORES = "scores"
        private const val KEY_SURVIVAL_SCORES = "survival_scores"

        private fun timeScoreKey(durationSeconds: Int): String =
            "time_scores_$durationSeconds"
    }
}

internal fun insertScore(existingScores: List<Int>, score: Int): ScoreSaveResult {
    data class RankedScore(val value: Int, val isNew: Boolean)

    val rankedScores = (existingScores.map { RankedScore(it, isNew = false) } +
        RankedScore(score, isNew = true))
        .sortedByDescending { it.value }
        .take(ScoreRepository.MAX_SCORES)

    return ScoreSaveResult(
        scores = rankedScores.map { it.value },
        insertedRank = rankedScores.indexOfFirst { it.isNew }
            .takeIf { it >= 0 }
            ?.plus(1)
    )
}
