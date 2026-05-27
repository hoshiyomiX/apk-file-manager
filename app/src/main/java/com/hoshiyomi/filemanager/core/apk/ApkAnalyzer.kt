package com.hoshiyomi.filemanager.core.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.hoshiyomi.filemanager.core.file.FileOperations
import com.hoshiyomi.filemanager.model.ApkInfo
import com.hoshiyomi.filemanager.model.CertificateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)

object ApkAnalyzer {

    suspend fun analyzeApk(context: Context, apkFile: File): Result<ApkInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!apkFile.exists() || !apkFile.isFile) {
                    throw IllegalArgumentException("APK file not found: ${apkFile.absolutePath}")
                }
                val packageInfo = getPackageInfoFromApk(context, apkFile)
                val manifestXml = parseManifest(apkFile).getOrDefault("")
                val permissions = extractPermissions(apkFile)
                val certificateInfo = extractCertificateInfo(apkFile)
                val sha256 = FileOperations.computeFileHash(apkFile, "SHA-256")

                val appName = try {
                    packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""
                } catch (e: Exception) {
                    ""
                }

                ApkInfo(
                    packageName = packageInfo?.packageName ?: "",
                    versionName = packageInfo?.versionName ?: "",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo?.longVersionCode ?: 0L
                    } else {
                        @Suppress("DEPRECATION")
                        (packageInfo?.versionCode?.toLong() ?: 0L)
                    },
                    minSdkVersion = extractMinSdk(packageInfo),
                    targetSdkVersion = packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
                    appName = appName,
                    permissions = permissions,
                    activities = extractComponentNames(packageInfo, "activity"),
                    services = extractComponentNames(packageInfo, "service"),
                    receivers = extractComponentNames(packageInfo, "receiver"),
                    providers = extractComponentNames(packageInfo, "provider"),
                    features = extractFeatures(packageInfo),
                    isDebuggable = (packageInfo?.applicationInfo?.flags
                        ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
                    sharedUserId = packageInfo?.sharedUserId,
                    fileSha256 = sha256,
                    certificateInfo = certificateInfo,
                    file = apkFile,
                    fileSize = apkFile.length(),
                    manifestXml = manifestXml
                )
            }
        }

    private fun getPackageInfoFromApk(context: Context, apkFile: File): PackageInfo? {
        val pm = context.packageManager
        return try {
            val archivePath = apkFile.absolutePath
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    archivePath,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_PERMISSIONS.toLong() or
                        PackageManager.GET_ACTIVITIES.toLong() or
                        PackageManager.GET_SERVICES.toLong() or
                        PackageManager.GET_RECEIVERS.toLong() or
                        PackageManager.GET_PROVIDERS.toLong() or
                        PackageManager.GET_SHARED_LIBRARY_FILES.toLong() or
                        PackageManager.GET_SIGNATURES.toLong() or
                        PackageManager.GET_CONFIGURATIONS.toLong() or
                        PackageManager.GET_META_DATA.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    archivePath,
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_SHARED_LIBRARY_FILES or
                    PackageManager.GET_SIGNATURES or
                    PackageManager.GET_CONFIGURATIONS or
                    PackageManager.GET_META_DATA
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMinSdk(packageInfo: PackageInfo?): Int {
        if (packageInfo == null) return 0
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val appInfo = packageInfo.applicationInfo
                val field = appInfo.javaClass.getDeclaredField("minSdkVersion")
                field.isAccessible = true
                field.getInt(appInfo)
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    private fun extractComponentNames(packageInfo: PackageInfo?, componentType: String): List<String> {
        if (packageInfo == null) return emptyList()
        return when (componentType) {
            "activity" -> packageInfo.activities?.mapNotNull { it.name } ?: emptyList()
            "service" -> packageInfo.services?.mapNotNull { it.name } ?: emptyList()
            "receiver" -> packageInfo.receivers?.mapNotNull { it.name } ?: emptyList()
            "provider" -> packageInfo.providers?.mapNotNull { it.name } ?: emptyList()
            else -> emptyList()
        }
    }

    private fun extractFeatures(packageInfo: PackageInfo?): List<String> {
        if (packageInfo == null) return emptyList()
        return packageInfo.reqFeatures?.mapNotNull { it.name } ?: emptyList()
    }

    suspend fun parseManifest(apkFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!apkFile.exists()) {
                    throw IllegalArgumentException("APK not found")
                }
                val rawManifest = extractRawManifest(apkFile)
                rawManifest
            }
        }

    private fun extractRawManifest(apkFile: File): String {
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return "AndroidManifest.xml not found in APK"
            val bytes = zip.getInputStream(entry).readBytes()
            return try {
                decodeBinaryXml(bytes)
            } catch (e: Exception) {
                "Binary AndroidManifest.xml detected. Size: ${bytes.size} bytes. " +
                    "Hex (first 100 bytes): ${bytes.take(100).joinToString(" ") { "%02x".format(it) }}"
            }
        }
    }

    private fun decodeBinaryXml(bytes: ByteArray): String {
        val sb = StringBuilder()
        var offset = 0

        if (bytes.size < 8) return "File too small to be a valid manifest"

        val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        if (magic == 0x00080003.toUInt().toInt()) {
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            offset = 8
        } else {
            offset = 0
        }

        val stringPool = mutableListOf<String>()
        offset = readStringPool(bytes, offset, stringPool)

        val resourceIds = mutableListOf<Int>()
        if (offset < bytes.size) {
            val chunkType = readShort(bytes, offset)
            if (chunkType == 0x0180) {
                offset = readResourceIdPool(bytes, offset, resourceIds)
            }
        }

        while (offset < bytes.size - 4) {
            val eventType = readShort(bytes, offset)
            when (eventType) {
                0x0102 -> {
                    val nameIdx = readInt(bytes, offset + 16)
                    val startTag = if (nameIdx < stringPool.size) stringPool[nameIdx] else "???"
                    sb.append("<$startTag")
                    val attrCount = readShort(bytes, offset + 20).toInt() and 0xFFFF
                    val attrStart = offset + 36
                    for (i in 0 until attrCount) {
                        val attrOffset = attrStart + i * 20
                        if (attrOffset + 20 <= bytes.size) {
                            val attrNameIdx = readInt(bytes, attrOffset)
                            val attrValueIdx = readInt(bytes, attrOffset + 8)
                            val attrName = if (attrNameIdx < stringPool.size) stringPool[attrNameIdx] else "attr_$attrNameIdx"
                            val attrValue = if (attrValueIdx >= 0 && attrValueIdx < stringPool.size) {
                                stringPool[attrValueIdx]
                            } else {
                                val raw = readInt(bytes, attrOffset + 8)
                                if (raw > 0 && raw < resourceIds.size) "res/0x${resourceIds[raw].toString(16)}"
                                else ""
                            }
                            if (attrName.startsWith("android:") || attrName == "package" ||
                                attrName == "versionCode" || attrName == "versionName" ||
                                attrName == "minSdkVersion" || attrName == "targetSdkVersion"
                            ) {
                                sb.append("\n    $attrName=\"$attrValue\"")
                            }
                        }
                    }
                    sb.append(">\n")
                    offset += readShort(bytes, offset + 20).toInt() and 0xFFFF
                }
                0x0103 -> {
                    val nameIdx = readInt(bytes, offset + 16)
                    val endTag = if (nameIdx < stringPool.size) stringPool[nameIdx] else "???"
                    sb.append("</$endTag>\n")
                    offset += readShort(bytes, offset + 20).toInt() and 0xFFFF
                }
                0x0101 -> {
                    offset += readShort(bytes, offset + 20).toInt() and 0xFFFF
                }
                else -> {
                    offset += 4
                }
            }
        }

        return sb.toString()
    }

    private fun readStringPool(bytes: ByteArray, offset: Int, pool: MutableList<String>): Int {
        if (offset + 20 > bytes.size) return offset
        val chunkSize = readInt(bytes, offset + 4)
        val stringCount = readInt(bytes, offset + 8)
        val styleCount = readInt(bytes, offset + 12)
        val flags = readInt(bytes, offset + 16)
        val isUtf8 = (flags and (1 shl 8)) != 0

        var stringOffset = offset + 20 + (if (stringCount > 0) stringCount * 4 else 0) + (if (styleCount > 0) styleCount * 4 else 0)

        for (i in 0 until stringCount) {
            if (stringOffset >= bytes.size) break
            if (isUtf8) {
                val strOffset = readInt(bytes, offset + 20 + i * 4)
                val pos = stringOffset + strOffset
                if (pos < bytes.size) {
                    val len = readUshort(bytes, pos)
                    val byteLen = if ((len and 0x8000) != 0) {
                        ((len and 0x7FFF) shl 8) or readUshort(bytes, pos + 2)
                    } else {
                        len
                    }
                    var start = pos + if ((len and 0x8000) != 0) 4 else 2
                    if (start < bytes.size && bytes[start].toInt() == 0) start++
                    val end = (start + byteLen).coerceAtMost(bytes.size)
                    pool.add(String(bytes, start, end - start, Charsets.UTF_8))
                } else {
                    pool.add("")
                }
            } else {
                val strOffset = readInt(bytes, offset + 20 + i * 4)
                val pos = stringOffset + strOffset
                if (pos + 4 <= bytes.size) {
                    val len = readShort(bytes, pos)
                    val charStart = pos + 4
                    if (charStart + len * 2 <= bytes.size) {
                        val chars = CharArray(len)
                        for (j in 0 until len) {
                            chars[j] = (bytes[charStart + j * 2].toInt() and 0xFF).toChar()
                        }
                        pool.add(String(chars))
                    } else {
                        pool.add("")
                    }
                } else {
                    pool.add("")
                }
            }
        }

        return offset + chunkSize
    }

    private fun readResourceIdPool(bytes: ByteArray, offset: Int, pool: MutableList<Int>): Int {
        if (offset + 8 > bytes.size) return offset
        val chunkSize = readInt(bytes, offset + 4)
        val count = chunkSize / 4 - 2
        for (i in 0 until count) {
            val pos = offset + 8 + i * 4
            if (pos + 4 <= bytes.size) {
                pool.add(readInt(bytes, pos))
            }
        }
        return offset + chunkSize
    }

    private fun readShort(bytes: ByteArray, offset: Int): Short {
        return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun readUshort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    suspend fun extractPermissions(apkFile: File): List<String> =
        withContext(Dispatchers.IO) {
            val permissions = mutableListOf<String>()
            try {
                ZipFile(apkFile).use { zip ->
                    val entry = zip.getEntry("AndroidManifest.xml") ?: return@withContext emptyList()
                    val bytes = zip.getInputStream(entry).readBytes()
                    val permPattern = Regex("android\\.permission\\.[A-Z_]+")
                    val text = String(bytes, Charsets.ISO_8859_1)
                    val found = permPattern.findAll(text).map { it.value }.toSet()
                    permissions.addAll(found)
                }
            } catch (_: Exception) {
            }
            permissions.distinct().sorted()
        }

    suspend fun extractCertificateInfo(apkFile: File): CertificateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val certEntryName = findCertificateEntry(apkFile) ?: return@withContext null
                ZipFile(apkFile).use { zip ->
                    val entry = zip.getEntry(certEntryName) ?: return@withContext null
                    val certBytes = zip.getInputStream(entry).readBytes()
                    parseCertificate(certBytes)
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun findCertificateEntry(apkFile: File): String? {
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.uppercase()
                if (name.startsWith("META-INF/") &&
                    (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))
                ) {
                    return entry.name
                }
            }
        }
        return null
    }

    private fun parseCertificate(certBytes: ByteArray): CertificateInfo {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val bais = java.io.ByteArrayInputStream(certBytes)
            val certs = cf.generateCertificates(bais)
            val cert = certs.firstOrNull() as? java.security.cert.X509Certificate ?: return CertificateInfo()

            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded).joinToString(":") { "%02X".format(it) }
            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(cert.encoded).joinToString(":") { "%02X".format(it) }
            val md5 = MessageDigest.getInstance("MD5")
                .digest(cert.encoded).joinToString(":") { "%02X".format(it) }

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

            CertificateInfo(
                issuer = cert.issuerX500Principal?.name ?: "",
                subject = cert.subjectX500Principal?.name ?: "",
                serialNumber = cert.serialNumber?.toString(16)?.uppercase() ?: "",
                validFrom = dateFormat.format(cert.notBefore),
                validTo = dateFormat.format(cert.notAfter),
                algorithm = cert.sigAlgName ?: "",
                sha256Fingerprint = sha256,
                sha1Fingerprint = sha1,
                md5Fingerprint = md5
            )
        } catch (e: Exception) {
            CertificateInfo()
        }
    }

    suspend fun getApkIcon(context: Context, apkFile: File): Drawable? =
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                }
                packageInfo?.applicationInfo?.let { info ->
                    info.sourceDir = apkFile.absolutePath
                    info.publicSourceDir = apkFile.absolutePath
                    info.loadIcon(pm)
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun listApkContents(apkFile: File): List<ZipEntryInfo> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<ZipEntryInfo>()
            try {
                ZipFile(apkFile).use { zip ->
                    val zipEntries = zip.entries()
                    while (zipEntries.hasMoreElements()) {
                        val entry = zipEntries.nextElement()
                        entries.add(
                            ZipEntryInfo(
                                name = entry.name,
                                size = entry.size,
                                compressedSize = entry.compressedSize,
                                isDirectory = entry.isDirectory,
                                lastModified = entry.time
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            entries.sortedWith(compareByDescending<ZipEntryInfo> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

    suspend fun extractFile(apkFile: File, entryName: String, destDir: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                ZipFile(apkFile).use { zip ->
                    val entry = zip.getEntry(entryName)
                        ?: throw FileNotFoundException("Entry not found: $entryName")
                    val outputFile = File(destDir, entryName.substringAfterLast('/'))
                    zip.getInputStream(entry).buffered().use { input ->
                        outputFile.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                    outputFile
                }
            }
        }
}
