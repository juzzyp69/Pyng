package com.jpetrydesign.pyng.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.Conv
import com.jpetrydesign.pyng.Hub
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun InboxScreen(hub: Hub, onOpen: (Conv) -> Unit, onLog: () -> Unit, onUnpair: () -> Unit) {
    val scope = rememberCoroutineScope()
    val connection by hub.connection.collectAsState()
    var convs by remember { mutableStateOf<List<Conv>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        var tries = 0
        while (true) {
            val r = hub.io { it.start(); it.listConversations(60) }
            if (r.isSuccess) { convs = Conv.list(r.getOrThrow()).sortedByDescending { it.ts }; error = null; break }
            error = r.exceptionOrNull()?.message
            if (++tries >= 5) break
            delay(1500L * tries)
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(Unit) {
        hub.events.collect { e ->
            when (e.kind) {
                "conversation" -> {
                    val c = Conv.from(e.data)
                    convs = (convs.filter { it.id != c.id } + c).sortedByDescending { it.ts }
                }
                "message" -> if (convs.none { it.id == e.data.optString("conversationId") }) load()
                "paired" -> load()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Messages", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        Text(statusLabel(connection), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                    }
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Debug log") }, onClick = { menu = false; onLog() })
                            DropdownMenuItem(text = { Text("Unlink Google Messages") }, onClick = { menu = false; onUnpair() })
                        }
                    }
                }
            }
            if (error != null && convs.isEmpty()) {
                item {
                    Text(
                        "Couldn't load chats: $error",
                        color = Color(0xFFC8372D),
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            if (loading && convs.isEmpty()) {
                item { Box(Modifier.padding(40.dp)) { CircularProgressIndicator() } }
            }
            items(convs, key = { it.id }) { c ->
                ConvRow(c) { onOpen(c) }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

private fun statusLabel(s: String) = when (s) {
    "ready", "idle" -> "Linked to Google Messages"
    "connecting" -> "Connecting…"
    "reconnecting" -> "Reconnecting…"
    "phone_not_responding" -> "Your phone isn't responding"
    "error" -> "Connection problem — tap refresh"
    else -> s
}

@Composable
private fun ConvRow(c: Conv, onClick: () -> Unit) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(c.name, c.color, 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name,
                        fontSize = 16.sp,
                        fontWeight = if (c.unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        shortTime(c.ts),
                        fontSize = 12.sp,
                        color = if (c.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontWeight = if (c.unread) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        (if (c.fromMe) "You: " else "") + c.preview,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (c.unread) 0.9f else 0.55f),
                        modifier = Modifier.weight(1f),
                    )
                    if (c.unread) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 86.dp, end = 20.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
    }
}

@Composable
fun Avatar(name: String, hex: String, sizeDp: Int) {
    val base = runCatching { Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val initials = name.split(" ", ",").filter { it.isNotBlank() && it.first().isLetter() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "#" }
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(base.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(initials, color = base, fontWeight = FontWeight.Bold, fontSize = (sizeDp / 3).sp) }
}

fun shortTime(ms: Long): String {
    if (ms <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
}
