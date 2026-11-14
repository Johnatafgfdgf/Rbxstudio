//! Character spawn adapter. The rig, joints, attachments, placement and
//! standing-height calculation are extracted original Rook code.
use glam::Vec3;
use rbx_dom::{Ref, Variant, WeakDom};
use rook_dom::Dom;
use rook_types::{CFrame, Color3, Value};

use crate::rook_character as rig;

#[derive(Debug, Clone)]
pub struct Character {
    pub model: Ref,
    pub root: Ref,
    pub humanoid: Ref,
    pub player: Ref,
}

impl Character {
    pub fn spawn(native: &mut WeakDom) -> Result<Self, String> {
        let bytes = rbx_xml::serialize(native).map_err(|e| e.to_string())?;
        let loaded = rook_formats::load(bytes.as_bytes()).map_err(|e| e.to_string())?;
        let mut dom = Dom::new();
        let top = dom.root();
        rook_formats::into_dom(loaded.dom, &mut dom, top);
        let player = dom.create_by_name("Player").map_err(|e| e.to_string())?;
        dom.set_name(player, "LocalPlayer".into())
            .map_err(|e| e.to_string())?;
        // A creator-provided rig takes precedence. Rook's original R6 builder
        // is the fallback and needs no proprietary rig file or avatar service.
        let custom = dom
            .find_service("StarterPlayer")
            .and_then(|s| dom.find_first_child(s, "StarterCharacter", false))
            .filter(|&c| dom.is_a(c, "Model"))
            .and_then(|c| dom.clone_instance(c));
        let model = match custom {
            Some(c) => c,
            None => rig::r6(&mut dom).map_err(|e| e.to_string())?,
        };
        dom.set_name(model, "LocalPlayer".into())
            .map_err(|e| e.to_string())?;
        let root = rig::root_part(&dom, model).ok_or("O rig não possui uma peça raiz")?;
        if dom.find_first_child_of_class(model, "Humanoid").is_none() {
            return Err("StarterCharacter não possui Humanoid".into());
        }
        // The classic colors from Rook's appearance.rs, not a bundled avatar.
        if custom.is_none() {
            for (name, number) in [
                ("Head", 24),
                ("Torso", 23),
                ("Left Arm", 24),
                ("Right Arm", 24),
                ("Left Leg", 37),
                ("Right Leg", 37),
            ] {
                if let (Some(p), Some(color)) = (
                    dom.find_first_child(model, name, false),
                    rook_types::BrickColor::from_number(number),
                ) {
                    dom.set_property_by_name(p, "Color", Value::Color3(Color3::from(color)))
                        .map_err(|e| e.to_string())?;
                }
            }
        }
        let spawn = rig::spawns_for(&dom, player).first().copied();
        // Local offline Play chooses the center of the first eligible spawn.
        // Rook's team/Enabled eligibility and complete rig placement are kept.
        let at = spawn
            .and_then(|s| {
                let cf = dom.cframe_property(s, "CFrame")?;
                let size = dom.vector3_property(s, "Size")?;
                let feet = cf * CFrame::from_position(Vec3::Y * (size.y * 0.5));
                let position = feet.position + Vec3::Y * (rig::feet_below_root(&dom, model) + 0.01);
                let look = cf.rotation * Vec3::NEG_Z;
                let forward = Vec3::new(look.x, 0., look.z)
                    .try_normalize()
                    .unwrap_or(Vec3::NEG_Z);
                Some(CFrame::look_at(position, position + forward, Vec3::Y))
            })
            .unwrap_or_else(|| CFrame::from_position(Vec3::new(0., 103.01, 0.)));
        rig::place_at(&mut dom, model, root, at).map_err(|e| e.to_string())?;
        dom.set_property_by_name(model, "PrimaryPart", Value::Ref(Some(root.to_ref())))
            .map_err(|e| e.to_string())?;

        // Bridge through the two ORIGINAL codecs. Copy only the generated rig:
        // the loaded place, unknown properties and editor IDs stay untouched.
        let weak = rook_formats::from_dom(&dom, &[model]);
        let xml =
            rook_formats::save(&weak, rook_formats::Format::Xml).map_err(|e| e.to_string())?;
        let rig_dom = rbx_xml::deserialize(std::str::from_utf8(&xml).map_err(|e| e.to_string())?)
            .map_err(|e| e.to_string())?;
        let workspace = service(native, "Workspace");
        let model =
            rbx_lua::plugin::copy_tree(&rig_dom, rig_dom.root_refs()[0], native, Some(workspace));
        let child = |dom: &WeakDom, name: &str| {
            dom.get(model).and_then(|m| {
                m.children()
                    .iter()
                    .find(|&&r| dom.get(r).is_some_and(|i| i.name() == name))
                    .copied()
            })
        };
        let root = child(native, "HumanoidRootPart")
            .or_else(|| child(native, "Torso"))
            .or_else(|| child(native, "UpperTorso"))
            .ok_or("A cópia do rig perdeu sua raiz")?;
        let humanoid = native
            .get(model)
            .unwrap()
            .children()
            .iter()
            .copied()
            .find(|&r| native.get(r).is_some_and(|i| i.class() == "Humanoid"))
            .ok_or("Humanoid não convertido")?;
        let players = service(native, "Players");
        let player = native.new_instance("Player", "LocalPlayer", Some(players));
        native
            .set_property(player, "Character", Variant::Ref(model))
            .map_err(|e| e.to_string())?;
        native
            .set_property(player, "UserId", Variant::Int64(0))
            .map_err(|e| e.to_string())?;
        native
            .set_property(player, "DisplayName", Variant::String("LocalPlayer".into()))
            .map_err(|e| e.to_string())?;
        native
            .set_property(players, "LocalPlayer", Variant::Ref(player))
            .map_err(|e| e.to_string())?;
        native.new_instance("Backpack", "Backpack", Some(player));
        let scripts = native.new_instance("PlayerScripts", "PlayerScripts", Some(player));
        let gui = native.new_instance("PlayerGui", "PlayerGui", Some(player));
        let source = native.clone();
        if let Some(starter) = source
            .root_refs()
            .iter()
            .copied()
            .find(|&r| source.get(r).is_some_and(|i| i.class() == "StarterPlayer"))
        {
            for &r in source.get(starter).unwrap().children() {
                let item = source.get(r).unwrap();
                let target = match item.class() {
                    "StarterPlayerScripts" => scripts,
                    "StarterCharacterScripts" => model,
                    _ => continue,
                };
                for &c in item.children() {
                    rbx_lua::plugin::copy_tree(&source, c, native, Some(target));
                }
            }
        }
        if let Some(starter) = source
            .root_refs()
            .iter()
            .copied()
            .find(|&r| source.get(r).is_some_and(|i| i.class() == "StarterGui"))
        {
            for &r in source.get(starter).unwrap().children() {
                rbx_lua::plugin::copy_tree(&source, r, native, Some(gui));
            }
        }
        Ok(Self {
            model,
            root,
            humanoid,
            player,
        })
    }

    pub fn position(&self, native: &WeakDom) -> Option<Vec3> {
        let Variant::CFrame(cf) = native.get(self.root)?.properties().get("CFrame")? else {
            return None;
        };
        Some(Vec3::new(cf.position.x, cf.position.y, cf.position.z))
    }
}

fn service(dom: &mut WeakDom, class: &str) -> Ref {
    dom.root_refs()
        .iter()
        .copied()
        .find(|&r| dom.get(r).is_some_and(|i| i.class() == class))
        .unwrap_or_else(|| dom.new_instance(class, class, None))
}
