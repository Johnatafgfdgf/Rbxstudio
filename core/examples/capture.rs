use rbx_android_core::Editor;
fn main() -> Result<(), String> {
    let out = std::path::PathBuf::from(std::env::args().nth(1).unwrap_or_else(|| "dist".into()));
    std::fs::create_dir_all(&out).map_err(|e| e.to_string())?;
    let mut editor = Editor::open_or_create(out.join("validation.rbxlx"))?;
    editor.run("local p=Instance.new('Part');p.Name='PortValidation';p.Anchored=true;p.Size=Vector3.new(6,6,6);p.Position=Vector3.new(0,4,0);p.Color=Color3.fromRGB(40,145,255);p.Parent=workspace")?;
    let textures = std::env::var_os("PORT_FETCH_TEXTURES").is_some();
    let mut viewer = rbx_viewer::Headless::from_dom(&editor.dom, textures)?;
    if textures {
        for _ in 0..150 {
            viewer.tick(std::time::Duration::from_millis(200));
            for warning in viewer.drain_warnings() {
                eprintln!("asset: {warning}");
            }
            std::thread::sleep(std::time::Duration::from_millis(200));
        }
    }
    viewer.open_at([22., 16., 25.], [0., 2., 0.], 70.);
    viewer.set_quality(rbx_viewer::QualityLevel::Level(5));
    let part = editor.snapshot()["tree"]
        .as_array()
        .unwrap()
        .iter()
        .find(|i| i["name"] == "PortValidation")
        .and_then(|i| i["id"].as_u64())
        .unwrap();
    viewer.set_selection(&rbx_viewer::pick::selection(
        &editor.dom,
        editor.database(),
        &[rbx_dom::Ref::new(part as u32)],
    ));
    viewer.set_gizmo(Some(rbx_viewer::Gizmo {
        kind: rbx_viewer::gizmo::Kind::Move,
        ..Default::default()
    }));
    let frame = viewer.capture(960, 540, None)?;
    let file =
        std::fs::File::create(out.join("original-renderer.png")).map_err(|e| e.to_string())?;
    let mut png = png::Encoder::new(file, 960, 540);
    png.set_color(png::ColorType::Rgba);
    png.set_depth(png::BitDepth::Eight);
    png.write_header()
        .map_err(|e| e.to_string())?
        .write_image_data(&frame.pixels)
        .map_err(|e| e.to_string())?;
    Ok(())
}
