package com.jpetrydesign.pyng.ui.screens

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.jpetrydesign.pyng.ui.kit.T
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
class RowCtx(
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
    val timeLabel: String,
    val isNew: Boolean,
    val burst: String?,
    val myId: String,
)

class RowActs(
    val onSide: () -> Unit,
    val onTap: () -> Unit,
    val onDouble: () -> Unit,
    val onLong: () -> Unit,
    val onReply: () -> Unit,
    val onOpenMedia: (MediaRef) -> Unit,
    val onRetry: () -> Unit,
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
    val enterA = remember { Animatable(if (x.isNew && motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (enterA.value < 1f) enterA.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f)) }
    val shape = bubbleShape(me, x.prevSame, x.nextSame, x.shape.first, x.shape.second)
    val jumbo = if (m.media.isEmpty() && x.quote == null) jumboCount(m.text) else 0

    Column(Modifier.fillMaxWidth()) {
        if (x.senderName != null) {
            T(x.senderName, size = 11.5.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.padding(start = if (x.group) 44.dp else 12.dp, top = 10.dp))
        }
        Box(
            Modifier.fillMaxWidth().padding(top = if (x.prevSame) 2.dp else 10.dp)
                .graphicsLayer {
                    translationX = x.tsShift
                    val k = enterA.value
                    alpha = k.coerceIn(0f, 1f)
                    if (k < 1f) {
                        translationX += (if (me) 80f else -80f) * (1f - k) * density.density / 2.6f
                        val s = 0.9f + 0.1f * k; scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (me) 1f else 0f, 1f)
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = a.onSide),
        ) {
            // Timestamp revealed when the list is dragged right.
            if (x.tsShift > 1f) {
                T(
                    x.timeLabel, size = 11.sp, weight = FontWeight.SemiBold, color = t.soft,
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = (-70).dp).width(60.dp)
                        .graphicsLayer { alpha = (x.tsShift / with(density) { 76.dp.toPx() }).coerceIn(0f, 1f) },
                    align = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            // Reply arrow that appears while swiping a bubble.
            if (swipe.value != 0f) {
                val k = (abs(swipe.value) / trigger).coerceIn(0f, 1f)
                Box(
                    Modifier.align(if (me) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 6.dp).size(32.dp)
                        .graphicsLayer { alpha = k; scaleX = k; scaleY = k }.clip(CircleShape).background(t.tone),
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.reply, t.p, 16.dp, 2.2f) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (x.selecting) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).then(if (x.selected) Modifier.background(t.fill) else Modifier.border(2.dp, t.faint, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (x.selected) Ico(Icons.check, t.onFill, 13.dp, 3f) }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth(0.86f).wrapAlign(me)) {
                        if (x.group && !me) {
                            val w = x.senderWho
                            Box(Modifier.size(26.dp).graphicsLayer { alpha = if (x.nextSame) 0f else 1f }) {
                                if (w != null) Avatar(w.photo, w.ini, 26.dp, t.tone, t.p, 10.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Box(Modifier.weight(1f, fill = false)) {
                            Box(
                                Modifier
                                    .offset { IntOffset(swipe.value.roundToInt(), 0) }
                                    .padding(bottom = if (m.reactions.isNotEmpty()) 14.dp else 0.dp)
                                    .then(if (x.highlighted) Modifier.border(3.dp, t.p, shape) else Modifier)
                                    .pointerInput(m.key, x.selecting) {
                                        detectTapGestures(
                                            onTap = { a.onTap() },
                                            onDoubleTap = if (x.selecting) null else ({ a.onDouble() }),
                                            onLongPress = { a.onLong() },
                                        )
                                    }
                                    .pointerInput(m.key) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (abs(swipe.value) > trigger) a.onReply()
                                                scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                                            },
                                            onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                                        ) { ch, d ->
                                            val want = if (me) -(swipe.value + d) else swipe.value + d
                                            if (want >= 0f || swipe.value != 0f) {
                                                ch.consume()
                                                val v = want.coerceIn(0f, maxPx)
                                                scope.launch { swipe.snapTo(if (me) -v else v) }
                                            }
                                        }
                                    },
                            ) {
                                BubbleBody(m, x, shape, jumbo, a)
                            }
                            if (x.burst != null) BurstEmoji(x.burst, Modifier.align(Alignment.Center))
                            if (m.reactions.isNotEmpty()) {
                                val all = m.reactions.sumOf { it.count }
                                val txt = m.reactions.joinToString("") { it.emoji } + if (all > 1) " $all" else ""
                                ReactionPill(txt, t, Modifier.align(if (me) Alignment.BottomEnd else Alignment.BottomStart).padding(horizontal = 10.dp).offset(y = 2.dp))
                            }
                        }
                    }
                    if (m.failed) {
                        Row(Modifier.padding(top = 5.dp, end = 4.dp).clickable(onClick = a.onRetry), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Icons.warn, t.danger, 14.dp, 2f)
                            Spacer(Modifier.width(5.dp))
                            T("Not sent · Tap to retry", size = 11.5.sp, weight = FontWeight.SemiBold, color = t.danger)
                        }
                    } else if (x.info != null) {
                        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp).fadeUp(), verticalAlignment = Alignment.CenterVertically) {
                            Ico(x.infoIcon, x.infoInk, 14.dp, 2.1f)
                            Spacer(Modifier.width(5.dp))
                            T(x.info, size = 11.5.sp, weight = FontWeight.Medium, color = t.soft)
                        }
                    }
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
        }
    }
}

