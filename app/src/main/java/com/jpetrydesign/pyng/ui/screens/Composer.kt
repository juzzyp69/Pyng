package com.jpetrydesign.pyng.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.BuildConfig
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.data.Pending
import com.jpetrydesign.pyng.ui.kit.EmojiData
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalMotion
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Seg
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.frost
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun Composer(
    t: Tok,
    conv: Conv?,
    draft: String,
    onDraft: (String) -> Unit,
    replyTo: Msg?,
    onCancelReply: () -> Unit,
    attachments: SnapshotStateList<Pending>,
    schedule: Pair<Long, String>?,
    onSchedule: (Pair<Long, String>?) -> Unit,
    onSend: (String) -> Unit,
    smart: Boolean,
    lastIncoming: String,
    fontSize: Float = 15.5f,
    initialPanel: String? = null,
) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val p = repo.prefs.state
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    var panel by remember { mutableStateOf(initialPanel) }
    var recording by remember { mutableStateOf(false) }
    var recSec by remember { mutableIntStateOf(0) }
    val recorder = remember { mutableStateListOf<Pair<MediaRecorder, File>>() }
    var busy by remember { mutableStateOf(false) }
    var makerUri by remember { mutableStateOf<Uri?>(null) }
    val pickForSticker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) makerUri = u }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            uris.take(10).forEach { u -> repo.importUri(u)?.let { attachments.add(it) } }
            busy = false; panel = null
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { add(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { add(it) }
    var camFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        if (ok && f != null) add(listOf(Uri.fromFile(f)))
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) scope.launch {
            vcardFor(ctx, uri, repo.newFile("vcf"))?.let { (f, name) -> attachments.add(Pending(f.path, "text/vcard", "$name.vcf")) }
            panel = null
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) repo.toast("Allow the microphone to record voice messages") }
    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res -> if (res.values.any { it }) panel = "gallery" else repo.toast("Allow photos to use the gallery here") }

    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { askMic.launch(Manifest.permission.RECORD_AUDIO); return }
        runCatching {
            val f = repo.newFile("m4a")
            val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000); setAudioSamplingRate(44_100)
                setOutputFile(f.path); prepare(); start()
            }
            recorder.clear(); recorder.add(r to f)
            recording = true; recSec = 0; panel = null
        }.onFailure { repo.toast("Couldn't start recording") }
    }
    fun stopRec(send: Boolean) {
        val (r, f) = recorder.firstOrNull() ?: return
        runCatching { r.stop() }; runCatching { r.release() }
        recorder.clear(); recording = false
        if (send && recSec >= 1 && conv != null) repo.send(conv.id, "", replyTo?.id.orEmpty(), listOf(Pending(f.path, "audio/mp4", "Voice message.m4a")))
        else f.delete()
    }
    LaunchedEffect(recording) { while (recording) { delay(1000); recSec++ } }
    DisposableEffect(Unit) { onDispose { recorder.firstOrNull()?.let { runCatching { it.first.release() } } } }
    BackHandler(enabled = panel != null) { panel = null }

    val hasDraft = draft.isNotBlank() || attachments.isNotEmpty()
    val sms = conv != null && !conv.rcs
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var smartClosed by remember(conv?.id, lastIncoming) { mutableStateOf(false) }
            AnimatedVisibility(smart && !smartClosed && panel == null && !recording, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val replies = if (lastIncoming.trim().endsWith("?")) listOf("Yes!", "Let me check", "Not sure 🤔") else listOf("Sounds good!", "Haha yes", "On it 👍")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    // Close button sits right beside the first suggestion.
                    Box(
                        Modifier.enter(0, pop = true).size(36.dp).edge(CircleShape, elevation = 3.dp).frost(CircleShape, t.glassStrong, 14.dp)
                            .press(0.9f) { smartClosed = true },
                        contentAlignment = Alignment.Center,
                    ) { Ico(Icons.close, t.soft, 15.dp, 2.2f) }
                    replies.forEachIndexed { i, r ->
                        Box(
                            Modifier.enter(i + 1, pop = true).height(36.dp).edge(RoundedCornerShape(18.dp), elevation = 3.dp).frost(RoundedCornerShape(18.dp), t.glassStrong, 14.dp)
                                .press(0.94f) { onSend(r) }.padding(horizontal = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) { T(r, size = 13.5.sp, weight = FontWeight.Medium) }
                    }
                }
            }
            val mention = if (conv?.group == true) Regex("(^|\\s)@(\\w*)$").find(draft) else null
            if (mention != null && conv != null) {
                val q = mention.groupValues[2].lowercase()
                val people = conv.others.filter { it.short.lowercase().startsWith(q) || it.display.lowercase().startsWith(q) }
                if (people.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEachIndexed { i, person ->
                        val w = whoOf(person)
                        Row(
                            Modifier.enter(i, pop = true).height(38.dp).edge(RoundedCornerShape(19.dp), elevation = 3.dp).frost(RoundedCornerShape(19.dp), t.glassStrong, 14.dp)
                                .clickable { onDraft(draft.replace(Regex("@(\\w*)$"), "@" + person.short + " ")) }.padding(start = 4.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.jpetrydesign.pyng.ui.kit.Avatar(w.photo, w.ini, 30.dp, t.tone, t.p, 11.sp)
                            Spacer(Modifier.width(8.dp))
                            T("@" + person.short, size = 13.5.sp, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (schedule != null) {
                Row(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(t.tone).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(Icons.clock, t.p, 13.dp, 2.3f); Spacer(Modifier.width(6.dp))
                    T("Sending ${schedule.second}", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.p)
                    Box(Modifier.size(24.dp).clickable { onSchedule(null) }, contentAlignment = Alignment.Center) { Ico(Icons.close, t.p, 12.dp, 2.6f) }
                }
            }
            if (replyTo != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).frost(RoundedCornerShape(18.dp), t.glassStrong, 14.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.reply, t.p, 16.dp, 2f); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T("Replying to " + (if (replyTo.fromMe) "yourself" else replyTo.sender.ifBlank { conv?.shortTitle ?: "" }), size = 12.sp, weight = FontWeight.Bold, color = t.p)
                        T(msgPreview(replyTo), size = 13.sp, color = t.soft, maxLines = 1)
                    }
                    Box(Modifier.size(34.dp).clickable(onClick = onCancelReply), contentAlignment = Alignment.Center) { Ico(Icons.close, t.soft, 15.dp, 2.4f) }
                }
            }
            if (attachments.isNotEmpty() || busy) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEach { a ->
                        Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(t.tone)) {
                            if (a.mime.startsWith("image/") || a.mime.startsWith("video/")) AsyncImage(File(a.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
                                Ico(if (a.mime.contains("vcard")) Icons.contact else Icons.file, t.p, 20.dp)
                                T(a.name, size = 10.sp, maxLines = 2, color = t.ink)
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { attachments.remove(a) },
                                contentAlignment = Alignment.Center,
                            ) { Ico(Icons.close, Color.White, 11.dp, 2.6f) }
                        }
                    }
                    if (busy) Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(t.tone), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = t.p, strokeWidth = 2.dp) }
                }
            }
        }

        val dockShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        Column(Modifier.fillMaxWidth().frost(dockShape, t.hdrBg, 34.dp).then(if (panel == null) Modifier.navigationBarsPadding() else Modifier).padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 12.dp)) {
            if (!recording) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(t.field).padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val rot by animateFloatAsState(if (panel != null && panel != "expr") 45f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "plus")
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { panel = if (panel != null && panel != "expr") null else "plus"; if (panel != null) kb?.hide() }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(22.dp).border(1.7.dp, t.ink, CircleShape).graphicsLayer { rotationZ = rot }, contentAlignment = Alignment.Center) { Ico(Icons.plus, t.ink, 13.dp, 3f) }
                        }
                        Box(Modifier.weight(1f).heightIn(min = 40.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) T(
                                when {
                                    schedule != null -> "Scheduled message"
                                    conv?.group == true -> if (conv.rcs) "Message ${conv.title}" else "Group text"
                                    conv != null && encryptionOf(conv, LocalRepo.current.msgs[conv.id].orEmpty()) == Enc.E2EE -> "Encrypted message"
                                    conv?.rcs == true -> "Chat message"
                                    else -> "Text message"
                                }, size = fontSize.sp, color = t.ink.copy(alpha = 0.5f), maxLines = 1,
                            )
                            BasicTextField(
                                draft, onDraft,
                                textStyle = TextStyle(fontFamily = Geist, fontSize = fontSize.sp, lineHeight = (fontSize * 1.32f).sp, color = t.ink),
                                cursorBrush = SolidColor(t.p),
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (p.on("enterSend")) ImeAction.Send else ImeAction.Default),
                                keyboardActions = KeyboardActions(onSend = { if (hasDraft) onSend(draft) }),
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                            )
                        }
                        if (sms && p.on("counter") && draft.isNotEmpty()) {
                            val left = 160 - (draft.length % 160).let { if (it == 0 && draft.isNotEmpty()) 160 else it }
                            val parts = (draft.length + 159) / 160
                            T(if (parts > 1) "$left/$parts" else "$left", size = 11.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp))
                        }
                        Box(Modifier.size(36.dp, 40.dp).clickable { panel = if (panel == "expr") null else "expr"; if (panel != null) kb?.hide() }, contentAlignment = Alignment.Center) {
                            Ico(Icons.emoji, if (panel == "expr") t.p else t.ink, 20.dp, 1.9f)
                        }
                        if (!hasDraft) Box(Modifier.size(36.dp, 40.dp).clickable {
                            if (panel == "gallery") panel = null
                            else if (hasPhotoAccess(ctx)) { panel = "gallery"; kb?.hide() }
                            else askPhotos.launch(photoPerms())
                        }, contentAlignment = Alignment.Center) { Ico(Icons.gallery, if (panel == "gallery") t.p else t.ink, 20.dp, 1.9f) }
                    }
                    when {
                        hasDraft -> Box(Modifier.padding(bottom = 2.dp).size(44.dp).clip(CircleShape).background(t.plusBg).press(0.88f) { onSend(draft) }, contentAlignment = Alignment.Center) {
                            Ico(Icons.send, t.plusInk, 21.dp, 2f)
                        }
                        p.on("micBtn") -> Box(Modifier.padding(bottom = 2.dp).size(44.dp).clip(CircleShape).background(t.plusBg).press(0.88f) { startRec() }, contentAlignment = Alignment.Center) {
                            Ico(Icons.mic, t.plusInk, 21.dp, 2f)
                        }
                        else -> Box(Modifier.padding(bottom = 2.dp).size(44.dp).clip(CircleShape).background(t.neutral), contentAlignment = Alignment.Center) { Ico(Icons.send, t.faint, 21.dp, 2f) }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(t.field).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { stopRec(false) }, contentAlignment = Alignment.Center) { Ico(Icons.trash, t.danger, 20.dp) }
                        RecDot()
                        Spacer(Modifier.width(10.dp))
                        T("%d:%02d".format(recSec / 60, recSec % 60), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                        RecBars(t.p, Modifier.weight(1f))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(t.plusBg).press(0.88f) { stopRec(true) }, contentAlignment = Alignment.Center) { Ico(Icons.send, t.plusInk, 21.dp, 2f) }
                }
            }
        }

        AnimatedVisibility(
            panel != null,
            enter = slideInVertically(if (motion) spring(dampingRatio = 0.72f, stiffness = 360f) else tween(0)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(if (motion) 220 else 0)) { it } + fadeOut(tween(160)),
        ) {
            var last by remember { mutableStateOf(panel ?: "plus") }
            if (panel != null) last = panel!!
            Column(Modifier.fillMaxWidth().height(when (last) { "expr" -> 380.dp; "plus" -> 270.dp; else -> 310.dp }).background(t.sheet).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 14.dp)) {
                when (last) {
                    "plus" -> PlusGrid(t) { what ->
                        when (what) {
                            "Gallery" -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            "Camera" -> { val f = repo.newFile("jpg"); camFile = f; runCatching { camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)) } }
                            "GIFs" -> panel = "gifs"
                            "Stickers" -> panel = "stickers"
                            "Files" -> pickFiles.launch(arrayOf("*/*"))
                            "Location" -> {
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                ) askLoc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                panel = "location"
                            }
                            "Contact" -> pickContact.launch(null)
                            "Schedule" -> panel = "schedule"
                            "Voice" -> startRec()
                        }
                    }
                    "expr" -> Expressions(t, initial = "Emoji", onMake = { panel = "maker" }, onEmoji = { e -> onDraft(draft + e); repo.prefs.edit { it.copy(recents = (listOf(e) + it.recents.filter { r -> r != e }).take(24)) } }, onSendText = { onSend(it) }, onGif = { f -> attachments.add(f); panel = null })
                    "gifs" -> Expressions(t, initial = "GIFs", onMake = { panel = "maker" }, onEmoji = { e -> onDraft(draft + e) }, onSendText = { onSend(it) }, onGif = { f -> attachments.add(f); panel = null })
                    "stickers" -> Expressions(t, initial = "Stickers", onEmoji = { e -> onDraft(draft + e) }, onSendText = { onSend(it); panel = null }, onGif = { f -> attachments.add(f); panel = null }, onMake = { panel = "maker" })
                    "maker" -> StickerMaker(t, makerUri,
                        onPick = { pickForSticker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onBack = { panel = "stickers" },
                        onDone = { f -> conv?.let { repo.send(it.id, "", replyTo?.id.orEmpty(), listOf(f)) }; makerUri = null; panel = null })
                    "gallery" -> Gallery(t, onPick = { add(it) }, onAll = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) })
                    "location" -> LocationPanel(t, onBack = { panel = "plus" }) { text -> onSend(text); panel = null }
                    "schedule" -> SchedulePanel(t, onBack = { panel = "plus" }) { onSchedule(it); panel = null }
                }
            }
        }
    }
}

