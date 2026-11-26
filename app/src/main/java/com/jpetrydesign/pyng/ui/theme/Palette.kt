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
    /** The sent-bubble gradient in dark mode, when it differs from light. */
    val gradD: Pair<Color, Color>? = null,
    /** Accent in dark mode and its text, when they differ from light. */
    val plusD: Color? = null,
    val plusInkD: Color? = null,
    /** Received bubbles and their text, when the theme sets them (light, then dark). */
    val inB: Color? = null,
    val inBd: Color? = null,
    val inInk: Color? = null,
    /** What's saved to remember this theme. Defaults to the name. */
    val key: String = name,
)

private fun pal(name: String, p: String, pd: String, s: String, bg: String? = null, bgd: String? = null, plus: String? = null, plusInk: String? = null) =
    Pal(name, hex(p), hex(pd), hex(s), bg?.let(::hex), bgd?.let(::hex), plus?.let(::hex), plusInk?.let(::hex))

private fun grad(name: String, a: String, b: String) = Pal(
    name, hex(a), androidx.compose.ui.graphics.lerp(hex(a), Color.White, 0.3f), androidx.compose.ui.graphics.lerp(hex(a), Color.White, 0.55f),
    grad = hex(a) to hex(b),
)

object Palettes {
    // Solid: five crisp colors on pure white (true black in dark mode), then five softer ones on a warm tint.
    val plain = listOf(
        pal("Blue", "#1E5FD1", "#7FA9FF", "#8CB8F0", "#FFFFFF", "#000000"),
        pal("Green", "#1F8A4C", "#6FD69A", "#9FD8B5", "#FFFFFF", "#000000"),
        pal("Red", "#D2334C", "#FF8798", "#F2A8B4", "#FFFFFF", "#000000"),
        pal("Orange", "#D85E14", "#FFA466", "#F7BE95", "#FFFFFF", "#000000"),
        pal("Purple", "#6B3FD6", "#B39BFF", "#C2B3F2", "#FFFFFF", "#000000"),
    )
    val tinted = listOf(
        pal("Denim", "#3A5683", "#9DB5DE", "#A7B7D2", "#F4F3EE", "#0F1116"),
        pal("Sage", "#4A6B4F", "#9CC2A0", "#9FB9A2", "#F5F6F0", "#0F1210"),
        pal("Rose", "#A84A64", "#E8A0B4", "#E2B1BF", "#FAF4F2", "#150F11"),
        pal("Clay", "#A34A2C", "#EE9A79", "#E2A78E", "#FAF3EC", "#15110F"),
        pal("Lavender", "#6A55B0", "#B9A8F2", "#C2B6E8", "#F8F6FC", "#100F16"),
    )
    // Duotone: a deep color and a partner. Rows run red, orange, gold, green, blue, violet.
    private val duoRows = listOf(
        "Bold" to listOf("Cherry" to ("#0E1A3A" to "#E11D48"), "Solar" to ("#32145F" to "#FF9F1C"), "Midnight" to ("#0B132B" to "#F5E642"), "Volt" to ("#0E2E1C" to "#A3FF3C"), "Arctic" to ("#0A1F3D" to "#B8E7FF"), "Acid" to ("#3F37C9" to "#C6FF00")),
        "Bright" to listOf("Flamingo" to ("#D81B60" to "#FFB199"), "Sunburst" to ("#E8590C" to "#FFD43B"), "Citrus" to ("#2B8A3E" to "#FFE066"), "Lagoon" to ("#1D4ED8" to "#2EE6A6"), "Riviera" to ("#0E7490" to "#7DD3FC"), "Grape" to ("#6D28D9" to "#F9A8D4")),
        "Soft" to listOf("Merlot" to ("#5B0E2E" to "#FEA982"), "Rose Gold" to ("#3A2219" to "#E7A58E"), "Champagne" to ("#0F2F2F" to "#E6C79C"), "Seafoam" to ("#0B3D35" to "#7FFFD4"), "Glacier" to ("#1C2A44" to "#CDE7F0"), "Plum" to ("#3B0D33" to "#F7A8CF")),
        "Jewel" to listOf("Garnet" to ("#4A0E1E" to "#F2B05E"), "Copper" to ("#0F3B3A" to "#E0895A"), "Saffron" to ("#3A1C4A" to "#F4C430"), "Jade" to ("#2A1638" to "#5EDC9A"), "Dusk" to ("#0B1B5C" to "#F5B8C6"), "Amethyst" to ("#2D0F4F" to "#B9F0FF")),
        "Retro" to listOf("Diner" to ("#8E1B2C" to "#F4E4C9"), "Avocado" to ("#4C5A22" to "#EF8A3A"), "Mustard" to ("#1F5C5A" to "#E2A93B"), "Pine" to ("#173A2A" to "#F08A3A"), "Sailor" to ("#2B4A78" to "#F3D68A"), "Mauve" to ("#5C3A57" to "#B9E4D2")),
    )
    // Gradient: deep to light, two or three colors.
    private val gradRows = listOf(
        "Glow" to listOf("Volcano" to listOf("#160B18", "#E52B18", "#FFB000"), "Desert" to listOf("#321B42", "#D45135", "#F5A623"), "Coral" to listOf("#087FA3", "#F04FA5", "#FFD43B"), "Aurora" to listOf("#171044", "#6B36C9", "#53F0A5"), "Deep Sea" to listOf("#07152F", "#006B9C", "#62F5D0"), "Butterfly" to listOf("#15104A", "#176BBD", "#D45BCE")),
        "Mix" to listOf("Berry" to listOf("#3B0D33", "#C2185B", "#F7A8CF"), "Sunset" to listOf("#2A0E4A", "#E8336D", "#FFB347"), "Citrus" to listOf("#C2410C", "#FF9F1C", "#FFE066"), "Neon" to listOf("#0E2E1C", "#1FAA59", "#C6FF3C"), "Lagoon" to listOf("#0B2A6B", "#1D7FD8", "#3EEAA8"), "Ice" to listOf("#0A1F3D", "#3B6FD8", "#B8E7FF")),
        "Bold" to listOf("Cherry" to listOf("#0E1A3A", "#E11D48"), "Solar" to listOf("#32145F", "#FF9F1C"), "Midnight" to listOf("#0B132B", "#F5E642"), "Volt" to listOf("#0E2E1C", "#A3FF3C"), "Arctic" to listOf("#0A1F3D", "#B8E7FF"), "Plum" to listOf("#3B0D33", "#F7A8CF")),
        "Jewel" to listOf("Garnet" to listOf("#2A0712", "#A3123A", "#F2B05E"), "Copper" to listOf("#0F3B3A", "#B5552E", "#F2A877"), "Saffron" to listOf("#2A1238", "#B8742A", "#F4C430"), "Jade" to listOf("#1A0F2E", "#1E7F5C", "#5EDC9A"), "Dusk" to listOf("#0B1B5C", "#6A4BC4", "#F5B8C6"), "Amethyst" to listOf("#2D0F4F", "#8B3FD9", "#B9F0FF")),
        "Soft" to listOf("Merlot" to listOf("#5B0E2E", "#FEA982"), "Sienna" to listOf("#7A2E24", "#E8D8C3"), "Champagne" to listOf("#0F2F2F", "#E6C79C"), "Seafoam" to listOf("#0B3D35", "#7FFFD4"), "Glacier" to listOf("#1C2A44", "#CDE7F0"), "Plum" to listOf("#3B0D33", "#F7A8CF")),
    )
    /** Duotones, in color order: each color family together (red first), then the next. */
    val duos: List<Pal> = (0 until 6).flatMap { c -> duoRows.map { (_, row) -> row[c].let { (n, pr) -> duoPal(n, hex(pr.first), hex(pr.second), key = "d:$n") } } }
    val grads: List<Pal> = (0 until 6).flatMap { c -> gradRows.map { (_, row) -> row[c].let { (n, cs) -> gradPal(n, cs.map(::hex), key = "g:$n") } } }
    val solids get() = plain + tinted
    /** "Solid", "Duotone", "Gradient", or "Custom" for your own colors. */
    fun kindOf(key: String): String = when {
        key.startsWith("Custom") -> "Custom"
        key.startsWith("g:") || oldGrads.any { it.key == key } -> "Gradient"
        key.startsWith("d:") || oldDuos.any { it.key == key } -> "Duotone"
        else -> "Solid"
    }
    // Themes from earlier versions, kept so a chat or the app that uses one still looks the same.
    private val oldDuos = listOf(
        pal("Ocean Sky", "#1E5FD1", "#7FA9FF", "#7DD3FC", "#F4F9FD", null, "#38A8E0"),
        pal("Plum Rose", "#7E3A96", "#D08BE0", "#F9A8C9", "#FCF6F9", null, "#E26A9C"),
        pal("Teal Sand", "#0F766E", "#5EC4B6", "#F2D49B", "#FBF8F1", null, "#D9A85C", "#24190A"),
        pal("Navy Peach", "#1E2A5A", "#9AA8E0", "#FDBA8C", "#FDF7F2", null, "#F29A68", "#2A1508"),
        pal("Navy Gold", "#1F2E5C", "#9FB0E6", "#9FB0E6", "#FAF7EF", "#0F1016", "#E3A72B", "#1F1A0A"),
        pal("70s Diner", "#2E6E6B", "#7FC6C1", "#9CCBC7", "#F8F4EC", "#0F1312", "#D9603A"),
        pal("Grape Soda", "#5B3FB0", "#B3A1F5", "#C4B8EE", "#F8F6FC", "#100F17", "#F2B33D", "#24190A"),
        pal("Cherry Cola", "#7A2032", "#E58598", "#D7A3AD", "#FAF4EE", "#150F10", "#D9A441", "#24190A"),
        pal("Volt", "#111613", "#C6FF3D", "#C9D6C4", "#F3F4F1", "#050605", "#C6FF3D", "#10140A"),
        pal("Cyber", "#0B1424", "#3DF2E0", "#B9C6D6", "#F2F5F8", "#04070D", "#3DF2E0", "#04201D"),
        pal("Onyx Gold", "#141414", "#E8C26A", "#D3CCBD", "#F6F4EF", "#070707", "#E8C26A", "#241B06"),
        pal("Ultraviolet", "#150C26", "#B77BFF", "#CFC2E6", "#F6F3FB", "#08050F", "#FF5BD8", "#2A0622"),
    )
    private val oldGrads = listOf(
        grad("Aurora", "#0F766E", "#4F46E5"), grad("Sunset", "#E8694A", "#7E3A96"), grad("Tide", "#1E5FD1", "#0EA5B7"),
        grad("Peach", "#F08A6B", "#E9A23B"), grad("Berry", "#A3245C", "#6A4BC4"), grad("Forest", "#1F6B4A", "#6FA05A"),
        grad("Dusk", "#3A5683", "#B05C8A"), grad("Lagoon", "#0B807B", "#4FA8E8"), grad("Ember", "#C2410C", "#7A2032"),
        grad("Lilac", "#7C6BF0", "#E27AB0"), grad("Midnight", "#1E2A5A", "#4F46E5"), grad("Neon", "#1DB86A", "#2BB8E8"),
    )
    private val legacy = oldDuos + oldGrads + listOf(
        pal("Teal", "#0B807B", "#5ED6CF", "#8FD0CB", "#FFFFFF", "#000000"), pal("Honey", "#8C6210", "#E8C06A", "#E9C987", "#FBF7EE", "#15130E"),
        pal("Ocean", "#1E5FD1", "#7FA9FF", "#8CB8F0"), pal("Sand", "#7E6443", "#D9BF98", "#D8C3A3", "#F7F2EA", "#14120F"),
        pal("Terracotta", "#A34A2C", "#EE9A79", "#E2A78E", "#FAF3EC", "#15110F"), pal("Olive", "#5A6628", "#C2CF85", "#BAC48F", "#F7F6EC", "#12130E"),
        pal("Mocha", "#6B4A3A", "#CFA894", "#C8AFA2", "#F8F3EF", "#14110F"), pal("Forest", "#2F5D45", "#8FC7A8", "#A3C2B1", "#F5F6F0", "#0E1210"),
        pal("Burgundy", "#7A2638", "#E38A9D", "#D4A2AC", "#FAF5F2", "#150F10"), pal("Tangerine", "#B04F12", "#FFA562", "#F7B98A", "#FFF8F1", "#16110D"),
        pal("Indigo", "#4F46E5", "#8B85FF", "#A5A0F5"), pal("Cobalt", "#2563EB", "#7EA6FF", "#93B4F5"), pal("Midnight", "#1E2A5A", "#9AA8E0", "#6F7FB8"),
        pal("Plum", "#7E3A96", "#D08BE0", "#C49BD3"), pal("Ember", "#B4472A", "#F2946F", "#E8A88F"), pal("Graphite", "#2F3A4F", "#A9B4C8", "#8E98AB"),
        pal("Cream Soda", "#A0472B", "#EE9A79", "#E2A78E", "#FBF5EC", "#15110F", "#2F6F6A"), pal("Matcha Latte", "#4E6B3A", "#A9C894", "#B5C7A5", "#F7F4EC", "#11130F", "#9C7448"),
        pal("Pool Party", "#1E6FD9", "#7FB0FF", "#9CC3F2", "#F3F8FC", "#0D1118", "#F06B5D"), pal("Mint Chocolate", "#4A3428", "#CDB2A2", "#C9B6AA", "#F4F6F1", "#12110F", "#2E9479"),
        pal("Ink Gold", "#1B1E28", "#C8CCD8", "#B9BDC9", "#F8F6F0", "#0E0F12", "#E2B84D", "#1F1A0A"), pal("Berry Coral", "#A3245C", "#F07AA8", "#EBA7C2", "#FCF5F3", "#160F12", "#E8694A"),
        pal("Indigo Lilac", "#4F46E5", "#8B85FF", "#C4B5FD", "#F8F7FF", null, "#8B7CF6"), pal("Azure Mint", "#1D6FE0", "#6FA8FF", "#5EEAD4", "#F3FAF9", null, "#14A38B"),
        pal("Cobalt Lime", "#2549D6", "#8098FF", "#C6F06B", "#F8FBF1", null, "#B5E550", "#18240A"), pal("Graphite Blue", "#2F3A4F", "#A9B4C8", "#7FA7E8", "#F5F7FA", null, "#4F84DB"),
    )
    // Older code paths still ask for these.
    val favorites get() = tinted.take(1) + plain.take(2)
    val colors get() = solids
    val all get() = solids + duos + grads + legacy.filter { l -> (solids + duos + grads).none { it.key == l.key } }
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

