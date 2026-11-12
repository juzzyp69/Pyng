package com.jpetrydesign.pyng.ui

import dev.chrisbanes.haze.hazeSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.data.Toast
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.ToastHost
import com.jpetrydesign.pyng.ui.screens.AppearanceScreen
import com.jpetrydesign.pyng.ui.screens.ChatThemeScreen
import com.jpetrydesign.pyng.ui.screens.ContactScreen
import com.jpetrydesign.pyng.ui.screens.HomeScreen
import com.jpetrydesign.pyng.ui.screens.ListScreen
import com.jpetrydesign.pyng.ui.screens.LogScreen
import com.jpetrydesign.pyng.ui.screens.NewChatScreen
import com.jpetrydesign.pyng.ui.screens.ShareScreen
import com.jpetrydesign.pyng.ui.screens.ProfileScreen
import com.jpetrydesign.pyng.ui.screens.ReactPickScreen
import com.jpetrydesign.pyng.ui.screens.SearchScreen
import com.jpetrydesign.pyng.ui.screens.SectionScreen
import com.jpetrydesign.pyng.ui.screens.SettingsScreen
import com.jpetrydesign.pyng.ui.screens.SetupScreen
import com.jpetrydesign.pyng.ui.screens.ThreadScreen
import com.jpetrydesign.pyng.ui.screens.ViewerScreen
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Palettes
import com.jpetrydesign.pyng.ui.theme.Tok
import com.jpetrydesign.pyng.ui.theme.tok
import kotlinx.coroutines.delay

sealed interface Route {
    data class Setup(val step: Int = 0) : Route
    data object Home : Route
    data object Search : Route
    data class NewChat(val group: Boolean = false) : Route
    data object Share : Route
    data class Thread(val id: String, val highlight: String? = null, val search: Boolean = false) : Route
    data class Contact(val id: String) : Route
    data class ChatTheme(val id: String) : Route
    data object Settings : Route
    data class Section(val key: String) : Route
    data object Appearance : Route
    data object Profile : Route
    data object ReactPick : Route
    data class Lists(val kind: String) : Route
    data class Viewer(val path: String, val mime: String) : Route
    data object Log : Route
}

/** The app's navigation, for the few deep places that need to open a chat (a shared contact's Message button). */
val LocalNav = androidx.compose.runtime.staticCompositionLocalOf<Nav?> { null }

/** The hold-to-peek preview. It's drawn above every pane, so on a Fold it centers over the whole screen. */
class PeekState {
    var conv by mutableStateOf<com.jpetrydesign.pyng.data.Conv?>(null)
    var onEditPins: (() -> Unit)? = null
}

val LocalPeek = androidx.compose.runtime.staticCompositionLocalOf { PeekState() }

/** A simple back stack with direction, so transitions slide the right way. */
class Nav(start: Route) {
    val stack = mutableStateListOf(start)
    var forward by mutableStateOf(true)
        private set
    val top get() = stack.last()

    /** The app color strip shown over Home for a live preview. */
    var strip by mutableStateOf(false)
    private var stripReturn: List<Route>? = null

    /** Shows the color strip over Home, remembering where you came from. */
    fun openStrip() { stripReturn = stack.toList(); strip = true; home() }

    /** Done with the color strip: back to the page you opened it from (Appearance), not Home. */
    fun closeStrip() {
        strip = false
        val back = stripReturn ?: return
        stripReturn = null
        if (back.size > 1) { forward = true; stack.clear(); stack.addAll(back) }
    }

    fun go(r: Route) { forward = true; stack.add(r) }
    fun replace(r: Route) { forward = true; stack.clear(); stack.add(r) }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }
    fun home() { forward = false; while (stack.size > 1) stack.removeAt(stack.lastIndex); if (stack.first() != Route.Home) { stack.clear(); stack.add(Route.Home) } }

    /** Swaps the open chat for another one, sliding in from the side you swiped toward. */
    fun swapChat(id: String, next: Boolean) {
        forward = next
        while (stack.size > 1 && stack.last() !is Route.Home) stack.removeAt(stack.lastIndex)
        stack.add(Route.Thread(id))
    }

    /** Opens a chat, replacing any chat already open, so back goes to the list. */
    fun openChat(id: String, highlight: String? = null) {
        forward = true
        while (stack.size > 1 && stack.last() !is Route.Home) stack.removeAt(stack.lastIndex)
        stack.add(Route.Thread(id, highlight))
    }
}

