fn main() {
    let temp = std::env::temp_dir().join(format!("rbx-mobile-diagnostics-{}", std::process::id()));
    std::fs::create_dir_all(&temp).unwrap();
    let mut editor = rbx_android_core::Editor::open_or_create(temp.join("host.rbxlx")).unwrap();
    rbx_android_core::PlaySession::start(&mut editor)
        .unwrap()
        .stop(&mut editor);
    let report = rbx_android_core::diagnostics::run();
    let text = serde_json::to_string_pretty(&report).unwrap();
    if let Some(path) = std::env::args().nth(1) {
        std::fs::write(path, &text).unwrap();
    }
    println!("{text}");
    let _ = std::fs::remove_dir_all(temp);
    if report["failed"].as_u64().unwrap_or(1) > 0 {
        std::process::exit(1);
    }
}
