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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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

/** The viewer's own colors: it's always dark, with the photo's colors glowing behind it. */
private val VBg = Color(0xFF0B0D0C)
private val VInk = Color(0xFFF2F4F2)
private val VSoft = Color(0x9EF2F4F2)
private val VGlass = Color(0x1FFFFFFF)

/**
 * Photos and videos open over the conversation on a dark stage, with the current photo blurred into
 * the background. You can swipe through the set you tapped (a strip of photos sent together, or just
 * the one), and the neighbors turn away in 3D on either side. Pinch or double-tap to zoom, drag down
 * to close. Thumbnails, Reply, reactions, Save and Share sit along the bottom.
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
    showInChat: ((Msg) -> Unit)? = null,
) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    var last by remember { mutableStateOf(open) }
    var lastItems by remember { mutableStateOf(items) }
    if (open != null) { last = open; if (items.isNotEmpty()) lastItems = items }
    BackHandler(enabled = open != null) { onClose() }
    AnimatedVisibility(open != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        val key = last ?: return@AnimatedVisibility
        val list = lastItems
        if (list.isEmpty()) return@AnimatedVisibility
        LightSystemBars()
        val n = list.size
        val single = n == 1
        val startIdx = remember(key) { list.indexOfFirst { it.first.key == key.first && it.second.id == key.second }.coerceAtLeast(0) }
        val pager = rememberPagerState(initialPage = startIdx) { n }
        val scope = rememberCoroutineScope()
        val dismiss = remember { Animatable(0f) }
        var chrome by remember { mutableStateOf(true) }
        var zoomed by remember { mutableStateOf(false) }
        var reacting by remember { mutableStateOf(false) }
        val closeAt = with(LocalDensity.current) { 110.dp.toPx() }
        val fade = (1f - abs(dismiss.value) / (closeAt * 3f)).coerceIn(0f, 1f)
        val cur = list.getOrNull(pager.currentPage) ?: list.first()
        val (m, ref) = cur
        val showChrome = chrome && abs(dismiss.value) < closeAt * 0.4f
        val path = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
        // The set rises in with a little spring when it opens.
        val rise = remember { Animatable(0f) }
        LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
        // Each time a photo lands in the middle it gives a small squish.
        val squish = remember { Animatable(1f) }
        var settledOnce by remember { mutableStateOf(false) }
        LaunchedEffect(pager.settledPage) {
            if (!settledOnce) { settledOnce = true; return@LaunchedEffect }
            reacting = false
            squish.snapTo(0f); squish.animateTo(1f, tween(520, easing = androidx.compose.animation.core.LinearEasing))
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(VBg))
            val glow = ref.let { r -> r.localPath.ifEmpty { repo.media[r.id].orEmpty() }.ifEmpty { repo.media[r.thumbId].orEmpty().ifEmpty { r.previewPath } } }
            androidx.compose.animation.Crossfade(glow, animationSpec = tween(420), label = "glow") { g ->
                if (g.isNotEmpty()) AsyncImage(
                    File(g), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.42f * fade; scaleX = 1.3f; scaleY = 1.3f }
                        .blur(60.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                )
            }
            Column(Modifier.fillMaxSize()) {
                // Top: close, who sent it and when, where you are in the set, and more.
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp)) {
                    androidx.compose.animation.AnimatedVisibility(showChrome, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                        var more by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxSize().padding(start = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircleButton(Icons.close, "Close", onClose, tint = VInk)
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                T(if (m.fromMe) "You" else m.sender.ifBlank { conv?.title ?: "" }, size = 15.5.sp, weight = FontWeight.SemiBold, color = VInk, maxLines = 1)
                                T(viewerDate(m.ts), size = 12.5.sp, color = VSoft, maxLines = 1)
                            }
                            if (!single) T(
                                "${pager.currentPage + 1} of $n", size = 12.sp, weight = FontWeight.Medium, color = VInk,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(VGlass).padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                            Box {
                                CircleButton(Icons.more, "More", { more = true }, tint = VInk, iconSize = 22.dp, stroke = 3.2f)
                                DropdownMenu(more, { more = false }, containerColor = Color(0xFF1B1F1D), shape = RoundedCornerShape(18.dp)) {
                                    DropdownMenuItem({ T("Forward", size = 15.sp, color = VInk) }, { more = false; onForward(m) }, leadingIcon = { Ico(Icons.forward, VInk, 18.dp) })
                                    DropdownMenuItem({ T("Open in another app", size = 15.sp, color = VInk) }, {
                                        more = false
                                        if (path.isNotEmpty()) openFile(ctx, path, ref.mime) else repo.toast("Still downloading")
                                    }, leadingIcon = { Ico(Icons.share, VInk, 18.dp) })
                                    if (showInChat != null) DropdownMenuItem({ T("Show in chat", size = 15.sp, color = VInk) }, { more = false; showInChat(m) }, leadingIcon = { Ico(Icons.message, VInk, 18.dp) })
                                    if (m.id.isNotEmpty()) DropdownMenuItem({ T("Delete", size = 15.sp, color = Color(0xFFFF8A80)) }, { more = false; onDelete(m) }, leadingIcon = { Ico(Icons.trash, Color(0xFFFF8A80), 18.dp) })
                                }
                            }
                        }
                    }
                }
                // The set. Neighbors sit to each side, smaller, dimmer and turned away.
                BoxWithConstraintsScopeless { wPx ->
                    val inset = if (single) 18.dp else with(LocalDensity.current) { (wPx * 0.15f).toDp() }
                    val spacing = 10.dp
                    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
                    HorizontalPager(
                        pager, Modifier.fillMaxSize().graphicsLayer {
                            val k = rise.value
                            alpha = k.coerceIn(0f, 1f)
                            translationY = (1f - k) * 30.dp.toPx()
                            val sc = 0.9f + 0.1f * k; scaleX = sc; scaleY = sc
                        },
                        contentPadding = PaddingValues(horizontal = inset), pageSpacing = spacing,
                        userScrollEnabled = !zoomed && abs(dismiss.value) < 1f && !single,
                        beyondViewportPageCount = 1,
                        key = { i -> list[i].first.key + "|" + list[i].second.id },
                    ) { page ->
                        val pr = list[page].second
                        val isCur = page == pager.currentPage
                        val drag: (Float) -> Unit = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } }
                        val dragEnd: () -> Unit = {
                            if (abs(dismiss.value) > closeAt) onClose()
                            else scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                        }
                        Box(
                            Modifier.fillMaxSize().zIndex(if (isCur) 2f else 1f).graphicsLayer {
                                val pageW = size.width
                                val p = (page - pager.currentPage) - pager.currentPageOffsetFraction
                                val a = min(abs(p), 1f)
                                val sgn = if (p < 0f) -1f else 1f
                                if (!single) {
                                    val step = wPx * 0.56f
                                    translationX = sgn * a * step - p * (pageW + spacingPx)
                                    rotationY = -sgn * a * 42f
                                    cameraDistance = 14f * density
                                    val sc = 1f - a * 0.18f
                                    scaleX = sc; scaleY = sc
                                    alpha = if (abs(p) > 1.9f) 0f else 1f
                                }
                                if (isCur && squish.value < 1f) {
                                    val (sx, sy) = squishAt(squish.value)
                                    scaleX *= sx; scaleY *= sy
                                }
                            },
                        ) {
                            val dim = run {
                                val p = (page - pager.currentPage) - pager.currentPageOffsetFraction
                                if (single) 0f else min(abs(p), 1f) * 0.4f
                            }
                            if (pr.isVideo) VideoPage(pr, current = isCur, dismissY = if (isCur) dismiss.value else 0f, onTap = { chrome = !chrome }, onDrag = drag, onDragEnd = dragEnd, dim = dim)
                            else ZoomPage(
                                pr, current = isCur,
                                dismissY = if (isCur) dismiss.value else 0f,
                                onZoomed = { z -> if (isCur) zoomed = z },
                                onTap = { chrome = !chrome },
                                onDrag = drag, onDragEnd = dragEnd, dim = dim,
                            )
                        }
                    }
                }
                // Bottom: thumbnails of the set, then Reply, react, Save and Share. Their space is kept.
                Box(Modifier.fillMaxWidth().navigationBarsPadding().height(if (single) 84.dp else 142.dp), contentAlignment = Alignment.BottomCenter) {
                    androidx.compose.animation.AnimatedVisibility(showChrome, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!single) Thumbs(list, pager.currentPage) { i -> scope.launch { pager.animateScrollToPage(i) } }
                            Box(Modifier.height(76.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val mine = m.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji
                                androidx.compose.animation.AnimatedContent(reacting, label = "acts",
                                    transitionSpec = { (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.9f)) togetherWith fadeOut(tween(120)) }) { r ->
                                    if (r) Row(
                                        Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(VGlass).padding(horizontal = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        repo.prefs.state.quickReacts.forEach { e ->
                                            Box(
                                                Modifier.size(40.dp).clip(CircleShape).background(if (e == mine) Color.White.copy(alpha = 0.22f) else Color.Transparent).press(0.85f) { onReact(m, e); reacting = false },
                                                contentAlignment = Alignment.Center,
                                            ) { T(e, size = 21.sp) }
                                        }
                                        Box(Modifier.size(40.dp).clip(CircleShape).press(0.85f) { reacting = false }, contentAlignment = Alignment.Center) { Ico(Icons.close, VInk, 18.dp) }
                                    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (m.id.isNotEmpty()) Row(
                                            Modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(VGlass).press(0.92f) { onReply(m) }.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Ico(Icons.reply, VInk, 18.dp); Spacer(Modifier.width(7.dp))
                                            T("Reply", size = 14.5.sp, weight = FontWeight.Medium, color = VInk)
                                        }
                                        if (m.id.isNotEmpty()) GlassButton("React", { reacting = true }) {
                                            if (mine != null) T(mine, size = 19.sp) else Ico(Icons.heart, VInk, 19.dp)
                                        }
                                        GlassButton("Save", { if (path.isNotEmpty()) saveToGallery(ctx, path, ref.mime) else repo.toast("Still downloading") }) { Ico(Icons.download, VInk, 19.dp) }
                                        GlassButton("Share", {
                                            if (path.isEmpty()) { repo.toast("Still downloading"); return@GlassButton }
                                            runCatching {
                                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
                                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(ref.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                                            }
                                        }) { Ico(Icons.share, VInk, 19.dp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Measures the width once, without the extra layout pass of BoxWithConstraints for every recomposition. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BoxWithConstraintsScopeless(content: @Composable (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(0f) }
    Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { w = it.width.toFloat() }) {
        if (w > 0f) content(w)
    }
}

/** A small jelly settle: wider and shorter, then a touch taller, then still. */
private fun squishAt(q: Float): Pair<Float, Float> {
    fun l(a: Float, b: Float, f: Float) = a + (b - a) * f
    return when {
        q < 0.3f -> { val f = q / 0.3f; l(1f, 1.035f, f) to l(1f, 0.975f, f) }
        q < 0.62f -> { val f = (q - 0.3f) / 0.32f; l(1.035f, 0.99f, f) to l(0.975f, 1.012f, f) }
        else -> { val f = (q - 0.62f) / 0.38f; l(0.99f, 1f, f) to l(1.012f, 1f, f) }
    }
}

