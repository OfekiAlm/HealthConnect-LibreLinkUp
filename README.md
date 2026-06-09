<!--
 Copyright 2026 Sam Steele
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# HealthConnect-LibreLinkUp

Syncs the latest glucose reading from Freestyle Libre sensors via LibreLinkUp to HealthConnect and WearOS

> ⚠️ **For informational use only.** This app is **not a medical device**, **not a replacement for
> the official Libre apps**, and **not intended for emergency glucose decisions**. It makes no
> FDA/CE or medical-compliance claims. All alerts and displays are convenience features only.
> **Always verify critical glucose readings with your official device/app.**

![App Screenshot](app.png)

![Complication Screenshot](complication.png)

![Tile Screenshot](wearable/src/main/res/drawable-round/tile_preview.png)

## Requirements

 * Android 9.0+
 * [Google HealthConnect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) (This is built-in on Android 14+)
 * Android WearOS 3.0+
 * Freestyle Libre 2 or 3 glucose sensor linked to a [Freestyle LibreLinkUp](https://librelinkup.com/) account (see below)

## Install

Download the [latest release](https://github.com/c99koder/HealthConnect-LibreLinkUp/releases/latest) and install `app-release.apk` on your phone and the optional `wearable-release.apk` on your WearOS watch

## Release APK build and signing

Use `/tmp/workspace/OfekiAlm/HealthConnect-LibreLinkUp/scripts/verify-release-build.sh` to validate the release toolchain, build both release APKs, sign them, verify the signatures, and optionally confirm installation over `adb`.

### Required tools

 * Java (includes `keytool`)
 * Android SDK with:
   * build-tools (`zipalign`, `apksigner`)
   * platform-tools (`adb`) when using install verification
 * The repository Gradle wrapper (`./gradlew`)

### Required signing environment

```bash
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
export KEYSTORE_PATH=/absolute/path/to/release.jks
export KEYSTORE_ALIAS=release
export KEYSTORE_PASSWORD='replace-me'
export KEY_PASSWORD="$KEYSTORE_PASSWORD" # optional when the key password matches
```

### Release build commands

```bash
cd /tmp/workspace/OfekiAlm/HealthConnect-LibreLinkUp
./scripts/verify-release-build.sh
```

Signed release APKs are written to:

 * `build/release/app-release-signed.apk`
 * `build/release/wearable-release-signed.apk`

### Optional install verification

If you want the script to install the signed APKs and verify the package is present afterwards, connect the target devices with `adb` and run:

```bash
export APP_DEVICE_SERIAL=<phone-adb-serial>
export WEAR_DEVICE_SERIAL=<wear-device-adb-serial>
./scripts/verify-release-build.sh --verify-install
```

### Signing checklist

 * [ ] `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) points to a valid Android SDK
 * [ ] `zipalign` and `apksigner` are present in the installed Android build-tools
 * [ ] `adb` is available when install verification is required
 * [ ] `KEYSTORE_PATH`, `KEYSTORE_ALIAS`, and `KEYSTORE_PASSWORD` are exported
 * [ ] The keystore alias resolves successfully before the build starts
 * [ ] `:app:assembleRelease` and `:wearable:assembleRelease` complete successfully
 * [ ] `apksigner verify --verbose --print-certs` passes for both final APKs
 * [ ] Installation is verified on the phone and optional Wear OS target when `--verify-install` is used

## Usage

Open the Freestyle Libre app and tap "Connected Apps" from the menu, then send yourself an invitation to view your data via LibreLinkUp.  Install the LibreLinkUp app on your phone, login, and accept the invitation.

Launch the `LibreLinkUp for HealthConnect` app on your phone, select your LibreView region, enter your Freestyle LibreLinkUp email address and password, then tap the login button.

The app will fetch your latest glucose setting every 15 minutes and write the new value into HealthConnect.
The wearable apk also provides a complication and tile to view the latest reading on your WearOS device.

## License

Copyright (C) 2026 Sam Steele. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