private fun photoPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasPhotoAccess(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 34) ok(Manifest.permission.READ_MEDIA_IMAGES) || ok("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
    else if (Build.VERSION.SDK_INT >= 33) ok(Manifest.permission.READ_MEDIA_IMAGES) else ok(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
private fun RecDot() {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "rec")
    val a by tr.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(tween(500), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(Modifier.size(9.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Color(0xFFE5484D)))
}

@Composable
private fun RecBars(color: Color, modifier: Modifier) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "bars")
    Row(modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        repeat(14) { i ->
            val k by tr.animateFloat(0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(450, delayMillis = (i * 61) % 400), androidx.compose.animation.core.RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(3.dp).height(24.dp).graphicsLayer { scaleY = k }.clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun PlusGrid(t: Tok, onPick: (String) -> Unit) {
    val items = listOf(
        "Gallery" to Icons.gallery, "Camera" to Icons.camera, "GIFs" to "", "Stickers" to Icons.sticker,
        "Files" to Icons.file, "Location" to Icons.pin, "Contact" to Icons.contact, "Schedule" to Icons.clock,
    )
    // Even spacing: the two rows sit centered in the panel with matching gaps.
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically)) {
        items.chunked(4).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEachIndexed { i, (label, icon) ->
                    Column(
                        Modifier.width(76.dp).enter(r * 4 + i, pop = true).clip(RoundedCornerShape(16.dp)).clickable { onPick(label) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) {
                            if (icon.isEmpty()) Box(Modifier.border(2.dp, t.p, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) { T("GIF", size = 12.sp, weight = FontWeight.Bold, color = t.p, letterSpacing = 0.5.sp) }
                            else Ico(icon, t.p, 22.dp, 1.9f)
                        }
                        Spacer(Modifier.height(8.dp))
                        T(label, size = 12.5.sp, weight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------- expressions ----------

@Composable
private fun Expressions(t: Tok, initial: String, onEmoji: (String) -> Unit, onSendText: (String) -> Unit, onGif: (Pending) -> Unit, onMake: () -> Unit = {}) {
    var tab by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize()) {
        Seg(listOf("Emoji", "GIFs", "Stickers"), tab, { tab = it }, height = 40.dp)
        Spacer(Modifier.height(10.dp))
        when (tab) {
            "Emoji" -> EmojiTab(t, onEmoji)
            "GIFs" -> GifTab(t, onGif)
            else -> {
                val stickers = listOf("🐶", "😺", "🍕", "🎉", "💖", "🔥", "😎", "🌈", "☕", "👻", "🌵", "🍩", "🥳", "🫶", "🦄", "🌻")
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Column(Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(t.tone).press(0.9f, onMake), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Ico(Icons.plus, t.p, 22.dp, 2.2f); Spacer(Modifier.height(4.dp)); T("Make", size = 12.sp, weight = FontWeight.SemiBold, color = t.p)
                        }
                    }
                    items(stickers) { e ->
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(t.surface).press(0.9f) { onSendText(e) }, contentAlignment = Alignment.Center) { T(e, size = 44.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiTab(t: Tok, onEmoji: (String) -> Unit) {
    val repo = LocalRepo.current
    var q by remember { mutableStateOf("") }
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val recents = repo.prefs.state.recents
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.field).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.search, t.soft, 16.dp, 2.2f); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) T("Search emoji", size = 14.sp, color = t.soft)
                BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 14.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
        val sections: List<Pair<String, List<String>>> = if (q.isNotBlank()) {
            listOf("Results" to EmojiData.cats.flatMap { it.items }.filter { it.second.contains(q.trim().lowercase()) }.map { it.first })
        } else listOf("Recent" to recents) + EmojiData.cats.map { it.label to it.items.map { e -> e.first } }
        val starts = remember(sections) { var n = 0; sections.map { s -> n.also { n += 1 + s.second.size } } }
        if (q.isBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (listOf(Icons.clock) + EmojiData.cats.map { it.icon }).forEachIndexed { i, ic ->
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { scope.launch { grid.animateScrollToItem(starts[i]) } }, contentAlignment = Alignment.Center) { Ico(ic, t.soft, 18.dp, 1.9f) }
                }
            }
        }
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize(), state = grid) {
            sections.forEach { (label, list) ->
                item(span = { GridItemSpan(maxLineSpan) }) { T(label, size = 12.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                items(list) { e -> Box(Modifier.aspectRatio(1f).clip(CircleShape).clickable { onEmoji(e) }, contentAlignment = Alignment.Center) { T(e, size = 26.sp) } }
            }
        }
    }
}

private data class Gif(val id: String, val preview: String, val full: String, val w: Int, val h: Int)

@Composable
private fun GifTab(t: Tok, onGif: (Pending) -> Unit) {
    val repo = LocalRepo.current
    val key = BuildConfig.GIPHY_KEY.ifBlank { repo.prefs.state.opt("giphyKey") }
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Trending") }
    var gifs by remember { mutableStateOf<List<Gif>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (key.isBlank()) {
        val ctx = LocalContext.current
        var entry by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            T("Turn on GIF search", size = 16.sp, weight = FontWeight.SemiBold)
            T("GIFs come from GIPHY, which needs a free key. Make one in a minute: sign in at GIPHY for Developers, tap Create an API key, pick API, then copy the key here.",
                size = 13.sp, color = t.soft, lineHeight = 18.sp, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            T("Open GIPHY for Developers", size = 14.sp, weight = FontWeight.SemiBold, color = t.p,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.giphy.com/dashboard/"))) } }.padding(horizontal = 14.dp, vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(t.field).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (entry.isEmpty()) T("Paste your GIPHY key", size = 14.sp, color = t.soft)
                    BasicTextField(entry, { entry = it.trim() }, singleLine = true, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 14.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).then(if (entry.length >= 16) Modifier.background(t.plusBg) else Modifier.background(t.surface))
                        .clickable(enabled = entry.length >= 16) { repo.prefs.setOpt("giphyKey", entry); repo.toast("GIF search is on") }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { T("Save", size = 14.sp, weight = FontWeight.SemiBold, color = if (entry.length >= 16) t.plusInk else t.soft) }
            }
        }
        return
    }
    val term = q.trim().ifEmpty { if (cat == "Trending") "" else cat }
    LaunchedEffect(term) {
        delay(300)
        loading = true
        gifs = withContext(Dispatchers.IO) {
            runCatching {
                val rating = repo.prefs.state.opt("gifRating").lowercase()
                val url = if (term.isEmpty()) "https://api.giphy.com/v1/gifs/trending?api_key=$key&limit=30&rating=$rating"
                else "https://api.giphy.com/v1/gifs/search?api_key=$key&limit=30&rating=$rating&q=" + URLEncoder.encode(term, "UTF-8")
                val json = JSONObject(URL(url).readText())
                val arr = json.getJSONArray("data")
                (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
                    val im = o.optJSONObject("images") ?: return@mapNotNull null
                    val pv = im.optJSONObject("fixed_width_downsampled") ?: im.optJSONObject("fixed_width")
                    val full = im.optJSONObject("downsized") ?: im.optJSONObject("original")
                    if (pv == null || full == null) null else Gif(o.optString("id"), pv.optString("url"), full.optString("url"), pv.optString("width").toIntOrNull() ?: 200, pv.optString("height").toIntOrNull() ?: 150)
                }
            }.getOrDefault(emptyList())
        }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.field).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.search, t.soft, 16.dp, 2.2f); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) T("Search GIPHY", size = 14.sp, color = t.soft)
                BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 14.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Trending", "Haha", "Love", "Yes", "Wow", "Sad", "Dance", "Thank you", "Hello").forEach { c ->
                val on = q.isBlank() && cat == c
                Box(Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) t.tone else t.surface).clickable { cat = c; q = "" }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    T(c, size = 12.5.sp, weight = FontWeight.SemiBold, color = if (on) t.p else t.soft)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(gifs, key = { it.id }) { g ->
                    Box(Modifier.fillMaxWidth().aspectRatio(g.w.toFloat() / maxOf(1, g.h)).clip(RoundedCornerShape(14.dp)).background(t.tone).clickable {
                        if (sending != null) return@clickable
                        sending = g.id
                        scope.launch {
                            val f = repo.newFile("gif")
                            val ok = withContext(Dispatchers.IO) { runCatching { URL(g.full).openStream().use { i -> f.outputStream().use { i.copyTo(it) } } }.isSuccess }
                            sending = null
                            if (ok) onGif(Pending(f.path, "image/gif", "GIF.gif")) else repo.toast("Couldn't get that GIF")
                        }
                    }) {
                        AsyncImage(g.preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (sending == g.id) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) }
                    }
                }
            }
            if (loading && gifs.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = t.p, strokeWidth = 2.dp)
            T("Powered by GIPHY", size = 10.sp, color = t.faint, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

// ---------- gallery ----------

@Composable
private fun Gallery(t: Tok, onPick: (List<Uri>) -> Unit, onAll: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<Pair<Uri, Boolean>>>(emptyList()) }
    val sel = remember { mutableStateListOf<Uri>() }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            runCatching {
                val out = mutableListOf<Pair<Uri, Long>>()
                val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
                listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false, MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true).forEach { (base, video) ->
                    ctx.contentResolver.query(base, proj, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { c ->
                        var n = 0
                        while (c.moveToNext() && n < 60) { out += ContentUris.withAppendedId(base, c.getLong(0)).let { if (video) Uri.parse("$it#v") else it } to c.getLong(1); n++ }
                    }
                }
                out.sortedByDescending { it.second }.take(90).map { (u, _) -> val v = u.fragment == "v"; (if (v) Uri.parse(u.toString().removeSuffix("#v")) else u) to v }
            }.getOrDefault(emptyList())
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Gallery", size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            T("All photos", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.clickable(onClick = onAll).padding(8.dp))
            if (sel.isNotEmpty()) Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(t.plusBg).clickable { onPick(sel.toList()) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                T("Add ${sel.size}", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.plusInk)
            }
        }
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.first.toString() }) { (u, video) ->
                val i = sel.indexOf(u)
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(t.tone).clickable { if (i >= 0) sel.remove(u) else sel.add(u) }) {
                    AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (video) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { Ico(Icons.play, Color.White, 10.dp, 1f, fill = Color.White) }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                            .background(if (i >= 0) t.fill else Color.Black.copy(alpha = 0.25f)).border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (i >= 0) T("${i + 1}", size = 11.sp, weight = FontWeight.Bold, color = t.onFill) }
                }
            }
        }
    }
}

