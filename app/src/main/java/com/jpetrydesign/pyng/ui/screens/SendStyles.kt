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
    val classic = listOf("Jelly", "Pop", "Swoosh", "Taffy", "Shimmer")
    val liquid = listOf("Rise", "Honey", "Droplet", "Splash", "Ink")
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

// ---------- Rise and Honey: the bubble lifts out of the box on a gooey neck ----------

internal class RiseFlight(run: MeltRun, d: Float, corner: Float, val honey: Boolean) : Flight(run, d, corner) {
    override val maxTime = if (honey) 2.4f else 1.9f
    private val textW = run.textBox.width.coerceAtMost(F.width - 32f * d)
    private val sx0 = run.textBox.left + textW / 2f
    private val bw0 = max(44f * d, min(textW + 20f * d, F.width * 0.7f))
    private val bh0 = F.height * 0.62f
    private val L = if (honey) FxSpring(sx0 - bw0 / 2, 0.5f, 0.8f) else FxSpring(sx0 - bw0 / 2, 0.36f, 0.74f)
    private val R = if (honey) FxSpring(sx0 + bw0 / 2, 0.54f, 0.78f) else FxSpring(sx0 + bw0 / 2, 0.4f, 0.72f)
    private val T = if (honey) FxSpring(F.top + (F.height - bh0) / 2, 0.6f, 0.8f) else FxSpring(F.top + (F.height - bh0) / 2, 0.44f, 0.74f)
    private val H = if (honey) FxSpring(bh0, 0.42f, 0.75f) else FxSpring(bh0, 0.32f, 0.7f)
    private val P = FxSpring(F.height, 0.3f, 0.95f)
    private val PX = FxSpring(sx0, 0.16f, 0.9f)
    private val D1 = if (honey) FxSpring(0f, 0.42f, 0.28f) else FxSpring(0f, 0.22f, 0.35f)
    private val D2 = FxSpring(0f, 0.24f, 0.85f)
    private val SX = FxSpring(1f, 0.32f, 0.4f)
    private val SY = FxSpring(1f, 0.32f, 0.4f)
    private val gk = if (honey) 0.34f else 0.24f
    private var pinched = false
    private var neckTx = 0f
    private var pinchGap = -1f

    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        if (pinchGap < 0f) {
            val travel = F.top - B.bottom
            pinchGap = min(max(8f * d, travel * 0.92f), max(24f * d, min((if (honey) 170f else 120f) * d, travel * (if (honey) 0.82f else 0.55f))))
        }
        if (!showReal) {
            L.to = B.left; R.to = B.right; T.to = B.top; H.to = B.height
            L.step(dt); R.step(dt); T.step(dt); H.step(dt)
            val gap = F.top - (T.v + H.v)
            PX.to = ((L.v + R.v) / 2f).coerceIn(F.left + 30f * d, F.right - 30f * d); PX.step(dt)
            if (!pinched && gap > pinchGap && t > 0.12f) {
                pinched = true
                val yb0 = T.v + H.v - min(min(H.v / 2f, 20f * d) * 0.8f, H.v * 0.35f)
                val half = (F.top + F.height / 2f - yb0) / 2f
                D1.v = min((if (honey) 44f else 34f) * d, half); D1.vel = 0f
                D2.v = min((if (honey) 30f else 22f) * d, half); D2.vel = 0f
                P.to = 0f
            }
            if (pinched) { D1.step(dt); D2.step(dt); P.step(dt) }
            val near = 2.5f * d
            if (pinched && t > 0.14f && abs(T.v - T.to) < near && abs(L.v - L.to) < near && abs(R.v - R.to) < near * 1.2f && abs(H.v - H.to) < near) {
                showReal = true; SX.vel = if (honey) 0.5f else 0.9f; SY.vel = if (honey) -0.75f else -1.2f
            }
        } else {
            SX.step(dt); SY.step(dt); P.step(dt)
            realSX = SX.v; realSY = SY.v
            done = SX.still(0.002f) && SY.still(0.002f) && P.v < 0.5f
        }
    }

    private fun drip(path: Path, px: Float, base: Float, len: Float, dir: Float, half: Float) {
        if (len < 0.6f) return
        val tip = base + len * dir
        path.moveTo(px - half, base)
        path.cubicTo(px - half * 0.35f, base + len * 0.45f * dir, px - 0.8f, tip - len * 0.12f * dir, px, tip)
        path.cubicTo(px + 0.8f, tip - len * 0.12f * dir, px + half * 0.35f, base + len * 0.45f * dir, px + half, base)
        path.close()
    }

    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        val B = lastB ?: return@with
        val col = lerpColor(k.fieldSolid, k.t.outFlat, easeOut(time / 0.12f))
        // The pool: the box fills, then gets drawn in toward the neck as the bubble pulls it up.
        val kk = easeOut(time / gk)
        val sc = max(0f, P.v / F.height)
        val pw = mix(F.width, 48f * d, kk) * (if (pinched) sc else 1f)
        val ph = mix(F.height, 24f * d, kk) * (if (pinched) sc else 1f)
        val pcx = mix(F.center.x, PX.v, kk)
        val poolTop = F.center.y - ph / 2f
        if (pw > 1f && ph > 1f) {
            val pool = bubblePath(pcx - pw / 2f, poolTop, pw, ph, ph / 2f)
            if (pinched && D2.v > 0.6f) {
                val up = Path(); drip(up, pcx, poolTop + ph * 0.5f, D2.v + ph * 0.5f, -1f, 7f * d)
                pool.op(pool, up, PathOperation.Union)
            }
            drawPath(pool, col, alpha = if (pinched) min(1f, sc * 2.5f) else 1f)
        }
        if (showReal) return@with
        val x = L.v; val w = max(1f, R.v - L.v); val top = T.v; val h = max(1f, H.v); val bot = top + h
        val r = min(h / 2f, 20f * d)
        val rp = smooth((F.top - top) / max(1f, F.top - B.top))
        val rad = mix(r, corner * d, rp)
        val v = min(0.09f, abs(T.vel) / (8000f * d))
        val shape = bubblePath(x, top, w, h, rad)
        // The neck thins smoothly to a thread right as it lets go; its halves become the drips, so nothing pops.
        val nwMax = if (honey) min(w * 0.34f, 34f * d) else min(w * 0.3f, 26f * d)
        val poolW = mix(F.width, 48f * d, kk)
        val swMax = min(w / 2f - r, nwMax * 1.8f)
        val bsMax = max(8f * d, min(poolW / 2f - 6f * d, nwMax * 1.6f))
        val yb = bot - min(r * 0.8f, h * 0.35f)
        val yf = F.center.y
        if (!pinched) {
            val gap = F.top - bot; val dy = yf - yb
            if (gap > -h && dy > 3f) {
                val q = smooth(gap / pinchGap)
                val nw = mix(nwMax, 0.7f * d, q); val sw = mix(swMax, 7f * d, q); val bs = mix(bsMax, 7f * d, q)
                val bx = mix(F.center.x, PX.v, kk)
                val tx = bx.coerceIn(x + sw + r * 0.3f, max(x + sw + r * 0.3f, x + w - sw - r * 0.3f))
                neckTx = tx - x
                val neck = Path().apply {
                    moveTo(tx - sw, yb)
                    cubicTo(tx - nw, yb + dy * 0.4f, bx - nw, yf - dy * 0.4f, bx - bs, yf)
                    lineTo(bx + bs, yf)
                    cubicTo(bx + nw, yf - dy * 0.4f, tx + nw, yb + dy * 0.4f, tx + sw, yb)
                    close()
                }
                shape.op(shape, neck, PathOperation.Union)
            }
        } else if (D1.v > 0.6f) {
            val dr = Path(); drip(dr, x + neckTx, yb, D1.v, 1f, 7f * d)
            shape.op(shape, dr, PathOperation.Union)
        }
        withTransform({ scale(1f - v * 0.5f, 1f + v, Offset(x + w / 2f, top + h / 2f)) }) {
            drawPath(shape, col)
            val body = bubblePath(x, top, w, h, rad)
            wetText(k, body, Offset(x + k.padL, top + k.padT + (h - B.height) / 2f), easeOut((w / B.width - 0.72f) / 0.28f) * easeOut((time - 0.1f) / 0.1f))
        }
        dryText(k, 1f - easeOut(time / 0.08f))
    }
}

