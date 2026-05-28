package com.hoshiyomi.filemanager.ui.filemanager

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.filemanager.core.archive.ArchiveManager
import com.hoshiyomi.filemanager.core.file.FileOperations
import com.hoshiyomi.filemanager.model.FileItem
import com.hoshiyomi.filemanager.model.SortMode
import com.hoshiyomi.filemanager.model.SortOrder
import com.hoshiyomi.filemanager.util.BookmarkManager
import com.hoshiyomi.filemanager.ui.filemanager.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ClipboardData(
    val files: List<File>,
    val isCut: Boolean = false
)

data class PanelFilesState(
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("file_manager_prefs", Context.MODE_PRIVATE)

    private val _leftPanelState = MutableStateFlow(PanelFilesState())
    val leftPanelState: StateFlow<PanelFilesState> = _leftPanelState.asStateFlow()

    private val _rightPanelState = MutableStateFlow(PanelFilesState())
    val rightPanelState: StateFlow<PanelFilesState> = _rightPanelState.asStateFlow()

    private val _clipboard = MutableStateFlow<ClipboardData?>(null)
    val clipboard: StateFlow<ClipboardData?> = _clipboard.asStateFlow()

    private val _showHidden = MutableStateFlow(loadBooleanPref(KEY_SHOW_HIDDEN, false))
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    private val _fileFilter = MutableStateFlow<String?>(null)
    val fileFilter: StateFlow<String?> = _fileFilter.asStateFlow()

    private val _sortMode = MutableStateFlow(
        SortMode.values().getOrElse(loadIntPref(KEY_SORT_MODE, 0)) { SortMode.NAME }
    )
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        SortOrder.values().getOrElse(loadIntPref(KEY_SORT_ORDER, 0)) { SortOrder.ASCENDING }
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun loadFiles(directory: File, isLeftPanel: Boolean) {
        val targetState = if (isLeftPanel) _leftPanelState else _rightPanelState
        targetState.value = targetState.value.copy(isLoading = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            val result = FileOperations.listFiles(
                directory,
                _showHidden.value,
                _sortMode.value,
                _sortOrder.value
            )
            val filtered = if (_fileFilter.value != null) {
                filterByType(result, _fileFilter.value!!)
            } else {
                result
            }
            val sortMode = _sortMode.value
            val isAscending = _sortOrder.value == SortOrder.ASCENDING
            val sorted = when (sortMode) {
                SortMode.NAME -> {
                    if (isAscending) filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
                    else filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenByDescending { it.name.lowercase() })
                }
                SortMode.SIZE -> {
                    if (isAscending) filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.file.length() })
                    else filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenByDescending { it.file.length() })
                }
                SortMode.DATE -> {
                    if (isAscending) filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.file.lastModified() })
                    else filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenByDescending { it.file.lastModified() })
                }
                SortMode.TYPE -> {
                    if (isAscending) filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.extension.lowercase() }.thenBy { it.name.lowercase() })
                    else filtered.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenByDescending { it.extension.lowercase() }.thenByDescending { it.name.lowercase() })
                }
            }
            targetState.value = PanelFilesState(files = sorted, isLoading = false)
        }
    }

    fun searchFiles(directory: File, query: String, isLeftPanel: Boolean) {
        val targetState = if (isLeftPanel) _leftPanelState else _rightPanelState
        targetState.value = targetState.value.copy(isLoading = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            val results = FileOperations.searchFiles(directory, query)
            targetState.value = PanelFilesState(files = results, isLoading = false)
        }
    }

    fun createDirectory(parent: File, name: String, isLeftPanel: Boolean, onComplete: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = FileOperations.createDirectory(parent, name)
            result.onSuccess { loadFiles(parent, isLeftPanel) }
            onComplete(result)
        }
    }

    fun createFile(parent: File, name: String, isLeftPanel: Boolean, onComplete: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = FileOperations.createFile(parent, name)
            result.onSuccess { loadFiles(parent, isLeftPanel) }
            onComplete(result)
        }
    }

    fun copyFilesToClipboard(files: List<File>) {
        _clipboard.value = ClipboardData(files = files, isCut = false)
    }

    fun cutFilesToClipboard(files: List<File>) {
        _clipboard.value = ClipboardData(files = files, isCut = true)
    }

    fun pasteFiles(destDir: File, isLeftPanel: Boolean, onComplete: (Result<Unit>) -> Unit) {
        val clip = _clipboard.value ?: return
        viewModelScope.launch {
            var lastResult: Result<Unit> = Result.success(Unit)
            for (file in clip.files) {
                val dest = File(destDir, file.name)
                lastResult = if (clip.isCut) {
                    FileOperations.moveFile(file, dest).map { }
                } else {
                    FileOperations.copyFile(file, dest).map { }
                }
                if (lastResult.isFailure) break
            }
            if (lastResult.isSuccess) {
                _clipboard.value = null
            }
            loadFiles(destDir, isLeftPanel)
            onComplete(lastResult)
        }
    }

    fun deleteFiles(files: List<File>, parentDir: File, isLeftPanel: Boolean, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            var lastResult: Result<Unit> = Result.success(Unit)
            for (file in files) {
                lastResult = FileOperations.deleteFile(file)
                if (lastResult.isFailure) break
            }
            loadFiles(parentDir, isLeftPanel)
            onComplete(lastResult)
        }
    }

    fun renameFile(file: File, newName: String, parentDir: File, isLeftPanel: Boolean, onComplete: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = FileOperations.renameFile(file, newName)
            result.onSuccess { loadFiles(parentDir, isLeftPanel) }
            onComplete(result)
        }
    }

    fun setShowHidden(show: Boolean) {
        _showHidden.value = show
        saveBooleanPref(KEY_SHOW_HIDDEN, show)
    }

    fun setFileFilter(filterType: String?) {
        _fileFilter.value = filterType
    }

    fun clearFileFilter() {
        _fileFilter.value = null
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        saveIntPref(KEY_SORT_MODE, mode.ordinal)
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        saveIntPref(KEY_SORT_ORDER, order.ordinal)
    }

    fun hasClipboard(): Boolean = _clipboard.value != null

    fun getClipboardInfo(): String {
        val clip = _clipboard.value ?: return ""
        val action = if (clip.isCut) "Cut" else "Copied"
        return "$action ${clip.files.size} items"
    }

    fun extractArchive(archiveFile: File, destDir: File, isLeftPanel: Boolean, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = ArchiveManager.extractArchive(archiveFile, destDir)
            result.onSuccess { loadFiles(destDir, isLeftPanel) }
            onComplete(result)
        }
    }

    fun compressFiles(files: List<File>, archiveFile: File, isLeftPanel: Boolean, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = ArchiveManager.createArchive(files, archiveFile)
            val parentDir = files.firstOrNull()?.parentFile
            if (parentDir != null) {
                result.onSuccess { loadFiles(parentDir, isLeftPanel) }
            }
            onComplete(result)
        }
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun getBookmarks(): List<File> {
        return BookmarkManager.getBookmarks()
    }

    fun saveBookmark(path: String) {
        BookmarkManager.saveBookmark(path)
    }

    fun removeBookmark(path: String) {
        BookmarkManager.removeBookmark(path)
    }

    fun isBookmarked(path: String): Boolean {
        return BookmarkManager.isBookmarked(path)
    }

    // ---- Navigation History ----

    private val MAX_HISTORY = 200
    private val KEY_NAV_HISTORY = "nav_history"

    fun addToHistory(path: String) {
        val history = loadHistoryRaw()
        val filtered = history.filter { it != path }
        val updated = listOf(path) + filtered
        val trimmed = if (updated.size > MAX_HISTORY) updated.take(MAX_HISTORY) else updated
        prefs.edit().putString(KEY_NAV_HISTORY, JSONArray(trimmed).toString()).apply()
    }

    fun getNavigationHistory(): List<HistoryEntry> {
        val paths = loadHistoryRaw()
        return paths.mapIndexedNotNull { index, path ->
            try {
                HistoryEntry(path = path)
            } catch (_: Exception) { null }
        }.reversed()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_NAV_HISTORY).apply()
    }

    fun removeHistoryEntry(path: String) {
        val history = loadHistoryRaw().filter { it != path }
        prefs.edit().putString(KEY_NAV_HISTORY, JSONArray(history).toString()).apply()
    }

    private fun loadHistoryRaw(): List<String> {
        val raw = prefs.getString(KEY_NAV_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun filterByType(files: List<FileItem>, type: String): List<FileItem> {
        return when (type) {
            "apk" -> files.filter { it.isApk }
            "image" -> files.filter { it.isImage }
            "video" -> files.filter { it.isVideo }
            "audio" -> files.filter { it.isAudio }
            "document" -> files.filter { it.isText }
            "archive" -> files.filter { it.isArchive }
            else -> files
        }
    }

    private fun loadBooleanPref(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    private fun saveBooleanPref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun loadIntPref(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    private fun saveIntPref(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    companion object {
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_SORT_ORDER = "sort_order"
    }

    init {
        // Ensure Gson is available (already in dependencies)
    }
}
