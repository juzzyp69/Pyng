package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.EmojiData
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.SheetAction
import com.jpetrydesign.pyng.ui.kit.SheetGroup
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.Tok
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MessageMenu(
    m: Msg?,
    t: Tok,
    conv: Conv?,
    onDismiss: () -> Unit,
    onReact: (Msg, String) -> Unit,
    onReply: (Msg) -> Unit,
    onCopy: (Msg) -> Unit,
    onForward: (Msg) -> Unit,
    onStar: (Msg) -> Unit,
    onSelect: (Msg) -> Unit,
    onDetails: (Msg) -> Unit,
    onDelete: (Msg) -> Unit,
) {
    val repo = LocalRepo.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    var more by remember(m) { mutableStateOf(false) }
    Sheet(visible = m != null, onDismiss = onDismiss) {
        val cur = last ?: return@Sheet
        val p = repo.prefs.state
        val myId = conv?.me?.id.orEmpty()
        val mine = cur.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji
        val canReact = cur.id.isNotEmpty() && conv?.rcs == true
        if (canReact) {
            if (!more) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.surface).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    p.quickReacts.forEachIndexed { i, e ->
                        Box(
                            Modifier.enter(i, pop = true).size(44.dp).clip(CircleShape).background(if (mine == e) t.tone else androidx.compose.ui.graphics.Color.Transparent)
                                .press(0.8f) { onReact(cur, e); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) { T(e, size = 26.sp) }
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { more = true }, contentAlignment = Alignment.Center) { Ico(Icons.plus, t.soft, 20.dp, 2f) }
                }
            } else {
                LazyVerticalGrid(GridCells.Fixed(8), Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(260.dp)) {
                    items(EmojiData.cats.flatMap { it.items }.map { it.first }) { e ->
                        Box(Modifier.aspectRatio(1f).clip(CircleShape).clickable { onReact(cur, e); onDismiss() }, contentAlignment = Alignment.Center) { T(e, size = 24.sp) }
                    }
                }
            }
        }
        val preview = msgPreview(cur)
        if (preview.isNotEmpty()) T(preview, size = 13.sp, color = t.soft, maxLines = 1, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
        SheetGroup {
            var i = 0
            if (cur.id.isNotEmpty()) SheetAction("Reply", Icons.reply, { onReply(cur); onDismiss() }, first = true, index = i++)
            if (cur.text.isNotEmpty()) SheetAction("Copy text", Icons.copy, { onCopy(cur); onDismiss() }, first = i == 0, index = i++)
            SheetAction("Forward", Icons.forward, { onForward(cur); onDismiss() }, first = i == 0, index = i++)
            SheetAction(if (p.isStarred(cur.key)) "Unstar" else "Star", Icons.star, { onStar(cur); onDismiss() }, index = i++)
            SheetAction("Select", Icons.select, { onSelect(cur); onDismiss() }, index = i++)
            SheetAction("Details", Icons.details, { onDetails(cur); onDismiss() }, index = i++)
            SheetAction("Delete", Icons.trash, { onDelete(cur); onDismiss() }, danger = true, index = i)
        }
    }
}

@Composable
fun DetailsSheet(m: Msg?, conv: Conv?, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    Sheet(visible = m != null, onDismiss = onDismiss) {
        val cur = last ?: return@Sheet
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val rows = buildList {
            add("Type" to when {
                cur.isSys -> "Update"
                conv?.rcs == true && !cur.isSms && !cur.isMms -> "Chat message (RCS)"
                cur.isMms || cur.media.isNotEmpty() -> "Picture message (MMS)"
                else -> "Text message (SMS)"
            })
            add((if (cur.fromMe) "To" else "From") to if (cur.fromMe) (conv?.title ?: "") else cur.sender.ifBlank { conv?.title ?: "" })
            if (cur.ts > 0) add((if (cur.fromMe) "Sent" else "Received") to fmt.format(cur.ts))
            if (cur.fromMe) add("Status" to when {
                cur.failed -> "Not sent" + (cur.error.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
                cur.pending -> "Sending"
                cur.readByThem -> "Read"
                cur.delivered -> "Delivered"
                else -> "Sent"
            })
            cur.media.forEach { add("Attachment" to (it.name.ifBlank { it.mime } + if (it.size > 0) " · " + sizeText(it.size) else "")) }
            if (cur.reactions.isNotEmpty()) add("Reactions" to cur.reactions.joinToString("  ") { it.emoji + if (it.count > 1) " ${it.count}" else "" })
        }
        T("Message details", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        SheetGroup {
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp)) {
                    T(k, size = 14.sp, color = t.soft, modifier = Modifier.width(100.dp))
                    T(v, size = 14.sp, weight = FontWeight.Medium)
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION") repo
    }
}

@Composable
fun ForwardSheet(m: Msg?, onPick: (String?, Msg) -> Unit) {
    val repo = LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    Sheet(visible = m != null, onDismiss = { last?.let { onPick(null, it) } }, maxHeightFraction = 0.8f) {
        val cur = last ?: return@Sheet
        T("Forward to", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        val convs = repo.convs.values.filter { it.status == "active" }.sortedByDescending { it.ts }
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
            items(convs, key = { it.id }) { c ->
                val w = whoOf(c)
                Row(Modifier.fillMaxWidth().clickable { onPick(c.id, cur) }.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(w.photo, w.ini, 42.dp, w.bg, w.ink)
                    Spacer(Modifier.width(12.dp))
                    T(c.title, size = 15.5.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    Ico(Icons.send, t.p, 18.dp, 2f)
                }
            }
        }
    }
}

@Composable
fun CallSheet(open: Boolean, conv: Conv?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    Sheet(visible = open, onDismiss = onDismiss) {
        val c = conv ?: return@Sheet
        val w = whoOf(c)
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(w.photo, w.ini, 64.dp, w.bg, w.ink, 22.sp)
            Spacer(Modifier.height(8.dp))
            T(c.title, size = 18.sp, weight = FontWeight.SemiBold)
            T(c.number, size = 13.5.sp, color = t.soft)
        }
        SheetGroup {
            SheetAction("Voice call", Icons.phone, { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' }))) }; onDismiss() }, first = true)
            SheetAction("Video call", Icons.video, {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' })).putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", 3))
                }
                onDismiss()
            }, index = 1)
            SheetAction("Copy number", Icons.copy, { copy(ctx, c.number); onDismiss() }, index = 2)
        }
    }
}
