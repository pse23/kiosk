#!/bin/bash
# Build Kiosk in Docker with no local JDK or Android SDK.
# Usage: scripts/docker-build.sh [gradle tasks...]   (default: quality :app:assembleDebug)
# The SDK, Gradle caches, and debug signing key persist in the kiosk-android-sdk,
# kiosk-gradle, and kiosk-android-home volumes, so debug APKs install over each other.
# Running this accepts the Android SDK licenses inside the container.
set -euo pipefail
cd "$(dirname "$0")/.."
tasks=("$@")
[ ${#tasks[@]} -eq 0 ] && tasks=(quality :app:assembleDebug)

exec docker run --rm \
  -v "$PWD":/src -w /src \
  -v kiosk-android-sdk:/sdk -v kiosk-gradle:/root/.gradle -v kiosk-android-home:/root/.android \
  -e ANDROID_HOME=/sdk -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
  -e DEBIAN_FRONTEND=noninteractive \
  eclipse-temurin:17-jdk bash -euo pipefail -c '
    if [ ! -x /sdk/cmdline-tools/latest/bin/sdkmanager ]; then
      apt-get update -qq && apt-get install -y -qq unzip curl >/dev/null
      mkdir -p /sdk/cmdline-tools && cd /sdk/cmdline-tools
      curl -sSL -o t.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
      unzip -q t.zip && rm t.zip && mv cmdline-tools latest && cd /src
    fi
    yes | /sdk/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null || true
    status=0
    ./gradlew --no-daemon "$@" || status=$?
    chown -R "$HOST_UID:$HOST_GID" /src/app/build /src/build /src/.gradle /src/.kotlin /src/app/schemas 2>/dev/null || true
    exit $status
  ' _ "${tasks[@]}"
