#!/usr/bin/env python3
"""Assemble the first Android design review from the prepared rbx-native source."""

import argparse
import base64
from io import BytesIO
import json
from pathlib import Path
import re

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / "upstream/rbx-native"
STUDIO = NATIVE / "crates/rbx_studio/src"
REVIEW = ROOT / "preview/lote-01"
KIT_ICON_NAMES = {
    "align-horizontal-justify-center", "axis-3d", "box", "check", "chevron-down",
    "chevron-right", "clipboard-paste", "code", "copy", "crosshair", "ellipsis",
    "file", "file-code", "flame", "folder-open", "gamepad-2", "house", "key-round",
    "list-tree", "magnet", "maximize-2", "menu", "minimize-2", "mouse-pointer-2",
    "move-3d", "package", "panels-top-left", "play", "plus", "redo-2", "rotate-3d",
    "rotate-cw", "save", "scaling", "search", "sliders-horizontal", "trash", "undo-2",
    "user", "wrench", "x", "clock",
}
CLASSES = (
    "Workspace", "Terrain", "Camera", "Part", "SpawnLocation", "Players", "Lighting",
    "ReplicatedFirst", "ReplicatedStorage", "ServerScriptService", "ServerStorage",
    "StarterGui", "StarterPack", "StarterPlayer", "Teams", "SoundService", "TextChatService",
    "LocalizationService", "TestService", "Folder", "Model", "Script", "LocalScript", "ModuleScript",
    "WedgePart", "CornerWedgePart", "ScreenGui", "SurfaceGui", "BillboardGui", "MeshPart",
)


def rust(path):
    return (STUDIO / path).read_text()


def kebab(identifier):
    return re.sub(r"(?<!^)([A-Z]|(?<=\D)\d)", r"-\1", identifier).lower()


def data_url(data, mime="image/svg+xml"):
    return f"data:{mime};base64,{base64.b64encode(data).decode()}"


def thumbnail(path, width):
    with Image.open(path) as original:
        image = original.convert("RGB")
        image.thumbnail((width, width))
        output = BytesIO()
        image.save(output, "WEBP", quality=85, method=6)
        return data_url(output.getvalue(), "image/webp")


def extract_menus():
    source = rust("menu_bar.rs")
    fields = {
        "File": source.split("let mut file = vec![", 1)[1].split("];", 1)[0],
        "Edit": source.split('Menu::new("Edit")', 1)[1].split('.owned()', 1)[0],
        "Model": source.split("let mut model = vec![", 1)[1].split("];", 1)[0],
        "View": source.split('Menu::new("View")', 1)[1].split('.owned()', 1)[0],
    }
    menus = {}
    for key, source in fields.items():
        items = []
        pattern = r'MenuItem::(separator\(\)|action\("((?:[^"\\]|\\.)*)")'
        for match in re.finditer(pattern, source):
            string = match[2]
            if string in ("Require Graph", "Update Planning…"):
                # The clean project has these beta features turned off.
                continue
            items.append(None if string is None else string.replace(r"\u{2026}", "…"))
        if key == "File":
            items.append("Diagnóstico do motor…")
        menus[key] = items
    return menus


