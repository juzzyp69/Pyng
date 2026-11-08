package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The send jelly. When you tap send, the message box becomes the bubble: it narrows, glides up
 * into place on soft springs and stretches a touch while it moves, with your words riding inside.
 * The moment it arrives, the real bubble takes over and does one small jelly squish, so there's
 * never a second copy on screen. Colors come from the chat's own bubble, so it suits any theme.
 */

/** Where things are on screen, and what's currently flying. Shared by the chat screen, composer and bubbles. */
class SendFx {
    var field: LayoutCoordinates? = null
    var text: LayoutCoordinates? = null
    var button: LayoutCoordinates? = null
    var bubble: LayoutCoordinates? = null
    var run by mutableStateOf<MeltRun?>(null)
    var hideTmp by mutableStateOf<String?>(null)
    var bubbleAlpha by mutableFloatStateOf(1f)
    /** The landing squish, applied to the real bubble. */
    var bubbleScaleX by mutableFloatStateOf(1f)
    var bubbleScaleY by mutableFloatStateOf(1f)
    /** True for a moment after sending, so the box's placeholder doesn't show through the flying bubble. */
    var quiet by mutableStateOf(false)
    // The send button stays put now; kept so the composer's hookup doesn't change.
    var btnScale by mutableFloatStateOf(1f)
    var btnAlpha by mutableFloatStateOf(1f)

    /** The new bubble stays hidden until the glide lands on it. */
    fun alphaFor(m: Msg): Float = if (m.tmpId.isNotEmpty() && m.tmpId == hideTmp) bubbleAlpha else 1f
    fun scaleXFor(m: Msg): Float = if (m.tmpId.isNotEmpty() && m.tmpId == hideTmp) bubbleScaleX else 1f
    fun scaleYFor(m: Msg): Float = if (m.tmpId.isNotEmpty() && m.tmpId == hideTmp) bubbleScaleY else 1f
    fun isTarget(m: Msg) = m.tmpId.isNotEmpty() && m.tmpId == run?.tmpId
    /** Messages that already arrived by gliding in, so they never play the fade-in on top of it. */
    val flown = HashSet<String>()
    fun arrived(m: Msg) = m.tmpId.isNotEmpty() && (m.tmpId in flown || m.tmpId == run?.tmpId)

    fun start(r: MeltRun) {
        flown += r.tmpId
        bubble = null
        bubbleAlpha = 0f
        bubbleScaleX = 1f; bubbleScaleY = 1f
        hideTmp = r.tmpId
        quiet = true
        run = r
    }

    fun finish() {
        run = null
        hideTmp = null
        quiet = false
        bubbleAlpha = 1f
        bubbleScaleX = 1f; bubbleScaleY = 1f
        btnScale = 1f
        btnAlpha = 1f
    }
}

data class MeltRun(val tmpId: String, val text: String, val field: Rect, val textBox: Rect, val button: Rect, val stamp: Long = System.nanoTime())

val LocalSendFx = compositionLocalOf<SendFx?> { null }

// ---------- motion ----------

private const val SUB = 1f / 240f // fixed steps: identical motion at 60, 90 or 120 Hz

/** A spring described like Apple's: how long it takes to respond, and how much it bounces. */
private class Sp(start: Float, response: Float, damping: Float) {
    var v = start
    var to = start
    var vel = 0f
    private val w = (2.0 * PI / response).toFloat()
    private val k = w * w
    private val c = 2f * damping * w
    fun step(dt: Float) { val a = -k * (v - to) - c * vel; vel += a * dt; v += vel * dt }
    fun still(eps: Float) = abs(v - to) < eps && abs(vel) < eps * 20f
    /** How far along from [from] to the target, 0 to 1. */
    fun prog(from: Float): Float { val d = to - from; return if (abs(d) < 0.5f) 1f else ((v - from) / d).coerceIn(0f, 1f) }
}

private fun ease(x: Float): Float { val u = 1f - x.coerceIn(0f, 1f); return 1f - u * u * u }
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private class Glide(run: MeltRun, val d: Float) {
    val F = run.field
    val T0 = run.textBox.topLeft
    val L = Sp(F.left, 0.24f, 0.95f)
    val R = Sp(F.right, 0.34f, 0.92f)
    val T = Sp(F.top, 0.34f, 0.9f)
    val H = Sp(F.height, 0.28f, 0.95f)
    val SX = Sp(1f, 0.3f, 0.42f)
    val SY = Sp(1f, 0.3f, 0.42f)
    var sx = 1f
    var sy = 1f
    var time = 0f
    /** Set once the flying bubble reaches its spot and the real one takes over. */
    var landed = false
    var done = false

    fun step(dt: Float, t: Float, B: Rect) {
        time = t
        L.to = B.left; R.to = B.right; T.to = B.top; H.to = B.height
        if (!landed) {
            L.step(dt); R.step(dt); T.step(dt); H.step(dt)
            // A soft stretch while it moves fast.
            val v = min(0.08f, abs(T.vel) / (9000f * d)); sy = 1f + v; sx = 1f - v * 0.6f
            val near = 2.5f * d
            if (t > 0.12f && abs(T.v - T.to) < near && abs(L.v - L.to) < near && abs(R.v - R.to) < near && abs(H.v - H.to) < near) {
                landed = true
                // One small squish on the real bubble: a touch wider and shorter, then settle.
                SX.vel = 1.1f; SY.vel = -1.1f
            }
        } else {
            SX.step(dt); SY.step(dt)
            done = SX.still(0.002f) && SY.still(0.002f)
        }
    }
}

