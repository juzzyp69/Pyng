package com.jpetrydesign.pyng.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.data.Pending
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalHaze
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Provide
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.TypingDots
import com.jpetrydesign.pyng.ui.kit.Wallpaper
import com.jpetrydesign.pyng.ui.kit.swipeDir
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.kit.frost
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Palettes
import com.jpetrydesign.pyng.ui.theme.withCustom
import com.jpetrydesign.pyng.ui.theme.Tok
import com.jpetrydesign.pyng.ui.theme.tok
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * A name color for each person in a group, from the chat's theme. One-color themes use shades of
 * that color (lighter, darker, a touch warmer or cooler); duotone themes blend between the two.
 * Spread by the golden ratio so neighbours in the list never look alike, and kept readable.
 */
fun groupNameColor(t: Tok, index: Int): Color {
    val f = ((index.coerceAtLeast(0) * 0.618034f) + 0.15f) % 1f
    val two = t.pal.plus
    val src = if (two != null) androidx.compose.ui.graphics.lerp(t.p, two, f) else t.p
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(src.toArgb(), hsl)
    val (lo, hi) = if (t.dark) 0.62f to 0.80f else 0.30f to 0.46f
    if (two == null) {
        hsl[0] = (hsl[0] + (f - 0.5f) * 32f + 360f) % 360f
        hsl[2] = lo + (hi - lo) * f
    } else hsl[2] = hsl[2].coerceIn(lo, hi)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.9f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** The palette a chat uses: its own look if it has one, otherwise the app's. */
@Composable
fun chatTok(convId: String): Tok {
    val repo = LocalRepo.current
    val app = LocalTok.current
    val p = repo.prefs.state
    val look = p.looks[convId]
    if (look == null || (look.key.isEmpty() && look.custom.isEmpty())) return app
    val base = if (look.key.isEmpty()) app else {
        val pal = if (look.key == "chat-custom") Palettes.chatCustom(look) else Palettes.byKey(look.key, p.c1, p.c2)
        remember(pal, app.dark, app.solid) { tok(pal, app.dark, app.solid, p.on("highContrast")) }
    }
    return remember(base, look.custom) { base.withCustom(look.custom) }
}

fun openFile(ctx: Context, path: String, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

@Composable
fun ThreadScreen(nav: Nav, convId: String, highlight: String?, startSearch: Boolean, wide: Boolean) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val conv = repo.convs[convId]
    val t = chatTok(convId)
    val p = repo.prefs.state
    val scope = rememberCoroutineScope()
    val haze = remember { HazeState() }
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val motion = LocalMotion.current

    var draft by remember(convId) { mutableStateOf(p.drafts[convId].orEmpty()) }
    var replyTo by remember(convId) { mutableStateOf<Msg?>(null) }
    val attachments = remember(convId) { mutableStateListOf<Pending>() }
    // Something shared from another app lands in the message box, ready to send.
    LaunchedEffect(convId) {
        repo.pendingShare?.let { sh ->
            if (sh.text.isNotBlank()) draft = if (draft.isBlank()) sh.text else draft.trimEnd() + " " + sh.text
            attachments.addAll(sh.files)
            repo.pendingShare = null
        }
    }
    var info by remember(convId) { mutableStateOf<String?>(null) }
    var menuFor by remember(convId) { mutableStateOf<Msg?>(null) }
    var detailsFor by remember(convId) { mutableStateOf<Msg?>(null) }
    var forward by remember(convId) { mutableStateOf<Msg?>(null) }
    // Forward all: the whole set of photos sent together (the sheet shows the first).
    var forwardAll by remember(convId) { mutableStateOf<List<Msg>?>(null) }
    var selectTextFor by remember(convId) { mutableStateOf<Msg?>(null) }
    val selected = remember(convId) { mutableStateListOf<String>() }
    var searching by remember(convId) { mutableStateOf(startSearch) }
    var query by remember(convId) { mutableStateOf("") }
    var matchIdx by remember(convId) { mutableIntStateOf(0) }
    var hl by remember(convId) { mutableStateOf(highlight) }
    var burst by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fx by remember { mutableStateOf<String?>(null) }
    var tsShift by remember { mutableStateOf(0f) }
    var bottomH by remember { mutableStateOf(0) }
    var headH by remember { mutableStateOf(0) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var schedule by remember(convId) { mutableStateOf<Pair<Long, String>?>(null) }
    val openedAt = remember(convId) { System.currentTimeMillis() }
    var callSheet by remember { mutableStateOf(false) }
    // Where each bubble sits, so the long-press screen can keep the message in place.
    val bubbleCoords = remember(convId) { HashMap<String, androidx.compose.ui.layout.LayoutCoordinates>() }
    val rowCtxs = remember(convId) { HashMap<String, RowCtx>() }
    val rootCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val sendFx = remember(convId) { SendFx() }
    var menuAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // The photo open in the viewer, as (message key, attachment id).
    var viewing by remember(convId) { mutableStateOf<Pair<String, String>?>(null) }
    fun view(m: Msg, ref: com.jpetrydesign.pyng.data.MediaRef) {
        if ((ref.isImage || ref.isVideo) && !ref.waiting) { viewing = m.key to ref.id; info = null } else openMedia(ctx, repo, nav, ref)
    }
    fun openMenu(m: Msg) {
        menuAnchor = runCatching {
            val r = rootCoords[0]; val b = bubbleCoords[m.key]
            if (r != null && b != null && b.isAttached) r.localBoundingBoxOf(b) else null
        }.getOrNull()
        menuFor = m
    }
    // Screenshot hooks: open a panel, menu or status line straight away.
    val demoOpen = remember { repo.demoOpen.also { repo.demoOpen = null } }
    val demoPanel = demoOpen?.takeIf { it in setOf("plus", "expr", "gifs", "stickers", "schedule", "maker") }
    LaunchedEffect(demoOpen, repo.msgs[convId]?.size) {
        val list = repo.msgs[convId].orEmpty()
        when (demoOpen) {
            "menu" -> if (menuFor == null) list.lastOrNull { !it.fromMe }?.let { delay(600); openMenu(it) }
            "search" -> searching = true
            "search-q" -> { searching = true; delay(600); query = "the" }
            "menu-strip" -> if (menuFor == null) list.lastOrNull { mediaBundles(list).of.containsKey(it.key) }?.let { delay(900); openMenu(it) }
            "menu-link" -> if (menuFor == null) list.lastOrNull { linksIn(it.text).isNotEmpty() }?.let { delay(900); openMenu(it) }
            "info" -> info = list.lastOrNull { it.fromMe }?.key
            "ts" -> tsShift = -with(density) { 76.dp.toPx() }
            "reply" -> replyTo = list.lastOrNull { !it.fromMe }
            "details" -> detailsFor = list.lastOrNull()
            "viewer" -> list.firstOrNull { m -> m.media.any { it.isImage } }?.let { m -> delay(700); viewing = m.key to m.media.first { it.isImage }.id }
            "viewer-video" -> list.firstOrNull { m -> m.media.any { it.isVideo } }?.let { m -> delay(700); viewing = m.key to m.media.first { it.isVideo }.id }
            "viewer-strip" -> list.lastOrNull { m -> m.text.isBlank() && m.media.any { it.isImage } }?.let { m -> delay(700); viewing = m.key to m.media.first().id }
        }
    }

    LaunchedEffect(convId) {
        repo.activeChat = convId
        com.jpetrydesign.pyng.data.Notifier.cancel(ctx, convId)
        // Always ask for the latest page: what's cached may only be texts that arrived since Pyng was set up,
        // and this is what turns on scrolling back through the full history.
        repo.loadMessages(convId)
        repo.markRead(convId)
        val last = repo.msgs[convId]?.lastOrNull { !it.fromMe }
        if (conv?.unread == true && last != null) fxKind(last.text)?.let { delay(420); fx = it }
    }
    androidx.compose.runtime.DisposableEffect(convId) {
        onDispose {
            if (repo.activeChat == convId) repo.activeChat = null
            Voice.stop()
        }
    }
    // Save the draft when leaving.
    androidx.compose.runtime.DisposableEffect(convId) {
        onDispose { val d = draft; repo.prefs.edit { if (d.isBlank()) it.copy(drafts = it.drafts - convId) else it.copy(drafts = it.drafts + (convId to d)) } }
    }

    // Messages from Google Messages, plus your scheduled ones shown faded at the end.
    // Truly empty ones are skipped; a picture still on its way shows as a placeholder instead.
    val msgs = repo.msgs[convId].orEmpty().filter { it.visible } + p.scheduled.filter { it.convId == convId }.sortedBy { it.at }.map { s ->
        Msg("", "sched-" + s.id, convId, "", "", "", true, "OUTGOING_SCHEDULED", s.label, s.text, emptyList(), s.at, emptyList(), "", 0, "text")
    }
    val loaded = repo.msgs.containsKey(convId)
    val typing = (repo.typing[convId] ?: 0L) > System.currentTimeMillis() && p.on("typing")
    val myId = conv?.me?.id.orEmpty()
    val byId = remember(msgs) { msgs.associateBy { it.id } }
    // Photos and videos sent together (or back to back) sit in one swipeable strip.
    val bundles = remember(msgs) { mediaBundles(msgs) }

    // New messages arriving while open: mark read, fire effects.
    LaunchedEffect(msgs.size) {
        val last = msgs.lastOrNull() ?: return@LaunchedEffect
        if (last.ts >= openedAt - 2000) {
            if (!last.fromMe) repo.markRead(convId)
            if (p.on("effects") && motion) fxKind(last.text)?.let { fx = it }
        }
    }
    // A picture still on its way: keep checking until it lands.
    val waitingMedia = msgs.any { it.awaitingMedia }
    LaunchedEffect(convId, waitingMedia) { if (waitingMedia) repo.watchPending(convId) }
    LaunchedEffect(fx) { if (fx != null) { delay(3800); fx = null } }
    LaunchedEffect(burst) { if (burst != null) { delay(900); burst = null } }

    val q = query.trim().lowercase()
    // Search shows its own page (shortcuts, then a list of matches) until you pick a match; then
    // the chat shows with that message highlighted and the arrows step through the rest.
    var showResults by remember(convId) { mutableStateOf(true) }
    val panelOn = searching && (q.isEmpty() || showResults)
    var olderPages by remember(convId) { mutableStateOf(0) }
    val pageCap = if (q.isNotEmpty()) 40 else 8
    val lookingFurther = searching && repo.hasMore[convId] == true && olderPages < pageCap
    LaunchedEffect(searching, q.isNotEmpty(), msgs.size) {
        if (lookingFurther) { delay(250); olderPages++; repo.loadMessages(convId, older = true) }
    }
    val matches = if (searching && q.isNotEmpty()) msgs.filter { it.text.lowercase().contains(q) }.map { it.key } else emptyList()
    val curMatch = if (matches.isNotEmpty()) matches[(matches.size - 1 - (matchIdx % matches.size)).coerceIn(0, matches.size - 1)] else null
    val reversed = msgs.asReversed()
    // Your newest message always shows its status underneath (unless Message status is Off).
    val newestMine = remember(msgs) { msgs.lastOrNull { it.fromMe && !it.isSys && it.status != "OUTGOING_SCHEDULED" }?.key }
    val statusStyle = p.opt("statusStyle").ifEmpty { "Icon and text" }
    val rowOf = remember(reversed) { HashMap<String, Int>().also { m -> reversed.forEachIndexed { i, x -> m[x.key] = i } } }
    // A message you send keeps the same row when Google confirms it (its id changes, its temporary
    // id doesn't), so it never disappears and fades back in.
    val listKeys = remember(reversed) {
        val k = reversed.map { m -> if (m.fromMe && m.tmpId.isNotEmpty()) "t:" + m.tmpId else "k:" + m.key }
        if (k.toSet().size == k.size) k else reversed.map { "k:" + it.key }
    }
    // The newest messages rise in, staggered, the first time a chat opens.
    val introPlayed = remember(convId) { HashSet<String>() }
    val introKeys = remember(convId, loaded) { if (loaded) reversed.take(10).map { it.key }.toSet() else emptySet() }
    // Stay on the newest message: when the chat opens, and whenever new ones load or arrive,
    // unless you've scrolled up to read older ones yourself.
    var stick by remember(convId) { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { moving -> if (!moving) stick = list.firstVisibleItemIndex <= 1 }
    }
    val newestKey = reversed.firstOrNull()?.key
    var opened by remember(convId) { mutableStateOf(false) }
    LaunchedEffect(newestKey, typing) {
        if (newestKey == null || hl != null && !opened) { if (newestKey != null) opened = true; return@LaunchedEffect }
        if (!opened) { list.scrollToItem(0); opened = true }
        else if (stick) list.animateScrollToItem(0)
    }
    // When the suggested replies (or anything else above the box) appear, keep the newest message in view above them.
    LaunchedEffect(bottomH) {
        if (opened && stick && sendFx.run == null && list.firstVisibleItemIndex <= 1) list.animateScrollToItem(0)
    }
    // Opened with a pile of unread texts: a pill up top takes you to the first one.
    val wasUnread = remember(convId) { conv?.unread == true || convId in p.markedUnread }
    var unreadAtOpen by remember(convId) { mutableStateOf(-1) }
    LaunchedEffect(loaded, msgs.size) {
        if (loaded && unreadAtOpen < 0 && msgs.isNotEmpty()) unreadAtOpen = if (wasUnread) msgs.takeLastWhile { !it.fromMe && !it.isSys }.size else 0
    }
    val typingRows = if (typing) 1 else 0
    val firstUnreadRow = unreadAtOpen - 1 + typingRows
    var unreadSeen by remember(convId) { mutableStateOf(false) }
    val unreadOffscreen by remember(convId) {
        derivedStateOf { unreadAtOpen >= 3 && (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < firstUnreadRow }
    }
    LaunchedEffect(unreadOffscreen, opened) { if (opened && unreadAtOpen >= 3 && !unreadOffscreen) unreadSeen = true }
    fun jumpToUnread() {
        unreadSeen = true
        scope.launch {
            list.scrollToItem(firstUnreadRow.coerceAtLeast(0))
            // Lift it toward the top so the messages after it are in view too.
            list.animateScrollBy(-(list.layoutInfo.viewportSize.height * 0.62f))
        }
    }
    // New texts while you're scrolled up: the down button counts them.
    var missed by remember(convId) { mutableStateOf(0) }
    LaunchedEffect(newestKey) {
        val newest = msgs.lastOrNull()
        if (opened && !stick && newest != null && !newest.fromMe && newest.ts >= openedAt - 2000) missed++
    }
    val atBottom by remember { derivedStateOf { list.firstVisibleItemIndex <= 1 } }
    LaunchedEffect(atBottom) { if (atBottom) missed = 0 }
    LaunchedEffect(curMatch, hl) {
        val k = curMatch ?: hl ?: return@LaunchedEffect
        val i = reversed.indexOfFirst { it.key == k || it.id == k }
        if (i >= 0) list.animateScrollToItem(i + 1)
    }

    // Pinch the chat to make bubbles bigger or smaller; it sticks for every chat.
    var zoom by remember { mutableStateOf(p.opt("chatZoom").toFloatOrNull() ?: 1f) }
    val fontSize = 16f * zoom
    val shape = when (p.bubble) { "Soft" -> 14.dp to 4.dp; "Pill" -> 22.dp to 22.dp; else -> 20.dp to 6.dp }
    val wall = p.looks[convId]?.wall?.ifEmpty { null } ?: p.defWall
    val selecting = selected.isNotEmpty()
    val unknownBanner = conv != null && !conv.known && !conv.group && !conv.business && convId !in p.dismissedUnknown && msgs.any { !it.fromMe }
    val spamWhy = if (conv != null && p.on("spam") && convId !in p.notSpam) spamCheck(conv, msgs.lastOrNull { !it.fromMe }?.text.orEmpty()) else null

    fun send(text: String, bigOk: Boolean = false) {
        if (conv == null) return
        // Over RCS's size limit: say so before sending, since it will most likely fail and can tip the chat into texting.
        if (!bigOk && attachments.any { java.io.File(it.path).length() > MAX_RCS_BYTES }) {
            repo.confirm(
                "This file is too big for RCS",
                "RCS chats can send up to 100 MB. Google Messages may try sending it as an MMS instead, but MMS only allows about 1 MB, so it probably won't go through. The chat may also switch to texting for a bit.",
                action = "Send anyway", danger = false,
                onCancel = { attachments.removeAll { java.io.File(it.path).length() > MAX_RCS_BYTES } },
            ) { send(text, bigOk = true) }
            return
        }
        val sch = schedule
        if (sch != null && text.isNotBlank()) {
            val item = com.jpetrydesign.pyng.data.Scheduled(java.util.UUID.randomUUID().toString(), convId, text.trim(), sch.first, sch.second)
            repo.prefs.edit { it.copy(scheduled = it.scheduled + item, drafts = it.drafts - convId) }
            com.jpetrydesign.pyng.data.ScheduleWorker.plan(ctx, item)
            repo.toast("Scheduled for ${sch.second}")
            schedule = null; draft = ""; return
        }
        // The send glide: note where the box and send button are before the text leaves them.
        val root = rootCoords[0]
        val melt = if (motion && attachments.isEmpty() && replyTo == null && text.isNotBlank() && root != null && sendFx.run == null) runCatching {
            Triple(root.localBoundingBoxOf(sendFx.field!!), root.localBoundingBoxOf(sendFx.text!!), root.localBoundingBoxOf(sendFx.button!!))
        }.getOrNull() else null
        repo.send(convId, text, replyTo?.id.orEmpty(), attachments.toList())
        if (melt != null) repo.msgs[convId]?.lastOrNull { it.status == "LOCAL" && it.text == text.trim() }?.tmpId?.let { tmp ->
            sendFx.start(MeltRun(tmp, text.trim(), melt.first, melt.second, melt.third))
        }
        if (p.on("effects")) fxKind(text)?.let { fx = it }
        draft = ""; replyTo = null; attachments.clear(); info = null
        scope.launch { list.animateScrollToItem(0) }
    }
    // Sample mode: type and send a message, to check the send animation end to end.
    LaunchedEffect(demoOpen) {
        if (demoOpen != "send") return@LaunchedEffect
        delay(2500); draft = "On my way!"; delay(1500); send(draft)
    }

    val blur by androidx.compose.animation.core.animateDpAsState(if (menuFor != null) 18.dp else if (viewing != null) 22.dp else 0.dp, label = "blur")
    // The viewer swipes through the strip you tapped, or just the one photo. Everything is on the contact page.
    val viewSet = viewing?.let { (k, _) -> bundles.of[k] ?: msgs.firstOrNull { it.key == k }?.let { m -> m.media.map { m to it } } }.orEmpty()
        .filter { (it.second.isImage || it.second.isVideo) && !it.second.waiting }
    Provide(t) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSendFx provides sendFx) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootCoords[0] = it }) {
            Box(Modifier.fillMaxSize().then(if (blur > 0.5.dp) Modifier.blur(blur) else Modifier)) {
            Wallpaper(wall, t)
            androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze) {
                val topPad = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 96.dp
                LazyColumn(
                    state = list,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().hazeSource(haze)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var changed = false
                                do {
                                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    if (e.changes.count { it.pressed } >= 2) {
                                        val z = e.calculateZoom()
                                        if (z != 1f) { zoom = (zoom * z).coerceIn(0.8f, 1.7f); changed = true }
                                        e.changes.forEach { it.consume() }
                                    }
                                } while (e.changes.any { it.pressed })
                                if (changed) repo.prefs.setOpt("chatZoom", "%.2f".format(java.util.Locale.US, zoom))
                            }
                        }
                        .swipeDir(convId, -1,
                            onDrag = { d -> tsShift = (tsShift + d * 0.7f).coerceIn(-with(density) { 76.dp.toPx() }, 0f) },
                            onEnd = { scope.launch { val a = Animatable(tsShift); a.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f)) { tsShift = value } } },
                        ),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPad, bottom = with(density) { bottomH.toDp() } + 12.dp),
                ) {
                    if (typing) item("typing") {
                        Box(Modifier.padding(top = 10.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(t.inBg).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            TypingDots(t.soft, 6.dp, 4.dp)
                        }
                    }
                    itemsIndexed(reversed, key = { i, m -> listKeys.getOrElse(i) { m.key } }) { i, m0 ->
                        // The rest of a photo strip is drawn in its first message's row.
                        if (m0.key in bundles.hidden) return@itemsIndexed
                        val bundle = bundles.byAnchor[m0.key]
                        val last = if (bundle != null) bundles.newest[m0.key] ?: m0 else m0
                        val failedOne = bundle?.firstOrNull { it.first.failed }?.first
                        // A strip shows its newest message's status, and any reactions on its own photos.
                        val m = if (bundle == null) m0 else m0.copy(reactions = emptyList(), status = failedOne?.status ?: last.status, error = failedOne?.error ?: "")
                        // i counts from newest; neighbours in time order are i+1 (older) and i-1 (newer).
                        val older = reversed.getOrNull(i + 1)
                        val newer = reversed.getOrNull((rowOf[last.key] ?: i) - 1)
                        val gap = older == null || m.ts - older.ts > 60 * 60_000L || dayOf(m.ts) != dayOf(older.ts)
                        fun same(a: Msg?, b: Msg) = a != null && !a.isSys && !b.isSys && a.fromMe == b.fromMe && a.participantId == b.participantId && kotlin.math.abs(a.ts - b.ts) < 10 * 60_000L
                        val prevSame = !gap && same(older, m)
                        val newerGap = newer != null && (newer.ts - last.ts > 60 * 60_000L || dayOf(newer.ts) != dayOf(last.ts))
                        val nextSame = !newerGap && same(newer, last)
                        // Older messages glide up to make room instead of jumping.
                        Column(if (motion) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(dampingRatio = 0.9f, stiffness = 380f)) else Modifier) {
                            if (gap && m.ts > 0) T(stamp(m.ts), size = 12.sp, weight = FontWeight.Medium, color = t.soft, modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                            if (m.isSys) {
                                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                                    T(m.text.ifEmpty { sysText(m.status) }, size = 12.sp, weight = FontWeight.Medium, color = t.soft, align = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(t.neutral).padding(horizontal = 14.dp, vertical = 6.dp))
                                }
                            } else {
                                val inf = if (info == m.key || p.opt("stamps") == "Every message" || m.status == "OUTGOING_SCHEDULED") infoFor(m, conv, true) else null
                                val det = if (!m.fromMe && spamWhy == null) detect(m.text) else Detected()
                                val chips = buildList<Triple<String, String, () -> Unit>> {
                                    det.date?.let { d -> add(Triple("Add to calendar", Icons.cal) { runCatching { ctx.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, m.text.take(60)).putExtra(CalendarContract.Events.DESCRIPTION, d)) } }) }
                                    det.addr?.let { ad -> add(Triple("Open in Maps", Icons.pin) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(ad)))) } }) }
                                    det.track?.let { tr -> add(Triple("Track package", Icons.box) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ups.com/track?tracknum=$tr"))) } }) }
                                    det.code?.let { code -> add(Triple("Copy $code", Icons.copy) { copy(ctx, code); repo.toast("Code copied") }) }
                                }
                                val quote = m.replyTo.takeIf { it.isNotEmpty() }?.let { rid ->
                                    val r = byId[rid]
                                    if (r == null) "Reply" to "Original message" else (if (r.fromMe) "You" else r.sender.ifBlank { conv?.shortTitle ?: "" }) to msgPreview(r)
                                }
                                val sender = if (conv?.group == true && !m.fromMe) conv.people.firstOrNull { it.id == m.participantId } else null
                                val rc = RowCtx(
                                        t = t, group = conv?.group == true, prevSame = prevSame, nextSame = nextSame, fontSize = fontSize, shape = shape,
                                        info = inf?.first, infoIcon = inf?.second ?: Icons.check, infoInk = if (inf?.third == true) t.p else t.soft,
                                        selecting = selecting, selected = m.key in selected || bundle?.any { it.first.key in selected } == true, highlighted = curMatch == m.key || hl == m.id,
                                        quote = quote, senderName = if (conv?.group == true && !m.fromMe && !prevSame) (sender?.short ?: m.senderFirst.ifBlank { m.sender }) else null,
                                        senderWho = sender?.let { whoOf(it) }, starred = p.isStarred(m.key), chips = if (m.fromMe) emptyList() else chips,
                                        showPreview = p.on("previews") && spamWhy == null, tsShift = tsShift, timeLabel = clock(m.ts),
                                        enterDelay = if (i < 10 && m.key in introKeys && introPlayed.add(m.key)) (80 + i * 45) else -1,
                                        isNew = m.ts > openedAt && !sendFx.arrived(m), burst = burst?.takeIf { b -> b.first == m.key || bundle?.any { it.first.key == b.first } == true }?.second, myId = myId,
                                        nameColor = if (conv?.group == true && !m.fromMe) groupNameColor(t, conv.others.indexOfFirst { it.id == m.participantId }) else null,
                                        bundle = bundle,
                                        autoStatus = if (statusStyle != "Off" && inf == null && last.key == newestMine && !m.failed) infoFor(m, conv, true) else null,
                                        statusFace = if (statusStyle == "Face" && conv != null && !conv.group) conv.others.firstOrNull()?.let { whoOf(it) } else null,
                                        failLabel = if (!m.failed) null else if (m.status == "OUTGOING_FAILED_TOO_LARGE" || (bundle?.map { it.second } ?: m.media).any { it.size > MAX_RCS_BYTES })
                                            "Not sent · too large to send" else infoFor(m, conv, false).first + " · Tap to retry",
                                        kindTag = if (conv?.rcs == true && (m.isSms || m.isMms)) (if (m.fromMe) "Sent as " else "Received as ") + (if (m.isMms) "MMS" else "SMS") else null,
                                    )
                                rowCtxs[m.key] = rc
                                // Holding one photo in a strip opens the menu for just that photo.
                                bundle?.forEach { (bm, _) -> rowCtxs[bm.key] = rc.copy(bundle = null, prevSame = false, nextSame = false, info = null, chips = emptyList()) }
                                MessageRow(
                                    m, rc,
                                    RowActs(
                                        onSide = { if (selecting) toggle(selected, m.key) else info = if (info == m.key) null else m.key },
                                        onTap = {
                                            if (selecting) toggle(selected, m.key)
                                            else m.media.firstOrNull()?.let { view(m, it) }
                                        },
                                        onDouble = { if (m.id.isNotEmpty()) { repo.react(m, "❤️"); burst = m.key to "❤️" } },
                                        onLong = { if (!selecting) { openMenu(m); info = null } },
                                        onReply = { replyTo = m },
                                        onOpenMedia = { ref -> if (selecting) toggle(selected, m.key) else view(m, ref) },
                                        onRetry = { if (bundle == null) repo.retry(m) else bundle.map { it.first }.distinctBy { it.key }.filter { it.failed }.forEach { repo.retry(it) } },
                                        onPlaced = { bubbleCoords[m.key] = it },
                                        onOpenItem = { bm, r -> if (selecting) toggle(selected, bm.key) else view(bm, r) },
                                        onLongItem = { bm -> if (!selecting) { openMenu(bm); info = null } },
                                        onDoubleItem = { bm -> if (bm.id.isNotEmpty()) { repo.react(bm, "❤️"); burst = bm.key to "❤️" } },
                                        onPlacedKey = { k, c -> bubbleCoords[k] = c },
                                    ),
                                )
                            }
                        }
                    }
                    item("top") {
                        Column(Modifier.fillMaxWidth()) {
                            if (spamWhy != null) SpamBanner(spamWhy, t, onReport = { repo.setBlocked(convId, true, true); repo.toast("Reported and blocked"); nav.back() }, onNot = { repo.prefs.edit { it.copy(notSpam = it.notSpam + convId) } })
                            else if (unknownBanner) UnknownBanner(t, conv,
                                onDismiss = { repo.prefs.edit { it.copy(dismissedUnknown = it.dismissedUnknown + convId) } },
                                onAdd = { runCatching { ctx.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, conv?.number)) } },
                                onSpam = { repo.setBlocked(convId, true, true); repo.toast("Reported and blocked"); nav.back() })
                            if (loaded && msgs.isEmpty() && conv != null) EmptyThread(conv, t) { send("👋") }
                            // Reaching the top loads the next older page, and keeps going while the top stays in view.
                            if (repo.hasMore[convId] == true) {
                                val oldestKey = msgs.firstOrNull { it.id.isNotEmpty() }?.id
                                LaunchedEffect(oldestKey) { repo.loadMessages(convId, older = true) }
                                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator(color = t.soft, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (!loaded) Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                SkeletonBubbles(t)
                                // Still waiting after a while: Google Messages on the phone is probably asleep.
                                var slow by remember(convId) { mutableStateOf(false) }
                                LaunchedEffect(convId) { delay(7_000); slow = true }
                                androidx.compose.animation.AnimatedVisibility(slow) {
                                    Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        T("Waiting on Google Messages on your phone", size = 14.sp, weight = FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                                        T("It may be asleep. Opening it wakes it up, then come back here.", size = 13.sp, color = t.soft, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                        Spacer(Modifier.height(12.dp))
                                        T("Open Google Messages", size = 14.sp, weight = FontWeight.SemiBold, color = t.onFill,
                                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(t.fill).clickable {
                                                runCatching { ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(com.jpetrydesign.pyng.data.GmListener.GM)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                                    .onFailure { repo.toast("Couldn't open Google Messages") }
                                            }.padding(horizontal = 18.dp, vertical = 10.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Jump to latest. With new texts waiting it widens into a pill that says how many.
                val showJump by remember { derivedStateOf { list.firstVisibleItemIndex > 4 } }
                AnimatedVisibility(showJump || missed > 0 && !atBottom, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = with(density) { bottomH.toDp() } + 12.dp), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Row(
                        Modifier.height(40.dp).animateContentSize().edge(RoundedCornerShape(20.dp)).frost(RoundedCornerShape(20.dp), if (missed > 0) t.fabP else t.fab)
                            .press(0.9f) { scope.launch { list.animateScrollToItem(0) } }.padding(horizontal = if (missed > 0) 14.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (missed > 0) {
                            T(if (missed == 1) "1 new" else "$missed new", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.plusInk)
                            Spacer(Modifier.width(6.dp))
                        }
                        Ico(Icons.arrowDown, if (missed > 0) t.plusInk else t.p, 18.dp, 2.2f)
                    }
                }
                // Up to your unread texts, when the chat opened with more than fit on screen.
                AnimatedVisibility(
                    !searching && !unreadSeen && unreadOffscreen,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { headH.toDp() } + 10.dp),
                    enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut() + scaleOut(targetScale = 0.85f),
                ) {
                    Row(
                        Modifier.height(38.dp).edge(RoundedCornerShape(19.dp)).frost(RoundedCornerShape(19.dp), t.fabP).press(0.94f) { jumpToUnread() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(Icons.arrowDown, t.plusInk, 16.dp, 2.2f, modifier = Modifier.graphicsLayer { rotationZ = 180f })
                        Spacer(Modifier.width(7.dp))
                        T("$unreadAtOpen new messages", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.plusInk)
                    }
                }

                // Search page, under the header
                androidx.compose.animation.AnimatedVisibility(panelOn, enter = fadeIn(), exit = fadeOut()) {
                    ChatSearchPanel(
                        t, conv, msgs, query, lookingFurther,
                        onFill = { query = it; matchIdx = 0; showResults = true },
                        onJump = { m ->
                            rememberSearch(repo, query)
                            val i = matches.indexOf(m.key)
                            matchIdx = if (i >= 0) matches.size - 1 - i else 0
                            showResults = false
                            focusManager.clearFocus()
                        },
                        onOpen = { m, ref -> view(m, ref) },
                        modifier = Modifier.padding(top = with(density) { headH.toDp() }),
                    )
                }

                // Header
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { headH = it.height }) {
                    when {
                        selecting -> SelectHeader(t, selected.size,
                            onClose = { selected.clear() },
                            onCopy = { copy(ctx, msgs.filter { it.key in selected }.joinToString("\n") { it.text }); repo.toast("Copied"); selected.clear() },
                            onStar = { star(repo, msgs.filter { it.key in selected }); selected.clear() },
                            onDelete = {
                                val del = msgs.filter { it.key in selected }
                                repo.confirm(if (del.size > 1) "Delete ${del.size} messages?" else "Delete message?", "They'll be deleted from Google Messages on your phone too. The other person still has their copy.") {
                                    repo.deleteMessages(del); repo.toast(if (del.size > 1) "${del.size} messages deleted" else "Message deleted"); selected.clear()
                                }
                            })
                        searching -> SearchHeader(t, query, { query = it; matchIdx = 0; showResults = true }, if (panelOn) -1 else matches.size, matchIdx, { matchIdx++ }, { matchIdx = (matchIdx - 1 + maxOf(1, matches.size)) % maxOf(1, matches.size) }) { searching = false; query = "" }
                        else -> ThreadHeader(t, conv, typing, wide, onBack = { nav.back() }, onContact = { nav.go(Route.Contact(convId)) }, onCall = { callSheet = true }, onSwipe = { next ->
                            // Same order as your chat list: pinned first, then newest.
                            val active = repo.convs.values.filter { it.status == "active" }
                            val order = p.pinned.filter { id -> active.any { it.id == id } } + active.filter { it.id !in p.pinned }.sortedByDescending { it.ts }.map { it.id }
                            val i = order.indexOf(convId)
                            val to = if (i < 0) null else order.getOrNull(if (next) i + 1 else i - 1)
                            if (to != null) nav.swapChat(to, next) else repo.toast(if (next) "That's your last chat" else "That's your first chat")
                        })
                    }
                }

                // Composer and everything above it. Measured with the keyboard included, so when the
                // keyboard opens the newest messages ride up with it instead of hiding behind it.
                if (!panelOn) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomH = it.height }.imePadding()) {
                    Composer(
                        t = t, conv = conv, draft = draft, onDraft = { draft = it; if (it.isNotEmpty()) repo.typingPing(convId) },
                        replyTo = replyTo, onCancelReply = { replyTo = null }, attachments = attachments, schedule = schedule,
                        onSchedule = { schedule = it }, onSend = { send(it) }, smart = p.on("smart") && msgs.lastOrNull()?.fromMe == false && draft.isEmpty() && !selecting && conv != null && !automated(conv, msgs.lastOrNull()?.text.orEmpty()),
                        lastIncoming = msgs.lastOrNull { !it.fromMe }?.text.orEmpty(),
                        fontSize = fontSize, initialPanel = demoPanel,
                    )
                }
            }

            }
            Effects(fx, t)
            SendMeltLayer(sendFx, t, fontSize, p.on("boldText"), corner = shape.first.value) { rootCoords[0] }
            MessageMenu(menuFor, t, conv, menuAnchor, menuFor?.let { rowCtxs[it.key] }, onDismiss = { menuFor = null },
                onReact = { m, e -> repo.react(m, e); burst = m.key to e },
                onReply = { replyTo = it }, onCopy = { copy(ctx, it.text); repo.toast("Copied") },
                onForward = { forward = it }, onStar = { star(repo, listOf(it)) },
                onSelect = { m -> (bundles.of[m.key]?.map { it.first.key }?.distinct() ?: listOf(m.key)).forEach { k -> if (k !in selected) selected.add(k) } },
                onDetails = { detailsFor = it }, onSelectText = { selectTextFor = it },
                group = menuFor?.let { bundles.of[it.key] },
                onSave = { refs -> scope.launch { saveFiles(ctx, repo.filesFor(refs)) } },
                onShare = { refs -> scope.launch { shareFiles(ctx, repo.filesFor(refs)) } },
                onForwardAll = { list -> forwardAll = list; forward = list.firstOrNull() },
                onDelete = {
                    val grp = bundles.of[it.key]?.map { p -> p.first }?.distinctBy { p -> p.key }?.takeIf { g -> g.size > 1 }
                    if (grp != null) {
                        repo.confirm("Delete ${grp.size} messages?", "They'll be deleted from Google Messages on your phone too. The other person still has their copy.") { repo.deleteMessages(grp); repo.toast("Deleted") }
                    } else if (it.tmpId.startsWith("sched-")) {
                        val id = it.tmpId.removePrefix("sched-")
                        com.jpetrydesign.pyng.data.ScheduleWorker.cancel(ctx, id)
                        repo.prefs.edit { pr -> pr.copy(scheduled = pr.scheduled.filter { s -> s.id != id }) }
                        repo.toast("Scheduled message canceled")
                    } else {
                        val m = it
                        repo.confirm("Delete message?", "It'll be deleted from Google Messages on your phone too. The other person still has their copy.") { repo.deleteMessages(listOf(m)); repo.toast("Message deleted") }
                    }
                })
            DetailsSheet(detailsFor, conv) { detailsFor = null }
            SelectTextSheet(selectTextFor) { selectTextFor = null }
            ForwardSheet(forward) { target, m ->
                forward = null
                val all = forwardAll
                forwardAll = null
                if (target != null) {
                    val list = all ?: listOf(m)
                    val refs = list.flatMap { it.media }
                    val text = if (all == null) m.text else ""
                    if (refs.isEmpty()) { repo.send(target, text); repo.toast("Forwarded") }
                    else scope.launch {
                        val files = repo.filesFor(refs)
                        if (files.isEmpty()) { repo.toast("Couldn't download it to forward"); return@launch }
                        repo.send(target, text, attachments = files.map { (p, r) -> Pending(p, r.mime, r.name) })
                        repo.toast(if (files.size < refs.size) "Forwarded ${files.size} of ${refs.size}" else "Forwarded")
                    }
                }
            }
            CallSheet(callSheet, conv) { callSheet = false }
            ContactSheet()
            PhotoViewer(viewing, viewSet, conv, myId,
                onClose = { viewing = null },
                onReply = { viewing = null; replyTo = it },
                onReact = { m, e -> repo.react(m, e); burst = m.key to e },
                onDelete = { m ->
                    repo.confirm("Delete message?", "It'll be deleted from Google Messages on your phone too. The other person still has their copy.") { viewing = null; repo.deleteMessages(listOf(m)); repo.toast("Message deleted") }
                },
                onForward = { viewing = null; forward = it })
        }
        }
    }
    BackHandler(enabled = selecting || searching) {
        when {
            selecting -> selected.clear()
            // Stepping through matches in the chat: back returns to the list of matches.
            !showResults && query.isNotBlank() -> showResults = true
            else -> { searching = false; query = "" }
        }
    }
}

