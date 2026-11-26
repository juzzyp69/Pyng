package com.jpetrydesign.pyng.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jpetrydesign.pyng.data.AppLook
import com.jpetrydesign.pyng.data.editLook
import com.jpetrydesign.pyng.data.lookFor
import com.jpetrydesign.pyng.data.splitLooks
import com.jpetrydesign.pyng.ui.BarIcons
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Pal
import com.jpetrydesign.pyng.ui.theme.Palettes
import com.jpetrydesign.pyng.ui.theme.Tok
import com.jpetrydesign.pyng.ui.theme.color
import com.jpetrydesign.pyng.ui.theme.hex
import com.jpetrydesign.pyng.ui.theme.hexString
import com.jpetrydesign.pyng.ui.theme.swatchOf
import com.jpetrydesign.pyng.ui.theme.toHsl
import com.jpetrydesign.pyng.ui.theme.tok
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A color as the canvas edits it: hue, saturation across, brightness down. */
private data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun color(): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
}

private fun Color.hsv(): Hsv { val a = FloatArray(3); android.graphics.Color.colorToHSV(toArgb(), a); return Hsv(a[0], a[1], a[2]) }

/** What's being picked, before it's saved: the mode, one color or two (blended or not), a preset or your own, tinted or plain. */
private data class Draft(val mode: String, val style: String, val preset: String?, val tint: Boolean, val a: Hsv, val b: Hsv) {
    val key: String get() = preset ?: (when (style) { "duo" -> "Custom duotone"; "grad" -> "Custom gradient"; else -> "Custom color" } + if (tint) "" else " flat")
    fun look() = AppLook(key, toHsl(a.color()), toHsl(b.color()))
    fun pal(): Pal { val l = look(); return Palettes.byKey(l.key, l.c1, l.c2) }
    /** Two drafts that would look the same are the same. */
    val sig: String get() = mode + "|" + key + if (preset == null) "|" + a.color().hexString() + (if (style != "solid") b.color().hexString() else "") else ""
}

private fun styleOf(kind: String) = when (kind) { "Duotone" -> "duo"; "Gradient" -> "grad"; else -> "solid" }

private fun presetDraft(d: Draft, x: Pal): Draft {
    val st = styleOf(Palettes.kindOf(x.key))
    val second = if (x.inB != null) x.pd else (x.grad?.second ?: x.plus ?: x.pd)
    return d.copy(style = st, preset = x.key, tint = Palettes.plain.none { it.key == x.key }, a = x.p.hsv(), b = if (st == "solid") d.b else second.hsv())
}

private fun draftOf(mode: String, lk: AppLook): Draft {
    val base = Draft(mode, "solid", null, true, lk.c1.color().hsv(), lk.c2.color().hsv())
    if (lk.key.startsWith("Custom")) {
        val st = when (lk.key.removeSuffix(" flat")) { "Custom duotone" -> "duo"; "Custom gradient" -> "grad"; else -> "solid" }
        return base.copy(style = st, tint = !lk.key.endsWith(" flat"))
    }
    return presetDraft(base, Palettes.byKey(lk.key, lk.c1, lk.c2))
}

/** The background of a mock screen's header and its list sheet. */
private fun Tok.mockHead() = if (dark) lerp(bg, Color.White, 0.07f) else lerp(bg, Color.White, 0.7f)
private fun Tok.mockSheet() = if (dark) lerp(bg, Color.White, 0.05f) else Color.White

private val Bounce = spring<Dp>(dampingRatio = 0.68f, stiffness = 300f)
private val BounceF = spring<Float>(dampingRatio = 0.68f, stiffness = 300f)

/**
 * App appearance: a live preview on two small phones, Light / Dark / System, your color (or two, blended
 * or not, on a tinted or plain background), a canvas and hue bar to pick it, and a row of ready-made ones.
 * Nothing changes until Done; Back asks whether to keep what you changed.
 */
