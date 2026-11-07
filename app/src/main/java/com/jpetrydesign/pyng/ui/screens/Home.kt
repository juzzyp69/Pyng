package com.jpetrydesign.pyng.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
    val now = System.currentTimeMillis()

    LaunchedEffect(Unit) { if (repo.convs.isEmpty() || !repo.hub.engine.isConnected) repo.refresh() }

    val all = repo.convs.values.toList()
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
    val pins = pinnedIds.mapNotNull { id -> active.firstOrNull { it.id == id } }.map { c ->
        Pin(c, whoOf(c, repo, t), countOf(c), typingOf(c), (if (c.fromMe) "You: " else if (c.group && c.sender.isNotBlank()) c.sender.substringBefore(' ') + ": " else "") + c.preview)
    }
    val layout = p.pinLayout
    val rows = (if (layout == "List") pins.map { it.c } else emptyList()) + active.filter { it.id !in pinnedIds }.sortedByDescending { it.ts }
    val archCount = all.count { it.status == "archived" }
    val spamCount = all.count { it.status == "spam" || it.status == "blocked" } + spamLocal.size
    val newCount = active.count { unreadOf(it) && it.id !in p.muted }

    // Drag-to-reorder for pinned chats.
    val slots = remember { mutableStateMapOf<String, Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var dragMoved by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val pinCtl = PinCtl(
        slots = slots, dragId = dragId, pointer = dragPointer, grab = grab,
        open = { id -> nav.openChat(id) },
        lift = { id, at ->
            dragId = id; dragMoved = false
            val r = slots[id]
            grab = if (r != null) at - r.center else Offset.Zero
            dragPointer = at
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        move = { at ->
            val id = dragId
            if (id != null) {
                if ((at - dragPointer).getDistance() > 4f) dragMoved = true
                dragPointer = at
                val target = at - grab
                val order = pinnedIds.toMutableList()
                val best = order.minByOrNull { o -> slots[o]?.center?.let { (it - target).getDistanceSquared() } ?: Float.MAX_VALUE }
                if (best != null && best != id) {
                    val from = order.indexOf(id); val to = order.indexOf(best)
                    if (from >= 0 && to >= 0) {
                        order.removeAt(from); order.add(to, id)
                        val rest = p.pinned.filter { it !in order }
                        repo.prefs.edit { it.copy(pinned = order + rest) }
                    }
                }
            }
        },
        drop = {
            val id = dragId
            dragId = null
            if (id != null) {
                if (dragMoved) repo.toast("Pinned order saved") else menuFor = repo.convs[id]
            }
        },
    )

    Box(Modifier.fillMaxSize()) {
        Glow()
        androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze) {
            LazyColumn(
                Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
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
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(26.dp)).background(t.card).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.pinned, t.p, 20.dp) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                T("Pin your favorite people", size = 14.5.sp, weight = FontWeight.SemiBold)
                                T("Hold any conversation and tap Pin. Up to 8 show here as big circles.", size = 12.5.sp, color = t.soft, lineHeight = 17.sp)
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
                        .height(60.dp).edge(RoundedCornerShape(30.dp)).frost(RoundedCornerShape(30.dp), t.fab)
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pins.forEachIndexed { i, pin ->
                        PinItem(pin, pinCtl, Modifier.enter(i, pop = true)) {
                            Box(Modifier.size(46.dp)) {
                                Avatar(pin.who.photo, pin.who.ini, 46.dp, pin.who.bg, pin.who.ink, 15.sp)
                                if (pin.count > 0) CountBadge(pin.count, h = 18.dp, modifier = Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp))
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(60.dp).edge(CircleShape).frost(CircleShape, t.fab).press(0.9f) { nav.go(Route.Search) },
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.search, t.ink, 23.dp, 2.5f) }
                Box(
                    Modifier.size(60.dp).edge(CircleShape, primary = true).frost(CircleShape, t.fabP).press(0.9f) { nav.go(Route.NewChat()) },
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.compose, t.plusInk, 25.dp, 2.1f) }
            }
        }

        RowMenu(menuFor, onDismiss = { menuFor = null })
        if (nav.strip) ColorStrip(onDone = { nav.strip = false })
    }
}

