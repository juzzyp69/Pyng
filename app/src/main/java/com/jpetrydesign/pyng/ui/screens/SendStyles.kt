package com.jpetrydesign.pyng.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Every send style runs on the same base:
 *  - Motion is worked out in fixed 1/240 s steps, so it looks the same at 60, 90, 120 or 144 Hz,
 *    and a slow frame never speeds it up or slows it down.
 *  - The flying shape lands exactly on the real bubble's spot and corners, then the real bubble takes
 *    over in the same frame and does the landing bounce itself, so there's only ever one bubble.
 *  - Shapes are plain paths (no blur tricks), so they're crisp and work on every Android version.
 * Only the shape and motion differ between styles.
 */

object SendStyles {
    // Six that each feel different and stay smooth: three classic, three liquid.
    val classic = listOf("Jelly", "Pop", "Shimmer")
    val liquid = listOf("Rise", "Droplet", "Splash")
    /** Older picks map onto the closest one that's still here. */
    fun of(s: String): String = when (s) {
        "Swoosh" -> "Jelly"; "Taffy" -> "Pop"; "Honey" -> "Rise"; "Ink" -> "Droplet"
        in classic, in liquid -> s
        else -> "Rise"
    }
    val about = mapOf(
        "Jelly" to "Glides up, one small squish", "Pop" to "Pops into place", "Swoosh" to "Sweeps in from the side",
        "Taffy" to "Stretches, then snaps up", "Shimmer" to "Quiet glide, soft light",
        "Rise" to "Lifts out on a gooey neck", "Honey" to "Thick, slow and stretchy", "Droplet" to "A drop that spreads out",
        "Splash" to "Soft edges, little drops", "Ink" to "Floods in from the send button",
    )
}

/** A spring described by how long it takes to respond and how much it bounces. */
internal class FxSpring(start: Float, response: Float, damping: Float) {
    var v = start
    var to = start
    var vel = 0f
    private var k = 0f
    private var c = 0f
    init { tune(response, damping) }
    fun tune(response: Float, damping: Float) { val w = (2.0 * PI / response).toFloat(); k = w * w; c = 2f * damping * w }
    fun step(dt: Float) { val a = -k * (v - to) - c * vel; vel += a * dt; v += vel * dt }
    fun still(eps: Float) = abs(v - to) < eps && abs(vel) < eps * 20f
}

internal fun easeOut(x: Float): Float { val u = 1f - x.coerceIn(0f, 1f); return 1f - u * u * u }
internal fun smooth(x: Float): Float { val y = x.coerceIn(0f, 1f); return y * y * (3f - 2f * y) }
internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** What a style needs to draw: colors, the words in the box and in the bubble, and the bubble's padding. */
internal class Look(val t: Tok, val dry: TextLayoutResult, val wet: () -> TextLayoutResult?, val padL: Float, val padT: Float) {
    val fieldSolid: Color = t.field.compositeOver(t.bg)
}

/** One send's animation. The layer steps it, draws it, and copies its real-bubble values onto the bubble. */
internal abstract class Flight(val run: MeltRun, val d: Float, val corner: Float) {
    val F = run.field
    var time = 0f
    /** The real bubble is showing (and the flying shape is gone). */
    var showReal = false
    var done = false
    var realSX = 1f; var realSY = 1f; var realTX = 0f; var realTY = 0f; var realRot = 0f
    /** Set by the layer: how wide the screen is. */
    var screenW = 0f
    open val needsBubble = true
    /** Blur for the whole flying layer (Splash), in px. Only used where Android supports it. */
    open val blur = 0f
    open val maxTime = 1.8f
    var lastB: Rect? = null
    abstract fun step(dt: Float, t: Float, B: Rect)
    abstract fun draw(s: DrawScope, k: Look)

    protected fun bubblePath(x: Float, y: Float, w: Float, h: Float, r: Float): Path {
        val rr = min(r, min(w, h) / 2f)
        return Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(rr))) }
    }
    protected fun DrawScope.dryText(k: Look, alpha: Float, at: Offset = run.textBox.topLeft) {
        if (alpha > 0.01f) drawText(k.dry, color = k.t.ink, topLeft = at, alpha = alpha)
    }
    protected fun DrawScope.wetText(k: Look, path: Path, at: Offset, alpha: Float) {
        val wet = k.wet() ?: return
        if (alpha > 0.01f) clipPath(path) { drawText(wet, color = k.t.outInk, topLeft = at, alpha = alpha) }
    }
}