@Composable
fun AppColorPage(onClose: () -> Unit) {
    val repo = LocalRepo.current
    val app = LocalTok.current
    val p = repo.prefs.state
    val sysDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // With light and dark set apart, this edits the half that's showing.
    val ed = remember { (StripTarget.dark.also { StripTarget.dark = null } ?: sysDark) && p.splitLooks }
    val start = remember { draftOf(p.mode, p.lookFor(ed)) }
    var saved by remember { mutableStateOf(start) }
    var d by remember { mutableStateOf(start) }
    var slot by remember { mutableStateOf("a") }
    var live by remember { mutableStateOf<String?>(null) }
    var front by remember { mutableStateOf("chat") }
    var asking by remember { mutableStateOf(false) }
    var hexOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val dark = when (d.mode) { "Dark" -> true; "Light" -> false; else -> sysDark }
    val pal = remember(d.sig, d.key) { d.pal() }
    val solid = app.solid
    val L = remember(pal, dark, solid) { tok(pal, dark, solid) }
    val Ll = remember(pal, solid) { tok(pal, false, solid) }
    val Ld = remember(pal, solid) { tok(pal, true, solid) }
    val two = d.style != "solid"
    if (!two && slot == "b") slot = "a"
    val cur = if (slot == "b") d.b else d.a
    fun edit(h: Hsv) { d = if (slot == "b") d.copy(b = h, preset = null) else d.copy(a = h, preset = null) }

    // Sliding in and out from the side, like any other page.
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 380f)) }
    var leaving by remember { mutableStateOf(false) }
    fun close() { if (leaving) return; leaving = true; asking = false; scope.launch { slide.animateTo(1f, tween(240)); onClose() } }
    fun save() {
        val look = d.look()
        repo.prefs.edit { it.copy(mode = d.mode) }
        repo.prefs.editLook(ed) { it.copy(key = look.key, c1 = look.c1, c2 = look.c2) }
        saved = d; asking = false; saving = true
        scope.launch { delay(750); close() }
    }
    val dirty = d.sig != saved.sig
    fun back() { if (hexOpen) hexOpen = false else if (asking) asking = false else if (dirty && !saving) asking = true else close() }
    BackHandler(onBack = ::back)
    // Status bar icons follow the look you're trying, not the one behind.
    DisposableEffect(dark) { BarIcons.light = dark; onDispose { BarIcons.light = null } }

    val ink = L.ink; val soft = L.soft
    val acc = L.plusFlat; val onAcc = L.plusInk
    val sel = if (dark) acc else if (acc.luminance() > 0.45f) L.fill else acc
    val panelBg by animateColorAsState(if (dark) lerp(L.bg, Color.White, 0.075f) else Color.White, tween(320), label = "panel")
    val tile = if (dark) lerp(L.bg, Color.White, 0.1f) else hex("#F3F4F6")
    val pageBg by animateColorAsState(if (dark) lerp(L.bg, Color.Black, 0.25f) else lerp(L.bg, Color.Black, 0.035f), tween(320), label = "page")
    val headBg by animateColorAsState(if (dark) lerp(L.bg, Color.White, 0.06f) else lerp(L.bg, Color.White, 0.5f), tween(320), label = "head")

    CompositionLocalProvider(LocalTok provides L) {
        BoxWithConstraints(
            Modifier.fillMaxSize().zIndex(30f)
                .graphicsLayer { translationX = slide.value * size.width; shadowElevation = 0f }
                .background(pageBg)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
            var panelH by remember { mutableStateOf(540.dp) }
            val prevH = (maxHeight - top - panelH).coerceAtLeast(230.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(top))
                PreviewFan(L, front, prevH) { front = it }
                Column(
                    Modifier.fillMaxWidth().onSizeChanged { panelH = with(density) { it.height.toDp() } }
                        .navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 16.dp)
                        .shadow(28.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.12f), spotColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.12f))
                        .background(panelBg, RoundedCornerShape(34.dp))
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Light, Dark or System, each with a tiny look at itself.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Light" to "Light", "Dark" to "Dark", "Match system" to "System").forEach { (m, label) ->
                            val on = d.mode == m
                            val shape = RoundedCornerShape(18.dp)
                            Column(
                                Modifier.weight(1f).clip(shape).background(tile).border(if (on) 2.dp else 1.dp, if (on) sel else soft.copy(alpha = 0.16f), shape)
                                    .clickable { d = d.copy(mode = m) }.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val mini = when (m) { "Light" -> Brush.linearGradient(listOf(Ll.bg, Ll.bg)); "Dark" -> Brush.linearGradient(listOf(Ld.bg, Ld.bg))
                                    else -> Brush.linearGradient(0f to Ll.bg, 0.5f to Ll.bg, 0.5f to Ld.bg, 1f to Ld.bg) }
                                Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(mini).border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(12.dp))) {
                                    val inT = if (m == "Dark") Ld else Ll; val outT = if (m == "Light") Ll else Ld
                                    Box(Modifier.padding(start = 8.dp, top = 7.dp).fillMaxWidth(0.46f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(inT.inBg.compositeOver(inT.bg)))
                                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 7.dp).fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(outT.outTop, outT.outBottom))))
                                }
                                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.size(16.dp).border(1.6.dp, if (on) sel else soft.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                                        if (on) Box(Modifier.size(7.dp).clip(CircleShape).background(sel))
                                    }
                                    T(label, size = 14.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) ink else soft, maxLines = 1)
                                }
                            }
                        }
                    }
                    ColorChips(d, slot, two, tile, panelBg, sel, ink, soft,
                        onTint = { d = d.copy(tint = !d.tint, preset = null) },
                        onSlot = { slot = it },
                        onAdd = {
                            val b = if (d.b.color().hexString() == d.a.color().hexString()) Hsv((d.a.h + 150f) % 360f, 0.55f, 0.95f) else d.b
                            d = d.copy(style = "duo", preset = null, b = b); slot = "b"
                        },
                        onDrop = { d = d.copy(style = "solid", preset = null); slot = "a" },
                        onBlend = { d = d.copy(style = if (d.style == "grad") "duo" else "grad", preset = null) },
                    )
                    // The canvas and the hue bar, together in one card.
                    Column(Modifier.fillMaxWidth().background(tile, RoundedCornerShape(26.dp)).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SatCanvas(cur, live == "c", { live = if (it) "c" else null }, ::edit) { hexOpen = true }
                        HueBar(cur, live == "h", { live = if (it) "h" else null }, ::edit)
                    }
                    Presets(d, soft, ink) { x -> d = presetDraft(d, x); slot = "a" }
                }
            }
            // Header: back, the title and Done.
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(headBg, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                    .statusBarsPadding().height(64.dp).padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Icons.back, "Back", ::back, size = 48.dp, iconSize = 24.dp)
                T("App appearance", size = 24.sp, weight = FontWeight.Bold, letterSpacing = (-0.4).sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
                val padH by animateDpAsState(if (saving) 18.dp else 22.dp, Bounce, label = "donePad")
                Row(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(acc)
                        .clickable { if (!saving) { if (dirty) save() else close() } }.padding(horizontal = padH),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(saving, enter = expandHorizontally(spring(dampingRatio = 0.6f, stiffness = 380f)) + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        Row { Ico(Icons.check, onAcc, 18.dp, 2.8f); Spacer(Modifier.width(6.dp)) }
                    }
                    T(if (saving) "Saved" else "Done", size = 16.sp, weight = FontWeight.SemiBold, color = onAcc)
                }
            }
            if (asking) PopDialog(panelBg, onDismiss = { asking = false }) {
                T("Keep your changes?", size = 21.sp, weight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogButton("Discard", tile, ink, Modifier.weight(1f)) { d = saved; close() }
                    DialogButton("Save", acc, onAcc, Modifier.weight(1f)) { save() }
                }
            }
            if (hexOpen) HexDialog(cur.color(), panelBg, tile, acc, onAcc, ink, onCancel = { hexOpen = false }) { c -> edit(c.hsv()); hexOpen = false }
        }
    }
}

