package com.jpetrydesign.pyng.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.CountBadge
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalHaze
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.SheetAction
import com.jpetrydesign.pyng.ui.kit.SheetGroup
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.TypingDots
import com.jpetrydesign.pyng.ui.kit.cardEdge
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.frost
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.LocalTok
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One pinned chat as the pinned layouts draw it. */
private class Pin(val c: Conv, val who: Who, val count: Int, val typing: Boolean, val preview: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: Nav, wide: Boolean) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val haze = remember { HazeState() }
    var menuFor by remember { mutableStateOf<Conv?>(null) }
    // Edit pins: the pinned chats wiggle, drag to reorder, tap the minus to unpin, Done to finish.
    // Outside it, holding a chat only opens its menu, so it's always clear when you're editing.
    var editPins by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = editPins) { editPins = false }
    val now = System.currentTimeMillis()

    LaunchedEffect(Unit) { if (repo.convs.isEmpty() || !repo.hub.engine.isConnected) repo.refresh() }
    val ctx = LocalContext.current
    var askPerms by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!repo.demo && p.permsAsked < 1 && missingPerms(ctx).isNotEmpty()) { kotlinx.coroutines.delay(700); askPerms = true }
        when (repo.demoOpen) {
            "rowmenu" -> menuFor = repo.convs["c6"]
            "editpins" -> editPins = true
            "strip" -> nav.strip = true
            "perms" -> askPerms = true
        }
        repo.demoOpen = null
    }

    val all = repo.convs.values.toList()
    val layout = p.pinLayout
    fun unreadOf(c: Conv) = c.unread || c.id in p.markedUnread
    fun countOf(c: Conv): Int {
        if (!unreadOf(c)) return 0
        val list = repo.msgs[c.id] ?: return 1
        return list.takeLastWhile { !it.fromMe }.size.coerceIn(1, 99)
    }
    fun typingOf(c: Conv) = (repo.typing[c.id] ?: 0L) > now
    val spamLocal = if (p.on("spam")) all.filter { c -> c.status == "active" && c.id !in p.notSpam && !c.fromMe && spamCheck(c, c.preview) != null }.map { it.id }.toSet() else emptySet()
    val active = all.filter { it.status == "active" && it.id !in spamLocal }
    val pinnedIds = p.pinned.filter { id -> active.any { it.id == id } }

    // Dragging: a pinned chat (to reorder or drag out) or a list row (to drag up into pins).
    val listState = rememberLazyListState()
    val dh = remember { DragHolder() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragFromRow by remember { mutableStateOf(false) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragMoved by remember { mutableStateOf(false) }
    var inZone by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val ghostLift = with(density) { 46.dp.toPx() }
    val previewIds = dragId.takeIf { dragMoved }.let { id ->
        if (id == null) pinnedIds
        else {
            val without = pinnedIds - id
            if (inZone && (!dragFromRow || without.size < 8)) without.toMutableList().apply { add(target.coerceIn(0, size), id) } else without
        }
    }
    val pins = previewIds.mapNotNull { id -> active.firstOrNull { it.id == id } }.map { c ->
        Pin(c, whoOf(c, repo, t), countOf(c), typingOf(c), (if (c.fromMe) "You: " else if (c.group && c.sender.isNotBlank()) c.sender.substringBefore(' ') + ": " else "") + repo.previewOf(c))
    }
    val rows = (if (layout == "List") pins.map { it.c } else emptyList()) + active.filter { it.id !in pinnedIds }.sortedByDescending { it.ts }
    val archCount = all.count { it.status == "archived" }
    val spamCount = all.count { it.status == "spam" || it.status == "blocked" } + spamLocal.size
    val newCount = active.count { unreadOf(it) && it.id !in p.muted }

    // Hold = menu. Only once the finger moves does the chat lift and follow it (like the home screen).
    val dragScope = rememberCoroutineScope()
    val ghostPos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val ghostScale = remember { Animatable(1f) }
    val ghostAlpha = remember { Animatable(1f) }
    var ghostId by remember { mutableStateOf<String?>(null) }
    var ghostSize by remember { mutableStateOf(64.dp) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shake = remember { Animatable(0f) }
    val slop = with(density) { 10.dp.toPx() }

    fun liftAt(id: String, at: Offset, center: Offset, size: androidx.compose.ui.unit.Dp, fromRow: Boolean) {
        dragId = id; dragFromRow = fromRow; dragMoved = false
        dragPointer = at; dragStart = at
        grab = center - at; origin = center; ghostSize = size
        inZone = !fromRow
        target = pinnedIds.indexOf(id).coerceAtLeast(0)
        dh.active = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuFor = if (editPins) null else repo.convs[id]
    }
    fun startDrag(id: String) {
        dragMoved = true
        menuFor = null
        ghostId = id
        dh.animUntil = Long.MAX_VALUE
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        dragScope.launch {
            ghostPos.snapTo(dragPointer + grab)
            ghostAlpha.snapTo(1f)
            launch { ghostScale.snapTo(1f); ghostScale.animateTo(1.12f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
            launch { ghostAlpha.animateTo(0.94f, tween(140)) }
        }
    }
    dh.onMove = move@{ at ->
        val id = dragId ?: return@move
        dragPointer = at
        if (!dragMoved) { if (editPins && (at - dragStart).getDistance() > slop) startDrag(id) else return@move }
        val g = at + grab
        dragScope.launch { ghostPos.snapTo(g) }
        val z = dh.zone
        // The pinned area reaches a little past its edges, so it's easy to hit.
        val now = z != null && z.inflate(with(density) { 20.dp.toPx() }).contains(g)
        if (now != inZone) {
            inZone = now
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (now && dragFromRow && pinnedIds.size >= 8) dragScope.launch { shake.snapTo(0f); for (x in listOf(10f, -9f, 7f, -5f, 3f, 0f)) shake.animateTo(x, tween(45)) }
        }
        if (now) {
            val n = (pinnedIds - id).size + 1
            // Move only once the chat's center is inside another slot: no flicker on the boundaries.
            val inside = (0 until n).firstOrNull { i -> dh.slots[i]?.contains(g) == true }
            val next = inside ?: if (!dh.hadSlot) (0 until n).mapNotNull { i -> dh.slots[i]?.let { i to (it.center - g).getDistanceSquared() } }.minByOrNull { it.second }?.first else null
            if (next != null) dh.hadSlot = true
            if (next != null && next != target) { target = next; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        } else dh.hadSlot = false
    }
    dh.onDrop = drop@{
        val id = dragId ?: return@drop
        dh.active = false
        dh.hadSlot = false
        if (!dragMoved) { dragId = null; return@drop } // the menu is already open
        dh.animUntil = System.currentTimeMillis() + 900
        val order = previewIds
        val cur = repo.prefs.state.pinned
        val full = pinnedIds.size >= 8 && id !in pinnedIds
        val landing = dh.slots[target]?.center
        var vanish = false
        var dest: Offset = origin
        when {
            dragFromRow && inZone && !full -> {
                repo.prefs.edit { it.copy(pinned = order + cur.filter { x -> x !in order }) }
                repo.toast("Pinned") { repo.prefs.edit { it.copy(pinned = cur) } }
                dest = landing ?: origin
            }
            dragFromRow && inZone -> repo.toast("Unpin a chat first (8 max)")
            dragFromRow -> {}
            inZone -> {
                if (order != pinnedIds) repo.prefs.edit { it.copy(pinned = order + cur.filter { x -> x !in order }) }
                dest = landing ?: origin
            }
            else -> {
                repo.prefs.edit { it.copy(pinned = cur.filter { x -> x != id }) }
                repo.toast("Unpinned") { repo.prefs.edit { it.copy(pinned = cur) } }
                vanish = true
            }
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        dragId = null
        // The lifted chat glides into place (or back home, or fades away when unpinned).
        dragScope.launch {
            if (vanish) {
                launch { ghostScale.animateTo(0.6f, tween(200)) }
                ghostAlpha.animateTo(0f, tween(200))
            } else {
                launch { ghostScale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
                ghostPos.animateTo(dest, spring(dampingRatio = 0.82f, stiffness = 380f))
            }
            ghostId = null
        }
    }
    if (layout == "List") dh.zone = null
    // Scroll the list while a dragged chat is held near the top or bottom edge.
    LaunchedEffect(dragId) {
        if (dragId == null) return@LaunchedEffect
        val edge = with(density) { 150.dp.toPx() }
        while (dragId != null) {
            withFrameNanos { }
            val y = dragPointer.y
            if (y < edge) listState.scrollBy(-(edge - y) / 10f)
            else if (dh.height > 0 && y > dh.height - edge) listState.scrollBy((y - (dh.height - edge)) / 10f)
        }
    }
    LaunchedEffect(pinnedIds.isEmpty()) { if (pinnedIds.isEmpty()) editPins = false }
    val pinCtl = PinCtl(
        dh = dh, dragId = ghostId, open = { id -> nav.openChat(id) }, lift = { id, at, center -> liftAt(id, at, center, 72.dp, false) },
        dropActive = dragId != null && dragMoved && inZone, shake = shake.value, editing = editPins,
        unpin = { id ->
            val cur = repo.prefs.state.pinned
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            dh.animUntil = System.currentTimeMillis() + 900
            repo.prefs.edit { it.copy(pinned = cur.filter { x -> x != id }) }
            repo.toast("Unpinned") { repo.prefs.edit { it.copy(pinned = cur) } }
        },
    )

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { dh.home = it; dh.height = it.size.height.toFloat() }
            // While something is lifted, this box follows the finger before anything else sees it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (!dh.active) continue
                        val ch = e.changes.firstOrNull() ?: continue
                        if (ch.pressed) { dh.onMove(ch.position); ch.consume() } else { ch.consume(); dh.onDrop() }
                    }
                }
            },
    ) {
        Glow()
        androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze) {
            LazyColumn(
                Modifier.fillMaxSize().hazeSource(haze),
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 150.dp),
            ) {
                item("head") {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 24.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T("Messages", size = 36.sp, weight = FontWeight.Bold, letterSpacing = (-1.3).sp, lineHeight = 40.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(t.dot))
                                Spacer(Modifier.width(8.dp))
                                val status by repo.hub.connection.collectAsState()
                                T(
                                    when {
                                        repo.loading && all.isEmpty() -> "Loading your chats…"
                                        status == "phone_not_responding" -> "Your phone isn't responding"
                                        status == "reconnecting" || status == "connecting" -> "Connecting…"
                                        newCount > 0 -> "$newCount New Conversation" + if (newCount > 1) "s" else ""
                                        else -> "You’re All Caught Up"
                                    },
                                    size = 13.5.sp, weight = FontWeight.Medium, color = t.soft,
                                )
                            }
                        }
                        val photo = p.profilePhoto.ifEmpty { null }
                        Box(Modifier.size(56.dp).edge(CircleShape).clip(CircleShape).press(0.92f) { nav.go(Route.Settings) }) {
                            Avatar(photo, com.jpetrydesign.pyng.ui.kit.initialsOf(p.profileName.ifBlank { "Me" }), 56.dp, t.tone, t.p)
                        }
                    }
                }
                if (pins.isNotEmpty() && layout != "List" && layout != "Dock") {
                    item("pins") { PinnedArea(layout, pins, pinCtl, p.pinStyle) }
                } else if (pins.isEmpty() && rows.isNotEmpty()) {
                    item("nopins") {
                        DisposableEffect(Unit) { onDispose { dh.zone = null } }
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                .onGloballyPositioned { c -> dh.home?.let { h -> runCatching { dh.zone = h.localBoundingBoxOf(c) } } }
                                .clip(RoundedCornerShape(26.dp)).background(t.card).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.pinned, t.p, 20.dp) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                T("Pin your favorite people", size = 14.5.sp, weight = FontWeight.SemiBold)
                                T("Hold a conversation and drag it up here, or tap Pin. Up to 8 show here as big circles.", size = 12.5.sp, color = t.soft, lineHeight = 17.sp)
                            }
                        }
                    }
                }
                if (rows.isEmpty() && !repo.loading) {
                    item("empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(84.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.message, t.p, 36.dp, 1.7f) }
                            Spacer(Modifier.height(10.dp))
                            T(if (repo.loadError != null) "Couldn't load chats" else "No conversations yet", size = 18.sp, weight = FontWeight.SemiBold)
                            T(repo.loadError ?: "Tap the message button to text someone or start a group.", size = 13.5.sp, color = t.soft, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            if (repo.loadError != null) T("Try again", size = 14.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.padding(top = 12.dp).clickable { repo.refresh() }.padding(8.dp))
                        }
                    }
                }
                items(rows, key = { "r:" + it.id }) { c ->
                    val i = rows.indexOf(c)
                    ConvRow(
                        c = c, index = i, pinMark = layout == "List" && c.id in pinnedIds, count = countOf(c), unread = unreadOf(c),
                        typing = typingOf(c), selected = wide && (nav.top as? Route.Thread)?.id == c.id,
                        onOpen = { nav.openChat(c.id) }, onMenu = { menuFor = c },
                        lifted = ghostId == c.id,
                        onLift = if (layout == "List") null else ({ at, center, size -> liftAt(c.id, at, center, size, true) }), home = dh,
                        onSwipe = { side -> swipeAct(repo, c, if (side > 0) p.opt("swipeR") else p.opt("swipe")) },
                    )
                }
                if (archCount > 0) item("arch") { FolderRow(Icons.archive, "Archived · $archCount") { nav.go(Route.Lists("archived")) } }
                if (spamCount > 0) item("spam") { FolderRow(Icons.shield, "Spam & blocked · $spamCount") { nav.go(Route.Lists("spam")) } }
                item("hint") {
                    val v = mapOf("Read/Unread" to "mark read", "Archive" to "archive", "Delete" to "delete", "Pin" to "pin", "Mute" to "mute")
                    val parts = listOfNotNull(v[p.opt("swipeR")]?.let { "swipe right to $it" }, v[p.opt("swipe")]?.let { "swipe left to $it" })
                    if (parts.isNotEmpty() && rows.isNotEmpty()) T(
                        parts.joinToString(", ").replaceFirstChar { it.uppercase() }, size = 11.5.sp, color = t.faint, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }

            if (layout == "Dock" && pins.isNotEmpty()) {
                Row(
                    Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 18.dp, bottom = 28.dp, end = 168.dp)
                        .onGloballyPositioned { c -> dh.home?.let { h -> runCatching { dh.zone = h.localBoundingBoxOf(c); dh.area = c } } }
                        .height(60.dp).edge(RoundedCornerShape(30.dp)).frost(RoundedCornerShape(30.dp), t.fab)
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pins.forEachIndexed { i, pin ->
                        PinItem(pin, i, pinCtl, Modifier.enter(i, pop = true)) {
                            Box(Modifier.size(46.dp)) {
                                Avatar(pin.who.photo, pin.who.ini, 46.dp, pin.who.bg, pin.who.ink, 15.sp)
                                if (pin.count > 0) {
                                    val d = LocalDensity.current
                                    val avPx = with(d) { 46.dp.roundToPx() }; val dx = with(d) { 3.dp.roundToPx() }; val dy = with(d) { 2.dp.roundToPx() }
                                    CountBadge(
                                        pin.count, h = 18.dp, modifier = Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp),
                                        backdrop = { Avatar(pin.who.photo, pin.who.ini, 46.dp, pin.who.bg, pin.who.ink, 15.sp) }, backdropSize = 46.dp,
                                        backdropAt = { sz -> IntOffset(-(avPx - sz.width + dx), dy) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val fabAlpha by androidx.compose.animation.core.animateFloatAsState(if ((dragId != null && dragMoved) || editPins) 0f else 1f, label = "fab")
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 28.dp).graphicsLayer { alpha = fabAlpha },
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                // Frosted glass: light blur, mostly see-through, with a bright rim instead of a heavy shadow.
                Box(
                    Modifier.size(60.dp).glassRim(CircleShape).frost(CircleShape, if (t.solid) t.fab else t.fab.copy(alpha = if (t.dark) 0.14f else 0.2f), 9.dp).press(0.9f) { nav.go(Route.Search) },
                    contentAlignment = Alignment.Center,
                ) { SolidSearch(t.ink, 24.dp) }
                Box(
                    Modifier.size(60.dp).glassRim(CircleShape).frost(CircleShape, if (t.solid) t.plusFlat else t.plusFlat.copy(alpha = if (t.dark) 0.3f else 0.34f), 9.dp).press(0.9f) { nav.go(Route.NewChat()) },
                    contentAlignment = Alignment.Center,
                ) { SolidCompose(if (t.solid) t.plusInk else t.ink, if (t.solid) t.plusFlat else t.plusFlat, 27.dp) }
            }
        }

        ghostId?.let { id -> repo.convs[id] }?.let { c ->
            val w = whoOf(c)
            val half = with(density) { ghostSize.toPx() / 2 }
            Box(
                Modifier.zIndex(30f)
                    .offset { IntOffset((ghostPos.value.x - half).roundToInt(), (ghostPos.value.y - half).roundToInt()) }
                    .size(ghostSize)
                    .graphicsLayer { scaleX = ghostScale.value; scaleY = ghostScale.value; alpha = ghostAlpha.value }
                    .edge(CircleShape, elevation = 18.dp).clip(CircleShape),
            ) { Avatar(w.photo, w.ini, ghostSize, w.bg, w.ink, 21.sp) }
        }
        // While dragging: a hint at the bottom says what letting go will do.
        val hint = when {
            dragId == null || !dragMoved -> null
            dragFromRow && inZone -> if (pinnedIds.size >= 8) "Pinned is full (8 max)" else "Release to pin"
            dragFromRow -> "Drag up to pin"
            !inZone -> "Release to unpin"
            else -> "Release to place"
        }
        androidx.compose.animation.AnimatedVisibility(
            hint != null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 34.dp),
            enter = fadeIn(tween(150)) + androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(150)),
        ) {
            var lastHint by remember { mutableStateOf("") }
            if (hint != null) lastHint = hint
            val danger = lastHint == "Release to unpin" || lastHint.startsWith("Pinned is full")
            androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze) {
            Row(
                Modifier.height(46.dp).glassRim(RoundedCornerShape(23.dp)).frost(RoundedCornerShape(23.dp), if (danger) t.danger.copy(alpha = 0.16f) else t.fab.copy(alpha = if (t.dark) 0.2f else 0.32f), 10.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(if (danger) Icons.trash else Icons.pinned, if (danger) t.danger else t.p, 17.dp, 2f)
                Spacer(Modifier.width(8.dp))
                T(lastHint, size = 14.sp, weight = FontWeight.SemiBold, color = if (danger) t.danger else t.ink)
            }
            }
        }

        // Editing pins: a frosted bar with what you can do and a Done button.
        androidx.compose.animation.AnimatedVisibility(
            editPins && hint == null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 30.dp),
            enter = fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(140)),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze) {
                Row(
                    Modifier.height(52.dp).glassRim(RoundedCornerShape(26.dp)).frost(RoundedCornerShape(26.dp), t.fab.copy(alpha = if (t.dark) 0.2f else 0.32f), 10.dp)
                        .padding(start = 18.dp, end = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("Drag to reorder · tap − to unpin", size = 13.5.sp, color = t.soft)
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.fill).clickable { editPins = false }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("Done", size = 14.sp, weight = FontWeight.SemiBold, color = t.onFill) }
                }
            }
        }

        com.jpetrydesign.pyng.ui.kit.StatusScrim(Modifier.align(Alignment.TopCenter))
        RowMenu(menuFor, onDismiss = { menuFor = null }, onEditPins = { editPins = true })
        PermissionSheet(askPerms) { askPerms = false; repo.prefs.edit { it.copy(permsAsked = 1) } }
        if (nav.strip) ColorStrip(onDone = { nav.strip = false })
    }
}

