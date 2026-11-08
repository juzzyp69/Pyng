package com.jpetrydesign.pyng.ui.screens

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jpetrydesign.pyng.data.Hsl
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Card
import com.jpetrydesign.pyng.ui.kit.Hairline
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Page
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.SectionLabel
import com.jpetrydesign.pyng.ui.kit.Seg
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.Wallpaper
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Palettes
import com.jpetrydesign.pyng.ui.theme.color
import com.jpetrydesign.pyng.ui.theme.hex
import com.jpetrydesign.pyng.ui.theme.hexString
import com.jpetrydesign.pyng.ui.theme.hsl
import com.jpetrydesign.pyng.ui.theme.swatchOf
import com.jpetrydesign.pyng.ui.theme.toHsl
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val TS = mapOf("S" to "Small", "M" to "Medium", "L" to "Large", "XL" to "Extra large")

@Composable
fun AppearanceScreen(nav: Nav) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    var pick by remember { mutableStateOf(repo.demoOpen?.removePrefix("pick:")?.takeIf { repo.demoOpen?.startsWith("pick:") == true }.also { repo.demoOpen = null }) }
    val pal = Palettes.byKey(p.themeKey, p.c1, p.c2)
    Box(Modifier.fillMaxSize()) {
        Page("Appearance", { nav.back() }) {
            val shape = RoundedCornerShape(28.dp)
            Column(Modifier.enter(0).fillMaxWidth().clip(shape).background(t.card).clickable { nav.strip = true; nav.home() }.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(swatchOf(pal)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T("Select color", size = 16.5.sp, weight = FontWeight.SemiBold)
                        T("${pal.name} · live preview on your messages", size = 12.5.sp, color = t.soft)
                    }
                    Ico(Icons.chev, t.faint, 16.dp, 2f)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("M", "AV", "DC", "JL").forEachIndexed { i, s ->
                        if (i > 0) Spacer(Modifier.width(8.dp))
                        Avatar(null, s, 38.dp, if (i % 2 == 0) t.tone else t.neutral, if (i % 2 == 0) t.p else t.neutralInk, 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(t.outBg).padding(horizontal = 14.dp, vertical = 9.dp)) { T("Looks good!", size = 14.sp, weight = FontWeight.Medium, color = t.outInk) }
                }
            }
            SectionLabel("Appearance")
            Seg(listOf("Light", "Dark", "Match system"), p.mode, { m -> repo.prefs.edit { it.copy(mode = m) } })
            Spacer(Modifier.height(14.dp))
            Card {
                val rows = listOf(
                    Triple("pinLayout", "Pinned layout", Icons.pinned) to p.pinLayout,
                    Triple("pinStyle", "Pinned notifications", Icons.bell) to p.pinStyle,
                    Triple("bubble", "Bubble shape", Icons.message) to p.bubble,
                    Triple("textSize", "Text size", Icons.textSize) to (TS[p.textSize] ?: p.textSize),
                    Triple("showPhotos", "Contact photos", Icons.contact) to p.showPhotos,
                    Triple("density", "List density", Icons.density) to p.density,
                    Triple("appIcon", "App icon", Icons.appIcon) to p.appIcon,
                    Triple("defWall", "Default chat wallpaper", Icons.gallery) to (if (p.defWall.startsWith("photo:")) "Your photo" else p.defWall),
                )
                rows.forEachIndexed { i, (k, value) ->
                    if (i > 0) Hairline()
                    Row(Modifier.enter(i + 1).fillMaxWidth().clickable { pick = k.first }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(k.third, t.p, 19.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(k.second, size = 15.5.sp, weight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Ico(Icons.chev, t.ink, 14.dp, 2.2f)
                            }
                            T(value, size = 13.sp, weight = FontWeight.SemiBold, color = t.p)
                        }
                    }
                }
            }
        }
        StylePicker(pick) { pick = null }
    }
}

// ---------- the style picker sheet ----------

