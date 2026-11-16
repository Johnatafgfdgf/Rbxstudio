#!/usr/bin/env python3
"""Prevent historical product branding from leaking into the active Android UI.

This intentionally does NOT reject legal attribution, dependency paths, stable
credential aliases or older historical test fixtures. Removing those without
migration can break projects, API keys or required notices.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def check(path: str, required: list[str], forbidden: list[str]) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    for marker in required:
        if marker not in text:
            raise SystemExit(f"{path}: missing Android identity guard: {marker}")
    for marker in forbidden:
        if marker in text:
            raise SystemExit(f"{path}: historical brand resurfaced: {marker}")


check(
    "native/src/android.rs",
    ['.with_tag("RbxStudio")', "Inicializando o RBX Studio…"],
    ['.with_tag("RbxNative")', "Inicializando núcleo rbx-native…"],
)
check(
    "core/src/editor.rs",
    ['status: "Rbx Studio · editor Rust".into()'],
    ['status: "rbx-native · núcleo Rust".into()'],
)
check(
    "core/src/play.rs",
    ['editor.status = "Play local · servidor e cliente".into();'],
    ['editor.status = "Play local · servidor + cliente Rook".into();'],
)
patch = (ROOT / "patches/rbx-native--android-port.patch").read_text(encoding="utf-8")
added = "\n".join(line[1:] for line in patch.splitlines()
                  if line.startswith("+") and not line.startswith("+++"))
for marker in [
    '"name": "Rbx Studio Dark"',
    '"name": "Rbx Studio Light"',
    '"name": "Rbx Studio High contrast"',
    '"author": "Rbx Studio"',
    "<source>Rbx Studio</source>",
    '"serverInfo": { "name": "Rbx Studio"',
    "Reads the place open in the Rbx Studio editor",
    "Upload from Rbx Studio",
    "Serving with Argon (Rbx Studio live sync)",
    "{name} is not available in Rbx Studio",
]:
    if marker not in added:
        raise SystemExit(f"Android patch missing current branding: {marker}")
for marker in [
    '"name": "rbx-native Dark"',
    '"name": "rbx-native Light"',
    '"name": "rbx-native High contrast"',
    "<source>rbx-native</source>",
    '"serverInfo": { "name": "rbx-native"',
    "Upload from rbx-native",
    "Serving with Argon (rbx-native live sync)",
]:

    if marker in added:
        raise SystemExit(f"Android patch contains outdated user-facing text: {marker}")
packager = (ROOT / "scripts/package_apk.py").read_text(encoding="utf-8")
for marker in ["legacy={", '"MainActivity.java"', '"NativeSmokeTest.java"',
               "sources=[source for source in sources if source.name not in legacy]"]:
    if marker not in packager:
        raise SystemExit(f"Legacy Java exclusion missing: {marker}")
print("Android product branding and GPUI packaging guards passed.")
