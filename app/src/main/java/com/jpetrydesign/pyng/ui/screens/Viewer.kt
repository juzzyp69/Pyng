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
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.widthIn
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
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.kit.frost
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
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
    // Opened from a photo stack: its photos fly out of the stack into the viewer, and back again on close.
    val mo0 = com.jpetrydesign.pyng.ui.kit.LocalMo.current
    val flying = open != null && PhotoFlight.spot != null && mo0.liquid
    var closer by remember { mutableStateOf<(() -> Unit)?>(null) }
    BackHandler(enabled = open != null) { closer?.invoke() ?: onClose() }
    AnimatedVisibility(open != null, enter = if (flying) fadeIn(tween(1)) else fadeIn(tween(220)), exit = fadeOut(tween(180))) {
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
        val showChrome0 = chrome && abs(dismiss.value) < closeAt * 0.4f
        val path = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
        val mo = com.jpetrydesign.pyng.ui.kit.LocalMo.current
        // The stack this set flew out of, if it did (only while Liquid motion is on).
        val spot = remember(key) { PhotoFlight.spot?.takeIf { mo.liquid }.also { PhotoFlight.spot = null } }
        // 0: the photos sit in the stack. 1: they're laid out in the viewer.
        val fly = remember(key) { Animatable(if (spot != null) 0f else 1f) }
        var phase by remember(key) { mutableStateOf(if (spot != null) "out" else "view") }
        LaunchedEffect(key) {
            if (spot == null) return@LaunchedEffect
            PhotoFlight.out = spot.group
            fly.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 280f))
            phase = "view"
        }
        fun close() {
            val curKey = photoKey(m, ref)
            if (spot != null && phase == "view" && curKey in spot.order) {
                phase = "back"
                scope.launch {
                    fly.animateTo(0f, spring(dampingRatio = 0.92f, stiffness = 380f))
                    PhotoFlight.landTop = spot.group to curKey
                    PhotoFlight.out = null
                    onClose()
                }
            } else if (phase != "back") { PhotoFlight.out = null; onClose() }
        }
        SideEffect { closer = { close() } }
        // The set rises in with a little spring when it opens (unless it flew out of a stack).
        val rise = remember { Animatable(if (spot != null) 1f else 0f) }
        LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
        val bgK = if (spot != null) fly.value.coerceIn(0f, 1f) else 1f
        val showChrome = showChrome0 && phase == "view"
        // Each time a photo lands in the middle it gives a small squish.
        val squish = remember { Animatable(1f) }
        var settledOnce by remember { mutableStateOf(false) }
        LaunchedEffect(pager.settledPage) {
            if (!settledOnce) { settledOnce = true; return@LaunchedEffect }
            reacting = false
            squish.snapTo(0f); squish.animateTo(1f, tween(520, easing = androidx.compose.animation.core.LinearEasing))
        }
        // Video controls for whichever video is in the middle, so the floating player can drive it.
        val vc = remember { VideoCtl() }
        // While a video plays, the controls step aside after a few seconds; any tap brings them back.
        LaunchedEffect(chrome, vc.playing) { if (chrome && vc.playing) { kotlinx.coroutines.delay(3000); chrome = false } }
        var origin by remember { mutableStateOf(Offset.Zero) }
        var viewSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
        val vHaze = remember { dev.chrisbanes.haze.HazeState() }
        Box(Modifier.fillMaxSize().onGloballyPositioned { c -> origin = c.boundsInRoot().topLeft; viewSize = c.size.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) } }) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade * bgK }.background(VBg))
            // The same picture, heavily frosted, fills whatever the photo or video doesn't, instead of black bars.
            // (For a video, its still frame.)
            val glow = ref.let { r ->
                val still = repo.media[r.thumbId].orEmpty().ifEmpty { r.previewPath }
                if (r.isVideo) still else r.localPath.ifEmpty { repo.media[r.id].orEmpty() }.ifEmpty { still }
            }
                        androidx.compose.animation.Crossfade(glow, animationSpec = tween(420), label = "glow") { g ->
                // A small copy of its own (so a sharp cached one is never reused), blurred for real.
                if (g.isNotEmpty()) AsyncImage(
                    coil.request.ImageRequest.Builder(ctx).data(File(g)).size(96).memoryCacheKey("glow:$g").build(), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f * fade * bgK; scaleX = 1.3f; scaleY = 1.3f }
                        .blur(44.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
                )
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade * bgK }.background(Color.Black.copy(alpha = 0.32f)))
            // The set, full screen. Neighbors sit to each side, smaller, dimmer and turned away.
            Box(Modifier.fillMaxSize().hazeSource(vHaze)) {
                var wPx by remember { mutableFloatStateOf(0f) }
                Box(Modifier.fillMaxSize().onSizeChanged { wPx = it.width.toFloat() })
                if (wPx > 0f) {
                    val inset = if (single) 0.dp else with(LocalDensity.current) { (wPx * 0.07f).toDp() }
                    val spacing = 10.dp
                    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
                    HorizontalPager(
                        pager, Modifier.fillMaxSize().graphicsLayer {
                            val k = rise.value
                            // While the photos are in flight, the flying copies stand in for the pager.
                            alpha = if (phase == "view") k.coerceIn(0f, 1f) else 0f
                            translationY = (1f - k) * 30.dp.toPx()
                            val sc = 0.9f + 0.1f * k; scaleX = sc; scaleY = sc
                        },
                        contentPadding = PaddingValues(horizontal = inset), pageSpacing = spacing,
                        userScrollEnabled = !zoomed && abs(dismiss.value) < 1f && !single,
                        beyondViewportPageCount = 1,
                        key = { i -> list[i].first.key + "|" + list[i].second.id + "|" + i },
                    ) { page ->
                        val pr = list[page].second
                        val isCur = page == pager.currentPage
                        val drag: (Float) -> Unit = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } }
                        val dragEnd: () -> Unit = {
                            if (abs(dismiss.value) > closeAt) close()
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
                            if (pr.isVideo) VideoPage(pr, current = isCur, ctl = vc, rounded = !single, dismissY = if (isCur) dismiss.value else 0f, onTap = { chrome = !chrome }, onDrag = drag, onDragEnd = dragEnd, dim = dim)
                            else ZoomPage(
                                pr, current = isCur, rounded = !single,
                                dismissY = if (isCur) dismiss.value else 0f,
                                onZoomed = { z -> if (isCur) zoomed = z },
                                onTap = { chrome = !chrome },
                                onDrag = drag, onDragEnd = dragEnd, dim = dim,
                            )
                        }
                    }
                }
            }
            // The photos in flight, between the stack in the chat and their places in the viewer.
            if (spot != null && phase != "view" && viewSize.width > 0f) {
                val dens = LocalDensity.current.density
                val W = viewSize.width; val H = viewSize.height
                val inset = if (single) 0f else W * 0.07f
                val pageW = W - 2 * inset
                val cur0 = pager.currentPage
                val curKey = photoKey(list[cur0].first, list[cur0].second)
                val order = spot.order
                val nn = order.size
                val curPos = order.indexOf(curKey).coerceAtLeast(0)
                // Drawn back to front: the photo you're on last, so it's on top.
                val drawOrder = order.indices.sortedByDescending { if (order[it] == curKey) -1 else (it - curPos + nn) % nn }
                for (pos in drawOrder) {
                    val k0 = order[pos]
                    val li = list.indexOfFirst { photoKey(it.first, it.second) == k0 }
                    if (li < 0) continue
                    val pr = list[li].second
                    // In the stack: on the way out, where it sat; on the way back, where it lands (the photo you're on, on top).
                    val depth = if (phase == "back") (pos - curPos + nn) % nn else pos
                    if (depth > 3) continue
                    val sc0 = 1f - depth * StackSpot.SHRINK
                    val b = spot.base
                    val sw = b.width * sc0; val sh = b.height * sc0
                    val ox = b.left + b.width * 0.5f; val oy = b.top + b.height * 0.9f
                    val sL = ox - sw * 0.5f + spot.sign * depth * spot.fan - origin.x
                    val sT = oy - sh * 0.9f + depth * spot.drop - origin.y
                    val sRot = spot.sign * depth * StackSpot.TILT
                    // In the viewer: the photo fitted to its page; neighbours to the side, smaller.
                    val off = li - cur0
                    val aspect = if (pr.w > 0 && pr.h > 0) pr.w.toFloat() / pr.h else b.width / b.height
                    var ew = pageW; var eh = pageW / aspect
                    if (eh > H) { eh = H; ew = H * aspect }
                    val esc = if (off == 0) 1f else 0.82f
                    ew *= esc; eh *= esc
                    val ecx = W / 2f + (if (off == 0) 0f else kotlin.math.sign(off.toFloat()) * W * 0.56f)
                    val eL = ecx - ew / 2f; val eT = H / 2f - eh / 2f
                    val gone = abs(off) > 1
                    val delay = (if (phase == "back") 0.05f else 0.07f) * depth
                    Box(
                        Modifier
                            .offset {
                                val k = ((fly.value - delay) / (1f - delay)).let { if (fly.value >= 1f) fly.value else it.coerceAtLeast(0f) }
                                val dy = if (off == 0) dismiss.value * k else 0f
                                IntOffset((sL + (eL - sL) * k).roundToInt(), (sT + (eT - sT) * k + dy).roundToInt())
                            }
                            .layout { m0, _ ->
                                val k = ((fly.value - delay) / (1f - delay)).let { if (fly.value >= 1f) fly.value else it.coerceAtLeast(0f) }
                                val wv = (sw + (ew - sw) * k).roundToInt().coerceAtLeast(1)
                                val hv = (sh + (eh - sh) * k).roundToInt().coerceAtLeast(1)
                                val pl = m0.measure(androidx.compose.ui.unit.Constraints.fixed(wv, hv))
                                layout(wv, hv) { pl.place(0, 0) }
                            }
                            .graphicsLayer {
                                val k = ((fly.value - delay) / (1f - delay)).let { if (fly.value >= 1f) fly.value else it.coerceAtLeast(0f) }
                                rotationZ = sRot * (1f - k.coerceIn(0f, 1f))
                                alpha = if (gone) (1f - k).coerceIn(0f, 1f) else 1f
                                shape = RoundedCornerShape((22f + (if (single) -22f else 2f) * k.coerceIn(0f, 1f)) * dens)
                                clip = true
                            },
                    ) {
                        val pic = pr.localPath.ifEmpty { repo.media[pr.id].orEmpty() }.ifEmpty { repo.media[pr.thumbId].orEmpty().ifEmpty { pr.previewPath } }
                        if (pic.isNotEmpty()) AsyncImage(File(pic), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().background(Color(0xFF2A2D36)))
                    }
                }
            }
            // Controls float over the picture and drain away when you tap; tap again and they bud back.
            val liquid = mo.liquid
            fun appear(from: Float) = if (mo == com.jpetrydesign.pyng.ui.kit.Mo.Minimal) fadeIn(tween(120))
                else fadeIn(tween(160)) + scaleIn(if (liquid) spring(dampingRatio = 0.55f, stiffness = 420f) else tween(220), initialScale = if (liquid) 0.55f else 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, from))
            fun vanish(to: Float) = if (mo == com.jpetrydesign.pyng.ui.kit.Mo.Minimal) fadeOut(tween(90))
                else fadeOut(tween(150)) + scaleOut(tween(200), targetScale = if (liquid) 0.5f else 0.94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, to))
            // Top: one frosted pill with close, who sent it and when.
            androidx.compose.animation.AnimatedVisibility(
                showChrome, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp, start = 14.dp, end = 14.dp),
                enter = appear(0f), exit = vanish(0f),
            ) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                Row(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).vFrost(vHaze, RoundedCornerShape(22.dp)).padding(start = 4.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).press(0.88f) { close() }.semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) { Ico(Icons.close, VInk, 19.dp, 2.2f) }
                    Spacer(Modifier.width(6.dp))
                    val p = repo.prefs.state
                    val t = LocalTok.current
                    val who = when {
                        m.fromMe -> Who(p.profilePhoto.ifEmpty { null }, initialsOf(p.profileName.ifBlank { "Me" }), t.tone, t.p)
                        conv == null -> null
                        conv.group -> conv.people.firstOrNull { it.id == m.participantId }?.let { personWho(it, repo, t) }
                        else -> whoOf(conv, repo, t)
                    }
                    if (who != null) { WhoAvatar(who, 24.dp, 9.sp); Spacer(Modifier.width(8.dp)) }
                    val name = if (m.fromMe) "You" else m.sender.ifBlank { conv?.title ?: "" }
                    T(listOf(name, viewerTime(m.ts)).filter { it.isNotEmpty() }.joinToString(" · "), size = 14.sp, weight = FontWeight.SemiBold, color = VInk, maxLines = 1)
                }
                }
                Spacer(Modifier.width(8.dp))
                // Less-used actions as small circles on the right, like the editor's trash.
                if (showInChat != null) { TopCircle("Go to chat", Icons.message, vHaze) { showInChat(m) }; Spacer(Modifier.width(8.dp)) }
                TopCircle("Open in another app", OPEN_OUT, vHaze) { if (path.isNotEmpty()) openFile(ctx, path, ref.mime) else repo.toast("Still downloading") }
                if (m.id.isNotEmpty()) { Spacer(Modifier.width(8.dp)); TopCircle("Delete", Icons.trash, vHaze) { onDelete(m) } }
              }
            }
            // Bottom: one frosted dock with the set's thumbnails, then what you can do with this one.
            androidx.compose.animation.AnimatedVisibility(
                showChrome, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp, start = 12.dp, end = 12.dp),
                enter = appear(1f), exit = vanish(1f),
            ) {
                val mine = m.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).vFrost(vHaze, RoundedCornerShape(30.dp))
                        .padding(start = 10.dp, end = 10.dp, top = if (single && !ref.isVideo) 8.dp else 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!single) {
                        Thumbs(list, pager.currentPage) { i -> scope.launch { pager.animateScrollToPage(i) } }
                        Box(Modifier.padding(horizontal = 4.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    }
                    if (ref.isVideo) Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The player: play, time, scrubber and sound.
                        CircleButton(if (vc.playing) Icons.pause else Icons.play, if (vc.playing) "Pause" else "Play", { vc.toggle() }, size = 42.dp, iconSize = 18.dp, tint = VInk)
                        val shown = vc.scrub?.let { (it * vc.dur).toLong() } ?: vc.pos
                        T(playClock(shown) + " / " + playClock(vc.dur), size = 12.sp, weight = FontWeight.Medium, color = VSoft, modifier = Modifier.padding(horizontal = 4.dp), maxLines = 1)
                        androidx.compose.material3.Slider(
                            value = vc.scrub ?: if (vc.dur > 0) (vc.pos.toFloat() / vc.dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { vc.scrub = it },
                            onValueChangeFinished = { vc.scrub?.let { f -> vc.seek(f) }; vc.scrub = null },
                            enabled = vc.player != null && vc.dur > 0,
                            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = VInk, activeTrackColor = VInk, inactiveTrackColor = VInk.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f).height(36.dp),
                        )
                        CircleButton(Icons.volume, if (vc.muted) "Sound on" else "Mute", { vc.muted = !vc.muted }, size = 40.dp, iconSize = 18.dp, tint = if (vc.muted) VSoft else VInk)
                    }
                    androidx.compose.animation.AnimatedContent(reacting, label = "acts",
                        transitionSpec = { (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.9f)) togetherWith fadeOut(tween(120)) }) { r ->
                        if (r) Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            repo.prefs.state.quickReacts.forEach { e ->
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(if (e == mine) Color.White.copy(alpha = 0.22f) else Color.Transparent).press(0.85f) { onReact(m, e); reacting = false },
                                    contentAlignment = Alignment.Center,
                                ) { T(e, size = 22.sp) }
                            }
                            Box(Modifier.size(44.dp).clip(CircleShape).press(0.85f) { reacting = false }.semantics { contentDescription = "Close reactions" }, contentAlignment = Alignment.Center) { Ico(Icons.close, VInk, 18.dp) }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                            DockAction("Save", Icons.download) { if (path.isNotEmpty()) saveToGallery(ctx, path, ref.mime) else repo.toast("Still downloading") }
                            DockAction("Share", Icons.share) {
                                if (path.isEmpty()) repo.toast("Still downloading")
                                else runCatching {
                                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
                                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(ref.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                                }
                            }
                            if (m.id.isNotEmpty()) DockAction("Forward", Icons.forward) { onForward(m) }
                            if (m.id.isNotEmpty()) DockAction("Reply", Icons.reply) { onReply(m) }
                            if (m.id.isNotEmpty()) DockAction("React", Icons.heart, emoji = mine) { reacting = true }
                        }
                    }
                }
            }
        }
    }
}