fun swipeAct(repo: com.jpetrydesign.pyng.data.Repo, c: Conv, action: String) {
    val p = repo.prefs.state
    when (action) {
        "Archive" -> { repo.setArchived(c.id, true); repo.toast("Chat archived") { repo.setArchived(c.id, false) } }
        "Delete" -> repo.confirm("Delete this conversation?", "All of ${c.shortTitle}'s messages will be deleted from Google Messages on your phone too.") { repo.deleteConversation(c.id); repo.toast("Conversation deleted") }
        "Pin" -> if (repo.togglePin(c.id)) repo.toast(if (p.isPinned(c.id)) "Unpinned" else "Pinned to top")
        "Mute" -> { val on = !p.isMuted(c.id); repo.setMuted(c.id, on); repo.toast(if (on) "Muted" else "Notifications on") }
        "Read/Unread" -> { val was = c.unread || c.id in p.markedUnread; repo.toggleUnread(c.id); repo.toast(if (was) "Marked as read" else "Marked as unread") }
    }
}

@Composable
private fun FolderRow(icon: String, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(t.neutral), contentAlignment = Alignment.Center) { Ico(icon, t.neutralInk, 18.dp) }
        Spacer(Modifier.width(14.dp))
        T(label, size = 14.5.sp, weight = FontWeight.Medium, color = t.soft)
    }
}

