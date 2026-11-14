#!/usr/bin/env python3
"""Capture and replay touch flows against an installed Studio APK."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess
import time
from xml.etree import ElementTree

PACKAGE = "com.rbxnative.androidport"
ACTIVITY = "dev.gpui.mobile.GpuiActivity"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
APP_ANR = re.compile(
    r"\bANR in (?:Window\{[^}\n]*\s)?"
    + re.escape(PACKAGE)
    + r"(?=[:/\s})]|$)"
)


def png_size(data):
    if len(data) < 24 or data[:8] != PNG_SIGNATURE or data[12:16] != b"IHDR":
        raise ValueError("Android did not return a PNG screenshot")
    return struct.unpack(">II", data[16:24])


def node_center(tree, selector):
    if len(selector) != 1:
        raise ValueError("Select an Android node by exactly one attribute")
    attr, label = next(iter(selector.items()))
    android_attr = {"text": "text", "description": "content-desc", "id": "resource-id"}[attr]
    matches = [node for node in tree.iter("node") if node.get(android_attr) == label]
    if len(matches) != 1:
        raise ValueError(f"Selector {selector!r} matched {len(matches)} Android nodes")
    x0, y0, x1, y1 = map(int, re.findall(r"-?\d+", matches[0].attrib["bounds"]))
    if x0 >= x1 or y0 >= y1:
        raise ValueError(f"Selector {selector!r} has empty bounds")
    return ((x0 + x1) // 2, (y0 + y1) // 2)


class Device:
    def __init__(self, serial, output):
        self.command = ["adb", "-s", serial] if serial else ["adb"]
        self.output = output

    def adb(self, *args, timeout=45):
        command = [*self.command, *map(str, args)]
        result = subprocess.run(command, capture_output=True, timeout=timeout)
        if result.returncode:
            detail = (result.stderr or result.stdout).decode(errors="replace").strip()
            raise RuntimeError(f"ADB {args[0]} exited with {result.returncode}: {detail}")
        return result.stdout

    def shell(self, *args, timeout=45):
        return self.adb("shell", shlex.join(map(str, args)), timeout=timeout).decode(errors="replace")

    def capture(self, name):
        data = self.adb("exec-out", "screencap", "-p")
        size = png_size(data)
        (self.output / f"{name}.png").write_bytes(data)
        return size

    def hierarchy(self, name):
        remote = f"/data/local/tmp/rbx-audit-{time.time_ns()}.xml"
        self.shell("uiautomator", "dump", remote, timeout=25)
        try:
            data = self.adb("exec-out", "cat", remote)
            tree = ElementTree.fromstring(data)
            (self.output / f"{name}.xml").write_bytes(data)
            return tree
        finally:
            self.shell("rm", "-f", remote)

    def focused(self):
        return self.shell("dumpsys", "activity", "activities")


def wait_for(predicate, deadline, description):
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(1)
    raise TimeoutError(description)


def app_anr(log_path, offset):
    with log_path.open("rb") as log:
        log.seek(offset)
        for line in log:
            message = line.decode(errors="replace").strip()
            if APP_ANR.search(message):
                return message
    return None


def act(device, step, size, expected_size, log_path):
    offset = log_path.stat().st_size
    if "tap" in step or "swipe" in step:
        if expected_size is None or size != tuple(expected_size):
            raise ValueError(f"Coordinate input requires a matching screenshot size, observed {size}")
        key = "tap" if "tap" in step else "swipe"
        values = step[key]
        points = values if key == "tap" else values[:4]
        if len(points) != (2 if key == "tap" else 4) or any(
            not 0 <= coordinate < size[index % 2] for index, coordinate in enumerate(points)
        ):
            raise ValueError(f"Invalid {key} coordinates for screenshot {size}")
        device.shell("input", "touchscreen", key, *values)
    elif "node" in step:
        point = node_center(device.hierarchy(step["name"] + "-target"), step["node"])
        device.shell("input", "touchscreen", "tap", *point)
    elif "key" in step:
        device.shell("input", "keyevent", step["key"])
    elif "text" in step:
        if not step["text"].isascii():
            raise ValueError("Use taps on the Android keyboard to test Unicode IME input")
        device.shell("input", "text", step["text"].replace(" ", "%s"))

    expectations = step.get("expect", {})
    deadline = time.monotonic() + step.get("timeout", 30)
    if "log" in expectations:
        marker = expectations["log"]
        def new_log_has_marker():
            with log_path.open("rb") as log:
                log.seek(offset)
                return marker.encode() in log.read()

        wait_for(
            new_log_has_marker,
            deadline,
            f"Missing log marker {marker!r}",
        )
    if "activity" in expectations:
        activity = expectations["activity"]
        wait_for(
            lambda: any(
                activity in line and ("mResumedActivity" in line or "topResumedActivity" in line)
                for line in device.focused().splitlines()
            ),
            deadline,
            f"Activity {activity!r} never came to the foreground",
        )
    if "node" in expectations:
        node_center(device.hierarchy(step["name"] + "-expected"), expectations["node"])
    return "passed" if expectations else "captured"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--serial")
    parser.add_argument("--plan", type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    plan = json.loads(args.plan.read_text()) if args.plan else {}
    device = Device(args.serial, args.out)
    report = {"apk": args.apk.name, "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "steps": []}
    log_path = args.out / "logcat.txt"
    result_path = args.out / "results.json"
    logcat = None
    session_marker = None
    current = "device"
    try:
        report["sdk"] = device.shell("getprop", "ro.build.version.sdk").strip()
        report["abi"] = device.shell("getprop", "ro.product.cpu.abilist").strip()
        report["density"] = device.shell("wm", "density").strip()
        if device.shell("getprop", "sys.boot_completed").strip() != "1":
            raise RuntimeError("Android has not completed boot")
        current = "install"
        report["install"] = device.adb("install", "-r", args.apk, timeout=240).decode().strip()
        if "Success" not in report["install"].splitlines():
            raise RuntimeError(report["install"])
        with log_path.open("wb") as log:
            logcat = subprocess.Popen(
                [*device.command, "logcat", "-T", "1", "-v", "threadtime"],
                stdout=log,
                stderr=subprocess.STDOUT,
            )
            current = "launch"
            marker = f"RBX_UI_AUDIT_{time.time_ns()}".encode()
            device.shell("log", "-t", "RbxUiAudit", marker.decode())
            wait_for(
                lambda: marker in log_path.read_bytes(),
                time.monotonic() + 30,
                "The Android log session marker did not arrive",
            )
            session_marker = log_path.read_bytes().index(marker) + len(marker)

            def native_started():
                with log_path.open("rb") as captured:
                    captured.seek(session_marker)
                    return b"NATIVE_INITIALIZED = true" in captured.read()

            report["launch"] = device.shell("am", "start", "-W", "-n", f"{PACKAGE}/{ACTIVITY}", timeout=120)
            if "Status: ok" not in report["launch"] and "Status: timeout" not in report["launch"]:
                raise RuntimeError(report["launch"])
            wait_for(
                native_started,
                time.monotonic() + 180,
                "The GPUI native startup marker did not arrive",
            )
            size = device.capture("00-launch")
            if anr := app_anr(log_path, session_marker):
                raise RuntimeError(anr)
            report["initial_screenshot"] = size
            report["steps"].append({"name": current, "status": "passed", "verified": "GPUI startup marker"})
            try:
                tree = device.hierarchy("00-launch")
                report["android_app_nodes"] = sum(node.get("package") == PACKAGE for node in tree.iter("node"))
            except (ValueError, OSError, RuntimeError, subprocess.SubprocessError, ElementTree.ParseError) as error:
                report["hierarchy_error"] = str(error)
            for step in plan.get("steps", []):
                current = step["name"]
                if re.fullmatch(r"[a-zA-Z0-9_-]+", current) is None:
                    raise ValueError(f"Unsafe step name {current!r}")
                status = act(device, step, size, plan.get("screenshot_size"), log_path)
                time.sleep(step.get("settle", 1))
                size = device.capture(current)
                if anr := app_anr(log_path, session_marker):
                    raise RuntimeError(anr)
                report["steps"].append({"name": current, "status": status, "screenshot": size})
    except (ValueError, OSError, RuntimeError, subprocess.SubprocessError, ElementTree.ParseError) as error:
        failure = {"name": current, "status": "failed", "error": str(error)}
        if session_marker is not None and (anr := app_anr(log_path, session_marker)):
            failure["android_anr"] = anr
        report["steps"].append(failure)
        raise
    finally:
        if logcat:
            logcat.terminate()
            try:
                logcat.wait(timeout=10)
            except subprocess.TimeoutExpired:
                logcat.kill()
                logcat.wait()
        result_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
