package com.jpetrydesign.pyng.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.Pending
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What you changed on one attachment. Crop is a fraction of the picture as it's shown (after turning). */
data class MediaEdit(
    val turns: Int = 0,
    val straight: Float = 0f,
    val crop: List<Float> = FULL,
    val ratio: String = "Free",
    val effect: String = "Original",
    val trimStart: Long = 0L,
    val trimEnd: Long = -1L,
) {
    val isPlain get() = turns % 4 == 0 && straight == 0f && crop == FULL && effect == "Original" && trimStart == 0L && trimEnd < 0L
    companion object { val FULL = listOf(0f, 0f, 1f, 1f) }
}

/** An edited attachment remembers its original and the changes, so reopening it picks up where you left off. */
data class Edited(val original: Pending, val edit: MediaEdit)

/** Edits by the path of the attachment they produced. */
object ReviewEdits { val byPath = mutableStateMapOf<String, Edited>() }

private enum class Kind { Photo, Video, Gif, Other }
private fun kindOf(p: Pending) = when {
    p.mime == "image/gif" || p.name.lowercase().endsWith(".gif") -> Kind.Gif
    p.mime.startsWith("image/") -> Kind.Photo
    p.mime.startsWith("video/") -> Kind.Video
    else -> Kind.Other
}

private class Item(val original: Pending, edit: MediaEdit) {
    var edit by mutableStateOf(edit)
    /** Width over height of the picture as it's shown, filled in once it's loaded. */
    var frame by mutableStateOf(1f)
}

private val EFFECTS = listOf("Original", "Vivid", "Warm", "Cool", "Mono", "Fade", "Noir")

/** The color change behind each effect, as a 4x5 color matrix (same numbers for the preview and the saved photo). */
private fun effectMatrix(name: String): FloatArray {
    val m = android.graphics.ColorMatrix()
    fun sat(s: Float) = m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(s) })
    fun contrast(c: Float, b: Float = 0f) { val tr = (1f - c) * 128f + b; m.postConcat(android.graphics.ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, tr, 0f, c, 0f, 0f, tr, 0f, 0f, c, 0f, tr, 0f, 0f, 0f, 1f, 0f))) }
    fun tint(r: Float, g: Float, b: Float) = m.postConcat(android.graphics.ColorMatrix().apply { setScale(r, g, b, 1f) })
    when (name) {
        "Vivid" -> { sat(1.5f); contrast(1.08f) }
        "Warm" -> { tint(1.08f, 1.0f, 0.86f); sat(1.12f) }
        "Cool" -> { tint(0.9f, 1.0f, 1.1f) }
        "Mono" -> sat(0f)
        "Fade" -> { sat(0.8f); contrast(0.84f, 16f) }
        "Noir" -> { sat(0f); contrast(1.4f, -10f) }
    }
    return m.array
}

/** How much to zoom so a picture tilted by [deg] still fills its frame with no empty corners. */
private fun fillScale(w: Float, h: Float, deg: Float): Float {
    val a = Math.toRadians(abs(deg).toDouble())
    val c = cos(a).toFloat(); val s = sin(a).toFloat()
    return max((w * c + h * s) / w, (w * s + h * c) / h)
}

/** Opens a photo at a sensible size, upright, for showing and editing. */
private fun loadPhoto(path: String, maxSide: Int): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { d, info, _ ->
        val big = max(info.size.width, info.size.height)
        if (big > maxSide) d.setTargetSampleSize(max(1, big / maxSide))
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        d.isMutableRequired = false
    }
}.getOrNull()

