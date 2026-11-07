package com.jpetrydesign.pyng

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.jpetrydesign.pyng.data.Notifier
import com.jpetrydesign.pyng.data.Prefs
import com.jpetrydesign.pyng.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class PyngApp : Application(), ImageLoaderFactory {
    lateinit var hub: Hub
        private set
    lateinit var repo: Repo
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
                        "Version: ${BuildConfig.VERSION_NAME}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, error)
        }
        Notifier.channels(this)
        hub = Hub(filesDir.resolve("engine").absolutePath)
        repo = Repo(this, hub, Prefs(this), appScope)
        repo.onIncoming = { conv, msg -> Notifier.show(this, repo, conv, msg) }
        repo.onRead = { id -> Notifier.cancel(this, id) }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            add(VideoFrameDecoder.Factory())
        }
        .crossfade(true)
        .build()

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
