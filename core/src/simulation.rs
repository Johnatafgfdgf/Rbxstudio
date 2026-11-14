use std::collections::HashMap;

use glam::{Mat3, Vec3};
use rbx_dom::{CFrameData, Change, Ref, Variant, Vector3Data, WeakDom};
use rook_dom::{Dom, InstanceId};
use rook_physics::Physics;
use rook_types::{CFrame, Value, Vector3};

/// A disposable Rook physics projection. The editor's rbx-native DOM remains
/// authoritative for files, scripts and drawing. The projection is never saved.
pub struct Simulation {
    dom: Dom,
    physics: Physics,
    map: HashMap<Ref, InstanceId>,
    reverse: HashMap<InstanceId, Ref>,
    player: Option<(crate::Character, Vec3, bool)>,
    pub state: String,
    pub events: Vec<(Ref, String, Vec<rbx_lua::plugin::Arg>)>,
}

impl Simulation {
    pub fn new(native: &WeakDom) -> Result<Self, String> {
        let _ = rook_platform::install_workers(Box::new(rook_platform::parallel::Serial));
        rook_physics::size_worker_pool(
            std::thread::available_parallelism()
                .map_or(2, |n| n.get() / 2)
                .max(1),
        );
        // Reuse both original codecs and Rook's full format conversion instead
        // of approximating its physics properties, joints and enum migrations.
        let bytes = rbx_xml::serialize(native).map_err(|e| e.to_string())?;
        let loaded = rook_formats::load(bytes.as_bytes()).map_err(|e| e.to_string())?;
        let mut dom = Dom::new();
        let root = dom.root();
        let converted = rook_formats::into_dom(loaded.dom, &mut dom, root);
        if converted.duplicate_unique_ids {
            return Err("UniqueIds duplicados na cena".into());
        }
        let mut map = HashMap::new();
        fn pair(
            native: &WeakDom,
            rook: &Dom,
            a: &[Ref],
            b: &[InstanceId],
            map: &mut HashMap<Ref, InstanceId>,
        ) -> Result<(), String> {
            if a.len() != b.len() {
                return Err("A conversão Rook alterou a hierarquia; Play cancelado".into());
            }
            for (&a, &b) in a.iter().zip(b) {
                let item = native.get(a).ok_or("Referência nativa inválida")?;
                if item.class() != rook.class_name(b) || item.name() != rook.name(b) {
                    return Err(format!("Divergência de conversão em {}", item.name()));
                }
                map.insert(a, b);
                pair(native, rook, item.children(), rook.children(b), map)?;
            }
            Ok(())
        }
        pair(native, &dom, native.root_refs(), &converted.top, &mut map)?;
        let mut physics = Physics::new();
        physics.sync(&mut dom);
        let consumed = dom.journal().end();
        dom.journal_mut().trim(consumed);
        let reverse = map.iter().map(|(&a, &b)| (b, a)).collect();
        Ok(Self {
            dom,
            physics,
            map,
            reverse,
            player: None,
            state: "Running".into(),
            events: Vec::new(),
        })
    }

    pub fn player_input(&mut self, character: &crate::Character, direction: Vec3, jump: bool) {
        let pending = self.player.as_ref().is_some_and(|(_, _, jump)| *jump);
        self.player = Some((
            character.clone(),
            direction.clamp_length_max(1.0),
            jump || pending,
        ));
    }