internal fun flightFor(style: String, run: MeltRun, d: Float, corner: Float): Flight = when (style) {
    "Pop" -> PopFlight(run, d, corner)
    "Swoosh" -> SwooshFlight(run, d, corner)
    "Taffy" -> TaffyFlight(run, d, corner)
    "Shimmer" -> GlideFlight(run, d, corner, GlideFlight.Kind.Shimmer)
    "Splash" -> GlideFlight(run, d, corner, GlideFlight.Kind.Splash)
    "Rise" -> RiseFlight(run, d, corner, honey = false)
    "Honey" -> RiseFlight(run, d, corner, honey = true)
    "Droplet" -> DropletFlight(run, d, corner)
    "Ink" -> InkFlight(run, d, corner)
    else -> GlideFlight(run, d, corner, GlideFlight.Kind.Jelly)
}

// ---------- Jelly, Splash, Shimmer: the box glides up into the bubble ----------

internal class GlideFlight(run: MeltRun, d: Float, corner: Float, val kind: Kind) : Flight(run, d, corner) {
    enum class Kind { Jelly, Splash, Shimmer }
    private val L = FxSpring(F.left, 0.24f, 0.95f)
    private val R = FxSpring(F.right, 0.34f, 0.92f)
    private val T = FxSpring(F.top, 0.34f, 0.9f)
    private val H = FxSpring(F.height, 0.28f, 0.95f)
    private val SX = FxSpring(1f, 0.3f, 0.42f)
    private val SY = FxSpring(1f, 0.3f, 0.42f)
    private var sx = 1f; private var sy = 1f
    private var landT = 0f
    private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float)
    private val drops = ArrayList<Drop>()
    override val blur get() = if (kind == Kind.Splash && !showReal) min(5f * d, abs(T.vel) / 260f) else 0f

    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        L.to = B.left; R.to = B.right; T.to = B.top; H.to = B.height
        if (!showReal) {
            L.step(dt); R.step(dt); T.step(dt); H.step(dt)
            if (kind != Kind.Shimmer) { val v = min(0.08f, abs(T.vel) / (9000f * d)); sy = 1f + v; sx = 1f - v * 0.6f }
            val near = 2.5f * d
            if (t > 0.12f && abs(T.v - T.to) < near && abs(L.v - L.to) < near && abs(R.v - R.to) < near && abs(H.v - H.to) < near) {
                showReal = true; landT = t
                if (kind == Kind.Shimmer) { SX.vel = 0.45f; SY.vel = 0.45f } else { SX.vel = 1.1f; SY.vel = -1.1f }
                if (kind == Kind.Splash) for (i in 0 until 6) {
                    val ang = -PI.toFloat() * (0.15f + 0.7f * i / 5f) + (if (i % 2 == 1) 0.12f else -0.1f)
                    val sp = (170f + (i * 37 % 90)) * d
                    drops += Drop(B.left + B.width * (0.15f + 0.7f * i / 5f), B.top + (if (i % 2 == 1) 2f * d else B.height * 0.2f), cos(ang) * sp * 0.6f, sin(ang) * sp, (2.2f + (i % 3)) * d)
                }
            }
        } else {
            SX.step(dt); SY.step(dt)
            drops.forEach { it.vy += 900f * d * dt; it.x += it.vx * dt; it.y += it.vy * dt }
            realSX = SX.v; realSY = SY.v
            val extra = when (kind) { Kind.Splash -> 0.45f; Kind.Shimmer -> 0.55f; else -> 0f }
            done = SX.still(0.002f) && SY.still(0.002f) && t - landT > extra
        }
    }

    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        if (!showReal) {
            val top = T.v; val w = max(1f, R.v - L.v); val h = max(1f, H.v)
            val prog = ((F.top - top) / max(1f, F.top - (lastB?.top ?: F.top - 1f))).coerceIn(0f, 1f)
            val r = mix(24f * d, corner * d, smooth(prog))
            val tint = easeOut(time / 0.13f)
            val path = bubblePath(L.v, top, w, h, r)
            withTransform({ scale(sx, sy, Offset(L.v + w / 2f, top + h / 2f)) }) {
                drawPath(path, lerpColor(k.fieldSolid, k.t.outFlat, tint))
                val tx = L.v + mix(run.textBox.left - F.left, k.padL, smooth(prog))
                val ty = top + mix(run.textBox.top - F.top, k.padT, smooth(prog))
                dryText(k, 1f - easeOut(time / 0.1f), Offset(tx, ty))
                wetText(k, path, Offset(tx, ty), easeOut((time - 0.02f) / 0.12f))
            }
        } else {
            val B = lastB ?: return@with
            drops.forEach { dr ->
                val age = (time - landT) / 0.42f
                if (age < 1f) drawCircle(k.t.outFlat, dr.r * (1f - age * 0.4f), Offset(dr.x, dr.y), alpha = (1f - age) * 0.9f)
            }
            if (kind == Kind.Shimmer) {
                val sw = (time - landT) / 0.5f
                if (sw in 0f..1f) clipPath(bubblePath(B.left, B.top, B.width, B.height, corner * d)) {
                    val x = mix(B.left - 60f * d, B.right + 60f * d, easeOut(sw))
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent), x - 40f * d, x + 40f * d), Offset(B.left, B.top), Size(B.width, B.height))
                }
            }
        }
    }
}

