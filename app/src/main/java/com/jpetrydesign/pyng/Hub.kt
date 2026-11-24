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

    // The saved link to Google Messages is sealed with a key the phone's secure hardware keeps.
    val engine: Messenger = Core.newSecureMessenger(dataDir, SessionKey.get(dataDir), this)

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

/**
 * The key Pyng's saved Google Messages link is sealed with. It's random, and stored only wrapped by a
 * key that never leaves the phone's secure hardware (Android Keystore), so a copy of Pyng's files is no
 * use on its own. If anything here fails, Pyng carries on unsealed rather than losing the link.
 */
object SessionKey {
    private const val ALIAS = "pyng_session_v1"

    fun get(dir: String): String = runCatching {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val wrap = (ks.getKey(ALIAS, null) as? javax.crypto.SecretKey) ?: javax.crypto.KeyGenerator
            .getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
                generateKey()
            }
        val f = java.io.File(dir, "session.key")
        val raw: ByteArray = f.takeIf { it.exists() }?.let { file ->
            runCatching {
                val b = file.readBytes()
                val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                c.init(javax.crypto.Cipher.DECRYPT_MODE, wrap, javax.crypto.spec.GCMParameterSpec(128, b.copyOfRange(0, 12)))
                c.doFinal(b.copyOfRange(12, b.size))
            }.getOrNull()
        } ?: run {
            // First run (or the old key can't be read any more): make a new one.
            val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, wrap)
            val tmp = java.io.File(dir, "session.key.tmp")
            tmp.writeBytes(c.iv + c.doFinal(fresh))
            if (!tmp.renameTo(f)) error("couldn't save the key")
            fresh
        }
        android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
    }.getOrDefault("")
}