/** Tint, then your color, then a second one (with a remove badge) and Blend, or a + to add it. They slide to stay centered. */
@Composable
private fun ColorChips(
    d: Draft, slot: String, two: Boolean, tile: Color, panelBg: Color, sel: Color, ink: Color, soft: Color,
    onTint: () -> Unit, onSlot: (String) -> Unit, onAdd: () -> Unit, onDrop: () -> Unit, onBlend: () -> Unit,
) {
    val A = d.a.color(); val B = d.b.color()
    val bW by animateDpAsState(if (two) 44.dp else 0.dp, Bounce, label = "bW")
    val bM by animateDpAsState(if (two) 10.dp else 0.dp, Bounce, label = "bM")
    val blW by animateDpAsState(if (two) 112.dp else 0.dp, Bounce, label = "blW")
    val blM by animateDpAsState(if (two) 12.dp else 0.dp, Bounce, label = "blM")
    val pW by animateDpAsState(if (two) 0.dp else 44.dp, Bounce, label = "pW")
    val pM by animateDpAsState(if (two) 0.dp else 10.dp, Bounce, label = "pM")
    val twoS by animateFloatAsState(if (two) 1f else 0.3f, BounceF, label = "twoS")
    val twoA by animateFloatAsState(if (two) 1f else 0f, tween(200), label = "twoA")
    val tintS by animateFloatAsState(if (d.tint) 1f else 0.92f, BounceF, label = "tintS")
    val grad = d.style == "grad"
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val chip = RoundedCornerShape(22.dp)
        Row(
            Modifier.height(44.dp).clip(chip).background(if (d.tint) sel.copy(alpha = 0.12f) else Color.Transparent)
                .border(if (d.tint) 2.dp else 1.5.dp, if (d.tint) sel else soft.copy(alpha = 0.3f), chip).clickable(onClick = onTint)
                .padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(22.dp).graphicsLayer { scaleX = tintS; scaleY = tintS }.clip(CircleShape)
                    .background(if (d.tint) sel else Color.Transparent)
                    .then(if (d.tint) Modifier else Modifier.border(2.dp, soft.copy(alpha = 0.45f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (d.tint) Ico(Icons.check, if (sel.luminance() > 0.45f) hex("#10131A") else Color.White, 14.dp, 3.4f) }
            T("Tint", size = 14.5.sp, weight = FontWeight.SemiBold, color = if (d.tint) ink else soft)
        }
        Box(Modifier.padding(horizontal = 12.dp).size(1.dp, 28.dp).background(soft.copy(alpha = 0.22f)))
        ColorDot(A, slot == "a", panelBg, ink) { onSlot("a") }
        Box(Modifier.padding(start = bM.coerceAtLeast(0.dp)).width(bW.coerceAtLeast(0.dp)).height(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(44.dp).graphicsLayer { scaleX = twoS; scaleY = twoS; alpha = twoA }) {
                ColorDot(B, slot == "b", panelBg, ink, enabled = two) { onSlot("b") }
                Box(
                    Modifier.align(Alignment.TopEnd).offset(7.dp, (-7).dp).size(22.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(panelBg)
                        .clickable(enabled = two, onClick = onDrop),
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.close, ink, 12.dp, 3f) }
            }
        }
        Box(Modifier.padding(start = blM.coerceAtLeast(0.dp)).width(blW.coerceAtLeast(0.dp)).height(48.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) {
            Row(
                Modifier.requiredWidth(112.dp).height(44.dp).graphicsLayer { alpha = twoA }.clip(chip)
                    .background(if (grad) sel.copy(alpha = 0.12f) else Color.Transparent)
                    .border(if (grad) 2.dp else 1.5.dp, if (grad) sel else soft.copy(alpha = 0.3f), chip)
                    .clickable(enabled = two, onClick = onBlend).padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(30.dp, 12.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (grad) Brush.horizontalGradient(listOf(A, B)) else Brush.horizontalGradient(0f to A, 0.5f to A, 0.5f to B, 1f to B))
                    .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp)))
                T("Blend", size = 14.5.sp, weight = FontWeight.SemiBold, color = if (grad) ink else soft, maxLines = 1)
            }
        }
        Box(Modifier.padding(start = pM.coerceAtLeast(0.dp)).width(pW.coerceAtLeast(0.dp)).height(44.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.requiredSize(44.dp).graphicsLayer { scaleX = 1.3f - twoS; scaleY = 1.3f - twoS; rotationZ = -90f * (twoS - 0.3f) / 0.7f; alpha = 1f - twoA }
                    .clip(CircleShape).border(1.5.dp, soft.copy(alpha = 0.4f), CircleShape).clickable(enabled = !two, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Ico(Icons.plus, soft, 20.dp, 2.4f) }
        }
    }
}

