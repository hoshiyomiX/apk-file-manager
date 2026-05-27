package com.hoshiyomi.filemanager.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.util.ThemeManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference<androidx.preference.SwitchPreferenceCompat>("dark_mode")?.setOnPreferenceChangeListener { _, newValue ->
            val darkMode = newValue as Boolean
            ThemeManager.applyTheme(requireContext(), darkMode)
            true
        }
    }
}
