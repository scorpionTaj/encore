use std::path::PathBuf;

const FILE: &str = "encore.log";

/// The file the running Encore appends its log to, `encore.log` under the platform's
/// state folder, or the cache folder where the platform has none. `None` when neither is known.
pub fn log_file() -> Option<PathBuf> {
    let root = dirs::state_dir().or_else(dirs::cache_dir)?;

    Some(root.join("encore").join(FILE))
}
