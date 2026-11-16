//! Android surface, lifecycle and input adapter. Rendering and object editing
//! are delegated to the pinned rbx-native sources.
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{mpsc, Arc, OnceLock, RwLock};
use std::thread;
use std::time::{Duration, Instant};

use glam::{Mat3, Vec2, Vec3};
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use ndk::native_window::NativeWindow;
use raw_window_handle::{
    AndroidDisplayHandle, AndroidNdkWindowHandle, RawDisplayHandle, RawWindowHandle,
};
use rbx_android_core::{Editor, PlaySession};
use rbx_viewer::{gizmo, pick, CameraInput, Headless};
use serde_json::{json, Value};

enum Message {
    Surface(NativeWindow, u32, u32, u64),
    Detach(mpsc::Sender<()>),
    Command(Value),
}

struct Bridge {
    sender: mpsc::Sender<Message>,
    snapshot: Arc<RwLock<String>>,
}
static BRIDGE: OnceLock<Bridge> = OnceLock::new();
static SURFACE_SERIAL: AtomicU64 = AtomicU64::new(0);

fn publish(snapshot: &RwLock<String>, value: &Value) {
    *snapshot.write().unwrap_or_else(|e| e.into_inner()) = value.to_string();
}

#[no_mangle]
pub extern "system" fn Java_com_rbxnative_androidport_MainActivity_nativeInit(
    mut env: JNIEnv,
    _: JClass,
    files: JString,
    cache: JString,
    content_apks: JString,
) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("RbxStudio")
            .with_max_level(log::LevelFilter::Info),
    );
    let files = match env.get_string(&files) {
        Ok(s) => PathBuf::from(s.to_string_lossy().into_owned()),
        Err(_) => return,
    };
    let cache = match env.get_string(&cache) {
        Ok(s) => PathBuf::from(s.to_string_lossy().into_owned()),
        Err(_) => return,
    };
    let content_apks = env
        .get_string(&content_apks)
        .ok()
        .and_then(|s| serde_json::from_str::<Vec<PathBuf>>(&s.to_string_lossy()).ok())
        .unwrap_or_default();
    BRIDGE.get_or_init(|| {
        // Set before starting any render/fetch workers. Never read OS credentials.
        std::env::set_var("XDG_CACHE_HOME", &cache);
        std::env::set_var("XDG_CONFIG_HOME", &files);
        std::env::set_var("TMPDIR", &cache);
        std::env::set_var("RBX_NO_KEYRING", "1");
        std::env::set_var("RBX_NATIVE_ASSET_CACHE_MB", "256");
        std::env::set_var("RBX_NATIVE_ROBLOX_RESOURCES", files.join("roblox-resources"));
        if let Ok(paths) = std::env::join_paths(&content_apks) {
            std::env::set_var("RBX_NATIVE_ANDROID_APKS", paths);
        }
        rbx_android_core::resources::begin_background_preparation();
        let (sender, receiver) = mpsc::channel();
        let snapshot = Arc::new(RwLock::new(
            json!({"status":"Inicializando o RBX Studio…"}).to_string(),
        ));
        let shared = snapshot.clone();
        thread::Builder::new()
            .name("rbx-studio-render".into())
            .spawn(move || {
                let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    run(receiver, files, shared.clone())
                }));
                let failure = match result {
                    Ok(Ok(())) => return,
                    Ok(Err(error)) => error,
                    Err(panic) => panic
                        .downcast_ref::<String>()
                        .cloned()
                        .or_else(|| panic.downcast_ref::<&str>().map(|s| s.to_string()))
                        .unwrap_or_else(|| "Pânico no núcleo nativo".into()),
                };
                log::error!("{failure}");
                publish(
                    &shared,
                    &json!({"fatal":failure,"status":"Falha no núcleo nativo"}),
                );
            })
            .expect("render worker could not start");
        Bridge { sender, snapshot }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_rbxnative_androidport_MainActivity_nativeSurface(
    env: JNIEnv,
    _: JClass,
    surface: JObject,
    width: jint,
    height: jint,
) {
    if let Some(bridge) = BRIDGE.get() {
        // ANativeWindow_fromSurface acquires an owned reference; SurfaceState
        // releases the Vulkan surface before dropping this native window.
        let window =
            unsafe { NativeWindow::from_surface(env.get_native_interface(), surface.as_raw()) };
        if let Some(window) = window {
            let serial = SURFACE_SERIAL.fetch_add(1, Ordering::SeqCst) + 1;
            let _ = bridge.sender.send(Message::Surface(
                window,
                width.max(1) as u32,
                height.max(1) as u32,
                serial,
            ));
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rbxnative_androidport_MainActivity_nativeDetach(
    _: JNIEnv,
    _: JClass,
) {
    if let Some(bridge) = BRIDGE.get() {
        // Invalidate immediately, even if a shader is still being compiled.
        // No frame may be presented after SurfaceHolder destruction returns.
        SURFACE_SERIAL.fetch_add(1, Ordering::SeqCst);
        let (tx, rx) = mpsc::channel();
        if bridge.sender.send(Message::Detach(tx)).is_ok() {
            // SurfaceHolder requires the render worker to release the surface
            // before its callback returns. The render thread never needs Java.
            if let Err(error) = rx.recv_timeout(Duration::from_secs(4)) {
                log::error!("Surface detach: {error}");
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rbxnative_androidport_MainActivity_nativeCommand(
    mut env: JNIEnv,
    _: JClass,
    text: JString,
) {
    if let (Some(bridge), Ok(text)) = (BRIDGE.get(), env.get_string(&text)) {
        match serde_json::from_str::<Value>(&text.to_string_lossy()) {
            Ok(value) => {
                let _ = bridge.sender.send(Message::Command(value));
            }
            Err(error) => log::error!("Android command: {error}"),
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rbxnative_androidport_MainActivity_nativeSnapshot(
    env: JNIEnv,
    _: JClass,
) -> jstring {
    let text = BRIDGE
        .get()
        .map(|b| b.snapshot.read().unwrap_or_else(|e| e.into_inner()).clone())
        .unwrap_or_else(|| "{}".into());
    env.new_string(text)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// winit's Android backend is a transitive part of the upstream viewer. This
// app uses SurfaceView/JNI instead of NativeActivity, so that entry is unused.
#[no_mangle]
pub fn android_main(_: winit::platform::android::activity::AndroidApp) {}

struct SurfaceState {
    // Drop order matters: the surface holds a raw ANativeWindow handle.
    surface: wgpu::Surface<'static>,
    viewer: Headless,
    device: wgpu::Device,
    queue: wgpu::Queue,
    config: wgpu::SurfaceConfiguration,
    present: crate::present::Present,
    serial: u64,
    _window: NativeWindow,
    _instance: wgpu::Instance,
}

impl SurfaceState {
    fn new(
        window: NativeWindow,
        width: u32,
        height: u32,
        editor: &Editor,
        serial: u64,
        quality: u8,
        render_cap: u32,
    ) -> Result<Self, String> {
        let mut descriptor = wgpu::InstanceDescriptor::new_without_display_handle_from_env();
        descriptor.backends = wgpu::Backends::VULKAN | wgpu::Backends::GL;
        // Older Android SPIR-V parsers reject newer debug source-language
        // operands. Retain validation and omit optional shader debug records.
        descriptor.flags.remove(wgpu::InstanceFlags::DEBUG);
        let instance = wgpu::Instance::new(descriptor);
        let surface = unsafe {
            instance.create_surface_unsafe(wgpu::SurfaceTargetUnsafe::RawHandle {
                raw_display_handle: Some(RawDisplayHandle::Android(AndroidDisplayHandle::new())),
                raw_window_handle: RawWindowHandle::AndroidNdk(AndroidNdkWindowHandle::new(
                    window.ptr().cast(),
                )),
            })
        }
        .map_err(|e| e.to_string())?;
        let (mut viewer, device, queue, config) = Headless::for_surface(
            &editor.dom,
            &instance,
            &surface,
            width,
            height,
            rbx_viewer::QualityLevel::Level(quality),
        )?;
        // Camera, baseplate and lighting come from the original Studio template.
        let camera = editor
            .dom
            .root_refs()
            .iter()
            .filter_map(|r| editor.dom.get(*r))
            .find(|i| i.class() == "Workspace")
            .and_then(|w| {
                w.children()
                    .iter()
                    .filter_map(|r| editor.dom.get(*r))
                    .find(|i| i.class() == "Camera")
            });
        if let Some(camera) = camera {
            if let Some(rbx_dom::Variant::CFrame(cf)) = camera.properties().get("CFrame") {
                let eye = Vec3::new(cf.position.x, cf.position.y, cf.position.z);
                let forward = -Mat3::from_cols_array(&cf.rotation).transpose().z_axis;
                viewer.open_at(eye.to_array(), (eye + forward * 30.).to_array(), 70.);
            }
        }
        log::info!("Android surface ready {width}x{height} {:?}", config.format);
        let (w, h) = render_extent(config.width, config.height, render_cap);
        let present = crate::present::Present::new(&device, config.format, w, h);
        Ok(Self {
            surface,
            viewer,
            device,
            queue,
            config,
            present,
            serial,
            _window: window,
            _instance: instance,
        })
    }

    fn resize(&mut self, width: u32, height: u32, serial: u64, render_cap: u32) {
        self.serial = serial;
        if (self.config.width, self.config.height) != (width, height) {
            self.config.width = width.max(1);
            self.config.height = height.max(1);
            self.surface.configure(&self.device, &self.config);
        }
        let (w, h) = render_extent(self.config.width, self.config.height, render_cap);
        self.present.resize(&self.device, w, h);
    }

    fn draw(&mut self) -> Result<bool, String> {
        if self.serial != SURFACE_SERIAL.load(Ordering::SeqCst) {
            return Ok(false);
        }
        let frame = match self.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(f)
            | wgpu::CurrentSurfaceTexture::Suboptimal(f) => f,
            wgpu::CurrentSurfaceTexture::Lost | wgpu::CurrentSurfaceTexture::Outdated => {
                self.surface.configure(&self.device, &self.config);
                return Ok(false);
            }
            wgpu::CurrentSurfaceTexture::Timeout | wgpu::CurrentSurfaceTexture::Occluded => {
                return Ok(false)
            }
            wgpu::CurrentSurfaceTexture::Validation => {
                return Err("A GPU rejeitou o frame da superfície".into())
            }
        };
        self.viewer.draw_to_surface_texture(
            &self.present.texture,
            self.present.texture.width(),
            self.present.texture.height(),
        );
        self.present.draw(&self.device, &self.queue, &frame.texture);
        self.queue.present(frame);
        Ok(true)
    }

    fn ray(&self, x: f32, y: f32) -> pick::Ray {
        let size = Vec2::new(self.config.width as f32, self.config.height as f32);
        let pose = self.viewer.view_pose();
        pick::ray_through(
            pose.view_projection(false, size.x / size.y),
            pick::ndc_of(Vec2::new(x, y), size),
        )
    }
}

struct Play {
    session: PlaySession,
    saved_pose: Option<rbx_viewer::Pose>,
    yaw: f32,
    pitch: f32,
    distance: f32,
    stick: Vec2,
    input_scale: Vec2,
    keys: [bool; 4],
    jump: bool,
}

impl Play {
    fn input(&mut self, c: &Value) {
        match c["op"].as_str().unwrap_or("") {
            "player" => {
                self.stick = Vec2::new(
                    c["x"].as_f64().unwrap_or(0.) as f32,
                    c["y"].as_f64().unwrap_or(0.) as f32,
                )
                .clamp_length_max(1.)
            }
            "jump" => {
                self.jump = true;
                self.session.key("Space", true);
                self.session.key("Space", false);
            }
            "player_key" => {
                if let Some(i) = ["w", "a", "s", "d"]
                    .iter()
                    .position(|k| Some(*k) == c["key"].as_str())
                {
                    self.keys[i] = c["down"].as_bool().unwrap_or(false);
                    self.session.key(&["W", "A", "S", "D"][i], self.keys[i]);
                }
            }
            "camera" if c["kind"] == "zoom" => {
                self.distance = (self.distance
                    * (-c["value"].as_f64().unwrap_or(0.) as f32 * 0.25).exp())
                .clamp(4., 64.)
            }
            "touch" if c["phase"] == "tap" => self.session.click(
                c["x"].as_f64().unwrap_or(0.) as f32 * self.input_scale.x,
                c["y"].as_f64().unwrap_or(0.) as f32 * self.input_scale.y,
            ),
            "touch" if c["phase"] == "move" => {
                self.yaw -= c["dx"].as_f64().unwrap_or(0.) as f32 * 0.005;
                self.pitch =
                    (self.pitch - c["dy"].as_f64().unwrap_or(0.) as f32 * 0.005).clamp(-1.3, 0.15);
            }
            _ => {}
        }
    }

    fn step(
        &mut self,
        editor: &mut Editor,
        a: &mut SurfaceState,
        dt: Duration,
    ) -> Result<(), String> {
        // Restore Stop on a failed game instead of killing the editor worker.
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| self.advance(editor, a, dt)))
            .unwrap_or_else(|panic| {
                Err(panic.downcast_ref::<String>().cloned()
                    .or_else(|| panic.downcast_ref::<&str>().map(|s| s.to_string()))
                    .unwrap_or_else(|| "Falha na sessão de jogo".into()))
            })
    }

    fn advance(
        &mut self,
        editor: &mut Editor,
        a: &mut SurfaceState,
        dt: Duration,
    ) -> Result<(), String> {
        let input = (self.stick
            + Vec2::new(
                (self.keys[3] as i8 - self.keys[1] as i8) as f32,
                (self.keys[0] as i8 - self.keys[2] as i8) as f32,
            ))
        .clamp_length_max(1.);
        let heading = Mat3::from_rotation_y(self.yaw);
        let direction = heading * Vec3::new(input.x, 0., -input.y);
        self.session
            .player_input(direction, std::mem::take(&mut self.jump));
        let (width, height) = (a.present.texture.width(), a.present.texture.height());
        self.input_scale = Vec2::new(
            width as f32 / a.config.width as f32,
            height as f32 / a.config.height as f32,
        );
        self.session.viewport(width, height);
        self.session.step(editor, dt)?;
        if let Some(camera) = self.session.scripted_camera() {
            let eye = camera.cframe.position;
            let forward = camera.cframe.rotation * Vec3::NEG_Z;
            a.viewer
                .open_at(eye.to_array(), (eye + forward * 30.).to_array(), camera.fov);
            return Ok(());
        }
        let Some(position) = self.session.character.position(&editor.dom) else {
            return Ok(());
        };
        let at = position + Vec3::Y * 1.5;
        let offset = Mat3::from_euler(glam::EulerRot::YXZ, self.yaw, self.pitch, 0.)
            * Vec3::Z
            * self.distance;
        let distance = self.session.camera_distance(at, offset);
        let eye = at + offset.normalize_or_zero() * distance;
        self.session.host_camera(eye, at, 70.);
        a.viewer.open_at(eye.to_array(), at.to_array(), 70.);
        Ok(())
    }
}

fn render_extent(width: u32, height: u32, cap: u32) -> (u32, u32) {
    let factor = if cap == 0 {
        1.
    } else {
        (cap as f64 / width.max(height).max(1) as f64).min(1.)
    };
    (
        ((width as f64 * factor).round() as u32).max(1),
        ((height as f64 * factor).round() as u32).max(1),
    )
}

#[derive(Clone)]
enum Drag {
    Scale(rbx_android_core::ScaleGesture),
    Axis {
        axis: gizmo::Axis,
        world: Vec3,
        origin: Vec3,
        value: f32,
        kind: String,
        sign: f32,
    },
    Ring {
        axis: gizmo::Axis,
        origin: Vec3,
        frame: (Vec3, Vec3, Vec3),
        last: f32,
    },
}

fn run(
    receiver: mpsc::Receiver<Message>,
    files: PathBuf,
    snapshot: Arc<RwLock<String>>,
) -> Result<(), String> {
    let mut editor = Editor::open_or_create(files.join("projects/Main.rbxlx"))?;
    let mut active: Option<SurfaceState> = None;
    let mut play: Option<Play> = None;
    let mut quality: u8 = 10;
    let mut render_cap = 1280u32;
    let mut frame_hz = 60u32;
    let mut paused = false;
    let mut redraw = true;
    let mut surface_builds = 0u32;
    let mut drag: Option<Drag> = None;
    let mut last = Instant::now();
    let mut published = Instant::now() - Duration::from_secs(1);
    let mut modified = Instant::now();
    let mut fps_start = Instant::now();
    let mut frames = 0;
    let mut presented_frames = 0u64;
    let mut fps = 0.;
    let mut next_frame = Instant::now();
    let mut render_ms = 0.;
    let mut idle = false;
    let mut cached = editor.snapshot();
    let mut cached_revision = editor.revision;
    let mut cached_selection = editor.selection.clone();
    let mut tree_dirty = false;
    loop {
        let timeout = if active.is_some() && !paused {
            next_frame.saturating_duration_since(Instant::now())
        } else {
            Duration::from_millis(150)
        };
        let first = match receiver.recv_timeout(timeout) {
            Ok(m) => Some(m),
            Err(mpsc::RecvTimeoutError::Timeout) => None,
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        };
        let mut messages = first.into_iter().collect::<Vec<_>>();
        messages.extend(receiver.try_iter().take(128));
        for message in messages {
            match message {
                Message::Surface(window, width, height, serial) => {
                    if serial != SURFACE_SERIAL.load(Ordering::SeqCst) {
                        continue;
                    }
                    if let Some(a) = active.as_mut().filter(|a| a._window.ptr() == window.ptr()) {
                        // Android may resize the same Surface for panels/IME.
                        // Keep every mesh, material, pipeline and GPU resource.
                        a.resize(width, height, serial, render_cap);
                        redraw = true;
                        continue;
                    }
                    let pose = active.as_ref().map(|s| s.viewer.view_pose());
                    active.take();
                    let mut surface = SurfaceState::new(
                        window, width, height, &editor, serial, quality, render_cap,
                    )?;
                    surface_builds += 1;
                    if serial != SURFACE_SERIAL.load(Ordering::SeqCst) {
                        continue;
                    }
                    if let Some(pose) = pose {
                        let (forward, ..) = pose.basis();
                        surface.viewer.open_at(
                            pose.position.to_array(),
                            (pose.position + forward * 30.).to_array(),
                            pose.fov_degrees,
                        );
                    }
                    active = Some(surface);
                    last = Instant::now();
                    next_frame = last;
                    redraw = true;
                }
                Message::Detach(done) => {
                    active.take();
                    drag = None;
                    editor.end_gesture();
                    if play.is_none() && editor.dirty {
                        if let Err(e) = editor.save() {
                            editor.logs.push(e);
                        }
                    }
                    let _ = done.send(());
                }
                Message::Command(command) => {
                    let op = command["op"].as_str().unwrap_or("");
                    let result = match op {
                        "pause" => {
                            paused = true;
                            if let Some(p) = &mut play {
                                p.stick = Vec2::ZERO;
                                for key in ["W", "A", "S", "D"] {
                                    p.session.key(key, false);
                                }
                                p.keys = [false; 4];
                                p.jump = false;
                            }
                            Ok(())
                        }
                        "resume" => {
                            paused = false;
                            last = Instant::now();
                            next_frame = last;
                            Ok(())
                        }
                        "player" | "jump" | "player_key" => {
                            if let Some(p) = &mut play {
                                p.input(&command);
                            }
                            Ok(())
                        }
                        "touch" => {
                            if let Some(p) = &mut play {
                                p.input(&command);
                                Ok(())
                            } else if let Some(a) = &mut active {
                                if command["phase"] == "down" {
                                    editor.begin_gesture();
                                }
                                if command["phase"] == "up" {
                                    editor.end_gesture();
                                }
                                touch(&mut editor, a, &command, &mut drag)
                            } else {
                                Ok(())
                            }
                        }
                        "camera" => {
                            if let Some(p) = &mut play {
                                p.input(&command);
                            } else if let Some(a) = &mut active {
                                camera(a, &editor, &command);
                            }
                            Ok(())
                        }
                        "play" => {
                            if let Some(p) = play.take() {
                                let pose = p.saved_pose;
                                p.session.stop(&mut editor);
                                editor.dom.take_changes();
                                if let Some(a) = &mut active {
                                    a.viewer.reload(&editor.dom)?;
                                    if let Some(pose) = pose {
                                        let (forward, ..) = pose.basis();
                                        a.viewer.open_at(
                                            pose.position.to_array(),
                                            (pose.position + forward * 30.).to_array(),
                                            pose.fov_degrees,
                                        );
                                    }
                                }
                                tree_dirty = true;
                                Ok(())
                            } else {
                                let saved_pose = active.as_ref().map(|a| a.viewer.view_pose());
                                PlaySession::start(&mut editor).map(|session| {
                                    play = Some(Play {
                                        session,
                                        saved_pose,
                                        yaw: 0.,
                                        pitch: -0.32,
                                        distance: 16.,
                                        stick: Vec2::ZERO,
                                        input_scale: Vec2::ONE,
                                        keys: [false; 4],
                                        jump: false,
                                    });
                                    drag = None;
                                    tree_dirty = true;
                                    last = Instant::now();
                                })
                            }
                        }
                        "animation_play" => match &mut play {
                            Some(p) => p.session.play_sequence(
                                &editor,
                                rbx_dom::Ref::new(command["id"].as_u64().unwrap_or(0) as u32),
                            ),
                            None => Err("Inicie o Play para reproduzir a animação".into()),
                        },
                        "animation_stop" => {
                            if let Some(p) = &mut play {
                                p.session.stop_animation();
                            }
                            Ok(())
                        }
                        "run" if play.is_some() => play
                            .as_mut()
                            .unwrap()
                            .session
                            .run_server(command["source"].as_str().unwrap_or("")),
                        "content_reload" => {
                            if let Some(a) = &mut active {
                                a.viewer.reload_assets(&editor.dom)?;
                            }
                            editor.status = "Recursos locais recarregados".into();
                            Ok(())
                        }
                        "content_import" => (|| -> Result<(), String> {
                            let source = PathBuf::from(command["path"].as_str().unwrap_or(""));
                            let result = (|| {
                                let count = rbx_assets::AndroidContent::validate(&source)
                                    .map_err(|e| e.to_string())?;
                                let name = std::path::Path::new(
                                    command["name"].as_str().unwrap_or("content.zip"),
                                )
                                .file_name()
                                .and_then(|n| n.to_str())
                                .unwrap_or("content.zip");
                                let name: String = name
                                    .chars()
                                    .take(140)
                                    .map(|c| {
                                        if c.is_alphanumeric() || "._-".contains(c) {
                                            c
                                        } else {
                                            '_'
                                        }
                                    })
                                    .collect();
                                let name = if name.to_ascii_lowercase().ends_with(".apk")
                                    || name.to_ascii_lowercase().ends_with(".zip")
                                {
                                    name
                                } else {
                                    format!("{name}.zip")
                                };
                                let directory = files.join("resources");
                                std::fs::create_dir_all(&directory).map_err(|e| e.to_string())?;
                                std::fs::rename(&source, directory.join(&name))
                                    .map_err(|e| e.to_string())?;
                                if let Some(a) = &mut active {
                                    a.viewer.reload_assets(&editor.dom)?;
                                }
                                editor.status =
                                    format!("Pacote {name}: {count} recursos reconhecidos");
                                editor.logs.push(editor.status.clone());
                                Ok(())
                            })();
                            if result.is_err() {
                                let _ = std::fs::remove_file(&source);
                            }
                            result
                        })(),
                        "resolution" => {
                            render_cap = command["value"].as_u64().unwrap_or(1280).min(4096) as u32;
                            if let Some(a) = &mut active {
                                a.resize(a.config.width, a.config.height, a.serial, render_cap);
                            }
                            Ok(())
                        }
                        "frame_limit" => {
                            frame_hz = command["value"].as_u64().unwrap_or(60).clamp(20, 60) as u32;
                            Ok(())
                        }
                        "quality" => {
                            quality = command["value"].as_u64().unwrap_or(10).clamp(1, 21) as u8;
                            if let Some(a) = &mut active {
                                a.viewer
                                    .set_quality(rbx_viewer::QualityLevel::Level(quality));
                            }
                            Ok(())
                        }
                        "focus" => {
                            if let Some(a) = &mut active {
                                focus(a, &editor);
                            }
                            Ok(())
                        }
                        _ if play.is_some()
                            && !["select", "tool", "local", "snap", "check"].contains(&op) =>
                        {
                            Err("Pare o Play para editar e salvar".into())
                        }
                        _ => editor.command(&command),
                    };
                    if let Err(error) = result {
                        log::error!("{error}");
                        editor.logs.push(error.clone());
                        editor.status = error;
                    }
                    if ["undo", "redo", "new", "open", "project_new", "project_open"].contains(&op)
                    {
                        if let Some(a) = &mut active {
                            a.viewer.reload(&editor.dom)?;
                        }
                    }
                    if !["camera", "touch", "player", "player_key", "jump"].contains(&op)
                        || editor.dirty
                    {
                        modified = Instant::now();
                    }
                    redraw = true;
                }
            }
        }
        let now = Instant::now();
        if active.is_some() && !paused && now >= next_frame {
            let dt = (now - last).min(Duration::from_millis(100));
            last = now;
            next_frame = now + Duration::from_secs_f64(1. / frame_hz as f64);
            if let (Some(p), Some(a)) = (&mut play, &mut active) {
                if let Err(error) = p.step(&mut editor, a, dt) {
                    let p = play.take().unwrap();
                    p.session.stop(&mut editor);
                    editor.status = format!("Play interrompido: {error}");
                    editor.logs.push(editor.status.clone());
                    a.viewer.reload(&editor.dom)?;
                    tree_dirty = true;
                }
            }
            let changes = editor.dom.take_changes();
            tree_dirty |= changes.iter().any(|c| {
                matches!(
                    c,
                    rbx_dom::Change::Added(_)
                        | rbx_dom::Change::Removed(_)
                        | rbx_dom::Change::Parent { .. }
                        | rbx_dom::Change::Class(_)
                ) || matches!(c,rbx_dom::Change::Property{name,..} if name=="Name")
            });
            if let Some(a) = &mut active {
                if !changes.is_empty() {
                    a.viewer.apply_changes(&editor.dom, &changes)?;
                }
                if redraw || !changes.is_empty() {
                    a.viewer.set_selection(&pick::selection(
                        &editor.dom,
                        editor.database(),
                        &editor.selection,
                    ));
                }
                let kind = match editor.tool.as_str() {
                    "move" => Some(gizmo::Kind::Move),
                    "rotate" => Some(gizmo::Kind::Rotate),
                    "scale" => Some(gizmo::Kind::Scale),
                    _ => None,
                };
                a.viewer.set_gizmo(kind.map(|kind| gizmo::Gizmo {
                    kind,
                    local: editor.local,
                    ..Default::default()
                }));
                let moved = a.viewer.tick(dt);
                idle = !(redraw
                    || moved
                    || !changes.is_empty()
                    || play.is_some()
                    || a.viewer.animating());
                let render_start = Instant::now();
                if !idle && a.draw()? {
                    frames += 1;
                    presented_frames += 1;
                    render_ms = render_start.elapsed().as_secs_f64() * 1000.;
                }
                redraw = false;
                editor.logs.extend(a.viewer.drain_warnings());
            }
        }
        if fps_start.elapsed() >= Duration::from_secs(1) {
            fps = frames as f64 / fps_start.elapsed().as_secs_f64();
            frames = 0;
            fps_start = now;
        }
        if play.is_none() && editor.dirty && modified.elapsed() > Duration::from_millis(900) {
            if let Err(e) = editor.save() {
                editor.status = e.clone();
                editor.logs.push(e);
                modified = now;
            }
        }
        if published.elapsed() > Duration::from_millis(250) {
            if editor.logs.len() > 300 {
                editor.logs.drain(..editor.logs.len() - 300);
            }
            if cached_revision != editor.revision
                || cached_selection != editor.selection
                || tree_dirty
            {
                cached = editor.snapshot();
                cached_revision = editor.revision;
                cached_selection = editor.selection.clone();
                tree_dirty = false;
            }
            cached["status"] = json!(editor.status);
            cached["logs"] = json!(editor.logs);
            cached["tool"] = json!(editor.tool);
            cached["snap"] = json!(editor.snap);
            cached["local"] = json!(editor.local);
            cached["dirty"] = json!(editor.dirty);
            cached["playing"] = json!(play.is_some());
            cached["runtime"]=play.as_ref().map(|p|json!({"host":"rook-engine::Session","full_projection_syncs":p.session.full_syncs(),"started_scripts":p.session.started_scripts()})).unwrap_or(Value::Null);
            cached["player"] = play.as_ref().and_then(|p|p.session.character.position(&editor.dom).map(|v|json!({"position":v.to_array(),"state":p.session.state,"root":p.session.character.root.value()}))).unwrap_or(Value::Null);
            cached["fps"] = json!(fps);
            cached["presented_frames"] = json!(presented_frames);
            cached["idle"] = json!(idle);
            cached["render_ms"] = json!(render_ms);
            cached["resources"] = rbx_android_core::resources::shared()
                .map(|r| r.diagnostics()).unwrap_or(Value::Null);
            cached["resolution"] = active
                .as_ref()
                .map(|a| json!([a.present.texture.width(), a.present.texture.height()]))
                .unwrap_or(Value::Null);
            cached["surface_builds"] = json!(surface_builds);
            cached["frame_limit"] = json!(frame_hz);
            cached["render_cap"] = json!(render_cap);
            cached["quality"] = json!(quality);
            publish(&snapshot, &cached);
            published = now;
        }
    }
    if play.is_none() && editor.dirty {
        editor.save()?;
    }
    Ok(())
}

fn target(editor: &Editor) -> Vec3 {
    editor
        .selection
        .first()
        .and_then(|id| pick::model_of(&editor.dom, *id))
        .map(|m| m.w_axis.truncate())
        .unwrap_or(Vec3::ZERO)
}

fn focus(a: &mut SurfaceState, editor: &Editor) {
    let center = target(editor);
    let eye = center + Vec3::new(12., 10., 16.);
    a.viewer.open_at(eye.to_array(), center.to_array(), 70.);
}

fn camera(a: &mut SurfaceState, editor: &Editor, c: &Value) {
    let value = |k: &str| c[k].as_f64().unwrap_or(0.) as f32;
    if c["kind"] == "zoom" {
        a.viewer.input(CameraInput::Wheel {
            notches: value("value"),
        });
        return;
    }
    let pose = a.viewer.view_pose();
    let (forward, right, up) = pose.basis();
    let dx = value("dx");
    let dy = value("dy");
    match c["kind"].as_str().unwrap_or("") {
        "orbit" => {
            let center = target(editor);
            let offset = pose.position - center;
            let rot = glam::Quat::from_axis_angle(Vec3::Y, -dx * 0.005)
                * glam::Quat::from_axis_angle(right, -dy * 0.005);
            let eye = center + rot * offset;
            a.viewer
                .open_at(eye.to_array(), center.to_array(), pose.fov_degrees);
        }
        "pan" => {
            let speed = (pose.position - target(editor)).length().max(5.) * 0.0015;
            let eye = pose.position + (-right * dx + up * dy) * speed;
            a.viewer.open_at(
                eye.to_array(),
                (eye + forward * 30.).to_array(),
                pose.fov_degrees,
            );
        }
        "look" => {
            a.viewer.input(CameraInput::LookButton(true));
            a.viewer.input(CameraInput::MouseLook { dx, dy });
        }
        "release" => a.viewer.input(CameraInput::Release),
        "key" => {
            let key = match c["key"].as_str().unwrap_or("") {
                "w" => rbx_viewer::CameraKey::Forward,
                "s" => rbx_viewer::CameraKey::Back,
                "a" => rbx_viewer::CameraKey::Left,
                "d" => rbx_viewer::CameraKey::Right,
                "e" => rbx_viewer::CameraKey::Up,
                "q" => rbx_viewer::CameraKey::Down,
                _ => return,
            };
            a.viewer.input(CameraInput::Key {
                key,
                pressed: c["down"].as_bool().unwrap_or(false),
            });
        }
        _ => {}
    }
}

fn touch(
    editor: &mut Editor,
    a: &mut SurfaceState,
    c: &Value,
    drag: &mut Option<Drag>,
) -> Result<(), String> {
    let x = c["x"].as_f64().unwrap_or(0.) as f32;
    let y = c["y"].as_f64().unwrap_or(0.) as f32;
    let ray = a.ray(x, y);
    match c["phase"].as_str().unwrap_or("") {
        "down" => {
            *drag = None;
            if let Some(model) = editor
                .selection
                .first()
                .and_then(|id| pick::model_of(&editor.dom, *id))
            {
                let origin = model.w_axis.truncate();
                let pose = a.viewer.view_pose();
                let handles = gizmo::Handles::new(
                    origin,
                    gizmo::basis(if editor.local {
                        Some(Mat3::from_mat4(model))
                    } else {
                        None
                    }),
                    gizmo::handle_length(origin, pose, false, a.present.texture.height() as f32),
                );
                if editor.tool == "rotate" {
                    if let Some(axis) = handles.grab_ring(ray) {
                        let frame = handles.ring_frame(axis);
                        if let Some((last, ..)) = gizmo::ring_crossing(origin, frame, ray) {
                            *drag = Some(Drag::Ring {
                                axis,
                                origin,
                                frame,
                                last,
                            });
                        }
                    }
                } else if editor.tool == "scale" {
                    *drag =
                        rbx_android_core::ScaleGesture::grab(editor, pose, ray).map(Drag::Scale);
                } else if editor.tool == "move" {
                    if let Some(axis) = handles.grab(ray) {
                        let world = handles.direction(axis);
                        if let Some(value) = gizmo::along_axis(origin, world, ray) {
                            *drag = Some(Drag::Axis {
                                axis,
                                world,
                                origin,
                                value,
                                kind: "move".into(),
                                sign: 1.,
                            });
                        }
                    }
                }
            }
        }
        "move" => {
            match drag {
                Some(Drag::Scale(gesture)) => gesture.advance(editor, ray)?,
                Some(Drag::Axis {
                    axis,
                    world,
                    origin,
                    value,
                    kind,
                    sign,
                }) => {
                    if let Some(at) = gizmo::along_axis(*origin, *world, ray) {
                        let delta = at - *value;
                        let step = if editor.snap > 0. {
                            (delta / editor.snap).round() * editor.snap
                        } else {
                            delta
                        };
                        if step.abs() > 0.0001 {
                            let v = if kind == "move" {
                                // The adapter already resolved local axes into world space.
                                if editor.local {
                                    let model = editor
                                        .selection
                                        .first()
                                        .and_then(|id| pick::model_of(&editor.dom, *id))
                                        .ok_or("Seleção ausente")?;
                                    Mat3::from_cols_array_2d(&[
                                        gizmo::basis(Some(Mat3::from_mat4(model)))[0].to_array(),
                                        gizmo::basis(Some(Mat3::from_mat4(model)))[1].to_array(),
                                        gizmo::basis(Some(Mat3::from_mat4(model)))[2].to_array(),
                                    ])
                                    .transpose()
                                        * (*world * step)
                                } else {
                                    *world * step
                                }
                            } else {
                                axis_unit(*axis) * step * (*sign)
                            };
                            editor.transform(kind, v.to_array())?;
                            *value += step;
                        }
                    }
                }
                Some(Drag::Ring {
                    axis,
                    origin,
                    frame,
                    last,
                }) => {
                    if let Some((at, ..)) = gizmo::ring_crossing(*origin, *frame, ray) {
                        let delta = gizmo::angle_step(*last, at);
                        let degrees = delta.to_degrees();
                        if degrees.abs() > 0.1 {
                            editor.transform("rotate", (axis_unit(*axis) * degrees).to_array())?;
                            *last = at;
                        }
                    }
                }
                None => camera(
                    a,
                    editor,
                    &json!({"kind":"orbit","dx":c["dx"],"dy":c["dy"]}),
                ),
            }
        }
        "tap" => {
            let meshes = a.viewer.pick_meshes();
            let hit = pick::parts_along(&editor.dom, editor.database(), &meshes, ray)
                .first()
                .copied();
            editor.selection = hit.map(|id| vec![id]).unwrap_or_default();
        }
        "up" => {
            *drag = None;
        }
        _ => {}
    }
    Ok(())
}

fn axis_unit(axis: gizmo::Axis) -> Vec3 {
    match axis {
        gizmo::Axis::X => Vec3::X,
        gizmo::Axis::Y => Vec3::Y,
        gizmo::Axis::Z => Vec3::Z,
    }
}
