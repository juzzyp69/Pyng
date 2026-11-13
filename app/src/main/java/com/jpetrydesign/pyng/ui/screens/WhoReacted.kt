@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.Tok

/** Someone who reacted to a message, with the emoji they used. */
data class Reactor(val id: String, val name: String, val who: Who, val emoji: String, val mine: Boolean)

/** Everyone who reacted, you first, then in the order the reactions came in. */
fun reactorsOf(m: Msg, conv: Conv?, repo: Repo, t: Tok): List<Reactor> {
    val myId = conv?.me?.id.orEmpty()
    val out = mutableListOf<Reactor>()
    m.reactions.forEach { r ->
        r.by.distinct().forEach { id ->
            val mine = myId.isNotEmpty() && id == myId
            val p = conv?.people?.firstOrNull { it.id == id }
            val who = when {
                mine -> Who(null, "You", t.p, t.onFill)
                p != null -> personWho(p, repo, t)
                else -> Who(null, "?", t.neutral, t.neutralInk)
            }
            out += Reactor(id, if (mine) "You" else p?.short ?: "Someone", who, r.emoji, mine)
        }
        // A reaction counted without names still shows up, as "Someone".
        repeat((r.count - r.by.distinct().size).coerceAtLeast(0)) { i ->
            out += Reactor("?${r.emoji}$i", "Someone", Who(null, "?", t.neutral, t.neutralInk), r.emoji, false)
        }
    }
    return out.sortedByDescending { it.mine }
}

/**
 * Sizes for the message popup. It measures the space on screen and uses the first of these that
 * fits: roomy, then compact, then fewer rows, then tight with the menu folded into one slim row.
 */
data class MenuFit(
    val name: String,
    val em: TextUnit, val badge: Dp, val label: TextUnit,
    val cell: Dp, val barEm: TextUnit,
    val rowPad: Dp, val gap: Dp, val li: Dp,
    val facesH: Dp, val listH: Dp,
    val rows: Int, val strip: Boolean,
)

fun menuFits(style: String): List<MenuFit> {
    val big = if (style == "Faces") 2 else 3
    val roomy = MenuFit("roomy", 30.sp, 20.dp, 11.sp, 42.dp, 24.sp, 12.dp, 10.dp, 30.dp, 64.dp, 40.dp, big, false)
    val compact = MenuFit("compact", 26.sp, 18.dp, 10.5.sp, 38.dp, 22.sp, 9.dp, 8.dp, 26.dp, 58.dp, 36.dp, big, false)
    val tight = MenuFit("tight", 24.sp, 16.dp, 10.sp, 36.dp, 21.sp, 8.dp, 7.dp, 24.dp, 54.dp, 34.dp, big, true)
    return listOf(
        roomy, compact, compact.copy(rows = maxOf(1, big - 1)),
        tight, tight.copy(rows = maxOf(1, big - 1)), tight.copy(rows = 1),
    )
}

/** The see-through frosted look of every panel in the popup: the chat behind shows through softly. */
fun popGlass(t: Tok): Color = t.sheet.copy(alpha = 0.72f)

/**
 * Who reacted, in your chosen style. Long lists stop at a few rows and scroll, with a slim bar
 * that only shows when there's more.
 */
