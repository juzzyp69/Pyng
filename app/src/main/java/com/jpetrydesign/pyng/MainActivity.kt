package com.jpetrydesign.pyng

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jpetrydesign.pyng.data.LinkService
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.PyngRoot
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.rememberTok
import com.jpetrydesign.pyng.ui.screens.CrashScreen
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app get() = application as PyngApp
    private lateinit var nav: Nav
    private var locked by mutableStateOf(false)
    private var lastStop = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = app.repo
        val demo = intent?.getBooleanExtra("demo", false) == true
        if (demo) setupDemo(intent)
        val p = repo.prefs.state
        val start: Route = when {
            demo -> demoRoute(intent.getStringExtra("route"))
            !repo.paired -> Route.Setup(if (p.setupDone) 2 else 0)
            !p.setupDone -> Route.Setup(3)
            else -> Route.Home
        }
        nav = Nav(start)
        locked = p.on("bioLock") && !demo
        handle(intent)
        setContent {
            var crash by remember { mutableStateOf(app.lastCrash()) }
            val t = rememberTok(repo)
            LaunchedEffect(repo.prefs.state.on("hideSwitcher")) {
                if (repo.prefs.state.on("hideSwitcher")) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            CompositionLocalProvider(LocalTok provides t, LocalRepo provides repo) {
                val report = crash
                when {
                    report != null -> CrashScreen(report) { app.clearCrash(); crash = null }
                    locked -> LockScreen { unlock() }
                    else -> PyngRoot(repo, nav)
                }
            }
        }
    }

    /** Sample data and settings for automated screenshots (adb only). */
    private fun setupDemo(i: Intent) {
        val repo = app.repo
        if (i.getBooleanExtra("promo", false)) repo.loadPromo(java.io.File(getExternalFilesDir(null), "promo")) else repo.loadDemo()
        repo.demoOpen = i.getStringExtra("open")
        repo.prefs.edit { pr ->
            var x = pr.copy(toggles = pr.toggles + ("bioLock" to false))
            i.getStringExtra("mode")?.let { x = x.copy(mode = it) }
            i.getStringExtra("layout")?.let { x = x.copy(pinLayout = it) }
            i.getStringExtra("text")?.let { x = x.copy(textSize = it) }
            i.getStringExtra("theme")?.let { x = x.copy(themeKey = it) }
            i.getStringExtra("bubble")?.let { x = x.copy(bubble = it) }
            i.getStringExtra("wall")?.let { x = x.copy(defWall = it) }
            i.getStringExtra("pinstyle")?.let { x = x.copy(pinStyle = it) }
            x
        }
    }

    private fun demoRoute(r: String?): Route {
        val (k, arg) = (r ?: "home").split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (k) {
            "thread" -> Route.Thread(arg.ifEmpty { "c1" })
            "search" -> Route.Search
            "new" -> Route.NewChat()
            "newgroup" -> Route.NewChat(true)
            "contact" -> Route.Contact(arg.ifEmpty { "c1" })
            "theme" -> Route.ChatTheme(arg.ifEmpty { "c1" })
            "settings" -> Route.Settings
            "section" -> Route.Section(arg)
            "appearance" -> Route.Appearance
            "profile" -> Route.Profile
            "react" -> Route.ReactPick
            "lists" -> Route.Lists(arg)
            "setup" -> Route.Setup(arg.toIntOrNull() ?: 0)
            "log" -> Route.Log
            else -> Route.Home
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == "new" && app.repo.paired) { nav.go(Route.NewChat()); return }
        val chat = i?.getStringExtra("chat") ?: return
        if (app.repo.paired && app.repo.prefs.state.setupDone) nav.openChat(chat)
    }

    override fun onStart() {
        super.onStart()
        val repo = app.repo
        repo.foreground = true
        if (repo.prefs.state.on("bioLock") && System.currentTimeMillis() - lastStop > 60_000 && lastStop > 0) locked = true
        if (locked) unlock()
        if (!repo.demo) com.jpetrydesign.pyng.data.Link.foreground(app)
    }

    override fun onStop() {
        super.onStop()
        app.repo.foreground = false
        lastStop = System.currentTimeMillis()
        if (!app.repo.demo) com.jpetrydesign.pyng.data.Link.background(app)
    }

    private fun unlock() {
        val bm = BiometricManager.from(this)
        val kinds = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (bm.canAuthenticate(kinds) != BiometricManager.BIOMETRIC_SUCCESS) { locked = false; return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Messages").setAllowedAuthenticators(kinds).build())
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val t = LocalTok.current
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.shield, t.p, 36.dp, 1.8f) }
            Spacer(Modifier.height(16.dp))
            T("Messages is locked", size = 20.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            PillButton("Unlock", onUnlock)
        }
    }
}