private const val OPEN_OUT = "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"

/** A small frosted circle at the top of the viewer. */
@Composable
private fun TopCircle(label: String, icon: String, hz: dev.chrisbanes.haze.HazeState, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).vFrost(hz, CircleShape).press(0.88f, onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Ico(icon, VInk, 19.dp, 2.1f)
    }
}

/** One action in the viewer's dock: its icon with a short name under it. */
@Composable
private fun DockAction(label: String, icon: String, emoji: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 58.dp).height(54.dp).clip(RoundedCornerShape(18.dp)).press(0.9f, onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        if (emoji != null) T(emoji, size = 19.sp) else Ico(icon, VInk, 22.dp, 2.1f)
        T(label, size = 11.5.sp, weight = FontWeight.Medium, color = VInk.copy(alpha = 0.85f), maxLines = 1)
    }
}

/** The viewer's frosted glass: the photo behind blurred and darkened, with a fine bright edge. */
internal fun Modifier.vFrost(hz: dev.chrisbanes.haze.HazeState, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.hazeEffect(hz, dev.chrisbanes.haze.HazeStyle(backgroundColor = VBg, tint = dev.chrisbanes.haze.HazeTint(Color(0x611E2028)), blurRadius = 28.dp, noiseFactor = 0f))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.08f))), shape)

