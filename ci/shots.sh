#!/bin/bash
# Opens every screen with sample data on an emulator, saves screenshots and any crashes.
PKG=com.jpetrydesign.pyng
APK="$1"
OUT=ci-logs/shots
mkdir -p "$OUT"
: > ci-logs/crashes.txt
adb install -r "$APK" > ci-logs/install.txt 2>&1 || { cat ci-logs/install.txt; exit 1; }
for p in POST_NOTIFICATIONS READ_CONTACTS READ_MEDIA_IMAGES READ_MEDIA_VIDEO RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  adb shell pm grant $PKG android.permission.$p 2>/dev/null
done
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
# Keep the emulator's own launcher and its "isn't responding" dialogs out of the way.
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher > /dev/null 2>&1 || true
# Turning the launcher off restarts the system UI; let it settle before the first screenshot.
sleep 15

shot() {
  local name="$1"; shift
  timeout 20 adb shell am force-stop $PKG
  timeout 10 adb logcat -b crash -c
  timeout 40 adb shell am start -W -n $PKG/.IconClassic --ez demo true "$@" > /dev/null || echo "start timed out: $name" >> ci-logs/crashes.txt
  sleep "${WAIT:-4}"
  if ! timeout 30 adb exec-out screencap -p > "$OUT/$name.png"; then
    echo "screencap timed out: $name" >> ci-logs/crashes.txt
    # The first time the phone stops answering, keep what it was doing for a look later.
    if [ ! -s ci-logs/hang.txt ]; then
      { echo "== $name"; timeout 20 adb logcat -d -t 400; timeout 10 adb shell top -b -n 1 -m 15; } > ci-logs/hang.txt 2>&1
    fi
  fi
  # Note an app that stopped answering (it would otherwise hang the rest of the run).
  if adb shell dumpsys activity processes 2>/dev/null | grep -q "notResponding=true"; then echo "ANR after: $name" >> ci-logs/crashes.txt; fi
  local crash
  crash=$(timeout 15 adb logcat -b crash -d)
  if [ -n "$crash" ]; then
    { echo "===== $name ====="; echo "$crash"; } >> ci-logs/crashes.txt
  fi
  echo "shot $name"
}

