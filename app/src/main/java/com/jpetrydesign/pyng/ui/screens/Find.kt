package com.jpetrydesign.pyng.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Contact
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Glow
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PageTop
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Seg
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.LocalTok
import kotlinx.coroutines.launch

@Composable
fun SearchField(value: String, onValue: (String) -> Unit, hint: String, prefix: String? = null, focus: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    val t = LocalTok.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focus) runCatching { fr.requestFocus() } }
    Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(t.field).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (prefix != null) { T(prefix, size = 15.sp, weight = FontWeight.SemiBold, color = t.soft); Spacer(Modifier.width(12.dp)) }
        else { Ico(Icons.search, t.soft, 18.dp, 2.2f); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) T(hint, size = 15.sp, color = t.soft)
            BasicTextField(
                value, onValue, singleLine = true, cursorBrush = SolidColor(t.p),
                textStyle = TextStyle(fontFamily = Geist, fontSize = 15.sp, color = t.ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
        }
        if (value.isNotEmpty()) Box(Modifier.size(28.dp).clip(CircleShape).clickable { onValue("") }, contentAlignment = Alignment.Center) { Ico(Icons.close, t.soft, 14.dp, 2.4f) }
    }
}

@Composable
fun SearchScreen(nav: Nav) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    var q by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("All") }
    BackHandler { nav.back() }
    val query = q.trim().lowercase()
    val convs = repo.convs.values.filter { it.status != "deleted" }
    val people = if (query.isEmpty()) emptyList() else convs.filter { c -> c.title.lowercase().contains(query) || c.others.any { it.number.contains(query) || it.formatted.contains(query) } }
    val hits = if (query.length < 2) emptyList() else repo.msgs.flatMap { (cid, list) -> list.filter { it.text.lowercase().contains(query) }.map { cid to it } }.sortedByDescending { it.second.ts }
    val previews = if (query.length < 2) emptyList() else convs.filter { it.preview.lowercase().contains(query) && hits.none { h -> h.first == it.id } }
    val media = repo.msgs.flatMap { (cid, list) -> list.filter { m -> m.media.any { it.isImage || it.isVideo } }.map { cid to it } }.sortedByDescending { it.second.ts }
    val links = repo.msgs.flatMap { (cid, list) -> list.filter { detect(it.text).url != null }.map { cid to it } }.sortedByDescending { it.second.ts }
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize()) {
            PageTop("Search", { nav.back() })
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                SearchField(q, { q = it }, "Search people and messages")
                Spacer(Modifier.height(12.dp))
                Seg(listOf("All", "Photos", "Links"), tab, { tab = it })
            }
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp)) {
                when (tab) {
                    "Photos" -> {
                        val shown = if (query.isEmpty()) media else media.filter { (cid, _) -> repo.convs[cid]?.title?.lowercase()?.contains(query) == true }
                        if (shown.isEmpty()) item { Empty("No photos in your recent chats", "Photos show here once their chat has been opened.") }
                        items(shown.chunked(3)) { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { (cid, m) ->
                                    val ref = m.media.first()
                                    val path = ref.localPath.ifEmpty { repo.media[ref.id] ?: repo.media[ref.thumbId].orEmpty() }
                                    LaunchedEffect(ref.id) { if (path.isEmpty()) repo.ensureMedia(ref, thumb = ref.thumbId.isNotEmpty()) }
                                    Box(Modifier.weight(1f).height(118.dp).clip(RoundedCornerShape(14.dp)).background(t.tone).clickable { nav.openChat(cid, m.id) }) {
                                        if (path.isNotEmpty()) coil.compose.AsyncImage(java.io.File(path), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    "Links" -> {
                        val shown = links.filter { query.isEmpty() || it.second.text.lowercase().contains(query) }
                        if (shown.isEmpty()) item { Empty("No links yet", "Links from chats you've opened show here.") }
                        items(shown, key = { "l" + it.second.key }) { (cid, m) ->
                            ResultRow(cid, detect(m.text).url ?: m.text, listTime(m.ts)) { nav.openChat(cid, m.id) }
                        }
                    }
                    else -> {
                        if (query.isEmpty()) {
                            item { SectionTitle("Recent") }
                            items(convs.filter { it.status == "active" }.sortedByDescending { it.ts }.take(8), key = { "rc" + it.id }) { c ->
                                ResultRow(c.id, c.preview, listTime(c.ts)) { nav.openChat(c.id) }
                            }
                        } else {
                            if (people.isNotEmpty()) item { SectionTitle("Conversations") }
                            items(people, key = { "p" + it.id }) { c -> ResultRow(c.id, c.number.ifEmpty { c.preview }, null) { nav.openChat(c.id) } }
                            if (hits.isNotEmpty() || previews.isNotEmpty()) item { SectionTitle("Messages") }
                            items(hits.take(60), key = { "m" + it.second.key }) { (cid, m) -> ResultRow(cid, m.text, listTime(m.ts), query) { nav.openChat(cid, m.id) } }
                            items(previews, key = { "pv" + it.id }) { c -> ResultRow(c.id, c.preview, listTime(c.ts), query) { nav.openChat(c.id) } }
                            if (people.isEmpty() && hits.isEmpty() && previews.isEmpty()) item { Empty("No results", "Search looks through names and the chats you've opened recently.") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(s: String) {
    T(s, size = 12.5.sp, weight = FontWeight.SemiBold, color = LocalTok.current.soft, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Empty(title: String, sub: String) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(Icons.search, t.p, 30.dp, 1.8f) }
        Spacer(Modifier.height(10.dp))
        T(title, size = 16.sp, weight = FontWeight.SemiBold)
        T(sub, size = 13.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp, start = 30.dp, end = 30.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ResultRow(cid: String, sub: String, meta: String?, hl: String? = null, onClick: () -> Unit) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val c = repo.convs[cid] ?: return
    val w = whoOf(c)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        WhoAvatar(w, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row { T(c.title, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f)); if (meta != null) T(meta, size = 12.sp, color = t.soft) }
            if (hl != null && sub.lowercase().contains(hl)) {
                val i = sub.lowercase().indexOf(hl)
                val start = maxOf(0, i - 24)
                val snippet = (if (start > 0) "…" else "") + sub.substring(start)
                androidx.compose.material3.Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        val j = snippet.lowercase().indexOf(hl)
                        append(snippet.substring(0, j))
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = t.p, fontWeight = FontWeight.SemiBold)); append(snippet.substring(j, j + hl.length)); pop()
                        append(snippet.substring(j + hl.length))
                    },
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Geist, fontSize = 13.5.sp, color = t.soft),
                )
            } else T(sub, size = 13.5.sp, color = t.soft, maxLines = 1)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewChatScreen(nav: Nav, group: Boolean) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Contact>() }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { repo.loadContacts() }
    LaunchedEffect(naming) { if (!naming) runCatching { fr.requestFocus() } }
    // Screenshot hooks: two people already picked, or straight to the name step.
    val demo = remember { repo.demoOpen?.takeIf { it.startsWith("group") }.also { if (it != null) repo.demoOpen = null } }
    LaunchedEffect(repo.contacts.size) {
        if (demo != null && picked.isEmpty() && repo.contacts.size >= 2) { picked.addAll(repo.contacts.filter { it.name.firstOrNull()?.isLetter() == true }.take(2)); if (demo == "group-name") naming = true }
    }
    BackHandler { if (naming) naming = false else nav.back() }

    val query = q.trim().lowercase()
    val digits = q.filter { it.isDigit() || it == '+' }
    val contacts = repo.contacts.filter { query.isEmpty() || it.name.lowercase().contains(query) || it.number.filter(Char::isDigit).contains(query.filter(Char::isDigit).ifEmpty { "~" }) }
    // Real names first; entries that are only a number go to the end, the way the phone's own list does.
    val distinctContacts = contacts.distinctBy { it.name + it.number.takeLast(7) }.sortedBy { if (it.name.firstOrNull()?.isLetter() == true) 0 else 1 }
    val typedNumber = digits.filter(Char::isDigit).length >= 3 && distinctContacts.none { it.number.filter(Char::isDigit).endsWith(digits.filter(Char::isDigit).takeLast(7)) }

    fun toggle(c: Contact) { if (picked.any { it.number == c.number }) picked.removeAll { it.number == c.number } else picked.add(c); q = "" }
    fun addTyped() { if (typedNumber) { picked.add(Contact("", "", q.trim(), digits, q.trim(), "")); q = "" } }
    fun open(numbers: List<String>, groupName: String = "") {
        if (working) return
        working = true
        scope.launch {
            repo.startChat(numbers, groupName)
                .onSuccess { c ->
                    photo?.let { f -> repo.prefs.setOpt("gphoto:" + c.id, f) }
                    nav.back(); nav.openChat(c.id)
                }
                .onFailure { repo.toast(it.message ?: "Couldn't start the chat") }
            working = false
        }
    }
    fun next() {
        addTyped()
        when {
            picked.size == 1 -> open(listOf(picked[0].number))
            picked.size > 1 -> naming = true
        }
    }

    if (naming) {
        GroupDetails(picked, name, { name = it }, photo, { photo = it }, working, onBack = { naming = false }) {
            open(picked.map { it.number }, name.trim())
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize()) {
            PageTop(if (group) "New group" else "New conversation", { nav.back() })
            Spacer(Modifier.height(12.dp))
            // One "To" field: everyone you add sits in it as a chip, and you can keep typing for more.
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(t.field)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { runCatching { fr.requestFocus() } }
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Lines up with the first row of chips, however many rows there are.
                Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) { T("To", size = 15.sp, weight = FontWeight.SemiBold, color = t.soft) }
                Spacer(Modifier.width(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    picked.forEach { c ->
                        Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(t.tone).clickable { picked.remove(c) }.padding(start = 4.dp, end = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            val (cb, ci) = toneOf(c.number, c.name)
                            Avatar(repo.avatars[c.id]?.ifEmpty { null }, initialsOf(c.name), 28.dp, cb, ci, 10.sp)
                            Spacer(Modifier.width(6.dp))
                            T(c.name.substringBefore(' ').ifBlank { c.formatted.ifBlank { c.number } }, size = 13.5.sp, weight = FontWeight.SemiBold, color = t.p, maxLines = 1)
                            Spacer(Modifier.width(6.dp))
                            Ico(Icons.close, t.p, 11.dp, 2.6f)
                        }
                    }
                    Box(Modifier.height(36.dp).widthIn(min = 72.dp).weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (q.isEmpty()) T(if (picked.isEmpty()) "Name or number" else "Anyone else?", size = 15.sp, color = t.soft)
                        BasicTextField(
                            q, { q = it }, singleLine = true, cursorBrush = SolidColor(t.p),
                            textStyle = TextStyle(fontFamily = Geist, fontSize = 15.sp, color = t.ink),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addTyped() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fr).onPreviewKeyEvent { e ->
                                // Backspace in an empty field takes off the last person.
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Backspace && q.isEmpty() && picked.isNotEmpty()) {
                                    picked.removeAt(picked.lastIndex); true
                                } else false
                            },
                        )
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp)) {
                if (typedNumber) item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).clickable { addTyped() }.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(null, "#", 44.dp, t.tone, t.p)
                        Spacer(Modifier.width(12.dp))
                        Column { T("Add $q", size = 15.sp, weight = FontWeight.SemiBold); T("Number", size = 13.sp, color = t.soft) }
                    }
                }
                item { T(if (repo.contacts.isEmpty()) (if (repo.contactsLoaded) "No contacts found. Type a number above to text it." else "Loading contacts…") else if (query.isEmpty()) "All contacts" else "Contacts", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)) }
                items(distinctContacts, key = { it.id + it.number }) { c ->
                    LaunchedEffect(c.id) { if (repo.prefs.state.showPhotos == "Show" && !c.id.startsWith("dev:")) repo.ensureAvatars(listOf(c.id)) }
                    val on = picked.any { it.number == c.number }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { toggle(c) }.padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val (cb, ci) = toneOf(c.number, c.name)
                        Avatar(repo.avatars[c.id]?.ifEmpty { null }, initialsOf(c.name), 44.dp, cb, ci)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(c.name, size = 15.5.sp, weight = FontWeight.Medium, maxLines = 1)
                            T(c.formatted.ifBlank { c.number }, size = 13.sp, color = t.soft)
                        }
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).then(if (on) Modifier.background(t.fill) else Modifier.border(2.dp, t.faint, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Ico(Icons.check, t.onFill, 13.dp, 3f) }
                    }
                }
            }
        }
        if (picked.isNotEmpty() || typedNumber) {
            PillButton(
                if (working) "Starting…" else "Continue",
                { next() },
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp).fillMaxWidth(), height = 54.dp,
            )
        }
    }
    @Suppress("UNUSED_EXPRESSION") Route.Home
}

