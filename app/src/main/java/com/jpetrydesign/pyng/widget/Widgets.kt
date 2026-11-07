package com.jpetrydesign.pyng.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jpetrydesign.pyng.MainActivity
import com.jpetrydesign.pyng.PyngApp
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Notifier
import com.jpetrydesign.pyng.ui.kit.initialsOf
import com.jpetrydesign.pyng.ui.screens.listTime

private val Ink = Color(0xFF15171D)
private val Soft = Color(0x9415171D)
private val Blue = Color(0xFF1E5FD1)
private val Card = Color(0xE6FFFFFF)
private val Tone = Color(0xFFE3ECFB)

private fun avatar(ctx: Context, c: Conv): Bitmap {
    val app = ctx.applicationContext as PyngApp
    val path = app.repo.avatars[c.avatarId].orEmpty()
    val photo = if (path.isNotEmpty()) runCatching { BitmapFactory.decodeFile(path) }.getOrNull() else null
    Notifier.circle(photo)?.let { return Bitmap.createScaledBitmap(it, 128, 128, true) }
    val b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    val cv = Canvas(b)
    cv.drawCircle(64f, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3ECFB.toInt() })
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E5FD1.toInt(); textSize = 46f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    cv.drawText(initialsOf(c.title), 64f, 64f - (tp.descent() + tp.ascent()) / 2, tp)
    return b
}

private fun open(ctx: Context, id: String?) =
    actionStartActivity(Intent(ctx, MainActivity::class.java).apply { if (id == null) action = "new" else { action = "open"; putExtra("chat", id) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun unread(app: PyngApp, c: Conv) = c.unread || c.id in app.repo.prefs.state.markedUnread

/** Recent conversations, newest first. */
class ConversationsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PyngApp
        val convs = app.repo.convs.values.filter { it.status == "active" }.sortedByDescending { it.ts }.take(4)
        val newCount = app.repo.convs.values.count { it.status == "active" && unread(app, it) }
        val avatars = convs.associate { it.id to avatar(context, it) }
        provideContent {
            Column(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Card).padding(14.dp)) {
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(26.dp).cornerRadius(8.dp).background(Blue), contentAlignment = Alignment.Center) {
                        Text("•••", style = TextStyle(color = ColorProvider(Color.White), fontSize = 8.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Text("Messages", style = TextStyle(color = ColorProvider(Ink), fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                    if (newCount > 0) Text("$newCount new", style = TextStyle(color = ColorProvider(Blue), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.width(10.dp))
                    Box(GlanceModifier.size(30.dp).cornerRadius(15.dp).background(Blue).clickable(open(context, null)), contentAlignment = Alignment.Center) {
                        Text("+", style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    }
                }
                if (convs.isEmpty()) Text("Open Messages to load your chats", style = TextStyle(color = ColorProvider(Soft), fontSize = 13.sp))
                convs.forEach { c ->
                    val hot = unread(app, c)
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(open(context, c.id)), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(avatars.getValue(c.id)), contentDescription = null, modifier = GlanceModifier.size(38.dp))
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Row(GlanceModifier.fillMaxWidth()) {
                                Text(c.title, maxLines = 1, style = TextStyle(color = ColorProvider(Ink), fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                                Text(listTime(c.ts), style = TextStyle(color = ColorProvider(if (hot) Blue else Soft), fontSize = 11.sp, fontWeight = if (hot) FontWeight.Bold else FontWeight.Normal))
                            }
                            Text((if (c.fromMe) "You: " else "") + c.preview.replace("\n", " "), maxLines = 1, style = TextStyle(color = ColorProvider(Soft), fontSize = 12.5.sp))
                        }
                        if (hot) { Spacer(GlanceModifier.width(8.dp)); Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(Blue)) {} }
                    }
                }
            }
        }
    }
}

/** Your pinned people as a grid of faces with unread badges. */
class PinnedWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PyngApp
        val pins = app.repo.prefs.state.pinned.mapNotNull { app.repo.convs[it] }.filter { it.status == "active" }.take(8)
        val avatars = pins.associate { it.id to avatar(context, it) }
        provideContent {
            Column(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Card).padding(12.dp)) {
                if (pins.isEmpty()) Text("Pin chats in Messages to see them here", style = TextStyle(color = ColorProvider(Soft), fontSize = 13.sp))
                pins.chunked(4).forEach { row ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { c ->
                            Column(GlanceModifier.defaultWeight().clickable(open(context, c.id)), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(GlanceModifier.size(54.dp), contentAlignment = Alignment.TopEnd) {
                                    Image(ImageProvider(avatars.getValue(c.id)), contentDescription = c.title, modifier = GlanceModifier.size(54.dp))
                                    if (unread(app, c)) Box(GlanceModifier.size(16.dp).cornerRadius(8.dp).background(Blue)) {}
                                }
                                Spacer(GlanceModifier.height(4.dp))
                                Text(c.shortTitle, maxLines = 1, style = TextStyle(color = ColorProvider(Ink), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                        repeat(4 - row.size) { Spacer(GlanceModifier.defaultWeight()) }
                    }
                }
            }
        }
    }
}

class ConversationsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ConversationsWidget() }
class PinnedWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PinnedWidget() }

object Widgets {
    suspend fun refresh(ctx: Context) {
        runCatching {
            if (GlanceAppWidgetManager(ctx).getGlanceIds(ConversationsWidget::class.java).isNotEmpty()) ConversationsWidget().updateAll(ctx)
            if (GlanceAppWidgetManager(ctx).getGlanceIds(PinnedWidget::class.java).isNotEmpty()) PinnedWidget().updateAll(ctx)
        }
    }
}

@Suppress("unused")
@Composable
private fun keep() = Unit

@Suppress("unused")
private val keepTone = Tone
