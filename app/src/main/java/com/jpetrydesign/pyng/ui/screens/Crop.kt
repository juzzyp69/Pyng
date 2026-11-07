package com.jpetrydesign.pyng.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A photo waiting to be cropped, and what to do with the square result. */
data class CropRequest(val uri: Uri, val title: String, val onDone: (Bitmap) -> Unit)

/**
 * Full-screen circle crop: pinch to zoom, drag to move, double-tap to reset.
 * The circle always stays filled, so there are never empty edges in the photo.
 */
@Composable
fun PhotoCropper(req: CropRequest?, onClose: () -> Unit) {
    var last by remember { mutableStateOf(req) }
    if (req != null) last = req
    AnimatedVisibility(req != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.zIndex(50f)) {
        val r = last ?: return@AnimatedVisibility
        BackHandler(onBack = onClose)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var bmp by remember(r.uri) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(r.uri) { bmp = withContext(Dispatchers.IO) { decode(ctx, r.uri) } }
        val zoom = remember(r.uri) { Animatable(1f) }
        val pan = remember(r.uri) { Animatable(Offset.Zero, Offset.VectorConverter) }
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10))) {
            val b = bmp
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
                val d = min(w, h) - with(density) { 48.dp.toPx() } // circle diameter
                if (b != null) {
                    val base = max(d / b.width, d / b.height) // covers the circle at zoom 1
                    fun clamp(z: Float, o: Offset): Offset {
                        val s = base * z
                        val mx = max(0f, (b.width * s - d) / 2); val my = max(0f, (b.height * s - d) / 2)
                        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
                    }
                    Image(
                        b.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .requiredSize(with(density) { b.width.toDp() }, with(density) { b.height.toDp() })
                            .align(Alignment.Center)
                            .graphicsLayer {
                                val s = base * zoom.value
                                scaleX = s; scaleY = s
                                translationX = pan.value.x; translationY = pan.value.y
                            },
                    )
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(b) {
                                detectTransformGestures { _, move, z, _ ->
                                    val nz = (zoom.value * z).coerceIn(1f, 6f)
                                    scope.launch { zoom.snapTo(nz); pan.snapTo(clamp(nz, pan.value + move)) }
                                }
                            }
                            .pointerInput(b) {
                                detectTapGestures(onDoubleTap = {
                                    scope.launch {
                                        launch { zoom.animateTo(1f, spring(dampingRatio = 0.8f)) }
                                        pan.animateTo(Offset.Zero, spring(dampingRatio = 0.8f))
                                    }
                                })
                            },
                    )
                    // Dim everything outside the circle.
                    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                        drawRect(Color.Black.copy(alpha = 0.62f))
                        drawCircle(Color.Black, radius = d / 2, center = Offset(w / 2, h / 2), blendMode = BlendMode.Clear)
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = d / 2, center = Offset(w / 2, h / 2), style = Stroke(1.5.dp.toPx()))
                    }
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                        T(r.title, size = 19.sp, weight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                        T("Pinch to zoom, drag to move", size = 13.5.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
                        Box(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton("Cancel", onClick = onClose, modifier = Modifier.weight(1f), primary = false, height = 50.dp)
                            PillButton("Done", onClick = {
                                val s = base * zoom.value
                                val left = ((-d / 2 - pan.value.x) / s + b.width / 2f).roundToInt()
                                val top = ((-d / 2 - pan.value.y) / s + b.height / 2f).roundToInt()
                                val side = (d / s).roundToInt()
                                val x = left.coerceIn(0, max(0, b.width - 1)); val y = top.coerceIn(0, max(0, b.height - 1))
                                val sz = side.coerceAtMost(min(b.width - x, b.height - y)).coerceAtLeast(1)
                                val sq = Bitmap.createBitmap(b, x, y, sz, sz)
                                val out = if (sz > 640) Bitmap.createScaledBitmap(sq, 640, 640, true) else sq
                                r.onDone(out)
                                onClose()
                            }, modifier = Modifier.weight(1f), height = 50.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Loads a picked photo, upright and no bigger than needed. */
private fun decode(ctx: android.content.Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = max(info.size.width, info.size.height)
            if (big > 2400) { val k = 2400f / big; dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt()) }
        }
    } else ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()
