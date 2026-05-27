package com.hoshiyomi.filemanager.util

import android.content.Context
import java.io.File

object BookmarkManager {

    private const val PREFS_NAME = "bookmarks"
    private const val KEY_BOOKMARKS = "bookmark_paths"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBookmarks(): List<File> {
        if (!::prefs.isInitialized) return emptyList()
        val paths = prefs.getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
        return paths.mapNotNull { path ->
            val file = File(path)
            if (file.exists() && file.isDirectory) file else null
        }.sortedBy { it.absolutePath.lowercase() }
    }

    fun saveBookmark(path: String) {
        if (!::prefs.isInitialized) return
        val current = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(path)
        prefs.edit().putStringSet(KEY_BOOKMARKS, current).apply()
    }

    fun removeBookmark(path: String) {
        if (!::prefs.isInitialized) return
        val current = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(path)
        prefs.edit().putStringSet(KEY_BOOKMARKS, current).apply()
    }

    fun isBookmarked(path: String): Boolean {
        if (!::prefs.isInitialized) return false
        val current = prefs.getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
        return path in current
    }
}
