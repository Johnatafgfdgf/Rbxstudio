use rbx_android_core::{Editor, PlaySession, Simulation};
use rbx_dom::{CFrameData, Ref, Variant};
use serde_json::json;

fn editor() -> (tempfile::TempDir, Editor) {
    let temp = tempfile::tempdir().unwrap();
    let editor = Editor::open_or_create(temp.path().join("project.rbxlx")).unwrap();
    (temp, editor)
}

#[test]
fn rook_r6_walks_jumps_and_stop_restores_the_exact_editor_document() {
    use glam::Vec3;
    use std::time::Duration;
    let (_, mut e) = editor();
    let part = e.insert("Part").unwrap();
    e.edit(part, "Name", "KeepMe").unwrap();
    e.transform("move", [25., 5., 0.]).unwrap();
    let before = rbx_xml::serialize(&e.dom).unwrap();
    let selection = e.selection.clone();
    let dirty = e.dirty;
    let mut play = PlaySession::start(&mut e).unwrap();
    let model = e.dom.get(play.character.model).unwrap();
    assert!(model
        .children()
        .iter()
        .any(|&r| e.dom.get(r).unwrap().name() == "Left Leg"));
    assert_eq!(
        e.dom
            .get(play.character.player)
            .unwrap()
            .properties()
            .get("Character"),
        Some(&Variant::Ref(play.character.model))
    );
    e.dom.take_changes();
    let tick = Duration::from_secs_f64(1. / 60.);
    for _ in 0..120 {
        play.step(&mut e, tick).unwrap();
        e.dom.take_changes();
    }
    let standing = play.character.position(&e.dom).unwrap();
    assert!(
        (standing.y - 4.).abs() < 0.15,
        "R6 should stand on SpawnLocation: {standing:?}"
    );
    for _ in 0..60 {
        play.player_input( Vec3::NEG_Z, false);
        play.step(&mut e, tick).unwrap();
        e.dom.take_changes();
    }
    let walked = play.character.position(&e.dom).unwrap();
    assert!(
        walked.z < standing.z - 12.,
        "Rook controller must actually walk: {standing:?} -> {walked:?}"
    );
    for _ in 0..60 {
        play.player_input( Vec3::ZERO, false);
        play.step(&mut e, tick).unwrap();
        e.dom.take_changes();
    }
    let start = play.character.position(&e.dom).unwrap();
    play.player_input( Vec3::ZERO, true);
    let mut highest = start.y;
    for _ in 0..90 {
        play.step(&mut e, tick).unwrap();
        e.dom.take_changes();
        highest = highest.max(play.character.position(&e.dom).unwrap().y);
    }
    assert!(
        highest > start.y + 3.,
        "Humanoid must jump via Rook, not teleport: {start:?}, peak {highest}"
    );
    let landed = play.character.position(&e.dom).unwrap();
    assert!(
        (landed.y - start.y).abs() < 0.2,
        "Must collide and land: {landed:?}"
    );
    play.stop(&mut e);
    assert_eq!(rbx_xml::serialize(&e.dom).unwrap(), before);
    assert_eq!(e.selection, selection);
    assert_eq!(e.dirty, dirty);
}

#[test]
fn a_drag_gesture_is_one_original_history_entry() {
    let (_, mut e) = editor();
    let p = e.insert("Part").unwrap();
    let original = frame(&e, p);
    e.begin_gesture();
    for _ in 0..20 {
        e.transform("move", [1., 0., 0.]).unwrap();
    }
    e.end_gesture();
    assert_eq!(frame(&e, p).position.x, original.position.x + 20.);
    e.command(&json!({"op":"undo"})).unwrap();
    assert_eq!(frame(&e, p), original);
    e.command(&json!({"op":"redo"})).unwrap();
    assert_eq!(frame(&e, p).position.x, original.position.x + 20.);
}