// ---------- Pop and Swoosh: the real bubble does all the moving ----------

internal class PopFlight(run: MeltRun, d: Float, corner: Float) : Flight(run, d, corner) {
    private val S = FxSpring(0.2f, 0.3f, 0.48f).apply { to = 1f }
    override val needsBubble = false
    init { showReal = true; realSX = 0.2f; realSY = 0.2f }
    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; S.step(dt); realSX = max(0f, S.v); realSY = max(0f, S.v)
        done = t > 0.15f && S.still(0.002f)
    }
    override fun draw(s: DrawScope, k: Look): Unit = with(s) { dryText(k, 1f - easeOut(time / 0.1f)) }
}

internal class SwooshFlight(run: MeltRun, d: Float, corner: Float) : Flight(run, d, corner) {
    private var X: FxSpring? = null
    private val Y = FxSpring(40f * d, 0.42f, 0.72f).apply { to = 0f }
    private val Rr = FxSpring(7f, 0.42f, 0.55f).apply { to = 0f }
    private val SX = FxSpring(1f, 0.3f, 0.45f)
    private var landed = false
    init { realTX = 10000f }
    override fun step(dt: Float, t: Float, B: Rect) {
        time = t
        val x = X ?: FxSpring(max(screenW, B.right) - B.left + 20f * d, 0.38f, 0.8f).apply { to = 0f }.also { X = it }
        showReal = true
        x.step(dt); Y.step(dt); Rr.step(dt)
        if (!landed && x.v < 1.5f * d) { landed = true; SX.vel = -0.5f }
        if (landed) SX.step(dt)
        realTX = x.v; realTY = Y.v; realRot = Rr.v; realSX = SX.v
        done = landed && x.still(0.3f * d) && Y.still(0.3f * d) && Rr.still(0.05f) && SX.still(0.002f)
    }
    override fun draw(s: DrawScope, k: Look): Unit = with(s) { dryText(k, 1f - easeOut(time / 0.1f)) }
}

// ---------- Taffy: the top leads, the bottom lags, then it snaps up ----------

