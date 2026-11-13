package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * A live, looping preview of a send animation for Style studio: a little message box sends a message
 * into a bubble with the chosen style, using the same animation code as real chats.
 */
@Composable
fun SendPreview(style: String) {
    val t = LocalTok.current
    val d = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val text = "On my way!"
    val dryStyle = remember { TextStyle(fontFamily = Geist, fontSize = 15.sp, lineHeight = (15f * 1.32f).sp) }
    val wetStyle = remember { TextStyle(fontFamily = Geist, fontSize = 15.sp, lineHeight = (15f * 1.4f).sp) }
    val boxes = remember { arrayOfNulls<Rect>(3) } // the box, its words, the bubble
    val wet = remember { arrayOfNulls<TextLayoutResult>(1) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val dry = remember { measurer.measure(AnnotatedString(text), dryStyle) }
    val look = remember(t) { Look(t, dry, { wet[0] }, 16f * d, 11f * d) }
    LaunchedEffect(style) {
        while (true) {
            flight = null; frame++
            delay(650)
            val f = boxes[0]; val w = boxes[1]; val b = boxes[2]
            if (f == null || w == null || b == null) continue
            wet[0] = measurer.measure(AnnotatedString(text), wetStyle, constraints = Constraints(maxWidth = max(1, (b.width - 32f * d).toInt())))
            val run = MeltRun("preview", text, f, Rect(f.left + 16f * d + w.left, f.top + w.top, f.left + 16f * d + w.right, f.top + w.bottom), Rect(f.right + 8f * d, f.top, f.right + 56f * d, f.bottom), style)
            val fl = flightFor(style, run, d, 20f)
            fl.screenW = b.right + 24f * d
            flight = fl
            var last = withFrameNanos { it }
            var carry = 0f; var tt = 0f
            while (!fl.done && tt < fl.maxTime) {
                val now = withFrameNanos { it }
                carry += ((now - last) / 1e9f).coerceAtMost(0.1f); last = now
                while (carry >= 1f / 240f) { carry -= 1f / 240f; tt += 1f / 240f; fl.step(1f / 240f, tt, b) }
                frame++
            }
            fl.showReal = true; fl.realSX = 1f; fl.realSY = 1f; fl.realTX = 0f; fl.realTY = 0f; fl.realRot = 0f; frame++
            delay(1200)
        }
    }
    Box(Modifier.fillMaxSize().background(t.bg)) {
        // Where the message lands.
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)
                .onGloballyPositioned { boxes[2] = it.boundsInParent() }
                .graphicsLayer {
                    if (frame < 0) return@graphicsLayer
                    val fl = flight
                    alpha = if (fl != null && fl.showReal) 1f else 0f
                    scaleX = fl?.realSX ?: 1f; scaleY = fl?.realSY ?: 1f
                    translationX = fl?.realTX ?: 0f; translationY = fl?.realTY ?: 0f; rotationZ = fl?.realRot ?: 0f
                }
                .clip(RoundedCornerShape(20.dp)).background(t.outBg).padding(horizontal = 16.dp, vertical = 11.dp),
        ) { T(text, size = 15.sp, color = t.outInk) }
        // The message box with the words waiting to go, and the send button.
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 72.dp, bottom = 14.dp).fillMaxWidth().height(48.dp)
                .onGloballyPositioned { boxes[0] = it.boundsInParent() }
                .clip(RoundedCornerShape(24.dp)).background(t.field).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.onGloballyPositioned { boxes[1] = it.boundsInParent() }.graphicsLayer { alpha = if (frame >= 0 && flight == null) 1f else 0f }) {
                T(text, size = 15.sp, color = t.ink, maxLines = 1)
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp).size(48.dp).clip(RoundedCornerShape(24.dp)).background(t.plusBg),
            contentAlignment = Alignment.Center,
        ) { Ico("M5 12h14M13 6l6 6-6 6", t.plusInk, 20.dp, 2.4f) }
        Canvas(Modifier.fillMaxSize()) {
            if (frame < 0) return@Canvas
            val fl = flight ?: return@Canvas
            fl.draw(this, look)
        }
    }
}
