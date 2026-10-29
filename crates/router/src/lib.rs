mod link;
mod navigation;

pub use link::Link;
pub use navigation::{Navigation, NavigationEvent};

use gpui::{App, AppContext as _, Entity, Global, SharedString};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LibraryTab {
    Songs,
    Albums,
    Playlists,
    Artists,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NavEntry {
    Home,
    Search,
    History,
    Local,
    Radio,
    Stats,
    Pins,
}

impl NavEntry {
    pub const ALL: [Self; 7] = [
        Self::Home,
        Self::Search,
        Self::Radio,
        Self::History,
        Self::Stats,
        Self::Local,
        Self::Pins,
    ];

    pub fn id(self) -> &'static str {
        match self {
            Self::Home => "home",
            Self::Search => "search",
            Self::History => "history",
            Self::Local => "local",
            Self::Radio => "radio",
            Self::Stats => "stats",
            Self::Pins => "sidebar-pins",
        }
    }

    pub fn key(self) -> &'static str {
        match self {
            Self::Home => "nav-home",
            Self::Search => "nav-search",
            Self::History => "nav-history",
            Self::Local => "nav-local",
            Self::Radio => "nav-radio",
            Self::Stats => "nav-stats",
            Self::Pins => "nav-pinned",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Screen {
    Home,
    Search,
    History,
    Songs,
    Albums,
    Playlists,
    Artists,
    Radio,
    Stats,
}

impl Screen {
    pub const ALL: [Self; 9] = [
        Self::Home,
        Self::Search,
        Self::Songs,
        Self::Albums,
        Self::Artists,
        Self::Playlists,
        Self::Radio,
        Self::History,
        Self::Stats,
    ];

    pub fn id(self) -> &'static str {
        match self {
            Self::Home => "home",
            Self::Search => "search",
            Self::History => "history",
            Self::Songs => "songs",
            Self::Albums => "albums",
            Self::Playlists => "playlists",
            Self::Artists => "artists",
            Self::Radio => "radio",
            Self::Stats => "stats",
        }
    }

    pub fn key(self) -> &'static str {
        match self {
            Self::Home => "nav-home",
            Self::Search => "nav-search",
            Self::History => "nav-history",
            Self::Songs => "nav-songs",
            Self::Albums => "nav-albums",
            Self::Playlists => "nav-playlists",
            Self::Artists => "nav-artists",
            Self::Radio => "nav-radio",
            Self::Stats => "nav-stats",
        }
    }

    pub fn from_id(id: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|screen| screen.id() == id)
    }

    pub fn destination(self) -> Destination {
        match self {
            Self::Home => Destination::Home,
            Self::Search => Destination::Search,
            Self::History => Destination::History,
            Self::Songs => Destination::Local(LibraryTab::Songs),
            Self::Albums => Destination::Local(LibraryTab::Albums),
            Self::Playlists => Destination::Local(LibraryTab::Playlists),
            Self::Artists => Destination::Local(LibraryTab::Artists),
            Self::Radio => Destination::Radio,
            Self::Stats => Destination::Stats,
        }
    }
}

/// One page of settings. All but the last are sections of the six tabs, each tab showing its
/// sections one after another on one scrolling page, and the last is a drill-in that opens from a
/// row on its parent page.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SettingsPage {
    MusicFolders,
    General,
    Appearance,
    PlayerBar,
    NowPlaying,
    Lyrics,
    Playback,
    Sound,
    Storage,
    Discord,
    About,
    LyricsSources,
}

impl SettingsPage {
    /// Every page, the sections first and the drill-ins after them.
    pub const ALL: [Self; 12] = [
        Self::MusicFolders,
        Self::General,
        Self::Appearance,
        Self::PlayerBar,
        Self::NowPlaying,
        Self::Lyrics,
        Self::Playback,
        Self::Sound,
        Self::Storage,
        Self::Discord,
        Self::About,
        Self::LyricsSources,
    ];

