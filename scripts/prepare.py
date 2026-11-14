#!/usr/bin/env python3
"""Extract desktop-independent original editor modules; apply reviewed patches.

The upstream submodules are immutable inputs. Every generated source records
its original path and SHA256. No Stew sources or resources are inputs.
"""
import hashlib
import json
import pathlib
import re
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "upstream/rbx-native/crates/rbx_studio/src"
OUT = ROOT / "core/src/upstream"
OUT.mkdir(parents=True, exist_ok=True)
manifest = []

def write_generated(path, content):
    data = content.encode("utf-8") if isinstance(content, str) else content
    if not path.exists() or path.read_bytes() != data:
        path.write_bytes(data)

# Patch first: provenance and extracted modules must be identical on a clean
# checkout and on every subsequent preparation of that checkout.
for patch in sorted((ROOT / "patches").glob("*.patch")):
    consolidated = ROOT / "patches" / (patch.name.split("--", 1)[0] + "--android-port.patch")
    if consolidated.exists() and patch != consolidated:
        continue
    repo = ROOT / "upstream" / patch.name.split("--", 1)[0]
    applied = subprocess.run(["git", "-C", str(repo), "apply", "--reverse", "--check", str(patch)], capture_output=True)
    if applied.returncode:
        subprocess.run(["git", "-C", str(repo), "apply", "--check", str(patch)], check=True)
        subprocess.run(["git", "-C", str(repo), "apply", str(patch)], check=True)

def module(path, transform=lambda s: s):
    original = (SRC / path).read_text()
    result = transform(original)
    # UI-specific upstream harnesses stay in the submodule, not the mobile crate.
    result = re.sub(r"#\[cfg\(test\)\]\s*(?:#\[[^\]]+\]\s*)*mod \w+;", "", result)
    result = re.sub(r"#\[cfg\(test\)\]\s*mod \w+\s*\{[\s\S]*$", "", result)
    target = OUT / ("properties/mod.rs" if path == "properties.rs" else path)
    target.parent.mkdir(parents=True, exist_ok=True)
    write_generated(target, "// Original: rbx-native/crates/rbx_studio/src/" + path + "\n" + result)
    manifest.append({"path": str(path), "sha256": hashlib.sha256(original.encode()).hexdigest(),
                     "adapted": result.rstrip() != original.rstrip()})

def without_shortcuts(source):
    source = source.replace("use gpui_kit::Modifiers;\n", "")
    # Only the original GPUI app saves mirrored SAF paths. The standalone
    # domain adapter writes app-private files and must not depend on GPUI.
    source = source.replace('    #[cfg(target_os = "android")]\n    gpui_mobile::android::commit_document(path).map_err(|err| err.to_string())?;\n', '')
    # Remove only the desktop key-dispatch section, retain serialization/history.
    start = source.index("/// What Ctrl+")
    end = source.index("/// Serializes `dom`", start) if "/// Serializes `dom`" in source[start:] else source.index("/// One snapshot", start)
    return source[:start] + source[end:]

module("save.rs", without_shortcuts)
module("history.rs", without_shortcuts)
module("home/template.rs")
module("script_editor/source.rs")
module("properties.rs")
for path in sorted((SRC / "properties").rglob("*.rs")):
    if "tests" not in path.parts and path.stem != "tests":
        module(str(path.relative_to(SRC)))
module("transform/frame.rs")
module("transform/targets.rs")
module("terrain/dom.rs")

def item(source, marker):
    start=source.index(marker)
    brace=source.index("{",start);depth=1;end=brace+1
    while depth:
        if source[end]=="{":depth+=1
        elif source[end]=="}":depth-=1
        end+=1
    return source[start:end]+"\n"

