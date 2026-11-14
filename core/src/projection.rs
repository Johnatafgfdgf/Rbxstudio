//! Render-only Rook client projection. The original editor snapshot is saved separately.
use rbx_dom::{CFrameData, Color3Data, Ref, Variant, Vector2Data, Vector3Data, WeakDom};
use rook_dom::{Change, Dom, InstanceId};
use rook_types::Value;
use std::collections::{HashMap, HashSet};
#[derive(Default)]
pub(crate) struct Projection {
    pub map: HashMap<InstanceId, Ref>,
    pub full_syncs: u64,
}
impl Projection {
    pub fn full(&mut self, source: &Dom, target: &mut WeakDom) -> Result<(), String> {
        let roots = source.children(source.root());
        let weak = rook_formats::from_dom(source, roots);
        let bytes =
            rook_formats::save(&weak, rook_formats::Format::Xml).map_err(|e| e.to_string())?;
        let temp = rbx_xml::deserialize(std::str::from_utf8(&bytes).map_err(|e| e.to_string())?)
            .map_err(|e| e.to_string())?;
        fn walk(
            source: &Dom,
            temp: &WeakDom,
            a: &[InstanceId],
            b: &[Ref],
            out: &mut Vec<(InstanceId, Ref)>,
        ) -> Result<(), String> {
            if a.len() != b.len() {
                return Err("Render projection changed the hierarchy".into());
            }
            for (&a, &b) in a.iter().zip(b) {
                let item = temp.get(b).ok_or("Invalid converted Instance")?;
                if source.class_name(a) != item.class() || source.name(a) != item.name() {
                    return Err("Render projection class/name mismatch".into());
                }
                out.push((a, b));
                walk(source, temp, source.children(a), item.children(), out)?;
            }
            Ok(())
        }
        let mut pairs = Vec::new();
        walk(source, &temp, roots, temp.root_refs(), &mut pairs)?;
        fn collect(d: &WeakDom, r: Ref, out: &mut Vec<Ref>) {
            out.push(r);
            if let Some(i) = d.get(r) {
                for c in i.children() {
                    collect(d, *c, out);
                }
            }
        }
        let initial = self.map.is_empty();
        let mut original = Vec::new();
        if initial {
            for r in target.root_refs() {
                collect(target, *r, &mut original);
            }
        }
        let mut used = HashSet::new();
        let mut remap = HashMap::new();
        for &(id, t) in &pairs {
            let item = temp.get(t).unwrap();
            let parent = source.parent(id).and_then(|p| self.map.get(&p).copied());
            let old = self
                .map
                .get(&id)
                .copied()
                .filter(|r| target.get(*r).is_some());
            let existing = if initial {
                let siblings = parent
                    .and_then(|p| target.get(p).map(|i| i.children()))
                    .unwrap_or(target.root_refs());
                siblings.iter().copied().find(|r| {
                    !used.contains(r)
                        && target
                            .get(*r)
                            .is_some_and(|i| i.class() == item.class() && i.name() == item.name())
                })
            } else {
                None
            };
            let dest = old
                .or(existing)
                .unwrap_or_else(|| target.new_instance(item.class(), item.name(), parent));
            self.map.insert(id, dest);
            used.insert(dest);
            remap.insert(t, dest);
        }
        for &(id, t) in &pairs {
            let dest = self.map[&id];
            let item = temp.get(t).unwrap();
            target.set_parent(
                dest,
                source.parent(id).and_then(|p| self.map.get(&p).copied()),
            );
            target
                .set_name(dest, item.name())
                .map_err(|e| e.to_string())?;
            let stale = target
                .get(dest)
                .unwrap()
                .properties()
                .keys()
                .filter(|p| !item.properties().contains_key(*p))
                .cloned()
                .collect::<Vec<_>>();
            for name in stale {
                target
                    .remove_property(dest, &name)
                    .map_err(|e| e.to_string())?;
            }
            for (name, value) in item.properties() {
                let value = match value {
                    Variant::Ref(r) => remap.get(r).copied().map(Variant::Ref),
                    v => Some(v.clone()),
                };
                if let Some(mut value) = value {
                    if name == "AttributesSerialize" {
                        if let Some(mapped) =
                            rbx_dom::attributes::remap_refs(&value, |r| remap.get(&r).copied())
                        {
                            value = mapped;
                        }
                    }
                    target
                        .set_property(dest, name, value)
                        .map_err(|e| e.to_string())?;
                }
            }
        }
        // Rook keeps defaults implicitly; rbx-native's extractor needs explicit geometry.
        for &(id, _) in &pairs {
            let dest = self.map[&id];
            if source.is_a(id, "PlayerGui") {
                target
                    .set_property(dest, "_RbxRuntimeGui", Variant::Bool(true))
                    .map_err(|e| e.to_string())?;
            }
            if source.is_a(id, "StarterGui") {
                target
                    .set_property(dest, "ShowDevelopmentGui", Variant::Bool(false))
                    .map_err(|e| e.to_string())?;
            }
            let mut required = Vec::new();
            if source.is_a(id, "BasePart") {
                required.extend([
                    "CFrame",
                    "Size",
                    "Color",
                    "Material",
                    "Transparency",
                    "Reflectance",
                    "CastShadow",
                ]);
            }
            if source.is_a(id, "Part") {
                required.push("Shape");
            }
            if source.is_a(id, "SpecialMesh") {
                required.extend(["MeshType", "Scale", "Offset"]);
            }
            for property in required {
                if let Some(pid) = source.property_id(id, property) {
                    if let Some((name, value)) = rook_formats::file_property(source, id, pid) {
                        if !target.get(dest).unwrap().properties().contains_key(&name) {
                            if let Some(value) = native_value(&value, &self.map) {
                                target
                                    .set_property(dest, &name, value)
                                    .map_err(|e| e.to_string())?;
                            }
                        }
                    }
                }
            }
        }
        let active: HashSet<_> = pairs.iter().map(|(id, _)| *id).collect();
        let stale = self
            .map
            .iter()
            .filter(|(id, _)| !active.contains(id))
            .map(|(&id, &r)| (id, r))
            .collect::<Vec<_>>();
        for (id, r) in stale {
            self.map.remove(&id);
            target.remove(r);
        }
        if initial {
            for r in original {
                if !used.contains(&r) {
                    target.remove(r);
                }
            }
        }
        self.full_syncs += 1;
        Ok(())
    }
    pub fn changes(
        &mut self,
        source: &Dom,
        changes: &[Change],
        target: &mut WeakDom,
    ) -> Result<(), String> {
        if changes.iter().any(|c| {
            matches!(
                c,
                Change::Created(_)
                    | Change::Reparented { .. }
                    | Change::Destroyed(_)
                    | Change::Collected(_)
            )
        }) {
            return self.full(source, target);
        }
        for change in changes {
            match change {
                Change::Property { id, property } => {
                    let Some(&r) = self.map.get(id) else { continue };
                    let name = source.database().property(*property).name;
                    if name == "Name" {
                        target
                            .set_name(r, source.name(*id))
                            .map_err(|e| e.to_string())?;
                        continue;
                    }
                    if name == "Parent" {
                        continue;
                    }
                    if let Some((name, value)) = rook_formats::file_property(source, *id, *property)
                    {
                        match native_value(&value, &self.map) {
                            Some(v) => {
                                target
                                    .set_property(r, &name, v)
                                    .map_err(|e| e.to_string())?;
                            }
                            None if matches!(value, Value::Ref(None)) => {
                                target
                                    .remove_property(r, &name)
                                    .map_err(|e| e.to_string())?;
                            }
                            None => return self.full(source, target),
                        }
                    }
                }
                Change::Attribute { id, name } => {
                    let Some(&r) = self.map.get(id) else { continue };
                    let Some(item) = source.get(*id) else {
                        continue;
                    };
                    let value = item.attributes.get(name);
                    let native = value.and_then(|v| native_value(v, &self.map));
                    if value.is_some() && native.is_none() {
                        return self.full(source, target);
                    }
                    let enum_type = match value {
                        Some(Value::EnumItem { enum_type, .. }) => Some(enum_type.as_str()),
                        _ => None,
                    };
                    let old = target
                        .get(r)
                        .unwrap()
                        .properties()
                        .get("AttributesSerialize");
                    let Some(blob) =
                        rbx_dom::attributes::set_attribute(old, name, native.as_ref(), enum_type)
                    else {
                        return self.full(source, target);
                    };
                    target
                        .set_property(r, "AttributesSerialize", blob)
                        .map_err(|e| e.to_string())?;
                }
                Change::TagAdded { id, .. } | Change::TagRemoved { id, .. } => {
                    let Some(&r) = self.map.get(id) else { continue };
                    if let Some(item) = source.get(*id) {
                        let tags = item.tags.iter().map(String::as_str).collect::<Vec<_>>();
                        target
                            .set_property(
                                r,
                                "Tags",
                                Variant::String(rbx_dom::attributes::encode_tags(&tags)),
                            )
                            .map_err(|e| e.to_string())?;
                    }
                }
                _ => {}
            }
        }
        Ok(())
    }
}
fn native_value(v: &Value, map: &HashMap<InstanceId, Ref>) -> Option<Variant> {
    Some(match v {
        Value::String(v) => Variant::String(v.clone()),
        Value::Bool(v) => Variant::Bool(*v),
        Value::Int32(v) => Variant::Int32(*v),
        Value::Int64(v) => Variant::Int64(*v),
        Value::Float32(v) => Variant::Float32(*v),
        Value::Float64(v) => Variant::Float64(*v),
        Value::Enum(v) | Value::EnumItem { value: v, .. } => Variant::Enum(*v),
        Value::BrickColor(v) => Variant::BrickColor(*v as u32),
        Value::Color3(v) => Variant::Color3(Color3Data {
            r: v.r,
            g: v.g,
            b: v.b,
        }),
        Value::Color3uint8(v) => Variant::Color3uint8 {
            r: v.r,
            g: v.g,
            b: v.b,
        },
        Value::Vector3(v) => Variant::Vector3(Vector3Data {
            x: v.x(),
            y: v.y(),
            z: v.z(),
        }),
        Value::Vector2(v) => Variant::Vector2(Vector2Data { x: v.x(), y: v.y() }),
        Value::CFrame(v) => Variant::CFrame(CFrameData {
            position: Vector3Data {
                x: v.position.x,
                y: v.position.y,
                z: v.position.z,
            },
            rotation: v.rotation.transpose().to_cols_array(),
        }),
        Value::Ref(Some(id)) => Variant::Ref(*map.get(&InstanceId::from_ref(*id))?),
        _ => return None,
    })
}
