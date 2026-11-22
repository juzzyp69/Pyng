package com.jpetrydesign.pyng.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person as NPerson
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jpetrydesign.pyng.MainActivity
import com.jpetrydesign.pyng.PyngApp
import com.jpetrydesign.pyng.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Message notifications, with reply and mark-as-read actions. */
object Notifier {
    const val CH_MESSAGES = "messages"
    const val CH_LINK = "link-hidden"
    /** Reminders and saved files: shown, but never counted on the app icon. Only unread chats are. */
    const val CH_QUIET = "pyng-other"
    const val KEY_REPLY = "reply"

    /** A chat's own channel (its sound and importance), once you've set one up; otherwise the shared one. */
    fun chatChannel(ctx: Context, convId: String): String {
        if (android.os.Build.VERSION.SDK_INT < 30) return CH_MESSAGES
        val id = "msg:$convId"
        return if (ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(id) != null) id else CH_MESSAGES
    }

    /**
     * Opens Android's sound settings for one chat. On Android 11 and up each chat can have its own
     * sound; the chat's channel is made the first time, starting from your usual message settings.
     */
    fun openChatSound(ctx: Context, convId: String, title: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val i = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val id = "msg:$convId"
            if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(NotificationChannel(id, title, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Texts from $title"
                setConversationId(CH_MESSAGES, convId)
            })
            i.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, id).putExtra(android.provider.Settings.EXTRA_CONVERSATION_ID, convId)
        } else i.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CH_MESSAGES)
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages"
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUIET, "Reminders and saved files", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Message reminders and files you save. These don't count on the app icon."
            setShowBadge(false)
        })
        runCatching { nm.deleteNotificationChannel("saves") }
        // Android needs a notification for the background connection. Its channel starts switched
        // off, so nothing shows; only texts from your chats notify.
        runCatching { nm.deleteNotificationChannel("link") }
        nm.createNotificationChannel(NotificationChannel(CH_LINK, "Background connection", NotificationManager.IMPORTANCE_NONE).apply {
            description = "Keeps Messages connected so new texts arrive right away. Off by default, so it never shows."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        })
    }

    private val history = mutableMapOf<String, MutableList<Pair<String, Msg>>>()
    /** New texts in each chat since Pyng was last opened, for the app icon's count. */
    private val counts = mutableMapOf<String, Int>()

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun circle(src: Bitmap?): Bitmap? {
        src ?: return null
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Path().apply { addOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), Path.Direction.CW) }
        c.clipPath(p)
        c.drawBitmap(src, ((size - src.width) / 2).toFloat(), ((size - src.height) / 2).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /**
     * A photo stand-in for people without one: their initials on a soft color picked from their
     * name, so the same person always gets the same color. [square] fills the whole tile for
     * Android's conversation icons, which crop it themselves.
     */
    fun initials(name: String, square: Boolean = false): Bitmap {
        val tones = listOf(0xFFDCE7FB to 0xFF1E5FD1, 0xFFF9DDE6 to 0xFFB8325E, 0xFFDFF1E6 to 0xFF2E7A4F, 0xFFEDE3FA to 0xFF6B3FB0,
            0xFFFCE8D6 to 0xFFB25A1C, 0xFFDDF0F3 to 0xFF16707E, 0xFFF3EFD9 to 0xFF7C6812, 0xFFE6E8EE to 0xFF41485A)
        val (bg, ink) = tones[Math.floorMod(name.trim().lowercase().hashCode(), tones.size)]
        val size = 192
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.toInt() }
        if (square) c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), fill) else c.drawCircle(size / 2f, size / 2f, size / 2f, fill)
        val ini = com.jpetrydesign.pyng.ui.kit.initialsOf(name).ifBlank { "#" }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink.toInt(); textAlign = Paint.Align.CENTER
            textSize = if (square) 56f else 74f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        c.drawText(ini, size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2, tp)
        return b
    }

    /** When each chat last made a sound, so the same text arriving twice only alerts once. */
    private val lastAlert = mutableMapOf<String, Pair<Long, String>>()
    private val placeholders = mutableMapOf<String, String>()

    private fun notifText(m: Msg): String = when {
        m.text.isNotEmpty() -> Tapbacks.summary(m.text) ?: m.text.removePrefix("Replied to a message:").trim().ifEmpty { m.text }
        m.summary == "Photo" || m.summary == "GIF" -> "📷 " + m.summary
        m.summary == "Video" -> "🎬 Video"
        m.summary == "Voice message" -> "🎤 Voice message"
        m.summary.isNotEmpty() -> "📎 " + m.summary
        else -> ""
    }

    /** Puts a verification code on the clipboard, marked sensitive so it isn't shown in previews. */
    fun copyCode(ctx: Context, code: String) {
        runCatching {
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            val clip = android.content.ClipData.newPlainText("Verification code", code)
            clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            cm.setPrimaryClip(clip)
        }
    }

    /** Photos Google Messages' own alert carried, by chat, for when Pyng has none of its own yet. */
    private val googlePhotos = mutableMapOf<String, Bitmap>()

    /**
     * The photo for a person or chat, as a circle: one you picked in Pyng, then their contact card's photo,
     * then the one Google Messages has, then the one from Google's alert. Null when there isn't one.
     */
    private fun photoFor(ctx: Context, repo: Repo, conv: Conv, personId: String? = null): Bitmap? {
        val p = repo.prefs.state
        if (personId == null && conv.group) return circle(loadBitmap(ctx, p.opt("gphoto:" + conv.id))) ?: googlePhotos[conv.id]
        val id = personId ?: conv.avatarId
        val person = conv.people.firstOrNull { it.id == id }
        val paths = listOf(p.opt("cphoto:$id"), repo.avatars[id].orEmpty()) +
            listOfNotNull(person?.let { repo.contactPhoto(it.number.ifBlank { it.formatted }) })
        paths.forEach { path -> circle(loadBitmap(ctx, path))?.let { return it } }
        return if (personId == null) googlePhotos[conv.id] else null
    }

    fun rememberGooglePhoto(convId: String, photo: Bitmap?) { if (photo != null) googlePhotos[convId] = circle(photo) ?: photo }

    fun show(ctx: Context, repo: Repo, conv: Conv, m: Msg) {
        val p = repo.prefs.state
        // A new code goes straight to the clipboard if you turned that on, so the keyboard can offer it.
        val otp = if (!m.fromMe) com.jpetrydesign.pyng.ui.screens.detect(m.text).code else null
        if (otp != null && p.on("otpAuto")) copyCode(ctx, otp)
        if (!p.on("notif") || conv.id in p.muted || !canPost(ctx)) return
        // Texts with a word you blocked go to Spam quietly; chats in Trash wait there too.
        if (!m.fromMe && repo.hasBlockedWord(m.text)) return
        val hush = quietNow(p) && !(p.on("quietFavs") && p.isPinned(conv.id))
        // Already shown from Google Messages' notification a moment ago? Update quietly.
        val prev = lastAlert[conv.id]
        val dupe = prev != null && System.currentTimeMillis() - prev.first < 20_000 && prev.second.trim() == m.text.trim()
        if (dupe) {
            history[conv.id]?.let { h -> if (h.isNotEmpty() && h.last().second.text.trim() == m.text.trim()) h.removeAt(h.lastIndex) }
        }
        lastAlert[conv.id] = System.currentTimeMillis() to m.text
        if (!dupe && !m.fromMe) counts[conv.id] = (counts[conv.id] ?: 0) + 1
        val alerts = p.alerts[conv.id]
        if (alerts?.on == false) return
        val body = notifText(m).ifEmpty { return }
        val list = history.getOrPut(conv.id) { mutableListOf() }
        val senderName = if (conv.group) m.sender.ifBlank { "Someone" } else conv.title
        list += senderName to m
        while (list.size > 7) list.removeAt(0)

        val photo = photoFor(ctx, repo, conv)
        // No photo: their initials in a circle, never a blank or a plus sign.
        val icon = photo ?: initials(conv.title)
        val me = NPerson.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(if (conv.group) conv.title else null).setGroupConversation(conv.group)
        list.forEach { (who, msg) ->
            val pb = NPerson.Builder().setName(who).setKey(msg.participantId.ifEmpty { who })
            // In a group, each line shows who sent it: their photo, or their initials.
            pb.setIcon(IconCompat.createWithBitmap(if (!conv.group) icon else photoFor(ctx, repo, conv, msg.participantId.ifEmpty { "-" }) ?: initials(who)))
            style.addMessage(
                notifText(msg).ifEmpty { "📎 Attachment" },
                if (msg.ts > 0) msg.ts else System.currentTimeMillis(), pb.build(),
            )
        }
        val code = conv.id.hashCode()
        val placeholder = conv.id.startsWith("gm:")
        // A real text for a chat that was only a stand-in (from Google Messages' alert, before Pyng had
        // the text): drop the stand-in, matched by who it's from or by the text, so it can't get stuck.
        if (!placeholder) dropStandIns(ctx, conv, m.text)
        else placeholders[conv.id] = m.text
        val open = PendingIntent.getActivity(
            ctx, code,
            (if (placeholder) Intent(ctx, MainActivity::class.java) else Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", conv.id)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, chatChannel(ctx, conv.id))
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setContentTitle(conv.title)
            .setContentText(body)
            .setLargeIcon(icon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShortcutId(conv.id)
            .setContentIntent(open)
            // Each new text counts on the app icon: 3 from one person shows 3. Opening Pyng clears it.
            .setNumber((counts[conv.id] ?: 0).coerceAtLeast(1))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setOnlyAlertOnce(dupe)
            .setSilent(dupe || hush)
        if (!p.on("vibrate") || alerts?.vibrate == "Off") b.setVibrate(longArrayOf(0L))
        // Chat bubbles: Android floats the chat over other apps when you allow bubbles for Pyng.
        if (android.os.Build.VERSION.SDK_INT >= 30 && p.on("bubbles") && !placeholder) runCatching {
            val bubble = PendingIntent.getActivity(
                ctx, code + 7, Intent(ctx, com.jpetrydesign.pyng.BubbleActivity::class.java).setAction("bubble:" + conv.id).putExtra("chat", conv.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            b.setBubbleMetadata(NotificationCompat.BubbleMetadata.Builder(bubble, IconCompat.createWithBitmap(icon)).setDesiredHeight(640).build())
        }
        if (otp != null && !placeholder) {
            val copyPi = PendingIntent.getBroadcast(
                ctx, code + 3, Intent(ctx, NotifyReceiver::class.java).setAction("copy").putExtra("chat", conv.id).putExtra("code", otp),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_stat_check, "Copy $otp", copyPi).setShowsUserInterface(false).build())
        }
        if (p.on("quick") && !placeholder) {
            val replyPi = PendingIntent.getBroadcast(
                ctx, code + 1, Intent(ctx, NotifyReceiver::class.java).setAction("reply").putExtra("chat", conv.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val ri = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_stat_message, "Reply", replyPi).addRemoteInput(ri).setAllowGeneratedReplies(true).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build())
            val readPi = PendingIntent.getBroadcast(
                ctx, code + 2, Intent(ctx, NotifyReceiver::class.java).setAction("read").putExtra("chat", conv.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_stat_check, "Mark as read", readPi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build())
        }
        // Registering the chat as a conversation puts it in Android's Conversations section, with the photo.
        if (!placeholder) runCatching {
            val person = NPerson.Builder().setName(conv.title).setKey(conv.id).setIcon(IconCompat.createWithBitmap(icon)).build()
            val sc = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, conv.id)
                .setShortLabel(conv.shortTitle.ifBlank { conv.title }).setLongLabel(conv.title)
                // A plain round picture: an "adaptive" one gets reshaped into the phone's app-icon squircle.
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", conv.id))
                .setLongLived(true).setPerson(person)
                .setCategories(setOf(com.jpetrydesign.pyng.ui.screens.SHARE_CATEGORY, "android.shortcut.conversation"))
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(ctx, sc)
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(conv.id, 1, b.build()) }
    }

    private fun loadBitmap(ctx: Context, path: String): Bitmap? = runCatching {
        when {
            path.isBlank() -> null
            path.startsWith("content://") -> ctx.contentResolver.openInputStream(android.net.Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
            else -> BitmapFactory.decodeFile(path)
        }
    }.getOrNull()

    /**
     * Shows a text using what Google Messages' own notification said, for when Pyng isn't
     * connected. Finds the matching chat so mute, photos and reply all work.
     */
    fun showFromGoogle(ctx: Context, repo: Repo, conv: Conv?, title: String, sender: String, text: String, ts: Long, photo: Bitmap? = null) {
        val c = conv ?: Conv.placeholder("gm:" + title, title)
        rememberGooglePhoto(c.id, photo)
        val m = Msg("", "gm-" + ts, c.id, "", sender, sender.substringBefore(' '), false, "INCOMING_COMPLETE", "", text, emptyList(), ts, emptyList(), "", 0, "text")
        show(ctx, repo, c, m)
    }

    fun cancel(ctx: Context, convId: String, title: String? = null) {
        history.remove(convId); counts.remove(convId)
        runCatching { NotificationManagerCompat.from(ctx).cancel(convId, 1) }
        if (title != null) dropStandIns(ctx, title)
    }

    /** Removes stand-in alerts for a chat: by its name, or (when given) by the same text. */
    private fun dropStandIns(ctx: Context, conv: Conv, text: String) {
        val names = setOf(conv.title.trim().lowercase(), conv.shortTitle.trim().lowercase()).filter { it.isNotEmpty() }
        placeholders.entries.removeAll { (pid, txt) ->
            val who = pid.removePrefix("gm:").trim().lowercase()
            (who in names || txt.trim() == text.trim()).also { if (it) runCatching { NotificationManagerCompat.from(ctx).cancel(pid, 1) } }
        }
        names.forEach { n -> runCatching { NotificationManagerCompat.from(ctx).cancel("gm:$n", 1) } }
    }

    private fun dropStandIns(ctx: Context, title: String) {
        val n = title.trim().lowercase()
        placeholders.entries.removeAll { (pid, _) -> (pid.removePrefix("gm:").trim().lowercase() == n).also { if (it) runCatching { NotificationManagerCompat.from(ctx).cancel(pid, 1) } } }
    }

    /**
     * Makes what's in the notification shade match what's actually unread: any chat that's been read
     * (here, in Google Messages, or on another device) loses its alert, and so does any stand-in whose
     * chat is read. Runs when Pyng opens and after chats sync, so the app icon's count is never stuck.
     */
    fun tidy(ctx: Context, repo: Repo) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val active = runCatching { nm.activeNotifications }.getOrNull() ?: return
        fun named(n: String) = repo.convs.values.filter { it.title.trim().lowercase() == n || it.shortTitle.trim().lowercase() == n }
        for (sbn in active) {
            val tag = sbn.tag ?: continue
            if (sbn.id != 1) continue
            if (tag.startsWith("gm:")) {
                val who = tag.removePrefix("gm:").trim().lowercase()
                // A stand-in goes once its chat has caught up with it and been read, or after a day.
                val chats = named(who)
                val caughtUp = chats.isNotEmpty() && chats.none { it.unread } && chats.any { it.ts >= sbn.postTime - 120_000 }
                if (caughtUp || System.currentTimeMillis() - sbn.postTime > 24L * 3600 * 1000) {
                    placeholders.remove(tag); runCatching { nm.cancel(tag, 1) }
                }
            } else {
                val c = repo.convs[tag] ?: continue
                if (!c.unread && tag !in repo.prefs.state.markedUnread) { history.remove(tag); counts.remove(tag); runCatching { nm.cancel(tag, 1) } }
            }
        }
    }

    /**
     * Opening Pyng: every text alert leaves the shade and the app icon's count goes back to zero.
     * Nothing is marked read; the chats stay unread in Pyng until you open them.
     */
    fun clearAll(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val active = runCatching { nm.activeNotifications }.getOrNull() ?: return
        for (sbn in active) {
            val tag = sbn.tag ?: continue
            if (sbn.id == 1) runCatching { nm.cancel(tag, 1) }
        }
        history.clear(); counts.clear(); placeholders.clear()
    }

    fun linkNotification(ctx: Context) = NotificationCompat.Builder(ctx, CH_LINK)
        .setSmallIcon(R.drawable.ic_stat_message)
        .setContentTitle("Pyng")
        .setContentText("Connected")
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()
}

/** Handles Reply and Mark as read from a notification. */
class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as PyngApp
        val chat = intent.getStringExtra("chat") ?: return
        if (intent.action == "copy") {
            val c = intent.getStringExtra("code") ?: return
            Notifier.copyCode(ctx, c)
            // Android 13 and up confirms copies itself.
            if (Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(ctx, "Copied $c", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString().orEmpty()
        Notifier.cancel(ctx, chat)
        if (chat.startsWith("gm:")) { Link.openFallback(ctx); return }
        val pending = goAsync()
        app.appScope.launch {
            // Pyng may be asleep; connect, do the action, then let it sleep again.
            Link.wake(app, "notification action")
            kotlinx.coroutines.withTimeoutOrNull(12_000) { while (!app.hub.engine.isConnected) delay(250) }
            when (intent.action) {
                "reply" -> if (text.isNotBlank()) { app.repo.send(chat, text); app.repo.markRead(chat) }
                "read" -> app.repo.markRead(chat)
            }
            pending.finish()
        }
    }
}

/** Keeps the connection open while the app is in the background, so texts arrive. */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        runCatching { ServiceCompat.startForeground(this, 77, Notifier.linkNotification(this), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        val app = application as PyngApp
        app.appScope.launch { app.hub.io { if (it.isPaired) it.start() } }
        return START_STICKY
    }

    companion object {
        fun start(ctx: Context) {
            val app = ctx.applicationContext as PyngApp
            if (!app.repo.paired || !app.repo.prefs.state.on("alwaysOn")) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, LinkService::class.java)) }
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, LinkService::class.java)) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) LinkService.start(ctx)
    }
}