#[test]
fn new_parts_created_during_play_enter_rook_physics() {
    use std::time::Duration;
    let (_, mut e) = editor();
    let mut play = PlaySession::start(&mut e).unwrap();
    e.dom.take_changes();
    play.run_server("local p=Instance.new('Part'); p.Name='RuntimeFalling'; p.Position=Vector3.new(30,25,0); p.Parent=workspace").unwrap();
    play.step(&mut e,Duration::from_secs_f64(1./60.)).unwrap();
    let p = e.snapshot()["tree"]
        .as_array()
        .unwrap()
        .iter()
        .find(|v| v["name"] == "RuntimeFalling")
        .unwrap()["id"]
        .as_u64()
        .unwrap();
    let p = Ref::new(p as u32);
    let start = frame(&e, p).position.y;
    for _ in 0..90 {
        play.step(&mut e, Duration::from_secs_f64(1. / 60.))
            .unwrap();
        e.dom.take_changes();
    }
    assert!(frame(&e, p).position.y < start - 10.);
    play.run_server("workspace.RuntimeFalling:Destroy()").unwrap();
    play.step(&mut e, Duration::from_secs_f64(1. / 60.))
        .unwrap();
    assert!(e.dom.get(p).is_none());
}

#[test]
fn original_rook_contacts_and_frame_signals_reach_the_original_luau_vm() {
    use std::time::Duration;
    let (_, mut e) = editor();
    e.run("local p=Instance.new('Part');p.Name='FallingContact';p.Position=Vector3.new(30,20,0);p.Parent=workspace").unwrap();
    let script = e.insert("Script").unwrap();
    e.edit(
        script,
        "Source",
        r#"
local rs=game:GetService('RunService')
assert(rs:IsRunning() and not rs:IsEdit())
rs.Heartbeat:Connect(function(dt)
    assert(dt>0)
    workspace:SetAttribute('Frames',(workspace:GetAttribute('Frames') or 0)+1)
end)
workspace.FallingContact.Touched:Connect(function(other)
    if other.Name=='Baseplate' then workspace:SetAttribute('Landed',true) end
end)
"#,
    )
    .unwrap();
    let mut play = PlaySession::start(&mut e).unwrap();
    e.dom.take_changes();
    for _ in 0..120 {
        play.step(&mut e, Duration::from_secs_f64(1. / 60.))
            .unwrap();
        e.dom.take_changes();
    }
    let workspace=e.dom.root_refs().iter().copied().find(|r|e.dom.get(*r).unwrap().class()=="Workspace").unwrap();
    let props = e.dom.get(workspace).unwrap().properties();
    let attributes = rbx_dom::attributes::decode(props.get("AttributesSerialize"));
    assert_eq!(
        attributes.get("Landed"),
        Some(&Variant::Bool(true)),
        "Real physics contact must call Luau: {:?}",
        e.logs
    );
    assert_eq!(
        attributes.get("Frames"),
        Some(&Variant::Float64(121.)),
        "Initial server step plus every subsequent frame must reach Heartbeat: {:?}",
        e.logs
    );
}

#[test]
fn importing_a_multi_root_model_keeps_cross_references_and_existing_place() {
    let (temp,mut e)=editor();
    let old=e.dom.root_refs().to_vec();
    let mut asset=rbx_dom::WeakDom::new();
    let a=asset.new_instance("Part","LinkedA",None);
    let b=asset.new_instance("Part","LinkedB",None);
    asset.set_property(a,"FutureReference",Variant::Ref(b)).unwrap();
    asset.set_property(a,"FutureData",Variant::String("unknown preserved".into())).unwrap();
    let path=temp.path().join("model.rbxmx");std::fs::write(&path,rbx_xml::serialize(&asset).unwrap()).unwrap();
    e.import_model(&path).unwrap();
    assert!(old.iter().all(|r|e.dom.get(*r).is_some()));
    let tree=e.snapshot()["tree"].as_array().unwrap().clone();
    let id=|name|Ref::new(tree.iter().find(|v|v["name"]==name).unwrap()["id"].as_u64().unwrap() as u32);
    let (a,b)=(id("LinkedA"),id("LinkedB"));
    assert_eq!(e.dom.get(a).unwrap().properties().get("FutureReference"),Some(&Variant::Ref(b)));
    assert_eq!(e.dom.get(a).unwrap().properties().get("FutureData"),Some(&Variant::String("unknown preserved".into())));
    e.command(&json!({"op":"undo"})).unwrap();
    assert!(e.dom.get(a).is_none()&&e.dom.get(b).is_none());
    assert_eq!(e.dom.root_refs(),old);
}

