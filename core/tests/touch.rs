use rbx_android_core::{Editor, PlaySession};
use rook_engine::{InputEvent, TouchPhase};
use std::time::Duration;

fn tick(play: &mut PlaySession, editor: &mut Editor) {
    play.step(editor, Duration::from_secs_f64(1.0 / 60.0)).unwrap();
    editor.dom.take_changes();
}

#[test]
fn host_viewport_is_visible_to_the_first_original_client_script() {
    let temp = tempfile::tempdir().unwrap();
    let mut editor = Editor::open_or_create(temp.path().join("viewport.rbxlx")).unwrap();
    editor.run(r#"
        local script=Instance.new('LocalScript')
        script.Source=[[
            assert(workspace.CurrentCamera.ViewportSize==Vector2.new(900,430),
                'The first client script saw a desktop/default viewport')
            workspace:SetAttribute('InitialViewportVerified',true)
        ]]
        script.Parent=game:GetService('StarterGui')
    "#).unwrap();
    let before = rbx_xml::serialize(&editor.dom).unwrap();
    let options = rook_engine::Options { touch_input: true, initial_viewport: Some((900,430)), ..Default::default() };
    let mut play = PlaySession::start_with_options(&mut editor, options).unwrap();
    tick(&mut play, &mut editor);
    let state = editor.snapshot();
    let id = state["tree"].as_array().unwrap().iter().find(|node| node["name"] == "Workspace").unwrap()["id"].as_u64().unwrap();
    let workspace = editor.dom.get(rbx_dom::Ref::new(id as u32)).unwrap();
    let attrs = rbx_dom::attributes::decode(workspace.properties().get("AttributesSerialize"));
    assert_eq!(attrs.get("InitialViewportVerified"), Some(&rbx_dom::Variant::Bool(true)), "{:?}", editor.logs);
    play.stop(&mut editor);
    assert_eq!(rbx_xml::serialize(&editor.dom).unwrap(), before);
}

#[test]
fn original_rook_delivers_independent_touch_objects_gui_activation_and_cancellation() {
    let temp = tempfile::tempdir().unwrap();
    let mut editor = Editor::open_or_create(temp.path().join("touch.rbxlx")).unwrap();
    let before = rbx_xml::serialize(&editor.dom).unwrap();
    let mut play = PlaySession::start(&mut editor).unwrap();
    play.viewport(600, 400);
    play.run_client(r#"
        local uis=game:GetService('UserInputService')
        assert(not pcall(function() game:GetService('GuiService'):GetScreenResolution() end),
            'GetScreenResolution must retain RobloxScript security')
        assert(workspace.CurrentCamera.ViewportSize==Vector2.new(600,400))
        shared.TouchTest={began={},moved={},ended={},activated={},changes=0}
        local test=shared.TouchTest
        local gui=Instance.new('ScreenGui')
        gui.IgnoreGuiInset=true
        gui.Parent=game.Players.LocalPlayer.PlayerGui
        for i=1,2 do
            local button=Instance.new('TextButton')
            button.Name='Touch'..i
            button.Position=UDim2.fromOffset((i-1)*200,0)
            button.Size=UDim2.fromOffset(150,150)
            button.Parent=gui
            button.Activated:Connect(function(input)
                assert(input.UserInputType==Enum.UserInputType.Touch)
                test.activated[i]=(test.activated[i] or 0)+1
            end)
        end
        uis.TouchStarted:Connect(function(input,processed)
            assert(input.UserInputType==Enum.UserInputType.Touch)
            assert(input.UserInputState==Enum.UserInputState.Begin)
            table.insert(test.began,{input=input,processed=processed})
        end)
        uis.TouchMoved:Connect(function(input,processed)
            assert(input.UserInputState==Enum.UserInputState.Change)
            table.insert(test.moved,{input=input,processed=processed,dx=input.Delta.X,dy=input.Delta.Y})
        end)
        uis.TouchEnded:Connect(function(input,processed)
            table.insert(test.ended,{input=input,processed=processed,state=input.UserInputState})
        end)
        uis.LastInputTypeChanged:Connect(function(kind)
            assert(typeof(kind)=='EnumItem')
            test.changes+=1
        end)
    "#).unwrap();
    tick(&mut play, &mut editor);
    let touch = |id, phase, x, y| InputEvent::Touch { id, phase, x, y };
    play.input(touch(11, TouchPhase::Begin, 30.0, 80.0));
    play.input(touch(22, TouchPhase::Begin, 230.0, 80.0));
    play.input(touch(11, TouchPhase::Move, 50.0, 90.0));
    play.input(touch(22, TouchPhase::Cancel, 230.0, 80.0));
    play.input(touch(11, TouchPhase::End, 50.0, 90.0));
    play.input(touch(33, TouchPhase::Begin, 550.0, 300.0));
    play.input(InputEvent::CancelTouches);
    // A late packet for a released pointer must not create another contact.
    play.input(touch(33, TouchPhase::Move, 560.0, 305.0));
    play.input(touch(44, TouchPhase::Begin, f32::NAN, 50.0));
    tick(&mut play, &mut editor);
    play.viewport(1200, 550);
    play.run_client(r#"
        local t=shared.TouchTest
        assert(#t.began==3 and #t.moved==1 and #t.ended==3)
        assert(t.began[1].input~=t.began[2].input)
        assert(t.began[1].input==t.moved[1].input and t.began[1].input==t.ended[2].input)
        assert(t.moved[1].dx==20 and t.moved[1].dy==10)
        assert(t.ended[1].input==t.began[2].input and t.ended[1].state==Enum.UserInputState.End)
        assert(t.ended[2].state==Enum.UserInputState.End and t.ended[3].state==Enum.UserInputState.End)
        assert(t.began[1].processed and t.began[2].processed and not t.began[3].processed)
        assert(t.activated[1]==1 and t.activated[2]==nil)
        assert(game:GetService('UserInputService'):GetLastInputType()==Enum.UserInputType.Touch)
        assert(game:GetService('UserInputService').PreferredInput==Enum.PreferredInput.Touch)
        assert(t.changes>=1)
        assert(workspace.CurrentCamera.ViewportSize==Vector2.new(1200,550))
        workspace:SetAttribute('TouchVerified',true)
    "#).unwrap();
    tick(&mut play, &mut editor);
    assert!(editor.logs.iter().all(|line| !line.contains("Error")), "{:?}", editor.logs);
    let snapshot = editor.snapshot();
    let id = snapshot["tree"].as_array().unwrap().iter().find(|item| item["name"] == "Workspace")
        .expect("projected workspace")["id"].as_u64().unwrap();
    let workspace = editor.dom.get(rbx_dom::Ref::new(id as u32)).unwrap();
    let attributes = rbx_dom::attributes::decode(workspace.properties().get("AttributesSerialize"));
    assert_eq!(attributes.get("TouchVerified"), Some(&rbx_dom::Variant::Bool(true)), "{:?}", editor.logs);
    play.stop(&mut editor);
    assert_eq!(rbx_xml::serialize(&editor.dom).unwrap(), before);
}
