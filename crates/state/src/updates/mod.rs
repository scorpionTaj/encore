mod install;

use std::io::Write as _;
use std::path::PathBuf;

use anyhow::{Context as _, Result};
use gpui::{Context, Entity, Task};
use serde::Deserialize;
use sha2::{Digest as _, Sha256};

use crate::{AppSettings, Io, VERSION, join};
use install::Install;

/// The newest published release of Encore, which every copy updates from.
const LATEST: &str = "https://api.github.com/repos/scorpionTaj/encore/releases/latest";
const SUMS: &str = "SHA256SUMS";
const AGENT: &str = concat!(
    "encore/",
    env!("CARGO_PKG_VERSION"),
    " (https://github.com/scorpionTaj/encore)"
);

/// Whether this copy of Encore installs its own updates. One running from the disk image, or
/// from a folder it cannot write to, only points at the release page.
pub fn updates_itself() -> bool {
    Install::current().updates_itself()
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Release {
    pub version: String,
    pub page: String,
    /// The disk image this install updates from, when the release carries one.
    package: Option<Asset>,
    sums: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum UpdateState {
    Quiet,
    Offered(Release),
    Fetching,
    Failed,
}

/// Looks for a newer release of Encore on GitHub once at startup and, when asked, installs it.
pub struct Updates {
    state: UpdateState,
    http: reqwest::Client,
    io: Io,
    task: Option<Task<()>>,
}

impl Updates {
    pub fn new(settings: Entity<AppSettings>, io: Io, cx: &mut Context<Self>) -> Self {
        let mut updates = Self {
            state: UpdateState::Quiet,
            http: reqwest::Client::new(),
            io,
            task: None,
        };
        if settings.read(cx).check_updates() {
            updates.look(cx);
        }
        updates
    }

    pub fn state(&self) -> &UpdateState {
        &self.state
    }

    pub fn offered(&self) -> Option<&Release> {
        match &self.state {
            UpdateState::Offered(release) => Some(release),
            _ => None,
        }
    }

    pub fn installable(&self) -> bool {
        updates_itself()
            && self
                .offered()
                .is_some_and(|release| release.package.is_some() && release.sums.is_some())
    }

    pub fn dismiss(&mut self, cx: &mut Context<Self>) {
        self.task = None;
        self.state = UpdateState::Quiet;
        cx.notify();
    }

    /// Downloads the offered build, checks it against the release's checksums and puts it in
    /// place, then quits so the new build can start. A failure leaves the running build as it
    /// was and shows the failure instead of the offer.
    pub fn install(&mut self, cx: &mut Context<Self>) {
        let Some(release) = self.offered().cloned() else {
            return;
        };
        let (Some(package), Some(sums)) = (release.package, release.sums) else {
            return;
        };
        self.state = UpdateState::Fetching;
        cx.notify();

        let http = self.http.clone();
        let io = self.io.clone();
        let version = release.version;
        self.task = Some(cx.spawn(async move |this, cx| {
            let fetched =
                join(io.spawn(async move { download(&http, &package, &sums, &version).await }))
                    .await;
            let applied = match fetched {
                Ok(path) => join(io.spawn_blocking(move || Install::current().apply(&path))).await,
                Err(error) => Err(error),
            };
            match applied {
                Ok(()) => cx.update(|cx| cx.quit()),
                Err(error) => {
                    log::warn!("updates: cannot install the new version: {error:#}");
                    this.update(cx, |this, cx| {
                        this.task = None;
                        this.state = UpdateState::Failed;
                        cx.notify();
                    })
                    .ok();
                }
            }
        }));
    }

    /// Asks GitHub for the newest release, and offers it when it is newer than this build.
    fn look(&mut self, cx: &mut Context<Self>) {
        let http = self.http.clone();
        let io = self.io.clone();
        self.task = Some(cx.spawn(async move |this, cx| {
            let found = join(io.spawn(async move { latest(&http).await })).await;
            this.update(cx, |this, cx| {
                this.task = None;
                match found {
                    Ok(Some(release)) => {
                        this.state = UpdateState::Offered(release);
                        cx.notify();
                    }
                    Ok(None) => {}
                    Err(error) => log::warn!("updates: cannot ask github: {error:#}"),
                }
            })
            .ok();
        }));
    }
}

#[derive(Deserialize)]
struct Published {
    tag_name: String,
    html_url: String,
    #[serde(default)]
    assets: Vec<Asset>,
}

#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
struct Asset {
    name: String,
    browser_download_url: String,
}

/// The newest release, or `None` when the running build is as new.
async fn latest(http: &reqwest::Client) -> Result<Option<Release>> {
    let published: Published = http
        .get(LATEST)
        .header("User-Agent", AGENT)
        .header("Accept", "application/vnd.github+json")
        .send()
        .await
        .context("cannot reach github")?
        .error_for_status()
        .context("github refused the release request")?
        .json()
        .await
        .context("cannot read the github release")?;

    let version = published.tag_name.trim_start_matches('v').to_owned();
    if !newer(&version, VERSION) {
        return Ok(None);
    }
    let package = published
        .assets
        .iter()
        .find(|asset| install::wants(&asset.name))
        .cloned();
    let sums = published
        .assets
        .iter()
        .find(|asset| asset.name == SUMS)
        .map(|asset| asset.browser_download_url.clone());

    Ok(Some(Release {
        version,
        page: published.html_url,
        package,
        sums,
    }))
}

/// Streams the asset into the temporary folder while hashing it, and keeps it only when the
/// hash matches the one the release lists.
async fn download(
    http: &reqwest::Client,
    package: &Asset,
    sums: &str,
    version: &str,
) -> Result<PathBuf> {
    let listed = http
        .get(sums)
        .header("User-Agent", AGENT)
        .send()
        .await
        .context("cannot reach the checksums")?
        .error_for_status()
        .context("the checksums are missing")?
        .text()
        .await
        .context("cannot read the checksums")?;
    let expected = listed
        .lines()
        .filter_map(|line| line.split_once(char::is_whitespace))
        .find(|(_, name)| name.trim() == package.name)
        .map(|(sum, _)| sum.trim().to_ascii_lowercase())
        .context("the release lists no checksum for the update")?;

    let mut response = http
        .get(&package.browser_download_url)
        .header("User-Agent", AGENT)
        .send()
        .await
        .context("cannot reach the update")?
        .error_for_status()
        .context("the update is missing")?;

    let path = std::env::temp_dir().join(format!("encore-{version}-{}", package.name));
    let mut file = std::fs::File::create(&path).context("cannot keep the update")?;
    let mut hasher = Sha256::new();
    while let Some(chunk) = response.chunk().await.context("cannot read the update")? {
        hasher.update(&chunk);
        file.write_all(&chunk).context("cannot keep the update")?;
    }
    drop(file);

    if format!("{:x}", hasher.finalize()) != expected {
        std::fs::remove_file(&path).ok();
        anyhow::bail!("the update does not match its checksum");
    }
    Ok(path)
}

/// Whether release `offered` is newer than `running`, comparing the release numbers.
fn newer(offered: &str, running: &str) -> bool {
    match (numbers(offered), numbers(running)) {
        (Some(offered), Some(running)) => offered > running,
        _ => false,
    }
}

/// The three release numbers of a version such as `1.2.3`, ignoring anything after a `-` or `+`.
fn numbers(version: &str) -> Option<(u64, u64, u64)> {
    let core = version.trim().split(['-', '+']).next()?;
    let mut parts = core.split('.').map(|part| part.parse::<u64>().ok());
    Some((parts.next()??, parts.next()??, parts.next()??))
}

#[cfg(test)]
mod tests {
    use super::newer;

    #[test]
    fn only_a_higher_release_is_newer() {
        assert!(newer("0.45.0", "0.44.2"));
        assert!(newer("1.0.0", "0.99.9"));
        assert!(!newer("0.44.2", "0.44.2"));
        assert!(!newer("0.44.1", "0.44.2"));
        assert!(!newer("garbage", "0.44.2"));
    }
}
