//! Regression tests for the same transform targets used by the GPUI viewport.
use glam::{Mat3, Mat4, Vec3};
use rbx_android_core::Editor;
use rbx_dom::{Ref, Variant, Vector3Data};
use rbx_viewer::{gizmo, Pose};

#[allow(dead_code)]
#[path = "../src/transform.rs"]
mod transform;

use transform::{frame, targets::Targets};

fn model() -> (tempfile::TempDir, Editor, Ref, [Ref; 2], Vec3) {
    let folder = tempfile::tempdir().unwrap();
    let mut editor = Editor::open_or_create(folder.path().join("gizmo.rbxl")).unwrap();
    let workspace = editor
        .dom
        .root_refs()
        .iter()
        .copied()
        .find(|&id| editor.dom.get(id).unwrap().class() == "Workspace")
        .unwrap();
    let model = editor
        .dom
        .new_instance("Model", "Assembly", Some(workspace));
    let group = editor.dom.new_instance("Model", "Nested", Some(model));
    let first = editor.dom.new_instance("Part", "First", Some(model));
    let second = editor.dom.new_instance("Part", "Second", Some(group));
    let pivot = Vec3::new(12., 4., -3.);
    let rotation = Mat3::from_rotation_z(0.6);
    for (part, offset) in [(first, Vec3::X * 4.), (second, Vec3::X * 8.)] {
        editor
            .dom
            .set_property(
                part,
                "size",
                Variant::Vector3(Vector3Data {
                    x: 4.,
                    y: 2.,
                    z: 2.,
                }),
            )
            .unwrap();
        editor
            .dom
            .set_property(
                part,
                "CFrame",
                Variant::CFrame(frame::cframe(
                    Mat4::from_translation(pivot + offset) * Mat4::from_mat3(rotation),
                )),
            )
            .unwrap();
    }
    editor
        .dom
        .set_property(
            model,
            "WorldPivot",
            Variant::CFrame(frame::cframe(
                Mat4::from_translation(pivot) * Mat4::from_mat3(rotation),
            )),
        )
        .unwrap();
    (folder, editor, model, [first, second], pivot)
}

#[test]
fn grouped_parts_move_turn_and_scale_from_the_same_custom_pivot() {
    let (_folder, editor, model, [first, second], pivot) = model();
    let held = Targets::read(&editor.dom, editor.database(), &[model, second]);
    let mut changed = held.clone();
    assert_eq!(held.iter().count(), 2);
    assert!(held
        .pivot()
        .unwrap()
        .w_axis
        .truncate()
        .abs_diff_eq(pivot, 1e-5));
    let turn = Mat3::from_rotation_y(std::f32::consts::FRAC_PI_2);
    let rotated = changed.rotate_about(&held, pivot, turn);
    assert_eq!(rotated[0].0, first);
    assert_eq!(rotated[1].0, second);
    for ((_, orientation, position), start) in rotated.iter().zip(held.iter()) {
        assert!(orientation.abs_diff_eq(turn * start.orientation(), 1e-5));
        assert!(position.abs_diff_eq(pivot + turn * (start.position() - pivot), 1e-5));
    }
    let moved = changed.translate(Vec3::new(1., 2., 3.));
    for ((_, position), (_, _, turned)) in moved.iter().zip(rotated.iter()) {
        assert!(position.abs_diff_eq(*turned + Vec3::new(1., 2., 3.), 1e-5));
    }
    let scaled = changed.scale_about(&held, pivot, 1.5);
    for ((_, size, position), start) in scaled.iter().zip(held.iter()) {
        assert!(size.abs_diff_eq(start.size() * 1.5, 1e-5));
        assert!(position.abs_diff_eq(pivot + (start.position() - pivot) * 1.5, 1e-5));
    }
}

#[test]
fn rotation_rings_follow_world_and_rotated_local_frames() {
    let (_folder, editor, model, _, pivot) = model();
    let held = Targets::read(&editor.dom, editor.database(), &[model]);
    let pose = Pose {
        position: pivot + Vec3::new(8., 6., 12.),
        yaw: 0.,
        pitch: 0.,
        fov_degrees: 70.,
        ortho_scale: 20.,
    };
    let local = Mat3::from_mat4(held.pivot().unwrap());
    let arm = gizmo::arm_length(pivot, pose, false);
    let local_handles = gizmo::Handles::new(pivot, gizmo::basis(Some(local)), arm);
    let world_handles = gizmo::Handles::new(pivot, gizmo::basis(None), arm);
    for (axis, basis) in gizmo::Axis::ALL
        .into_iter()
        .zip([Vec3::X, Vec3::Y, Vec3::Z])
    {
        assert!(world_handles.ring_frame(axis).0.abs_diff_eq(basis, 1e-5));
        assert!(local_handles
            .ring_frame(axis)
            .0
            .abs_diff_eq(local * basis, 1e-5));
    }
}