private fun dayOf(ms: Long) = ms / 86_400_000L + (java.util.TimeZone.getDefault().getOffset(ms) / 86_400_000L)

private fun toggle(list: MutableList<String>, k: String) { if (k in list) list.remove(k) else list.add(k) }

fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Message", text))
}

private fun star(repo: com.jpetrydesign.pyng.data.Repo, list: List<Msg>) {
    val p = repo.prefs.state
    val allOn = list.all { p.isStarred(it.key) }
    repo.prefs.edit { pr ->
        if (allOn) pr.copy(starred = pr.starred.filter { s -> list.none { it.key == s.msgId } })
        else pr.copy(starred = pr.starred + list.filter { !pr.isStarred(it.key) }.map { com.jpetrydesign.pyng.data.Starred(it.key, it.convId, msgPreview(it), it.ts) })
    }
    repo.toast(if (allOn) "Removed from starred" else "Starred")
}

fun openMedia(ctx: Context, repo: com.jpetrydesign.pyng.data.Repo, nav: Nav, ref: MediaRef) {
    val path = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    if (path.isEmpty()) { repo.ensureMedia(ref, manual = true); repo.toast("Downloading…"); return }
    if (ref.isImage) nav.go(Route.Viewer(path, ref.mime)) else openFile(ctx, path, ref.mime)
}

