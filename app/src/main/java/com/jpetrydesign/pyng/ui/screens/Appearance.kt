package com.jpetrydesign.pyng.ui.screens

import androidx.compose.animation.slideInHorizontally
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.theme.Tok
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.draw.drawBehind
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
import kotlin.math.min
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
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
    val demo = remember { repo.demoOpen.also { repo.demoOpen = null } }
    var pick by remember { mutableStateOf(demo?.removePrefix("pick:")?.takeIf { demo.startsWith("pick:") }) }
    var studio by remember { mutableStateOf(demo?.removePrefix("studio:")?.takeIf { demo.startsWith("studio:") }) }
    val pal = Palettes.byKey(p.themeKey, p.c1, p.c2)
    Box(Modifier.fillMaxSize()) {
        Page("Appearance", { nav.back() }) {
            // App theme: a live sample of your chats in the current color, a quick row of favorites, and Change for the full set.
            val shape = RoundedCornerShape(28.dp)
            val quick = listOf("Sage", "Ocean", "Forest", "Burgundy", "Cobalt", "Terracotta", "Plum", "Rose", "Mocha")
                .let { if (pal.name in it) it else listOf(pal.name) + it.dropLast(1) }
                .mapNotNull { n -> if (n == pal.name) pal else Palettes.all.firstOrNull { it.name == n } }
            SectionLabel("App theme")
            Column(Modifier.enter(0).fillMaxWidth().clip(shape).background(if (t.dark) t.card else Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(t.tone, if (t.dark) t.surface else t.bg)))
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.clip(RoundedCornerShape(19.dp)).background(t.inBg).padding(horizontal = 14.dp, vertical = 8.dp)) { T("Dinner at 7?", size = 14.sp, color = t.inInk) }
                    Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(19.dp)).background(Brush.verticalGradient(listOf(t.outTop, androidx.compose.ui.graphics.lerp(t.outTop, t.outBottom, 0.5f)))).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        T("Sounds perfect", size = 14.sp, color = t.outInk)
                    }
                    Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(19.dp)).background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(t.outTop, t.outBottom, 0.5f), t.outBottom))).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        T("I'll bring dessert", size = 14.sp, color = t.outInk)
                    }
                }
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(pal.name, size = 20.sp, weight = FontWeight.Bold)
                        T("Bubbles, buttons and highlights", size = 13.sp, color = t.soft)
                    }
                    PillButton("Change", { nav.openStrip() }, height = 44.dp)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    quick.forEach { x ->
                        val on = x.name == pal.name
                        Box(
                            Modifier.size(32.dp).then(if (on) Modifier.border(2.dp, t.p, CircleShape) else Modifier).padding(if (on) 4.dp else 3.dp)
                                .clip(CircleShape).background(swatchOf(x)).clickable { repo.prefs.edit { it.copy(themeKey = x.name) } },
                        )
                    }
                }
            }
            SectionLabel("Mode")
            Seg(listOf("Light", "Dark", "Match system"), p.mode, { m -> repo.prefs.edit { it.copy(mode = m) } })
            // Style studio: open Chat style or Home style to see every choice with a live preview.
            SectionLabel("Style studio")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioCard("Chat style", listOf(p.bubble, p.opt("photoGroups"), p.opt("statusStyle").let { if (it == "Off") "No status" else it }).joinToString(" · "), Modifier.weight(1f).enter(1)) { studio = "chat" }
                StudioCard("Home style", listOf(p.pinLayout, p.pinStyle, p.density).joinToString(" · "), Modifier.weight(1f).enter(2)) { studio = "home" }
            }
            Spacer(Modifier.height(14.dp))
            Card {
                Row(Modifier.enter(3).fillMaxWidth().clickable { pick = "appIcon" }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconPreview(AppIcons.key(p.appIcon, AppIcons.colorFor(p, p.appIcon)), 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T("App icon", size = 15.5.sp, weight = FontWeight.SemiBold)
                        T(p.appIcon + (AppIcons.colorFor(p, p.appIcon)?.let { " · $it" } ?: ""), size = 13.sp, weight = FontWeight.SemiBold, color = t.p)
                    }
                    Ico(Icons.chev, t.faint, 16.dp, 2f)
                }
            }
        }
        StylePicker(pick) { pick = null }
        StyleStudio(studio) { studio = null }
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
            "pinShape" -> "Photo shape" to "The shape of pinned chat photos"
            "pinNames" -> "Names" to "How names show under pinned chats"
            "pinBack" -> "Backdrop" to "What sits behind your pinned chats"
            "bubble" -> "Bubble shape" to "Tap a style to preview it live"
            "gradient" -> "Bubble gradient" to "How color flows through the bubbles you send"
            "sendStyle" -> "Send animation" to "How a message leaves the box when you send it"
            "reactStyle" -> "Reactions" to "How you see who reacted to a message"
            "textSize" -> "Text size" to "Messages and your conversation list"
            "font" -> "Font" to "Used everywhere in Pyng"
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
            StyleTiles(k) { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
    }
}

