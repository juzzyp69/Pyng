package com.jpetrydesign.pyng.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** A color as hue (0-360), saturation (0-1) and lightness (0-1). */
data class Hsl(val h: Float, val s: Float, val l: Float) {
    fun json() = JSONObject().put("h", h.toDouble()).put("s", s.toDouble()).put("l", l.toDouble())

    companion object {
        fun from(o: JSONObject?, def: Hsl) = if (o == null) def else Hsl(
            o.optDouble("h", def.h.toDouble()).toFloat(),
            o.optDouble("s", def.s.toDouble()).toFloat(),
            o.optDouble("l", def.l.toDouble()).toFloat(),
        )
    }
}

/** A chat's own look: a palette key (or "chat-custom"), a wallpaper and custom colors. */
data class ChatLook(
    val key: String = "",
    val wall: String = "",
    val mode: String = "Mono",
    val c1: Hsl = Hsl(214f, 0.75f, 0.47f),
    val c2: Hsl = Hsl(340f, 0.62f, 0.62f),
    /** Colors set one by one under "Customize more": bg, inBg, inInk, outBg, outInk, accent. */
    val custom: Map<String, Hsl> = emptyMap(),
) {
    fun json(): JSONObject = JSONObject().put("key", key).put("wall", wall).put("mode", mode).put("c1", c1.json()).put("c2", c2.json())
        .put("custom", JSONObject().apply { custom.forEach { (k, v) -> put(k, v.json()) } })

    companion object {
        fun from(o: JSONObject) = ChatLook(
            o.optString("key"), o.optString("wall"), o.optString("mode", "Mono"),
            Hsl.from(o.optJSONObject("c1"), Hsl(214f, 0.75f, 0.47f)), Hsl.from(o.optJSONObject("c2"), Hsl(340f, 0.62f, 0.62f)),
            o.optJSONObject("custom")?.let { c -> c.keys().asSequence().associateWith { k -> Hsl.from(c.optJSONObject(k), Hsl(0f, 0f, 0.5f)) } } ?: emptyMap(),
        )
    }
}

data class ChatAlerts(val on: Boolean = true, val sound: String = "Default", val vibrate: String = "Default") {
    fun json(): JSONObject = JSONObject().put("on", on).put("sound", sound).put("vibrate", vibrate)

    companion object {
        fun from(o: JSONObject) = ChatAlerts(o.optBoolean("on", true), o.optString("sound", "Default"), o.optString("vibrate", "Default"))
    }
}

data class Starred(val msgId: String, val convId: String, val text: String, val ts: Long) {
    fun json(): JSONObject = JSONObject().put("msgId", msgId).put("convId", convId).put("text", text).put("ts", ts)

    companion object {
        fun from(o: JSONObject) = Starred(o.optString("msgId"), o.optString("convId"), o.optString("text"), o.optLong("ts"))
    }
}

data class Scheduled(val id: String, val convId: String, val text: String, val at: Long, val label: String) {
    fun json(): JSONObject = JSONObject().put("id", id).put("convId", convId).put("text", text).put("at", at).put("label", label)

    companion object {
        fun from(o: JSONObject) = Scheduled(o.optString("id"), o.optString("convId"), o.optString("text"), o.optLong("at"), o.optString("label"))
    }
}

val DEFAULT_TOGGLES = mapOf(
    "smart" to true, "micBtn" to true, "typing" to true, "receipts" to true, "previews" to true, "enterSend" to false,
    "translate" to true, "notif" to true, "sounds" to false, "vibrate" to true, "reactNotif" to true, "mentionsOnly" to false,
    "quick" to true, "counter" to true, "spam" to true, "otpChip" to true, "otpAuto" to true, "bioLock" to false, "hideSwitcher" to false,
    "reduceBlur" to false, "reduceMotion" to false, "boldText" to false, "highContrast" to false, "effects" to true,
    "alwaysOn" to false, "hideGmAlerts" to false, "smsLinks" to false,
)

val DEFAULT_OPTS = mapOf(
    "autoDl" to "Always", "quality" to "High", "swipe" to "Archive", "swipeR" to "Read/Unread",
    "stamps" to "Grouped", "gifRating" to "pg", "gradient" to "Across the screen", "statusStyle" to "Icon and text", "photoGroups" to "Peek", "font" to "Google",
)

