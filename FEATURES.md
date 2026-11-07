# Pyng feature checklist

Every feature from the design prototype and our conversations, with where it stands in the app.
✅ built · 🔶 built with a limit · ❌ not possible yet (reason given)

## Setup
- ✅ Welcome: "Messages, Your Way" with the three feature rows
- ✅ Permissions: notifications, contacts, photos & videos, microphone, location (each with Allow), plus "Allow and continue"
- ✅ Turn On Chat Features: Google sign-in, emoji confirmation, "Chat features connected" card, QR fallback, debug log
- ✅ Pick Your Colors: preview card, palettes, Light / Dark / Match system
- ✅ Skip, step dots, animated transitions
- 🔶 "Set as default texting app" step is left out on purpose: Google Messages stays your default and Pyng links to it

## Home
- ✅ "Messages" header, live "N New Conversations" / "You're All Caught Up" / connection status
- ✅ Profile photo button → Settings
- ✅ Pinned chats seeded from Google Messages pins, up to 8
- ✅ 10 pinned layouts: Grid, Large, Pages, Carousel, Compact, Chips, Cards, Spotlight, List, Dock
- ✅ Pinned notifications: Badges or Rings, typing dots on pinned faces
- ✅ Hold a pinned chat and drag it: the others slide aside to show where it lands, and it saves when you let go
- ✅ Drag a pinned chat out to unpin it; hold a chat in the list and drag it up to pin it
- ✅ Rows: photo or initials, name, time, preview, typing, draft, read check, muted, pin mark, unread badge
- ✅ Copy button for verification codes
- ✅ Swipe left and right with your chosen actions (archive, read/unread, delete, pin, mute, off)
- ✅ Long-press menu: pin, archive, mute, mark read/unread, block, delete — with Undo where it makes sense
- ✅ Archived and Spam & blocked folders, swipe hint, empty states
- ✅ Frosted glass search and new-message buttons (solid icons)
- ✅ In-app banner with quick reply when a message arrives in another chat
- ✅ App color strip that previews live on Home
- 🔶 Unread badge shows "1" until the chat's messages are loaded (Google Messages only says *if* a chat is unread)
- ❌ Auto-deleting codes after 24 hours (would delete messages in Google Messages; left out on purpose)

## Search and new chats
- ✅ Search people and messages, with Photos and Links tabs
- ✅ New conversation: To field, numbers, your contacts
- ✅ Create group with a name
- ❌ Notes to self (Google Messages has no "message yourself" chat to link to)

## Chat
- ✅ Frosted header: photo, name with chevron, typing / members line, blue call button in a bubble
- ✅ Call sheet: voice call, video call, copy number
- ✅ Wallpapers per chat or app-wide, including your own photo
- ✅ Bubble shapes: Rounded, Soft, Pill, with grouped corners
- ✅ Sent bubbles on the right, about 72% max width
- ✅ Pinch a chat to make bubbles bigger or smaller
- ✅ Time dividers ("Today 3:52 PM"), or a time under every message
- ✅ Tap beside a message for its status (received / sending / delivered / read / scheduled)
- ✅ Swipe left to reveal timestamps (your bubbles slide over, like iMessage)
- ✅ Swipe a bubble right to reply; reply quotes inside bubbles
- ✅ Double-tap for ❤️ with a burst
- ✅ Long-press like Google Messages: chat blurs, the message stays in place, reactions above (emoji button for the rest), actions below
- ✅ Reactions under bubbles
- ✅ Big emoji when a message is only 1–3 emoji
- ✅ Photos, GIFs, video, voice messages (with playback), files, contact cards
- ✅ Link preview cards: picture, title, description, site icon (a link-only message shows just the card)
- ✅ Smart chips: add to calendar, open in Maps, track package, copy code
- ✅ Translate: turn it on per chat from the + menu; messages in other languages translate on-device
- ✅ Typing bubble, jump-to-latest button, staggered message entrance
- ✅ Smart replies above the keyboard, with an X to close them
- ✅ @mentions in group chats
- ✅ Composer: + (rotates to ×), emoji, gallery, send / mic, SMS character counter; box reads "RCS Message" or "SMS Message"
- ✅ + panel: Gallery, Camera, GIFs, Stickers, Files, Location, Contact, Schedule
- ✅ Emoji with search, categories and recents; stickers; sticker maker (Cutout / Circle / Rounded, white outline)
- ✅ In-panel gallery grid with multi-select
- ✅ Scheduled send (pick date and time, or a suggestion), shown faded in the chat until it goes
- ✅ Location with a map preview
- ✅ Voice recording with live bars
- ✅ Effects: balloons for birthdays, confetti, hearts
- ✅ Scam warning and "Not in your contacts" banners
- ✅ Select mode (copy, star, delete) and search inside a chat
- ✅ Deleting a message or conversation asks first
- ✅ Chats open at the newest message and stay there as new ones arrive
- ✅ Empty chat with "Say hi"
- 🔶 GIF search needs a free GIPHY key: paste it in the GIFs tab
- ❌ Voice message transcripts (Android can't transcribe audio files on-device yet)

## Contact card
- ✅ Photo, name, number, encryption status (tap it for what it means)
- ✅ Contact photos from your phone's contacts, with Google Messages as backup
- ✅ Call, Search, Mute, Pin
- ✅ Chat theme, notifications on/off, vibration
- ✅ Media, Links, Files tabs
- ✅ Group members
- ✅ Add to contacts, archive, block & report, delete
- 🔶 Per-chat notification sound (sound follows Android's notification settings)
- 🔶 Rename a group and change its photo: shows in Pyng only (Google Messages doesn't let linked apps change them)
- ✅ Media, Links and Files show who sent each one
- ❌ Add people, leave group (Google Messages for Web doesn't offer these)

## Chat theme
- ✅ App theme, all palettes and duotones, Custom (Mono / Duotone / Gradient) with the color wheel
- ✅ Wallpapers: Custom photo, None, Mist, Dusk, Paper, Aurora; Reset to app theme

## Settings
- ✅ Profile card, Archived / Starred / Scheduled tiles
- ✅ Appearance, Chats, Notifications, Chat features, Privacy & safety, Accessibility, Storage, Help & about
- ✅ Appearance pickers with previews and the current value under each row:
  pinned layout, pinned notifications, bubble shape, text size, contact photos, list density, app icon, default wallpaper
- ✅ Color picker with hex input, quick colors, dark-to-light slider
- ✅ Light / Dark / Match system
- ✅ Quick reactions picker, profile name and photo
- ✅ Fingerprint lock, hide in recent apps, permissions page
- ✅ Bold text, high contrast, reduce transparency, reduce motion, text size (applies to the whole app)
- ✅ Debug log and crash reports
- ❌ AI assistant (removed)
- ❌ SIM & MMS settings (these live in Google Messages)

## Around the phone
- ✅ Notifications with Reply and Mark as read
- ✅ Doesn't run all the time: wakes when Google Messages gets a text (notification access), shows its own alert and hides Google's
- ✅ "Always connected" switch for anyone who wants the old behavior
- ✅ Text notifications show in Android's Conversations section with the person's photo
- ✅ Home-screen widgets: Conversations and Pinned people
- ✅ App-icon shortcuts: New message plus your top 3 pinned chats
- ✅ Fold inner screen: chat list and conversation side by side
- ✅ Five app icons: Classic, Duotone, Soft, Mono, Glass
