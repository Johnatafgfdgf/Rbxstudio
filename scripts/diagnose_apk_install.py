#!/usr/bin/env python3
"""Run a real Android PackageManager installation of the archived ARM64 APK.

This diagnoses the *existing compiled APK*. No re-signing or mutation.
Android's adb install result is more authoritative than aapt/zipfile alone.
"""
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dist" / "apk-install-diagnostics"
OUT.mkdir(parents=True, exist_ok=True)
REPORT = OUT / "report.txt"
apk_candidates = list(ROOT.glob("rbx-studio-android-0.6.11-arm64-v8a-experimental-debug.apk"))
if len(apk_candidates) != 1:
    raise SystemExit(f"Expected precisely one existing ARM64 APK, got {len(apk_candidates)}")
apk = apk_candidates[0]
sidecar = apk.with_suffix(".apk.sha256")
expected = sidecar.read_text().strip().split()[0].lower()
actual = hashlib.sha256(apk.read_bytes()).hexdigest()
if not re.fullmatch("[0-9a-f]{64}", expected) or actual != expected:
    raise SystemExit(f"APK checksum invalid (expected {expected}; actual {actual}).")
with zipfile.ZipFile(apk) as archive:
    damaged = archive.testzip()
    if damaged: raise SystemExit(f"Damaged ZIP member: {damaged}")
    names = archive.namelist()
    for item in ["AndroidManifest.xml", "classes.dex",
                 "lib/arm64-v8a/librbx_android_studio.so",
                 "lib/arm64-v8a/librbx_android_player.so"]:
        if item not in names:
            raise SystemExit(f"APK missing: {item}")
print(f"Archive verified: {apk.name}, {apk.stat().st_size} bytes, SHA256 {actual}")

def capture(args, timeout=90):
    try:
        result = subprocess.run(args, text=True, capture_output=True, timeout=timeout)
        return result.returncode, (result.stdout + "\n" + result.stderr).strip()
    except subprocess.TimeoutExpired:
        return 124, "ADB command timed out."

lines = [f"APK: {apk.name}", f"SHA256: {actual}", "ZIP: integrity OK"]
for title, args in [
    ("SDK", ["adb", "shell", "getprop", "ro.build.version.sdk"]),
    ("CPU ABIs", ["adb", "shell", "getprop", "ro.product.cpu.abilist"]),
    ("Device", ["adb", "shell", "getprop", "ro.product.model"]),
]:
    code, output = capture(args)
    lines.append(f"{title}: {output or 'Unavailable'} (exit {code})")
# Do not use -t / force / bypass security restrictions. Normal Android install only.
code, output = capture(["adb", "install", "--no-streaming", "-r", str(apk)], timeout=180)
lines.extend([f"ADB install exit code: {code}", f"ADB install output: {output}"])
if code == 0 and "Success" in output:
    lines.append("OUTCOME: INSTALLED by Android PackageManager; on-device launch NOT tested.")
    code2, output2 = capture(["adb", "shell", "pm", "path",
                              "com.rbxnative.androidport.experimental"])
    lines.append(f"Package path: {output2} (exit {code2})")
    if code2 != 0 or "base.apk" not in output2:
        lines.append("WARNING: installation returned success but package not located.")
else:
    lines.append("OUTCOME: INSTALL REJECTED. This is the OS parser/installer result.")
    if "INSTALL_FAILED_NO_MATCHING_ABIS" in output:
        lines.append("NOTE: emulator lacks ARM64 translator; parser diagnosis is inconclusive.")
    elif "INSTALL_PARSE_FAILED" in output:
        lines.append("NOTE: package parsing failure reproduced on emulator.")
    elif "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output:
        lines.append("NOTE: existing package signer conflicts with this certificate.")
REPORT.write_text("\n".join(lines) + "\n")
print("\n".join(lines))
# Installation rejection is a diagnostic failure, not a pass.
sys.exit(0 if code == 0 and "Success" in output else 1)