/** The status line shown under a message when you tap beside it: text, icon, and whether it's the "read" accent. */
/** RCS chats can send files up to about 100 MB. */
const val MAX_RCS_BYTES = 100L * 1024 * 1024

private fun infoFor(m: Msg, c: Conv?, receipts: Boolean): Triple<String, String, Boolean> = when {
    m.status == "OUTGOING_SCHEDULED" -> Triple("Scheduled for " + m.statusText, Icons.clock, false)
    !m.fromMe -> Triple("Received " + whenText(m.ts), Icons.inbox, false)
    m.failed -> Triple(when {
        m.status == "OUTGOING_FAILED_TOO_LARGE" -> "Not sent · too large"
        m.status == "OUTGOING_FAILED_RECIPIENT_LOST_RCS" -> "Not sent · they're no longer on RCS"
        m.status == "OUTGOING_CANCELED" -> "Canceled"
        m.error.isNotEmpty() -> "Not sent · " + m.error.substringBefore(':').take(60)
        else -> "Not sent"
    }, Icons.warn, false)
    m.pending -> Triple("Sending…", Icons.clock, false)
    m.readByThem && receipts -> Triple("Read " + whenText(m.ts), Icons.eye, true)
    m.delivered -> Triple("Delivered " + whenText(m.ts), Icons.read, false)
    else -> Triple("Sent " + whenText(m.ts), Icons.check, false)
}.let { if (c?.rcs == false && it.first.startsWith("Read")) it.copy(first = it.first.replace("Read", "Delivered")) else it }

