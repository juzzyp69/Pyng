package com.jpetrydesign.pyng.ui.screens

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import com.jpetrydesign.pyng.ui.kit.edge
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withLink
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.layout.findRootCoordinates
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.swipeDir
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything a single message row needs from the thread. */
data class RowCtx(
    val t: Tok,
    val group: Boolean,
    val prevSame: Boolean,
    val nextSame: Boolean,
    val fontSize: Float,
    val shape: Pair<Dp, Dp>,
    val info: String?,
    val infoIcon: String,
    val infoInk: Color,
    val selecting: Boolean,
    val selected: Boolean,
    val highlighted: Boolean,
    val quote: Pair<String, String>?,
    val senderName: String?,
    val senderWho: Who?,
    val starred: Boolean,
    val chips: List<Triple<String, String, () -> Unit>>,
    val showPreview: Boolean,
    val tsShift: Float,
    val enterDelay: Int,
    val timeLabel: String,
    val isNew: Boolean,
    val burst: String?,
    val myId: String,
    /** This person's name color in a group chat, picked from the chat's theme. */
    val nameColor: Color? = null,
    /** Photos and videos sent together, drawn as one swipeable strip in this row. */
    val bundle: List<Pair<Msg, MediaRef>>? = null,
    /** The status always shown under your newest message (text, icon, read), and their face for the Face style. */
    val autoStatus: Triple<String, String, Boolean>? = null,
    val statusFace: Who? = null,
    /** Why it didn't send, when there's a clearer reason than "Not sent". */
    val failLabel: String? = null,
    /** "Sent as SMS" and the like, for texts that went out (or came in) without RCS in an RCS chat. */
    val kindTag: String? = null,
)

