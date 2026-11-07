package com.jpetrydesign.pyng

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import com.jpetrydesign.pyng.ui.ChatScreen
import com.jpetrydesign.pyng.ui.CrashScreen
import com.jpetrydesign.pyng.ui.EmojiScreen
import com.jpetrydesign.pyng.ui.InboxScreen
import com.jpetrydesign.pyng.ui.LogScreen
import com.jpetrydesign.pyng.ui.PyngTheme
import com.jpetrydesign.pyng.ui.QrScreen
import com.jpetrydesign.pyng.ui.SignInScreen
import com.jpetrydesign.pyng.ui.WelcomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val hub get() = (application as PyngApp).hub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PyngApp
        setContent {
            PyngTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var crash by remember { mutableStateOf(app.lastCrash()) }
                    val report = crash
                    if (report != null) {
                        CrashScreen(report) { app.clearCrash(); crash = null }
                    } else {
                        Root(hub)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { hub.io { if (it.isPaired) it.start() } }
    }
}

sealed interface Screen {
    data object Welcome : Screen
    data object SignIn : Screen
    data class Emoji(val emoji: String) : Screen
    data class Qr(val url: String) : Screen
    data object Inbox : Screen
    data class Chat(val id: String, val name: String) : Screen
    data object Log : Screen
}

@Composable
fun Root(hub: Hub) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(if (hub.engine.isPaired) Screen.Inbox else Screen.Welcome) }
    var lastHome by remember { mutableStateOf<Screen>(screen) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        hub.events.collect { e ->
            when (e.kind) {
                "paired" -> { error = null; screen = Screen.Inbox; lastHome = Screen.Inbox }
                "pair_error" -> { error = e.data.optString("error"); screen = Screen.Welcome }
                "unpaired" -> { screen = Screen.Welcome; lastHome = Screen.Welcome; e.data.optString("reason").takeIf { it.isNotBlank() }?.let { error = "Unlinked: $it" } }
            }
        }
    }

    val goBack: () -> Unit = {
        screen = when (screen) {
            is Screen.Chat, Screen.Log -> lastHome
            Screen.SignIn, is Screen.Qr, is Screen.Emoji -> { scope.launch { hub.io { it.cancelPairing() } }; Screen.Welcome }
            else -> screen
        }
    }
    BackHandler(enabled = screen !is Screen.Inbox && screen !is Screen.Welcome) { goBack() }

    when (val s = screen) {
        Screen.Welcome -> WelcomeScreen(
            error = error,
            onGoogle = { error = null; screen = Screen.SignIn },
            onQr = {
                error = null
                scope.launch {
                    hub.io { it.startQRPairing() }
                        .onSuccess { screen = Screen.Qr(it) }
                        .onFailure { error = it.message }
                }
            },
            onLog = { lastHome = Screen.Welcome; screen = Screen.Log },
        )
        Screen.SignIn -> SignInScreen(
            onBack = goBack,
            onCookies = { cookiesJson ->
                scope.launch {
                    hub.io { it.startGooglePairing(cookiesJson) }
                        .onSuccess { screen = Screen.Emoji(it) }
                        .onFailure { error = it.message; screen = Screen.Welcome }
                }
            },
        )
        is Screen.Emoji -> EmojiScreen(emoji = s.emoji, onCancel = goBack)
        is Screen.Qr -> QrScreen(url = s.url, onCancel = goBack)
        Screen.Inbox -> InboxScreen(
            hub = hub,
            onOpen = { c -> lastHome = Screen.Inbox; screen = Screen.Chat(c.id, c.name) },
            onLog = { lastHome = Screen.Inbox; screen = Screen.Log },
            onUnpair = { scope.launch { hub.io { it.unpair() } } },
        )
        is Screen.Chat -> ChatScreen(hub = hub, conversationId = s.id, name = s.name, onBack = goBack)
        Screen.Log -> LogScreen(hub = hub, onBack = goBack)
    }
}
