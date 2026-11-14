use std::sync::Once;

fn setup() {
    static SETUP: Once = Once::new();
    SETUP.call_once(|| {
        let temp = tempfile::tempdir().unwrap();
        let mut editor =
            rbx_android_core::Editor::open_or_create(temp.path().join("diagnostics.rbxlx"))
                .unwrap();
        rbx_android_core::PlaySession::start(&mut editor)
            .unwrap()
            .stop(&mut editor);
    });
}

#[test]
fn receive_item_button_reaches_backpack_grip_use_and_unequip() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("tool_delivery");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn client_created_tool_has_a_working_grip_without_server_replication() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("local_tool");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn modern_constraints_actually_move_and_release_physics_bodies() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("mover_constraints");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn live_runtime_objects_tasks_and_tweens_replicate() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("runtime_objects");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn constraint_modes_limits_reactions_and_attachment_mutations() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("constraint_modes");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn generic_constraints_drive_the_humanoid_assembly_and_release_it() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("humanoid_forces");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn startergear_and_starterpack_refill_the_backpack_on_respawn() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("inventory_respawn");
    assert!(result.is_ok(), "{result:?}");
}

#[test]
fn tool_mouse_hit_and_target_are_updated_before_touch_activation() {
    setup();
    let result = rbx_android_core::diagnostics::run_case("tool_aim");
    assert!(result.is_ok(), "{result:?}");
}