fn frame(e: &Editor, id: Ref) -> CFrameData {
    match e.dom.get(id).unwrap().properties().get("CFrame").unwrap() {
        Variant::CFrame(frame) => *frame,
        _ => panic!("wrong type"),
    }
}

#[test]
fn original_template_is_a_real_studio_sized_place() {
    let (_, e) = editor();
    let state = e.snapshot();
    let tree = state["tree"].as_array().unwrap();
    assert!(tree.iter().any(|i| i["class"] == "Lighting"));
    let base = tree.iter().find(|i| i["name"] == "Baseplate").unwrap();
    let id = Ref::new(base["id"].as_u64().unwrap() as u32);
    let Some(Variant::Vector3(size)) = e.dom.get(id).unwrap().properties().get("size") else {
        panic!("size missing")
    };
    assert!(size.x >= 512. && size.z >= 512.);
}

#[test]
fn transforms_properties_and_original_history_round_trip() {
    let (_, mut e) = editor();
    let p = e.insert("Part").unwrap();
    e.edit(p, "Name", "MobilePart").unwrap();
    e.transform("move", [7., 5., -3.]).unwrap();
    let moved = frame(&e, p);
    e.transform("rotate", [20., 45., 10.]).unwrap();
    let rotated = frame(&e, p);
    assert_ne!(moved.rotation, rotated.rotation);
    e.edit(p, "CFrame", "12, 20, 8").unwrap();
    assert_eq!(
        frame(&e, p).rotation,
        rotated.rotation,
        "position editing must not round-trip the rotation"
    );
    e.command(&json!({"op":"undo"})).unwrap();
    assert_eq!(frame(&e, p), rotated);
    e.command(&json!({"op":"redo"})).unwrap();
    assert_eq!(frame(&e, p).position.y, 20.);
    e.transform("scale", [1., 2., 3.]).unwrap();
    let Some(Variant::Vector3(s)) = e.dom.get(p).unwrap().properties().get("size") else {
        panic!()
    };
    assert!((s.x - 5.).abs() < 0.001 && (s.y - 3.2).abs() < 0.001 && (s.z - 5.).abs() < 0.001);
}

#[test]
fn scripts_compile_execute_and_mutate_the_original_datamodel() {
    let (_, mut e) = editor();
    e.run("local p=Instance.new('Part');p.Name='FromLuau';p.Parent=workspace;p.Position=Vector3.new(3,18,2); print(p.Name)").unwrap();
    assert!(e.logs.iter().any(|l| l.contains("FromLuau")));
    assert!(e.snapshot()["tree"]
        .as_array()
        .unwrap()
        .iter()
        .any(|i| i["name"] == "FromLuau"));
    assert!(rbx_lua::compile_check("local = broken", "bad").is_err());
}

#[test]
fn save_reopen_keeps_script_whitespace_unknown_properties_and_references() {
    let (temp, mut e) = editor();
    let p = e.insert("Part").unwrap();
    let script = e.insert("Script").unwrap();
    let code = "\n-- spaces and trailing newline\nlocal answer = 42\nprint(answer)\n";
    e.edit(script, "Source", code).unwrap();
    e.dom
        .set_property(
            script,
            "FutureCustomData",
            Variant::String("unrecognized preserved ✓".into()),
        )
        .unwrap();
    e.dom
        .set_property(script, "FutureReference", Variant::Ref(p))
        .unwrap();
    e.save().unwrap();
    let reopened = Editor::open_or_create(e.path.clone()).unwrap();
    let state = reopened.snapshot();
    let tree = state["tree"].as_array().unwrap();
    let s = tree.iter().find(|i| i["class"] == "Script").unwrap();
    let sid = Ref::new(s["id"].as_u64().unwrap() as u32);
    let props = reopened.dom.get(sid).unwrap().properties();
    assert_eq!(props.get("Source"), Some(&Variant::String(code.into())));
    assert_eq!(
        props.get("FutureCustomData"),
        Some(&Variant::String("unrecognized preserved ✓".into()))
    );
    let Some(Variant::Ref(target)) = props.get("FutureReference") else {
        panic!("reference lost")
    };
    assert_eq!(reopened.dom.get(*target).unwrap().class(), "Part");
    let binary = temp.path().join("roundtrip.rbxl");
    e.command(&json!({"op":"export","path":binary})).unwrap();
    let parsed = rbx_viewer::read_place(&binary).unwrap();
    assert_eq!(parsed.root_refs().len(), e.dom.root_refs().len());
}