@Composable
private fun ColorDot(c: Color, on: Boolean, gap: Color, ink: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "ring")
    Box(
        Modifier.size(44.dp).drawBehind {
            if (ring > 0f) drawCircle(ink.copy(alpha = ring), radius = size.minDimension / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx()))
        }.clip(CircleShape).background(c).border(1.dp, Color.Black.copy(alpha = 0.12f * (1f - ring)), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** Saturation across and brightness down. Drag anywhere to pick; tap the dot itself to type a hex code. */
@Composable
private fun SatCanvas(cur: Hsv, live: Boolean, onLive: (Boolean) -> Unit, onChange: (Hsv) -> Unit, onTapKnob: () -> Unit) {
    val density = LocalDensity.current
    val now by rememberUpdatedState(cur)
    val change by rememberUpdatedState(onChange)
    val liveFn by rememberUpdatedState(onLive)
    val tap by rememberUpdatedState(onTapKnob)
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
        val w = with(density) { maxWidth.toPx() }; val h = with(density) { 150.dp.toPx() }
        Canvas(
            Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).pointerInput(Unit) {
                fun apply(o: Offset) = change(now.copy(s = (o.x / size.width).coerceIn(0f, 1f), v = (1f - o.y / size.height).coerceIn(0f, 1f)))
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val k = Offset(now.s * size.width, (1f - now.v) * size.height)
                    val hit = (down.position - k).getDistance() < 22.dp.toPx()
                    var moved = false
                    if (!hit) { apply(down.position); liveFn(true) }
                    down.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (hit && !moved && (ch.position - down.position).getDistance() < 6.dp.toPx()) continue
                        if (!moved) { moved = true; liveFn(true) }
                        apply(ch.position); ch.consume()
                    }
                    liveFn(false)
                    if (hit && !moved) tap()
                }
            },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Hsv(cur.h, 1f, 1f).color())))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        Box(Modifier.matchParentSize().border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(18.dp)))
        Knob(cur.color(), live, cur.s * w, (1f - cur.v) * h)
        HexBubble(live, cur.color(), (cur.s * w).coerceIn(with(density) { 52.dp.toPx() }, w - with(density) { 52.dp.toPx() }), (1f - cur.v) * h)
    }
}

