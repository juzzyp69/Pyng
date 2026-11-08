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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The "drip and bounce" send. When you tap send, the send button pours left across the message
 * box so the two become one drop of liquid with your words inside. That drop glides up into the
 * new bubble on one soft spring, shrinking to fit, while a few drips trail behind and get pulled
 * back in. It lands with a quick squish, the real bubble takes over, and the send button pops back.
 *
 * The liquid is a set of simple shapes blended together and traced into one smooth outline every
 * frame. Your words are drawn twice, clipped to inside and outside that outline, so they turn
 * white exactly where the liquid covers them and never disappear.
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
    var btnScale by mutableFloatStateOf(1f)
    var btnAlpha by mutableFloatStateOf(1f)

    /** The new bubble stays hidden until the liquid lands on it. */
    fun alphaFor(m: Msg): Float = if (m.tmpId.isNotEmpty() && m.tmpId == hideTmp) bubbleAlpha else 1f
    fun isTarget(m: Msg) = m.tmpId.isNotEmpty() && m.tmpId == run?.tmpId

    fun start(r: MeltRun) {
        bubble = null
        bubbleAlpha = 0f
        hideTmp = r.tmpId
        run = r
    }

    fun finish() {
        run = null
        hideTmp = null
        bubbleAlpha = 1f
        btnScale = 1f
        btnAlpha = 1f
    }
}

data class MeltRun(val tmpId: String, val text: String, val field: Rect, val textBox: Rect, val button: Rect, val stamp: Long = System.nanoTime())

val LocalSendFx = compositionLocalOf<SendFx?> { null }

// ---------- physics ----------

private const val SUB = 1f / 240f // fixed steps: identical motion at 60, 90 or 120 Hz

private class Sp(var v: Float, var k: Float, var c: Float, val eps: Float) {
    var to = v
    var vel = 0f
    fun step(dt: Float) { val a = -k * (v - to) - c * vel; vel += a * dt; v += vel * dt }
    val still get() = abs(v - to) < eps && abs(vel) < eps * 20f
}

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** One blob of liquid: a circle (hh = 0) or a rounded rectangle. [k] is how gooey its join to the rest is. */
private class Blob(val x: Float, val y: Float, val hw: Float, val hh: Float, val r: Float, val k: Float, val circle: Boolean)

private class Bead(val r: Float, val rate: Float, val hold: Float, val fade: Float, val k: Float) { var x = 0f; var y = 0f }

private class Melt(val run: MeltRun, val d: Float) {
    val F = run.field
    val fy = F.center.y
    val btnR = run.button.width / 2f
    val r0 = run.button.right
    var phase = 0
    var t1 = 0f
    var landed = false
    val L = Sp(run.button.left, 2000f, 88f, 0.6f)
    lateinit var lx: Sp; lateinit var rx: Sp; lateinit var cy: Sp; lateinit var hh: Sp
    val sx = Sp(1f, 560f, 22f, 0.002f); val sy = Sp(1f, 560f, 22f, 0.002f)
    var pudX = 0f
    lateinit var pudW: Sp; lateinit var pudH: Sp
    val beads = listOf(Bead(8f * d, 64f, 0.05f, 0.09f, 22f * d), Bead(6f * d, 52f, 0.07f, 0.09f, 22f * d), Bead(4.5f * d, 42f, 0.09f, 0.09f, 22f * d))
    val bs = Sp(1f, 600f, 30f, 0.002f)
    var bo = 1f
    var done = false

    // what gets drawn
    val blobs = ArrayList<Blob>()
    var tx = run.textBox.left
    var ty = 0f
    var scaleX = 1f
    var scaleY = 1f

    private fun pill(x0: Float, x1: Float, y: Float, h2: Float, k: Float = 18f * d): Blob {
        val hw = max((x1 - x0) / 2f, h2)
        return Blob((x0 + x1) / 2f, y, hw, h2, min(h2, hw), k, false)
    }

