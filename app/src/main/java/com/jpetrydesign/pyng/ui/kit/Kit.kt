package com.jpetrydesign.pyng.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.Prefs
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Tok
import com.jpetrydesign.pyng.ui.theme.wallBrushes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.io.File
import kotlin.math.roundToInt

val LocalRepo = compositionLocalOf<Repo> { error("no repo") }
val LocalHaze = compositionLocalOf<HazeState?> { null }
val LocalMotion = compositionLocalOf { true }

val Repo.p get() = prefs.state
val Prefs.s get() = state

// ---------- text ----------

@Composable
fun T(
    text: String,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalTok.current.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
        style = TextStyle(fontFamily = Geist, fontSize = size, fontWeight = weight, color = color, lineHeight = lineHeight, letterSpacing = letterSpacing),
    )
}

// ---------- icons ----------

private val pathCache = HashMap<String, Path>()

private fun parsed(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/** Draws one of the 24×24 line icons. */
@Composable
fun Ico(d: String, tint: Color = LocalTok.current.ink, size: Dp = 20.dp, stroke: Float = 1.9f, fill: Color? = null, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            val path = parsed(d)
            if (fill != null) drawPath(path, fill, style = Fill)
            drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ---------- surfaces ----------

/** Frosted glass: blurs what's behind (when a haze source exists) and tints it. */
fun Modifier.frost(shape: Shape, tint: Color, blur: Dp = 26.dp): Modifier = composed {
    val t = LocalTok.current
    val hz = LocalHaze.current
    if (hz != null && !t.solid) {
        this.clip(shape).hazeEffect(state = hz, style = HazeStyle(backgroundColor = t.bg, tint = HazeTint(tint), blurRadius = blur, noiseFactor = 0f))
    } else {
        this.clip(shape).background(tint)
    }
}

/** The soft floating shadow plus the bright inner edge used on glass controls. */
fun Modifier.edge(shape: Shape, primary: Boolean = false, elevation: Dp = 12.dp): Modifier = composed {
    val t = LocalTok.current
    this
        .shadow(elevation, shape, clip = false, ambientColor = if (primary) t.shadowP else t.shadow, spotColor = if (primary) t.shadowP else t.shadow)
        .border(1.dp, if (primary) Color.White.copy(alpha = if (t.dark) 0.14f else 0.35f) else t.edgeLine, shape)
}

fun Modifier.cardEdge(shape: Shape): Modifier = composed { this.border(1.dp, LocalTok.current.cardEdge, shape) }

/** Gentle shrink while pressed, springing back on release. */
fun Modifier.press(scaleTo: Float = 0.95f, onClick: (() -> Unit)? = null): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val motion = LocalMotion.current
    val k by animateFloatAsState(if (pressed && motion) scaleTo else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    var m = this.scale(k)
    if (onClick != null) m = m.clickable(interactionSource = src, indication = null, onClick = onClick)
    m
}

private val enteredOnce = HashSet<String>()

/** Like [enter], but plays only the first time [key] appears after the app opens, not every time you come back to the screen. */
fun Modifier.enterOnce(key: String, index: Int, pop: Boolean = false): Modifier = composed {
    val first = remember { enteredOnce.add(key) }
    if (first) Modifier.enter(index, pop) else Modifier
}

/** Stagger-in for list items: fade and rise, like the prototype's fadeUp/popUp. */
fun Modifier.enter(index: Int, pop: Boolean = false): Modifier = composed {
    val motion = LocalMotion.current
    val a = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        kotlinx.coroutines.delay((30 + minOf(index, 12) * 35).toLong())
        a.animateTo(1f, spring(dampingRatio = if (pop) 0.55f else 0.8f, stiffness = 260f))
    }
    val dy = with(LocalDensity.current) { 14.dp.toPx() }
    this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * dy
        if (pop) { val s = 0.86f + 0.14f * a.value; scaleX = s; scaleY = s }
    }
}

@Composable
fun Glow(t: Tok = LocalTok.current, modifier: Modifier = Modifier.fillMaxSize()) {
    val brushes = remember(t) { wallBrushes("Glow", t) }
    Box(modifier.background(t.bg).drawBehind { brushes.forEach { drawRect(it) } })
}