def insert_items(name):
    source = rust("shell/ribbon/commands.rs").split(f"static {name}_INSERTS:", 1)[1].split("];", 1)[0]
    return [
        [label, kebab(icon), class_name]
        for label, icon, class_name in re.findall(
            r'insert\(\s*"([^"]+)",\s*IconName::(\w+),\s*"([^"]+)"', source
        )
    ]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--kit-assets", required=True, type=Path,
                        help="Unpacked gpui-kit-assets 0.6.1 crate")
    parser.add_argument("--templates", required=True, type=Path,
                        help="Directory with the three images listed in launcher/home_window/images.rs")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--fragment", type=Path)
    args = parser.parse_args()

    tokens = dict(re.findall(r"pub\(crate\) const (\w+): \w+ = (0x[0-9A-F]+|[0-9]+\.?);", rust("mobile_style.rs")))
    css = []
    for name, value in tokens.items():
        key = name.lower().replace("_", "-")
        if value.startswith("0x"):
            css.append(f"--rbx-{key}: #{value[2:]}")
        elif "RADIUS" in name:
            css.append(f"--rbx-{key}: {value.rstrip('.')}px")
    css.append("--rbx-wash: color-mix(in srgb, var(--rbx-accent) 16%, transparent)")

    panel_sources = rust("shell/layout.rs").split("impl Panel {", 1)[1]
    panels = re.findall(r'Panel::\w+ => "([^"]+)"', panel_sources.split("fn key(self)", 1)[1].split("///", 1)[0])
    tabs = re.findall(r'Tab::\w+ => "([^"]+)"', rust("shell/ribbon.rs").split("fn label(self)", 1)[1].split("impl Shell", 1)[0])
    explorer_rows = []
    source = rust("shell/explorer_edit/menu.rs").split("let rows = [", 1)[1].split("let actions:", 1)[0]
    for key, icon, label in re.findall(r'row\(\s*"([^"]+)",\s*IconName::(\w+),\s*"([^"]+)"', source):
        explorer_rows.append([key, kebab(icon), label])
    ungroup = next(index for index, row in enumerate(explorer_rows) if row[0] == "ungroup")
    explorer_rows.insert(ungroup, ["group", "package", "Group as Model"])

    catalog = json.loads((REVIEW / "catalog.json").read_text())
    inserts = catalog["inserts"] | {
        "Part": insert_items("PART"),
        "Script": insert_items("SCRIPT") + [["ModuleScript (Class)", "package", "ModuleScript"]],
        "UI": insert_items("GUI"),
    }
    inserts["UI"].insert(2, ["AdGui", "megaphone", False])
    template_labels = re.findall(r'Template::\w+ => "([^"]+)"', rust("home/template.rs").split("pub(crate) fn create", 1)[0])
    templates = list(zip(template_labels[:3], template_labels[3:], strict=True))

    icon_names = KIT_ICON_NAMES | {row[1] for row in explorer_rows}
    icon_names |= {row[1] for rows in inserts.values() for row in rows if row[1]}
    icon_names |= {row[1] for groups in catalog["tools"].values() for group in groups for row in group}
    for name in sorted(icon_names):
        icon = args.kit_assets / "assets/icons" / (name + ".svg")
        css.append(f"--icon-{name}: url({data_url(icon.read_bytes())})")
    mark = []
    for svg in sorted((NATIVE / "assets/icons/brand").glob("rbx-mobile-*.svg")):
        fill = "#" + tokens["ACCENT"][2:] if "outline" in svg.name else "#F1F1FB"
        mark.extend(part.replace('fill="#000000"', f'fill="{fill}"') for part in re.findall(r"<path\b[^>]+/>", svg.read_text()))
    brand = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="300 320 930 910">' + "".join(mark) + "</svg>"
    css.append(f"--rbx-logo: url({data_url(brand.encode())})")

    slugs = dict(re.findall(r'\("([^"]+)", "([^"]+)"\)', rust("class_icons/slugs.rs")))

    def class_icons(pack):
        icons = {}
        for name in CLASSES:
            path = NATIVE / "assets/icons/default" / pack / (slugs.get(name, "part") + ".svg")
            if path.exists():
                icons[name] = data_url(path.read_bytes())
        return icons

    data = {
        "menus": extract_menus(), "panels": panels, "tabs": tabs, "tools": catalog["tools"],
        "inserts": inserts, "templates": templates, "explorerRows": explorer_rows,
        "classIcons": class_icons("dark"), "lightClassIcons": class_icons("light"),
        "images": [thumbnail(args.templates / name, width) for name, width in [
            ("baseplate.png", 637), ("classic-baseplate.png", 180), ("flat-terrain.png", 180)
        ]],
        "motion": {
            "openMs": int(tokens["OPEN_MS"]), "closeMs": int(tokens["CLOSE_MS"]),
            "sheetShift": float(tokens["SHEET_SHIFT"]), "drawerShift": float(tokens["DRAWER_SHIFT"]),
        },
    }
    if (len(panels), len(tabs), len(explorer_rows)) != (21, 7, 19):
        raise ValueError(f"Recheck the catalog: {len(panels)} panels, {len(tabs)} tabs, {len(explorer_rows)} Explorer actions")
    css_text = "#rbx-mobile-review { " + ";\n".join(css) + "; }\n" + (REVIEW / "style.css").read_text()
    license_text = (args.kit_assets / "LICENSE-LUCIDE").read_text().replace("-->", "")
    fragment = (
        "<!-- UI sample. Source icon notices:\n" + license_text + "\n-->\n"
        + "<style>\n" + css_text + "</style>\n"
        + (REVIEW / "view.html").read_text().rstrip().removesuffix("</section>")
        + '\n<script type="application/json">' + json.dumps(data, ensure_ascii=False).replace("<", "\\u003c") + "</script>\n"
        + "<script>\n" + (REVIEW / "review.js").read_text() + "\n</script>\n</section>\n"
    )
    if args.fragment:
        args.fragment.write_text(fragment)
    args.output.write_text(
        '<!doctype html>\n<html lang="pt-BR"><meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n'
        '<title>Rbx Studio · revisão 1</title>\n'
        '<style>html,body{margin:0;background:#0b1322}#rbx-mobile-review{--frame-height:100dvh}</style>\n'
        + fragment + "</html>\n"
    )
    print(f"{len(panels)} painéis, {len(tabs)} abas, {len(explorer_rows)} ações de contexto, {len(fragment)} caracteres")


if __name__ == "__main__":
    main()
