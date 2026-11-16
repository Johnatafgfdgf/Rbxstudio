#!/usr/bin/env python3
"""Package the actual Cargo-built library with the Android SDK (no WebView)."""
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
SDK=pathlib.Path(os.environ.get("ANDROID_HOME",ROOT.parent/"tools/android-sdk"))
NDK=pathlib.Path(os.environ.get("ANDROID_NDK_HOME",SDK/"android-ndk-r28c"))
BUILD=ROOT/"android/build"
DIST=ROOT/"dist"
studio=os.environ.get("PORT_APP")=="studio"
experimental=studio and os.environ.get("PORT_EXPERIMENTAL_PACKAGE")=="1"
version="0.6.11" if studio else "0.3"
library_name="rbx_android_studio" if studio else "rbx_android"
BT=SDK/"build-tools/35.0.0"
PLATFORM=SDK/"platforms/android-35/android.jar"
BUILD.mkdir(parents=True,exist_ok=True);DIST.mkdir(exist_ok=True)
subprocess.run([os.environ.get("PYTHON","python3"),str(ROOT/"scripts/license_notices.py")],check=True,cwd=ROOT)
assets=BUILD/"assets";assets.mkdir(exist_ok=True)
shutil.copy(NDK/"toolchains/llvm/prebuilt/linux-x86_64/NOTICE",assets/"ANDROID_NDK_NOTICE.txt")
rust_notices=list((ROOT/"docs/license-sources").glob("rust-1.99.1--*"))
if rust_notices:(assets/"RUST_LICENSES.txt").write_text("\n\n".join(p.read_text() for p in sorted(rust_notices)))
notices=("Rbx Studio" if studio else "rbx-native Android")+" — "+version+"\n\n"
notices+="Base: chteau/rbx-native (MIT), commit eb85a1088a69f4159598b4fed0ae26956573c175.\n"
notices+="Runtime/física: AsyncFinn/rook (Apache-2.0), commit 8680da0fde3ded84731b7422c59de26db0e14135.\n"
notices+="Não inclui Stew, assets proprietários ou binários do Roblox. Texturas externas requerem acesso autorizado.\n"
if studio:
    notices+="Editor GPUI original em validação Android. Não é uma declaração de paridade funcional. Serviços de arquivos, janelas, entrada, Play e ferramentas auxiliares exigem testes Android.\n"
    notices+="Backend móvel: longbridge/gpui-mobile, commit 0b882efdac7f524e0bb0b1d4c886b2aa752f9f20 (opção Apache-2.0).\n"
else:
    notices+="GPUI desktop, luau-lsp, publicação, autenticação Roblox, saída de áudio Android, multiplayer Internet e avatares completos ainda não foram portados. Play usa Session/servidor/cliente originais Rook, com controle Android.\n"
for path in ["upstream/rbx-native/LICENSE","upstream/rook/NOTICE","upstream/rook/LICENSE"]:
    notices+="\n\n"+path+"\n"+(ROOT/path).read_text()
(assets/"THIRD_PARTY_NOTICES.txt").write_text(notices)
classes=BUILD/"classes"
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
sources=list((ROOT/"android/src").rglob("*.java"))
if studio:
    # The GPUI launcher is the only Studio entry point. Never ship the
    # unrelated 0.3 MainActivity, its instrumentation, or their standalone
    # legacy helper classes in the 0.6.11 application DEX.
    # RbxGeometry is still used by current branding, loading UI and player.
    # It must remain packaged until its symbols are migrated safely.
    legacy={"MainActivity.java", "NativeSmokeTest.java",
            "PlayerPad.java", "StudioIcon.java"}
    sources=[source for source in sources if source.name not in legacy]
    helpers=ROOT/"upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile"
    sources.extend(helpers/(name+".java") for name in ["GpuiClipboard","GpuiFilePicker","GpuiPickerActivity","GpuiDeepLink","GpuiPlatformView","GpuiVideoPlayer","GpuiCamera"])
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True,cwd=ROOT)
run("javac","--release","11","-classpath",PLATFORM,"-d",classes,*sources)
run(BT/"d8","--lib",PLATFORM,"--min-api","29","--output",BUILD,*classes.rglob("*.class"))
base=BUILD/"base.apk"
manifest=ROOT/"android"/("StudioManifest.xml" if studio else "AndroidManifest.xml")
packaged_manifest=BUILD/"AndroidManifest.xml"
manifest_text = manifest.read_text()
if experimental:
    # Experimental builds use their own Android application identity: install
    # beside an existing Studio without overwriting its app data or requiring
    # the original signing certificate.
    original_id='package="com.rbxnative.androidport"'
    assert original_id in manifest_text, "Unexpected Studio package ID"
    manifest_text=manifest_text.replace(original_id, 'package="com.rbxnative.androidport.experimental"', 1)
    manifest_text=manifest_text.replace('android:label="Rbx Studio"', 'android:label="Rbx Studio Experimental"', 1)