/** A chat wallpaper: a named pattern, or "photo:<path>" with a readability veil. */
@Composable
fun Wallpaper(name: String, t: Tok, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(t.bg)) {
        if (name.startsWith("photo:")) {
            AsyncImage(model = File(name.removePrefix("photo:")), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(if (t.dark) Color(0x800A0C10) else Color(0x57FFFFFF)))
        } else if (name == "Paper") {
            val dot = if (t.dark) Color.White.copy(alpha = 0.07f) else Color(0x1215171D)
            Canvas(Modifier.fillMaxSize()) {
                val step = 16.dp.toPx(); val r = 1.2.dp.toPx()
                var y = 0f
                while (y < size.height) { var x = 0f; while (x < size.width) { drawCircle(dot, r, Offset(x, y)); x += step }; y += step }
            }
        } else {
            val brushes = remember(t, name) { wallBrushes(name, t) }
            if (brushes.isNotEmpty()) Box(Modifier.fillMaxSize().drawBehind { brushes.forEach { drawRect(it) } })
        }
    }
}

// ---------- avatars & badges ----------

@Composable
fun Avatar(
    photo: String?,
    initials: String,
    size: Dp,
    bg: Color = LocalTok.current.neutral,
    ink: Color = LocalTok.current.neutralInk,
    fontSize: TextUnit = (size.value / 3.1f).sp,
    halo: Color? = null,
    modifier: Modifier = Modifier,
) {
    var m = modifier.size(size)
    if (halo != null) m = m.drawBehind {
        drawCircle(halo, radius = this.size.minDimension / 2 + 3.5.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
    Box(m.clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (!photo.isNullOrEmpty()) {
            AsyncImage(model = if (photo.startsWith("http") || photo.startsWith("content://")) photo else File(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            T(initials, size = fontSize, weight = FontWeight.SemiBold, color = ink)
        }
    }
}

fun initialsOf(name: String): String {
    val w = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (w.isEmpty()) return "#"
    val a = w[0].firstOrNull { it.isLetterOrDigit() }
    if (a == null || !a.isLetter()) return "#"
    val b = w.getOrNull(1)?.firstOrNull { it.isLetter() }
    return (a.toString() + (b?.toString() ?: "")).uppercase()
}

/**
 * Unread count, frosted. Over a photo, pass [backdrop] (the same photo, drawn at [backdropSize]) and
 * [backdropAt] (where that photo's corner sits relative to the badge): the badge then shows that
 * patch of the photo softly blurred behind the number, like real frosted glass. No outline.
 */
/**
 * The unread count. Give it a [haze] (the picture right under it) and it becomes frosted glass:
 * that picture blurred, with a soft tint of the badge color, and no outline.
 */
@Composable
fun CountBadge(n: Int, bg: Color = LocalTok.current.badgeBg, ink: Color = LocalTok.current.badgeInk, h: Dp = 21.dp, modifier: Modifier = Modifier, haze: HazeState? = null) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(h / 2)
    val glass = androidx.compose.ui.graphics.lerp(bg, Color.White, if (t.dark) 0.06f else 0.12f)
    val frosted = haze != null && !t.solid
    Box(
        modifier.heightIn(min = h).widthIn(min = h).clip(shape)
            .then(
                if (frosted) Modifier.hazeEffect(state = haze, style = HazeStyle(backgroundColor = t.bg, tint = HazeTint(glass.copy(alpha = if (t.dark) 0.56f else 0.62f)), blurRadius = 9.dp, noiseFactor = 0f))
                else Modifier.background(glass.copy(alpha = if (t.solid) 1f else 0.92f)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        T(if (n > 99) "99+" else n.toString(), size = (h.value * 0.52f).sp, weight = FontWeight.Bold, color = ink, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
fun TypingDots(color: Color, dot: Dp = 4.dp, gap: Dp = 3.dp) {
    val motion = LocalMotion.current
    val tr = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val y by tr.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1200, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "d$i",
            )
            val phase = ((y * 1200f - i * 150f) / 1200f).let { if (it < 0) it + 1 else it }
            val up = if (phase < 0.3f) phase / 0.3f else if (phase < 0.6f) 1f - (phase - 0.3f) / 0.3f else 0f
            Box(
                Modifier.size(dot).graphicsLayer {
                    if (motion) { translationY = -3.dp.toPx() * up; alpha = 0.45f + 0.55f * up }
                }.clip(CircleShape).background(color),
            )
        }
    }
}

// ---------- controls ----------

@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTok.current
    val x by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "sw")
    Box(
        modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) t.p else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(2.dp, t.faint, RoundedCornerShape(16.dp))),
    ) {
        val knob = 16.dp + 8.dp * x
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = 4.dp + (52.dp - 8.dp - knob) * x)
                .size(knob).clip(CircleShape).background(if (on) (if (t.dark) Color(0xFF101218) else Color.White) else t.faint),
        )
    }
}

