// Extracted original Rook character/rig functions; see docs/rook-provenance.json.
use glam::Vec3;
use rook_dom::{Dom, DomError, InstanceId};
use rook_types::{CFrame, Color3, Value, Vector3};
/// Move `model`'s parts, keeping their places around `root`, so `root`
/// is at `at`.
pub fn place_at(
    dom: &mut Dom,
    model: InstanceId,
    root: InstanceId,
    at: CFrame,
) -> Result<(), DomError> {
    let Some(from) = dom.cframe_property(root, "CFrame") else {
        return Ok(());
    };
    let to_new = at * from.inverse();
    for part in dom.descendants(model) {
        if !dom.is_a(part, "BasePart") { continue; }
        if let Some(cf) = dom.cframe_property(part, "CFrame") {
            dom.set_property_by_name(
                part,
                "CFrame",
                Value::CFrame((to_new * cf).orthonormalize()),
            )?;
        }
    }
    Ok(())
}

/// One R6 part: name, size, place relative to the root part, CanCollide.
const R6_PARTS: [(&str, [f32; 3], [f32; 3], bool); 6] = [
    ("Head", [2.0, 1.0, 1.0], [0.0, 1.5, 0.0], true),
    ("Torso", [2.0, 2.0, 1.0], [0.0, 0.0, 0.0], true),
    ("Left Arm", [1.0, 2.0, 1.0], [-1.5, 0.0, 0.0], false),
    ("Right Arm", [1.0, 2.0, 1.0], [1.5, 0.0, 0.0], false),
    ("Left Leg", [1.0, 2.0, 1.0], [-0.5, -2.0, 0.0], false),
    ("Right Leg", [1.0, 2.0, 1.0], [0.5, -2.0, 0.0], false),
];

/// Rotations of R6's joint frames (rows of the matrix).
const FACE_RIGHT: [f32; 9] = [0.0, 0.0, 1.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0];
const FACE_LEFT: [f32; 9] = [0.0, 0.0, -1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0];
const UPRIGHT: [f32; 9] = [-1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0];

/// An R6 Motor6D: name, holder and Part0, Part1, C0 and C1 positions,
/// and the rotation both frames share.
type R6Joint = (
    &'static str,
    &'static str,
    &'static str,
    [f32; 3],
    [f32; 3],
    [f32; 9],
);

const R6_JOINTS: [R6Joint; 6] = [
    (
        "Right Shoulder",
        "Torso",
        "Right Arm",
        [1.0, 0.5, 0.0],
        [-0.5, 0.5, 0.0],
        FACE_RIGHT,
    ),
    (
        "Left Shoulder",
        "Torso",
        "Left Arm",
        [-1.0, 0.5, 0.0],
        [0.5, 0.5, 0.0],
        FACE_LEFT,
    ),
    (
        "Right Hip",
        "Torso",
        "Right Leg",
        [1.0, -1.0, 0.0],
        [0.5, 1.0, 0.0],
        FACE_RIGHT,
    ),
    (
        "Left Hip",
        "Torso",
        "Left Leg",
        [-1.0, -1.0, 0.0],
        [-0.5, 1.0, 0.0],
        FACE_LEFT,
    ),
    (
        "Neck",
        "Torso",
        "Head",
        [0.0, 1.0, 0.0],
        [0.0, -0.5, 0.0],
        UPRIGHT,
    ),
    (
        "RootJoint",
        "HumanoidRootPart",
        "Torso",
        [0.0, 0.0, 0.0],
        [0.0, 0.0, 0.0],
        UPRIGHT,
    ),
];