# Everything the phone logs during the run, to see what happened if it stops answering.
( adb logcat -v time > ci-logs/logcat.txt 2>&1 & )
# 0.9.7.5 first: the color page, Appearance with Style open, the viewer dock and the editor.
WAIT=8 shot 00a0-color --es open strip --es mode Light --es theme Blue
WAIT=6 shot 00a0-color-dark --es open strip --es mode Dark
WAIT=8 shot 00a0-color-merlot --es open strip --es theme d:Merlot --es mode Light
WAIT=6 shot 00a0-color-aurora --es open strip --es theme g:Aurora --es mode Dark
WAIT=6 shot 00a0-appearance-style --es route appearance --es open style --es mode Light --es theme d:Merlot
WAIT=6 shot 00a0-thread-merlot --es route thread:c1 --es theme d:Merlot --es mode Light
WAIT=6 shot 00a0-thread-citrus-dark --es route thread:c1 --es theme d:Citrus --es mode Dark
WAIT=6 shot 00a0-thread-blue --es route thread:c1 --es theme Blue --es mode Light
WAIT=6 shot 00a0-thread-denim --es route thread:c1 --es theme Denim --es mode Light
WAIT=10 shot 00a0-review --es route thread:c9 --es open review
# 0.9.7 first: settings groups, theme card and picker, glass sliders, select, dark home, photo stack.
WAIT=6 shot 00a-settings-groups --es route settings
WAIT=6 shot 00a-appearance --es route appearance
WAIT=6 shot 00a-appearance-dark --es route appearance --es mode Dark
WAIT=6 shot 00a-strip --es open strip
WAIT=6 shot 00a-select --es open select
WAIT=6 shot 00a-home-dark --es mode Dark
WAIT=6 shot 00a-home-volt --es mode Dark --es theme d:Volt
WAIT=6 shot 00a-home-aurora --es theme g:Aurora
WAIT=8 shot 00a-thread-stack --es route thread:c9
WAIT=6 shot 00a-rowmenu --es open rowmenu
# 0.9.6 first: the new viewer and the two glass looks.
WAIT=6 shot 00z-frost-appearance --es route appearance
# New in 0.9.5.1: What's new cards, the Motion tab, and liquid motion caught mid-flight.
WAIT=7 shot 00-whatsnew --ez whatsnew true
WAIT=6 shot 00b-motion-tab --es route appearance --es open studio:home:motion
WAIT=3 shot 00c-menu-budding --es route thread:c1 --es open menu
WAIT=7 shot 00d-menu-open --es route thread:c1 --es open menu
# Tap the search button and grab frames while it pours open.
SZ=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${SZ%x*}; H=${SZ#*x}
DN=$(adb shell wm density | grep -oE '[0-9]+' | tail -1)
px() { echo $(( $1 * DN / 160 )); }
WAIT=6 shot 00e-home-before
grab() { adb exec-out screencap -p > "$OUT/$1.png"; }
# The search button sits left of the new message button: 20dp margin, 60dp button, 14dp gap, then half a button.
adb shell input tap $(( W - $(px 124) )) $(( H - $(px 98) ))
sleep 0.1; grab 00f-search-pouring
sleep 0.3; grab 00f2-search-pouring
sleep 1; grab 00g-search-open
adb shell input keyevent 4; sleep 0.3; adb shell input keyevent 4
sleep 0.1; grab 00h-search-draining
sleep 1.2; grab 00i-search-closed
# Open the first chat in the list and catch it growing, then going back into its row.
adb shell input tap $(( W / 2 )) 1200
sleep 0.1; grab 00j-chat-growing
sleep 0.3; grab 00j2-chat-growing
sleep 1.2; grab 00k-chat-open
adb shell input keyevent 4
sleep 0.15; grab 00l-chat-draining
sleep 1.2; grab 00m-chat-closed
echo "shot search morph"
# New in 0.9.5 next.
WAIT=6 shot 09-home-cats --ez cats true
WAIT=6 shot 09b-home-profile --es open profile
WAIT=6 shot 09c-home-whatsnew --ez whatsnew true
WAIT=6 shot 09e-home-snooze --es open snooze
WAIT=6 shot 09f-home-newcat --ez cats true --es open newcat
shot 50-settings --es route settings
for S in categories chats notifications privacy storage access blockedWords; do shot "51-section-$S" --es route "section:$S"; done
shot 52-appearance --es route appearance
for K in trash snoozed; do shot "56-list-$K" --es route "lists:$K"; done
WAIT=6 shot 26-thread-maker --es route thread:c1 --es open maker
# Fold inner screen
adb shell wm size 1812x2176
adb shell wm density 420
WAIT=6 shot 70-fold-home --es route home --ez cats true
shot 72-fold-settings --es route settings
adb shell wm size reset
adb shell wm density reset
# Flip phone cover screen (about the size of a Razr Ultra's outside screen)
adb shell wm size 1080x1272
adb shell wm density 400
WAIT=6 shot 75-cover-home --es route home --ez cats true
WAIT=6 shot 75b-cover-profile --es open profile
shot 79-cover-settings --es route settings
adb shell wm size reset
adb shell wm density reset

shot 01-home
shot 02-home-dark --es mode Dark
for L in Large Pages Carousel Compact Chips Cards Spotlight List Dock; do shot "03-layout-$L" --es mode Light --es layout "$L"; done
shot 04-home-rings --es layout Grid --es pinstyle Rings
WAIT=10 shot 05-home-rowmenu --es open rowmenu --es pinstyle Badges
shot 06-home-strip --es open strip
shot 07-home-perms --es open perms
shot 08-home-duo --es theme d:Grape
WAIT=6 shot 09d-home-unknown --es open unknown
shot 10-thread --es route thread:c1 --es theme Ocean
shot 11-thread-dark --es route thread:c1 --es mode Dark
shot 12-thread-group --es route thread:c3 --es mode Light
adb logcat -d -t 600 '*:W' > ci-logs/log-12-group.txt 2>&1
WAIT=10 shot 12b-thread-group-late --es route thread:c3 --es mode Light
adb logcat -d -t 600 '*:W' > ci-logs/log-12b-group.txt 2>&1
WAIT=10 shot 12c-thread-mom-late --es route thread:c2 --es mode Light
adb exec-out run-as com.jpetrydesign.pyng true 2>/dev/null; adb shell dumpsys activity processes | grep -i "anr\|not responding" > ci-logs/anr.txt 2>&1
shot 13-thread-textXL --es route thread:c1 --es text XL
shot 14-thread-textS --es route thread:c1 --es text S
shot 15-thread-soft --es route thread:c1 --es text M --es bubble Soft
shot 16-thread-pill --es route thread:c1 --es bubble Pill
shot 17-thread-mist --es route thread:c1 --es bubble Rounded --es wall Mist
shot 18-thread-info --es route thread:c1 --es wall None --es open info
shot 19-thread-ts --es route thread:c1 --es open ts
shot 20-thread-menu --es route thread:c1 --es open menu
WAIT=10 shot 20b-thread-menu-late --es route thread:c1 --es open menu
WAIT=8 shot 20c-thread-who --es route thread:c1 --es open menu-who
adb shell dumpsys gfxinfo $PKG > ci-logs/gfx-menu.txt 2>&1
shot 21-thread-plus --es route thread:c1 --es open plus
shot 22-thread-emoji --es route thread:c1 --es open expr
shot 23-thread-stickers --es route thread:c1 --es open stickers
shot 24-thread-gifs --es route thread:c1 --es open gifs
shot 25-thread-schedule --es route thread:c1 --es open schedule
shot 26-thread-maker --es route thread:c1 --es open maker
shot 27-thread-reply --es route thread:c1 --es open reply
shot 28-thread-details --es route thread:c1 --es open details
shot 29-thread-spam --es route thread:c8
shot 30-thread-mom --es route thread:c2
shot 31-thread-empty --es route thread:c9
shot 40-contact --es route contact:c1
shot 41-contact-group --es route contact:c3
shot 42-chat-theme --es route theme:c1
shot 43-search --es route search
shot 44-new --es route new
shot 45-newgroup --es route newgroup
WAIT=8 shot 45b-newchat-picked --es route new --es open group-pick
WAIT=8 shot 45c-newgroup-name --es route new --es open group-name
WAIT=8 shot 45d-share --es route share
WAIT=8 shot 45e-share-dark --es route share --es mode Dark
shot 50-settings --es route settings
for S in chats categories notifications chatfeat privacy access storage about blockedWords; do shot "51-section-$S" --es route "section:$S"; done
shot 52-appearance --es route appearance
shot 52b-studio-chat --es route appearance --es open studio:chat
shot 52c-studio-home --es route appearance --es open studio:home
for K in pinLayout pinStyle bubble textSize showPhotos density appIcon defWall sendStyle reactStyle; do shot "53-pick-$K" --es route appearance --es open "pick:$K"; done
shot 54-profile --es route profile
shot 55-react --es route react
for K in archived starred scheduled spam trash snoozed; do shot "56-list-$K" --es route "lists:$K"; done
for N in 0 1 2 3; do shot "60-setup-$N" --es route "setup:$N"; done
shot 61-log --es route log
shot 62-home-editpins --es open editpins
shot 63-theme --es route theme:c1
shot 64-theme-custom --es route theme:c1 --es open custom
WAIT=16 shot 65-thread-sent --es route thread:c1 --es open send
# Record a real send so the animation can be checked frame by frame.
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.IconClassic --ez demo true --es route thread:c1 --es open send > /dev/null
adb shell screenrecord --time-limit 9 --bit-rate 8000000 /sdcard/send.mp4 || true
adb pull /sdcard/send.mp4 ci-logs/send.mp4 > /dev/null 2>&1 || true
echo "recorded send"
# Record a real long-press on a chat row, with the app already open, to check the springy entrance.
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.IconClassic --ez demo true > /dev/null
sleep 12
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
# Memory with Home open and settled.
adb shell dumpsys meminfo $PKG > ci-logs/meminfo-home.txt 2>&1
(adb shell screenrecord --time-limit 7 --bit-rate 8000000 /sdcard/peek.mp4 || true) &
sleep 1
adb shell input swipe 300 1140 300 1140 900
wait
adb pull /sdcard/peek.mp4 ci-logs/peek.mp4 > /dev/null 2>&1 || true
adb exec-out screencap -p > "$OUT/05b-home-peek-held.png" 2>/dev/null || true
echo "recorded peek"
# The chat list sheet: a slow partial drag (should snap back), a flick up over the pins, then back down (jelly rise).
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.IconClassic --ez demo true > /dev/null
sleep 12
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
(adb shell screenrecord --time-limit 16 --bit-rate 8000000 /sdcard/sheet.mp4 || true) &
sleep 1
adb shell input swipe 540 1700 540 1560 700
sleep 3
adb exec-out screencap -p > "$OUT/05c-sheet-snapback.png"
adb shell input swipe 540 1900 540 700 250
sleep 3
adb exec-out screencap -p > "$OUT/05d-sheet-up.png"
adb shell input swipe 540 800 540 2000 250
sleep 4
adb exec-out screencap -p > "$OUT/05e-sheet-down.png"
wait
adb pull /sdcard/sheet.mp4 ci-logs/sheet.mp4 > /dev/null 2>&1 || true
echo "recorded sheet"
shot 66-thread-media --es route thread:c9
WAIT=10 shot 67-thread-viewer --es route thread:c9 --es open viewer
# Memory after a chat full of photos and the viewer.
adb shell dumpsys meminfo $PKG > ci-logs/meminfo-media.txt 2>&1
WAIT=10 shot 67b-thread-viewer-video --es route thread:c9 --es open viewer-video
WAIT=10 shot 67c-thread-viewer-strip --es route thread:c9 --es open viewer-strip
shot 59-version-history --es route section:versions
WAIT=10 shot 68-chat-search --es route thread:c9 --es open search
WAIT=10 shot 68b-chat-search-results --es route thread:c1 --es open search-q
WAIT=10 shot 67d-thread-menu-strip --es route thread:c9 --es open menu-strip
WAIT=10 shot 67e-thread-menu-link --es route thread:c1 --es open menu-link
shot 40b-contact-media --es route contact:c9
shot 68-section-chats-top --es route section:chats

# Fold inner screen
adb shell wm size 1812x2176
adb shell wm density 420
shot 70-fold-home --es route home
shot 71-fold-thread --es route thread:c1
shot 72-fold-settings --es route settings
adb shell wm size reset
adb shell wm density reset

# Flip phone cover screen (about the size of a Razr Ultra's outside screen)
adb shell wm size 1080x1272
adb shell wm density 400
shot 75-cover-home --es route home
shot 76-cover-thread --es route thread:c1
shot 77-cover-thread-keyboard --es route thread:c3 --es open expr
shot 78-cover-share --es route share
shot 79-cover-settings --es route settings
adb shell wm size reset
adb shell wm density reset

# New in 0.9
WAIT=8 shot 80-thread-unread-pill --es route thread:c2
shot 82-section-starred-empty --es route lists:starred

adb logcat -d -s AndroidRuntime:E > ci-logs/runtime-errors.txt 2>&1
adb logcat -d | grep -E "Choreographer|pyng|Pyng" | tail -400 > ci-logs/logcat-app.txt 2>&1
(command -v mogrify >/dev/null && mogrify -resize 50% "$OUT"/*.png) || true
ls "$OUT" | wc -l

# The viewer last (it plays a video), to see whether anything after it suffers.
WAIT=10 shot 00v-viewer --es route thread:c9 --es open viewer
WAIT=10 shot 00w-viewer-video --es route thread:c9 --es open viewer-video
WAIT=10 shot 00w2-viewer-strip --es route thread:c9 --es open viewer-strip

# Liquid Glass last: its heavy live blur has stalled the emulator's software graphics a few times.
WAIT=6 shot 00x-liquid-settings --es route settings --es glass "'Liquid Glass'"
WAIT=6 shot 00y-liquid-appearance --es route appearance --es glass "'Liquid Glass'"
WAIT=6 shot 00za-liquid-home --es glass "'Liquid Glass'"
shot 09g-home-liquid --es glass "'Liquid Glass'"
