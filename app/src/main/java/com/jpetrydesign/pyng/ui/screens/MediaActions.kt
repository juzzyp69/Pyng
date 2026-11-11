package com.jpetrydesign.pyng.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jpetrydesign.pyng.data.MediaRef
import com.jpetrydesign.pyng.data.Repo
import kotlinx.coroutines.delay
import java.io.File

/** Where a downloaded attachment lives, or "" if it isn't on the phone yet. */
fun Repo.pathOf(ref: MediaRef): String = ref.localPath.ifEmpty { media[ref.id].orEmpty() }

/**
 * The files behind [refs], downloading any that aren't here yet (waits up to a minute).
 * Returns the ones that made it, as (path, attachment).
 */
suspend fun Repo.filesFor(refs: List<MediaRef>): List<Pair<String, MediaRef>> {
    val missing = refs.filter { pathOf(it).isEmpty() }
    if (missing.isNotEmpty()) {
        toast(if (missing.size == 1) "Downloading…" else "Downloading ${missing.size}…")
        missing.forEach { ensureMedia(it, manual = true) }
        var waited = 0
        while (refs.any { pathOf(it).isEmpty() } && waited < 60_000) { delay(400); waited += 400 }
    }
    return refs.mapNotNull { r -> pathOf(r).takeIf { it.isNotEmpty() && File(it).exists() }?.let { it to r } }
}

/** One share sheet for one or many attachments. */
fun shareFiles(ctx: Context, files: List<Pair<String, MediaRef>>) {
    if (files.isEmpty()) return
    runCatching {
        val uris = ArrayList(files.map { (p, _) -> FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(p)) })
        val mimes = files.map { it.second.mime.ifBlank { "*/*" } }.distinct()
        val type = mimes.singleOrNull() ?: if (mimes.all { it.startsWith("image/") }) "image/*" else if (mimes.all { it.startsWith("image/") || it.startsWith("video/") }) "*/*" else "*/*"
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        ctx.startActivity(Intent.createChooser(send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
    }.onFailure { Toast.makeText(ctx, "Couldn't share", Toast.LENGTH_SHORT).show() }
}

/** Saves to Pictures, Movies or Downloads (in a Messages folder), with one toast for the lot. */
fun saveFiles(ctx: Context, files: List<Pair<String, MediaRef>>) {
    if (files.isEmpty()) return
    var ok = 0
    files.forEach { (p, r) -> if (saveOne(ctx, p, r.mime, r.name)) ok++ }
    val kinds = files.map { it.second }
    val what = when {
        kinds.all { it.isImage } -> if (ok == 1) "photo" else "photos"
        kinds.all { it.isVideo } -> if (ok == 1) "video" else "videos"
        else -> if (ok == 1) "file" else "files"
    }
    Toast.makeText(ctx, if (ok == 0) "Couldn't save" else if (ok < files.size) "Saved $ok of ${files.size}" else "Saved $ok $what", Toast.LENGTH_SHORT).show()
}

private fun saveOne(ctx: Context, path: String, mime: String, name: String): Boolean = runCatching {
    val video = mime.startsWith("video/")
    val image = mime.startsWith("image/")
    val ext = mime.substringAfter('/').substringBefore(';').substringBefore('+').ifBlank { if (video) "mp4" else "jpg" }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, if (!image && !video && name.isNotBlank()) name else "Messages-" + System.nanoTime() + "." + ext)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/Messages" else if (image) "Pictures/Messages" else "Download/Messages")
    }
    val where: Uri = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val uri = ctx.contentResolver.insert(where, values)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { out -> File(path).inputStream().use { it.copyTo(out) } }
    true
}.getOrDefault(false)

/** Web links in a message's text, cleaned of trailing punctuation. */
fun linksIn(text: String): List<String> =
    Regex("https?://[^\\s]+").findAll(text).map { it.value.trimEnd('.', ',', ')', '!', '?', ';', ':', '"', '\'') }.distinct().toList()
