use std::sync::{Arc, OnceLock};

use rook_engine::{session::Session, DataModel, InputEvent, Options, TouchPhase};
use rook_lua::MessageType;
use rook_types::Value;

fn install_test_platform() {
    static PLATFORM: OnceLock<()> = OnceLock::new();
    PLATFORM.get_or_init(|| {
        let temp = tempfile::tempdir().unwrap();
        let mut editor =
            rbx_android_core::Editor::open_or_create(temp.path().join("host.rbxlx")).unwrap();
        let play = rbx_android_core::PlaySession::start(&mut editor).unwrap();
        play.stop(&mut editor);
    });
}

fn start(options: Options, source: &str) -> Session {
    install_test_platform();
    let mut server = DataModel::new(&options).unwrap();
    server.run_extra_script(source).unwrap();
    server.start();
    let mut session = Session::new(server, options);
    session.join("MobileFixture", 1).unwrap();
    session.run_for(0.2);
    session
}

fn session(server_source: &str) -> Session {
    start(
        Options {
            touch_input: true,
            initial_viewport: Some((960, 480)),
            ..Options::default()
        },
        server_source,
    )
}

fn client(session: &Session) -> &DataModel {
    session.client(0).unwrap()
}

fn check_logs(session: &Session) {
    for dm in [session.server(), client(session)] {
        let errors: Vec<_> = dm
            .take_log()
            .into_iter()
            .filter(|line| line.kind == MessageType::Error)
            .collect();
        assert!(errors.is_empty(), "{errors:?}");
    }
}

fn server_attribute(session: &Session, object: &str, name: &str) -> f64 {
    let w = session.server().runtime().world();
    let workspace = w.dom.find_service("Workspace").unwrap();
    let id = w.dom.find_first_child(workspace, object, false).unwrap();
    match w.dom.attribute(id, name) {
        Some(Value::Float64(n)) => *n,
        None => 0.,
        other => panic!("Unexpected attribute {other:?}"),
    }
}

fn server_character(session: &Session) -> rook_dom::InstanceId {
    let player = session.player(0).unwrap();
    session
        .server()
        .runtime()
        .world()
        .dom
        .ref_property(player, "Character")
        .unwrap()
}

#[test]
fn obby_climbs_faces_the_truss_and_pushes_away_with_jump_held() {
    let mut session = session(
        r#"
        local floor=Instance.new('Part', workspace)
        floor.Anchored=true
        floor.Size=Vector3.new(80,1,80)
        floor.Position=Vector3.new(0,-0.5,0)
        local truss=Instance.new('TrussPart', workspace)
        truss.Anchored=true
        truss.Size=Vector3.new(2,28,2)
        truss.Position=Vector3.new(0,14,-7)
        local platform=Instance.new('Part', workspace)
        platform.Anchored=true
        platform.Size=Vector3.new(8,1,8)
        platform.Position=Vector3.new(0,28,-11)
    "#,
    );
    client(&session)
        .run_extra_script(
            r#"
        local c=game.Players.LocalPlayer.Character
        c:PivotTo(CFrame.new(0,3,0) * CFrame.Angles(0,math.rad(90),0))
        c.Humanoid:Move(Vector3.new(0,0,-1))
    "#,
        )
        .unwrap();
    session.run_for(0.9);
    let observe = |session: &Session| {
        let w = client(session).runtime().world();
        let c = w
            .dom
            .ref_property(w.dom.local_player().unwrap(), "Character")
            .unwrap();
        let h = w.dom.find_first_child_of_class(c, "Humanoid").unwrap();
        let r = rook_lua::humanoid_root(&w.dom, h).unwrap();
        (
            w.physics.humanoid_state(h).unwrap(),
            w.dom.cframe_property(r, "CFrame").unwrap(),
        )
    };
    use rook_physics::humanoid::State;
    let (state, before) = observe(&session);
    assert_eq!(state, State::Climbing, "{before:?}");
    assert!(before.position.y > 6.0, "{before:?}");
    assert!((before.rotation * glam::Vec3::NEG_Z).dot(glam::Vec3::NEG_Z) > 0.98);
    // Keep pressing into the ladder while holding jump, as with two fingers.
    for _ in 0..8 {
        client(&session)
            .run_extra_script("game.Players.LocalPlayer.Character.Humanoid.Jump=true")
            .unwrap();
        session.step();
        assert_ne!(observe(&session).0, State::Climbing);
    }
    let (state, after) = observe(&session);
    assert_eq!(state, State::Freefall);
    assert!(
        after.position.z > before.position.z + 0.6,
        "{before:?} -> {after:?}"
    );
    assert!(after.position.y > before.position.y + 1.0);
    check_logs(&session);
}