// ---------- location ----------

@SuppressLint("MissingPermission")
@Composable
private fun LocationPanel(t: Tok, onBack: () -> Unit, onSend: (String) -> Unit) {
    val ctx = LocalContext.current
    var loc by remember { mutableStateOf<Location?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted || lm == null) { delay(2500); if (loc == null) failed = true; return@LaunchedEffect }
        loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            lm.getCurrentLocation(LocationManager.FUSED_PROVIDER.takeIf { lm.allProviders.contains(it) } ?: LocationManager.GPS_PROVIDER, null, ContextCompat.getMainExecutor(ctx)) { l -> if (l != null) loc = l }
        }
        delay(8000); if (loc == null) failed = true
    }
    Column(Modifier.fillMaxSize()) {
        PanelHead(t, "Location", onBack)
        val l = loc
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(t.p.copy(alpha = 0.25f), t.s.copy(alpha = 0.45f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (l != null) MapTiles(l.latitude, l.longitude, t.dark)
            if (l == null && !failed) CircularProgressIndicator(Modifier.size(26.dp), color = t.p, strokeWidth = 2.dp)
            else Box(Modifier.offset(y = (-14).dp).size(36.dp).edge(CircleShape, primary = true, elevation = 6.dp).clip(CircleShape).background(t.fill), contentAlignment = Alignment.Center) { Ico(Icons.pin, t.onFill, 19.dp, 2f) }
            if (l != null) T("© OpenStreetMap", size = 9.5.sp, color = Color(0xFF3A3F4A), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (l != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.surface).clickable {
                onSend("📍 My location: https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, l.latitude, l.longitude))
            }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.pin, t.p, 19.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T("Send your current location", size = 15.sp, weight = FontWeight.SemiBold)
                    T("Accurate to ${l.accuracy.toInt()} m · opens in Google Maps", size = 12.5.sp, color = t.soft)
                }
            }
        } else if (failed) T("Couldn't find your location. Check that location is on and allowed for Messages.", size = 13.5.sp, color = t.soft, lineHeight = 19.sp)
    }
}

