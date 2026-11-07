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
fun TranslateBlock(key: String, text: String, t: Tok) {
    val repo = LocalRepo.current
    if (!repo.prefs.state.on("translate")) return
    LaunchedEffect(key) { Translator.identify(key, text) }
    val done = Translator.result[key]
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

/** Cuts a photo into a sticker shape with an optional white outline, as a PNG. */
suspend fun makeSticker(ctx: Context, uri: Uri, shape: String, outline: Boolean, out: java.io.File): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val size = 512
        val pad = if (outline) 18f else 4f
        val inner = RectF(pad, pad, size - pad, size - pad)
        val path = Path().apply {
            when (shape) {
                "Circle" -> addOval(inner, Path.Direction.CW)
                "Rounded" -> addRoundRect(inner, size * 0.24f, size * 0.24f, Path.Direction.CW)
                else -> addOval(RectF(size * 0.1f + pad / 2, size * 0.06f + pad / 2, size * 0.9f - pad / 2, size - pad), Path.Direction.CW)
            }
        }
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        if (outline) {
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = pad * 2 })
        }
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        lc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK })
        lc.drawBitmap(Bitmap.createScaledBitmap(square, size, size, true), 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        c.drawBitmap(layer, 0f, 0f, null)
        FileOutputStream(out).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
        result
    }.getOrNull()
}

@Composable
fun StickerMaker(t: Tok, uri: Uri?, onPick: () -> Unit, onBack: () -> Unit, onDone: (Pending) -> Unit) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    var shape by remember { mutableStateOf("Cutout") }
    var outline by remember { mutableStateOf("White") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(uri, shape, outline) {
        if (uri == null) return@LaunchedEffect
        val f = repo.newFile("png")
        preview = makeSticker(ctx, uri, shape, outline == "White", f)
        file = f
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Ico(Icons.back, t.ink, 20.dp, 2f) }
            Spacer(Modifier.width(6.dp))
            T("Make a sticker", size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (preview != null) PillButton("Send", { file?.let { onDone(Pending(it.path, "image/png", "Sticker.png")) } }, height = 34.dp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)).background(t.surface).clickable(onClick = onPick), contentAlignment = Alignment.Center) {
                val b = preview
                if (b != null) Image(b.asImageBitmap(), "Sticker preview", Modifier.size(132.dp))
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { Ico(Icons.gallery, t.p, 26.dp); Spacer(Modifier.height(6.dp)); T("Pick a photo", size = 12.5.sp, color = t.soft) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Shape", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft)
                Seg(listOf("Cutout", "Circle", "Rounded"), shape, { shape = it }, height = 36.dp)
                T("Outline", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft)
                Seg(listOf("White", "None"), outline, { outline = it }, height = 36.dp)
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") LocalTok
}