#[test]
fn mobile_taps_reach_game_tools_and_detectors_and_swipes_scroll_gui() {
    let mut session = session(
        r#"
        local target=Instance.new('Part',workspace)
        target.Name='TouchTarget'
        target.Anchored=true
        target.Size=Vector3.new(6,6,2)
        target.Position=Vector3.new(0,6,-8)
        local click=Instance.new('ClickDetector',target)
        click.MaxActivationDistance=100
        click.MouseClick:Connect(function(player)
            assert(player==game.Players:GetPlayers()[1])
            target:SetAttribute('Clicks',(target:GetAttribute('Clicks') or 0)+1)
        end)
        local tool=Instance.new('Tool',game:GetService('StarterPack'))
        tool.Name='TapTool'
        tool.RequiresHandle=false
        local handler=Instance.new('Script',tool)
        handler.Source=[[
            local tool=script.Parent
            local t=workspace.TouchTarget
            tool.Activated:Connect(function() t:SetAttribute('Uses',(t:GetAttribute('Uses') or 0)+1) end)
            tool.Deactivated:Connect(function() t:SetAttribute('Releases',(t:GetAttribute('Releases') or 0)+1) end)
        ]]
    "#,
    );
    client(&session)
        .run_extra_script(
            r#"
        local player=game.Players.LocalPlayer
        local c=player.Character
        c:PivotTo(CFrame.new(10,6,0))
        c.Humanoid:EquipTool(player.Backpack.TapTool)
        local cam=workspace.CurrentCamera
        cam.CameraType=Enum.CameraType.Scriptable
        cam.CFrame=CFrame.lookAt(Vector3.new(0,6,0),Vector3.new(0,6,-8))
        local gui=Instance.new('ScreenGui',player.PlayerGui)
        gui.IgnoreGuiInset=true
        local list=Instance.new('ScrollingFrame',gui)
        list.Name='TouchList'
        list.Size=UDim2.fromOffset(200,170)
        list.CanvasSize=UDim2.fromOffset(200,800)
        local button=Instance.new('TextButton',list)
        button.Size=UDim2.fromOffset(180,160)
        button.MouseButton1Click:Connect(function()
            gui:SetAttribute('Click', (gui:GetAttribute('Click') or 0)+1)
        end)
        button.Activated:Connect(function(input)
            assert(input.UserInputType==Enum.UserInputType.Touch)
            gui:SetAttribute('Activated', (gui:GetAttribute('Activated') or 0)+1)
        end)
        game:GetService('UserInputService').TouchTapInWorld:Connect(function(at, processed)
            assert(typeof(at)=='Vector2' and not processed)
            gui:SetAttribute('WorldTap',(gui:GetAttribute('WorldTap') or 0)+1)
        end)
    "#,
        )
        .unwrap();
    session.run_for(0.1);
    let mut touch = |id, phase, x, y| session.input(0, InputEvent::Touch { id, phase, x, y });
    touch(1, TouchPhase::Begin, 480., 240.);
    touch(1, TouchPhase::End, 480., 240.);
    touch(2, TouchPhase::Begin, 50., 120.);
    touch(2, TouchPhase::End, 50., 120.);
    touch(3, TouchPhase::Begin, 60., 130.);
    touch(3, TouchPhase::Move, 60., 60.);
    touch(3, TouchPhase::End, 60., 60.);
    touch(4, TouchPhase::Begin, 480., 240.);
    touch(5, TouchPhase::Begin, 540., 240.);
    touch(4, TouchPhase::End, 480., 240.);
    touch(5, TouchPhase::Cancel, 540., 240.);
    session.run_for(0.15);
    assert_eq!(server_attribute(&session, "TouchTarget", "Clicks"), 1.);
    assert_eq!(server_attribute(&session, "TouchTarget", "Uses"), 1.);
    assert_eq!(server_attribute(&session, "TouchTarget", "Releases"), 1.);
    client(&session)
        .run_extra_script(
            r#"
        local gui=game.Players.LocalPlayer.PlayerGui:FindFirstChildOfClass('ScreenGui')
        assert(gui:GetAttribute('Click')==1)
        assert(gui:GetAttribute('Activated')==1)
        assert(gui:GetAttribute('WorldTap')==1)
        assert(gui.TouchList.CanvasPosition.Y>=60)
    "#,
        )
        .unwrap();
    check_logs(&session);
}

fn core_script(dm: &DataModel, source: &str) {
    let rt = dm.runtime();
    let id = {
        let mut w = rt.world_mut();
        let id = w.dom.create_by_name("CoreScript").unwrap();
        w.bytecode.insert(
            id,
            rook_lua::Compiled {
                code: mlua::chunk::Compiler::new().compile(source).unwrap().into(),
                core: true,
            },
        );
        id
    };
    assert!(rt.run_script(id));
}

#[test]
fn core_reset_respects_authority_and_only_leave_requests_shutdown() {
    let mut session = session("game.Players.RespawnTime=0.05");
    let before = server_character(&session);
    client(&session)
        .run_extra_script(
            r#"
        assert(not pcall(function() game:Shutdown() end))
        game.Players.LocalPlayer.Character.Humanoid.Health=0
    "#,
        )
        .unwrap();
    session.run_for(0.2);
    assert_eq!(server_character(&session), before);
    assert!(!client(&session).runtime().world().shutdown_requested);

    core_script(
        client(&session),
        r#"
        local g=game:GetService('GuiService')
        g:SetMenuIsOpen(true, 'Test')
        assert(g.MenuIsOpen)
        g:SetMenuIsOpen(false, 'Test')
        assert(not g.MenuIsOpen)
        game.Players.LocalPlayer.Character.Humanoid.Health=0
    "#,
    );
    session.run_for(0.25);
    assert_ne!(server_character(&session), before);
    assert!(!client(&session).runtime().world().shutdown_requested);
    core_script(client(&session), "game:Shutdown()");
    assert!(client(&session).runtime().world().shutdown_requested);
    check_logs(&session);
}

