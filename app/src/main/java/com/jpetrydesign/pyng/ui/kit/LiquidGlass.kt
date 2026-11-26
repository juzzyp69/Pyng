package com.jpetrydesign.pyng.ui.kit

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.jpetrydesign.pyng.ui.theme.LocalTok
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.min

/*
 * Liquid Glass that actually bends light. A screen's content is kept as a layer (see [glassSource]);
 * each glass surface draws its own patch of that layer through a lens: lightly blurred and a little more
 * vivid, then pulled inward near the edges the way a thick, rounded piece of glass refracts, with a hint
 * of color splitting right at the rim. A thin lit edge sits on top. Android 13 and up; older phones keep
 * the frosted look underneath.
 */

/** What a screen last drew, kept so glass on top of it can bend it. */
class GlassBackdrop {
    var layer: GraphicsLayer? = null
    var origin by mutableStateOf(Offset.Zero)
    /** True while the screen itself is drawing, so glass inside it never tries to draw itself. */
    var drawing = false
}

private val backdrops = java.util.WeakHashMap<HazeState, GlassBackdrop>()

fun backdropOf(hz: HazeState): GlassBackdrop = synchronized(backdrops) { backdrops.getOrPut(hz) { GlassBackdrop() } }

private val lensReady get() = Build.VERSION.SDK_INT >= 33

/** Marks what glass on this screen sits over: blurs it for Frost, and keeps it for Liquid Glass to bend. */
fun Modifier.glassSource(hz: HazeState): Modifier = this.hazeSource(hz).then(
    Modifier.composed {
        val t = LocalTok.current
        if (!LocalGlass.current || t.solid || !lensReady || "lens" in DebugFlags.off) return@composed Modifier
        val layer = rememberGraphicsLayer()
        val bd = remember(hz) { backdropOf(hz) }
        DisposableEffect(bd, layer) { bd.layer = layer; onDispose { if (bd.layer === layer) bd.layer = null } }
        Modifier.onGloballyPositioned { bd.origin = it.positionInRoot() }
            .drawWithContent {
                bd.drawing = true
                try { layer.record { this@drawWithContent.drawContent() } } finally { bd.drawing = false }
                drawLayer(layer)
            }
    },
)

// A rounded rectangle's distance field; inside is negative. The lens pulls the picture inward near the
// rim following a circular profile, like looking through the curved edge of a thick slab.
private const val LENS = """
uniform shader content;
uniform float2 origin;
uniform float2 rsize;
uniform float radius;
uniform float bend;
uniform float amount;
uniform float split;

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 hb = rsize * 0.5;
    float2 p = coord - (origin + hb);
    float d = sdBox(p, hb, radius);
    float depth = -d;
    if (depth <= 0.0 || depth >= bend) return content.eval(coord);
    float2 n = normalize(float2(
        sdBox(p + float2(1.0, 0.0), hb, radius) - sdBox(p - float2(1.0, 0.0), hb, radius),
        sdBox(p + float2(0.0, 1.0), hb, radius) - sdBox(p - float2(0.0, 1.0), hb, radius)) + float2(0.0001));
    float x = 1.0 - depth / bend;
    float k = 1.0 - sqrt(max(1.0 - x * x, 0.0));
    float2 at = coord - n * k * amount;
    if (split <= 0.0) return content.eval(at);
    float2 s = n * k * split;
    half4 g = content.eval(at);
    return half4(content.eval(at + s).r, g.g, content.eval(at - s).b, g.a);
}
"""

@RequiresApi(33)
private class Lens {
    val shader = RuntimeShader(LENS)
    var key = ""
    var effect: RenderEffect? = null
}

/** The rim radius, and whether this is a bar along the top or bottom of the screen (so only one edge is glass). */
private fun DrawScope.edges(shape: Shape): Triple<Float, Boolean, Boolean> {
    val rc = shape as? RoundedCornerShape ?: return Triple(min(size.width, size.height) / 2f, false, false)
    val tl = rc.topStart.toPx(size, this)
    val bl = rc.bottomStart.toPx(size, this)
    val topBar = tl < 0.5f && bl > 0.5f
    val bottomBar = bl < 0.5f && tl > 0.5f
    return Triple(if (topBar) bl else tl, topBar, bottomBar)
}

/**
 * Draws the screen behind through a lens, under this surface's tint. Put it after a clip to the same shape.
 * Draws nothing if there's nothing to bend (older Android, or no screen layer yet), leaving the frost beneath.
 */