    pub fn id(self) -> &'static str {
        match self {
            Self::General => "general",
            Self::Appearance => "appearance",
            Self::PlayerBar => "player-bar",
            Self::NowPlaying => "now-playing",
            Self::Lyrics => "lyrics",
            Self::Playback => "playback",
            Self::Sound => "sound",
            Self::Storage => "storage",
            Self::Discord => "discord",
            Self::About => "about",
            Self::LyricsSources => "lyrics-sources",
            Self::MusicFolders => "music-folders",
        }
    }

    /// The Fluent key of the page's name.
    pub fn key(self) -> &'static str {
        match self {
            Self::General => "settings-page-general",
            Self::Appearance => "settings-page-appearance",
            Self::PlayerBar => "settings-page-player-bar",
            Self::NowPlaying => "settings-page-now-playing",
            Self::Lyrics => "settings-page-lyrics",
            Self::Playback => "settings-page-playback",
            Self::Sound => "settings-page-sound",
            Self::Storage => "settings-page-storage",
            Self::Discord => "settings-page-discord",
            Self::About => "settings-page-about",
            Self::LyricsSources => "settings-page-lyrics-sources",
            Self::MusicFolders => "settings-page-music-folders",
        }
    }

    /// The page a drill-in returns to, or `None` for a section of a tab.
    pub fn parent(self) -> Option<Self> {
        match self {
            Self::LyricsSources => Some(Self::Lyrics),
            _ => None,
        }
    }

    pub fn from_id(id: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|page| page.id() == id)
    }

    /// The tab the page shows in. A drill-in belongs to its parent's tab.
    pub fn tab(self) -> SettingsTab {
        match self {
            Self::General | Self::About => SettingsTab::General,
            Self::MusicFolders => SettingsTab::Library,
            Self::Appearance | Self::PlayerBar | Self::NowPlaying => SettingsTab::Appearance,
            Self::Lyrics | Self::LyricsSources => SettingsTab::Lyrics,
            Self::Playback | Self::Sound | Self::Storage => SettingsTab::Playback,
            Self::Discord => SettingsTab::Integrations,
        }
    }
}

/// One tab of the settings header. A tab is one scrolling page of its sections, each a former
/// page under its own title.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SettingsTab {
    General,
    Library,
    Appearance,
    Lyrics,
    Playback,
    Integrations,
}

impl SettingsTab {
    /// Every tab, in the order the header shows them.
    pub const ALL: [Self; 6] = [
        Self::General,
        Self::Library,
        Self::Appearance,
        Self::Lyrics,
        Self::Playback,
        Self::Integrations,
    ];