private fun sysText(status: String) = when (status) {
    "TOMBSTONE_PARTICIPANT_JOINED" -> "Someone joined"
    "TOMBSTONE_PARTICIPANT_LEFT" -> "Someone left"
    "TOMBSTONE_SELF_LEFT" -> "You left"
    "TOMBSTONE_RCS_GROUP_CREATED", "TOMBSTONE_MMS_GROUP_CREATED" -> "Group created"
    "TOMBSTONE_ONE_ON_ONE_RCS_CREATED" -> "Chatting with RCS"
    "TOMBSTONE_ONE_ON_ONE_SMS_CREATED" -> "Texting with SMS/MMS"
    else -> "Update"
}

@Composable
private fun ThreadHeader(t: Tok, c: Conv?, typing: Boolean, wide: Boolean, onBack: () -> Unit, onContact: () -> Unit, onCall: () -> Unit, onSwipe: (next: Boolean) -> Unit = {}) {
    val shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    // Swipe across the name to go to the next or previous chat.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reach = with(androidx.compose.ui.platform.LocalDensity.current) { 84.dp.toPx() }
    var drag by remember { mutableStateOf(0f) }
    val shown by androidx.compose.animation.core.animateFloatAsState(drag, label = "headSwipe")
    Row(
        Modifier.fillMaxWidth().shadowEdge(t).frost(shape, t.hdrBg, 34.dp).statusBarsPadding().padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 18.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { val d = drag; drag = 0f; if (kotlin.math.abs(d) >= reach) onSwipe(d < 0) },
                    onDragCancel = { drag = 0f },
                ) { ch, dx ->
                    val before = drag
                    drag = (drag + dx).coerceIn(-reach * 1.4f, reach * 1.4f)
                    if (kotlin.math.abs(before) < reach && kotlin.math.abs(drag) >= reach) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    ch.consume()
                }
            },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!wide) CircleButton(Icons.back, "Back", onBack)
        else Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).graphicsLayer { translationX = shown * 0.45f; alpha = 1f - (kotlin.math.abs(shown) / (reach * 3f)).coerceIn(0f, 0.5f) }
                .clip(RoundedCornerShape(24.dp)).clickable(onClick = onContact).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (c != null) {
                val w = whoOf(c)
                Box(Modifier.edge(CircleShape, elevation = 6.dp)) { WhoAvatar(w, 46.dp, 15.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(c.title, size = 19.sp, weight = FontWeight.Bold, letterSpacing = (-0.5).sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Ico("M9.5 6.5l5.5 5.5-5.5 5.5", t.soft, 16.dp, 2.4f)
                    }
                    val enc = encryptionOf(c, LocalRepo.current.msgs[c.id].orEmpty())
                    val sub = when {
                        typing -> "typing…"
                        c.group -> c.others.joinToString { it.short } + " and you"
                        enc == Enc.E2EE -> "Encrypted"
                        enc == Enc.SMS && !c.business -> "Text message"
                        else -> null
                    }
                    if (sub != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!typing && enc == Enc.E2EE) { Ico(Icons.lock, t.soft, 11.dp, 2f); Spacer(Modifier.width(3.dp)) }
                        T(sub, size = 12.sp, color = if (typing) t.p else t.soft, maxLines = 1)
                    }
                }
            }
        }
        if (c != null && !c.group && !c.business) {
            Box(Modifier.size(44.dp).edge(CircleShape).clip(CircleShape).background(t.fab).press(0.9f, onCall), contentAlignment = Alignment.Center) {
                Ico(Icons.phone, t.p, 20.dp, 1f, fill = t.p)
            }
        }
    }
}

