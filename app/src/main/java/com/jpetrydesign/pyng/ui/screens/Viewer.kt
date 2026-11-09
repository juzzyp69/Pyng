package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.frost
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * Photos open over the conversation instead of on a separate black screen: the chat stays behind,
 * blurred and dimmed. Swipe sideways through every photo in the chat, pinch or double-tap to zoom,
 * swipe down to close. Reactions and Reply sit along the bottom.
 */
@Composable
fun PhotoViewer(
    open: Pair<String, String>?,
    items: List<Pair<Msg, MediaRef>>,
    conv: Conv?,
    myId: String,
    onClose: () -> Unit,
    onReply: (Msg) -> Unit,
    onReact: (Msg, String) -> Unit,
    onDelete: (Msg) -> Unit,
    onForward: (Msg) -> Unit,
) {
    val t = LocalTok.current
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    var last by remember { mutableStateOf(open) }
    if (open != null) last = open
    BackHandler(enabled = open != null) { onClose() }
    AnimatedVisibility(
        open != null,
        enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.86f, stiffness = 520f), initialScale = 0.94f),
        exit = fadeOut(tween(170)) + scaleOut(tween(170), targetScale = 0.97f),
    ) {
        val key = last ?: return@AnimatedVisibility
        if (items.isEmpty()) return@AnimatedVisibility
        val startIdx = remember(key) { items.indexOfFirst { it.first.key == key.first && it.second.id == key.second }.coerceAtLeast(0) }
        val pager = rememberPagerState(initialPage = startIdx) { items.size }
        val scope = rememberCoroutineScope()
        val dismiss = remember { Animatable(0f) }
        var chrome by remember { mutableStateOf(true) }
        var zoomed by remember { mutableStateOf(false) }
        val closeAt = with(LocalDensity.current) { 110.dp.toPx() }
        val fade = (1f - abs(dismiss.value) / (closeAt * 3f)).coerceIn(0f, 1f)
        val cur = items.getOrNull(pager.currentPage) ?: items.first()
        val (m, ref) = cur
        val showChrome = chrome && abs(dismiss.value) < closeAt * 0.4f
        val path = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
        Box(Modifier.fillMaxSize()) {
            // A soft backdrop over the blurred chat, so the photo stands on its own.
            Box(Modifier.fillMaxSize().background((if (t.dark) Color(0xFF0A0C11) else Color(0xFFF1F3F8)).copy(alpha = 0.9f * fade)))
            Column(Modifier.fillMaxSize()) {
                // Top: who sent it and when, plus save, share, delete and more. Its space is kept even when hidden.
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp)) {
                    androidx.compose.animation.AnimatedVisibility(showChrome, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                        var more by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircleButton(Icons.back, "Close", onClose)
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                T(if (m.fromMe) "You" else m.sender.ifBlank { conv?.title ?: "" }, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                                T(viewerDate(m.ts), size = 12.5.sp, color = t.soft, maxLines = 1)
                            }
                            CircleButton(Icons.download, "Save", { if (path.isNotEmpty()) saveToGallery(ctx, path, ref.mime) else repo.toast("Still downloading") }, iconSize = 20.dp)
                            CircleButton(Icons.share, "Share", {
                                if (path.isEmpty()) { repo.toast("Still downloading"); return@CircleButton }
                                runCatching {
                                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
                                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(ref.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                                }
                            }, iconSize = 20.dp)
                            if (m.id.isNotEmpty()) CircleButton(Icons.trash, "Delete", { onDelete(m) }, iconSize = 20.dp)
                            Box {
                                CircleButton(Icons.details, "More", { more = true }, iconSize = 20.dp)
                                DropdownMenu(more, { more = false }, containerColor = t.sheet, shape = RoundedCornerShape(18.dp)) {
                                    DropdownMenuItem({ T("Forward", size = 15.sp) }, { more = false; onForward(m) }, leadingIcon = { Ico(Icons.forward, t.ink, 18.dp) })
                                    DropdownMenuItem({ T("Open in another app", size = 15.sp) }, {
                                        more = false
                                        if (path.isNotEmpty()) openFile(ctx, path, ref.mime) else repo.toast("Still downloading")
                                    }, leadingIcon = { Ico(Icons.share, t.ink, 18.dp) })
                                    DropdownMenuItem({ T("Show in chat", size = 15.sp) }, { more = false; onClose() }, leadingIcon = { Ico(Icons.message, t.ink, 18.dp) })
                                }
                            }
                        }
                    }
                }
                // The photo or video sits between the bars, never under them.
                HorizontalPager(
                    pager, Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 14.dp,
                    userScrollEnabled = !zoomed && abs(dismiss.value) < 1f,
                    key = { i -> items[i].first.key + "|" + items[i].second.id },
                ) { page ->
                    val pr = items[page].second
                    val isCur = page == pager.currentPage
                    val drag: (Float) -> Unit = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } }
                    val dragEnd: () -> Unit = {
                        if (abs(dismiss.value) > closeAt) onClose()
                        else scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                    }
                    if (pr.isVideo) VideoPage(pr, current = isCur, dismissY = if (isCur) dismiss.value else 0f, onTap = { chrome = !chrome }, onDrag = drag, onDragEnd = dragEnd)
                    else ZoomPage(
                        pr, current = isCur,
                        dismissY = if (isCur) dismiss.value else 0f,
                        onZoomed = { z -> if (isCur) zoomed = z },
                        onTap = { chrome = !chrome },
                        onDrag = drag, onDragEnd = dragEnd,
                    )
                }
                // Bottom: quick reactions and Reply. Its space is kept too.
                Box(Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(showChrome && m.id.isNotEmpty(), enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val mine = m.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji
                            Row(
                                Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (t.dark) Color(0xFF1C1F27) else Color.White).padding(horizontal = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                repo.prefs.state.quickReacts.forEach { e ->
                                    Box(
                                        Modifier.size(39.dp).clip(CircleShape).background(if (e == mine) t.tone else Color.Transparent).press(0.85f) { onReact(m, e) },
                                        contentAlignment = Alignment.Center,
                                    ) { T(e, size = 20.sp) }
                                }
                            }
                            Row(
                                Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (t.dark) Color(0xFF1C1F27) else Color.White).press(0.92f) { onReply(m) }.padding(horizontal = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Ico(Icons.reply, t.ink, 18.dp)
                                Spacer(Modifier.width(6.dp))
                                T("Reply", size = 14.5.sp, weight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun viewerDate(ts: Long): String =
    if (ts <= 0) "" else java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ts))

/** One photo: pinch or double-tap to zoom, drag while zoomed to look around, drag down to close. */
@Composable
private fun ZoomPage(ref: MediaRef, current: Boolean, dismissY: Float, onZoomed: (Boolean) -> Unit, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty().ifEmpty { ref.previewPath }
    LaunchedEffect(ref.id) { if (full.isEmpty()) repo.ensureMedia(ref, manual = true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(current) { if (!current) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale > 1.01f) { onZoomed(scale > 1.01f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(ref.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { o ->
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero }
                        else {
                            scale = 2.5f
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val maxX = size.width * (scale - 1f) / 2f; val maxY = size.height * (scale - 1f) / 2f
                            val o2 = (c - o) * (scale - 1f)
                            offset = Offset(o2.x.coerceIn(-maxX, maxX), o2.y.coerceIn(-maxY, maxY))
                        }
                    },
                )
            }
            .pointerInput(ref.id) {
                fun clamp(o: Offset): Offset {
                    val maxX = size.width * (scale - 1f) / 2f; val maxY = size.height * (scale - 1f) / 2f
                    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var total = Offset.Zero
                    var mode = 0 // 0 deciding, 1 closing, 2 zoom/pan, 3 left to the pager
                    do {
                        val e = awaitPointerEvent()
                        val pressed = e.changes.count { it.pressed }
                        if (pressed >= 2 && mode != 1) {
                            mode = 2
                            scale = (scale * e.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale > 1f) clamp(offset + e.calculatePan()) else Offset.Zero
                            e.changes.forEach { it.consume() }
                        } else if (scale > 1.01f && mode != 3 && mode != 1) {
                            mode = 2
                            offset = clamp(offset + e.calculatePan())
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (mode == 0 || mode == 1) {
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            val d = ch.positionChange()
                            if (mode == 0) {
                                total += d
                                if (abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.2f) mode = 1
                                else if (abs(total.x) > slop) mode = 3
                            }
                            if (mode == 1) { onDrag(d.y); ch.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                    if (mode == 1) onDragEnd()
                }
            }
            .graphicsLayer {
                translationY = dismissY
                val s = 1f - min(0.14f, abs(dismissY) / 2600f)
                scaleX = s; scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        val show = full.ifEmpty { thumb }
        // The picture's real shape (from Google, or from the file once it loads), so it fits between the bars.
        var loaded by remember(show) { mutableFloatStateOf(0f) }
        val ratio = if (loaded > 0f) loaded else if (ref.w > 0 && ref.h > 0) ref.w.toFloat() / ref.h else 0f
        val shape = RoundedCornerShape(if (scale > 1.01f) 0.dp else 20.dp)
        val imgMod = (if (ratio > 0f) Modifier.padding(vertical = 6.dp).aspectRatio(ratio) else Modifier.fillMaxSize())
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            .then(if (ratio > 0f && scale <= 1.01f) Modifier.shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f)) else Modifier)
            .clip(shape)
        if (show.isNotEmpty()) AsyncImage(
            File(show), "Photo", contentScale = if (ratio > 0f) ContentScale.Crop else ContentScale.Fit, modifier = imgMod,
            onSuccess = { st -> val dw = st.result.drawable; if (dw.intrinsicWidth > 0 && dw.intrinsicHeight > 0) loaded = dw.intrinsicWidth.toFloat() / dw.intrinsicHeight },
        )
        else Box(Modifier.size(220.dp).clip(RoundedCornerShape(18.dp)).background(t.tone))
        if (full.isEmpty() && repo.mediaBusy[ref.id] == true) CircularProgressIndicator(Modifier.size(28.dp), color = t.p, strokeWidth = 2.dp)
        else if (full.isEmpty() && show.isEmpty()) T("Asking your phone for this photo…", size = 13.sp, color = t.soft)
    }
}

/** A video between the bars: plays right here with play/pause, a scrubber, time and sound. Drag down to close. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(ref: MediaRef, current: Boolean, dismissY: Float, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val ctx = LocalContext.current
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty().ifEmpty { ref.previewPath }
    LaunchedEffect(ref.id) { if (full.isEmpty()) repo.ensureMedia(ref, manual = true) }
    var ratio by remember { mutableFloatStateOf(if (ref.w > 0 && ref.h > 0) ref.w.toFloat() / ref.h else 16f / 9f) }
    val shape = RoundedCornerShape(20.dp)
    val player = remember(full, current) {
        if (full.isEmpty() || !current) null
        else androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(File(full))))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember(player) { mutableStateOf(false) }
    var pos by remember(player) { mutableStateOf(0L) }
    var dur by remember(player) { mutableStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) { player?.pause(); player?.seekTo(0) }
            }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l); player?.release() }
    }
    LaunchedEffect(player) {
        while (player != null) {
            if (scrubbing == null) pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(200)
        }
    }
    LaunchedEffect(muted, player) { player?.volume = if (muted) 0f else 1f }
    Column(
        Modifier.fillMaxSize()
            .pointerInput(ref.id) {
                androidx.compose.foundation.gestures.detectVerticalDragGestures(onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() }) { ch, dy -> onDrag(dy); ch.consume() }
            }
            .graphicsLayer {
                translationY = dismissY
                val s = 1f - min(0.14f, abs(dismissY) / 2600f)
                scaleX = s; scaleY = s
            },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.weight(1f, fill = false).padding(vertical = 6.dp).aspectRatio(ratio)
                .shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape).background(Color.Black)
                .pointerInput(player) { detectTapGestures(onTap = { if (player == null) onTap() else if (player.isPlaying) player.pause() else player.play() }) },
            contentAlignment = Alignment.Center,
        ) {
            if (player != null) androidx.compose.ui.viewinterop.AndroidView(
                factory = { c -> android.view.TextureView(c).also { tv -> player.setVideoTextureView(tv) } },
                modifier = Modifier.fillMaxSize(),
            )
            else {
                val still = full.ifEmpty { thumb }
                if (still.isNotEmpty()) AsyncImage(File(still), "Video", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (player == null || !playing) Box(Modifier.size(60.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                if (full.isEmpty()) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
                else Ico(Icons.play, Color.White, 26.dp, 1f, fill = Color.White)
            }
        }
        // Controls under the video, on the backdrop, so they never cover the picture.
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                .background(if (t.dark) Color(0xFF1C1F27) else Color.White).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(if (playing) Icons.pause else Icons.play, if (playing) "Pause" else "Play", {
                val p = player ?: return@CircleButton
                if (p.isPlaying) p.pause() else p.play()
            }, size = 40.dp, iconSize = 18.dp)
            val shown = scrubbing?.let { (it * dur).toLong() } ?: pos
            T(clock(shown) + " / " + clock(dur), size = 12.5.sp, weight = FontWeight.Medium, color = t.soft, modifier = Modifier.padding(horizontal = 6.dp))
            androidx.compose.material3.Slider(
                value = scrubbing ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = { scrubbing?.let { f -> player?.seekTo((f * dur).toLong()); pos = (f * dur).toLong() }; scrubbing = null },
                enabled = player != null && dur > 0,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = t.p, activeTrackColor = t.p, inactiveTrackColor = t.p.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).height(36.dp),
            )
            CircleButton(Icons.volume, if (muted) "Sound on" else "Mute", { muted = !muted }, size = 40.dp, iconSize = 18.dp, tint = if (muted) t.soft else t.ink)
        }
    }
}

private fun clock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
