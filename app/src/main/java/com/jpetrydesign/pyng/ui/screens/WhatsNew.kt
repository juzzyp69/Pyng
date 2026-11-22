package com.jpetrydesign.pyng.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawWithContent
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
        // Cards without a picture stay short instead of leaving an empty box.
        val cardH = if (cards.all { it.art == "icon" }) 150.dp else (maxWidth * 0.92f).coerceIn(280.dp, 330.dp)
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
    if (h.art == "icon") {
        // No picture for this one: a compact card, its icon beside the words.
        Row(
            modifier.fillMaxSize().clip(shape)
                .background(Brush.verticalGradient(listOf(t.p.copy(alpha = if (t.dark) 0.26f else 0.16f), t.surface)))
                .border(1.dp, t.edgeLine, shape).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.spark, t.p, 24.dp, 1.9f) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                T(h.title, size = 17.sp, weight = FontWeight.Bold, letterSpacing = (-0.3).sp, maxLines = 2)
                if (h.body.isNotEmpty()) T(h.body, size = 13.sp, color = t.soft, lineHeight = 18.sp, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
            }
        }
        return
    }
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
        "bleep" -> BleepArt(t)
        "glass" -> GlassArt(t)
        "looks" -> LooksArt(t)
        "live" -> LiveArt(t)
        "groupname" -> GroupNameArt(t)
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

/** A voice message transcript with the switch beside it: flip it and the swear word bleeps, flip it back and it doesn't. */
@Composable
private fun BleepArt(t: Tok) {
    var hide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(1800); hide = !hide } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.Center) {
        // The voice message: play button, waveform, then its transcript.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.inBg).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.play, t.p, 14.dp, 2.2f) }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f).height(24.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.4f, 0.8f, 0.55f, 1f, 0.7f, 0.35f, 0.9f, 0.6f, 0.45f, 0.75f, 0.5f, 0.85f, 0.4f, 0.65f).forEach { h ->
                        Box(Modifier.width(3.dp).height((24f * h).dp).clip(RoundedCornerShape(2.dp)).background(t.soft.copy(alpha = 0.7f)))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("This is so ", size = 14.sp, maxLines = 1)
                androidx.compose.animation.AnimatedContent(hide, label = "bleep", transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.scaleIn(initialScale = 0.8f)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
                }) { h -> T(if (h) "d***" else "damn", size = 14.sp, weight = FontWeight.SemiBold, color = if (h) t.soft else t.ink, maxLines = 1) }
                T(" good", size = 14.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Hide swear words", size = 13.5.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Toggle(hide)
        }
    }
}

/** A group's header: renamed in Google Messages, the new name drops in and a check confirms it's the same for everyone. */
@Composable
private fun GroupNameArt(t: Tok) {
    val names = listOf("Weekend crew", "Ski trip 🏔️")
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2200); i = (i + 1) % names.size } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(t.sheet).border(1.dp, t.edgeLine, RoundedCornerShape(26.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Three faces, overlapped like a group photo.
            Box(Modifier.size(44.dp)) {
                listOf(Triple("AM", 0.dp, 0.dp), Triple("NK", 18.dp, 4.dp), Triple("MR", 6.dp, 20.dp)).forEach { (n, x, y) ->
                    Box(Modifier.padding(start = x, top = y).size(24.dp).clip(CircleShape).background(t.tone).border(1.5.dp, t.sheet, CircleShape), contentAlignment = Alignment.Center) {
                        T(n, size = 8.sp, weight = FontWeight.Bold, color = t.p)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            androidx.compose.animation.AnimatedContent(i, label = "gname", transitionSpec = {
                (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                    (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
            }, modifier = Modifier.weight(1f)) { k -> T(names[k], size = 16.sp, weight = FontWeight.Bold, maxLines = 1) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(t.tone).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.check, t.p, 14.dp, 2.4f)
            Spacer(Modifier.width(6.dp))
            T("Renamed in Google Messages · same for everyone", size = 12.sp, weight = FontWeight.SemiBold, color = t.p, maxLines = 1)
        }
    }
}

/** A glass pill over a colorful glow, its glint sliding back and forth like light on liquid glass. */
@Composable
private fun GlassArt(t: Tok) {
    val sweep = androidx.compose.animation.core.rememberInfiniteTransition(label = "glassArt")
    val k by sweep.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2600), androidx.compose.animation.core.RepeatMode.Reverse), label = "glint")
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(t.p.copy(alpha = 0.55f), t.s.copy(alpha = 0.45f), t.p.copy(alpha = 0.25f))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(30.dp)
        Box(
            Modifier.fillMaxWidth(0.72f).height(60.dp).clip(shape)
                .background(Color.White.copy(alpha = if (t.dark) 0.1f else 0.32f))
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.45f to Color.Transparent))
                    val cx = size.width * (0.1f + 0.8f * k); val band = size.width * 0.3f
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), androidx.compose.ui.geometry.Offset(cx - band, 0f), androidx.compose.ui.geometry.Offset(cx + band, size.height)))
                }
                .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.5f))), shape),
            contentAlignment = Alignment.Center,
        ) { T("RCS Message", size = 15.sp, weight = FontWeight.Medium, color = Color.White) }
    }
}

/** The same chat in its light look and its dark look, side by side, each with its own color. */
@Composable
private fun LooksArt(t: Tok) {
    val looks = listOf(Triple(Color(0xFFF6F4EF), Color(0xFF3F5A8A), Color(0xFF1C1E22)), Triple(Color(0xFF15161A), Color(0xFFB5476B), Color(0xFFF1F1F4)))
    Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        looks.forEachIndexed { i, (bg, accent, ink) ->
            Column(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(18.dp)).background(bg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T(if (i == 0) "Light" else "Dark", size = 11.sp, weight = FontWeight.Bold, color = ink.copy(alpha = 0.55f))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.09f)).padding(horizontal = 10.dp, vertical = 6.dp)) { T("Dinner at 7?", size = 11.5.sp, color = ink, maxLines = 1) }
                Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).background(accent).padding(horizontal = 10.dp, vertical = 6.dp)) { T("Sounds good", size = 11.5.sp, color = Color.White, maxLines = 1) }
            }
        }
    }
}

/** A voice message's words arriving one at a time, as they're heard. */
@Composable
private fun LiveArt(t: Tok) {
    val words = "Hey, I'm running about ten minutes late, save me a seat!".split(' ')
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { n = 0; delay(500); while (n < words.size) { delay(220); n++ }; delay(1800) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.Center) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.inBg).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.pause, t.p, 14.dp, 2.2f) }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f).height(24.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.4f, 0.8f, 0.55f, 1f, 0.7f, 0.35f, 0.9f, 0.6f, 0.45f, 0.75f, 0.5f, 0.85f).forEachIndexed { i, h ->
                        Box(Modifier.width(3.dp).height((24f * h).dp).clip(RoundedCornerShape(2.dp)).background(if (i < n) t.p else t.soft.copy(alpha = 0.5f)))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            T(if (n == 0) "Listening…" else words.take(n).joinToString(" "), size = 14.sp, color = if (n == 0) t.soft else t.inInk, lineHeight = 19.sp, maxLines = 3)
        }
    }
}
