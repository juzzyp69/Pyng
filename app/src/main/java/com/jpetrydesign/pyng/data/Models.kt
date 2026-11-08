package com.jpetrydesign.pyng.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

data class Person(
    val id: String,
    val number: String,
    val formatted: String,
    val name: String,
    val first: String,
    val color: String,
    val me: Boolean,
    val contactId: String,
) {
    val display get() = name.ifBlank { formatted.ifBlank { number } }
    val short get() = first.ifBlank { display.substringBefore(' ') }

    companion object {
        fun from(o: JSONObject) = Person(
            id = o.optString("id"),
            number = o.optString("number"),
            formatted = o.optString("formatted"),
            name = o.optString("name"),
            first = o.optString("first"),
            color = o.optString("color"),
            me = o.optBoolean("me"),
            contactId = o.optString("contactId"),
        )
    }
}

data class Conv(
    val id: String,
    val name: String,
    val preview: String,
    val fromMe: Boolean,
    val sender: String,
    val lastStatus: String,
    val ts: Long,
    val unread: Boolean,
    val group: Boolean,
    val rcs: Boolean,
    val color: String,
    val pinned: Boolean,
    val readOnly: Boolean,
    val status: String,
    val people: List<Person>,
    val groupPhoto: String,
) {
    val others get() = people.filter { !it.me }
    val me get() = people.firstOrNull { it.me }
    val title get() = name.ifBlank { others.joinToString { it.short }.ifBlank { "Unknown" } }
    val shortTitle get() = if (group) title else (others.firstOrNull()?.short ?: title.substringBefore(' '))
    val avatarId get() = if (group) "" else others.firstOrNull()?.id.orEmpty()
    val number get() = others.firstOrNull()?.let { it.formatted.ifBlank { it.number } }.orEmpty()
    val known get() = group || others.any { it.contactId.isNotBlank() }
    val business get() = !group && others.firstOrNull()?.number?.let { n -> n.filter(Char::isDigit).length in 3..6 } == true
    val read get() = fromMe && lastStatus == "OUTGOING_DISPLAYED"

    companion object {
        /** A stand-in for a chat Pyng hasn't loaded yet (used for notifications only). */
        fun placeholder(id: String, title: String) = Conv(id, title, "", false, "", "", System.currentTimeMillis(), true, false, false, "", false, false, "active", emptyList(), "")

        fun from(o: JSONObject) = Conv(
            id = o.optString("id"),
            name = o.optString("name"),
            preview = o.optString("preview"),
            fromMe = o.optBoolean("fromMe"),
            sender = o.optString("sender"),
            lastStatus = o.optString("lastStatus"),
            ts = o.optLong("ts"),
            unread = o.optBoolean("unread"),
            group = o.optBoolean("group"),
            rcs = o.optBoolean("rcs"),
            color = o.optString("color"),
            pinned = o.optBoolean("pinned"),
            readOnly = o.optBoolean("readOnly"),
            status = o.optString("status", "active"),
            people = o.optJSONArray("people").objects().map(Person::from),
            groupPhoto = o.optString("groupPhoto"),
        )

        fun list(json: String): List<Conv> = JSONArray(json).objects().map(::from).filter { it.id.isNotEmpty() }
    }
}

data class MediaRef(
    val id: String,
    val key: String,
    val thumbId: String,
    val thumbKey: String,
    val mime: String,
    val name: String,
    val size: Long,
    val w: Int,
    val h: Int,
    val localPath: String = "",
) {
    val isImage get() = mime.startsWith("image/")
    val isGif get() = mime == "image/gif"
    val isVideo get() = mime.startsWith("video/")
    val isAudio get() = mime.startsWith("audio/")
    val isContact get() = mime.contains("vcard")
    /** Google hasn't finished uploading this attachment yet; the real one arrives in a later update. */
    val waiting get() = id.isEmpty() && thumbId.isEmpty() && localPath.isEmpty()
    val kindLabel get() = when {
        isGif -> "GIF"
        isImage -> "Photo"
        isVideo -> "Video"
        isAudio -> "Voice message"
        isContact -> "Contact"
        else -> name.ifBlank { "Attachment" }
    }
    val ext get() = when {
        mime == "image/jpeg" || mime == "image/jpg" -> "jpg"
        mime.contains("/") -> mime.substringAfter('/').substringBefore(';').substringAfterLast('.').take(8).let { if (it == "mpeg") "mp3" else it }
        else -> name.substringAfterLast('.', "bin")
    }

    companion object {
        fun from(o: JSONObject) = MediaRef(
            id = o.optString("id"),
            key = o.optString("key"),
            thumbId = o.optString("thumbId"),
            thumbKey = o.optString("thumbKey"),
            mime = o.optString("mime").ifBlank { mimeFromName(o.optString("name")) },
            name = o.optString("name"),
            size = o.optLong("size"),
            w = o.optInt("w"),
            h = o.optInt("h"),
        )
    }
}