    /** Advances one fixed step. [B] is where the new bubble is right now (null until it's laid out). */
    fun step(dt: Float, t: Float, B: Rect?, padL: Float, padT: Float, textH: Float) {
        blobs.clear()
        if (phase == 0) {
            // The send button pours left across the message box.
            L.to = F.left; L.step(dt)
            blobs += pill(L.v, r0, fy, F.height / 2f)
            tx = run.textBox.left; ty = fy - textH / 2f
            if (abs(L.v - F.left) < 2.5f * d && B != null) {
                phase = 1; t1 = t
                lx = Sp(L.v, 420f, 34f, 0.6f).also { it.to = B.left }
                rx = Sp(r0, 460f, 36f, 0.6f).also { it.to = B.right }
                cy = Sp(fy, 440f, 21f, 0.6f).also { it.to = B.center.y }
                hh = Sp(F.height / 2f, 560f, 40f, 0.6f).also { it.to = B.height / 2f }
                pudX = (F.left + r0) / 2f
                pudW = Sp((r0 - F.left) / 2f, 1000f, 63f, 0.6f).also { it.to = 0f }
                pudH = Sp(F.height / 2f, 950f, 62f, 0.6f).also { it.to = 0f }
                beads.forEachIndexed { i, b -> b.x = lerp(F.left + 40f * d, r0 - 30f * d, (i + 1f) / (beads.size + 1f)); b.y = fy }
                bs.v = 0.35f; bo = 0f
            }
            return
        }
        if (B != null) { lx.to = B.left; rx.to = B.right; cy.to = B.center.y; hh.to = B.height / 2f }
        val lt = t - t1
        lx.step(dt); rx.step(dt); cy.step(dt); hh.step(dt); sx.step(dt); sy.step(dt); pudW.step(dt); pudH.step(dt)
        // A quick squish on landing.
        if (!landed && cy.vel > 0f && lt > 0.05f) { landed = true; sx.vel = 1.7f; sy.vel = -1.7f }
        val span = (fy - cy.to).let { if (abs(it) < 1f) 1f else it }
        val prog = ((fy - cy.v) / span).coerceIn(0f, 1f)
        val st = 1f + min(0.2f, abs(cy.vel) / (3000f * d))
        val cx = (lx.v + rx.v) / 2f
        val half = (rx.v - lx.v) / 2f / sqrt(st) * sx.v
        val h2 = hh.v * st * sy.v
        blobs += pill(cx - half, cx + half, cy.v, h2)
        // What's left in the box drains up toward the bubble.
        pudX += (cx - pudX) * (1f - exp(-dt * 18f))
        val pudLive = pudH.v > 4.5f * d && pudW.v > 10f * d
        if (pudLive) blobs += Blob(pudX, fy, pudW.v, pudH.v, min(pudH.v, pudW.v), 30f * d, false)
        // Drips: each one chases the one ahead, then is absorbed.
        var trailing = 0
        var px = cx + half * 0.25f; var py = cy.v + h2 * 0.65f
        for (b in beads) {
            val a = 1f - exp(-dt * b.rate)
            b.x += (px - b.x) * a; b.y += (py - b.y) * a
            val r = b.r * (1f - smooth((lt - b.hold) / b.fade))
            if (r > 1.2f * d) { blobs += Blob(b.x, b.y, r, 0f, r, b.k, true); trailing++ }
            px = b.x; py = b.y
        }
        // A fresh send button pops back in once the drop has left the box.
        if (lt > 0.08f) { bs.to = 1f; bs.step(dt); bo = min(1f, bo + dt / 0.12f) }
        tx = lx.v + lerp(run.textBox.left - F.left, padL, smooth(prog))
        ty = cy.v - (B?.height ?: (textH + padT * 2)) / 2f + padT
        scaleX = 1f + (sx.v - 1f) * 0.5f
        scaleY = 1f + (sy.v - 1f) * 0.5f
        done = lx.still && rx.still && cy.still && hh.still && sx.still && sy.still && bs.still && bo >= 1f && !pudLive && trailing == 0
    }
}

// ---------- tracing the liquid's outline ----------

private fun sdf(b: Blob, x: Float, y: Float): Float {
    val dx = x - b.x; val dy = y - b.y
    if (b.circle) return sqrt(dx * dx + dy * dy) - b.r
    val qx = abs(dx) - (b.hw - b.r); val qy = abs(dy) - (b.hh - b.r)
    val ox = max(qx, 0f); val oy = max(qy, 0f)
    return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - b.r
}

