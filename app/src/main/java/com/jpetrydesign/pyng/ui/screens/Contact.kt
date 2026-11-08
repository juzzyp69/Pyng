package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.ChatAlerts
import com.jpetrydesign.pyng.data.ChatLook
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Card
import com.jpetrydesign.pyng.ui.kit.Chips
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Hairline
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.ListRow
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Provide
import com.jpetrydesign.pyng.ui.kit.Seg
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.Toggle
import com.jpetrydesign.pyng.ui.kit.Wallpaper
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Palettes
import com.jpetrydesign.pyng.ui.theme.swatchOf
import com.jpetrydesign.pyng.ui.theme.tok
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ContactScreen(nav: Nav, convId: String) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val c = repo.convs[convId]
    val t = chatTok(convId)
    val p = repo.prefs.state
    var tab by remember { mutableStateOf("Media") }
    BackHandler { nav.back() }
    if (c == null) { LaunchedEffect0 { nav.back() }; return }
    val w = whoOf(c)
    val alerts = p.alerts[convId] ?: ChatAlerts()
    val look = p.looks[convId]
    val msgs = repo.msgs[convId].orEmpty()
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { if (c.group) repo.setGroupPhoto(convId, uri) else repo.setContactPhoto(c.avatarId, uri) }
    }
    val enc = encryptionOf(c, msgs)
    fun senderOf(m: com.jpetrydesign.pyng.data.Msg): String = if (m.fromMe) "You" else c.people.firstOrNull { it.id == m.participantId }?.short ?: m.senderFirst.ifBlank { m.sender.substringBefore(' ') }.ifBlank { c.shortTitle }
    Provide(t) {
        Box(Modifier.fillMaxSize()) {
            Glow(t)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 30.dp)) {
                Row(Modifier.padding(start = 6.dp, top = 12.dp)) { CircleButton(Icons.back, "Back", { nav.back() }) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.enter(0, pop = true)) {
                        Box(Modifier.edge(CircleShape, elevation = 16.dp).clip(CircleShape).clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Avatar(w.photo, w.ini, 116.dp, w.bg, w.ink, 38.sp)
                        }
                        // Tap the photo to change it (in Pyng only), with a crop step.
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(36.dp).edge(CircleShape, elevation = 4.dp).clip(CircleShape).background(t.fill)
                                .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center,
                        ) { Ico(Icons.camera2, t.onFill, 18.dp, 1.9f) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.enter(1).clip(RoundedCornerShape(14.dp)).then(if (c.group) Modifier.clickable { renaming = true } else Modifier).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(c.title, size = 26.sp, weight = FontWeight.Bold, letterSpacing = (-0.6).sp, align = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                        if (c.group) { Spacer(Modifier.width(8.dp)); Ico(Icons.pencil, t.soft, 17.dp, 2f) }
                    }
                    if (c.group) T(c.others.joinToString { it.short } + " and you", size = 14.sp, color = t.soft, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    else if (c.number.isNotEmpty() && c.number != c.title) T(c.number, size = 15.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    // Tap for what it means.
                    Row(
                        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (enc == Enc.SMS) t.neutral else t.tone)
                            .clickable { repo.toast(encExplain(enc)) }.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (enc == Enc.E2EE) Ico(Icons.lock, t.p, 13.dp, 2.1f) else Box(Modifier.size(6.dp).clip(CircleShape).background(if (enc == Enc.SMS) t.soft else t.p))
                        Spacer(Modifier.width(6.dp))
                        T(encLabel(enc), size = 12.sp, weight = FontWeight.SemiBold, color = if (enc == Enc.SMS) t.soft else t.p)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val acts = buildList<Triple<String, String, () -> Unit>> {
                            if (!c.group && !c.business) add(Triple("Call", Icons.phone) { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' }))) } })
                            add(Triple("Search", Icons.search) { nav.back(); nav.go(Route.Thread(convId, search = true)) })
                            add(Triple(if (p.isMuted(convId)) "Unmute" else "Mute", if (p.isMuted(convId)) Icons.bell else Icons.bellOff) { repo.setMuted(convId, !p.isMuted(convId)) })
                            add(Triple(if (p.isPinned(convId)) "Unpin" else "Pin", Icons.pinned) { repo.togglePin(convId) })
                        }
                        acts.forEachIndexed { i, (label, icon, go) ->
                            Column(
                                Modifier.weight(1f).enter(2 + i, pop = true).clip(RoundedCornerShape(20.dp)).background(t.tone).press(0.94f, go).padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Ico(icon, t.p, 20.dp, 1.9f)
                                Spacer(Modifier.height(6.dp))
                                T(label, size = 12.5.sp, weight = FontWeight.SemiBold, color = t.p)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Card {
                        ListRow(
                            "Chat theme",
                            sub = (if (look == null || look.key.isEmpty()) "Matches app (${p.themeKey})" else if (look.key == "chat-custom") "Custom · ${look.mode}" else look.key) +
                                " · " + ((look?.wall?.ifEmpty { null } ?: p.defWall).let { if (it.startsWith("photo:")) "Photo" else it }) + " wallpaper",
                            onClick = { nav.go(Route.ChatTheme(convId)) },
                            trailing = { Ico(Icons.chev, t.faint, 16.dp, 2f) },
                        )
                        Hairline()
                        Row(Modifier.fillMaxWidth().clickable { repo.prefs.edit { it.copy(alerts = it.alerts + (convId to alerts.copy(on = !alerts.on))) } }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.bell, t.p, 19.dp) }
                            Spacer(Modifier.width(14.dp))
                            T("Notifications", size = 15.5.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Toggle(alerts.on)
                        }
                        if (alerts.on) {
                            Hairline()
                            Column(Modifier.padding(16.dp)) {
                                T("Vibration for ${c.shortTitle}", size = 14.5.sp, weight = FontWeight.SemiBold)
                                Spacer(Modifier.height(10.dp))
                                Chips(listOf("Default", "Off"), alerts.vibrate, { v -> repo.prefs.edit { it.copy(alerts = it.alerts + (convId to alerts.copy(vibrate = v))) } })
                            }
                        }
                    }
                    if (c.group) {
                        Spacer(Modifier.height(14.dp))
                        Card {
                            T("${c.others.size + 1} people", size = 13.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                            c.others.forEach { person ->
                                val pw = whoOf(person)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(pw.photo, pw.ini, 40.dp, t.tone, t.p)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        T(person.display, size = 15.sp, weight = FontWeight.Medium)
                                        if (person.name.isNotBlank() && person.formatted.isNotBlank()) T(person.formatted, size = 12.5.sp, color = t.soft)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Seg(listOf("Media", "Links", "Files"), tab, { tab = it })
                            Spacer(Modifier.height(12.dp))
                            when (tab) {
                                "Media" -> {
                                    val media = msgs.flatMap { m -> m.media.filter { it.isImage || it.isVideo }.map { m to it } }.asReversed()
                                    if (media.isEmpty()) T("No photos shared yet", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(4.dp))
                                    media.take(12).chunked(3).forEach { row ->
                                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row.forEach { (m, ref) ->
                                                val path = ref.localPath.ifEmpty { repo.media[ref.id] ?: repo.media[ref.thumbId].orEmpty() }
                                                LaunchedEffect(ref.id) { if (path.isEmpty()) repo.ensureMedia(ref, thumb = true) }
                                                Box(Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(14.dp)).background(t.tone).clickable { openMedia(ctx, repo, nav, ref) }) {
                                                    if (path.isNotEmpty()) AsyncImage(File(path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    // Who sent it, in groups.
                                                    if (c.group) {
                                                        val who = c.people.firstOrNull { it.id == m.participantId }
                                                        Row(
                                                            Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(start = 2.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            if (who != null && !m.fromMe) { val ww = whoOf(who); Avatar(ww.photo, ww.ini, 16.dp, ww.bg, ww.ink, 7.sp); Spacer(Modifier.width(4.dp)) }
                                                            else Spacer(Modifier.width(5.dp))
                                                            T(senderOf(m), size = 10.5.sp, weight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                                        }
                                                    }
                                                }
                                            }
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                                "Links" -> {
                                    val links = msgs.mapNotNull { m -> detect(m.text).url?.let { m to it } }.asReversed()
                                    if (links.isEmpty()) T("No links shared yet", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(4.dp))
                                    links.take(15).forEach { (m, url) ->
                                        LaunchedEffect(url) { Links.load(url) }
                                        val meta = Links.cache[url]
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(t.tone), contentAlignment = Alignment.Center) {
                                                if (meta?.image != null) AsyncImage(meta.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                else Ico(Icons.link, t.p, 18.dp)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                T(meta?.title?.ifBlank { null } ?: url.removePrefix("https://").removePrefix("http://"), size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
                                                T(meta?.host ?: runCatching { java.net.URL(url).host.removePrefix("www.") }.getOrDefault(""), size = 12.sp, color = t.soft, maxLines = 1)
                                                // Who shared it and when.
                                                T((if (c.group || m.fromMe) "Sent by ${senderOf(m)} · " else "") + whenText(m.ts), size = 11.5.sp, color = t.faint, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    val files = msgs.flatMap { m -> m.media.filter { !it.isImage && !it.isVideo && !it.isAudio }.map { m to it } }.asReversed()
                                    if (files.isEmpty()) T("No files shared yet", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(4.dp))
                                    files.take(15).forEach { (m, ref) ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { openMedia(ctx, repo, nav, ref) }.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.file, t.p, 18.dp) }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                T(ref.name.ifBlank { "Attachment" }, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
                                                T(sizeText(ref.size) + " · " + (if (c.group || m.fromMe) "Sent by ${senderOf(m)} · " else "") + whenText(m.ts), size = 12.sp, color = t.soft, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                            if (msgs.isNotEmpty()) T("From the messages loaded in this chat", size = 11.5.sp, color = t.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Card {
                        if (!c.known && !c.group) {
                            ListRow("Add to contacts", icon = Icons.contact, onClick = {
                                runCatching { ctx.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, c.number)) }
                            }, trailing = null)
                            Hairline()
                        }
                        ListRow(if (c.status == "archived") "Unarchive" else "Archive", icon = Icons.archive, onClick = {
                            val on = c.status != "archived"; repo.setArchived(convId, on); repo.toast(if (on) "Chat archived" else "Moved back to your inbox")
                        }, trailing = null)
                        Hairline()
                        if (!c.group) {
                            ListRow(if (c.status == "blocked") "Unblock" else "Block & report spam", icon = Icons.block, danger = c.status != "blocked", onClick = {
                                if (c.status == "blocked") { repo.setBlocked(convId, false); repo.toast("Unblocked") } else { repo.setBlocked(convId, true, true); repo.toast("Blocked and reported"); nav.home() }
                            }, trailing = null)
                            Hairline()
                        }
                        ListRow("Delete conversation", icon = Icons.trash, danger = true, onClick = {
                            repo.confirm("Delete this conversation?", "All of its messages will be deleted from Google Messages on your phone too.") { repo.deleteConversation(convId); repo.toast("Conversation deleted"); nav.home() }
                        }, trailing = null)
                    }
                }
            }
            com.jpetrydesign.pyng.ui.kit.StatusScrim(Modifier.align(Alignment.TopCenter))
            RenameSheet(renaming, c.title, { renaming = false }) { repo.setGroupName(convId, it) }
        }
    }
}

/** Rename a group (in Pyng only). */
@Composable
fun RenameSheet(visible: Boolean, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val t = LocalTok.current
    var v by remember(visible) { mutableStateOf(current) }
    com.jpetrydesign.pyng.ui.kit.Sheet(visible, onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            T("Group name", size = 18.sp, weight = FontWeight.Bold)
            T("Shows in Pyng only. Google Messages doesn't let linked apps rename groups.", size = 13.sp, color = t.soft, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(t.field).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                if (v.isEmpty()) T("Name this group", size = 15.sp, color = t.soft)
                androidx.compose.foundation.text.BasicTextField(v, { v = it.take(60) }, singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(t.p),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = 15.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Reset", onClick = { onSave(""); onDismiss() }, modifier = Modifier.weight(1f), primary = false)
                PillButton("Save", onClick = { onSave(v); onDismiss() }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LaunchedEffect0(block: () -> Unit) { androidx.compose.runtime.LaunchedEffect(Unit) { block() } }

@Composable
fun ChatThemeScreen(nav: Nav, convId: String) {
    val repo = LocalRepo.current
    val p = repo.prefs.state
    val c = repo.convs[convId]
    val look = p.looks[convId] ?: ChatLook()
    val t = chatTok(convId)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf("c1") }
    var customizing by remember { mutableStateOf(look.custom.isNotEmpty() || repo.demoOpen == "custom") }
    var part by remember { mutableStateOf("inBg") }
    val scroll = rememberScrollState()
    BackHandler { nav.back() }
    fun set(f: (ChatLook) -> ChatLook) = repo.prefs.edit { it.copy(looks = it.looks + (convId to f(it.looks[convId] ?: ChatLook()))) }
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = repo.importUri(uri) ?: return@launch
            val dest = repo.keepFile("wall")
            File(f.path).copyTo(dest, overwrite = true)
            set { it.copy(wall = "photo:" + dest.path) }
        }
    }
    val wall = look.wall.ifEmpty { p.defWall }
    Provide(t) {
        Box(Modifier.fillMaxSize()) {
            Glow(t)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding().padding(bottom = 30.dp)) {
                com.jpetrydesign.pyng.ui.kit.PageTop("Chat theme", { nav.back() }, sub = "Only for ${c?.title ?: "this chat"}")
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp))) {
                    Wallpaper(wall, t)
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Bubble1("Dinner Friday still on?", false, t); Bubble1("I can book for 7", false, t)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.align(Alignment.End)) { Bubble1("Yes! 7 is perfect", true, t) }
                        Box(Modifier.align(Alignment.End)) { Bubble1("See you there", true, t) }
                    }
                }
                // Two modes, never both at once: Theme (pick a theme or one overall color) and
                // Custom (set each part's color on its own). Switching crossfades.
                androidx.compose.animation.Crossfade(customizing, label = "themeMode") { custom ->
                    if (custom) Column(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)) {
                        com.jpetrydesign.pyng.ui.kit.SectionLabel("Custom colors")
                        val parts = listOf("bg" to "Background", "inBg" to "Received bubble", "inInk" to "Received text", "outBg" to "Sent bubble", "outInk" to "Sent text", "accent" to "Accent")
                        fun shown(k: String): Color = when (k) { "bg" -> t.bg; "inBg" -> t.inBg; "inInk" -> t.inInk; "outBg" -> t.outFlat; "outInk" -> t.outInk; else -> t.p }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            parts.forEach { (k, label) ->
                                val on = part == k
                                Row(
                                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) t.fill else t.tone).clickable { part = k }.padding(start = 6.dp, end = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(shown(k)).border(1.5.dp, if (on) Color.White.copy(alpha = 0.7f) else t.hair, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    T(label, size = 13.5.sp, weight = FontWeight.SemiBold, color = if (on) t.onFill else t.ink)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        // Starts from what the chat looks like now, so you're tweaking, not starting over.
                        val cur = look.custom[part] ?: com.jpetrydesign.pyng.ui.theme.toHsl(shown(part))
                        ColorWheel(cur) { h -> set { it.copy(custom = it.custom + (part to h)) } }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            if (look.custom.containsKey(part)) T("Use automatic", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.p,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { set { it.copy(custom = it.custom - part) } }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    } else Column {
                        com.jpetrydesign.pyng.ui.kit.SectionLabel("Color", Modifier.padding(start = 16.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Swatch("App theme", swatchOf(Palettes.byKey(p.themeKey, p.c1, p.c2)), look.key.isEmpty()) { set { it.copy(key = "") } }
                            Swatch("Any color", androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), look.key == "chat-custom") { set { it.copy(key = "chat-custom") } }
                            (Palettes.favorites + Palettes.colors + Palettes.duos).forEach { x -> Swatch(x.name, swatchOf(x), look.key == x.name) { set { it.copy(key = x.name) } } }
                        }
                        if (look.key == "chat-custom") {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Seg(listOf("Mono", "Duotone", "Gradient"), look.mode, { m -> set { it.copy(mode = m) } })
                                if (look.mode != "Mono") {
                                    Spacer(Modifier.height(10.dp))
                                    Seg(listOf("Main color", "Second color"), if (editing == "c1") "Main color" else "Second color", { editing = if (it == "Main color") "c1" else "c2" })
                                }
                                Spacer(Modifier.height(12.dp))
                                val cur = if (editing == "c2" && look.mode != "Mono") look.c2 else look.c1
                                ColorWheel(cur) { h -> set { if (editing == "c2" && it.mode != "Mono") it.copy(c2 = h) else it.copy(c1 = h) } }
                            }
                        }
                        // Custom colors are kept when you switch back; say so, with a way to clear them.
                        if (look.custom.isNotEmpty()) Row(
                            Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.tone).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T("Custom colors on", size = 13.5.sp, weight = FontWeight.Medium, color = t.ink, modifier = Modifier.weight(1f))
                            T("Clear", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.p,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { set { it.copy(custom = emptyMap()) } }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                com.jpetrydesign.pyng.ui.kit.SectionLabel("Wallpaper", Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WallTile("Custom", null, look.wall.startsWith("photo:"), t, add = true) { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Palettes.walls.forEach { name -> WallTile(name, name, (look.wall.ifEmpty { p.defWall }) == name && !look.wall.startsWith("photo:"), t) { set { it.copy(wall = name) } } }
                }
                Spacer(Modifier.height(14.dp))
                if (p.looks[convId] != null) T("Reset to app theme", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.soft, align = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(14.dp))
                        .clickable { customizing = false; repo.prefs.edit { it.copy(looks = it.looks - convId) } }.padding(horizontal = 14.dp, vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                // Custom switches to setting each color yourself; Theme switches back. Nothing is lost either way.
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(if (customizing) "Theme" else "Custom", {
                        customizing = !customizing
                        scope.launch { scroll.animateScrollTo(0) }
                    }, Modifier.weight(1f), primary = false, height = 50.dp)
                    PillButton("Done", { nav.back() }, Modifier.weight(1f), height = 50.dp)
                }
            }
            com.jpetrydesign.pyng.ui.kit.StatusScrim(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun Bubble1(text: String, me: Boolean, t: com.jpetrydesign.pyng.ui.theme.Tok) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (me) t.outBg else androidx.compose.ui.graphics.Brush.linearGradient(listOf(t.inBg, t.inBg))).padding(horizontal = 14.dp, vertical = 8.dp)) {
        T(text, size = 14.5.sp, color = if (me) t.outInk else t.inInk)
    }
}

@Composable
fun Swatch(name: String, brush: androidx.compose.ui.graphics.Brush, on: Boolean, size: androidx.compose.ui.unit.Dp = 50.dp, onClick: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.width(size + 16.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size + 8.dp).then(if (on) Modifier.border(2.dp, t.ink, CircleShape) else Modifier).padding(4.dp).clip(CircleShape).background(brush).border(1.dp, t.hair, CircleShape))
        Spacer(Modifier.height(4.dp))
        T(name, size = 11.sp, weight = FontWeight.Medium, color = t.soft, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
fun WallTile(name: String, wall: String?, on: Boolean, t: com.jpetrydesign.pyng.ui.theme.Tok, add: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(16.dp)).then(if (on) Modifier.border(2.dp, t.ink, RoundedCornerShape(16.dp)) else Modifier.border(1.dp, t.hair, RoundedCornerShape(16.dp)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (wall != null) Wallpaper(wall, t)
            else Box(Modifier.fillMaxSize().background(t.surface))
            if (add) Box(Modifier.size(36.dp).clip(CircleShape).background(if (t.dark) Color(0xFF2A2D36) else Color.White), contentAlignment = Alignment.Center) { Ico(Icons.gallery, t.p, 17.dp) }
        }
        Spacer(Modifier.height(5.dp))
        T(name, size = 11.5.sp, weight = FontWeight.Medium, color = t.soft)
    }
}
