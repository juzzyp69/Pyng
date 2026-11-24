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
import androidx.compose.ui.layout.boundsInRoot
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
    media.all { it.isImage || it.isVideo } && media.none { Stickers.isSticker(it) } && status != "OUTGOING_SCHEDULED"

/**
 * Stickers: see-through pictures (Gboard's Emoji Kitchen, sticker packs, cutouts). They show on their own,
 * whole and with no box behind them, never cropped into a photo strip.
 */
object Stickers {
    /** Checked files: true when the picture really is see-through. Read by the strip so it regroups once known. */
    val known = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    /** Before the file is checked, a square-ish PNG or WebP is taken to be a sticker; screenshots are tall. */
    fun isSticker(r: com.jpetrydesign.pyng.data.MediaRef): Boolean {
        known[r.id]?.let { return it }
        if (!r.isImage || !(r.mime.contains("png") || r.mime.contains("webp"))) return false
        if (r.w <= 0 || r.h <= 0) return true
        val ratio = r.h.toFloat() / r.w
        return ratio in 0.6f..1.6f && maxOf(r.w, r.h) <= 1600
    }

    /** Looks at the downloaded file: see-through if a good share of it (or a corner) is clear. */
    suspend fun check(r: com.jpetrydesign.pyng.data.MediaRef, path: String) {
        if (r.id.isEmpty() || known.containsKey(r.id) || !(r.mime.contains("png") || r.mime.contains("webp"))) return
        val clear = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val b = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return@runCatching false
                if (!b.hasAlpha()) return@runCatching false
                val w = b.width; val h = b.height
                val corners = listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1).count { (x, y) -> (b.getPixel(x, y) ushr 24) < 24 }
                var seeThrough = 0
                val step = maxOf(1, minOf(w, h) / 24)
                var total = 0
                for (y in 0 until h step step) for (x in 0 until w step step) { total++; if ((b.getPixel(x, y) ushr 24) < 24) seeThrough++ }
                corners >= 2 || seeThrough > total * 0.08f
            }.getOrDefault(false)
        }
        known[r.id] = clear
    }
}

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

/** Photos sent together: a fanned stack you flick through in place, and tap to open. */
@Composable
fun MediaStrip(items: List<Pair<Msg, MediaRef>>, me: Boolean, x: RowCtx, a: RowActs) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileW = minOf(maxWidth * 0.5f, 250.dp)
        val tileH = tileW * 1.25f
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
            StackStyle(items, me, tileW, tileH, x, a)
        }
    }
}

/**
 * Where a photo stack sits on screen, so the viewer can fly its photos out of it and back into it.
 * [base] is the top card's spot (in root coordinates, before any tilt); each card further down the
 * stack sits [fan] further across, [drop] lower, turned [tilt] degrees more and a little smaller.
 */
class StackSpot(
    val group: String,
    val base: androidx.compose.ui.geometry.Rect,
    val sign: Float,
    val fan: Float,
    val drop: Float,
    val order: List<String>,
    val radius: Float,
) {
    companion object { const val TILT = 3.2f; const val SHRINK = 0.05f }
}

object PhotoFlight {
    /** The stack you just tapped, handed to the viewer. */
    var spot: StackSpot? = null
    /** The stack whose photos are out in the viewer right now (hidden in the chat meanwhile). */
    var out by mutableStateOf<String?>(null)
    /** Coming back: the stack to restack, and the photo that should land on top. */
    var landTop by mutableStateOf<Pair<String, String>?>(null)
}

fun photoKey(m: Msg, ref: MediaRef) = m.key + "|" + ref.id

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

/**
 * Stack: a small fan of the photos with a count. Swipe it and the top photo slides off and tucks in at
 * the back, so you flick through them right in the chat. Tap to open them all in the viewer.
 */