#[test]
fn malformed_import_does_not_replace_or_damage_the_document() {
    let (temp, mut e) = editor();
    let p = e.insert("Part").unwrap();
    e.save().unwrap();
    let before = std::fs::read(&e.path).unwrap();
    let bad = temp.path().join("bad.rbxl");
    std::fs::write(&bad, b"not a place").unwrap();
    assert!(e.load(&bad).is_err());
    assert!(e.dom.get(p).is_some());
    assert_eq!(std::fs::read(&e.path).unwrap(), before);
}

#[test]
fn rook_actually_steps_a_falling_part_and_editor_snapshot_stays_intact() {
    let (_, mut e) = editor();
    let p = e.insert("Part").unwrap();
    e.edit(p, "Anchored", "false").unwrap();
    e.edit(p, "CFrame", "0, 30, 0").unwrap();
    let before = e.dom.clone();
    let original = frame(&e, p).position.y;
    let mut sim = Simulation::new(&e.dom).unwrap();
    for _ in 0..120 {
        sim.step(&mut e.dom, 1. / 120.).unwrap();
    }
    let after = frame(&e, p).position.y;
    assert!(
        after < original - 5.,
        "Rook must move the part, original={original}, after={after}"
    );
    assert!(
        after > -5.,
        "Rook must collide with the original baseplate: {after}"
    );
    e.dom = before;
    assert_eq!(frame(&e, p).position.y, original);
}

#[test]
fn duplicate_uses_original_luau_clone_and_keeps_references_valid() {
    let (_, mut e) = editor();
    let original = e.insert("Part").unwrap();
    e.command(&json!({"op":"duplicate"})).unwrap();
    let copy = e.selection[0];
    assert_ne!(copy, original);
    assert_eq!(
        e.dom.get(copy).unwrap().properties(),
        e.dom.get(original).unwrap().properties()
    );
    e.command(&json!({"op":"group"})).unwrap();
    let model = e.selection[0];
    assert_eq!(e.dom.get(model).unwrap().class(), "Model");
    assert_eq!(e.dom.parent(copy), Some(model));
}

#[test]
fn original_scale_dragger_keeps_the_opposite_face_fixed() {
    use glam::Vec3;
    let (_, mut e) = editor();
    let p = e.insert("Part").unwrap();
    e.snap = 1.;
    let pose = rbx_viewer::Pose {
        position: Vec3::new(-12., 8., 16.),
        yaw: 0.,
        pitch: 0.,
        fov_degrees: 70.,
        ortho_scale: 20.,
    };
    let origin = Vec3::new(-12., 4., 10.);
    let press = rbx_viewer::pick::Ray::new(origin, Vec3::new(-2., 4., 0.) - origin);
    let drag = rbx_android_core::ScaleGesture::grab(&e, pose, press)
        .expect("negative X face must be grabbable");
    let moved = rbx_viewer::pick::Ray::new(origin, Vec3::new(-4., 4., 0.) - origin);
    drag.advance(&mut e, moved).unwrap();
    let Some(Variant::Vector3(size)) = e.dom.get(p).unwrap().properties().get("size") else {
        panic!()
    };
    let x = frame(&e, p).position.x;
    assert!(
        (size.x - 6.).abs() < 0.01 && (x + 1.).abs() < 0.01,
        "pulling -X outward must grow, size={size:?}, x={x}"
    );
    assert!(
        (x + size.x / 2. - 2.).abs() < 0.01,
        "the opposite +X face must stay fixed"
    );
}