@Composable
fun rememberTok(repo: Repo): Tok {
    val p = repo.prefs.state
    val sysDark = isSystemInDarkTheme()
    val dark = p.mode == "Dark" || (p.mode == "Match system" && sysDark)
    val pal = Palettes.byKey(p.themeKey, p.c1, p.c2)
    return remember(pal, dark, p.on("reduceBlur"), p.on("highContrast")) { tok(pal, dark, p.on("reduceBlur"), p.on("highContrast")) }
}

@Composable
fun PyngRoot(repo: Repo, nav: Nav) {
    // Font choice is read before anything draws, so text never flashes in the wrong typeface.
    val fontPick = repo.prefs.state.opt("font")
    remember(fontPick) { com.jpetrydesign.pyng.ui.theme.AppFont.pick = fontPick; fontPick }
    val t = rememberTok(repo)
    // Motion follows both Pyng's own "Reduce motion" and Android's "Remove animations".
    val actx = androidx.compose.ui.platform.LocalContext.current
    val systemAnim = remember { runCatching { android.provider.Settings.Global.getFloat(actx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f }
    val motion = !repo.prefs.state.on("reduceMotion") && systemAnim
    val scheme = if (t.dark) darkColorScheme(primary = t.p, background = t.bg, surface = t.bg, onBackground = t.ink, onSurface = t.ink)
    else lightColorScheme(primary = t.p, background = t.bg, surface = t.bg, onBackground = t.ink, onSurface = t.ink)
    var toast by remember { mutableStateOf<Toast?>(null) }
    var banner by remember { mutableStateOf<Pair<com.jpetrydesign.pyng.data.Conv, com.jpetrydesign.pyng.data.Msg>?>(null) }
    LaunchedEffect(Unit) { repo.banners.collect { banner = it } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(repo.prefs.state.pinned, repo.convs.size, repo.convs.values.maxOfOrNull { it.ts }) { com.jpetrydesign.pyng.ui.screens.updateShortcuts(ctx, repo) }
    LaunchedEffect(Unit) {
        repo.toasts.collect {
            toast = it
        }
    }
    LaunchedEffect(toast) {
        val cur = toast ?: return@LaunchedEffect
        delay(if (cur.undo != null) 4500 else 2200)
        if (toast == cur) toast = null
    }
    LaunchedEffect(repo.paired) {
        if (!repo.paired && nav.top !is Route.Setup && nav.top !is Route.Log) nav.replace(Route.Setup(2))
    }
    // Status bar and gesture bar icons follow the app's light or dark look, not the phone's.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !t.dark; isAppearanceLightNavigationBars = !t.dark }
        }
    }
    MaterialTheme(colorScheme = scheme) {
        // Text size from Appearance scales every bit of text in the app, on top of Android's own setting.
        val baseDensity = androidx.compose.ui.platform.LocalDensity.current
        val textScale = com.jpetrydesign.pyng.ui.kit.textScaleOf(repo.prefs.state.textSize)
        val scaled = androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * textScale)
        val peek = remember { PeekState() }
        CompositionLocalProvider(LocalTok provides t, LocalRepo provides repo, LocalMotion provides motion, androidx.compose.ui.platform.LocalDensity provides scaled, LocalNav provides nav, LocalPeek provides peek) {
            // Everything on screen feeds the in-app banner's frosted glass, but only while a banner
            // is up (and a moment after, while it slides away), so normal scrolling costs nothing extra.
            val appHaze = remember { dev.chrisbanes.haze.HazeState() }
            var hazeOn by remember { mutableStateOf(false) }
            LaunchedEffect(banner != null) { if (banner != null) hazeOn = true else { delay(450); hazeOn = false } }
            BoxWithConstraints(Modifier.fillMaxSize().background(t.bg)) {
                val wide = maxWidth >= 600.dp
                val fullW = maxWidth
                // While peeking, everything behind sinks back a little and blurs, so the preview reads clearly.
                val peeking = peek.conv != null
                val peekBlur by androidx.compose.animation.core.animateDpAsState(if (peeking) 30.dp else 0.dp, tween(260), label = "peekBlur")
                val peekSink by androidx.compose.animation.core.animateFloatAsState(
                    if (peeking) 0.94f else 1f,
                    if (motion) androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 380f) else tween(0), label = "peekSink",
                )
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = peekSink; scaleY = peekSink }
                        .then(if (peekBlur > 0.5.dp) Modifier.blur(peekBlur) else Modifier)
                        .then(if (hazeOn) Modifier.hazeSource(appHaze) else Modifier),
                ) {
                    if (wide) WideLayout(nav, fullW) else Pane(nav, Modifier.fillMaxSize(), wide = false)
                }
                com.jpetrydesign.pyng.ui.screens.RowMenu(peek.conv, onDismiss = { peek.conv = null }, onEditPins = peek.onEditPins)
                ToastHost(toast, onUndo = { toast?.undo?.invoke(); toast = null }, Modifier.align(Alignment.BottomCenter))
                com.jpetrydesign.pyng.ui.kit.AskDialog(repo.ask) { repo.ask = null }
                com.jpetrydesign.pyng.ui.screens.PhotoCropper(repo.crop) { repo.crop = null }
                Box(Modifier.align(Alignment.TopCenter)) {
                    CompositionLocalProvider(com.jpetrydesign.pyng.ui.kit.LocalHaze provides appHaze) {
                    com.jpetrydesign.pyng.ui.screens.InAppBanner(banner, onOpen = { id -> banner = null; nav.openChat(id) }, onClose = { banner = null })
                    }
                }
            }
        }
    }
}