/** A conversation row with swipe actions, long-press menu, typing and code chips. */
@Composable
fun ConvRow(
    c: Conv,
    index: Int,
    pinMark: Boolean,
    count: Int,
    unread: Boolean,
    typing: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onSwipe: (Int) -> Unit,
    lifted: Boolean = false,
    onLift: ((Offset, Offset, androidx.compose.ui.unit.Dp) -> Unit)? = null,
    home: DragHolder? = null,
) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val ctx = LocalContext.current
    val who = whoOf(c)
    val muted = c.id in p.muted
    val hot = unread && !muted
    val bold = p.on("boldText")
    val draft = p.drafts[c.id]?.takeIf { it.isNotBlank() }
    val compact = p.density == "Compact"
    val nameSize = 16.5f
    val prevSize = 14.5f
    val av = if (compact) 46.dp else 56.dp
    val density = LocalDensity.current
    val maxPx = with(density) { 150.dp.toPx() }
    val trigger = with(density) { 90.dp.toPx() }
    val leftOn = p.opt("swipe") != "Off"; val rightOn = p.opt("swipeR") != "Off"
    val dx = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val det = if (!c.fromMe && p.on("otpChip")) detect(c.preview) else Detected()
    val preview = when {
        typing -> if (c.group) "Someone is typing…" else "typing…"
        draft != null -> draft
        else -> (if (c.fromMe) "You: " else if (c.group && c.sender.isNotBlank()) c.sender.substringBefore(' ') + ": " else "") + repo.previewOf(c)
    }.replace(Regex("\\s*\\n\\s*"), " ")
    val action = if (dx.value > 0) p.opt("swipeR") else p.opt("swipe")
    val cfg = swipeCfg(action, c, p.isPinned(c.id), p.isMuted(c.id), unread, t)

    val rowCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val liftNow by androidx.compose.runtime.rememberUpdatedState(onLift)
    val menuNow by androidx.compose.runtime.rememberUpdatedState(onMenu)
    val openNow by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val seen = remember { SeenRows.contains(c.id) }
    androidx.compose.runtime.SideEffect { SeenRows.add(c.id) }
    Column(
        Modifier.then(if (index < 10 && !seen) Modifier.enter(index) else Modifier).fillMaxWidth()
            .onGloballyPositioned { rowCoords[0] = it }
            .graphicsLayer { alpha = if (lifted) 0.4f else 1f },
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (dx.value != 0f) 18.dp else 0.dp))) {
            if (dx.value != 0f) {
                Row(
                    Modifier.matchParentSize().background(cfg.third).padding(horizontal = 22.dp),
                    horizontalArrangement = if (dx.value > 0) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.graphicsLayer { alpha = (abs(dx.value) / trigger).coerceIn(0f, 1f) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ico(cfg.first, Color.White, 18.dp, 2f)
                            Spacer(Modifier.width(8.dp))
                            T(cfg.second, size = 13.sp, weight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().offset { IntOffset(dx.value.roundToInt(), 0) }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (dx.value != 0f) t.bg else if (selected) t.tone else Color.Transparent)
                    .pointerInput(c.id) {
                        detectTapGestures(onTap = { openNow() }, onLongPress = { at ->
                            val h = home?.home; val rc = rowCoords[0]; val lift = liftNow
                            if (lift != null && h != null && rc != null) runCatching {
                                val b = h.localBoundingBoxOf(rc)
                                val avPx = with(this) { av.toPx() }
                                lift(h.localPositionOf(rc, at), Offset(b.left + avPx / 2, b.center.y), av)
                            }.onFailure { menuNow() }
                            else menuNow()
                        })
                    }
                    .pointerInput(c.id, leftOn, rightOn) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val v = dx.value
                                if (v > trigger) onSwipe(1) else if (v < -trigger) onSwipe(-1)
                                scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                            },
                            onDragCancel = { scope.launch { dx.animateTo(0f) } },
                        ) { ch, d ->
                            ch.consume()
                            val next = (dx.value + d).coerceIn(if (leftOn) -maxPx else 0f, if (rightOn) maxPx else 0f)
                            scope.launch { dx.snapTo(next) }
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 9.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(who.photo, who.ini, av, who.bg, who.ink, 17.sp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                T(c.title, size = nameSize.sp, weight = if (hot || bold) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                if (pinMark) { Spacer(Modifier.width(6.dp)); Ico(Icons.pinned, t.p, 13.dp, 1.6f, fill = t.p) }
                                if (muted) { Spacer(Modifier.width(6.dp)); Ico(Icons.bellOff, t.faint, 13.dp, 2.2f) }
                            }
                            Spacer(Modifier.width(14.dp))
                            T(listTime(c.ts), size = 12.5.sp, color = if (hot) t.p else t.soft, weight = if (hot) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.read && p.on("receipts") && !typing && draft == null) { Ico(Icons.read, t.p, 16.dp, 2.2f); Spacer(Modifier.width(6.dp)) }
                            if (draft != null && !typing) { T("Draft:", size = 14.sp, weight = FontWeight.SemiBold, color = t.danger); Spacer(Modifier.width(6.dp)) }
                            T(
                                preview, size = prevSize.sp, maxLines = 1,
                                color = if (typing) t.p else if (hot) t.ink else t.soft,
                                weight = if (hot || typing || bold) FontWeight.Medium else FontWeight.Normal,
                                // Ends with "…" well before the badge and time column.
                                modifier = Modifier.weight(1f).padding(end = 14.dp),
                            )
                            if (count > 0) CountBadge(count, if (muted) t.neutral else t.badgeBg, if (muted) t.soft else t.badgeInk, 22.dp)
                        }
                    }
                }
                if (det.code != null) {
                    Row(Modifier.padding(start = av + 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(t.tone).clickable {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Code", det.code))
                                repo.toast("Code ${det.code} copied")
                            }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Ico(Icons.copy, t.p, 15.dp, 2f)
                            Spacer(Modifier.width(7.dp))
                            T("Copy ${det.code}", size = 13.sp, weight = FontWeight.SemiBold, color = t.p)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
    }
}

private fun swipeCfg(a: String, c: Conv, pinned: Boolean, muted: Boolean, unread: Boolean, t: com.jpetrydesign.pyng.ui.theme.Tok): Triple<String, String, Color> = when (a) {
    "Archive" -> Triple(Icons.archive, "Archive", t.fill)
    "Delete" -> Triple(Icons.trash, "Delete", t.danger)
    "Pin" -> Triple(Icons.pinned, if (pinned) "Unpin" else "Pin", Color(0xFFB7791F))
    "Mute" -> Triple(if (muted) Icons.bell else Icons.bellOff, if (muted) "Unmute" else "Mute", Color(0xFF5B6272))
    "Read/Unread" -> Triple(if (unread) Icons.read else Icons.unread, if (unread) "Read" else "Unread", t.pal.plus ?: Color(0xFF2E8B6E))
    else -> Triple(Icons.archive, "", Color.Transparent)
}

// ---------- pinned layouts ----------

/** Plain holders shared by the drag code; none of these trigger recomposition. */
class DragHolder {
    var home: LayoutCoordinates? = null
    var height = 0f
    var area: LayoutCoordinates? = null
    var zone: Rect? = null
    val slots = HashMap<Int, Rect>()
    val lastPos = HashMap<String, Offset>()
    var active = false
    var animUntil = 0L
    var hadSlot = false
    var onMove: (Offset) -> Unit = {}
    var onDrop: () -> Unit = {}
}

/** A thin bright edge and a soft, light shadow: reads as glass rather than a solid disc. */
private fun Modifier.glassRim(shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    val t = LocalTok.current
    this.shadow(3.dp, shape, clip = false, ambientColor = t.shadow, spotColor = t.shadow)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (t.dark) 0.28f else 0.75f), Color.White.copy(alpha = if (t.dark) 0.06f else 0.2f))), shape)
}

