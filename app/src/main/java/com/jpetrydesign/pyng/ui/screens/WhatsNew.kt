package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.ui.kit.CountBadge
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Mo
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.Toggle
import com.jpetrydesign.pyng.ui.kit.arrive
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.delay

/** A feature big enough for its own card at the top of What's new. [art] picks the picture on the card. */
data class Highlight(val title: String, val body: String, val art: String)

/**
 * After an update: the biggest changes as cards you can swipe through (they move on by themselves until
 * you touch them), then everything else that's new, then what got fixed.
 */
@Composable
fun WhatsNewSheet(r: Release?, onAll: () -> Unit, onDone: () -> Unit) {
    val t = LocalTok.current
    var shown by remember { mutableStateOf<Release?>(null) }
    if (r != null) shown = r
    val cur = shown
    Sheet(r != null, onDone, maxHeightFraction = 0.9f) {
        if (cur == null) return@Sheet
        // Older updates without cards of their own use their first few features.
        val cards = cur.highlights.ifEmpty {
            cur.added.take(3).map { Highlight(it.substringBefore('\n'), it.substringAfter('\n', ""), "icon") }
        }
        val heads = cards.map { it.title.lowercase() }.toSet()
        val rest = cur.added.filter { it.substringBefore('\n').lowercase() !in heads }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("What's new", size = 25.sp, weight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                    T("Pyng ${cur.version} · ${cur.date}", size = 13.sp, color = t.soft, maxLines = 1)
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(t.tone).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    T("${cur.added.size} new · ${cur.fixed.size} fixed", size = 11.5.sp, weight = FontWeight.SemiBold, color = t.p, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                HeroPager(cards, t)
                if (rest.isNotEmpty()) {
                    Head("Also new", Icons.spark, t.p)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.surface).padding(vertical = 4.dp)) {
                        rest.forEachIndexed { i, line -> Line(line, Icons.spark, t.p, t.tone, Modifier.arrive(i + 2, true)) }
                    }
                }
                if (cur.fixed.isNotEmpty()) {
                    Head("Fixed", Icons.check, t.soft)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.surface).padding(vertical = 4.dp)) {
                        cur.fixed.forEachIndexed { i, line -> Line(line, Icons.check, t.soft, t.neutral, Modifier.arrive(rest.size + i + 3, true)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Got it", onClick = onDone, modifier = Modifier.fillMaxWidth())
            T(
                "See everything that changed", size = 14.sp, weight = FontWeight.SemiBold, color = t.p,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(16.dp)).clickable { onDone(); onAll() }.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun HeroPager(cards: List<Highlight>, t: Tok) {
    if (cards.isEmpty()) return
    val pager = rememberPagerState { cards.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var touched by remember { mutableStateOf(false) }
    if (dragged) touched = true
    val motion = LocalMotion.current
    // The cards move on by themselves every few seconds, until you swipe one yourself.
    LaunchedEffect(touched, cards.size) {
        if (touched || cards.size < 2 || !motion) return@LaunchedEffect
        while (true) {
            delay(5200)
            if (!touched) pager.animateScrollToPage((pager.currentPage + 1) % cards.size)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Tall enough for the picture and three lines of text, never more than most of a short screen.
        val cardH = (maxWidth * 0.92f).coerceIn(280.dp, 330.dp)
        HorizontalPager(pager, Modifier.fillMaxWidth().height(cardH), pageSpacing = 12.dp) { page ->
            HeroCard(cards[page], t, Modifier.arrive(page.coerceAtMost(1), true))
        }
    }
    // Page dots: the current one stretches into a pill, sliding as you swipe.
    if (cards.size > 1) Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        cards.indices.forEach { i ->
            val off = (pager.currentPage - i) + pager.currentPageOffsetFraction
            val near = (1f - kotlin.math.abs(off)).coerceIn(0f, 1f)
            Box(
                Modifier.padding(horizontal = 3.dp).height(7.dp).width(7.dp + 14.dp * near).clip(RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.lerp(t.faint.copy(alpha = 0.5f), t.p, near))
                    .clickable { touched = true },
            )
        }
    }
}

@Composable
private fun HeroCard(h: Highlight, t: Tok, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxSize().clip(shape)
            .background(Brush.verticalGradient(listOf(t.p.copy(alpha = if (t.dark) 0.26f else 0.16f), t.surface)))
            .border(1.dp, t.edgeLine, shape),
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(10.dp).clip(RoundedCornerShape(20.dp))
                .background(if (t.dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) { HeroArt(h.art, t) }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp)) {
            T(h.title, size = 19.sp, weight = FontWeight.Bold, letterSpacing = (-0.4).sp, maxLines = 1)
            if (h.body.isNotEmpty()) T(h.body, size = 13.5.sp, color = t.soft, lineHeight = 18.5.sp, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** The live picture on a card: the real animation where there is one. */
@Composable
private fun HeroArt(art: String, t: Tok) {
    val repo = LocalRepo.current
    when (art) {
        "motion" -> MotionMini(Mo.of(repo.prefs.state.opt("motion")).let { if (it.liquid) it else Mo.Rise }, t, big = true)
        "send" -> SendPreview(repo.prefs.state.opt("sendStyle").let { if (it in SendStyles.liquid) it else "Rise" })
        "moments" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { T("🎂", size = 30.sp) }
                Box(Modifier.size(64.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(t.fab), contentAlignment = Alignment.Center) { Ico(Icons.search, t.ink, 26.dp, 2.2f) }
                    val glow = androidx.compose.animation.core.rememberInfiniteTransition(label = "heroDot")
                    val g by glow.animateFloat(0.5f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1300), androidx.compose.animation.core.RepeatMode.Reverse), label = "heroDotA")
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(13.dp).graphicsLayer { alpha = g }.clip(CircleShape).background(t.p))
                }
            }
        }
        "touches" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // A switch flipping and a count growing, on a loop.
            var on by remember { mutableStateOf(false) }
            var n by remember { mutableIntStateOf(1) }
            LaunchedEffect(Unit) { while (true) { delay(1400); on = !on; if (on) n = n % 9 + 1 } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Toggle(on)
                Box(Modifier.size(58.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { T("JL", size = 18.sp, weight = FontWeight.SemiBold, color = t.p) }
                    CountBadge(n, h = 22.dp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
        else -> Box(Modifier.size(84.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.spark, t.p, 38.dp, 1.8f) }
    }
}

@Composable
private fun Head(text: String, icon: String, color: Color) {
    Row(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, color, 15.dp, 2.2f)
        Spacer(Modifier.width(6.dp))
        T(text, size = 13.5.sp, weight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Line(raw: String, icon: String, ink: Color, bg: Color, modifier: Modifier) {
    val t = LocalTok.current
    val head = raw.substringBefore('\n'); val more = raw.substringAfter('\n', "")
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp).size(26.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Ico(icon, ink, 13.dp, 2.2f) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(head, size = 14.5.sp, weight = FontWeight.SemiBold, lineHeight = 19.sp)
            if (more.isNotEmpty()) T(more, size = 13.sp, color = t.soft, lineHeight = 18.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
