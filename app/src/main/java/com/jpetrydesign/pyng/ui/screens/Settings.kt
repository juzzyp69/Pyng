package com.jpetrydesign.pyng.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings as SysSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.jpetrydesign.pyng.BuildConfig
import com.jpetrydesign.pyng.PyngApp
import com.jpetrydesign.pyng.data.LinkService
import com.jpetrydesign.pyng.data.ScheduleWorker
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Card
import com.jpetrydesign.pyng.ui.kit.Chips
import com.jpetrydesign.pyng.ui.kit.CircleButton
import com.jpetrydesign.pyng.ui.kit.EmojiData
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Hairline
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.ListRow
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Page
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.SectionLabel
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.Toggle
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Sec(val key: String, val title: String, val icon: String, val sub: String)

@Composable
fun SettingsScreen(nav: Nav) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val conn by repo.hub.connection.collectAsState()
    val pal = Palettes.byKey(p.themeKey, p.c1, p.c2)
    Page("Settings", { nav.back() }) {
        val shape = RoundedCornerShape(26.dp)
        Row(Modifier.enter(0).fillMaxWidth().clip(shape).background(t.card).clickable { nav.go(Route.Profile) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.profilePhoto.ifEmpty { null }, initialsOf(p.profileName.ifBlank { "Me" }), 58.dp, t.tone, t.p, 20.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                T(p.profileName.ifBlank { "Your profile" }, size = 18.sp, weight = FontWeight.SemiBold)
                T("Your profile · name and photo", size = 13.sp, color = t.soft)
            }
            Ico(Icons.chev, t.faint, 16.dp, 2f)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(Icons.archive, "Archived", repo.convs.values.count { it.status == "archived" }) to "archived",
                Triple(Icons.star, "Starred", p.starred.size) to "starred",
                Triple(Icons.clock, "Scheduled", p.scheduled.size) to "scheduled",
            ).forEachIndexed { i, (x, kind) ->
                Column(Modifier.weight(1f).enter(1 + i, pop = true).clip(RoundedCornerShape(22.dp)).background(t.surface).press(0.95f) { nav.go(Route.Lists(kind)) }.padding(14.dp)) {
                    Ico(x.first, t.p, 19.dp)
                    Spacer(Modifier.height(10.dp))
                    T("${x.third}", size = 20.sp, weight = FontWeight.SemiBold)
                    T(x.second, size = 12.5.sp, color = t.soft)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val secs = listOf(
            Sec("appearance", "Appearance", Icons.palette, "${pal.name} · ${p.mode} · ${p.bubble} bubbles"),
            Sec("chats", "Chats", Icons.message, "Replies, receipts, media, GIFs, gestures"),
            Sec("notifications", "Notifications", Icons.bell, if (p.on("notif")) "On · quick reply " + (if (p.on("quick")) "on" else "off") else "Off"),
            Sec("chatfeat", "Chat features", Icons.wifi, if (conn == "ready" || conn == "idle") "Connected through Google Messages" else "Connecting…"),
            Sec("privacy", "Privacy & safety", Icons.shield, (if (p.on("spam")) "Spam protection on" else "Spam protection off") + if (p.on("bioLock")) " · app lock on" else ""),
            Sec("access", "Accessibility", Icons.access, "Text ${p.textSize}" + (if (p.on("boldText")) " · bold" else "") + (if (p.on("highContrast")) " · high contrast" else "") + (if (p.on("reduceBlur")) " · solid surfaces" else "")),
            Sec("storage", "Storage", Icons.storage, "Cached photos and files"),
            Sec("about", "Help & about", Icons.about, "Version ${BuildConfig.VERSION_NAME}"),
        )
        Card {
            secs.forEachIndexed { i, s ->
                if (i > 0) Hairline()
                Box(Modifier.enter(4 + i)) {
                    ListRow(s.title, s.sub, s.icon, onClick = { if (s.key == "appearance") nav.go(Route.Appearance) else nav.go(Route.Section(s.key)) })
                }
            }
        }
    }
}

@Composable
private fun TogRow(label: String, key: String, sub: String? = null, onChange: (Boolean) -> Unit = {}) {
    val repo = LocalRepo.current
    val on = repo.prefs.state.on(key)
    ListRow(label, sub, onClick = { repo.prefs.toggle(key); onChange(!on) }, trailing = { Toggle(on) })
}

@Composable
private fun SegRow(label: String, key: String, options: List<String>, sub: String? = null) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        T(label, size = 15.5.sp, weight = FontWeight.SemiBold)
        if (sub != null) T(sub, size = 12.5.sp, color = t.soft, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        Chips(options, repo.prefs.state.opt(key), { repo.prefs.setOpt(key, it) })
    }
}

