package dev.scorpiontaj.encore.core

import android.content.Context

/**
 * The desktop's Rust playback engine, loaded from `libencore_core.so` in debug builds for the
 * Rust-core spike. `scripts/build-android-core.sh` builds the library; see
 * `android/RUST_CORE_SPIKE.md`.
 */
object RustCore {
    /** Whether the library was built into this APK. */
    val available: Boolean = runCatching { System.loadLibrary("encore_core") }.isSuccess

    /** Hands the engine's audio backend the app's context. Call once before [play]. */
    external fun init(context: Context)

    /** Plays the file at [path] from the start; answers why it could not, or null. */
    external fun play(path: String): String?

    /** Stops the engine and lets it go. */
    external fun stop()
}
