//! What the system around Encore is missing for it to work fully: the ALSA plugin into the sound
//! server and an output device that opens. Each is found
//! here and drawn as a card by `views`, which can put it away for the run or for good.

use gpui::{App, Context, Entity, Task};
use music::SoundServer;

use crate::{AppSettings, Io, Outputs};

/// One thing missing from the system, named by what a user installs to fix it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Requirement {
    /// The sound server runs but ALSA has no plugin into it, so nothing played is heard.
    AudioBridge(SoundServer),
    /// The output device would not open for some other reason.
    AudioOutput,
}

impl Requirement {
    /// The name `settings.json` keeps a silenced requirement under.
    pub fn slug(self) -> &'static str {
        match self {
            Self::AudioBridge(_) => "audio-bridge",
            Self::AudioOutput => "audio-output",
        }
    }
}

/// The requirements this machine is missing. The system is looked at once at startup, off the
/// main thread; the output is followed through `Outputs`, so a device that fails later or comes
/// back is noticed.
pub struct Requirements {
    bridge: Option<SoundServer>,
    output: bool,
    /// Put away until the next run.
    dismissed: Vec<&'static str>,
    settings: Entity<AppSettings>,
    task: Option<Task<()>>,
}

impl Requirements {
    pub fn new(
        settings: Entity<AppSettings>,
        outputs: Entity<Outputs>,
        io: Io,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&outputs, |this, outputs, cx| this.listen(outputs, cx))
            .detach();

        let task = cx.spawn(async move |this, cx| {
            let looked = io.spawn_blocking(music::missing_bridge).await;
            let Ok(bridge) = looked else {
                return;
            };
            if let Some(server) = bridge {
                log::warn!("requirements: {server:?} runs without its ALSA plugin");
            }
            this.update(cx, |this, cx| {
                this.task = None;
                this.bridge = bridge;
                cx.notify();
            })
            .ok();
        });

        Self {
            bridge: None,
            output: outputs.read(cx).failing(),
            dismissed: Vec::new(),
            settings,
            task: Some(task),
        }
    }

    /// The requirements to show, most pressing first. A failed output whose cause is already
    /// known to be the missing ALSA plugin is left to that card.
    pub fn shown(&self, cx: &App) -> Vec<Requirement> {
        let settings = self.settings.read(cx);
        let found = [
            self.bridge.map(Requirement::AudioBridge),
            (self.output && self.bridge.is_none()).then_some(Requirement::AudioOutput),
        ];
        found
            .into_iter()
            .flatten()
            .filter(|requirement| {
                let slug = requirement.slug();
                !self.dismissed.contains(&slug) && !settings.requirement_silenced(slug)
            })
            .collect()
    }

    /// Puts `requirement` away until the next run.
    pub fn dismiss(&mut self, requirement: Requirement, cx: &mut Context<Self>) {
        self.dismissed.push(requirement.slug());
        cx.notify();
    }

    /// Puts `requirement` away for good.
    pub fn silence(&mut self, requirement: Requirement, cx: &mut Context<Self>) {
        self.settings.update(cx, |settings, cx| {
            settings.silence_requirement(requirement.slug(), cx)
        });
        cx.notify();
    }

    /// Follows the output after `Outputs` changed. An output that came back takes its card away
    /// and lets it show again should it fail later in the run.
    fn listen(&mut self, outputs: Entity<Outputs>, cx: &mut Context<Self>) {
        let output = outputs.read(cx).failing();
        if output == self.output {
            return;
        }
        match output {
            true => log::warn!("requirements: the audio output cannot be opened"),
            false => {
                let slug = Requirement::AudioOutput.slug();
                self.dismissed.retain(|dismissed| *dismissed != slug);
            }
        }
        self.output = output;
        cx.notify();
    }
}
