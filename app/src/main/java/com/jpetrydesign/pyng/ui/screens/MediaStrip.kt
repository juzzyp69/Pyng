package com.jpetrydesign.pyng.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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

/** The strip: a count, a row of photos that snaps one at a time with the next one peeking in, and dots. */
@Composable
fun MediaStrip(items: List<Pair<Msg, MediaRef>>, me: Boolean, x: RowCtx, a: RowActs) {
    val t = x.t
    val n = items.size
    val vids = items.count { it.second.isVideo }
    val label = when { vids == 0 -> "$n photos"; vids == n -> "$n videos"; else -> "$n photos and videos" }
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileW = minOf(maxWidth * 0.62f, 300.dp)
        val tileH = tileW * 1.25f
        val tail = (maxWidth - tileW).coerceAtLeast(0.dp)
        val tilePx = with(LocalDensity.current) { tileW.toPx() }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
            Row(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.stack, t.soft, 14.dp, 2f)
                Spacer(Modifier.width(6.dp))
                T(label, size = 12.5.sp, weight = FontWeight.Medium, color = t.soft)
            }
            LazyRow(
                state = state, flingBehavior = fling, reverseLayout = me,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = if (me) PaddingValues(start = tail) else PaddingValues(end = tail),
            ) {
                itemsIndexed(items, key = { _, it -> it.first.key + "|" + it.second.id }) { _, item ->
                    StripTile(item.first, item.second, tileW, tileH, x, a)
                }
            }
            if (n <= 12) {
                val on by remember { derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > tilePx / 2f) 1 else 0 } }
                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(n) { i ->
                        val idx = if (me) n - 1 - i else i
                        val active = idx == on
                        val w by animateDpAsState(if (active) 16.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot")
                        Box(Modifier.height(6.dp).width(w).clip(RoundedCornerShape(3.dp)).background(if (active) t.p else t.faint))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripTile(m: Msg, ref: MediaRef, w: Dp, h: Dp, x: RowCtx, a: RowActs) {
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
        Modifier.size(w, h)
            .onGloballyPositioned { c -> a.onPlacedKey?.invoke(m.key, c) }
            .clip(RoundedCornerShape(20.dp)).background(t.tone)
            .combinedClickable(
                onClick = {
                    when {
                        ref.waiting -> Unit
                        ref.isImage && full.isEmpty() && show.isEmpty() -> repo.ensureMedia(ref, manual = true)
                        else -> a.onOpenItem?.invoke(m, ref) ?: a.onOpenMedia(ref)
                    }
                },
                onLongClick = { a.onLongItem?.invoke(m) ?: a.onLong() },
                onDoubleClick = { a.onDoubleItem?.invoke(m) ?: a.onDouble() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (show.isNotEmpty()) AsyncImage(File(show), if (ref.isVideo) "Video" else "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (ref.isVideo) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) else Ico(Icons.play, Color.White, 24.dp, 1f, fill = Color.White)
            }
        } else if (ref.waiting || (full.isEmpty() && busy)) {
            CircularProgressIndicator(Modifier.size(26.dp), color = if (show.isEmpty()) t.p else Color.White, strokeWidth = 2.dp)
        }
        // Still sending, or didn't send.
        if (m.pending && !ref.waiting) Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp) }
        if (m.failed) Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(t.danger),
            contentAlignment = Alignment.Center,
        ) { Ico(Icons.warn, Color.White, 14.dp, 2.2f) }
        // This photo's reactions, on its own corner.
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