/** Segmented control (tabs in a soft track). */
@Composable
fun Seg(options: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, height: Dp = 38.dp) {
    val t = LocalTok.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.neutral).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            val bgA by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "seg")
            Box(
                Modifier.weight(1f).height(height - 8.dp).clip(RoundedCornerShape(10.dp))
                    .background((if (t.dark) Color.White.copy(alpha = 0.14f) else Color.White).copy(alpha = (if (t.dark) 0.14f else 1f) * bgA))
                    .clickable { onPick(o) },
                contentAlignment = Alignment.Center,
            ) { T(o, size = 13.5.sp, weight = FontWeight.SemiBold, color = if (on) t.ink else t.soft, maxLines = 1) }
        }
    }
}

/** Wrapping pill choices, like the prototype's option chips. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTok.current
    androidx.compose.foundation.layout.FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) t.tone else Color.Transparent)
                    .border(1.dp, if (on) Color.Transparent else t.hair, RoundedCornerShape(16.dp))
                    .clickable { onPick(o) }.padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) { T(o, size = 12.5.sp, weight = FontWeight.SemiBold, color = if (on) t.p else t.soft) }
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    height: Dp = 48.dp,
    icon: String? = null,
) {
    val t = LocalTok.current
    val bg: Brush = when {
        danger -> Brush.linearGradient(listOf(t.danger, t.danger))
        primary -> t.plusBg
        else -> Brush.linearGradient(listOf(t.tone, t.tone))
    }
    val ink = when { danger -> Color.White; primary -> t.plusInk; else -> t.p }
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(bg).press(0.96f, onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, ink, 18.dp, 2f); Spacer(Modifier.width(8.dp)) }
        T(text, size = 15.sp, weight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

@Composable
fun CircleButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = LocalTok.current.ink, bg: Color = Color.Transparent, iconSize: Dp = 22.dp, stroke: Float = 2f) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).press(0.9f, onClick)
            .then(Modifier.semanticsLabel(label)),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint, iconSize, stroke) }
}

fun Modifier.semanticsLabel(label: String): Modifier = this.semantics { contentDescription = label }

/**
 * A horizontal drag that only claims the gesture when it starts in one direction
 * (sign = +1 for right, -1 for left). Anything else is left for parents and scrolling.
 */
fun Modifier.swipeDir(key: Any?, sign: Int, onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier = this.pointerInput(key, sign) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        var dragging = false
        val slop = viewConfiguration.touchSlop
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if (!dragging && ch.isConsumed) break
            val d = ch.positionChange()
            if (!dragging) {
                total += d
                if (kotlin.math.abs(total.y) > slop && kotlin.math.abs(total.y) > kotlin.math.abs(total.x)) break
                if (kotlin.math.abs(total.x) > slop) {
                    if (sign * total.x > 0 && kotlin.math.abs(total.x) > kotlin.math.abs(total.y) * 1.2f) { dragging = true; ch.consume(); onDrag(total.x - sign * slop) } else break
                }
            } else { ch.consume(); onDrag(d.x) }
        }
        if (dragging) onEnd()
    }
}

// ---------- page chrome ----------

/**
 * The glass for title bars pinned to the top: the chat header's frost with a touch of the theme
 * color, so it reads as a soft tint instead of black or white.
 */
