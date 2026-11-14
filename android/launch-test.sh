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

# The loading/offline screen is a plain Android layout with text; the website is not.
# If that text is still showing, the site did not open.
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
adb pull /sdcard/ui.xml shots/ui.xml > /dev/null 2>&1 || true
if grep -qE "Can't reach AI Code Studio|Can&apos;t reach|Opening AI Code Studio|Still starting" shots/ui.xml 2>/dev/null; then
  echo "The website did not open inside the app (still on the loading or offline screen)."
  site_failed=1
fi

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n "$pkg/.MainActivity"
sleep 5
adb exec-out screencap -p > shots/3-reopened.png

adb logcat -d > shots/logcat.txt
if grep -E "FATAL EXCEPTION|ANR in $pkg" -A 12 shots/logcat.txt; then
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
if [ -n "${site_failed:-}" ]; then exit 1; fi
echo "Launch test passed."