/** A small street map around a point, built from OpenStreetMap tiles. */
@Composable
fun MapTiles(lat: Double, lon: Double, dark: Boolean, zoom: Int = 16) {
    val ctx = LocalContext.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val n = 1 shl zoom
        val x = (lon + 180.0) / 360.0 * n
        val latR = Math.toRadians(lat)
        val y = (1.0 - kotlin.math.ln(kotlin.math.tan(latR) + 1.0 / kotlin.math.cos(latR)) / Math.PI) / 2.0 * n
        val tx = kotlin.math.floor(x).toInt(); val ty = kotlin.math.floor(y).toInt()
        val fx = (x - tx).toFloat(); val fy = (y - ty).toFloat()
        val tile = 150.dp
        val cx = maxWidth / 2; val cy = maxHeight / 2
        for (dx in -2..2) for (dy in -1..1) {
            val xi = ((tx + dx) % n + n) % n; val yi = ty + dy
            if (yi < 0 || yi >= n) continue
            val req = remember(zoom, xi, yi) {
                coil.request.ImageRequest.Builder(ctx).data("https://tile.openstreetmap.org/$zoom/$xi/$yi.png")
                    .addHeader("User-Agent", "Pyng/" + BuildConfig.VERSION_NAME + " (personal Android messaging app)")
                    .crossfade(true).build()
            }
            AsyncImage(
                req, null, contentScale = ContentScale.FillBounds,
                colorFilter = if (dark) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(0.72f, 0.72f, 0.78f, 1f) }) else null,
                modifier = Modifier.size(tile).offset(x = cx + tile * (dx - fx), y = cy + tile * (dy - fy)),
            )
        }
    }
}

