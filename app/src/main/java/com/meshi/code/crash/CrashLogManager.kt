package com.meshi.code.crash

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogManager {

    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_BYTES = 128 * 1024 // 128 KB

    fun install(context: Context) {
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(appContext, thread, throwable)
            } catch (_: Exception) {
                // Don't let the crash handler itself crash
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val file = logFile(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = buildString {
            appendLine("===== CRASH $timestamp =====")
            appendLine("Thread: ${thread.name}")
            appendLine(throwable.stackTraceToString())
        }
        // Append and trim if over limit
        val existing = if (file.exists()) file.readText() else ""
        val combined = existing + entry
        val trimmed = if (combined.length > MAX_BYTES) combined.takeLast(MAX_BYTES) else combined
        file.writeText(trimmed)
    }

    fun read(context: Context): String {
        val file = logFile(context)
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context) {
        logFile(context).delete()
    }

    private fun logFile(context: Context): File = File(context.filesDir, LOG_FILE)
}
