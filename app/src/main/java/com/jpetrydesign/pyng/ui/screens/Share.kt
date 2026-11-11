package com.jpetrydesign.pyng.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.data.Contact
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PageTop
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.launch
import java.io.File

/** Someone you can share to: an existing chat, or a contact you don't have a chat with yet. */
private sealed class ShareTo(val key: String) {
    class Chat(val c: Conv) : ShareTo("c:" + c.id)
    class Someone(val p: Contact) : ShareTo("p:" + p.number.filter(Char::isDigit).takeLast(10))
}

/**
 * Sharing from another app: pinned chats as a row of faces, a few recent chats (more on request),
 * then contacts. Pick one or more, edit the message at the bottom, and send.
 */
@Composable
fun ShareScreen(nav: Nav) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val scope = rememberCoroutineScope()
    // Short screens (a flip phone's cover screen) get a slimmer bottom bar so the list has room.
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 700
    val share = repo.pendingShare
    var text by remember { mutableStateOf(share?.text.orEmpty()) }
    val files = remember { mutableStateListOf<com.jpetrydesign.pyng.data.Pending>().apply { addAll(share?.files.orEmpty()) } }
    var q by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(4) }
    val picked = remember { mutableStateListOf<ShareTo>() }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repo.loadContacts() }
    fun leave() { repo.pendingShare = null; nav.back() }
    BackHandler { leave() }

    val query = q.trim().lowercase()
    val pinnedIds = repo.prefs.state.pinned
    val pins = pinnedIds.mapNotNull { repo.convs[it] }
    val recent = repo.convs.values.filter { it.status == "active" && it.id !in pinnedIds }.sortedByDescending { it.ts }
        .filter { query.isEmpty() || it.title.lowercase().contains(query) }
    val chatNumbers = repo.convs.values.filter { !it.group }.flatMap { c -> c.others.map { it.number.filter(Char::isDigit).takeLast(10) } }.toSet()
    val contacts = repo.contacts.distinctBy { it.number.filter(Char::isDigit).takeLast(10) }
        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.number.filter(Char::isDigit).contains(query.filter(Char::isDigit).ifEmpty { "~" }) }
        .sortedBy { if (it.name.firstOrNull()?.isLetter() == true) it.name.lowercase() else "~" + it.name }
    fun isOn(tg: ShareTo) = picked.any { it.key == tg.key }
    fun toggle(tg: ShareTo) { if (isOn(tg)) picked.removeAll { it.key == tg.key } else picked.add(tg) }

    fun send(asGroup: Boolean) {
        if (sending || picked.isEmpty()) return
        sending = true
        scope.launch {
            val body = text.trim()
            val opened = mutableListOf<String>()
            if (asGroup) {
                val numbers = picked.flatMap { tg -> when (tg) { is ShareTo.Chat -> tg.c.others.map { it.number }; is ShareTo.Someone -> listOf(tg.p.number) } }.distinct()
                repo.startChat(numbers).onSuccess { c -> repo.send(c.id, body, attachments = files.toList()); opened += c.id }
                    .onFailure { repo.toast(it.message ?: "Couldn't start the group") }
            } else picked.forEach { tg ->
                when (tg) {
                    is ShareTo.Chat -> { repo.send(tg.c.id, body, attachments = files.toList()); opened += tg.c.id }
                    is ShareTo.Someone -> repo.startChat(listOf(tg.p.number)).onSuccess { c -> repo.send(c.id, body, attachments = files.toList()); opened += c.id }
                        .onFailure { repo.toast(it.message ?: "Couldn't text ${tg.p.name}") }
                }
            }
            sending = false
            repo.pendingShare = null
            if (opened.isEmpty()) return@launch
            repo.toast(if (opened.size == 1) "Sent" else "Sent to ${opened.size} chats")
            // One chat: open it so you can keep talking. Several: back to your chat list.
            if (opened.size == 1) { nav.back(); nav.openChat(opened.first()) } else nav.home()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize()) {
            PageTop("Share", { leave() })
            Spacer(Modifier.height(if (short) 6.dp else 12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SearchField(q, { q = it }, "Search people and chats", focus = false) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
                // Pinned: a scrolling row of faces, however many you have.
                if (pins.isNotEmpty() && query.isEmpty()) {
                    item { ShareLabel("Pinned") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            items(pins, key = { "pin:" + it.id }) { c ->
                                val tg = ShareTo.Chat(c)
                                val on = isOn(tg)
                                Column(Modifier.width(66.dp).clip(RoundedCornerShape(16.dp)).clickable { toggle(tg) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(58.dp)) {
                                        WhoAvatar(whoOf(c, repo, t), 58.dp, halo = if (on) t.p else null)
                                        if (on) ShareCheck(Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    T(c.shortTitle, size = 12.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) t.p else t.ink, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item { ShareLabel("Recent conversations") }
                    val list = if (query.isEmpty()) recent.take(shown) else recent.take(20)
                    items(list, key = { "c:" + it.id }) { c ->
                        val tg = ShareTo.Chat(c)
                        SharePickRow(isOn(tg), { toggle(tg) }, { WhoAvatar(whoOf(c, repo, t), 44.dp) }, c.title, repo.previewOf(c).ifBlank { if (c.group) "${c.people.size} people" else "" })
                    }
                    if (query.isEmpty() && recent.size > shown) item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            T("Load more", size = 14.sp, weight = FontWeight.SemiBold, color = t.p,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { shown += 8 }.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                    }
                }
                val people = contacts.filter { it.number.filter(Char::isDigit).takeLast(10) !in chatNumbers || query.isNotEmpty() }
                if (people.isNotEmpty()) {
                    item { ShareLabel("Contacts") }
                    items(people, key = { "p:" + it.id + it.number }) { p ->
                        val tg = ShareTo.Someone(p)
                        LaunchedEffect(p.id) { if (repo.prefs.state.showPhotos == "Show" && !p.id.startsWith("dev:")) repo.ensureAvatars(listOf(p.id)) }
                        SharePickRow(isOn(tg), { toggle(tg) }, {
                            val (cb, ci) = toneOf(p.number, p.name)
                            com.jpetrydesign.pyng.ui.kit.Avatar(repo.avatars[p.id]?.ifEmpty { null }, initialsOf(p.name), 44.dp, cb, ci)
                        }, p.name, p.formatted.ifBlank { p.number })
                    }
                }
            }
            // What you're sending, editable, with Send underneath.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(t.sheet)
                    .navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = if (short) 10.dp else 14.dp),
            ) {
                if (files.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        items(files.toList(), key = { it.path }) { f ->
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(t.tone), contentAlignment = Alignment.Center) {
                                if (f.mime.startsWith("image/") || f.mime.startsWith("video/")) AsyncImage(File(f.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else T(f.name.substringAfterLast('.', "FILE").take(4).uppercase(), size = 11.sp, weight = FontWeight.Bold, color = t.p)
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable { files.remove(f) }, contentAlignment = Alignment.Center) {
                                    Ico(Icons.close, Color.White, 10.dp, 2.6f)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = if (short) 42.dp else 48.dp).clip(RoundedCornerShape(24.dp)).background(t.field).padding(horizontal = 18.dp, vertical = if (short) 10.dp else 13.dp)) {
                    if (text.isEmpty()) T("Add a message", size = 15.sp, color = t.soft)
                    BasicTextField(text, { text = it }, maxLines = if (short) 2 else 4, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 15.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(if (short) 8.dp else 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val canGroup = picked.size >= 2
                    Box(
                        Modifier.weight(1f).height(if (short) 44.dp else 52.dp).clip(RoundedCornerShape(26.dp)).border(1.5.dp, if (canGroup) t.p else t.hair, RoundedCornerShape(26.dp))
                            .clickable(enabled = canGroup && !sending) { send(asGroup = true) },
                        contentAlignment = Alignment.Center,
                    ) { T("Send to a group", size = 15.sp, weight = FontWeight.SemiBold, color = if (canGroup) t.p else t.faint) }
                    PillButton(
                        if (sending) "Sending…" else if (picked.size > 1) "Send to ${picked.size}" else "Send",
                        { if (picked.isEmpty()) repo.toast("Pick someone to send to") else send(asGroup = false) },
                        Modifier.weight(1f), primary = picked.isNotEmpty(), height = if (short) 44.dp else 52.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareLabel(text: String) {
    T(text, size = 12.5.sp, weight = FontWeight.SemiBold, color = LocalTok.current.soft, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp))
}

@Composable
private fun ShareCheck(modifier: Modifier = Modifier) {
    val t = LocalTok.current
    Box(modifier.size(22.dp).border(2.dp, t.bg, CircleShape).padding(2.dp).clip(CircleShape).background(t.fill), contentAlignment = Alignment.Center) {
        Ico(Icons.check, t.onFill, 11.dp, 3f)
    }
}

@Composable
private fun SharePickRow(on: Boolean, onClick: () -> Unit, avatar: @Composable () -> Unit, title: String, sub: String) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) t.tone else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(title, size = 15.5.sp, weight = FontWeight.Medium, maxLines = 1)
            if (sub.isNotBlank()) T(sub, size = 13.sp, color = t.soft, maxLines = 1)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).then(if (on) Modifier.background(t.fill) else Modifier.border(2.dp, t.faint, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (on) Ico(Icons.check, t.onFill, 13.dp, 3f) }
    }
}
