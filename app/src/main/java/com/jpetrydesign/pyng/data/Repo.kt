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

    var onIncoming: ((Conv, Msg) -> Unit)? = null
    var onRead: ((String) -> Unit)? = null

    private val cacheFile = File(app.filesDir, "convs.json")
    private val avatarPending = mutableSetOf<String>()

    init {
        runCatching { if (paired && cacheFile.exists()) Conv.list(cacheFile.readText()).forEach { convs[it.id] = it } }
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
                    else c?.let { onIncoming?.invoke(it, m) }
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
                convs.clear(); msgs.clear()
                cacheFile.delete()
            }
            "connection" -> if (e.data.optString("state") == "ready" && convs.isEmpty()) refresh()
        }
    }

    private fun putConv(c: Conv) {
        if (c.id.isEmpty()) return
        if (c.status == "deleted") { convs.remove(c.id); return }
        val old = convs[c.id]
        convs[c.id] = c
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

    fun refresh() {
        if (loading) return
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
        if (contacts.isNotEmpty() && !force) return
        scope.launch { hub.io { it.listContacts() }.onSuccess { j -> contacts = Contact.list(j).sortedBy { it.name.lowercase() } } }
    }

    fun ensureAvatars(ids: List<String>, contactsKind: Boolean = false) {
        val need = ids.filter { it.isNotEmpty() && !avatars.containsKey(it) && it !in avatarPending }
        if (need.isEmpty()) return
        avatarPending += need
        scope.launch {
            delay(150)
            need.chunked(20).forEach { batch ->
                hub.io { it.fetchAvatars(JSONArray(batch).toString(), contactsKind) }.onSuccess { j ->
                    val o = org.json.JSONObject(j)
                    batch.forEach { id -> avatars[id] = o.optString(id) }
                }.onFailure { batch.forEach { id -> avatarPending.remove(id) } }
            }
        }
    }

    fun ensureMedia(ref: MediaRef, thumb: Boolean = false) {
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
        if (p.pinned.size >= 12) { toast("Unpin a chat first (12 max)"); return false }
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
            toast("Cache cleared")
        }
    }
}