private class PinCtl(
    val dh: DragHolder,
    val dragId: String?,
    val open: (String) -> Unit,
    val lift: (String, Offset, Offset) -> Unit,
    val dropActive: Boolean = false,
    val shake: Float = 0f,
    val editing: Boolean = false,
    val unpin: (String) -> Unit = {},
)

/** Set while editing pins: each pinned avatar shows a minus that unpins it. */
private val LocalPinMinus = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

/** Wraps one pinned slot: records where it sits, opens on tap, lifts on long-press, and glides when the order changes. */
@Composable
private fun PinItem(pin: Pin, index: Int, ctl: PinCtl, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val id = pin.c.id
    val dh = ctl.dh
    val placeholder = ctl.dragId == id
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val open by androidx.compose.runtime.rememberUpdatedState(ctl.open)
    val lift by androidx.compose.runtime.rememberUpdatedState(ctl.lift)
    val editing by androidx.compose.runtime.rememberUpdatedState(ctl.editing)
    // A gentle wiggle while editing, each pin slightly out of step with its neighbours.
    val wiggle = if (ctl.editing && !placeholder) {
        val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "wiggle")
        tr.animateFloat(
            -1f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(130 + (index % 3) * 18, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "w",
        ).value
    } else 0f
    Box(
        modifier
            .onGloballyPositioned { c ->
                coords[0] = c
                val h = dh.home ?: return@onGloballyPositioned
                runCatching {
                    dh.slots[index] = h.localBoundingBoxOf(c)
                    val area = dh.area
                    if (area != null) {
                        val pos = area.localPositionOf(c, Offset.Zero)
                        val prev = dh.lastPos[id]
                        dh.lastPos[id] = pos
                        if (prev != null && System.currentTimeMillis() < dh.animUntil && (prev - pos).getDistance() > 2f) {
                            scope.launch {
                                shift.snapTo(shift.value + (prev - pos))
                                shift.animateTo(Offset.Zero, spring(dampingRatio = 0.78f, stiffness = 420f))
                            }
                        }
                    }
                }
            }
            .pointerInput(id) {
                detectTapGestures(onTap = { if (!editing) open(id) }, onLongPress = { at ->
                    val h = dh.home; val c = coords[0]
                    if (h != null && c != null) runCatching { lift(id, h.localPositionOf(c, at), h.localBoundingBoxOf(c).center) }
                })
            }
            .graphicsLayer {
                translationX = shift.value.x; translationY = shift.value.y
                rotationZ = wiggle * 2.4f
                alpha = if (placeholder) 0.28f else 1f
            },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPinMinus provides if (ctl.editing) ({ ctl.unpin(id) }) else null) { content() }
    }
}