val Tok.barBg: Color get() = if (solid) hdrBg else androidx.compose.ui.graphics.lerp(hdrBg, p.copy(alpha = hdrBg.alpha), if (dark) 0.16f else 0.08f)

/**
 * A slim bar pinned to the top of a screen. It covers the status bar, so whatever scrolls slides
 * under the frost instead of under the clock and icons. Pass the screen's [haze] to blur what's behind.
 */
@Composable
fun TopBar(haze: HazeState?, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
    CompositionLocalProvider(LocalHaze provides haze) {
        Row(
            modifier.fillMaxWidth()
                .shadow(8.dp, shape, clip = false, ambientColor = t.shadow.copy(alpha = 0.10f), spotColor = t.shadow.copy(alpha = 0.14f))
                .frost(shape, if (haze == null && !t.solid) androidx.compose.ui.graphics.lerp(t.bg, t.barBg.copy(alpha = 1f), 0.5f).copy(alpha = 0.94f) else t.barBg, 30.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Title bar for full pages: back chevron and a bold title, pinned at the top. */
@Composable
fun PageTop(title: String, onBack: () -> Unit, sub: String? = null, trailing: (@Composable () -> Unit)? = null, haze: HazeState? = null, modifier: Modifier = Modifier) {
    TopBar(haze, modifier) {
        Row(Modifier.weight(1f).padding(start = 6.dp, end = 12.dp, top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.back, "Back", onBack)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                T(title, size = if (sub != null) 20.sp else 22.sp, weight = FontWeight.Bold, letterSpacing = (-0.5).sp, maxLines = 1)
                if (sub != null) T(sub, size = 12.5.sp, color = LocalTok.current.soft, maxLines = 1)
            }
            trailing?.invoke()
        }
    }
}

/** How much the Text size setting scales text app-wide. */
fun textScaleOf(size: String): Float = when (size) { "S" -> 0.95f; "L" -> 1.1f; "XL" -> 1.22f; else -> 1f }

/** Keeps scrolled content from showing through under the status bar: a light tinted glass, not a dark fade. */
@Composable
fun StatusScrim(modifier: Modifier = Modifier) {
    val t = LocalTok.current
    val c = if (t.solid) t.hdrBg else androidx.compose.ui.graphics.lerp(t.bg, t.barBg.copy(alpha = 1f), 0.5f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(c.copy(alpha = 0.9f)))
        Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.verticalGradient(listOf(c.copy(alpha = 0.9f), c.copy(alpha = 0f)))))
    }
}

/** A scrolling page with the glow background and a title bar that stays put while the page slides under it. */
@Composable
fun Page(title: String, onBack: () -> Unit, sub: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    val haze = remember { HazeState() }
    var barH by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current
    val guess = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 56.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Glow()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(if (barH > 0) with(density) { barH.toDp() } else guess))
                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 32.dp), content = content)
            }
        }
        PageTop(title, onBack, sub, trailing, haze = haze, modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { barH = it.height })
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 26.dp, bg: Color = LocalTok.current.card, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(modifier.fillMaxWidth().clip(shape).background(bg).cardEdge(shape), content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    T(text, size = 13.sp, weight = FontWeight.SemiBold, color = LocalTok.current.soft, modifier = modifier.padding(start = 6.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
fun Hairline(start: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(LocalTok.current.hair))
}

/** A settings-style row: optional icon bubble, label, subtitle and a trailing slot. */
@Composable
fun ListRow(
    label: String,
    sub: String? = null,
    icon: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    subColor: Color? = null,
    trailing: (@Composable () -> Unit)? = { Ico(Icons.chev, LocalTok.current.faint, 16.dp, 2f) },
) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (danger) t.danger.copy(alpha = 0.12f) else t.tone), contentAlignment = Alignment.Center) {
                Ico(icon, if (danger) t.danger else t.p, 19.dp)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            T(label, size = 15.5.sp, weight = FontWeight.SemiBold, color = if (danger) t.danger else t.ink)
            if (!sub.isNullOrEmpty()) T(sub, size = 12.5.sp, color = subColor ?: t.soft, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() }
    }
}

