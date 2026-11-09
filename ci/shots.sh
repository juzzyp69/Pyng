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

shot() {
  local name="$1"; shift
  adb shell am force-stop $PKG
  adb logcat -b crash -c
  adb shell am start -W -n $PKG/.IconClassic --ez demo true "$@" > /dev/null
  sleep "${WAIT:-4}"
  adb exec-out screencap -p > "$OUT/$name.png"
  local crash
  crash=$(adb logcat -b crash -d)
  if [ -n "$crash" ]; then
    { echo "===== $name ====="; echo "$crash"; } >> ci-logs/crashes.txt
  fi
  echo "shot $name"
}

shot 01-home
shot 02-home-dark --es mode Dark
for L in Large Pages Carousel Compact Chips Cards Spotlight List Dock; do shot "03-layout-$L" --es mode Light --es layout "$L"; done
shot 04-home-rings --es layout Grid --es pinstyle Rings
shot 05-home-rowmenu --es open rowmenu --es pinstyle Badges
shot 06-home-strip --es open strip
shot 07-home-perms --es open perms
shot 08-home-duo --es theme "'Grape Soda'"
shot 10-thread --es route thread:c1 --es theme Ocean
shot 11-thread-dark --es route thread:c1 --es mode Dark
shot 12-thread-group --es route thread:c3 --es mode Light
shot 13-thread-textXL --es route thread:c1 --es text XL
shot 14-thread-textS --es route thread:c1 --es text S
shot 15-thread-soft --es route thread:c1 --es text M --es bubble Soft
shot 16-thread-pill --es route thread:c1 --es bubble Pill
shot 17-thread-mist --es route thread:c1 --es bubble Rounded --es wall Mist
shot 18-thread-info --es route thread:c1 --es wall None --es open info
shot 19-thread-ts --es route thread:c1 --es open ts
shot 20-thread-menu --es route thread:c1 --es open menu
WAIT=10 shot 20b-thread-menu-late --es route thread:c1 --es open menu
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
shot 50-settings --es route settings
for S in chats notifications chatfeat privacy access storage about; do shot "51-section-$S" --es route "section:$S"; done
shot 52-appearance --es route appearance
for K in pinLayout pinStyle bubble textSize showPhotos density appIcon defWall; do shot "53-pick-$K" --es route appearance --es open "pick:$K"; done
shot 54-profile --es route profile
shot 55-react --es route react
for K in archived starred scheduled spam; do shot "56-list-$K" --es route "lists:$K"; done
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
shot 66-thread-media --es route thread:c9
WAIT=10 shot 67-thread-viewer --es route thread:c9 --es open viewer
WAIT=10 shot 67b-thread-viewer-video --es route thread:c9 --es open viewer-video
shot 68-section-chats-top --es route section:chats

# Fold inner screen
adb shell wm size 1812x2176
adb shell wm density 420
shot 70-fold-home --es route home
shot 71-fold-thread --es route thread:c1
shot 72-fold-settings --es route settings
adb shell wm size reset
adb shell wm density reset

adb logcat -d -s AndroidRuntime:E > ci-logs/runtime-errors.txt 2>&1
adb logcat -d | grep -E "Choreographer|pyng|Pyng" | tail -400 > ci-logs/logcat-app.txt 2>&1
(command -v mogrify >/dev/null && mogrify -resize 50% "$OUT"/*.png) || true
ls "$OUT" | wc -l