/** Applies turns, tilt, crop and effect to a photo and saves it as a new JPEG. */
private suspend fun renderPhoto(repo: Repo, src: Pending, e: MediaEdit): Pending? = withContext(Dispatchers.Default) {
    val bmp = loadPhoto(src.path, 2560) ?: return@withContext null
    val odd = e.turns % 2 != 0
    val ew = if (odd) bmp.height else bmp.width
    val eh = if (odd) bmp.width else bmp.height
    val fill = fillScale(ew.toFloat(), eh.toFloat(), e.straight)
    val l = (e.crop[0] * ew).roundToInt(); val t = (e.crop[1] * eh).roundToInt()
    val w = max(1, (e.crop[2] * ew).roundToInt() - l); val h = max(1, (e.crop[3] * eh).roundToInt() - t)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    c.drawColor(android.graphics.Color.BLACK)
    c.translate(-l.toFloat(), -t.toFloat())
    c.translate(ew / 2f, eh / 2f)
    c.rotate(e.turns * 90f + e.straight)
    c.scale(fill, fill)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    if (e.effect != "Original") paint.colorFilter = android.graphics.ColorMatrixColorFilter(effectMatrix(e.effect))
    c.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)
    val f = repo.newFile("jpg")
    runCatching { f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) } }.getOrNull() ?: return@withContext null
    Pending(f.path, "image/jpeg", src.name.substringBeforeLast('.') + ".jpg")
}

/** Trims and crops a video on the phone with Android's video tools, saving a new MP4. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private suspend fun renderVideo(ctx: android.content.Context, repo: Repo, src: Pending, e: MediaEdit): Pending? = withContext(Dispatchers.Main) {
    val out = repo.newFile("mp4")
    val clip = androidx.media3.common.MediaItem.ClippingConfiguration.Builder().setStartPositionMs(e.trimStart)
        .apply { if (e.trimEnd > 0) setEndPositionMs(e.trimEnd) }.build()
    val item = androidx.media3.common.MediaItem.Builder().setUri(Uri.fromFile(File(src.path))).setClippingConfiguration(clip).build()
    val effects = if (e.crop != MediaEdit.FULL) {
        // Media3 measures crops from the middle, -1 to 1, with up being positive.
        val (l, t, r, b) = e.crop
        listOf<androidx.media3.common.Effect>(androidx.media3.effect.Crop(l * 2f - 1f, r * 2f - 1f, 1f - b * 2f, 1f - t * 2f))
    } else emptyList()
    val edited = androidx.media3.transformer.EditedMediaItem.Builder(item)
        .setEffects(androidx.media3.transformer.Effects(emptyList(), effects)).build()
    suspendCancellableCoroutine { cont ->
        val tr = androidx.media3.transformer.Transformer.Builder(ctx)
            .addListener(object : androidx.media3.transformer.Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    if (cont.isActive) cont.resume(Pending(out.path, "video/mp4", src.name.substringBeforeLast('.') + ".mp4"))
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                    out.delete()
                    if (cont.isActive) cont.resume(null)
                }
            }).build()
        tr.start(edited, out.path)
        cont.invokeOnCancellation { runCatching { tr.cancel() } }
    }
}

private fun durationOf(path: String): Long = runCatching {
    MediaMetadataRetriever().run { try { setDataSource(path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L } finally { release() } }
}.getOrDefault(0L)

private fun fmt(ms: Long): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

/**
 * Everything you're about to send, big: swipe or tap along the bottom to move between them, crop,
 * turn, tilt and add an effect to photos, trim or crop videos, and preview GIFs. Done saves the edits
 * and puts everything back on the message; tap any attachment again to come back here.
 */