internal class TaffyFlight(run: MeltRun, d: Float, corner: Float) : Flight(run, d, corner) {
    private val L = FxSpring(F.left, 0.24f, 0.95f)
    private val R = FxSpring(F.right, 0.34f, 0.92f)
    private val T = FxSpring(F.top, 0.34f, 0.9f)
    private val H = FxSpring(F.height, 0.28f, 0.95f)
    private val Bot = FxSpring(F.bottom, 0.5f, 0.62f)
    private val SX = FxSpring(1f, 0.3f, 0.42f)
    private val SY = FxSpring(1f, 0.3f, 0.42f)
    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        L.to = B.left; R.to = B.right; T.to = B.top; H.to = B.height; Bot.to = B.bottom
        if (!showReal) {
            L.step(dt); R.step(dt); T.step(dt); H.step(dt); Bot.step(dt)
            val near = 2.5f * d
            if (t > 0.12f && abs(T.v - T.to) < near && abs(L.v - L.to) < near && abs(R.v - R.to) < near && abs(H.v - H.to) < near && abs(Bot.v - Bot.to) < near) {
                showReal = true; SX.vel = -0.7f; SY.vel = 1.4f
            }
        } else { SX.step(dt); SY.step(dt); realSX = SX.v; realSY = SY.v; done = SX.still(0.002f) && SY.still(0.002f) }
    }
    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        if (showReal) return@with
        val top = T.v; val w = max(1f, R.v - L.v); val h = max(1f, H.v)
        val bot = max(top + h, Bot.v); val hh = bot - top
        val waist = min(w * 0.1f, max(0f, hh - h) * 0.22f)
        val prog = ((F.top - top) / max(1f, F.top - (lastB?.top ?: F.top - 1f))).coerceIn(0f, 1f)
        val r = min(h / 2f, mix(24f * d, corner * d, smooth(prog)))
        val x = L.v
        val path = Path().apply {
            moveTo(x + r, top); lineTo(x + w - r, top); quadraticTo(x + w, top, x + w, top + r)
            cubicTo(x + w - waist, top + hh * 0.3f, x + w - waist, top + hh * 0.7f, x + w, bot - r)
            quadraticTo(x + w, bot, x + w - r, bot); lineTo(x + r, bot); quadraticTo(x, bot, x, bot - r)
            cubicTo(x + waist, top + hh * 0.7f, x + waist, top + hh * 0.3f, x, top + r); quadraticTo(x, top, x + r, top); close()
        }
        drawPath(path, lerpColor(k.fieldSolid, k.t.outFlat, easeOut(time / 0.13f)))
        val tx = x + mix(run.textBox.left - F.left, k.padL, smooth(prog))
        val ty = top + mix(run.textBox.top - F.top, k.padT, smooth(prog))
        dryText(k, 1f - easeOut(time / 0.1f), Offset(tx, ty))
        wetText(k, path, Offset(tx, ty), easeOut((time - 0.02f) / 0.12f))
    }
}

// ---------- Rise and Honey: the bubble lifts out of where you typed on a neck that thins and lets go ----------

internal class RiseFlight(run: MeltRun, d: Float, corner: Float, val honey: Boolean) : Flight(run, d, corner) {
    override val maxTime = if (honey) 2.4f else 1.9f
    private var ready = false
    private lateinit var L: FxSpring; private lateinit var R: FxSpring; private lateinit var T: FxSpring; private lateinit var H: FxSpring
    private val PX = FxSpring(0f, 0.16f, 0.9f)
    private val D1 = if (honey) FxSpring(0f, 0.42f, 0.28f) else FxSpring(0f, 0.22f, 0.35f)
    private val SX = FxSpring(1f, 0.32f, 0.4f)
    private val SY = FxSpring(1f, 0.32f, 0.4f)
    private var bh0 = 0f
    private var travel = 1f
    private var letGo = false
    private var letT = 0f
    private var nw = 0f
    private var gap = 0f