// ---------- drawing ----------

/** Draws the flying bubble over the chat. Put it last in the chat screen's root box. */
@Composable
fun SendMeltLayer(fx: SendFx, t: Tok, fontSize: Float, bold: Boolean, corner: Float = 20f, root: () -> LayoutCoordinates?) {
    val run = fx.run ?: return
    val density = LocalDensity.current
    val d = density.density
    val measurer = rememberTextMeasurer()
    val weight = if (bold) FontWeight.Medium else FontWeight.Normal
    val dryStyle = remember(fontSize, bold) { TextStyle(fontFamily = Geist, fontSize = fontSize.sp, lineHeight = (fontSize * 1.32f).sp, fontWeight = weight) }
    val wetStyle = remember(fontSize, bold) { TextStyle(fontFamily = Geist, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4f).sp, fontWeight = weight) }
    var frame by remember(run.stamp) { mutableIntStateOf(0) }
    var layerAlpha by remember(run.stamp) { mutableFloatStateOf(1f) }
    val glide = remember(run.stamp) { Glide(run, d) }
    val dry = remember(run.stamp) { measurer.measure(AnnotatedString(run.text), dryStyle, constraints = Constraints(maxWidth = max(1, run.textBox.width.toInt()))) }
    val wetHolder = remember(run.stamp) { arrayOfNulls<TextLayoutResult>(1) }
    val padL = 16f * d
    val padT = 11f * d
    LaunchedEffect(run.stamp) {
        fun bubbleRect(): Rect? {
            val r = root() ?: return null; val b = fx.bubble ?: return null
            return runCatching { if (b.isAttached && r.isAttached) r.localBoundingBoxOf(b) else null }.getOrNull()
        }
        var measuredFor = -1
        var last = withFrameNanos { it }
        var waited = 0f
        var carry = 0f
        var t = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f); last = now
            val B = bubbleRect()
            if (B == null && t == 0f) {
                // Waiting a frame or two for the new bubble to be laid out.
                waited += dt
                if (waited > 0.45f) break // it never showed (scrolled away, or an emoji-only message)
                frame++
                continue
            }
            if (B != null && measuredFor != B.width.toInt()) {
                measuredFor = B.width.toInt()
                wetHolder[0] = measurer.measure(AnnotatedString(run.text), wetStyle, constraints = Constraints(maxWidth = max(1, (B.width - padL * 2).toInt())))
            }
            carry += dt
            val target = B ?: Rect(glide.L.to, glide.T.to, glide.R.to, glide.T.to + glide.H.to)
            while (carry >= SUB) { carry -= SUB; t += SUB; glide.step(SUB, t, target) }
            if (t > 0.2f) fx.quiet = false
            if (glide.landed) {
                // Swap in the real bubble in the same frame the flying one disappears.
                layerAlpha = 0f
                fx.bubbleAlpha = 1f
                fx.bubbleScaleX = glide.SX.v
                fx.bubbleScaleY = glide.SY.v
            }
            frame++
            if (glide.done || t > 1.6f) break
        }
        fx.finish()
    }

    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = layerAlpha }) {
        if (frame < 0) return@Canvas // reading frame redraws every frame
        val g = glide
        if (g.landed) return@Canvas
        val top = g.T.v
        val w = max(1f, g.R.v - g.L.v)
        val h = max(1f, g.H.v)
        val tint = ease(g.time / 0.13f)
        val r = min(h / 2f, lerp(24f * d, corner * d, g.T.prog(g.F.top)))
        val pivot = Offset(g.L.v + w / 2f, top + h / 2f)
        scale(g.sx, g.sy, pivot) {
            drawRoundRect(t.outBg, topLeft = Offset(g.L.v, top), size = Size(w, h), cornerRadius = CornerRadius(r), alpha = lerp(0.22f, 1f, tint))
            // Your words: the box's ink fading out as the bubble's ink fades in.
            val tx = g.L.v + lerp(g.T0.x - g.F.left, padL, g.L.prog(g.F.left))
            val ty = top + lerp(g.T0.y - g.F.top, padT, g.T.prog(g.F.top))
            val dryA = 1f - ease(g.time / 0.1f)
            if (dryA > 0.01f) drawText(dry, color = t.ink, topLeft = Offset(tx, ty), alpha = dryA)
            val wet = wetHolder[0]
            val wetA = ease((g.time - 0.02f) / 0.12f)
            if (wet != null && wetA > 0.01f) drawText(wet, color = t.outInk, topLeft = Offset(tx, ty), alpha = wetA)
        }
    }
}
