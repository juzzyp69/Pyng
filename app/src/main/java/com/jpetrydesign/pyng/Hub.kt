package com.jpetrydesign.pyng

import com.jpetrydesign.pyng.core.Core
import com.jpetrydesign.pyng.core.Listener
import com.jpetrydesign.pyng.core.Messenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One engine for the whole app, plus a stream of everything it reports. */
class Hub(dataDir: String) : Listener {

    data class Event(val kind: String, val data: JSONObject)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 512)
    val events: SharedFlow<Event> = _events

    private val _connection = MutableStateFlow("idle")
    val connection: StateFlow<String> = _connection

    val engine: Messenger = Core.newMessenger(dataDir, this)

    override fun onEvent(kind: String?, payload: String?) {
        val k = kind ?: return
        val obj = runCatching { JSONObject(payload ?: "{}") }.getOrElse { JSONObject() }
        if (k == "connection") _connection.value = obj.optString("state", "idle")
        if (k == "unpaired") _connection.value = "idle"
        _events.tryEmit(Event(k, obj))
    }

    /** Runs an engine call off the main thread and wraps failures. */
    suspend fun <T> io(block: (Messenger) -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block(engine) } }
}

data class Conv(
    val id: String,
    val name: String,
    val preview: String,
    val fromMe: Boolean,
    val ts: Long,
    val unread: Boolean,
    val group: Boolean,
    val rcs: Boolean,
    val color: String,
) {
    companion object {
        fun from(o: JSONObject) = Conv(
            id = o.optString("id"),
            name = o.optString("name").ifBlank { "Unknown" },
            preview = o.optString("preview"),
            fromMe = o.optBoolean("fromMe"),
            ts = o.optLong("ts"),
            unread = o.optBoolean("unread"),
            group = o.optBoolean("group"),
            rcs = o.optBoolean("rcs"),
            color = o.optString("color"),
        )

        fun list(json: String): List<Conv> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { from(arr.getJSONObject(it)) }
        }
    }
}

data class Msg(
    val id: String,
    val tmpId: String,
    val conversationId: String,
    val fromMe: Boolean,
    val text: String,
    val media: String,
    val ts: Long,
    val status: String,
    val sender: String,
    val reactions: String,
) {
    val failed get() = status.contains("FAILED")
    val pending get() = status.contains("SENDING") || status.contains("YET_TO_SEND") || status == "LOCAL"

    companion object {
        fun from(o: JSONObject): Msg {
            val r = o.optJSONArray("reactions")
            val reactions = if (r == null) "" else (0 until r.length()).joinToString(" ") {
                val e = r.getJSONObject(it)
                val n = e.optInt("count")
                e.optString("emoji") + if (n > 1) "$n" else ""
            }
            return Msg(
                id = o.optString("id"),
                tmpId = o.optString("tmpId"),
                conversationId = o.optString("conversationId"),
                fromMe = o.optBoolean("fromMe"),
                text = o.optString("text"),
                media = o.optString("media"),
                ts = o.optLong("ts"),
                status = o.optString("status"),
                sender = o.optString("sender"),
                reactions = reactions,
            )
        }

        fun list(json: String): List<Msg> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { from(arr.getJSONObject(it)) }
        }
    }
}
