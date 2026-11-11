package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.EmojiData
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.SheetAction
import com.jpetrydesign.pyng.ui.kit.SheetGroup
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.Tok
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Long-press on a message, like Google Messages: the chat blurs, the message stays where it was,
 * reactions sit above it (with an emoji button for the full set) and actions sit below.
 */
@Composable
fun MessageMenu(
    m: Msg?,
    t: Tok,
    conv: Conv?,
    anchor: androidx.compose.ui.geometry.Rect?,
    rowCtx: RowCtx?,
    onDismiss: () -> Unit,
    onReact: (Msg, String) -> Unit,
    onReply: (Msg) -> Unit,
    onCopy: (Msg) -> Unit,
    onForward: (Msg) -> Unit,
    onStar: (Msg) -> Unit,
    onSelect: (Msg) -> Unit,
    onDetails: (Msg) -> Unit,
    onDelete: (Msg) -> Unit,
    onSelectText: (Msg) -> Unit = {},
    /** The photos sent together with this one, if it's part of a group. */
    group: List<Pair<Msg, com.jpetrydesign.pyng.data.MediaRef>>? = null,
    onSave: (List<com.jpetrydesign.pyng.data.MediaRef>) -> Unit = {},
    onShare: (List<com.jpetrydesign.pyng.data.MediaRef>) -> Unit = {},
    onForwardAll: (List<Msg>) -> Unit = {},
) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val motion = com.jpetrydesign.pyng.ui.kit.LocalMotion.current
    var last by remember { mutableStateOf(m) }
    var lastAnchor by remember { mutableStateOf(anchor) }
    var lastRow by remember { mutableStateOf(rowCtx) }
    var lastGroup by remember { mutableStateOf(group) }
    if (m != null) { last = m; lastAnchor = anchor; lastRow = rowCtx; lastGroup = group?.takeIf { it.size > 1 } }
    var more by remember(m) { mutableStateOf(false) }
    var reminding by remember(m) { mutableStateOf(false) }
    if (m != null) androidx.activity.compose.BackHandler { if (more) more = false else onDismiss() }
    val k = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(m != null) {
        if (m != null) { if (motion) k.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) else k.snapTo(1f) }
        else if (motion) k.animateTo(0f, androidx.compose.animation.core.tween(160)) else k.snapTo(0f)
    }
    if (m == null && k.value <= 0.01f) return
    val cur = last ?: return
    val p = repo.prefs.state
    val myId = conv?.me?.id.orEmpty()
    val mine = cur.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji
    val canReact = cur.id.isNotEmpty() && conv?.rcs == true
    val me = cur.fromMe
    val density = androidx.compose.ui.platform.LocalDensity.current
    val close = { more = false; onDismiss() }

    Box(
        Modifier.fillMaxSize().zIndex(25f)
            .graphicsLayer { alpha = k.value.coerceIn(0f, 1f) }
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (t.dark) 0.45f else 0.22f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { close() },
    ) {
        val topSafe = WindowInsets.statusBars.getTop(density) + with(density) { 12.dp.roundToPx() }
        val bottomSafe = WindowInsets.navigationBars.getBottom(density) + with(density) { 12.dp.roundToPx() }
        val bar: @Composable () -> Unit = {
            if (canReact) {
                if (!more) {
                    Row(
                        Modifier.shadow(10.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(t.sheet)
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        p.quickReacts.forEachIndexed { i, e ->
                            Box(
                                Modifier.enter(i, pop = true).size(46.dp).clip(CircleShape).background(if (mine == e) t.tone else androidx.compose.ui.graphics.Color.Transparent)
                                    .press(0.8f) { onReact(cur, e); close() },
                                contentAlignment = Alignment.Center,
                            ) { T(e, size = 27.sp) }
                        }
                        Box(Modifier.size(46.dp).clip(CircleShape).clickable { more = true }, contentAlignment = Alignment.Center) { Ico(Icons.emojiAdd, t.soft, 24.dp, 1.8f) }
                    }
                } else {
                    Box(
                        Modifier.width(340.dp).height(300.dp).shadow(10.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(t.sheet)
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                    ) {
                        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize().padding(8.dp)) {
                            items(EmojiData.cats.flatMap { it.items }.map { it.first }) { e ->
                                Box(Modifier.aspectRatio(1f).clip(CircleShape).clickable { onReact(cur, e); close() }, contentAlignment = Alignment.Center) { T(e, size = 24.sp) }
                            }
                        }
                    }
                }
            }
        }
        val bubble: @Composable () -> Unit = {
            val rc = lastRow
            val a = lastAnchor
            Box(
                Modifier.graphicsLayer { val s = 1f + 0.035f * k.value; scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (me) 1f else 0f, 0.5f) }
                    .heightIn(max = 300.dp).clip(RoundedCornerShape(20.dp))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                contentAlignment = if (me) Alignment.TopEnd else Alignment.TopStart,
            ) {
                val g = lastGroup
                if (g != null) GatherStack(g, cur.key, t)
                else if (rc != null) MenuBubble(cur, rc)
                else T(msgPreview(cur), size = 15.sp, color = if (me) t.outInk else t.inInk, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(t.inBg).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        val actions: @Composable () -> Unit = {
            Column(
                Modifier.width(232.dp).heightIn(max = 430.dp).shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(t.sheet)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                var i = 0
                @Composable fun item(label: String, icon: String, danger: Boolean = false, go: () -> Unit) {
                    Row(
                        Modifier.enter(i++).fillMaxWidth().clickable { go(); close() }.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(icon, if (danger) t.danger else t.ink, 21.dp)
                        Spacer(Modifier.width(16.dp))
                        T(label, size = 15.5.sp, weight = FontWeight.Medium, color = if (danger) t.danger else t.ink)
                    }
                }
                val g = lastGroup
                val files = cur.media.filter { !it.isContact }
                val links = linksIn(cur.text)
                if (cur.id.isNotEmpty()) item("Reply", Icons.reply) { onReply(cur) }
                if (g != null) {
                    val kind = if (g.all { it.second.isVideo }) "videos" else if (g.any { it.second.isVideo }) "items" else "photos"
                    item("Save all ${g.size} $kind", Icons.download) { onSave(g.map { it.second }) }
                    item("Share all", Icons.share) { onShare(g.map { it.second }) }
                    item("Forward all", Icons.forward) { onForwardAll(g.map { it.first }.distinctBy { it.key }) }
                } else {
                    item("Forward", Icons.forward) { onForward(cur) }
                    if (files.isNotEmpty()) {
                        item("Save", Icons.download) { onSave(files) }
                        item("Share", Icons.share) { onShare(files) }
                    }
                }
                if (links.isNotEmpty()) {
                    item(if (links.size > 1) "Copy first link" else "Copy link", Icons.link) { copy(ctx, links.first()); repo.toast("Link copied") }
                    item("Share link", Icons.share) {
                        runCatching { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, links.first()), "Share link")) }
                    }
                }
                if (cur.text.isNotEmpty()) item(if (links.isNotEmpty() && cur.text.trim() != links.first()) "Copy text" else if (links.isNotEmpty()) "Copy all" else "Copy", Icons.copy) { onCopy(cur) }
                if (cur.text.length > 1) item("Select text", Icons.select) { onSelectText(cur) }
                item(if (p.isStarred(cur.key)) "Unstar" else "Star", Icons.star) { onStar(cur) }
                // Remind me: opens up into three times right here in the menu.
                if (conv != null && cur.id.isNotEmpty()) {
                    Row(
                        Modifier.enter(i++).fillMaxWidth().clickable { reminding = !reminding }.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(Icons.bell, t.ink, 21.dp)
                        Spacer(Modifier.width(16.dp))
                        T("Remind me", size = 15.5.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Ico(if (reminding) "M6 15l6-6 6 6" else "M6 9l6 6 6-6", t.soft, 16.dp, 2.2f)
                    }
                    if (reminding) com.jpetrydesign.pyng.data.ReminderWorker.choices().forEach { (label, at) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val who = if (cur.fromMe) "You" else conv.people.firstOrNull { it.id == cur.participantId }?.short ?: conv.shortTitle
                                com.jpetrydesign.pyng.data.ReminderWorker.plan(ctx, conv.id, cur.id, conv.title, (if (conv.group || cur.fromMe) "$who: " else "") + cur.text.ifBlank { cur.summary }, at)
                                repo.toast("Reminder set for " + (if (label == "In 1 hour") clock(at) else label.substringAfter(" at ").let { l -> if (label.startsWith("Tomorrow")) "tomorrow at $l" else "tonight at $l" }))
                                close()
                            }.padding(start = 55.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
                        ) { T(label, size = 14.5.sp, color = t.p, weight = FontWeight.Medium) }
                    }
                }
                if (p.on("readAloud") && cur.text.isNotEmpty()) item("Read aloud", Icons.volume) { Speaker.say(ctx, cur.text) }
                item("Delete", Icons.trash, danger = true) { onDelete(cur) }
                item("Select more", Icons.select) { onSelect(cur) }
                item("Info", Icons.details) { onDetails(cur) }
            }
        }
        val gap = with(density) { 10.dp.roundToPx() }
        val side = with(density) { 12.dp.roundToPx() }
        androidx.compose.ui.layout.Layout(
            contents = listOf(bar, bubble, actions),
            modifier = Modifier.fillMaxSize(),
        ) { (barM, bubM, actM), cons ->
            val loose = cons.copy(minWidth = 0, minHeight = 0)
            val b = barM.firstOrNull()?.measure(loose)
            // Same width limit as bubbles in the chat, so the text wraps the same way.
            val maxBub = minOf((cons.maxWidth * 0.74f).toInt(), with(density) { 420.dp.roundToPx() })
            val u = bubM.first().measure(loose.copy(maxWidth = maxBub))
            val ac = actM.first().measure(loose)
            layout(cons.maxWidth, cons.maxHeight) {
                val H = cons.maxHeight; val W = cons.maxWidth
                val top0 = lastAnchor?.top?.toInt() ?: (H / 3)
                val barH = if (b != null) b.height + gap else 0
                val total = barH + u.height + gap + ac.height
                var y = top0 - barH
                if (y + total > H - bottomSafe) y = H - bottomSafe - total
                if (y < topSafe) y = topSafe
                val a = lastAnchor
                val bubX = (if (a != null) (if (me) a.right.toInt() - u.width else a.left.toInt()) else if (me) W - side - u.width else side).coerceIn(0, maxOf(0, W - u.width))
                val edgeX = { w: Int -> if (me) (bubX + u.width - w).coerceIn(side, maxOf(side, W - side - w)) else bubX.coerceIn(side, maxOf(side, W - side - w)) }
                val lift = ((1f - k.value) * 24f).toInt()
                b?.placeRelative(edgeX(b.width), y + lift)
                u.placeRelative(bubX, y + barH)
                ac.placeRelative(edgeX(ac.width), y + barH + u.height + gap - lift)
            }
        }
    }
}

