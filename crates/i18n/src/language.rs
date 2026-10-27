use unic_langid::{LanguageIdentifier, langid};

pub const AUTO: &str = "auto";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(usize)]
pub enum Language {
    English,
    French,
    Arabic,
}

impl Language {
    pub const ALL: [Self; 3] = [Self::English, Self::French, Self::Arabic];

    pub fn id(self) -> &'static str {
        match self {
            Self::English => "en-US",
            Self::French => "fr",
            Self::Arabic => "ar",
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::English => "English",
            Self::French => "Français",
            Self::Arabic => "العربية",
        }
    }

    pub fn from_id(id: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|language| language.id() == id)
    }

    pub fn detect() -> Self {
        let Some(locale) = sys_locale::get_locale() else {
            return Self::English;
        };
        let primary = base(&locale);

        Self::ALL
            .into_iter()
            .find(|language| base(language.id()) == primary)
            .unwrap_or(Self::English)
    }

    pub(crate) fn tag(self) -> LanguageIdentifier {
        match self {
            Self::English => langid!("en-US"),
            Self::French => langid!("fr"),
            Self::Arabic => langid!("ar"),
        }
    }

    pub(crate) fn source(self) -> &'static str {
        match self {
            Self::English => include_str!("../../../assets/i18n/en-US/main.ftl"),
            Self::French => include_str!("../../../assets/i18n/fr/main.ftl"),
            Self::Arabic => include_str!("../../../assets/i18n/ar/main.ftl"),
        }
    }
}

pub fn resolve(id: &str) -> Language {
    Language::from_id(id).unwrap_or_else(Language::detect)
}

fn base(tag: &str) -> String {
    tag.split(['-', '_'])
        .next()
        .unwrap_or_default()
        .to_lowercase()
}