/** Sends a scheduled message when its time comes. */
class ScheduleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PyngApp
        val id = inputData.getString("id") ?: return Result.failure()
        val item = app.repo.prefs.state.scheduled.firstOrNull { it.id == id } ?: return Result.success()
        app.hub.io { if (it.isPaired) it.start() }
        delay(1500)
        val r = app.hub.io { it.sendText(item.convId, item.text, "") }
        return if (r.isSuccess) {
            app.repo.prefs.edit { p -> p.copy(scheduled = p.scheduled.filter { it.id != id }) }
            Result.success()
        } else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }

    companion object {
        fun plan(ctx: Context, s: Scheduled) {
            val delayMs = (s.at - System.currentTimeMillis()).coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<ScheduleWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("id" to s.id))
                .addTag("sched-" + s.id)
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }

        fun cancel(ctx: Context, id: String) { WorkManager.getInstance(ctx).cancelAllWorkByTag("sched-$id") }
    }
}


/**
 * When Pyng is connected to Google Messages. By default it connects while the app is open and
 * for a short while after a text wakes it, then disconnects, so nothing runs in the background.
 * "Always connected" in Settings keeps it connected instead.
 */
object Link {
    private var sleepJob: kotlinx.coroutines.Job? = null
    private const val AWAKE_MS = 45_000L