class RowActs(
    val onSide: () -> Unit,
    val onTap: () -> Unit,
    val onDouble: () -> Unit,
    val onLong: () -> Unit,
    val onReply: () -> Unit,
    val onOpenMedia: (MediaRef) -> Unit,
    val onRetry: () -> Unit,
    val onPlaced: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {},
    /** For a strip: open, hold or double-tap one photo in it. */
    val onOpenItem: ((Msg, MediaRef) -> Unit)? = null,
    val onLongItem: ((Msg) -> Unit)? = null,
    val onDoubleItem: ((Msg) -> Unit)? = null,
    val onPlacedKey: ((String, androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
)

fun bubbleShape(me: Boolean, prev: Boolean, next: Boolean, big: Dp, small: Dp): RoundedCornerShape =
    if (me) RoundedCornerShape(topStart = big, topEnd = if (prev) small else big, bottomEnd = if (next) small else big, bottomStart = big)
    else RoundedCornerShape(topStart = if (prev) small else big, topEnd = big, bottomEnd = big, bottomStart = if (next) small else big)

@Composable
fun MessageRow(m: Msg, x: RowCtx, a: RowActs) {
    val t = x.t
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val me = m.fromMe
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val trigger = with(density) { 56.dp.toPx() }
    val maxPx = with(density) { 84.dp.toPx() }
    val animate = motion && (x.isNew || x.enterDelay >= 0)
    val enterA = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enterA.value < 1f) {
            if (!x.isNew && x.enterDelay > 0) delay(x.enterDelay.toLong())
            enterA.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f))
        }
    }
    val shape = bubbleShape(me, x.prevSame, x.nextSame, x.shape.first, x.shape.second)
    val jumbo = if (m.media.isEmpty() && x.quote == null) jumboCount(m.text) else 0
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Pressing a bubble shrinks it a touch; holding hands it to the menu with a buzz.
    val press = remember { Animatable(1f) }

    Column(Modifier.fillMaxWidth()) {
        if (x.senderName != null) {
            T(x.senderName, size = 12.sp, weight = FontWeight.SemiBold, color = x.nameColor ?: t.soft, modifier = Modifier.padding(start = if (x.group) 46.dp else 14.dp, top = 14.dp))
        }
        Box(
            // Tight within a run from one person, more room when the sender changes.
            Modifier.fillMaxWidth().padding(top = if (x.senderName != null) 4.dp else if (x.prevSame) 3.dp else 12.dp)
                .graphicsLayer {
                    val k = enterA.value
                    alpha = k.coerceIn(0f, 1f) * (if (m.status == "OUTGOING_SCHEDULED") 0.7f else 1f)
                    if (k < 1f) {
                        translationX = (if (me) 80f else -80f) * (1f - k) * density.density / 2.6f
                        val s = 0.9f + 0.1f * k; scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (me) 1f else 0f, 1f)
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = a.onSide),
        ) {
            // Reply arrow that appears while swiping a bubble.
            if (swipe.value != 0f) {
                val k = (abs(swipe.value) / trigger).coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp).size(32.dp)
                        .graphicsLayer { alpha = k; scaleX = k; scaleY = k }.clip(CircleShape).background(t.tone),
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.reply, t.p, 16.dp, 2.2f) }
            }
            // Like iMessage: dragging the list left slides your own bubbles over; replies stay put.
            Row(Modifier.fillMaxWidth().graphicsLayer { translationX = if (me) x.tsShift else 0f }, verticalAlignment = Alignment.CenterVertically) {
                if (x.selecting) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).then(if (x.selected) Modifier.background(t.fill) else Modifier.border(2.dp, t.faint, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (x.selected) Ico(Icons.check, t.onFill, 13.dp, 3f) }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = if (me) Arrangement.End else Arrangement.Start,
                        modifier = if (x.bundle != null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(if (x.group && !me) 0.8f else 0.72f).widthIn(max = 420.dp),
                    ) {
                        if (x.group && !me) {
                            val w = x.senderWho
                            val repoB = LocalRepo.current
                            val navB = com.jpetrydesign.pyng.ui.LocalNav.current
                            // Tap their photo to go to your own chat with them.
                            Box(
                                Modifier.size(30.dp).graphicsLayer { alpha = if (x.nextSame) 0f else 1f }
                                    .then(if (!x.nextSame && !x.selecting) Modifier.clip(CircleShape).clickable {
                                        repoB.convs[m.convId]?.people?.firstOrNull { it.id == m.participantId }?.let { openDirect(repoB, navB, scope, it) }
                                    } else Modifier),
                            ) {
                                if (w != null) WhoAvatar(w, 30.dp, 11.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(Modifier.weight(1f, fill = false)) {
                            Box(
                                Modifier
                                    .onGloballyPositioned { a.onPlaced(it) }
                                    .offset { IntOffset(swipe.value.roundToInt(), 0) }
                                    .padding(bottom = if (m.reactions.isNotEmpty()) 16.dp else 0.dp)
                                    .then(if (x.highlighted) Modifier.border(3.dp, t.p, shape) else Modifier)
                                    .graphicsLayer { scaleX = press.value; scaleY = press.value }
                                    .pointerInput(m.key, x.selecting) {
                                        detectTapGestures(
                                            onPress = {
                                                val held = scope.launch { delay(90); press.animateTo(0.96f, tween(260)) }
                                                tryAwaitRelease()
                                                held.cancel()
                                                scope.launch { press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f)) }
                                            },
                                            onTap = { a.onTap() },
                                            onDoubleTap = if (x.selecting) null else ({ a.onDouble() }),
                                            onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); a.onLong() },
                                        )
                                    }
                                    .swipeDir(m.key, +1,
                                        onDrag = { d -> scope.launch { swipe.snapTo((swipe.value + d * 0.8f).coerceIn(0f, maxPx)) } },
                                        onEnd = {
                                            if (swipe.value > trigger) a.onReply()
                                            scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                                        },
                                    ),
                            ) {
                                BubbleBody(m, x, shape, jumbo, a)
                            }
                            if (x.burst != null) BurstEmoji(x.burst, Modifier.align(Alignment.Center))
                            if (m.reactions.isNotEmpty()) {
                                val all = m.reactions.sumOf { it.count }
                                val txt = m.reactions.joinToString("") { it.emoji } + if (all > 1) " $all" else ""
                                ReactionPill(txt, t, Modifier.align(if (me) Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = 8.dp).offset(y = 2.dp))
                            }
                        }
                    }
                    if (m.failed) {
                        Row(Modifier.padding(top = 5.dp, end = 4.dp).clickable(onClick = a.onRetry), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Icons.warn, t.danger, 14.dp, 2f)
                            Spacer(Modifier.width(5.dp))
                            T(x.failLabel ?: "Not sent · Tap to retry", size = 11.5.sp, weight = FontWeight.SemiBold, color = t.danger)
                        }
                    } else if (x.info == null) {
                        StatusLine(x.autoStatus, x.statusFace, t)
                    }
                    if (x.kindTag != null) {
                        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Icons.message, t.soft, 12.dp, 2f); Spacer(Modifier.width(4.dp))
                            T(x.kindTag, size = 11.sp, weight = FontWeight.Medium, color = t.soft)
                        }
                    }
                    if (!m.failed && x.info != null) {
                        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp).fadeUp(), verticalAlignment = Alignment.CenterVertically) {
                            Ico(x.infoIcon, x.infoInk, 14.dp, 2.1f)
                            Spacer(Modifier.width(5.dp))
                            T(x.info, size = 11.5.sp, weight = FontWeight.Medium, color = t.soft)
                        }
                    }
                    if (!me && m.text.isNotEmpty() && m.media.isEmpty()) TranslateBlock(m.key, m.text, t, m.convId)
                    if (x.chips.isNotEmpty()) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            x.chips.forEach { (label, icon, go) ->
                                Row(
                                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(t.glassStrong).clickable(onClick = go).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Ico(icon, t.p, 14.dp, 2f); Spacer(Modifier.width(6.dp))
                                    T(label, size = 12.5.sp, weight = FontWeight.SemiBold, color = t.p)
                                }
                            }
                        }
                    }
                    if (x.starred) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Icons.star, t.p, 12.dp, 1.5f, fill = t.p); Spacer(Modifier.width(5.dp))
                            T("Starred", size = 11.sp, weight = FontWeight.SemiBold, color = t.p)
                        }
                    }
                }
            }
            // Timestamp slides in from the right edge as the list is dragged left.
            if (x.tsShift < -1f) {
                val full = with(density) { 76.dp.toPx() }
                T(
                    x.timeLabel, size = 11.sp, weight = FontWeight.SemiBold, color = t.soft,
                    align = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd).width(64.dp)
                        .graphicsLayer { translationX = full + x.tsShift; alpha = (-x.tsShift / full).coerceIn(0f, 1f) },
                )
            }
        }
    }
}


