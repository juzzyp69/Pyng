package com.jpetrydesign.pyng.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import com.jpetrydesign.pyng.Hub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Something picked to send, already copied into app storage. */
data class Pending(val path: String, val mime: String, val name: String)

data class Toast(val text: String, val undo: (() -> Unit)? = null)

/**
 * Everything the screens show, kept in Compose state, plus every action that talks
 * to Google Messages. Lives for the whole process.
 */
class Repo(private val app: Context, val hub: Hub, val prefs: Prefs, private val scope: CoroutineScope) {

    val convs = mutableStateMapOf<String, Conv>()
    val msgs = mutableStateMapOf<String, List<Msg>>()
    val hasMore = mutableStateMapOf<String, Boolean>()
    val typing = mutableStateMapOf<String, Long>()
    val avatars = mutableStateMapOf<String, String>()
    val media = mutableStateMapOf<String, String>()
    val mediaBusy = mutableStateMapOf<String, Boolean>()
    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set
    var sims by mutableStateOf<List<SimInfo>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var paired by mutableStateOf(hub.engine.isPaired)
        private set

    /** The chat currently on screen, so it doesn't notify. */
    var activeChat: String? = null
    var foreground = false

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts
    fun toast(text: String, undo: (() -> Unit)? = null) { _toasts.tryEmit(Toast(text, undo)) }

    /** A yes/no question shown over everything, e.g. before deleting. */
    data class Ask(val title: String, val body: String, val action: String, val danger: Boolean = true, val go: () -> Unit)
    var ask by mutableStateOf<Ask?>(null)
    fun confirm(title: String, body: String, action: String = "Delete", danger: Boolean = true, go: () -> Unit) { ask = Ask(title, body, action, danger, go) }