@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    if (title != null) SectionLabel(title)
    else Spacer(Modifier.height(12.dp))
    Card { GroupRows(content) }
}

@Composable
private fun GroupRows(content: @Composable () -> Unit) { content() }

@Composable
fun SectionScreen(nav: Nav, key: String) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val conn by repo.hub.connection.collectAsState()
    val app = ctx.applicationContext as PyngApp
    val title = mapOf("chats" to "Chats", "notifications" to "Notifications", "chatfeat" to "Chat features", "privacy" to "Privacy & safety", "access" to "Accessibility", "storage" to "Storage", "about" to "Help & about")[key] ?: "Settings"
    Page(title, { nav.back() }) {
        when (key) {
            "chats" -> {
                Group("Conversation") {
                    TogRow("Smart replies", "smart", "Suggestions above the keyboard"); Hairline()
                    TogRow("Typing indicators", "typing", "Show when people are typing, and let them see when you are"); Hairline()
                    TogRow("Read receipts", "receipts", "Show when people have read your messages"); Hairline()
                    TogRow("Voice message button", "micBtn", "Show the mic next to the message box"); Hairline()
                    TogRow("Link previews", "previews", "Show a preview card for links"); Hairline()
                    TogRow("Enter key sends", "enterSend", "Your keyboard's enter key sends instead of adding a line")
                }
                Group("Reactions & effects") {
                    ListRow("Quick reactions", p.quickReacts.joinToString("  "), onClick = { nav.go(Route.ReactPick) }); Hairline()
                    TogRow("Message effects", "effects", "Confetti, balloons and hearts for phrases like \"happy birthday\"")
                }
                Group("Media & GIFs") {
                    SegRow("Auto-download media", "autoDl", listOf("Always", "Wi-Fi only", "Never")); Hairline()
                    SegRow("Photo quality when sending", "quality", listOf("Original", "High", "Data saver")); Hairline()
                    SegRow("GIF content rating", "gifRating", listOf("G", "PG", "PG-13"))
                }
                Group("Gestures & layout") {
                    SegRow("Swipe a conversation right to", "swipeR", listOf("Read/Unread", "Archive", "Delete", "Pin", "Mute", "Off")); Hairline()
                    SegRow("Swipe a conversation left to", "swipe", listOf("Archive", "Read/Unread", "Delete", "Pin", "Mute", "Off")); Hairline()
                    SegRow("Timestamps", "stamps", listOf("Grouped", "Every message"), "Grouped shows a time between bursts. Tap beside any message for its status.")
                }
            }
            "notifications" -> {
                Group("Alerts") {
                    TogRow("Notifications", "notif", "Alerts for new messages"); Hairline()
                    TogRow("Vibrate", "vibrate"); Hairline()
                    TogRow("Reply and mark as read from notifications", "quick")
                }
                Group("Staying connected") {
                    TogRow("Keep connected in the background", "background", "Needed for notifications. Shows a quiet, silent notification while connected.") { on ->
                        if (on) LinkService.start(ctx) else LinkService.stop(ctx)
                    }
                    Hairline()
                    ListRow("Sound and lock screen", "Choose the notification sound in Android settings", onClick = {
                        runCatching {
                            ctx.startActivity(Intent(SysSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(SysSettings.EXTRA_CHANNEL_ID, "messages"))
                        }
                    })
                }
                Note("Each chat can turn its own alerts off in its contact card. Do Not Disturb follows your phone settings.")
            }
            "chatfeat" -> {
                Group("Status") {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ok = conn == "ready" || conn == "idle"
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Color(0xFF2EB872) else if (conn == "error") t.danger else Color(0xFFE3A72B)))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            T(when (conn) { "ready", "idle" -> "Connected"; "phone_not_responding" -> "Your phone isn't responding"; "error" -> "Connection problem"; else -> "Connecting…" }, size = 15.5.sp, weight = FontWeight.SemiBold)
                            T("Linked through Google Messages on your phone", size = 12.5.sp, color = t.soft)
                        }
                    }
                    repo.sims.forEach { s ->
                        Hairline()
                        ListRow(s.name.ifBlank { "SIM" }, (s.number.ifBlank { "Number hidden" }) + if (s.rcs) " · chat features on" else " · texts only", icon = Icons.sim, trailing = null)
                    }
                    Hairline()
                    ListRow("Reconnect", "Use if new messages stop arriving", onClick = { repo.refresh(); repo.toast("Reconnecting…") })
                }
                Group(null) {
                    ListRow("Unlink Google Messages", "Signs this app out. Your messages stay in Google Messages.", icon = Icons.logout, danger = true, onClick = {
                        repo.unpair(); LinkService.stop(ctx)
                    }, trailing = null)
                }
                Note("Chat features (RCS) come from Google Messages running on your phone. Group names, reactions, typing and read receipts only reach people who also have chat features on.")
            }
            "privacy" -> {
                Group("Permissions") { PermissionRows() }
                Group("Protection") {
                    TogRow("Spam protection", "spam", "Checks unknown senders and their links on this phone"); Hairline()
                    ListRow("Spam & blocked", "Chats Google Messages marked as spam, and numbers you blocked", onClick = { nav.go(Route.Lists("spam")) }); Hairline()
                    TogRow("Copy buttons for codes", "otpChip", "Shows a Copy button for verification codes in your list")
                }
                Group("App lock") {
                    TogRow("Lock with fingerprint", "bioLock", "Require your fingerprint or face to open Messages"); Hairline()
                    TogRow("Hide in recent apps", "hideSwitcher", "Blank out conversations in the app switcher and block screenshots")
                }
            }
            "access" -> {
                Group("Text") {
                    Column(Modifier.padding(16.dp)) {
                        T("Text size", size = 15.5.sp, weight = FontWeight.SemiBold)
                        T("Messages and your conversation list", size = 12.5.sp, color = t.soft)
                        Spacer(Modifier.height(10.dp))
                        Chips(listOf("S", "M", "L", "XL"), p.textSize, { v -> repo.prefs.edit { it.copy(textSize = v) } })
                    }
                    Hairline()
                    TogRow("Bold text", "boldText", "Heavier text in chats and your conversation list"); Hairline()
                    TogRow("High contrast", "highContrast", "Darker secondary text, stronger dividers and fields")
                }
                Group("Visual") {
                    TogRow("Reduce transparency", "reduceBlur", "Solid surfaces instead of frosted glass"); Hairline()
                    TogRow("Reduce motion", "reduceMotion", "Turns off animations like typing dots and effects")
                }
                val scale = remember { runCatching { SysSettings.Global.getFloat(ctx.contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) }
                if (scale == 0f) {
                    Spacer(Modifier.height(12.dp))
                    Card {
                        ListRow("Android animations are off", "Your phone's \"Remove animations\" setting turns off motion in every app, including this one. Tap to open accessibility settings.", icon = Icons.access, onClick = {
                            runCatching { ctx.startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        })
                    }
                }
                Note("These work alongside your phone's accessibility settings, including system font size and TalkBack.")
            }
            "storage" -> {
                var size by remember { mutableStateOf(-1L) }
                LaunchedEffect(Unit) { size = withContext(Dispatchers.IO) { repo.cacheBytes() } }
                Group("Usage") {
                    ListRow("Cached photos and files", if (size < 0) "Calculating…" else sizeText(size).ifEmpty { "Nothing cached" }, icon = Icons.storage, trailing = null); Hairline()
                    ListRow("Clear cache", "Photos download again when you open them", onClick = { repo.clearCache(); size = 0 })
                }
                Note("Your messages and media are kept and backed up by Google Messages with your Google account. This app only keeps its own settings and a cache.")
            }
            else -> {
                Group(null) {
                    ListRow("Version", BuildConfig.VERSION_NAME, trailing = null); Hairline()
                    ListRow("Debug log", "Share this if something isn't working", onClick = { nav.go(Route.Log) }); Hairline()
                    ListRow("Replay setup", "See the welcome screens again", onClick = { nav.replace(Route.Setup(0)) }); Hairline()
                    ListRow("Open-source licenses", "libgm (AGPL-3.0) · Geist font (OFL)", onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mautrix/gmessages"))) }
                    })
                }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") app
}

@Composable
private fun Note(text: String) {
    T(text, size = 12.5.sp, color = LocalTok.current.soft, lineHeight = 18.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp))
}

