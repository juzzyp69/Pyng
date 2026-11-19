@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jpetrydesign.pyng.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.jpetrydesign.pyng.data.Pending
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Chips
import com.jpetrydesign.pyng.ui.kit.Seg
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Locale

// ---------- translation (on-device) ----------

object Translator {
    val lang = mutableStateMapOf<String, String>()
    val result = mutableStateMapOf<String, String>()
    val busy = mutableStateMapOf<String, Boolean>()
    private val asked = mutableSetOf<String>()

    val target: String get() = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH

    fun identify(key: String, text: String) {
        if (key in asked || text.length < 8 || text.count { it.isLetter() } < 6) return
        asked += key
        runCatching {
            LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { code -> lang[key] = code }
        }
    }

    fun canOffer(key: String): Boolean {
        val l = lang[key] ?: return false
        if (l == "und") return false
        val src = TranslateLanguage.fromLanguageTag(l) ?: return false
        return src != target
    }

    fun name(key: String): String = lang[key]?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }.orEmpty()

    fun translate(key: String, text: String, onFail: (String) -> Unit) {
        val src = lang[key]?.let { TranslateLanguage.fromLanguageTag(it) } ?: return
        busy[key] = true
        runCatching {
            val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(target).build())
            client.downloadModelIfNeeded()
                .addOnSuccessListener {
                    client.translate(text)
                        .addOnSuccessListener { out -> result[key] = out; busy.remove(key) }
                        .addOnFailureListener { busy.remove(key); onFail("Couldn't translate") }
                }
                .addOnFailureListener { busy.remove(key); onFail("Couldn't download the translation model") }
        }.onFailure { busy.remove(key); onFail("Couldn't translate") }
    }
}

/** "Translate" under a message in another language, and the translation once done. */
@Composable
fun TranslateBlock(key: String, text: String, t: Tok, convId: String = "") {
    val repo = LocalRepo.current
    // Off unless you turn on Translate for this chat from the + menu.
    if (!repo.prefs.state.on("translate") || repo.prefs.state.opt("tr:$convId") != "1") return
    LaunchedEffect(key) { Translator.identify(key, text) }
    val done = Translator.result[key]
    LaunchedEffect(key, Translator.canOffer(key)) { if (Translator.canOffer(key) && done == null && Translator.busy[key] != true) Translator.translate(key, text) { } }
    var showOriginal by remember { mutableStateOf(false) }
    if (done != null && !showOriginal) {
        Column(Modifier.padding(top = 4.dp).fillMaxWidth(0.78f).clip(RoundedCornerShape(14.dp)).background(t.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
            T(done, size = 13.5.sp, lineHeight = 19.sp)
            T("Translated from ${Translator.name(key)} · Show original", size = 11.5.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.padding(top = 4.dp).clickable { showOriginal = true })
        }
    } else if (Translator.canOffer(key)) {
        Row(Modifier.padding(top = 4.dp).clickable {
            showOriginal = false
            if (done == null) Translator.translate(key, text) { repo.toast(it) }
        }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Translator.busy[key] == true) { CircularProgressIndicator(Modifier.size(12.dp), color = t.p, strokeWidth = 1.5.dp); Spacer(Modifier.width(6.dp)) }
            T("Translate", size = 12.sp, weight = FontWeight.SemiBold, color = t.p)
        }
    }
}

// ---------- sticker maker ----------

/** [b] turned the way the photo's orientation tag says (camera photos are often saved sideways). */
fun upright(ctx: Context, uri: Uri, b: Bitmap): Bitmap {
    val o = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1) }
    }.getOrNull() ?: 1
    val m = android.graphics.Matrix()
    when (o) {
        2 -> m.setScale(-1f, 1f)
        3 -> m.setRotate(180f)
        4 -> m.setScale(1f, -1f)
        5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
        6 -> m.setRotate(90f)
        7 -> { m.setRotate(270f); m.postScale(-1f, 1f) }
        8 -> m.setRotate(270f)
        else -> return b
    }
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
}

/** Loads a photo at about 1024px, turned upright. */
private fun loadUpright(ctx: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
    val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    // Camera photos are often stored sideways with a note saying which way is up: turn them upright.
    return upright(ctx, uri, raw)
}

/**
 * Finds the subject of a photo (a person, pet or thing) and cuts it out, right on the phone.
 * Null when there's nothing clear to cut out or the cutter isn't ready yet.
 */
private suspend fun cutOut(src: Bitmap): Bitmap? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    runCatching {
        val seg = com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(
            com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder().enableForegroundBitmap().build(),
        )
        seg.process(com.google.mlkit.vision.common.InputImage.fromBitmap(src, 0))
            .addOnSuccessListener { r -> seg.close(); if (cont.isActive) cont.resumeWith(Result.success(r.foregroundBitmap)) }
            .addOnFailureListener { seg.close(); if (cont.isActive) cont.resumeWith(Result.success(null)) }
    }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
}