// ---------- sheets ----------

/** A bottom sheet that springs up with a little bounce and drags down to close. */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, maxHeightFraction: Float = 0.9f, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    val motion = LocalMotion.current
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (t.dark) 0.5f else 0.28f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(if (motion) spring(dampingRatio = 0.78f, stiffness = 380f) else tween(0)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(if (motion) 220 else 0)) { it } + fadeOut(tween(200)),
        ) {
            var drag by remember { mutableFloatStateOf(0f) }
            val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            BoxWithMax(maxHeightFraction) { maxH ->
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 640.dp).heightIn(max = maxH)
                        .offset { androidx.compose.ui.unit.IntOffset(0, drag.roundToInt()) }
                        .clip(shape).background(t.sheet)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding().padding(bottom = 12.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(26.dp).pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { if (drag > 120f) onDismiss(); drag = 0f },
                                onDragCancel = { drag = 0f },
                            ) { _, dy -> drag = (drag + dy).coerceAtLeast(0f) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(t.faint)) }
                    content()
                }
            }
        }
    }
}

@Composable
private fun BoxWithMax(fraction: Float, content: @Composable (Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        content(maxHeight * fraction)
    }
}

/** The rounded group of actions used inside sheets. */
@Composable
fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(t.surface), content = content)
}

@Composable
fun SheetAction(label: String, icon: String, onClick: () -> Unit, danger: Boolean = false, first: Boolean = false, index: Int = 0) {
    val t = LocalTok.current
    Column(Modifier.enter(index)) {
        if (!first) Hairline()
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(icon, if (danger) t.danger else t.ink, 20.dp)
            Spacer(Modifier.width(16.dp))
            T(label, size = 15.5.sp, weight = FontWeight.Medium, color = if (danger) t.danger else t.ink)
        }
    }
}

// ---------- toast ----------

@Composable
fun ToastHost(toast: com.jpetrydesign.pyng.data.Toast?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTok.current
    AnimatedVisibility(
        toast != null, modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(),
    ) {
        var last by remember { mutableStateOf(toast) }
        if (toast != null) last = toast
        val cur = last ?: return@AnimatedVisibility
        Row(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 104.dp)
                .clip(RoundedCornerShape(22.dp)).background(if (t.dark) Color(0xF22A2D36) else Color(0xF215171D))
                .padding(start = 18.dp, end = if (cur.undo != null) 6.dp else 18.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(cur.text, size = 13.5.sp, weight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(vertical = 6.dp))
            if (cur.undo != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onUndo).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    T("Undo", size = 13.5.sp, weight = FontWeight.Bold, color = t.pal.pd)
                }
            }
        }
    }
}

@Composable
fun Provide(tok: Tok, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTok provides tok, content = content)
}


/** A centered yes/no card over a dimmed screen, used before anything destructive. */
@Composable
fun AskDialog(ask: com.jpetrydesign.pyng.data.Repo.Ask?, onClose: () -> Unit) {
    val t = LocalTok.current
    var last by remember { mutableStateOf(ask) }
    if (ask != null) last = ask
    if (ask != null) BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().zIndex(40f)) {
        AnimatedVisibility(ask != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (t.dark) 0.55f else 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
        }
        AnimatedVisibility(
            ask != null, modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 520f), initialScale = 0.92f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f),
        ) {
            val a = last ?: return@AnimatedVisibility
            Column(
                Modifier.padding(horizontal = 28.dp).widthIn(max = 380.dp).fillMaxWidth().edge(RoundedCornerShape(30.dp), elevation = 24.dp)
                    .clip(RoundedCornerShape(30.dp)).background(t.sheet)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
            ) {
                T(a.title, size = 19.sp, weight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                Spacer(Modifier.height(8.dp))
                T(a.body, size = 14.sp, color = t.soft, lineHeight = 20.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Cancel", onClick = onClose, modifier = Modifier.weight(1f), primary = false, height = 46.dp)
                    PillButton(a.action, onClick = { onClose(); a.go() }, modifier = Modifier.weight(1f), danger = a.danger, height = 46.dp)
                }
            }
        }
    }
}
