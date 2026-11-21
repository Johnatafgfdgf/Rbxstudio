//! Engine-side regression for the reviewed Android AI "Receber item" kit.
//! Exercises real Edit command Luau, Script.Source/LocalScript.Source, a
//! server-authoritative RemoteEvent, Backpack delivery and duplicate denial.
//! This does not replace a touchscreen/MCP-approval test on an Android phone.

use rbx_android_core::{Editor, PlaySession};
use rbx_dom::{Ref, Variant, WeakDom};
use std::time::Duration;
use rook_engine::{InputEvent, TouchPhase};

fn find(dom: &WeakDom, name: &str) -> Option<Ref> {
    let mut stack = dom.root_refs().to_vec();
    while let Some(id) = stack.pop() {
        let Some(item) = dom.get(id) else { continue };
        if item.name() == name { return Some(id); }
        stack.extend_from_slice(item.children());
    }
    None
}

fn assemble(e: &mut Editor) {
    e.run(r#"
        local rs = game:GetService('ReplicatedStorage')
        local store = game:GetService('ServerStorage')
        local server = game:GetService('ServerScriptService')
        local starter = game:GetService('StarterGui')
        assert(not rs:FindFirstChild('AI_Grant_Request'))
        local remote = Instance.new('RemoteEvent')
        remote.Name = 'AI_Grant_Request'
        local tool = Instance.new('Tool')
        tool.Name = 'AI_Grant_Tool'
        tool.ToolTip = 'Lanterna'
        tool.RequiresHandle = true
        tool.CanBeDropped = false
        local handle = Instance.new('Part')
        handle.Name = 'Handle'
        handle.Size = Vector3.new(0.6, 0.6, 1.5)
        handle.CanCollide = false
        local light = Instance.new('PointLight')
        light.Range = 14
        light.Brightness = 2
        light.Parent = handle
        handle.Parent = tool

        local logic = Instance.new('Script')
        logic.Name = 'AI_Grant_Server'
        logic.Source = [==[
            local rs=game:GetService('ReplicatedStorage')
            local store=game:GetService('ServerStorage')
            local remote=rs:WaitForChild('AI_Grant_Request')
            local template=store:WaitForChild('AI_Grant_Tool')
            remote.OnServerEvent:Connect(function(player)
                local bag=player:FindFirstChildOfClass('Backpack')
                if not bag then return end
                if bag:FindFirstChild('AI_Grant_Tool') or
                    (player.Character and player.Character:FindFirstChild('AI_Grant_Tool')) then
                    return
                end
                local item=template:Clone()
                item.Parent=bag
                workspace:SetAttribute('GrantCount',
                    (workspace:GetAttribute('GrantCount') or 0)+1)
            end)
        ]==]
        local gui = Instance.new('ScreenGui')
        gui.Name = 'AI_Grant_Gui'
        gui.ResetOnSpawn = false
        local button = Instance.new('TextButton')
        button.Name = 'ReceiveItem'
        button.Size = UDim2.fromOffset(200, 52)
        button.AnchorPoint = Vector2.new(0.5, 0.5)
        button.Position = UDim2.fromScale(0.5, 0.5)
        button.Text = 'Receber item'
        local client = Instance.new('LocalScript')
        client.Name = 'RequestClient'
        client.Source = [==[
            local remote=game:GetService('ReplicatedStorage'):
                WaitForChild('AI_Grant_Request')
            script.Parent.Activated:Connect(function()
                remote:FireServer()
            end)
        ]==]
        client.Parent=button
        button.Parent=gui
        remote.Parent=rs
        tool.Parent=store
        logic.Parent=server
        gui.Parent=starter
    "#).expect("editor must assemble the real four-service system");
}

#[test]
fn ai_receive_tool_edit_luau_creates_source_and_tool_instances() {
    let tmp = tempfile::tempdir().unwrap();
    let mut editor = Editor::open_or_create(tmp.path().join("kit.rbxlx")).unwrap();
    assemble(&mut editor);
    for (name, class) in [
        ("AI_Grant_Request", "RemoteEvent"),
        ("AI_Grant_Tool", "Tool"),
        ("AI_Grant_Server", "Script"),
        ("AI_Grant_Gui", "ScreenGui"),
        ("RequestClient", "LocalScript"),
        ("Handle", "Part"),
    ] {
        let reference = find(&editor.dom, name).unwrap_or_else(|| panic!("missing {name}"));
        assert_eq!(editor.dom.get(reference).unwrap().class(), class);
    }
    for name in ["AI_Grant_Server", "RequestClient"] {
        let instance = editor.dom.get(find(&editor.dom, name).unwrap()).unwrap();
        assert!(
            matches!(instance.properties().get("Source"), Some(Variant::String(s)) if s.contains("OnServerEvent") || s.contains("FireServer")),
            "the runtime must persist Source on {name}"
        );
    }
}

#[test]
fn ai_receive_tool_play_delivers_once_to_backpack() {
    let tmp = tempfile::tempdir().unwrap();
    let mut editor = Editor::open_or_create(tmp.path().join("kit-play.rbxlx")).unwrap();
    assemble(&mut editor);
    let mut play = PlaySession::start(&mut editor).expect("Play should start");
    for _ in 0..10 {
        play.step(&mut editor, Duration::from_secs_f64(1. / 60.)).unwrap();
        editor.dom.take_changes();
    }
    play.run_client(r#"
        local r=game:GetService('ReplicatedStorage'):WaitForChild('AI_Grant_Request')
        r:FireServer()
    "#).expect("client should send a RemoteEvent");
    for _ in 0..12 {
        play.step(&mut editor, Duration::from_secs_f64(1. / 60.)).unwrap();
        editor.dom.take_changes();
    }
    let workspace = find(&editor.dom, "Workspace").unwrap();
    let attrs = rbx_dom::attributes::decode(
        editor.dom.get(workspace).unwrap().properties().get("AttributesSerialize"),
    );
    assert_eq!(attrs.get("GrantCount"), Some(&Variant::Float64(1.)), "logs: {:?}", editor.logs);
    // A second click / RemoteEvent must not grant an additional copy.
    play.run_client("game:GetService('ReplicatedStorage').AI_Grant_Request:FireServer()")
        .unwrap();
    for _ in 0..12 {
        play.step(&mut editor, Duration::from_secs_f64(1. / 60.)).unwrap();
        editor.dom.take_changes();
    }
    let attrs = rbx_dom::attributes::decode(
        editor.dom.get(workspace).unwrap().properties().get("AttributesSerialize"),
    );
    assert_eq!(attrs.get("GrantCount"), Some(&Variant::Float64(1.)), "logs: {:?}", editor.logs);
}


#[test]
fn ai_receive_tool_mobile_tap_activated_invokes_local_script_and_server() {
    let tmp = tempfile::tempdir().unwrap();
    let mut editor = Editor::open_or_create(tmp.path().join("kit-touch.rbxlx")).unwrap();
    assemble(&mut editor);
    let mut play = PlaySession::start(&mut editor).expect("Play should start");
    play.viewport(960, 480);
    for _ in 0..12 {
        play.step(&mut editor, Duration::from_secs_f64(1. / 60.)).unwrap();
        editor.dom.take_changes();
    }
    // Unlike IgnoreGuiInset=true in the existing touch regression, this
    // StarterGui ScreenGui uses the engine's default top-bar inset. Its
    // visual center is at y = 58 + (480 - 58)/2 = 269, not viewport y=240.
    // Confirm the actual GuiObject geometry before sending a touch.
    play.run_client(r#"
        local gui=game.Players.LocalPlayer.PlayerGui:FindFirstChild('AI_Grant_Gui')
        assert(gui and gui.ReceiveItem and gui.ReceiveItem.RequestClient,
            'StarterGui was not cloned into PlayerGui with the LocalScript')
        local b=gui.ReceiveItem
        assert(gui.IgnoreGuiInset == false, 'regression requires default GUI inset')
        local p,s=b.AbsolutePosition,b.AbsoluteSize
        -- AbsolutePosition is expressed in GUI coordinates (below the
        -- 58 px top bar), while InputEvent.Touch uses window coordinates.
        assert(p.X <= 480 and 480 <= p.X+s.X and
               p.Y <= 211 and 211 <= p.Y+s.Y,
               ('touch coordinate misses button: pos=%s size=%s')
                    :format(tostring(p),tostring(s)))
    "#).expect("ScreenGui must render its button below the top-bar inset");
    play.input(InputEvent::Touch {
        id: 42, phase: TouchPhase::Begin, x: 480., y: 269.,
    });
    play.input(InputEvent::Touch {
        id: 42, phase: TouchPhase::End, x: 480., y: 269.,
    });
    for _ in 0..20 {
        play.step(&mut editor, Duration::from_secs_f64(1. / 60.)).unwrap();
        editor.dom.take_changes();
    }
    let workspace = find(&editor.dom, "Workspace").unwrap();
    let attrs = rbx_dom::attributes::decode(
        editor.dom.get(workspace).unwrap().properties().get("AttributesSerialize"),
    );
    assert_eq!(attrs.get("GrantCount"), Some(&Variant::Float64(1.)),
        "touch Activated must reach server-authoritative delivery: {:?}", editor.logs);
}
