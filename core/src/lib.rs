#![allow(dead_code, unused_imports)]
//! Mobile adapter over rbx-native's original editor domain modules.

#[path = "upstream/history.rs"]
mod history;
#[path = "upstream/properties/mod.rs"]
mod properties;
#[path = "upstream/save.rs"]
mod save;
mod script_editor;
#[path = "upstream/home/template.rs"]
mod template;
mod folder_colors {
    // The original Properties module only needs this class discriminator.
    pub(crate) const FOLDER_CLASS: &str = "Folder";
}
#[path = "upstream/terrain/dom.rs"]
mod terrain;
mod transform;

mod editor;
mod scale_bridge;
mod simulation;
mod character;
mod play;
mod animation;
mod projection;
mod platform;
pub mod resources;
pub mod diagnostics;
pub use platform::install_platform;
#[path = "upstream/rook_character.rs"]
mod rook_character;
pub use editor::Editor;
pub use scale_bridge::ScaleGesture;
pub use simulation::Simulation;
pub use character::Character;
pub use play::PlaySession;
