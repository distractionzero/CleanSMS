<p align="center">
  <img src="docs/icon.svg" alt="Bouncer icon" width="96" height="96">
</p>

<h1 align="center">Bouncer</h1>

<p align="center">A clean, fast SMS and MMS app for Android. Free, open source and fully offline.</p>

<p align="center">
  <a href="#layout">Layout</a> ·
  <a href="#build">Build</a> ·
  <a href="#run-on-an-emulator">Run on an emulator</a> ·
  <a href="#release-build">Release build</a>
</p>

A messaging app for Android that looks and feels like Google Messages. Bouncer replaces your SMS app: it holds the
default SMS role (`ROLE_SMS`), so it receives, sends and stores every SMS and MMS on the device.

On first launch Bouncer imports the system SMS/MMS store into its own Room database. After that, a content
observer brings in new messages as they arrive. All UI reads from that local copy through Paging 3, so lists with
50,000+ messages scroll smoothly.

- SMS and MMS (photos, group chats). **No RCS:** Android has no public RCS API for third-party apps.
- Full-text search across all messages (SQLite FTS4).
- Archive, delete and mark-as-read from a long press.
- Notifications with quick reply and mark-as-read.
- System, light and dark themes, with Material You colours on Android 12+.
- No `INTERNET` permission. MMS goes through the system MMS service; nothing else leaves the device.

## Layout

```
app/app/src/main/java/dev/codingcoffee/bouncer/
  BouncerApp.kt                 # Application + AppContainer (manual DI)
  MainActivity.kt               # entry point, SENDTO / notification intents
  HeadlessSmsSendService.kt     # "respond via message" from the call screen
  data/db/                      # Room entities, DAOs, FTS table
  data/sync/TelephonySync.kt    # system provider -> Room import (full + incremental)
  data/Messaging.kt             # send / receive / read / archive / delete
  data/mms/MmsService.kt        # MMS send, download, persist
  data/mms/pdu/                 # minimal MMS PDU encoder/decoder
  data/contacts/                # cached contact lookup and search
  data/prefs/SettingsStore.kt   # theme + sync state (SharedPreferences)
  data/DebugSeeder.kt           # debug-only: 50k fake messages
  receivers/                    # SMS_DELIVER, WAP_PUSH, send results, notification actions
  notify/Notifier.kt            # MessagingStyle notifications
  ui/                           # Compose screens
```

## Build

```sh
nix develop
cd app
./gradlew assembleDebug        # build
./gradlew lintDebug            # optional
```

## Run on an emulator

```sh
avdmanager create avd -n pixel37 -d pixel_8 -k "system-images;android-37.0;google_apis;x86_64"
emulator -avd pixel37

adb devices # ensure device is running
cd app && ./gradlew installDebug
adb shell am start -n dev.codingcoffee.bouncer/.MainActivity

# in case its not killed
adb emu kill

emulator -list-avds
avdmanager delete avd -n <name>
```

To type with your computer's keyboard, set `hw.keyboard = yes` in `~/.android/avd/<name>.avd/config.ini` and
restart the emulator.

Open the app and set Bouncer as the default SMS app. Then simulate incoming messages:

```sh
adb emu sms send 5551234 "hello"                 # new conversation
adb emu sms send 5551234 "are you free tonight?" # same thread, grouped bubble
adb emu sms send 5559876 "$(printf 'long %.0s' {1..60})" # multipart SMS
```

The emulator can't receive MMS. Test MMS on a real device with a SIM.

To attach a photo, push it into the emulator and rescan media so the photo picker sees it:

```sh
adb push ~/Pictures/test.jpg /sdcard/Pictures/
adb shell content call --method scan_volume --uri content://media --arg external_primary
```

To check scrolling performance, open Settings → Debug → **Seed 50,000 test messages**. This writes only to
Bouncer's local database, not to the system SMS store; **Remove test messages** clears it. Judge smoothness on a
release build: debug Compose builds are much slower.

To pull the local database from a debug build:

```sh
adb exec-out run-as dev.codingcoffee.bouncer cat databases/bouncer.db > bouncer.db
adb exec-out run-as dev.codingcoffee.bouncer cat databases/bouncer.db-wal > bouncer.db-wal
```

## Release build

### Signing key (once)

```sh
keytool -genkeypair -v -keystore ~/.android/bouncer-release.jks \
  -alias bouncer -keyalg RSA -keysize 4096 -validity 10000
```

Back up the keystore. Updates must be signed with the same key; losing it means uninstalling, which wipes
Bouncer's local database (your messages stay in the system SMS store and are re-imported).

Add the credentials to `~/.gradle/gradle.properties` (user-level, never the repo), then
`chmod 600 ~/.gradle/gradle.properties`:

```properties
BOUNCER_STORE_FILE=/home/<you>/.android/bouncer-release.jks
BOUNCER_STORE_PASSWORD=<password>
BOUNCER_KEY_ALIAS=bouncer
BOUNCER_KEY_PASSWORD=<password>
```

Use an absolute path; Gradle doesn't expand `~`. Without these properties the release APK is built unsigned.

### Build and verify

```sh
nix develop
cd app
./gradlew assembleRelease
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The output is `app/build/outputs/apk/release/app-release.apk`. If it's named `app-release-unsigned.apk`, the
signing properties weren't picked up.

### Publish a GitHub release

From a clean, up-to-date `main` inside `nix develop`, with a `GITHUB_TOKEN` that can write releases
(fine-grained, Contents: read and write, this repo only):

```sh
just release patch   # or minor / major
```

This bumps `versionName` and `versionCode`, builds the signed APK, commits and tags `vX.Y.Z`, pushes both,
and creates a GitHub release with generated notes and `bouncer-vX.Y.Z.apk` attached.

### Install and run on the emulator

Debug and release builds are signed with different keys, so uninstall the debug build first. This wipes the
app's data.

```sh
adb uninstall dev.codingcoffee.bouncer
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n dev.codingcoffee.bouncer/.MainActivity
```

Later release builds install over the previous one with `adb install -r`, keeping data.

### Install on a phone

With USB debugging on: `adb install -r app/build/outputs/apk/release/app-release.apk`. Or copy the APK to the
phone and open it, allowing "Install unknown apps" when asked. If setting Bouncer as the default SMS app is
blocked as a restricted setting, go to Settings → Apps → Bouncer → ⋮ → Allow restricted settings.

Switching back to Google Messages later is safe: messages live in the system SMS store, not only in Bouncer.

### Debugging a release crash

```sh
adb logcat -c
adb shell am start -n dev.codingcoffee.bouncer/.MainActivity
adb logcat -d -b crash
```

R8 obfuscates class names. Map them back with `app/build/outputs/mapping/release/mapping.txt` from the same
build. `run-as` doesn't work on release builds.
