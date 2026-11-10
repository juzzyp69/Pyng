package com.jpetrydesign.pyng.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jpetrydesign.pyng.R

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Every color the UI uses, derived from a palette the same way the prototype does. */
@Immutable
data class Tok(
    val pal: Pal,
    val dark: Boolean,
    val solid: Boolean,
    val p: Color,
    val s: Color,
    val bg: Color,
    val fill: Color,
    val onFill: Color,
    val danger: Color,
    val ink: Color,
    val soft: Color,
    val faint: Color,
    val hair: Color,
    val neutral: Color,
    val neutralInk: Color,
    val tone: Color,
    val glass: Color,
    val glassStrong: Color,
    val sheet: Color,
    val field: Color,
    val card: Color,
    val cardEdge: Color,
    val surface: Color,
    val inBg: Color,
    val inInk: Color,
    val outBg: Brush,
    val outFlat: Color,
    val outInk: Color,
    val badgeBg: Color,
    val badgeInk: Color,
    val plusBg: Brush,
    val plusFlat: Color,
    val plusInk: Color,
    val fab: Color,
    val fabP: Color,
    val edgeLine: Color,
    val shadow: Color,
    val shadowP: Color,
    val hdrBg: Color,
    val accent2: Color,
    /** Solid avatar colors for people without a photo: (background, letters), nudged toward the theme. */
    val tones: List<Pair<Color, Color>> = emptyList(),
    /** The two ends of the sent-bubble gradient: the theme's own pair, or gentle shades of its color. */
    val outTop: Color = Color.Unspecified,
    val outBottom: Color = Color.Unspecified,
) {
    /** The same person always gets the same tone. */
    fun toneFor(key: String): Pair<Color, Color> =
        if (tones.isEmpty()) neutral to neutralInk else tones[Math.floorMod(key.hashCode(), tones.size)]
    val dot get() = Brush.linearGradient(listOf(p, pal.plus ?: pal.s))
    fun swatch(): Brush = swatchOf(pal)
}

fun swatchOf(x: Pal): Brush {
    val second = x.plus ?: x.bg ?: x.s
    return Brush.linearGradient(0f to x.p, 0.5f to x.p, 0.5f to second, 1f to second)
}

fun tok(P: Pal, dark: Boolean, solid: Boolean = false, highContrast: Boolean = false): Tok {
    val p = if (dark) P.pd else (P.pt ?: P.p)
    val bg = if (dark) (P.bgd ?: hex("#0D0F14")) else (P.bg ?: Color.White)
    val fill = if (dark) P.pd else P.p
    val onFill = if (dark) hex("#101218") else (P.onP ?: Color.White)
    val plus = P.plus ?: fill
    val inkD = hex("#F1F2F6"); val inkL = hex("#15171D")
    val outBrush: Brush = P.grad?.let { Brush.linearGradient(listOf(it.first, it.second)) } ?: Brush.linearGradient(listOf(fill, fill))
    return Tok(
        pal = P, dark = dark, solid = solid,
        p = p, s = P.s, bg = bg, fill = fill, onFill = onFill,
        danger = if (dark) hex("#FF7A70") else hex("#C8372D"),
        ink = if (dark) inkD else inkL,
        soft = if (dark) inkD.copy(alpha = if (highContrast) 0.84f else 0.66f) else inkL.copy(alpha = if (highContrast) 0.8f else 0.63f),
        faint = if (dark) inkD.copy(alpha = if (highContrast) 0.58f else 0.42f) else inkL.copy(alpha = if (highContrast) 0.55f else 0.4f),
        hair = if (dark) Color.White.copy(alpha = if (highContrast) 0.18f else 0.07f) else inkL.copy(alpha = if (highContrast) 0.18f else 0.07f),
        // Solid, never see-through: a shadow behind an avatar must not show through it.
        neutral = (if (dark) Color.White.copy(alpha = 0.08f) else inkL.copy(alpha = 0.05f)).compositeOver(bg),
        neutralInk = if (dark) inkD.copy(alpha = 0.82f) else inkL.copy(alpha = 0.72f),
        tone = p.a(if (dark) 0x2E else 0x1F).compositeOver(bg),
        glass = if (solid) (if (dark) hex("#22252D") else if (P.bg != null) Color.White else hex("#EEF1F7")) else (if (dark) Color.White.copy(alpha = 0.08f) else p.a(0x14)),
        glassStrong = if (solid) (if (dark) hex("#1C1F26") else Color.White) else (if (dark) hex("#1E2028").copy(alpha = 0.72f) else Color.White.copy(alpha = 0.7f)),
        sheet = if (solid) (if (dark) hex("#181A21") else Color.White) else (if (dark) hex("#181A21").copy(alpha = 0.97f) else Color.White.copy(alpha = 0.98f)),
        field = if (dark) Color.White.copy(alpha = if (highContrast) 0.16f else 0.09f) else p.a(if (highContrast) 0x26 else 0x14),
        card = if (dark) Color.White.copy(alpha = 0.05f) else p.a(0x0D),
        cardEdge = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.7f),
        surface = if (dark) Color.White.copy(alpha = 0.05f) else inkL.copy(alpha = 0.035f),
        // Received bubbles in light mode: nearly white with just a hint of the theme color.
        inBg = if (dark) Color.White.copy(alpha = 0.10f) else (if (P.bg != null) Color.White.copy(alpha = 0.9f) else androidx.compose.ui.graphics.lerp(Color.White, p, 0.045f)),
        inInk = if (dark) inkD else inkL,
        outBg = outBrush, outFlat = fill, outInk = onFill,
        badgeBg = fill.a(0xD9), badgeInk = onFill,
        plusBg = P.grad?.let { Brush.linearGradient(listOf(it.first, it.second)) } ?: Brush.linearGradient(listOf(plus, plus)),
        plusFlat = plus,
        plusInk = P.plusInk ?: (if (P.plus != null) Color.White else onFill),
        fab = if (solid) (if (dark) hex("#23262E") else Color.White) else (if (dark) hex("#2E313C").copy(alpha = 0.62f) else Color.White.copy(alpha = 0.74f)),
        fabP = if (solid) plus else plus.a(0xCC),
        edgeLine = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.8f),
        shadow = if (dark) Color.Black.copy(alpha = 0.45f) else hex("#141E3C").copy(alpha = 0.22f),
        shadowP = if (dark) Color.Black.copy(alpha = 0.45f) else plus.a(0x88),
        hdrBg = if (solid) (if (dark) hex("#181A21") else Color.White) else (if (dark) hex("#16181F").copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)),
        accent2 = P.plus ?: P.s,
        outTop = P.grad?.first ?: androidx.compose.ui.graphics.lerp(fill, Color.White, if (dark) 0.10f else 0.08f),
        outBottom = P.grad?.second ?: androidx.compose.ui.graphics.lerp(fill, Color.Black, if (dark) 0.10f else 0.18f),
        tones = (if (dark) darkTones else lightTones).map { (b, i) -> androidx.compose.ui.graphics.lerp(b, fill, if (dark) 0.10f else 0.12f) to i },
    )
}

