//! Reuse Android's real host services for the original Rook engine.
use std::io;

#[derive(Debug)]
struct Clipboard;
impl rook_platform::clipboard::Clipboard for Clipboard {
    fn text(&self) -> Option<String> {
        gpui_mobile::packages::clipboard::get_text().ok().flatten()
    }
    fn set_text(&self, text: &str) {
        if gpui_mobile::packages::clipboard::set_text(text).is_err() {
            log::warn!("O Android não conseguiu copiar o texto do jogo");
        }
    }
}

#[derive(Debug)]
struct Credentials;
impl rook_platform::credentials::Credentials for Credentials {
    fn get(&self, name: &str) -> io::Result<Option<String>> {
        gpui_mobile::android::read_credentials(&format!("Rook:{name}"))
            .map_err(|error| io::Error::other(error.to_string()))?
            .map(|(_, bytes)| String::from_utf8(bytes).map_err(|error| io::Error::other(error.utf8_error())))
            .transpose()
    }
    fn set(&self, name: &str, secret: &str) -> io::Result<()> {
        gpui_mobile::android::write_credentials(&format!("Rook:{name}"), name, secret.as_bytes())
            .map_err(|error| io::Error::other(error.to_string()))
    }
    fn clear(&self, name: &str) -> io::Result<bool> {
        let present = self.get(name)?.is_some();
        gpui_mobile::android::delete_credentials(&format!("Rook:{name}"))
            .map_err(|error| io::Error::other(error.to_string()))?;
        Ok(present)
    }
}

pub fn install() {
    rbx_android_core::install_platform(Box::new(Clipboard), Box::new(Credentials));
}