@Composable
private fun Modifier.fadeUp(): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(200)) }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 10f }
}

@Composable
private fun ReactionPill(text: String, t: Tok, modifier: Modifier) {
    val k = remember { Animatable(0f) }
    LaunchedEffect(text) { k.snapTo(0f); k.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
    Box(
        modifier.graphicsLayer { scaleX = k.value; scaleY = k.value }
            .shadow(4.dp, RoundedCornerShape(13.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow)
            .height(26.dp).widthIn(min = 26.dp).clip(RoundedCornerShape(13.dp)).background(if (t.dark) Color(0xFF2A2D36) else Color.White)
            .border(1.dp, t.edgeLine, RoundedCornerShape(13.dp)).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, size = 12.5.sp, color = t.ink, maxLines = 1) }
}

@Composable
private fun BurstEmoji(e: String, modifier: Modifier) {
    val k = remember { Animatable(0f) }
    LaunchedEffect(e) { k.animateTo(1f, tween(850)) }
    val v = k.value
    Box(modifier.graphicsLayer {
        val s = if (v < 0.3f) 0.2f + v / 0.3f * 1.1f else 1.3f - (v - 0.3f) / 0.7f * 0.3f
        scaleX = s; scaleY = s; alpha = if (v < 0.3f) v / 0.3f else 1f - (v - 0.3f) / 0.7f
        translationY = -v * 60f
    }) { T(e, size = 46.sp) }
}

/** The message as the long-press screen shows it: same bubble, no extras. */
@Composable
fun MenuBubble(m: Msg, x: RowCtx) {
    val shape = bubbleShape(m.fromMe, false, false, x.shape.first, x.shape.second)
    val jumbo = if (m.media.isEmpty() && x.quote == null) jumboCount(m.text) else 0
    BubbleBody(
        m, x.copy(info = null, chips = emptyList(), highlighted = false, selecting = false, burst = null, tsShift = 0f, enterDelay = -1, starred = false),
        shape, jumbo, RowActs({}, {}, {}, {}, {}, { _ -> }, {}),
    )
}

@Composable
private fun BubbleBody(m: Msg, x: RowCtx, shape: RoundedCornerShape, jumbo: Int, a: RowActs) {
    if (x.bundle != null) { MediaStrip(x.bundle, m.fromMe, x, a); return }
    val t = x.t
    val me = m.fromMe
    val gradMode = LocalRepo.current.prefs.state.opt("gradient")
    val bg: Brush = when {
        !me -> Brush.linearGradient(listOf(t.inBg, t.inBg))
        gradMode == "Off" -> Brush.linearGradient(listOf(t.outFlat, t.outFlat))
        else -> Brush.verticalGradient(listOf(t.outTop, t.outBottom))
    }
    // Across the screen: every sent bubble shows its slice of one screen-tall gradient (like iMessage).
    val acrossScreen = me && gradMode == "Across the screen"
    val fg = if (me) t.outInk else t.inInk
    val sub = if (me) (if (t.dark) Color(0x9E101218) else Color(0xBFFFFFFF)) else t.soft
    val quoteBg = if (me) Color.White.copy(alpha = 0.18f) else t.neutral
    val bold = LocalRepo.current.prefs.state.on("boldText")
    Column(Modifier.graphicsLayer { alpha = if (m.pending && m.media.isNotEmpty()) 0.7f else 1f }, horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
        // A picture message the phone is still fetching (or something Pyng can't read yet).
        if (m.media.isEmpty() && (m.fetching || m.unsupported)) when {
            m.unsupported -> MediaWaiting("Can't show this message yet", "Open it in Google Messages to see it", shape, spinning = false)
            m.needsManual -> MediaWaiting("Picture message", "Open it in Google Messages to download it", shape, spinning = false)
            m.fetchFailed -> MediaWaiting("Couldn't download", "Open it in Google Messages to try again", shape, spinning = false)
            else -> MediaWaiting("Photo on its way", null, shape, spinning = true)
        }
        m.media.forEachIndexed { i, ref ->
            if (i > 0) Spacer(Modifier.height(4.dp))
            if (ref.waiting) MediaWaiting(ref.kindLabel + " on its way", null, shape, spinning = true)
            else MediaView(m, ref, shape, me, fg, sub, bg, a)
        }
        val url = if (x.showPreview && jumbo == 0) detect(m.text).url else null
        // A message that's only a link shows just the card.
        val text = if (url != null && x.quote == null && m.text.trim() == url) "" else m.text
        if (jumbo > 0) {
            T(text, size = listOf(58f, 58f, 48f, 40f)[jumbo].sp, lineHeight = (listOf(58f, 58f, 48f, 40f)[jumbo] * 1.15f).sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        } else if (text.isNotEmpty() || x.quote != null) {
            if (m.media.isNotEmpty()) Spacer(Modifier.height(4.dp))
            val fx = LocalSendFx.current
            Column(
                Modifier
                    .then(if (fx != null && fx.isTarget(m)) Modifier.onGloballyPositioned { fx.bubble = it } else Modifier)
                    .then(if (fx != null) Modifier.graphicsLayer { alpha = fx.alphaFor(m); scaleX = fx.scaleXFor(m); scaleY = fx.scaleYFor(m); translationX = fx.txFor(m); translationY = fx.tyFor(m); rotationZ = fx.rotFor(m) } else Modifier)
                    .clip(shape)
                    .then(if (acrossScreen) Modifier.screenGradient(t.outTop, t.outBottom) else Modifier.background(bg))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                if (x.quote != null) {
                    Column(Modifier.padding(top = 1.dp, bottom = 7.dp).clip(RoundedCornerShape(12.dp)).background(quoteBg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        T(x.quote.first, size = 12.5.sp, weight = FontWeight.Bold, color = fg)
                        T(x.quote.second, size = 12.5.sp, color = fg.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.widthIn(max = 220.dp))
                    }
                }
                if (text.isNotEmpty()) LinkText(text, x.fontSize, fg, if (bold) FontWeight.Medium else FontWeight.Normal)
            }
        }
        if (url != null) {
            Spacer(Modifier.height(4.dp))
            LinkCard(url, me, onLong = a.onLong)
        }
    }
}

// ---------- media ----------

/** Stands in for an attachment that hasn't arrived yet, so the message is never invisible. */
@Composable
private fun MediaWaiting(label: String, note: String?, shape: RoundedCornerShape, spinning: Boolean) {
    val t = LocalTok.current
    Box(Modifier.size(232.dp, 150.dp).clip(shape).background(t.tone), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp)) {
            if (spinning) CircularProgressIndicator(Modifier.size(24.dp), color = t.p, strokeWidth = 2.dp)
            else Ico(Icons.download, t.soft, 24.dp, 2f)
            Spacer(Modifier.height(10.dp))
            T(label, size = 14.sp, weight = FontWeight.Medium, color = t.ink, align = TextAlign.Center)
            if (note != null) T(note, size = 12.sp, color = t.soft, align = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun MediaView(m: Msg, ref: MediaRef, shape: RoundedCornerShape, me: Boolean, fg: Color, sub: Color, bg: Brush, a: RowActs) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty().ifEmpty { ref.previewPath }
    val auto = remember { repo.autoDownloadAllowed() }
    LaunchedEffect(ref.id, ref.thumbId, repo.connEpoch.intValue) {
        if (ref.localPath.isEmpty()) {
            // The small preview first (it's quick), then the full file.
            if ((ref.isVideo || ref.isImage) && ref.thumbId.isNotEmpty()) repo.ensureMedia(ref, thumb = true)
            // Short videos come down too, so the bubble shows a sharp first frame instead of the blurry preview.
            if ((ref.isImage || ref.isAudio || (ref.isVideo && ref.size in 1..30_000_000L)) && auto) repo.ensureMedia(ref)
        }
    }
    // Only spin while something is actually downloading.
    val busy = repo.mediaBusy[ref.id] == true || (ref.id.isEmpty() && repo.mediaBusy[ref.thumbId] == true)
    val failed = repo.mediaFailed[ref.id] == true && !busy
    when {
        ref.isImage || ref.isVideo -> {
            val ratio = if (ref.w > 0 && ref.h > 0) ref.h.toFloat() / ref.w else 1f
            val w = 232.dp
            val h = (232f * ratio).coerceIn(140f, 320f).dp
            // A downloaded video shows its own first frame; until then, the preview.
            val show = if (full.isNotEmpty()) full else thumb
            // PNGs, GIFs and stickers can be see-through: no box behind them, shown whole.
            val clear = ref.isImage && (ref.mime.contains("png") || ref.mime.contains("gif") || ref.mime.contains("webp"))
            Box(
                Modifier.size(w, h).clip(shape).then(if (show.isEmpty() || !clear) Modifier.background(if (show.isEmpty()) t.tone else Color.Transparent) else Modifier)
                    .holdable(a.onLong) { if (ref.isImage && full.isEmpty() && show.isEmpty()) repo.ensureMedia(ref, manual = true) else a.onOpenMedia(ref) },
                contentAlignment = Alignment.Center,
            ) {
                if (show.isNotEmpty()) AsyncImage(model = File(show), contentDescription = if (ref.isVideo) "Video" else "Photo", contentScale = if (clear) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (ref.isVideo && full.isNotEmpty()) AsyncImage(model = File(full), contentDescription = "Video", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (ref.isVideo) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) else Ico(Icons.play, Color.White, 24.dp, 1f, fill = Color.White)
                    }
                } else if (full.isEmpty()) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)).clickable(enabled = !busy) { repo.ensureMedia(ref, manual = true) }, contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Ico(if (failed) Icons.refresh else Icons.download, Color.White, 22.dp, 2f)
                    }
                    if (failed) T(
                        if (show.isNotEmpty()) "Preview · tap to load full photo" else "Tap to try again", size = 11.5.sp, weight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
        ref.isAudio -> VoicePlayer(m, ref, full, busy, shape, bg, fg, sub, me)
        ref.isContact -> ContactCard(ref, full, busy, me, a.onLong)
        else -> {
            Row(
                Modifier.clip(shape).background(bg).holdable(a.onLong) { a.onOpenMedia(ref) }.padding(10.dp).widthIn(min = 180.dp, max = 260.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (me) Color.White.copy(alpha = 0.2f) else t.tone), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = if (me) fg else t.p, strokeWidth = 2.dp)
                    else Ico(if (ref.isContact) Icons.contact else Icons.file, if (me) fg else t.ink, 20.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    T(ref.name.ifBlank { if (ref.isContact) "Contact card" else "Attachment" }, size = 14.5.sp, weight = FontWeight.SemiBold, color = fg, maxLines = 1)
                    T(sizeText(ref.size) + (if (full.isEmpty() && ref.localPath.isEmpty()) " · Tap to download" else ""), size = 12.sp, color = sub)
                }
            }
        }
    }
}

fun sizeText(b: Long): String = when {
    b <= 0 -> ""
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${(b / 1024f).roundToInt()} KB"
    else -> "%.1f MB".format(b / 1048576f)
}

// ---------- link previews ----------

data class LinkMeta(val title: String, val host: String, val image: String?, val desc: String = "", val icon: String? = null)

object Links {
    val cache = mutableStateMapOf<String, LinkMeta?>()
    private val busy = mutableSetOf<String>()
    const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"
    // Social sites hide their preview details from browsers that aren't signed in, but share them
    // with the link-preview crawlers chat apps use. Asking the way those crawlers do gets the card.
    private const val CRAWLER = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private val social = Regex("(^|\\.)(instagram\\.com|instagr\\.am|threads\\.net|threads\\.com|facebook\\.com|fb\\.watch|fb\\.com|x\\.com|twitter\\.com|tiktok\\.com|pinterest\\.com|pin\\.it|snapchat\\.com|linkedin\\.com)$", RegexOption.IGNORE_CASE)
    private var dir: File? = null

    fun init(ctx: android.content.Context) { if (dir == null) dir = File(ctx.cacheDir, "links").apply { mkdirs() } }

    private fun diskFile(url: String) = dir?.let { File(it, Integer.toHexString(url.hashCode()) + "-" + url.length + ".json") }

    private fun readDisk(url: String): LinkMeta? = runCatching {
        val f = diskFile(url) ?: return null
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 14L * 24 * 3600_000) return null
        val o = org.json.JSONObject(f.readText())
        if (o.optString("url") != url) return null
        LinkMeta(o.optString("title"), o.optString("host"), o.optString("image").ifBlank { null }, o.optString("desc"), o.optString("icon").ifBlank { null })
    }.getOrNull()

    private fun writeDisk(url: String, m: LinkMeta) {
        runCatching {
            val o = org.json.JSONObject().put("url", url).put("title", m.title).put("host", m.host).put("image", m.image ?: "").put("desc", m.desc).put("icon", m.icon ?: "")
            diskFile(url)?.writeText(o.toString())
        }
    }

    suspend fun load(url: String) {
        if (cache.containsKey(url) || !busy.add(url)) return
        val meta = withContext(Dispatchers.IO) {
            readDisk(url)?.let { return@withContext it }
            val host = runCatching { URL(url).host }.getOrDefault("")
            val first = if (social.containsMatchIn(host)) CRAWLER else UA
            val a = fetch(url, first)
            // A page with no picture or title gets a second try the other way.
            val got = if (a == null || a.image == null || a.title.isBlank()) {
                val b = fetch(url, if (first == UA) CRAWLER else UA)
                when {
                    b == null -> a
                    a == null -> b
                    (b.image != null) && (a.image == null) -> b
                    a.title.isBlank() && b.title.isNotBlank() -> b
                    else -> a
                }
            } else a
            got?.also { writeDisk(url, it) }
        }
        cache[url] = meta
        busy.remove(url)
    }

    private fun fetch(url: String, ua: String): LinkMeta? = runCatching {
        fun open(u: URL) = (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000; readTimeout = 8000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        // Short links (bit.ly, advmd.co…) hop between hosts; follow those hops by hand.
        var conn = open(URL(url))
        var hops = 0
        while (conn.responseCode in 300..399 && hops < 6) {
            val next = conn.getHeaderField("Location") ?: break
            val u = URL(conn.url, next)
            conn.disconnect()
            conn = open(u)
            hops++
        }
        if (conn.responseCode >= 400) { conn.disconnect(); return null }
        val base = conn.url
        val html = conn.inputStream.bufferedReader().use { r -> val buf = CharArray(400_000); var n = 0; while (n < buf.size) { val k = r.read(buf, n, buf.size - n); if (k <= 0) break; n += k; if (String(buf, 0, n).contains("</head>", true)) break }; String(buf, 0, n) }
        fun clean(v: String) = android.text.Html.fromHtml(v, 0).toString().trim()
        fun og(vararg names: String): String? {
            for (p in names) {
                val e = Regex.escape(p)
                val v = Regex("<meta[^>]+(?:property|name|itemprop)\\s*=\\s*[\"']$e[\"'][^>]*content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                    ?: Regex("<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*(?:property|name|itemprop)\\s*=\\s*[\"']$e[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                if (!v.isNullOrBlank()) return clean(v)
            }
            return null
        }
        fun abs(v: String?): String? = v?.let { runCatching { URL(base, if (it.startsWith("//")) base.protocol + ":" + it else it).toString() }.getOrNull() }
        val title = og("og:title", "twitter:title") ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::clean) ?: ""
        val desc = og("og:description", "twitter:description", "description").orEmpty()
        val img = abs(og("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src", "image")
            ?: Regex("<link[^>]+rel=[\"']image_src[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::clean))
        val icon = abs(Regex("<link[^>]+rel=[\"'](?:apple-touch-icon|icon|shortcut icon)[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::clean))
            ?: "https://${base.host}/favicon.ico"
        conn.disconnect()
        // A login wall isn't a preview.
        if (title.equals("Instagram", true) && img == null || title.contains("Log in", true) && img == null) return null
        LinkMeta(title, base.host.removePrefix("www."), img, desc, icon)
    }.getOrNull()
}

/** A link preview card: picture, title, a line of description and the site with its icon. */
@Composable
fun LinkCard(url: String, me: Boolean, modifier: Modifier = Modifier, onLong: (() -> Unit)? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val t = LocalTok.current
    LaunchedEffect(url) { Links.init(ctx); Links.load(url) }
    val meta = Links.cache[url]
    val host = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault(url)
    var imgFailed by remember(meta?.image) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.widthIn(max = 300.dp).fillMaxWidth().edge(shape, elevation = 2.dp).clip(shape).background(if (t.dark) t.neutral else Color.White)
            .holdable(onLong) { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } },
    ) {
        if (meta?.image != null && !imgFailed) {
            val req = remember(meta.image) {
                coil.request.ImageRequest.Builder(ctx).data(meta.image).addHeader("User-Agent", Links.UA).crossfade(true).build()
            }
            AsyncImage(
                req, null, contentScale = ContentScale.Crop,
                onState = { st -> if (st is coil.compose.AsyncImagePainter.State.Error) imgFailed = true },
                modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).background(t.tone),
            )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            if (meta == null && !Links.cache.containsKey(url)) {
                Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(t.tone))
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(t.tone))
            } else {
                T(meta?.title?.ifBlank { null } ?: host, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 18.sp)
                if (!meta?.desc.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    T(meta!!.desc, size = 12.5.sp, color = t.soft, maxLines = 2, lineHeight = 16.5.sp)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                var iconFailed by remember(meta?.icon) { mutableStateOf(false) }
                if (meta?.icon != null && !iconFailed) {
                    AsyncImage(meta.icon, null, onState = { st -> if (st is coil.compose.AsyncImagePainter.State.Error) iconFailed = true },
                        modifier = Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)))
                } else Ico(Icons.link, t.soft, 14.dp)
                Spacer(Modifier.width(6.dp))
                T(meta?.host ?: host, size = 12.sp, weight = FontWeight.Medium, color = t.soft, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LinkText(text: String, size: Float, color: Color, weight: FontWeight) {
    val links = remember(text) { Regex("https?://[^\\s]+").findAll(text).toList() }
    val annotated = remember(text, color) {
        androidx.compose.ui.text.buildAnnotatedString {
            var i = 0
            links.forEach { mr ->
                append(text.substring(i, mr.range.first))
                withLink(androidx.compose.ui.text.LinkAnnotation.Url(mr.value, androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)))) {
                    append(mr.value)
                }
                i = mr.range.last + 1
            }
            append(text.substring(i))
        }
    }
    val style = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = size.sp, lineHeight = (size * 1.4f).sp, color = color, fontWeight = weight)
    val laid = remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
    // The bubble hugs its text: a wrapped message is as wide as its longest line (no empty gap on the right),
    // and a very short one ("k", "?") gets even padding on both sides so its pill never ends up taller than it is wide.
    androidx.compose.ui.layout.Layout(content = { androidx.compose.material3.Text(annotated, style = style, onTextLayout = { laid[0] = it }) }) { kids, c ->
        val p = kids.first().measure(c.copy(minWidth = 0))
        val r = laid[0]
        var left = 0f
        var right = p.width.toFloat()
        if (r != null && r.lineCount > 1) {
            left = (0 until r.lineCount).minOf { r.getLineLeft(it) }
            right = (0 until r.lineCount).maxOf { r.getLineRight(it) }
        }
        val textW = kotlin.math.ceil(right - left).toInt().coerceIn(0, p.width)
        val w = maxOf(textW, (size * 1.4f).sp.roundToPx()).coerceIn(c.minWidth, c.maxWidth)
        layout(w, p.height) { p.place((w - textW) / 2 - left.toInt(), 0) }
    }
}

@Suppress("unused")
private fun Modifier.minH(h: Dp) = this.heightIn(min = h)


/**
 * Paints one gradient that spans the whole screen, showing the part where this element sits.
 * It follows the element as it scrolls by redrawing only, never recomposing.
 */
fun Modifier.screenGradient(top: Color, bottom: Color): Modifier = this then ScreenGradElement(top, bottom)

private data class ScreenGradElement(val top: Color, val bottom: Color) : androidx.compose.ui.node.ModifierNodeElement<ScreenGradNode>() {
    override fun create() = ScreenGradNode(top, bottom)
    override fun update(node: ScreenGradNode) { node.top = top; node.bottom = bottom; node.invalidateDrawSafe() }
}

private class ScreenGradNode(var top: Color, var bottom: Color) :
    Modifier.Node(), androidx.compose.ui.node.DrawModifierNode, androidx.compose.ui.node.GlobalPositionAwareModifierNode {
    private var y = 0f
    private var h = 1f

    fun invalidateDrawSafe() { if (isAttached) invalidateDraw() }

    override fun onGloballyPositioned(coordinates: androidx.compose.ui.layout.LayoutCoordinates) {
        val ny = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
        val nh = coordinates.findRootCoordinates().size.height.toFloat().coerceAtLeast(1f)
        if (ny != y || nh != h) { y = ny; h = nh; invalidateDrawSafe() }
    }

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        drawRect(Brush.verticalGradient(listOf(top, bottom), startY = -y, endY = h - y))
        drawContent()
    }
}

/**
 * The status under your newest message. Each change rolls in from below (Sending… to Delivered to Read),
 * the check turns into two and takes the chat's color when it's read, and it springs in under each new message.
 */
@Composable
private fun StatusLine(st: Triple<String, String, Boolean>?, face: Who?, t: Tok) {
    var last by remember { mutableStateOf(st) }
    if (st != null) last = st
    androidx.compose.animation.AnimatedVisibility(
        st != null,
        enter = androidx.compose.animation.fadeIn(tween(200)) + androidx.compose.animation.expandVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) +
            androidx.compose.animation.scaleIn(spring(dampingRatio = 0.55f, stiffness = 520f), initialScale = 0.8f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
        exit = androidx.compose.animation.fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(220)),
    ) {
        val s = last ?: return@AnimatedVisibility
        val ink by androidx.compose.animation.animateColorAsState(if (s.third) t.p else t.soft, tween(450), label = "statusInk")
        androidx.compose.animation.AnimatedContent(
            targetState = s,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it } + androidx.compose.animation.fadeIn(tween(180))) togetherWith
                    (androidx.compose.animation.slideOutVertically(tween(180)) { -it } + androidx.compose.animation.fadeOut(tween(140)))
            },
            label = "status",
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp),
        ) { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (face != null && v.third) { WhoAvatar(face, 15.dp, 6.sp); Spacer(Modifier.width(5.dp)) }
                else {
                    val icon = when { v.third -> Icons.read; v.first.startsWith("Sending") -> Icons.clock; else -> Icons.check }
                    Ico(icon, ink, 14.dp, 2.1f); Spacer(Modifier.width(4.dp))
                }
                T(v.first, size = 11.5.sp, weight = if (v.third) FontWeight.SemiBold else FontWeight.Medium, color = ink)
            }
        }
    }
}


/** Tap to open, hold for the message menu (with the usual buzz), so links, photos and files never swallow a hold. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.holdable(onLong: (() -> Unit)?, onClick: () -> Unit): Modifier = composed {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    this.combinedClickable(
        onClick = onClick,
        onLongClick = onLong?.let { l -> { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); l() } },
    )
}