private fun Modifier.wrapAlign(me: Boolean): Modifier = this.then(Modifier)

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
            .border(2.dp, t.bg, RoundedCornerShape(12.dp)).padding(2.dp)
            .height(22.dp).clip(RoundedCornerShape(11.dp)).background(if (t.dark) Color(0xFF2A2D36) else Color.White).padding(horizontal = 7.dp),
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

@Composable
private fun BubbleBody(m: Msg, x: RowCtx, shape: RoundedCornerShape, jumbo: Int, a: RowActs) {
    val t = x.t
    val me = m.fromMe
    val bg: Brush = if (me) t.outBg else Brush.linearGradient(listOf(t.inBg, t.inBg))
    val fg = if (me) t.outInk else t.inInk
    val sub = if (me) (if (t.dark) Color(0x9E101218) else Color(0xBFFFFFFF)) else t.soft
    val quoteBg = if (me) Color.White.copy(alpha = 0.18f) else t.neutral
    val bold = LocalRepo.current.prefs.state.on("boldText")
    Column(Modifier.graphicsLayer { alpha = if (m.pending && m.media.isNotEmpty()) 0.7f else 1f }) {
        m.media.forEachIndexed { i, ref ->
            if (i > 0) Spacer(Modifier.height(4.dp))
            MediaView(m, ref, shape, me, fg, sub, bg, a)
        }
        val text = m.text
        if (jumbo > 0) {
            T(text, size = listOf(58f, 58f, 48f, 40f)[jumbo].sp, lineHeight = (listOf(58f, 58f, 48f, 40f)[jumbo] * 1.15f).sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        } else if (text.isNotEmpty() || x.quote != null) {
            if (m.media.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Column(Modifier.clip(shape).background(bg).padding(horizontal = 14.dp, vertical = 9.dp).widthIn(min = 20.dp)) {
                if (x.quote != null) {
                    Column(Modifier.padding(top = 1.dp, bottom = 7.dp).clip(RoundedCornerShape(12.dp)).background(quoteBg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        T(x.quote.first, size = 12.5.sp, weight = FontWeight.Bold, color = fg)
                        T(x.quote.second, size = 12.5.sp, color = fg.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.widthIn(max = 220.dp))
                    }
                }
                if (text.isNotEmpty()) LinkText(text, x.fontSize, fg, if (bold) FontWeight.Medium else FontWeight.Normal)
                val url = if (x.showPreview) detect(text).url else null
                if (url != null) LinkCard(url, quoteBg, fg)
            }
        }
    }
}

// ---------- media ----------

@Composable
private fun MediaView(m: Msg, ref: MediaRef, shape: RoundedCornerShape, me: Boolean, fg: Color, sub: Color, bg: Brush, a: RowActs) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty()
    val auto = remember { repo.autoDownloadAllowed() }
    LaunchedEffect(ref.id) {
        if (ref.localPath.isEmpty()) {
            if ((ref.isImage || ref.isAudio) && auto) repo.ensureMedia(ref)
            if ((ref.isVideo || ref.isImage) && ref.thumbId.isNotEmpty()) repo.ensureMedia(ref, thumb = true)
        }
    }
    val busy = repo.mediaBusy[ref.id] == true
    when {
        ref.isImage || ref.isVideo -> {
            val ratio = if (ref.w > 0 && ref.h > 0) ref.h.toFloat() / ref.w else 1f
            val w = 232.dp
            val h = (232f * ratio).coerceIn(140f, 320f).dp
            Box(Modifier.size(w, h).clip(shape).background(t.tone).clickable { a.onOpenMedia(ref) }, contentAlignment = Alignment.Center) {
                val show = if (ref.isImage && full.isNotEmpty()) full else thumb
                if (show.isNotEmpty()) AsyncImage(model = File(show), contentDescription = if (ref.isVideo) "Video" else "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (ref.isVideo && full.isNotEmpty()) AsyncImage(model = File(full), contentDescription = "Video", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (ref.isVideo) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) else Ico(Icons.play, Color.White, 24.dp, 1f, fill = Color.White)
                    }
                } else if (full.isEmpty()) {
                    if (busy || auto) CircularProgressIndicator(Modifier.size(26.dp), color = t.p, strokeWidth = 2.dp)
                    else Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable { repo.ensureMedia(ref) }, contentAlignment = Alignment.Center) {
                        Ico(Icons.download, Color.White, 22.dp, 2f)
                    }
                }
            }
        }
        ref.isAudio -> VoiceBubble(ref, full, busy, shape, bg, fg, sub, me)
        else -> {
            Row(
                Modifier.clip(shape).background(bg).clickable { a.onOpenMedia(ref) }.padding(10.dp).widthIn(min = 180.dp, max = 260.dp),
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

@Composable
private fun VoiceBubble(ref: MediaRef, path: String, busy: Boolean, shape: RoundedCornerShape, bg: Brush, fg: Color, sub: Color, me: Boolean) {
    val repo = LocalRepo.current
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durMs by remember { mutableStateOf(0) }
    val player = remember { mutableStateMapOf<String, MediaPlayer>() }
    LaunchedEffect(path) {
        if (path.isNotEmpty()) durMs = withContext(Dispatchers.IO) {
            runCatching { MediaMetadataRetriever().run { setDataSource(path); val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0; release(); d } }.getOrDefault(0)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val mp = player["p"] ?: break
            progress = if (mp.duration > 0) mp.currentPosition.toFloat() / mp.duration else 0f
            delay(80)
        }
    }
    DisposableEffect(Unit) { onDispose { player["p"]?.release(); player.clear() } }
    val bars = remember(ref.id) { List(26) { i -> (0.3f + ((ref.id.hashCode() shr (i % 16)) and 7) / 10f).coerceIn(0.25f, 1f) } }
    Row(
        Modifier.clip(shape).background(bg).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (me) Color.White.copy(alpha = 0.22f) else LocalTok.current.tone).clickable {
                if (path.isEmpty()) { repo.ensureMedia(ref); return@clickable }
                val mp = player["p"]
                if (mp != null && playing) { mp.pause(); playing = false }
                else if (mp != null) { mp.start(); playing = true }
                else runCatching {
                    MediaPlayer().apply {
                        setDataSource(path); prepare(); start()
                        setOnCompletionListener { playing = false; progress = 0f; release(); player.clear() }
                    }.also { player["p"] = it; playing = true }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
            else Ico(if (playing) Icons.pause else Icons.play, fg, 16.dp, 1f, fill = fg)
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEachIndexed { i, h ->
                val on = i / bars.size.toFloat() < progress
                Box(Modifier.width(3.dp).height((22 * h).dp).clip(RoundedCornerShape(2.dp)).background(if (on || !playing && progress == 0f) fg else fg.copy(alpha = 0.4f)))
            }
        }
        Spacer(Modifier.width(10.dp))
        val secs = durMs / 1000
        T(if (durMs > 0) "%d:%02d".format(secs / 60, secs % 60) else "Voice", size = 13.sp, weight = FontWeight.SemiBold, color = fg)
    }
}

// ---------- link previews ----------

data class LinkMeta(val title: String, val host: String, val image: String?)

object Links {
    val cache = mutableStateMapOf<String, LinkMeta?>()
    private val busy = mutableSetOf<String>()

    suspend fun load(url: String) {
        if (cache.containsKey(url) || !busy.add(url)) return
        val meta = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000; c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36")
                val html = c.inputStream.bufferedReader().use { r -> val buf = CharArray(120_000); val n = r.read(buf); if (n > 0) String(buf, 0, n) else "" }
                fun og(p: String) = Regex("<meta[^>]+(?:property|name)=[\"']$p[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                    ?: Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']$p[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                val title = og("og:title") ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: ""
                var img = og("og:image")
                if (img != null && img.startsWith("/")) img = URL(URL(url), img).toString()
                LinkMeta(android.text.Html.fromHtml(title.trim(), 0).toString(), URL(url).host.removePrefix("www."), img)
            }.getOrNull()
        }
        cache[url] = meta
    }
}

@Composable
private fun LinkCard(url: String, bg: Color, fg: Color) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(url) { Links.load(url) }
    val meta = Links.cache[url]
    val host = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault(url)
    Column(
        Modifier.padding(top = 8.dp).widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } },
    ) {
        val t = LocalTok.current
        Box(Modifier.fillMaxWidth().height(if (meta?.image != null) 120.dp else 64.dp).background(Brush.linearGradient(listOf(t.p.copy(alpha = 0.33f), t.s.copy(alpha = 0.53f))))) {
            if (meta?.image != null) AsyncImage(model = meta.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            T(meta?.title?.ifBlank { null } ?: "Link", size = 12.5.sp, weight = FontWeight.Bold, color = fg, maxLines = 2)
            T(meta?.host ?: host, size = 11.5.sp, color = fg.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

@Composable
private fun LinkText(text: String, size: Float, color: Color, weight: FontWeight) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val links = remember(text) { Regex("https?://[^\\s]+").findAll(text).toList() }
    if (links.isEmpty()) {
        T(text, size = size.sp, color = color, lineHeight = (size * 1.4f).sp, weight = weight)
        return
    }
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
    androidx.compose.material3.Text(
        annotated,
        style = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = size.sp, lineHeight = (size * 1.4f).sp, color = color, fontWeight = weight),
    )
    @Suppress("UNUSED_VARIABLE") val unused = ctx
}

@Suppress("unused")
private fun Modifier.minH(h: Dp) = this.heightIn(min = h)
