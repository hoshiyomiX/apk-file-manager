package com.hoshiyomi.filemanager.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    fun applyTheme(context: Context, darkMode: Boolean) {
        val mode = if (darkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        saveDarkMode(context, darkMode)
    }

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun toggleTheme(context: Context) {
        val currentDark = isDarkMode(context)
        applyTheme(context, !currentDark)
    }

    private fun saveDarkMode(context: Context, darkMode: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply()
    }

    fun initTheme(context: Context) {
        val darkMode = isDarkMode(context)
        applyTheme(context, darkMode)
    }
}
