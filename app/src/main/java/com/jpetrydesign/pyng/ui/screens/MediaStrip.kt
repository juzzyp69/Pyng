package com.jpetrydesign.pyng.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import java.io.File
import kotlin.math.abs

/**
 * Photos and videos sent together, or one after another by the same person within a minute and a half,
 * show as one swipeable strip instead of a tall column of pictures.
 *
 * [byAnchor] maps the oldest message of each strip to everything in it; the strip is drawn in that
 * message's row and the rows of the others in it stay empty. [of] maps every message in a strip to the
 * strip, so the viewer can swipe through just that set.
 */
class Bundles(
    val byAnchor: Map<String, List<Pair<Msg, MediaRef>>>,
    val hidden: Set<String>,
    val of: Map<String, List<Pair<Msg, MediaRef>>>,
    val newest: Map<String, Msg>,
)

private fun Msg.mediaOnly() = !isSys && text.isBlank() && replyTo.isEmpty() && media.isNotEmpty() &&
    media.all { it.isImage || it.isVideo } && status != "OUTGOING_SCHEDULED"

/** [msgs] in time order, oldest first. */
fun mediaBundles(msgs: List<Msg>): Bundles {
    val groups = ArrayList<MutableList<Msg>>()
    var cur: MutableList<Msg>? = null
    for (m in msgs) {
        if (!m.mediaOnly()) { cur = null; continue }
        val last = cur?.lastOrNull()
        if (cur != null && last != null && last.fromMe == m.fromMe && last.participantId == m.participantId && abs(m.ts - last.ts) <= 90_000L) cur.add(m)
        else { val g = mutableListOf(m); groups.add(g); cur = g }
    }
    val byAnchor = HashMap<String, List<Pair<Msg, MediaRef>>>()
    val hidden = HashSet<String>()
    val of = HashMap<String, List<Pair<Msg, MediaRef>>>()
    val newest = HashMap<String, Msg>()
    for (g in groups) {
        val items = g.flatMap { m -> m.media.map { m to it } }
        if (items.size < 2) continue
        byAnchor[g.first().key] = items
        newest[g.first().key] = g.last()
        g.drop(1).forEach { hidden.add(it.key) }
        g.forEach { of[it.key] = items }
    }
    return Bundles(byAnchor, hidden, of, newest)
}

/** Photos sent together, in the style picked under Appearance › Chat style › Photos: Peek, Stack, Rail or Deck. */
@Composable
fun MediaStrip(items: List<Pair<Msg, MediaRef>>, me: Boolean, x: RowCtx, a: RowActs) {
    val t = x.t
    val n = items.size
    val vids = items.count { it.second.isVideo }
    val label = when { vids == 0 -> "$n photos"; vids == n -> "$n videos"; else -> "$n photos and videos" }
    val style = LocalRepo.current.prefs.state.opt("photoGroups").ifEmpty { "Peek" }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileW = minOf(maxWidth * 0.46f, 240.dp)
        val tileH = tileW * 1.2f
        val mw = maxWidth
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
            Row(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.stack, t.soft, 14.dp, 2f)
                Spacer(Modifier.width(6.dp))
                T(label, size = 12.5.sp, weight = FontWeight.Medium, color = t.soft)
            }
            when (style) {
                "Stack" -> StackStyle(items, me, tileW, tileH, mw, x, a)
                "Rail" -> RailStyle(items, me, tileW, tileH, x, a)
                "Deck" -> DeckStyle(items, me, tileW, tileH, x, a)
                else -> PeekStyle(items, me, tileW, tileH, mw, x, a, nudge = x.isNew || x.enterDelay >= 0)
            }
        }
    }
}

/** A glass tag over a photo, like "1/3". */
@Composable
private fun GlassTag(text: String, modifier: Modifier = Modifier, icon: String? = null) {
    Row(
        modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, Color.White, 12.dp, 2f); Spacer(Modifier.width(4.dp)) }
        T(text, size = 11.5.sp, weight = FontWeight.SemiBold, color = Color.White)
    }
}

