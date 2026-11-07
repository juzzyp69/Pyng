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
    const val KEY_REPLY = "reply"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages"
        })
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

    /** When each chat last made a sound, so the same text arriving twice only alerts once. */
    private val lastAlert = mutableMapOf<String, Pair<Long, String>>()
    private val placeholders = mutableMapOf<String, String>()

    fun show(ctx: Context, repo: Repo, conv: Conv, m: Msg) {
        val p = repo.prefs.state
        if (!p.on("notif") || conv.id in p.muted || !canPost(ctx)) return
        // Already shown from Google Messages' notification a moment ago? Update quietly.
        val prev = lastAlert[conv.id]
        val dupe = prev != null && System.currentTimeMillis() - prev.first < 20_000 && prev.second.trim() == m.text.trim()
        if (dupe) {
            history[conv.id]?.let { h -> if (h.isNotEmpty() && h.last().second.text.trim() == m.text.trim()) h.removeAt(h.lastIndex) }
        }
        lastAlert[conv.id] = System.currentTimeMillis() to m.text
        val alerts = p.alerts[conv.id]
        if (alerts?.on == false) return
        val body = when {
            m.text.isNotEmpty() -> m.text
            m.media.any { it.isImage } -> "📷 Photo"
            m.media.any { it.isVideo } -> "🎬 Video"
            m.media.any { it.isAudio } -> "🎤 Voice message"
            m.media.isNotEmpty() -> "📎 " + m.media.first().name.ifBlank { "Attachment" }
            else -> return
        }
        val list = history.getOrPut(conv.id) { mutableListOf() }
        val senderName = if (conv.group) m.sender.ifBlank { "Someone" } else conv.title
        list += senderName to m
        while (list.size > 7) list.removeAt(0)

        val avatarPath = if (conv.group) repo.prefs.state.opt("gphoto:" + conv.id) else repo.prefs.state.opt("cphoto:" + conv.avatarId).ifBlank { repo.avatars[conv.avatarId].orEmpty() }
        val icon = circle(loadBitmap(ctx, avatarPath))
        val me = NPerson.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(if (conv.group) conv.title else null).setGroupConversation(conv.group)
        list.forEach { (who, msg) ->
            val pb = NPerson.Builder().setName(who).setKey(msg.participantId.ifEmpty { who })
            if (!conv.group && icon != null) pb.setIcon(IconCompat.createWithBitmap(icon))
            style.addMessage(
                msg.text.ifEmpty { if (msg.media.any { it.isImage }) "📷 Photo" else "📎 Attachment" },
                if (msg.ts > 0) msg.ts else System.currentTimeMillis(), pb.build(),
            )
        }
        val code = conv.id.hashCode()
        val placeholder = conv.id.startsWith("gm:")
        // A real text for a chat that was only a placeholder: drop the placeholder alert.
        if (!placeholder) placeholders.entries.removeAll { (pid, txt) -> (txt.trim() == m.text.trim()).also { same -> if (same) runCatching { NotificationManagerCompat.from(ctx).cancel(pid, 1) } } }
        else placeholders[conv.id] = m.text
        val open = PendingIntent.getActivity(
            ctx, code,
            (if (placeholder) Intent(ctx, MainActivity::class.java) else Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", conv.id)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, CH_MESSAGES)
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
            .setOnlyAlertOnce(dupe)
            .setSilent(dupe)
        if (!p.on("vibrate") || alerts?.vibrate == "Off") b.setVibrate(longArrayOf(0L))
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
            val person = NPerson.Builder().setName(conv.title).setKey(conv.id).apply { if (icon != null) setIcon(IconCompat.createWithBitmap(icon)) }.build()
            val sc = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, conv.id)
                .setShortLabel(conv.shortTitle.ifBlank { conv.title }).setLongLabel(conv.title)
                .setIcon(if (icon != null) IconCompat.createWithAdaptiveBitmap(icon) else IconCompat.createWithResource(ctx, R.drawable.ic_shortcut_new))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", conv.id))
                .setLongLived(true).setPerson(person)
                .setCategories(setOf("android.shortcut.conversation"))
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
    fun showFromGoogle(ctx: Context, repo: Repo, conv: Conv?, title: String, sender: String, text: String, ts: Long) {
        val c = conv ?: Conv.placeholder("gm:" + title, title)
        val m = Msg("", "gm-" + ts, c.id, "", sender, sender.substringBefore(' '), false, "INCOMING_COMPLETE", "", text, emptyList(), ts, emptyList(), "", 0, "text")
        show(ctx, repo, c, m)
    }

    fun cancel(ctx: Context, convId: String) {
        history.remove(convId)
        runCatching { NotificationManagerCompat.from(ctx).cancel(convId, 1) }
    }

    fun linkNotification(ctx: Context) = NotificationCompat.Builder(ctx, CH_LINK)
        .setSmallIcon(R.drawable.ic_stat_message)
        .setContentTitle("Messages")
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
        app.appScope.launch { app.hub.io { if (it.isPaired) it.start() } }
        LinkService.start(app)
    }

    fun background(app: PyngApp) {
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
        val conv = repo.matchConversation(sbn.notification.shortcutId, title, sender)

        // Pyng shows its own version; Google Messages' copy goes away.
        val live = app.hub.engine.isConnected
        if (!live) Notifier.showFromGoogle(this, repo, conv, if (style?.isGroupConversation == true) title else sender.ifBlank { title }, sender, text, last?.timestamp ?: System.currentTimeMillis())
        if (repo.prefs.state.on("replaceGm") && Notifier.canPost(this)) runCatching { cancelNotification(sbn.key) }
        Link.wake(app, "text from Google Messages")
    }

    companion object { const val GM = "com.google.android.apps.messaging" }
}
