use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::OnceLock;

use anyhow::{Context as _, Result};

/// The bundle the release's disk image carries.
const BUNDLE: &str = "Encore.app";

/// Whether a release asset is the disk image this build updates from.
pub(super) fn wants(asset: &str) -> bool {
    asset.ends_with("-macos-arm64.dmg")
}

/// How this copy of Encore was installed, which decides whether it may replace itself.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(super) enum Install {
    /// An app bundle in a folder Encore can write to.
    Bundle(PathBuf),
    /// A copy still on the disk image, translocated by macOS, or in a folder it cannot write to,
    /// which Encore leaves alone.
    Fixed,
}

impl Install {
    /// Works out how Encore was installed, once per run.
    pub(super) fn current() -> &'static Self {
        static CURRENT: OnceLock<Install> = OnceLock::new();
        CURRENT.get_or_init(detect)
    }

    pub(super) fn updates_itself(&self) -> bool {
        *self != Self::Fixed
    }

    /// Puts the downloaded disk image's bundle in place of the running one and arranges for the
    /// new one to start once this process has quit.
    pub(super) fn apply(&self, image: &Path) -> Result<()> {
        match self {
            Self::Bundle(bundle) => swap_bundle(image, bundle),
            Self::Fixed => anyhow::bail!("this copy of Encore does not update itself"),
        }
    }
}

/// A bundle counts when Encore runs from its own `.app` in a folder it can write to. One still
/// on the disk image and one macOS translocated are left alone.
fn detect() -> Install {
    let bundle = std::env::current_exe()
        .and_then(|exe| exe.canonicalize())
        .ok()
        .and_then(|exe| Some(exe.parent()?.parent()?.parent()?.to_path_buf()))
        .filter(|bundle| {
            bundle
                .extension()
                .is_some_and(|extension| extension == "app")
        });
    let Some(bundle) = bundle else {
        return Install::Fixed;
    };
    let foreign =
        bundle.starts_with("/Volumes") || bundle.to_string_lossy().contains("/AppTranslocation/");
    match !foreign && bundle.parent().is_some_and(writable) {
        true => Install::Bundle(bundle),
        false => Install::Fixed,
    }
}

fn writable(folder: &Path) -> bool {
    let probe = folder.join(format!(".encore-write-{}", std::process::id()));
    match std::fs::File::create(&probe) {
        Ok(_) => {
            std::fs::remove_file(&probe).ok();
            true
        }
        Err(_) => false,
    }
}

/// Copies Encore.app off the disk image beside the running bundle, deletes the image, then
/// trades the two bundles by renaming, so a failure part way leaves the old one where it was.
fn swap_bundle(image: &Path, bundle: &Path) -> Result<()> {
    let mount = tempfile::tempdir().context("cannot make a mount point")?;
    run(Command::new("/usr/bin/hdiutil")
        .args([
            "attach",
            "-nobrowse",
            "-readonly",
            "-noautoopen",
            "-mountpoint",
        ])
        .arg(mount.path())
        .arg(image))
    .context("cannot open the disk image")?;

    let staged = bundle.with_file_name(".Encore.app.update");
    let copied = remove(&staged).and_then(|()| {
        run(Command::new("/usr/bin/ditto")
            .arg(mount.path().join(BUNDLE))
            .arg(&staged))
        .context("cannot copy the new version off the disk image")
    });
    if let Err(error) = run(Command::new("/usr/bin/hdiutil")
        .args(["detach", "-quiet"])
        .arg(mount.path()))
    {
        log::warn!("updates: cannot eject the disk image: {error:#}");
    }
    copied?;
    std::fs::remove_file(image).ok();

    let retired = bundle.with_file_name(".Encore.app.old");
    remove(&retired)?;
    std::fs::rename(bundle, &retired).context("cannot move the old version aside")?;
    if let Err(error) = std::fs::rename(&staged, bundle) {
        std::fs::rename(&retired, bundle).ok();
        return Err(error).context("cannot put the new version in place");
    }
    if let Err(error) = remove(&retired) {
        log::warn!("updates: cannot delete the old version: {error:#}");
    }
    relaunch(Command::new("/usr/bin/open").arg(bundle))
}

/// Starts `command` from a shell that first waits for this process to exit, so the new copy
/// never runs beside the old one.
fn relaunch(command: &mut Command) -> Result<()> {
    let mut waiter = Command::new("/bin/sh");
    waiter
        .arg("-c")
        .arg(r#"pid=$1; shift; while kill -0 "$pid" 2>/dev/null; do sleep 0.2; done; exec "$@""#)
        .arg("sh")
        .arg(std::process::id().to_string())
        .arg(command.get_program())
        .args(command.get_args());
    waiter.spawn().context("cannot schedule the restart")?;
    Ok(())
}

fn run(command: &mut Command) -> Result<()> {
    let output = command.output().context("cannot start the command")?;
    if !output.status.success() {
        anyhow::bail!(
            "{} exited with {}: {}",
            command.get_program().to_string_lossy(),
            output.status,
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    Ok(())
}

/// Deletes `path`, file or folder, and is content when it is not there.
fn remove(path: &Path) -> Result<()> {
    match std::fs::remove_dir_all(path).or_else(|_| std::fs::remove_file(path)) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error).with_context(|| format!("cannot delete {}", path.display())),
    }
}