/** A thin bar split into one piece per photo; the pieces up to the one you're on are filled. */
@Composable
private fun SegBar(n: Int, on: Int, width: Dp, t: Tok, me: Boolean) {
    Row(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp).width(width), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(n) { i ->
            val idx = if (me) n - 1 - i else i
            val c by androidx.compose.animation.animateColorAsState(if (idx <= on) t.p else t.faint.copy(alpha = 0.45f), tween(260), label = "seg")
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
        }
    }
}

/** Peek: a row that snaps one photo at a time, the next one showing at the edge, with "1/3" and a progress bar. */
@Composable
private fun PeekStyle(items: List<Pair<Msg, MediaRef>>, me: Boolean, w: Dp, h: Dp, maxW: Dp, x: RowCtx, a: RowActs, nudge: Boolean) {
    val n = items.size
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
    val tilePx = with(LocalDensity.current) { w.toPx() }
    val on by remember { derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > tilePx / 2f) 1 else 0 } }
    // When the photos arrive, the row nudges over a little to show it scrolls.
    val shift = remember { androidx.compose.animation.core.Animatable(0f) }
    val nudgePx = with(LocalDensity.current) { 34.dp.toPx() }
    val motion = com.jpetrydesign.pyng.ui.kit.LocalMotion.current
    LaunchedEffect(Unit) {
        if (!nudge || !motion) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        shift.animateTo(if (me) nudgePx else -nudgePx, tween(240))
        shift.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    Box {
        LazyRow(
            state = state, flingBehavior = fling, reverseLayout = me,
            modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = shift.value },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = if (me) PaddingValues(start = (maxW - w).coerceAtLeast(0.dp)) else PaddingValues(end = (maxW - w).coerceAtLeast(0.dp)),
        ) {
            itemsIndexed(items, key = { _, it -> it.first.key + "|" + it.second.id }) { _, item ->
                Box(Modifier.size(w, h).tileGestures(item.first, item.second, a)) { TileContent(item.first, item.second, x, a) }
            }
        }
        GlassTag("${on + 1}/$n", Modifier.align(if (me) Alignment.TopEnd else Alignment.TopStart).padding(top = 8.dp).offset(x = if (me) (-8).dp else w - 50.dp))
    }
    SegBar(n, on, w, x.t, me)
}

