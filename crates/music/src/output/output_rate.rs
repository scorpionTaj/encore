//! The config an output device is opened in, and whether it still runs at the rate a stream was
//! opened at. Encore opens the device at the rate it really runs at and resamples every track to it
//! with ardftsrc, so nothing between Encore and the hardware converts it again.
//!
//! The default config's rate is the hardware's nominal rate. cpal reports a change of it as an
//! error on a stream opened on a named device, but not on one that follows the system default,
//! so the rate is watched there.
//!
//! Every output opens in stereo at most, whatever channels the device has. rodio shifts every
//! channel over by one when a player starts on an output wider than stereo, so surround setups
//! heard the left channel from the centre speaker. The system maps stereo onto the speakers
//! itself: CoreAudio converts the stream.

use cpal::SupportedStreamConfig;
use cpal::traits::DeviceTrait as _;

/// The config to open `device` in, given `default`, its default one, in stereo at most. That is
/// the device's real rate, so `wanted` is not asked for.
pub(crate) fn config(
    device: &cpal::Device,
    default: SupportedStreamConfig,
    _wanted: Option<u32>,
) -> SupportedStreamConfig {
    stereo(device, default)
}

/// The rates `device` can be opened at in `config`, lowest first. That is only the rate it runs
/// at, since the system sets it.
pub(crate) fn rates(_device: &cpal::Device, config: &SupportedStreamConfig) -> Vec<u32> {
    vec![config.sample_rate()]
}

/// Whether `device` now runs at another rate than `rate`, the one its stream runs at. CoreAudio
/// converts a stream that follows the system default rather than fail it.
pub(crate) fn moved(device: &cpal::Device, rate: u32, _wanted: Option<u32>) -> bool {
    device
        .default_output_config()
        .is_ok_and(|default| default.sample_rate() != rate)
}

/// `config` in stereo when it has more channels. CoreAudio takes a stereo stream on any
/// device and map it onto the speakers, so the device is not asked.
fn stereo(_device: &cpal::Device, config: SupportedStreamConfig) -> SupportedStreamConfig {
    match config.channels() > 2 {
        true => SupportedStreamConfig::new(
            2,
            config.sample_rate(),
            *config.buffer_size(),
            config.sample_format(),
        ),
        false => config,
    }
}