private fun Modifier.shadowEdge(t: Tok): Modifier =
    this.shadow(10.dp, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp), clip = false, ambientColor = t.shadow.copy(alpha = 0.12f), spotColor = t.shadow.copy(alpha = 0.18f))

@Composable
private fun SelectHeader(t: Tok, n: Int, onClose: () -> Unit, onCopy: () -> Unit, onStar: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    Row(Modifier.fillMaxWidth().frost(shape, t.hdrBg, 34.dp).statusBarsPadding().padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.close, "Cancel", onClose)
        T("$n selected", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
        CircleButton(Icons.copy, "Copy", onCopy, iconSize = 20.dp)
        CircleButton(Icons.star, "Star", onStar, iconSize = 20.dp)
        CircleButton(Icons.trash, "Delete", onDelete, tint = t.danger, iconSize = 20.dp)
    }
}

@Composable
private fun SearchHeader(t: Tok, q: String, onQ: (String) -> Unit, count: Int, idx: Int, onUp: () -> Unit, onDown: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().frost(shape, t.hdrBg, 34.dp).statusBarsPadding().padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.back, "Close search", onClose)
        Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(t.field).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (q.isEmpty()) T("Search this chat", size = 15.sp, color = t.soft)
            BasicTextField(q, onQ, singleLine = true, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 15.sp, color = t.ink), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        }
        // count < 0: the search page is showing, so the arrows aren't needed yet.
        if (count >= 0) {
            T(if (q.isBlank()) "" else if (count == 0) "0" else "${(idx % count) + 1}/$count", size = 12.5.sp, color = t.soft, modifier = Modifier.padding(horizontal = 8.dp))
            CircleButton("M6 15l6-6 6 6", "Older", onUp, size = 38.dp, iconSize = 18.dp)
            CircleButton("M6 9l6 6 6-6", "Newer", onDown, size = 38.dp, iconSize = 18.dp)
        } else if (q.isNotEmpty()) {
            CircleButton(Icons.close, "Clear", { onQ("") }, size = 38.dp, iconSize = 16.dp, tint = t.soft)
        } else Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SpamBanner(why: List<String>, t: Tok, onReport: () -> Unit, onNot: () -> Unit) {
    Column(Modifier.padding(4.dp).padding(bottom = 6.dp).clip(RoundedCornerShape(22.dp)).background(t.glassStrong).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(t.danger.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Ico(Icons.warn, t.danger, 18.dp, 2f) }
            Spacer(Modifier.width(10.dp))
            Column {
                T("Likely a scam", size = 14.5.sp, weight = FontWeight.SemiBold)
                T(why.joinToString(". ") + ". Links are turned off in this chat.", size = 12.5.sp, color = t.soft, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.4f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.danger).clickable(onClick = onReport), contentAlignment = Alignment.Center) { T("Report & block", size = 13.5.sp, weight = FontWeight.SemiBold, color = Color.White) }
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.neutral).clickable(onClick = onNot), contentAlignment = Alignment.Center) { T("Not spam", size = 13.5.sp, weight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun UnknownBanner(t: Tok, c: Conv?, onDismiss: () -> Unit, onAdd: () -> Unit, onSpam: () -> Unit) {
    Column(Modifier.padding(4.dp).padding(bottom = 6.dp).clip(RoundedCornerShape(22.dp)).background(t.glassStrong).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Ico(Icons.shield, t.p, 20.dp, 1.9f)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                T("Not in your contacts", size = 14.sp, weight = FontWeight.SemiBold)
                T("Be careful with links, and never share a verification code with someone you don't know.", size = 12.5.sp, color = t.soft, lineHeight = 17.sp)
            }
            CircleButton(Icons.close, "Dismiss", onDismiss, size = 28.dp, iconSize = 16.dp, tint = t.soft)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.tone).clickable(onClick = onAdd), contentAlignment = Alignment.Center) { T("Add contact", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.p) }
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.neutral).clickable(onClick = onSpam), contentAlignment = Alignment.Center) { T("Report spam", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.danger) }
        }
    }
    @Suppress("UNUSED_EXPRESSION") c
}

