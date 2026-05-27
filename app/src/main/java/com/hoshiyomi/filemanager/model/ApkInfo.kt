package com.hoshiyomi.filemanager.model

import java.io.File

data class ApkInfo(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val appName: String = "",
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val isDebuggable: Boolean = false,
    val sharedUserId: String? = null,
    val fileSha256: String = "",
    val certificateInfo: CertificateInfo? = null,
    val file: File? = null,
    val fileSize: Long = 0,
    val manifestXml: String = ""
)

data class CertificateInfo(
    val issuer: String = "",
    val subject: String = "",
    val serialNumber: String = "",
    val validFrom: String = "",
    val validTo: String = "",
    val algorithm: String = "",
    val sha256Fingerprint: String = "",
    val sha1Fingerprint: String = "",
    val md5Fingerprint: String = ""
)
