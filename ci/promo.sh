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
adb shell mkdir -p /sdcard/Android/data/$PKG/files/promo
adb push ci/promo-faces/. /sdcard/Android/data/$PKG/files/promo/ > /dev/null
adb shell ls /sdcard/Android/data/$PKG/files/promo | head -3

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
  adb shell am start -W -n $PKG/.IconClassic --ez demo true --ez promo true "$@" > /dev/null
  sleep "${WAIT:-7}"
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
  shot "$1-home"
  shot "$1-home-dark" --es mode Dark
  shot "$1-chat" --es route thread:g1
  shot "$1-chat-dark" --es route thread:g1 --es mode Dark
  shot "$1-group" --es route thread:g3
  shot "$1-group-dark" --es route thread:g3 --es mode Dark
  shot "$1-settings" --es route settings
  shot "$1-appearance" --es route appearance
  shot "$1-chat-theme" --es route theme:g1
done
adb shell wm size reset
adb shell wm density reset
ls "$OUT" | wc -l