    pub fn step(&mut self, native: &mut WeakDom, dt: f64) -> Result<(), String> {
        // Consume only the actual mutations, not every property of every
        // instance each frame. The renderer still owns draining this log.
        let mut created = Vec::new();
        for change in native.changes() {
            match *change {
                Change::Added(a) if !self.map.contains_key(&a) => {
                    let Some(item) = native.get(a) else { continue };
                    let b = self
                        .dom
                        .create_by_name(item.class())
                        .map_err(|e| e.to_string())?;
                    self.dom
                        .set_name(b, item.name().into())
                        .map_err(|e| e.to_string())?;
                    self.map.insert(a, b);
                    self.reverse.insert(b, a);
                    created.push(a);
                }
                Change::Removed(a) => {
                    if let Some(b) = self.map.remove(&a) {
                        self.reverse.remove(&b);
                        if !self.dom.is_destroyed(b) {
                            self.dom.destroy(b).map_err(|e| e.to_string())?;
                        }
                    }
                }
                _ => {}
            }
        }
        let parent_of = |native: &WeakDom, map: &HashMap<Ref, InstanceId>, a: Ref, root| {
            native
                .parent(a)
                .and_then(|r| map.get(&r).copied())
                .unwrap_or(root)
        };
        for &a in &created {
            let b = self.map[&a];
            self.dom
                .set_parent(b, Some(parent_of(native, &self.map, a, self.dom.root())))
                .map_err(|e| e.to_string())?;
            for (name, value) in native.get(a).unwrap().properties() {
                if let Some(value) = convert(value, &self.map) {
                    self.write(b, name, value)?;
                }
            }
        }
        for change in native.changes() {
            match change {
                Change::Property { referent: a, name } => {
                    let Some(&b) = self.map.get(a) else { continue };
                    let Some(item) = native.get(*a) else { continue };
                    if name == "Name" {
                        self.dom
                            .set_name(b, item.name().into())
                            .map_err(|e| e.to_string())?;
                    } else if let Some(value) = item
                        .properties()
                        .get(name)
                        .and_then(|v| convert(v, &self.map))
                    {
                        self.write(b, name, value)?;
                    }
                }
                Change::Parent { referent: a, .. } => {
                    if let Some(&b) = self.map.get(a) {
                        self.dom
                            .set_parent(b, Some(parent_of(native, &self.map, *a, self.dom.root())))
                            .map_err(|e| e.to_string())?;
                    }
                }
                _ => {}
            }
        }
        self.physics.sync(&mut self.dom);
        // Physics has consumed these entries. Keep its subsequent write-back
        // entries for the NEXT sync; trim only this known-safe prefix.
        let consumed = self.dom.journal().end();
        if let Some((character, direction, jump)) = &mut self.player {
            if let (Some(&h), Some(&r)) = (
                self.map.get(&character.humanoid),
                self.map.get(&character.root),
            ) {
                let dom = &self.dom;
                let size = dom
                    .vector3_property(r, "Size")
                    .unwrap_or(Vec3::new(2., 2., 1.));
                let model = dom.parent(h).ok_or("Humanoid sem Model")?;
                let f = |name, default| dom.float_property(h, name).unwrap_or(default);
                let b = |name, default| dom.bool_property(h, name).unwrap_or(default);
                let speed = f("WalkSpeed", 16.).max(0.);
                let gravity = dom
                    .find_service("Workspace")
                    .and_then(|ws| dom.float_property(ws, "Gravity"))
                    .unwrap_or(196.2);
                self.physics
                    .drive_humanoids(vec![rook_physics::humanoid::Drive {
                        humanoid: h,
                        root: r,
                        root_size: size,
                        standing_height: crate::rook_character::standing_height(
                            dom, h, model, size,
                        ),
                        walk: *direction * speed,
                        walk_speed: speed,
                        jump: std::mem::take(jump) || b("Jump", false),
                        jump_speed: if b("UseJumpPower", true) {
                            f("JumpPower", 50.)
                        } else {
                            (2. * gravity * f("JumpHeight", 7.2)).max(0.).sqrt()
                        },
                        max_slope: f("MaxSlopeAngle", 89.).to_radians(),
                        auto_rotate: b("AutoRotate", true),
                        face: None,
                        platform_stand: b("PlatformStand", false),
                        sit: b("Sit", false),
                        dead: dom
                            .number_property(h, "Health_XML")
                            .is_some_and(|v| v <= 0.),
                        disabled: 0,
                    }]);
            }
        }
        self.physics.step(dt.clamp(0.0, 0.1));
        let fallen = self.physics.write_back(&mut self.dom);
        for report in self.physics.humanoid_reports(&self.dom) {
            self.state = report.state.name().into();
            if let Some(&humanoid) = self.reverse.get(&report.humanoid) {
                for (old, new) in &report.changes {
                    use rbx_lua::plugin::Arg;
                    let state = |s: rook_physics::humanoid::State| Arg::Enum {
                        enum_name: "HumanoidStateType".into(),
                        item: s.name().into(),
                    };
                    self.events.push((
                        humanoid,
                        "StateChanged".into(),
                        vec![state(*old), state(*new)],
                    ));
                }
            }
            if let Some((character, ..)) = &self.player {
                if report.clear_jump {
                    native
                        .set_property(character.humanoid, "Jump", Variant::Bool(false))
                        .map_err(|e| e.to_string())?;
                }
                native
                    .set_property(
                        character.humanoid,
                        "FloorMaterial",
                        Variant::Enum(report.floor_material.unwrap_or(1792)),
                    )
                    .map_err(|e| e.to_string())?;
            }
        }
        for touch in self.physics.take_touches() {
            if let (Some(&a), Some(&b)) = (
                self.reverse.get(&touch.part),
                self.reverse.get(&touch.other),
            ) {
                let name = if touch.began { "Touched" } else { "TouchEnded" };
                self.events
                    .push((a, name.into(), vec![rbx_lua::plugin::Arg::Instance(b)]));
                self.events
                    .push((b, name.into(), vec![rbx_lua::plugin::Arg::Instance(a)]));
            }
        }
        self.physics.take_reached();
        self.dom.journal_mut().trim(consumed);
        let mut removed = Vec::new();
        for (&a, &b) in &self.map {
            if fallen.contains(&b) {
                removed.push(a);
                continue;
            }
            if native.get(a).is_none() {
                continue;
            }
            if self.dom.is_destroyed(b) || !self.dom.is_a(b, "BasePart") {
                continue;
            }
            if let Some(Value::CFrame(cf)) = self.dom.property_by_name(b, "CFrame") {
                native
                    .set_property(
                        a,
                        "CFrame",
                        Variant::CFrame(CFrameData {
                            position: Vector3Data {
                                x: cf.position.x,
                                y: cf.position.y,
                                z: cf.position.z,
                            },
                            rotation: cf.rotation.transpose().to_cols_array(),
                        }),
                    )
                    .map_err(|e| e.to_string())?;
            }
        }
        for a in removed {
            if let Some(b) = self.map.remove(&a) {
                self.reverse.remove(&b);
                self.dom.destroy(b).map_err(|e| e.to_string())?;
            }
            native.remove(a);
        }
        Ok(())
    }

