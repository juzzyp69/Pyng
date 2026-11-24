package com.jpetrydesign.pyng.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * How things move around the app. Rise, Pour and Drop are the liquid styles: new surfaces grow out of
 * what you touched. Simple fades and slides a little. Minimal is a quick fade, and it's what you get
 * when Reduce motion or Android's Remove animations is on.
 */
enum class Mo {
    Rise, Pour, Drop, Simple, Minimal;

    val liquid get() = this == Rise || this == Pour || this == Drop

    companion object {
        /** Three choices now: Liquid (the old Rise, Pour and Drop all become it), Simple and Reduced. */
        fun of(s: String): Mo = when (s) {
            "Simple" -> Simple
            "Reduced", "Minimal" -> Minimal
            else -> Rise
        }
        val choices = listOf("Liquid", "Simple", "Reduced")
        /** The saved setting as one of the three names. */
        fun name(s: String): String = when (of(s)) { Simple -> "Simple"; Minimal -> "Reduced"; else -> "Liquid" }
        val about = mapOf(
            "Liquid" to "Things pour out of what you tap and settle",
            "Simple" to "Short slides and fades",
            "Reduced" to "Quick fades, nothing moves",
        )
    }
}

val LocalMo = compositionLocalOf { Mo.Rise }

/** A rounded box in the liquid's own units (dp). */
data class LRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2

    companion object {
        /** From a rect in pixels, made relative to [origin] (also pixels). */
        fun of(px: Rect, radiusPx: Float, density: Float, origin: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero) =
            LRect((px.left - origin.x) / density, (px.top - origin.y) / density, px.width / density, px.height / density, radiusPx / density)
    }
}

internal fun cl(v: Float, a: Float, b: Float) = max(a, min(b, v))
internal fun smooth(t0: Float): Float { val t = cl(t0, 0f, 1f); return t * t * (3 - 2 * t) }

/** A plain spring, stepped in small slices so it stays steady at any frame rate. */
internal class Sp(var x: Float, var t: Float, var st: Float, var dm: Float) {
    var v = 0f
    fun step(dt: Float) { val a = -st * (x - t) - dm * v; v += a * dt; x += v * dt }
    val done get() = abs(x - t) < 0.2f && abs(v) < 3f
}

internal fun allDone(vararg s: Sp) = s.all { it.done }

internal data class P(val x: Float, val y: Float)

/** Builds liquid outlines in dp, writing them into [path] in pixels. Overlapping parts join into one shape. */
class LP(val path: Path, private val k: Float) {
    private fun corner(l: Float, t: Float, r: Float, start: Float) {
        if (r < 0.01f) return
        path.arcTo(Rect(l, t, l + 2 * r, t + 2 * r), start, 90f, false)
    }

    /** A rounded box whose sides can bulge out a little (top, right, bottom, left). */
    fun rr(x0: Float, y0: Float, w0: Float, h0: Float, r0: Float, bt: Float = 0f, br: Float = 0f, bb: Float = 0f, bl: Float = 0f) {
        val w = max(w0, 0.01f) * k; val h = max(h0, 0.01f) * k
        val x = x0 * k; val y = y0 * k
        val r = max(0f, min(r0 * k, min(w / 2, h / 2)))
        path.moveTo(x + r, y)
        path.quadraticTo(x + w / 2, y - bt * k * 2, x + w - r, y)
        corner(x + w - 2 * r, y, r, -90f)
        path.quadraticTo(x + w + br * k * 2, y + h / 2, x + w, y + h - r)
        corner(x + w - 2 * r, y + h - 2 * r, r, 0f)
        path.quadraticTo(x + w / 2, y + h + bb * k * 2, x + r, y + h)
        corner(x, y + h - 2 * r, r, 90f)
        path.quadraticTo(x - bl * k * 2, y + h / 2, x, y + r)
        corner(x, y, r, 180f)
        path.close()
    }

    fun circle(cx: Float, cy: Float, r: Float) { if (r >= 0.3f) rr(cx - r, cy - r, 2 * r, 2 * r, r) }