@Composable
private fun PanelHead(t: Tok, title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Ico(Icons.back, t.ink, 20.dp, 2f) }
        Spacer(Modifier.width(6.dp))
        T(title, size = 15.sp, weight = FontWeight.SemiBold)
    }
}

// ---------- schedule ----------

@Composable
private fun SchedulePanel(t: Tok, onBack: () -> Unit, onPick: (Pair<Long, String>) -> Unit) {
    val ctx = LocalContext.current
    fun at(daysAhead: Int, hour: Int, nextWeekday: Int? = null): Long {
        val c = Calendar.getInstance()
        if (nextWeekday != null) { do { c.add(Calendar.DAY_OF_YEAR, 1) } while (c.get(Calendar.DAY_OF_WEEK) != nextWeekday) } else c.add(Calendar.DAY_OF_YEAR, daysAhead)
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
        return c.timeInMillis
    }
    fun label(ms: Long) = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault()).format(ms)
    val now = System.currentTimeMillis()
    val opts = buildList {
        at(0, 18).takeIf { it > now + 10 * 60_000 }?.let { add("Later today" to it) }
        if (at(0, 21) > now + 10 * 60_000) add("Tonight" to at(0, 21))
        add("Tomorrow morning" to at(1, 9))
        add("Tomorrow evening" to at(1, 18))
        add("Monday morning" to at(0, 9, Calendar.MONDAY))
    }.filter { it.second > now }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PanelHead(t, "Schedule send", onBack)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.tone).clickable {
            val c = Calendar.getInstance()
            android.app.DatePickerDialog(ctx, { _, y, mo, d ->
                android.app.TimePickerDialog(ctx, { _, h, mi ->
                    val pick = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }.timeInMillis
                    if (pick > System.currentTimeMillis()) onPick(pick to label(pick))
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
        }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(t.fill), contentAlignment = Alignment.Center) { Ico(Icons.cal, t.onFill, 18.dp) }
            Spacer(Modifier.width(12.dp))
            Column { T("Pick date and time", size = 15.sp, weight = FontWeight.SemiBold); T("Choose exactly when it sends", size = 12.5.sp, color = t.soft) }
        }
        Spacer(Modifier.height(8.dp))
        T("Suggested", size = 12.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp))
        opts.forEach { (name, ms) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(ms to label(ms)) }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.clock, t.p, 18.dp); Spacer(Modifier.width(12.dp))
                Column { T(name, size = 15.sp, weight = FontWeight.SemiBold); T(label(ms), size = 12.5.sp, color = t.soft) }
            }
        }
    }
}

// ---------- contacts ----------

private suspend fun vcardFor(ctx: Context, uri: Uri, out: File): Pair<File, String>? = withContext(Dispatchers.IO) {
    runCatching {
        var name = "Contact"
        var id = ""
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) { id = c.getString(0); name = c.getString(1) ?: name }
        }
        val phones = mutableListOf<String>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(id), null,
            )?.use { c -> while (c.moveToNext()) phones += c.getString(0) }
        }
        if (phones.isEmpty()) {
            val lookup = runCatching {
                ctx.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            if (lookup != null) {
                val vUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookup)
                ctx.contentResolver.openInputStream(vUri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                return@runCatching out to name
            }
        }
        out.writeText("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:$name\r\n" + phones.joinToString("") { "TEL;TYPE=CELL:$it\r\n" } + "END:VCARD\r\n")
        out to name
    }.getOrNull()
}
