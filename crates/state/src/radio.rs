use std::rc::Rc;

use gpui::{Context, Entity, Task};
use music::radio::{self, Station};

use crate::{AppSettings, Io, Outcome, Toasts, join};

/// Where the station list stands.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum RadioState {
    /// Nothing asked for yet, or the list is on its way.
    Loading,
    Ready,
    /// The list could not be read, and this is why.
    Failed(String),
}

/// The radio stations of the country picked in settings, read from Radio Browser the first time
/// the radio page opens. Favourites, kept in settings, come first.
pub struct Radio {
    settings: Entity<AppSettings>,
    state: RadioState,
    stations: Rc<Vec<Station>>,
    http: reqwest::Client,
    io: Io,
    task: Option<Task<()>>,
    asked: bool,
}

impl Radio {
    pub fn new(settings: Entity<AppSettings>, io: Io) -> Self {
        Self {
            settings,
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

    /// The stations, favourites first and otherwise in Radio Browser's order.
    pub fn stations(&self) -> Rc<Vec<Station>> {
        self.stations.clone()
    }

    /// The country the stations are from, by ISO 3166 code.
    pub fn country<'a>(&self, cx: &'a gpui::App) -> &'a str {
        self.settings.read(cx).radio_country()
    }

    /// Lists the stations of `country` instead.
    pub fn set_country(&mut self, country: &str, cx: &mut Context<Self>) {
        self.settings
            .update(cx, |settings, cx| settings.set_radio_country(country, cx));
        self.reload(cx);
    }

    pub fn favorite(&self, id: &str, cx: &gpui::App) -> bool {
        self.settings.read(cx).radio_favorite(id)
    }

    /// Marks a station as a favourite, or takes the mark off, and moves it to match.
    pub fn toggle_favorite(&mut self, id: &str, cx: &mut Context<Self>) {
        self.settings
            .update(cx, |settings, cx| settings.toggle_radio_favorite(id, cx));
        let stations = self.ordered(self.stations.to_vec(), cx);
        self.stations = Rc::new(stations);
        cx.notify();
    }

    /// The station being recorded, by stream address.
    pub fn recording(&self) -> Option<String> {
        radio::recording()
    }

    /// Records the station on `stream` into `Music/Encore Recordings`, in a file named `stem`.
    /// The music folders' watch adds it to the library once it is there.
    pub fn start_recording(&mut self, stream: &str, stem: &str, cx: &mut Context<Self>) {
        let folder = dirs::audio_dir()
            .or_else(|| dirs::home_dir().map(|home| home.join("Music")))
            .unwrap_or_default()
            .join("Encore Recordings");
        if let Err(error) = radio::start_recording(stream, stem, &folder) {
            log::warn!("radio: cannot start recording: {error:#}");
            Toasts::show(Outcome::Failed, "toast-recording-failed", cx);
        }
        cx.notify();
    }

    /// Stops the recording and says where it was saved.
    pub fn stop_recording(&mut self, cx: &mut Context<Self>) {
        if let Some(path) = radio::stop_recording() {
            let name = path
                .file_name()
                .map(|name| name.to_string_lossy().into_owned())
                .unwrap_or_default();
            Toasts::about(Outcome::Done, "toast-recording-saved", name, cx);
        }
        cx.notify();
    }

    /// `stations` with the favourites moved to the front, each group keeping its order.
    fn ordered(&self, stations: Vec<Station>, cx: &gpui::App) -> Vec<Station> {
        let settings = self.settings.read(cx);
        let (mut favorites, others): (Vec<_>, Vec<_>) = stations
            .into_iter()
            .partition(|station| settings.radio_favorite(&station.id));
        favorites.extend(others);
        favorites
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
        let country = self.country(cx).to_owned();
        self.task = Some(cx.spawn(async move |this, cx| {
            let found = join(io.spawn(async move { radio::stations(&http, &country).await })).await;
            this.update(cx, |this, cx| {
                this.task = None;
                match crate::settled(found, cx) {
                    Ok(stations) => {
                        this.stations = Rc::new(this.ordered(stations, cx));
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