    /** A liquid neck between two points: [wa] wide where it starts, [wm] in the middle, [wb] where it ends. */
    fun neck(ax: Float, ay: Float, bx: Float, by: Float, wa: Float, wm: Float, wb: Float) {
        val dx = bx - ax; val dy = by - ay; val len = hypot(dx, dy)
        if (len < 0.5f || wm < 0.25f) return
        var nx = -dy / len; var ny = dx / len
        fun side(px: Float, py: Float, s: Float, w: Float) = P(px + nx * w / 2 * s, py + ny * w / 2 * s)
        val a1 = side(ax, ay, 1f, wa); val b1 = side(bx, by, 1f, wb); val b2 = side(bx, by, -1f, wb); val a2 = side(ax, ay, -1f, wa)
        val area = (a1.x * b1.y - b1.x * a1.y) + (b1.x * b2.y - b2.x * b1.y) + (b2.x * a2.y - a2.x * b2.y) + (a2.x * a1.y - a1.x * a2.y)
        if (area < 0) { nx = -nx; ny = -ny }
        fun at(t: Float, s: Float, w: Float) = P((ax + dx * t + nx * w / 2 * s) * k, (ay + dy * t + ny * w / 2 * s) * k)
        val A1 = at(0f, 1f, wa); val C1 = at(.28f, 1f, wa * .92f); val C2 = at(.38f, 1f, wm); val M1 = at(.5f, 1f, wm)
        val C3 = at(.62f, 1f, wm); val C4 = at(.72f, 1f, wb * .92f); val B1 = at(1f, 1f, wb)
        val B2 = at(1f, -1f, wb); val D4 = at(.72f, -1f, wb * .92f); val D3 = at(.62f, -1f, wm); val M2 = at(.5f, -1f, wm)
        val D2 = at(.38f, -1f, wm); val D1 = at(.28f, -1f, wa * .92f); val A2 = at(0f, -1f, wa)
        path.moveTo(A1.x, A1.y)
        path.cubicTo(C1.x, C1.y, C2.x, C2.y, M1.x, M1.y)
        path.cubicTo(C3.x, C3.y, C4.x, C4.y, B1.x, B1.y)
        path.lineTo(B2.x, B2.y)
        path.cubicTo(D4.x, D4.y, D3.x, D3.y, M2.x, M2.y)
        path.cubicTo(D2.x, D2.y, D1.x, D1.y, A2.x, A2.y)
        path.close()
    }
}

/** Where a ray from the middle of [r] along (ux, uy) leaves it, pulled in by [inset]. */
internal fun edge(r: LRect, ux: Float, uy: Float, inset: Float): P {
    val tx = if (abs(ux) < 1e-6f) 1e9f else (r.w / 2) / abs(ux)
    val ty = if (abs(uy) < 1e-6f) 1e9f else (r.h / 2) / abs(uy)
    val t = max(0f, min(tx, ty) - inset)
    return P(r.cx + ux * t, r.cy + uy * t)
}

/** One running liquid motion. Each frame it steps its springs, then draws its outline. */
abstract class LiquidSim {
    var e = 0f
    var onReveal: (() -> Unit)? = null
    /** How formed the shape is (0 to 1) when its content starts to show. Big surfaces start a bit sooner. */
    var revealAt = 0.72f
    private var revealed = false
    protected fun reveal(p: Float, rev: Boolean) { if (!rev && !revealed && p > revealAt) { revealed = true; onReveal?.invoke() } }
    abstract fun step(dt: Float)
    abstract fun draw(lp: LP)
    abstract fun finished(): Boolean
    /** How full the shape is, 0 to 1, for things that follow along (like the flying photo). */
    open val fill: Float get() = 0f
}

/**
 * A new shape grows out of [s] into [t] and pinches off. With [rev] it's pulled back into [s] instead.
 * Each liquid style has its own way of getting there.
 */