    pub fn id(self) -> &'static str {
        match self {
            Self::General => "general",
            Self::Library => "library",
            Self::Appearance => "appearance",
            Self::Lyrics => "lyrics",
            Self::Playback => "playback",
            Self::Integrations => "integrations",
        }
    }

    /// The Fluent key of the tab's name.
    pub fn key(self) -> &'static str {
        match self {
            Self::General => "settings-page-general",
            Self::Library => "settings-tab-library",
            Self::Appearance => "settings-page-appearance",
            Self::Lyrics => "settings-page-lyrics",
            Self::Playback => "settings-page-playback",
            Self::Integrations => "settings-tab-integrations",
        }
    }

    /// The icon before the tab's name in the header, and beside a search result found in it.
    pub fn icon(self) -> &'static str {
        match self {
            Self::General => "icons/settings.svg",
            Self::Library => "icons/library-big.svg",
            Self::Appearance => "icons/palette.svg",
            Self::Lyrics => "icons/mic-vocal.svg",
            Self::Playback => "icons/play.svg",
            Self::Integrations => "icons/link.svg",
        }
    }

    /// The pages the tab shows, top to bottom. The first is the one the tab opens on.
    pub fn sections(self) -> &'static [SettingsPage] {
        match self {
            Self::General => &[SettingsPage::General, SettingsPage::About],
            Self::Library => &[SettingsPage::MusicFolders],
            Self::Appearance => &[
                SettingsPage::Appearance,
                SettingsPage::PlayerBar,
                SettingsPage::NowPlaying,
            ],
            Self::Lyrics => &[SettingsPage::Lyrics],
            Self::Playback => &[
                SettingsPage::Playback,
                SettingsPage::Sound,
                SettingsPage::Storage,
            ],
            Self::Integrations => &[SettingsPage::Discord],
        }
    }

    /// The page the tab opens on, at its top.
    pub fn lead(self) -> SettingsPage {
        self.sections()[0]
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Destination {
    Home,
    History,
    Local(LibraryTab),
    Album(SharedString),
    Song(SharedString),
    Playlist(SharedString),
    Artist(SharedString),
    /// The radio stations of the picked country.
    Radio,
    /// What was listened to most.
    Stats,
    Search,
    Settings(SettingsPage),
    /// The equalizer's own settings page, opened from Sound.
    Equalizer,
    Fullscreen,
}

impl From<&ui::Pin> for Destination {
    fn from(pin: &ui::Pin) -> Self {
        let id = SharedString::from(pin.id.clone());
        match pin.kind {
            ui::PinKind::Album => Destination::Album(id),
            ui::PinKind::Artist => Destination::Artist(id),
            ui::PinKind::Playlist => Destination::Playlist(id),
            ui::PinKind::Song => Destination::Song(id),
        }
    }
}

impl Destination {
    pub fn same_section(&self, other: &Destination) -> bool {
        match (self, other) {
            (Destination::Local(_), Destination::Local(_)) => true,
            _ if self.in_settings() && other.in_settings() => true,
            _ => self == other,
        }
    }

    /// Whether this destination is a settings page, the equalizer included.
    pub fn in_settings(&self) -> bool {
        matches!(self, Destination::Settings(_) | Destination::Equalizer)
    }

    /// The settings page a drill-in's back button returns to. Only drill-ins have one.
    pub fn parent(&self) -> Option<Destination> {
        match self {
            Destination::Equalizer => Some(Destination::Settings(SettingsPage::Sound)),
            Destination::Settings(page) => page.parent().map(Destination::Settings),
            _ => None,
        }
    }

    /// The tabbed page this destination belongs to: the page itself, or a drill-in's parent.
    pub fn settings_page(&self) -> Option<SettingsPage> {
        match self {
            Destination::Equalizer => Some(SettingsPage::Sound),
            Destination::Settings(page) => Some(page.parent().unwrap_or(*page)),
            _ => None,
        }
    }

    /// Whether this page shows provider data that Refresh can load again. Settings, search and
    /// the fullscreen player have nothing to reload, so they get no refresh button.
    pub fn refreshes(&self) -> bool {
        !matches!(
            self,
            Destination::Search
                | Destination::Settings(_)
                | Destination::Equalizer
                | Destination::Fullscreen
        )
    }
}

#[derive(Clone)]
struct Router(Entity<Navigation>);

impl Global for Router {}

pub fn init(start: Destination, cx: &mut App) {
    let navigation = cx.new(|_| Navigation::new(start));
    cx.set_global(Router(navigation));
}

pub fn trail(cx: &App) -> Entity<Navigation> {
    cx.global::<Router>().0.clone()
}

pub fn navigate(destination: Destination, cx: &mut App) {
    trail(cx).update(cx, |navigation, cx| navigation.go(destination, cx));
}

/// Replaces the current destination so Back and Forward do not revisit a page that disappeared.
pub fn replace(destination: Destination, cx: &mut App) {
    trail(cx).update(cx, |navigation, cx| navigation.replace(destination, cx));
}

pub fn back(cx: &mut App) {
    trail(cx).update(cx, |navigation, cx| navigation.back(cx));
}

pub fn forward(cx: &mut App) {
    trail(cx).update(cx, |navigation, cx| navigation.forward(cx));
}

/// Steps up from a drill-in to `parent`, going back when it is the page before so the history
/// holds no loop, and replacing the drill-in otherwise.
pub fn up(parent: Destination, cx: &mut App) {
    trail(cx).update(cx, |navigation, cx| navigation.up(parent, cx));
}

/// Opens settings on the page they last showed, or on General the first time.
pub fn open_settings(cx: &mut App) {
    trail(cx).update(cx, |navigation, cx| navigation.open_settings(cx));
}