#[test]
fn nested_attachments_share_positions_between_scripts_and_effect_scene() {
    install_test_platform();
    let temp = tempfile::tempdir().unwrap();
    let resources =
        rbx_android_core::resources::StudioResources::new(temp.path().into(), vec![], false);
    let content = resources.resolver();
    let mut dm = DataModel::new(&Options::default()).unwrap();
    dm.run_extra_script(
        r#"
        local part=Instance.new('Part',workspace)
        part.Anchored=true
        part.CFrame=CFrame.new(15,10,5)*CFrame.Angles(0,math.pi/2,0)
        local a=Instance.new('Attachment',part)
        a.Position=Vector3.new(1,2,3)
        local b=Instance.new('Attachment',a)
        b.Position=Vector3.new(0,4,0)
        assert((b.WorldPosition-Vector3.new(18,16,4)).Magnitude<0.001)
        local emitter=Instance.new('ParticleEmitter',b)
        emitter.Enabled=false
        emitter:Emit(7)
        local beam=Instance.new('Beam',part)
        beam.Attachment0=a
        beam.Attachment1=b
        beam.CurveSize0=4
        local trail=Instance.new('Trail',part)
        trail.Attachment0=a
        trail.Attachment1=b
    "#,
    )
    .unwrap();
    dm.start();
    dm.run_for(0.05);
    let scene = rook_view::SceneCache::for_data_model(&mut dm, &content, glam::Vec3::ZERO).scene;
    assert_eq!(scene.emitters.len(), 1);
    assert_eq!(scene.emitters[0].emitted, 7);
    assert!(
        scene.emitters[0]
            .position
            .distance(glam::Vec3::new(18., 16., 4.))
            < 0.001
    );
    assert!(scene.beams[0].enabled);
    assert_eq!(scene.beams[0].curve[0], 4.);
    assert!(scene.trails[0].enabled);
    assert_eq!(scene.trails[0].ends[1], scene.emitters[0].position);
    assert!(!dm
        .take_log()
        .iter()
        .any(|line| line.kind == MessageType::Error));
}

#[test]
#[ignore = "requires a Vulkan or GL adapter for offscreen rendering"]
fn renders_textured_luau_particles_against_a_six_face_sky() {
    fn png(path: &std::path::Path, width: u32, height: u32, pixels: &[u8]) {
        let mut encoder = png::Encoder::new(std::fs::File::create(path).unwrap(), width, height);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        encoder
            .write_header()
            .unwrap()
            .write_image_data(pixels)
            .unwrap();
    }
    install_test_platform();
    let temp = tempfile::tempdir().unwrap();
    let path = temp.path().join("studio-packages/textures");
    std::fs::create_dir_all(&path).unwrap();
    for (index, face) in ["Up", "Bk", "Ft", "Lf", "Rt", "Dn"].into_iter().enumerate() {
        png(
            &path.join(format!("face-{face}.png")),
            8,
            8,
            &[20, 40 + index as u8 * 8, 160, 255].repeat(64),
        );
    }
    let curl: Vec<_> = (0..32)
        .flat_map(|y| {
            (0..32).flat_map(move |x| {
                let r = (x as f32 - 16.).hypot(y as f32 - 16.);
                [
                    255,
                    32,
                    240,
                    if (6.0..=13.0).contains(&r) && (x > 14 || y < 16) {
                        255
                    } else {
                        0
                    },
                ]
            })
        })
        .collect();
    png(&path.join("curl.png"), 32, 32, &curl);
    let resources =
        rbx_android_core::resources::StudioResources::new(temp.path().into(), vec![], false);
    let content = resources.resolver();
    let mut dm = DataModel::new(&Options {
        content: Some(content.clone()),
        ..Default::default()
    })
    .unwrap();
    dm.run_extra_script(
        r#"
        local sky=Instance.new('Sky', game:GetService('Lighting'))
        for _, face in {'Up','Bk','Ft','Lf','Rt','Dn'} do
            sky['Skybox'..face]='rbxasset://textures/face-'..face..'.png'
        end
        sky.CelestialBodiesShown=false
        local part=Instance.new('Part',workspace)
        part.CFrame=CFrame.new(0,6,0)
        part.Anchored=true
        part.Transparency=1
        local a=Instance.new('Attachment',part)
        local b=Instance.new('Attachment',a)
        local emitter=Instance.new('ParticleEmitter',b)
        emitter.Texture='rbxasset://textures/curl.png'
        emitter.Enabled=false
        emitter.Lifetime=NumberRange.new(3)
        emitter.Speed=NumberRange.new(0)
        emitter.Size=NumberSequence.new(2)
        emitter.Transparency=NumberSequence.new(0)
        emitter.LightInfluence=0
        emitter:Emit(1)
    "#,
    )
    .unwrap();
    dm.start();
    dm.run_for(0.05);
    let camera =
        rook_render::Camera::look_at(glam::Vec3::new(0., 6., 8.), glam::Vec3::new(0., 6., 0.));
    let mut scene = rook_view::SceneCache::for_data_model(&mut dm, &content, camera.position);
    scene.load_all(&dm, camera.position);
    assert!(scene.scene.lighting.skybox.is_some());
    assert!(scene.scene.emitters[0].look.texture.is_some());
    let mut renderer = rook_render::Renderer::headless().unwrap();
    renderer.set_scene(&scene.scene);
    renderer.finish_uploads();
    renderer.settle_particles(0.25);
    let pixels = renderer.render_to_image(640, 360, &camera).unwrap();
    let wind = pixels
        .chunks_exact(4)
        .filter(|p| p[0] > 160 && p[1] < 100 && p[2] > 150)
        .count();
    let sky = pixels
        .chunks_exact(4)
        .filter(|p| p[0] < 90 && p[2] > 100 && p[2] > p[1])
        .count();
    println!("{} | wind pixels {wind} | sky pixels {sky}", renderer.gpu());
    assert!(wind > 500 && sky > 150_000);
    if let Some(path) = std::env::var_os("RBX_MOBILE_EFFECT_CAPTURE") {
        png(path.as_ref(), 640, 360, &pixels);
    }
}

