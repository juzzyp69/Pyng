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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
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
import com.jpetrydesign.pyng.ui.kit.LRect
import com.jpetrydesign.pyng.ui.kit.LiquidDriver
import com.jpetrydesign.pyng.ui.kit.LiquidStage
import com.jpetrydesign.pyng.ui.kit.LocalMo
import com.jpetrydesign.pyng.ui.kit.Mo
import com.jpetrydesign.pyng.ui.kit.budSim
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Where a screen grows out of: the button or row you tapped, in window pixels, with its corner radius.
 * [avatar] is the photo in that row, which flies up into the chat's header.
 */
class Morph(val from: Rect, val radius: Float, val avatar: Rect? = null) {
    /** The screen it was opened from. Going back to that screen drains it back into what you tapped. */
    var under: Any? = null
}

/** How long the screen underneath stays put while a screen grows over it or shrinks back off it. */
const val MORPH_HOLD = 1100

/** Lets the chat header tell the flying photo where to land, and hides its own photo until it does. */
class AvatarSlot {
    var rect by mutableStateOf<Rect?>(null)
    var hidden by mutableStateOf(false)
}

val LocalAvatarSlot = staticCompositionLocalOf<AvatarSlot?> { null }

/** Marks the photo the flying one lands on. Does nothing outside a morph. */
fun Modifier.avatarSlot(): Modifier = composed {
    val s = LocalAvatarSlot.current ?: return@composed this
    this.onGloballyPositioned { c -> val r = Rect(c.positionInRoot(), c.size.toSize()); if (s.rect != r) s.rect = r }
        .graphicsLayer { alpha = if (s.hidden) 0f else 1f }
}

/** Same as [avatarSlot], for callers that'd rather not import an extension. */
fun avatarSlotOf(m: Modifier): Modifier = m.avatarSlot()

private class PathShape(val path: Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(path)
}

private val Settle = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * A screen that pours out of what you tapped (and drains back into it when you go back), in the
 * current liquid motion style. Content fades in from a soft blur once the shape is mostly formed.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun AnimatedVisibilityScope.MorphScreen(m: Morph, grow: Boolean, shrink: Boolean, avatar: (@Composable (Dp) -> Unit)?, content: @Composable () -> Unit) {
    val mo = LocalMo.current
    val t = LocalTok.current
    val density = LocalDensity.current
    val stage = remember { LiquidStage() }
    LiquidDriver(stage)
    // Only a screen that's arriving grows; one that was already up (or came back some other way) just shows.
    val grows = remember { grow && transition.currentState == EnterExitState.PreEnter }
    val exiting = shrink && transition.targetState == EnterExitState.PostExit
    var box by remember { mutableStateOf<Rect?>(null) }
    var growing by remember { mutableStateOf(grows) }
    var gone by remember { mutableStateOf(false) }
    // Content fade: 0 hidden, 1 shown.
    val shown = remember { Animatable(if (grows) 0f else 1f) }
    val slot = remember { AvatarSlot().also { it.hidden = grows && avatar != null && m.avatar != null } }
    // The flying photo: 0 at the row, 1 in the header.
    val fly = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(grows && avatar != null && m.avatar != null) }
    val dir = remember { mutableStateOf(Offset(0f, 1f)) }

    fun simRects(b: Rect): Pair<LRect, LRect> {
        val d = density.density
        val src = LRect.of(m.from, m.radius, d, b.topLeft)
        val dst = LRect(0f, 0f, b.width / d, b.height / d, 28f)
        return src to dst
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!grows) return@LaunchedEffect
        val b = snapshotFlow { box }.filterNotNull().first()
        val (s, d) = simRects(b)
        dir.value = Offset(d.cx - s.cx, d.cy - s.cy).let { o -> val l = o.getDistance().coerceAtLeast(1f); Offset(o.x / l, o.y / l) }
        val sim = budSim(s, d, rev = false, style = mo)
        // A whole screen starts showing through a little sooner, so there's never a blank moment.
        sim.revealAt = 0.5f
        val revealMs = if (mo == Mo.Drop) 320 else 260
        sim.onReveal = { scope.launch { shown.animateTo(1f, tween(revealMs, easing = Settle)) } }
        if (flying) scope.launch {
            snapshotFlow { slot.rect }.filterNotNull().first()
            fly.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
            // Hand over to the header's own photo only once the chat has fully faded in.
            snapshotFlow { shown.value >= 0.999f }.first { it }
            flying = false; slot.hidden = false
        }
        stage.play(sim, t.bg)
        if (shown.value < 1f) shown.animateTo(1f, tween(120))
        growing = false
    }
    // Going back: the content fades, then the screen drains back into what opened it.
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        // Backing out mid-growth: let it finish forming first so the two never fight.
        snapshotFlow { growing }.first { !it }
        val b = box ?: run { gone = true; return@LaunchedEffect }
        val (s, d) = simRects(b)
        if (avatar != null && m.avatar != null && slot.rect != null) {
            fly.snapTo(1f); flying = true; slot.hidden = true
            scope.launch { fly.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 300f)) }
        }
        shown.animateTo(0f, tween(110))
        growing = true
        stage.play(budSim(s, d, rev = true, style = mo), t.bg)
        gone = true
    }

    // Nothing below reads a moving value while composing: each frame only re-draws or re-places, so the
    // screen inside (a whole chat) is never rebuilt mid-motion.
    val contentOn by remember { derivedStateOf { shown.value > 0f } }
    val landSize by remember { derivedStateOf { slot.rect?.width } }
    Box(Modifier.fillMaxSize().onGloballyPositioned { c -> val r = c.boundsInRoot(); if (box != r) box = r }) {
        if (gone) return@Box
        val liquid = growing || stage.busy
        if (liquid) Canvas(Modifier.fillMaxSize()) { stage.tick; stage.paths().forEach { drawPath(it, t.bg) } }
        if (!exiting || contentOn) {
            val d = density.density
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val a = shown.value
                    alpha = a
                    if (liquid) {
                        stage.tick
                        stage.paths().firstOrNull()?.let { clip = true; shape = PathShape(it) }
                    }
                    if (a < 1f) {
                        val k = 1f - a
                        when (mo) {
                            Mo.Pour -> { translationX = -dir.value.x * 10f * d * k; translationY = -dir.value.y * 10f * d * k }
                            Mo.Drop -> { val sc = 0.9f + 0.1f * a; scaleX = sc; scaleY = sc }
                            else -> translationY = 7f * d * k
                        }
                        val blur = (if (mo == Mo.Drop) 7f else 5f) * d * k
                        if (blur > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                    }
                },
            ) { CompositionLocalProvider(LocalAvatarSlot provides slot) { content() } }
        }
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