    private fun start(B: Rect) {
        // It starts as a small bead right where your words were, then grows into the bubble as it rises.
        val bw0 = min(B.width, F.width * 0.45f)
        bh0 = min(B.height, F.height * 0.62f)
        val sx0 = min(F.right - bw0 / 2f - 12f * d, run.textBox.left + bw0 / 2f)
        L = if (honey) FxSpring(sx0 - bw0 / 2f, 0.5f, 0.8f) else FxSpring(sx0 - bw0 / 2f, 0.36f, 0.74f)
        R = if (honey) FxSpring(sx0 + bw0 / 2f, 0.54f, 0.78f) else FxSpring(sx0 + bw0 / 2f, 0.4f, 0.72f)
        T = if (honey) FxSpring(F.top + (F.height - bh0) / 2f, 0.6f, 0.8f) else FxSpring(F.top + (F.height - bh0) / 2f, 0.44f, 0.74f)
        H = if (honey) FxSpring(bh0, 0.42f, 0.75f) else FxSpring(bh0, 0.32f, 0.7f)
        PX.v = sx0; PX.to = sx0
        travel = max(1f, F.top - B.bottom)
        ready = true
    }

    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        if (!ready) start(B)
        if (!showReal) {
            L.to = B.left; R.to = B.right; T.to = B.top; H.to = B.height
            L.step(dt); R.step(dt); T.step(dt); H.step(dt)
            PX.to = (L.v + R.v) / 2f; PX.step(dt)
            gap = F.top - (T.v + H.v)
            val k = min(1f, max(0f, gap) / (travel * (if (honey) 0.85f else 0.7f)))
            if (!letGo) {
                nw = max(0f, (R.v - L.v) * (if (honey) 0.55f else 0.42f) * (1f - k * k) + (if (honey) 6f else 3f) * d * (1f - k))
                if (gap > 2f * d && nw < 1.5f * d) { letGo = true; letT = t; D1.v = min((if (honey) 44f else 34f) * d, gap / 2f); D1.vel = 0f }
            } else D1.step(dt)
            val near = 2.5f * d
            if (t > 0.15f && abs(T.v - T.to) < near && abs(L.v - L.to) < near && abs(R.v - R.to) < near && abs(H.v - H.to) < near) {
                showReal = true; SX.vel = if (honey) 0.5f else 0.9f; SY.vel = if (honey) -0.75f else -1.2f
            }
        } else {
            SX.step(dt); SY.step(dt); D1.step(dt)
            realSX = SX.v; realSY = SY.v
            done = SX.still(0.002f) && SY.still(0.002f) && t - letT > 0.34f
        }
    }

    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        val B = lastB ?: return@with
        if (!ready) return@with
        val col = k.t.outFlat
        dryText(k, 1f - easeOut(time / 0.08f))
        // The neck: a soft strand from the bead down into the box, thinning until it lets go.
        if (!letGo) {
            if (gap > 0f && nw > 0.4f) {
                val top = T.v + H.v - 6f * d; val hh = max(0f, gap + 12f * d)
                drawRoundRect(col, Offset(PX.v - nw / 2f, top), Size(nw, hh), CornerRadius(nw / 2f))
            }
        } else {
            // What's left of it falls back into the box as a drip and fades away.
            val r = max(0f, D1.v * 0.18f); val a = (1f - (time - letT) * 3f).coerceIn(0f, 1f)
            if (r > 0.3f && a > 0f) drawCircle(col, r, Offset(PX.v, F.top - D1.v * 0.4f + r), alpha = a)
        }
        if (showReal) return@with
        val x = L.v; val w = max(1f, R.v - L.v); val top = T.v; val h = max(1f, H.v)
        val prog = smooth((F.top + (F.height - bh0) / 2f - top) / max(1f, F.top - B.top))
        val body = bubblePath(x, top, w, h, mix(h / 2f, corner * d, prog))
        drawPath(body, col)
        // Your words ride along from where you typed them, sliding into place as it forms.
        val tx = mix(max(4f * d, run.textBox.left - x), k.padL, prog)
        val ty = mix((h - 20f * d) / 2f, k.padT, prog)
        wetText(k, body, Offset(x + tx, top + ty), easeOut((time - 0.04f) / 0.14f))
    }
}

// ---------- Droplet: shrinks to a drop, then spreads out into the bubble near the top ----------

internal class DropletFlight(run: MeltRun, d: Float, corner: Float) : Flight(run, d, corner) {
    private val D = 36f * d
    private var ready = false
    private lateinit var CX: FxSpring; private lateinit var CY: FxSpring; private lateinit var W: FxSpring; private lateinit var H: FxSpring
    private val SX = FxSpring(1f, 0.3f, 0.42f)
    private val SY = FxSpring(1f, 0.3f, 0.42f)
    private var spread = false

    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        if (!ready) {
            val tw0 = min(F.width - 32f * d, run.textBox.width)
            val seed = min(tw0, 80f * d)
            CX = FxSpring(run.textBox.left + seed / 2f, 0.34f, 0.84f); CY = FxSpring(F.center.y, 0.36f, 0.8f)
            W = FxSpring(max(44f * d, seed + 20f * d), 0.16f, 0.92f); H = FxSpring(F.height * 0.72f, 0.16f, 0.92f)
            W.to = D; H.to = D
            ready = true
        }
        if (!showReal) {
            CX.to = B.center.x; CY.to = B.center.y
            if (!spread && abs(CY.v - B.center.y) < (F.center.y - B.center.y) * 0.3f) { spread = true; W.to = B.width; H.to = B.height }
            CX.step(dt); CY.step(dt); W.step(dt); H.step(dt)
            val e = 2.5f * d
            if (spread && t > 0.15f && abs(W.v - B.width) < e && abs(H.v - B.height) < e && abs(CX.v - B.center.x) < e && abs(CY.v - B.center.y) < e) {
                showReal = true; SX.vel = 1.2f; SY.vel = -1.2f
            }
        } else { SX.step(dt); SY.step(dt); realSX = SX.v; realSY = SY.v; done = SX.still(0.002f) && SY.still(0.002f) }
    }

    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        if (showReal || !ready) return@with
        val B = lastB ?: return@with
        val w = max(1f, W.v); val h = max(1f, H.v); val x = CX.v - w / 2f; val y = CY.v - h / 2f
        val kk = if (spread) smooth((w - D) / max(1f, B.width - D)) else 0f
        // A circle is a rounded box with fully round corners, so it widens straight into the bubble.
        val body = bubblePath(x, y, w, h, mix(min(w, h) / 2f, corner * d, kk))
        drawPath(body, lerpColor(k.fieldSolid, k.t.outFlat, easeOut(time / 0.1f)))
        wetText(k, body, Offset(x + k.padL - (B.width - w) / 2f, y + k.padT - (B.height - h) / 2f), kk)
    }
}

