package com.jpetrydesign.pyng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.Hub
import com.jpetrydesign.pyng.Msg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(hub: Hub, conversationId: String, name: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun upsert(m: Msg) {
        val i = msgs.indexOfFirst { (m.id.isNotEmpty() && it.id == m.id) || (m.tmpId.isNotEmpty() && it.tmpId == m.tmpId) }
        msgs = tidy(if (i >= 0) msgs.toMutableList().also { it[i] = m } else msgs + m)
    }

    LaunchedEffect(conversationId) {
        var tries = 0
        while (true) {
            val r = hub.io { it.fetchMessages(conversationId, 50) }
            if (r.isSuccess) { msgs = tidy(Msg.list(r.getOrThrow()) + msgs.filter { it.id.isEmpty() }); error = null; break }
            error = r.exceptionOrNull()?.message
            if (++tries >= 4) break
            delay(1500L * tries)
        }
        loading = false
        msgs.lastOrNull { !it.fromMe }?.let { last -> hub.io { it.markRead(conversationId, last.id) } }
    }
    LaunchedEffect(conversationId) {
        hub.events.collect { e ->
            when (e.kind) {
                "message" -> if (e.data.optString("conversationId") == conversationId) {
                    val m = Msg.from(e.data)
                    upsert(m)
                    if (!m.fromMe) { typing = false; hub.io { it.markRead(conversationId, m.id) } }
                }
                "typing" -> if (e.data.optString("conversationId") == conversationId) typing = e.data.optBoolean("typing")
            }
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(0) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        val local = Msg("", "local-${System.nanoTime()}", conversationId, true, text, "", System.currentTimeMillis(), "LOCAL", "", "")
        msgs = msgs + local
        scope.launch {
            hub.io { it.sendText(conversationId, text) }
                .onSuccess { tmp -> msgs = msgs.map { if (it.tmpId == local.tmpId) it.copy(tmpId = tmp, status = "OUTGOING_SENDING") else it } }
                .onFailure { err -> msgs = msgs.map { if (it.tmpId == local.tmpId) it.copy(status = "OUTGOING_FAILED: ${err.message}") else it } }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Avatar(name, "", 44)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (typing) Text("typing…", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.weight(1f).widthIn(max = 760.dp).fillMaxWidth()) {
            if (loading && msgs.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (error != null && msgs.isEmpty()) {
                Text("Couldn't load messages: $error", color = Color(0xFFC8372D), modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            LazyColumn(
                state = list,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(msgs.asReversed(), key = { msgKey(it) }) { m -> Bubble(m) }
            }
        }
        Row(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Chat message") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { send() }, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

private fun msgKey(m: Msg) = if (m.id.isNotEmpty()) "m:" + m.id else "t:" + m.tmpId

/** One entry per message, oldest first. */
private fun tidy(list: List<Msg>) = list.distinctBy { msgKey(it) }.sortedBy { if (it.ts > 0) it.ts else Long.MAX_VALUE }

@Composable
private fun Bubble(m: Msg) {
    val mine = m.fromMe
    val text = m.text.ifEmpty { if (m.media.isNotEmpty()) mediaLabel(m.media) else "" }
    if (text.isEmpty() && m.reactions.isEmpty()) return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine && m.sender.isNotEmpty()) {
            Text(m.sender, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        }
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                lineHeight = 21.sp,
            )
        }
        val meta = buildList {
            if (m.reactions.isNotEmpty()) add(m.reactions)
            if (mine && m.failed) add("Not sent")
            else if (mine && m.pending) add("Sending…")
        }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString("  ·  "),
                fontSize = 11.sp,
                color = if (m.failed) Color(0xFFC8372D) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

private fun mediaLabel(mime: String) = when {
    mime.startsWith("image/gif") -> "GIF"
    mime.startsWith("image") -> "📷 Photo"
    mime.startsWith("video") -> "🎬 Video"
    mime.startsWith("audio") -> "🎤 Voice message"
    else -> "📎 Attachment"
}