/** The choices for one style as a grid of tiles with little previews. Used by the picker sheet and the style studio. */
@Composable
fun StyleTiles(k: String, pickWall: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val t = LocalTok.current
    val p = repo.prefs.state
    Column {
            data class Tile(val name: String, val sub: String?, val on: Boolean, val pick: () -> Unit, val preview: @Composable () -> Unit)
            val AV = t.p.copy(alpha = 0.35f)
            val LN = if (t.dark) Color.White.copy(alpha = 0.18f) else Color(0x1F15171D)
            fun dot(s: Dp, c: Color = AV): @Composable () -> Unit = { Box(Modifier.size(s).clip(CircleShape).background(c)) }
            val tiles: List<Tile>
            var cols = 2
            var pvH = 96.dp
            var heads: List<String>? = null
            when (k) {
                "pinLayout" -> tiles = listOf(
                    "Grid" to "2 rows of 4", "Large" to "Bigger, 3 across", "Pages" to "4 per page, swipe", "Carousel" to "One scrolling row", "Compact" to "Small row, no card",
                    "Chips" to "Name pills that wrap", "Cards" to "2 across, with previews", "Spotlight" to "Top chat big, rest small", "List" to "Pinned rows on top", "Dock" to "Floating bar at the bottom",
                    "Honeycomb" to "Rows that nest",
                ).map { (n, s) -> Tile(n, s, p.pinLayout == n, { repo.prefs.edit { it.copy(pinLayout = n) } }) { PinMini(n, AV, LN) } }
                "pinStyle" -> { cols = 3; pvH = 76.dp; tiles = listOf("Badges" to "Count", "Rings" to "Ring", "Glow" to "Soft light", "Dot" to "Small dot", "Pill" to "Name pill").map { (n, sub) ->
                    Tile(if (n == "Pill") "Name pill" else n, sub, p.pinStyle == n, { repo.prefs.edit { it.copy(pinStyle = n) } }) { PinSample(pinLookOf(p).copy(sign = n)) }
                } }
                "pinShape" -> { cols = 3; pvH = 76.dp; tiles = listOf("Circle", "Squircle", "Rounded", "Bubble", "Framed").map { n ->
                    Tile(if (n == "Bubble") "Chat bubble" else n, null, pinLookOf(p).shape == n, { repo.prefs.setOpt("pinShape", n) }) { PinSample(pinLookOf(p).copy(shape = n)) }
                } }
                "pinNames" -> { cols = 3; pvH = 76.dp; tiles = listOf("Below" to "Below", "Bold" to "Bold when new", "Chip" to "In a chip", "Over" to "On the photo", "Hidden" to "Hidden").map { (n, label) ->
                    Tile(label, null, pinLookOf(p).names == n, { repo.prefs.setOpt("pinNames", n) }) { PinSample(pinLookOf(p).copy(names = n)) }
                } }
                "pinBack" -> { cols = 3; pvH = 76.dp; tiles = listOf("None" to "None", "Frosted" to "Frosted card", "Tray" to "Tinted tray", "Tiles" to "Own tiles", "Band" to "Color band").map { (n, label) ->
                    Tile(label, null, pinLookOf(p).back == n, { repo.prefs.setOpt("pinBack", n) }) { PinSample(pinLookOf(p).copy(back = n)) }
                } }
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
                "font" -> { cols = 3; pvH = 72.dp; tiles = listOf(
                    Triple("Google", "Google Sans", com.jpetrydesign.pyng.ui.theme.GoogleSans),
                    Triple("Phone", "Your phone's", androidx.compose.ui.text.font.FontFamily.Default),
                    Triple("Pyng", "Geist", com.jpetrydesign.pyng.ui.theme.PyngFont),
                ).map { (n, label, fam) ->
                    Tile(label, null, p.opt("font") == n, { repo.prefs.setOpt("font", n) }) {
                        androidx.compose.material3.Text("Aa", style = androidx.compose.ui.text.TextStyle(fontFamily = fam, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = t.ink))
                    }
                } }
                "showPhotos" -> tiles = listOf("Show" to "Photos next to names", "Hide" to "Initials only, cleaner list").map { (n, s) ->
                    Tile(n, s, p.showPhotos == n, { repo.prefs.edit { it.copy(showPhotos = n) } }) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            repeat(3) { Row(verticalAlignment = Alignment.CenterVertically) { if (n == "Show") { dot(18.dp)(); Spacer(Modifier.width(8.dp)) }; Box(Modifier.size(if (n == "Show") 84.dp else 110.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(LN)) } }
                        }
                    }
                }
                "gradient" -> tiles = listOf(
                    "Across the screen" to "Lighter at the top, deeper below",
                    "Each bubble" to "Every bubble has its own",
                    "Off" to "One solid color",
                ).map { (n, s) ->
                    Tile(n, s, p.opt("gradient") == n, { repo.prefs.setOpt("gradient", n) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.End) {
                            listOf(0, 1, 2).forEach { i ->
                                val a = androidx.compose.ui.graphics.lerp(t.outTop, t.outBottom, i / 3f)
                                val b = androidx.compose.ui.graphics.lerp(t.outTop, t.outBottom, (i + 1) / 3f)
                                val fill = when (n) {
                                    "Across the screen" -> Brush.verticalGradient(listOf(a, b))
                                    "Each bubble" -> Brush.verticalGradient(listOf(t.outTop, t.outBottom))
                                    else -> Brush.linearGradient(listOf(t.outFlat, t.outFlat))
                                }
                                Box(Modifier.size(width = (54 + i * 10).dp, height = 18.dp).clip(RoundedCornerShape(9.dp)).background(fill))
                            }
                        }
                    }
                }
                "density" -> tiles = listOf("Comfortable" to "More room per chat", "Compact" to "See more at once").map { (n, s) ->
                    Tile(n, s, p.density == n, { repo.prefs.edit { it.copy(density = n) } }) {
                        Column(verticalArrangement = Arrangement.spacedBy(if (n == "Compact") 4.dp else 11.dp)) { repeat(if (n == "Compact") 6 else 4) { Box(Modifier.size(104.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(LN)) } }
                    }
                }
                "sendStyle" -> { cols = 2; pvH = 84.dp; heads = listOf("Classic", "Liquid")
                    // Two columns: classic styles on the left, liquid ones on the right.
                    tiles = SendStyles.classic.zip(SendStyles.liquid).flatMap { (a, b) -> listOf(a, b) }.map { n ->
                        Tile(n, SendStyles.about[n], p.opt("sendStyle").ifBlank { "Jelly" } == n, { repo.prefs.setOpt("sendStyle", n) }) { SendMini(n, t) }
                    } }
                "reactStyle" -> { cols = 3; pvH = 84.dp; tiles = listOf("Faces" to "Big emoji, small photo", "Cards" to "Grouped by emoji", "Tabs" to "A list with tabs").map { (n, sub) ->
                    Tile(n, sub, p.opt("reactStyle").ifBlank { "Faces" } == n, { repo.prefs.setOpt("reactStyle", n) }) { ReactMini(n, t) }
                } }
                "statusStyle" -> { cols = 3; pvH = 72.dp; tiles = listOf("Icon and text" to "Check, then Read", "Face" to "Their photo", "Off" to "Tap to see").map { (n, sub) ->
                    Tile(n, sub, p.opt("statusStyle") == n, { repo.prefs.setOpt("statusStyle", n) }) { StatusSample(n, t) }
                } }
                "photoGroups" -> { cols = 2; pvH = 92.dp; tiles = listOf("Peek" to "Next photo peeks in", "Stack" to "Swipe to fan out", "Rail" to "Thumbnails beside", "Deck" to "Flick through").map { (n, sub) ->
                    Tile(n, sub, p.opt("photoGroups") == n, { repo.prefs.setOpt("photoGroups", n) }) { PhotoGroupMini(n, t) }
                } }
                "appIcon" -> { cols = 3; pvH = 84.dp; tiles = AppIcons.styles.map { st ->
                    val color = AppIcons.colorFor(p, st)
                    Tile(st, null, p.appIcon == st, {
                        AppIcons.set(ctx, st, color); repo.prefs.edit { it.copy(appIcon = st) }
                        repo.toast("$st icon set on your home screen")
                    }) { AppIconPreview(AppIcons.key(st, color)) }
                } }
                else -> { cols = 3; pvH = 120.dp; tiles = listOf(Tile("Your photo", null, p.defWall.startsWith("photo:"), { pickWall() }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (p.defWall.startsWith("photo:")) Wallpaper(p.defWall, t)
                        Box(Modifier.size(36.dp).clip(CircleShape).background(if (t.dark) Color(0xFF2A2D36) else Color.White), contentAlignment = Alignment.Center) { Ico(Icons.gallery, t.p, 17.dp) }
                    }
                }) + Palettes.walls.map { w -> Tile(w, null, p.defWall == w, { repo.prefs.edit { it.copy(defWall = w) } }) { Wallpaper(w, t) } } }
            }
            heads?.let { hs ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    hs.forEach { h -> T(h, size = 13.sp, weight = FontWeight.SemiBold, color = t.soft, align = TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
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
            // Soft and Duotone come in colors: a row of swatches under the grid.
            if (k == "appIcon") AppIcons.colors[p.appIcon]?.let { list ->
                val cur = AppIcons.colorFor(p, p.appIcon)
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    list.forEach { c ->
                        val on = c.name == cur
                        Box(
                            Modifier.size(34.dp).then(if (on) Modifier.border(2.dp, t.p, CircleShape).padding(4.dp) else Modifier).clip(CircleShape)
                                .background(Brush.linearGradient(0.5f to c.a, 0.5f to c.b, start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset.Infinite))
                                .clickable {
                                    repo.prefs.setOpt("iconColor:" + p.appIcon, c.name)
                                    AppIcons.set(ctx, p.appIcon, c.name)
                                    repo.toast("${p.appIcon} · ${c.name} icon set")
                                },
                        )
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
        "Honeycomb" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { c(17.dp) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { c(17.dp) } }
        }
        "Spotlight" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { box(96.dp, 28.dp, 8.dp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { box(28.dp, 24.dp, 7.dp) } } }
        "List" -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(2) { box(110.dp, 11.dp, 4.dp, AV, false) }; repeat(3) { box(110.dp, 11.dp, 4.dp, LN, false) } }
        else -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { box(110.dp, 9.dp, 4.dp, LN, false) }; box(84.dp, 18.dp, 9.dp) }
    }
}

// ---------- app icons ----------

object AppIcons {
    /** A color choice for Soft or Duotone, with the two colors its swatch shows. */
    class Color2(val name: String, val a: Color, val b: Color)

    val styles = listOf("Frost", "Frost dark", "Glass", "Soft", "Duotone", "Mono")
    val colors = mapOf(
        "Soft" to listOf(
            Color2("Blue", hex("#D6E3FF"), hex("#4A86FF")), Color2("Rose", hex("#FFD9E5"), hex("#F0679A")),
            Color2("Peach", hex("#FFDCC8"), hex("#FF9161")), Color2("Mint", hex("#CFF1E2"), hex("#2FBF8A")),
            Color2("Lilac", hex("#E1D6FF"), hex("#8C6CF0")), Color2("Lemon", hex("#FFF0B8"), hex("#F2B92B")),
            Color2("Sky", hex("#CDEEFF"), hex("#2BA8E8")), Color2("Slate", hex("#DDE2EA"), hex("#5E6B82")),
        ),
        "Duotone" to listOf(
            Color2("Midnight", hex("#1C2B66"), hex("#7FB0FF")), Color2("Plum", hex("#4A1D4F"), hex("#FF8FC8")),
            Color2("Forest", hex("#123F33"), hex("#7CE3B5")), Color2("Ember", hex("#4F1E0C"), hex("#FFB27A")),
            Color2("Ocean", hex("#0E3A52"), hex("#7EE0FF")), Color2("Wine", hex("#5A1424"), hex("#FF8E9E")),
            Color2("Cocoa", hex("#3E2A1F"), hex("#F1C99B")), Color2("Graphite", hex("#2B2F38"), hex("#D7DEEA")),
        ),
    )

    fun colorFor(p: com.jpetrydesign.pyng.data.AppPrefs, style: String): String? =
        colors[style]?.let { list -> p.opt("iconColor:$style").takeIf { c -> list.any { it.name == c } } ?: list.first().name }

    /** The artwork name, e.g. "frost", "soft_rose", "duo_plum". */
    fun key(style: String, color: String?): String = when (style) {
        "Frost" -> "frost"
        "Frost dark" -> "frost_dark"
        "Glass" -> "glass"
        "Mono" -> "mono"
        "Soft" -> "soft_" + (color ?: "Blue").lowercase()
        else -> "duo_" + (color ?: "Midnight").lowercase()
    }

    /** Launcher entries. The first color of Soft and Duotone keep their original names, so updates never move your icon. */
    private fun alias(style: String, color: String?): String = when (style) {
        "Frost" -> ".IconClassic"
        "Frost dark" -> ".IconFrostDark"
        "Glass" -> ".IconGlass"
        "Mono" -> ".IconMono"
        "Soft" -> if (color == null || color == "Blue") ".IconSoft" else ".IconSoft$color"
        else -> if (color == null || color == "Midnight") ".IconDuotone" else ".IconDuo$color"
    }

    private val allAliases: List<String> get() = styles.flatMap { st -> colors[st]?.map { alias(st, it.name) } ?: listOf(alias(st, null)) }

    fun set(ctx: android.content.Context, style: String, color: String? = null) {
        val pm = ctx.packageManager
        val want = alias(style, color)
        // Turn the new one on first, so there's never a moment with no icon at all.
        (listOf(want) + allAliases.filter { it != want }).forEach { a ->
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(ctx.packageName, ctx.packageName + a),
                    if (a == want) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

@Composable
private fun AppIconPreview(key: String, size: Dp = 58.dp) {
    // The real launcher artwork, drawn the way a round icon mask shows it.
    val ctx = LocalContext.current
    val res = ctx.resources
    val bg = remember(key) { res.getIdentifier("icon_${key}_bg", "drawable", ctx.packageName) }
    val fg = remember(key) { res.getIdentifier("icon_${key}_fg", "mipmap", ctx.packageName) }
    Box(Modifier.size(size).shadow(if (size > 48.dp) 6.dp else 3.dp, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (bg != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(bg), null, modifier = Modifier.requiredSize(size * 1.5f))
        if (fg != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(fg), null, modifier = Modifier.requiredSize(size * 1.5f))
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
    val lib = remember { ThemeLibrary() }
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
                // See the color in both looks without leaving the sheet.
                Seg(listOf("Light", "Dark", "Match system"), p.mode, { m -> repo.prefs.edit { it.copy(mode = m) } }, Modifier.padding(horizontal = 20.dp))
                // Themes you saved, ready to use for the whole app.
                MyThemes(
                    lib,
                    isOn = { st -> p.themeKey.startsWith("Custom") && p.c1 == st.c1 && (st.mode == "Mono") == (p.themeKey == "Custom color") && (st.mode == "Mono" || p.c2 == st.c2) },
                    onPick = { st -> repo.prefs.edit { it.copy(themeKey = if (st.mode == "Mono") "Custom color" else "Custom duotone", c1 = st.c1, c2 = st.c2) } },
                    onSave = if (tab == "Custom" && p.themeKey.startsWith("Custom")) ({
                        val mode = if (p.themeKey == "Custom duotone") "Duotone" else "Mono"
                        lib.saving = com.jpetrydesign.pyng.data.SavedTheme(java.util.UUID.randomUUID().toString(), suggestName(mode, p.c1, p.savedThemes.map { it.name }), mode, p.c1, p.c2)
                    }) else null,
                    side = 20.dp,
                )
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
        ThemeLibrarySheets(lib)
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

// ---------- style studio ----------

/** One of the two studio cards on Appearance: a small live preview, its name and what's picked. */
@Composable
private fun StudioCard(title: String, summary: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(if (t.dark) t.card else Color.White).press(0.97f, onClick).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(18.dp)).background(if (t.dark) t.surface else t.bg)) {
            if (title.startsWith("Chat")) ChatStylePreview(compact = true) else HomeStylePreview(compact = true)
        }
        Row(Modifier.padding(start = 6.dp, end = 2.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Ico(Icons.chev, t.faint, 14.dp, 2.2f)
        }
        T(summary, size = 11.5.sp, color = t.soft, maxLines = 1, modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp))
    }
}

/** Chat style or Home style: a live preview at the top, a tab for each part, and its choices below. */
@Composable
private fun StyleStudio(kind: String?, onClose: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf(kind) }
    if (kind != null) last = kind
    if (kind != null) BackHandler(onBack = onClose)
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = repo.importUri(uri) ?: return@launch
            val dest = repo.keepFile("wall"); java.io.File(f.path).copyTo(dest, overwrite = true)
            repo.prefs.edit { it.copy(defWall = "photo:" + dest.path) }
        }
    }
    AnimatedVisibility(
        kind != null, modifier = Modifier.zIndex(12f),
        enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(160)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180)),
    ) {
        val k = last ?: return@AnimatedVisibility
        val chat = k == "chat"
        val tabs = if (chat) listOf("bubble" to "Bubbles", "sendStyle" to "Send", "reactStyle" to "Reactions", "gradient" to "Gradient", "statusStyle" to "Status", "photoGroups" to "Photos", "textSize" to "Text", "font" to "Font", "defWall" to "Wallpaper")
            else listOf("pinLayout" to "Layout", "pinShape" to "Shape", "pinStyle" to "New messages", "pinNames" to "Names", "pinBack" to "Backdrop", "density" to "Density", "showPhotos" to "Faces")
        var tab by remember(k) { mutableStateOf(tabs.first().first) }
        Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                com.jpetrydesign.pyng.ui.kit.CircleButton(Icons.back, "Back", onClose)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    T(if (chat) "Chat style" else "Home style", size = 21.sp, weight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                    T("Pick a tab, then a style. The preview updates as you go.", size = 12.5.sp, color = t.soft)
                }
                PillButton("Done", onClose, height = 40.dp)
            }
            // Live preview.
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(if (chat) 270.dp else 230.dp)
                    .edge(RoundedCornerShape(28.dp), elevation = 10.dp).clip(RoundedCornerShape(28.dp)).background(if (t.dark) t.card else Color.White),
            ) {
                // On the Send tab the preview plays your send animation on a loop.
                if (chat && tab == "sendStyle") SendPreview(LocalRepo.current.prefs.state.opt("sendStyle").ifBlank { "Jelly" })
                else if (chat && tab == "reactStyle") ReactPreview()
                else if (chat) ChatStylePreview(compact = false) else HomeStylePreview(compact = false)
            }
            // Tabs.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { (key, label) ->
                    val on = tab == key
                    val bg by androidx.compose.animation.animateColorAsState(if (on) t.fill else if (t.dark) t.card else Color.White, tween(200), label = "tab")
                    Box(Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(bg).clickable { tab = key }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        T(label, size = 13.5.sp, weight = FontWeight.SemiBold, color = if (on) t.onFill else t.soft)
                    }
                }
            }
            androidx.compose.animation.AnimatedContent(tab, label = "studioTab", modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }) { key ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                    StyleTiles(key) { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
            }
        }
    }
}

