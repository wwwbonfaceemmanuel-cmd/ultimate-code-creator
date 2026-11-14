#!/usr/bin/env bash
# Installs the APK on a running emulator, opens it and fails if it crashes.
# Screenshots are written to ./shots. Usage: launch-test.sh path/to/app.apk
set -euo pipefail
apk="$1"
pkg=com.aicodestudio.app
mkdir -p shots

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 8
adb exec-out screencap -p > shots/1-after-8s.png

# The hosted site can take a while to wake up; give it time, then look again.
sleep 75
adb exec-out screencap -p > shots/2-after-83s.png

# Print the text on screen so the log shows what the user would see.
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
echo "--- text on screen ---"
adb shell cat /sdcard/ui.xml 2>/dev/null | grep -oE '(text|content-desc)="[^"]+"' | sort -u | head -40 || true
echo "----------------------"

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n "$pkg/.MainActivity"
sleep 5
adb exec-out screencap -p > shots/3-reopened.png

if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $pkg" -A 12; then
  echo "The app crashed or stopped responding."
  exit 1
fi
if ! adb shell pidof "$pkg" > /dev/null; then
  echo "The app is not running after launch."
  exit 1
fi
adb shell dumpsys activity activities | grep -E "ResumedActivity|mResumedActivity" | grep -q "$pkg/.MainActivity" || {
  echo "The app's screen is not in front."
  exit 1
}
echo "Launch test passed."