@Composable
fun WhoReacted(list: List<Reactor>, style: String, fit: MenuFit, t: Tok, onRemoveMine: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val kinds = list.groupBy { it.emoji }
    if (filter.isNotEmpty() && filter !in kinds) filter = ""
    val total = list.size
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(popGlass(t)).border(1.dp, t.edgeLine, RoundedCornerShape(22.dp))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (style != "Cards" && kinds.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (listOf("") + kinds.keys).forEach { e ->
                    val on = e == filter
                    Box(Modifier.clip(RoundedCornerShape(13.dp)).background(if (on) t.p else t.ink.copy(alpha = 0.06f)).clickable { filter = e }.padding(horizontal = 9.dp, vertical = 4.dp)) {
                        T(if (e.isEmpty()) "All $total" else "$e ${kinds[e]!!.size}", size = 12.sp, weight = FontWeight.Medium, color = if (on) t.onFill else t.ink, maxLines = 1)
                    }
                }
            }
        } else {
            T(if (total == 1) "1 REACTION" else "$total REACTIONS", size = 11.sp, weight = FontWeight.SemiBold, color = t.soft, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
        val shown = list.filter { filter.isEmpty() || it.emoji == filter }
        when (style) {
            "Cards" -> {
                val rowH = fit.li + 26.dp
                Scroller(maxH = rowH * fit.rows + 6.dp * (fit.rows - 1), t = t) {
                    kinds.entries.forEachIndexed { i, (e, ps) ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(t.edgeLine))
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(e, size = (fit.em.value * 0.8f).sp, modifier = Modifier.width(32.dp), align = TextAlign.Center)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ps.forEach { r ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 56.dp)) {
                                        Box {
                                            WhoAvatar(r.who, fit.li, (fit.li.value * 0.36f).sp)
                                            if (r.mine) RemoveX(t, Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp), onRemoveMine)
                                        }
                                        T(r.name, size = fit.label, color = t.soft, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Tabs" -> {
                Scroller(maxH = fit.listH * fit.rows, t = t) {
                    shown.forEach { r ->
                        Row(Modifier.fillMaxWidth().height(fit.listH).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            WhoAvatar(r.who, fit.li, (fit.li.value * 0.36f).sp)
                            Spacer(Modifier.width(10.dp))
                            T(r.name, size = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            T(r.emoji, size = (fit.em.value * 0.72f).sp)
                            if (r.mine) { Spacer(Modifier.width(6.dp)); RemoveX(t, Modifier, onRemoveMine) }
                        }
                    }
                }
            }
            else -> {
                // Faces: a big emoji with a small photo tucked under it, then their name.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cols = maxOf(1, ((maxWidth - 8.dp) / 58.dp).toInt())
                    val gap = 8.dp
                    Scroller(maxH = fit.facesH * fit.rows + gap * (fit.rows - 1) + 8.dp, t = t) {
                        Spacer(Modifier.height(8.dp))
                        if (shown.size == 1) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FaceCell(shown[0], fit, t, big = true, onRemoveMine) }
                        } else shown.chunked(cols).forEachIndexed { i, row ->
                            if (i > 0) Spacer(Modifier.height(gap))
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { r -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { FaceCell(r, fit, t, big = false, onRemoveMine) } }
                                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceCell(r: Reactor, fit: MenuFit, t: Tok, big: Boolean, onRemoveMine: () -> Unit) {
    val em = if (big) (fit.em.value * 1.35f).sp else fit.em
    val badge = if (big) fit.badge + 6.dp else fit.badge
    Box(Modifier.height(if (big) fit.facesH + 12.dp else fit.facesH).widthIn(min = 52.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(r.emoji, size = em)
            Box(Modifier.offset(y = -(badge * 0.5f))) {
                Box(Modifier.border(1.5.dp, t.sheet, CircleShape).padding(1.5.dp)) { WhoAvatar(r.who, badge, (badge.value * 0.42f).sp) }
            }
            T(r.name, size = fit.label, color = t.soft, maxLines = 1, modifier = Modifier.offset(y = -(badge * 0.5f) + 2.dp).widthIn(max = 58.dp))
        }
        if (r.mine) RemoveX(t, Modifier.align(Alignment.TopCenter).offset(x = 16.dp, y = (-6).dp), onRemoveMine)
    }
}

/** The little red ✕ that takes your own reaction off. */
@Composable
fun RemoveX(t: Tok, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(20.dp).clip(CircleShape).background(t.sheet).padding(2.dp).clip(CircleShape).background(Color(0xFFE5484D)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { com.jpetrydesign.pyng.ui.kit.Ico("M7 7l10 10M17 7 7 17", Color.White, 10.dp, 3f) }
}

/** A column that stops growing at [maxH] and scrolls, fading at the edges with a slim bar when there's more. */
@Composable
fun Scroller(maxH: Dp, t: Tok, content: @Composable () -> Unit) {
    val s = rememberScrollState()
    Box {
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).fadeEdges(s).verticalScroll(s).padding(end = 8.dp)) { content() }
        if (s.maxValue > 0) Canvas(Modifier.matchParentSize()) {
            val w = 3.dp.toPx(); val x = size.width - w
            val top = 4.dp.toPx(); val h = size.height - 8.dp.toPx()
            drawRoundRect(t.soft.copy(alpha = 0.22f), Offset(x, top), Size(w, h), CornerRadius(w / 2))
            val th = maxOf(16.dp.toPx(), h * size.height / (size.height + s.maxValue))
            val y = top + (h - th) * s.value / s.maxValue
            drawRoundRect(t.p, Offset(x, y), Size(w, th), CornerRadius(w / 2))
        }
    }
}

/** Fades the top and bottom of a scrolling list when there's more past that edge. */
fun Modifier.fadeEdges(s: ScrollState): Modifier = this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val f = (14.dp.toPx() / size.height).coerceIn(0f, 0.4f)
    val up = s.value > 0; val down = s.value < s.maxValue
    if (up || down) drawRect(
        Brush.verticalGradient(
            0f to (if (up) Color.Transparent else Color.Black), f to Color.Black,
            1f - f to Color.Black, 1f to (if (down) Color.Transparent else Color.Black),
        ),
        blendMode = BlendMode.DstIn,
    )
}

/** A tiny sketch of each who-reacted style, for the style pickers. */
@Composable
fun ReactMini(style: String, t: Tok) {
    val tones = listOf(t.p.copy(alpha = 0.55f), t.p.copy(alpha = 0.38f), t.p.copy(alpha = 0.25f))
    val line = t.ink.copy(alpha = 0.14f)
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        when (style) {
            "Cards" -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("❤️" to 3, "😂" to 2).forEach { (e, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(e, size = 14.sp); Spacer(Modifier.width(6.dp))
                        repeat(n) { i -> Box(Modifier.padding(end = 3.dp).size(14.dp).clip(CircleShape).background(tones[i])) }
                    }
                }
            }
            "Tabs" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(18.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(t.p))
                    Box(Modifier.size(14.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(line))
                    Box(Modifier.size(14.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(line))
                }
                listOf("❤️", "😂").forEachIndexed { i, e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(tones[i])); Spacer(Modifier.width(5.dp))
                        Box(Modifier.size(32.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(line)); Spacer(Modifier.width(5.dp))
                        T(e, size = 10.sp)
                    }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("❤️", "😂", "❤️").forEachIndexed { i, e ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(e, size = 19.sp)
                        Box(Modifier.offset(y = (-5).dp).size(10.dp).clip(CircleShape).background(tones[i]))
                    }
                }
            }
        }
    }
}

/** A live sample of the who-reacted popup in your chosen style, for Style studio and setup. */
@Composable
fun ReactPreview() {
    val repo = com.jpetrydesign.pyng.ui.kit.LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    val style = repo.prefs.state.opt("reactStyle").ifBlank { "Faces" }
    val sample = remember(t) {
        listOf("You" to "❤️", "Jasmine" to "❤️", "Ana" to "😂", "Mike" to "❤️", "Chase" to "😮", "Rachel" to "😂", "Kim" to "👍", "Josh" to "❤️", "Leo" to "😂")
            .mapIndexed { i, (n, e) ->
                val (bg, ink) = if (i == 0) t.p to t.onFill else t.toneFor(n)
                Reactor(n, n, Who(null, if (i == 0) "You" else n.take(2).uppercase(), bg, ink), e, i == 0)
            }
    }
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            WhoReacted(sample, style, menuFits(style)[1].copy(rows = if (style == "Faces") 2 else 3), t) {}
        }
    }
}