@Composable
fun ProfileScreen(nav: Nav) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p.profileName) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = repo.importUri(uri) ?: return@launch
            val dest = repo.keepFile("me"); File(f.path).copyTo(dest, overwrite = true)
            repo.prefs.edit { it.copy(profilePhoto = dest.path) }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { repo.prefs.edit { it.copy(profileName = name.trim()) } } }
    Page("Your profile", { nav.back() }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.enter(0, pop = true).press(0.95f) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Avatar(p.profilePhoto.ifEmpty { null }, initialsOf(name.ifBlank { "Me" }), 120.dp, t.tone, t.p, 40.sp)
                Box(Modifier.align(Alignment.BottomEnd).size(38.dp).clip(CircleShape).background(t.fill).border(3.dp, t.bg, CircleShape), contentAlignment = Alignment.Center) { Ico(Icons.camera, t.onFill, 17.dp, 2f) }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(t.field).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Name", size = 14.sp, weight = FontWeight.SemiBold, color = t.soft)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (name.isEmpty()) T("Your name", size = 16.sp, color = t.soft)
                    BasicTextField(name, { name = it }, singleLine = true, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 16.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
                }
            }
            if (p.profilePhoto.isNotEmpty()) T("Remove photo", size = 14.sp, weight = FontWeight.SemiBold, color = t.danger, modifier = Modifier.padding(top = 14.dp).clickable { repo.prefs.edit { it.copy(profilePhoto = "") } }.padding(8.dp))
            Note("Your name and photo show on your Settings card and home screen. What others see when you text them comes from your Google Messages profile.")
        }
    }
}

