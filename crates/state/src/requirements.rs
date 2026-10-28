//! What the system around Encore is missing for it to work fully: an output device that opens.
//! It is found here and drawn as a card by `views`, which can put it away for the run or for
//! good.

use gpui::{App, Context, Entity};

use crate::{AppSettings, Outputs};

/// One thing missing from the system.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Requirement {
    /// The output device would not open.
    AudioOutput,
}

impl Requirement {
    /// The name `settings.json` keeps a silenced requirement under.
    pub fn slug(self) -> &'static str {
        match self {
            Self::AudioOutput => "audio-output",
        }
    }
}

/// The requirements this machine is missing. The output is followed through `Outputs`, so a
/// device that fails later or comes back is noticed.
pub struct Requirements {
    output: bool,
    /// Put away until the next run.
    dismissed: Vec<&'static str>,
    settings: Entity<AppSettings>,
}

impl Requirements {
    pub fn new(
        settings: Entity<AppSettings>,
        outputs: Entity<Outputs>,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&outputs, |this, outputs, cx| this.listen(outputs, cx))
            .detach();
        Self {
            output: outputs.read(cx).failing(),
            dismissed: Vec::new(),
            settings,
        }
    }

    /// The requirements to show.
    pub fn shown(&self, cx: &App) -> Vec<Requirement> {
        let settings = self.settings.read(cx);
        self.output
            .then_some(Requirement::AudioOutput)
            .into_iter()
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