def scale_functions(source):
    imports="use glam::Vec3;\nuse rbx_viewer::gizmo::{self, Faces};\nuse rbx_viewer::pick::Ray;\nuse crate::transform::targets::{Slope, Target};\nuse super::{Drag, Change, Landing, snapped, MAX_SIZE, MIN_SIZE};\n"
    return imports+"\n".join(item(source,"pub(super) fn "+name) if name!="boxed" else item(source,"fn boxed") for name in ["grab_face","grab_box","boxed","advance"])

module("workspace_view/gizmo/scale.rs",scale_functions)
original_gizmo=(SRC/"workspace_view/gizmo.rs").read_text()
types="use glam::{Vec3, Mat3};\nuse crate::transform::targets::Slope;\n"
for name in ("Drag","Change"):
    types+="#[derive(Debug, Clone, Copy, PartialEq)]\n"+item(original_gizmo,"pub(super) enum "+name)
write_generated(OUT/"gizmo_types.rs", "// Original enums from rbx_studio/src/workspace_view/gizmo.rs\n"+types)
manifest.append({"path":"workspace_view/gizmo.rs (Drag and Change)","sha256":hashlib.sha256(original_gizmo.encode()).hexdigest(),"adapted":True})

write_generated(OUT / "provenance.json", json.dumps(manifest, indent=2) + "\n")

# Original rig helpers for editor tools and tests; Play links the full
# Rook Session with its server/client VMs separately.
rook_src = ROOT / "upstream/rook/crates/rook-lua/src/api"
rook_manifest = []
def rook_part(path, transform):
    original = (rook_src / path).read_text()
    rook_manifest.append({"path": "crates/rook-lua/src/api/" + path,
                          "sha256": hashlib.sha256(original.encode()).hexdigest(),
                          "adapted": True})
    return transform(original)

rig = rook_part("methods/characters/rigs.rs", lambda s:
    s[s.index("/// Move `model`"):].replace("pub(super) fn place_at", "pub fn place_at")
    .replace("fn r6(", "pub fn r6("))
rig = rig.replace('if let Some(cf) = dom.cframe_property(part, "CFrame")\n            && dom.is_a(part, "BasePart")\n        {',
                  'if !dom.is_a(part, "BasePart") { continue; }\n        if let Some(cf) = dom.cframe_property(part, "CFrame") {')
rig = "use rook_dom::{Dom, DomError, InstanceId};\nuse rook_types::{CFrame, Color3, Value, Vector3};\n" + rig
rig += rook_part("properties.rs", lambda s: item(s, "pub(crate) fn content_value").replace("pub(crate)", ""))
rig += rook_part("methods/characters.rs", lambda s:
    'const ROOT_NAMES: [&str; 3] = ["HumanoidRootPart", "Torso", "UpperTorso"];\n' + item(s, "pub fn root_part"))
rig += rook_part("methods/characters/spawning.rs", lambda s:
    "\n".join(item(s, marker).replace("pub(super)", "pub").replace("super::root_part", "root_part")
              for marker in ["fn spawns_for", "fn reach", "fn body_parts", "pub(super) fn feet_below_root"])
    .replace("fn spawns_for", "pub fn spawns_for"))
rig += rook_part("methods/characters/movement.rs", lambda s: item(s, "fn standing_height").replace("fn standing_height", "pub fn standing_height"))
write_generated(OUT / "rook_character.rs", "// Extracted original Rook character/rig functions; see docs/rook-provenance.json.\nuse glam::Vec3;\n" + rig)
write_generated(OUT / "rook-provenance.json", json.dumps(rook_manifest, indent=2) + "\n")
geometry=ROOT/"geometry/src/upstream"
geometry.mkdir(parents=True,exist_ok=True)
head=ROOT/"upstream/rook/crates/rook-render/src/mesh/head.rs"
write_generated(geometry/"rook_head.rs", head.read_bytes())
write_generated(geometry/"provenance.json", json.dumps({"path":"crates/rook-render/src/mesh/head.rs","sha256":hashlib.sha256(head.read_bytes()).hexdigest(),"adapted":False},indent=2)+"\n")
print(f"Prepared {len(manifest)} original editor source files and Android patches")