@Composable
fun ReactPickScreen(nav: Nav) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    var slot by remember { mutableIntStateOf(0) }
    BackHandler { nav.back() }
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize()) {
            com.jpetrydesign.pyng.ui.kit.PageTop("Quick reactions", { nav.back() }, sub = "Tap a slot, then pick an emoji")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp)).background(t.surface).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                p.quickReacts.forEachIndexed { i, e ->
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (i == slot) t.tone else Color.Transparent).then(if (i == slot) Modifier.border(2.dp, t.p, CircleShape) else Modifier).clickable { slot = i },
                        contentAlignment = Alignment.Center,
                    ) { T(e, size = 26.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(EmojiData.cats.flatMap { it.items }.map { it.first }) { e ->
                    Box(Modifier.aspectRatio(1f).clip(CircleShape).clickable {
                        repo.prefs.edit { pr -> pr.copy(quickReacts = pr.quickReacts.toMutableList().also { l -> if (slot in l.indices) l[slot] = e }) }
                        slot = (slot + 1) % 6
                    }, contentAlignment = Alignment.Center) { T(e, size = 26.sp) }
                }
            }
        }
    }
}

@Composable
fun ListScreen(nav: Nav, kind: String) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val ctx = LocalContext.current
    val p = repo.prefs.state
    val title = mapOf("archived" to "Archived", "spam" to "Spam & blocked", "starred" to "Starred", "scheduled" to "Scheduled")[kind] ?: ""
    val sub = mapOf(
        "archived" to "Archived chats come back when someone messages you.",
        "spam" to "Suspected spam lands here quietly, with no alerts. Links stay off until you mark a chat as not spam.",
        "starred" to "Messages you starred in any chat",
        "scheduled" to "Messages waiting to send",
    )[kind]
    Page(title, { nav.back() }) {
        if (sub != null) T(sub, size = 13.sp, color = t.soft, lineHeight = 18.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        data class Item(val convId: String, val title: String, val sub: String, val meta: String?, val open: () -> Unit, val acts: List<Pair<String, () -> Unit>>)
        val spamLocal = repo.convs.values.filter { c -> p.on("spam") && c.status == "active" && c.id !in p.notSpam && !c.fromMe && spamCheck(c, c.preview) != null }
        val items: List<Item> = when (kind) {
            "archived" -> repo.convs.values.filter { it.status == "archived" }.sortedByDescending { it.ts }.map { c ->
                Item(c.id, c.title, c.preview, listTime(c.ts), { nav.openChat(c.id) }, listOf("Unarchive" to { repo.setArchived(c.id, false); repo.toast("Moved back to your inbox") }))
            }
            "spam" -> (repo.convs.values.filter { it.status == "spam" || it.status == "blocked" } + spamLocal).distinctBy { it.id }.sortedByDescending { it.ts }.map { c ->
                Item(c.id, c.title, c.preview, if (c.status == "blocked") "Blocked" else null, { nav.openChat(c.id) }, listOf(
                    "Not spam" to {
                        if (c.status == "blocked" || c.status == "spam") repo.setBlocked(c.id, false)
                        repo.prefs.edit { it.copy(notSpam = it.notSpam + c.id) }; repo.toast("Moved to your inbox")
                    },
                ) + if (c.status != "blocked") listOf("Report & block" to { repo.setBlocked(c.id, true, true); repo.toast("Reported and blocked") }) else emptyList())
            }
            "starred" -> p.starred.sortedByDescending { it.ts }.map { s ->
                val c = repo.convs[s.convId]
                Item(s.convId, c?.title ?: "Chat", s.text, listTime(s.ts), { nav.openChat(s.convId, s.msgId) }, listOf("Unstar" to { repo.prefs.edit { pr -> pr.copy(starred = pr.starred.filter { it.msgId != s.msgId }) } }))
            }
            else -> p.scheduled.sortedBy { it.at }.map { s ->
                val c = repo.convs[s.convId]
                Item(s.convId, c?.title ?: "Chat", s.text, "Sends ${s.label}", { nav.openChat(s.convId) }, listOf(
                    "Send now" to {
                        ScheduleWorker.cancel(ctx, s.id); repo.send(s.convId, s.text)
                        repo.prefs.edit { pr -> pr.copy(scheduled = pr.scheduled.filter { it.id != s.id }) }; repo.toast("Sent")
                    },
                    "Cancel" to {
                        ScheduleWorker.cancel(ctx, s.id)
                        repo.prefs.edit { pr -> pr.copy(scheduled = pr.scheduled.filter { it.id != s.id }) }; repo.toast("Scheduled message canceled")
                    },
                ))
            }
        }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) {
                    Ico(mapOf("archived" to Icons.archive, "spam" to Icons.shield, "starred" to Icons.star, "scheduled" to Icons.clock)[kind] ?: Icons.inbox, t.p, 30.dp, 1.8f)
                }
                Spacer(Modifier.height(10.dp))
                T(mapOf("archived" to "Nothing archived", "spam" to "No spam right now", "starred" to "No starred messages", "scheduled" to "Nothing scheduled")[kind] ?: "", size = 16.sp, weight = FontWeight.SemiBold)
            }
        }
        items.forEachIndexed { i, it ->
            val c = repo.convs[it.convId]
            Column(Modifier.enter(i).fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp)).background(t.card).clickable(onClick = it.open).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c != null) { val w = whoOf(c); Avatar(w.photo, w.ini, 42.dp, w.bg, w.ink) } else Avatar(null, "#", 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row { T(it.title, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f)); if (it.meta != null) T(it.meta, size = 12.sp, color = t.soft) }
                        T(it.sub, size = 13.5.sp, color = t.soft, maxLines = 2)
                    }
                }
                if (it.acts.isNotEmpty()) Row(Modifier.padding(top = 10.dp, start = 54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    it.acts.forEachIndexed { j, (label, go) ->
                        Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (j == 0) t.tone else t.neutral).clickable(onClick = go).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            T(label, size = 13.sp, weight = FontWeight.SemiBold, color = if (j == 0) t.p else if (label.contains("block")) t.danger else t.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViewerScreen(nav: Nav, path: String, mime: String) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var chrome by remember { mutableStateOf(true) }
    val state = rememberTransformableState { z, pan, _ -> scale = (scale * z).coerceIn(1f, 6f); offset = if (scale > 1f) offset + pan else Offset.Zero }
    BackHandler { nav.back() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            File(path), null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().transformable(state)
                .pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }, onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        )
        if (chrome) Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).statusBarsPadding().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.back, "Back", { nav.back() }, tint = Color.White)
            Spacer(Modifier.weight(1f))
            CircleButton(Icons.share, "Share", {
                runCatching {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                }
            }, tint = Color.White, iconSize = 20.dp)
            CircleButton(Icons.download, "Save", { saveToGallery(ctx, path, mime) }, tint = Color.White, iconSize = 20.dp)
        }
    }
}

