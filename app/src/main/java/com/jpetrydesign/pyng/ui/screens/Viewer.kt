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
        Box(Modifier.fillMaxSize()) {
            // See-through, so the blurred chat shows behind the photo.
            Box(Modifier.fillMaxSize().background((if (t.dark) Color(0xFF07080C) else Color(0xFFF2F4F8)).copy(alpha = 0.74f * fade)))
            HorizontalPager(
                pager, Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp), pageSpacing = 12.dp,
                userScrollEnabled = !zoomed && abs(dismiss.value) < 1f,
                key = { i -> items[i].first.key + "|" + items[i].second.id },
            ) { page ->
                val (pm, pr) = items[page]
                ZoomPage(
                    pr, current = page == pager.currentPage,
                    dismissY = if (page == pager.currentPage) dismiss.value else 0f,
                    onZoomed = { z -> if (page == pager.currentPage) zoomed = z },
                    onTap = { chrome = !chrome },
                    onDrag = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } },
                    onDragEnd = {
                        if (abs(dismiss.value) > closeAt) onClose()
                        else scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                    },
                )
                @Suppress("UNUSED_VARIABLE") val unused = pm
            }
            val showChrome = chrome && abs(dismiss.value) < closeAt * 0.4f
            // Top: who sent it and when, plus save, share, delete and more.
            AnimatedVisibility(showChrome, Modifier.align(Alignment.TopCenter), enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                var more by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.back, "Close", onClose)
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        T(if (m.fromMe) "You" else m.sender.ifBlank { conv?.title ?: "" }, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                        T(viewerDate(m.ts), size = 12.5.sp, color = t.soft, maxLines = 1)
                    }
                    val path = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
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
            // Bottom: quick reactions and Reply.
            AnimatedVisibility(showChrome && m.id.isNotEmpty(), Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                Row(
                    Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val mine = m.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji
                    Row(
                        Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).frost(RoundedCornerShape(25.dp), t.glassStrong, 20.dp).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repo.prefs.state.quickReacts.forEach { e ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(if (e == mine) t.tone else Color.Transparent).press(0.85f) { onReact(m, e) },
                                contentAlignment = Alignment.Center,
                            ) { T(e, size = 21.sp) }
                        }
                    }
                    Row(
                        Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).frost(RoundedCornerShape(25.dp), t.glassStrong, 20.dp).press(0.92f) { onReply(m) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(Icons.reply, t.ink, 18.dp)
                        Spacer(Modifier.width(7.dp))
                        T("Reply", size = 14.5.sp, weight = FontWeight.SemiBold)
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
    val thumb = repo.media[ref.thumbId].orEmpty()
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
        val ratio = if (ref.w > 0 && ref.h > 0) ref.w.toFloat() / ref.h else 0f
        val imgMod = (if (ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            .clip(RoundedCornerShape(if (scale > 1.01f) 0.dp else 18.dp))
        if (show.isNotEmpty()) AsyncImage(File(show), "Photo", contentScale = if (ratio > 0f) ContentScale.Crop else ContentScale.Fit, modifier = imgMod)
        else Box(Modifier.size(220.dp).clip(RoundedCornerShape(18.dp)).background(t.tone))
        if (full.isEmpty()) CircularProgressIndicator(Modifier.size(28.dp), color = t.p, strokeWidth = 2.dp)
    }
}

@Suppress("unused")
private fun Modifier.noRipple(onClick: () -> Unit) = this.clickable(onClick = onClick)