/** Stack: a small fanned stack with a count. Swipe it and the photos spread into a row; Stack folds them back. */
@Composable
private fun StackStyle(items: List<Pair<Msg, MediaRef>>, me: Boolean, w: Dp, h: Dp, maxW: Dp, x: RowCtx, a: RowActs) {
    val n = items.size
    val t = x.t
    var open by remember { mutableStateOf(false) }
    val spread = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(open) { spread.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    val wPx = with(LocalDensity.current) { w.toPx() }
    val tiltPx = with(LocalDensity.current) { 12.dp.toPx() }
    val sign = if (me) -1f else 1f
    val scroll = androidx.compose.foundation.rememberScrollState()
    Box(
        Modifier.fillMaxWidth().height(h + 12.dp)
            .pointerInput(open) {
                if (open) return@pointerInput
                detectHorizontalDragGestures { ch, d -> if (kotlin.math.abs(d) > 2f) { ch.consume(); open = true } }
            }
            .then(if (open) Modifier.horizontalScroll(scroll, reverseScrolling = me) else Modifier),
    ) {
        Box(Modifier.width(w * n + 8.dp * (n - 1) + 110.dp).height(h + 12.dp).align(if (me) Alignment.TopEnd else Alignment.TopStart)) {
            items.forEachIndexed { i, item ->
                val k = (spread.value * 1.15f - i * 0.05f).coerceIn(0f, 1f)
                val stackX = sign * i * tiltPx
                val rowX = sign * i * (wPx + gapPx)
                Box(
                    Modifier.align(if (me) Alignment.TopEnd else Alignment.TopStart).padding(top = 6.dp).zIndex((n - i).toFloat())
                        .graphicsLayer {
                            translationX = stackX + (rowX - stackX) * k
                            val tilt = sign * i * 4f * (1f - k)
                            rotationZ = tilt
                            val sc = 1f - i * 0.05f * (1f - k); scaleX = sc; scaleY = sc
                            alpha = if (i > 2 && k < 0.05f) 0f else 1f
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                        }
                        .size(w, h)
                        .shadow(if (k < 1f) 6.dp else 0.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow)
                        .tileGestures(item.first, item.second, a) { if (!open) a.onOpenItem?.invoke(items[0].first, items[0].second) else null },
                ) {
                    TileContent(item.first, item.second, x, a)
                    if (i == 0) androidx.compose.animation.AnimatedVisibility(!open, Modifier.align(if (me) Alignment.TopStart else Alignment.TopEnd).padding(8.dp), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                        GlassTag("$n", icon = Icons.stack)
                    }
                }
            }
            if (open) Row(
                Modifier.align(if (me) Alignment.CenterEnd else Alignment.CenterStart).offset(x = if (me) -(w * n + 8.dp * n + 4.dp) else w * n + 8.dp * n + 4.dp)
                    .graphicsLayer { alpha = ((spread.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                    .height(36.dp).clip(RoundedCornerShape(18.dp)).background(t.tone).clickable { open = false }.padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Ico(Icons.stack, t.p, 14.dp, 2f); Spacer(Modifier.width(6.dp)); T("Stack", size = 13.sp, weight = FontWeight.SemiBold, color = t.p) }
        }
    }
    androidx.compose.animation.AnimatedVisibility(!open) {
        T(if (me) "← Swipe to see all" else "Swipe to see all →", size = 11.5.sp, color = t.soft, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp))
    }
}

/** Rail: one photo with the next ones beside it as thumbnails. Swipe the photo or tap a thumbnail. */
@Composable
private fun RailStyle(items: List<Pair<Msg, MediaRef>>, me: Boolean, w: Dp, h: Dp, x: RowCtx, a: RowActs) {
    val n = items.size
    var cur by remember { mutableIntStateOf(0) }
    val side = (h - 6.dp) / 2
    val swipe = with(LocalDensity.current) { 36.dp.toPx() }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        val main: @Composable () -> Unit = {
            var acc by remember { mutableStateOf(0f) }
            Box(
                Modifier.size(w, h).pointerInput(n) {
                    detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { ch, d ->
                        acc += d
                        if (kotlin.math.abs(acc) > swipe) { ch.consume(); cur = (cur + (if (acc < 0) 1 else n - 1)) % n; acc = 0f }
                    }
                },
            ) {
                androidx.compose.animation.AnimatedContent(
                    cur, label = "rail",
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 380f)) { it * dir } + androidx.compose.animation.fadeIn()) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(tween(220)) { -it * dir / 3 } + androidx.compose.animation.fadeOut(tween(200)))
                    },
                ) { i ->
                    val (m, ref) = items[i]
                    Box(Modifier.size(w, h).tileGestures(m, ref, a)) { TileContent(m, ref, x, a) }
                }
                GlassTag("${cur + 1}/$n", Modifier.align(if (me) Alignment.TopStart else Alignment.TopEnd).padding(8.dp))
            }
        }
        val rail: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val next = (1 until n).map { (cur + it) % n }.take(2)
                next.forEachIndexed { q, j ->
                    val (m, ref) = items[j]
                    androidx.compose.runtime.key(j) {
                        val pop = remember { androidx.compose.animation.core.Animatable(0.6f) }
                        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                        Box(
                            Modifier.size(side).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(14.dp)).clickable { cur = j },
                        ) {
                            TileContent(m, ref, x, a, small = true)
                            if (q == 1 && n > 3) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                T("+${n - 3}", size = 15.sp, weight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        if (me) { rail(); main() } else { main(); rail() }
    }
}

/** Deck: a small deck with "1 of 3" on the photo. Swipe and the top one tucks to the back. */
@Composable
private fun DeckStyle(items: List<Pair<Msg, MediaRef>>, me: Boolean, w: Dp, h: Dp, x: RowCtx, a: RowActs) {
    val n = items.size
    val scope = rememberCoroutineScope()
    var top by remember { mutableIntStateOf(0) }
    val dx = remember { androidx.compose.animation.core.Animatable(0f) }
    val wPx = with(LocalDensity.current) { w.toPx() }
    val peek = with(LocalDensity.current) { 11.dp.toPx() }
    val sign = if (me) -1f else 1f
    Box(
        Modifier.width(w + 26.dp).height(h + 4.dp).pointerInput(n) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (kotlin.math.abs(dx.value) > wPx * 0.3f) {
                            dx.animateTo(kotlin.math.sign(dx.value) * wPx * 1.1f, tween(200))
                            top = (top + 1) % n; dx.snapTo(0f)
                        } else dx.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f))
                    }
                },
            ) { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } }
        },
    ) {
        (0 until n).filter { (it - top + n) % n <= 2 }.sortedByDescending { (it - top + n) % n }.forEach { i -> androidx.compose.runtime.key(i) {
            val d = (i - top + n) % n
            val (m, ref) = items[i]
            val depth by androidx.compose.animation.core.animateFloatAsState(d.toFloat(), spring(dampingRatio = 0.62f, stiffness = 320f), label = "deck")
            Box(
                Modifier.align(if (me) Alignment.TopEnd else Alignment.TopStart).size(w, h)
                    .graphicsLayer {
                        if (d == 0) { translationX = dx.value; rotationZ = dx.value / wPx * 8f }
                        else translationX = sign * depth * peek
                        val sc = 1f - depth * 0.06f; scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.85f)
                    }
                    .shadow(6.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = x.t.shadow, spotColor = x.t.shadow)
                    .tileGestures(m, ref, a),
            ) {
                TileContent(m, ref, x, a)
                if (d == 0) Row(
                    Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    T("‹", size = 14.sp, color = Color.White); T("${top + 1} of $n", size = 12.sp, weight = FontWeight.SemiBold, color = Color.White); T("›", size = 14.sp, color = Color.White)
                }
            }
        } }
    }
}

