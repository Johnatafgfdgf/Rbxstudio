//! Online integration check using Rook's unmodified official package loader.
//! No downloaded Roblox files or script sources are published as artifacts.
use rbx_android_core::{resources::{self, Preparation, StudioResources}, Editor, PlaySession};
use std::sync::Arc;
use std::{path::PathBuf, time::{Duration, Instant}};
#[path = "studio_resources/font_delivery.rs"]
mod font_delivery;

fn step(play: &mut PlaySession, editor: &mut Editor) -> Result<(), String> {
    play.step(editor, Duration::from_secs_f64(1. / 60.))?;
    editor.dom.take_changes();
    Ok(())
}

fn attribute(editor: &Editor, name: &str) -> Result<rbx_dom::Variant, String> {
    let state = editor.snapshot();
    let id = state["tree"].as_array().and_then(|nodes| nodes.iter().find(|node| node["name"] == "Workspace"))
        .and_then(|node| node["id"].as_u64()).ok_or("The client workspace is missing")?;
    let workspace = editor.dom.get(rbx_dom::Ref::new(id as u32)).ok_or("The projected workspace is missing")?;
    rbx_dom::attributes::decode(workspace.properties().get("AttributesSerialize"))
        .remove(name).ok_or_else(|| format!("The original controls did not report {name}"))
}