/** A theme you made and named, kept to use again for the app or any chat. */
data class SavedTheme(
    val id: String,
    val name: String,
    /** Mono, Duotone or Gradient. */
    val mode: String,
    val c1: Hsl,
    val c2: Hsl,
    /** Colors set one by one (bg, inBg, inInk, outBg, outInk, accent), when the theme came from a chat's Custom colors. */
    val custom: Map<String, Hsl> = emptyMap(),
    val at: Long = 0L,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("mode", mode).put("c1", c1.json()).put("c2", c2.json()).put("at", at)
        .put("custom", JSONObject().apply { custom.forEach { (k, v) -> put(k, v.json()) } })

    companion object {
        fun from(o: JSONObject) = SavedTheme(
            o.optString("id"), o.optString("name"), o.optString("mode", "Mono"),
            Hsl.from(o.optJSONObject("c1"), Hsl(214f, 0.75f, 0.47f)), Hsl.from(o.optJSONObject("c2"), Hsl(340f, 0.62f, 0.62f)),
            o.optJSONObject("custom")?.let { c -> c.keys().asSequence().associateWith { k -> Hsl.from(c.optJSONObject(k), Hsl(0f, 0f, 0.5f)) } } ?: emptyMap(),
            o.optLong("at"),
        )
    }
}

data class AppPrefs(
    val setupDone: Boolean = false,
    val themeKey: String = "Ocean",
    val mode: String = "Match system",
    val c1: Hsl = Hsl(340f, 0.62f, 0.62f),
    val c2: Hsl = Hsl(42f, 0.72f, 0.42f),
    val pinStyle: String = "Badges",
    val pinLayout: String = "Grid",
    val bubble: String = "Rounded",
    val textSize: String = "M",
    val density: String = "Comfortable",
    val showPhotos: String = "Show",
    val defWall: String = "None",
    val appIcon: String = "Frost",
    val toggles: Map<String, Boolean> = DEFAULT_TOGGLES,
    val opts: Map<String, String> = DEFAULT_OPTS,
    val quickReacts: List<String> = listOf("❤️", "😂", "😮", "😢", "👍", "🔥"),
    val recents: List<String> = listOf("😂", "❤️", "🔥", "🥰", "😭", "👍", "✨", "😏", "🙏", "🫶", "💀", "😱"),
    val pinned: List<String> = emptyList(),
    val pinsSeeded: Boolean = false,
    val muted: Set<String> = emptySet(),
    val markedUnread: Set<String> = emptySet(),
    val starred: List<Starred> = emptyList(),
    val looks: Map<String, ChatLook> = emptyMap(),
    val alerts: Map<String, ChatAlerts> = emptyMap(),
    val drafts: Map<String, String> = emptyMap(),
    val scheduled: List<Scheduled> = emptyList(),
    val profileName: String = "",
    val profilePhoto: String = "",
    val dismissedUnknown: Set<String> = emptySet(),
    val notSpam: Set<String> = emptySet(),
    val permsAsked: Int = 0,
    val savedThemes: List<SavedTheme> = emptyList(),
) {
    fun on(key: String) = toggles[key] ?: DEFAULT_TOGGLES[key] ?: false
    fun opt(key: String) = opts[key] ?: DEFAULT_OPTS[key].orEmpty()
    fun isPinned(id: String) = id in pinned
    fun isMuted(id: String) = id in muted
    fun isStarred(id: String) = starred.any { it.msgId == id }
}

private fun <T> JSONArray?.mapEach(f: (Int) -> T): List<T> = if (this == null) emptyList() else (0 until length()).map(f)

private fun AppPrefs.toJson(): JSONObject = JSONObject().apply {
    put("setupDone", setupDone); put("themeKey", themeKey); put("mode", mode); put("c1", c1.json()); put("c2", c2.json())
    put("pinStyle", pinStyle); put("pinLayout", pinLayout); put("bubble", bubble); put("textSize", textSize)
    put("density", density); put("showPhotos", showPhotos); put("defWall", defWall); put("appIcon", appIcon)
    put("toggles", JSONObject(toggles as Map<*, *>)); put("opts", JSONObject(opts as Map<*, *>))
    put("quickReacts", JSONArray(quickReacts)); put("recents", JSONArray(recents)); put("pinned", JSONArray(pinned))
    put("pinsSeeded", pinsSeeded); put("muted", JSONArray(muted.toList())); put("markedUnread", JSONArray(markedUnread.toList()))
    put("starred", JSONArray(starred.map { it.json() }))
    put("looks", JSONObject().also { o -> looks.forEach { (k, v) -> o.put(k, v.json()) } })
    put("alerts", JSONObject().also { o -> alerts.forEach { (k, v) -> o.put(k, v.json()) } })
    put("drafts", JSONObject(drafts as Map<*, *>)); put("scheduled", JSONArray(scheduled.map { it.json() }))
    put("profileName", profileName); put("profilePhoto", profilePhoto)
    put("dismissedUnknown", JSONArray(dismissedUnknown.toList())); put("notSpam", JSONArray(notSpam.toList()))
    put("permsAsked", permsAsked)
    put("savedThemes", JSONArray(savedThemes.map { it.json() }))
}