    fn write(&mut self, id: InstanceId, name: &str, value: Value) -> Result<(), String> {
        let name = if name == "size" { "Size" } else { name };
        if self.dom.property_by_name(id, name).as_ref() != Some(&value) {
            self.dom
                .set_property_by_name(id, name, value)
                .map_err(|e| e.to_string())?;
        }
        Ok(())
    }

    pub fn camera_distance(
        &mut self,
        character: &crate::Character,
        origin: Vec3,
        offset: Vec3,
    ) -> f32 {
        let ignore = self
            .map
            .get(&character.model)
            .copied()
            .into_iter()
            .collect();
        let filter = rook_physics::Filter {
            instances: ignore,
            respect_can_collide: true,
            ..Default::default()
        };
        self.physics
            .raycast(&mut self.dom, origin, offset, &filter)
            .map_or(offset.length(), |hit| (hit.distance - 0.4).max(0.5))
    }
}

fn convert(v: &Variant, map: &HashMap<Ref, InstanceId>) -> Option<Value> {
    Some(match v {
        Variant::CFrame(cf) => Value::CFrame(CFrame {
            position: Vec3::new(cf.position.x, cf.position.y, cf.position.z),
            rotation: Mat3::from_cols_array(&cf.rotation).transpose(),
        }),
        Variant::Vector3(v) => Value::Vector3(Vector3::new(v.x, v.y, v.z)),
        Variant::Float32(v) => Value::Float32(*v),
        Variant::Float64(v) => Value::Float64(*v),
        Variant::Bool(v) => Value::Bool(*v),
        Variant::Int32(v) => Value::Int32(*v),
        Variant::Int64(v) => Value::Int64(*v),
        Variant::Enum(v) => Value::Enum(*v),
        Variant::String(v) => Value::String(v.clone()),
        Variant::Ref(v) => Value::Ref(map.get(v).map(|r| r.to_ref())),
        _ => return None,
    })
}
