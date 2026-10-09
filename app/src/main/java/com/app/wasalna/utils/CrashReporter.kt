package com.app.wasalna.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 10.8.0a: lightweight local-only crash reporting (no SDK, no network).
 * - Installs a global uncaught-exception handler chaining the previous one.
 * - Persists the stack trace + device snapshot to internal storage.
 * - Share via ACTION_SEND (share sheet) from Settings dev section.
 * All file I/O runs off the crashing thread via a dedicated writer thread —
 * the crashing thread waits (bounded) so the log lands before the kill.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR = "crash-logs"
    private const val MAX_FILES = 10

    fun install(appContext: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
                .onFailure { Log.e(TAG, "writeCrash failed: ${it.message}") }
            previous?.uncaughtException(thread, throwable)
        }
        // Prune old logs on every start (bounded storage, internal only).
        runCatching { prune(appContext) }
    }

    fun latestFile(appContext: Context): File? =
        crashDir(appContext)
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()

    fun shareLatest(context: Context) {
        val file = latestFile(context) ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wasalna crash log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share crash log"))
    }

    private fun writeCrash(appContext: Context, thread: Thread, throwable: Throwable) {
        val writer = Thread {
            runCatching {
                val dir = crashDir(appContext).apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "crash-$stamp.txt")
                file.writeText(buildReport(appContext, thread, throwable))
                prune(appContext)
            }
        }
        writer.start()
        // Bounded wait (2s) — never block the kill indefinitely.
        runCatching { writer.join(2_000L) }
    }

    private fun buildReport(appContext: Context, thread: Thread, throwable: Throwable): String =
        buildString {
            appendLine("Wasalna crash — ${Date()}")
            appendLine("Thread: ${thread.name}")
            appendLine("App: ${appContext.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("OS: ${Build.VERSION.RELEASE}")
            appendLine()
            appendLine(throwable.stackTraceToString())
            throwable.cause?.let { appendLine("Caused by: ${it.stackTraceToString()}") }
        }

    private fun crashDir(appContext: Context): File = File(appContext.filesDir, DIR)

    private fun prune(appContext: Context) {
        val files = crashDir(appContext).listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_FILES) {
            files.take(files.size - MAX_FILES).forEach { runCatching { it.delete() } }
        }
    }
}