@Composable
private fun EmptyThread(c: Conv, t: Tok, onHi: () -> Unit) {
    val w = whoOf(c)
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WhoAvatar(w, 84.dp, 28.sp)
        Spacer(Modifier.height(10.dp))
        T(c.title, size = 18.sp, weight = FontWeight.SemiBold)
        T(if (c.rcs) "Chat features are on. Typing, read receipts and HD photos work here." else "Texts send as SMS/MMS.", size = 13.5.sp, color = t.soft, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 30.dp, end = 30.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(t.tone).clickable(onClick = onHi).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            T("👋", size = 20.sp); Spacer(Modifier.width(8.dp)); T("Say hi", size = 14.5.sp, weight = FontWeight.SemiBold, color = t.p)
        }
    }
}

/** Confetti, balloons and hearts for phrases like "happy birthday". */
@Composable
private fun Effects(kind: String?, t: Tok) {
    if (kind == null) return
    val k = remember(kind) { Animatable(0f) }
    LaunchedEffect(kind) { k.animateTo(1f, androidx.compose.animation.core.tween(3600, easing = androidx.compose.animation.core.LinearEasing)) }
    val seed = remember(kind) { java.util.Random(kind.hashCode().toLong() + System.nanoTime()) }
    val colors = listOf(t.p, t.s, t.pal.plus ?: Color(0xFFFFB547), Color(0xFFFFD166), Color(0xFFFF5C8A), Color(0xFF7C5CFF), Color(0xFF4FD1C5))
    val parts = remember(kind) {
        val confetti = if (kind == "hearts") 0 else if (kind == "birthday") 34 else 70
        val floaty = when (kind) { "birthday" -> listOf("🎈", "🎈", "🎉", "🎈", "🎂"); "hearts" -> listOf("❤️", "💖", "💕", "❤️"); else -> emptyList() }
        val n = if (kind == "hearts") 18 else 12
        List(confetti) { FxPart(null, seed.nextFloat(), seed.nextFloat() * 0.25f, 1.6f + seed.nextFloat() * 1.3f, colors[seed.nextInt(colors.size)], seed.nextBoolean(), seed.nextFloat() * 560f) } +
            if (floaty.isEmpty()) emptyList() else List(n) { i -> FxPart(floaty[i % floaty.size], seed.nextFloat() * 0.88f, seed.nextFloat() * 0.25f, 2.6f + seed.nextFloat() * 1.1f, Color.Transparent, false, 0f) }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat(); val w = constraints.maxWidth.toFloat()
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val time = k.value * 3.8f
            parts.filter { it.emoji == null }.forEach { pt ->
                val local = ((time - pt.delay) / pt.dur).coerceIn(0f, 1f)
                if (local <= 0f || local >= 1f) return@forEach
                val y = -24f + local * (h + 60f)
                val x = pt.x * w
                rotate(pt.spin * local, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                    if (pt.round) drawCircle(pt.color, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                    else drawRect(pt.color, androidx.compose.ui.geometry.Offset(x - 4f, y - 7f), androidx.compose.ui.geometry.Size(9.dp.toPx() * 0.8f, 14.dp.toPx() * 0.8f))
                }
            }
        }
        val time = k.value * 3.8f
        parts.filter { it.emoji != null }.forEach { pt ->
            val local = ((time - pt.delay) / pt.dur).coerceIn(0f, 1f)
            if (local > 0f && local < 1f) {
                T(
                    pt.emoji!!, size = 38.sp,
                    modifier = Modifier.offset { IntOffset((pt.x * w).toInt(), (h - local * (h + 200f)).toInt()) }
                        .graphicsLayer { rotationZ = -6f + 12f * kotlin.math.sin(local * 6f) },
                )
            }
        }
    }
}

private class FxPart(val emoji: String?, val x: Float, val delay: Float, val dur: Float, val color: Color, val round: Boolean, val spin: Float)