@Composable
fun DetailsSheet(m: Msg?, conv: Conv?, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    Sheet(visible = m != null, onDismiss = onDismiss) {
        val cur = last ?: return@Sheet
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val rows = buildList {
            add("Type" to when {
                cur.isSys -> "Update"
                conv?.rcs == true && !cur.isSms && !cur.isMms -> "Chat message (RCS)"
                cur.isMms || cur.media.isNotEmpty() -> "Picture message (MMS)"
                else -> "Text message (SMS)"
            })
            add((if (cur.fromMe) "To" else "From") to if (cur.fromMe) (conv?.title ?: "") else cur.sender.ifBlank { conv?.title ?: "" })
            if (cur.ts > 0) add((if (cur.fromMe) "Sent" else "Received") to fmt.format(cur.ts))
            if (cur.fromMe) add("Status" to when {
                cur.failed -> "Not sent" + (cur.error.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
                cur.pending -> "Sending"
                cur.readByThem -> "Read"
                cur.delivered -> "Delivered"
                else -> "Sent"
            })
            cur.media.forEach { add("Attachment" to (it.name.ifBlank { it.mime } + if (it.size > 0) " · " + sizeText(it.size) else "")) }
            if (cur.reactions.isNotEmpty()) add("Reactions" to cur.reactions.joinToString("  ") { it.emoji + if (it.count > 1) " ${it.count}" else "" })
        }
        T("Message details", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        SheetGroup {
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp)) {
                    T(k, size = 14.sp, color = t.soft, modifier = Modifier.width(100.dp))
                    T(v, size = 14.sp, weight = FontWeight.Medium)
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION") repo
    }
}

@Composable
fun ForwardSheet(m: Msg?, onPick: (String?, Msg) -> Unit) {
    val repo = LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    Sheet(visible = m != null, onDismiss = { last?.let { onPick(null, it) } }, maxHeightFraction = 0.8f) {
        val cur = last ?: return@Sheet
        T("Forward to", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        val convs = repo.convs.values.filter { it.status == "active" }.sortedByDescending { it.ts }
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
            items(convs, key = { it.id }) { c ->
                val w = whoOf(c)
                Row(Modifier.fillMaxWidth().clickable { onPick(c.id, cur) }.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WhoAvatar(w, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    T(c.title, size = 15.5.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    Ico(Icons.send, t.p, 18.dp, 2f)
                }
            }
        }
    }
}

@Composable
fun CallSheet(open: Boolean, conv: Conv?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    Sheet(visible = open, onDismiss = onDismiss) {
        val c = conv ?: return@Sheet
        val w = whoOf(c)
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WhoAvatar(w, 64.dp, 22.sp)
            Spacer(Modifier.height(8.dp))
            T(c.title, size = 18.sp, weight = FontWeight.SemiBold)
            T(c.number, size = 13.5.sp, color = t.soft)
        }
        SheetGroup {
            SheetAction("Voice call", Icons.phone, { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' }))) }; onDismiss() }, first = true)
            SheetAction("Video call", Icons.video, {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' })).putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", 3))
                }
                onDismiss()
            }, index = 1)
            SheetAction("Copy number", Icons.copy, { copy(ctx, c.number); onDismiss() }, index = 2)
        }
    }
}

/** Reads a message out loud with the phone's text-to-speech voice. */
object Speaker {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun say(ctx: android.content.Context, text: String) {
        val cur = tts
        if (cur != null && ready) { cur.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "msg"); return }
        pending = text
        if (cur == null) tts = android.speech.tts.TextToSpeech(ctx.applicationContext) { status ->
            ready = status == android.speech.tts.TextToSpeech.SUCCESS
            if (ready) pending?.let { tts?.speak(it, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "msg") }
            pending = null
        }
    }
}


/** Shows a message's words so you can pick just the part you want and copy it. */
@Composable
fun SelectTextSheet(m: Msg?, onDismiss: () -> Unit) {
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    val ctx = LocalContext.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    com.jpetrydesign.pyng.ui.kit.Sheet(m != null, onDismiss) {
        val cur = last ?: return@Sheet
        T("Select text", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 2.dp))
        T("Hold a word, then drag the handles to choose what you want. Copy shows up above it.", size = 12.5.sp, color = t.soft, lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(t.surface)
                .heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.material3.Text(
                    cur.text,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = 17.sp, lineHeight = 25.sp, color = t.ink),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        com.jpetrydesign.pyng.ui.kit.PillButton("Copy all", { copy(ctx, cur.text); onDismiss() }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), primary = false, height = 50.dp)
    }
}
