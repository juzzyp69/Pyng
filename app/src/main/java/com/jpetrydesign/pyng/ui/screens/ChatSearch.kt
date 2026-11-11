package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.Tok
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Something shared in a chat that search can show: a photo, video, file or link. */
private sealed class Shared(val m: Msg) {
    class Media(m: Msg, val ref: MediaRef) : Shared(m)
    class Link(m: Msg, val url: String) : Shared(m)
}

/**
 * Search inside one chat, in the "Quiet" layout: four shortcut tiles, a short list of what was
 * shared last, and your recent searches. Typing turns it into a list of matches; tapping one
 * jumps to it in the chat.
 */
@Composable
fun ChatSearchPanel(
    t: Tok, conv: Conv?, msgs: List<Msg>, query: String, lookingFurther: Boolean,
    onFill: (String) -> Unit, onJump: (Msg) -> Unit, onOpen: (Msg, MediaRef) -> Unit, modifier: Modifier = Modifier,
) {
    val repo = LocalRepo.current
    var kind by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = kind != null && query.isBlank()) { kind = null }
    val newestFirst = remember(msgs) { msgs.asReversed() }
    val photos = remember(newestFirst) { newestFirst.flatMap { m -> m.media.filter { it.isImage && !it.waiting }.map { Shared.Media(m, it) } } }
    val videos = remember(newestFirst) { newestFirst.flatMap { m -> m.media.filter { it.isVideo && !it.waiting }.map { Shared.Media(m, it) } } }
    val files = remember(newestFirst) { newestFirst.flatMap { m -> m.media.filter { !it.isImage && !it.isVideo && !it.isContact }.map { Shared.Media(m, it) } } }
    val links = remember(newestFirst) { newestFirst.flatMap { m -> linksIn(m.text).map { Shared.Link(m, it) } }.distinctBy { it.url } }

    Box(modifier.fillMaxSize().background(t.bg)) {
        AnimatedContent(
            targetState = if (query.isNotBlank()) "results" else kind ?: "home",
            transitionSpec = {
                if (targetState != "home" && targetState != "results") (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
                else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            },
            label = "chatSearch",
        ) { screen ->
            when (screen) {
                "results" -> Results(t, conv, msgs, query, lookingFurther, onJump)
                "Photos" -> MediaGrid(t, "Photos", photos, lookingFurther, { kind = null }, onOpen)
                "Videos" -> MediaGrid(t, "Videos", videos, lookingFurther, { kind = null }, onOpen)
                "Links" -> SharedList(t, "Links", links, { kind = null }, onOpen)
                "Files" -> SharedList(t, "Files", files, { kind = null }, onOpen)
                else -> Quiet(t, photos.size, videos.size, links.size, files.size,
                    latest = (photos.take(3) + videos.take(2) + links.take(3)).sortedByDescending { it.m.ts }.take(4),
                    recent = recentSearches(repo), onKind = { kind = it }, onFill = onFill, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun Quiet(
    t: Tok, nPhotos: Int, nVideos: Int, nLinks: Int, nFiles: Int, latest: List<Shared>, recent: List<String>,
    onKind: (String) -> Unit, onFill: (String) -> Unit, onOpen: (Msg, MediaRef) -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 40.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Photos", Icons.gallery, nPhotos), Triple("Videos", Icons.video, nVideos), Triple("Links", Icons.link, nLinks), Triple("Files", Icons.file, nFiles)).forEach { (name, icon, n) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(t.card).clickable { onKind(name) }.padding(top = 12.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(icon, t.p, 19.dp, 2f) }
                    Spacer(Modifier.height(6.dp))
                    T(name, size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    T(n.toString(), size = 11.5.sp, color = t.faint)
                }
            }
        }
        if (latest.isNotEmpty()) {
            Heading(t, "Latest shared")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.card)) {
                latest.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
                    Row(
                        Modifier.fillMaxWidth().clickable { open(ctx, s, onOpen) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SharedThumb(t, s, 48.dp, RoundedCornerShape(14.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(titleOf(s), size = 14.5.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            T(subOf(s), size = 12.5.sp, color = t.soft, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            Heading(t, "Recent searches")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { w ->
                    Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(t.card).clickable { onFill(w) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        T(w, size = 13.5.sp, color = t.soft, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(t: Tok, text: String) {
    T(text, size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
}

/** A sub-page header: back arrow, title and count. */
@Composable
private fun SubTop(t: Tok, title: String, n: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.back, "Back", onBack, size = 40.dp, iconSize = 18.dp)
        Spacer(Modifier.width(4.dp))
        T(title, size = 17.sp, weight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        T(n.toString(), size = 14.sp, color = t.faint)
    }
}

private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

@Composable
private fun MediaGrid(t: Tok, title: String, items: List<Shared.Media>, lookingFurther: Boolean, onBack: () -> Unit, onOpen: (Msg, MediaRef) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubTop(t, title, items.size, onBack)
        if (items.isEmpty()) { Empty(t, if (lookingFurther) "Looking further back…" else "No ${title.lowercase()} in this chat yet"); return }
        val byMonth = items.groupBy { monthFmt.format(Date(it.m.ts)) }
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            byMonth.forEach { (month, list) ->
                item(span = { GridItemSpan(3) }) { T(month, size = 13.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) }
                items(list, key = { it.m.key + it.ref.id }) { s ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { onOpen(s.m, s.ref) }) { SharedThumb(t, s, null, RoundedCornerShape(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SharedList(t: Tok, title: String, items: List<Shared>, onBack: () -> Unit, onOpen: (Msg, MediaRef) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        SubTop(t, title, items.size, onBack)
        if (items.isEmpty()) { Empty(t, "No ${title.lowercase()} in this chat yet"); return }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { s -> s.m.key + (if (s is Shared.Link) s.url else (s as Shared.Media).ref.id) }) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.card).clickable { open(ctx, s, onOpen) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SharedThumb(t, s, 64.dp, RoundedCornerShape(14.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(titleOf(s), size = 14.5.sp, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 19.sp)
                        Spacer(Modifier.height(3.dp))
                        T(subOf(s), size = 12.5.sp, color = t.soft, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Results(t: Tok, conv: Conv?, msgs: List<Msg>, query: String, lookingFurther: Boolean, onJump: (Msg) -> Unit) {
    val q = query.trim().lowercase()
    val hits = remember(msgs, q) { msgs.filter { !it.isSys && it.text.lowercase().contains(q) }.asReversed() }
    if (hits.isEmpty()) { Empty(t, if (lookingFurther) "Looking further back…" else "Nothing in this chat matches “${query.trim()}”"); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 40.dp)) {
        var day = ""
        hits.forEach { m ->
            val d = listTime(m.ts).let { if (it.contains(":")) "Today" else it }
            if (d != day) { day = d; item("d:" + d + m.key) { T(d, size = 12.sp, weight = FontWeight.SemiBold, color = t.faint, modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp)) } }
            item(m.key) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onJump(m) }.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val who = if (m.fromMe) null else conv?.people?.firstOrNull { it.id == m.participantId }
                    if (who != null) WhoAvatar(whoOf(who), 34.dp, 12.sp)
                    else Box(Modifier.size(34.dp).clip(CircleShape).background(if (m.fromMe) t.fill else t.tone), contentAlignment = Alignment.Center) {
                        T(if (m.fromMe) "You" else initialsOf(nameOf(m, conv)), size = 11.sp, weight = FontWeight.Bold, color = if (m.fromMe) t.onFill else t.p)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            T(nameOf(m, conv), size = 12.5.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                            T(clock(m.ts), size = 12.5.sp, color = t.soft)
                        }
                        Spacer(Modifier.height(2.dp))
                        androidx.compose.material3.Text(
                            highlight(m.text, q, t), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = 14.5.sp, lineHeight = 20.sp, color = t.ink),
                        )
                    }
                    m.media.firstOrNull { it.isImage || it.isVideo }?.let { ref ->
                        Spacer(Modifier.width(10.dp))
                        SharedThumb(t, Shared.Media(m, ref), 44.dp, RoundedCornerShape(12.dp))
                    }
                }
            }
        }
        if (lookingFurther) item("more") { Empty(t, "Looking further back…", top = 12.dp) }
    }
}

/** The match in bold on a soft yellow, so it's easy to spot. */
private fun highlight(text: String, q: String, t: Tok) = buildAnnotatedString {
    val lower = text.lowercase()
    var i = 0
    // Start a little before the first match so it's visible in a long message.
    val first = lower.indexOf(q)
    val from = if (first > 60) (lower.lastIndexOf(' ', first - 30).takeIf { it > 0 } ?: (first - 30)) else 0
    if (from > 0) { append("…"); i = from }
    while (i < text.length) {
        val at = lower.indexOf(q, i)
        if (at < 0 || q.isEmpty()) { append(text.substring(i)); break }
        append(text.substring(i, at))
        withStyle(SpanStyle(background = if (t.dark) Color(0xFF6B5A12) else Color(0xFFFFE38A), color = if (t.dark) Color(0xFFFFF4C9) else Color(0xFF2A2200), fontWeight = FontWeight.SemiBold)) {
            append(text.substring(at, at + q.length))
        }
        i = at + q.length
    }
}

@Composable
private fun Empty(t: Tok, text: String, top: Dp = 48.dp) {
    Box(Modifier.fillMaxWidth().padding(top = top, start = 24.dp, end = 24.dp), contentAlignment = Alignment.Center) {
        T(text, size = 14.sp, color = t.soft, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** A square preview: the photo or video frame, a link's preview image, or a file icon. */
@Composable
private fun SharedThumb(t: Tok, s: Shared, size: Dp?, shape: RoundedCornerShape) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val mod = (if (size != null) Modifier.size(size) else Modifier.fillMaxSize()).clip(shape).background(t.tone)
    when (s) {
        is Shared.Media -> {
            val ref = s.ref
            val path = if (ref.isVideo) (repo.media[ref.thumbId] ?: ref.previewPath.ifEmpty { null } ?: repo.pathOf(ref))
                else repo.pathOf(ref).ifEmpty { repo.media[ref.thumbId] ?: ref.previewPath }
            LaunchedEffect(ref.id) { if (path.isEmpty() && (ref.isImage || ref.isVideo)) repo.ensureMedia(ref, thumb = true) }
            Box(mod, contentAlignment = Alignment.Center) {
                if ((ref.isImage || ref.isVideo) && path.isNotEmpty()) AsyncImage(File(path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (!ref.isImage && !ref.isVideo) T(ref.name.substringAfterLast('.', "FILE").take(4).uppercase(), size = 11.sp, weight = FontWeight.Bold, color = t.p)
                else Ico(if (ref.isVideo) Icons.video else Icons.gallery, t.p.copy(alpha = 0.55f), 20.dp)
                if (ref.isVideo && path.isNotEmpty()) Box(Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
                    Ico(Icons.play, Color.White, 12.dp, 2f, fill = Color.White)
                }
            }
        }
        is Shared.Link -> {
            LaunchedEffect(s.url) { Links.init(ctx); Links.load(s.url) }
            val img = Links.cache[s.url]?.image
            Box(mod, contentAlignment = Alignment.Center) {
                if (img != null) {
                    val req = remember(img) { coil.request.ImageRequest.Builder(ctx).data(img).addHeader("User-Agent", Links.UA).crossfade(true).build() }
                    AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else Ico(Icons.link, t.p, 20.dp, 2f)
            }
        }
    }
}

private fun hostOf(url: String) = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault(url)

private fun titleOf(s: Shared): String = when (s) {
    is Shared.Link -> Links.cache[s.url]?.title?.ifBlank { null } ?: hostOf(s.url)
    is Shared.Media -> when {
        s.ref.isImage -> "Photo"
        s.ref.isVideo -> "Video"
        else -> s.ref.name.ifBlank { "File" }
    }
}

private fun subOf(s: Shared): String {
    val who = nameOf(s.m, null)
    return when (s) {
        is Shared.Link -> hostOf(s.url) + " · " + listTime(s.m.ts)
        is Shared.Media -> if (!s.ref.isImage && !s.ref.isVideo && s.ref.size > 0) sizeOf(s.ref.size) + " · " + listTime(s.m.ts) else who + " · " + listTime(s.m.ts)
    }
}

private fun sizeOf(b: Long) = if (b >= 1_000_000) String.format(Locale.US, "%.1f MB", b / 1e6) else "${maxOf(1, b / 1000)} KB"

private fun open(ctx: android.content.Context, s: Shared, onOpen: (Msg, MediaRef) -> Unit) {
    when (s) {
        is Shared.Media -> onOpen(s.m, s.ref)
        is Shared.Link -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url))) }
    }
}

/** Your last few searches inside chats, newest first. */
fun recentSearches(repo: com.jpetrydesign.pyng.data.Repo): List<String> =
    repo.prefs.state.opt("recentSearches").split('\u001F').filter { it.isNotBlank() }

fun rememberSearch(repo: com.jpetrydesign.pyng.data.Repo, q: String) {
    val w = q.trim().take(40)
    if (w.length < 2) return
    val list = (listOf(w) + recentSearches(repo).filter { !it.equals(w, ignoreCase = true) }).take(6)
    repo.prefs.setOpt("recentSearches", list.joinToString("\u001F"))
}

private fun nameOf(m: Msg, conv: Conv?): String =
    if (m.fromMe) "You" else conv?.people?.firstOrNull { it.id == m.participantId }?.short ?: m.senderFirst.ifBlank { m.sender.substringBefore(' ') }.ifBlank { conv?.shortTitle ?: "" }
