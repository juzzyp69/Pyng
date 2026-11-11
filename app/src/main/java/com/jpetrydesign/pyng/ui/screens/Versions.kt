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

/** Every Pyng update, newest first. Keep each line short: what it is, not how it works. */
val RELEASES = listOf(
    Release(
        "0.8", "Nov 11, 2026",
        added = listOf(
            "Copy button for verification codes in notifications\nRight next to Reply and Mark as read",
            "New codes are copied for you\nTap the code box and your keyboard suggests it. Turn it off in Settings",
            "Version history\nTap Version in Help & about to see this page",
        ),
    ),
    Release(
        "0.7", "Nov 11, 2026",
        added = listOf(
            "Google Sans font\nOr pick your phone's font or Geist in Style studio",
            "Search inside a chat\nShortcuts for photos, videos, links and files, plus your recent searches",
            "Your full message history as you scroll up",
            "Older chats load as you scroll down the list",
            "Taller emoji, GIF and sticker panel",
        ),
        fixed = listOf(
            "Unread rings and badges cut off while pinned chats animate in",
        ),
    ),
    Release(
        "0.6", "Nov 10, 2026",
        added = listOf(
            "Style studio for chat and home styles",
            "Photo groups\nFour looks to pick from: Peek, Stack, Rail and Deck",
            "Delivered and Read under your newest message",
            "Typing wave on pinned chats",
            "Swipeable, frosted in-app banner",
            "New conversation flow with group name and photo",
            "Hold links, photos, videos and files to save, share or forward",
            "GIFs, stickers, clips and memes\nSearch and trending in one panel",
            "Warning before sending a file too big for chat features",
            "App is a quarter of its old size\nAbout 35 MB, down from 140 MB",
        ),
        fixed = listOf(
            "Google Messages notifications getting hidden\nNow off by default, and codes are never touched",
            "Sign-in gives up too soon on a slow phone",
            "Crash in the chat color picker",
            "Creating a group",
            "Chats stuck loading when the phone is asleep\nPyng now nudges Google Messages and offers a button to wake it",
            "Pinned chats popping in before their animation",
        ),
    ),
    Release(
        "0.5", "Nov 10, 2026",
        added = listOf(
            "Chat list on a sheet that slides over pinned chats",
            "Photo strips and a new photo viewer",
            "Saved themes",
            "Encryption status and SMS-only setting on contact pages",
            "Group photo stacks and an App theme card",
        ),
        fixed = listOf(
            "Photos not sending",
            "Photos now send before the text that goes with them",
            "Message bubbles now fit their text",
            "Pinned chats drifting behind the top bar",
        ),
    ),
    Release(
        "0.4", "Nov 8, 2026",
        added = listOf(
            "Hold a chat to peek at recent messages",
            "Select part of a message to copy",
            "Your phone contacts when starting a chat",
            "Videos play inside the app",
        ),
        fixed = listOf(
            "Photos not loading at full size",
            "Sent messages flickering when delivered",
            "Sign-in with more than one phone on the account",
        ),
    ),
    Release(
        "0.3", "Nov 7, 2026",
        added = listOf(
            "Photo viewer",
            "Voice message player",
            "Contact cards and better link previews",
            "Glide animation when sending",
        ),
        fixed = listOf("Smoother scrolling with lighter blur"),
    ),
    Release(
        "0.2", "Nov 7, 2026",
        added = listOf(
            "Message previews in the chat list",
            "Keyboard lifts the newest messages",
            "Drip and bounce animation when sending",
            "Frosted icon set",
        ),
        fixed = listOf("Photos not coming through"),
    ),
    Release(
        "0.1", "Nov 6, 2026",
        added = listOf(
            "First release: sign in with Google Messages, chat list and chats",
            "Pinned chats, themes, widgets and a sticker maker",
            "Translation, scheduled messages and smart replies",
            "Pinch to zoom chats and app-wide text size",
        ),
        fixed = listOf(
            "Unreadable text colors",
            "Duplicate messages and chats",
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