private fun transition(forward: Boolean, motion: Boolean): ContentTransform =
    if (!motion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    else if (forward) (slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 380f)) { it / 5 } + fadeIn(tween(220))) togetherWith
        (slideOutHorizontally(tween(220)) { -it / 10 } + fadeOut(tween(180)))
    else (slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 380f)) { -it / 10 } + fadeIn(tween(220))) togetherWith
        (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(160)))

/** Renders the top of the back stack with a slide in the right direction. */
@Composable
private fun Pane(nav: Nav, modifier: Modifier, wide: Boolean) {
    val motion = LocalMotion.current
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    // Going to another screen puts the keyboard away. A screen that wants it (like New message) asks again.
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(nav.top) { focus.clearFocus(); kb?.hide() }
    AnimatedContent(nav.top, modifier, transitionSpec = { transition(nav.forward, motion) }, label = "nav") { r ->
        Screen(r, nav, wide)
    }
}

/** Fold inner screen and tablets: the conversation list stays on the left. */
@Composable
private fun WideLayout(nav: Nav, width: androidx.compose.ui.unit.Dp) {
    val t = LocalTok.current
    val motion = LocalMotion.current
    val top = nav.top
    val fullScreen = top is Route.Setup || top is Route.Viewer || top is Route.Log
    if (fullScreen) { Pane(nav, Modifier.fillMaxSize(), wide = true); return }
    val leftW = if (width > 900.dp) 420.dp else 360.dp
    val leftRoutes = setOf(Route.Home::class, Route.Settings::class, Route.Section::class, Route.Appearance::class, Route.Profile::class, Route.ReactPick::class, Route.Lists::class, Route.Search::class, Route.NewChat::class, Route.Share::class)
    val leftTop = nav.stack.lastOrNull { it::class in leftRoutes } ?: Route.Home
    val rightTop = nav.stack.lastOrNull { it !is Route.Home && it::class !in leftRoutes }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(leftW).fillMaxHeight()) {
            AnimatedContent(leftTop, transitionSpec = { transition(nav.forward, motion) }, label = "left") { r -> Screen(r, nav, true) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(t.hair))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(rightTop, transitionSpec = { transition(nav.forward, motion) }, label = "right") { r ->
                if (r == null) WideEmpty() else Screen(r, nav, true)
            }
        }
    }
}

@Composable
private fun WideEmpty() {
    val t = LocalTok.current
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.message, t.p, 36.dp, 1.7f) }
            T("Pick a conversation", size = 18.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            T("Your chats open here, side by side with the list.", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Screen(r: Route, nav: Nav, wide: Boolean) {
    when (r) {
        is Route.Setup -> SetupScreen(r.step, onDone = { nav.replace(Route.Home) }, onLog = { nav.go(Route.Log) })
        Route.Home -> HomeScreen(nav, wide)
        Route.Search -> SearchScreen(nav)
        is Route.NewChat -> NewChatScreen(nav, r.group)
        Route.Share -> ShareScreen(nav)
        is Route.Thread -> ThreadScreen(nav, r.id, r.highlight, r.search, wide)
        is Route.Contact -> ContactScreen(nav, r.id)
        is Route.ChatTheme -> ChatThemeScreen(nav, r.id)
        Route.Settings -> SettingsScreen(nav)
        is Route.Section -> SectionScreen(nav, r.key)
        Route.Appearance -> AppearanceScreen(nav)
        Route.Profile -> ProfileScreen(nav)
        Route.ReactPick -> ReactPickScreen(nav)
        is Route.Lists -> ListScreen(nav, r.kind)
        is Route.Viewer -> ViewerScreen(nav, r.path, r.mime)
        Route.Log -> LogScreen(nav)
    }
}