private fun viewerTime(ts: Long): String {
    if (ts <= 0) return ""
    val now = java.util.Calendar.getInstance(); val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR)
    val sameYear = now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
    val pat = if (sameDay) "h:mm a" else if (sameYear) "MMM d, h:mm a" else "MMM d, yyyy"
    return java.text.SimpleDateFormat(pat, java.util.Locale.getDefault()).format(java.util.Date(ts))
}

/** The middle video's player, shared with the floating controls. */
private class VideoCtl {
    var player by mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null)
    var playing by mutableStateOf(false)
    var pos by mutableStateOf(0L)
    var dur by mutableStateOf(0L)
    var muted by mutableStateOf(false)
    var scrub by mutableStateOf<Float?>(null)
    fun toggle() { val p = player ?: return; if (p.isPlaying) p.pause() else p.play() }
    fun seek(f: Float) { player?.seekTo((f * dur).toLong()); pos = (f * dur).toLong() }
}

private val VPill = Color(0x8C1B1F1D)

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
        state = state, modifier = Modifier.height(56.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(list.size) { i ->
            val r = list[i].second
            val on = i == current
            val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "thumb")
            val pth = repo.media[r.thumbId].orEmpty().ifEmpty { r.previewPath }.ifEmpty { r.localPath.ifEmpty { repo.media[r.id].orEmpty() } }
            val shape = RoundedCornerShape(13.dp)
            Box(
                Modifier.size(46.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (on) 1f else 0.88f }
                    .then(if (on) Modifier.shadow(6.dp, shape).border(2.5.dp, VInk, shape) else Modifier)
                    .clip(shape).background(VGlass)
                    .then(if (on) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), shape))
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
internal fun LightSystemBars() {
    DisposableEffect(Unit) {
        com.jpetrydesign.pyng.ui.BarIcons.light = true
        onDispose { com.jpetrydesign.pyng.ui.BarIcons.light = null }
    }
}

