#!/usr/bin/env bash
set -euo pipefail
trap 'adb pull /sdcard/Android/data/info.marzan.tbft.nativeapp.debug/files/screenshots ../native-screenshots || true' EXIT
gradle --no-daemon connectedDebugAndroidTest
