use rbx_android_core::{Editor, PlaySession};
use rbx_dom::{Ref, Variant};
use std::time::Duration;
fn setup() -> (tempfile::TempDir, Editor) {
    let t = tempfile::tempdir().unwrap();
    let e = Editor::open_or_create(t.path().join("runtime.rbxlx")).unwrap();
    (t, e)
}
fn find(e: &Editor, name: &str) -> Ref {
    let state = e.snapshot();
    Ref::new(
        state["tree"]
            .as_array()
            .unwrap()
            .iter()
            .find(|v| v["name"] == name)
            .unwrap_or_else(|| panic!("{name} not projected; {:?}", e.logs))["id"]
            .as_u64()
            .unwrap() as u32,
    )
}
fn attributes(e: &Editor, r: Ref) -> std::collections::BTreeMap<String, Variant> {
    rbx_dom::attributes::decode(
        e.dom
            .get(r)
            .unwrap()
            .properties()
            .get("AttributesSerialize"),
    )
}
fn ticks(p: &mut PlaySession, e: &mut Editor, n: u32) {
    for _ in 0..n {
        p.step(e, Duration::from_secs_f64(1. / 60.)).unwrap();
        e.dom.take_changes();
    }
}
#[test]
fn original_rook_emote_collections_keep_state_clones_and_signal_payloads() {
    let (_t, mut e) = setup();
    let mut p = PlaySession::start(&mut e).unwrap();
    p.run_client(r#"
        local d=Instance.new('HumanoidDescription')
        assert(type(d:GetEmotes())=='table' and next(d:GetEmotes())==nil)
        assert(type(d:GetEquippedEmotes())=='table' and #d:GetEquippedEmotes()==0)
        local changed,equipped=0,0
        d.EmotesChanged:Connect(function(value) changed+=1; value.Wave=nil end)
        d.EquippedEmotesChanged:Connect(function(value) equipped+=1; if value[1] then value[1].Name='mutated' end end)
        d:SetEmotes({Wave={1,2},Salute={3}})
        d:AddEmote('Wave',2)
        d:AddEmote('Wave',4)
        d:SetEquippedEmotes({'Wave','Salute'})
        local list=d:GetEquippedEmotes()
        assert(list[1].Slot==1 and list[1].Name=='Wave')
        list[1].Name='not-shared'
        assert(d:GetEquippedEmotes()[1].Name=='Wave')
        d:SetEquippedEmotes({{Slot=6,Name='Wave'}})
        assert(not pcall(function() d:SetEquippedEmotes({{Slot=6,Name='Wave'},{Slot=6,Name='Salute'}}) end))
        assert(d:GetEquippedEmotes()[1].Slot==6)
        assert(not pcall(function() d:SetEmotes({Invalid={-1}}) end))
        assert(#d:GetEmotes().Wave==3)
        local copy=d:Clone()
        assert(copy:GetEquippedEmotes()[1].Slot==6 and #copy:GetEmotes().Wave==3)
        copy:SetEmotes({Other={7}})
        assert(d:GetEmotes().Wave~=nil and copy:GetEmotes().Wave==nil)
        d:RemoveEmote('Salute')
        task.wait()
        assert(changed==4 and equipped==2)
        assert(#d:GetEmotes().Wave==3 and d:GetEquippedEmotes()[1].Name=='Wave')
        workspace:SetAttribute('EmotesApiVerified',true)
    "#).unwrap();
    ticks(&mut p, &mut e, 12);
    assert_eq!(attributes(&e, find(&e, "Workspace")).get("EmotesApiVerified"), Some(&Variant::Bool(true)), "{:?}", e.logs);
}

#[test]
fn mobile_camera_and_variable_frames_preserve_journal_readers_and_stop() {
    let (_t, mut e) = setup();
    let before = rbx_xml::serialize(&e.dom).unwrap();
    let mut p = PlaySession::start(&mut e).unwrap();
    let rates = [120., 90., 60., 120., 30., 75., 144., 45.];
    for frame in 0..2400 {
        p.viewport(1280, 480);
        let heading = glam::Mat3::from_rotation_y(frame as f32 * 0.002);
        let direction = if frame % 360 < 240 { heading * glam::Vec3::NEG_Z } else { glam::Vec3::ZERO };
        p.player_input(direction, frame % 150 == 0);
        p.step(&mut e, Duration::from_secs_f64(1. / rates[frame % rates.len()])).unwrap();
        if let Some(position) = p.character.position(&e.dom) {
            let at = position + glam::Vec3::Y * 1.5;
            let offset = heading * glam::Vec3::new(0., 5., 16.);
            let distance = p.camera_distance(at, offset);
            p.host_camera(at + offset.normalize() * distance, at, 70.);
        }
        e.dom.take_changes();
    }
    assert!(p.character.position(&e.dom).is_some());
    p.stop(&mut e);
    assert_eq!(rbx_xml::serialize(&e.dom).unwrap(), before);
}
#[test]
fn original_session_runs_both_sides_and_round_trips_remote_events() {
    let (_t, mut e) = setup();
    e.run(r#"
 local r=Instance.new('RemoteEvent');r.Name='PortPing';r.Parent=game:GetService('ReplicatedStorage')
 local hidden=Instance.new('Folder');hidden.Name='ServerSecret';hidden.Parent=game:GetService('ServerStorage')
 local s=Instance.new('Script');s.Name='ServerTest';s.Source=[[
 assert(game:GetService('RunService'):IsServer())
 local r=game:GetService('ReplicatedStorage').PortPing
 r.OnServerEvent:Connect(function(player,value) r:SetAttribute('ServerReply',value+1);r:FireClient(player,value+2) end)
 ]];s.Parent=game:GetService('ServerScriptService')
 local starter=game:GetService('StarterPlayer');local scripts=starter:FindFirstChild('StarterPlayerScripts') or Instance.new('StarterPlayerScripts',starter)
 local c=Instance.new('LocalScript');c.Name='ClientTest';c.Source=[[
 assert(game:GetService('RunService'):IsClient());assert(game.Players.LocalPlayer.Name=='LocalPlayer')
 assert(not game:GetService('ServerStorage'):FindFirstChild('ServerSecret'))
 local r=game:GetService('ReplicatedStorage').PortPing
 r.OnClientEvent:Connect(function(value) workspace:SetAttribute('ClientReply',value) end);r:FireServer(40)
 ]];c.Parent=scripts
 "#).unwrap();
    let mut p = PlaySession::start(&mut e).unwrap();
    ticks(&mut p, &mut e, 10);
    assert_eq!(
        attributes(&e, find(&e, "PortPing")).get("ServerReply"),
        Some(&Variant::Float64(41.)),
        "{:?}",
        e.logs
    );
    assert_eq!(
        attributes(&e, find(&e, "Workspace")).get("ClientReply"),
        Some(&Variant::Float64(42.)),
        "{:?}",
        e.logs
    );
    assert!(p.started_scripts().0 >= 1 && p.started_scripts().1 >= 1);
    assert!(!e.snapshot()["tree"]
        .as_array()
        .unwrap()
        .iter()
        .any(|v| v["name"] == "ServerSecret"));
}
#[test]
fn original_session_respawns_and_stop_keeps_unknown_editor_data() {
    let (_t, mut e) = setup();
    let part = e.insert("Part").unwrap();
    e.dom
        .set_property(part, "FutureProperty", Variant::String("preserve".into()))
        .unwrap();
    let before = rbx_xml::serialize(&e.dom).unwrap();
    let mut p = PlaySession::start(&mut e).unwrap();
    let old = p.character.model;
    p.run_server("game.Players.RespawnTime=0.1;game.Players:GetPlayers()[1].Character:FindFirstChildOfClass('Humanoid').Health=0").unwrap();
    ticks(&mut p, &mut e, 120);
    assert_ne!(p.character.model, old, "{:?}", e.logs);
    assert!(p.character.position(&e.dom).is_some());
    p.stop(&mut e);
    assert_eq!(rbx_xml::serialize(&e.dom).unwrap(), before);
}
#[test]
fn runtime_color_size_tweens_and_camera_reach_original_renderer_properties_without_full_copy() {
    let (_t, mut e) = setup();
    let part = e.insert("Part").unwrap();
    e.edit(part, "Name", "VisualTest").unwrap();
    let mut p = PlaySession::start(&mut e).unwrap();
    ticks(&mut p, &mut e, 3);
    let full = p.full_syncs();
    p.run_client("workspace.VisualTest.Size=Vector3.new(7,8,9);workspace.VisualTest.Color=Color3.fromRGB(12,34,56);workspace.CurrentCamera.CameraType=Enum.CameraType.Scriptable;workspace.CurrentCamera.CFrame=CFrame.new(1,2,3)").unwrap();
    ticks(&mut p, &mut e, 1);
    let props = e.dom.get(part).unwrap().properties();
    assert_eq!(
        props.get("size"),
        Some(&Variant::Vector3(rbx_dom::Vector3Data {
            x: 7.,
            y: 8.,
            z: 9.
        }))
    );
    assert_eq!(
        props.get("Color3uint8"),
        Some(&Variant::Color3uint8 {
            r: 12,
            g: 34,
            b: 56
        })
    );
    assert!(
        p.scripted_camera()
            .unwrap()
            .cframe
            .position
            .distance(glam::Vec3::new(1., 2., 3.))
            < 0.001
    );
    assert_eq!(p.full_syncs(), full);
}
#[test]
fn imported_keyframes_use_original_animator_and_rook_joint_physics() {
    use rbx_animation::{Clip, Frame, Keyframe, PoseKey};
    let (_t, mut e) = setup();
    let storage = e
        .dom
        .root_refs()
        .iter()
        .copied()
        .find(|r| e.dom.get(*r).unwrap().class() == "ReplicatedStorage")
        .unwrap_or_else(|| {
            e.dom
                .new_instance("ReplicatedStorage", "ReplicatedStorage", None)
        });
    let mut clip = Clip {
        name: "FixtureRightArm".into(),
        ..Clip::default()
    };
    clip.looped = false;
    let mut a = Keyframe::at(0.);
    a.poses
        .insert("Right Arm".into(), PoseKey::new(Frame::IDENTITY));
    let mut b = Keyframe::at(1.);
    b.poses.insert(
        "Right Arm".into(),
        PoseKey::new(Frame {
            position: glam::Vec3::ZERO,
            rotation: glam::Quat::from_rotation_z(0.8),
        }),
    );
    clip.keyframes = vec![a, b];
    let seq = rbx_animation::sequence::write(&mut e.dom, Some(storage), &clip, None);
    let mut p = PlaySession::start(&mut e).unwrap();
    ticks(&mut p, &mut e, 3);
    let arm = e
        .dom
        .get(p.character.model)
        .unwrap()
        .children()
        .iter()
        .copied()
        .find(|r| e.dom.get(*r).unwrap().name() == "Right Arm")
        .unwrap();
    let before = e
        .dom
        .get(arm)
        .unwrap()
        .properties()
        .get("CFrame")
        .unwrap()
        .clone();
    p.play_sequence(&e, seq).unwrap();
    ticks(&mut p, &mut e, 30);
    let after = e.dom.get(arm).unwrap().properties().get("CFrame").unwrap();
    let (Variant::CFrame(a), Variant::CFrame(b)) = (&before, after) else {
        panic!("Missing joint transforms")
    };
    assert!(a
        .rotation
        .iter()
        .zip(b.rotation)
        .any(|(a, b)| (*a - b).abs() > 0.1));
    p.stop_animation();
    ticks(&mut p, &mut e, 2);
}
#[test]
fn actual_rook_vm_interrupts_non_yielding_scripts() {
    let (_t, mut e) = setup();
    let mut p = PlaySession::start(&mut e).unwrap();
    let start = std::time::Instant::now();
    p.run_server("while true do end").unwrap();
    ticks(&mut p, &mut e, 2);
    assert!(start.elapsed() < Duration::from_secs(2));
    assert!(
        e.logs
            .iter()
            .any(|s| s.contains("Script timeout: exhausted allowed execution time")),
        "{:?}",
        e.logs
    );
    assert!(p.character.position(&e.dom).is_some());
}
#[test]
fn implicit_rook_mesh_and_part_defaults_reach_the_real_render_extractor() {
    let (_t, mut e) = setup();
    let mut p = PlaySession::start(&mut e).unwrap();
    let head = e
        .dom
        .get(p.character.model)
        .unwrap()
        .children()
        .iter()
        .copied()
        .find(|r| e.dom.get(*r).unwrap().name() == "Head")
        .unwrap();
    let mesh = e
        .dom
        .get(head)
        .unwrap()
        .children()
        .iter()
        .copied()
        .find(|r| e.dom.get(*r).unwrap().class() == "SpecialMesh")
        .unwrap();
    assert_eq!(
        e.dom.get(mesh).unwrap().properties().get("MeshType"),
        Some(&Variant::Enum(0))
    );
    p.run_server(
        "local p=Instance.new('Part');p.Name='DefaultSize';p.Anchored=true;p.Parent=workspace",
    )
    .unwrap();
    ticks(&mut p, &mut e, 2);
    let props = e.dom.get(find(&e, "DefaultSize")).unwrap().properties();
    assert!(matches!(props.get("size"), Some(Variant::Vector3(_))));
    assert!(matches!(props.get("CFrame"), Some(Variant::CFrame(_))));
    assert!(matches!(
        props.get("Color3uint8"),
        Some(Variant::Color3uint8 { .. })
    ));
}
#[test]
fn original_client_gui_click_and_key_events_reach_luau_after_projection() {
    let (_t, mut e) = setup();
    e.run(r#"
 local screen=Instance.new('ScreenGui');screen.Name='InputFixture';screen.IgnoreGuiInset=true;screen.Parent=game:GetService('StarterGui')
 local button=Instance.new('TextButton');button.Name='FixtureButton';button.Position=UDim2.fromOffset(80,100);button.Size=UDim2.fromOffset(160,80);button.Parent=screen
 local code=Instance.new('LocalScript');code.Name='InputWatch';code.Source=[[
 script.Parent.MouseButton1Click:Connect(function() workspace:SetAttribute('GuiClicked',true) end)
 game:GetService('UserInputService').InputBegan:Connect(function(input)if input.KeyCode==Enum.KeyCode.W then workspace:SetAttribute('KeyBegan',true) end end)
 ]];code.Parent=button
 "#).unwrap();
    let mut p = PlaySession::start(&mut e).unwrap();
    p.viewport(800, 600);
    ticks(&mut p, &mut e, 3);
    p.click(160., 140.);
    p.key("W", true);
    p.key("W", false);
    ticks(&mut p, &mut e, 2);
    let a = attributes(&e, find(&e, "Workspace"));
    assert_eq!(
        a.get("GuiClicked"),
        Some(&Variant::Bool(true)),
        "{:?}",
        e.logs
    );
    assert_eq!(
        a.get("KeyBegan"),
        Some(&Variant::Bool(true)),
        "{:?}",
        e.logs
    );
    assert!(e.snapshot()["tree"]
        .as_array()
        .unwrap()
        .iter()
        .any(|v| v["name"] == "FixtureButton"));
    let screens = e.snapshot()["tree"]
        .as_array()
        .unwrap()
        .iter()
        .filter(|v| v["class"] == "ScreenGui" && v["name"] == "InputFixture")
        .map(|v| Ref::new(v["id"].as_u64().unwrap() as u32))
        .collect::<Vec<_>>();
    let runtime = screens
        .iter()
        .copied()
        .find(|r| {
            e.dom
                .parent(*r)
                .is_some_and(|p| e.dom.get(p).unwrap().class() == "PlayerGui")
        })
        .expect("Actual PlayerGui clone");
    assert!(
        rbx_viewer::shows_screen(
            &e.dom,
            &rbx_reflection::ReflectionDatabase::embedded(),
            runtime
        ),
        "The original render planner must include the runtime GUI"
    );
    for screen in screens {
        if e.dom
            .parent(screen)
            .is_some_and(|p| e.dom.get(p).unwrap().class() == "StarterGui")
        {
            assert_eq!(
                e.dom
                    .get(e.dom.parent(screen).unwrap())
                    .unwrap()
                    .properties()
                    .get("ShowDevelopmentGui"),
                Some(&Variant::Bool(false))
            );
        }
    }
}

#[test]
fn legacy_map_keeps_its_color_and_spawn_in_the_binary_play_snapshot() {
    use rook_engine::{DataModel, Options, session::Session};

    let folder = tempfile::tempdir().unwrap();
    let path = folder.path().join("map.rbxl");
    std::fs::write(&path, r#"<roblox version="4">
        <Item class="Workspace" referent="world">
            <Properties><string name="Name">Workspace</string></Properties>
            <Item class="Part" referent="map">
                <Properties>
                    <string name="Name">LegacyWall</string>
                    <int name="BrickColor">21</int>
                    <bool name="Anchored">true</bool>
                </Properties>
            </Item>
        </Item>
    </roblox>"#).unwrap();
    let mut editor = Editor::open_or_create(path.clone()).unwrap();
    let wall = find(&editor, "LegacyWall");
    let [r, g, b] = rbx_dom::BrickColor::from_number(21).unwrap().rgb;
    assert_eq!(
        editor.dom.get(wall).unwrap().properties().get("Color3uint8"),
        Some(&Variant::Color3uint8 { r, g, b }),
    );
    editor.save().unwrap();

    let options = Options::default();
    let mut server = DataModel::load_place(&std::fs::read(path).unwrap(), &options).unwrap();
    server.start();
    let mut session = Session::new(server, options);
    let client = session.join("Player1", 1).unwrap();
    for _ in 0..3 {
        session.step();
    }
    let world = session.client(client).unwrap().runtime().world();
    let dom = &world.dom;
    let workspace = dom.find_service("Workspace").unwrap();
    let wall = dom.find_first_child(workspace, "LegacyWall", false).unwrap();
    assert_eq!(dom.color3_property(wall, "Color").unwrap().to_rgb(), (r, g, b));
    let root = rook_lua::character_root(dom, dom.local_player().unwrap()).unwrap();
    assert!(dom.cframe_property(root, "CFrame").unwrap().position.is_finite());
}

#[test]
fn mobile_play_swings_stock_legs_without_editing_their_joint_transforms() {
    use rook_engine::{session::Session, DataModel, Options};
    use rook_types::CFrame;

    let (_folder, mut editor) = setup();
    editor.save().unwrap();
    let options = Options {
        touch_input: true,
        ..Options::default()
    };
    let mut server = DataModel::load_place(&std::fs::read(&editor.path).unwrap(), &options).unwrap();
    server.start();
    let mut session = Session::new(server, options);
    let player = session.join("MobilePlayer", 1).unwrap();
    session.run_for(0.8);

    let mut peak_speed = 0.0f32;
    let mut opposing_stride = 0.0f32;
    for _ in 0..50 {
        session.client(player).unwrap().runtime().lua().load(
            "game.Players.LocalPlayer.Character.Humanoid:Move(Vector3.new(1, 0, 0), false)",
        ).exec().unwrap();
        session.step();
        let world = session.client(player).unwrap().runtime().world();
        let dom = &world.dom;
        let character = dom.ref_property(dom.local_player().unwrap(), "Character").unwrap();
        let root = dom.find_first_child(character, "HumanoidRootPart", false).unwrap();
        let speed = dom.vector3_property(root, "AssemblyLinearVelocity").unwrap();
        peak_speed = peak_speed.max(speed.x.hypot(speed.z));

        let pitch = |r6, r15| {
            let part = dom.find_first_child(character, r6, false)
                .or_else(|| dom.find_first_child(character, r15, false)).unwrap();
            let base = dom.cframe_property(part, "CFrame").unwrap();
            let pose = dom.drawn_cframe(part).unwrap();
            (base.rotation.transpose() * pose.rotation).y_axis.z
        };
        let left = pitch("Left Leg", "LeftUpperLeg");
        let right = pitch("Right Leg", "RightUpperLeg");
        if left * right < 0.0 {
            opposing_stride = opposing_stride.max(left.abs().min(right.abs()));
        }
        assert!(dom.descendants(character).into_iter().filter(|&id| dom.class_name(id) == "Motor6D")
            .all(|id| dom.cframe_property(id, "Transform").unwrap_or_default() == CFrame::IDENTITY));
    }
    assert!(peak_speed > 2.0, "the test avatar never started walking: {peak_speed}");
    assert!(opposing_stride > 0.16, "the rendered legs did not oppose each other: {opposing_stride}");
}

#[test]
fn mobile_play_uses_the_asset_pose_when_the_default_clip_is_available() {
    use std::sync::Arc;

    use rook_content::{
        resolver::{cache::Cache, Config},
        ContentRef, Resolver,
    };
    use rook_engine::{session::Session, DataModel, Options};
    use rook_types::CFrame;

    let (folder, mut editor) = setup();
    editor.save().unwrap();
    let cache_dir = folder.path().join("content");
    let cache = Cache::open(cache_dir.clone()).unwrap();
    let clip = r#"<roblox version="4">
    <Item class="KeyframeSequence"><Properties><string name="Name">Idle</string></Properties>
      <Item class="Keyframe"><Properties><float name="Time">1</float></Properties>
        <Item class="Pose"><Properties>
          <string name="Name">Left Arm</string><float name="Weight">1</float>
          <CoordinateFrame name="CFrame"><X>0.5</X><Y>0</Y><Z>0</Z>
          <R00>1</R00><R01>0</R01><R02>0</R02>
          <R10>0</R10><R11>1</R11><R12>0</R12>
          <R20>0</R20><R21>0</R21><R22>1</R22></CoordinateFrame>
        </Properties></Item>
      </Item>
    </Item></roblox>"#;
    let reference = ContentRef::parse("rbxassetid://180435571").unwrap();
    cache
        .put(&reference.cache_key().unwrap(), clip.as_bytes(), "fixture")
        .unwrap();
    let options = Options {
        touch_input: true,
        content: Some(Arc::new(Resolver::new(Config {
            cache_dir: Some(cache_dir),
            ..Config::empty()
        }))),
        ..Options::default()
    };
    let mut server = DataModel::load_place(&std::fs::read(&editor.path).unwrap(), &options).unwrap();
    server.start();
    let mut session = Session::new(server, options);
    let player = session.join("MobilePlayer", 1).unwrap();
    session.run_for(0.6);
    let expected_shift = CFrame::from_position(glam::Vec3::new(0.5, 0.0, 0.0));
    let mut error = f32::INFINITY;
    for _ in 0..100 {
        session.step();
        {
            let world = session.client(player).unwrap().runtime().world();
            let dom = &world.dom;
            let character = dom
                .ref_property(dom.local_player().unwrap(), "Character")
                .unwrap();
            let torso = dom.find_first_child(character, "Torso", false).unwrap();
            let arm = dom.find_first_child(character, "Left Arm", false).unwrap();
            let shoulder = dom.find_first_child(torso, "Left Shoulder", false).unwrap();
            let bind = dom.cframe_property(shoulder, "C0").unwrap();
            let base = dom.cframe_property(torso, "CFrame").unwrap().inverse()
                * dom.cframe_property(arm, "CFrame").unwrap();
            let target = bind * expected_shift * bind.inverse() * base;
            let shown = dom.drawn_cframe(torso).unwrap().inverse() * dom.drawn_cframe(arm).unwrap();
            error = (shown.position - target.position).length();
            if error < 0.025 {
                assert_eq!(
                    dom.cframe_property(shoulder, "Transform").unwrap_or_default(),
                    CFrame::IDENTITY
                );
                break;
            }
        }
        std::thread::sleep(Duration::from_millis(3));
    }
    assert!(error < 0.025, "did not reach the cached keyframe pose: {error}");
}

#[test]
fn play_follows_a_decal_asset_to_its_image() {
    use std::sync::Arc;

    use rook_content::{
        resolver::{cache::Cache, Config},
        ContentRef, Loader, Resolver, Want,
    };

    let folder = tempfile::tempdir().unwrap();
    let cache = Cache::open(folder.path()).unwrap();
    let decal = ContentRef::parse("rbxassetid://123450011").unwrap();
    let image = ContentRef::parse("rbxassetid://123450012").unwrap();
    let model = r#"<roblox version="4"><Item class="Decal"><Properties>
        <Content name="Texture"><url>rbxassetid://123450012</url></Content>
    </Properties></Item></roblox>"#;
    cache
        .put(&decal.cache_key().unwrap(), model.as_bytes(), "fixture-decal")
        .unwrap();
    let mut pixels = Vec::new();
    {
        let mut encoder = png::Encoder::new(&mut pixels, 1, 1);
        encoder.set_color(png::ColorType::Rgba);
        let mut writer = encoder.write_header().unwrap();
        writer.write_image_data(&[30, 90, 150, 255]).unwrap();
    }
    cache
        .put(&image.cache_key().unwrap(), &pixels, "fixture-image")
        .unwrap();
    let resolver = Arc::new(Resolver::new(Config {
        cache_dir: Some(folder.path().to_owned()),
        ..Config::empty()
    }));
    let mut loader = Loader::new(resolver);
    let wanted = loader.request_as(decal, Want::Image);
    let mut results = loader.wait_all();
    let (ticket, outcome) = results.pop().unwrap();
    assert_eq!(ticket, wanted);
    let image = outcome.unwrap().image.unwrap();
    assert_eq!((image.width, image.height), (1, 1));
}
