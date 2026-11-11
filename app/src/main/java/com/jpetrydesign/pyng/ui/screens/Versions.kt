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
 * Every Pyng update, newest first. Each line is a short headline; anything after a line break is
 * a sentence of detail shown underneath.
 */
val RELEASES = listOf(
    Release(
        "0.8", "Nov 11, 2026",
        added = listOf(
            "Copy Button for Codes\nVerification code notifications now show a Copy button next to Reply",
            "Automatic Code Copying\nNew codes are copied as they arrive, so your keyboard can suggest them. Turn it off in Settings",
            "Version History Added\nTap Version in Help & about to see what changed in each update",
            "New App Icons\nEvery icon uses the main Pyng logo, with 8 colors each for Soft and Duotone and a frostier Glass",
            "More Pinned Chat Styles\nNew photo shapes, new-message signs, name styles, backdrops and a Honeycomb layout in Style studio",
            "App Renamed to Pyng\nEasier to tell apart from Google Messages on your home screen and in notifications",
            "Share to Pyng\nPyng now shows up when you share from other apps. Pick a recent chat or anyone in your contacts",
        ),
        fixed = listOf(
            "Chat Search Names\nItems under Latest shared now show who sent them",
            "App Color Done Button\nDone now takes you back to Appearance instead of your chat list",
        ),
    ),
    Release(
        "0.7", "Nov 11, 2026",
        added = listOf(
            "Full Message History\nScrolling up in a chat automatically loads earlier messages",
            "Older Chats Load In\nScrolling to the bottom of your chat list loads older conversations",
            "Search Inside a Chat\nShortcuts for photos, videos, links and files, plus your recent searches",
            "New Font Options Added\nDefaulted font to Google Sans, with your phone's font and Geist as alternative options",
            "Taller GIF Panel\nThe emoji, GIF and sticker panel now takes about half the screen",
        ),
        fixed = listOf(
            "Pinned Chat Badges\nUnread rings and badges no longer get cut off while pins animate in",
        ),
    ),
    Release(
        "0.6", "Nov 10, 2026",
        added = listOf(
            "Style Studio Added\nPreview and change your chat and home styles in one place",
            "Photo Group Styles\nPhotos sent together can show as Peek, Stack, Rail or Deck",
            "Message Status\nSent, Delivered and Read show under your newest message",
            "Typing Wave\nPinned chats wiggle when someone is typing",
            "New In-App Banner\nFrosted and swipeable, with reply, reactions and mark as read",
            "New Conversation Flow\nAdd people to the To field, then name your group and pick its photo",
            "Save, Share and Forward\nHold links, photos, videos and files for options, including Save all for photo groups",
            "GIFs, Stickers, Clips and Memes",
            "Large File Warning\nPyng warns you before sending a file too big for chat features",
            "Smaller App\nAbout 35 MB, down from 140 MB",
        ),
        fixed = listOf(
            "Google Messages Notifications\nNo longer hidden unless you turn that on, and codes are never touched",
            "Sign-In on Slow Phones\nPyng now tries a few times before giving up",
            "Chat Color Picker Crash",
            "Group Chat Creation\nNew groups now go through, with or without a name",
            "Chats Stuck Loading\nPyng nudges Google Messages and offers a button to wake it",
            "Pinned Chat Animation\nPins no longer pop in before their animation",
        ),
    ),
    Release(
        "0.5", "Nov 10, 2026",
        added = listOf(
            "Sliding Chat List\nYour chat list now sits on a sheet that slides over pinned chats",
            "New Photo Viewer\nWith photo strips for pictures sent together",
            "Saved Themes\nSave a color combination and reuse it in any chat",
            "Contact Page Additions\nEncryption status and an SMS-only setting",
            "Group Photo Stacks\nGroups show their members' photos, plus a new App theme card",
        ),
        fixed = listOf(
            "Photo Sending\nPhotos send reliably, and go out before the text that goes with them",
            "Bubble Sizing\nMessage bubbles now fit their text",
            "Pinned Chats Drifting\nPins no longer slide behind the top bar",
        ),
    ),
    Release(
        "0.4", "Nov 8, 2026",
        added = listOf(
            "Peek at Chats\nHold a chat to see recent messages without opening it",
            "Select Text\nCopy just part of a message",
            "Phone Contacts\nYour contacts show up when starting a chat",
            "Video Playback\nVideos play right inside the app",
        ),
        fixed = listOf(
            "Photo Quality\nPhotos now load at full size",
            "Sent Message Flicker\nMessages no longer flicker when they're delivered",
            "Sign-In with Multiple Phones\nWorks when more than one phone is on the account",
        ),
    ),
    Release(
        "0.3", "Nov 7, 2026",
        added = listOf(
            "Photo Viewer",
            "Voice Message Player",
            "Contact Cards and Link Previews",
            "Glide Send Animation",
        ),
        fixed = listOf("Smoother Scrolling\nBlur effects are lighter now"),
    ),
    Release(
        "0.2", "Nov 7, 2026",
        added = listOf(
            "Message Previews\nYour chat list shows the latest message in each chat",
            "Keyboard Lift\nThe newest messages move up with the keyboard",
            "Send Animation\nA drip and bounce when you send",
            "Frosted Icon Set",
        ),
        fixed = listOf("Photo Delivery\nPhotos now always come through"),
    ),
    Release(
        "0.1", "Nov 6, 2026",
        added = listOf(
            "First Release\nSign in with Google Messages and text from Pyng",
            "Pinned Chats, Themes and Widgets\nPlus a sticker maker",
            "Translation, Scheduling and Smart Replies",
            "Text Size and Pinch to Zoom",
        ),
        fixed = listOf(
            "Text Colors\nText is easier to read",
            "Duplicate Messages\nMessages and chats no longer show up twice",
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
                    T(main, size = 14.5.sp, lineHeight = 20.sp, weight = FontWeight.SemiBold)
                    // The detail sits one step further in, with its own dash, like a nested bullet.
                    if (extra.isNotEmpty()) Row(Modifier.padding(start = 14.dp, top = 2.dp), verticalAlignment = Alignment.Top) {
                        T("–", size = 13.sp, lineHeight = 18.sp, color = t.faint)
                        Spacer(Modifier.width(8.dp))
                        T(extra, size = 13.sp, lineHeight = 18.sp, color = t.soft)
                    }
                }
            }
        }
    }
}