@Composable
fun ReviewScreen(attachments: SnapshotStateList<Pending>, start: Int, onClose: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember {
        mutableStateListOf<Item>().apply {
            attachments.forEach { p -> val e = ReviewEdits.byPath[p.path]; add(Item(e?.original ?: p, e?.edit ?: MediaEdit())) }
        }
    }
    var cur by remember { mutableStateOf(start.coerceIn(0, max(0, attachments.size - 1))) }
    var tool by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    fun close() { shown = false; scope.launch { kotlinx.coroutines.delay(260); onClose() } }
    BackHandler { if (tool != null) tool = null else close() }
    val addMore = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        scope.launch {
            val before = items.size
            uris.forEach { u -> repo.importUri(u)?.let { items.add(Item(it, MediaEdit())) } }
            if (items.size > before) { cur = before; tool = null }
        }
    }

    fun done() {
        if (saving) return
        saving = true
        scope.launch {
            val result = ArrayList<Pending>()
            var failed = 0
            for (it in items) {
                val e = it.edit
                val k = kindOf(it.original)
                val made = when {
                    e.isPlain || k == Kind.Gif || k == Kind.Other -> it.original
                    k == Kind.Photo -> renderPhoto(repo, it.original, e)
                    else -> renderVideo(ctx, repo, it.original, e)
                }
                if (made == null) { failed++; result += it.original; continue }
                result += made
                if (made.path != it.original.path) ReviewEdits.byPath[made.path] = Edited(it.original, e)
            }
            attachments.clear(); attachments.addAll(result)
            saving = false
            if (failed > 0) repo.toast(if (failed == 1) "One edit couldn't be saved, so the original is attached" else "$failed edits couldn't be saved, so the originals are attached")
            close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0C0D11)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}.statusBarsPadding().navigationBarsPadding()) {
                // Top: close, where you are, Done
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable { close() }, contentAlignment = Alignment.Center) { Ico(Icons.close, Color.White, 16.dp, 2.4f) }
                    T(if (items.size > 1) "${cur + 1} of ${items.size}" else "Preview", size = 15.sp, weight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                    Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF4D80F2)).clickable { done() }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        T("Done", size = 14.5.sp, weight = FontWeight.Bold, color = Color.White)
                    }
                }
                val item = items.getOrNull(cur)
                if (item == null) { LaunchedEffect(Unit) { close() }; return@Column }
                val kind = kindOf(item.original)
                // The big preview. Swipe sideways to move between items when no tool is open.
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                        .pointerInput(cur, tool) {
                            if (tool != null) return@pointerInput
                            var dx = 0f
                            detectHorizontalDragGestures(onDragEnd = {
                                if (dx < -90f && cur < items.size - 1) cur++ else if (dx > 90f && cur > 0) cur--
                                dx = 0f
                            }) { ch, d -> dx += d; ch.consume() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (kind) {
                        Kind.Photo -> PhotoStage(item, tool == "Crop")
                        Kind.Video -> VideoStage(item, tool == "Crop")
                        Kind.Gif -> AsyncImage(File(item.original.path), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                        Kind.Other -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                Ico(if (item.original.mime.contains("vcard")) Icons.contact else Icons.file, Color.White, 34.dp)
                            }
                            Spacer(Modifier.height(12.dp))
                            T(item.original.name, size = 15.sp, color = Color.White, weight = FontWeight.Medium)
                        }
                    }
                    if (kind == Kind.Gif) T("GIF", size = 11.sp, weight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 7.dp, vertical = 3.dp))
                }
                // Tools for this kind of item
                val tools = when (kind) { Kind.Photo -> listOf("Crop", "Rotate", "Effects"); Kind.Video -> listOf("Trim", "Crop"); else -> emptyList() }
                if (tools.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                    tools.forEach { n ->
                        val on = tool == n
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent).clickable { tool = if (on) null else n }.padding(horizontal = 16.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Ico(when (n) { "Crop" -> CROP; "Rotate" -> ROTATE; "Effects" -> Icons.spark; else -> TRIM }, if (on) Color.White else Color.White.copy(alpha = 0.6f), 22.dp, 2f)
                            Spacer(Modifier.height(3.dp))
                            T(n, size = 11.5.sp, weight = FontWeight.SemiBold, color = if (on) Color.White else Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                // The open tool's controls
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    when {
                        kind == Kind.Gif -> Note("GIFs, stickers and clips send just as they are. Tap ✕ below to take one off.")
                        kind == Kind.Other -> Note("Files send as they are.")
                        tool == "Crop" -> CropControls(item)
                        tool == "Rotate" -> RotateControls(item)
                        tool == "Effects" -> EffectControls(item)
                        tool == "Trim" -> TrimControls(item)
                        else -> Note(if (kind == Kind.Video) "Trim to send just part of it, or crop it." else "Crop, turn or add an effect. Changes are saved when you tap Done.")
                    }
                }
                // Everything you're sending, plus room for more
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEachIndexed { i, x ->
                        val on = i == cur
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { cur = i; tool = null }.graphicsAlpha(if (on) 1f else 0.6f),
                        ) {
                            val k = kindOf(x.original)
                            if (k == Kind.Other) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Ico(Icons.file, Color.White, 20.dp) }
                            else AsyncImage(File(x.original.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (!x.edit.isPlain) Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).size(16.dp).clip(CircleShape).background(Color(0xFF4D80F2)), contentAlignment = Alignment.Center) { Ico(PENCIL, Color.White, 9.dp, 2.6f) }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { items.removeAt(i); if (cur >= items.size) cur = max(0, items.size - 1); tool = null; if (items.isEmpty()) { attachments.clear(); close() } },
                                contentAlignment = Alignment.Center,
                            ) { Ico(Icons.close, Color.White, 8.dp, 2.8f) }
                        }
                    }
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f))
                            .clickable { addMore.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        contentAlignment = Alignment.Center,
                    ) { Ico(Icons.plus, Color.White, 20.dp, 2.4f) }
                }
            }
        }
        if (saving) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(12.dp))
                T("Saving your edits…", size = 14.sp, color = Color.White)
            }
        }
    }
}