/** The hue bar under the canvas. */
@Composable
private fun HueBar(cur: Hsv, live: Boolean, onLive: (Boolean) -> Unit, onChange: (Hsv) -> Unit) {
    val density = LocalDensity.current
    val now by rememberUpdatedState(cur)
    val change by rememberUpdatedState(onChange)
    val liveFn by rememberUpdatedState(onLive)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(26.dp)) {
        val w = with(density) { maxWidth.toPx() }; val h = with(density) { 26.dp.toPx() }
        val hues = remember { listOf(0, 40, 80, 120, 160, 200, 240, 280, 320, 359).map { Hsv(it.toFloat(), 0.9f, 1f).color() } }
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(13.dp)).background(Brush.horizontalGradient(hues)).pointerInput(Unit) {
                fun apply(x: Float) = change(now.copy(h = (x / size.width).coerceIn(0f, 1f) * 359f))
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    apply(down.position.x); liveFn(true); down.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        apply(ch.position.x); ch.consume()
                    }
                    liveFn(false)
                }
            },
        )
        val x = cur.h / 359f * w
        Knob(Hsv(cur.h, 0.9f, 1f).color(), live, x, h / 2)
        HexBubble(live, cur.color(), x.coerceIn(with(density) { 52.dp.toPx() }, w - with(density) { 52.dp.toPx() }), h / 2)
    }
}

/** A white-ringed dot centered on (x, y), a little bigger while you drag it. */
@Composable
private fun Knob(c: Color, live: Boolean, x: Float, y: Float) {
    val density = LocalDensity.current
    val k = with(density) { 30.dp.toPx() }
    val s by animateFloatAsState(if (live) 1.18f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "knob")
    Box(
        Modifier.offset { IntOffset((x - k / 2).toInt(), (y - k / 2).toInt()) }.size(30.dp)
            .graphicsLayer { scaleX = s; scaleY = s }.shadow(5.dp, CircleShape).clip(CircleShape).background(Color.White).padding(3.5.dp).clip(CircleShape).background(c),
    )
}

