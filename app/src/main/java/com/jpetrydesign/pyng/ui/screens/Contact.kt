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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Provide(t) {
        Box(Modifier.fillMaxSize()) {
            Glow(t)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 30.dp)) {
                Row(Modifier.padding(start = 6.dp, top = 12.dp)) { CircleButton(Icons.back, "Back", { nav.back() }) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.enter(0, pop = true).edge(CircleShape, elevation = 16.dp)) { Avatar(w.photo, w.ini, 116.dp, w.bg, w.ink, 38.sp) }
                    Spacer(Modifier.height(14.dp))
                    T(c.title, size = 26.sp, weight = FontWeight.Bold, letterSpacing = (-0.6).sp, align = TextAlign.Center, modifier = Modifier.enter(1))
                    if (c.group) T(c.others.joinToString { it.short } + " and you", size = 14.sp, color = t.soft, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    else if (c.number.isNotEmpty() && c.number != c.title) T(c.number, size = 15.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(t.tone).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(t.p)); Spacer(Modifier.width(6.dp))
                        T(if (c.rcs) "RCS" else "SMS/MMS", size = 12.sp, weight = FontWeight.SemiBold, color = t.p)
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
                                            row.forEach { (_, ref) ->
                                                val path = ref.localPath.ifEmpty { repo.media[ref.id] ?: repo.media[ref.thumbId].orEmpty() }
                                                Box(Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp)).background(t.tone).clickable { openMedia(ctx, repo, nav, ref) }) {
                                                    if (path.isNotEmpty()) AsyncImage(File(path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                            }
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                                "Links" -> {
                                    val links = msgs.mapNotNull { m -> detect(m.text).url?.let { m to it } }.asReversed()
                                    if (links.isEmpty()) T("No links shared yet", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(4.dp))
                                    links.take(15).forEach { (_, url) ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Ico(Icons.link, t.p, 18.dp); Spacer(Modifier.width(10.dp)); T(url, size = 13.5.sp, maxLines = 1)
                                        }
                                    }
                                }
                                else -> {
                                    val files = msgs.flatMap { m -> m.media.filter { !it.isImage && !it.isVideo && !it.isAudio }.map { it } }.asReversed()
                                    if (files.isEmpty()) T("No files shared yet", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(4.dp))
                                    files.take(15).forEach { ref ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openMedia(ctx, repo, nav, ref) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Ico(Icons.file, t.p, 18.dp); Spacer(Modifier.width(10.dp))
                                            T(ref.name.ifBlank { "Attachment" }, size = 13.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                            T(sizeText(ref.size), size = 12.sp, color = t.soft)
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
                        ListRow("Delete conversation", icon = Icons.trash, danger = true, onClick = { repo.deleteConversation(convId); repo.toast("Conversation deleted"); nav.home() }, trailing = null)
                    }
                }
            }
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
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 30.dp)) {
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
                com.jpetrydesign.pyng.ui.kit.SectionLabel("Color", Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Swatch("App theme", swatchOf(Palettes.byKey(p.themeKey, p.c1, p.c2)), look.key.isEmpty()) { set { it.copy(key = "") } }
                    Swatch("Custom", androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), look.key == "chat-custom") { set { it.copy(key = "chat-custom") } }
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
                com.jpetrydesign.pyng.ui.kit.SectionLabel("Wallpaper", Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WallTile("Custom", null, look.wall.startsWith("photo:"), t, add = true) { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Palettes.walls.forEach { name -> WallTile(name, name, (look.wall.ifEmpty { p.defWall }) == name && !look.wall.startsWith("photo:"), t) { set { it.copy(wall = name) } } }
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Reset to app theme", { repo.prefs.edit { it.copy(looks = it.looks - convId) } }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), primary = false, height = 50.dp)
            }
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