/// Send actual host contacts to the original SDK widgets, with no replacement
/// controller or direct writes to Humanoid movement/jump properties.
fn verify_original_controls(play: &mut PlaySession, editor: &mut Editor) -> Result<(), String> {
    use rook_engine::{InputEvent, TouchPhase};
    play.run_client(r#"
        local frame=game.Players.LocalPlayer.PlayerGui.TouchGui.TouchControlFrame
        local stick=frame:FindFirstChild('DynamicThumbstickFrame') or frame:FindFirstChild('ThumbstickFrame')
        local jump=frame:FindFirstChild('JumpButton')
        assert(stick and stick.Visible,'The original thumbstick is not enabled')
        assert(jump and jump.Visible,'The original jump button is not enabled')
        local inset=game:GetService('GuiService'):GetGuiInset()
        for name,widget in pairs({OriginalStickPoint=stick,OriginalJumpPoint=jump}) do
            local p,s=widget.AbsolutePosition,widget.AbsoluteSize
            assert(s.X>0 and s.Y>0,'The original control has no layout')
            workspace:SetAttribute(name,Vector3.new(p.X+s.X*.5,p.Y+s.Y*.5+inset.Y,0))
            print('Original control layout',name,p,s,inset)
        end
    "#)?;
    for _ in 0..3 { step(play, editor)?; }
    let point = |name| match attribute(editor, name)? {
        rbx_dom::Variant::Vector3(v) => Ok((v.x, v.y)),
        _ => Err(format!("Invalid original control coordinates: {name}")),
    };
    let stick = point("OriginalStickPoint")?;
    let jump = point("OriginalJumpPoint")?;
    let touch = |id, phase, p: (f32, f32)| InputEvent::Touch { id, phase, x: p.0, y: p.1 };
    let position = |play: &PlaySession, editor: &Editor| play.character.position(&editor.dom).ok_or("The mobile character is missing");
    let before = position(play, editor)?;
    play.input(touch(901, TouchPhase::Begin, stick));
    step(play, editor)?;
    let moved = (stick.0 + 100., stick.1);
    play.input(touch(901, TouchPhase::Move, moved));
    for _ in 0..60 { step(play, editor)?; }
    let ground = position(play, editor)?.y;
    play.input(touch(902, TouchPhase::Begin, jump));
    let mut peak = ground;
    for frame in 0..60 {
        if frame == 3 { play.input(touch(902, TouchPhase::End, jump)); }
        step(play, editor)?;
        peak = peak.max(position(play, editor)?.y);
    }
    play.input(InputEvent::CancelTouches);
    for _ in 0..30 { step(play, editor)?; }
    let after = position(play, editor)?;
    let distance = glam::Vec2::new(after.x - before.x, after.z - before.z).length();
    if distance < 2. { return Err(format!("The original thumbstick did not move the character: {distance:.3} studs")); }
    if peak - ground < 2. { return Err(format!("The original jump button did not raise the character: {:.3} studs", peak - ground)); }
    for _ in 0..30 { step(play, editor)?; }
    let stopped = position(play, editor)?;
    let drift = glam::Vec2::new(stopped.x - after.x, stopped.z - after.z).length();
    if drift > 0.5 { return Err(format!("The original controller kept moving after contact cancellation: {drift:.3} studs")); }
    // Release a camera contact too: the SDK must not assert on native CANCEL.
    play.input(touch(903, TouchPhase::Begin, (600., 180.)));
    step(play, editor)?;
    play.input(InputEvent::CancelTouches);
    step(play, editor)?;
    println!("Original touch widgets: moved={distance:.3} studs jump={:.3} studs stopped_drift={drift:.3}; simultaneous contacts and cancellation verified", peak - ground);
    Ok(())
}

/// Exercise the actual editor reader with no numeric-ID fetcher, then prove
/// Rook's public legacy image is usable from its original offline cache.
fn verify_native_legacy_texture(root: &std::path::Path) -> Result<(), String> {
    let resolver = rbx_assets::AssetResolver::new(
        rbx_assets::AssetCache::new(Some(root.join("editor-assets"))).map_err(|e| e.to_string())?,
        Box::new(rbx_assets::MemoryFetcher::new()),
        rbx_assets::NativeContent::new(root.join("editor-packages")),
    );
    let asset = resolver.resolve(&rbx_assets::AssetRef::Native("textures/SpawnLocation.png".into()))
        .map_err(|e| format!("Original editor SpawnLocation resource: {e}"))?;
    let image = png::Decoder::new(std::io::Cursor::new(&asset.bytes)).read_info()
        .map_err(|e| format!("The public Rook legacy texture is not a PNG: {e}"))?;
    if image.info().width == 0 || image.info().height == 0 { return Err("Legacy texture is empty".into()); }
    let offline = StudioResources::new(root.join("official-resources"), vec![], false);
    let cached = offline.read_resource("Textures\\SpawnLocation.png")
        .ok_or("The original Rook legacy texture did not resolve offline")?;
    if cached != asset.bytes { return Err("Offline legacy texture differs from the editor resource".into()); }
    println!("Original editor resource bridge: public legacy SpawnLocation PNG {}x{}, no numeric fetcher, exact offline reuse", image.info().width, image.info().height);
    Ok(())
}

fn main() -> Result<(), String> {
    let root = std::env::args_os().nth(1).map(PathBuf::from)
        .ok_or("Pass a scratch folder for the official Studio resources")?;
    let touch = std::env::args().any(|arg| arg == "--touch");
    let cached = std::env::args().any(|arg| arg == "--offline-cache");
    std::env::set_var("RBX_NATIVE_ROBLOX_RESOURCES", root.join("official-resources"));
    std::env::set_var("XDG_CACHE_HOME", root.join("cache"));
    std::env::set_var("XDG_CONFIG_HOME", root.join("config"));
    std::env::set_var("RBX_NO_KEYRING", "1");
    let shared = if cached {
        Arc::new(StudioResources::new(root.join("official-resources"), vec![], false))
    } else { resources::shared().ok_or("Resource service was not created")?.clone() };
    if !cached { verify_native_legacy_texture(&root)?; }
    shared.prepare(false);
    let deadline = Instant::now() + Duration::from_secs(120);
    loop {
        match shared.state() {
            Preparation::Ready => break,
            Preparation::Failed(error) => return Err(error),
            _ if Instant::now() >= deadline => return Err("Official package preparation timed out".into()),
            _ => std::thread::sleep(Duration::from_millis(20)),
        }
    }
    println!("{}", shared.diagnostics());
    let font_path = "fonts/families/SourceSansPro.json";
    let font = shared.read_package(font_path).ok_or("The original font package did not resolve")?;
    serde_json::from_slice::<serde_json::Value>(&font).map_err(|e| e.to_string())?;
    let mut editor = Editor::open_or_create(root.join("official-core-test.rbxlx"))?;
    let before = rbx_xml::serialize(&editor.dom).map_err(|e| e.to_string())?;
    let mut play = if touch {
        let mut options = shared.play_options()?;
        options.touch_input = true;
        options.initial_viewport = Some((900, 430));
        PlaySession::start_with_options(&mut editor, options)?
    } else { PlaySession::start(&mut editor)? };
    if touch {
        play.viewport(900, 430);
        play.run_client(r#"
            local uis=game:GetService('UserInputService')
            assert(uis.TouchEnabled and not uis.KeyboardEnabled and not uis.MouseEnabled)
            assert(uis:GetLastInputType()==Enum.UserInputType.Touch)
            assert(uis.PreferredInput==Enum.PreferredInput.Touch)
            local params=Instance.new('GetTextBoundsParams')
            params.Font=Font.new('rbxasset://fonts/families/SourceSansPro.json')
            params.Size=18
            params.Text='Água e criação no celular'
            local textService=game:GetService('TextService')
            local plain=textService:GetTextBoundsAsync(params)
            assert(plain.X>20 and plain.Y>=18,'The original font was not measured')
            params.Width=math.huge
            assert(textService:GetTextBoundsAsync(params)==plain,'Infinite width must leave the line unconstrained')
            params.Width=-48
            assert(textService:GetTextBoundsAsync(params)==plain,'Initial padded widths must not break original UI measurement')
            params.Width=40
            local wrapped=textService:GetTextBoundsAsync(params)
            assert(wrapped.Y>plain.Y,'Modern font bounds did not wrap')
            params.Width=0
            params.RichText=true
            params.Text='<font size="36">Água</font>'
            local rich=textService:GetTextBoundsAsync(params)
            assert(rich.Y>=36,'Rich text bounds ignored the original font size')
            assert(not pcall(function() textService:GetTextBoundsAsync(Instance.new('Folder')) end))
            params:Destroy()
            workspace:SetAttribute('OriginalFontBounds',true)
            local gui=game.Players.LocalPlayer.PlayerGui:WaitForChild('TouchGui',5)
            if not gui then
                local ps=game.Players.LocalPlayer:FindFirstChild('PlayerScripts')
                print('Touch diagnostics: PlayerScripts',ps,'loaded',game:IsLoaded())
                if ps then
                    for _,child in ipairs(ps:GetChildren()) do
                        print('PlayerScripts child',child.Name,child.ClassName)
                    end
                    local module=ps:FindFirstChild('PlayerModule')
                    if module then
                        local ok,result=pcall(require,module)
                        print('PlayerModule diagnostic require',ok,typeof(result),ok and '' or tostring(result))
                        if ok then
                            local success,controls=pcall(function() return result:GetControls() end)
                            print('Controls diagnostic',success,typeof(controls),game:GetService('GuiService').TouchControlsEnabled)
                            if success and type(controls)=='table' then
                                for key,value in pairs(controls) do print('Control field',key,typeof(value),type(value)=='function' and '' or tostring(value)) end
                            end
                        end
                    end
                end
            end
            assert(gui and gui:IsA('ScreenGui'),'The original mobile controls were not created')
            assert(gui:WaitForChild('TouchControlFrame',2),'The original touch control frame is missing')
            workspace:SetAttribute('OriginalTouchControls',true)
        "#)?;
    }
    for _ in 0..360 {
        step(&mut play, &mut editor)?;
        std::thread::sleep(Duration::from_millis(16));
    }
    if touch {
        if attribute(&editor, "OriginalFontBounds")? != rbx_dom::Variant::Bool(true) {
            return Err("Modern original font bounds validation did not finish".into());
        }
        play.run_client(r#"
            local character=game.Players.LocalPlayer.Character
            local head=assert(character:FindFirstChild('Head'),'The original avatar head is missing')
            local torso=assert(character:FindFirstChild('Torso') or character:FindFirstChild('UpperTorso'),
                'The original avatar torso is missing')
            local humanoid=character:FindFirstChildOfClass('Humanoid')
            local mesh=head:FindFirstChildOfClass('SpecialMesh')
            print('Original avatar visual state',humanoid and humanoid.RigType,head.ClassName,
                head.CFrame,head.Size,head.Transparency,head.LocalTransparencyModifier)
            if mesh then
                print('Original avatar head mesh',mesh.MeshType,mesh.Scale,mesh.MeshId)
                assert(mesh.Scale.X>0 and mesh.Scale.Y>0 and mesh.Scale.Z>0,'The original head mesh has zero scale')
            elseif head:IsA('MeshPart') then
                print('Original avatar MeshPart head',head.MeshId,head.MeshSize)
            end
            local camera=workspace.CurrentCamera
            if camera then print('Original avatar camera',camera.CFrame,camera.CameraSubject) end
            assert(head.Position.Y>torso.Position.Y,'The original avatar head is below its torso')
        "#)?;
        for _ in 0..3 { step(&mut play, &mut editor)?; }
        if let Err(error) = verify_original_controls(&mut play, &mut editor) {
            for line in &editor.logs { eprintln!("{line}"); }
            return Err(error);
        }
    }
    let (server, client) = play.started_scripts();
    if touch {
        for line in &editor.logs { eprintln!("{line}"); }
        if editor.logs.iter().any(|line| line.contains("Infinite yield possible on 'CoreGui.RobloxGui:WaitForChild(\"ControlFrame\")'")) {
            return Err("The original touch CoreScripts are blocked waiting for the engine control frame".into());
        }
    }
    let errors: Vec<_> = editor.logs.iter().filter(|line| line.contains("Error") || line.contains("timed out")).collect();
    if touch && editor.logs.iter().any(|line| line.contains("Font cannot be measured") || line.contains("Text measurement failed")) {
        return Err("Original UI font measurement still fell back to frame bounds".into());
    }
    println!("touch={touch} server_scripts={server} client_scripts={client} errors={}", errors.len());
    for line in &errors { eprintln!("{line}"); }
    if client == 0 { return Err("The official client CoreScripts did not start".into()); }
    if !errors.is_empty() { return Err("The official CoreScripts reported runtime errors".into()); }
    if touch {
        let state = editor.snapshot();
        let id = state["tree"].as_array().and_then(|nodes| nodes.iter().find(|node| node["name"] == "Workspace"))
            .and_then(|node| node["id"].as_u64()).ok_or("The client workspace is missing")?;
        let workspace = editor.dom.get(rbx_dom::Ref::new(id as u32)).ok_or("The projected workspace is missing")?;
        let attributes = rbx_dom::attributes::decode(workspace.properties().get("AttributesSerialize"));
        if attributes.get("OriginalTouchControls") != Some(&rbx_dom::Variant::Bool(true)) {
            return Err("The original touch controls check did not finish".into());
        }
    }
    if play.character.position(&editor.dom).is_none() { return Err("The playable character is missing".into()); }
    play.stop(&mut editor);
    if rbx_xml::serialize(&editor.dom).map_err(|e| e.to_string())? != before {
        return Err("Stop failed to restore the original document".into());
    }
    let offline = Arc::new(StudioResources::new(root.join("official-resources"), vec![], false));
    offline.prepare(false);
    let deadline = Instant::now() + Duration::from_secs(20);
    while matches!(offline.state(), Preparation::Loading) && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(20));
    }
    if !matches!(offline.state(), Preparation::Ready) { return Err("Downloaded CoreScripts were not available offline".into()); }
    if offline.read_package(font_path).as_ref() != Some(&font) { return Err("The cached original font changed offline".into()); }
    let cached = offline.resolver().resolve(&rook_content::ContentRef::File(font_path.into())).map_err(|e| e.to_string())?;
    if &*cached.bytes != font.as_slice() { return Err("The offline engine did not read the original font".into()); }
    println!("Official packages, CoreScripts, Stop and offline reuse verified");
    if touch { font_delivery::verify(&shared, &mut editor)?; }
    Ok(())
}