    fun foreground(app: PyngApp) {
        sleepJob?.cancel()
        app.appScope.launch { app.hub.io { it.setForeground(true); if (it.isPaired) it.start() } }
        LinkService.start(app)
    }

    fun background(app: PyngApp) {
        app.appScope.launch { app.hub.io { it.setForeground(false) } }
        if (app.repo.prefs.state.on("alwaysOn")) return
        sleepLater(app)
    }

    /** Connects for a little while, e.g. after a text arrives or a notification button is tapped. */
    fun wake(app: PyngApp, why: String) {
        if (!app.repo.paired || app.repo.demo) return
        app.appScope.launch {
            app.hub.io { if (it.isPaired) it.start() }
            app.repo.refreshQuietly()
        }
        if (!app.repo.foreground && !app.repo.prefs.state.on("alwaysOn")) sleepLater(app)
    }

    private fun sleepLater(app: PyngApp) {
        sleepJob?.cancel()
        sleepJob = app.appScope.launch {
            delay(AWAKE_MS)
            if (!app.repo.foreground && !app.repo.prefs.state.on("alwaysOn")) app.hub.io { it.stop() }
        }
    }

    fun openFallback(ctx: Context) {
        runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun listenerOn(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}

/**
 * Wakes Pyng when Google Messages gets a text. Google Messages is already woken by Google's push
 * service for every text, so Pyng doesn't need its own connection running: it reads the alert,
 * shows its own notification (your photos, mute rules, quick reply) and hides the original.
 */
class GmListener : android.service.notification.NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.packageName != GM) return
        val n = sbn.notification ?: return
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style == null && n.category != android.app.Notification.CATEGORY_MESSAGE) return
        val app = applicationContext as PyngApp
        val repo = app.repo
        if (!repo.paired) return
        val last = style?.messages?.lastOrNull()
        val title = (style?.conversationTitle ?: n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE))?.toString().orEmpty()
        val sender = last?.person?.name?.toString() ?: title
        val text = (last?.text ?: n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (text.isBlank()) return
        // Android hides codes and other sensitive texts from apps like Pyng that read notifications. When it has,
        // leave Google Messages' alert alone (it has the real text) and just wake Pyng to fetch the message itself.
        if (redacted(text)) { Link.wake(app, "hidden text from Google Messages"); return }
        val conv = repo.matchConversation(sbn.notification.shortcutId, title, sender)

        // Pyng shows its own version. Google Messages' copy goes away only if you turned that on.
        val live = app.hub.engine.isConnected
        // The photo Google's alert shows (the contact's picture), kept for Pyng's own alert.
        val pic = if (style?.isGroupConversation == true) null else runCatching {
            val ic = last?.person?.icon?.toIcon(this) ?: n.getLargeIcon()
            (ic?.loadDrawable(this) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: ic?.loadDrawable(this)?.let { d -> android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888).also { b -> val cv = android.graphics.Canvas(b); d.setBounds(0, 0, 192, 192); d.draw(cv) } }
        }.getOrNull()
        conv?.let { Notifier.rememberGooglePhoto(it.id, pic) }
        if (!live) Notifier.showFromGoogle(this, repo, conv, if (style?.isGroupConversation == true) title else sender.ifBlank { title }, sender, text, last?.timestamp ?: System.currentTimeMillis(), pic)
        if (repo.prefs.state.on("hideGmAlerts") && Notifier.canPost(this)) runCatching { cancelNotification(sbn.key) }
        Link.wake(app, "text from Google Messages")
    }

    /** True when Android swapped the text for its "content hidden" placeholder. */
    private fun redacted(text: String): Boolean {
        val sys = runCatching {
            val id = android.content.res.Resources.getSystem().getIdentifier("redacted_notification_message", "string", "android")
            if (id != 0) android.content.res.Resources.getSystem().getString(id) else null
        }.getOrNull()
        return (sys != null && text.trim() == sys.trim()) || text.contains("content hidden", ignoreCase = true)
    }

    companion object { const val GM = "com.google.android.apps.messaging" }
}

