#!/usr/bin/env bash
# Installs the minified RELEASE build (what users install) on the running emulator, launches it and
# fails if the app crashes: catches R8 / Hilt / Room / serialization problems the debug build hides.
set -u
APK=app/build/outputs/apk/release/app-release.apk
./gradlew --no-daemon :app:assembleRelease -q || { echo "release build failed"; exit 1; }
adb uninstall studio.pyreels.app >/dev/null 2>&1 || true
adb uninstall studio.pyreels.app.test >/dev/null 2>&1 || true
adb install "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
adb shell monkey -p studio.pyreels.app -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 15
if ! adb shell pidof studio.pyreels.app >/dev/null; then
  echo "SMOKE: release app is not running"; adb logcat -d -b crash | tail -60; exit 1
fi
if adb logcat -d -b crash | grep -q "studio.pyreels"; then
  echo "SMOKE: release app crashed"; adb logcat -d -b crash | tail -60; exit 1
fi
echo "SMOKE: release app launched and stayed alive"
