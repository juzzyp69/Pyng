package com.jpetrydesign.pyng

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class PyngApp : Application() {
    lateinit var hub: Hub
        private set

    val crashFile: File get() = filesDir.resolve("crash-app.txt")

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                crashFile.writeText(
                    "App crash on thread ${thread.name}\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, error)
        }
        hub = Hub(filesDir.resolve("engine").absolutePath)
    }

    /** A crash report from the last run (app or engine), or null. */
    fun lastCrash(): String? {
        val app = runCatching { crashFile.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
        val engine = runCatching { hub.engine.lastCrash() }.getOrNull().orEmpty()
        val text = listOf(app, engine).filter { it.isNotBlank() }.joinToString("\n\n---- engine ----\n\n")
        return text.ifBlank { null }
    }

    fun clearCrash() {
        crashFile.delete()
        runCatching { hub.engine.clearCrash() }
    }
}