// ---------- Droplet: one shape the whole way, a drop that spreads into the bubble ----------

internal class DropletFlight(run: MeltRun, d: Float, corner: Float) : Flight(run, d, corner) {
    private val D = 34f * d
    private val CX = FxSpring(run.textBox.left + run.textBox.width.coerceAtMost(F.width - 32f * d) / 2f, 0.34f, 0.84f)
    private val CY = FxSpring(F.center.y, 0.36f, 0.8f)
    private val W = FxSpring(max(44f * d, run.textBox.width + 20f * d), 0.16f, 0.92f)
    private val H = FxSpring(F.height * 0.72f, 0.16f, 0.92f)
    private val SX = FxSpring(1f, 0.3f, 0.42f)
    private val SY = FxSpring(1f, 0.3f, 0.42f)
    private var spread = 0f
    private var dist0 = -1f
    private var softened = false
    override fun step(dt: Float, t: Float, B: Rect) {
        time = t; lastB = B
        val tx = B.center.x; val ty = B.center.y
        if (dist0 < 0f) dist0 = hypot(tx - CX.v, ty - CY.v)
        if (!showReal) {
            if (t > 0.09f) { CX.to = tx; CY.to = ty }
            CX.step(dt); CY.step(dt)
            val dd = hypot(tx - CX.v, ty - CY.v)
            spread = if (t < 0.09f) 0f else smooth(1f - (dd - 6f * d) / max(30f * d, min(90f * d, dist0 * 0.55f)))
            W.to = mix(D, B.width, spread); H.to = mix(D, B.height, spread)
            if (spread > 0.02f && !softened) { W.tune(0.3f, 0.7f); H.tune(0.3f, 0.7f); softened = true }
            W.step(dt); H.step(dt)
            val e = 1.5f * d
            if (spread > 0.995f && abs(W.v - B.width) < e && abs(H.v - B.height) < e && abs(CX.v - tx) < e && abs(CY.v - ty) < e) {
                showReal = true; SX.vel = 1.2f; SY.vel = -1.2f
            }
        } else { SX.step(dt); SY.step(dt); realSX = SX.v; realSY = SY.v; done = SX.still(0.002f) && SY.still(0.002f) }
    }
    override fun draw(s: DrawScope, k: Look): Unit = with(s) {
        if (showReal) return@with
        val B = lastB ?: return@with
        val w = max(1f, W.v); val h = max(1f, H.v); val x = CX.v - w / 2f; val y = CY.v - h / 2f
        // A circle is a rounded box with fully round corners, so it widens straight into the bubble.
        val r = mix(min(w, h) / 2f, corner * d, smooth(spread))
        val body = bubblePath(x, y, w, h, r)
        val shape = Path().apply { addPath(body) }
        val tail = min(20f * d, max(0f, -CY.vel) / 34f) * (1f - spread) * (1f - spread)
        if (tail > 0.8f) {
            val rr = min(w, h) / 2f; val bx = CX.v; val by = y + h - rr * 0.45f; val tipX = bx - CX.vel * 0.003f; val tipY = y + h + tail
            val tp = Path().apply {
                moveTo(bx - rr * 0.72f, by)
                cubicTo(bx - rr * 0.6f, by + rr * 0.5f, tipX - rr * 0.12f, tipY - tail * 0.3f, tipX, tipY)
                cubicTo(tipX + rr * 0.12f, tipY - tail * 0.3f, bx + rr * 0.6f, by + rr * 0.5f, bx + rr * 0.72f, by)
                close()
            }
            shape.op(body, tp, PathOperation.Union)
        }
        drawPath(shape, lerpColor(k.fieldSolid, k.t.outFlat, easeOut(time / 0.1f)))
        dryText(k, 1f - easeOut(time / 0.08f))
        val a = smooth((spread - 0.55f) / 0.45f) * smooth((w / B.width - 0.75f) / 0.25f)
        wetText(k, body, Offset(x + k.padL + (w - B.width) / 2f, y + k.padT + (h - B.height) / 2f), a)
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
        val cx = B.right - 16f * d; val cy = B.bottom - 14f * d
        val x = X ?: FxSpring(run.button.center.x, 0.2f, 0.85f).also { X = it }
        val y = Y ?: FxSpring(run.button.center.y, 0.2f, 0.85f).also { Y = it; it.vel = -300f * d }
        x.to = cx; y.to = cy
        val diag = hypot(B.width, B.height)
        when (phase) {
            0 -> { x.step(dt); y.step(dt); if (hypot(x.v - cx, y.v - cy) < 4f * d) { phase = 1; Rd.to = diag * 1.05f } }
            1 -> { Rd.step(dt); if (Rd.v > diag * 0.98f) { phase = 2; landT = t; showReal = true; SX.vel = 0.35f; SY.vel = 0.35f } }
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
                    val cx = B.right - 16f * d; val cy = B.bottom - 14f * d; val r = Rd.v
                    val wave = Path()
                    for (i in 0..48) {
                        val a = i / 48f * 2f * PI.toFloat(); val ww = 1f + 0.05f * sin(a * 6f + time * 18f)
                        val px = cx + cos(a) * r * ww; val py = cy + sin(a) * r * ww
                        if (i == 0) wave.moveTo(px, py) else wave.lineTo(px, py)
                    }
                    wave.close()
                    drawPath(wave, out)
                }
                wetText(k, body, Offset(B.left + k.padL, B.top + k.padT), easeOut((Rd.v / hypot(B.width, B.height) - 0.6f) / 0.4f))
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

