package com.jpetrydesign.pyng.data

/**
 * iPhones sometimes send reactions and replies as plain text: Loved “see you soon”, Laughed at an image,
 * Reacted 🔥 to “lol”, Removed a heart from “ok”, Replied to a message: … . These turn back into what they
 * were: a reaction on the message they point at, or a reply under it. Anything that can't be matched is left
 * as it came.
 */
object Tapbacks {
    private val verbs = linkedMapOf(
        "Loved" to "❤️", "Liked" to "👍", "Disliked" to "👎", "Laughed at" to "😂",
        "Emphasized" to "‼️", "Emphasised" to "‼️", "Questioned" to "❓",
    )
    private val removedNames = mapOf(
        "a heart" to "❤️", "a like" to "👍", "a dislike" to "👎", "a laugh" to "😂",
        "an exclamation" to "‼️", "an exclamation mark" to "‼️", "a question mark" to "❓",
    )
    private const val Q = "[“\"”]"
    private val quick = Regex("^\\s*(Loved|Liked|Disliked|Laughed at|Emphasi[sz]ed|Questioned|Reacted|Removed|Replied to) ")
    private const val THING = "an? (?:image|photo|picture|video|GIF|gif|sticker|attachment|audio message|voice message|movie|link|message)"

    private val reactRx = Regex("^(${verbs.keys.joinToString("|")}) (?:$Q([\\s\\S]+)$Q|($THING))\\s*$")
    private val emojiRx = Regex("^Reacted (\\S+?) to (?:$Q([\\s\\S]+)$Q|($THING))\\s*$")
    private val removeRx = Regex("^Removed (?:(${removedNames.keys.joinToString("|")})|(\\S+?)) from (?:$Q([\\s\\S]+)$Q|($THING))\\s*$")
    private val replyRx = Regex("^Replied to (?:$Q([\\s\\S]+?)$Q|($THING))\\s*:?\\s*([\\s\\S]*)$")

    /** What a reaction text means: the emoji, what it points at (quoted text or a kind of thing), and whether it was taken back. */
    data class Parsed(val emoji: String, val quote: String?, val thing: String?, val removed: Boolean)

    fun parse(text: String): Parsed? {
        val s = text.trim()
        reactRx.find(s)?.let { return Parsed(verbs.getValue(it.groupValues[1]), it.groupValues[2].ifEmpty { null }, it.groupValues[3].ifEmpty { null }, false) }
        emojiRx.find(s)?.let { return Parsed(it.groupValues[1], it.groupValues[2].ifEmpty { null }, it.groupValues[3].ifEmpty { null }, false) }
        removeRx.find(s)?.let {
            val e = removedNames[it.groupValues[1]] ?: it.groupValues[2]
            return Parsed(e, it.groupValues[3].ifEmpty { null }, it.groupValues[4].ifEmpty { null }, true)
        }
        return null
    }

    /** A short line for chat lists and notifications: "Reacted ❤️ to a photo". Null when [text] isn't one. */
    fun summary(text: String): String? {
        val p = parse(text) ?: return null
        val what = p.thing?.let { if (it.endsWith("image") || it.endsWith("picture")) "a photo" else it }
            ?: "“" + p.quote!!.take(40) + "”"
        return if (p.removed) "Removed ${p.emoji} from $what" else "Reacted ${p.emoji} to $what"
    }

    private fun norm(s: String) = s.trim().trimEnd('…', '.').replace('’', '\'').replace(Regex("\\s+"), " ")

    private fun matches(target: Msg, quote: String?, thing: String?): Boolean {
        if (target.isSys) return false
        if (quote != null) {
            val q = norm(quote)
            if (q.isEmpty() || target.text.isBlank()) return false
            val t = norm(target.text)
            // Long messages come back cut short with "…".
            return t == q || (quote.trim().endsWith("…") || quote.trim().endsWith("...")) && t.startsWith(q)
        }
        val kind = thing.orEmpty().lowercase()
        return when {
            kind.endsWith("image") || kind.endsWith("photo") || kind.endsWith("picture") || kind.endsWith("gif") || kind.endsWith("sticker") ->
                target.media.any { it.isImage }
            kind.endsWith("video") || kind.endsWith("movie") -> target.media.any { it.isVideo }
            kind.endsWith("audio message") || kind.endsWith("voice message") -> target.media.any { it.isAudio }
            kind.endsWith("attachment") -> target.media.isNotEmpty()
            kind.endsWith("link") -> target.text.contains("http")
            else -> true
        }
    }

