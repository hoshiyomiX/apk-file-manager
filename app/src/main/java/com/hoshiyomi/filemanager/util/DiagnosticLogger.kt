package com.hoshiyomi.filemanager.util

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe in-memory diagnostic logger with structured format.
 * Designed for APK analysis diagnostics — easy to copy/paste for AI-assisted debugging.
 *
 * Log format (human-readable + parseable):
 * ```
 * [TIMESTAMP] [LEVEL] [TAG] message
 *   key=value
 * ```
 */
object DiagnosticLogger {

    enum class Level(val label: String) {
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR"),
        DEBUG("DEBUG")
    }

    data class LogEntry(
        val timestamp: String,
        val level: Level,
        val tag: String,
        val message: String,
        val details: Map<String, Any> = emptyMap(),
        val durationMs: Long? = null
    )

    private val entries = CopyOnWriteArrayList<LogEntry>()
    private var sessionStartTime: Long = System.currentTimeMillis()

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var maxEntries: Int = 2000

    /** Start a new logging session — clears all previous entries */
    @Synchronized
    fun newSession(sessionLabel: String = "APK Analysis") {
        entries.clear()
        sessionStartTime = System.currentTimeMillis()
        info("SYSTEM", "Session started: $sessionLabel", mapOf(
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
            "app_version" to "1.0.0",
            "timestamp" to formatTimestamp(sessionStartTime)
        ))
    }

    fun info(tag: String, message: String, details: Map<String, Any> = emptyMap()) {
        log(Level.INFO, tag, message, details)
    }

    fun warn(tag: String, message: String, details: Map<String, Any> = emptyMap()) {
        log(Level.WARN, tag, message, details)
    }

    fun error(tag: String, message: String, details: Map<String, Any> = emptyMap()) {
        log(Level.ERROR, tag, message, details)
    }

    fun debug(tag: String, message: String, details: Map<String, Any> = emptyMap()) {
        log(Level.DEBUG, tag, message, details)
    }

    /** Log a timed operation with duration */
    fun timed(
        tag: String,
        message: String,
        startTimeMs: Long,
        details: Map<String, Any> = emptyMap()
    ) {
        val duration = System.currentTimeMillis() - startTimeMs
        log(Level.INFO, tag, "$message ($duration ms)", details + ("duration_ms" to duration), duration)
    }

    private fun log(level: Level, tag: String, message: String, details: Map<String, Any>, durationMs: Long? = null) {
        if (!enabled) return
        val entry = LogEntry(
            timestamp = formatTimestamp(System.currentTimeMillis()),
            level = level,
            tag = tag,
            message = message,
            details = details,
            durationMs = durationMs
        )
        entries.add(entry)
        // Trim oldest if over max
        while (entries.size > maxEntries) {
            entries.removeAt(0)
        }
    }

    fun getEntries(): List<LogEntry> = entries.toList()

    fun getEntriesByTag(tag: String): List<LogEntry> =
        entries.filter { it.tag.equals(tag, ignoreCase = true) }

    fun getEntriesByLevel(level: Level): List<LogEntry> =
        entries.filter { it.level == level }

    fun getErrorCount(): Int = entries.count { it.level == Level.ERROR }
    fun getWarnCount(): Int = entries.count { it.level == Level.WARN }

    fun clear() {
        entries.clear()
    }

    fun isEnabled(): Boolean = enabled
    fun setEnabled(enabled: Boolean) { this.enabled = enabled }

    /** Export all logs as structured text — optimized for copy/paste and AI diagnosis */
    fun exportAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("╔════════════════════════════════════════════════════════════╗")
        sb.appendLine("║  APK Manager — Diagnostic Log                           ║")
        sb.appendLine("║  ${formatTimestamp(sessionStartTime).padEnd(54)}║")
        sb.appendLine("╚════════════════════════════════════════════════════════════╝")
        sb.appendLine()

        val errorCount = getErrorCount()
        val warnCount = getWarnCount()
        sb.appendLine("Summary: ${entries.size} entries | $errorCount errors | $warnCount warnings | ${entries.size - errorCount - warnCount} info")
        sb.appendLine("─".repeat(64))

        for (entry in entries) {
            val levelBadge = when (entry.level) {
                Level.ERROR -> "[ERROR]"
                Level.WARN  -> "[WARN] "
                Level.DEBUG -> "[DEBUG]"
                Level.INFO  -> "[INFO] "
            }
            sb.append("[${entry.timestamp}] ")
            sb.append("$levelBadge ")
            sb.append("[${entry.tag}] ")
            sb.appendLine(entry.message)

            if (entry.details.isNotEmpty()) {
                for ((key, value) in entry.details) {
                    val displayValue = when (value) {
                        is List<*> -> value.joinToString(", ")
                        else -> value.toString()
                    }
                    sb.appendLine("  ├ $key = $displayValue")
                }
            }
            if (entry.durationMs != null) {
                sb.appendLine("  └ elapsed: ${entry.durationMs}ms")
            }
        }