@Composable
private fun StylePicker(key: String?, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val t = LocalTok.current
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf(key) }
    if (key != null) last = key
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = repo.importUri(uri) ?: return@launch
            val dest = repo.keepFile("wall"); java.io.File(f.path).copyTo(dest, overwrite = true)
            repo.prefs.edit { it.copy(defWall = "photo:" + dest.path) }
        }
    }
    Sheet(visible = key != null, onDismiss = onDone, maxHeightFraction = 0.82f) {
        val k = last ?: return@Sheet
        val p = repo.prefs.state
        val (title, hint) = when (k) {
            "pinLayout" -> "Pinned layout" to "Hold a pinned chat, then drag to rearrange"
            "pinStyle" -> "Pinned notifications" to "How pinned chats show something new"
            "bubble" -> "Bubble shape" to "Tap a style to preview it live"
            "textSize" -> "Text size" to "Messages and your conversation list"
            "showPhotos" -> "Contact photos" to "Tap a style to preview it live"
            "density" -> "List density" to "Tap a style to preview it live"
            "appIcon" -> "App icon" to "Changes the icon on your home screen"
            else -> "Default chat wallpaper" to "Chats use this unless they have their own"
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(title, size = 19.sp, weight = FontWeight.Bold)
                T(hint, size = 12.5.sp, color = t.soft)
            }
            PillButton("Done", onDone, height = 42.dp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            data class Tile(val name: String, val sub: String?, val on: Boolean, val pick: () -> Unit, val preview: @Composable () -> Unit)
            val AV = t.p.copy(alpha = 0.35f)
            val LN = if (t.dark) Color.White.copy(alpha = 0.18f) else Color(0x1F15171D)
            fun dot(s: Dp, c: Color = AV): @Composable () -> Unit = { Box(Modifier.size(s).clip(CircleShape).background(c)) }
            val tiles: List<Tile>
            var cols = 2
            var pvH = 96.dp
            when (k) {
                "pinLayout" -> tiles = listOf(
                    "Grid" to "2 rows of 4", "Large" to "Bigger, 3 across", "Pages" to "4 per page, swipe", "Carousel" to "One scrolling row", "Compact" to "Small row, no card",
                    "Chips" to "Name pills that wrap", "Cards" to "2 across, with previews", "Spotlight" to "Top chat big, rest small", "List" to "Pinned rows on top", "Dock" to "Floating bar at the bottom",
                ).map { (n, s) -> Tile(n, s, p.pinLayout == n, { repo.prefs.edit { it.copy(pinLayout = n) } }) { PinMini(n, AV, LN) } }
                "pinStyle" -> tiles = listOf("Badges" to "Unread count on the photo", "Rings" to "A colored ring when there is news").map { (n, s) ->
                    Tile(n, s, p.pinStyle == n, { repo.prefs.edit { it.copy(pinStyle = n) } }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(46.dp)) {
                                if (n == "Rings") Box(Modifier.size(46.dp).border(2.5.dp, t.pal.plus ?: t.p, CircleShape).padding(5.dp).clip(CircleShape).background(AV))
                                else { Box(Modifier.padding(3.dp).size(40.dp).clip(CircleShape).background(AV)); Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(t.badgeBg)) }
                            }
                            Box(Modifier.padding(3.dp).size(40.dp).clip(CircleShape).background(AV))
                        }
                    }
                }
                "bubble" -> {
                    cols = 3
                    tiles = listOf("Rounded", "Soft", "Pill").map { n ->
                        val r = when (n) { "Soft" -> RoundedCornerShape(5.dp) to RoundedCornerShape(5.dp); "Pill" -> RoundedCornerShape(11.dp) to RoundedCornerShape(11.dp)
                            else -> RoundedCornerShape(9.dp, 9.dp, 9.dp, 3.dp) to RoundedCornerShape(9.dp, 9.dp, 3.dp, 9.dp) }
                        Tile(n, null, p.bubble == n, { repo.prefs.edit { it.copy(bubble = n) } }) {
                            Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(62.dp, 22.dp).clip(r.first).background(t.inBg).border(1.dp, LN, r.first))
                                Box(Modifier.align(Alignment.End).size(52.dp, 22.dp).clip(r.second).background(t.outBg))
                            }
                        }
                    }
                }
                "textSize" -> { cols = 4; pvH = 72.dp; tiles = listOf("S" to 14, "M" to 17, "L" to 21, "XL" to 26).map { (n, fs) ->
                    Tile(TS[n]!!, null, p.textSize == n, { repo.prefs.edit { it.copy(textSize = n) } }) { T("Aa", size = fs.sp, weight = FontWeight.SemiBold) }
                } }
                "showPhotos" -> tiles = listOf("Show" to "Photos next to names", "Hide" to "Initials only, cleaner list").map { (n, s) ->
                    Tile(n, s, p.showPhotos == n, { repo.prefs.edit { it.copy(showPhotos = n) } }) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            repeat(3) { Row(verticalAlignment = Alignment.CenterVertically) { if (n == "Show") { dot(18.dp)(); Spacer(Modifier.width(8.dp)) }; Box(Modifier.size(if (n == "Show") 84.dp else 110.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(LN)) } }
                        }
                    }
                }
                "density" -> tiles = listOf("Comfortable" to "More room per chat", "Compact" to "See more at once").map { (n, s) ->
                    Tile(n, s, p.density == n, { repo.prefs.edit { it.copy(density = n) } }) {
                        Column(verticalArrangement = Arrangement.spacedBy(if (n == "Compact") 4.dp else 11.dp)) { repeat(if (n == "Compact") 6 else 4) { Box(Modifier.size(104.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(LN)) } }
                    }
                }
                "appIcon" -> { cols = 3; pvH = 84.dp; tiles = AppIcons.all.map { ic ->
                    Tile(ic.name, null, p.appIcon == ic.name, { AppIcons.set(ctx, ic.name); repo.prefs.edit { it.copy(appIcon = ic.name) }; repo.toast("${ic.name} icon set on your home screen") }) { AppIconPreview(ic) }
                } }
                else -> { cols = 3; pvH = 120.dp; tiles = listOf(Tile("Your photo", null, p.defWall.startsWith("photo:"), { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (p.defWall.startsWith("photo:")) Wallpaper(p.defWall, t)
                        Box(Modifier.size(36.dp).clip(CircleShape).background(if (t.dark) Color(0xFF2A2D36) else Color.White), contentAlignment = Alignment.Center) { Ico(Icons.gallery, t.p, 17.dp) }
                    }
                }) + Palettes.walls.map { w -> Tile(w, null, p.defWall == w, { repo.prefs.edit { it.copy(defWall = w) } }) { Wallpaper(w, t) } } }
            }
            tiles.chunked(cols).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, tile ->
                        val shape = RoundedCornerShape(22.dp)
                        Column(
                            Modifier.weight(1f).enter(r * cols + i, pop = true).clip(shape).background(if (tile.on) t.tone else t.surface)
                                .then(if (tile.on) Modifier.border(2.dp, t.p, shape) else Modifier).clickable(onClick = tile.pick).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().height(pvH).clip(RoundedCornerShape(16.dp)).background(if (t.dark) Color.White.copy(alpha = 0.04f) else Color.White), contentAlignment = Alignment.Center) { tile.preview() }
                            Spacer(Modifier.height(8.dp))
                            T(tile.name, size = 13.5.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, maxLines = 1)
                            if (tile.sub != null) T(tile.sub, size = 11.sp, color = t.soft, align = TextAlign.Center, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PinMini(n: String, AV: Color, LN: Color) {
    val t = LocalTok.current
    val cd = t.card
    @Composable fun c(s: Dp) = Box(Modifier.size(s).clip(CircleShape).background(AV))
    @Composable fun box(w: Dp, h: Dp, r: Dp, bg: Color = cd, line: Boolean = true) = Box(Modifier.size(w, h).clip(RoundedCornerShape(r)).background(bg).then(if (line) Modifier.border(1.dp, LN, RoundedCornerShape(r)) else Modifier))
    when (n) {
        "Grid", "Large" -> {
            val cols = if (n == "Grid") 4 else 3; val s = if (n == "Grid") 16.dp else 24.dp
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(cd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(cols) { c(s) } } }
            }
        }
        "Pages" -> Column(Modifier.clip(RoundedCornerShape(12.dp)).background(cd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { c(18.dp) } }
            Box(Modifier.size(22.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(t.p))
        }
        "Carousel" -> Row(Modifier.width(122.dp).clip(RoundedCornerShape(12.dp)).background(cd).padding(start = 10.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { repeat(5) { c(22.dp) } }
        "Compact" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(6) { c(14.dp) } }
        "Chips" -> androidx.compose.foundation.layout.FlowRow(Modifier.width(120.dp), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(42, 30, 36, 46, 28).forEach { box(it.dp, 14.dp, 7.dp) }
        }
        "Cards" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(2) { box(44.dp, 28.dp, 7.dp) } } } }
        "Spotlight" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { box(96.dp, 28.dp, 8.dp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { box(28.dp, 24.dp, 7.dp) } } }
        "List" -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(2) { box(110.dp, 11.dp, 4.dp, AV, false) }; repeat(3) { box(110.dp, 11.dp, 4.dp, LN, false) } }
        else -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { box(110.dp, 9.dp, 4.dp, LN, false) }; box(84.dp, 18.dp, 9.dp) }
    }
}

