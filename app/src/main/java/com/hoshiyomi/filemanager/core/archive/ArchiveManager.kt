package com.hoshiyomi.filemanager.core.archive

import com.hoshiyomi.filemanager.core.apk.ZipEntryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import java.io.File

object ArchiveManager {

    private val SUPPORTED_EXTENSIONS = setOf("zip", "jar", "apk", "rar", "7z")

    suspend fun listArchiveContents(archiveFile: File): List<ZipEntryInfo> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<ZipEntryInfo>()
            try {
                val zip = ZipFile(archiveFile)
                if (!zip.isValidZipFile) {
                    return@withContext entries
                }
                val headers: List<FileHeader> = zip.fileHeaders
                for (header in headers) {
                    entries.add(
                        ZipEntryInfo(
                            name = header.fileName,
                            size = header.uncompressedSize,
                            compressedSize = header.compressedSize,
                            isDirectory = header.isDirectory,
                            lastModified = header.lastModifiedTime
                        )
                    )
                }
            } catch (_: ZipException) {
            }
            entries.sortedWith(compareByDescending<ZipEntryInfo> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

    suspend fun extractArchive(archiveFile: File, destDir: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                val zip = ZipFile(archiveFile)
                zip.extractAll(destDir.absolutePath)
            }
        }

    suspend fun extractEntry(archiveFile: File, entryPath: String, destDir: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                val zip = ZipFile(archiveFile)
                val outputFile = File(destDir, entryPath.substringAfterLast('/'))
                zip.extractFile(entryPath, destDir.absolutePath)
                outputFile
            }
        }

    suspend fun createArchive(files: List<File>, archiveFile: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                archiveFile.parentFile?.mkdirs()
                val zip = ZipFile(archiveFile)
                for (file in files) {
                    if (file.exists()) {
                        if (file.isDirectory) {
                            zip.addFolder(file)
                        } else {
                            zip.addFile(file)
                        }
                    }
                }
            }
        }

    suspend fun addToArchive(archiveFile: File, files: List<File>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!archiveFile.exists()) {
                    throw FileNotFoundException("Archive not found: ${archiveFile.absolutePath}")
                }
                val zip = ZipFile(archiveFile)
                for (file in files) {
                    if (file.exists()) {
                        if (file.isDirectory) {
                            zip.addFolder(file)
                        } else {
                            zip.addFile(file)
                        }
                    }
                }
            }
        }

    fun isArchiveSupported(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in SUPPORTED_EXTENSIONS
    }
}
