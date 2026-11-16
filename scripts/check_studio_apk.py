#!/usr/bin/env python3
"""Inspect the *built* GPUI Android APK, not just source and HTML previews.

Only two inherited private credential service identifiers are tolerated in
classes.dex until a reversible credential migration is available.
"""
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
apks = sorted((ROOT / "dist").glob("rbx-studio-android-*.apk"))
if len(apks) != 1:
    raise SystemExit(f"Expected one Studio APK under dist, found {len(apks)}")
apk = apks[0]
abi = "arm64-v8a" if "arm64-v8a" in apk.name else "x86_64" if "x86_64" in apk.name else None
if abi is None:
    raise SystemExit(f"Unrecognized APK ABI in {apk.name}")

with ZipFile(apk) as packaged:
    files = set(packaged.namelist())
    for required in [
        "classes.dex", "AndroidManifest.xml", "assets/THIRD_PARTY_NOTICES.txt",
        f"lib/{abi}/librbx_android_studio.so",
        f"lib/{abi}/librbx_android_player.so",
    ]:
        if required not in files:
            raise SystemExit(f"{apk.name}: missing required file {required}")
    dex = packaged.read("classes.dex")
    for obsolete in [
        b"Lcom/rbxnative/androidport/MainActivity;",
        b"Lcom/rbxnative/androidport/NativeSmokeTest;",
        b"Lcom/rbxnative/androidport/PlayerPad;",
        b"Lcom/rbxnative/androidport/StudioIcon;",
        b"Qualidade original do rbx-native",
        b"Build 0.3",
    ]:
        if obsolete in dex:
            raise SystemExit(f"{apk.name}: obsolete Android UI component detected")
    if b"Lcom/rbxnative/androidport/RbxGeometry;" not in dex:
        raise SystemExit(f"{apk.name}: geometry required by the current app is missing")
    if b"Lcom/rbxnative/androidport/StudioAiHub;" not in dex:
        raise SystemExit(f"{apk.name}: native AI provider hub was not packaged")
    if b"StudioAiHub" not in dex:
        raise SystemExit(f"{apk.name}: native AI hub is unavailable")
    allowed_private_ids = (
        b"https://apis.roblox.com/rbx-native",
        b"rbx-native-studio-credentials-v1",
    )
    for item in dex.split(b"\x00"):
        if b"rbx-native" in item and not any(item.endswith(value) for value in allowed_private_ids):
            raise SystemExit(f"{apk.name}: unexpected legacy identity in compiled Android UI")
    notices = packaged.read("assets/THIRD_PARTY_NOTICES.txt")
    for required in (b"chteau/rbx-native", b"AsyncFinn/rook"):
        if required not in notices:
            raise SystemExit(f"{apk.name}: required third-party attribution is missing")

print(f"Checked packed GPUI Studio identity and legacy exclusions: {apk.name}")
