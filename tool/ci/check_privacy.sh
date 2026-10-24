#!/usr/bin/env bash
# Enforces Unscroll's core promise: the app can never reach the network and
# ships no tracking/ads/non-free SDKs. Used by CI and before every release.
#
# Usage: tool/ci/check_privacy.sh <path-to-release.apk>
# Run from the repository root after `flutter build apk --release`.
set -euo pipefail

APK="${1:?usage: check_privacy.sh <release.apk>}"
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 2; }

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
[[ -n "$ANDROID_HOME" ]] || { echo "ANDROID_HOME is not set" >&2; exit 2; }
BUILD_TOOLS="$(ls -1 "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
AAPT2="$ANDROID_HOME/build-tools/$BUILD_TOOLS/aapt2"

fail=0
error() { echo "::error::$1"; fail=1; }

# 1. Forbidden permissions in the merged release manifest.
FORBIDDEN_PERMISSIONS=(
  android.permission.INTERNET
  android.permission.BIND_VPN_SERVICE
  com.google.android.gms.permission.AD_ID
  android.permission.READ_CONTACTS
  android.permission.READ_SMS
  android.permission.ACCESS_FINE_LOCATION
  android.permission.ACCESS_COARSE_LOCATION
  android.permission.RECORD_AUDIO
  android.permission.CAMERA
)
permissions="$("$AAPT2" dump permissions "$APK")"
for p in "${FORBIDDEN_PERMISSIONS[@]}"; do
  if grep -qF "$p" <<<"$permissions"; then
    error "Forbidden permission in release APK: $p"
  fi
done

# 2. No VpnService declared anywhere.
if "$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" | grep -q 'android.net.VpnService'; then
  error "Release APK declares a VpnService (not allowed: see docs/ARCHITECTURE.md)"
fi

# 3. Banned Dart packages (network, analytics, ads, crash reporting, non-free).
BANNED_DART='^  (http|dio|chopper|retrofit|graphql[a-z_]*|web_socket_channel|socket_io_client|grpc|firebase_[a-z_]*|cloud_firestore|google_mobile_ads|sentry[a-z_]*|amplitude[a-z_]*|mixpanel[a-z_]*|appsflyer[a-z_]*|facebook[a-z_]*|onesignal[a-z_]*|posthog[a-z_]*|datadog[a-z_]*|bugsnag[a-z_]*|google_sign_in|in_app_purchase|url_launcher_web):'
if [[ -f pubspec.lock ]] && grep -Eq "$BANNED_DART" pubspec.lock; then
  error "Banned Dart package(s) in pubspec.lock: $(grep -Eo "$BANNED_DART" pubspec.lock | tr -d ' :' | tr '\n' ' ')"
fi

# 4. Banned Android libraries in the release runtime classpath.
BANNED_JVM='com\.google\.firebase|com\.google\.android\.gms|com\.squareup\.okhttp|com\.squareup\.retrofit|io\.sentry|com\.crashlytics|com\.appsflyer|com\.facebook|com\.mixpanel|com\.amplitude|com\.onesignal|io\.ktor:ktor-client|com\.android\.billingclient'
if [[ -x android/gradlew ]]; then
  deps="$(cd android && ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath 2>/dev/null || true)"
  if [[ -z "$deps" ]]; then
    error "Could not resolve :app releaseRuntimeClasspath for the dependency check"
  elif hits="$(grep -Eo "($BANNED_JVM)[^ ]*" <<<"$deps" | sort -u)" && [[ -n "$hits" ]]; then
    error "Banned Android libraries in release classpath: $(tr '\n' ' ' <<<"$hits")"
  fi
fi

if (( fail )); then
  echo "Privacy check FAILED. Unscroll must never reach the network or bundle tracking/non-free SDKs."
  exit 1
fi
echo "Privacy check passed: no network permission, no VpnService, no banned dependencies."
