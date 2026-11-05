#!/usr/bin/env bash
# Builds the Rust-core spike (crates/android-core) for 64-bit ARM Android and drops it into the
# debug APK's native libraries. Release builds never carry it. Needs the Android NDK
# (`brew install --cask android-ndk`, or ANDROID_NDK_HOME) and the Rust target
# (`rustup target add aarch64-linux-android`).
set -euo pipefail

cd "$(dirname "$0")/.."

ndk="${ANDROID_NDK_HOME:-/opt/homebrew/share/android-ndk}"
api=29 # the app's minSdk
bin="$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin"
[[ -d "$bin" ]] || { echo "no Android NDK at $ndk; set ANDROID_NDK_HOME" >&2; exit 1; }

export ANDROID_NDK_HOME="$ndk"
export CC_aarch64_linux_android="$bin/aarch64-linux-android$api-clang"
export CXX_aarch64_linux_android="$bin/aarch64-linux-android$api-clang++"
# libopus builds with CMake, through the NDK's own toolchain file.
export CMAKE_TOOLCHAIN_FILE_aarch64_linux_android="$ndk/build/cmake/android.toolchain.cmake"
export ANDROID_ABI=arm64-v8a
export ANDROID_PLATFORM="android-$api"
export AR_aarch64_linux_android="$bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CC_aarch64_linux_android"

cargo build --locked --release -p android-core --target aarch64-linux-android

out=android/app/src/debug/jniLibs/arm64-v8a
mkdir -p "$out"
cp target/aarch64-linux-android/release/libencore_core.so "$out/"
echo "built $out/libencore_core.so"
