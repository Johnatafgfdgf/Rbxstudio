//! A delayed loopback delivery fixture; original font bytes stay in memory.
use rbx_android_core::{resources::StudioResources, Editor, PlaySession};
use std::{io::{Read, Write}, net::TcpListener, sync::{Arc, atomic::{AtomicBool, AtomicUsize, Ordering}}, thread, time::{Duration, Instant}};

struct Server { stop: Arc<AtomicBool>, calls: Arc<AtomicUsize>, thread: Option<thread::JoinHandle<()>> }
impl Drop for Server {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Release);
        if let Some(thread) = self.thread.take() { let _ = thread.join(); }
    }
}

pub(super) fn verify(resources: &StudioResources, editor: &mut Editor) -> Result<(), String> {
    let face = resources.read_package("fonts/SourceSansPro-Regular.ttf").ok_or("The official font face is missing")?;
    let listener = TcpListener::bind("127.0.0.1:0").map_err(|e| e.to_string())?;
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let base = format!("http://{}", listener.local_addr().map_err(|e| e.to_string())?);
    let stop = Arc::new(AtomicBool::new(false));
    let calls = Arc::new(AtomicUsize::new(0));
    let stopped = stop.clone();
    let counted = calls.clone();
    let address = base.clone();
    let worker = thread::spawn(move || {
        while !stopped.load(Ordering::Acquire) {
            let (mut stream, _) = match listener.accept() {
                Ok(stream) => stream,
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => { thread::sleep(Duration::from_millis(2)); continue; }
                Err(_) => break,
            };
            let _ = stream.set_read_timeout(Some(Duration::from_secs(2)));
            let _ = stream.set_write_timeout(Some(Duration::from_secs(2)));
            let mut request = Vec::new();
            let mut byte = [0u8; 1];
            while request.len() < 8192 && !request.ends_with(b"\r\n\r\n") {
                if stream.read(&mut byte).ok() != Some(1) { break; }
                request.push(byte[0]);
            }
            let text = String::from_utf8_lossy(&request);
            let path = text.split_whitespace().nth(1).unwrap_or("");
            let body = if path == "/v2/assetId/900001" {
                serde_json::json!({"locations":[{"assetFormat":"source","location":format!("{address}/family.json")}]}).to_string().into_bytes()
            } else if path == "/v2/assetId/900002" {
                serde_json::json!({"locations":[{"assetFormat":"source","location":format!("{address}/face.ttf")}]}).to_string().into_bytes()
            } else if path == "/family.json" {
                serde_json::json!({"faces":[{"weight":400,"style":"normal","assetId":"rbxassetid://900002"}]}).to_string().into_bytes()
            } else if path == "/face.ttf" {
                face.clone()
            } else {
                // The local avatar also asks Asset Delivery for its resources.
                let _ = write!(stream, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                continue;
            };
            counted.fetch_add(1, Ordering::AcqRel);
            thread::sleep(Duration::from_millis(30));
            let _ = write!(stream, "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len());
            let _ = stream.write_all(&body);
        }
    });
    let server = Server { stop, calls, thread: Some(worker) };
    let mut config = rook_content::resolver::Config::empty();
    config.network = true;
    config.endpoints = rook_platform::endpoints::Endpoints::new(Some("off"), [("assetdelivery", base.as_str())]);
    let options = rook_engine::Options { touch_input: true, initial_viewport: Some((900,430)),
        content: Some(Arc::new(rook_content::Resolver::new(config))), wait_for_sounds: false, ..Default::default() };
    let before = rbx_xml::serialize(&editor.dom).map_err(|e| e.to_string())?;
    let mut play = PlaySession::start_with_options(editor, options)?;
    play.run_client(r#"
        local p=Instance.new('GetTextBoundsParams')
        p.Font=Font.new('rbxassetid://900001')
        p.Size=20
        p.Text='Texto remoto: criação'
        shared.RemoteFontParams=p
        task.defer(function() workspace:SetAttribute('FontHeartbeatRuns',true) end)
        local bounds=game:GetService('TextService'):GetTextBoundsAsync(p)
        assert(bounds.X>20 and bounds.Y>=20,'A pending font returned fallback/empty bounds')
        shared.RemoteFontBounds=bounds
        workspace:SetAttribute('RemoteFontReady',true)
    "#)?;
    if super::attribute(editor, "RemoteFontReady").is_ok() { return Err("The cold remote font did not yield".into()); }
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut frames = 0;
    while super::attribute(editor, "RemoteFontReady").is_err() && Instant::now() < deadline && frames < 900 {
        super::step(&mut play, editor)?;
        frames += 1;
        thread::sleep(Duration::from_millis(2));
    }
    if super::attribute(editor, "RemoteFontReady").ok() != Some(rbx_dom::Variant::Bool(true)) {
        return Err(format!("Delayed font delivery did not finish: {:?}", editor.logs));
    }
    if super::attribute(editor, "FontHeartbeatRuns").ok() != Some(rbx_dom::Variant::Bool(true)) {
        return Err("Font loading blocked client scheduling".into());
    }
    let fetched = server.calls.load(Ordering::Acquire);
    if fetched != 4 || frames < 2 { return Err(format!("Expected delayed family+face delivery, got {fetched} requests / {frames} frames")); }
    play.run_client(r#"
        local b=game:GetService('TextService'):GetTextBoundsAsync(shared.RemoteFontParams)
        assert(b==shared.RemoteFontBounds)
        workspace:SetAttribute('RemoteFontCached',true)
    "#)?;
    for _ in 0..20 { super::step(&mut play, editor)?; }
    if super::attribute(editor, "RemoteFontCached").ok() != Some(rbx_dom::Variant::Bool(true))
        || server.calls.load(Ordering::Acquire) != fetched {
        return Err("Remote font cache changed its metrics or fetched the font twice".into());
    }
    play.stop(editor);
    if rbx_xml::serialize(&editor.dom).map_err(|e| e.to_string())? != before { return Err("Remote font Stop changed the document".into()); }
    println!("Delayed remote font family+face: {fetched} HTTP requests, {frames} responsive frames, exact cached metrics and Stop verified");
    Ok(())
}