@Composable
private fun PinFace(pin: Pin, av: Dp, iniSize: Float, style: String, badgeH: Dp = 21.dp) {
    val t = LocalTok.current
    val hot = pin.count > 0 || pin.typing
    Box(Modifier.size(av)) {
        Avatar(
            pin.who.photo, pin.who.ini, av, pin.who.bg, pin.who.ink, iniSize.sp,
            halo = if (style == "Rings" && hot) (t.pal.plus ?: t.p) else null,
        )
        if (pin.count > 0 && style != "Rings") {
            val d = LocalDensity.current
            val avPx = with(d) { av.roundToPx() }; val dx = with(d) { 5.dp.roundToPx() }; val dy = with(d) { 3.dp.roundToPx() }
            CountBadge(
                pin.count, h = badgeH, modifier = Modifier.align(Alignment.TopEnd).offset(5.dp, (-3).dp),
                backdrop = { Avatar(pin.who.photo, pin.who.ini, av, pin.who.bg, pin.who.ink, iniSize.sp) }, backdropSize = av,
                backdropAt = { sz -> IntOffset(-(avPx - sz.width + dx), dy) },
            )
        }
        LocalPinMinus.current?.let { unpin ->
            Box(
                Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape)
                    .background(if (t.dark) Color(0xFF3A3F4C) else Color.White).clickable(onClick = unpin),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(11.dp, 2.5.dp).clip(RoundedCornerShape(2.dp)).background(t.ink)) }
        }
        if (pin.typing) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(8.dp, 1.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(t.glassStrong).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) { TypingDots(t.p) }
        }
    }
}