/** A small chat drawn with your current choices: bubble shape, gradient, photos, status, text size and wallpaper. */
@Composable
fun ChatStylePreview(compact: Boolean) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val (big, small) = when (p.bubble) { "Soft" -> 14.dp to 4.dp; "Pill" -> 22.dp to 22.dp; else -> 20.dp to 6.dp }
    val fs = (when (p.textSize) { "S" -> 13f; "L" -> 16.5f; "XL" -> 18.5f; else -> 14.5f }) * (if (compact) 0.72f else 1f)
    val pad = if (compact) 8.dp else 14.dp
    val gm = p.opt("gradient")
    val mid = androidx.compose.ui.graphics.lerp(t.outTop, t.outBottom, 0.5f)
    fun outFill(i: Int): Brush = when (gm) {
        "Off" -> Brush.linearGradient(listOf(t.outFlat, t.outFlat))
        "Each bubble" -> Brush.verticalGradient(listOf(t.outTop, t.outBottom))
        else -> if (i == 0) Brush.verticalGradient(listOf(t.outTop, mid)) else Brush.verticalGradient(listOf(mid, t.outBottom))
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxHeight >= 240.dp
        if (!compact) Wallpaper(p.defWall, t)
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
            Box(Modifier.clip(com.jpetrydesign.pyng.ui.screens.bubbleShape(false, false, false, big * (if (compact) 0.7f else 1f), small)).background(t.inBg).padding(horizontal = if (compact) 9.dp else 13.dp, vertical = if (compact) 5.dp else 8.dp)) {
                T("Back from the trip!!", size = fs.sp, color = t.inInk, maxLines = 1)
            }
            if (!compact && roomy) { PhotoGroupMini(p.opt("photoGroups"), t, 1.15f); Spacer(Modifier.height(2.dp)) }
            Spacer(Modifier.weight(1f))
            listOf("Ok these are so good", "How was the drive?").forEachIndexed { i, txt ->
                Box(Modifier.align(Alignment.End).clip(com.jpetrydesign.pyng.ui.screens.bubbleShape(true, i == 1, i == 0, big * (if (compact) 0.7f else 1f), small)).background(outFill(i))
                    .padding(horizontal = if (compact) 9.dp else 13.dp, vertical = if (compact) 5.dp else 8.dp)) {
                    T(txt, size = fs.sp, color = t.outInk, maxLines = 1)
                }
            }
            if (p.opt("statusStyle") != "Off") Box(Modifier.align(Alignment.End)) { StatusSample(p.opt("statusStyle"), t, compact) }
        }
    }
}