#[test]
#[ignore = "requires the official Studio CDN DataModelPatch and cached fonts"]
fn original_pause_menu_touches_resume_respawn_settings_and_leave() {
    install_test_platform();
    let path = std::env::var_os("RBX_MOBILE_OFFICIAL_COREPATCH").expect("DataModelPatch.rbxm path");
    let root = std::env::var_os("RBX_MOBILE_OFFICIAL_CONTENT").expect("Studio resource cache path");
    let settings_dir = tempfile::tempdir().unwrap();
    let settings_path = settings_dir.path().join("GlobalBasicSettings.xml");
    let patch = rook_engine::core_scripts::CorePatch::load(path.as_ref(), None).unwrap();
    let content =
        rbx_android_core::resources::StudioResources::new(root.into(), vec![], false).resolver();
    let mut session = start(
        Options {
            content: Some(content),
            core_scripts: Some(Arc::new(patch)),
            touch_input: true,
            initial_viewport: Some((1920, 864)),
            user_settings: Some(settings_path.clone()),
            ..Options::default()
        },
        "game.Players.RespawnTime=0.1",
    );
    session.run_for(1.5);
    let open = |session: &mut Session| {
        for down in [true, false] {
            session.input(
                0,
                InputEvent::Key {
                    code: "Escape".into(),
                    down,
                },
            );
        }
        session.run_for(0.5);
    };
    let tap = |session: &mut Session, name: &str| {
        let at = {
            let w = client(session).runtime().world();
            let core = w.dom.find_service("CoreGui").unwrap();
            let suffix = format!(".{name}");
            w.dom
                .descendants(core)
                .into_iter()
                .filter(|&id| w.dom.is_a(id, "GuiButton") && w.dom.full_name(id).ends_with(&suffix))
                .find_map(|id| {
                    let at = w.dom.vector2_property(id, "AbsolutePosition")?.0;
                    let size = w.dom.vector2_property(id, "AbsoluteSize")?.0;
                    let at = at + size * 0.5 + glam::Vec2::new(0., 58.);
                    (size.x > 5. && size.y > 5. && (0.0..864.0).contains(&at.y)).then_some(at)
                })
                .unwrap_or_else(|| panic!("Visible button {name} missing"))
        };
        for phase in [TouchPhase::Begin, TouchPhase::End] {
            session.input(
                0,
                InputEvent::Touch {
                    id: 49,
                    phase,
                    x: at.x,
                    y: at.y,
                },
            );
            session.step();
        }
        session.run_for(0.9);
    };
    let first = server_character(&session);
    open(&mut session);
    assert!(client(&session).runtime().menu_is_open());
    tap(&mut session, "ResumeButtonButton");
    assert!(!client(&session).runtime().menu_is_open());
    assert_eq!(server_character(&session), first);
    open(&mut session);
    tap(&mut session, "ResetCharacterButtonButton");
    assert_eq!(
        server_character(&session),
        first,
        "Reset needs confirmation"
    );
    tap(&mut session, "ResetCharacterButton");
    assert_ne!(
        server_character(&session),
        first,
        "Confirmed reset did not respawn"
    );
    open(&mut session);
    tap(&mut session, "GameSettingsTab");
    tap(&mut session, "Graphics ModeFrame.Selector.RightButton");
    tap(&mut session, "Graphics QualityFrame.Slider.StepsContainer.Step6");
    assert_eq!(client(&session).player_settings().unwrap().graphics_quality, 6);
    tap(&mut session, "HelpTab");
    tap(&mut session, "LeaveGameButtonButton");
    let is_leaving = |session: &Session| client(session).runtime().world().shutdown_requested;
    assert!(!is_leaving(&session), "Leave needs confirmation");
    tap(&mut session, "LeaveButtonContainer.DontLeaveGameButton");
    assert!(!is_leaving(&session), "Cancel must keep the game running");
    tap(&mut session, "LeaveGameButtonButton");
    tap(&mut session, "LeaveButtonContainer.LeaveGameButton");
    assert!(is_leaving(&session), "Confirmed leave did not request shutdown");
    session.client_mut(0).unwrap().save_user_settings();
    let saved = rook_engine::user_settings::PlayerSettings::load(Some(&settings_path));
    assert_eq!(saved.graphics_quality, 6);
    check_logs(&session);
}

