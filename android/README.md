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

The release APK is minified and signed with the local debug key so it installs as built. Builds
from different machines (including CI) carry different debug keys, so uninstall before installing
one over another. Swap in a real `signingConfig` before publishing to a store.

## Release

`.github/workflows/android.yml` builds the APK on every push to `main` that touches `android/`.
Bump `versionName` and `versionCode` in `app/build.gradle.kts`, then push a tag
`android-v<versionName>` to attach the APK to a GitHub release.