private fun smin(a: Float, b: Float, k: Float): Float {
    if (k <= 0f) return min(a, b)
    val h = k - abs(a - b)
    if (h <= 0f) return min(a, b)
    val hk = h / k
    return min(a, b) - hk * hk * k * 0.25f
}

/** Blends the blobs into one surface and traces it as a smooth closed outline (marching squares). */
private fun outline(blobs: List<Blob>, step: Float): Path? {
    val live = blobs.filter { it.hw > 0.3f && (it.circle || it.hh > 0.3f) }
    if (live.isEmpty()) return null
    var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var km = 0f
    for (b in live) {
        val ex = if (b.circle) b.r else b.hw; val ey = if (b.circle) b.r else b.hh
        x0 = min(x0, b.x - ex); x1 = max(x1, b.x + ex); y0 = min(y0, b.y - ey); y1 = max(y1, b.y + ey); km = max(km, b.k)
    }
    val pad = km * 0.3f + step * 3f
    x0 = kotlin.math.floor((x0 - pad) / step) * step; y0 = kotlin.math.floor((y0 - pad) / step) * step
    x1 += pad; y1 += pad
    val nx = max(1, ((x1 - x0) / step).toInt() + 1); val ny = max(1, ((y1 - y0) / step).toInt() + 1)
    val cols = nx + 1
    val v = FloatArray(cols * (ny + 1))
    for (j in 0..ny) {
        val y = y0 + j * step
        for (i in 0..nx) {
            val x = x0 + i * step
            var dd = sdf(live[0], x, y)
            for (q in 1 until live.size) dd = smin(dd, sdf(live[q], x, y), live[q].k)
            if ((i == 0 || j == 0 || i == nx || j == ny) && dd < 0.5f) dd = 0.5f // keeps every outline closed
            v[j * cols + i] = dd
        }
    }
    fun hKey(i: Int, j: Int) = (j * cols + i) * 2
    fun vKey(i: Int, j: Int) = (j * cols + i) * 2 + 1
    fun point(key: Int): Offset {
        val base = key shr 1; val i = base % cols; val j = base / cols
        val a = v[j * cols + i]
        return if (key and 1 == 1) { val b = v[(j + 1) * cols + i]; Offset(x0 + i * step, y0 + (j + a / (a - b)) * step) }
        else { val b = v[j * cols + i + 1]; Offset(x0 + (i + a / (a - b)) * step, y0 + j * step) }
    }
    val sa = ArrayList<Int>(); val sb = ArrayList<Int>()
    val adj = HashMap<Int, MutableList<Int>>()
    fun seg(k1: Int, k2: Int) {
        val id = sa.size; sa += k1; sb += k2
        adj.getOrPut(k1) { ArrayList(2) } += id
        adj.getOrPut(k2) { ArrayList(2) } += id
    }
    for (j in 0 until ny) for (i in 0 until nx) {
        val a = v[j * cols + i]; val b = v[j * cols + i + 1]; val c = v[(j + 1) * cols + i + 1]; val dd = v[(j + 1) * cols + i]
        val id = (if (a < 0) 8 else 0) or (if (b < 0) 4 else 0) or (if (c < 0) 2 else 0) or (if (dd < 0) 1 else 0)
        if (id == 0 || id == 15) continue
        val tK = hKey(i, j); val rK = vKey(i + 1, j); val bK = hKey(i, j + 1); val lK = vKey(i, j)
        when (id) {
            1, 14 -> seg(lK, bK)
            2, 13 -> seg(bK, rK)
            3, 12 -> seg(lK, rK)
            4, 11 -> seg(tK, rK)
            6, 9 -> seg(tK, bK)
            7, 8 -> seg(tK, lK)
            else -> { val mid = (a + b + c + dd) < 0f; if ((id == 5) == mid) { seg(tK, lK); seg(bK, rK) } else { seg(tK, rK); seg(lK, bK) } }
        }
    }
    val used = BooleanArray(sa.size)
    val path = Path()
    for (s in sa.indices) {
        if (used[s]) continue
        used[s] = true
        val keys = arrayListOf(sa[s], sb[s]); var cur = sb[s]
        while (true) {
            val nxt = adj[cur]?.firstOrNull { !used[it] } ?: break
            used[nxt] = true
            cur = if (sa[nxt] == cur) sb[nxt] else sa[nxt]
            if (cur == keys[0]) break
            keys += cur
        }
        if (keys.size < 3) continue
        val ps = keys.map(::point); val m = ps.size
        path.moveTo((ps[m - 1].x + ps[0].x) / 2f, (ps[m - 1].y + ps[0].y) / 2f)
        for (i in 0 until m) {
            val p = ps[i]; val n = ps[(i + 1) % m]
            path.quadraticTo(p.x, p.y, (p.x + n.x) / 2f, (p.y + n.y) / 2f)
        }
        path.close()
    }
    return path
}