    /** A chat's own colors: one, two, or two blended, made the same way as the app's. */
    fun chatCustom(look: ChatLook): Pal =
        byKey(when (look.mode) { "Duotone" -> "Custom duotone"; "Gradient" -> "Custom gradient"; else -> "Custom color" }, look.c1, look.c2)

    /**
     * Your own colors: "Custom color" (one), "Custom duotone" (two) or "Custom gradient" (two, blended),
     * each with " flat" on the end when the background isn't tinted.
     */
    fun byKey(key: String, c1: Hsl, c2: Hsl): Pal {
        val flat = key.endsWith(" flat")
        return when (key.removeSuffix(" flat")) {
            "Custom color" -> fromColor(c1, key).let { b -> if (flat) b.copy(bg = Color.White, bgd = Color.Black) else b }
            "Custom duotone" -> duoPal("Custom", c1.color(), c2.color(), key = key, flat = flat)
            "Custom gradient" -> gradPal("Custom", listOf(c1.color(), c2.color()), key = key, flat = flat)
            else -> all.firstOrNull { it.key == key } ?: tinted[0]
        }
    }
}

private fun mixC(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
private fun cr(a: Color, b: Color): Float { val x = a.luminance() + 0.05f; val y = b.luminance() + 0.05f; return if (x > y) x / y else y / x }

/** Text that reads on [c]: white when it can, else the theme's deep color, darkened as far as it needs. */
private fun inkFor(c: Color, deep: Color): Color {
    if (cr(Color.White, c) >= 3f) return Color.White
    for (k in listOf(0.5f, 0.72f, 0.9f)) { val d = mixC(deep, Color.Black, k); if (cr(d, c) >= 3f) return d }
    return hex("#101218")
}

/**
 * A pair of colors: [deep] carries your bubbles in light mode and [lite] in dark mode. The partner is
 * always the accent, the background takes a soft tint of it (unless [flat]), and received bubbles get a
 * wash of it that always stands apart from the background.
 */
internal fun duoPal(name: String, deep: Color, lite: Color, key: String = name, flat: Boolean = false, gradL: Pair<Color, Color>? = null, gradD: Pair<Color, Color>? = null, accL: Color? = null): Pal {
    val bright = lite.luminance() > 0.55f
    val bg = if (flat) Color.White else mixC(Color.White, lite, if (bright) 0.12f else 0.065f)
    val bgd = if (flat) hex("#0B0C0E") else mixC(hex("#0A0A0C"), deep, 0.3f)
    var inB = mixC(bg, lite, if (bright) 0.42f else 0.26f)
    var n = 0
    while (n < 8 && cr(inB, bg) < 1.18f) { inB = mixC(inB, deep, 0.05f); n++ }
    var inBd = mixC(bgd, mixC(deep, Color.White, 0.16f), 0.62f)
    n = 0
    while (n < 8 && cr(inBd, bgd) < 1.25f) { inBd = mixC(inBd, Color.White, 0.04f); n++ }
    val a0 = accL ?: lite
    val acc = if (a0.luminance() > 0.62f) mixC(a0, deep, 0.18f) else a0
    return Pal(
        name = name, p = deep, pd = lite, s = lite, bg = bg, bgd = bgd,
        plus = acc, plusInk = inkFor(acc, deep), plusD = lite, plusInkD = inkFor(lite, deep),
        grad = gradL, gradD = gradD, inB = inB, inBd = inBd, inInk = mixC(deep, Color.Black, 0.15f), key = key,
    )
}

/** A gradient, deep to light: deep into the middle color in light mode, the middle into light in dark mode. */
internal fun gradPal(name: String, cs: List<Color>, key: String = name, flat: Boolean = false): Pal {
    val deep = cs.first(); val lite = cs.last()
    val mid = if (cs.size > 2) cs[1] else null
    return duoPal(
        name, deep, lite, key = key, flat = flat,
        gradL = deep to (mid ?: mixC(deep, lite, 0.45f)),
        gradD = (mid ?: mixC(deep, lite, 0.55f)) to lite,
        accL = mid,
    )
}

@Suppress("unused")
private fun near(a: Float, b: Float) = abs(a - b) < 0.001f