/** A small home screen drawn with your current choices: pinned layout, badges, density and photos. */
@Composable
fun HomeStylePreview(compact: Boolean) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val AV = t.p.copy(alpha = 0.35f)
    val LN = if (t.dark) Color.White.copy(alpha = 0.18f) else Color(0x1F15171D)
    Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { val s = if (compact) 0.78f else 1.1f; scaleX = s; scaleY = s }.padding(vertical = if (compact) 0.dp else 8.dp)) {
            if (compact) {
                PinMini(p.pinLayout, AV, LN)
                if (p.pinStyle == "Badges") Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(12.dp).clip(CircleShape).background(t.badgeBg))
            } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinMini(p.pinLayout, AV, LN)
                PinSample(pinLookOf(p), big = true)
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(if (t.dark) t.surface else t.bg).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (p.density == "Compact") 4.dp else 8.dp),
        ) {
            repeat(if (compact) 3 else 5) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.showPhotos == "Show") { Box(Modifier.size(if (compact) 12.dp else 22.dp).clip(CircleShape).background(AV)); Spacer(Modifier.width(8.dp)) }
                    Box(Modifier.weight(1f).height(if (compact) 6.dp else 9.dp).clip(RoundedCornerShape(4.dp)).background(LN))
                }
            }
        }
    }
}

