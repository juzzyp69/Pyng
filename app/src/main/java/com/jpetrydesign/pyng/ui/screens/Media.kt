package com.jpetrydesign.pyng.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.LocalNav
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Hairline
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

// ---------- voice messages ----------

/** The one voice message playing right now. Starting another stops this one. */
object Voice {
    var key by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var pos by mutableIntStateOf(0)
    var dur by mutableIntStateOf(0)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    private var mp: MediaPlayer? = null

    /** Waveforms and transcripts, worked out once per message. */
    val waves = mutableStateMapOf<String, FloatArray>()
    val texts = mutableStateMapOf<String, String>()
    val working = mutableStateMapOf<String, Boolean>()

    fun toggle(k: String, path: String, startAt: Float = -1f) {
        val cur = mp
        if (key == k && cur != null) {
            if (startAt >= 0f) seekTo(startAt)
            if (playing && startAt < 0f) { cur.pause(); playing = false } else { cur.start(); applySpeed(); playing = true }
            return
        }
        stop()
        runCatching {
            val p = MediaPlayer().apply { setDataSource(path); prepare() }
            p.setOnCompletionListener { playing = false; pos = 0; runCatching { it.seekTo(0) } }
            mp = p; key = k; dur = p.duration
            if (startAt > 0f) p.seekTo((startAt * p.duration).toInt())
            p.start(); applySpeed(); playing = true
        }
    }

    fun seekTo(frac: Float) { mp?.let { val to = (frac.coerceIn(0f, 1f) * it.duration).toInt(); it.seekTo(to); pos = to } }

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        if (playing) applySpeed()
    }

    // Changing speed while paused would start playback, so it's only applied while playing.
    private fun applySpeed() { runCatching { mp?.let { it.playbackParams = it.playbackParams.setSpeed(speed) } } }

    fun tick() { val p = mp ?: return; if (playing) pos = runCatching { p.currentPosition }.getOrDefault(pos) }

    fun stop() {
        runCatching { mp?.release() }
        mp = null; key = null; playing = false; pos = 0; dur = 0
    }
}

/** A voice message: play/pause, a waveform you can tap or drag to jump, speed, and a transcript. */
@Composable
fun VoicePlayer(m: Msg, ref: MediaRef, path: String, busy: Boolean, shape: RoundedCornerShape, bg: Brush, fg: Color, sub: Color, me: Boolean) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val k = ref.id.ifEmpty { m.key }
    val active = Voice.key == k
    val playing = active && Voice.playing
    var fileDur by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(path) {
        if (path.isEmpty()) return@LaunchedEffect
        fileDur = withContext(Dispatchers.IO) {
            runCatching { android.media.MediaMetadataRetriever().run { setDataSource(path); val d = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0; release(); d } }.getOrDefault(0)
        }
        if (Voice.waves[k] == null) Voice.waves[k] = waveOf(path)
        Voice.texts[k] ?: Transcripts.load(ctx, k)?.let { Voice.texts[k] = it }
        if (repo.prefs.state.on("autoTranscribe") && Voice.texts[k] == null && hasMic(ctx)) Transcripts.run(ctx, k, path) { repo.toast(it) }
    }
    LaunchedEffect(playing) { while (playing) { Voice.tick(); delay(50) } }
    val dur = if (active && Voice.dur > 0) Voice.dur else fileDur
    val progress = if (active && dur > 0) Voice.pos.toFloat() / dur else 0f
    val placeholder = remember(k) { FloatArray(32) { i -> (0.3f + ((k.hashCode() shr (i % 16)) and 7) / 10f).coerceIn(0.25f, 1f) } }
    val bars = Voice.waves[k] ?: placeholder
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && path.isNotEmpty()) scope.launch { Transcripts.run(ctx, k, path) { repo.toast(it) } } else if (!ok) repo.toast("Allow the microphone to transcribe voice messages")
    }
    val chip = if (me) Color.White.copy(alpha = 0.2f) else t.tone
    Column(Modifier.widthIn(max = 280.dp).clip(shape).background(bg).animateContentSize().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(chip).clickable {
                    if (path.isEmpty()) repo.ensureMedia(ref, manual = true) else Voice.toggle(k, path)
                },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
                else if (path.isEmpty()) Ico(Icons.download, fg, 17.dp, 2f)
                else Ico(if (playing) Icons.pause else Icons.play, fg, 16.dp, 1f, fill = fg)
            }
            Spacer(Modifier.width(10.dp))
            // The waveform: what's played is solid, the rest faded. Tap or drag anywhere on it to jump.
            var waveW by remember { mutableIntStateOf(1) }
            Row(
                Modifier.weight(1f).height(32.dp).onSizeChanged { waveW = it.width.coerceAtLeast(1) }
                    .pointerInput(k, path) {
                        detectTapGestures { o -> if (path.isNotEmpty()) Voice.toggle(k, path, (o.x / waveW).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(k, path) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> if (path.isNotEmpty() && Voice.key != k) Voice.toggle(k, path, (o.x / waveW).coerceIn(0f, 1f)) },
                        ) { ch, _ -> if (Voice.key == k) Voice.seekTo(ch.position.x / waveW) }
                    },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                bars.forEachIndexed { i, h ->
                    val on = (i + 0.5f) / bars.size <= progress
                    Box(Modifier.width(3.dp).height((4f + 26f * h).dp).clip(RoundedCornerShape(2.dp)).background(if (on) fg else fg.copy(alpha = 0.38f)))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                val shown = if (active && Voice.pos > 0) Voice.pos else dur
                val secs = shown / 1000
                T(if (shown > 0) "%d:%02d".format(secs / 60, secs % 60) else "Voice", size = 12.5.sp, weight = FontWeight.SemiBold, color = fg)
                Spacer(Modifier.height(3.dp))
                val sp = if (active) Voice.speed else 1f
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(chip).clickable { if (!active && path.isNotEmpty()) Voice.toggle(k, path); Voice.cycleSpeed() }
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) { T(if (sp == 1f) "1×" else if (sp == 1.5f) "1.5×" else "2×", size = 11.sp, weight = FontWeight.Bold, color = fg) }
            }
        }
        val text = Voice.texts[k]
        val working = Voice.working[k] == true
        if (text != null || working) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(1.dp).background(fg.copy(alpha = 0.16f)))
            Spacer(Modifier.height(7.dp))
            T(if (working) "Transcribing…" else text!!.ifBlank { "No words heard in this one" }, size = 14.sp, color = if (working || text.isNullOrBlank()) sub else fg, lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp))
        } else if (path.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.padding(start = 48.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    if (hasMic(ctx)) scope.launch { Transcripts.run(ctx, k, path) { repo.toast(it) } } else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Icons.textSize, sub, 14.dp, 2f)
                Spacer(Modifier.width(5.dp))
                T("Transcribe", size = 12.5.sp, weight = FontWeight.SemiBold, color = sub)
            }
        }
    }
}