/** The hex code riding above your finger while you drag. */
@Composable
private fun HexBubble(on: Boolean, c: Color, x: Float, y: Float) {
    val density = LocalDensity.current
    val sc by animateFloatAsState(if (on) 1f else 0.4f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "bubS")
    val al by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "bubA")
    if (al <= 0.01f) return
    val lift = with(density) { 22.dp.toPx() }
    Row(
        Modifier.zIndex(6f)
            .layout { m, _ ->
                val pl = m.measure(Constraints())
                layout(0, 0) { pl.place((x - pl.width / 2f).toInt(), (y - lift - pl.height).toInt()) }
            }
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = al; transformOrigin = TransformOrigin(0.5f, 1f) }
            .shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color(0xDB14161C))
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(c).border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape))
        T(c.hexString(), size = 13.5.sp, weight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

/** Ready-made colors for the style you're on, in color order, in one row that scrolls sideways. */
@Composable
private fun Presets(d: Draft, soft: Color, ink: Color, onPick: (Pal) -> Unit) {
    AnimatedContent(d.style, label = "presets", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { st ->
        val list = when (st) { "duo" -> Palettes.duos; "grad" -> Palettes.grads; else -> Palettes.solids }
        val sc = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(st) {
            val i = list.indexOfFirst { it.key == d.preset }
            if (i > 2) sc.scrollTo(with(density) { ((i - 2) * 58).dp.toPx() }.toInt())
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).horizontalScroll(sc), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                list.forEach { x ->
                    val on = d.preset == x.key
                    val s by animateFloatAsState(if (on) 0.9f else 1f, BounceF, label = "preset")
                    Box(
                        Modifier.size(52.dp).border(2.5.dp, if (on) ink else Color.Transparent, CircleShape).clip(CircleShape).clickable { onPick(x) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(42.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(swatchOf(x)).border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(4.dp)) {
                val max = sc.maxValue.takeIf { it in 1 until Int.MAX_VALUE } ?: 0
                val vw = sc.viewportSize.toFloat()
                if (max > 0 && vw > 0f) {
                    val frac = vw / (vw + max)
                    val thumb = maxWidth * frac
                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(2.dp)).background(soft.copy(alpha = 0.16f)))
                    Box(Modifier.offset(x = (maxWidth - thumb) * (sc.value.toFloat() / max)).width(thumb).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(soft.copy(alpha = 0.55f)))
                }
            }
        }
    }
}

/** Two small phones, one in front of the other: the chat and Home in the look you're trying. Tap the one behind to bring it forward. */
@Composable
private fun PreviewFan(L: Tok, front: String, height: Dp, onFront: (String) -> Unit) {
    val scale = ((height.value - 20f) / 350f).coerceIn(0.4f, 0.86f)
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(340.dp, 350.dp).graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, 0f) }) {
            listOf("home" to true, "chat" to false).sortedBy { if (it.first == front) 1 else 0 }.forEach { (which, right) -> androidx.compose.runtime.key(which) {
                val isFront = which == front
                val tx by animateFloatAsState(if (isFront) (if (right) 66f else -66f) else (if (right) 74f else -74f), BounceF, label = "fx$which")
                val ty by animateFloatAsState(if (isFront) 6f else -4f, BounceF, label = "fy$which")
                val rot by animateFloatAsState(if (isFront) (if (right) 3f else -4f) else (if (right) 7f else -8f), BounceF, label = "fr$which")
                val sc by animateFloatAsState(if (isFront) 1f else 0.92f, BounceF, label = "fs$which")
                val dim by animateFloatAsState(if (isFront) 0f else (if (L.dark) 0.2f else 0.04f), tween(300), label = "fd$which")
                val shape = RoundedCornerShape(34.dp)
                Box(
                    Modifier.align(Alignment.TopCenter).size(172.dp, 340.dp)
                        .graphicsLayer { translationX = tx.dp.toPx(); translationY = ty.dp.toPx(); rotationZ = rot; scaleX = sc; scaleY = sc }
                        .shadow(22.dp, shape, ambientColor = Color.Black.copy(alpha = if (L.dark) 0.55f else 0.22f), spotColor = Color.Black.copy(alpha = if (L.dark) 0.55f else 0.22f))
                        .clip(shape).background(if (L.dark) hex("#2A2D33") else hex("#16181D"))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFront(which) }
                        .padding(6.dp),
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) {
                        if (which == "chat") ChatMock(L) else HomeMock(L)
                        if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
                    }
                }
            } }
        }
    }
}