// Sage, moss, eucalyptus, honey, sea, clay, heather, stone.
private val lightTones = listOf(
    "#D9E5D6" to "#33502F", "#E4E8CF" to "#4F5A1F", "#CFE3DD" to "#24564B", "#EFE3C8" to "#6A5016",
    "#D6E0E8" to "#2F4C63", "#EEDDD2" to "#7A4430", "#E5DBE6" to "#5A3D5B", "#E1E3DE" to "#41463F",
).map { (b, i) -> hex(b) to hex(i) }
private val darkTones = listOf(
    "#2B3A2A" to "#BCD2B7", "#363A22" to "#D3DBA6", "#1F3833" to "#A9D6CB", "#3D3220" to "#E8CF9A",
    "#22313D" to "#AFC8DC", "#3F2A22" to "#E7BCA6", "#362838" to "#D9BEDB", "#2C2F2B" to "#C9CDC5",
).map { (b, i) -> hex(b) to hex(i) }

/** Wallpaper and glow backgrounds, as layered brushes over the base color. */
fun wallBrushes(name: String, t: Tok): List<Brush> {
    val d = t.dark
    return when (name) {
        "Mist" -> listOf(
            RadialSpec(t.s.a(if (d) 0x33 else 0x4D), 1f, 0f, 0.9f),
            RadialSpec(t.p.a(if (d) 0x26 else 0x1F), 0f, 1f, 0.8f),
        ).map { it.brush() }
        "Dusk" -> listOf(Brush.linearGradient(listOf(t.p.a(if (d) 0x33 else 0x29), t.s.a(if (d) 0x1A else 0x3D))))
        "Aurora" -> listOf(
            RadialSpec(t.s.a(if (d) 0x40 else 0x59), 0.15f, 0.2f, 0.7f),
            RadialSpec(t.p.a(if (d) 0x40 else 0x33), 0.9f, 0.75f, 0.6f),
        ).map { it.brush() }
        "Glow" -> listOf(
            RadialSpec(t.s.a(if (d) 0x1A else 0x33), 1f, 0f, 0.8f),
            RadialSpec(t.p.a(if (d) 0x1F else 0x17), 0f, 1f, 0.7f),
        ).map { it.brush() }
        else -> emptyList()
    }
}

/** A radial glow positioned in fractions of the screen; resolved to pixels at draw time. */
data class RadialSpec(val color: Color, val fx: Float, val fy: Float, val fr: Float) {
    fun brush(): Brush = FractionalRadial(color, fx, fy, fr)
}

class FractionalRadial(private val color: Color, private val fx: Float, private val fy: Float, private val fr: Float) : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
        val r = maxOf(size.width, size.height) * fr
        return androidx.compose.ui.graphics.RadialGradientShader(
            center = Offset(size.width * fx, size.height * fy), radius = if (r > 0f) r else 1f,
            colors = listOf(color, Color.Transparent),
        )
    }

    override fun equals(other: Any?) = other is FractionalRadial && other.color == color && other.fx == fx && other.fy == fy && other.fr == fr
    override fun hashCode() = color.hashCode() * 31 + fx.hashCode() * 17 + fy.hashCode() * 7 + fr.hashCode()
}

val LocalTok = staticCompositionLocalOf { tok(Palettes.favorites[1], false) }


/** Applies the colors picked one by one in "Customize more". */
fun Tok.withCustom(m: Map<String, com.jpetrydesign.pyng.data.Hsl>): Tok {
    if (m.isEmpty()) return this
    var t = this
    m["bg"]?.color()?.let { t = t.copy(bg = it) }
    m["inBg"]?.color()?.let { t = t.copy(inBg = it) }
    m["inInk"]?.color()?.let { t = t.copy(inInk = it) }
    m["outBg"]?.color()?.let { t = t.copy(outBg = Brush.linearGradient(listOf(it, it)), outFlat = it) }
    m["outInk"]?.color()?.let { t = t.copy(outInk = it) }
    m["accent"]?.color()?.let { t = t.copy(p = it, fill = it, badgeBg = it, plusFlat = it, plusBg = Brush.linearGradient(listOf(it, it))) }
    return t
}
