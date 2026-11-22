package com.jpetrydesign.pyng.ui.screens

import com.jpetrydesign.pyng.ui.kit.pane
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
data class Release(val version: String, val date: String, val added: List<String>, val fixed: List<String> = emptyList(), val highlights: List<Highlight> = emptyList())

/**
 * Every Pyng update, newest first. Each line is a short headline; anything after a line break is
 * a sentence of detail shown underneath.
 */
val RELEASES = listOf(
    Release(
        "0.9.6", "Nov 21, 2026",
        highlights = listOf(
            Highlight("Liquid Glass, for real", "Clear, glossy glass with a glint that moves as you scroll, on cards, sheets and controls. Frost is now fully frosted.", "glass"),
            Highlight("Light & dark looks", "With Match system on, give light and dark their own colors. Turn it off to use one color for both.", "looks"),
            Highlight("Transcripts that keep up", "Words appear as they're heard, with cleaner punctuation. Redo one, or hold it to copy.", "live"),
        ),
        added = listOf(
            "Liquid Glass, for real\nClear, glossy glass with a glint that moves as you scroll, on cards, sheets and controls. Frost is now fully frosted",
            "Light & dark looks\nWith Match system on, give light and dark their own colors, in Appearance",
            "Transcripts that keep up\nWords appear as they're heard, with cleaner punctuation; Redo, or hold to copy",
            "A full-screen viewer\nPhotos and videos fill the screen at their own shape, with one floating player and everything else behind the dots. Tap to hide it all",
            "What's new shows the real thing\nCards play the feature itself, and ones without a picture stay small",
            "Smaller read receipts\nDelivered, Sent and Read take up less room under your messages",
        ),
        fixed = listOf(
            "Texts that fail on a dropped connection send again on their own",
            "The app icon counts unread chats, one per person, and never gets stuck",
            "Saved files and reminders don't count on the app icon",
            "The save button stays blank until you tap it, then fills with the real progress",
            "The save button sits centered beside the photo, and saves a whole photo group at once",
        ),
    ),
    Release(
        "0.9.5.2", "Nov 19, 2026",
        highlights = listOf(
            Highlight("Swear words, your call", "Voice message transcripts keep words as said. Turn on Hide swear words in Settings, Chats to bleep them.", "bleep"),
            Highlight("Group names come from Google Messages", "Rename a group there and it changes for everyone, then shows here in Pyng too.", "groupname"),
        ),
        added = listOf(
            "Swear words, your call\nVoice message transcripts keep words as said. Turn on Hide swear words in Settings, Chats to bleep them",
            "Group names come from Google Messages\nTap a group's name to rename it there, so it changes for everyone and shows here too",
        ),
        fixed = listOf(
            "Typing shows in group chats",
            "Who reacted shows your profile photo instead of a You badge",
        ),
    ),
    Release(
        "0.9.5.1", "Nov 19, 2026",
        highlights = listOf(
            Highlight("Liquid motion", "Search, new messages and chats pour out of whatever you tap, then drain back into it when you go back.", "motion"),
            Highlight("Send styles, touched up", "Rise and Honey lift out right where you typed, Droplet shrinks to a drop and spreads, and Ink floods out from the middle.", "send"),
            Highlight("Moments", "Birthdays from your contacts show up in Search, with a soft glowing dot on the search button.", "moments"),
            Highlight("Little touches", "Switches stretch, tabs slide, unread counts bud, smart replies rise out of the message box, and found dates glint.", "touches"),
        ),
        added = listOf(
            "Liquid motion\nSearch, new messages and chats pour out of whatever you tap, then drain back into it when you go back",
            "Send styles, touched up\nRise and Honey lift out right where you typed, Droplet shrinks to a drop and spreads, and Ink floods out from the middle",
            "Moments\nBirthdays from your contacts show up in Search, with a soft glowing dot on the search button",
            "Little touches\nSwitches stretch, tabs slide, unread counts bud, smart replies rise out of the message box, and found dates glint",
            "Motion styles\nRise, Pour or Drop, or keep it Simple or Minimal, in Appearance, Home style, Motion",
            "Hold a message\nThe reaction bar rises out of the bubble and the menu drips out below",
            "Your photo flies up\nOpening a chat carries its photo from the list into the header",
            "Hide moments until tomorrow\nRight from the Moments heading in Search",
            "Sticker maker turns\nTurn the photo a quarter at a time, or tilt the sticker left or right",
            "New number note\nA slim pill instead of a big card for numbers that aren't in your contacts",
            "A new What's new\nThe biggest changes on cards, then everything else",
        ),
        fixed = listOf(
            "The send button eases between send and the mic instead of jumping",
            "Tabs slide to the one you tap instead of blinking",
            "Switches fade their color instead of snapping",
        ),
    ),
    Release(
        "0.9.5", "Nov 18, 2026",
        added = listOf(
            "Categories\nPriority, Primary, Favorites and your own, in a bar under Messages. Turn them on in Settings, Categories",
            "Trash\nDeleted messages and chats wait 30 days in Trash, so you can bring them back",
            "Undo send\nA few seconds to take back a message after you tap send",
            "Snooze\nHold a chat and tap Snooze to put it away until later",
            "Pin a message\nHold a message and tap Pin; it stays under the chat's name",
            "Sticker maker\nCuts out the subject automatically, with Outline, Puffy, Comic, Holo and Glitter, and keeps them in My stickers",
            "Glass styles\nFrost, Liquid Glass or Solid, in Appearance",
            "Save beside photos\nA save button next to photos and videos, and files land in Downloads with a notification",
            "Chat bubbles\nChats can float over other apps",
            "Raise to listen\nLift your phone to your ear while a voice message plays",
            "Unknown senders\nA slim banner to review texts from numbers you don't know",
            "Blocked words, quiet hours, a sound for each chat, and codes that delete themselves",
            "Backup and restore\nSave Pyng's settings, looks, categories, stars and Trash to a file",
            "A menu on your photo\nStarred, Archived, Scheduled, Spam, Trash and What's new, with Settings one tap away",
        ),
        fixed = listOf(
            "Sent bubble text stays readable on every bubble color",
            "Group chats show who's typing",
            "Texts come through even when Google Messages' alerts are set to Silent",
            "iPhone reactions and replies land on the right message",
            "Addresses open in Maps",
            "Stickers show whole and see-through",
            "Photos come in upright in the sticker maker",
            "Contact photos show in notifications, as circles",
            "The camera button opens the camera",
        ),
    ),
    Release(
        "0.9.2", "Nov 12, 2026",
        added = listOf(
            "See Who Reacted\nTap the reactions under a message to see everyone's emoji and photo, with filters for each emoji",
            "Three Reaction Styles\nFaces, Cards or Tabs, in Style studio and the setup screen",
            "One Reaction Each\nTap a new emoji to switch yours, or tap it again (or its ✕) to take it off",
            "Fits Any Screen\nThe message menu sizes itself to your screen and slides up when a message is low",
            "Frosted Menus\nThe reaction bar and menu are see-through, so the chat shows softly behind them",
        ),
        fixed = listOf(
            "Favoriting Emoji\nHolding an emoji in the picker only favorites it and no longer reacts",
            "Choose Your Style\nLive previews for Send and Reactions, Classic and Liquid side by side, labeled Pinned choices, and no stray bubble in the preview",
        ),
    ),
    Release(
        "0.9.1", "Nov 12, 2026",
        added = listOf(
            "Send Animation Preview\nThe Send tab in Style studio now plays your chosen animation on a loop",
            "Swipe All the Way\nDrag a chat across to archive, delete or mark it, and it slides off as the action happens",
        ),
        fixed = listOf(
            "Clipped Rows When Swiping\nThe chat row keeps its rounded corners and photo while you swipe",
        ),
    ),
    Release(
        "0.9", "Nov 12, 2026",
        added = listOf(
            "Send Animations\nTen ways for a message to leave the box, in Classic and Liquid sets. Pick one in Style studio under Chat style, Send",
            "Choose Your Style\nA new setup page with a live preview for colors, pinned chats, bubbles, send animation and app icon",
            "New Share Screen\nPinned chats in a row up top, then recent chats and contacts. Pick a few, edit the message at the bottom and send, or start a group",
            "Pyng in the Share Sheet\nYour pinned and recent chats show up right when you share from another app",
            "Review Before Sending\nTap an attachment to preview everything. Crop, turn, straighten or add an effect to photos, and trim or crop videos",
            "Paste Pictures\nPaste images into the message box, or send GIFs and stickers straight from your keyboard",
            "Reaction Picker\nSearch every emoji, hold one to favorite it, and find your recent reactions",
            "Message Reminders\nHold a message and tap Remind me. The reminder opens the chat right at that message",
            "Swipe Between Chats\nSwipe across the name at the top of a chat to go to the next or previous one",
            "Unread Jump\nA pill takes you to the first new message, and the down arrow counts texts that arrive while you're scrolled up",
            "Reply From Peek\nHold any chat to see its last message and reply without opening it",
            "Fuller Voice Transcripts\nLong voice messages are transcribed start to finish, with punctuation and a Full transcript button",
            "Light and Dark Everywhere\nSwitch between light and dark right from App color and Chat theme",
            "Read Receipts Shortcut\nA new card in Chats settings takes you to Google Messages to turn read receipts on or off",
            "Text Links in Pyng\nAn optional setting lets text links from other apps open in Pyng",
            "Friendlier Screens\nSoft placeholders while things load, and helpful notes when a list is empty",
        ),
        fixed = listOf(
            "Missing Parts of Transcripts\nLong voice messages no longer skip whole stretches in the middle",
            "Dark Text on Bubbles\nChats with a deep bubble color now get white text in dark mode",
            "Plus Sign Instead of a Photo\nPeople without a photo now show their initials in notifications",
            "White Box on Emoji\nSending just an emoji no longer flashes a white box",
            "No Paste Option\nHolding the message box now shows Paste and Select all",
            "Keyboard Staying Open\nThe keyboard now closes when you open a photo, a menu or another screen",
            "Cover Screen Layout\nThe emoji panel and share screen fit on a flip phone's outside screen",
            "Read Status Toggle\nRemoved the switch that only hid Read under your messages. Read status always shows now",
        ),
    ),
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
            "App Icon Row\nThe App icon row in Appearance shows your real icon instead of a blank square",
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
            "Missing Message History\nChats only showed texts from after Pyng was set up. Older messages now load",
            "Long Chat Lists\nYour chat list no longer stops at your 120 most recent conversations",
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
            "Chat Color Picker Crash\nOpening the color wheel in a chat's theme no longer closes the app",
            "Group Chat Creation\nNew groups now go through, with or without a name",
            "Chats Stuck Loading\nPyng nudges Google Messages and offers a button to wake it",
            "Pinned Chat Animation\nPins no longer pop in before their animation",
            "Pins Behind the Top Bar\nPinned chats stay hidden while covered instead of peeking out",
            "Links and Media Ignoring a Hold\nLink cards, photos, videos and files now open the message menu when held",
            "Empty GIF Tab Flash\nThe GIF panel shows a spinner while it loads instead of saying nothing was found",
            "Memes Tab Empty\nMemes now load",
            "Send Failures Explained\nFailed messages now say why, like a file that's too large",
            "Debug Log Cut Off\nThe debug log is now shared as a full file",
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
            "Bubble Sizing\nMessage bubbles now fit their text, and short messages get even padding",
            "Pinned Chats Drifting\nPins no longer slide behind the top bar",
            "Received Bubble Contrast\nMessages from others are easier to read",
            "Toll-Free Numbers\n800 and similar numbers now show as businesses",
            "Startup and Memory\nPyng opens faster and uses less memory",
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
            "Photo Quality\nPhotos now load at full size instead of a blurry preview",
            "Sent Message Flicker\nMessages no longer flicker or fade in twice when they're delivered",
            "Sign-In with Multiple Phones\nWorks when more than one phone is on the account",
            "Select Text Scrolling\nSelecting part of a message no longer jumps around",
            "Blurry Video Previews\nVideo thumbnails are sharp",
            "Loading States\nPhotos show when they're still downloading instead of a blank box",
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
        fixed = listOf(
            "Smoother Scrolling\nBlur effects are lighter now",
            "Media Downloads\nPhotos and videos download more reliably",
            "Wide Screens\nFixed the layout on unfolded and wide screens",
        ),
    ),
    Release(
        "0.2", "Nov 7, 2026",
        added = listOf(
            "Message Previews\nYour chat list shows the latest message in each chat",
            "Keyboard Lift\nThe newest messages move up with the keyboard",
            "Send Animation\nA drip and bounce when you send",
            "Frosted Icon Set",
        ),
        fixed = listOf(
            "Photo Delivery\nPhotos now always come through",
        ),
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
            "Sign-In Reliability\nPairing with Google Messages is more dependable",
            "Pinned Chat Dragging\nDragging pins to rearrange them works smoothly",
            "Animations Replaying\nEntrance animations no longer replay every time you scroll",
            "Chats Opening Mid-Thread\nChats now open at the newest message",
            "Color Wheel\nPicking a custom color works again",
            "Battery Use\nPyng wakes for new texts instead of running all the time",
        ),
    ),
)

/** The version history page: one card per update, newest first, the one you have marked. */
@Composable
fun VersionHistory() {
    val t = LocalTok.current
    val current = BuildConfig.VERSION_NAME.removeSuffix(".0")
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RELEASES.forEach { r ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).pane(RoundedCornerShape(26.dp)).padding(14.dp)) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Pyng ${r.version}", size = 19.sp, weight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                        T(r.date, size = 12.5.sp, color = t.soft)
                    }
                    if (r.version == current) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(t.fill).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        T("Latest version", size = 11.5.sp, weight = FontWeight.SemiBold, color = t.onFill)
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
