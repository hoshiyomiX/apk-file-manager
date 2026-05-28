package com.hoshiyomi.filemanager.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val value: Int) {
    SYSTEM(-1),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return values().firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun applyTheme(context: Context, mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(mode.value)
        saveThemeMode(context, mode)
    }

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedValue = prefs.getInt(KEY_THEME_MODE, -1)
        return ThemeMode.fromValue(savedValue)
    }

    fun isDarkMode(context: Context): Boolean {
        return when (getThemeMode(context)) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val currentNightMode = AppCompatDelegate.getDefaultNightMode()
                currentNightMode == AppCompatDelegate.MODE_NIGHT_YES
            }
        }
    }

    fun initTheme(context: Context) {
        val mode = getThemeMode(context)
        applyTheme(context, mode)
    }

    private fun saveThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_THEME_MODE, mode.value).apply()
    }
}
