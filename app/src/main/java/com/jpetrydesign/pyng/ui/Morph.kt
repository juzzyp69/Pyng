package com.jpetrydesign.pyng.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jpetrydesign.pyng.ui.kit.LocalMo
import com.jpetrydesign.pyng.ui.kit.Mo
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Where a screen grows out of: the button or row you tapped, in window pixels, with its corner radius.
 * [avatar] is the photo in that row, which flies up into the chat's header.
 */
class Morph(val from: Rect, val radius: Float, val avatar: Rect? = null, val key: Any? = null) {
    /** The screen it was opened from. Going back to that screen drains it back into what you tapped. */
    var under: Any? = null
}

/** How long the screen underneath stays put while a screen grows over it or shrinks back off it. */
const val MORPH_HOLD = 750

/** Lets the chat header tell the flying photo where to land, and hides its own photo until it does. */
class AvatarSlot {
    var rect by mutableStateOf<Rect?>(null)
    var hidden by mutableStateOf(false)
}

val LocalAvatarSlot = staticCompositionLocalOf<AvatarSlot?> { null }

/** The row whose photo is in the air right now. That row hides its own photo, so it's never seen twice. */
object FlyingAvatar {
    var key by mutableStateOf<Any?>(null)
}

/** Marks the photo the flying one lands on. Does nothing outside a morph. */
fun Modifier.avatarSlot(): Modifier = composed {
    val s = LocalAvatarSlot.current ?: return@composed this
    this.onGloballyPositioned { c -> val r = Rect(c.positionInRoot(), c.size.toSize()); if (s.rect != r) s.rect = r }
        .graphicsLayer { alpha = if (s.hidden) 0f else 1f }
}

/** Same as [avatarSlot], for callers that'd rather not import an extension. */
fun avatarSlotOf(m: Modifier): Modifier = m.avatarSlot()

/** A rounded box in pixels, as an outline. Cheap to clip to every frame, unlike a free-form path. */
private data class BoxShape(val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(androidx.compose.ui.geometry.RoundRect(l, t, maxOf(r, l), maxOf(b, t), androidx.compose.ui.geometry.CornerRadius(radius)))
}

private val Settle = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** How each edge springs for a motion style. The edges move at slightly different speeds, which is what makes it look liquid. */
private fun edgeSpring(mo: Mo, edge: Int, back: Boolean): androidx.compose.animation.core.SpringSpec<Float> {
    if (back) return spring(dampingRatio = 0.92f, stiffness = if (edge == 1) 520f else 440f, visibilityThreshold = 0.001f)
    // 0 left, 1 top, 2 right, 3 bottom. The top leads, the bottom follows a beat later, the sides settle with a soft wobble.
    val damp = when (mo) { Mo.Drop -> 0.66f; Mo.Pour -> 0.8f; else -> 0.76f }
    val stiff = when (edge) { 1 -> 430f; 3 -> 300f; else -> 360f }
    return spring(dampingRatio = damp, stiffness = stiff, visibilityThreshold = 0.001f)
}

