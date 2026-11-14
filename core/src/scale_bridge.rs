use crate::{transform::targets::Target, Editor};
use glam::Vec3;
use rbx_dom::Ref;
use rbx_viewer::{gizmo::Faces, pick::Ray, Pose};

#[path = "upstream/gizmo_types.rs"]
mod types;
use types::{Change, Drag};
#[path = "upstream/workspace_view/gizmo/scale.rs"]
mod original;
const MIN_SIZE: f32 = 0.001;
const MAX_SIZE: f32 = 2048.;
#[derive(Clone, Copy)]
struct Landing {
    grid: f32,
}
fn snapped(value: f32, landing: Landing) -> f32 {
    rbx_viewer::snap::round_to(value, landing.grid)
}

/// The original ScaleDragger's state and advance function, over touch rays.
#[derive(Clone)]
pub struct ScaleGesture {
    id: Ref,
    drag: Drag,
}
impl ScaleGesture {
    pub fn grab(editor: &Editor, pose: Pose, ray: Ray) -> Option<Self> {
        let id = *editor.selection.first()?;
        let target = Target::read(&editor.dom, editor.database(), Some(id))?;
        let faces = Faces::new(target.model, pose, false);
        let drag = original::grab_face(&faces, target, ray, false, false)?;
        Some(Self { id, drag })
    }
    pub fn advance(&self, editor: &mut Editor, ray: Ray) -> Result<(), String> {
        if let Some((_, Change::Size { size, position })) =
            original::advance(self.drag, ray, Landing { grid: editor.snap })
        {
            editor.resize_absolute(self.id, size, position)?;
        }
        Ok(())
    }
}
