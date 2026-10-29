# Encore for Android

A native Android build of Encore: Kotlin, Jetpack Compose and Media3. It plays the music on the
phone (from MediaStore) and Moroccan internet radio from Radio Browser, and adds a 5×1 home-screen
widget. It shares Encore's name, icon and colours with the desktop app but no code.

It builds for 64-bit ARM only (`arm64-v8a`) and needs Android 10 or newer.

## Build

Needs JDK 17 or newer and the Android SDK (platform 36). Point Gradle at the SDK with
`ANDROID_HOME` or a `local.properties` file holding `sdk.dir=…`.

```sh
./gradlew assembleDebug     # app/build/outputs/apk/debug/encore-<version>-arm64-v8a-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/encore-<version>-arm64-v8a-release.apk
```

The version comes from the workspace `Cargo.toml`, so Android and macOS always share a version
number and a section of `CHANGELOG.md`.

The release APK is minified. With `ENCORE_KEYSTORE`, `ENCORE_KEYSTORE_PASSWORD`,
`ENCORE_KEY_ALIAS` and `ENCORE_KEY_PASSWORD` set it is signed with the release key; without them
it falls back to the local debug key, which installs but cannot update a release-signed copy.

## Release

Android ships in the same release as the Mac app. Pushing a `v<version>` tag runs
`.github/workflows/release.yml`, which builds the disk image, then builds this APK, renames it
`Encore-<version>-android-arm64.apk` and attaches it with its line in `SHA256SUMS`. The app checks
`releases/latest` and installs that APK when it is newer.

The release job needs the signing key as repository secrets, made once:

```sh
keytool -genkeypair -v -keystore encore-release.jks -alias encore -keyalg RSA -keysize 4096 -validity 36500
base64 -i encore-release.jks | pbcopy   # paste as ANDROID_KEYSTORE_BASE64
```

Then add `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`encore`) and `ANDROID_KEY_PASSWORD`.
Keep the keystore backed up outside the repository: a release signed with any other key cannot
update installed copies.

`.github/workflows/android.yml` builds the APK on pushes and pull requests that touch `android/`.
