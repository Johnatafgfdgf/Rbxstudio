#!/usr/bin/env python3
"""Rebuild ONLY Java/UI around an already-built native Studio APK for emulator screenshot.

Diagnostics only. The resulting APK has a temporary key and an exported AI
Activity exclusively in the temporary manifest, NEVER in source or release.
Native libraries/assets are preserved byte-for-byte.
"""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ["ANDROID_HOME"])
BT = SDK / "build-tools/35.0.0"
ANDROID_JAR = SDK / "platforms/android-35/android.jar"
SOURCE = ROOT / "rbx-studio-android-0.6.11-arm64-v8a-experimental-debug.apk"
OUT = ROOT / "dist/ai-ui-native-preview"
OUT.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)

original_bytes = SOURCE.read_bytes()
with zipfile.ZipFile(io.BytesIO(original_bytes)) as old:
    if old.testzip() is not None:
        raise RuntimeError("Archived Android APK is not a valid zip")
    libs = {x.filename: old.read(x) for x in old.infolist()
            if x.filename.startswith("lib/") and not x.is_dir()}
    files = [(x.filename,old.read(x)) for x in old.infolist()
             if x.filename.startswith("assets/") and not x.is_dir()]
assets = OUT / "assets"
for name,data in files:
    target = OUT/name
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(data)

manifest = (ROOT / "android/StudioManifest.xml").read_text()
original = 'package="com.rbxnative.androidport"'
if manifest.count(original) != 1: raise RuntimeError("Unexpected package manifest")
manifest = manifest.replace(original,
                            'package="com.rbxnative.androidport.experimental"',1)
original_ai = ('android:name="com.rbxnative.androidport.StudioAiHub" '
               'android:exported="false"')
if manifest.count(original_ai) != 1:
    raise RuntimeError("Cannot isolate private AI Activity in temporary manifest")
# ONLY the diagnostic APK exports it for deterministic Android UI screenshots.
manifest = manifest.replace(original_ai,
                            original_ai.replace('exported="false"','exported="true"'),1)
manifest_path = OUT / "AndroidManifest.xml"
manifest_path.write_text(manifest)
classes = OUT/"classes"
classes.mkdir(exist_ok=True)
src = [p for p in (ROOT/"android/src").rglob("*.java")
       if p.name not in {"MainActivity.java","NativeSmokeTest.java",
                         "PlayerPad.java","StudioIcon.java"}]
helpers = ROOT / "upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile"
src.extend(helpers/(p+".java") for p in [
    "GpuiClipboard","GpuiFilePicker","GpuiPickerActivity","GpuiDeepLink",
    "GpuiPlatformView","GpuiVideoPlayer","GpuiCamera"])
run("javac","--release","11","-classpath",ANDROID_JAR,"-d",classes,*src)
run(BT/"d8","--lib",ANDROID_JAR,"--min-api","29","--output",OUT,
    *classes.rglob("*.class"))
res_apk=OUT/"res.apk"
run(BT/"aapt","package","-f","-M",manifest_path,"-I",ANDROID_JAR,
    "-S",ROOT/"android/res","-A",assets,"-F",res_apk)
unsigned=OUT/"unsigned.apk"
unsigned.write_bytes(res_apk.read_bytes())
with zipfile.ZipFile(unsigned,"a") as apk:
    apk.write(OUT/"classes.dex","classes.dex",compress_type=zipfile.ZIP_DEFLATED)
    for name,data in libs.items():
        apk.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
aligned=OUT/"aligned.apk"
run(BT/"zipalign","-f","-P","16","4",unsigned,aligned)
key=OUT/"preview.keystore"
if not key.exists():
    run("keytool","-genkeypair","-noprompt","-keystore",key,
        "-alias","androiddebugkey","-storepass","android",
        "-keypass","android","-keyalg","RSA","-keysize","2048",
        "-validity","40","-dname","CN=Disposable Screenshot Test")
signed=OUT/"preview.apk"
run(BT/"apksigner","sign","--ks",key,"--ks-pass","pass:android",
    "--key-pass","pass:android","--v4-signing-enabled","false",
    "--out",signed,aligned)
run(BT/"apksigner","verify","--verbose",signed)
for name,data in libs.items():
    with zipfile.ZipFile(signed) as artifact:
        assert artifact.read(name) == data, name
print("Screenshot-only native AI screen APK built:",
      signed.stat().st_size, "bytes, SHA256",
      hashlib.sha256(signed.read_bytes()).hexdigest())
