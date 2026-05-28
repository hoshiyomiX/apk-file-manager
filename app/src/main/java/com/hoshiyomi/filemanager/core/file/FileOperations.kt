package com.hoshiyomi.filemanager.core.file

import com.hoshiyomi.filemanager.model.FileItem
import com.hoshiyomi.filemanager.model.SortMode
import com.hoshiyomi.filemanager.model.SortOrder
import com.hoshiyomi.filemanager.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object FileOperations {

    suspend fun listFiles(
        directory: File,
        showHidden: Boolean,
        sortMode: SortMode,
        sortOrder: SortOrder
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) {
            DiagnosticLogger.warn("FM-LIST", "Cannot list directory", mapOf<String, Any>(
                "path" to directory.absolutePath,
                "exists" to directory.exists(),
                "is_directory" to directory.isDirectory
            ))
            return@withContext emptyList()
        }

        val files = directory.listFiles()
        if (files == null) {
            DiagnosticLogger.error("FM-LIST", "listFiles() returned null", mapOf<String, Any>(
                "path" to directory.absolutePath,
                "can_read" to directory.canRead(),
                "can_write" to directory.canWrite()
            ))
            return@withContext emptyList()
        }

        files
            .filter { showHidden || !it.isHidden }
            .map { FileItem(it) }
            .sortedWith(createComparator(sortMode, sortOrder))
    }

    private fun createComparator(sortMode: SortMode, sortOrder: SortOrder): Comparator<FileItem> {
        val base: Comparator<FileItem> = when (sortMode) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.SIZE -> compareBy { if (it.isDirectory) 0L else it.size }
            SortMode.DATE -> compareBy { it.lastModified }
            SortMode.TYPE -> compareBy { it.extension }
        }
        return if (sortOrder == SortOrder.DESCENDING) base.reversed() else base
    }

    suspend fun createDirectory(parent: File, name: String): Result<File> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                if (name.isBlank()) {
                    throw IllegalArgumentException("Name cannot be empty")
                }
                val dir = File(parent, name)
                if (dir.exists()) {
                    throw IllegalStateException("${dir.name} already exists")
                }
                if (!dir.mkdirs()) {
                    throw RuntimeException("Failed to create directory: ${dir.absolutePath}")
                }
                DiagnosticLogger.timed("FM-CREATE", "Created directory: ${dir.name}", start, mapOf<String, Any>(
                    "path" to dir.absolutePath,
                    "parent" to parent.absolutePath
                ))
                dir
            }.onFailure { e ->
                DiagnosticLogger.error("FM-CREATE", "Failed to create directory: $name", mapOf<String, Any>(
                    "parent" to parent.absolutePath,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

    suspend fun createFile(parent: File, name: String): Result<File> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                if (name.isBlank()) {
                    throw IllegalArgumentException("Name cannot be empty")
                }
                val file = File(parent, name)
                if (file.exists()) {
                    throw IllegalStateException("${file.name} already exists")
                }
                if (!file.createNewFile()) {
                    throw RuntimeException("Failed to create file: ${file.absolutePath}")
                }
                DiagnosticLogger.timed("FM-CREATE", "Created file: ${file.name}", start, mapOf<String, Any>(
                    "path" to file.absolutePath,
                    "parent" to parent.absolutePath
                ))
                file
            }.onFailure { e ->
                DiagnosticLogger.error("FM-CREATE", "Failed to create file: $name", mapOf<String, Any>(
                    "parent" to parent.absolutePath,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

    suspend fun copyFile(source: File, destination: File): Result<File> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                if (!source.exists()) {
                    throw FileNotFoundException("Source not found: ${source.absolutePath}")
                }
                if (source.isDirectory) {
                    copyDirectory(source, destination)
                } else {
                    destination.parentFile?.mkdirs()
                    source.inputStream().buffered().use { input ->
                        destination.outputStream().buffered().use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        }
                    }
                }
                DiagnosticLogger.timed("FM-COPY", "Copied: ${source.name}", start, mapOf<String, Any>(
                    "source" to source.absolutePath,
                    "destination" to destination.absolutePath,
                    "size" to source.length()
                ))
                destination
            }.onFailure { e ->
                DiagnosticLogger.error("FM-COPY", "Copy failed: ${source.name}", mapOf<String, Any>(
                    "source" to source.absolutePath,
                    "destination" to destination.absolutePath,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

    private fun copyDirectory(source: File, destination: File) {
        if (!destination.exists()) {
            destination.mkdirs()
        }
        source.listFiles()?.forEach { child ->
            val destChild = File(destination, child.name)
            if (child.isDirectory) {
                copyDirectory(child, destChild)
            } else {
                child.inputStream().buffered().use { input ->
                    destChild.outputStream().buffered().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
            }
        }
    }

    suspend fun moveFile(source: File, destination: File): Result<File> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                if (!source.exists()) {
                    throw FileNotFoundException("Source not found: ${source.absolutePath}")
                }
                if (!source.renameTo(destination)) {
                    copyDirectoryRecursive(source, destination)
                    deleteFileInternal(source)
                }
                DiagnosticLogger.timed("FM-MOVE", "Moved: ${source.name}", start, mapOf<String, Any>(
                    "source" to source.absolutePath,
                    "destination" to destination.absolutePath
                ))
                destination
            }.onFailure { e ->
                DiagnosticLogger.error("FM-MOVE", "Move failed: ${source.name}", mapOf<String, Any>(
                    "source" to source.absolutePath,
                    "destination" to destination.absolutePath,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

    private fun copyDirectoryRecursive(source: File, destination: File) {
        if (source.isDirectory) {
            if (!destination.exists()) {
                destination.mkdirs()
            }
            source.listFiles()?.forEach { child ->
                copyDirectoryRecursive(child, File(destination, child.name))
            }
        } else {
            destination.parentFile?.mkdirs()
            source.inputStream().buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }

    private fun deleteFileInternal(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteFileInternal(it) }
        }
        file.delete()
    }

    suspend fun renameFile(file: File, newName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (newName.isBlank()) {
                    throw IllegalArgumentException("Name cannot be empty")
                }
                val newFile = File(file.parentFile, newName)
                if (newFile.exists()) {
                    throw IllegalStateException("${newName} already exists")
                }
                if (!file.renameTo(newFile)) {
                    throw RuntimeException("Failed to rename ${file.name} to $newName")
                }
                DiagnosticLogger.info("FM-RENAME", "Renamed: ${file.name} -> $newName", mapOf<String, Any>(
                    "path" to file.absolutePath,
                    "new_path" to newFile.absolutePath
                ))
                newFile
            }.onFailure { e ->
                DiagnosticLogger.error("FM-RENAME", "Rename failed: ${file.name}", mapOf<String, Any>(
                    "path" to file.absolutePath,
                    "new_name" to newName,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

    suspend fun deleteFile(file: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                if (!file.exists()) {
                    throw FileNotFoundException("File not found: ${file.absolutePath}")
                }
                deleteFileInternal(file)
                DiagnosticLogger.timed("FM-DELETE", "Deleted: ${file.name}", start, mapOf<String, Any>(
                    "path" to file.absolutePath,
                    "type" to if (file.isDirectory) "directory" else "file"
                ))
            }.onFailure { e ->
                DiagnosticLogger.error("FM-DELETE", "Delete failed: ${file.name}", mapOf<String, Any>(
                    "path" to file.absolutePath,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

    suspend fun getFileInfo(file: File): Map<String, String> =
        withContext(Dispatchers.IO) {
            val info = mutableMapOf<String, String>()
            info["Name"] = file.name
            info["Path"] = file.absolutePath
            info["Size"] = FileItem.formatFileSize(if (file.isDirectory) calculateDirectorySize(file) else file.length())
            info["Type"] = if (file.isDirectory) "Directory" else file.extension.uppercase().ifEmpty { "Unknown" }
            info["Modified"] = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date(file.lastModified()))
            info["Can Read"] = if (file.canRead()) "Yes" else "No"
            info["Can Write"] = if (file.canWrite()) "Yes" else "No"
            info["Can Execute"] = if (file.canExecute()) "Yes" else "No"
            info["Hidden"] = if (file.isHidden) "Yes" else "No"
            if (file.isDirectory) {
                val children = file.listFiles()
                info["Contains"] = "${children?.size ?: 0} items"
            }
            info
        }

    suspend fun searchFiles(directory: File, query: String): List<FileItem> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val results = mutableListOf<FileItem>()
            val lowerQuery = query.lowercase()
            searchRecursive(directory, lowerQuery, results)
            DiagnosticLogger.timed("FM-SEARCH", "Searched: '$query'", start, mapOf<String, Any>(
                "root" to directory.absolutePath,
                "results" to results.size
            ))
            results
        }

    private fun searchRecursive(directory: File, query: String, results: MutableList<FileItem>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.name.lowercase().contains(query)) {
                results.add(FileItem(file))
            }
            if (file.isDirectory) {
                searchRecursive(file, query, results)
            }
        }
    }

    suspend fun calculateDirectorySize(directory: File): Long =
        withContext(Dispatchers.IO) {
            calculateSizeInternal(directory)
        }

    private fun calculateSizeInternal(directory: File): Long {
        var size = 0L
        val files = directory.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) {
                calculateSizeInternal(file)
            } else {
                file.length()
            }
        }
        return size
    }

    suspend fun computeFileHash(file: File, algorithm: String): String =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                throw FileNotFoundException("File not found: ${file.absolutePath}")
            }
            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
}
