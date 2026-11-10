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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.launch
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

/**
 * A frosted banner that drops in when a message arrives in another chat: reply right there, react, or mark it
 * read. Swipe it left or right to dismiss it, or flick it up to tuck it away.
 */
@Composable
fun InAppBanner(item: Pair<Conv, Msg>?, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var last by remember { mutableStateOf(item) }
    if (item != null) last = item
    var replying by remember(last) { mutableStateOf(false) }
    var draft by remember(last) { mutableStateOf("") }
    var holding by remember(last) { mutableStateOf(false) }
    val dx = remember(last) { androidx.compose.animation.core.Animatable(0f) }
    val dy = remember(last) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(item, replying, holding) { if (item != null && !replying && !holding) { delay(6000); onClose() } }
    AnimatedVisibility(
        item != null,
        enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = 420f)) { -it } + fadeIn(tween(150)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
    ) {
        val (c, m) = last ?: return@AnimatedVisibility
        val w = whoOf(c)
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 560.dp).fillMaxWidth()
                .graphicsLayer {
                    translationX = dx.value
                    translationY = kotlin.math.min(0f, dy.value) + kotlin.math.max(0f, dy.value) * 0.2f
                    rotationZ = dx.value / size.width.coerceAtLeast(1f) * 8f
                    alpha = 1f - (kotlin.math.abs(dx.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.7f)
                }
                .pointerInput(last) {
                    detectDragGestures(
                        onDragStart = { holding = true },
                        onDragEnd = {
                            scope.launch {
                                val wpx = size.width.toFloat()
                                when {
                                    kotlin.math.abs(dx.value) > wpx * 0.28f -> { dx.animateTo(kotlin.math.sign(dx.value) * wpx * 1.3f, tween(200)); onClose() }
                                    dy.value < -40f -> onClose()
                                    else -> {
                                        launch { dy.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                                        dx.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                                    }
                                }
                                holding = false
                            }
                        },
                        onDragCancel = { scope.launch { dx.animateTo(0f); dy.animateTo(0f); holding = false } },
                    ) { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d.x); dy.snapTo(dy.value + d.y) } }
                }
                .shadow(22.dp, shape, clip = false, ambientColor = t.shadow.copy(alpha = 0.26f), spotColor = t.shadow.copy(alpha = 0.34f))
                .frost(shape, if (t.solid) t.glassStrong else if (t.dark) androidx.compose.ui.graphics.Color(0xB81E2129) else androidx.compose.ui.graphics.Color(0xB8FFFFFF), 40.dp)
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = if (t.dark) 0.08f else 0.55f), shape)
                .clickable { onOpen(c.id) }.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                WhoAvatar(w, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(c.title, size = 15.5.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        T("now", size = 12.sp, color = t.soft)
                    }
                    T((if (c.group && m.sender.isNotBlank()) m.sender.substringBefore(' ') + ": " else "") + msgPreview(m), size = 14.sp, color = t.ink.copy(alpha = 0.86f), maxLines = 2, lineHeight = 19.sp)
                }
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
            } else {
                // Reply, a few quick reactions, and Mark read.
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(t.ink.copy(alpha = 0.07f)).clickable { replying = true }.padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Ico(Icons.reply, t.ink, 16.dp, 2f); Spacer(Modifier.width(6.dp)); T("Reply", size = 13.5.sp, weight = FontWeight.SemiBold) }
                    if (m.id.isNotEmpty()) repo.prefs.state.quickReacts.take(3).forEach { e ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(t.ink.copy(alpha = 0.07f)).clickable { repo.react(m, e); repo.toast("Reacted $e"); onClose() },
                            contentAlignment = Alignment.Center,
                        ) { T(e, size = 17.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(t.ink.copy(alpha = 0.07f)).clickable { repo.markRead(c.id); onClose() }.padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("Mark read", size = 13.5.sp, weight = FontWeight.SemiBold) }
                }
            }
            Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(34.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(t.ink.copy(alpha = 0.16f)))
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
