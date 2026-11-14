#!/usr/bin/env bash
# Installs the APK on a running emulator, opens it and fails if it crashes.
# Screenshots are written to ./shots. Usage: launch-test.sh path/to/app.apk
set -euo pipefail
apk="$1"
pkg=com.aicodestudio.app
mkdir -p shots

fail() {
  echo "$1"
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error title=Launch test::$1"; fi
  exit 1
}
trap 'if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error title=Launch test::stopped at line $LINENO"; fi' ERR

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
  site_failed="The website did not open inside the app. Screen text: $(grep -oE 'text="[^"]+"' shots/ui.xml | paste -sd ' ' - | cut -c1-300)"
fi

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n "$pkg/.MainActivity"
sleep 5
adb exec-out screencap -p > shots/3-reopened.png

adb logcat -d > shots/logcat.txt
if grep -E "FATAL EXCEPTION|ANR in $pkg" -A 12 shots/logcat.txt; then
  fail "The app crashed or stopped responding: $(grep -E "FATAL EXCEPTION" -A 4 shots/logcat.txt | paste -sd ' ' - | cut -c1-600)"
fi
if ! adb shell pidof "$pkg" > /dev/null; then
  fail "The app is not running after launch."
fi
adb shell dumpsys activity activities | grep -E "ResumedActivity|mResumedActivity" | grep -q "$pkg/.MainActivity" || {
  fail "The app's screen is not in front."
}
if [ -n "${site_failed:-}" ]; then fail "$site_failed"; fi
echo "Launch test passed."