#[test]
fn moving_preserves_the_stored_rotation_exactly() {
    let (_, mut e) = editor();
    let p = e.insert("Part").unwrap();
    let mut cf = frame(&e, p);
    cf.rotation = [
        0.9999, 0.01, 0.002, -0.009, 1., 0.003, 0.004, -0.005, 0.9998,
    ];
    e.dom
        .set_property(p, "CFrame", Variant::CFrame(cf))
        .unwrap();
    e.transform("move", [2., 3., 4.]).unwrap();
    assert_eq!(frame(&e, p).rotation, cf.rotation);
}

#[test]
fn toolbox_reviews_nested_model_scripts_without_running_them() {
    use rbx_audit::model_review::{Coverage, Risk, remove_model_scripts, review_model, script_source};
    use rbx_dom::{Variant, WeakDom};

    let mut dom = WeakDom::new();
    let model = dom.new_instance("Model", "Crate", None);
    let folder = dom.new_instance("Folder", "Hidden", Some(model));
    let main = dom.new_instance("Script", "Bootstrap", Some(folder));
    let helper = dom.new_instance("ModuleScript", "Helper", Some(folder));
    let client = dom.new_instance("LocalScript", "Local", Some(main));
    let art = dom.new_instance("Part", "Decoration", Some(client));

    let source = r#"
        local helper = require(script.Parent.Helper)
        require(9042)
        local http = game:GetService("HttpService")
        local scriptText = string.char(65,66,67,68,69,70,71,72,73,74,75,76)
        loadstring(scriptText)()
        workspace:ClearAllChildren()
    "#;
    dom.set_property(main, "Source", Variant::String(source.into())).unwrap();
    dom.set_property(helper, "Source", Variant::String(
        "-- loadstring('x'); require(17)\nreturn 'HttpService in a literal'".into(),
    )).unwrap();
    let scripts = review_model(&dom);
    assert_eq!(scripts.len(), 3);
    let risk = |kind| scripts[0].risks.iter().any(|(risk, line)| *risk == kind && *line > 0);
    assert_eq!(scripts[0].path, "Crate.Hidden.Bootstrap");
    assert_eq!(scripts[0].coverage, Coverage::Scanned);
    for kind in [Risk::ExternalCode, Risk::DynamicCode, Risk::Network, Risk::GameMutation, Risk::Obfuscation] {
        assert!(risk(kind), "{scripts:?}");
    }
    assert!(!risk(Risk::UnresolvedRequire));
    assert_eq!(script_source(&dom, main), Some(source));
    assert!(scripts.iter().find(|s| s.instance == helper).unwrap().risks.is_empty());
    assert_eq!(scripts.iter().find(|s| s.instance == client).unwrap().coverage, Coverage::MissingSource);
    remove_model_scripts(&mut dom, &scripts);
    assert!(review_model(&dom).is_empty());
    assert_eq!(dom.parent(art), Some(folder));
}

#[test]
fn android_audit_fixture_imports_with_three_reviewable_script_classes() {
    use rbx_audit::model_review::{Coverage, remove_model_scripts, review_model};

    let mut model = rbx_xml::deserialize(include_str!("../../tests/android/toolbox-review.rbxmx"))
        .expect("Toolbox audit fixture must be a valid RBXMX");
    let reviewed = review_model(&model);
    let mut classes: Vec<_> = reviewed.iter().map(|script| script.class.as_str()).collect();
    classes.sort_unstable();
    assert_eq!(classes, ["LocalScript", "ModuleScript", "Script"]);
    assert!(reviewed.iter().all(|script| script.coverage == Coverage::Scanned));
    remove_model_scripts(&mut model, &reviewed);
    assert!(review_model(&model).is_empty());
    assert_eq!(model.root_refs().len(), 1);
    let root = model.get(model.root_refs()[0]).unwrap();
    assert_eq!(root.name(), "Audit Toolbox");
    assert_eq!(root.children().len(), 1);
}