@Composable
private fun StackStyle(items: List<Pair<Msg, MediaRef>>, me: Boolean, w: Dp, h: Dp, x: RowCtx, a: RowActs) {
    val n = items.size
    val t = x.t
    val scope = rememberCoroutineScope()
    val group = items[0].first.key
    val keys = remember(items) { items.map { photoKey(it.first, it.second) } }
    var order by remember(keys) { mutableStateOf(keys.indices.toList()) }
    val dx = remember { androidx.compose.animation.core.Animatable(0f) }
    val density = LocalDensity.current
    val wPx = with(density) { w.toPx() }
    val fanPx = with(density) { 13.dp.toPx() }
    val dropPx = with(density) { 3.dp.toPx() }
    val sign = if (me) -1f else 1f
    val here = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    // Back from the viewer: the photo you were on lands on top.
    val land = PhotoFlight.landTop
    LaunchedEffect(land) {
        if (land != null && land.first == group) {
            val top = keys.indexOf(land.second)
            if (top >= 0) { order = (0 until n).map { (top + it) % n }; dx.snapTo(0f) }
            PhotoFlight.landTop = null
        }
    }
    val away = PhotoFlight.out == group
    Box(
        Modifier.size(w + 13.dp * 2 + 8.dp, h + 14.dp)
            .graphicsLayer { alpha = if (away) 0f else 1f }
            .onGloballyPositioned { here[0] = it }
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (kotlin.math.abs(dx.value) > wPx * 0.28f && n > 1) {
                                // Off it goes, then it tucks in at the back.
                                dx.animateTo(kotlin.math.sign(dx.value) * wPx * 1.15f, tween(170, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                                order = order.drop(1) + order.first()
                                dx.snapTo(0f)
                            } else dx.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f))
                        }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f)) } },
                ) { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } }
            },
    ) {
        order.forEachIndexed { depth, i -> if (depth <= 3) {
            androidx.compose.runtime.key(keys[i]) {
                val (m, ref) = items[i]
                val d by androidx.compose.animation.core.animateFloatAsState(depth.toFloat(), spring(dampingRatio = 0.66f, stiffness = 360f), label = "stack")
                val seen by androidx.compose.animation.core.animateFloatAsState(if (depth > 2) 0f else 1f, tween(200), label = "stackSeen")
                Box(
                    Modifier.align(if (me) Alignment.TopEnd else Alignment.TopStart).padding(top = 4.dp, start = if (me) 0.dp else 4.dp, end = if (me) 4.dp else 0.dp)
                        .zIndex((n - depth).toFloat()).size(w, h)
                        .graphicsLayer {
                            val top = depth == 0
                            translationX = sign * d * fanPx + if (top) dx.value else 0f
                            translationY = d * dropPx
                            rotationZ = sign * d * StackSpot.TILT + if (top) dx.value / wPx * 9f else 0f
                            val sc = 1f - d * StackSpot.SHRINK; scaleX = sc; scaleY = sc
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                            alpha = seen
                        }
                        .shadow(if (depth == 0) 8.dp else 4.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow)
                        .tileGestures(m, ref, a, radius = 22.dp) {
                            // Tapping the stack opens the photo on top, and the rest fly out with it.
                            val c = here[0]
                            if (c != null && c.isAttached) {
                                val box = c.boundsInRoot()
                                val pad = with(density) { 4.dp.toPx() }
                                val left = if (me) box.right - pad - wPx else box.left + pad
                                val topY = box.top + with(density) { 4.dp.toPx() }
                                PhotoFlight.spot = StackSpot(
                                    group, androidx.compose.ui.geometry.Rect(left, topY, left + wPx, topY + with(density) { h.toPx() }),
                                    sign, fanPx, dropPx, order.map { keys[it] }, with(density) { 22.dp.toPx() },
                                )
                            }
                            val (tm, tr) = items[order.first()]
                            a.onOpenItem?.invoke(tm, tr) ?: a.onOpenMedia(tr)
                            Unit
                        },
                ) {
                    TileContent(m, ref, x, a)
                    if (depth == 0 && n > 1) GlassTag("$n", Modifier.align(if (me) Alignment.TopStart else Alignment.TopEnd).padding(9.dp), icon = Icons.stack)
                }
            }
        } }
    }
}

/** Tap to open, hold for the menu, double-tap to heart: for one photo in a group. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tileGestures(m: Msg, ref: MediaRef, a: RowActs, radius: Dp = 20.dp, tap: (() -> Unit?)? = null): Modifier = this
    .clip(RoundedCornerShape(radius))
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


/**
 * Holding a photo in a group: the photos slide together into one fanned stack, so the menu below
 * clearly acts on all of them. The one you held sits on top.
 */
@Composable
fun GatherStack(group: List<Pair<Msg, MediaRef>>, heldKey: String, t: Tok) {
    val repo = LocalRepo.current
    val ordered = group.sortedBy { if (it.first.key == heldKey) 1 else 0 }.takeLast(4)
    val n = ordered.size
    val gather = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { gather.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 240f)) }
    val cardW = 158.dp; val cardH = 192.dp
    val tilts = listOf(0f, -7f, 6.5f, -3.5f)
    Box(Modifier.width(cardW + 56.dp).height(cardH + 44.dp), contentAlignment = Alignment.Center) {
        ordered.forEachIndexed { i, (_, ref) ->
            val depth = n - 1 - i
            val spread = (i - (n - 1) / 2f) * 64f
            Box(
                Modifier.size(cardW, cardH)
                    .graphicsLayer {
                        val g = gather.value
                        translationX = spread * density * (1f - g)
                        rotationZ = tilts[depth] * g
                        val s = 1f - depth * 0.045f * g
                        scaleX = s; scaleY = s
                    }
                    .shadow(if (depth == 0) 10.dp else 4.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(t.tone),
                contentAlignment = Alignment.Center,
            ) {
                val path = repo.pathOf(ref).ifEmpty { repo.media[ref.thumbId].orEmpty() }.ifEmpty { ref.previewPath }
                if (path.isNotEmpty()) AsyncImage(File(path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (ref.isVideo) Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Ico(Icons.play, Color.White, 18.dp, 1f, fill = Color.White)
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = gather.value.coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 5.dp),
        ) { T("${group.size} " + if (group.all { it.second.isVideo }) "videos" else if (group.any { it.second.isVideo }) "items" else "photos", size = 12.5.sp, weight = FontWeight.SemiBold, color = Color.White) }
    }
}