private fun mimeFromName(n: String) = when (n.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "mp4", "m4v" -> "video/mp4"
    "3gp" -> "video/3gpp"
    "mov" -> "video/quicktime"
    "m4a", "aac" -> "audio/mp4"
    "amr" -> "audio/amr"
    "mp3" -> "audio/mpeg"
    "ogg", "opus" -> "audio/ogg"
    "vcf" -> "text/vcard"
    else -> ""
}

data class Reaction(val emoji: String, val count: Int, val by: List<String>)

data class Msg(
    val id: String,
    val tmpId: String,
    val convId: String,
    val participantId: String,
    val sender: String,
    val senderFirst: String,
    val fromMe: Boolean,
    val status: String,
    val statusText: String,
    val text: String,
    val media: List<MediaRef>,
    val ts: Long,
    val reactions: List<Reaction>,
    val replyTo: String,
    val type: Long,
    val kind: String,
    val error: String = "",
    val unknownParts: Int = 0,
) {
    val key get() = id.ifEmpty { tmpId }
    val failed get() = error.isNotEmpty() || status.contains("FAILED") || status == "OUTGOING_CANCELED"
    val pending get() = status == "LOCAL" || status.contains("SENDING") || status.contains("YET_TO_SEND") || status == "OUTGOING_VALIDATING"
    val readByThem get() = status == "OUTGOING_DISPLAYED"
    val delivered get() = status == "OUTGOING_DELIVERED" || readByThem
    val isSys get() = kind == "sys"
    val isSms get() = type == 1L
    val isMms get() = type == 2L || type == 3L
    /** An incoming picture message the phone is still fetching, or one waiting for a manual download. */
    val fetching get() = !fromMe && status.startsWith("INCOMING_") && status.contains("DOWNLOAD")
    val needsManual get() = status == "INCOMING_YET_TO_MANUAL_DOWNLOAD"
    val fetchFailed get() = status.startsWith("INCOMING_DOWNLOAD_FAILED") || status == "INCOMING_DOWNLOAD_CANCELED"
    /** Something Pyng can't read yet. Shown as a note instead of hiding the message. */
    val unsupported get() = !isSys && text.isBlank() && media.isEmpty() && !fetching && unknownParts > 0
    val awaitingMedia get() = media.any { it.waiting } || (fetching && media.isEmpty() && !needsManual && !fetchFailed)
    /** Never hide a real message just because its text or picture hasn't arrived yet. */
    val visible get() = isSys || text.isNotBlank() || media.isNotEmpty() || fetching || unsupported
    /** One short line for previews and notifications. */
    val summary get() = when {
        text.isNotBlank() -> text
        media.isNotEmpty() -> media.firstOrNull { it.isImage || it.isVideo }?.kindLabel ?: media.first().kindLabel
        fetching -> "Photo"
        unsupported -> "Message"
        else -> ""
    }

    companion object {
        fun from(o: JSONObject) = Msg(
            id = o.optString("id"),
            tmpId = o.optString("tmpId"),
            convId = o.optString("conversationId"),
            participantId = o.optString("participantId"),
            sender = o.optString("sender"),
            senderFirst = o.optString("senderFirst"),
            fromMe = o.optBoolean("fromMe"),
            status = o.optString("status"),
            statusText = o.optString("statusText"),
            text = o.optString("text"),
            // Keep attachments that are still uploading: they show as "on its way" and fill in when ready.
            media = o.optJSONArray("media").objects().map(MediaRef::from),
            ts = o.optLong("ts"),
            reactions = o.optJSONArray("reactions").objects().map {
                Reaction(it.optString("emoji"), it.optInt("count"), it.optJSONArray("by").strings())
            }.filter { it.emoji.isNotEmpty() },
            replyTo = o.optString("replyTo"),
            type = o.optLong("type"),
            kind = o.optString("kind", "text"),
            unknownParts = o.optInt("unknown"),
        )

        fun list(json: String): List<Msg> = JSONArray(json).objects().map(::from)
    }
}

data class Contact(
    val id: String,
    val contactId: String,
    val name: String,
    val number: String,
    val formatted: String,
    val color: String,
) {
    companion object {
        fun list(json: String): List<Contact> = JSONArray(json).objects().map {
            Contact(
                it.optString("id"), it.optString("contactId"), it.optString("name"),
                it.optString("number"), it.optString("formatted"), it.optString("color"),
            )
        }.filter { it.number.isNotBlank() }
    }
}

data class SimInfo(val name: String, val number: String, val rcs: Boolean)