// ---------- app icons ----------

object AppIcons {
    class Ic(val name: String, val alias: String, val bg: Brush, val fg: Color, val dot: Color?)
    val all = listOf(
        // "Frost" lives on the original default slot, so updating never moves the icon on your home screen.
        Ic("Frost", ".IconClassic", Brush.linearGradient(listOf(hex("#FFFFFF"), hex("#EEF3FF"))), hex("#2A5AE8"), null),
        Ic("Frost dark", ".IconFrostDark", Brush.linearGradient(listOf(hex("#1A2756"), hex("#0A1230"))), Color.White, null),
        Ic("Duotone", ".IconDuotone", Brush.linearGradient(listOf(hex("#1E5FD1"), hex("#1E5FD1"))), hex("#F2B33D"), null),
        Ic("Soft", ".IconSoft", Brush.linearGradient(listOf(hex("#E3ECFB"), hex("#E3ECFB"))), hex("#1E5FD1"), null),
        Ic("Mono", ".IconMono", Brush.linearGradient(listOf(hex("#15171D"), hex("#15171D"))), Color.White, null),
        Ic("Glass", ".IconGlass", Brush.linearGradient(listOf(hex("#1E5FD1"), hex("#8CB8F0"))), Color.White, null),
    )

    fun set(ctx: android.content.Context, name: String) {
        val pm = ctx.packageManager
        all.forEach { ic ->
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(ctx.packageName, ctx.packageName + ic.alias),
                    if (ic.name == name) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

@Composable
private fun AppIconPreview(ic: AppIcons.Ic) {
    // The real launcher artwork, drawn the way a round icon mask shows it.
    val key = ic.alias.removePrefix(".Icon").lowercase()
    val bgRes = when (key) { "frostdark" -> com.jpetrydesign.pyng.R.drawable.icon_frost_dark_bg; "classic" -> com.jpetrydesign.pyng.R.drawable.icon_frost_bg; "duotone" -> com.jpetrydesign.pyng.R.drawable.icon_duotone_bg; "soft" -> com.jpetrydesign.pyng.R.drawable.icon_soft_bg; "mono" -> com.jpetrydesign.pyng.R.drawable.icon_mono_bg; "glass" -> com.jpetrydesign.pyng.R.drawable.icon_glass_bg; else -> com.jpetrydesign.pyng.R.drawable.icon_classic_bg }
    val fgRes = when (key) { "frostdark" -> com.jpetrydesign.pyng.R.mipmap.icon_frost_dark_fg; "classic" -> com.jpetrydesign.pyng.R.mipmap.icon_frost_fg; "duotone" -> com.jpetrydesign.pyng.R.drawable.icon_duotone_fg; "soft" -> com.jpetrydesign.pyng.R.drawable.icon_soft_fg; "mono" -> com.jpetrydesign.pyng.R.drawable.icon_mono_fg; "glass" -> com.jpetrydesign.pyng.R.drawable.icon_glass_fg; else -> com.jpetrydesign.pyng.R.drawable.icon_classic_fg }
    Box(Modifier.size(58.dp).shadow(6.dp, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(bgRes), null, modifier = Modifier.requiredSize(58.dp * 1.5f))
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(fgRes), null, modifier = Modifier.requiredSize(58.dp * 1.5f))
    }
}

// ---------- color strip on Home ----------

@Composable
fun ColorStrip(onDone: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val motion = LocalMotion.current
    val p = repo.prefs.state
    var tab by remember { mutableStateOf(if (p.themeKey.startsWith("Custom")) "Custom" else if (Palettes.duos.any { it.name == p.themeKey }) "Duotones" else "Colors") }
    var target by remember { mutableStateOf("c1") }
    BackHandler(onBack = onDone)
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize().zIndex(15f), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(shown, enter = slideInVertically(if (motion) spring(dampingRatio = 0.78f, stiffness = 380f) else tween(0)) { it }, exit = slideOutVertically { it } + fadeOut()) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = t.shadow, spotColor = t.shadow)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(t.sheet).navigationBarsPadding().padding(top = 10.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(t.faint))
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("App color", size = 12.sp, weight = FontWeight.Medium, color = t.soft)
                        T(Palettes.byKey(p.themeKey, p.c1, p.c2).name, size = 18.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                    }
                    PillButton("Done", onDone, height = 40.dp)
                }
                Seg(listOf("Colors", "Duotones", "Custom"), tab, { tab = it }, Modifier.padding(horizontal = 20.dp))
                if (tab != "Custom") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (tab == "Duotones") Palettes.duos else Palettes.favorites + Palettes.colors).forEach { x ->
                            Swatch(x.name, swatchOf(x), p.themeKey == x.name) { repo.prefs.edit { it.copy(themeKey = x.name) } }
                        }
                    }
                } else {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val duo = p.themeKey == "Custom duotone"
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Custom color", "Custom duotone").forEach { key ->
                                val on = p.themeKey == key || (key == "Custom color" && !p.themeKey.startsWith("Custom"))
                                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (on) t.plusBg else Brush.linearGradient(listOf(t.neutral, t.neutral)))
                                    .clickable { repo.prefs.edit { it.copy(themeKey = key) } }, contentAlignment = Alignment.Center) {
                                    T(key, size = 13.5.sp, weight = FontWeight.SemiBold, color = if (on) t.plusInk else t.ink)
                                }
                            }
                        }
                        if (duo) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("c1" to "Main", "c2" to "Accent").forEach { (k, label) ->
                                val on = target == k
                                Row(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (on) t.tone else t.surface).clickable { target = k }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background((if (k == "c1") p.c1 else p.c2).color()))
                                    Spacer(Modifier.width(8.dp)); T(label, size = 13.sp, weight = FontWeight.SemiBold, color = if (on) t.p else t.soft)
                                }
                            }
                        }
                        val cur = if (duo && target == "c2") p.c2 else p.c1
                        ColorWheel(cur) { h ->
                            repo.prefs.edit { pr ->
                                val key = if (pr.themeKey.startsWith("Custom")) pr.themeKey else "Custom color"
                                if (duo && target == "c2") pr.copy(c2 = h, themeKey = key) else pr.copy(c1 = h, themeKey = key)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hue and softness on a wheel, a lightness slider, a hex field and quick colors. */
@Composable
fun ColorWheel(value: Hsl, onChange: (Hsl) -> Unit) {
    val t = LocalTok.current
    val density = LocalDensity.current
    val cur = value.color()
    var hexText by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val wheel = 168.dp
            val r = with(density) { wheel.toPx() / 2 }
            val live by androidx.compose.runtime.rememberUpdatedState(value)
            val change by androidx.compose.runtime.rememberUpdatedState(onChange)
            fun at(o: Offset) {
                val dx = o.x - r; val dy = o.y - r
                val dist = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                // Same angle as the drawn colors: red at 3 o'clock, going clockwise.
                var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (h < 0) h += 360f
                change(live.copy(h = h, s = dist)); hexText = null
            }
            Box(
                Modifier.size(wheel).pointerInput(Unit) {
                    detectTapGestures { at(it) }
                }.pointerInput(Unit) { detectDragGestures(onDragStart = { at(it) }) { ch, _ -> ch.consume(); at(ch.position) } },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val hues = List(13) { i -> hsl(i * 30f, 100f, 50f) }
                    drawCircle(Brush.sweepGradient(hues, center))
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, size.minDimension / 2))
                    val l = value.l
                    if (l < 0.5f) drawCircle(Color.Black.copy(alpha = (0.5f - l) * 1.6f))
                    else if (l > 0.5f) drawCircle(Color.White.copy(alpha = (l - 0.5f) * 1.4f))
                }
                val rad = Math.toRadians(value.h.toDouble())
                val tx = r + cos(rad).toFloat() * value.s * r
                val ty = r + sin(rad).toFloat() * value.s * r
                val th = with(density) { 12.dp.toPx() }
                Box(Modifier.offset { IntOffset((tx - th).roundToInt(), (ty - th).roundToInt()) }.size(24.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(cur))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(cur))
                Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(t.field).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("HEX", size = 11.5.sp, weight = FontWeight.Bold, color = t.soft, letterSpacing = 0.4.sp)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        hexText ?: cur.hexString(), { v ->
                            val clean = ("#" + v.uppercase().filter { it in "0123456789ABCDEF" }).take(7)
                            hexText = clean
                            if (clean.length == 7) onChange(toHsl(hex(clean)))
                        },
                        singleLine = true, cursorBrush = SolidColor(t.p),
                        textStyle = TextStyle(fontFamily = Geist, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.ink),
                        modifier = Modifier.weight(1f),
                    )
                }
                T("Drag around the wheel for hue, toward the center for softer tones", size = 12.sp, color = t.soft, lineHeight = 16.sp)
            }
        }
        T("Dark to light", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft)
        LightSlider(value) { onChange(value.copy(l = it)); hexText = null }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.quick.forEach { (name, h) ->
                val on = cur.hexString() == h
                Column(Modifier.width(46.dp).clickable { onChange(toHsl(hex(h))); hexText = null }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).then(if (on) Modifier.border(2.dp, t.ink, CircleShape).padding(3.dp) else Modifier).clip(CircleShape).background(hex(h)).border(1.dp, t.hair, CircleShape))
                    Spacer(Modifier.height(4.dp))
                    T(name, size = 10.5.sp, color = t.soft, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LightSlider(value: Hsl, onL: (Float) -> Unit) {
    val density = LocalDensity.current
    var w by remember { mutableStateOf(1f) }
    val knobPx = with(density) { 26.dp.toPx() }
    val set by androidx.compose.runtime.rememberUpdatedState(onL)
    // The knob's center follows your finger exactly, edge to edge.
    fun at(x: Float) = set(0.06f + ((x - knobPx / 2) / (w - knobPx).coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.88f)
    Box(
        Modifier.fillMaxWidth().height(30.dp)
            .onSizeChanged { w = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { at(it.x) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { at(it.x) }) { ch, _ -> ch.consume(); at(ch.position.x) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(hsl(value.h, value.s * 100, 6f), hsl(value.h, value.s * 100, 50f), hsl(value.h, value.s * 100, 94f)))))
        val frac = ((value.l - 0.06f) / 0.88f).coerceIn(0f, 1f)
        val knob = with(density) { 26.dp.toPx() }
        Box(Modifier.offset { IntOffset(((w - knob) * frac).roundToInt(), 0) }.size(26.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}
