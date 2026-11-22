#!/usr/bin/env python3
"""Build current Java/resources with audited native payloads and ABI guards.

This creates a disposable CI APK. Delivery uses the preserved project key.
"""
import hashlib
import io
import json
import os
import re
from pathlib import Path
import subprocess
import zipfile
import shutil

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_HOME'])
BT = SDK / 'build-tools/35.0.0'
ABI = os.environ.get('STUDIO_ABI', 'x86_64')
if ABI not in ('x86_64', 'arm64-v8a'): raise ValueError('Unsupported Android ABI')
APK = Path(os.environ.get('STUDIO_SOURCE_APK', ROOT / f'dist/rbx-native-android-0.4-{ABI}-debug.apk'))
DELIVERY = Path(os.environ.get('STUDIO_OUTPUT_APK',APK))
DELIVERY.parent.mkdir(parents=True,exist_ok=True)
OUT = ROOT / 'android/build/repack'
OUT.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

source = APK.read_bytes()
expected = os.environ.get("STUDIO_SOURCE_SHA256")
if expected and hashlib.sha256(source).hexdigest() != expected: raise ValueError("Native base APK checksum mismatch")
preserved = {}
assets = OUT / 'assets'
assets.mkdir(exist_ok=True)
with zipfile.ZipFile(io.BytesIO(source)) as original:
    for item in original.infolist():
        assert '..' not in Path(item.filename).parts
        if item.filename.startswith('assets/') and not item.is_dir():
            path = OUT / item.filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(original.read(item))
        if item.filename.startswith('lib/'):
            preserved[item.filename] = original.read(item)
replaced = {}
for variable, library in [('STUDIO_EDITOR_OVERRIDE','librbx_android_studio.so'),
                          ('STUDIO_PLAYER_OVERRIDE','librbx_android_player.so'),
                          ('STUDIO_CPP_OVERRIDE','libc++_shared.so')]:
    if not os.environ.get(variable): continue
    name = f'lib/{ABI}/{library}'
    replacement = Path(os.environ[variable]).read_bytes()
    if name not in preserved: raise ValueError('Override missing from base APK: '+name)
    if replacement[:6] != b'\x7fELF\x02\x01' or int.from_bytes(replacement[18:20], 'little') != {'x86_64': 62, 'arm64-v8a': 183}[ABI]:
        raise ValueError('Native override has the wrong ELF architecture: '+library)
    replaced[name] = {'source_sha256': hashlib.sha256(preserved[name]).hexdigest(),
                      'replacement_sha256': hashlib.sha256(replacement).hexdigest()}
    preserved[name] = replacement
if any(not name.startswith(f'lib/{ABI}/') for name in preserved):
    raise ValueError('Base APK contains libraries for a different ABI')
classes = OUT / 'classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir(exist_ok=True)
helpers = ROOT / 'upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile'
sources = [p for p in (ROOT / 'android/src').rglob('*.java') if p.name not in {'MainActivity.java','NativeSmokeTest.java','PlayerPad.java','StudioIcon.java'}]
sources.extend(helpers / (name + '.java') for name in ['GpuiClipboard', 'GpuiFilePicker',
    'GpuiPickerActivity', 'GpuiDeepLink', 'GpuiPlatformView', 'GpuiVideoPlayer', 'GpuiCamera'])
source_hashes = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
run('javac', '--release', '11', '-classpath', SDK / 'platforms/android-35/android.jar', '-d', classes, *sources)
if source_hashes != {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}:
    raise ValueError('Java sources changed while compiling; rebuild from a stable checkpoint')
run(BT / 'd8', '--lib', SDK / 'platforms/android-35/android.jar', '--min-api', '29',
    '--output', OUT, *classes.rglob('*.class'))
manifest = OUT / 'AndroidManifest.xml'
manifest_text=(ROOT / 'android/StudioManifest.xml').read_text()
suffix=os.environ.get('STUDIO_PACKAGE_SUFFIX')
if suffix:
    import re
    if not re.fullmatch(r'[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*',suffix): raise ValueError('Invalid package suffix')
    manifest_text=manifest_text.replace('package="com.rbxnative.androidport"',f'package="com.rbxnative.androidport.{suffix}"',1)
manifest.write_text(manifest_text)
unsigned = OUT / 'unsigned.apk'
run(BT / 'aapt', 'package', '-f', '-M', manifest, '-I', SDK / 'platforms/android-35/android.jar',
    '-S', ROOT / 'android/res', '-A', assets, '-F', unsigned)
with zipfile.ZipFile(unsigned, 'a') as target:
    target.write(OUT / 'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
    for name, data in preserved.items():
        target.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
aligned = OUT / 'aligned.apk'
run(BT / 'zipalign', '-f', '-P', '16', '4', unsigned, aligned)
delivery_key = os.environ.get('STUDIO_SIGNING_KEY')
key = Path(delivery_key) if delivery_key else OUT / 'disposable-test.keystore'
if delivery_key and not key.is_file(): raise FileNotFoundError('The preserved Android signing key is missing')
if not key.exists():
    run('keytool', '-genkeypair', '-noprompt', '-keystore', key, '-alias', 'androiddebugkey',
        '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '30', '-dname', 'CN=Disposable Android Layout Test')
signed = OUT / 'signed.apk'
run(BT / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
    '--v4-signing-enabled', 'false', '--out', signed, aligned)
run(BT / 'apksigner', 'verify', '--verbose', signed)
run(BT / 'zipalign', '-c', '-P', '16', '4', signed)
with zipfile.ZipFile(signed) as target:
    for name, data in preserved.items():
        assert target.read(name) == data, 'Changed compiled payload: ' + name
signed.replace(DELIVERY)
DELIVERY.with_suffix('.apk.sha256').write_text(hashlib.sha256(DELIVERY.read_bytes()).hexdigest() + '  ' + DELIVERY.name + '\n')
report = {'source_apk_sha256': hashlib.sha256(source).hexdigest(),
    'repacked_apk_sha256': hashlib.sha256(DELIVERY.read_bytes()).hexdigest(),
    'native_libraries_unchanged': sorted(set(preserved) - set(replaced)),
    'native_libraries_replaced': replaced, 'java_recompiled': True, 'abi': ABI,
    'delivery': bool(delivery_key), 'java_source_sha256': source_hashes,
    'packaged_manifest_sha256': hashlib.sha256(manifest_text.encode()).hexdigest()}
DELIVERY.with_suffix('.inspection.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