@Composable
private fun GlassButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(VGlass).press(0.88f, onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The set as small thumbnails; the current one is bigger with a white ring. Tap one to jump to it. */
@Composable
private fun Thumbs(list: List<Pair<Msg, MediaRef>>, current: Int, onPick: (Int) -> Unit) {
    val repo = LocalRepo.current
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { state.animateScrollToItem((current - 3).coerceAtLeast(0)) }
    androidx.compose.foundation.lazy.LazyRow(
        state = state, modifier = Modifier.height(66.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(list.size) { i ->
            val r = list[i].second
            val on = i == current
            val sz by androidx.compose.animation.core.animateDpAsState(if (on) 48.dp else 38.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "thumb")
            val pth = repo.media[r.thumbId].orEmpty().ifEmpty { r.previewPath }.ifEmpty { r.localPath.ifEmpty { repo.media[r.id].orEmpty() } }
            Box(
                Modifier.size(sz).then(if (on) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)).padding(3.dp) else Modifier)
                    .clip(RoundedCornerShape(if (on) 9.dp else 10.dp)).background(VGlass)
                    .graphicsLayer { alpha = if (on) 1f else 0.55f }
                    .clickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (pth.isNotEmpty()) AsyncImage(File(pth), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (r.isVideo) Ico(Icons.play, Color.White, 12.dp, 1f, fill = Color.White)
            }
        }
    }
}

/** Light status and navigation bar icons while the dark viewer is open. */
@Composable
private fun LightSystemBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        var c: android.content.Context = view.context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val win = (c as? android.app.Activity)?.window
        val ctl = win?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val s = ctl?.isAppearanceLightStatusBars
        val nb = ctl?.isAppearanceLightNavigationBars
        ctl?.isAppearanceLightStatusBars = false
        ctl?.isAppearanceLightNavigationBars = false
        onDispose { if (s != null) ctl?.isAppearanceLightStatusBars = s; if (nb != null) ctl?.isAppearanceLightNavigationBars = nb }
    }
}

