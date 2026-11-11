package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import androidx.compose.ui.graphics.Color
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.data.Person
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How a conversation or person is drawn as an avatar. */
data class Who(
    val photo: String?,
    val ini: String,
    val bg: Color,
    val ink: Color,
    /** A short code or business number: drawn as a building, not "#". */
    val business: Boolean = false,
    /** Up to three members of a group without a photo, drawn as overlapping circles. */
    val stack: List<Who> = emptyList(),
)

@Composable
fun whoOf(c: Conv): Who {
    val repo = LocalRepo.current
    val t = LocalTok.current
    return whoOf(c, repo, t)
}

fun whoOf(c: Conv, repo: Repo, t: Tok): Who {
    val show = repo.prefs.state.showPhotos == "Show"
    val photo = if (!show) null
        else if (c.group) repo.prefs.state.opt("gphoto:" + c.id).takeIf { it.isNotBlank() && java.io.File(it).exists() } ?: c.groupPhoto.takeIf { it.startsWith("http") }
        else repo.prefs.state.opt("cphoto:" + c.avatarId).takeIf { it.isNotBlank() && java.io.File(it).exists() } ?: repo.avatars[c.avatarId]?.takeIf { it.isNotEmpty() }
    if (c.group) {
        val stack = if (photo != null) emptyList() else c.others.take(3).map { personWho(it, repo, t) }
        return Who(photo, initialsOf(c.title), t.tone, t.p, stack = stack)
    }
    if (c.business) return Who(photo, initialsOf(c.title), t.neutral, t.neutralInk, business = true)
    val (bg, ink) = t.toneFor(toneKey(c.number, c.avatarId.ifEmpty { c.id }))
    return Who(photo, initialsOf(c.title), bg, ink)
}

fun personWho(p: Person, repo: Repo, t: Tok): Who {
    val show = repo.prefs.state.showPhotos == "Show"
    val photo = if (show) repo.prefs.state.opt("cphoto:" + p.id).takeIf { it.isNotBlank() && java.io.File(it).exists() } ?: repo.avatars[p.id]?.takeIf { it.isNotEmpty() } else null
    val (bg, ink) = t.toneFor(toneKey(p.number, p.id))
    return Who(photo, initialsOf(p.display), bg, ink)
}

@Composable
fun whoOf(p: Person): Who = personWho(p, LocalRepo.current, LocalTok.current)

/** Goes to your one-on-one chat with someone from a group, starting it if there isn't one yet. */
fun openDirect(repo: Repo, nav: com.jpetrydesign.pyng.ui.Nav?, scope: kotlinx.coroutines.CoroutineScope, person: Person) {
    val number = person.number.ifBlank { person.formatted }
    if (number.isBlank() || nav == null) return
    val digits = number.filter(Char::isDigit).takeLast(10)
    val existing = repo.convs.values.firstOrNull { c -> !c.group && c.others.any { it.number.filter(Char::isDigit).takeLast(10) == digits } }
    if (existing != null) { nav.openChat(existing.id); return }
    scope.launch {
        repo.startChat(listOf(number)).onSuccess { c -> nav.openChat(c.id) }.onFailure { repo.toast(it.message ?: "Couldn't start the chat") }
    }
}

/** Tones follow the phone number, so a person has the same color in chats, groups and contacts. */
fun toneKey(number: String, fallback: String): String = number.filter(Char::isDigit).takeLast(10).ifEmpty { fallback }

/** A tone for someone known only by name or number (contact lists, search). */
@Composable
fun toneOf(number: String, fallback: String): Pair<Color, Color> = LocalTok.current.toneFor(toneKey(number, fallback))

/**
 * The one way to draw a [Who]: their photo, their initials on a solid tone, a building for
 * businesses, or up to three overlapping member circles for a group without a photo.
 */
@Composable
fun WhoAvatar(w: Who, size: Dp, fontSize: TextUnit = (size.value / 3.1f).sp, halo: Color? = null, ring: Color = LocalTok.current.bg, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    when {
        w.photo == null && w.stack.size >= 2 -> GroupStack(w.stack, size, ring, halo)
        w.photo == null && w.business -> Box(
            Modifier.size(size).clip(shape).background(w.bg),
            contentAlignment = Alignment.Center,
        ) { Ico(Icons.building, w.ink, size * 0.44f, 1.9f) }
        else -> Avatar(w.photo, w.ini, size, w.bg, w.ink, fontSize, halo, shape = shape)
    }
}