fun budSim(s: LRect, t: LRect, rev: Boolean, style: Mo): LiquidSim {
    val sc = P(s.cx, s.cy); val tc = P(t.cx, t.cy)
    val dx0 = tc.x - sc.x; val dy0 = tc.y - sc.y
    val l0 = hypot(dx0, dy0).let { if (it == 0f) 1f else it }
    val ux = dx0 / l0; val uy = dy0 / l0
    val pa = edge(s, ux, uy, min(6f, min(s.w, s.h) * .25f))
    val d0 = cl(min(s.w, s.h) * .6f, 10f, 30f)
    val dist = hypot(tc.x - pa.x, tc.y - pa.y)
    val pillT = t.r >= min(t.w, t.h) / 2 - 1
    fun shapeR(w: Float, h: Float, p: Float) = if (pillT) min(w, h) / 2 else min(max(t.r, min(w, h) / 2 * (1 - smooth(p))), min(w / 2, h / 2))
    fun tEdge(x: Float, y: Float, w: Float, h: Float) = edge(LRect(x - w / 2, y - h / 2, w, h, 0f), -ux, -uy, min(4f, min(w, h) * .25f))
    fun LP.swell(k: Float) { if (k > .02f) circle(pa.x + ux * d0 * .16f * k, pa.y + uy * d0 * .16f * k, d0 * .42f * k) }

    return when (style) {
        Mo.Pour -> object : LiquidSim() {
            val X = Sp(if (rev) tc.x else pa.x, if (rev) pa.x else tc.x, if (rev) 420f else 470f, if (rev) 36f else 33f)
            val Y = Sp(if (rev) tc.y else pa.y, if (rev) pa.y else tc.y, if (rev) 420f else 470f, if (rev) 36f else 33f)
            val W = Sp(if (rev) t.w else d0, if (rev) d0 * .8f else d0, if (rev) 480f else 330f, if (rev) 40f else 29f)
            val H = Sp(if (rev) t.h else d0, if (rev) d0 * .8f else d0, if (rev) 480f else 330f, if (rev) 40f else 29f)
            val tX = Sp(pa.x, pa.x, 250f, 30f); val tY = Sp(pa.y, pa.y, 250f, 30f)
            var grown = rev; var drain = false; var gone = false
            override val fill get() = if (rev) 1f - min(1f, hypot(X.x - tc.x, Y.x - tc.y) / (dist.coerceAtLeast(1f))) else min(W.x / t.w, H.x / t.h).coerceIn(0f, 1f)
            override fun step(dt: Float) {
                X.step(dt); Y.step(dt); W.step(dt); H.step(dt)
                if (!rev) {
                    val prog = 1 - hypot(tc.x - X.x, tc.y - Y.x) / (if (dist == 0f) 1f else dist)
                    if (!grown && prog > .45f) { grown = true; W.t = t.w; H.t = t.h }
                    if (!drain && e > .07f) { drain = true; tX.t = tc.x; tY.t = tc.y }
                    if (drain) { tX.step(dt); tY.step(dt) }
                } else if (!gone && hypot(X.x - pa.x, Y.x - pa.y) < d0 * .5f) {
                    gone = true; W.t = 0f; H.t = 0f; W.st = 700f; H.st = 700f; W.dm = 50f; H.dm = 50f
                }
            }
            override fun draw(lp: LP) {
                val w = max(0f, W.x); val h = max(0f, H.x); val x = X.x; val y = Y.x
                if (w < .5f || h < .5f) return
                val p = min(w / t.w, h / t.h); reveal(p, rev)
                val vx = X.v; val vy = Y.v; val sp = hypot(vx, vy)
                var bt = 0f; var br = 0f; var bb = 0f; var bl = 0f
                if (sp > 1f) {
                    val amt = cl(sp * .012f, 0f, min(w, h) * .16f)
                    if (abs(vx) > abs(vy)) { if (vx > 0) br = amt else bl = amt } else { if (vy > 0) bb = amt else bt = amt }
                }
                lp.rr(x - w / 2, y - h / 2, w, h, shapeR(w, h, p), bt, br, bb, bl)
                val pb = tEdge(x, y, w, h)
                if (!rev) {
                    val tx = tX.x; val ty = tY.x
                    val along = (tx - pa.x) * ux + (ty - pa.y) * uy
                    val left = (pb.x - pa.x) * ux + (pb.y - pa.y) * uy
                    if (along < left - 1) {
                        val sw = d0 * .72f
                        lp.neck(tx, ty, pb.x, pb.y, sw * (if (along < 2) 1.15f else .9f), sw * .8f, min(min(w, h), sw * 1.6f))
                        if (along < 4) lp.swell(.7f) else lp.circle(tx, ty, sw * .45f)
                    }
                } else {
                    val k = 1 - min(1f, hypot(pb.x - pa.x, pb.y - pa.y) / 80f)
                    if (k > 0) { lp.swell(k * .7f); lp.neck(pa.x, pa.y, pb.x, pb.y, d0 * .9f, d0 * .7f * smooth(k), min(w, h) * .9f) }
                }
            }
            override fun finished() = if (rev) gone && W.x < .6f else grown && drain && allDone(X, Y, W, H, tX, tY)
        }

        Mo.Drop -> object : LiquidSim() {
            val r0 = d0 * .5f
            val ex = edge(t, -ux, -uy, r0)
            val endP = if (rev) ex else tc
            val arc = min(22f, dist * .16f) * (if (ux >= 0) 1f else -1f)
            val q = Sp(if (rev) 1f else 0f, if (rev) 0f else 1f, 360f, 36f)
            fun pos(k: Float) = P(pa.x + (endP.x - pa.x) * k + (-uy) * arc * sin(PI.toFloat() * k), pa.y + (endP.y - pa.y) * k + ux * arc * sin(PI.toFloat() * k))
            val budT = .08f
            var phase = if (rev) "gather" else "bud"
            // Each side of the shape on its own spring: left, top, right, bottom.
            var E: Array<Sp>? = if (rev) arrayOf(Sp(t.x, ex.x - r0, 430f, 40f), Sp(t.y, ex.y - r0, 430f, 40f), Sp(t.x + t.w, ex.x + r0, 430f, 40f), Sp(t.y + t.h, ex.y + r0, 430f, 40f)) else null
            var prev: P? = null; var vel = P(0f, 0f); var land: P? = null; var formE = 0f; var mergeK = 0f
            override val fill get() = when (phase) { "form" -> E?.let { min((it[2].x - it[0].x) / t.w, (it[3].x - it[1].x) / t.h).coerceIn(0f, 1f) } ?: 0f; "gather" -> 1f; else -> if (rev) q.x * .5f else 0f }
            fun headR() = r0 * (1 + (if (!rev && q.x > .5f) smooth((q.x - .5f) / .3f) * .3f else 0f))
            fun LP.teardrop(p: P, r: Float) {
                prev?.let { vel = P(p.x - it.x, p.y - it.y) }; prev = p
                val sp = abs(q.v) * dist
                val len = cl(sp * .032f, 0f, r * 1.7f)
                val vm = hypot(vel.x, vel.y)
                val vx = if (sp > 0 && vm > 0) vel.x / vm else 0f; val vy = if (sp > 0 && vm > 0) vel.y / vm else 0f
                val tx = p.x - vx * len; val ty = p.y - vy * len
                circle(p.x, p.y, r)
                if (len > 1) { circle(tx, ty, r * .5f); neck(tx, ty, p.x, p.y, r, r * 1.3f, r * 1.95f) }
            }
            override fun step(dt: Float) {
                if (!rev) {
                    if (phase == "bud" && e > budT) phase = "fly"
                    if (phase == "fly") {
                        q.step(dt)
                        if (q.x > .8f) {
                            phase = "form"; val ld = pos(q.x); land = ld; val hr = headR()
                            E = arrayOf(Sp(ld.x - hr, t.x, 330f, 33f), Sp(ld.y - hr, t.y, 330f, 33f), Sp(ld.x + hr, t.x + t.w, 330f, 33f), Sp(ld.y + hr, t.y + t.h, 330f, 33f))
                            formE = e
                        }
                    } else if (phase == "form") E?.forEach { it.step(dt) }
                } else {
                    when (phase) {
                        "gather" -> { val es = E!!; es.forEach { it.step(dt) }; if (es[2].x - es[0].x < r0 * 2.2f && es[3].x - es[1].x < r0 * 2.2f) phase = "fly" }
                        "fly" -> { q.step(dt); if (q.x < .05f) phase = "merge" }
                        else -> mergeK = min(1f, mergeK + dt / .11f)
                    }
                }
            }
            override fun draw(lp: LP) {
                if (!rev) {
                    if (phase == "bud") {
                        val k = e / budT; val r = r0 * smooth(k); val c = P(pa.x + ux * r, pa.y + uy * r)
                        lp.swell(1 - k); lp.circle(c.x, c.y, r); lp.neck(pa.x, pa.y, c.x, c.y, d0 * .85f, d0 * .55f * (1 - k), r * 1.7f)
                        return
                    }
                    if (phase == "fly") {
                        lp.teardrop(pos(q.x), headR())
                        val k = (e - budT) / .09f
                        if (k < 1) { val rb = d0 * .22f * (1 - smooth(k)); lp.circle(pa.x + ux * rb * .5f, pa.y + uy * rb * .5f, rb) }
                        return
                    }
                    // Form: the shape spreads out from where the drop landed while its tail flows in.
                    val es = E ?: return; val ld = land ?: return
                    val x = es[0].x; val y = es[1].x; val w = es[2].x - es[0].x; val h = es[3].x - es[1].x
                    val p = min(w / t.w, h / t.h); reveal(p, rev)
                    lp.rr(x, y, w, h, shapeR(w, h, p))
                    val k = (e - formE) / .12f
                    if (k < 1) {
                        val sp = hypot(vel.x, vel.y).let { if (it == 0f) 1f else it }
                        val len = r0 * 1.5f * (1 - smooth(k)); val vx = vel.x / sp; val vy = vel.y / sp
                        val tx = ld.x - vx * len; val ty = ld.y - vy * len
                        if (len > 1) { lp.circle(tx, ty, r0 * .5f * (1 - k)); lp.neck(tx, ty, ld.x, ld.y, r0 * (1 - k), r0 * 1.1f * (1 - k), r0 * 1.8f) }
                    }
                    return
                }
                if (phase == "gather") {
                    val es = E!!; val x = es[0].x; val y = es[1].x; val w = es[2].x - es[0].x; val h = es[3].x - es[1].x
                    lp.rr(x, y, w, h, shapeR(w, h, min(w / t.w, h / t.h))); return
                }
                val p = pos(q.x)
                if (phase == "fly") {
                    lp.teardrop(p, r0)
                    if (q.x < .32f) { val k = 1 - q.x / .32f; lp.swell(k * .6f); lp.neck(pa.x, pa.y, p.x, p.y, d0 * .85f, d0 * .5f * smooth(k), r0 * 1.6f) }
                    return
                }
                val r = r0 * (1 - smooth(mergeK))
                lp.swell(.6f * (1 - mergeK)); lp.circle(p.x, p.y, r); lp.neck(pa.x, pa.y, p.x, p.y, d0 * .85f, d0 * .5f * (1 - mergeK), r * 1.6f)
            }
            override fun finished() = if (rev) phase == "merge" && mergeK >= 1f else phase == "form" && E?.let { allDone(*it) } == true
        }

        // Rise, the standard: a bead swells out on a neck that thins and pinches off, then settles.
        else -> object : LiquidSim() {
            val vert = abs(uy) >= abs(ux)
            val X = Sp(if (rev) tc.x else pa.x, if (rev) pa.x else tc.x, 400f, 31f)
            val Y = Sp(if (rev) tc.y else pa.y, if (rev) pa.y else tc.y, 400f, 31f)
            val W = Sp(if (rev) t.w else d0 * (if (vert) .55f else .8f), if (rev) d0 * .6f else t.w, if (vert) 280f else 460f, if (vert) 25f else 31f)
            val H = Sp(if (rev) t.h else d0 * (if (vert) .8f else .55f), if (rev) d0 * .6f else t.h, if (vert) 470f else 280f, if (vert) 31f else 25f)
            var pinchE: Float? = null; var gone = false
            override val fill get() = min(max(0f, W.x) / t.w, max(0f, H.x) / t.h).coerceIn(0f, 1f)
            override fun step(dt: Float) {
                X.step(dt); Y.step(dt); W.step(dt); H.step(dt)
                if (rev && !gone && hypot(X.x - pa.x, Y.x - pa.y) < d0 * .55f) { gone = true; W.t = 0f; H.t = 0f; W.st = 700f; H.st = 700f; W.dm = 52f; H.dm = 52f }
            }
            override fun draw(lp: LP) {
                val w = max(0f, W.x); val h = max(0f, H.x); val x = X.x; val y = Y.x
                val p = min(w / t.w, h / t.h); reveal(p, rev)
                if (w > .5f && h > .5f) lp.rr(x - w / 2, y - h / 2, w, h, shapeR(w, h, p))
                val pb = tEdge(x, y, w, h); val nl = hypot(pb.x - pa.x, pb.y - pa.y)
                if (!rev) {
                    if (pinchE == null) {
                        val k = max(e / .17f, nl / 80f)
                        if (k >= 1) pinchE = e
                        else { lp.swell(1 - k * .6f); lp.neck(pa.x, pa.y, pb.x, pb.y, d0 * (1 - k * .3f), d0 * .6f * (1 - smooth(k)).pow(1.3f), min(min(w, h), d0 * 1.5f) * .85f) }
                    }
                    val pe = pinchE
                    if (pe != null) {
                        val k = (e - pe) / .14f
                        if (k < 1) {
                            // The two halves of the neck snap back: one into the source, one into the shape.
                            val rb = d0 * .24f * (1 - smooth(k))
                            val s1 = min(nl, 40f) * .3f * (1 - smooth(k)); val s2 = min(nl, 40f) * .24f * (1 - smooth(k))
                            lp.circle(pa.x + ux * s1, pa.y + uy * s1, rb); lp.neck(pa.x, pa.y, pa.x + ux * s1, pa.y + uy * s1, rb * 2.2f, rb * 1.5f, rb * 2f)
                            lp.circle(pb.x - ux * s2, pb.y - uy * s2, rb * .9f); lp.neck(pb.x, pb.y, pb.x - ux * s2, pb.y - uy * s2, rb * 2.2f, rb * 1.4f, rb * 1.8f)
                        }
                    }
                } else {
                    val k = 1 - min(1f, nl / 70f)
                    if (k > 0) { lp.swell(k * .8f); lp.neck(pa.x, pa.y, pb.x, pb.y, d0 * .95f, d0 * .55f * smooth(k), min(w, h) * .85f) }
                }
            }
            override fun finished() = if (rev) gone && W.x < .6f && H.x < .6f else pinchE.let { it != null && e > it + .14f } && allDone(X, Y, W, H)
        }
    }
}

