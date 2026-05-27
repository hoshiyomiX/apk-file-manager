package com.hoshiyomi.filemanager.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.model.FileItem
import java.io.File

object FileUtils {

    fun getFileIcon(fileItem: FileItem): Int {
        return when {
            fileItem.isDirectory -> R.drawable.ic_folder
            fileItem.isApk -> R.drawable.ic_apk
            fileItem.isImage -> R.drawable.ic_image
            fileItem.isVideo -> R.drawable.ic_video
            fileItem.isAudio -> R.drawable.ic_audio
            fileItem.isArchive -> R.drawable.ic_archive
            fileItem.isCode -> R.drawable.ic_code
            fileItem.isText -> R.drawable.ic_text
            else -> R.drawable.ic_file
        }
    }

    fun getFileExtensionIcon(extension: String): Int {
        return when (extension.lowercase()) {
            "apk" -> R.drawable.ic_apk
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg" -> R.drawable.ic_image
            "mp4", "avi", "mkv", "webm", "3gp", "flv" -> R.drawable.ic_video
            "mp3", "wav", "ogg", "flac", "aac", "m4a" -> R.drawable.ic_audio
            "zip", "jar", "rar", "7z" -> R.drawable.ic_archive
            "kt", "java", "smali", "xml", "json", "js", "html", "css",
            "py", "c", "cpp", "h", "go", "rs", "sh" -> R.drawable.ic_code
            "txt", "csv", "log", "md", "yml", "yaml", "ini", "cfg",
            "conf", "properties", "gradle", "sql" -> R.drawable.ic_text
            else -> R.drawable.ic_file
        }
    }

    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        val mimeFromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (!mimeFromMap.isNullOrBlank()) return mimeFromMap
        return when (extension) {
            "apk" -> "application/vnd.android.package-archive"
            "jar" -> "application/java-archive"
            "zip" -> "application/zip"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "smali" -> "text/plain"
            "yml", "yaml" -> "text/yaml"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "sh" -> "application/x-sh"
            "log" -> "text/plain"
            "properties" -> "text/plain"
            "gradle" -> "text/plain"
            "sql" -> "application/sql"
            else -> "application/octet-stream"
        }
    }

    fun openFile(context: Context, file: File): Boolean {
        return try {
            val uri = getFileUri(context, file) ?: return false
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getFileUri(context: Context, file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: IllegalArgumentException) {
            Uri.fromFile(file)
        }
    }

    fun getStorageDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        dirs.add(Environment.getExternalStorageDirectory())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dirs.add(File(Environment.getExternalStorageDirectory(), "Documents"))
            dirs.add(File(Environment.getExternalStorageDirectory(), "Download"))
            dirs.add(File(Environment.getExternalStorageDirectory(), "DCIM"))
            dirs.add(File(Environment.getExternalStorageDirectory(), "Pictures"))
            dirs.add(File(Environment.getExternalStorageDirectory(), "Music"))
            dirs.add(File(Environment.getExternalStorageDirectory(), "Movies"))
        }

        val externalDirs = context.getExternalFilesDirs(null)
        for (dir in externalDirs) {
            if (dir != null) {
                val path = dir.absolutePath
                val separatorIndex = path.indexOf("/Android/data/")
                if (separatorIndex > 0) {
                    val storageRoot = File(path.substring(0, separatorIndex))
                    if (!dirs.contains(storageRoot)) {
                        dirs.add(storageRoot)
                    }
                }
            }
        }

        dirs.add(File("/"))
        return dirs.distinctBy { it.absolutePath }
    }

    fun isStoragePermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val result = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                val writeResult = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                result == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    writeResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    fun requestManageStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
            }
        }
    }

    fun getReadableFileSize(size: Long): String {
        return FileItem.formatFileSize(size)
    }

    const val REQUEST_CODE_STORAGE_PERMISSION = 1001
}