@Composable
private fun GroupStack(members: List<Who>, size: Dp, ring: Color, halo: Color?) {
    val three = members.size >= 3
    val d = if (three) size * 0.58f else size * 0.66f
    val font = (d.value / 2.7f).sp
    val edge = (size.value / 26f).coerceIn(1.5f, 3f).dp
    @Composable
    fun One(w: Who, x: Dp, y: Dp) {
        Box(Modifier.offset(x, y).size(d).clip(CircleShape).background(ring).padding(edge)) {
            Avatar(w.photo, w.ini, d - edge * 2, w.bg, w.ink, font)
        }
    }
    var m = Modifier.size(size)
    if (halo != null) m = m.drawBehind {
        drawCircle(halo, radius = this.size.minDimension / 2 + 3.5.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
    Box(m) {
        if (three) {
            One(members[0], 0.dp, size * 0.02f)
            One(members[1], size - d, size * 0.02f)
            One(members[2], (size - d) / 2, size - d + size * 0.04f)
        } else {
            One(members[0], 0.dp, 0.dp)
            One(members[1], size - d, size - d)
        }
    }
}

private val timeFmt get() = SimpleDateFormat("h:mm a", Locale.getDefault())

fun clock(ms: Long): String = if (ms <= 0) "" else timeFmt.format(Date(ms))

private fun dayDiff(ms: Long): Int {
    val a = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val b = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return ((a.timeInMillis - b.timeInMillis) / 86_400_000L).toInt()
}

/** List time: "3:52 PM", "Yesterday", "Mon", or "Nov 2". */
fun listTime(ms: Long): String {
    if (ms <= 0) return ""
    val d = dayDiff(ms)
    return when {
        d <= 0 -> clock(ms)
        d == 1 -> "Yesterday"
        d < 7 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
        Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("M/d/yy", Locale.getDefault()).format(Date(ms))
    }
}

/** Divider stamp in a chat: "Today 3:52 PM", "Yesterday 9:10 AM", "Monday 2:00 PM", "Nov 2, 4:15 PM". */
fun stamp(ms: Long): String {
    val d = dayDiff(ms)
    return when {
        d <= 0 -> "Today • " + clock(ms)
        d == 1 -> "Yesterday • " + clock(ms)
        d < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms)) + " • " + clock(ms)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms)) + " • " + clock(ms)
    }
}

/** "at 3:52 PM", "yesterday at 3:52 PM", "on Nov 2". */
fun whenText(ms: Long): String {
    val d = dayDiff(ms)
    return when {
        d <= 0 -> "at " + clock(ms)
        d == 1 -> "yesterday at " + clock(ms)
        d < 7 -> "on " + SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms)) + " at " + clock(ms)
        else -> "on " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

fun msgPreview(m: Msg): String = m.summary

/** Pulls handy things out of a message: a code, a tracking number, an address, a date, a link. */
data class Detected(val code: String? = null, val track: String? = null, val addr: String? = null, val date: String? = null, val url: String? = null)

fun detect(text: String): Detected {
    if (text.isEmpty()) return Detected()
    val code = Regex("\\b(\\d{4,8})\\b").find(text)?.groupValues?.get(1)?.takeIf { Regex("(?i)code|verif|otp|pin").containsMatchIn(text) }
    val track = Regex("\\b1Z[0-9A-Z]{16}\\b").find(text)?.value
    val addr = Regex("\\b\\d{2,5}\\s+[A-Z][a-z]+\\s+(St|Street|Ave|Avenue|Rd|Road|Blvd|Dr|Way|Ln|Lane|Ct|Pl)\\b").find(text)?.value
    val dm = Regex("(?i)\\b(today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b[^.?!]*?\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm))").find(text)
    val date = dm?.let { it.groupValues[1].replaceFirstChar { c -> c.uppercase() }.let { d -> d.take(1) + d.drop(1).lowercase() } + " at " + it.groupValues[2].uppercase() }
    val url = Regex("https?://[^\\s]+").find(text)?.value
    return Detected(code, track, addr, date, url)
}

/** A local check for scam texts from unknown numbers, mirroring the prototype's rules. */
fun spamCheck(c: Conv, text: String): List<String>? {
    if (c.known || c.group || text.isEmpty()) return null
    var score = 1
    val why = mutableListOf<String>()
    val url = Regex("(?i)https?://[^\\s]+|\\b[\\w-]+\\.(top|xyz|click|info|live|icu|cc|buzz|link)\\b[^\\s]*").find(text)?.value
    if (url != null) {
        score += 1
        val host = url.replace(Regex("^https?://"), "").substringBefore('/').lowercase()
        val brand = Regex("(?i)\\b(usps|fedex|ups|dmv|irs|paypal|amazon|apple|chase|wells fargo|netflix|toll)\\b").find(text)?.value
        if (Regex("\\.(top|xyz|click|info|live|icu|cc|buzz|link)$").containsMatchIn(host)) { score += 1; why += "Link goes to $host" }
        if (brand != null) {
            val b = brand.lowercase().replace(" ", "")
            if (!host.contains("$b.com")) { score += 1; why += "Mentions ${brand.uppercase()} but the link is not $b.com" }
        }
    }
    if (Regex("(?i)\\b(unpaid|fee|suspend|verify|locked|final notice|redeliver|claim|prize|gift card|overdue|act now)\\b").containsMatchIn(text)) { score += 1; why += "Asks you to pay or act fast" }
    why += "Sender is not in your contacts"
    return if (score >= 3) why.take(3) else null
}