fun swipeAct(repo: com.jpetrydesign.pyng.data.Repo, c: Conv, action: String) {
    val p = repo.prefs.state
    when (action) {
        "Archive" -> { repo.setArchived(c.id, true); repo.toast("Chat archived") { repo.setArchived(c.id, false) } }
        "Delete" -> repo.deleteConversation(c.id).also { repo.toast("Conversation deleted") }
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
    val nameSize = mapOf("S" to 14.5f, "M" to 15.5f, "L" to 17f, "XL" to 18.5f)[p.textSize] ?: 15.5f
    val prevSize = mapOf("S" to 13f, "M" to 14f, "L" to 15.5f, "XL" to 17f)[p.textSize] ?: 14f
    val av = if (compact) 40.dp else 48.dp
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
        else -> (if (c.fromMe) "You: " else if (c.group && c.sender.isNotBlank()) c.sender.substringBefore(' ') + ": " else "") + c.preview
    }
    val action = if (dx.value > 0) p.opt("swipeR") else p.opt("swipe")
    val cfg = swipeCfg(action, c, p.isPinned(c.id), p.isMuted(c.id), unread, t)

    Column(Modifier.enter(index).fillMaxWidth()) {
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
                    .background(if (dx.value != 0f) t.bg else if (selected) t.tone else Color.Transparent)
                    .pointerInput(c.id) {
                        detectTapGestures(onTap = { onOpen() }, onLongPress = { onMenu() })
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
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(who.photo, who.ini, av, who.bg, who.ink, 15.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(c.title, size = nameSize.sp, weight = if (hot || bold) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            if (pinMark) { Spacer(Modifier.width(6.dp)); Ico(Icons.pinned, t.p, 13.dp, 1.6f, fill = t.p) }
                            if (muted) { Spacer(Modifier.width(6.dp)); Ico(Icons.bellOff, t.faint, 13.dp, 2.2f) }
                            Spacer(Modifier.weight(1f))
                            T(listTime(c.ts), size = 12.5.sp, color = if (hot) t.p else t.soft, weight = if (hot) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.read && p.on("receipts") && !typing && draft == null) { Ico(Icons.read, t.p, 16.dp, 2.2f); Spacer(Modifier.width(6.dp)) }
                            if (draft != null && !typing) { T("Draft:", size = 14.sp, weight = FontWeight.SemiBold, color = t.danger); Spacer(Modifier.width(6.dp)) }
                            T(
                                preview, size = prevSize.sp, maxLines = 1,
                                color = if (typing) t.p else if (hot) t.ink else t.soft,
                                weight = if (hot || typing || bold) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (count > 0) {
                                Spacer(Modifier.width(6.dp))
                                CountBadge(count, if (muted) t.neutral else t.badgeBg, if (muted) t.soft else t.badgeInk, 22.dp)
                            }
                        }
                    }
                }
                if (det.code != null) {
                    Row(Modifier.padding(start = 62.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
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

private class PinCtl(
    val slots: MutableMap<String, Rect>,
    val dragId: String?,
    val pointer: Offset,
    val grab: Offset,
    val open: (String) -> Unit,
    val lift: (String, Offset) -> Unit,
    val move: (Offset) -> Unit,
    val drop: () -> Unit,
)

/** Wraps one pinned item: records its slot, opens on tap, lifts and drags on long-press. */
@Composable
private fun PinItem(pin: Pin, ctl: PinCtl, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val id = pin.c.id
    val dragging = ctl.dragId == id
    val k by animateFloatAsState(if (dragging) 1.08f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .zIndex(if (dragging) 6f else 0f)
            .onGloballyPositioned { coords -> val r = coords.boundsInRoot(); ctl.slots[id] = r; origin = r.topLeft }
            .graphicsLayer {
                scaleX = k; scaleY = k
                if (dragging) {
                    val r = ctl.slots[id]
                    if (r != null) { val target = ctl.pointer - ctl.grab; translationX = target.x - r.center.x; translationY = target.y - r.center.y }
                }
            }
            .pointerInput(id) { detectTapGestures(onTap = { ctl.open(id) }, onLongPress = { }) }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> ctl.lift(id, origin + at) },
                    onDragEnd = { ctl.drop() },
                    onDragCancel = { ctl.drop() },
                ) { ch, _ -> ch.consume(); ctl.move(origin + ch.position) }
            },
    ) { content() }
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
        if (pin.count > 0 && style != "Rings") CountBadge(pin.count, h = badgeH, modifier = Modifier.align(Alignment.TopEnd).offset(5.dp, (-3).dp))
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
    when (layout) {
        "Large", "Grid" -> {
            val cols = if (layout == "Large") 3 else 4
            val av = if (layout == "Large") 86.dp else 68.dp
            Column(cardMod.padding(horizontal = if (layout == "Large") 10.dp else 8.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                pins.chunked(cols).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, pin ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                PinItem(pin, ctl, Modifier.enter(r * cols + i, pop = true)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        PinFace(pin, av, if (layout == "Large") 26f else 21f, style)
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
                                PinItem(pin, ctl, Modifier.enter(i, pop = true)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        PinFace(pin, 68.dp, 21f, style); Spacer(Modifier.height(8.dp)); PinName(pin, 13f, 80.dp)
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
                    PinItem(pin, ctl, Modifier.width(if (compact) 56.dp else 80.dp).enter(i, pop = true)) {
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
                    PinItem(pin, ctl, Modifier.enter(i, pop = true)) {
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
                    PinItem(first, ctl, Modifier.fillMaxWidth().enter(0, pop = true)) {
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
                            PinItem(pin, ctl, Modifier.weight(1f).enter(1 + r * cols + i, pop = true)) {
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

// ---------- long-press menu ----------

@Composable
fun RowMenu(c: Conv?, onDismiss: () -> Unit) {
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
            SheetAction("Delete", Icons.trash, { repo.deleteConversation(cur.id); repo.toast("Conversation deleted"); onDismiss() }, danger = true, index = acts.size)
        }
    }
}

@Suppress("unused")
private fun Modifier.ring(on: Boolean, color: Color) = if (on) this.border(2.dp, color, CircleShape) else this
