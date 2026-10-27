use std::rc::Rc;

use gpui::{Context, Task};
use music::radio::{self, Station};

use crate::{Io, join};

/// Where the station list stands.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum RadioState {
    /// Nothing asked for yet, or the list is on its way.
    Loading,
    Ready,
    /// The list could not be read, and this is why.
    Failed(String),
}

/// The Moroccan radio stations, read from Radio Browser the first time the radio page opens.
pub struct Radio {
    state: RadioState,
    stations: Rc<Vec<Station>>,
    http: reqwest::Client,
    io: Io,
    task: Option<Task<()>>,
    asked: bool,
}

impl Radio {
    pub fn new(io: Io) -> Self {
        Self {
            state: RadioState::Loading,
            stations: Rc::new(Vec::new()),
            http: reqwest::Client::new(),
            io,
            task: None,
            asked: false,
        }
    }

    pub fn state(&self) -> &RadioState {
        &self.state
    }

    pub fn stations(&self) -> Rc<Vec<Station>> {
        self.stations.clone()
    }

    /// Reads the station list unless it was already asked for.
    pub fn ensure(&mut self, cx: &mut Context<Self>) {
        if !self.asked {
            self.reload(cx);
        }
    }

    /// Reads the station list again from Radio Browser.
    pub fn reload(&mut self, cx: &mut Context<Self>) {
        self.asked = true;
        self.state = RadioState::Loading;
        cx.notify();
        let http = self.http.clone();
        let io = self.io.clone();
        self.task = Some(cx.spawn(async move |this, cx| {
            let found = join(io.spawn(async move { radio::stations(&http).await })).await;
            this.update(cx, |this, cx| {
                this.task = None;
                match crate::settled(found, cx) {
                    Ok(stations) => {
                        this.stations = Rc::new(stations);
                        this.state = RadioState::Ready;
                    }
                    Err(reason) => {
                        log::warn!("radio: cannot read the stations: {reason}");
                        this.state = RadioState::Failed(reason);
                    }
                }
                cx.notify();
            })
            .ok();
        }));
    }
}
