#!/usr/bin/env python3
"""Exercise the production DOM operations, parser, Luau compiler and file serializer.

The small crate excludes GPUI rendering. No editor operation is replaced by a mock.
Android UI, GPU rendering and provider requests need device integration tests.
"""
from pathlib import Path
import os, re, subprocess, tempfile, sys

root=Path(__file__).resolve().parents[1]
studio=root/"upstream/rbx-native/crates/rbx_studio/src"

def function(src,name):
    match=re.search(r"(?m)^(?:pub\([^\n]+\) )?fn "+re.escape(name)+r"\(",src)
    if not match: raise ValueError(name)
    tail=src[match.start():]
    return tail[:tail.index("\n}")+3]

def before_tests(path):
    return path.read_text().split("#[cfg(test)]")[0]

def write(path,content):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(content)

with tempfile.TemporaryDirectory(prefix="rbx-native-agent-") as tmp:
    tmp=Path(tmp)
    deps="\n".join(f'{name} = {{ path = "{root}/upstream/rbx-native/crates/{name}"'+
        (', features = ["embedded-dump"]' if name=="rbx_reflection" else "")+' }'
        for name in ("rbx_dom","rbx_reflection","rbx_terrain","rbx_lua","rbx_binary"))
    write(tmp/"Cargo.toml",'[package]\nname="rbx-agent-dom-regression"\nversion="0.0.0"\nedition="2021"\n[dependencies]\n'+deps+'\nserde_json="1"\n')
    dest=tmp/"src"
    batch=(studio/"shell/mcp/project_edit.rs").read_text()
    batch=batch[batch.index("use rbx_dom"):batch.index("impl Shell {")]
    batch=batch.replace("use super::{Phase, Shell};","").replace("super::clipboard","crate::shell::clipboard")
    batch=batch.replace("fn apply_operations(","pub(crate) fn apply_operations(").replace("fn lookup(","pub(crate) fn lookup(").replace("fn full_name(","pub(crate) fn full_name(").replace("fn schema(","pub(crate) fn schema(").replace("fn snapshot_path(","pub(crate) fn snapshot_path(").replace("fn write_snapshot(","pub(crate) fn write_snapshot(").replace("fn read_snapshot(","pub(crate) fn read_snapshot(").replace("fn full_refresh_changes(","pub(crate) fn full_refresh_changes(")
    shell=(studio/"shell/mcp.rs").read_text()
    batch+="\n"+function(shell,"remove_asset_sources").replace("fn remove_asset_sources(","pub(crate) fn remove_asset_sources(")
    write(dest/"batch.rs",batch)
    edit=(studio/"properties/edit.rs").read_text()
    edit=edit.replace("mod authority;\n","").replace("pub(super) mod pivot;\n","").replace("pub(crate) use many::commit_all;\n","")
    write(dest/"properties/edit.rs",edit.split("#[cfg(test)]")[0])
    many=(studio/"properties/edit/many.rs").read_text()
    write(dest/"properties/edit/many.rs","use rbx_dom::{Instance, Variant};\nuse rbx_reflection::ReflectionDatabase;\n"+function(many,"stored_or_default"))
    for name in ("content","font","reference","sequence","orientation"):
        write(dest/f"properties/edit/{name}.rs",before_tests(studio/f"properties/edit/{name}.rs"))
    attr=(studio/"properties/attributes.rs").read_text()
    consts='\n'.join(line for line in attr.splitlines() if any(line.startswith(prefix+' '+name+':')
        for name in ("ATTRIBUTES_PROPERTY","TAGS_PROPERTY","MAX_ATTRIBUTE_NAME_LEN","STRING_TYPE_ID")
        for prefix in ("pub(crate) const","const")))
    white=attr[attr.index("const WHITE:"):attr.index("\n};",attr.index("const WHITE:"))+3]
    write(dest/"properties/attributes.rs","use rbx_dom::*;\nuse std::collections::BTreeMap;\n"+consts+"\n"+white+"\n"+
        "\n".join(function(attr,n) for n in ("attributes","tags","enum_types","validate_attribute_name","gone","put_attribute",
            "write_attributes","write_with_enums","default_value","add_tag","remove_tag","write_tags"))+
        "\n"+attr[attr.index("pub(crate) const ATTRIBUTE_TYPES:"):attr.index("\n];",attr.index("pub(crate) const ATTRIBUTE_TYPES:"))+3]+"\n")
    # The only UI-free fixtures are module paths; implementation is copied verbatim.
    source=(studio/"script_editor/source.rs").read_text().replace("#[cfg(test)]\nmod tests;\n","")
    write(dest/"script_source.rs",source)
    write(dest/"terrain.rs",(studio/"terrain/dom.rs").read_text())
    gui=(studio/"explorer/insert.rs").read_text()
    write(dest/"gui_defaults.rs","use rbx_reflection::ReflectionDatabase;\n"+function(gui,"gui_defaults"))
    keys=(studio/"shell/keys.rs").read_text()
    write(dest/"part_defaults.rs","use rbx_dom::*;\nuse rbx_reflection::ReflectionDatabase;\nconst PART_TYPE_BLOCK:u32=1;\n"+
        "\n".join(function(keys,n).replace("pub(super)","pub(crate)") for n in ("part_defaults_shape","apply_part_defaults","part_defaults")))
    clip=(studio/"shell/clipboard.rs").read_text()
    clipped=clip[clip.index("pub(super) struct Clipped"):clip.index("\n}",clip.index("pub(super) struct Clipped"))+3]
    write(dest/"clipboard.rs","use std::collections::{BTreeMap,HashMap};\nuse rbx_dom::*;\nconst ARCHIVABLE:&str=\"archivable\";\n"+
        clipped.replace("pub(super)","pub(crate)")+"\n"+
        "\n".join(function(clip,n).replace("pub(super)","pub(crate)") for n in
            ("snapshot","archivable","materialize","materialize_mapped","create","write_properties","remap","clone_into")))
    tests=(root/"tests/native_agent_contracts.rs").read_text()
    write(dest/"lib.rs",'#![allow(dead_code,unused_imports)]\nmod properties {pub(crate) mod edit; pub(crate) mod attributes;}\n'+
        'pub(crate) mod script_source;\nmod script_editor {pub(crate) use crate::script_source as source;}\n'+
        'mod terrain;\npub(crate) mod clipboard;\npub(crate) mod part_defaults;\npub(crate) mod gui_defaults;\n'+
        'mod explorer {pub(crate) use crate::gui_defaults as insert;}\n'+
        'mod shell {pub(crate) use crate::clipboard;pub(crate) use crate::part_defaults as keys;}\nmod batch;\n'+tests)
    if "--bridge" in sys.argv:
        with (dest/"lib.rs").open("a") as f: f.write((root/"tests/native_agent_bridge.rs").read_text())
        write(dest/"main.rs",'fn main() { rbx_agent_dom_regression::run_bridge(); }\n')
    env=os.environ.copy()
    if "--bridge" not in sys.argv and not env.get("RBX_GENERATOR_FIXTURES"):
        fixture=tmp/"generators.json"
        subprocess.run([os.environ.get("PYTHON","python3"),str(root/"scripts/test_developer_engine_java.py"),str(fixture)],check=True)
        env["RBX_GENERATOR_FIXTURES"]=str(fixture)
    env.setdefault("CARGO_BUILD_JOBS","2")
    env.setdefault("CARGO_TARGET_DIR",str(root/"target/agent-native-tests"))
    cargo=os.environ.get("CARGO",str(Path.home()/".cargo/bin/cargo"))
    command=[cargo,"run" if "--bridge" in sys.argv else "test","--manifest-path",str(tmp/"Cargo.toml")]
    if env.get("CARGO_NET_OFFLINE") == "true": command.append("--offline")
    subprocess.run(command,env=env,check=True)
