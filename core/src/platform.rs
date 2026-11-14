//! Rook's portable host, without desktop clipboard/keyring dependencies.
use rook_platform::{clipboard::Clipboard, credentials::Credentials, Platform};
use std::io;
#[derive(Debug)]
struct NoWindowClipboard;
impl Clipboard for NoWindowClipboard {
    fn text(&self) -> Option<String> {
        None
    }
    fn set_text(&self, _: &str) {
        log::warn!("Clipboard unavailable in this host");
    }
}
#[derive(Debug)]
struct NoCredentialStore;
impl Credentials for NoCredentialStore {
    fn get(&self, _: &str) -> io::Result<Option<String>> {
        Err(io::Error::new(
            io::ErrorKind::Unsupported,
            "Host credential store not installed",
        ))
    }
    fn set(&self, _: &str, _: &str) -> io::Result<()> {
        Err(io::Error::new(
            io::ErrorKind::Unsupported,
            "Host credential store not installed",
        ))
    }
    fn clear(&self, _: &str) -> io::Result<bool> {
        Err(io::Error::new(
            io::ErrorKind::Unsupported,
            "Host credential store not installed",
        ))
    }
}
pub fn install_platform(clipboard: Box<dyn Clipboard>, credentials: Box<dyn Credentials>) {
    let _ = rook_platform::install(Platform {
        files: Box::new(rook_platform::files::NativeFiles),
        workers: Box::new(rook_platform::parallel::Rayon),
        threads: Box::new(rook_platform::threads::OsThreads),
        network: Box::new(rook_platform::network::Ureq::new()),
        clipboard,
        credentials,
    });
    let _ = rook_platform::install_workers(Box::new(rook_platform::parallel::Serial));
    rook_physics::size_worker_pool(
        std::thread::available_parallelism()
            .map_or(2, |n| n.get() / 2)
            .clamp(1, 4),
    );
}
pub(crate) fn ensure_platform() {
    install_platform(Box::new(NoWindowClipboard), Box::new(NoCredentialStore));
}