@Composable
private fun MockLine(frac: Float, h: Dp, c: Color, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth(frac).height(h).clip(RoundedCornerShape(4.dp)).background(c))

/** A tiny chat: header, a photo, two bubbles in and two out, and the message box. */
@Composable
fun ChatMock(L: Tok, modifier: Modifier = Modifier) {
    val acc = L.plusFlat
    val head = L.mockHead()
    val inB = L.inBg.compositeOver(L.bg)
    val inLine = if (L.dark) Color.White.copy(alpha = 0.26f) else L.inInk.copy(alpha = 0.2f)
    val outLine = if (L.outInk.luminance() > 0.5f) Color.White.copy(alpha = 0.5f) else hex("#10131A").copy(alpha = 0.32f)
    val out = Brush.verticalGradient(listOf(L.outTop, L.outBottom))
    Column(modifier.fillMaxSize().background(L.bg)) {
        Row(
            Modifier.fillMaxWidth().background(head, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)).padding(start = 10.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Ico(Icons.back, L.soft, 13.dp, 2.6f)
            Box(Modifier.size(26.dp).clip(CircleShape).background(lerp(L.bg, acc, 0.55f)))
            MockLine(0.42f, 8.dp, L.ink.copy(alpha = if (L.dark) 0.55f else 0.6f))
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)) {
            val sky = lerp(L.bg, acc, if (L.dark) 0.32f else 0.28f)
            val hill = lerp(L.bg, L.fill, if (L.dark) 0.45f else 0.42f)
            val hill2 = lerp(L.bg, L.fill, 0.62f)
            Canvas(Modifier.fillMaxWidth(0.66f).aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)).background(sky)) {
                val kx = size.width / 150f; val ky = size.height / 100f
                drawCircle(if (L.dark) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.85f), 11f * kx, Offset(112f * kx, 30f * ky))
                fun poly(vararg pts: Float, c: Color) {
                    val path = Path().apply { moveTo(pts[0] * kx, pts[1] * ky); var i = 2; while (i < pts.size) { lineTo(pts[i] * kx, pts[i + 1] * ky); i += 2 }; close() }
                    drawPath(path, c)
                }
                poly(0f, 100f, 0f, 72f, 38f, 44f, 70f, 70f, 96f, 52f, 150f, 86f, 150f, 100f, c = hill)
                poly(0f, 100f, 0f, 86f, 52f, 66f, 104f, 84f, 150f, 74f, 150f, 100f, c = hill2)
            }
            @Composable fun bub(w: Float, fill: Brush, line: Color, end: Boolean) =
                Box(Modifier.align(if (end) Alignment.End else Alignment.Start).fillMaxWidth(w).clip(RoundedCornerShape(18.dp)).background(fill).padding(horizontal = 14.dp, vertical = 11.dp)) { MockLine(1f, 7.dp, line) }
            bub(0.62f, SolidColor(inB), inLine, false)
            bub(0.40f, SolidColor(inB), inLine, false)
            bub(0.58f, out, outLine, true)
            bub(0.36f, out, outLine, true)
            Box(Modifier.align(Alignment.End).padding(end = 6.dp, top = 1.dp).size(26.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(acc))
        }
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 9.dp).fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp)).background(L.field.compositeOver(L.bg))
                .padding(start = 13.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MockLine(0.38f, 6.dp, L.soft.copy(alpha = 0.45f))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(26.dp).clip(CircleShape).background(acc))
        }
    }
}

