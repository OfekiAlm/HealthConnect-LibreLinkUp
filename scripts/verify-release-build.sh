#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: scripts/verify-release-build.sh [--verify-install] [--app-only|--wear-only] [--skip-clean]

Builds the release APKs, signs them with a provided keystore, verifies the
signatures, and optionally installs them onto connected devices.

Required environment variables:
  ANDROID_SDK_ROOT or ANDROID_HOME
  KEYSTORE_PATH
  KEYSTORE_ALIAS
  KEYSTORE_PASSWORD

Optional environment variables:
  KEY_PASSWORD        Defaults to KEYSTORE_PASSWORD when omitted
  BUILD_TOOLS_VERSION Overrides the auto-detected Android build-tools version
  APP_DEVICE_SERIAL   adb serial used when installing the phone APK
  WEAR_DEVICE_SERIAL  adb serial used when installing the Wear OS APK

Examples:
  KEYSTORE_PATH=/path/to/release.jks \
  KEYSTORE_ALIAS=release \
  KEYSTORE_PASSWORD=secret \
  ./scripts/verify-release-build.sh

  KEYSTORE_PATH=/path/to/release.jks \
  KEYSTORE_ALIAS=release \
  KEYSTORE_PASSWORD=secret \
  APP_DEVICE_SERIAL=R58M... \
  WEAR_DEVICE_SERIAL=emulator-5554 \
  ./scripts/verify-release-build.sh --verify-install
EOF
}

log() {
    printf '\n==> %s\n' "$1"
}

fail() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_file() {
    [[ -f "$1" ]] || fail "Missing required file: $1"
}

VERIFY_INSTALL=0
RUN_APP=1
RUN_WEAR=1
RUN_CLEAN=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --verify-install)
            VERIFY_INSTALL=1
            ;;
        --app-only)
            RUN_WEAR=0
            ;;
        --wear-only)
            RUN_APP=0
            ;;
        --skip-clean)
            RUN_CLEAN=0
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage
            fail "Unknown argument: $1"
            ;;
    esac
    shift
done

(( RUN_APP == 1 || RUN_WEAR == 1 )) || fail "Select at least one target APK"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLEW="$REPO_ROOT/gradlew"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
KEYSTORE_PATH="${KEYSTORE_PATH:-}"
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-}"
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-}"
OUTPUT_DIR="$REPO_ROOT/build/release"
APP_ID="org.c99.healthconnect_librelinkup"

require_file "$GRADLEW"
require_command java
require_command keytool

[[ -n "$ANDROID_SDK_ROOT" ]] || fail "ANDROID_SDK_ROOT or ANDROID_HOME must be set"
[[ -d "$ANDROID_SDK_ROOT" ]] || fail "Android SDK directory does not exist: $ANDROID_SDK_ROOT"
[[ -n "$KEYSTORE_PATH" ]] || fail "KEYSTORE_PATH must be set"
[[ -n "$KEYSTORE_ALIAS" ]] || fail "KEYSTORE_ALIAS must be set"
[[ -n "$KEYSTORE_PASSWORD" ]] || fail "KEYSTORE_PASSWORD must be set"
require_file "$KEYSTORE_PATH"

if [[ -z "$BUILD_TOOLS_VERSION" ]]; then
    build_tools_root="$ANDROID_SDK_ROOT/build-tools"
    [[ -d "$build_tools_root" ]] || fail "Android build-tools directory not found: $build_tools_root"
    BUILD_TOOLS_VERSION="$(find "$build_tools_root" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -n 1)"
    [[ -n "$BUILD_TOOLS_VERSION" ]] || fail "No Android build-tools versions found under $build_tools_root"
fi

ZIPALIGN="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/zipalign"
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"
ADB="${ADB:-$ANDROID_SDK_ROOT/platform-tools/adb}"

require_file "$ZIPALIGN"
require_file "$APKSIGNER"

log "Checking signing keystore and alias"
keytool -list -keystore "$KEYSTORE_PATH" -storepass "$KEYSTORE_PASSWORD" -alias "$KEYSTORE_ALIAS" >/dev/null 2>&1 \
    || fail "Unable to read alias '$KEYSTORE_ALIAS' from $KEYSTORE_PATH"