@Composable
private fun PinName(pin: Pin, size: Float, max: Dp) {
    val hot = pin.count > 0 || pin.typing
    T(pin.c.shortTitle, size = size.sp, weight = if (hot) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, align = TextAlign.Center, modifier = Modifier.widthIn(max = max))
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PinnedArea(layout: String, all: List<Pin>, ctl: PinCtl, style: String) {
    val t = LocalTok.current
    val cardShape = RoundedCornerShape(32.dp)
    val cardMod = Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(cardShape).background(t.card).cardEdge(cardShape)
    val limit = mapOf("Grid" to 8, "Large" to 6, "Cards" to 6, "Spotlight" to 7)[layout] ?: 12
    val pins = all.take(limit)
    val dh = ctl.dh
    DisposableEffect(Unit) { onDispose { dh.zone = null; dh.area = null } }
    val glow by androidx.compose.animation.core.animateFloatAsState(if (ctl.dropActive) 1f else 0f, tween(160), label = "drop")
    Box(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { c -> dh.area = c; dh.home?.let { h -> runCatching { dh.zone = h.localBoundingBoxOf(c) } } }
            .graphicsLayer { translationX = ctl.shake * density; val k = 1f + 0.015f * glow; scaleX = k; scaleY = k }
            // Lights up while a dragged chat is over it.
            .then(if (glow > 0.01f) Modifier.border(2.dp, LocalTok.current.p.copy(alpha = 0.55f * glow), RoundedCornerShape(30.dp)) else Modifier),
    ) {
    when (layout) {
        "Large", "Grid" -> {
            val cols = if (layout == "Large") 3 else 4
            val av = if (layout == "Large") 88.dp else 74.dp
            Column(cardMod.padding(horizontal = if (layout == "Large") 10.dp else 8.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                pins.chunked(cols).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, pin ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                PinItem(pin, r * cols + i, ctl, Modifier.enter(r * cols + i, pop = true)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        PinFace(pin, av, if (layout == "Large") 26f else 22f, style)
                                        Spacer(Modifier.height(8.dp))
                                        PinName(pin, if (layout == "Large") 14f else 13f, if (layout == "Large") 100.dp else 80.dp)
                                    }
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        "Pages" -> {
            val pages = pins.chunked(4)
            val state = rememberPagerState { pages.size }
            Column(cardMod.padding(top = 20.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(state, Modifier.fillMaxWidth()) { pg ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        pages[pg].forEachIndexed { i, pin ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                PinItem(pin, pg * 4 + i, ctl, Modifier.enter(i, pop = true)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        PinFace(pin, 72.dp, 22f, style); Spacer(Modifier.height(8.dp)); PinName(pin, 13f, 82.dp)
                                    }
                                }
                            }
                        }
                        repeat(4 - pages[pg].size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size) { i ->
                        val on = state.currentPage == i
                        Box(Modifier.size(if (on) 22.dp else 7.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(if (on) t.p else t.faint))
                    }
                }
            }
        }
        "Carousel", "Compact" -> {
            val compact = layout == "Compact"
            val mod = if (compact) Modifier.fillMaxWidth().padding(bottom = 20.dp) else cardMod.padding(vertical = 18.dp)
            Row(mod.horizontalScroll(rememberScrollState()).padding(horizontal = if (compact) 2.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 4.dp)) {
                pins.forEachIndexed { i, pin ->
                    PinItem(pin, i, ctl, Modifier.width(if (compact) 56.dp else 80.dp).enter(i, pop = true)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            PinFace(pin, if (compact) 52.dp else 64.dp, if (compact) 17f else 20f, style)
                            Spacer(Modifier.height(8.dp))
                            PinName(pin, if (compact) 11.5f else 13f, if (compact) 58.dp else 80.dp)
                        }
                    }
                }
            }
        }
        "Chips" -> {
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pins.forEachIndexed { i, pin ->
                    PinItem(pin, i, ctl, Modifier.enter(i, pop = true)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(t.card).cardEdge(RoundedCornerShape(999.dp)).padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PinFace(pin, 34.dp, 12f, style, 16.dp)
                            Spacer(Modifier.width(9.dp))
                            PinName(pin, 14f, 120.dp)
                        }
                    }
                }
            }
        }
        else -> { // Cards and Spotlight
            val spot = layout == "Spotlight"
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val first = if (spot) pins.firstOrNull() else null
                if (first != null) {
                    PinItem(first, 0, ctl, Modifier.fillMaxWidth().enter(0, pop = true)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.card).cardEdge(RoundedCornerShape(24.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PinFace(first, 62.dp, 18f, style, 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                T(first.c.title, size = 14.5.sp, weight = FontWeight.Bold, maxLines = 1)
                                T(first.preview, size = 12.5.sp, color = t.soft, maxLines = 2, lineHeight = 17.sp)
                            }
                        }
                    }
                }
                val rest = if (spot) pins.drop(1) else pins
                val cols = if (spot) 3 else 2
                rest.chunked(cols).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { i, pin ->
                            PinItem(pin, (if (spot) 1 else 0) + r * cols + i, ctl, Modifier.weight(1f).enter(1 + r * cols + i, pop = true)) {
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.card).cardEdge(RoundedCornerShape(24.dp))
                                        .padding(if (spot) PaddingValues(horizontal = 6.dp, vertical = 14.dp) else PaddingValues(14.dp)),
                                    horizontalAlignment = if (spot) Alignment.CenterHorizontally else Alignment.Start,
                                ) {
                                    PinFace(pin, if (spot) 50.dp else 44.dp, 16f, style, 20.dp)
                                    Spacer(Modifier.height(10.dp))
                                    T(pin.c.shortTitle, size = 14.5.sp, weight = FontWeight.Bold, maxLines = 1, align = if (spot) TextAlign.Center else null)
                                    if (!spot) T(pin.preview, size = 12.5.sp, color = t.soft, maxLines = 2, lineHeight = 17.sp)
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    }
}

// ---------- long-press menu ----------

@Composable
fun RowMenu(c: Conv?, onDismiss: () -> Unit, onEditPins: (() -> Unit)? = null) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    var last by remember { mutableStateOf(c) }
    if (c != null) last = c
    val cur = last
    Sheet(visible = c != null, onDismiss = onDismiss) {
        if (cur == null) return@Sheet
        val p = repo.prefs.state
        val who = whoOf(cur)
        Row(Modifier.padding(horizontal = 22.dp, vertical = 6.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(who.photo, who.ini, 44.dp, who.bg, who.ink)
            Spacer(Modifier.width(12.dp))
            T(cur.title, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
        }
        val unread = cur.unread || cur.id in p.markedUnread
        SheetGroup {
            val acts = buildList<Triple<String, String, () -> Unit>> {
                if (onEditPins != null && p.isPinned(cur.id)) add(Triple("Edit pins", Icons.pencil) { onEditPins() })
                add(Triple(if (p.isPinned(cur.id)) "Unpin" else "Pin to top", Icons.pinned) { val was = p.isPinned(cur.id); if (repo.togglePin(cur.id)) repo.toast(if (was) "Unpinned" else "Pinned to top") })
                add(Triple(if (cur.status == "archived") "Unarchive" else "Archive", Icons.archive) {
                    val on = cur.status != "archived"
                    repo.setArchived(cur.id, on)
                    repo.toast(if (on) "Chat archived" else "Moved back to your inbox", if (on) ({ repo.setArchived(cur.id, false) }) else null)
                })
                add(Triple(if (p.isMuted(cur.id)) "Unmute" else "Mute", if (p.isMuted(cur.id)) Icons.bell else Icons.bellOff) { val on = !p.isMuted(cur.id); repo.setMuted(cur.id, on); repo.toast(if (on) "Muted" else "Notifications on") })
                add(Triple(if (unread) "Mark as read" else "Mark as unread", if (unread) Icons.read else Icons.unread) { repo.toggleUnread(cur.id) })
                if (!cur.group) add(Triple("Block", Icons.block) { repo.setBlocked(cur.id, true); repo.toast("Blocked ${cur.shortTitle}") { repo.setBlocked(cur.id, false) } })
            }
            acts.forEachIndexed { i, (label, icon, go) -> SheetAction(label, icon, { go(); onDismiss() }, first = i == 0, index = i) }
            SheetAction("Delete", Icons.trash, { onDismiss(); repo.confirm("Delete this conversation?", "All of ${cur.shortTitle}'s messages will be deleted from Google Messages on your phone too.") { repo.deleteConversation(cur.id); repo.toast("Conversation deleted") } }, danger = true, index = acts.size)
        }
    }
}

@Suppress("unused")
private fun Modifier.ring(on: Boolean, color: Color) = if (on) this.border(2.dp, color, CircleShape) else this

/** A filled chat bubble with a plus cut through it, centered in its box. */
@Composable
fun SolidCompose(ink: Color, hole: Color, size: Dp) {
    // Drawn on its own layer so the plus can be punched out of the bubble.
    androidx.compose.foundation.Canvas(Modifier.size(size).graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            val bubble = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12 3.2c-4.9 0-8.8 3.6-8.8 8.1 0 2 .8 3.9 2.1 5.3l-.9 3.6 3.9-1.6c1.1.5 2.4.8 3.7.8 4.9 0 8.8-3.6 8.8-8.1S16.9 3.2 12 3.2z").toPath()
            drawPath(bubble, ink)
            val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(hole, Offset(12f, 7.8f), Offset(12f, 14.8f), strokeWidth = st.width, cap = st.cap, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            drawLine(hole, Offset(8.5f, 11.3f), Offset(15.5f, 11.3f), strokeWidth = st.width, cap = st.cap, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        }
    }
}

/** A bold magnifier with a filled lens ring. */
@Composable
fun SolidSearch(ink: Color, size: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawCircle(ink, radius = 6.6f, center = Offset(10.5f, 10.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.8f))
            drawLine(ink, Offset(15.4f, 15.4f), Offset(20.2f, 20.2f), strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Rows that already played their entrance this session, so scrolling doesn't replay it. */
object SeenRows {
    private val ids = HashSet<String>()
    fun contains(id: String) = id in ids
    fun add(id: String) { ids += id }
}
