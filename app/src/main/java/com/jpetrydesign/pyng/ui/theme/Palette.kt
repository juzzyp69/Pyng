package com.jpetrydesign.pyng.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.jpetrydesign.pyng.data.ChatLook
import com.jpetrydesign.pyng.data.Hsl
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun hex(s: String): Color {
    val h = s.removePrefix("#")
    return when (h.length) {
        6 -> Color(("FF$h").toLong(16))
        8 -> Color((h.substring(6, 8) + h.substring(0, 6)).toLong(16))
        else -> Color.Unspecified
    }
}

fun Color.hexString(): String {
    val r = (red * 255).roundToInt(); val g = (green * 255).roundToInt(); val b = (blue * 255).roundToInt()
    return "#%02X%02X%02X".format(r, g, b)
}

/** Adds an alpha written as a two-digit hex byte, like CSS "#1E5FD114". */
fun Color.a(byte: Int): Color = copy(alpha = byte / 255f)

/** HSL (h 0-360, s/l 0-100) to a color, the same formula the prototype uses. */
fun hsl(h: Float, s: Float, l: Float): Color {
    val sf = s / 100f; val lf = l / 100f
    val a = sf * min(lf, 1 - lf)
    fun f(n: Float): Float {
        val k = (n + h / 30f) % 12f
        return lf - a * max(-1f, min(k - 3f, min(9f - k, 1f)))
    }
    return Color(f(0f).coerceIn(0f, 1f), f(8f).coerceIn(0f, 1f), f(4f).coerceIn(0f, 1f))
}

fun Hsl.color() = hsl(h, s * 100f, l * 100f)

fun toHsl(c: Color): Hsl {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    var h = 0f; var s = 0f
    if (mx != mn) {
        val d = mx - mn
        s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
        h = when (mx) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60f
    }
    return Hsl(h, s, l)
}

/** A theme palette: primary, its dark-mode twin, a soft second tone and optional extras. */
@Immutable
data class Pal(
    val name: String,
    val p: Color,
    val pd: Color,
    val s: Color,
    val bg: Color? = null,
    val bgd: Color? = null,
    val plus: Color? = null,
    val plusInk: Color? = null,
    val pt: Color? = null,
    val onP: Color? = null,
    val grad: Pair<Color, Color>? = null,
)

private fun pal(name: String, p: String, pd: String, s: String, bg: String? = null, bgd: String? = null, plus: String? = null, plusInk: String? = null) =
    Pal(name, hex(p), hex(pd), hex(s), bg?.let(::hex), bgd?.let(::hex), plus?.let(::hex), plusInk?.let(::hex))