/// R6's attachments: part, name, position (all unrotated).
const R6_ATTACHMENTS: [(&str, &str, [f32; 3]); 18] = [
    ("Head", "HairAttachment", [0.0, 0.6, 0.0]),
    ("Head", "HatAttachment", [0.0, 0.6, 0.0]),
    ("Head", "FaceFrontAttachment", [0.0, 0.0, -0.6]),
    ("Head", "FaceCenterAttachment", [0.0, 0.0, 0.0]),
    ("Torso", "NeckAttachment", [0.0, 1.0, 0.0]),
    ("Torso", "BodyFrontAttachment", [0.0, 0.0, -0.5]),
    ("Torso", "BodyBackAttachment", [0.0, 0.0, 0.5]),
    ("Torso", "LeftCollarAttachment", [-1.0, 1.0, 0.0]),
    ("Torso", "RightCollarAttachment", [1.0, 1.0, 0.0]),
    ("Torso", "WaistFrontAttachment", [0.0, -1.0, -0.5]),
    ("Torso", "WaistCenterAttachment", [0.0, -1.0, 0.0]),
    ("Torso", "WaistBackAttachment", [0.0, -1.0, 0.5]),
    ("Left Arm", "LeftShoulderAttachment", [0.0, 1.0, 0.0]),
    ("Left Arm", "LeftGripAttachment", [0.0, -1.0, 0.0]),
    ("Right Arm", "RightShoulderAttachment", [0.0, 1.0, 0.0]),
    ("Right Arm", "RightGripAttachment", [0.0, -1.0, 0.0]),
    ("Left Leg", "LeftFootAttachment", [0.0, -1.0, 0.0]),
    ("Right Leg", "RightFootAttachment", [0.0, -1.0, 0.0]),
];

fn frame(position: [f32; 3], rotation: [f32; 9]) -> CFrame {
    let [x, y, z] = position;
    let [a, b, c, d, e, f, g, h, i] = rotation;
    CFrame::from_components([x, y, z, a, b, c, d, e, f, g, h, i])
}

fn vector(v: [f32; 3]) -> Value {
    Value::Vector3(Vector3(glam::Vec3::from(v)))
}

/// A new instance of `class` named `name` under `parent`, with `props`.
fn add(
    dom: &mut Dom,
    class: &str,
    name: &str,
    parent: InstanceId,
    props: &[(&str, Value)],
) -> Result<InstanceId, DomError> {
    let id = dom.create_by_name(class)?;
    dom.set_name(id, name.to_owned())?;
    for (prop, value) in props {
        dom.set_property_by_name(id, prop, value.clone())?;
    }
    dom.set_parent(id, Some(parent))?;
    Ok(id)
}