private fun viewerDate(ts: Long): String =
    if (ts <= 0) "" else java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ts))

/** One photo: pinch or double-tap to zoom, drag while zoomed to look around, drag down to close. */
@Composable
private fun ZoomPage(ref: MediaRef, current: Boolean, rounded: Boolean = true, dismissY: Float, onZoomed: (Boolean) -> Unit, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, dim: Float = 0f) {
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
        // One photo fills the screen edge to edge; in a set, each is a card so its neighbors read as cards too.
        val shape = RoundedCornerShape(if (scale > 1.01f || !rounded) 0.dp else 18.dp)
        val imgMod = (if (ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            .then(if (ratio > 0f && scale <= 1.01f && rounded) Modifier.shadow(26.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f)) else Modifier)
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

/**
 * A video at its own shape, as big as the screen allows. Tap to show or hide the controls (they float
 * over it, in the viewer); drag down to close.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(ref: MediaRef, current: Boolean, ctl: VideoCtl, rounded: Boolean, dismissY: Float, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, dim: Float = 0f) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val full = ref.localPath.ifEmpty { repo.media[ref.id].orEmpty() }
    val thumb = repo.media[ref.thumbId].orEmpty().ifEmpty { ref.previewPath }
    LaunchedEffect(ref.id) { if (full.isEmpty()) repo.ensureMedia(ref, manual = true) }
    var ratio by remember { mutableFloatStateOf(if (ref.w > 0 && ref.h > 0) ref.w.toFloat() / ref.h else 16f / 9f) }
    val shape = RoundedCornerShape(if (rounded) 18.dp else 0.dp)
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
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (current) ctl.playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) { player?.pause(); player?.seekTo(0) }
            }
        }
        player?.addListener(l)
        if (current) { ctl.player = player; ctl.pos = 0L; ctl.dur = 0L; ctl.playing = false }
        onDispose {
            player?.removeListener(l); player?.release()
            if (ctl.player === player) { ctl.player = null; ctl.playing = false }
        }
    }
    LaunchedEffect(player) {
        while (player != null) {
            if (ctl.scrub == null && ctl.player === player) ctl.pos = player.currentPosition
            if (ctl.player === player) ctl.dur = player.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(200)
        }
    }
    LaunchedEffect(ctl.muted, player) { player?.volume = if (ctl.muted) 0f else 1f }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(ref.id) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(ref.id) {
                detectVerticalDragGestures(onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() }) { ch, dy -> onDrag(dy); ch.consume() }
            }
            .graphicsLayer {
                translationY = dismissY
                val s = 1f - min(0.14f, abs(dismissY) / 2600f)
                scaleX = s; scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        // The whole video, never cropped: as wide or as tall as the screen allows at its own shape.
        Box(
            Modifier.aspectRatio(ratio)
                .then(if (rounded) Modifier.shadow(26.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f)) else Modifier)
                .clip(shape).background(Color.Black)
                .drawWithContent { drawContent(); if (dim > 0.005f) drawRect(Color.Black.copy(alpha = dim)) },
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
            if (player == null || !playing) Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = player != null) { player?.play() },
                contentAlignment = Alignment.Center,
            ) {
                if (full.isEmpty()) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
                else Ico(Icons.play, Color.White, 26.dp, 1f, fill = Color.White)
            }
        }
    }
}

private fun playClock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
