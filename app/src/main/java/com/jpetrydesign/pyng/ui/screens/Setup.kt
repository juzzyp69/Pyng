package com.jpetrydesign.pyng.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PageTop
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Seg
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.cardEdge
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Palettes
import com.jpetrydesign.pyng.ui.theme.swatchOf
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val SIGN_IN_URL = "https://accounts.google.com/AccountChooser?continue=https://messages.google.com/web/config"
private val NEEDED = listOf("SID", "HSID", "SSID", "APISID", "SAPISID", "OSID")
private val WANTED = NEEDED + "__Secure-1PSIDTS"

/** First-run setup: welcome, permissions, linking Google Messages and colors. */
@Composable
fun SetupScreen(startStep: Int, onDone: () -> Unit, onLog: () -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(startStep) }
    var link by remember { mutableStateOf(if (repo.paired) "done" else "idle") }
    var emoji by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var waitMsg by remember { mutableStateOf("Contacting Google Messages…") }

    LaunchedEffect(Unit) {
        repo.hub.events.collect { e ->
            when (e.kind) {
                "paired" -> { link = "done"; error = null }
                "pair_error" -> { link = "idle"; error = e.data.optString("error").replaceFirstChar { it.uppercase() } }
                "pair_retry" -> waitMsg = "Your phone is slow to answer. Trying again (${e.data.optInt("attempt") + 1} of 4)…"
            }
        }
    }

    when (link) {
        "signin" -> { SignInWeb(onBack = { link = "idle" }) { cookies ->
            link = "waiting"; waitMsg = "Contacting Google Messages…"
            scope.launch {
                repo.hub.io { it.startGooglePairing(cookies) }
                    .onSuccess { emoji = it; link = "emoji" }
                    .onFailure { error = it.message?.replaceFirstChar { c -> c.uppercase() }; link = "idle" }
            }
        }; return }
        "waiting" -> { Waiting(waitMsg); return }
        "emoji" -> { EmojiConfirm(emoji) { scope.launch { repo.hub.io { it.cancelPairing() } }; link = "idle" }; return }
        "qr" -> { QrPair(qr) { scope.launch { repo.hub.io { it.cancelPairing() } }; link = "idle" }; return }
    }

    val finish = {
        repo.prefs.edit { it.copy(setupDone = true) }
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        Glow()
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 6 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 8 } + fadeOut(tween(160)))
            },
            label = "setup",
        ) { s ->
            when (s) {
                0 -> SetupPage(Icons.message, "Messages, Your Way", "Texts, chat features and group chats in one app that you can make yours.", "Get started", { step = 1 }) {
                    Feature(0, Icons.pinned, "Your people, front and center", "Pin up to 8 favorites into your pinned chat bar")
                    Feature(1, Icons.palette, "Make it yours", "Colors, duotones, a color wheel and themes")
                    Feature(2, Icons.shield, "Private and secure", "Messages stay between your phone and Google Messages, and nothing is shared with anyone else")
                }
                1 -> Permissions { step = 2 }
                2 -> SetupPage(
                    Icons.link, "Turn On Chat Features",
                    "Typing, read receipts, group names and HD media come from RCS, borrowed from Google Messages.",
                    if (link == "done") "Continue" else "Link Google Messages",
                    { if (link == "done") step = 3 else { error = null; link = "signin" } },
                    secondary = if (link == "done") null else "Use a QR code instead",
                    onSecondary = {
                        error = null
                        scope.launch {
                            repo.hub.io { it.startQRPairing() }.onSuccess { qr = it; link = "qr" }.onFailure { error = it.message }
                        }
                    },
                    tertiary = "Debug log", onTertiary = onLog,
                ) {
                    LinkSteps()
                    Spacer(Modifier.height(14.dp))
                    if (link == "done") StatusCard(true, "Chat features connected", "RCS through Google Messages")
                    else if (error != null) StatusCard(false, "Couldn't link", error!!)
                }
                else -> StyleStep { finish() }
            }
        }
        if (step in 1..2 && !(step == 2 && link != "done")) {
            T(
                "Skip", size = 14.sp, weight = FontWeight.SemiBold, color = LocalTok.current.soft,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 18.dp, end = 26.dp)
                    .clickable { step += 1 },
            )
        }
        Dots(step, 4, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))
    }
    BackHandler(enabled = step > 0) { step -= 1 }
}

@Composable
private fun Dots(cur: Int, n: Int, modifier: Modifier) {
    val t = LocalTok.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(n) { i ->
            val w by animateDpAsState(if (i == cur) 30.dp else 8.dp, spring(dampingRatio = 0.7f), label = "dot")
            Box(Modifier.size(w, 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= cur) t.p.copy(alpha = if (i == cur) 1f else 0.5f) else t.faint.copy(alpha = 0.5f)))
        }
    }
}