/** Tap to open, hold for the menu, double-tap to heart: for one photo in a group. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tileGestures(m: Msg, ref: MediaRef, a: RowActs, tap: (() -> Unit?)? = null): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .combinedClickable(
        onClick = { if (tap?.invoke() == null) a.onOpenItem?.invoke(m, ref) ?: a.onOpenMedia(ref) },
        onLongClick = { a.onLongItem?.invoke(m) ?: a.onLong() },
        onDoubleClick = { a.onDoubleItem?.invoke(m) ?: a.onDouble() },
    )

/** One photo or video: its picture (downloading as needed), a play badge, sending or failed marks, and its reactions. */
@Composable
private fun TileContent(m: Msg, ref: MediaRef, x: RowCtx, a: RowActs, small: Boolean = false) {
    val repo = LocalRepo.current
    val t = x.t
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty().ifEmpty { ref.previewPath }
    val auto = remember { repo.autoDownloadAllowed() }
    LaunchedEffect(ref.id, ref.thumbId, repo.connEpoch.intValue) {
        if (ref.localPath.isEmpty()) {
            if (ref.thumbId.isNotEmpty()) repo.ensureMedia(ref, thumb = true)
            if ((ref.isImage || (ref.isVideo && ref.size in 1..30_000_000L)) && auto) repo.ensureMedia(ref)
        }
    }
    val busy = repo.mediaBusy[ref.id] == true || (ref.id.isEmpty() && repo.mediaBusy[ref.thumbId] == true)
    val show = if (full.isNotEmpty()) full else thumb
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { c -> a.onPlacedKey?.invoke(m.key, c) }.background(t.tone),
        contentAlignment = Alignment.Center,
    ) {
        if (show.isNotEmpty()) AsyncImage(File(show), if (ref.isVideo) "Video" else "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (ref.isVideo) {
            Box(Modifier.size(if (small) 26.dp else 46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(if (small) 12.dp else 22.dp), color = Color.White, strokeWidth = 2.dp) else Ico(Icons.play, Color.White, if (small) 12.dp else 22.dp, 1f, fill = Color.White)
            }
        } else if (ref.waiting || (full.isEmpty() && busy)) {
            CircularProgressIndicator(Modifier.size(if (small) 16.dp else 24.dp), color = if (show.isEmpty()) t.p else Color.White, strokeWidth = 2.dp)
        }
        if (small) return@Box
        if (m.pending && !ref.waiting) Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(13.dp), color = Color.White, strokeWidth = 2.dp) }
        if (m.failed) Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape).background(t.danger),
            contentAlignment = Alignment.Center,
        ) { Ico(Icons.warn, Color.White, 13.dp, 2.2f) }
        if (m.reactions.isNotEmpty()) {
            val all = m.reactions.sumOf { it.count }
            T(
                m.reactions.joinToString("") { it.emoji } + if (all > 1) " $all" else "", size = 12.5.sp, color = t.ink, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (t.dark) Color(0xFF2A2D36) else Color.White).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

/** Small drawings of each photo style, for the style studio. */
@Composable
fun PhotoGroupMini(style: String, t: Tok, scale: Float = 1f) {
    val w = (44 * scale).dp; val h = (54 * scale).dp
    val c1 = t.p.copy(alpha = 0.55f); val c2 = t.p.copy(alpha = 0.38f); val c3 = t.p.copy(alpha = 0.24f)
    val sh = RoundedCornerShape((9 * scale).dp)
    when (style) {
        "Stack" -> Box(Modifier.size(w + (16 * scale).dp, h + (6 * scale).dp)) {
            Box(Modifier.padding(start = (14 * scale).dp).size(w, h).graphicsLayer { rotationZ = 9f; scaleX = .9f; scaleY = .9f }.clip(sh).background(c3))
            Box(Modifier.padding(start = (7 * scale).dp).size(w, h).graphicsLayer { rotationZ = 4.5f; scaleX = .95f; scaleY = .95f }.clip(sh).background(c2))
            Box(Modifier.size(w, h).clip(sh).background(c1))
        }
        "Rail" -> Row(horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
            Box(Modifier.size(w, h).clip(sh).background(c1))
            Column(verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                Box(Modifier.size((h - (4 * scale).dp) / 2).clip(sh).background(c2)); Box(Modifier.size((h - (4 * scale).dp) / 2).clip(sh).background(c3))
            }
        }
        "Deck" -> Box(Modifier.size(w + (14 * scale).dp, h)) {
            Box(Modifier.padding(start = (14 * scale).dp).size(w, h).graphicsLayer { scaleX = .88f; scaleY = .88f }.clip(sh).background(c3))
            Box(Modifier.padding(start = (7 * scale).dp).size(w, h).graphicsLayer { scaleX = .94f; scaleY = .94f }.clip(sh).background(c2))
            Box(Modifier.size(w, h).clip(sh).background(c1)) {
                Box(Modifier.align(Alignment.BottomCenter).padding((4 * scale).dp).fillMaxWidth().height((9 * scale).dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.3f)))
            }
        }
        else -> Column {
            Row(horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                Box(Modifier.size(w, h).clip(sh).background(c1)); Box(Modifier.size(w, h).clip(sh).background(c2)); Box(Modifier.size((w.value * 0.35f).dp, h).clip(sh).background(c3))
            }
            Row(Modifier.padding(top = (4 * scale).dp).width(w), horizontalArrangement = Arrangement.spacedBy((3 * scale).dp)) {
                Box(Modifier.weight(1f).height((3 * scale).dp).clip(RoundedCornerShape(2.dp)).background(t.p))
                Box(Modifier.weight(1f).height((3 * scale).dp).clip(RoundedCornerShape(2.dp)).background(c3))
                Box(Modifier.weight(1f).height((3 * scale).dp).clip(RoundedCornerShape(2.dp)).background(c3))
            }
        }
    }
}
