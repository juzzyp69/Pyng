package com.jpetrydesign.pyng.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.data.Person
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.Tok
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How a conversation or person is drawn as an avatar. */
data class Who(val photo: String?, val ini: String, val bg: Color, val ink: Color)

@Composable
fun whoOf(c: Conv): Who {
    val repo = LocalRepo.current
    val t = LocalTok.current
    return whoOf(c, repo, t)
}

fun whoOf(c: Conv, repo: Repo, t: Tok): Who {
    val show = repo.prefs.state.showPhotos == "Show"
    val photo = if (show && !c.group) repo.avatars[c.avatarId]?.takeIf { it.isNotEmpty() } else null
    val tinted = c.group || c.business
    return Who(photo, initialsOf(c.title), if (tinted) t.tone else t.neutral, if (tinted) t.p else t.neutralInk)
}

@Composable
fun whoOf(p: Person): Who {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val show = repo.prefs.state.showPhotos == "Show"
    val photo = if (show) repo.avatars[p.id]?.takeIf { it.isNotEmpty() } else null
    return Who(photo, initialsOf(p.display), t.tone, t.p)
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
        d <= 0 -> "Today " + clock(ms)
        d == 1 -> "Yesterday " + clock(ms)
        d < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms)) + " " + clock(ms)
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
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

fun msgPreview(m: Msg): String = when {
    m.text.isNotEmpty() -> m.text
    m.media.any { it.isGif } -> "GIF"
    m.media.any { it.isImage } -> "Photo"
    m.media.any { it.isVideo } -> "Video"
    m.media.any { it.isAudio } -> "Voice message"
    m.media.any { it.isContact } -> "Contact"
    m.media.isNotEmpty() -> m.media.first().name.ifBlank { "Attachment" }
    else -> ""
}

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