private fun Modifier.graphicsAlpha(a: Float) = this.graphicsLayer { alpha = a }

private const val CROP = "M6 2v14a2 2 0 0 0 2 2h14M2 6h14a2 2 0 0 1 2 2v14"
private const val ROTATE = "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5"
private const val TRIM = "M6 4v16M18 4v16M6 8h12M6 16h12"
private const val PENCIL = "M4 20l4-1 11-11-3-3L5 16z"

@Composable
private fun Note(text: String) {
    T(text, size = 13.sp, color = Color.White.copy(alpha = 0.65f), align = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (on) Color.White else Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, size = 13.sp, weight = FontWeight.SemiBold, color = if (on) Color(0xFF111111) else Color.White) }
}

/** The photo as it will send: turned, tilted, effect applied, and the crop box on top while cropping. */
@Composable
private fun PhotoStage(item: Item, cropping: Boolean) {
    val bmp by produceState<ImageBitmap?>(null, item.original.path) { value = withContext(Dispatchers.IO) { loadPhoto(item.original.path, 1600)?.asImageBitmap() } }
    val b = bmp ?: run { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp)); return }
    val e = item.edit
    val odd = e.turns % 2 != 0
    val ew = (if (odd) b.height else b.width).toFloat()
    val eh = (if (odd) b.width else b.height).toFloat()
    androidx.compose.runtime.SideEffect { item.frame = ew / eh }
    val filter = remember(e.effect) { if (e.effect == "Original") null else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(effectMatrix(e.effect))) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val dens = LocalDensity.current
        val availW = with(dens) { maxWidth.toPx() }; val availH = with(dens) { maxHeight.toPx() }
        val s = min(availW / ew, availH / eh)
        val dw = ew * s; val dh = eh * s
        val fill = fillScale(ew, eh, e.straight)
        Box(Modifier.size(with(dens) { dw.toDp() }, with(dens) { dh.toDp() })) {
            Canvas(Modifier.fillMaxSize()) {
                val c = e.crop
                val clip = !cropping && c != MediaEdit.FULL
                // Outside the crop is hidden once you're done cropping.
                val draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                    translate(dw / 2f, dh / 2f) {
                        rotate(e.turns * 90f + e.straight, pivot = Offset.Zero) {
                            scale(s * fill, s * fill, pivot = Offset.Zero) {
                                drawImage(b, dstOffset = IntOffset(-b.width / 2, -b.height / 2), dstSize = IntSize(b.width, b.height), colorFilter = filter)
                            }
                        }
                    }
                }
                if (clip) clipRect(c[0] * dw, c[1] * dh, c[2] * dw, c[3] * dh) { draw() } else draw()
            }
            if (cropping) CropBox(item, dw, dh, ew / eh)
        }
    }
}