/** Step two of a new group: a name and a photo. The photo starts as the members' faces. */
@Composable
private fun GroupDetails(
    members: List<Contact>, name: String, onName: (String) -> Unit, photo: String?, onPhoto: (String?) -> Unit,
    working: Boolean, onBack: () -> Unit, onCreate: () -> Unit,
) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) repo.cropThen(uri, "Group photo", "group-new-" + System.currentTimeMillis()) { f -> onPhoto(f) }
    }
    fun choose() = pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    val faces = members.take(3).map { c -> val (bg, ink) = toneOf(c.number, c.name); Who(repo.avatars[c.id]?.ifEmpty { null }, initialsOf(c.name), bg, ink) }
    Box(Modifier.fillMaxSize()) {
        Glow()
        Column(Modifier.fillMaxSize()) {
            PageTop("Name your group", onBack, sub = members.joinToString { it.name.substringBefore(' ').ifBlank { it.formatted.ifBlank { it.number } } })
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                Box(Modifier.size(136.dp)) {
                    Box(Modifier.size(136.dp).clip(CircleShape).clickable { choose() }) {
                        if (photo != null) Avatar(photo, "", 136.dp)
                        else WhoAvatar(Who(null, "", t.tone, t.p, stack = faces), 136.dp)
                    }
                    // The usual edit badge: a small circle on the photo's corner.
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp).size(42.dp).clip(CircleShape).background(t.bg).padding(3.dp)
                            .clip(CircleShape).background(t.fill).clickable { choose() },
                        contentAlignment = Alignment.Center,
                    ) { Ico(Icons.pencil, t.onFill, 17.dp, 2.2f) }
                }
                if (photo != null) T("Remove photo", size = 13.5.sp, weight = FontWeight.SemiBold, color = t.p,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).clickable { onPhoto(null) }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.height(if (photo != null) 14.dp else 28.dp))
                SearchField(name, onName, "Group name (optional)", prefix = "Name")
                Spacer(Modifier.height(10.dp))
                T("Everyone in the group sees the name. The photo shows in Pyng only.", size = 13.sp, color = t.soft, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        PillButton(
            if (working) "Creating…" else "Create group of ${members.size + 1}", onCreate,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp).fillMaxWidth(), height = 54.dp,
        )
    }
}
