//! Original Rook server/client host, projected into rbx-native's renderer.
use crate::{projection::Projection, Character, Editor};
use glam::Vec3;
use rbx_dom::WeakDom;
use rook_dom::InstanceId;
use rook_engine::{session::Session, DataModel, Options};
use rook_types::{Value, Vector3};
use std::{sync::Arc, time::Duration};

pub struct PlaySession {
    animation: Option<crate::animation::ClipPlayer>,
    before: WeakDom,
    selected: Vec<rbx_dom::Ref>,
    dirty: bool,
    session: Session,
    client: usize,
    projection: Projection,
    cursor: u64,
    accumulator: f64,
    pending_jump: bool,
    pub character: Character,
    pub state: String,
}
impl PlaySession {
    pub fn start(editor: &mut Editor) -> Result<Self, String> {
        Self::start_configured(editor, None)
    }
    pub fn start_with_options(editor: &mut Editor, options: Options) -> Result<Self, String> {
        Self::start_configured(editor, Some(options))
    }
    fn start_configured(editor: &mut Editor, supplied: Option<Options>) -> Result<Self, String> {
        editor.end_gesture();
        crate::platform::ensure_platform();
        let before = editor.dom.clone();
        let selected = editor.selection.clone();
        let dirty = editor.dirty;
        let local = rbx_assets::AndroidContent::from_env();
        let mut config = rook_content::resolver::Config::empty();
        config.host_content = Some(rook_content::resolver::HostContent::new(
            move |path| match local.read(path) {
                Ok(bytes) => bytes,
                Err(e) => {
                    log::warn!("{e}");
                    None
                }
            },
        ));
        config.network = true;
        config.cache_dir = std::env::var_os("XDG_CACHE_HOME")
            .map(|p| std::path::PathBuf::from(p).join("rook-content"));
        config.cache_max = Some(128 << 20);
        let resources = crate::resources::shared();
        let options = if let Some(options) = supplied { options } else if let Some(resources) = resources {
            if matches!(resources.state(), crate::resources::Preparation::Idle) { resources.prepare(false); }
            resources.play_options()?
        } else { Options {
            script_timeout: Duration::from_millis(20),
            memory_limit: 256 << 20,
            wait_for_sounds: false,
            content: Some(Arc::new(rook_content::Resolver::new(config))),
            ..Options::default()
        }};
        let bytes = rbx_xml::serialize(&before).map_err(|e| e.to_string())?;
        let mut server =
            DataModel::load_place(bytes.as_bytes(), &options).map_err(|e| e.to_string())?;
        server.start();
        let mut session = Session::new(server, options);
        let client = session.join("LocalPlayer", 0).map_err(|e| e.to_string())?;
        session.step();
        session.client_mut(client).unwrap().set_auto_trim(false);
        let mut projection = Projection::default();
        let result = {
            let w = session.client(client).unwrap().runtime().world();
            projection.full(&w.dom, &mut editor.dom)
        };
        if let Err(e) = result {
            editor.dom = before;
            return Err(e);
        }
        let dm = session.client(client).unwrap();
        let cursor = dm.runtime().world().dom.journal().end();
        let character = match character(dm, &projection) {
            Some(c) => c,
            None => {
                editor.dom = before;
                return Err("Rook did not create a playable character".into());
            }
        };
        let state = humanoid_state(dm);
        editor.selection.clear();
        editor.status = "Play local · servidor e cliente".into();
        let mut p = Self {
            animation: None,
            before,
            selected,
            dirty,
            session,
            client,
            projection,
            cursor,
            accumulator: 0.,
            pending_jump: false,
            character,
            state,
        };
        p.logs(editor);
        Ok(p)
    }
    pub fn player_input(&mut self, direction: Vec3, jump: bool) {
        self.pending_jump |= jump;
        if let Some(h) = humanoid(self.session.client(self.client).unwrap()) {
            let dm = self.session.client(self.client).unwrap();
            let v = direction.clamp_length_max(1.);
            dm.set_property(
                h,
                "MoveDirection",
                Value::Vector3(Vector3::new(v.x, v.y, v.z)),
            );
            if self.pending_jump {
                dm.set_property(h, "Jump", Value::Bool(true));
            }
        }
    }
    pub fn key(&mut self, code: &str, down: bool) {
        self.session.input(
            self.client,
            rook_engine::InputEvent::Key {
                code: code.into(),
                down,
            },
        );
    }
    pub fn input(&mut self, event: rook_engine::InputEvent) {
        self.session.input(self.client, event);
    }
    pub fn run_server(&mut self, source: &str) -> Result<(), String> {
        self.session.server().run_extra_script(source)
    }
    pub fn run_client(&mut self, source: &str) -> Result<(), String> {
        self.session
            .client(self.client)
            .unwrap()
            .run_extra_script(source)
    }
    pub fn full_syncs(&self) -> u64 {
        self.projection.full_syncs
    }
    pub fn started_scripts(&self) -> (usize, usize) {
        (
            self.session.server().started_scripts(),
            self.session.client(self.client).unwrap().started_scripts(),
        )
    }
    pub fn step(&mut self, editor: &mut Editor, dt: Duration) -> Result<(), String> {
        let dt = dt.as_secs_f64().clamp(0., 0.1);
        self.accumulator += dt;
        let fixed = 1. / 60.;
        let mut steps = 0;
        while self.accumulator + 1e-9 >= fixed && steps < 6 {
            self.session.step_server();
            self.accumulator = (self.accumulator - fixed).max(0.);
            steps += 1;
        }
        if let Some(a) = &mut self.animation {
            a.step(dt as f32, self.session.client(self.client).unwrap());
        }
        self.session.step_clients_by(dt);
        self.pending_jump = false;
        let dm = self.session.client_mut(self.client).unwrap();
        dm.capture_host_changes(&mut self.cursor, |dom, changes| {
            self.projection.changes(dom, changes, &mut editor.dom)
        })?;
        if let Some(c) = character(dm, &self.projection) {
            if self.animation.as_ref().is_some_and(|a| a.model != c.model) {
                self.animation = None;
            }
            self.character = c;
            self.state = humanoid_state(dm);
        } else {
            self.state = "Respawning".into();
        }
        let _ = dm.take_render_changes();
        self.logs(editor);
        Ok(())
    }
    pub fn play_sequence(&mut self, editor: &Editor, sequence: rbx_dom::Ref) -> Result<(), String> {
        self.stop_animation();
        self.animation = Some(crate::animation::ClipPlayer::new(
            &editor.dom,
            self.character.model,
            sequence,
            &self.projection,
            self.session.client(self.client).unwrap(),
        )?);
        Ok(())
    }
    pub fn stop_animation(&mut self) {
        if let Some(a) = self.animation.take() {
            a.stop(self.session.client(self.client).unwrap());
        }
    }
    pub fn viewport(&mut self, width: u32, height: u32) {
        self.session
            .client_mut(self.client)
            .unwrap()
            .set_viewport(width, height);
    }
    pub fn click(&mut self, x: f32, y: f32) {
        for e in (rook_engine::ScreenPoint { x, y }).click() {
            self.session.input(self.client, e);
        }
    }
    pub fn scripted_camera(&self) -> Option<rook_engine::CameraView> {
        self.session.client(self.client)?.scripted_camera()
    }
    pub fn host_camera(&self, eye: Vec3, at: Vec3, fov: f32) {
        let dm = self.session.client(self.client).unwrap();
        if let Some(c) = dm.current_camera() {
            dm.set_property(
                c,
                "CFrame",
                Value::CFrame(rook_types::CFrame::look_at(eye, at, Vec3::Y)),
            );
            dm.set_property(
                c,
                "Focus",
                Value::CFrame(rook_types::CFrame::from_position(at)),
            );
            dm.set_property(c, "FieldOfView", Value::Float32(fov));
        }
    }
    pub fn camera_distance(&mut self, origin: Vec3, offset: Vec3) -> f32 {
        let dm = self.session.client_mut(self.client).unwrap();
        let mut w = dm.runtime().world_mut();
        let ignored = local_character(&w.dom).into_iter().collect();
        let filter = rook_physics::Filter {
            instances: ignored,
            respect_can_collide: true,
            ..Default::default()
        };
        let world = &mut *w;
        world
            .physics
            .raycast(&mut world.dom, origin, offset, &filter)
            .map_or(offset.length(), |h| (h.distance - 0.4).max(0.5))
    }
    fn logs(&mut self, editor: &mut Editor) {
        for (side, dm) in [
            ("Servidor", self.session.server()),
            ("Cliente", self.session.client(self.client).unwrap()),
        ] {
            for log in dm.take_log() {
                editor.logs.push(format!(
                    "[{side} {:?}] {}{}",
                    log.kind,
                    log.source.map(|s| format!("{s}: ")).unwrap_or_default(),
                    log.message
                ));
            }
        }
        if editor.logs.len() > 500 {
            editor.logs.drain(..editor.logs.len() - 500);
        }
    }
    pub fn stop(self, editor: &mut Editor) {
        editor.dom = self.before;
        editor.selection = self.selected;
        editor.dirty = self.dirty;
        editor.status = "Edição restaurada".into();
    }
}
fn local_character(dom: &rook_dom::Dom) -> Option<InstanceId> {
    dom.local_player()
        .and_then(|p| dom.ref_property(p, "Character"))
}
fn humanoid(dm: &DataModel) -> Option<InstanceId> {
    let w = dm.runtime().world();
    let c = local_character(&w.dom)?;
    w.dom.find_first_child_of_class(c, "Humanoid")
}
fn humanoid_state(dm: &DataModel) -> String {
    let Some(h) = humanoid(dm) else {
        return "Respawning".into();
    };
    let w = dm.runtime().world();
    w.physics
        .humanoid_state(h)
        .map(|s| s.name().to_owned())
        .unwrap_or_else(|| "AwaitingPhysics".into())
}
fn character(dm: &DataModel, p: &Projection) -> Option<Character> {
    let w = dm.runtime().world();
    let d = &w.dom;
    let player = d.local_player()?;
    let model = local_character(d)?;
    let root = rook_lua::root_part(d, model)?;
    let h = d.find_first_child_of_class(model, "Humanoid")?;
    Some(Character {
        model: *p.map.get(&model)?,
        root: *p.map.get(&root)?,
        humanoid: *p.map.get(&h)?,
        player: *p.map.get(&player)?,
    })
}