mkdir -p "$OUTPUT_DIR"

declare -a gradle_tasks=()
if (( RUN_CLEAN == 1 )); then
    gradle_tasks+=(clean)
fi
if (( RUN_APP == 1 )); then
    gradle_tasks+=(:app:assembleRelease)
fi
if (( RUN_WEAR == 1 )); then
    gradle_tasks+=(:wearable:assembleRelease)
fi

log "Building release APKs with Gradle"
(
    cd "$REPO_ROOT"
    "$GRADLEW" --no-daemon "${gradle_tasks[@]}"
)

find_unsigned_apk() {
    local module="$1"
    local release_dir="$REPO_ROOT/$module/build/outputs/apk/release"
    local unsigned_apk="$release_dir/$module-release-unsigned.apk"
    local signed_apk="$release_dir/$module-release.apk"

    if [[ -f "$unsigned_apk" ]]; then
        printf '%s\n' "$unsigned_apk"
        return 0
    fi
    if [[ -f "$signed_apk" ]]; then
        printf '%s\n' "$signed_apk"
        return 0
    fi

    fail "Could not find release APK output for module '$module' in $release_dir"
}

sign_module() {
    local module="$1"
    local source_apk="$2"
    local aligned_apk="$OUTPUT_DIR/$module-release-aligned.apk"
    local final_apk="$OUTPUT_DIR/$module-release-signed.apk"

    log "Aligning $module APK"
    "$ZIPALIGN" -f -p 4 "$source_apk" "$aligned_apk"

    log "Signing $module APK"
    KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" KEY_PASSWORD="$KEY_PASSWORD" \
        "$APKSIGNER" sign \
        --ks "$KEYSTORE_PATH" \
        --ks-key-alias "$KEYSTORE_ALIAS" \
        --ks-pass env:KEYSTORE_PASSWORD \
        --key-pass env:KEY_PASSWORD \
        --out "$final_apk" \
        "$aligned_apk"

    log "Verifying $module APK signature"
    "$APKSIGNER" verify --verbose --print-certs "$final_apk"
}

resolve_serial() {
    local configured_serial="$1"
    local label="$2"
    local device_count

    if [[ -n "$configured_serial" ]]; then
        printf '%s\n' "$configured_serial"
        return 0
    fi

    device_count="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
    if [[ "$device_count" -eq 1 ]]; then
        "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
        return 0
    fi

    fail "Set ${label}_DEVICE_SERIAL or connect exactly one device before using --verify-install"
}

install_and_verify() {
    local module="$1"
    local apk_path="$2"
    local serial="$3"

    log "Installing $module APK on $serial"
    "$ADB" -s "$serial" install -r "$apk_path"

    log "Verifying package install for $module on $serial"
    "$ADB" -s "$serial" shell pm path "$APP_ID" | tr -d '\r' | grep -q '^package:' \
        || fail "Package $APP_ID was not found on device $serial after installing $module"
}

declare -A selected_modules=()
if (( RUN_APP == 1 )); then
    selected_modules[app]="$(find_unsigned_apk app)"
fi
if (( RUN_WEAR == 1 )); then
    selected_modules[wearable]="$(find_unsigned_apk wearable)"
fi

for module in "${!selected_modules[@]}"; do
    sign_module "$module" "${selected_modules[$module]}"
done

if (( VERIFY_INSTALL == 1 )); then
    require_file "$ADB"
    if (( RUN_APP == 1 )); then
        install_and_verify app "$OUTPUT_DIR/app-release-signed.apk" "$(resolve_serial "${APP_DEVICE_SERIAL:-}" APP)"
    fi
    if (( RUN_WEAR == 1 )); then
        install_and_verify wearable "$OUTPUT_DIR/wearable-release-signed.apk" "$(resolve_serial "${WEAR_DEVICE_SERIAL:-}" WEAR)"
    fi
fi

log "Release verification complete"
if (( RUN_APP == 1 )); then
    printf 'Phone APK: %s\n' "$OUTPUT_DIR/app-release-signed.apk"
fi
if (( RUN_WEAR == 1 )); then
    printf 'Wear APK:  %s\n' "$OUTPUT_DIR/wearable-release-signed.apk"
fi