/**
 * Runs liquid motions and keeps their current outlines. Draw it with [LiquidCanvas], or read [pathOf]
 * to clip something to the shape.
 */
class LiquidStage {
    class Run(val sim: LiquidSim, val fill: Color, val rim: Color?, internal val done: CompletableDeferred<Unit>) {
        var path = Path()
            internal set
    }

    internal val runs = mutableStateListOf<Run>()
    /** Bumps every frame something moves, so whatever draws the shapes redraws. */
    var tick by mutableIntStateOf(0)
        internal set
    internal var density = 1f
    val busy get() = runs.isNotEmpty()
    /** The current outline of everything playing. */
    fun paths(): List<Path> = runs.map { it.path }

    /** Plays [sim] to the end. */
    suspend fun play(sim: LiquidSim, fill: Color, rim: Color? = null) {
        val r = Run(sim, fill, rim, CompletableDeferred())
        runs.add(r)
        try { r.done.await() } finally { runs.remove(r) }
    }

    /** Steps every running motion by [dt] seconds and rebuilds its outline. */
    internal fun frame(dt: Float) {
        val over = ArrayList<Run>()
        for (r in runs) {
            var left = dt
            while (left > 0f) { val h = min(left, 1f / 240f); r.sim.step(h); r.sim.e += h; left -= h }
            val p = Path(); r.sim.draw(LP(p, density)); r.path = p
            // A motion that somehow never settles still ends, so nothing waiting on it is ever stuck.
            if (r.sim.finished() || r.sim.e > 2.5f) over.add(r)
        }
        over.forEach { it.done.complete(Unit); runs.remove(it) }
        tick++
    }
}

