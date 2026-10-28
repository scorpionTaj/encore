use std::io::{self, ErrorKind, Read, Write};
use std::thread;
use std::time::Duration;

use interprocess::local_socket::traits::{ListenerExt as _, Stream as _};
use interprocess::local_socket::{ListenerOptions, Name, Stream};
use tokio::sync::mpsc::UnboundedSender;

const SOCKET: &str = match cfg!(debug_assertions) {
    true => "encore-dev.sock",
    false => "encore.sock",
};

pub enum Instance {
    First,
    Running,
    Failed,
}

/// `args` is every non-flag command-line argument of this launch: a lone `spotify:`/web link, or
/// one or more file paths from an "Open With" selection.
pub fn claim(args: &[String], sender: UnboundedSender<Vec<String>>) -> Instance {
    let name = match name() {
        Ok(name) => name,
        Err(error) => {
            log::warn!("single: cannot name the instance socket: {error:#}");
            return Instance::Failed;
        }
    };

    let listener = match options(name.clone()).create_sync() {
        Ok(listener) => listener,

        Err(error) => {
            // Windows reports an occupied pipe with an error other than
            // AddrInUse, so any failure may mean another Encore owns the socket.
            if hand_over(name.clone(), args) {
                return Instance::Running;
            }

            // Only a filesystem socket can leave a stale AddrInUse behind.
            if error.kind() != ErrorKind::AddrInUse {
                log::error!("single: cannot own the instance socket: {error:#}");
                return Instance::Failed;
            }

            log::warn!("single: socket is occupied but unreachable; attempting recovery");

            match options(name.clone())
                .try_overwrite(true)
                .max_spin_time(Duration::ZERO)
                .create_sync()
            {
                Ok(listener) => listener,
                Err(error) => {
                    log::error!("single: cannot recover instance socket: {error:#}");
                    return Instance::Failed;
                }
            }
        }
    };

    thread::spawn(move || {
        for connection in listener.incoming() {
            let Ok(mut connection) = connection else {
                continue;
            };
            let mut payload = String::new();
            if connection.read_to_string(&mut payload).is_err() {
                continue;
            }
            let items: Vec<String> = payload
                .split('\n')
                .map(str::trim)
                .filter(|line| !line.is_empty())
                .map(str::to_owned)
                .collect();
            if sender.send(items).is_err() {
                break;
            }
        }
    });

    Instance::First
}

fn hand_over(name: Name<'_>, args: &[String]) -> bool {
    let mut stream = match Stream::connect(name) {
        Ok(stream) => stream,
        Err(error) => {
            log::warn!("single: cannot reach the running instance: {error:#}");
            return false;
        }
    };
    stream.write_all(args.join("\n").as_bytes()).is_ok()
}

/// The instance socket as a file in a folder only this user can enter, so another account can
/// neither take the name first nor connect to it.
fn name() -> io::Result<Name<'static>> {
    use interprocess::local_socket::{GenericFilePath, ToFsName as _};

    private_dir()?.join(SOCKET).to_fs_name::<GenericFilePath>()
}

/// The per-user temporary folder macOS keeps under `/var/folders`, which only this user can
/// enter. It is asked of the system rather than read from `TMPDIR`, so a launch from a shell
/// that changed the variable still finds the same socket.
fn private_dir() -> io::Result<std::path::PathBuf> {
    use std::ffi::OsString;
    use std::os::unix::ffi::OsStringExt as _;

    let mut buffer = vec![0u8; libc::PATH_MAX as usize];
    // SAFETY: confstr writes at most `buffer.len()` bytes into the buffer it is given.
    let length = unsafe {
        libc::confstr(
            libc::_CS_DARWIN_USER_TEMP_DIR,
            buffer.as_mut_ptr().cast(),
            buffer.len(),
        )
    };
    if length == 0 || length > buffer.len() {
        return Err(io::Error::last_os_error());
    }
    // the length counts the terminating NUL
    buffer.truncate(length - 1);
    Ok(OsString::from_vec(buffer).into())
}

/// The listener for the instance socket, open to every Encore this user launches.
fn options(name: Name<'_>) -> ListenerOptions<'_> {
    ListenerOptions::new().name(name)
}