private fun hasMic(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Reads the recording and boils it down to [n] bar heights, 0 to 1. */
private suspend fun waveOf(path: String, n: Int = 32): FloatArray = withContext(Dispatchers.IO) {
    runCatching {
        val levels = FloatArray(n)
        decodePcm(path) { samples, count, frac, _, _ ->
            var sum = 0.0
            var i = 0
            while (i < count) { val v = samples[i].toDouble(); sum += v * v; i += 2 }
            val rms = sqrt(sum / maxOf(1, count / 2)).toFloat() / 32768f
            val b = (frac * n).toInt().coerceIn(0, n - 1)
            if (rms > levels[b]) levels[b] = rms
        }
        // Fill any gaps, then scale so the loudest bar is full height.
        for (i in 1 until n) if (levels[i] == 0f) levels[i] = levels[i - 1] * 0.8f
        val top = levels.maxOrNull()?.takeIf { it > 0.0001f } ?: return@runCatching null
        FloatArray(n) { (sqrt(levels[it] / top)).coerceIn(0.12f, 1f) }
    }.getOrNull() ?: FloatArray(n) { 0.3f + ((path.hashCode() shr (it % 16)) and 7) / 12f }
}

/**
 * Decodes an audio file to 16-bit samples, handing each chunk to [chunk] with how far through
 * the recording it is (0 to 1) and the sample rate and channel count. Returns the final rate and channels.
 */
private fun decodePcm(path: String, chunk: (ShortArray, Int, Float, Int, Int) -> Unit): Pair<Int, Int> {
    val ex = MediaExtractor()
    ex.setDataSource(path)
    val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    ex.selectTrack(track)
    val fmt = ex.getTrackFormat(track)
    val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
    var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(fmt, null, null, 0)
    codec.start()
    val info = MediaCodec.BufferInfo()
    var inDone = false
    var outDone = false
    var guard = 0
    var buf = ShortArray(8192)
    try {
        while (!outDone && guard++ < 400_000) {
            if (!inDone) {
                val i = codec.dequeueInputBuffer(5_000)
                if (i >= 0) {
                    val ib = codec.getInputBuffer(i)!!
                    val size = ex.readSampleData(ib, 0)
                    if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 5_000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f = codec.outputFormat
                rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (o >= 0) {
                val ob = codec.getOutputBuffer(o)
                if (ob != null && info.size > 0) {
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val count = sb.remaining()
                    if (buf.size < count) buf = ShortArray(count)
                    sb.get(buf, 0, count)
                    chunk(buf, count, if (durUs > 0) (info.presentationTimeUs.toDouble() / durUs).toFloat().coerceIn(0f, 1f) else 0f, rate, channels)
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
            }
        }
    } finally {
        runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { ex.release() }
    }
    return rate to channels
}

/** Turns voice messages into text with the phone's own speech recognition. Nothing leaves the phone. */
object Transcripts {
    private fun file(ctx: Context, key: String) = File(File(ctx.filesDir, "transcripts").apply { mkdirs() }, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")

    fun load(ctx: Context, key: String): String? = runCatching { file(ctx, key).takeIf { it.exists() }?.readText() }.getOrNull()

    suspend fun run(ctx: Context, key: String, path: String, say: (String) -> Unit) {
        if (Voice.working[key] == true) return
        Voice.working[key] = true
        try {
            if (Build.VERSION.SDK_INT < 33) { say("Transcribing needs Android 13 or newer"); return }
            // The recognizer takes plain 16 kHz mono audio, so the recording is converted first.
            val pcm = withContext(Dispatchers.IO) { runCatching { toPcm16k(ctx, path) }.getOrNull() }
            if (pcm == null) { say("Couldn't read this voice message"); return }
            val text = withTimeoutOrNull(120_000) { recognize(ctx, pcm) }
            pcm.delete()
            if (text == null) { say("Couldn't transcribe this one"); return }
            Voice.texts[key] = text
            runCatching { file(ctx, key).writeText(text) }
        } finally {
            Voice.working.remove(key)
        }
    }

    private fun toPcm16k(ctx: Context, path: String): File {
        val out = File(ctx.cacheDir, "stt-" + System.nanoTime() + ".pcm")
        java.io.BufferedOutputStream(out.outputStream()).use { bos ->
            // Down-mix to mono and resample to 16 kHz as the chunks arrive.
            var acc = 0.0
            decodePcm(path) { s, count, _, rate, channels ->
                val chs = channels.coerceAtLeast(1)
                val step = rate.coerceAtLeast(8_000) / 16_000.0
                var i = 0
                while (i + chs - 1 < count) {
                    var v = 0
                    for (c in 0 until chs) v += s[i + c]
                    acc += 1.0
                    if (acc >= step) {
                        acc -= step
                        val mono = v / chs
                        bos.write(mono and 0xFF); bos.write((mono shr 8) and 0xFF)
                    }
                    i += chs
                }
            }
        }
        return out
    }

    @android.annotation.SuppressLint("NewApi")
    private suspend fun recognize(ctx: Context, pcm: File): String? = withContext(Dispatchers.Main) {
        val onDevice = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(ctx)) return@withContext null
        val sr = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else SpeechRecognizer.createSpeechRecognizer(ctx)
        val pfd = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            suspendCancellableCoroutine { cont ->
                val parts = ArrayList<String>()
                fun done(v: String?) { if (cont.isActive) cont.resume(v) }
                fun best(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onSegmentResults(segmentResults: Bundle) { best(segmentResults).takeIf { it.isNotEmpty() }?.let { parts += it } }
                    override fun onEndOfSegmentedSession() { done(parts.joinToString(" ")) }
                    override fun onResults(results: Bundle?) { best(results).takeIf { it.isNotEmpty() }?.let { parts += it }; done(parts.joinToString(" ")) }
                    override fun onError(error: Int) {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) done(parts.joinToString(" "))
                        else done(if (parts.isNotEmpty()) parts.joinToString(" ") else null)
                    }
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                sr.startListening(intent)
                cont.invokeOnCancellation { runCatching { sr.cancel() } }
            }
        } finally {
            runCatching { sr.destroy() }
            runCatching { pfd.close() }
        }
    }
}

// ---------- shared contacts ----------

data class VCard(val name: String, val phones: List<String>, val emails: List<String>, val org: String, val photo: String?)

/** Reads a .vcf contact card: name, numbers, emails, company and its photo. */
fun parseVCard(ctx: Context, path: String): VCard? = runCatching {
    val raw = File(path).readText()
    // Long lines are folded onto the next line with a leading space; put them back together.
    val lines = raw.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split("\n")
    var fn = ""; var n = ""; var org = ""; var photo: String? = null
    val phones = ArrayList<String>(); val emails = ArrayList<String>()
    for (l in lines) {
        val colon = l.indexOf(':'); if (colon < 0) continue
        val head = l.substring(0, colon).uppercase(); val v = l.substring(colon + 1).trim()
        val prop = head.substringBefore(';').substringAfter('.')
        when (prop) {
            "FN" -> fn = v
            "N" -> n = v.split(';').let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(2), p.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ") }
            "TEL" -> v.removePrefix("tel:").takeIf { it.isNotBlank() }?.let { phones += it }
            "EMAIL" -> v.takeIf { it.isNotBlank() }?.let { emails += it }
            "ORG" -> org = v.replace(';', ' ').trim()
            "PHOTO" -> {
                val data = v.substringAfter("base64,", v)
                if (head.contains("ENCODING=B") || head.contains("BASE64") || v.startsWith("data:")) runCatching {
                    val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                    val f = File(File(ctx.cacheDir, "vcard").apply { mkdirs() }, File(path).nameWithoutExtension + ".jpg")
                    f.writeBytes(bytes); photo = f.path
                }
            }
        }
    }
    val name = fn.ifBlank { n }.ifBlank { phones.firstOrNull() ?: "Contact" }
    VCard(name.replace("\\,", ","), phones.distinct(), emails.distinct(), org, photo)
}.getOrNull()

/** Which shared contact is open in the details sheet, if any. */
object ContactPeek { var card by mutableStateOf<VCard?>(null) }

/** A shared contact, shown like a contact card: photo, name, and View contact. */
@Composable
fun ContactCard(ref: MediaRef, path: String, busy: Boolean, me: Boolean) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val ctx = LocalContext.current
    var card by remember(path) { mutableStateOf<VCard?>(null) }
    LaunchedEffect(path) { if (path.isNotEmpty()) card = withContext(Dispatchers.IO) { parseVCard(ctx, path) } }
    LaunchedEffect(ref.id) { if (path.isEmpty()) repo.ensureMedia(ref) }
    val shape = RoundedCornerShape(22.dp)
    val c = card
    val name = c?.name ?: ref.name.removeSuffix(".vcf").ifBlank { "Contact" }
    Column(
        Modifier.width(240.dp).clip(shape).background(if (t.dark) t.neutral else t.card).clickable {
            if (c != null) ContactPeek.card = c else repo.ensureMedia(ref, manual = true)
        },
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c?.photo, initialsOf(name), 44.dp, t.tone, t.p)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                T(c?.phones?.firstOrNull() ?: if (busy) "Loading…" else "Contact", size = 12.5.sp, color = t.soft, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
        Box(Modifier.fillMaxWidth().padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
            if (busy && c == null) CircularProgressIndicator(Modifier.size(18.dp), color = t.p, strokeWidth = 2.dp)
            else T("View contact", size = 14.sp, weight = FontWeight.SemiBold, color = t.p)
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = me
}

/** The details of a shared contact, with Add to contacts and Message. Lives at the chat screen's root. */
@Composable
fun ContactSheet() {
    val t = LocalTok.current
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val shown = ContactPeek.card
    var last by remember { mutableStateOf<VCard?>(null) }
    if (shown != null) last = shown
    Sheet(shown != null, onDismiss = { ContactPeek.card = null }) {
        val c = last ?: return@Sheet
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Avatar(c.photo, initialsOf(c.name), 84.dp, t.tone, t.p)
            Spacer(Modifier.height(12.dp))
            T(c.name, size = 22.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            if (c.org.isNotBlank()) T(c.org, size = 13.5.sp, color = t.soft, align = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            val rows = c.phones.map { Icons.phone to it } + c.emails.map { Icons.message to it }
            if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.surface)) {
                rows.forEachIndexed { i, (ic, v) ->
                    if (i > 0) Hairline(start = 52.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(ic, t.soft, 19.dp)
                        Spacer(Modifier.width(15.dp))
                        T(v, size = 15.sp, weight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Add to contacts", {
                    runCatching {
                        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.NAME, c.name)
                        c.phones.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                        c.phones.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
                        c.emails.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                        if (c.org.isNotBlank()) i.putExtra(ContactsContract.Intents.Insert.COMPANY, c.org)
                        ctx.startActivity(i)
                    }
                    ContactPeek.card = null
                }, Modifier.weight(1f), primary = false, height = 50.dp)
                val number = c.phones.firstOrNull()
                if (number != null) PillButton("Message", {
                    ContactPeek.card = null
                    scope.launch {
                        repo.startChat(listOf(number)).onSuccess { conv -> nav?.openChat(conv.id) }.onFailure { repo.toast("Couldn't start a chat with $number") }
                    }
                }, Modifier.weight(1f), height = 50.dp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
