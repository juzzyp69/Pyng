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
    const val CH_LINK = "link"
    const val KEY_REPLY = "reply"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages"
        })
        nm.createNotificationChannel(NotificationChannel(CH_LINK, "Connection to Google Messages", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Keeps Messages connected so new texts arrive right away"
            setShowBadge(false)
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

    fun show(ctx: Context, repo: Repo, conv: Conv, m: Msg) {
        val p = repo.prefs.state
        if (!p.on("notif") || conv.id in p.muted || !canPost(ctx)) return
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

        val avatarPath = repo.avatars[conv.avatarId].orEmpty()
        val icon = circle(if (avatarPath.isNotEmpty()) BitmapFactory.decodeFile(avatarPath) else null)
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
        val open = PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java).setAction("open").putExtra("chat", conv.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
            .setOnlyAlertOnce(false)
        if (!p.on("vibrate") || alerts?.vibrate == "Off") b.setVibrate(longArrayOf(0L))
        if (p.on("quick")) {
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
        runCatching { NotificationManagerCompat.from(ctx).notify(conv.id, 1, b.build()) }
    }

    fun cancel(ctx: Context, convId: String) {
        history.remove(convId)
        runCatching { NotificationManagerCompat.from(ctx).cancel(convId, 1) }
    }

    fun linkNotification(ctx: Context) = NotificationCompat.Builder(ctx, CH_LINK)
        .setSmallIcon(R.drawable.ic_stat_message)
        .setContentTitle("Connected to Google Messages")
        .setContentText("New texts arrive right away")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()
}

/** Handles Reply and Mark as read from a notification. */
class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as PyngApp
        val chat = intent.getStringExtra("chat") ?: return
        when (intent.action) {
            "reply" -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    app.repo.send(chat, text)
                    app.repo.markRead(chat)
                }
                Notifier.cancel(ctx, chat)
            }
            "read" -> { app.repo.markRead(chat); Notifier.cancel(ctx, chat) }
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
            if (!app.repo.paired || !app.repo.prefs.state.on("background")) return
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