private fun saveToGallery(ctx: Context, path: String, mime: String) {
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Messages-" + System.currentTimeMillis() + "." + (mime.substringAfter('/').ifBlank { "jpg" }))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Messages")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { out -> File(path).inputStream().use { it.copyTo(out) } }
        Toast.makeText(ctx, "Saved to Pictures", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(ctx, "Couldn't save", Toast.LENGTH_SHORT).show() }
}

@Composable
fun LogScreen(nav: Nav) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val t = LocalTok.current
    var text by remember { mutableStateOf("Loading…") }
    LaunchedEffect(Unit) {
        val scale = runCatching { SysSettings.Global.getFloat(ctx.contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        val head = "Version ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · animation scale $scale\n\n"
        text = head + repo.hub.io { it.readLog(150_000) }.getOrDefault("").ifBlank { "The log is empty." }
    }
    BackHandler { nav.back() }
    Box(Modifier.fillMaxSize().background(t.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            com.jpetrydesign.pyng.ui.kit.PageTop("Debug log", { nav.back() }, trailing = {
                Row {
                    T("Copy", size = 14.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.clickable {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Log", text)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }.padding(10.dp))
                    T("Share", size = 14.sp, weight = FontWeight.SemiBold, color = t.p, modifier = Modifier.clickable {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share log"))
                    }.padding(10.dp))
                }
            })
            androidx.compose.material3.Text(
                text, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = t.ink),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}

@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTok.current
    Box(Modifier.fillMaxSize().background(t.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(20.dp)) {
                T("Messages closed unexpectedly", size = 22.sp, weight = FontWeight.Bold)
                T("Share this report so it can be fixed.", size = 14.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Share report", { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share crash report")) }, height = 42.dp)
                    PillButton("Continue", onContinue, primary = false, height = 42.dp)
                }
            }
            androidx.compose.material3.Text(
                report, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = t.ink),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}