/** What the status under your newest message looks like in each style. */
@Composable
fun StatusSample(style: String, t: Tok, compact: Boolean = false) {
    val fs = if (compact) 9.sp else 11.5.sp
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (style) {
            "Off" -> T("Tap a message to see", size = fs, color = t.faint)
            "Face" -> {
                Box(Modifier.size(if (compact) 10.dp else 15.dp).clip(CircleShape).background(t.p.copy(alpha = 0.45f)))
                Spacer(Modifier.width(4.dp)); T("Read", size = fs, weight = FontWeight.Medium, color = t.soft)
            }
            else -> {
                Ico(Icons.read, t.p, if (compact) 10.dp else 14.dp, 2.1f); Spacer(Modifier.width(3.dp))
                T(if (compact) "Read" else "Read 3:02 PM", size = fs, weight = FontWeight.SemiBold, color = t.p)
            }
        }
    }
}


/**
 * Three sample pinned chats drawn with a [PinLook]: the first has news, so the new-message sign,
 * shape, names and backdrop all show.
 */
@Composable
fun PinSample(look: PinLook, big: Boolean = false) {
    val t = LocalTok.current
    val av = if (big) 40.dp else 30.dp
    val accent = t.pal.plus ?: t.p
    val shape = pinShapeOf(look.shape)
    val tones = listOf(t.p.copy(alpha = 0.45f), t.p.copy(alpha = 0.3f), t.p.copy(alpha = 0.2f))
    val backMod = when (look.back) {
        "Frosted" -> Modifier.clip(RoundedCornerShape(14.dp)).background(t.glassStrong).padding(6.dp)
        "Tray" -> Modifier.clip(RoundedCornerShape(14.dp)).background(t.tone).padding(6.dp)
        "Band" -> Modifier.clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0f)))).padding(6.dp)
        else -> Modifier.padding(6.dp)
    }
    Row(backMod, horizontalArrangement = Arrangement.spacedBy(if (big) 12.dp else 8.dp), verticalAlignment = Alignment.Top) {
        tones.forEachIndexed { i, tone ->
            val hot = i == 0
            Column(
                (if (look.back == "Tiles") Modifier.clip(RoundedCornerShape(10.dp)).background(if (t.dark) t.card else Color.White).padding(4.dp) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(av)) {
                    if (hot && look.sign == "Glow") Box(Modifier.size(av).drawBehind {
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), accent.copy(alpha = 0f)), center = center, radius = size.minDimension * 0.8f), radius = size.minDimension * 0.8f)
                    })
                    var m = Modifier.size(av)
                    if (hot && look.sign == "Rings") m = m.drawBehind {
                        val g = 3.dp.toPx()
                        val o = shape.createOutline(androidx.compose.ui.geometry.Size(size.width + 2 * g, size.height + 2 * g), layoutDirection, this)
                        translate(-g, -g) { drawOutline(o, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx())) }
                    }
                    if (look.shape == "Framed") m = m.shadow(3.dp, CircleShape).border(2.dp, if (t.dark) Color(0xFF2A2F3B) else Color.White, CircleShape)
                    Box(m.clip(shape).background(tone), contentAlignment = Alignment.BottomCenter) {
                        if (look.names == "Over") Box(Modifier.padding(bottom = av * 0.18f).size(av * 0.5f, 3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                    }
                    if (hot && look.sign == "Badges") Box(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp).size(if (big) 14.dp else 11.dp).clip(CircleShape).background(t.badgeBg))
                    if (hot && look.sign == "Dot") Box(Modifier.align(Alignment.BottomEnd).size(if (big) 11.dp else 9.dp).border(2.dp, t.bg, CircleShape).padding(1.5.dp).clip(CircleShape).background(accent))
                }
                if (look.names != "Hidden" && look.names != "Over") {
                    Spacer(Modifier.height(5.dp))
                    val w = av * 0.8f
                    when {
                        hot && look.sign == "Pill" -> Box(Modifier.size(w + 6.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                        look.names == "Chip" -> Box(Modifier.clip(RoundedCornerShape(5.dp)).background(if (t.dark) t.card else Color.White).padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Box(Modifier.size(w * 0.8f, 4.dp).clip(RoundedCornerShape(2.dp)).background(t.soft.copy(alpha = 0.5f)))
                        }
                        look.names == "Bold" -> Box(Modifier.size(w, if (hot) 5.dp else 4.dp).clip(RoundedCornerShape(2.dp)).background(if (hot) t.ink else t.faint.copy(alpha = 0.6f)))
                        else -> Box(Modifier.size(w, 4.dp).clip(RoundedCornerShape(2.dp)).background(t.soft.copy(alpha = 0.5f)))
                    }
                }
            }
        }
    }
}