private fun viewerDate(ts: Long): String =
    if (ts <= 0) "" else java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ts))

/** One photo: pinch or double-tap to zoom, drag while zoomed to look around, drag down to close. */
@Composable
private fun ZoomPage(ref: MediaRef, current: Boolean, dismissY: Float, onZoomed: (Boolean) -> Unit, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, dim: Float = 0f) {
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
        val shape = RoundedCornerShape(if (scale > 1.01f) 0.dp else 22.dp)
        val imgMod = (if (ratio > 0f) Modifier.padding(vertical = 6.dp).aspectRatio(ratio) else Modifier.fillMaxSize())
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            .then(if (ratio > 0f && scale <= 1.01f) Modifier.shadow(26.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f)) else Modifier)
            .clip(shape)
            .drawWithContent { drawContent(); if (dim > 0.005f) drawRect(Color.Black.copy(alpha = dim)) }
        if (show.isNotEmpty()) AsyncImage(
            File(show), "Photo", contentScale = if (ratio > 0f) ContentScale.Crop else ContentScale.Fit, modifier = imgMod,
            onSuccess = { st -> val dw = st.result.drawable; if (dw.intrinsicWidth > 0 && dw.intrinsicHeight > 0) loaded = dw.intrinsicWidth.toFloat() / dw.intrinsicHeight },
        )
        else Box(Modifier.size(220.dp).clip(RoundedCornerShape(18.dp)).background(VGlass))
        if (full.isEmpty() && repo.mediaBusy[ref.id] == true) CircularProgressIndicator(Modifier.size(28.dp), color = VInk, strokeWidth = 2.dp)
        else if (full.isEmpty() && show.isEmpty()) T("Asking your phone for this photo…", size = 13.sp, color = VSoft)
    }
}