/**
 * A screen that pours out of what you tapped (and drains back into it when you go back): its edges spring
 * out from the row to the screen's edges, each at its own pace, so it stretches like liquid. The chat shows
 * through right away and is never hidden behind the motion, so there's never a blank screen.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun AnimatedVisibilityScope.MorphScreen(m: Morph, grow: Boolean, shrink: Boolean, avatar: (@Composable (Dp) -> Unit)?, content: @Composable () -> Unit) {
    val mo = LocalMo.current
    val t = LocalTok.current
    val density = LocalDensity.current
    // Only a screen that's arriving grows; one that was already up (or came back some other way) just shows.
    val grows = remember { grow && transition.currentState == EnterExitState.PreEnter }
    val exiting = shrink && transition.targetState == EnterExitState.PostExit
    var box by remember { mutableStateOf<Rect?>(null) }
    var growing by remember { mutableStateOf(grows) }
    var gone by remember { mutableStateOf(false) }
    // Each edge: 0 at what you tapped, 1 at the screen's edge.
    val edges = remember { List(4) { Animatable(if (grows) 0f else 1f) } }
    // Content fade: 0 hidden, 1 shown.
    val shown = remember { Animatable(if (grows) 0f else 1f) }
    val slot = remember { AvatarSlot().also { it.hidden = grows && avatar != null && m.avatar != null } }
    // The flying photo: 0 at the row, 1 in the header.
    val fly = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(grows && avatar != null && m.avatar != null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!grows) return@LaunchedEffect
        if (flying) scope.launch {
            withTimeoutOrNull(1500) {
                snapshotFlow { slot.rect }.filterNotNull().first()
                fly.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 300f))
                // Hand over to the header's own photo only once the chat has fully faded in.
                snapshotFlow { shown.value >= 0.999f }.first { it }
            }
            flying = false; slot.hidden = false
        }
        // The chat fades in almost at once, while the shape is still pouring out.
        scope.launch {
            kotlinx.coroutines.delay(50)
            shown.animateTo(1f, tween(if (mo == Mo.Drop) 260 else 220, easing = Settle))
        }
        kotlinx.coroutines.coroutineScope {
            edges.forEachIndexed { i, e -> launch { e.animateTo(1f, edgeSpring(mo, i, back = false)) } }
        }
        growing = false
    }
    // Going back: the chat fades as the shape drains back into the row it came from.
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        // Backing out mid-growth: carry on from wherever the edges are.
        growing = true
        if (avatar != null && m.avatar != null && slot.rect != null) {
            fly.snapTo(1f); flying = true; slot.hidden = true
            scope.launch { fly.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 320f)) }
        }
        scope.launch { shown.animateTo(0f, tween(130)) }
        withTimeoutOrNull(900) {
            kotlinx.coroutines.coroutineScope {
                edges.forEachIndexed { i, e -> launch { e.animateTo(0f, edgeSpring(mo, i, back = true)) } }
            }
        }
        gone = true
    }

    // While the photo is in the air (either way), the row it came from keeps its own photo hidden.
    LaunchedEffect(Unit) {
        val k = m.key ?: return@LaunchedEffect
        snapshotFlow { flying && !gone }.collect { up ->
            if (up) FlyingAvatar.key = k else if (FlyingAvatar.key == k) FlyingAvatar.key = null
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (m.key != null && FlyingAvatar.key == m.key) FlyingAvatar.key = null }
    }

    /** The shape right now, in this screen's own pixels. */
    fun shapeNow(w: Float, h: Float): BoxShape {
        val o = box?.topLeft ?: Offset.Zero
        val f = m.from
        fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
        val l = lerp(f.left - o.x, 0f, edges[0].value)
        val tp = lerp(f.top - o.y, 0f, edges[1].value)
        val r = lerp(f.right - o.x, w, edges[2].value)
        val b = lerp(f.bottom - o.y, h, edges[3].value)
        val k = edges.minOf { it.value }.coerceIn(0f, 1f)
        val radius = lerp(m.radius, 30f * density.density, k)
        return BoxShape(l, tp, r, b, radius)
    }

    // Nothing below reads a moving value while composing: each frame only re-draws or re-places, so the
    // screen inside (a whole chat) is never rebuilt mid-motion.
    val landSize by remember { derivedStateOf { slot.rect?.width } }
    Box(Modifier.fillMaxSize().onGloballyPositioned { c -> val r = c.boundsInRoot(); if (box != r) box = r }) {
        if (gone) return@Box
        // The liquid itself: the screen's own background, spreading over what's underneath.
        if (growing) Canvas(Modifier.fillMaxSize()) {
            val s = shapeNow(size.width, size.height)
            drawRoundRect(t.bg, Offset(s.l, s.t), Size((s.r - s.l).coerceAtLeast(0f), (s.b - s.t).coerceAtLeast(0f)), androidx.compose.ui.geometry.CornerRadius(s.radius))
        }
        val d = density.density
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val a = shown.value
                alpha = a
                if (growing) { clip = true; shape = shapeNow(size.width, size.height) }
                // A small settle as it fades in; no blur, so it stays smooth on any phone.
                if (a < 1f) {
                    val k = 1f - a
                    when (mo) {
                        Mo.Drop -> { val sc = 0.96f + 0.04f * a; scaleX = sc; scaleY = sc }
                        else -> translationY = 8f * d * k
                    }
                }
            },
        ) { CompositionLocalProvider(LocalAvatarSlot provides slot) { content() } }
        // The photo from the row, flying into the header (or back down into the row).
        val from = m.avatar
        val size = landSize
        if (flying && avatar != null && from != null && size != null) {
            Box(
                Modifier
                    .offset {
                        val to = slot.rect ?: from; val b = box ?: Rect.Zero
                        val k = fly.value
                        // A gentle arc: the photo lifts a little as it travels.
                        val lift = kotlin.math.sin(Math.PI * k.coerceIn(0f, 1f)).toFloat() * 18f * density.density
                        val cx = from.center.x + (to.center.x - from.center.x) * k - b.left
                        val cy = from.center.y + (to.center.y - from.center.y) * k - b.top - lift
                        IntOffset((cx - size / 2).roundToInt(), (cy - size / 2).roundToInt())
                    }
                    .size(with(density) { size.toDp() })
                    .graphicsLayer {
                        val k = fly.value
                        val sc = (from.width / size) + (1f - from.width / size) * k
                        scaleX = sc; scaleY = sc
                    },
            ) { avatar(with(density) { size.toDp() }) }
        }
    }
}