/** "Remind me" on a message: a notification later that opens the chat right at that message. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val conv = inputData.getString("conv") ?: return Result.failure()
        val msg = inputData.getString("msg").orEmpty()
        val title = inputData.getString("title").orEmpty().ifBlank { "A message" }
        val text = inputData.getString("text").orEmpty()
        Notifier.channels(ctx)
        val open = PendingIntent.getActivity(
            ctx, ("remind" + conv + msg).hashCode(),
            Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", conv).putExtra("msg", msg).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, Notifier.CH_QUIET)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Reminder · $title")
            .setContentText(text.ifBlank { "Tap to see the message" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { "Tap to see the message" }))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify("remind:$conv:$msg", 7, n) }
        return Result.success()
    }

    companion object {
        fun plan(ctx: Context, convId: String, msgId: String, title: String, text: String, at: Long) {
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("conv" to convId, "msg" to msgId, "title" to title, "text" to text.take(300)))
                .addTag("remind-$convId-$msgId")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("remind-$convId-$msgId", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        /** The three choices, as (label, time): an hour from now, tonight at 8, tomorrow at 9. */
        fun choices(now: Long = System.currentTimeMillis()): List<Pair<String, Long>> {
            val cal = java.util.Calendar.getInstance()
            fun at(daysAhead: Int, hour: Int): Long {
                cal.timeInMillis = now
                cal.add(java.util.Calendar.DAY_OF_YEAR, daysAhead)
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }
            val tonight = at(0, 20)
            return listOfNotNull(
                "In 1 hour" to now + 60 * 60_000L,
                ("Tonight at 8 PM" to tonight).takeIf { tonight - now > 90 * 60_000L },
                "Tomorrow at 9 AM" to at(1, 9),
            )
        }
    }
}

/** True during your quiet hours (they can cross midnight, like 10:00 PM to 7:00 AM). */
fun quietNow(p: AppPrefs, now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
    if (!p.on("quiet")) return false
    fun mins(s: String) = s.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val from = mins(p.opt("quietFrom")); val to = mins(p.opt("quietTo"))
    val n = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    return if (from <= to) n in from until to else n >= from || n < to
}