/** Corner handles and a draggable middle. Fixed ratios keep their shape while you drag. */
@Composable
private fun CropBox(item: Item, dw: Float, dh: Float, frameRatio: Float) {
    val dens = LocalDensity.current
    val grab = with(dens) { 34.dp.toPx() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(dw, dh) {
            var mode = -1 // 0..3 corners, 4 move
            detectDragGestures(
                onDragStart = { p ->
                    val c = item.edit.crop
                    val pts = listOf(Offset(c[0] * dw, c[1] * dh), Offset(c[2] * dw, c[1] * dh), Offset(c[0] * dw, c[3] * dh), Offset(c[2] * dw, c[3] * dh))
                    val near = pts.indices.minByOrNull { (pts[it] - p).getDistance() }!!
                    mode = if ((pts[near] - p).getDistance() < grab) near else if (p.x in c[0] * dw..c[2] * dw && p.y in c[1] * dh..c[3] * dh) 4 else -1
                },
                onDragEnd = { mode = -1 }, onDragCancel = { mode = -1 },
            ) { ch, d ->
                if (mode < 0) return@detectDragGestures
                ch.consume()
                val e = item.edit
                var (l, t, r, b) = e.crop
                val dx = d.x / dw; val dy = d.y / dh
                val minS = 0.12f
                if (mode == 4) {
                    val mx = dx.coerceIn(-l, 1f - r); val my = dy.coerceIn(-t, 1f - b)
                    l += mx; r += mx; t += my; b += my
                } else {
                    if (mode == 0 || mode == 2) l = (l + dx).coerceIn(0f, r - minS) else r = (r + dx).coerceIn(l + minS, 1f)
                    if (mode == 0 || mode == 1) t = (t + dy).coerceIn(0f, b - minS) else b = (b + dy).coerceIn(t + minS, 1f)
                    ratioOf(e.ratio, frameRatio)?.let { want ->
                        // Keep the chosen shape: height follows width, anchored at the opposite edge.
                        val hFrac = ((r - l) * frameRatio / want).coerceAtMost(1f)
                        if (mode == 0 || mode == 1) t = (b - hFrac).coerceAtLeast(0f) else b = (t + hFrac).coerceAtMost(1f)
                    }
                }
                item.edit = e.copy(crop = listOf(l, t, r, b))
            }
        },
    ) {
        val (l, t, r, b) = item.edit.crop
        val x0 = l * dw; val y0 = t * dh; val x1 = r * dw; val y1 = b * dh
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset.Zero, Size(size.width, y0))
        drawRect(dim, Offset(0f, y1), Size(size.width, size.height - y1))
        drawRect(dim, Offset(0f, y0), Size(x0, y1 - y0))
        drawRect(dim, Offset(x1, y0), Size(size.width - x1, y1 - y0))
        drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2.dp.toPx()))
        for (i in 1..2) {
            val gx = x0 + (x1 - x0) * i / 3f; val gy = y0 + (y1 - y0) * i / 3f
            drawLine(Color.White.copy(alpha = 0.35f), Offset(gx, y0), Offset(gx, y1), 1f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x0, gy), Offset(x1, gy), 1f)
        }
        val k = 18.dp.toPx(); val w = 3.5.dp.toPx()
        listOf(Triple(x0, y0, 1 to 1), Triple(x1, y0, -1 to 1), Triple(x0, y1, 1 to -1), Triple(x1, y1, -1 to -1)).forEach { (x, y, s) ->
            drawLine(Color.White, Offset(x, y), Offset(x + k * s.first, y), w)
            drawLine(Color.White, Offset(x, y), Offset(x, y + k * s.second), w)
        }
    }
}