    /**
     * [msgs] oldest first, with reaction texts folded onto the message they point at and reply texts turned
     * into replies. Looks back through the 200 messages before each one.
     */
    fun fold(msgs: List<Msg>): List<Msg> {
        if (msgs.none { quick.containsMatchIn(it.text) }) return msgs
        val out = ArrayList<Msg>(msgs.size)
        // "Replied to a message:" on its own, with the reply itself in the next message from them.
        var waiting: Triple<String, String, Long>? = null
        for (m0 in msgs) {
            val who0 = m0.participantId.ifEmpty { if (m0.fromMe) "me" else m0.sender }
            val m = waiting?.takeIf { (w, _, ts) -> w == who0 && m0.ts - ts in 0L..120_000L && m0.replyTo.isEmpty() && !m0.isSys }
                ?.let { (_, target, _) -> m0.copy(replyTo = target) } ?: m0
            if (m !== m0 || (waiting != null && waiting.first == who0)) waiting = null
            val p = if (m.media.isEmpty() && !m.isSys) parse(m.text) else null
            if (p != null) {
                val who = m.participantId.ifEmpty { if (m.fromMe) "me" else m.sender }
                val at = (out.lastIndex downTo maxOf(0, out.size - 200)).firstOrNull { i ->
                    val c = out[i]
                    // A reaction to a photo is to someone else's photo; a quoted text can be anyone's.
                    (p.quote != null || c.participantId != m.participantId || c.fromMe != m.fromMe) && matches(c, p.quote, p.thing)
                }
                if (at != null) {
                    val c = out[at]
                    val rs = c.reactions.toMutableList()
                    val i = rs.indexOfFirst { it.emoji == p.emoji }
                    if (p.removed) {
                        if (i >= 0) {
                            val by = rs[i].by - who
                            if (by.isEmpty() || rs[i].count <= 1) rs.removeAt(i) else rs[i] = rs[i].copy(count = rs[i].count - 1, by = by)
                        }
                    } else {
                        // One reaction each: a new one replaces the one they gave before.
                        for (j in rs.indices.reversed()) if (j != i && who in rs[j].by) {
                            val by = rs[j].by - who
                            if (by.isEmpty()) rs.removeAt(j) else rs[j] = rs[j].copy(count = (rs[j].count - 1).coerceAtLeast(1), by = by)
                        }
                        val k = rs.indexOfFirst { it.emoji == p.emoji }
                        if (k < 0) rs += Reaction(p.emoji, 1, listOf(who))
                        else if (who !in rs[k].by) rs[k] = rs[k].copy(count = rs[k].count + 1, by = rs[k].by + who)
                    }
                    out[at] = c.copy(reactions = rs)
                    continue
                }
            }
            val r = if (!m.isSys && m.replyTo.isEmpty()) replyRx.find(m.text.trim()) else null
            if (r != null) {
                val quote = r.groupValues[1].ifEmpty { null }
                val thing = r.groupValues[2].ifEmpty { null }
                val rest = r.groupValues[3].trim()
                val pointed = quote != null || (thing != null && thing != "a message")
                val at = if (!pointed) null else (out.lastIndex downTo maxOf(0, out.size - 200)).firstOrNull { i -> matches(out[i], quote, thing) }
                // "?" when there's no telling which message it answers: it still shows as a reply, without the line.
                val target = at?.let { out[it].id }?.ifEmpty { null } ?: "?"
                if (rest.isEmpty() && m.media.isEmpty()) { waiting = Triple(who0, target, m.ts); continue }
                out += m.copy(text = rest, replyTo = target)
                continue
            }
            out += m
        }
        return out
    }
}