/** Keeps a [LiquidStage] moving while it has anything to play. Put it once, anywhere, for each stage. */
@Composable
fun LiquidDriver(stage: LiquidStage) {
    stage.density = LocalDensity.current.density
    LaunchedEffect(stage) {
        while (true) {
            snapshotFlow { stage.runs.size }.first { it > 0 }
            var last = withFrameNanos { it }
            while (stage.runs.isNotEmpty()) {
                withFrameNanos { now ->
                    // Follows real time even when frames come slowly (just in bigger steps), so a motion
                    // always finishes in about the same time on any phone.
                    val dt = min(0.1f, (now - last) / 1e9f); last = now
                    stage.frame(dt)
                }
            }
        }
    }
}

/** Draws every shape on [stage], each in its own color with an optional thin rim. */
@Composable
fun LiquidCanvas(stage: LiquidStage, modifier: Modifier = Modifier) {
    LiquidDriver(stage)
    if (!stage.busy) return
    // Drawn in its own layer so a rim can be traced around the joined shape: stroke everything,
    // clear the inside (which also wipes the seams between parts), then fill.
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        stage.tick
        for (r in stage.runs) {
            r.rim?.let { drawPath(r.path, it, style = Stroke(2.dp.toPx())); drawPath(r.path, Color.Black, blendMode = BlendMode.Clear) }
            drawPath(r.path, r.fill)
        }
    }
}

