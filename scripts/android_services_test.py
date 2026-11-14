#!/usr/bin/env python3
"""Exercise Keystore and SAF on a real emulator, including process restart.

The disposable test APK has no native editor and is never a deliverable.
Its temporary signing key is unrelated to the preserved product signing key.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_HOME'])
BT = SDK / 'build-tools/35.0.0'
PLATFORM = SDK / 'platforms/android-35/android.jar'
LOGS = ROOT / 'logs/android-services'
LOGS.mkdir(parents=True, exist_ok=True)

def run(*args, capture=False):
    return subprocess.run([str(arg) for arg in args], check=True, text=True,
                          stdout=subprocess.PIPE if capture else None, stderr=subprocess.STDOUT)

with tempfile.TemporaryDirectory(prefix='studio-services-') as temporary:
    build = Path(temporary)
    classes = build / 'classes'
    classes.mkdir()
    sources = [ROOT / 'android/src/com/rbxnative/androidport' / name for name in ['StudioServices.java', 'StudioDocuments.java']]
    sources += sorted((ROOT / 'android/test').rglob('*.java'))
    run('javac', '--release', '11', '-classpath', PLATFORM, '-d', classes, *sources)
    run(BT / 'd8', '--lib', PLATFORM, '--min-api', '29', '--output', build, *classes.rglob('*.class'))
    raw, signed = build / 'raw.apk', build / 'signed.apk'
    manifest = build / 'AndroidManifest.xml'
    shutil.copyfile(ROOT / 'android/ServiceTestManifest.xml', manifest)
    run(BT / 'aapt', 'package', '-f', '-M', manifest, '-I', PLATFORM, '-F', raw)
    with zipfile.ZipFile(raw, 'a') as apk:
        apk.write(build / 'classes.dex', 'classes.dex')
    key = build / 'ephemeral-service-test-only.jks'
    run('keytool', '-genkeypair', '-noprompt', '-keystore', key, '-alias', 'test', '-storepass', 'android',
        '-keypass', 'android', '-keyalg', 'RSA', '-validity', '2', '-dname', 'CN=Disposable service test')
    run(BT / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--out', signed, raw)
    run('adb', 'install', '-r', signed)
    try:
        run('adb', 'logcat', '-c')
        for phase in ['write', 'restart']:
            run('adb', 'shell', 'am', 'force-stop', 'com.rbxnative.androidport')
            output = run('adb', 'shell', 'am', 'instrument', '-w', '-e', 'phase', phase,
                         'com.rbxnative.androidport/.StudioServicesTest', capture=True).stdout
            (LOGS / (phase + '.txt')).write_text(output)
            print(output)
            if 'test_error' in output or 'PASS ' + phase not in output:
                raise RuntimeError('Android host service validation failed: ' + phase)
    finally:
        (LOGS / 'logcat.txt').write_text(run('adb', 'logcat', '-d', capture=True).stdout)
