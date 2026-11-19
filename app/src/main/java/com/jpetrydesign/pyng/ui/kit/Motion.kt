package com.jpetrydesign.pyng.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private val Settle = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Turns a spring written as stiffness and a damping force into Compose's damping ratio. */
private fun ratio(st: Float, dm: Float) = (dm / (2f * sqrt(st))).coerceIn(0.05f, 1.5f)

/**
 * A springy squash whenever [key] changes, like a drop changing shape: Rise stretches up,
 * Pour sloshes side to side, Drop squashes and rounds back. Nothing happens on the first showing.
 */
fun Modifier.wobble(key: Any?): Modifier = composed {
    val mo = LocalMo.current
    val d = LocalDensity.current.density
    val sx = remember { Animatable(1f) }; val sy = remember { Animatable(1f) }
    val ty = remember { Animatable(0f) }; val ro = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (!mo.liquid) return@LaunchedEffect
        // Stiffness, damping, starting push on width and height, a little hop, a little turn.
        val (st, dm, vx, vy, hop, turn) = when (mo) {
            Mo.Pour -> W6(360f, 10f, 1.1f, -0.7f, 0f, 1f)
            Mo.Drop -> W6(420f, 38f, 2.2f, -2.6f, 1f, 0f)
            else -> W6(520f, 17f, -1.5f, 2.1f, -1f, 0f)
        }
        val s = spring<Float>(dampingRatio = ratio(st, dm), stiffness = st)
        launch { sx.animateTo(1f, s, initialVelocity = vx) }
        launch { sy.animateTo(1f, s, initialVelocity = vy) }
        launch { ty.animateTo(0f, spring(dampingRatio = ratio(420f, 24f), stiffness = 420f), initialVelocity = hop * 60f * d) }
        launch { ro.animateTo(0f, spring(dampingRatio = ratio(300f, 9f), stiffness = 300f), initialVelocity = turn * 90f) }
    }
    this.graphicsLayer { scaleX = sx.value; scaleY = sy.value; translationY = ty.value; rotationZ = ro.value }
}

private data class W6(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float)

/**
 * Buds in from below, as if it rose out of the message box: stretched tall and narrow at first, then
 * settling round. Staggered by [index]. Without liquid motion it just fades up a little.
 */
fun Modifier.budIn(index: Int): Modifier = composed {
    val mo = LocalMo.current
    val motion = LocalMotion.current
    val a = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        delay(index * 80L)
        if (mo.liquid) a.animateTo(1f, spring(dampingRatio = if (mo == Mo.Drop) 0.62f else if (mo == Mo.Pour) 0.8f else 0.55f, stiffness = if (mo == Mo.Pour) 300f else 420f))
        else a.animateTo(1f, tween(if (mo == Mo.Minimal) 120 else 240, easing = Settle))
    }
    this.graphicsLayer {
        val k = a.value
        alpha = (k * 2.2f).coerceIn(0f, 1f)
        if (mo.liquid) {
            transformOrigin = TransformOrigin(0.5f, 1f)
            val q = 1f - k
            when (mo) {
                Mo.Drop -> { val z = 0.25f + 0.75f * k; scaleX = z; scaleY = z; translationY = q * 22f * density }
                Mo.Pour -> { scaleX = 1f - q * 0.35f; scaleY = 1f + q * 0.15f; translationY = q * 16f * density }
                else -> { scaleX = 0.35f + 0.65f * k; scaleY = 1f + q * 0.35f; translationY = q * 20f * density }
            }
        } else if (mo == Mo.Simple) translationY = (1f - k) * 8f * density
    }
}

private val shimmered = HashSet<String>()

/** A soft band of light sweeps across once, the first time [key] shows (e.g. a smart chip). */
fun Modifier.shimmerOnce(key: String, tint: Color = Color.White): Modifier = composed {
    val motion = LocalMotion.current
    val run = remember(key) { motion && shimmered.add(key) }
    val p = remember(key) { Animatable(if (run) 0f else 1f) }
    LaunchedEffect(key) { if (run) { delay(350); p.animateTo(1f, tween(900, easing = Settle)) } }
    this.drawWithContent {
        drawContent()
        val k = p.value
        if (k in 0.001f..0.999f) {
            val w = size.width; val band = size.height * 2.2f
            val x = -band + (w + band * 2) * k
            drawRect(Brush.linearGradient(listOf(Color.Transparent, tint.copy(alpha = 0.42f), Color.Transparent), Offset(x - band, 0f), Offset(x + band, size.height)))
        }
    }
}