private fun ratioOf(name: String, frame: Float): Float? = when (name) { "Square" -> 1f; "4:3" -> 4f / 3f; "16:9" -> 16f / 9f; else -> null }

/** The biggest centered box of a given shape that fits the frame. */
private fun centeredCrop(want: Float?, frame: Float): List<Float> {
    if (want == null) return MediaEdit.FULL
    return if (frame > want) { val w = want / frame; listOf((1 - w) / 2, 0f, (1 + w) / 2, 1f) } else { val h = frame / want; listOf(0f, (1 - h) / 2, 1f, (1 + h) / 2) }
}

@Composable
private fun CropControls(item: Item) {
    val frame = item.frame
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Free", "Square", "4:3", "16:9", "Original").forEach { r ->
            Chip(r, item.edit.ratio == r) { item.edit = item.edit.copy(ratio = r, crop = centeredCrop(ratioOf(r, frame), frame)) }
        }
        Chip("Reset", false) { item.edit = item.edit.copy(ratio = "Free", crop = MediaEdit.FULL) }
    }
}

@Composable
private fun RotateControls(item: Item) {
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Turning swaps width and height, so the crop starts over.
            Chip("↻  Turn 90°", false) { item.edit = item.edit.copy(turns = (item.edit.turns + 1) % 4, crop = MediaEdit.FULL, ratio = "Free") }
            Chip("Reset", false) { item.edit = item.edit.copy(turns = 0, straight = 0f, crop = MediaEdit.FULL, ratio = "Free") }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Straighten", size = 12.5.sp, color = Color.White.copy(alpha = 0.7f))
            Slider(
                item.edit.straight, { v -> item.edit = item.edit.copy(straight = (v * 2).roundToInt() / 2f) }, valueRange = -15f..15f,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            )
            T("${item.edit.straight}°", size = 12.5.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun EffectControls(item: Item) {
    val small by produceState<ImageBitmap?>(null, item.original.path) { value = withContext(Dispatchers.IO) { loadPhoto(item.original.path, 220)?.asImageBitmap() } }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EFFECTS.forEach { n ->
            val on = item.edit.effect == n
            Column(Modifier.clickable { item.edit = item.edit.copy(effect = n) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(14.dp))) {
                    small?.let { Image(it, n, contentScale = ContentScale.Crop, colorFilter = if (n == "Original") null else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(effectMatrix(n))), modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.height(4.dp))
                T(n, size = 11.5.sp, weight = FontWeight.SemiBold, color = if (on) Color.White else Color.White.copy(alpha = 0.65f))
            }
        }
    }
}

/** The video, playing on a loop inside the trimmed part, with the crop box on top while cropping. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoStage(item: Item, cropping: Boolean) {
    val ctx = LocalContext.current
    val path = item.original.path
    val player = remember(path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            volume = 0f
            prepare(); playWhenReady = true
        }
    }
    var ratio by remember(path) { mutableStateOf(16f / 9f) }
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    // Loop just the part you're keeping.
    LaunchedEffect(player) {
        while (true) {
            val e = item.edit
            val end = if (e.trimEnd > 0) e.trimEnd else player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            if (player.currentPosition >= end - 30 || player.currentPosition < e.trimStart - 200 || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                player.seekTo(e.trimStart); player.play()
            }
            kotlinx.coroutines.delay(40)
        }
    }
    androidx.compose.runtime.SideEffect { item.frame = ratio }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val dens = LocalDensity.current
        val availW = with(dens) { maxWidth.toPx() }; val availH = with(dens) { maxHeight.toPx() }
        val s = min(availW / ratio, availH)
        val dw = ratio * s; val dh = s
        Box(Modifier.size(with(dens) { dw.toDp() }, with(dens) { dh.toDp() }).clip(RoundedCornerShape(10.dp))) {
            AndroidView(factory = { c -> android.view.TextureView(c).also { tv -> player.setVideoTextureView(tv) } }, modifier = Modifier.fillMaxSize())
            if (cropping) CropBox(item, dw, dh, ratio)
            else if (item.edit.crop != MediaEdit.FULL) Canvas(Modifier.fillMaxSize()) {
                val (l, t, r, b) = item.edit.crop
                val dim = Color.Black.copy(alpha = 0.75f)
                drawRect(dim, Offset.Zero, Size(size.width, t * dh)); drawRect(dim, Offset(0f, b * dh), Size(size.width, size.height - b * dh))
                drawRect(dim, Offset(0f, t * dh), Size(l * dw, (b - t) * dh)); drawRect(dim, Offset(r * dw, t * dh), Size(size.width - r * dw, (b - t) * dh))
            }
        }
    }
}

/** A strip of frames with handles at each end; the time kept is shown underneath. */
@Composable
private fun TrimControls(item: Item) {
    val path = item.original.path
    val dur by produceState(0L, path) { value = withContext(Dispatchers.IO) { durationOf(path) } }
    val frames by produceState<List<ImageBitmap>>(emptyList(), path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(path)
                        val d = (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L) * 1000
                        (0 until 8).mapNotNull { i -> getScaledFrameAtTime(d * i / 8 + d / 16, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 120, 120)?.asImageBitmap() }
                    } finally { release() }
                }
            }.getOrDefault(emptyList())
        }
    }
    if (dur <= 0) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)); return }
    val e = item.edit
    val endMs = if (e.trimEnd > 0) e.trimEnd else dur
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222))) {
            val w = with(LocalDensity.current) { maxWidth.toPx() }
            Row(Modifier.fillMaxSize()) { frames.forEach { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize()) } }
            val minGap = 1000L
            Canvas(
                Modifier.fillMaxSize().pointerInput(dur, w) {
                    var which = -1
                    detectDragGestures(onDragStart = { p ->
                        val a = item.edit.trimStart.toFloat() / dur * w
                        val b = (if (item.edit.trimEnd > 0) item.edit.trimEnd else dur).toFloat() / dur * w
                        which = if (abs(p.x - a) < abs(p.x - b)) 0 else 1
                    }, onDragEnd = { which = -1 }) { ch, d ->
                        ch.consume()
                        val ed = item.edit
                        val end = if (ed.trimEnd > 0) ed.trimEnd else dur
                        val delta = (d.x / w * dur).toLong()
                        item.edit = if (which == 0) ed.copy(trimStart = (ed.trimStart + delta).coerceIn(0L, end - minGap))
                        else ed.copy(trimEnd = (end + delta).coerceIn(ed.trimStart + minGap, dur).let { if (it >= dur) -1L else it })
                    }
                },
            ) {
                val a = e.trimStart.toFloat() / dur * size.width
                val b = endMs.toFloat() / dur * size.width
                drawRect(Color.Black.copy(alpha = 0.6f), Offset.Zero, Size(a, size.height))
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(b, 0f), Size(size.width - b, size.height))
                val y = Color(0xFFFFD54A)
                drawRoundRect(y, Offset(a, 0f), Size(b - a, size.height), CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()))
                listOf(a + 6.dp.toPx(), b - 6.dp.toPx()).forEach { x -> drawRoundRect(y, Offset(x - 2.5.dp.toPx(), size.height * 0.28f), Size(5.dp.toPx(), size.height * 0.44f), CornerRadius(3.dp.toPx())) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            T("${fmt(e.trimStart)} – ${fmt(endMs)}", size = 12.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            T("Sending ${fmt(endMs - e.trimStart)} of ${fmt(dur)}", size = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
