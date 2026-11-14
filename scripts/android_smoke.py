#!/usr/bin/env python3
"""Run with an existing adb device, or --software-emulator in one sandbox.

This runs genuine Android instrumentation against the compiled JNI app.
It never substitutes images or mocks for the renderer.
"""
import os
import pathlib
import subprocess
import sys
import time
import json

root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path(os.environ.get("ANDROID_HOME",root.parent/"tools/android-sdk"))
adb=sdk/"platform-tools/adb"
out=root/"dist/android-test";out.mkdir(parents=True,exist_ok=True)
emulator=None
def run(*args,timeout=45,check=True):
    return subprocess.run([str(adb),*map(str,args)],capture_output=True,timeout=timeout,check=check)
try:
    if "--software-emulator" in sys.argv:
        log=open(out/"emulator.log","wb")
        emulator=subprocess.Popen([str(sdk/"emulator/emulator"),"-avd",os.environ.get("PORT_EMULATOR_AVD","rbxnative"),"-read-only","-port","5570","-no-window","-no-audio","-no-boot-anim","-no-snapshot","-accel","off","-gpu","swiftshader","-memory","2048","-cores","4","-feature","Vulkan"],stdout=log,stderr=subprocess.STDOUT)
        run("start-server")
    deadline=time.monotonic()+900
    while time.monotonic()<deadline:
        result=run("shell","getprop","sys.boot_completed",check=False,timeout=15)
        if result.stdout.strip()==b"1":break
        print("Waiting for Android boot",flush=True);time.sleep(10)
    else:raise RuntimeError("Android did not boot within 15 minutes")
    print("Android booted",flush=True)
    run("shell","wm","size","1280x720")
    run("shell","wm","density","240")
    apk=root/"dist/rbx-native-android-0.3-x86_64-debug.apk"
    while not apk.exists():
        if time.monotonic()>deadline:raise RuntimeError("x86_64 APK unavailable")
        time.sleep(5)
    while True:
        stamp=apk.stat().st_mtime_ns
        run("shell","am","force-stop","com.rbxnative.androidport",check=False)
        print(run("install","--no-incremental","-r",apk,timeout=180).stdout.decode(),flush=True)
        with open(out/"instrumentation.txt","wb") as live:
            args=[str(adb),"shell","am","instrument","-w"]
            if "--software-emulator" in sys.argv:args.extend(["-e","software","true"])
            args.append("com.rbxnative.androidport/.NativeSmokeTest")
            test=subprocess.Popen(args,stdout=live,stderr=subprocess.STDOUT)
            due=time.monotonic()+480
            while test.poll() is None and time.monotonic()<due:
                try:
                    logs=run("logcat","-d","-t","300","-s","RbxNative","AndroidRuntime",check=False,timeout=10).stdout
                    (out/"logcat.txt").write_bytes(logs)
                except subprocess.TimeoutExpired as e:
                    (out/"adb-timeout.txt").write_text(str(e))
                time.sleep(4)
            if test.poll() is None:test.kill()
        text=(out/"instrumentation.txt").read_text(errors="replace");print(text,flush=True)
        if "error=" in text or "INSTRUMENTATION_CODE: -1" not in text:
            print((out/"logcat.txt").read_text(errors="replace")[-15000:] if (out/"logcat.txt").exists() else "No logcat available",flush=True)
        for name in ("android-validation.png","android-validation.json","android-play.png","android-play.json","android-failure.png","android-failure.json"):
            result=run("exec-out","run-as","com.rbxnative.androidport","cat","files/"+name,check=False)
            valid=result.returncode==0
            if name.endswith(".png"):valid=valid and result.stdout.startswith(b"\x89PNG")
            else:
                try:json.loads(result.stdout)
                except (ValueError,UnicodeDecodeError):valid=False
            if valid:(out/name).write_bytes(result.stdout)
            else:(out/(name+".error.txt")).write_bytes(result.stdout+result.stderr)
        if "INSTRUMENTATION_CODE: -1" in text and "error=" not in text:break
        if "--watch" not in sys.argv:raise RuntimeError("Android instrumentation failed")
        print("Android failed; waiting for rebuilt APK",flush=True)
        while apk.stat().st_mtime_ns==stamp:time.sleep(3)
finally:
    if emulator:
        emulator.terminate()
        try:emulator.wait(timeout=10)
        except subprocess.TimeoutExpired:emulator.kill()