object Palettes {
    val favorites = listOf(
        pal("Sage", "#4A6B4F", "#9CC2A0", "#9FB9A2"),
        pal("Ocean", "#1E5FD1", "#7FA9FF", "#8CB8F0"),
        pal("Rose", "#B23A63", "#F08DAE", "#E6A3BA"),
    )
    val colors = listOf(
        pal("Honey", "#8C6210", "#E8C06A", "#E9C987", "#FBF7EE", "#15130E"), pal("Sand", "#7E6443", "#D9BF98", "#D8C3A3", "#F7F2EA", "#14120F"),
        pal("Terracotta", "#A34A2C", "#EE9A79", "#E2A78E", "#FAF3EC", "#15110F"), pal("Olive", "#5A6628", "#C2CF85", "#BAC48F", "#F7F6EC", "#12130E"),
        pal("Mocha", "#6B4A3A", "#CFA894", "#C8AFA2", "#F8F3EF", "#14110F"), pal("Denim", "#3A5683", "#9DB5DE", "#A7B7D2", "#F4F3EE", "#0F1116"),
        pal("Lavender", "#6A55B0", "#B9A8F2", "#C2B6E8", "#FAF8FD", "#100F16"), pal("Forest", "#2F5D45", "#8FC7A8", "#A3C2B1", "#F5F6F0", "#0E1210"),
        pal("Burgundy", "#7A2638", "#E38A9D", "#D4A2AC", "#FAF5F2", "#150F10"), pal("Tangerine", "#B04F12", "#FFA562", "#F7B98A", "#FFF8F1", "#16110D"),
        pal("Indigo", "#4F46E5", "#8B85FF", "#A5A0F5"), pal("Cobalt", "#2563EB", "#7EA6FF", "#93B4F5"), pal("Midnight", "#1E2A5A", "#9AA8E0", "#6F7FB8"),
        pal("Teal", "#0F766E", "#5EC4B6", "#7CC8BE"), pal("Plum", "#7E3A96", "#D08BE0", "#C49BD3"), pal("Ember", "#B4472A", "#F2946F", "#E8A88F"), pal("Graphite", "#2F3A4F", "#A9B4C8", "#8E98AB"),
    )
    val duos = listOf(
        pal("Navy Marigold", "#1F2E5C", "#9FB0E6", "#9FB0E6", "#FAF7EF", "#0F1016", "#E3A72B", "#1F1A0A"),
        pal("Cream Soda", "#A0472B", "#EE9A79", "#E2A78E", "#FBF5EC", "#15110F", "#2F6F6A"),
        pal("70s Diner", "#2E6E6B", "#7FC6C1", "#9CCBC7", "#F8F4EC", "#0F1312", "#D9603A"),
        pal("Grape Soda", "#5B3FB0", "#B3A1F5", "#C4B8EE", "#F8F6FC", "#100F17", "#F2B33D", "#24190A"),
        pal("Matcha Latte", "#4E6B3A", "#A9C894", "#B5C7A5", "#F7F4EC", "#11130F", "#9C7448"),
        pal("Cherry Cola", "#7A2032", "#E58598", "#D7A3AD", "#FAF4EE", "#150F10", "#D9A441", "#24190A"),
        pal("Pool Party", "#1E6FD9", "#7FB0FF", "#9CC3F2", "#F3F8FC", "#0D1118", "#F06B5D"),
        pal("Mint Chocolate", "#4A3428", "#CDB2A2", "#C9B6AA", "#F4F6F1", "#12110F", "#2E9479"),
        pal("Ink Gold", "#1B1E28", "#C8CCD8", "#B9BDC9", "#F8F6F0", "#0E0F12", "#E2B84D", "#1F1A0A"),
        pal("Berry Coral", "#A3245C", "#F07AA8", "#EBA7C2", "#FCF5F3", "#160F12", "#E8694A"),
        pal("Indigo Lilac", "#4F46E5", "#8B85FF", "#C4B5FD", "#F8F7FF", null, "#8B7CF6"), pal("Ocean Sky", "#1E5FD1", "#7FA9FF", "#7DD3FC", "#F4F9FD", null, "#38A8E0"),
        pal("Azure Mint", "#1D6FE0", "#6FA8FF", "#5EEAD4", "#F3FAF9", null, "#14A38B"), pal("Navy Peach", "#1E2A5A", "#9AA8E0", "#FDBA8C", "#FDF7F2", null, "#F29A68", "#2A1508"),
        pal("Cobalt Lime", "#2549D6", "#8098FF", "#C6F06B", "#F8FBF1", null, "#B5E550", "#18240A"), pal("Teal Sand", "#0F766E", "#5EC4B6", "#F2D49B", "#FBF8F1", null, "#D9A85C", "#24190A"),
        pal("Plum Rose", "#7E3A96", "#D08BE0", "#F9A8C9", "#FCF6F9", null, "#E26A9C"), pal("Graphite Blue", "#2F3A4F", "#A9B4C8", "#7FA7E8", "#F5F7FA", null, "#4F84DB"),
    )
    val all get() = favorites + colors + duos
    val quick = listOf(
        "Blush" to "#F4B6C8", "Mustard" to "#B88A1E", "Chestnut" to "#7A4A2E", "Sage" to "#8FA888", "Lilac" to "#B9A3E3", "Slate" to "#4F5D75",
        "Coral" to "#F08A6B", "Mint" to "#9ED9C3", "Butter" to "#F2DC8C", "Wine" to "#6E2340", "Denim" to "#4A6A9A", "Clay" to "#C27A5A",
    )
    val walls = listOf("None", "Mist", "Dusk", "Paper", "Aurora")

    fun fromColor(c: Hsl, name: String): Pal {
        val s100 = c.s * 100f
        val p = hsl(c.h, s100, c.l * 100f)
        val light = p.luminance() > 0.36f
        return Pal(
            name = name, p = p,
            pt = if (light) hsl(c.h, min(s100, 65f), 32f) else p,
            pd = hsl(c.h, min(s100, 80f), max(c.l * 100f, 72f)),
            s = hsl(c.h, s100 * 0.6f, 80f),
            bg = hsl(c.h, min(s100, 45f), 97.5f),
            bgd = hsl(c.h, min(s100, 20f), 7f),
            onP = if (light) hex("#16181D") else Color.White,
        )
    }

    fun custom(c1: Hsl, c2: Hsl, duo: Boolean): Pal {
        val base = fromColor(c1, if (duo) "Custom duotone" else "Custom color")
        if (!duo) return base
        val plus = c2.color()
        return base.copy(plus = plus, plusInk = if (plus.luminance() > 0.36f) hex("#16181D") else Color.White)
    }

    fun chatCustom(look: ChatLook): Pal {
        val base = fromColor(look.c1, "Custom · ${look.mode}")
        if (look.mode == "Mono") return base
        val p2 = look.c2.color()
        val extra = base.copy(plus = p2, plusInk = if (p2.luminance() > 0.36f) hex("#16181D") else Color.White, s = hsl(look.c2.h, look.c2.s * 60f, 80f))
        return if (look.mode == "Gradient") extra.copy(grad = base.p to p2) else extra
    }

    fun byKey(key: String, c1: Hsl, c2: Hsl): Pal = when (key) {
        "Custom color" -> custom(c1, c2, false)
        "Custom duotone" -> custom(c1, c2, true)
        else -> all.firstOrNull { it.name == key } ?: favorites[1]
    }
}

@Suppress("unused")
private fun near(a: Float, b: Float) = abs(a - b) < 0.001f
