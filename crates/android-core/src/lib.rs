//! The Rust-core spike: plays a file through `music::engine` from the Android app, so the app
//! can find out what sharing the desktop's engine would take. Only debug builds load it, through
//! `dev.scorpiontaj.encore.core.RustCore`; see `android/RUST_CORE_SPIKE.md`.
#![cfg(target_os = "android")]

use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use anyhow::Result;
use jni::EnvUnowned;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JClass, JObject, JString};
use music::equalizer::{Curve, EqualizerWriter};
use music::{Analyzer, Meter, PlaybackConfig, PlaybackEvents, PlaybackFactory as _, Player};

/// How often the engine reports the position; nothing reads it in the spike.
const POSITION_INTERVAL: Duration = Duration::from_millis(500);

/// The engine playing, kept with what it reports and the curve it reads, until `stop`.
struct Playing {
    player: Box<dyn Player>,
    _events: Box<dyn PlaybackEvents>,
    _equalizer: EqualizerWriter,
}

static PLAYING: Mutex<Option<Playing>> = Mutex::new(None);
static CONTEXT: OnceLock<()> = OnceLock::new();

/// Hands the audio backend the app's context, which it needs to list the outputs. Call once
/// before `play`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_scorpiontaj_encore_core_RustCore_init<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    context: JObject<'local>,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        if CONTEXT.get().is_some() {
            return Ok(());
        }
        let vm = env.get_java_vm()?;
        // Kept for the life of the process, as the backend expects.
        let context = env.new_global_ref(context)?.into_raw();
        // SAFETY: both pointers come from the running VM and outlive every use of them.
        unsafe { ndk_context::initialize_android_context(vm.get_raw().cast(), context.cast()) };
        CONTEXT.set(()).ok();
        Ok(())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

/// Plays the file at `path` from the start, replacing whatever played. Answers why it could not,
/// or null once the engine took the file.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_scorpiontaj_encore_core_RustCore_play<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> JString<'local> {
    env.with_env(|env| -> jni::errors::Result<JString<'local>> {
        let path = path.try_to_string(env)?;
        match play(&path) {
            Ok(()) => Ok(JString::default()),
            Err(error) => env.new_string(format!("{error:#}")),
        }
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

/// Stops the engine and lets it go.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_scorpiontaj_encore_core_RustCore_stop<'local>(
    _env: EnvUnowned<'local>,
    _class: JClass<'local>,
) {
    if let Some(playing) = PLAYING.lock().unwrap_or_else(|e| e.into_inner()).take() {
        playing.player.pause();
    }
}

fn play(path: &str) -> Result<()> {
    let mut playing = PLAYING.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(old) = playing.take() {
        old.player.pause();
    }
    let equalizer = EqualizerWriter::new(false, Curve::bands(&[]));
    let config = PlaybackConfig {
        normalisation: false,
        gapless: false,
        position_interval: POSITION_INTERVAL,
        gain: 1.0,
        crossfade: Duration::ZERO,
        equalizer: equalizer.reader(),
        meter: Meter::default(),
        analyzer: Analyzer::default(),
    };
    let (player, events) = music::local::FilePlayback.start(config);
    player.load(path, Duration::ZERO, false)?;
    *playing = Some(Playing {
        player,
        _events: events,
        _equalizer: equalizer,
    });
    Ok(())
}