/** The smallest box around everything that isn't see-through. */
private fun trimmed(b: Bitmap): Bitmap {
    val w = b.width; val h = b.height
    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
    var l = w; var r = -1; var top = h; var bot = -1
    for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 24) { if (x < l) l = x; if (x > r) r = x; if (y < top) top = y; if (y > bot) bot = y }
    return if (r < l || bot < top) b else Bitmap.createBitmap(b, l, top, r - l + 1, bot - top + 1)
}

/** A solid copy of [b]'s shape, grown by [radius] in every direction (the base of an outline). */
private fun grown(b: Bitmap, radius: Float, color: Int): Bitmap {
    val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = android.graphics.PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
    val steps = 36
    for (ring in listOf(radius, radius * 0.6f, radius * 0.3f)) for (i in 0 until steps) {
        val a = i * (2 * Math.PI / steps)
        c.drawBitmap(b, (Math.cos(a) * ring).toFloat(), (Math.sin(a) * ring).toFloat(), tint)
    }
    c.drawBitmap(b, 0f, 0f, tint)
    return out
}

/**
 * Makes a 512px see-through PNG sticker: the subject cut out (or a circle or rounded square), with an
 * effect: Outline (white edge), Puffy (thick edge, soft shadow, a little shine), Comic (black and white
 * edge), Holo (rainbow sheen) or Glitter (sparkles). Saved to [out].
 */
suspend fun makeSticker(ctx: Context, uri: Uri, shape: String, effect: String, out: java.io.File, turns: Int = 0, tilt: Float = 0f): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val upright = loadUpright(ctx, uri) ?: return@runCatching null
        // Quarter turns spin the photo itself, before the cutout, so the edge follows the new angle.
        val src = if (turns % 4 == 0) upright else Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, android.graphics.Matrix().apply { postRotate(90f * (turns % 4)) }, true)
        val size = 512
        val pad = when (effect) { "None" -> 6f; "Comic" -> 30f; "Puffy" -> 34f; else -> 24f }
        val inner = (size - pad * 2).toInt()
        // The subject on its own, scaled to fit with room for the edge.
        val subject: Bitmap = run {
            val cut = if (shape == "Cutout") cutOut(src)?.let { trimmed(it) } else null
            val base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(base)
            if (cut != null) {
                val k = minOf(inner.toFloat() / cut.width, inner.toFloat() / cut.height)
                val w = cut.width * k; val h = cut.height * k
                c.drawBitmap(cut, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } else {
                // No clear subject (or a shape was picked): a circle or rounded square from the middle of the photo.
                val side = minOf(src.width, src.height)
                val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                val rect = RectF(pad, pad, size - pad, size - pad)
                val path = Path().apply { if (shape == "Rounded") addRoundRect(rect, size * 0.22f, size * 0.22f, Path.Direction.CW) else addOval(rect, Path.Direction.CW) }
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK })
                c.drawBitmap(Bitmap.createScaledBitmap(sq, size, size, true), 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
            }
            base
        }
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        val white = android.graphics.Color.WHITE
        when (effect) {
            "Outline" -> c.drawBitmap(grown(subject, 14f, white), 0f, 0f, null)
            "Puffy" -> {
                val edge = grown(subject, 22f, white)
                // A soft shadow under the whole sticker, then the thick white edge.
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.argb(70, 0, 0, 0), PorterDuff.Mode.SRC_IN)
                    maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                c.drawBitmap(edge.extractAlpha(), 0f, 8f, shadow)
                c.drawBitmap(edge, 0f, 0f, null)
            }
            "Comic" -> {
                c.drawBitmap(grown(subject, 24f, android.graphics.Color.BLACK), 0f, 0f, null)
                c.drawBitmap(grown(subject, 12f, white), 0f, 0f, null)
            }
            "Holo", "Glitter" -> c.drawBitmap(grown(subject, 12f, white), 0f, 0f, null)
        }
        c.drawBitmap(subject, 0f, 0f, null)
        when (effect) {
            // A shine across the top of the puffy sticker, only where the sticker is.
            "Puffy" -> c.drawRect(0f, 0f, size.toFloat(), size * 0.55f, Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 0f, size * 0.55f, android.graphics.Color.argb(70, 255, 255, 255), android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            })
            // Rainbow foil: soft bands of color at an angle, with a bright streak.
            "Holo" -> {
                val colors = intArrayOf(0x66FF6FAE, 0x66FFD36F, 0x666FFFC8, 0x666FB8FF, 0x66B98CFF, 0x66FF6FAE)
                c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
                    shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors, null, android.graphics.Shader.TileMode.MIRROR)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                })
                c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
                    shader = android.graphics.LinearGradient(size * 0.25f, 0f, size * 0.75f, size.toFloat(), intArrayOf(0, 0x80FFFFFF.toInt(), 0), floatArrayOf(0.35f, 0.5f, 0.65f), android.graphics.Shader.TileMode.CLAMP)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                })
            }
            // Little four-point sparkles scattered over the sticker, the same each time for the same photo.
            "Glitter" -> {
                val rnd = java.util.Random(uri.hashCode().toLong())
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
                repeat(46) {
                    val x = rnd.nextFloat() * size; val y = rnd.nextFloat() * size; val r = 3f + rnd.nextFloat() * 7f
                    p.color = if (rnd.nextBoolean()) android.graphics.Color.WHITE else android.graphics.Color.argb(255, 255, 226, 140)
                    p.alpha = 150 + rnd.nextInt(105)
                    val star = Path().apply {
                        moveTo(x, y - r); quadTo(x, y, x + r, y); quadTo(x, y, x, y + r); quadTo(x, y, x - r, y); quadTo(x, y, x, y - r); close()
                    }
                    c.drawPath(star, p)
                }
            }
        }
        // A tilt turns the finished sticker a little, shrunk just enough that no corner gets cut off.
        val final = if (tilt == 0f) result else {
            val rad = Math.toRadians(tilt.toDouble())
            val fit = (1.0 / (kotlin.math.abs(kotlin.math.cos(rad)) + kotlin.math.abs(kotlin.math.sin(rad)))).toFloat().coerceAtLeast(0.84f)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
                Canvas(b).apply { rotate(tilt, size / 2f, size / 2f); scale(fit, fit, size / 2f, size / 2f); drawBitmap(result, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
            }
        }
        FileOutputStream(out).use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
        final
    }.getOrNull()
}

