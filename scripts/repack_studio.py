#!/usr/bin/env python3
"""Test the current Java/manifest/theme with byte-identical native libraries.

This creates a disposable CI APK. Delivery uses the preserved project key.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_HOME'])
BT = SDK / 'build-tools/35.0.0'
ABI = os.environ.get('STUDIO_ABI', 'x86_64')
if ABI not in ('x86_64', 'arm64-v8a'): raise ValueError('Unsupported Android ABI')
APK = ROOT / f'dist/rbx-native-android-0.4-{ABI}-debug.apk'
OUT = ROOT / 'android/build/repack'
OUT.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

source = APK.read_bytes()
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
if os.environ.get('STUDIO_PLAYER_OVERRIDE'):
    name = f'lib/{ABI}/librbx_android_player.so'
    replacement = Path(os.environ['STUDIO_PLAYER_OVERRIDE']).read_bytes()
    assert replacement[:6] == b'\x7fELF\x02\x01' and int.from_bytes(replacement[18:20], 'little') == {'x86_64': 62, 'arm64-v8a': 183}[ABI]
    replaced[name] = {'source_sha256': hashlib.sha256(preserved[name]).hexdigest(),
                      'replacement_sha256': hashlib.sha256(replacement).hexdigest()}
    preserved[name] = replacement
classes = OUT / 'classes'
classes.mkdir(exist_ok=True)
helpers = ROOT / 'upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile'
sources = list((ROOT / 'android/src').rglob('*.java'))
sources.extend(helpers / (name + '.java') for name in ['GpuiClipboard', 'GpuiFilePicker',
    'GpuiPickerActivity', 'GpuiDeepLink', 'GpuiPlatformView', 'GpuiVideoPlayer', 'GpuiCamera'])
run('javac', '--release', '11', '-classpath', SDK / 'platforms/android-35/android.jar', '-d', classes, *sources)
run(BT / 'd8', '--lib', SDK / 'platforms/android-35/android.jar', '--min-api', '29',
    '--output', OUT, *classes.rglob('*.class'))
manifest = OUT / 'AndroidManifest.xml'
manifest.write_bytes((ROOT / 'android/StudioManifest.xml').read_bytes())
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
signed.replace(APK)
APK.with_suffix('.apk.sha256').write_text(hashlib.sha256(APK.read_bytes()).hexdigest() + '  ' + APK.name + '\n')
report = {'source_apk_sha256': hashlib.sha256(source).hexdigest(),
    'repacked_apk_sha256': hashlib.sha256(APK.read_bytes()).hexdigest(),
    'native_libraries_unchanged': sorted(set(preserved) - set(replaced)),
    'native_libraries_replaced': replaced, 'java_recompiled': True, 'abi': ABI,
    'delivery': bool(delivery_key)}
(ROOT / 'dist/repack-inspection.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