if os.environ.get("PORT_DEBUGGABLE") == "0":
    manifest_text = manifest_text.replace('android:debuggable="true"', 'android:debuggable="false"')
packaged_manifest.write_text(manifest_text)
manifest=packaged_manifest
run(BT/"aapt","package","-f","-M",manifest,"-I",PLATFORM,"-S",ROOT/"android/res","-A",assets,"-F",base)
raw=BUILD/"unsigned.apk";shutil.copy(base,raw)
arch=os.environ.get("PORT_ARCH","aarch64-linux-android")
abi={"aarch64-linux-android":"arm64-v8a","x86_64-linux-android":"x86_64"}[arch]
library=ROOT/"target/android"/arch/os.environ.get("PORT_PROFILE","debug")/("lib"+library_name+".so")
if not library.exists():raise SystemExit("Cargo library missing: "+str(library))
with zipfile.ZipFile(raw,"a") as apk:
    apk.write(BUILD/"classes.dex","classes.dex",compress_type=zipfile.ZIP_DEFLATED)
    apk.write(library,"lib/"+abi+"/lib"+library_name+".so",compress_type=zipfile.ZIP_DEFLATED)
    if studio:
        player_library=library.with_name("librbx_android_player.so")
        if not player_library.is_file():raise SystemExit("Original Rook player library missing: "+str(player_library))
        apk.write(player_library,"lib/"+abi+"/librbx_android_player.so",compress_type=zipfile.ZIP_DEFLATED)
    cpp=NDK/"toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib"/arch/"libc++_shared.so"
    apk.write(cpp,"lib/"+abi+"/libc++_shared.so",compress_type=zipfile.ZIP_DEFLATED)
aligned=BUILD/"aligned.apk";run(BT/"zipalign","-f","-P","16","4",raw,aligned)
# A new key on every CI runner changes the APK certificate and prevents
# Android from installing upgrades over previously installed versions.
# For reproducible upgrades, pass PORT_SIGNING_KEYSTORE_PATH to a persistent,
# PRIVATE key restored from a GitHub Actions secret (never commit it).
provided_key = os.environ.get("PORT_SIGNING_KEYSTORE_PATH")
if studio and os.environ.get("PORT_REQUIRE_PERSISTENT_SIGNING") == "1" and not provided_key:
    raise SystemExit("Persistent Studio signing key is required, but PORT_SIGNING_KEYSTORE_PATH is unset")
key = pathlib.Path(provided_key) if provided_key else ROOT/"tools/debug.keystore"
key.parent.mkdir(parents=True,exist_ok=True)
if provided_key and not key.is_file():
    raise SystemExit("Configured Android signing keystore does not exist")
alias = os.environ.get("PORT_SIGNING_KEY_ALIAS") or "androiddebugkey"
store_pass = os.environ.get("PORT_SIGNING_STORE_PASS") or "android"
key_pass = os.environ.get("PORT_SIGNING_KEY_PASS") or store_pass
if not key.exists():
    print("WARNING: no persistent Android signing key configured; upgrades may be rejected due to different signatures")
    run("keytool","-genkeypair","-noprompt","-keystore",key,"-alias",alias,
        "-storepass",store_pass,"-keypass",key_pass,"-keyalg","RSA","-keysize","2048",
        "-validity","10000","-dname",("CN=Rbx Studio Android Debug,O=Development,C=BR" if studio else "CN=rbx-native Android Debug,O=Development,C=BR"))
product_prefix="rbx-studio-android" if studio else "rbx-native-android"
apk=DIST/(product_prefix+"-"+version+"-"+abi+("-experimental" if experimental else "")+"-debug.apk")
signed=BUILD/"signed.apk"
# Passwords stay out of shell command arguments and logs. apksigner reads
# them from short-lived environment variables.
signing_env = dict(os.environ)
signing_env["RBX_STUDIO_SIGN_STORE_PASS"] = store_pass
signing_env["RBX_STUDIO_SIGN_KEY_PASS"] = key_pass
subprocess.run([str(BT/"apksigner"),"sign","--ks",str(key),"--ks-key-alias",alias,
    "--ks-pass","env:RBX_STUDIO_SIGN_STORE_PASS",
    "--key-pass","env:RBX_STUDIO_SIGN_KEY_PASS",
    "--v4-signing-enabled","false","--out",str(signed),str(aligned)],
    check=True,cwd=ROOT,env=signing_env)
run(BT/"apksigner","verify","--verbose",signed)
run(BT/"zipalign","-c","-P","16","4",signed)
signed.replace(apk)
old_sidecar=apk.with_suffix(".apk.idsig")
if old_sidecar.exists():old_sidecar.unlink()
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
(apk.with_suffix(".apk.sha256")).write_text(digest+"  "+apk.name+"\n")
print(json.dumps({"apk":str(apk),"sha256":digest,"bytes":apk.stat().st_size,"abi":abi}))
