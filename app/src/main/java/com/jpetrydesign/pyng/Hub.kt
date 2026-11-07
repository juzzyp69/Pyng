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
import org.json.JSONObject

/** One engine for the whole app, plus a stream of everything it reports. */
class Hub(dataDir: String) : Listener {

    data class Event(val kind: String, val data: JSONObject)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1024)
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