/** Your saved stickers, newest first. */
fun myStickers(ctx: Context): List<java.io.File> =
    java.io.File(ctx.filesDir, "stickers").listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

private fun keepSticker(ctx: Context, f: java.io.File): java.io.File {
    val dir = java.io.File(ctx.filesDir, "stickers").apply { mkdirs() }
    return java.io.File(dir, "sticker-" + System.currentTimeMillis() + ".png").also { f.copyTo(it, overwrite = true) }
}

@Composable
fun StickerMaker(t: Tok, uri: Uri?, onPick: () -> Unit, onBack: () -> Unit, onDone: (Pending) -> Unit) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    var shape by remember { mutableStateOf("Cutout") }
    var effect by remember { mutableStateOf("Outline") }
    var turns by remember { mutableStateOf(0) }
    var tilt by remember { mutableStateOf("Straight") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var working by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf(myStickers(ctx)) }
    LaunchedEffect(uri, shape, effect, turns, tilt) {
        if (uri == null) return@LaunchedEffect
        working = true
        val f = repo.newFile("png")
        preview = makeSticker(ctx, uri, shape, effect, f, turns, when (tilt) { "Tilt left" -> -10f; "Tilt right" -> 10f; else -> 0f })
        file = f
        working = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Ico(Icons.back, t.ink, 20.dp, 2f) }
            Spacer(Modifier.width(6.dp))
            T("Make a sticker", size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            if (preview != null && !working) PillButton("Send", {
                file?.let { f -> mine = listOf(keepSticker(ctx, f)) + mine; onDone(Pending(f.path, "image/png", "Sticker.png")) }
            }, height = 36.dp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // The preview sits on a checkerboard so you can see what's see-through.
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)).background(t.surface).clickable(onClick = onPick), contentAlignment = Alignment.Center) {
                val b = preview
                if (b != null) Image(b.asImageBitmap(), "Sticker preview", Modifier.size(136.dp))
                else if (!working) Column(horizontalAlignment = Alignment.CenterHorizontally) { Ico(Icons.gallery, t.p, 26.dp); Spacer(Modifier.height(6.dp)); T("Pick a photo", size = 12.5.sp, color = t.soft) }
                if (working) CircularProgressIndicator(color = t.p, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                // Turn the photo a quarter at a time.
                if (uri != null) Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(t.sheet).clickable { turns = (turns + 1) % 4 },
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.refresh, t.ink, 16.dp, 2f) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Shape", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft)
                Chips(listOf("Cutout", "Circle", "Rounded"), shape, { shape = it })
                T("Effect", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft)
                Chips(listOf("None", "Outline", "Puffy", "Comic", "Holo", "Glitter"), effect, { effect = it })
                T("Turn", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft)
                Chips(listOf("Straight", "Tilt left", "Tilt right"), tilt, { tilt = it })
            }
        }
        if (shape == "Cutout" && uri != null && !working && preview != null) T("Cut out automatically. If it missed, try Circle or Rounded.", size = 11.5.sp, color = t.faint, modifier = Modifier.padding(top = 8.dp))
        if (mine.isNotEmpty()) {
            T("My stickers", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mine.size) { i ->
                    val f = mine[i]
                    val bmp = remember(f.path) { BitmapFactory.decodeFile(f.path) }
                    if (bmp != null) Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(t.surface).combinedClickable(
                        onClick = { onDone(Pending(f.path, "image/png", "Sticker.png")) },
                        onLongClick = { f.delete(); mine = myStickers(ctx); repo.toast("Sticker removed") },
                    ), contentAlignment = Alignment.Center) { Image(bmp.asImageBitmap(), "Sticker", Modifier.size(62.dp)) }
                }
            }
        }
    }
}
