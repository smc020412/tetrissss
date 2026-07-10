package com.smc020412.brigblog.data

import android.content.Context
import com.smc020412.brigblog.ui.ControlAction
import com.smc020412.brigblog.ui.ControlPlacement
import com.smc020412.brigblog.ui.DefaultControlLayout

class ControlLayoutRepository(
    context: Context
) {
    private val preferences = context.getSharedPreferences("brigblog_controls", Context.MODE_PRIVATE)

    fun load(): List<ControlPlacement> {
        if (preferences.getInt(KEY_VERSION, 0) < LAYOUT_VERSION) {
            return reset()
        }
        val encoded = preferences.getString(KEY_LAYOUT, null) ?: return DefaultControlLayout.placements
        val parsed = encoded.split(";")
            .mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size != 4) return@mapNotNull null

                val action = runCatching { ControlAction.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                val x = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val y = parts[2].toFloatOrNull() ?: return@mapNotNull null
                val size = parts[3].toFloatOrNull() ?: return@mapNotNull null
                ControlPlacement(
                    action = action,
                    xRatio = x.coerceIn(0f, 1f),
                    yRatio = y.coerceIn(0f, 1f),
                    sizeDp = size.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                )
            }

        return DefaultControlLayout.normalized(parsed)
    }

    fun save(placements: List<ControlPlacement>) {
        val encoded = DefaultControlLayout.normalized(placements).joinToString(";") { placement ->
            "${placement.action.name}:${placement.xRatio}:${placement.yRatio}:${placement.sizeDp}"
        }
        preferences.edit().putString(KEY_LAYOUT, encoded).apply()
        preferences.edit().putInt(KEY_VERSION, LAYOUT_VERSION).apply()
    }

    fun reset(): List<ControlPlacement> {
        preferences.edit()
            .remove(KEY_LAYOUT)
            .putInt(KEY_VERSION, LAYOUT_VERSION)
            .apply()
        return DefaultControlLayout.placements
    }

    companion object {
        private const val KEY_LAYOUT = "layout"
        private const val KEY_VERSION = "layout_version"
        private const val LAYOUT_VERSION = 6
        private const val MIN_SIZE_DP = 44f
        private const val MAX_SIZE_DP = 86f
    }
}
