package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.BuildConfig
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.T

/** One update: its version, when it went out, what's new and what got fixed. */
data class Release(val version: String, val date: String, val added: List<String>, val fixed: List<String> = emptyList())

/**
 * Every Pyng update, newest first. Each line reads "Added…" or "Fixed…"; anything after a line
 * break is a short bit of extra context shown underneath.
 */
val RELEASES = listOf(
    Release(
        "0.8", "Nov 11, 2026",
        added = listOf(
            "Added a Copy button for verification codes\nWhen a code arrives, its notification shows a Copy button next to Reply",
            "Added automatic code copying\nNew codes are copied as soon as they arrive, so your keyboard suggests them in the code box. You can turn this off in Settings",
            "Added version history\nTap Version in Help & about to see what changed in each update",
        ),
        fixed = listOf(
            "Fixed missing names in chat search\nItems under Latest shared now show who sent them",
        ),
    ),
    Release(
        "0.7", "Nov 11, 2026",
        added = listOf(
            "Added ability to load all previous messages\nWhen scrolling up through a message thread, Pyng automatically loads earlier messages",
            "Added ability to load older chats\nScroll to the bottom of your chat list and older conversations load in",
            "Added search inside a chat\nShortcuts for photos, videos, links and files, the latest things shared, and your recent searches",
            "Added Google Sans as the app font\nYou can switch to your phone's font or Geist in Style studio",
            "Made the emoji, GIF and sticker panel taller",
        ),
        fixed = listOf(
            "Fixed unread rings and badges getting cut off\nThis happened for a moment while pinned chats animated back in",
        ),
    ),
    Release(
        "0.6", "Nov 10, 2026",
        added = listOf(
            "Added Style studio\nPreview and change your chat and home styles in one place",
            "Added styles for photo groups\nPhotos sent together can show as Peek, Stack, Rail or Deck",
            "Added message status under your newest message\nShows Sent, Delivered or Read, and animates as it changes",
            "Added a typing wave to pinned chats",
            "Added a swipeable, frosted in-app banner\nReply, react or mark as read right from the banner",
            "Added a new way to start conversations\nAdd people to the To field, then name your group and pick its photo",
            "Added save, share and forward for links, photos, videos and files\nHold any of them to see the options. Photos sent together can be saved all at once",
            "Added GIFs, stickers, clips and memes",
            "Added a warning before sending large files\nFiles too big for chat features may send as a regular text instead",
            "Made the app much smaller\nAbout 35 MB, down from 140 MB",
        ),
        fixed = listOf(
            "Fixed Google Messages notifications getting hidden\nPyng no longer hides them unless you turn that on, and never hides verification codes",
            "Fixed sign-in failing on slow phones\nPyng now tries a few times before giving up",
            "Fixed a crash in the chat color picker",
            "Fixed group chats not being created",
            "Fixed chats getting stuck loading\nPyng now nudges Google Messages, and offers a button to wake it if it's asleep",
            "Fixed pinned chats popping in before their animation",
        ),
    ),
    Release(
        "0.5", "Nov 10, 2026",
        added = listOf(
            "Added a chat list that slides over your pinned chats",
            "Added photo strips and a new photo viewer",
            "Added saved themes\nSave a color combination and reuse it in any chat",
            "Added encryption status and an SMS-only setting to contact pages",
            "Added group photo stacks and an App theme card",
        ),
        fixed = listOf(
            "Fixed photos not sending",
            "Fixed photos sending after their text\nPhotos now go first, then the message",
            "Fixed message bubbles not fitting their text",
            "Fixed pinned chats drifting behind the top bar",
        ),
    ),
    Release(
        "0.4", "Nov 8, 2026",
        added = listOf(
            "Added peek\nHold a chat to see its recent messages without opening it",
            "Added ability to select part of a message to copy",
            "Added your phone contacts when starting a chat",
            "Added video playback inside the app",
        ),
        fixed = listOf(
            "Fixed photos not loading at full size",
            "Fixed sent messages flickering when delivered",
            "Fixed sign-in when more than one phone is on the account",
        ),
    ),
    Release(
        "0.3", "Nov 7, 2026",
        added = listOf(
            "Added a photo viewer",
            "Added a voice message player",
            "Added contact cards and better link previews",
            "Added a glide animation when sending",
        ),
        fixed = listOf("Fixed choppy scrolling\nBlur effects are lighter now"),
    ),
    Release(
        "0.2", "Nov 7, 2026",
        added = listOf(
            "Added message previews to the chat list",
            "Added keyboard lift\nThe newest messages move up with the keyboard instead of hiding behind it",
            "Added a drip and bounce animation when sending",
            "Added a frosted icon set",
        ),
        fixed = listOf("Fixed photos not coming through"),
    ),
    Release(
        "0.1", "Nov 6, 2026",
        added = listOf(
            "First release\nSign in with Google Messages and text from Pyng",
            "Added pinned chats, themes, widgets and a sticker maker",
            "Added translation, scheduled messages and smart replies",
            "Added pinch to zoom in chats and app-wide text size",
        ),
        fixed = listOf(
            "Fixed hard-to-read text colors",
            "Fixed duplicate messages and chats",
        ),
    ),
)

/** The version history page: one card per update, newest first, the one you have marked. */
@Composable
fun VersionHistory() {
    val t = LocalTok.current
    val current = BuildConfig.VERSION_NAME.split('.').take(2).joinToString(".")
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RELEASES.forEach { r ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(t.card).padding(14.dp)) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Pyng ${r.version}", size = 19.sp, weight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                        T(r.date, size = 12.5.sp, color = t.soft)
                    }
                    if (r.version == current) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(t.fill).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        T("You have this", size = 11.5.sp, weight = FontWeight.SemiBold, color = t.onFill)
                    }
                }
                if (r.added.isNotEmpty()) Section(Icons.spark, "What's New", r.added, t.tone, t.p)
                if (r.added.isNotEmpty() && r.fixed.isNotEmpty()) Spacer(Modifier.height(8.dp))
                if (r.fixed.isNotEmpty()) Section(Icons.check, "What's Fixed", r.fixed, t.neutral, t.soft)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A tinted panel with a heading and bullets. A line can carry extra context after a line break. */
@Composable
private fun Section(icon: String, title: String, items: List<String>, bg: androidx.compose.ui.graphics.Color, accent: androidx.compose.ui.graphics.Color) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Ico(icon, accent, 15.dp, 2.2f)
            Spacer(Modifier.width(6.dp))
            T(title, size = 13.5.sp, weight = FontWeight.Bold, color = accent)
        }
        items.forEach { raw ->
            val main = raw.substringBefore('\n')
            val extra = raw.substringAfter('\n', "")
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Column {
                    T(main, size = 14.5.sp, lineHeight = 20.sp)
                    if (extra.isNotEmpty()) T(extra, size = 13.sp, lineHeight = 18.sp, color = t.soft, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
    }
}