// ---------- Ink: a drop shoots from the send button and floods the bubble ----------

internal class InkFlight(run: MeltRun, d: Float, corner: Float) : Flight(run, d, corner) {
    private var X: FxSpring? = null
    private var Y: FxSpring? = null
    private val Rd = FxSpring(7f * d, 0.34f, 0.82f)
    private val SX = FxSpring(1f, 0.3f, 0.45f)
    private val SY = FxSpring(1f, 0.3f, 0.45f)
    private var phase = 0 // 0 flying, 1 filling, 2 landed
    private var landT = 0f
    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        val cx = B.center.x; val cy = B.center.y
        val x = X ?: FxSpring(run.button.center.x, 0.2f, 0.85f).also { X = it }
        val y = Y ?: FxSpring(run.button.center.y, 0.2f, 0.85f).also { Y = it; it.vel = -300f * d }
        x.to = cx; y.to = cy
        val diag = hypot(B.width, B.height)
        // From the middle, half the diagonal reaches every corner.
        when (phase) {
            0 -> { x.step(dt); y.step(dt); if (hypot(x.v - cx, y.v - cy) < 4f * d) { phase = 1; Rd.to = diag * 0.6f } }
            1 -> { Rd.step(dt); if (Rd.v > diag * 0.55f) { phase = 2; landT = t; showReal = true; SX.vel = 0.35f; SY.vel = 0.35f } }
            else -> { SX.step(dt); SY.step(dt); realSX = SX.v; realSY = SY.v; done = SX.still(0.002f) && SY.still(0.002f) && t - landT > 0.5f }
        }
    }
    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        val B = lastB ?: return@with
        dryText(k, 1f - easeOut(time / 0.1f))
        val out = k.t.outFlat
        when (phase) {
            0 -> {
                val x = X ?: return@with; val y = Y ?: return@with
                val sp = hypot(x.vel, y.vel); val ang = atan2(y.vel, x.vel); val st = min(2.2f, 1f + sp / (900f * d))
                withTransform({ translate(x.v, y.v); rotate(Math.toDegrees(ang.toDouble()).toFloat(), Offset.Zero); scale(st, 1f / sqrt(st), Offset.Zero) }) {
                    drawCircle(out, 7f * d, Offset.Zero)
                }
            }
            1 -> {
                val body = bubblePath(B.left, B.top, B.width, B.height, corner * d)
                clipPath(body) {
                    val cx = B.center.x; val cy = B.center.y; val r = Rd.v
                    val wave = Path()
                    for (i in 0..48) {
                        val a = i / 48f * 2f * PI.toFloat(); val ww = 1f + 0.05f * sin(a * 6f + time * 18f)
                        val px = cx + cos(a) * r * ww; val py = cy + sin(a) * r * ww
                        if (i == 0) wave.moveTo(px, py) else wave.lineTo(px, py)
                    }
                    wave.close()
                    drawPath(wave, out)
                }
                wetText(k, body, Offset(B.left + k.padL, B.top + k.padT), easeOut((Rd.v / (hypot(B.width, B.height) * 0.55f) - 0.5f) / 0.5f))
            }
            else -> {
                val p = (time - landT) / 0.5f
                if (p < 1f) {
                    val g = (6f + p * 22f) * d
                    drawPath(bubblePath(B.left - g, B.top - g, B.width + g * 2, B.height + g * 2, corner * d + g), out, alpha = (1f - p) * 0.5f, style = Stroke(2f * d))
                }
            }
        }
    }
}

