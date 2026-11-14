#!/usr/bin/env python3
import os
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[1]
sdk = pathlib.Path(os.environ.get("ANDROID_HOME", root.parent / "tools/android-sdk"))
ndk = pathlib.Path(os.environ.get("ANDROID_NDK_HOME", sdk / "android-ndk-r28c"))
bin_dir = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
arch = os.environ.get("PORT_ARCH", "aarch64-linux-android")
clang = bin_dir / (arch + "29-clang")
env = os.environ.copy()
env.update({"CARGO_TARGET_" + arch.upper().replace("-", "_") + "_LINKER": str(clang),
            "CC_" + arch.replace("-", "_"): str(clang),
            "CXX_" + arch.replace("-", "_"): str(clang) + "++",
            "AR_" + arch.replace("-", "_"): str(bin_dir / "llvm-ar"),
            "CXXSTDLIB_" + arch.replace("-", "_"): "c++_shared",
            "CARGO_BUILD_JOBS": env.get("CARGO_BUILD_JOBS", "3"),
            "CARGO_TARGET_DIR": str(root / "target/android"),
            "RUSTFLAGS": "-C link-arg=-Wl,-z,max-page-size=16384"})
cargo = pathlib.Path(os.environ.get("CARGO", pathlib.Path.home() / ".cargo/bin/cargo"))
raise SystemExit(subprocess.call([str(cargo), *(sys.argv[1:] or ["build", "-p", "rbx_android"]), "--target", arch], cwd=root, env=env))