@Composable
fun IconTile(icon: String, glyph: String? = null) {
    val t = LocalTok.current
    Box(
        Modifier.size(84.dp).edge(RoundedCornerShape(26.dp), primary = true, elevation = 18.dp).clip(RoundedCornerShape(26.dp)).background(t.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) T(glyph, size = 38.sp) else Ico(icon, t.onFill, 34.dp, 1.9f)
    }
}

@Composable
private fun SetupPage(
    icon: String,
    title: String,
    body: String,
    cta: String,
    onCta: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    tertiary: String? = null,
    onTertiary: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val t = LocalTok.current
    Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Box(Modifier.enter(0, pop = true)) { IconTile(icon) }
            Spacer(Modifier.height(30.dp))
            T(title, size = 30.sp, weight = FontWeight.Bold, align = TextAlign.Center, letterSpacing = (-0.8).sp, lineHeight = 35.sp, modifier = Modifier.enter(1))
            Spacer(Modifier.height(12.dp))
            T(body, size = 15.5.sp, color = t.soft, align = TextAlign.Center, lineHeight = 23.sp, modifier = Modifier.enter(2))
            Spacer(Modifier.height(30.dp))
            content()
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton(cta, onCta, Modifier.fillMaxWidth().edge(RoundedCornerShape(29.dp), primary = true), height = 58.dp)
            if (secondary != null) T(secondary, size = 14.5.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onSecondary).padding(6.dp))
            if (tertiary != null) T(tertiary, size = 13.sp, weight = FontWeight.Medium, color = t.faint, modifier = Modifier.padding(top = 2.dp).clickable(onClick = onTertiary).padding(6.dp))
        }
    }
}

@Composable
private fun Feature(i: Int, icon: String, title: String, sub: String) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp).enter(3 + i), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(icon, t.p, 19.dp) }
        Spacer(Modifier.height(10.dp))
        T(title, size = 16.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        T(sub, size = 13.5.sp, color = t.soft, align = TextAlign.Center, lineHeight = 19.sp)
    }
}

@Composable
private fun WhiteCard(content: @Composable () -> Unit) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().edge(shape, elevation = 16.dp).clip(shape).background(if (t.dark) Color(0xFF1B1E26) else Color.White),
    ) { content() }
}