        sb.appendLine()
        sb.appendLine("─".repeat(64))
        sb.appendLine("End of diagnostic log (${entries.size} entries)")

        return sb.toString()
    }

    /** Export as compact key=value format — ideal for AI chat pasting */
    fun exportAsCompact(): String {
        val sb = StringBuilder()
        sb.appendLine("--- APK DIAGNOSTIC LOG ---")

        // Header
        sb.appendLine("generated=${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("android=API${Build.VERSION.SDK_INT}")
        sb.appendLine("total_entries=${entries.size}")
        sb.appendLine("errors=${getErrorCount()}")
        sb.appendLine("warnings=${getWarnCount()}")
        sb.appendLine()

        for (entry in entries) {
            sb.append("[${entry.level.label}] ${entry.tag}: ${entry.message}")
            if (entry.details.isNotEmpty()) {
                sb.append(" | ")
                sb.append(entry.details.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /** Export a structured APK analysis summary — the most useful format for AI diagnosis */
    fun buildApkDiagnosticSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("═══ APK ANALYSIS REPORT ═══")

        for (entry in entries) {
            when (entry.tag) {
                "APK-PKG" -> {
                    if (entry.details.isNotEmpty()) {
                        sb.appendLine("Package: ${entry.details["package"] ?: "unknown"}")
                        sb.appendLine("Version: ${entry.details["version_name"] ?: "?"} (${entry.details["version_code"] ?: "?"})")
                        sb.appendLine("Min SDK: ${entry.details["min_sdk"] ?: "?"} | Target SDK: ${entry.details["target_sdk"] ?: "?"}")
                        sb.appendLine("App Name: ${entry.details["app_name"] ?: "?"}")
                        sb.appendLine("Debuggable: ${entry.details["debuggable"] ?: "?"}")
                        sb.appendLine("File Size: ${entry.details["file_size"] ?: "?"}")
                        sb.appendLine("SHA-256: ${entry.details["sha256"] ?: "?"}")
                        sb.appendLine()
                    }
                }
                "APK-CERT" -> {
                    sb.append("Certificate: ")
                    if (entry.details.isNotEmpty()) {
                        sb.appendLine("${entry.details["issuer"] ?: "?"}")
                        sb.appendLine("  Subject: ${entry.details["subject"] ?: "?"}")
                        sb.appendLine("  Algorithm: ${entry.details["algorithm"] ?: "?"}")
                        sb.appendLine("  Valid: ${entry.details["valid_from"] ?: "?"} → ${entry.details["valid_to"] ?: "?"}")
                        sb.appendLine("  SHA-256: ${entry.details["sha256"] ?: "?"}")
                        sb.appendLine("  SHA-1: ${entry.details["sha1"] ?: "?"}")
                    } else {
                        sb.appendLine(entry.message)
                    }
                    sb.appendLine()
                }
                "APK-PERM" -> {
                    val perms = entry.details["permissions"] as? List<*>
                    if (perms != null) {
                        sb.appendLine("Permissions (${perms.size}):")
                        perms.forEach { sb.appendLine("  - $it") }
                        sb.appendLine()
                    }
                }
                "APK-COMP" -> {
                    if (entry.details.isNotEmpty()) {
                        val activities = entry.details["activities"] as? Int ?: 0
                        val services = entry.details["services"] as? Int ?: 0
                        val receivers = entry.details["receivers"] as? Int ?: 0
                        val providers = entry.details["providers"] as? Int ?: 0
                        sb.appendLine("Components: ${activities}A / ${services}S / ${receivers}R / ${providers}P")
                        sb.appendLine()
                    }
                }
                "APK-MANIFEST" -> {
                    sb.appendLine("Manifest: ${entry.message}")
                    sb.appendLine()
                }
                "APK-ERROR" -> {
                    sb.appendLine("[ERROR] ${entry.message}")
                    if (entry.details.isNotEmpty()) {
                        for ((k, v) in entry.details) {
                            sb.appendLine("  $k = $v")
                        }
                    }
                    sb.appendLine()
                }
                "APK-FILES" -> {
                    val totalFiles = entry.details["total_files"] as? Int ?: 0
                    val totalSize = entry.details["total_size"] as? String ?: "?"
                    sb.appendLine("Archive Contents: $totalFiles files, $totalSize")
                    sb.appendLine()
                }
            }
        }

        // Add any errors/warnings at the end
        val errors = entries.filter { it.level == Level.ERROR }
        if (errors.isNotEmpty()) {
            sb.appendLine("═══ ERRORS (${errors.size}) ═══")
            for (e in errors) {
                sb.appendLine("[${e.tag}] ${e.message}")
            }
        }

        val warnings = entries.filter { it.level == Level.WARN }
        if (warnings.isNotEmpty()) {
            sb.appendLine("═══ WARNINGS (${warnings.size}) ═══")
            for (w in warnings) {
                sb.appendLine("[${w.tag}] ${w.message}")
            }
        }

        return sb.toString()
    }

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))
    }
}
