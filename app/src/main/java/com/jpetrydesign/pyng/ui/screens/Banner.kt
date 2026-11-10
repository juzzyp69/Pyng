package com.jpetrydesign.pyng.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jpetrydesign.pyng.MainActivity
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.data.Notifier
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.edge
import com.jpetrydesign.pyng.ui.kit.frost
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.delay

/** A frosted banner that drops in when a message arrives in another chat, with quick reply. */
@Composable
fun InAppBanner(item: Pair<Conv, Msg>?, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    var replying by remember(item) { mutableStateOf(false) }
    var draft by remember(item) { mutableStateOf("") }
    LaunchedEffect(item, replying) { if (item != null && !replying) { delay(5500); onClose() } }
    var last by remember { mutableStateOf(item) }
    if (item != null) last = item
    AnimatedVisibility(
        item != null,
        enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = 420f)) { -it } + fadeIn(tween(150)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
    ) {
        val (c, m) = last ?: return@AnimatedVisibility
        val w = whoOf(c)
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 560.dp).fillMaxWidth()
                .shadow(18.dp, shape, clip = false, ambientColor = t.shadow.copy(alpha = 0.22f), spotColor = t.shadow.copy(alpha = 0.3f))
                .frost(shape, if (t.solid) t.glassStrong else if (t.dark) androidx.compose.ui.graphics.Color(0xD0232633) else androidx.compose.ui.graphics.Color(0xD6FFFFFF), 36.dp)
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = if (t.dark) 0.08f else 0.5f), shape)
                .clickable { onOpen(c.id) }.padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WhoAvatar(w, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(if (c.group) c.title else c.title, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    T((if (c.group && m.sender.isNotBlank()) m.sender.substringBefore(' ') + ": " else "") + msgPreview(m), size = 13.5.sp, color = t.ink.copy(alpha = 0.8f), maxLines = 2)
                }
                if (!replying) Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(t.tone).clickable { replying = true }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { T("Reply", size = 13.sp, weight = FontWeight.SemiBold, color = t.p) }
            }
            if (replying) {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(t.field).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (draft.isEmpty()) T("Reply to ${c.shortTitle}", size = 14.5.sp, color = t.soft)
                        BasicTextField(
                            draft, { draft = it }, singleLine = true, cursorBrush = SolidColor(t.p),
                            textStyle = TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = 14.5.sp, color = t.ink),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) { repo.send(c.id, draft); repo.markRead(c.id); onClose() } }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fr),
                        )
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(t.plusBg).clickable {
                        if (draft.isNotBlank()) { repo.send(c.id, draft); repo.markRead(c.id); onClose() }
                    }, contentAlignment = Alignment.Center) { Ico(Icons.send, t.plusInk, 17.dp, 2f) }
                }
            }
        }
    }
}

/** Launcher shortcuts: New message plus your top pinned chats (hold the app icon). */
fun updateShortcuts(ctx: Context, repo: Repo) {
    runCatching {
        val p = repo.prefs.state
        val list = mutableListOf<ShortcutInfoCompat>()
        list += ShortcutInfoCompat.Builder(ctx, "new")
            .setShortLabel("New message")
            .setIcon(IconCompat.createWithResource(ctx, com.jpetrydesign.pyng.R.drawable.ic_shortcut_new))
            .setIntent(Intent(ctx, MainActivity::class.java).setAction("new"))
            .build()
        p.pinned.mapNotNull { repo.convs[it] }.filter { it.status == "active" }.take(3).forEachIndexed { i, c ->
            val path = repo.avatars[c.avatarId].orEmpty()
            val bmp = Notifier.circle(if (path.isNotEmpty()) BitmapFactory.decodeFile(path) else null) ?: initialsBitmap(initialsOf(c.title))
            list += ShortcutInfoCompat.Builder(ctx, "chat-" + c.id)
                .setShortLabel(c.shortTitle.ifBlank { c.title })
                .setLongLabel(c.title)
                .setRank(i + 1)
                .setIcon(IconCompat.createWithBitmap(bmp))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", c.id))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
    }
}

private fun initialsBitmap(ini: String): Bitmap {
    val size = 192
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    c.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3ECFB.toInt() })
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E5FD1.toInt(); textSize = 72f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    c.drawText(ini, size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2, tp)
    return b
}