/** A tiny Home: the title, pinned chats and a few rows with unread dots. */
@Composable
fun HomeMock(L: Tok, modifier: Modifier = Modifier, rows: Int = 5, pins: Int = 3) {
    val acc = L.plusFlat
    val head = L.mockHead()
    val avs = listOf(lerp(L.bg, L.fill, 0.45f), lerp(L.bg, acc, 0.5f), lerp(L.bg, L.ink, if (L.dark) 0.22f else 0.16f), lerp(L.bg, L.fill, 0.25f), lerp(L.bg, acc, 0.28f), lerp(L.bg, L.ink, 0.1f))
    Column(modifier.fillMaxSize().background(head)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MockLine(0.6f, 11.dp, L.ink.copy(alpha = if (L.dark) 0.75f else 0.8f), Modifier.weight(1f))
                Box(Modifier.size(22.dp).clip(CircleShape).background(lerp(L.bg, L.ink, if (L.dark) 0.22f else 0.14f)))
            }
            MockLine(0.34f, 6.dp, acc.copy(alpha = 0.9f))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(pins) { j ->
                    Box(Modifier.size(38.dp).drawBehind {
                        if (j == 0) drawCircle(acc, radius = size.minDimension / 2 + 3.5.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }.clip(CircleShape).background(avs[(j + 1) % avs.size]))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().background(L.mockSheet(), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).padding(start = 12.dp, end = 12.dp, top = 6.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp).size(26.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(L.soft.copy(alpha = 0.3f)))
            val w1 = listOf(0.56f, 0.44f, 0.62f, 0.38f, 0.5f, 0.46f); val w2 = listOf(0.78f, 0.64f, 0.7f, 0.84f, 0.58f, 0.72f)
            repeat(rows) { i ->
                val un = i < 2
                Row(Modifier.height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(avs[i % avs.size]))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MockLine(w1[i % 6], 7.dp, L.ink.copy(alpha = if (un) (if (L.dark) 0.6f else 0.62f) else (if (L.dark) 0.3f else 0.28f)))
                        MockLine(w2[i % 6], 6.dp, L.soft.copy(alpha = if (un) 0.42f else 0.26f))
                    }
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (un) acc else Color.Transparent))
                }
            }
        }
    }
}

/** A card over a dimmed page that pops in. */
@Composable
private fun PopDialog(bg: Color, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 420f)) }
    Box(Modifier.fillMaxSize().zIndex(8f).background(Color.Black.copy(alpha = 0.34f * a.value.coerceIn(0f, 1f)))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        .imePadding().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { val s = 0.92f + 0.08f * a.value; scaleX = s; scaleY = s; alpha = a.value.coerceIn(0f, 1f) }
                .shadow(30.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) { content() }
    }
}

@Composable
private fun DialogButton(label: String, bg: Color, ink: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.height(50.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f }.clip(RoundedCornerShape(25.dp)).background(bg).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        T(label, size = 16.sp, weight = FontWeight.SemiBold, color = ink)
    }
}

private fun hexFull(s: String): Color? {
    val d = s.filter { it in "0123456789ABCDEFabcdef" }
    val six = when (d.length) { 3 -> d.map { "$it$it" }.joinToString(""); 6 -> d; else -> return null }
    return hex("#" + six.uppercase())
}

/** Type a hex code: the dot shows it as you go; Apply takes three or six digits. */
@Composable
private fun HexDialog(c: Color, bg: Color, tile: Color, acc: Color, onAcc: Color, ink: Color, onCancel: () -> Unit, onApply: (Color) -> Unit) {
    var draft by remember { mutableStateOf(c.hexString().removePrefix("#")) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() } }
    val ok = hexFull(draft)
    PopDialog(bg, onCancel) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(ok ?: c).border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape))
            Row(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(27.dp)).background(tile).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                T("#", size = 21.sp, weight = FontWeight.SemiBold, color = ink.copy(alpha = 0.45f))
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    draft, { v -> draft = v.filter { it in "0123456789ABCDEFabcdef" }.take(6).uppercase() },
                    singleLine = true, cursorBrush = SolidColor(ink),
                    textStyle = TextStyle(fontFamily = Geist, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = ink, letterSpacing = 1.5.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { ok?.let(onApply) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancel", tile, ink, Modifier.weight(1f), onClick = onCancel)
            DialogButton("Apply", acc, onAcc, Modifier.weight(1f), enabled = ok != null) { ok?.let(onApply) }
        }
    }
}
