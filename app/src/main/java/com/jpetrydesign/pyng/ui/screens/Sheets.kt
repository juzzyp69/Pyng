package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val motion = com.jpetrydesign.pyng.ui.kit.LocalMotion.current
    var last by remember { mutableStateOf(m) }
    var lastAnchor by remember { mutableStateOf(anchor) }
    var lastRow by remember { mutableStateOf(rowCtx) }
    if (m != null) { last = m; lastAnchor = anchor; lastRow = rowCtx }
    var more by remember(m) { mutableStateOf(false) }
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
                Modifier.heightIn(max = 300.dp).clip(RoundedCornerShape(20.dp))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                contentAlignment = if (me) Alignment.TopEnd else Alignment.TopStart,
            ) {
                if (rc != null) MenuBubble(cur, rc)
                else T(msgPreview(cur), size = 15.sp, color = if (me) t.outInk else t.inInk, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(t.inBg).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        val actions: @Composable () -> Unit = {
            Column(
                Modifier.width(232.dp).shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(t.sheet)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
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
                if (cur.id.isNotEmpty()) item("Reply", Icons.reply) { onReply(cur) }
                item("Forward", Icons.forward) { onForward(cur) }
                if (cur.text.isNotEmpty()) item("Copy", Icons.copy) { onCopy(cur) }
                item(if (p.isStarred(cur.key)) "Unstar" else "Star", Icons.star) { onStar(cur) }
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
                    Avatar(w.photo, w.ini, 42.dp, w.bg, w.ink)
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
            Avatar(w.photo, w.ini, 64.dp, w.bg, w.ink, 22.sp)
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