fn aim_mobile_fixture(session: &mut Session) {
    client(session).run_extra_script(
        r#"
        game.Players.LocalPlayer.Character:PivotTo(CFrame.new(0,6,0))
        local camera=workspace.CurrentCamera
        camera.CameraType=Enum.CameraType.Scriptable
        camera.CFrame=CFrame.lookAt(Vector3.new(0,6,0),Vector3.new(0,6,-9))
        "#,
    ).unwrap();
    session.run_for(0.15);
}

fn touch_at(session: &mut Session, id: u64, phase: TouchPhase, x: f32, y: f32) {
    session.input(0, InputEvent::Touch { id, phase, x, y });
}

#[test]
fn custom_prompt_touch_hold_reaches_local_and_server_scripts() {
    let mut session = session(r#"
        workspace.Gravity=0
        local pad=Instance.new('Part',workspace)
        pad.Name='PromptPad'
        pad.Anchored=true
        pad.Size=Vector3.new(2,2,2)
        pad.Position=Vector3.new(0,6,-8)
        local prompt=Instance.new('ProximityPrompt',Instance.new('Attachment',pad))
        prompt.Style=Enum.ProximityPromptStyle.Custom
        prompt.HoldDuration=0.30
        prompt.MaxActivationDistance=14
        prompt.RequiresLineOfSight=true
        prompt.Enabled=false
        local function add(name)
            pad:SetAttribute(name,(pad:GetAttribute(name) or 0)+1)
        end
        prompt.Triggered:Connect(function(player)
            assert(player==game.Players:GetPlayers()[1])
            add('Triggered')
        end)
        prompt.PromptButtonHoldBegan:Connect(function() add('Began') end)
        prompt.PromptButtonHoldEnded:Connect(function() add('Ended') end)
        prompt.TriggerEnded:Connect(function() add('Released') end)
        game:GetService('ProximityPromptService').PromptTriggered:Connect(function(p,player)
            assert(p==prompt and player==game.Players:GetPlayers()[1])
            add('ServiceTriggered')
        end)
    "#);
    aim_mobile_fixture(&mut session);
    client(&session).run_extra_script(r#"
        local player=game.Players.LocalPlayer
        local prompt=workspace.PromptPad.Attachment.ProximityPrompt
        local gui=Instance.new('ScreenGui',player.PlayerGui)
        gui.IgnoreGuiInset=true
        local button=Instance.new('TextButton',gui)
        button.Active=true
        button.Size=UDim2.fromOffset(160,100)
        button.Position=UDim2.fromOffset(20,150)
        button.InputBegan:Connect(function(i)
            if i.UserInputType==Enum.UserInputType.Touch then prompt:InputHoldBegin() end
        end)
        button.InputEnded:Connect(function(i)
            if i.UserInputType==Enum.UserInputType.Touch then prompt:InputHoldEnd() end
        end)
        local service=game:GetService('ProximityPromptService')
        service.PromptShown:Connect(function(p,input)
            assert(p==prompt and input==Enum.ProximityPromptInputType.Touch)
            gui:SetAttribute('Shown',true)
        end)
        service.PromptHidden:Connect(function(p)
            assert(p==prompt)
            gui:SetAttribute('Hidden',true)
        end)
        prompt.Triggered:Connect(function(p)
            assert(p==player)
            gui:SetAttribute('LocalTrigger',true)
        end)
        shared.PromptTestGui=gui
    "#).unwrap();
    session.server().run_extra_script("workspace.PromptPad.Attachment.ProximityPrompt.Enabled=true").unwrap();
    session.run_for(0.15);
    let touch = |session: &mut Session, phase| touch_at(session, 90, phase, 70.0, 205.0);
    touch(&mut session, TouchPhase::Begin);
    session.run_for(0.1);
    touch(&mut session, TouchPhase::End);
    session.run_for(0.35);
    assert_eq!(server_attribute(&session, "PromptPad", "Began"), 1.0);
    assert_eq!(server_attribute(&session, "PromptPad", "Ended"), 1.0);
    assert_eq!(server_attribute(&session, "PromptPad", "Triggered"), 0.0);
    touch(&mut session, TouchPhase::Begin);
    session.run_for(0.45);
    assert_eq!(server_attribute(&session, "PromptPad", "Triggered"), 1.0);
    assert_eq!(server_attribute(&session, "PromptPad", "ServiceTriggered"), 1.0);
    assert_eq!(server_attribute(&session, "PromptPad", "Released"), 0.0);
    touch(&mut session, TouchPhase::End);
    session.run_for(0.1);
    assert_eq!(server_attribute(&session, "PromptPad", "Released"), 1.0);

    session.server().run_extra_script(
        "local wall=Instance.new('Part',workspace); wall.Anchored=true; wall.Size=Vector3.new(10,10,1); wall.Position=Vector3.new(0,6,-4)",
    ).unwrap();
    session.run_for(0.15);
    client(&session).run_extra_script(r#"
        local g=shared.PromptTestGui
        assert(g:GetAttribute('Shown'),'no PromptShown')
        assert(g:GetAttribute('LocalTrigger'),'no local Triggered')
        assert(g:GetAttribute('Hidden'),'no PromptHidden behind wall')
    "#).unwrap();
    touch(&mut session, TouchPhase::Begin);
    session.run_for(0.45);
    touch(&mut session, TouchPhase::Cancel);
    assert_eq!(server_attribute(&session, "PromptPad", "Triggered"), 1.0);
    check_logs(&session);
}

#[test]
fn equipped_handle_follows_the_hand_and_touch_hold_releases_on_cancel() {
    let mut session = session(r#"
        workspace.Gravity=0
        local counts=Instance.new('Folder',workspace)
        counts.Name='ToolCounts'
        local tool=Instance.new('Tool',game:GetService('StarterPack'))
        tool.Name='Drill'
        tool.Grip=CFrame.new(0.15,-0.1,0.2)
        local handle=Instance.new('Part',tool)
        handle.Name='Handle'
        handle.Size=Vector3.new(.4,.9,.4)
        handle.CanCollide=false
        handle.Massless=true
        local scriptCopy=Instance.new('Script',tool)
        scriptCopy.Source=[[
            local counts=workspace.ToolCounts
            local tool=script.Parent
            tool.Activated:Connect(function()
                counts:SetAttribute('Activated',(counts:GetAttribute('Activated') or 0)+1)
            end)
            tool.Deactivated:Connect(function()
                counts:SetAttribute('Deactivated',(counts:GetAttribute('Deactivated') or 0)+1)
            end)
        ]]
    "#);
    client(&session).run_extra_script(
        "local p=game.Players.LocalPlayer; p.Character.Humanoid:EquipTool(p.Backpack.Drill)",
    ).unwrap();
    session.run_for(0.25);
    let grip_check = r#"
        local character=game.Players:GetPlayers()[1].Character
        local hand=character:FindFirstChild('RightHand') or character['Right Arm']
        local grip=assert(hand:FindFirstChild('RightGrip'))
        local handle=character.Drill.Handle
        assert(grip.Part0==hand and grip.Part1==handle)
        assert((hand.CFrame * grip.C0).Position:FuzzyEq((handle.CFrame * grip.C1).Position,0.1))
    "#;
    session.server().run_extra_script(grip_check).unwrap();
    client(&session).run_extra_script(grip_check).unwrap();
    {
        let w = client(&session).runtime().world();
        let dom = &w.dom;
        let character = dom.ref_property(dom.local_player().unwrap(), "Character").unwrap();
        let hand = dom.find_first_child(character, "RightHand", false)
            .or_else(|| dom.find_first_child(character, "Right Arm", false)).unwrap();
        let tool = dom.find_first_child(character, "Drill", false).unwrap();
        let handle = dom.find_first_child(tool, "Handle", false).unwrap();
        let grip = dom.find_first_child(hand, "RightGrip", false).unwrap();
        let hand_pose = dom.drawn_cframe(hand).unwrap() * dom.cframe_property(grip, "C0").unwrap();
        let handle_pose = dom.drawn_cframe(handle).unwrap() * dom.cframe_property(grip, "C1").unwrap();
        assert!(hand_pose.position.distance(handle_pose.position) < 0.1, "drawn tool lagged behind the hand");
    }
    touch_at(&mut session, 95, TouchPhase::Begin, 650.0, 180.0);
    session.run_for(0.25);
    assert_eq!(server_attribute(&session, "ToolCounts", "Activated"), 1.0);
    assert_eq!(server_attribute(&session, "ToolCounts", "Deactivated"), 0.0);
    session.input(0, InputEvent::CancelTouches);
    session.run_for(0.1);
    assert_eq!(server_attribute(&session, "ToolCounts", "Deactivated"), 1.0);
    client(&session).run_extra_script("game.Players.LocalPlayer.Character.Drill.Parent=workspace").unwrap();
    session.run_for(0.16);
    session.server().run_extra_script(r#"
        local c=game.Players:GetPlayers()[1].Character
        assert(workspace:FindFirstChild('Drill') and not c:FindFirstChild('RightGrip',true))
        local handle=workspace.Drill.Handle
        handle.CFrame=CFrame.new(35,15,0)
        handle.AssemblyLinearVelocity=Vector3.zero
    "#).unwrap();
    session.run_for(0.6);
    session.server().run_extra_script(
        "local p=game.Players:GetPlayers()[1]; workspace.Drill.Handle.CFrame=p.Character.HumanoidRootPart.CFrame",
    ).unwrap();
    session.run_for(0.15);
    session.server().run_extra_script(
        "assert(game.Players:GetPlayers()[1].Backpack:FindFirstChild('Drill'),'Touched Handle did not enter Backpack')",
    ).unwrap();
    check_logs(&session);
}

fn server_part_position(session: &Session, name: &str) -> glam::Vec3 {
    let w = session.server().runtime().world();
    let workspace = w.dom.find_service("Workspace").unwrap();
    let part = w.dom.find_first_child(workspace, name, false).unwrap();
    w.dom.cframe_property(part, "CFrame").unwrap().position
}

#[test]
fn mobile_drag_moves_anchored_part_with_limits_and_fires_server_events() {
    let mut session = session(r#"
        workspace.Gravity=0
        local crate=Instance.new('Part',workspace)
        crate.Name='DragCrate'
        crate.Anchored=true
        crate.Size=Vector3.new(4,4,2)
        crate.Position=Vector3.new(0,6,-9)
        local drag=Instance.new('DragDetector',crate)
        drag.DragStyle=Enum.DragDetectorDragStyle.TranslateViewPlane
        drag.ResponseStyle=Enum.DragDetectorResponseStyle.Geometric
        drag.MaxActivationDistance=20
        drag.MinDragTranslation=Vector3.new(-2,0,0)
        drag.MaxDragTranslation=Vector3.new(2,0,0)
        drag.DragStart:Connect(function(player,ray,view,hit,part)
            assert(player==game.Players:GetPlayers()[1] and part==crate)
            assert(typeof(ray)=='Ray' and typeof(view)=='CFrame' and typeof(hit)=='CFrame')
            crate:SetAttribute('Started',1)
        end)
        drag.DragContinue:Connect(function(player,ray,view)
            assert(player==game.Players:GetPlayers()[1] and typeof(ray)=='Ray' and typeof(view)=='CFrame')
            crate:SetAttribute('Delta',drag.DragFrame.Position.X)
        end)
        drag.DragEnd:Connect(function(player)
            assert(player==game.Players:GetPlayers()[1])
            crate:SetAttribute('Ended',1)
        end)
    "#);
    aim_mobile_fixture(&mut session);
    touch_at(&mut session, 96, TouchPhase::Begin, 480.0, 240.0);
    session.step();
    assert_eq!(server_attribute(&session, "DragCrate", "Started"), 1.0);
    touch_at(&mut session, 96, TouchPhase::Move, 700.0, 240.0);
    session.run_for(0.1);
    assert!((server_attribute(&session, "DragCrate", "Delta") - 2.0).abs() < 0.01);
    assert!((server_part_position(&session, "DragCrate").x - 2.0).abs() < 0.01);
    session.input(0, InputEvent::CancelTouches);
    session.step();
    assert_eq!(server_attribute(&session, "DragCrate", "Ended"), 1.0);
    check_logs(&session);
}

#[test]
fn mobile_drag_of_unanchored_part_uses_physics_and_releases_actuator() {
    let mut session = session(r#"
        workspace.Gravity=0
        local crate=Instance.new('Part',workspace)
        crate.Name='DragCrate'
        crate.Size=Vector3.new(2,2,2)
        crate.Position=Vector3.new(0,6,-9)
        local drag=Instance.new('DragDetector',crate)
        drag.DragStyle=Enum.DragDetectorDragStyle.TranslateViewPlane
        drag.ResponseStyle=Enum.DragDetectorResponseStyle.Physical
        drag.MaxForce=100000
        drag.MaxActivationDistance=20
    "#);
    aim_mobile_fixture(&mut session);
    touch_at(&mut session, 97, TouchPhase::Begin, 480.0, 240.0);
    session.step();
    touch_at(&mut session, 97, TouchPhase::Move, 650.0, 240.0);
    assert!(server_part_position(&session, "DragCrate").x.abs() < 0.01, "Part teleported on move");
    session.run_for(0.6);
    assert!(server_part_position(&session, "DragCrate").x > 0.5);
    touch_at(&mut session, 97, TouchPhase::End, 650.0, 240.0);
    session.run_for(0.05);
    let w = session.server().runtime().world();
    let crate_part = w.dom.find_first_child(w.dom.find_service("Workspace").unwrap(), "DragCrate", false).unwrap();
    assert!(w.dom.find_first_child_of_class(crate_part, "BodyPosition").is_none());
    drop(w);
    check_logs(&session);
}

#[test]
fn authored_animation_moves_a_motor_emits_marker_and_restores_pose() {
    use rook_content::{ContentRef, Resolver, resolver::{Config, cache::Cache}};
    use rook_dom::Dom;
    use rook_types::CFrame;

    let temp = tempfile::tempdir().unwrap();
    let cache_dir = temp.path().join("animation-content");
    let mut dom = Dom::new();
    let sequence = dom.create_by_name("KeyframeSequence").unwrap();
    for (time, angle) in [(0.0, 0.0), (0.25, 0.9), (0.5, 0.2)] {
        let frame = dom.create_by_name("Keyframe").unwrap();
        dom.set_property_by_name(frame, "Time", Value::Float32(time)).unwrap();
        dom.set_parent(frame, Some(sequence)).unwrap();
        for name in ["Right Arm", "RightHand"] {
            let pose = dom.create_by_name("Pose").unwrap();
            dom.set_name(pose, name.to_owned()).unwrap();
            dom.set_property_by_name(pose, "Weight", Value::Float32(1.0)).unwrap();
            dom.set_property_by_name(pose, "CFrame", Value::CFrame(CFrame::angles(0.0, 0.0, angle))).unwrap();
            dom.set_parent(pose, Some(frame)).unwrap();
        }
        if time == 0.25 {
            let marker = dom.create_by_name("KeyframeMarker").unwrap();
            dom.set_name(marker, "Impact".to_owned()).unwrap();
            dom.set_property_by_name(marker, "Value", Value::String("hit".to_owned())).unwrap();
            dom.set_parent(marker, Some(frame)).unwrap();
        }
    }
    let model = rook_formats::from_dom(&dom, &[sequence]);
    let file = rook_formats::save(&model, rook_formats::Format::Binary).unwrap();
    let reference = ContentRef::parse("rbxassetid://950000010").unwrap();
    Cache::open(cache_dir.clone()).unwrap()
        .put(&reference.cache_key().unwrap(), &file, "authored fixture").unwrap();
    let content = Arc::new(Resolver::new(Config { cache_dir: Some(cache_dir), ..Config::empty() }));
    assert_eq!(&*content.resolve(&reference).unwrap().bytes, file.as_slice());

    let mut session = start(Options {
        content: Some(content), touch_input: true, ..Options::default()
    }, "workspace.Gravity=0");
    client(&session).run_extra_script(r#"
        local c=game.Players.LocalPlayer.Character
        local animator=assert(c.Humanoid:FindFirstChildOfClass('Animator'))
        local animation=Instance.new('Animation')
        animation.AnimationId='rbxassetid://950000010'
        local track=animator:LoadAnimation(animation)
        track.Looped=true
        track.Priority=Enum.AnimationPriority.Action
        track:GetMarkerReachedSignal('Impact'):Connect(function(value)
            assert(value=='hit')
            shared.ImpactMarker=true
        end)
        track.Ended:Connect(function() shared.TrackEnded=true end)
        track:Play(0,1,1)
        assert(track.IsPlaying and animator:GetPlayingAnimationTracks()[1]==track)
        shared.AuthoredTrack=track
    "#).unwrap();
    for _ in 0..100 {
        session.step();
        let ready = {
            let w = client(&session).runtime().world();
            w.animations.keys().any(|&id| w.dom.float_property(id, "Length") == Some(0.5))
        };
        if ready { break }
        std::thread::sleep(std::time::Duration::from_millis(2));
    }
    session.run_for(0.34);
    client(&session).run_extra_script(r#"
        local c=game.Players.LocalPlayer.Character
        local hand=c:FindFirstChild('RightHand') or c['Right Arm']
        local motor
        for _,item in c:GetDescendants() do
            if item:IsA('Motor6D') and item.Part1==hand then motor=item; break end
        end
        assert(motor and motor.Transform~=CFrame.identity,'clip did not animate the Motor6D')
        assert(shared.ImpactMarker and shared.AuthoredTrack.Length==0.5,'clip or marker missing')
        shared.AuthoredMotor=motor
        shared.AuthoredTrack:Stop(.05)
    "#).unwrap();
    session.run_for(0.15);
    client(&session).run_extra_script(r#"
        assert(shared.TrackEnded and not shared.AuthoredTrack.IsPlaying)
        assert(shared.AuthoredMotor.Transform==CFrame.identity,'pose did not restore')
    "#).unwrap();
    check_logs(&session);
}

#[test]
#[ignore = "requires the original Studio CoreScripts and fonts"]
fn default_prompt_button_uses_native_touch_hold_with_core_scripts() {
    install_test_platform();
    let path = std::env::var_os("RBX_MOBILE_OFFICIAL_COREPATCH").expect("DataModelPatch.rbxm path");
    let root = std::env::var_os("RBX_MOBILE_OFFICIAL_CONTENT").expect("Studio resource cache path");
    let patch = rook_engine::core_scripts::CorePatch::load(path.as_ref(), None).unwrap();
    let content = rbx_android_core::resources::StudioResources::new(root.into(), vec![], false).resolver();
    let mut session = start(Options {
        core_scripts: Some(Arc::new(patch)), content: Some(content), touch_input: true,
        initial_viewport: Some((960,480)), ..Options::default()
    }, r#"
        workspace.Gravity=0
        local part=Instance.new('Part',workspace)
        part.Name='OriginalPromptPad'
        part.Anchored=true
        part.Size=Vector3.new(3,3,1)
        part.Position=Vector3.new(0,6,-9)
        local prompt=Instance.new('ProximityPrompt',part)
        prompt.ActionText='Acender'
        prompt.HoldDuration=.25
        prompt.MaxActivationDistance=20
        prompt.RequiresLineOfSight=false
        prompt.Enabled=false
        prompt.Triggered:Connect(function() part:SetAttribute('Triggered',1) end)
    "#);
    aim_mobile_fixture(&mut session);
    session.server().run_extra_script("workspace.OriginalPromptPad.ProximityPrompt.Enabled=true").unwrap();
    session.run_for(1.1);
    {
        let w = client(&session).runtime().world();
        let boards: Vec<_> = w.dom.descendants(w.dom.root()).into_iter()
            .filter(|&id| w.dom.is_a(id, "BillboardGui"))
            .map(|id| w.dom.full_name(id)).collect();
        assert!(boards.iter().any(|name| name.contains("Prompt")), "{boards:?}");
    }
    touch_at(&mut session, 98, TouchPhase::Begin, 480.0, 240.0);
    session.run_for(0.42);
    touch_at(&mut session, 98, TouchPhase::End, 480.0, 240.0);
    session.run_for(0.1);
    assert_eq!(server_attribute(&session, "OriginalPromptPad", "Triggered"), 1.0);
    check_logs(&session);
}
