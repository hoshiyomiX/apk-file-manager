package com.hoshiyomi.filemanager.util

import android.app.Application
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

class Application : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemeManager.initTheme(this)
        BookmarkManager.init(this)
        initDiagnosticLogging()
        registerGlobalExceptionHandler()
    }

    private fun initDiagnosticLogging() {
        DiagnosticLogger.newSession("XD Manager started")
        DiagnosticLogger.info("FM-SYSTEM", "App initialized", mapOf(
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
            "app_version" to "1.0.0"
        ))
    }

    private fun registerGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticLogger.error("FM-CRASH", "Uncaught exception on thread: ${thread.name}", mapOf(
                "exception" to throwable.javaClass.simpleName,
                "message" to (throwable.message ?: "no message"),
                "stacktrace" to throwable.stackTraceToString()
            ))
            // Write crash to a file so it survives process death
            try {
                val crashFile = filesDir.resolve("last_crash.log")
                crashFile.writeText(buildCrashReport(throwable))
            } catch (_: Exception) { }
            // Let system handle the crash
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashReport(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return """
═══ CRASH REPORT ═══
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android: API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
App: XD Manager v1.0.0
Thread: ${Thread.currentThread().name}
Exception: ${throwable.javaClass.simpleName}: ${throwable.message}

${sw.toString()}
═══ END CRASH REPORT ═══
        """.trimIndent()
    }

    companion object {
        lateinit var instance: Application
            private set
    }
}