/// The R6 rig, as Studio 0.744 builds it for a HumanoidDescription
/// (`Players:CreateHumanoidModelFromDescription`), in its child order.
pub fn r6(dom: &mut Dom) -> Result<InstanceId, DomError> {
    let model = dom.create_by_name("Model")?;
    let part = |dom: &mut Dom, name: &str, size: [f32; 3], at: [f32; 3], collide: bool| {
        add(
            dom,
            "Part",
            name,
            model,
            &[
                ("Size", vector(size)),
                ("CFrame", Value::CFrame(CFrame::from_position(at.into()))),
                ("CanCollide", Value::Bool(collide)),
                // Bodies have smooth sides (Studio's rig shows no studs).
                ("TopSurface", Value::Enum(0)),
                ("BottomSurface", Value::Enum(0)),
            ],
        )
    };
    let mut parts = Vec::new();
    for (name, size, at, collide) in R6_PARTS {
        parts.push((name, part(dom, name, size, at, collide)?));
    }
    let humanoid = add(dom, "Humanoid", "Humanoid", model, &[])?;
    add(dom, "Animator", "Animator", humanoid, &[])?;
    let root = part(dom, "HumanoidRootPart", [2.0, 2.0, 1.0], [0.0; 3], false)?;
    dom.set_property_by_name(root, "Transparency", Value::Float32(1.0))?;
    dom.set_property_by_name(
        root,
        "Color",
        Value::Color3(Color3::from_rgb(163, 162, 165)),
    )?;
    parts.push(("HumanoidRootPart", root));
    let find = |name: &str| parts.iter().find(|(n, _)| *n == name).map(|(_, id)| *id);
    let head = find("Head").ok_or(DomError::Stale)?;
    add(
        dom,
        "SpecialMesh",
        "Mesh",
        head,
        &[
            ("MeshType", Value::Enum(0)),
            ("Scale", vector([1.25, 1.25, 1.25])),
        ],
    )?;
    add(
        dom,
        "Decal",
        "face",
        head,
        &[(
            "TextureContent",
            content_value("rbxasset://textures/face.png"),
        )],
    )?;
    let torso = find("Torso").ok_or(DomError::Stale)?;
    add(dom, "Decal", "roblox", torso, &[])?;
    for (holder, name, at) in R6_ATTACHMENTS {
        let holder = find(holder).ok_or(DomError::Stale)?;
        add(
            dom,
            "Attachment",
            name,
            holder,
            &[("CFrame", Value::CFrame(CFrame::from_position(at.into())))],
        )?;
    }
    for (name, holder, part1, c0, c1, rotation) in R6_JOINTS {
        let (Some(holder), Some(part1)) = (find(holder), find(part1)) else {
            continue;
        };
        add(
            dom,
            "Motor6D",
            name,
            holder,
            &[
                ("Part0", Value::Ref(Some(holder.to_ref()))),
                ("Part1", Value::Ref(Some(part1.to_ref()))),
                ("C0", Value::CFrame(frame(c0, rotation))),
                ("C1", Value::CFrame(frame(c1, rotation))),
            ],
        )?;
    }
    Ok(model)
}
 fn content_value(url: &str) -> Value {
    if url.is_empty() {
        return Value::Content(rook_types::Content::none());
    }
    let url = match url.strip_prefix("rbxasset://") {
        Some(path) => format!("rbxasset://{}", path.replace('\\', "/")),
        None => url.to_owned(),
    };
    Value::Content(rook_types::Content::from_uri(url))
}
const ROOT_NAMES: [&str; 3] = ["HumanoidRootPart", "Torso", "UpperTorso"];
pub fn root_part(dom: &rook_dom::Dom, model: InstanceId) -> Option<InstanceId> {
    ROOT_NAMES
        .into_iter()
        .filter_map(|name| dom.find_first_child(model, name, false))
        .find(|&p| dom.is_a(p, "BasePart"))
}
pub fn spawns_for(dom: &Dom, player: InstanceId) -> Vec<InstanceId> {
    let Some(ws) = dom.find_service("Workspace") else {
        return Vec::new();
    };
    let team_color = (dom.bool_property(player, "Neutral") == Some(false))
        .then(|| dom.brick_color_property(player, "TeamColor"))
        .flatten();
    dom.descendants(ws)
        .into_iter()
        .filter(|&s| dom.is_a(s, "SpawnLocation"))
        .filter(|&s| dom.bool_property(s, "Enabled") != Some(false))
        .filter(|&s| {
            dom.bool_property(s, "Neutral") != Some(false)
                || team_color.is_some() && dom.brick_color_property(s, "TeamColor") == team_color
        })
        .collect()
}

fn reach(dom: &Dom, part: InstanceId) -> Option<(Vec3, Vec3)> {
    let cf = dom.cframe_property(part, "CFrame")?;
    let half = dom.vector3_property(part, "Size")? / 2.0;
    let r = cf.rotation;
    Some((
        cf.position,
        (r * Vec3::X * half.x).abs() + (r * Vec3::Y * half.y).abs() + (r * Vec3::Z * half.z).abs(),
    ))
}

fn body_parts(dom: &Dom, body: InstanceId) -> Vec<(Vec3, Vec3)> {
    dom.descendants(body)
        .into_iter()
        .filter(|&p| dom.is_a(p, "BasePart"))
        .filter_map(|p| reach(dom, p))
        .collect()
}

pub fn feet_below_root(dom: &Dom, body: InstanceId) -> f32 {
    let root = root_part(dom, body);
    let Some(root_y) = root
        .and_then(|r| dom.cframe_property(r, "CFrame"))
        .map(|c| c.position.y)
    else {
        return 3.0;
    };
    let lowest = body_parts(dom, body)
        .into_iter()
        .map(|(at, reach)| at.y - reach.y)
        .fold(root_y, f32::min);
    root_y - lowest
}
pub fn standing_height(
    dom: &Dom,
    humanoid: InstanceId,
    model: InstanceId,
    root_size: glam::Vec3,
) -> f32 {
    let hip = dom.float_property(humanoid, "HipHeight").unwrap_or(0.0);
    let r15 = dom.enum_item_property(humanoid, "RigType") == Some("R15");
    let legs = if r15 {
        0.0
    } else {
        dom.find_first_child(model, "Left Leg", false)
            .and_then(|l| dom.vector3_property(l, "Size"))
            .map_or(2.0, |s| s.y)
    };
    hip + root_size.y * 0.5 + legs
}
