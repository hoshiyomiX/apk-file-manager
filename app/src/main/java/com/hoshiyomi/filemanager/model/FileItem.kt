package com.hoshiyomi.filemanager.model

import java.io.File
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val isHidden: Boolean = file.isHidden,
    val isArchive: Boolean = name.endsWith(".zip", true) ||
                            name.endsWith(".jar", true) ||
                            name.endsWith(".apk", true) ||
                            name.endsWith(".rar", true) ||
                            name.endsWith(".7z", true),
    val isApk: Boolean = name.endsWith(".apk", true),
    val isText: Boolean = name.endsWith(".txt", true) ||
                          name.endsWith(".xml", true) ||
                          name.endsWith(".json", true) ||
                          name.endsWith(".csv", true) ||
                          name.endsWith(".log", true) ||
                          name.endsWith(".md", true) ||
                          name.endsWith(".html", true) ||
                          name.endsWith(".css", true) ||
                          name.endsWith(".js", true) ||
                          name.endsWith(".kt", true) ||
                          name.endsWith(".java", true) ||
                          name.endsWith(".smali", true) ||
                          name.endsWith(".yml", true) ||
                          name.endsWith(".yaml", true) ||
                          name.endsWith(".ini", true) ||
                          name.endsWith(".cfg", true) ||
                          name.endsWith(".conf", true) ||
                          name.endsWith(".sh", true) ||
                          name.endsWith(".bat", true) ||
                          name.endsWith(".properties", true) ||
                          name.endsWith(".gradle", true) ||
                          name.endsWith(".pro", true) ||
                          name.endsWith(".c", true) ||
                          name.endsWith(".cpp", true) ||
                          name.endsWith(".h", true) ||
                          name.endsWith(".py", true) ||
                          name.endsWith(".rb", true) ||
                          name.endsWith(".rs", true) ||
                          name.endsWith(".go", true) ||
                          name.endsWith(".sql", true) ||
                          name.endsWith(".MF", true),
    val isImage: Boolean = name.endsWith(".png", true) ||
                           name.endsWith(".jpg", true) ||
                           name.endsWith(".jpeg", true) ||
                           name.endsWith(".gif", true) ||
                           name.endsWith(".webp", true) ||
                           name.endsWith(".bmp", true) ||
                           name.endsWith(".svg", true) ||
                           name.endsWith(".9.png", false),
    val isVideo: Boolean = name.endsWith(".mp4", true) ||
                           name.endsWith(".avi", true) ||
                           name.endsWith(".mkv", true) ||
                           name.endsWith(".webm", true) ||
                           name.endsWith(".3gp", true) ||
                           name.endsWith(".flv", true),
    val isAudio: Boolean = name.endsWith(".mp3", true) ||
                           name.endsWith(".wav", true) ||
                           name.endsWith(".ogg", true) ||
                           name.endsWith(".flac", true) ||
                           name.endsWith(".aac", true) ||
                           name.endsWith(".m4a", true),
    val isCode: Boolean = name.endsWith(".kt", true) ||
                          name.endsWith(".java", true) ||
                          name.endsWith(".smali", true) ||
                          name.endsWith(".xml", true) ||
                          name.endsWith(".json", true) ||
                          name.endsWith(".js", true) ||
                          name.endsWith(".html", true) ||
                          name.endsWith(".css", true) ||
                          name.endsWith(".py", true) ||
                          name.endsWith(".c", true) ||
                          name.endsWith(".cpp", true) ||
                          name.endsWith(".h", true) ||
                          name.endsWith(".go", true) ||
                          name.endsWith(".rs", true) ||
                          name.endsWith(".sh", true)
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val readableSize: String get() = formatFileSize(size)
    val readableDate: String get() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(lastModified))
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceAtMost(units.size - 1)
            return String.format(
                Locale.US, "%.1f %s",
                bytes / Math.pow(1024.0, index.toDouble()),
                units[index]
            )
        }
    }
}