// ---------- drawing ----------

/** Draws the flying liquid over the chat. Put it last in the chat screen's root box. */
@Composable
fun SendMeltLayer(fx: SendFx, t: Tok, fontSize: Float, bold: Boolean, root: () -> LayoutCoordinates?) {
    val run = fx.run ?: return
    val density = LocalDensity.current
    val d = density.density
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize, bold) {
        TextStyle(fontFamily = Geist, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4f).sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)
    }
    var frame by remember(run.stamp) { mutableIntStateOf(0) }
    var layerAlpha by remember(run.stamp) { mutableFloatStateOf(1f) }
    val holder = remember(run.stamp) { arrayOfNulls<Any>(3) } // path, text layout, melt
    LaunchedEffect(run.stamp) {
        val melt = Melt(run, d)
        holder[2] = melt
        val padL = 16f * d; val padT = 11f * d
        var layout: TextLayoutResult = measurer.measure(AnnotatedString(run.text), style, constraints = Constraints(maxWidth = max(1, run.textBox.width.toInt())))
        var measuredFor = -1
        fun bubbleRect(): Rect? {
            val r = root() ?: return null; val b = fx.bubble ?: return null
            return runCatching { if (b.isAttached && r.isAttached) r.localBoundingBoxOf(b) else null }.getOrNull()
        }
        var last = withFrameNanos { it }
        var carry = 0f; var t = 0f
        while (true) {
            val now = withFrameNanos { it }
            carry += ((now - last) / 1e9f).coerceAtMost(0.1f); last = now
            val B = bubbleRect()
            if (B != null && measuredFor != B.width.toInt()) {
                measuredFor = B.width.toInt()
                layout = measurer.measure(AnnotatedString(run.text), style, constraints = Constraints(maxWidth = max(1, (B.width - padL * 2).toInt())))
            }
            while (carry >= SUB) { carry -= SUB; t += SUB; melt.step(SUB, t, B, padL, padT, layout.size.height.toFloat()) }
            fx.btnScale = melt.bs.v; fx.btnAlpha = melt.bo
            holder[0] = outline(melt.blobs, 2.2f * d)
            holder[1] = layout
            frame++
            // Never get stuck: if the bubble never shows up (you'd scrolled away), just finish.
            if (melt.done || t > 2.2f || (melt.phase == 0 && t > 0.5f)) break
        }
        // Hand over to the real bubble with a very short crossfade, so there's no pop.
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val k = ((now - start) / 1e9f / 0.09f).coerceIn(0f, 1f)
            layerAlpha = 1f - k
            fx.bubbleAlpha = k
            if (k >= 1f) break
        }
        fx.finish()
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = layerAlpha }) {
        if (frame < 0) return@Canvas // reading frame redraws every frame
        val path = holder[0] as? Path ?: return@Canvas
        val layout = holder[1] as? TextLayoutResult
        val melt = holder[2] as? Melt ?: return@Canvas
        drawPath(path, t.outFlat)
        if (layout != null) {
            val tl = Offset(melt.tx, melt.ty)
            val pivot = tl + Offset(layout.size.width / 2f, layout.size.height / 2f)
            scale(melt.scaleX, melt.scaleY, pivot) {
                clipPath(path) { drawText(layout, color = t.outInk, topLeft = tl) }
                clipPath(path, ClipOp.Difference) { drawText(layout, color = t.ink, topLeft = tl) }
            }
        }
    }
}