private fun JSONObject.strList(k: String) = optJSONArray(k).mapEach { optJSONArray(k)!!.optString(it) }

private fun JSONObject.strMap(k: String): Map<String, String> {
    val o = optJSONObject(k) ?: return emptyMap()
    return o.keys().asSequence().associateWith { o.optString(it) }
}

private fun fromJson(o: JSONObject): AppPrefs {
    val d = AppPrefs()
    val tg = o.optJSONObject("toggles")
    val toggles = DEFAULT_TOGGLES + (tg?.keys()?.asSequence()?.associateWith { tg.optBoolean(it) } ?: emptyMap())
    val looksO = o.optJSONObject("looks")
    val alertsO = o.optJSONObject("alerts")
    return AppPrefs(
        setupDone = o.optBoolean("setupDone"),
        themeKey = o.optString("themeKey", d.themeKey),
        mode = o.optString("mode", d.mode),
        c1 = Hsl.from(o.optJSONObject("c1"), d.c1),
        c2 = Hsl.from(o.optJSONObject("c2"), d.c2),
        pinStyle = o.optString("pinStyle", d.pinStyle),
        pinLayout = o.optString("pinLayout", d.pinLayout),
        bubble = o.optString("bubble", d.bubble),
        textSize = o.optString("textSize", d.textSize),
        density = o.optString("density", d.density),
        showPhotos = o.optString("showPhotos", d.showPhotos),
        defWall = o.optString("defWall", d.defWall),
        appIcon = o.optString("appIcon", d.appIcon).let { if (it == "Classic") "Frost" else it },
        toggles = toggles,
        opts = DEFAULT_OPTS + o.strMap("opts"),
        quickReacts = o.strList("quickReacts").ifEmpty { d.quickReacts },
        recents = o.strList("recents").ifEmpty { d.recents },
        pinned = o.strList("pinned"),
        pinsSeeded = o.optBoolean("pinsSeeded"),
        muted = o.strList("muted").toSet(),
        markedUnread = o.strList("markedUnread").toSet(),
        starred = o.optJSONArray("starred").mapEach { Starred.from(o.optJSONArray("starred")!!.getJSONObject(it)) },
        looks = looksO?.keys()?.asSequence()?.associateWith { ChatLook.from(looksO.getJSONObject(it)) } ?: emptyMap(),
        alerts = alertsO?.keys()?.asSequence()?.associateWith { ChatAlerts.from(alertsO.getJSONObject(it)) } ?: emptyMap(),
        drafts = o.strMap("drafts"),
        scheduled = o.optJSONArray("scheduled").mapEach { Scheduled.from(o.optJSONArray("scheduled")!!.getJSONObject(it)) },
        profileName = o.optString("profileName"),
        profilePhoto = o.optString("profilePhoto"),
        dismissedUnknown = o.strList("dismissedUnknown").toSet(),
        notSpam = o.strList("notSpam").toSet(),
        permsAsked = o.optInt("permsAsked"),
        savedThemes = o.optJSONArray("savedThemes").mapEach { SavedTheme.from(o.optJSONArray("savedThemes")!!.getJSONObject(it)) },
    )
}

/** All of the app's own settings, observable from Compose and saved on every change. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pyng", Context.MODE_PRIVATE)

    var state by mutableStateOf(
        runCatching { sp.getString("prefs", null)?.let { fromJson(JSONObject(it)) } }.getOrNull() ?: AppPrefs(),
    )
        private set

    @Synchronized
    fun edit(f: (AppPrefs) -> AppPrefs) {
        val next = f(state)
        if (next == state) return
        state = next
        sp.edit().putString("prefs", next.toJson().toString()).apply()
    }

    fun toggle(key: String) = edit { it.copy(toggles = it.toggles + (key to !it.on(key))) }
    fun setOpt(key: String, value: String) = edit { it.copy(opts = it.opts + (key to value)) }
}