/** Effects for phrases like "happy birthday". */
fun fxKind(t: String): String? = when {
    Regex("(?i)happy\\s+(early\\s+)?b(irth)?day|\\bhbd\\b").containsMatchIn(t) -> "birthday"
    Regex("(?i)congrat|happy new year").containsMatchIn(t) -> "confetti"
    Regex("(?i)\\b(love you|ily|miss you)\\b").containsMatchIn(t) -> "hearts"
    else -> null
}

/** 1–3 emoji and nothing else are shown big. */
fun jumboCount(text: String): Int {
    val x = text.trim()
    if (x.isEmpty() || x.length > 24) return 0
    if (x.any { it.isLetterOrDigit() }) return 0
    var count = 0
    var i = 0
    while (i < x.length) {
        val cp = x.codePointAt(i)
        val type = Character.getType(cp)
        if (type == Character.OTHER_SYMBOL.toInt() || Character.isSurrogate(x[i])) {
            if (cp != 0x200D && cp != 0xFE0F && !(cp in 0x1F3FB..0x1F3FF)) {
                val prev = if (i > 0) x.codePointBefore(i) else 0
                if (prev != 0x200D) count++
            }
        } else if (!Character.isWhitespace(cp) && cp != 0x200D && cp != 0xFE0F && cp != 0x20E3) return 0
        i += Character.charCount(cp)
    }
    return if (count in 1..3) count else 0
}


/** Texts from businesses and automated senders: no point suggesting "Haha yes" to these. */
fun automated(c: com.jpetrydesign.pyng.data.Conv, lastText: String): Boolean {
    if (c.business) return true
    val digits = c.number.filter(Char::isDigit).removePrefix("1")
    val tollFree = !c.group && digits.length == 10 && digits.take(3) in setOf("800", "833", "844", "855", "866", "877", "888")
    if (tollFree && !c.known) return true
    val t = lastText.lowercase()
    return listOf("do not reply", "don't reply", "do not respond", "reply stop", "text stop", "stop to opt", "stop to end", "to unsubscribe", "unsubscribe", "msg&data rates", "msg & data rates", "verification code", "one-time code", "your code is").any { it in t }
}


enum class Enc { E2EE, RCS, SMS }

/**
 * Whether a chat is end-to-end encrypted. Google Messages marks it with a note in the chat
 * ("…now end-to-end encrypted", "switched to text messages"), so the newest such note decides.
 */
fun encryptionOf(c: com.jpetrydesign.pyng.data.Conv, msgs: List<com.jpetrydesign.pyng.data.Msg>): Enc {
    val note = msgs.lastOrNull { it.isSys && ("ENCRYPTED" in it.status || "E2EE" in it.status || "PROTOCOL_SWITCH" in it.status || it.status.endsWith("_RCS_CREATED") || it.status.endsWith("_SMS_CREATED") || it.status.endsWith("MMS_GROUP_CREATED") || it.status.endsWith("RCS_GROUP_CREATED")) }?.status
    if (note != null) return when {
        note.endsWith("E2EE_TO_TEXT") || note.endsWith("SWITCH_TO_TEXT") || note.endsWith("SMS_CREATED") || note.endsWith("MMS_GROUP_CREATED") -> Enc.SMS
        note.endsWith("E2EE_TO_RCS") || note.endsWith("PROTOCOL_SWITCH_TO_RCS") || note == "TOMBSTONE_ONE_ON_ONE_RCS_CREATED" || note == "TOMBSTONE_RCS_GROUP_CREATED" -> if (c.rcs) Enc.RCS else Enc.SMS
        else -> if (c.rcs) Enc.E2EE else Enc.SMS
    }
    return if (c.rcs) Enc.RCS else Enc.SMS
}

fun encLabel(e: Enc) = when (e) { Enc.E2EE -> "End-to-end encrypted"; Enc.RCS -> "RCS chat"; Enc.SMS -> "SMS/MMS · not encrypted" }
fun encExplain(e: Enc) = when (e) {
    Enc.E2EE -> "Messages and replies in this chat are end-to-end encrypted. Only the people in it can read them."
    Enc.RCS -> "This chat uses RCS. Google Messages hasn't marked it end-to-end encrypted, so it may not be (for example, chats with some iPhones)."
    Enc.SMS -> "This chat uses SMS/MMS text messages, which aren't encrypted. Your carrier can see them."
}
