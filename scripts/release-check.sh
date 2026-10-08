#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${APK:-$ROOT_DIR/app/build/outputs/apk/release/app-release.apk}"

cd "$ROOT_DIR"
~/.gradle-agents/gw "${GW_WHO:-owner}" assembleRelease

if [[ ! -f "$APK" ]]; then
    echo "Release APK not found: $APK" >&2
    exit 1
fi

AAPT="${AAPT:-}"
if [[ -z "$AAPT" ]]; then
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "$SDK_ROOT" && -f "$ROOT_DIR/local.properties" ]]; then
        while IFS='=' read -r key value; do
            if [[ "$key" == "sdk.dir" ]]; then
                SDK_ROOT="${value//\\:/:}"
                break
            fi
        done < "$ROOT_DIR/local.properties"
    fi
    if [[ -z "$SDK_ROOT" ]]; then
        echo "Set ANDROID_HOME or ANDROID_SDK_ROOT, configure sdk.dir in local.properties, or provide AAPT." >&2
        exit 1
    fi
    AAPT="$(find "$SDK_ROOT/build-tools" -type f -name aapt -print | grep -v -- '-rc' | sort | tail -n 1)"
fi
if [[ ! -x "$AAPT" ]]; then
    echo "aapt is not executable: $AAPT" >&2
    exit 1
fi

printf 'APK size: '
wc -c < "$APK"
BADGING="$("$AAPT" dump badging "$APK")"
printf '%s\n' "$BADGING"

if ! grep -q 'android.permission.INTERNET' <<<"$BADGING"; then
    echo "Release APK is missing INTERNET permission." >&2
    exit 1
fi

if ! grep -q "^package:.*versionName=" <<<"$BADGING"; then
    echo "APK does not declare package versionName." >&2
    exit 1
fi

MANIFEST="$("$AAPT" dump xmltree "$APK" AndroidManifest.xml)"
if grep -E -q 'android:debuggable\([^)]*\)=\(type 0x12\)0xffffffff' <<<"$MANIFEST"; then
    echo "Release APK manifest has android:debuggable=true." >&2
    exit 1
fi
if ! grep -E -q 'android:allowBackup\([^)]*\)=\(type 0x12\)0x0' <<<"$MANIFEST"; then
    echo "Release APK manifest must set android:allowBackup=false." >&2
    exit 1
fi

if grep -E -q 'android:usesCleartextTraffic\([^)]*\)=\(type 0x12\)0xffffffff' <<<"$MANIFEST"; then
    echo "Release APK manifest allows cleartext traffic." >&2
    exit 1
fi

printf '%s\n' "Release APK checks passed: $APK"
