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
  WAIT=8 shot "$1-peek" --es open rowmenu
  # Core palette: green, red, blue.
  shot "$1-home-forest" --es theme Forest
  shot "$1-home-burgundy" --es theme Burgundy
  shot "$1-home-cobalt" --es theme Cobalt
  shot "$1-chat-forest" --es route thread:g1 --es theme Forest
  shot "$1-chat-burgundy-dark" --es route thread:g1 --es theme Burgundy --es mode Dark
  shot "$1-chat-cobalt" --es route thread:g3 --es theme Cobalt
  # Warm alternates: rose gold, plum, pink, brown.
  shot "$1-home-terracotta" --es theme Terracotta
  shot "$1-home-plum" --es theme Plum
  shot "$1-home-rose" --es theme Rose
  shot "$1-home-mocha" --es theme Mocha
  shot "$1-chat-terracotta" --es route thread:g1 --es theme Terracotta
  shot "$1-chat-plum-dark" --es route thread:g3 --es theme Plum --es mode Dark
  shot "$1-chat-rose" --es route thread:g1 --es theme Rose
  shot "$1-chat-mocha-dark" --es route thread:g1 --es theme Mocha --es mode Dark
done
adb shell wm size reset
adb shell wm density reset
ls "$OUT" | wc -l
