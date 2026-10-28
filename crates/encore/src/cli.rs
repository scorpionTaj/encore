use clap::error::ErrorKind;
use clap::{Arg, ArgAction, Parser};

/// Encore's command line. Everything on it is optional, since a launcher usually starts Encore
/// with nothing or with the files and links to open.
#[derive(Parser)]
#[command(
    name = "encore",
    version = state::VERSION,
    about = "A native music streaming client",
    disable_version_flag = true,
    arg = Arg::new("version")
        .short('v')
        .long("version")
        .action(ArgAction::Version)
        .help("Print the version"),
)]
pub struct Cli {
    /// Files to play or links to open
    #[arg(value_name = "FILE_OR_LINK")]
    pub opened: Vec<String>,
}

impl Cli {
    /// Reads the command line. The help and the version print and exit. Any other mistake, such
    /// as a flag a launcher added that Encore does not know, leaves the flags out and keeps the
    /// rest as things to open, so Encore still starts.
    pub fn read() -> Self {
        match Self::try_parse() {
            Ok(cli) => cli,
            Err(error)
                if matches!(
                    error.kind(),
                    ErrorKind::DisplayHelp | ErrorKind::DisplayVersion
                ) =>
            {
                error.exit()
            }
            Err(_) => Self {
                opened: std::env::args()
                    .skip(1)
                    .filter(|arg| !arg.starts_with('-'))
                    .collect(),
            },
        }
    }
}
