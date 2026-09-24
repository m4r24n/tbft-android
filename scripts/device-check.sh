#!/usr/bin/env bash
set -euo pipefail
trap 'adb pull /sdcard/Download/tbft-native-shots ../native-screenshots || true' EXIT
gradle --no-daemon connectedDebugAndroidTest
