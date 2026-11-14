//! A real Rook Play session drawn by the original rbx-native renderer.
//! Offline validation image; intentionally no externally fetched assets.
use rbx_android_core::{Editor, PlaySession};
use std::{path::PathBuf, time::Duration};
fn main() -> Result<(), String> {
    let out = PathBuf::from(
        std::env::args()
            .nth(1)
            .unwrap_or("dist/play-validation".into()),
    );
    std::fs::create_dir_all(&out).map_err(|e| e.to_string())?;
    let mut editor = Editor::open_or_create(out.join("capture-place.rbxlx"))?;
    if std::env::args().any(|a|a=="--gui-check") {
        editor.run("local g=Instance.new('ScreenGui');g.Name='RendererGuiFixture';g.IgnoreGuiInset=true;g.Parent=game:GetService('StarterGui');local b=Instance.new('TextButton');b.Name='RendererButton';b.Text='Rook / rbx-native';b.Position=UDim2.fromOffset(80,100);b.Size=UDim2.fromOffset(160,80);b.BackgroundColor3=Color3.fromRGB(24,118,234);b.Parent=g")?;
    }
    let mut viewer = rbx_viewer::Headless::from_dom(&editor.dom, false)?;
    viewer.set_quality(rbx_viewer::QualityLevel::Level(5));
    let mut play = PlaySession::start(&mut editor)?;
    play.viewport(960,540);
    let changes=editor.dom.take_changes();
    println!("Play entry renderer patch: {:?}",viewer.apply_changes(&editor.dom,&changes)?);
    for _ in 0..120 {
        play.step(&mut editor, Duration::from_secs_f64(1. / 60.))?;
        let changes=editor.dom.take_changes();
        viewer.apply_changes(&editor.dom,&changes)?;
    }
    let at=play.character.position(&editor.dom).ok_or("No character")?;
    viewer.open_at((at+glam::Vec3::new(5.,3.,9.)).to_array(),(at+glam::Vec3::Y).to_array(),50.);
    let frame = viewer.capture(960, 540, None)?;
    let file = std::fs::File::create(out.join("rook-r6-original-renderer.png"))
        .map_err(|e| e.to_string())?;
    let mut png = png::Encoder::new(file, 960, 540);
    png.set_color(png::ColorType::Rgba);
    png.set_depth(png::BitDepth::Eight);
    png.write_header()
        .map_err(|e| e.to_string())?
        .write_image_data(&frame.pixels)
        .map_err(|e| e.to_string())?;
    println!(
        "Original Rook R6 rendered at {:?}",
        play.character.position(&editor.dom)
    );
    play.stop(&mut editor);
    viewer.reload(&editor.dom)?;
    Ok(())
}
