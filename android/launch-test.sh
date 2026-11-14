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

# Record the text on screen so the run shows what the user would see.
adb shell rm -f /sdcard/ui.xml
adb shell uiautomator dump /sdcard/ui.xml || true
adb pull /sdcard/ui.xml shots/ui.xml > /dev/null 2>&1 || true
seen=$(grep -oE '(text|content-desc)="[^"]+"' shots/ui.xml 2>/dev/null | sed -E 's/^[a-z-]+="//; s/"$//' | awk '!x[$0]++' | head -25 | paste -sd '|' - || true)
echo "Text on screen: ${seen:-none found}"
# Shown as a note on the run's summary page when run by GitHub Actions.
if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::notice title=Text on the app screen::${seen:-none found}"; fi

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
echo "Launch test passed."
