#!/bin/bash
# Promo screenshots: sample chats with photos, at phone, Fold cover and Fold open sizes, light and dark.
PKG=com.jpetrydesign.pyng
APK="$1"
OUT=promo-out
mkdir -p "$OUT"
adb install -r "$APK" || exit 1
for p in POST_NOTIFICATIONS READ_CONTACTS READ_MEDIA_IMAGES READ_MEDIA_VIDEO RECORD_AUDIO; do
  adb shell pm grant $PKG android.permission.$p 2>/dev/null
done
# Start once so the app's files folder exists, then hand it the photos.
adb shell am start -W -n $PKG/.IconClassic --ez demo true > /dev/null
sleep 4
adb shell mkdir -p /data/local/tmp/promo
adb push ci/promo-faces/. /data/local/tmp/promo/ > /dev/null
adb shell chmod -R 755 /data/local/tmp/promo
# Copy into the app's own storage (debug build), where it can always read them.
adb shell run-as $PKG sh -c "'mkdir -p files/promo && cp /data/local/tmp/promo/* files/promo/ && ls files/promo | head -3'"

# Keep the emulator's own launcher (and its "isn't responding" dialogs) out of the way.
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher > /dev/null 2>&1 || true
# Skip Android's "Viewing full screen" tip, since promo shots hide the navigation bar.
adb shell settings put secure immersive_mode_confirmations confirmed
# A clean status bar: 9:41, full battery and signal, no notification icons.
adb shell settings put global sysui_demo_allowed 1
sb() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
sb enter
sb clock -e hhmm 0941
sb battery -e level 100 -e plugged false
sb network -e wifi show -e level 4 -e fully true
sb network -e mobile show -e datatype none -e level 4 -e fully true
sb notifications -e visible false

shot() {
  local name="$1"; shift
  adb shell am force-stop $PKG
  # Start from Ocean in light mode each time (settings stick between launches); later extras win.
  adb shell am start -W -n $PKG/.IconClassic --ez demo true --ez promo true --es theme Ocean --es mode Light "$@" > /dev/null
  sleep "${WAIT:-7}"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
  # Re-apply the clean status bar each time (it can reset when system UI restarts).
  sb enter; sb clock -e hhmm 0941; sb battery -e level 100 -e plugged false
  sb network -e wifi show -e level 4 -e fully true; sb network -e mobile show -e datatype none -e level 4 -e fully true
  sb notifications -e visible false
  sleep 1
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "shot $name"
}

for dev in "s26-ultra 1440x3120 560" "fold8-cover 1080x2520 420" "fold8-open 1968x2184 420"; do
  set -- $dev
  adb shell wm size "$2"
  adb shell wm density "$3"
  sleep 3
  sb enter
  sb clock -e hhmm 0941
  # Peek only (the rest of the set is already done).
  WAIT=10 shot "$1-peek" --es open rowmenu
  WAIT=10 shot "$1-peek-dark" --es open rowmenu --es mode Dark
done
adb shell wm size reset
adb shell wm density reset
sleep 3
# Record the peek opening (the recording starts before launch, since a cold start is slow here).
adb shell am force-stop $PKG
(adb shell screenrecord --time-limit 25 --bit-rate 8000000 /sdcard/peek.mp4 || true) &
sleep 1
adb shell am start -n $PKG/.IconClassic --ez demo true --ez promo true --es theme Ocean --es mode Light --es open rowmenu > /dev/null
wait
adb pull /sdcard/peek.mp4 "$OUT/peek.mp4" > /dev/null 2>&1 || true
ls "$OUT" | wc -l