fun Modifier.liquidLens(hz: HazeState, shape: Shape, tint: Color, blur: androidx.compose.ui.unit.Dp): Modifier =
    if (!lensReady || "lens" in DebugFlags.off) this else composed {
        val bd = remember(hz) { backdropOf(hz) }
        val gfx = rememberGraphicsLayer()
        val lens = remember { if (Build.VERSION.SDK_INT >= 33) Lens() else null }
        var me by remember { mutableStateOf(Offset.Zero) }
        this.onGloballyPositioned { me = it.positionInRoot() }.drawBehind {
            val src = bd.layer
            if (src == null || bd.drawing || lens == null || size.minDimension <= 1f) return@drawBehind
            drawLensed(lens, gfx, src, bd.origin - me, shape, blur.toPx())
            drawRect(tint)
        }
    }

@RequiresApi(33)
private fun DrawScope.drawLensed(lens: Lens, gfx: GraphicsLayer, src: GraphicsLayer, at: Offset, shape: Shape, blurPx: Float) {
    val (r, topBar, bottomBar) = edges(shape)
    val bar = topBar || bottomBar
    // A bar's far edges run off screen, so only its inner edge bends.
    val far = 4000f
    val ox = if (bar) -far else 0f
    val oy = if (topBar) -far else 0f
    val w = if (bar) size.width + far * 2 else size.width
    val h = if (bar) size.height + far else size.height
    val bend = min(r.coerceAtLeast(8.dp.toPx()), 24.dp.toPx()).coerceAtMost(min(size.width, size.height) / 2f)
    // Mostly blur: the edge only bends a little, with no color splitting.
    val amount = bend * 0.3f
    val split = 0f
    val key = "$ox,$oy,$w,$h,$r,$bend,$blurPx"
    if (key != lens.key || lens.effect == null) {
        lens.shader.setFloatUniform("origin", ox, oy)
        lens.shader.setFloatUniform("rsize", w, h)
        lens.shader.setFloatUniform("radius", r.coerceAtMost(min(w, h) / 2f))
        lens.shader.setFloatUniform("bend", bend)
        lens.shader.setFloatUniform("amount", amount)
        lens.shader.setFloatUniform("split", split)
        // Blur first, a touch more color, then bend.
        val vivid = ColorMatrix().apply { setSaturation(1.2f) }
        var e: RenderEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(vivid))
        if (blurPx > 0.5f) e = RenderEffect.createChainEffect(e, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
        e = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(lens.shader, "content"), e)
        lens.effect = e
        lens.key = key
        gfx.renderEffect = e.asComposeRenderEffect()
    }
    gfx.record(size.toIntSize()) { translate(at.x, at.y) { drawLayer(src) } }
    drawLayer(gfx)
}

private fun outlinePath(o: Outline): Path = when (o) {
    is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(o.rect) }
    is Outline.Generic -> o.path
}

/**
 * Liquid Glass's edge: kept simple, like frosted glass catching a little light. A faint sheen falls from
 * the top left, and a hairline is brighter along the top than the bottom. Bars along the top or bottom of
 * the screen only show the edge you can see.
 */
fun Modifier.liquidShine(shape: Shape): Modifier = if ("shine" in DebugFlags.off) this else composed {
    val t = LocalTok.current
    val sheen = Color.White.copy(alpha = if (t.dark) 0.05f else 0.22f)
    val top = Color.White.copy(alpha = if (t.dark) 0.14f else 0.7f)
    val bottom = Color.White.copy(alpha = if (t.dark) 0.03f else 0.25f)
    this.drawWithContent {
        val (r, topBar, bottomBar) = edges(shape)
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = outlinePath(outline)
        // The reflection, under the content.
        if (!topBar && !bottomBar) drawPath(path, Brush.linearGradient(0f to sheen, 0.55f to Color.Transparent, start = Offset.Zero, end = Offset(size.width * 0.7f, size.height)))
        drawContent()
        val line = 1.dp.toPx()
        val draw: DrawScope.() -> Unit = {
            clipPath(path) { drawPath(path, Brush.verticalGradient(listOf(top, bottom)), style = Stroke(line * 2)) }
        }
        when {
            topBar -> clipRect(top = size.height - r - line * 2) { clipPath(path) { drawPath(path, bottom.copy(alpha = bottom.alpha * 2f), style = Stroke(line * 2)) } }
            bottomBar -> clipRect(bottom = r + line * 2) { clipPath(path) { drawPath(path, top, style = Stroke(line * 2)) } }
            else -> draw()
        }
    }
}
