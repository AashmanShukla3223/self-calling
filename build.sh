#!/usr/bin/env bash
# build.sh — Build signed release APK only (NO debug builds)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> Building signed release APK (no debug)"
./gradlew --offline assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
  echo "==> Signed release APK ready: $APK"
  ls -lh "$APK"
  echo ""
  echo "==> Install on device:"
  echo "    adb -d install -r $APK"
else
  echo "!! Build failed or APK not found" >&2
  exit 1
fi