    // Group names and photos you set in Pyng (Google Messages doesn't let linked apps change them).
    private val rawNames = HashMap<String, String>()
    private fun withLocalName(c: Conv): Conv {
        if (!c.group) return c
        val n = prefs.state.opt("gname:" + c.id)
        return if (n.isNotBlank()) c.copy(name = n) else c
    }
    fun setGroupName(convId: String, name: String) {
        prefs.setOpt("gname:$convId", name.trim())
        convs[convId]?.let { c -> convs[convId] = if (name.isBlank()) c.copy(name = rawNames[convId] ?: c.name) else c.copy(name = name.trim()) }
        saveCacheSoon(); com.jpetrydesign.pyng.widget.Widgets.refresh(app)
    }
    suspend fun setGroupPhoto(convId: String, uri: Uri?) {
        if (uri == null) { prefs.setOpt("gphoto:$convId", ""); return }
        val f = withContext(Dispatchers.IO) {
            runCatching {
                val src = app.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
                val side = minOf(src.width, src.height)
                val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                val out = android.graphics.Bitmap.createScaledBitmap(sq, minOf(side, 512), minOf(side, 512), true)
                val file = File(File(app.filesDir, "group-photos").apply { mkdirs() }, "$convId-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                file.absolutePath
            }.getOrNull()
        }
        if (f != null) { prefs.setOpt("gphoto:$convId", f); toast("Group photo updated") } else toast("Couldn't use that photo")
    }

    var onIncoming: ((Conv, Msg) -> Unit)? = null

    /** In-app banner for a message from another chat while the app is open. */
    private val _banners = MutableSharedFlow<Pair<Conv, Msg>>(extraBufferCapacity = 4)
    val banners: SharedFlow<Pair<Conv, Msg>> = _banners

    /** Sample data for automated screenshots; never touches Google Messages. */
    var demo = false
        private set
    var demoOpen: String? = null
    var onRead: ((String) -> Unit)? = null

    private val cacheFile = File(app.filesDir, "convs.json")
    private val avatarPending = mutableSetOf<String>()
    private val avatarFile = File(app.filesDir, "avatars.json")

    init {
        runCatching { if (paired && cacheFile.exists()) Conv.list(cacheFile.readText()).forEach { convs[it.id] = it } }
        // Photos found last time show straight away; everyone else is fetched once connected.
        runCatching {
            if (paired && avatarFile.exists()) {
                val o = org.json.JSONObject(avatarFile.readText())
                o.keys().forEach { k -> val v = o.optString(k); if (v.isNotEmpty() && (v.startsWith("content://") || File(v).exists())) avatars[k] = v }
            }
        }
        scope.launch { hub.events.collect { handle(it) } }
        scope.launch {
            while (true) {
                delay(2000)
                val now = System.currentTimeMillis()
                typing.entries.filter { it.value < now }.map { it.key }.forEach { typing.remove(it) }
            }
        }
    }

    // ---------- events ----------

    private fun handle(e: Hub.Event) {
        when (e.kind) {
            "message" -> {
                val m = Msg.from(e.data)
                if (m.convId.isEmpty()) return
                if (msgs.containsKey(m.convId)) upsert(m)
                if (!m.fromMe) typing.remove(m.convId)
                val c = convs[m.convId]
                if (c == null) {
                    scope.launch { hub.io { it.getConversation(m.convId) }.onSuccess { j -> putConv(Conv.from(org.json.JSONObject(j))) } }
                }
                if (!m.fromMe && !m.isSys && m.ts > System.currentTimeMillis() - 120_000) {
                    if (activeChat == m.convId && foreground) markRead(m.convId)
                    else if (foreground && c != null) {
                        val p = prefs.state
                        if (p.on("notif") && c.id !in p.muted && p.alerts[c.id]?.on != false) _banners.tryEmit(c to m)
                    } else c?.let { onIncoming?.invoke(it, m) }
                }
            }
            "conversation" -> putConv(Conv.from(e.data))
            "typing" -> {
                val id = e.data.optString("conversationId")
                if (e.data.optBoolean("typing")) typing[id] = System.currentTimeMillis() + 8000 else typing.remove(id)
            }
            "settings" -> {
                val arr = e.data.optJSONArray("sims") ?: JSONArray()
                sims = (0 until arr.length()).map { arr.getJSONObject(it) }.map { SimInfo(it.optString("name"), it.optString("number"), it.optBoolean("rcs")) }
            }
            "paired" -> { paired = true; refresh() }
            "unpaired" -> {
                paired = false
                convs.clear(); msgs.clear(); avatars.clear()
                cacheFile.delete(); avatarFile.delete()
            }
            "connection" -> if (e.data.optString("state") == "ready" && convs.isEmpty()) refresh()
        }
    }

    private fun putConv(c: Conv) {
        if (c.id.isEmpty()) return
        if (c.status == "deleted") { convs.remove(c.id); return }
        val old = convs[c.id]
        if (c.group) rawNames[c.id] = c.name
        convs[c.id] = withLocalName(c)
        if (old == null || old.avatarId != c.avatarId) ensureAvatars(c.others.map { it.id })
        if (old != null && old.unread && !c.unread) onRead?.invoke(c.id)
        saveCacheSoon()
    }

    private var saveJob: kotlinx.coroutines.Job? = null
    private fun saveCacheSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            val snapshot = convs.values.toList()
            withContext(Dispatchers.IO) {
                runCatching {
                    val arr = JSONArray()
                    snapshot.forEach { c ->
                        arr.put(org.json.JSONObject().apply {
                            put("id", c.id); put("name", c.name); put("preview", c.preview); put("fromMe", c.fromMe)
                            put("sender", c.sender); put("lastStatus", c.lastStatus); put("ts", c.ts); put("unread", c.unread)
                            put("group", c.group); put("rcs", c.rcs); put("color", c.color); put("pinned", c.pinned)
                            put("readOnly", c.readOnly); put("status", c.status); put("groupPhoto", c.groupPhoto)
                            put("people", JSONArray(c.people.map { p ->
                                org.json.JSONObject().put("id", p.id).put("number", p.number).put("formatted", p.formatted)
                                    .put("name", p.name).put("first", p.first).put("color", p.color).put("me", p.me).put("contactId", p.contactId)
                            }))
                        })
                    }
                    cacheFile.writeText(arr.toString())
                }
                com.jpetrydesign.pyng.widget.Widgets.refresh(app)
            }
        }
    }

    private fun upsert(m: Msg) {
        val list = msgs[m.convId] ?: emptyList()
        val i = list.indexOfFirst { (m.id.isNotEmpty() && it.id == m.id) || (m.tmpId.isNotEmpty() && it.tmpId == m.tmpId) }
        val next = if (i >= 0) list.toMutableList().also { it[i] = m } else list + m
        msgs[m.convId] = tidy(next)
    }

    private fun tidy(list: List<Msg>) =
        list.distinctBy { it.key }.sortedBy { if (it.ts > 0) it.ts else Long.MAX_VALUE }

    // ---------- loading ----------

    /** Finds the chat a Google Messages notification is about. */
    fun matchConversation(shortcutId: String?, title: String, sender: String): Conv? {
        shortcutId?.let { convs[it] }?.let { return it }
        val all = convs.values.filter { it.status == "active" || it.status == "archived" }
        fun digits(s: String) = s.filter(Char::isDigit).takeLast(10)
        return all.firstOrNull { it.title == title && it.group } ?: all.firstOrNull { !it.group && (it.title == title || it.title == sender) }
            ?: all.firstOrNull { !it.group && digits(title).length >= 7 && digits(it.number) == digits(title) }
            ?: all.firstOrNull { it.group && (rawNames[it.id] == title) }
    }

    /** Catches up after being asleep: newest chats, plus any chat you have open. */
    fun refreshQuietly() {
        refresh()
        activeChat?.let { if (msgs.containsKey(it)) loadMessages(it) }
        convs.values.sortedByDescending { it.ts }.take(3).forEach { c -> if (msgs.containsKey(c.id)) loadMessages(c.id) }
    }

    fun refresh() {
        if (loading || demo) return
        loading = true
        scope.launch {
            var tries = 0
            while (true) {
                val r = hub.io { e ->
                    e.start()
                    Triple(e.listConversations("inbox", 120), runCatching { e.listConversations("archive", 60) }.getOrNull(), runCatching { e.listConversations("spam", 30) }.getOrNull())
                }
                if (r.isSuccess) {
                    val (inbox, arch, spam) = r.getOrThrow()
                    val all = Conv.list(inbox) + (arch?.let { Conv.list(it) } ?: emptyList()) + (spam?.let { Conv.list(it) } ?: emptyList())
                    val ids = all.map { it.id }.toSet()
                    convs.keys.filter { it !in ids }.forEach { convs.remove(it) }
                    all.distinctBy { it.id }.forEach { putConv(it) }
                    ensureAvatars(convs.values.flatMap { c -> c.others.map { it.id } }.distinct())
                    loadError = null
                    seedPins()
                    break
                }
                loadError = r.exceptionOrNull()?.message
                if (++tries >= 5) break
                delay(1500L * tries)
            }
            loading = false
        }
    }

    private fun seedPins() {
        val p = prefs.state
        if (p.pinsSeeded) return
        val fromPhone = convs.values.filter { it.pinned }.sortedByDescending { it.ts }.map { it.id }
        val extra = if (fromPhone.isEmpty()) convs.values.filter { it.status == "active" }.sortedByDescending { it.ts }.take(4).map { it.id } else emptyList()
        prefs.edit { it.copy(pinned = (fromPhone + extra).take(8), pinsSeeded = true) }
    }

    fun loadMessages(convId: String, older: Boolean = false) {
        if (demo) { if (!msgs.containsKey(convId)) msgs[convId] = emptyList(); return }
        scope.launch {
            val cur = msgs[convId].orEmpty()
            val oldest = cur.firstOrNull { it.id.isNotEmpty() }
            val r = hub.io {
                if (older && oldest != null) it.fetchMessages(convId, 40, oldest.id, oldest.ts)
                else it.fetchMessages(convId, 40, "", 0)
            }
            r.onSuccess { json ->
                val got = Msg.list(json)
                hasMore[convId] = got.size >= 40
                val pendingLocal = cur.filter { it.id.isEmpty() }
                msgs[convId] = tidy(if (older) got + cur else got + cur.filter { c -> got.none { it.id == c.id } && c.id.isNotEmpty() && c.ts > (got.lastOrNull()?.ts ?: 0) } + pendingLocal)
            }.onFailure {
                if (!older && cur.isEmpty()) msgs[convId] = emptyList()
                toast("Couldn't load messages: ${it.message}")
            }
        }
    }

    fun loadContacts(force: Boolean = false) {
        if (demo) return
        if (contacts.isNotEmpty() && !force) return
        scope.launch { hub.io { it.listContacts() }.onSuccess { j -> contacts = Contact.list(j).sortedBy { it.name.lowercase() } } }
    }

    fun ensureAvatars(ids: List<String>, contactsKind: Boolean = false) {
        if (demo) return
        val need = ids.filter { it.isNotEmpty() && !avatars.containsKey(it) && it !in avatarPending }
        if (need.isEmpty()) return
        avatarPending += need
        scope.launch {
            delay(150)
            // Most recent chats first, a few at a time, so the visible photos land quickly.
            val order = need.sortedByDescending { id -> convs.values.filter { c -> c.others.any { it.id == id } }.maxOfOrNull { it.ts } ?: 0L }
            // First choice: the photo saved on this phone's contact card, same as Google Messages shows.
            val local = withContext(Dispatchers.IO) {
                order.associateWith { id ->
                    val person = convs.values.firstNotNullOfOrNull { c -> c.others.firstOrNull { it.id == id } }
                    person?.let { contactPhoto(it.number.ifBlank { it.formatted }) }
                }
            }
            local.forEach { (id, uri) -> if (uri != null) avatars[id] = uri }
            val rest = order.filter { local[it] == null }
            rest.chunked(6).forEach { batch ->
                hub.io { it.fetchAvatars(JSONArray(batch).toString(), contactsKind) }.onSuccess { j ->
                    val o = org.json.JSONObject(j)
                    batch.forEach { id -> avatars[id] = o.optString(id) }
                }.onFailure { batch.forEach { id -> avatarPending.remove(id) } }
            }
            saveAvatars()
        }
    }

    /** Looks up a phone number in this phone's contacts and returns its photo, if it has one. */
    private fun contactPhoto(number: String): String? {
        if (number.isBlank()) return null
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number))
            app.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup.PHOTO_URI, android.provider.ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null)?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: c.getString(1))?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private fun saveAvatars() {
        val snap = avatars.filterValues { it.isNotEmpty() }
        scope.launch(Dispatchers.IO) { runCatching { avatarFile.writeText(org.json.JSONObject(snap as Map<*, *>).toString()) } }
    }

    fun ensureMedia(ref: MediaRef, thumb: Boolean = false) {
        if (demo) return
        val id = if (thumb && ref.thumbId.isNotEmpty()) ref.thumbId else ref.id
        val key = if (thumb && ref.thumbId.isNotEmpty()) ref.thumbKey else ref.key
        if (id.isEmpty() || media.containsKey(id) || mediaBusy[id] == true) return
        mediaBusy[id] = true
        scope.launch {
            hub.io { it.downloadMedia(id, key, if (thumb) "jpg" else ref.ext) }
                .onSuccess { media[id] = it }
                .onFailure { toast("Couldn't download: ${it.message}") }
            mediaBusy.remove(id)
        }
    }

    fun autoDownloadAllowed(): Boolean = when (prefs.state.opt("autoDl")) {
        "Never" -> false
        "Wi-Fi only" -> {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        else -> true
    }

    // ---------- sending ----------

    private fun localMsg(convId: String, text: String, replyTo: String, att: Pending?): Msg = Msg(
        id = "", tmpId = "local-" + UUID.randomUUID(), convId = convId, participantId = "", sender = "", senderFirst = "",
        fromMe = true, status = "LOCAL", statusText = "", text = text,
        media = att?.let { listOf(MediaRef("", "", "", "", it.mime, it.name, File(it.path).length(), 0, 0, it.path)) } ?: emptyList(),
        ts = System.currentTimeMillis(), reactions = emptyList(), replyTo = replyTo, type = 0, kind = "text",
    )

    fun send(convId: String, text: String, replyTo: String = "", attachments: List<Pending> = emptyList()) {
        val body = text.trim()
        if (body.isEmpty() && attachments.isEmpty()) return
        val locals = attachments.mapIndexed { i, a -> localMsg(convId, if (i == attachments.lastIndex) body else "", if (i == 0) replyTo else "", a) }
            .ifEmpty { listOf(localMsg(convId, body, replyTo, null)) }
        if (msgs.containsKey(convId)) msgs[convId] = tidy(msgs[convId].orEmpty() + locals)
        locals.forEach { deliver(it) }
        prefs.edit { it.copy(drafts = it.drafts - convId, markedUnread = it.markedUnread - convId) }
    }

    private fun deliver(local: Msg) {
        if (demo) {
            scope.launch {
                delay(650)
                msgs[local.convId] = msgs[local.convId].orEmpty().map { if (it.tmpId == local.tmpId) it.copy(status = "OUTGOING_DELIVERED", id = "d" + it.tmpId) else it }
            }
            return
        }
        scope.launch {
            val att = local.media.firstOrNull()
            val r = hub.io {
                if (att != null) it.sendMedia(local.convId, att.localPath, att.mime, att.name, local.text, local.replyTo)
                else it.sendText(local.convId, local.text, local.replyTo)
            }
            val list = msgs[local.convId] ?: return@launch
            msgs[local.convId] = list.map { m ->
                if (m.tmpId != local.tmpId) m
                else r.fold({ tmp -> m.copy(tmpId = tmp, status = "OUTGOING_SENDING") }, { e -> m.copy(error = e.message ?: "Not sent") })
            }
        }
    }

    fun retry(m: Msg) {
        val list = msgs[m.convId] ?: return
        val fresh = m.copy(error = "", status = "LOCAL", tmpId = "local-" + UUID.randomUUID(), ts = System.currentTimeMillis())
        msgs[m.convId] = tidy(list.filter { it.key != m.key } + fresh)
        deliver(fresh)
    }

    fun discard(m: Msg) { msgs[m.convId] = msgs[m.convId].orEmpty().filter { it.key != m.key } }

    fun react(m: Msg, emoji: String) {
        val myId = convs[m.convId]?.me?.id.orEmpty()
        val mine = m.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }
        val remove = mine?.emoji == emoji
        val updated = m.reactions.map { r ->
            if (r.emoji == mine?.emoji) r.copy(count = r.count - 1, by = r.by - myId) else r
        }.filter { it.count > 0 }.let { rs ->
            if (remove) rs else {
                val ex = rs.firstOrNull { it.emoji == emoji }
                if (ex != null) rs.map { if (it.emoji == emoji) it.copy(count = it.count + 1, by = it.by + myId) else it }
                else rs + Reaction(emoji, 1, listOf(myId))
            }
        }
        upsert(m.copy(reactions = updated))
        scope.launch {
            if (mine != null && !remove) hub.io { it.sendReaction(m.id, mine.emoji, true) }
            hub.io { it.sendReaction(m.id, emoji, remove) }.onFailure { toast("Reaction didn't send") }
        }
    }

    fun deleteMessages(list: List<Msg>) {
        val convId = list.firstOrNull()?.convId ?: return
        val keys = list.map { it.key }.toSet()
        msgs[convId] = msgs[convId].orEmpty().filter { it.key !in keys }
        scope.launch {
            var failed = 0
            list.filter { it.id.isNotEmpty() }.forEach { m -> hub.io { it.deleteMessage(m.id) }.onFailure { failed++ } }
            if (failed > 0) { toast("$failed couldn't be deleted"); loadMessages(convId) }
        }
        prefs.edit { p -> p.copy(starred = p.starred.filter { it.msgId !in keys }) }
    }

    fun markRead(convId: String) {
        val c = convs[convId]
        if (convId in prefs.state.markedUnread) prefs.edit { it.copy(markedUnread = it.markedUnread - convId) }
        onRead?.invoke(convId)
        if (c == null || !c.unread) return
        convs[convId] = c.copy(unread = false)
        val last = msgs[convId]?.lastOrNull { !it.fromMe && it.id.isNotEmpty() }
        scope.launch {
            val id = last?.id ?: hub.io { e -> Msg.list(e.fetchMessages(convId, 1, "", 0)).lastOrNull()?.id }.getOrNull() ?: return@launch
            hub.io { it.markRead(convId, id) }
        }
    }

    private var lastTyping = 0L
    fun typingPing(convId: String) {
        if (!prefs.state.on("typing")) return
        val now = System.currentTimeMillis()
        if (now - lastTyping < 4000) return
        lastTyping = now
        scope.launch { hub.io { it.setTyping(convId) } }
    }

    // ---------- conversation actions ----------

    fun setArchived(convId: String, on: Boolean) {
        convs[convId]?.let { convs[convId] = it.copy(status = if (on) "archived" else "active") }
        if (on) prefs.edit { it.copy(pinned = it.pinned - convId) }
        scope.launch { hub.io { it.setArchived(convId, on) }.onFailure { e -> toast("Couldn't update Google Messages: ${e.message}") } }
    }

    fun setMuted(convId: String, on: Boolean) {
        prefs.edit { it.copy(muted = if (on) it.muted + convId else it.muted - convId) }
        scope.launch { hub.io { it.setMuted(convId, on) } }
    }

    fun togglePin(convId: String): Boolean {
        val p = prefs.state
        if (p.isPinned(convId)) { prefs.edit { it.copy(pinned = it.pinned - convId) }; return true }
        if (p.pinned.size >= 8) { toast("Unpin a chat first (8 max)"); return false }
        prefs.edit { it.copy(pinned = it.pinned + convId) }
        if (convs[convId]?.status == "archived") setArchived(convId, false)
        return true
    }

    fun toggleUnread(convId: String) {
        val c = convs[convId] ?: return
        val isUnread = c.unread || convId in prefs.state.markedUnread
        if (isUnread) markRead(convId) else prefs.edit { it.copy(markedUnread = it.markedUnread + convId) }
    }

    fun deleteConversation(convId: String) {
        val removed = convs.remove(convId)
        msgs.remove(convId)
        prefs.edit { it.copy(pinned = it.pinned - convId) }
        scope.launch {
            hub.io { it.deleteConversation(convId) }.onFailure { e ->
                removed?.let { convs[convId] = it }
                toast("Couldn't delete: ${e.message}")
            }
        }
    }

    fun setBlocked(convId: String, on: Boolean, report: Boolean = false) {
        convs[convId]?.let { convs[convId] = it.copy(status = if (on) "blocked" else "active") }
        if (on) prefs.edit { it.copy(pinned = it.pinned - convId) }
        scope.launch { hub.io { it.setBlocked(convId, on, report) }.onFailure { e -> toast("Couldn't update Google Messages: ${e.message}") } }
    }

    suspend fun startChat(numbers: List<String>, groupName: String = ""): Result<Conv> =
        hub.io { it.startChat(JSONArray(numbers).toString(), groupName) }.map { Conv.from(org.json.JSONObject(it)) }.onSuccess { putConv(it) }

    fun unpair() { scope.launch { hub.io { it.unpair() } } }

    // ---------- files ----------

    /** Copies a picked item into app storage, shrinking big photos per the quality setting. */
    suspend fun importUri(uri: Uri): Pending? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = app.contentResolver
            var mime = cr.getType(uri) ?: "application/octet-stream"
            var name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: (uri.lastPathSegment ?: "file")
            if (mime == "application/octet-stream") {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())?.let { mime = it }
            }
            val dir = File(app.cacheDir, "outgoing").apply { mkdirs() }
            val out = File(dir, UUID.randomUUID().toString() + "-" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            cr.openInputStream(uri)!!.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            val q = prefs.state.opt("quality")
            if ((mime == "image/jpeg" || mime == "image/png" || mime == "image/heic" || mime == "image/webp") && q != "Original") {
                val max = if (q == "Data saver") 1280 else 2304
                shrink(out, max, if (q == "Data saver") 72 else 86)?.let { shrunk ->
                    out.delete()
                    mime = "image/jpeg"
                    name = name.substringBeforeLast('.') + ".jpg"
                    return@runCatching Pending(shrunk.path, mime, name)
                }
            }
            Pending(out.path, mime, name)
        }.getOrNull()
    }

    private fun shrink(f: File, max: Int, quality: Int): File? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        val m = android.graphics.Matrix().apply { postScale(scale, scale); postRotate(rot) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val dest = File(f.parentFile, UUID.randomUUID().toString() + ".jpg")
        FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        dest
    }.getOrNull()

    /** A file in permanent app storage, for wallpapers and your profile photo. */
    fun keepFile(prefix: String): File = File(File(app.filesDir, "keep").apply { mkdirs() }, prefix + "-" + System.currentTimeMillis() + ".jpg")

    fun newFile(ext: String): File = File(File(app.cacheDir, "outgoing").apply { mkdirs() }, UUID.randomUUID().toString() + "." + ext)

    fun cacheBytes(): Long = runCatching { hub.engine.cacheSize() }.getOrDefault(0L) + (File(app.cacheDir, "outgoing").listFiles()?.sumOf { it.length() } ?: 0L)

    fun clearCache() {
        scope.launch {
            hub.io { it.clearCache() }
            withContext(Dispatchers.IO) { File(app.cacheDir, "outgoing").deleteRecursively() }
            media.clear()
            avatars.clear(); avatarPending.clear()
            withContext(Dispatchers.IO) { avatarFile.delete() }
            toast("Cache cleared")
            ensureAvatars(convs.values.flatMap { c -> c.others.map { it.id } }.distinct())
        }
    }

    // ---------- sample data ----------

    fun loadDemo() {
        demo = true
        paired = true
        convs.clear(); msgs.clear()
        val now = System.currentTimeMillis()
        val min = 60_000L
        fun person(id: String, name: String, num: String, known: Boolean = true) =
            Person(id, num, num, name, name.substringBefore(' '), "", false, if (known) "c$id" else "")
        val me = Person("me", "", "", "You", "You", "", true, "")
        fun conv(id: String, name: String, people: List<Person>, preview: String, agoMin: Long, unread: Boolean = false, fromMe: Boolean = false, group: Boolean = false, rcs: Boolean = true, status: String = "active", read: Boolean = false) =
            Conv(id, name, preview, fromMe, if (group) people.first().name else "", if (read) "OUTGOING_DISPLAYED" else "OUTGOING_DELIVERED",
                now - agoMin * min, unread, group, rcs, "", false, false, status, people + me, "")
        val jordan = person("p1", "Jordan Lee", "(720) 555-0193")
        val ava = person("p2", "Ava Morales", "(720) 555-0177")
        val marcus = person("p3", "Marcus Reed", "(303) 555-0119")
        val nina = person("p4", "Nina Kask", "(303) 555-0164")
        val mom = person("p5", "Mom", "(720) 555-0142")
        val list = listOf(
            conv("c1", "Jordan Lee", listOf(jordan), "Sending the files over now", 8, unread = true),
            conv("c2", "Mom", listOf(mom), "Dinner Sunday at 5:00 PM? Bring the good salad", 20, unread = true),
            conv("c3", "Design crew", listOf(marcus, nina, ava), "The pinned grid looks so clean", 45, group = true),
            conv("c4", "Ava Morales", listOf(ava), "Yes!! Can not wait", 70, fromMe = false),
            conv("c5", "227767", listOf(person("p6", "227767", "227767", false)), "Your verification code is 118895. It expires in 10 minutes.", 90, unread = true, rcs = false),
            conv("c6", "Priya Shah", listOf(person("p7", "Priya Shah", "(303) 555-0185")), "Sounds good, see you then", 130, fromMe = true, read = true),
            conv("c7", "Marcus Reed", listOf(marcus), "Game night Friday at 7:00 PM?", 600),
            conv("c8", "(855) 410-2291", listOf(person("p8", "", "(855) 410-2291", false)), "USPS: Your package is on hold due to an unpaid \$1.95 redelivery fee. Pay now: https://usps-redelivery-help.top/track", 700, rcs = false),
            conv("c9", "Nina Kask", listOf(nina), "Look who claimed my spot on the couch", 1500),
            conv("c10", "Chris Novak", listOf(person("p9", "Chris Novak", "(720) 555-0156")), "Haha that is perfect", 3000, status = "archived"),
        )
        list.forEach { convs[it.id] = it }
        var n = 0
        fun msg(c: String, fromMe: Boolean, text: String, agoMin: Long, who: Person? = null, status: String = if (fromMe) "OUTGOING_DISPLAYED" else "INCOMING_COMPLETE", reactions: List<Reaction> = emptyList(), reply: String = "") =
            Msg("m${++n}", "", c, who?.id ?: if (fromMe) "me" else "", who?.name ?: "", who?.first ?: "", fromMe, status, "", text, emptyList(), now - agoMin * min, reactions, reply, 4, "text")
        msgs["c1"] = listOf(
            msg("c1", false, "Hey! Did you get a chance to look at the new layouts?", 70),
            msg("c1", false, "No rush at all", 69),
            msg("c1", true, "Yes! The second one feels so much cleaner", 60),
            msg("c1", true, "Can you send the final exports when you get a sec?", 59),
            msg("c1", false, "On it 👍", 30, reactions = listOf(Reaction("❤️", 1, listOf("me")))),
            msg("c1", false, "Sending the files over now, here is the link too: https://github.com/juzzyp69/Pyng", 9),
            msg("c1", false, "🔥🔥", 8),
        )
        msgs["c3"] = listOf(
            msg("c3", false, "New round of comps is up", 80, marcus),
            msg("c3", false, "The pinned grid looks so clean", 60, nina, reactions = listOf(Reaction("🔥", 2, listOf("p3", "p2")))),
            msg("c3", false, "Agreed, ship it", 55, ava),
            msg("c3", true, "Pushing the update tonight", 50, reply = "m${n - 1}"),
            msg("c3", false, "Saturday at 9:00 AM works for everyone?", 45, marcus),
        )
        msgs["c2"] = listOf(
            msg("c2", false, "Did you make it home ok?", 1500),
            msg("c2", true, "Yep! Just got in", 1490),
            msg("c2", false, "Call me when you are free", 25),
            msg("c2", false, "Dinner Sunday at 5:00 PM? Bring the good salad", 20),
        )
        msgs["c8"] = listOf(msg("c8", false, convs["c8"]!!.preview, 700))
        typing["c4"] = now + 600_000
        contacts = listOf(jordan, ava, marcus, nina, mom).map { Contact(it.id, it.contactId, it.name, it.number, it.formatted, "") }
        prefs.edit { it.copy(pinned = listOf("c2", "c4", "c3", "c7", "c9", "c6"), pinsSeeded = true, setupDone = true, profileName = "Justin Petry") }
    }
}