/** A little sketch of a send animation: the message box at the bottom, the bubble it becomes, and the style's signature. */
@Composable
fun SendMini(style: String, t: com.jpetrydesign.pyng.ui.theme.Tok) {
    val out = t.outFlat
    val box = if (t.dark) Color.White.copy(alpha = 0.12f) else Color(0x14000000)
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        val w = size.width; val h = size.height; val dp = density
        val r = 9f * dp
        // the message box
        drawRoundRect(box, androidx.compose.ui.geometry.Offset(0f, h - 18f * dp), androidx.compose.ui.geometry.Size(w * 0.78f, 18f * dp), androidx.compose.ui.geometry.CornerRadius(9f * dp))
        // where the bubble ends up
        val bw = w * 0.5f; val bh = 20f * dp; val bx = w - bw; val by = 4f * dp
        fun bubble(x: Float = bx, y: Float = by, ww: Float = bw, hh: Float = bh, a: Float = 1f) =
            drawRoundRect(out, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(ww, hh), androidx.compose.ui.geometry.CornerRadius(min(r, hh / 2)), alpha = a)
        val faint = out.copy(alpha = 0.35f)
        when (style) {
            "Jelly" -> { bubble(bx - 3f * dp, by + 3f * dp, bw + 6f * dp, bh - 5f * dp); drawRoundRect(faint, androidx.compose.ui.geometry.Offset(bx + bw * 0.2f, by + bh + 8f * dp), androidx.compose.ui.geometry.Size(bw * 0.6f, 12f * dp), androidx.compose.ui.geometry.CornerRadius(6f * dp)) }
            "Pop" -> { bubble(); for (i in 0 until 5) { val a = (-150f + i * 30f) * (Math.PI / 180f).toFloat(); val cx = bx + bw / 2; val cy = by + bh / 2
                drawLine(faint, androidx.compose.ui.geometry.Offset(cx + kotlin.math.cos(a) * bw * 0.62f, cy + kotlin.math.sin(a) * bh * 1.1f), androidx.compose.ui.geometry.Offset(cx + kotlin.math.cos(a) * bw * 0.78f, cy + kotlin.math.sin(a) * bh * 1.5f), 2f * dp) } }
            "Swoosh" -> { rotate(-6f, androidx.compose.ui.geometry.Offset(bx + bw / 2, by + bh / 2)) { bubble() }; for (i in 0 until 3) drawLine(faint, androidx.compose.ui.geometry.Offset(bx - (12f + i * 8f) * dp, by + (5f + i * 6f) * dp), androidx.compose.ui.geometry.Offset(bx - (2f + i * 4f) * dp, by + (5f + i * 6f) * dp), 2f * dp) }
            "Taffy" -> { bubble(); drawRoundRect(faint, androidx.compose.ui.geometry.Offset(bx + bw * 0.15f, by + bh - 4f * dp), androidx.compose.ui.geometry.Size(bw * 0.7f, h - bh - 18f * dp), androidx.compose.ui.geometry.CornerRadius(10f * dp)) }
            "Shimmer" -> { bubble(); clipRect(bx, by, bx + bw, by + bh) { drawLine(Color.White.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(bx + bw * 0.45f, by + bh + 4f), androidx.compose.ui.geometry.Offset(bx + bw * 0.62f, by - 4f), 6f * dp) } }
            "Rise", "Honey" -> { bubble(); val nw = if (style == "Honey") 9f * dp else 5f * dp; val cx = bx + bw * 0.45f
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(cx - nw * 1.6f, by + bh - 2f); cubicTo(cx - nw * 0.5f, by + bh + 10f * dp, cx - nw * 0.5f, h - 26f * dp, cx - nw * 2.2f, h - 16f * dp); lineTo(cx + nw * 2.2f, h - 16f * dp); cubicTo(cx + nw * 0.5f, h - 26f * dp, cx + nw * 0.5f, by + bh + 10f * dp, cx + nw * 1.6f, by + bh - 2f); close() }
                drawPath(path, out) }
            "Droplet" -> { bubble(a = 0.35f); val cx = w * 0.42f; val cy = h * 0.55f; val rr = 8f * dp
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(cx, cy + rr + 9f * dp); cubicTo(cx - rr * 0.3f, cy + rr * 0.9f, cx - rr, cy + rr * 0.5f, cx - rr, cy); cubicTo(cx - rr, cy - rr * 1.35f, cx + rr, cy - rr * 1.35f, cx + rr, cy); cubicTo(cx + rr, cy + rr * 0.5f, cx + rr * 0.3f, cy + rr * 0.9f, cx, cy + rr + 9f * dp); close() }
                drawPath(path, out) }
            "Splash" -> { bubble(); listOf(-0.1f to -0.5f, 0.2f to -0.9f, 0.75f to -0.8f, 1.05f to -0.3f).forEach { (fx, fy) -> drawCircle(out, 2.6f * dp, androidx.compose.ui.geometry.Offset(bx + bw * fx, by + bh * 0.5f + bh * fy)) } }
            "Ink" -> { bubble(); drawRoundRect(out.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(bx - 5f * dp, by - 5f * dp), androidx.compose.ui.geometry.Size(bw + 10f * dp, bh + 10f * dp), androidx.compose.ui.geometry.CornerRadius(r + 5f * dp), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * dp))
                drawCircle(out, 4f * dp, androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.62f)) }
        }
    }
}
