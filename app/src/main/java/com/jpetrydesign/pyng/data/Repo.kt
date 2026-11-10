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
import kotlinx.coroutines.sync.withPermit
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
    /** Attachments that still failed after a few tries; the bubble offers a retry instead of spinning forever. */
    val mediaFailed = mutableStateMapOf<String, Boolean>()
    /** Goes up each time the connection comes back, so waiting attachments try again. */
    val connEpoch = androidx.compose.runtime.mutableIntStateOf(0)
    /** Previews Google left blank (a photo, a voice note…), filled in from the newest message. */
    val previewFill = mutableStateMapOf<String, String>()
    private val previewAsked = HashSet<String>()
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
    data class Ask(val title: String, val body: String, val action: String, val danger: Boolean = true, val onCancel: (() -> Unit)? = null, val go: () -> Unit)
    var ask by mutableStateOf<Ask?>(null)
    fun confirm(title: String, body: String, action: String = "Delete", danger: Boolean = true, onCancel: (() -> Unit)? = null, go: () -> Unit) { ask = Ask(title, body, action, danger, onCancel, go) }

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
        saveCacheSoon(); scope.launch { com.jpetrydesign.pyng.widget.Widgets.refresh(app) }
    }
    /** A photo waiting in the crop screen (group, contact or profile photo). */
    var crop by mutableStateOf<com.jpetrydesign.pyng.ui.screens.CropRequest?>(null)

    /** Saves a cropped photo and returns where it went (a new file each time, so it never shows stale). */
    suspend fun savePhoto(name: String, bmp: android.graphics.Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(app.filesDir, "photos").apply { mkdirs() }
            dir.listFiles { f -> f.name.startsWith("$name-") }?.forEach { it.delete() }
            val file = File(dir, "$name-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }

    /** Opens the crop screen for a picked photo, then saves it as a group, contact or profile photo. */
    fun cropThen(uri: Uri, title: String, name: String, done: (String) -> Unit) {
        crop = com.jpetrydesign.pyng.ui.screens.CropRequest(uri, title) { bmp ->
            scope.launch { val f = savePhoto(name, bmp); if (f != null) done(f) else toast("Couldn't use that photo") }
        }
    }

    fun setGroupPhoto(convId: String, uri: Uri?) {
        if (uri == null) { prefs.setOpt("gphoto:$convId", ""); return }
        cropThen(uri, "Group photo", "group-" + convId.filter(Char::isLetterOrDigit)) { f -> prefs.setOpt("gphoto:$convId", f); toast("Group photo updated") }
    }

    /** A photo for someone that shows in Pyng only (their contact card stays as it is). */
    fun setContactPhoto(personId: String, uri: Uri?) {
        if (uri == null) { prefs.setOpt("cphoto:$personId", ""); toast("Photo removed"); return }
        cropThen(uri, "Contact photo", "contact-" + personId.filter(Char::isLetterOrDigit)) { f -> prefs.setOpt("cphoto:$personId", f); toast("Photo updated") }
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
                if (m.unsupported) android.util.Log.w("Pyng", "Message " + m.id + " has " + m.unknownParts + " part(s) Pyng can't read yet")
                if (!m.isSys && m.summary.isNotEmpty() && m.ts >= (convs[m.convId]?.ts ?: 0)) previewFill[m.convId] = m.summary
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
            "connection" -> if (e.data.optString("state") == "ready") {
                if (convs.isEmpty()) refresh()
                // Back online: anything that failed gets another go.
                mediaFailed.clear(); connEpoch.intValue++
            }
        }
    }

    private fun putConv(c: Conv) {
        if (c.id.isEmpty()) return
        if (c.status == "deleted") { convs.remove(c.id); return }
        val old = convs[c.id]
        if (c.group) rawNames[c.id] = c.name
        convs[c.id] = withLocalName(c)
        fillPreview(c)
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
                            put("readOnly", c.readOnly); put("status", c.status); put("groupPhoto", c.groupPhoto); put("smsOnly", c.smsOnly)
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

    /** Google's temporary id for a message → the id this app gave it when you tapped send. */
    private val tmpAlias = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Files you sent, by the app's own temporary id. Google's copy of the message only names its upload, so this keeps your photo on the bubble. */
    private val sentFiles = java.util.concurrent.ConcurrentHashMap<String, MediaRef>()

    /**
     * Google's first copy of a photo you send can arrive before the attachment is attached to it,
     * so it would show as an empty (hidden) message. Until Google's copy carries the photo, the
     * bubble keeps yours; once it does, it still shows your local file instead of downloading it back.
     */
    private fun aliased(m: Msg): Msg {
        val a = tmpAlias[m.tmpId]?.let { m.copy(tmpId = it) } ?: m
        val mine = sentFiles[a.tmpId] ?: return a
        if (!File(mine.localPath).exists()) return a
        val first = a.media.firstOrNull() ?: return a.copy(media = listOf(mine))
        if (first.localPath.isNotEmpty()) return a
        if (first.id.isNotEmpty()) media[first.id] = mine.localPath
        return a.copy(media = listOf(first.copy(localPath = mine.localPath)) + a.media.drop(1))
    }

    private fun upsert(raw: Msg) {
        val m = aliased(raw)
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
                val got = Msg.list(json).map { aliased(it) }
                hasMore[convId] = got.size >= 40
                val pendingLocal = cur.filter { it.id.isEmpty() && got.none { g -> g.tmpId == it.tmpId } }
                msgs[convId] = tidy(if (older) got + cur else got + cur.filter { c -> got.none { it.id == c.id } && c.id.isNotEmpty() && c.ts > (got.lastOrNull()?.ts ?: 0) } + pendingLocal)
            }.onFailure {
                if (!older && cur.isEmpty()) msgs[convId] = emptyList()
                toast("Couldn't load messages: ${it.message}")
            }
        }
    }

    var contactsLoaded by mutableStateOf(false)

    /** Your phone's own contacts first (instant), merged with the ones Google Messages knows. */
    fun loadContacts(force: Boolean = false) {
        if (demo) return
        if (contacts.isNotEmpty() && !force) return
        scope.launch {
            val phone = withContext(Dispatchers.IO) { deviceContacts() }
            if (phone.isNotEmpty()) contacts = mergeContacts(phone, contacts)
            val google = hub.io { Contact.list(it.listContacts()) }.getOrDefault(emptyList())
            contacts = mergeContacts(contacts, google)
            contactsLoaded = true
        }
    }

    private fun mergeContacts(a: List<Contact>, b: List<Contact>) =
        (a + b).filter { it.number.isNotBlank() }
            .distinctBy { it.name.trim().lowercase() + "|" + it.number.filter(Char::isDigit).takeLast(10) }
            .sortedBy { it.name.lowercase() }

    private fun deviceContacts(): List<Contact> = runCatching {
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val P = android.provider.ContactsContract.CommonDataKinds.Phone::class.java
        val out = ArrayList<Contact>()
        app.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val raw = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val id = c.getLong(0)
                out += Contact("dev:$id", id.toString(), name, c.getString(3)?.takeIf { it.isNotBlank() } ?: raw, raw, "")
            }
        }
        @Suppress("UNUSED_VARIABLE") val unused = P
        out
    }.getOrDefault(emptyList())

    private var avatarsUpgraded = false

    /**
     * Once per launch: anyone showing Google's small thumbnail gets their phone contact's
     * full-size photo instead, if their card has one (it may have been added since).
     */
    private fun upgradeAvatars() {
        if (avatarsUpgraded) return
        avatarsUpgraded = true
        scope.launch {
            delay(2500)
            val swaps = withContext(Dispatchers.IO) {
                avatars.toMap().filter { (_, v) -> !v.startsWith("content://") }.keys.mapNotNull { id ->
                    val person = convs.values.firstNotNullOfOrNull { c -> c.others.firstOrNull { it.id == id } } ?: return@mapNotNull null
                    contactPhoto(person.number.ifBlank { person.formatted })?.let { id to it }
                }
            }
            if (swaps.isNotEmpty()) { swaps.forEach { (id, uri) -> avatars[id] = uri }; saveAvatars() }
        }
    }

    fun ensureAvatars(ids: List<String>, contactsKind: Boolean = false) {
        if (demo) return
        upgradeAvatars()
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

    /** What a chat row should say: Google's preview, or a filled-in one when Google's was blank. */
    fun previewOf(c: Conv): String = c.preview.ifBlank { previewFill[c.id].orEmpty() }

    private fun fillPreview(c: Conv) {
        if (demo || c.preview.isNotBlank()) return
        if (!previewAsked.add(c.id + ":" + c.ts)) return
        msgs[c.id]?.lastOrNull { it.visible && !it.isSys }?.let { previewFill[c.id] = it.summary; return }
        scope.launch {
            hub.io { it.fetchMessages(c.id, 3, "", 0) }.onSuccess { j ->
                Msg.list(j).lastOrNull { it.visible && !it.isSys }?.let { previewFill[c.id] = it.summary }
            }
        }
    }

    /** Up to 3 downloads at once; the newest messages ask first, so they load first. */
    private val dlGate = kotlinx.coroutines.sync.Semaphore(4)

    /**
     * Downloads an attachment. Waits for the connection to be ready first, gives each try a time
     * limit, and retries twice. If it still fails, the bubble shows a retry button instead of
     * spinning forever; [manual] is a tap on that button.
     */
    /**
     * Gets an attachment onto the phone. Mirrors how the Google Messages bridge does it: download
     * the full file; if only a small preview exists (or the full one fails), show the preview and
     * ask your phone to upload the full-size file, then try again once it has.
     */
    fun ensureMedia(ref: MediaRef, thumb: Boolean = false, manual: Boolean = false) {
        if (demo || ref.localPath.isNotEmpty()) return
        if (!thumb && ref.id.isEmpty()) {
            // Only a preview so far.
            if (ref.thumbId.isNotEmpty()) { ensureMedia(ref, thumb = true, manual = manual); askFull(ref) }
            return
        }
        val id = if (thumb) ref.thumbId else ref.id
        val key = if (thumb) ref.thumbKey else ref.key
        if (id.isEmpty() || media.containsKey(id) || mediaBusy[id] == true) return
        if (mediaFailed[id] == true && !manual) return
        mediaFailed.remove(id)
        mediaBusy[id] = true
        scope.launch {
            try {
                dlGate.withPermit {
                    kotlinx.coroutines.withTimeoutOrNull(15_000) { while (!hub.engine.isConnected) delay(300) }
                    var tries = 0
                    while (true) {
                        val r = hub.io { it.downloadMedia(id, key, if (thumb) "jpg" else ref.ext) }
                        val path = r.getOrNull().orEmpty()
                        if (path.isNotEmpty()) { media[id] = path; break }
                        tries++
                        val why = r.exceptionOrNull()?.message.orEmpty()
                        // A 4xx means Google doesn't have the file; trying again right away won't help.
                        if (tries >= 2 || Regex("answered 4\\d\\d").containsMatchIn(why)) {
                            mediaFailed[id] = true
                            hub.io { it.logNote("Couldn't get " + (if (thumb) "preview" else ref.kindLabel.lowercase()) + " after " + tries + " tries: " + why.take(160)) }
                            if (!thumb) {
                                if (ref.thumbId.isNotEmpty()) ensureMedia(ref, thumb = true)
                                askFull(ref)
                            }
                            if (manual) toast("Couldn't download it yet. Pyng asked your phone for it, so try again in a moment.")
                            break
                        }
                        delay(1500)
                    }
                }
            } finally {
                mediaBusy.remove(id)
            }
        }
    }

    private val fullAsked = HashSet<String>()

    /** Asks your phone to upload the full-size file, then retries the download a couple of times. */
    private fun askFull(ref: MediaRef) {
        if (ref.msgId.isEmpty() || ref.part.isEmpty() || !fullAsked.add(ref.msgId + "|" + ref.part)) return
        scope.launch {
            val ok = hub.io { it.requestFullMedia(ref.msgId, ref.part) }.isSuccess
            if (!ok || ref.id.isEmpty()) return@launch // a new id arrives with the message update
            for (wait in listOf(6_000L, 20_000L)) {
                delay(wait)
                if (media.containsKey(ref.id)) return@launch
                mediaFailed.remove(ref.id)
                ensureMedia(ref)
            }
        }
    }

    suspend fun watchPending(convId: String) {
        repeat(40) {
            delay(3000)
            if (msgs[convId].orEmpty().none { it.awaitingMedia }) return
            loadMessages(convId)
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

    private fun localMsg(convId: String, text: String, replyTo: String, att: Pending?, order: Int = 0): Msg = Msg(
        id = "", tmpId = "local-" + UUID.randomUUID(), convId = convId, participantId = "", sender = "", senderFirst = "",
        fromMe = true, status = "LOCAL", statusText = "", text = text,
        media = att?.let { listOf(MediaRef("", "", "", "", it.mime, it.name, File(it.path).length(), 0, 0, it.path)) } ?: emptyList(),
        ts = System.currentTimeMillis() + order, reactions = emptyList(), replyTo = replyTo, type = 0, kind = "text",
    )

    /** One queue per chat, so what you send arrives in the order you sent it. */
    private val sendLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    fun send(convId: String, text: String, replyTo: String = "", attachments: List<Pending> = emptyList()) {
        val body = text.trim()
        if (body.isEmpty() && attachments.isEmpty()) return
        // Photos and videos first, in the order you picked them, then the text underneath them.
        val pics = attachments.mapIndexed { i, a -> localMsg(convId, "", if (i == 0 && body.isEmpty()) replyTo else "", a, order = i) }
        val note = if (body.isNotEmpty()) listOf(localMsg(convId, body, replyTo, null, order = attachments.size)) else emptyList()
        val locals = pics + note
        if (msgs.containsKey(convId)) msgs[convId] = tidy(msgs[convId].orEmpty() + locals)
        locals.forEach { l -> l.media.firstOrNull()?.takeIf { it.localPath.isNotEmpty() }?.let { sentFiles[l.tmpId] = it } }
        if (demo) locals.forEach { deliver(it) }
        else scope.launch { locals.forEach { deliverNow(it) } }
        prefs.edit { it.copy(drafts = it.drafts - convId, markedUnread = it.markedUnread - convId) }
    }

    private fun deliver(local: Msg) {
        if (demo) {
            // Plays out what Google does: a temporary id of its own first, then the confirmed message.
            scope.launch {
                delay(250)
                val eng = "eng-" + local.tmpId.takeLast(8)
                tmpAlias[eng] = local.tmpId
                delay(400)
                upsert(local.copy(tmpId = eng, id = "d" + local.tmpId, status = "OUTGOING_DELIVERED"))
            }
            return
        }
        scope.launch { deliverNow(local) }
    }

    /** Sends one message, waiting its turn behind anything else going to the same chat. */
    private suspend fun deliverNow(local: Msg) {
        if (demo) { deliver(local); return }
        val lock = sendLocks.getOrPut(local.convId) { kotlinx.coroutines.sync.Mutex() }
        lock.lock()
        try {
            val att = local.media.firstOrNull()
            val r = hub.io {
                if (att != null) it.sendMedia(local.convId, att.localPath, att.mime, att.name, local.text, local.replyTo)
                else it.sendText(local.convId, local.text, local.replyTo)
            }
            val list = msgs[local.convId] ?: return
            msgs[local.convId] = list.map { m ->
                if (m.tmpId != local.tmpId) m
                // Keep the app's own temporary id so the bubble keeps its place; remember Google's to match its echo.
                else r.fold({ tmp -> if (tmp.isNotEmpty()) tmpAlias[tmp] = m.tmpId; m.copy(status = "OUTGOING_SENDING") }, { e -> m.copy(error = e.message ?: "Not sent") })
            }
        } finally { lock.unlock() }
    }

    fun retry(m: Msg) {
        val list = msgs[m.convId] ?: return
        val fresh = m.copy(error = "", status = "LOCAL", tmpId = "local-" + UUID.randomUUID(), ts = System.currentTimeMillis())
        fresh.media.firstOrNull()?.takeIf { it.localPath.isNotEmpty() }?.let { sentFiles[fresh.tmpId] = it }
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

    /**
     * Sample chats with real-looking people, for promo screenshots (adb only). Photos come from
     * the app's own external files folder (`promo/f00.jpg`…), pushed there by the screenshot script.
     */
    fun loadPromo(dir: File) {
        loadDemo()
        convs.clear(); msgs.clear(); typing.clear(); avatars.clear()
        // Everything is timed as if it's 9:41 this morning, to match the screenshots' clock.
        val now = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 41); set(java.util.Calendar.SECOND, 0) }.timeInMillis
        val min = 60_000L
        fun face(i: Int) = File(dir, "f%02d.jpg".format(i)).takeIf { it.exists() }?.path.orEmpty()
        fun person(id: String, name: String, num: String, faceIx: Int = -1): Person {
            if (faceIx >= 0) face(faceIx).takeIf { it.isNotEmpty() }?.let { avatars[id] = it }
            return Person(id, num, num, name, name.substringBefore(' '), "", false, "c$id")
        }
        val me = Person("me", "", "", "You", "You", "", true, "")
        fun conv(id: String, name: String, people: List<Person>, preview: String, agoMin: Long, unread: Boolean = false, fromMe: Boolean = false, group: Boolean = false, rcs: Boolean = true, read: Boolean = false) =
            Conv(id, name, preview, fromMe, if (group) people.first().name else "", if (read) "OUTGOING_DISPLAYED" else "OUTGOING_DELIVERED",
                now - agoMin * min, unread, group, rcs, "", false, false, "active", people + me, "")
        var n = 0
        fun msg(c: String, fromMe: Boolean, text: String, agoMin: Long, who: Person? = null, reactions: List<Reaction> = emptyList(), media: List<MediaRef> = emptyList(), reply: String = "") =
            Msg("pm${++n}", "", c, who?.id ?: if (fromMe) "me" else "", who?.name ?: "", who?.first ?: "", fromMe,
                if (fromMe) "OUTGOING_DISPLAYED" else "INCOMING_COMPLETE", "", text, media, now - agoMin * min, reactions, reply, 4, "text")
        fun photo(name: String, w: Int, h: Int): List<MediaRef> =
            File(dir, name).takeIf { it.exists() }?.let { listOf(MediaRef("promo-$name", "", "", "", "image/jpeg", name, it.length(), w, h, it.path)) } ?: emptyList()

        val maya = person("q1", "Maya Chen", "(720) 555-0131", 5)
        val jordan = person("q2", "Jordan Brooks", "(720) 555-0148", 4)
        val ava = person("q3", "Ava Morales", "(303) 555-0177", 1)
        val marcus = person("q4", "Marcus Reed", "(303) 555-0119", 13)
        val grace = person("q5", "Grace Okafor", "(720) 555-0162", 7)
        val leo = person("q6", "Leo Carter", "(303) 555-0154", 15)
        val sofia = person("q7", "Sofia Rossi", "(720) 555-0186", 26)
        val mom = person("q8", "Mom", "(720) 555-0142", 3)
        val ethan = person("q9", "Ethan Wright", "(303) 555-0125", 27)
        val chloe = person("q10", "Chloe Bennett", "(720) 555-0109", 18)
        val daniel = person("q11", "Daniel Price", "(303) 555-0193", 11)
        val zara = person("q12", "Zara Ali", "(720) 555-0170", 32)
        val dad = person("q13", "Dad", "(720) 555-0143", 6)
        val noah = person("q14", "Noah Fischer", "(303) 555-0137", 21)
        val isla = person("q15", "Isla Grant", "(720) 555-0158", 9)
        val andre = person("q16", "Andre Thompson", "(303) 555-0111", 0)
        val amara = person("q17", "Amara Johnson", "(720) 555-0174", 16)
        val tasha = person("q18", "Tasha Green", "(303) 555-0166", 22)
        val kenji = person("q19", "Ruby Nakamura", "(720) 555-0115", 24)

        val list = listOf(
            conv("g1", "Maya Chen", listOf(maya), "Look who's coming Saturday 😄", 2, unread = true),
            conv("g2", "Jordan Brooks", listOf(jordan), "Down for tacos tonight?", 9, unread = true),
            conv("g3", "Weekend crew", listOf(tasha, ethan, kenji, andre), "Ruby: I'll bring the speaker 🔊", 14, group = true, unread = true),
            conv("g4", "Ava Morales", listOf(ava), "You're the best, thank you!!", 26, fromMe = false),
            conv("g5", "Mom", listOf(mom), "Call me when you land ❤️", 41),
            conv("g6", "Marcus Reed", listOf(marcus), "Game night Friday at 7?", 55, fromMe = true, read = true),
            conv("g7", "Grace Okafor", listOf(grace), "That color palette is perfect", 80),
            conv("g8", "Leo Carter", listOf(leo), "Haha no way 😂", 120, fromMe = true, read = true),
            conv("g9", "Sofia Rossi", listOf(sofia), "Photos from the gallery opening", 150),
            conv("g10", "Ethan Wright", listOf(ethan), "Sent you the invite", 190, fromMe = true, read = true),
            conv("g11", "Chloe Bennett", listOf(chloe), "Coffee tomorrow? ☕", 240, unread = true),
            conv("g12", "Daniel Price", listOf(daniel), "The new build looks amazing", 320),
            conv("g13", "Zara Ali", listOf(zara), "Yesss, count me in", 600),
            conv("g14", "Dad", listOf(dad), "Proud of you kiddo 👏", 900),
            conv("g15", "Noah Fischer", listOf(noah), "Sounds like a plan", 1300),
            conv("g16", "Isla Grant", listOf(isla), "Happy birthday!! 🎉", 1700),
            conv("g17", "Andre Thompson", listOf(andre), "Let's run it back next week", 2300),
            conv("g18", "Amara Johnson", listOf(amara), "See you there!", 3000),
        )
        list.forEach { convs[it.id] = it }
        File(dir, "group.jpg").takeIf { it.exists() }?.let { prefs.setOpt("gphoto:g3", it.path) }

        msgs["g1"] = listOf(
            msg("g1", false, "Are we still on for Saturday? 🏔️", 95, maya),
            msg("g1", true, "Yes! Leaving at 7. I'll grab coffee on the way", 92),
            msg("g1", false, "You're a legend", 91, maya, reactions = listOf(Reaction("❤️", 1, listOf("me")))),
            msg("g1", true, "Is everyone still coming?", 12),
            msg("g1", false, "", 3, maya, media = photo("strip.jpg", 1050, 350)),
            msg("g1", false, "Look who's coming Saturday 😄", 2, maya, reactions = listOf(Reaction("🔥", 1, listOf("me")))),
        )
        msgs["g3"] = listOf(
            msg("g3", false, "Okay who's driving Saturday?", 60, tasha),
            msg("g3", false, "I can take 4 people 🚗", 58, ethan),
            msg("g3", true, "I'm in! I'll bring snacks", 55),
            msg("g3", false, "", 20, andre, media = photo("collage.jpg", 700, 700)),
            msg("g3", false, "Last trip was too good", 19, andre, reactions = listOf(Reaction("😂", 2, listOf("q18", "me")))),
            msg("g3", false, "I'll bring the speaker 🔊", 14, kenji),
        )
        msgs["g2"] = listOf(
            msg("g2", false, "Down for tacos tonight?", 9, jordan),
        )
        msgs["g5"] = listOf(
            msg("g5", true, "Boarding now ✈️", 50),
            msg("g5", false, "Call me when you land ❤️", 41, mom),
        )
        typing["g4"] = System.currentTimeMillis() + 600_000
        contacts = listOf(maya, jordan, ava, marcus, grace, leo, sofia, mom, ethan, chloe, daniel, zara, dad, noah, isla, andre, amara)
            .map { Contact(it.id, it.contactId, it.name, it.number, it.formatted, "") }
        prefs.edit { it.copy(pinned = listOf("g1", "g2", "g3", "g4", "g5", "g6", "g7", "g9"), pinsSeeded = true, setupDone = true, profileName = "Justin Petry") }
    }

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
            msg("c3", false, "", 44, nina, status = "INCOMING_AUTO_DOWNLOADING"),
        )
        msgs["c2"] = listOf(
            msg("c2", false, "Did you make it home ok?", 1500),
            msg("c2", true, "Yep! Just got in", 1490),
            msg("c2", false, "Call me when you are free", 25),
            msg("c2", false, "Dinner Sunday at 5:00 PM? Bring the good salad", 20),
        )
        msgs["c8"] = listOf(msg("c8", false, convs["c8"]!!.preview, 700))
        // A sample photo and a shared contact, so the photo viewer and contact card can be checked.
        runCatching {
            val dir = File(app.cacheDir, "demo").apply { mkdirs() }
            val pic = File(dir, "couch.jpg")
            if (!pic.exists()) {
                val bmp = android.graphics.Bitmap.createBitmap(900, 1200, android.graphics.Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bmp)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.shader = android.graphics.LinearGradient(0f, 0f, 900f, 1200f, 0xFF8EC5FC.toInt(), 0xFFE0C3FC.toInt(), android.graphics.Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, 900f, 1200f, paint)
                paint.shader = null; paint.color = 0x55FFFFFF
                cv.drawCircle(450f, 520f, 260f, paint)
                pic.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            }
            val card = File(dir, "Sam Rivera.vcf")
            card.writeText("BEGIN:VCARD\nVERSION:3.0\nFN:Sam Rivera\nN:Rivera;Sam;;;\nORG:Northside Studio\nTEL;TYPE=CELL:(720) 555-0128\nEMAIL:sam@example.com\nEND:VCARD\n")
            val photo = MediaRef("demo-photo", "", "", "", "image/jpeg", "couch.jpg", pic.length(), 900, 1200, pic.path)
            val vcf = MediaRef("demo-card", "", "", "", "text/x-vcard", "Sam Rivera.vcf", card.length(), 0, 0, card.path)
            val clip = File(dir, "clip.mp4")
            if (!clip.exists()) app.assets.open("demo-clip.mp4").use { input -> clip.outputStream().use { input.copyTo(it) } }
            val video = MediaRef("demo-video", "", "", "", "video/mp4", "clip.mp4", clip.length(), 480, 640, clip.path)
            // A few more sent back to back, so they show as one strip.
            fun shot(name: String, a: Int, b: Int): MediaRef {
                val f = File(dir, name)
                if (!f.exists()) {
                    val bmp = android.graphics.Bitmap.createBitmap(1200, 900, android.graphics.Bitmap.Config.ARGB_8888)
                    val cv = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    paint.shader = android.graphics.LinearGradient(0f, 0f, 1200f, 900f, a, b, android.graphics.Shader.TileMode.CLAMP)
                    cv.drawRect(0f, 0f, 1200f, 900f, paint)
                    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
                }
                return MediaRef("demo-$name", "", "", "", "image/jpeg", name, f.length(), 1200, 900, f.path)
            }
            msgs["c9"] = listOf(
                msg("c9", false, "Look who claimed my spot on the couch", 1500).copy(media = listOf(photo)),
                msg("c9", false, "Here's Sam's number for the shoot", 1499).copy(media = listOf(vcf)),
                msg("c9", false, "", 1498).copy(media = listOf(video)),
                msg("c9", false, "", 1498).copy(media = listOf(shot("beach.jpg", 0xFFF6D365.toInt(), 0xFFFDA085.toInt()))),
                msg("c9", false, "", 1498).copy(media = listOf(shot("lake.jpg", 0xFF84FAB0.toInt(), 0xFF8FD3F4.toInt()))),
                msg("c9", false, "", 1498).copy(media = listOf(shot("dusk.jpg", 0xFFA18CD1.toInt(), 0xFFFBC2EB.toInt()))),
                msg("c9", false, "Last day of the trip", 1497),
            )
        }
        typing["c4"] = now + 600_000
        contacts = listOf(jordan, ava, marcus, nina, mom).map { Contact(it.id, it.contactId, it.name, it.number, it.formatted, "") }
        prefs.edit { it.copy(pinned = listOf("c2", "c4", "c3", "c7", "c9", "c6"), pinsSeeded = true, setupDone = true, profileName = "Justin Petry") }
    }
}