/** A video between the bars: plays right here with play/pause, a scrubber, time and sound. Drag down to close. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(ref: MediaRef, current: Boolean, dismissY: Float, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, dim: Float = 0f) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val ctx = LocalContext.current
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty().ifEmpty { ref.previewPath }
    LaunchedEffect(ref.id) { if (full.isEmpty()) repo.ensureMedia(ref, manual = true) }
    var ratio by remember { mutableFloatStateOf(if (ref.w > 0 && ref.h > 0) ref.w.toFloat() / ref.h else 16f / 9f) }
    val shape = RoundedCornerShape(22.dp)
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
                detectVerticalDragGestures(onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() }) { ch, dy -> onDrag(dy); ch.consume() }
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
                .shadow(26.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
                .clip(shape).background(Color.Black)
                .drawWithContent { drawContent(); if (dim > 0.005f) drawRect(Color.Black.copy(alpha = dim)) }
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
        // Controls under the video, on the dark stage, so they never cover the picture. Only on the one in the middle.
        if (current) Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                .background(VGlass).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(if (playing) Icons.pause else Icons.play, if (playing) "Pause" else "Play", {
                val p = player ?: return@CircleButton
                if (p.isPlaying) p.pause() else p.play()
            }, size = 40.dp, iconSize = 18.dp, tint = VInk)
            val shown = scrubbing?.let { (it * dur).toLong() } ?: pos
            T(playClock(shown) + " / " + playClock(dur), size = 12.5.sp, weight = FontWeight.Medium, color = VSoft, modifier = Modifier.padding(horizontal = 6.dp))
            androidx.compose.material3.Slider(
                value = scrubbing ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = { scrubbing?.let { f -> player?.seekTo((f * dur).toLong()); pos = (f * dur).toLong() }; scrubbing = null },
                enabled = player != null && dur > 0,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = VInk, activeTrackColor = VInk, inactiveTrackColor = VInk.copy(alpha = 0.25f)),
                modifier = Modifier.weight(1f).height(36.dp),
            )
            CircleButton(Icons.volume, if (muted) "Sound on" else "Mute", { muted = !muted }, size = 40.dp, iconSize = 18.dp, tint = if (muted) VSoft else VInk)
        }
    }
}

private fun playClock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