@Composable
private fun LinkSteps() {
    val t = LocalTok.current
    Box(Modifier.enter(3)) {
        WhiteCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    "Keep Google Messages installed. It can stay off your home screen.",
                    "Sign in with the Google account Google Messages uses, then tap the emoji it shows.",
                    "Friends with chat features get RCS. Everyone else gets regular texts.",
                ).forEachIndexed { i, s ->
                    Row {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) {
                            T("${i + 1}", size = 13.sp, weight = FontWeight.Bold, color = t.p)
                        }
                        Spacer(Modifier.width(12.dp))
                        T(s, size = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(ok: Boolean, title: String, sub: String) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.enter(0, pop = true).edge(shape, elevation = 10.dp).clip(shape).background(if (t.dark) Color(0xFF1B1E26) else Color.White).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Color(0xFF2EB872) else t.danger))
        Spacer(Modifier.width(12.dp))
        Column {
            T(title, size = 14.5.sp, weight = FontWeight.SemiBold)
            T(sub, size = 12.5.sp, color = t.soft, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Permissions(onNext: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onNext() }
    SetupPage(
        Icons.shield, "A Few Permissions", "Only what the app needs. You can change these any time in Android settings.",
        "Allow and continue", {
            val need = missingPerms(ctx).flatMap { it.perms }
            if (need.isEmpty()) onNext() else launcher.launch(need.toTypedArray())
        },
        secondary = "Not now", onSecondary = onNext,
    ) {
        Box(Modifier.enter(3)) { WhiteCard { PermissionRows() } }
    }
}

@Composable
private fun ColorsStep(onDone: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    SetupPage(Icons.palette, "Pick Your Colors", "Change this any time, or make your own with the color wheel in Appearance.", "Start messaging", onDone) {
        Box(Modifier.enter(3)) {
            WhiteCard {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        listOf("M", "AV", "DC", "JL").forEachIndexed { i, s ->
                            if (i > 0) Spacer(Modifier.width(10.dp))
                            Avatar(null, s, 44.dp, if (i % 2 == 0) t.tone else t.neutral, if (i % 2 == 0) t.p else t.neutralInk)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(t.inBg).padding(horizontal = 14.dp, vertical = 9.dp)) { T("Dinner Friday still on?", size = 15.sp, color = t.inInk) }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(18.dp)).background(t.outBg).padding(horizontal = 14.dp, vertical = 9.dp)) { T("Yes! 7 is perfect", size = 15.sp, color = t.outInk) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (Palettes.favorites + Palettes.colors).forEach { x ->
                val on = p.themeKey == x.name
                Column(Modifier.width(66.dp).clickable { repo.prefs.edit { it.copy(themeKey = x.name) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(54.dp).then(if (on) Modifier.border(2.dp, t.ink, CircleShape).padding(4.dp) else Modifier)
                            .clip(CircleShape).background(swatchOf(x)),
                    )
                    Spacer(Modifier.height(6.dp))
                    T(x.name, size = 11.5.sp, weight = FontWeight.Medium, color = t.soft, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Seg(listOf("Light", "Dark", "Match system"), p.mode, { m -> repo.prefs.edit { it.copy(mode = m) } })
    }
}

/**
 * Choose your style: one page with a live preview up top and tabs for colors, pinned chats, bubbles,
 * send animation and app icon. Next walks through the tabs; "Customize later" skips straight in.
 */
@Composable
private fun StyleStep(onDone: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val tabs = listOf("Colors", "Pinned", "Bubbles", "Send", "Reactions", "Icon")
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            T("Choose Your Style", size = 27.sp, weight = FontWeight.Bold, letterSpacing = (-0.6).sp, modifier = Modifier.enter(0))
            T("Tap around. You can change all of it later.", size = 14.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp).enter(1))
            Spacer(Modifier.height(14.dp))
            // The preview: your home screen for pinned chats, the animation for Send, reactions for
            // Reactions, and a chat for everything else.
            Box(
                Modifier.fillMaxWidth().height(220.dp).enter(2).edge(RoundedCornerShape(26.dp), elevation = 8.dp)
                    .clip(RoundedCornerShape(26.dp)).background(if (t.dark) t.card else Color.White),
            ) {
                androidx.compose.animation.Crossfade(tabs[tab], label = "stylePreview") { name ->
                    when (name) {
                        "Pinned" -> HomeStylePreview(compact = false)
                        "Send" -> SendPreview(p.opt("sendStyle").ifBlank { "Jelly" })
                        "Reactions" -> ReactPreview()
                        else -> ChatStylePreview(compact = false)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val tabScroll = rememberScrollState()
            androidx.compose.runtime.LaunchedEffect(tab) { tabScroll.animateScrollTo(if (tab >= 3) tabScroll.maxValue else 0) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(if (t.dark) t.card else t.neutral).horizontalScroll(tabScroll).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { i, n ->
                    val on = i == tab
                    Box(Modifier.height(34.dp).clip(RoundedCornerShape(15.dp)).background(if (on) t.tone else Color.Transparent).clickable { tab = i }.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                        T(n, size = 12.5.sp, weight = FontWeight.SemiBold, color = if (on) t.p else t.soft, maxLines = 1)
                    }
                }
            }
        }
        // The open tab's choices.
        androidx.compose.animation.AnimatedContent(tab, label = "styleTab", modifier = Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (androidx.compose.animation.slideInHorizontally { it * dir / 3 } + androidx.compose.animation.fadeIn()) togetherWith (androidx.compose.animation.slideOutHorizontally { -it * dir / 3 } + androidx.compose.animation.fadeOut())
            }) { i ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                when (tabs[i]) {
                    "Colors" -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (Palettes.favorites + Palettes.colors).forEach { x ->
                                val on = p.themeKey == x.name
                                Column(Modifier.width(66.dp).clickable { repo.prefs.edit { it.copy(themeKey = x.name) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(54.dp).then(if (on) Modifier.border(2.dp, t.ink, CircleShape).padding(4.dp) else Modifier).clip(CircleShape).background(swatchOf(x)))
                                    Spacer(Modifier.height(6.dp))
                                    T(x.name, size = 11.5.sp, weight = FontWeight.Medium, color = t.soft, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Seg(listOf("Light", "Dark", "Match system"), p.mode, { m -> repo.prefs.edit { it.copy(mode = m) } })
                    }
                    "Pinned" -> {
                        SetupLabel("Layout"); StyleTiles("pinLayout") {}
                        SetupLabel("Photo shape"); StyleTiles("pinShape") {}
                        SetupLabel("New messages"); StyleTiles("pinStyle") {}
                    }
                    "Bubbles" -> { SetupLabel("Bubble shape"); StyleTiles("bubble") {}; SetupLabel("Text size"); StyleTiles("textSize") {} }
                    "Send" -> StyleTiles("sendStyle") {}
                    "Reactions" -> StyleTiles("reactStyle") {}
                    else -> StyleTiles("appIcon") {}
                }
            }
        }
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 26.dp, top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val last = tab == tabs.lastIndex
            PillButton(if (last) "Start messaging" else "Next", { if (last) onDone() else tab++ }, Modifier.fillMaxWidth().edge(RoundedCornerShape(29.dp), primary = true), height = 56.dp)
            T("Customize later", size = 14.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(top = 10.dp).clickable(onClick = onDone).padding(6.dp))
        }
    }
}

/** A small heading above a group of choices in setup. */
@Composable
private fun SetupLabel(text: String) {
    T(text, size = 13.sp, weight = FontWeight.SemiBold, color = LocalTok.current.soft, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp))
}

@Composable
private fun Waiting(text: String) {
    val t = LocalTok.current
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = t.p)
            Spacer(Modifier.height(16.dp))
            T(text, size = 15.sp, color = t.soft)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SignInWeb(onBack: () -> Unit, onCookies: (String) -> Unit) {
    var sent by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    // Google sometimes asks to use the camera to confirm it's you. Ask Android for it, then pass it to the page.
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val pendingCam = remember { arrayOfNulls<android.webkit.PermissionRequest>(1) }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pendingCam[0]?.let { r -> if (ok) r.grant(arrayOf(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else r.deny() }
        pendingCam[0] = null
    }
    Column(Modifier.fillMaxSize().background(LocalTok.current.bg)) {
        PageTop("Sign in with Google", onBack, sub = "Use the account Google Messages uses")
        Spacer(Modifier.height(12.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            factory = { ctx ->
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                    }
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                            val cam = android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            if (cam !in request.resources) { request.deny(); return }
                            if (androidx.core.content.ContextCompat.checkSelfPermission(appCtx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) request.grant(arrayOf(cam))
                            else { pendingCam[0] = request; askCam.launch(Manifest.permission.CAMERA) }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = check(url)
                        override fun onPageFinished(view: WebView?, url: String?) = check(url)
                        private fun check(url: String?) {
                            if (sent || url == null || !url.startsWith("https://messages.google.com")) return
                            cm.flush()
                            val jar = parse(cm.getCookie("https://messages.google.com/web/config")) + parse(cm.getCookie("https://accounts.google.com"))
                            if (NEEDED.all { !jar[it].isNullOrEmpty() }) {
                                sent = true
                                val o = JSONObject()
                                WANTED.forEach { k -> jar[k]?.let { o.put(k, it) } }
                                onCookies(o.toString())
                            }
                        }
                    }
                    loadUrl(SIGN_IN_URL)
                }
            },
        )
    }
}

private fun parse(raw: String?): Map<String, String> =
    raw.orEmpty().split(";").mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
    }.toMap()

@Composable
private fun EmojiConfirm(emoji: String, onCancel: () -> Unit) {
    val t = LocalTok.current
    BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.enter(0, pop = true).size(156.dp).edge(CircleShape, elevation = 18.dp).clip(CircleShape).background(if (t.dark) Color(0xFF1B1E26) else Color.White),
                contentAlignment = Alignment.Center,
            ) { T(emoji, size = 78.sp) }
            Spacer(Modifier.height(30.dp))
            T("Tap This Emoji", size = 28.sp, weight = FontWeight.Bold, align = TextAlign.Center, letterSpacing = (-0.6).sp, modifier = Modifier.enter(1))
            Spacer(Modifier.height(10.dp))
            T(
                "Google Messages is asking to link a new device. Open it from the notification or the app, and tap the emoji that matches the one above.",
                size = 15.sp, color = t.soft, align = TextAlign.Center, lineHeight = 22.sp, modifier = Modifier.enter(2).widthIn(max = 380.dp),
            )
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.enter(3)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = t.p, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                T("Waiting for your phone…", size = 14.sp, color = t.soft)
            }
            Spacer(Modifier.height(24.dp))
            T("Cancel", size = 14.5.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
        }
    }
}

@Composable
private fun QrPair(url: String, onCancel: () -> Unit) {
    val t = LocalTok.current
    val bitmap = remember(url) { qrBitmap(url, 720) }
    BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            T("Scan With Google Messages", size = 26.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            T(
                "In Google Messages: profile photo › Device pairing › QR code scanner. A phone can't scan its own screen, so open a screenshot of this on another screen.",
                size = 14.5.sp, color = t.soft, align = TextAlign.Center, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(24.dp))
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Pairing QR code",
                modifier = Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).cardEdge(RoundedCornerShape(24.dp)).padding(14.dp),
            )
            Spacer(Modifier.height(24.dp))
            PillButton("Cancel", onCancel, primary = false)
        }
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}

@Suppress("unused")
private fun Repo.ignore() = Unit
