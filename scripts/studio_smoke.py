#!/usr/bin/env python3
"""Launch the complete original GPUI editor, capture it, and resume it.

This establishes startup/lifecycle evidence. It does not certify every tool.
"""
import os
import json
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dist/original-studio-test'
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'com.rbxnative.androidport'
log_stream = None
log_file = None
start_activity = ['shell', 'am', 'start', '-W', '-n', PACKAGE + '/dev.gpui.mobile.GpuiActivity']
if os.environ.get('STUDIO_PLAYER_PROBE') == '1': start_activity += ['--ez', 'nativePlayerProbe', 'true']
player_backend = os.environ.get('STUDIO_PLAYER_BACKEND')
if player_backend == 'default': player_backend = None
if player_backend:
    if player_backend not in ('vulkan', 'gl'): raise RuntimeError('Unsupported player diagnostic backend')
    start_activity += ['--es', 'nativePlayerBackend', player_backend]
backend = os.environ.get('STUDIO_VIEWER_BACKEND')
if backend:
    if backend not in ('vulkan', 'gl'): raise RuntimeError('Unsupported viewer diagnostic backend')
    start_activity += ['--es', 'nativeViewerBackend', backend]
elif os.environ.get('STUDIO_VULKAN_ONLY') == '1':
    start_activity += ['--ez', 'nativeVulkanProbe', 'true']
camera_failure = False
resume_first = os.environ.get('STUDIO_SKIP_RESUME') != '1'

def adb(*args, binary=False):
    return subprocess.check_output(['adb', *args], text=not binary, timeout=45)

def logs():
    if log_stream is not None:
        return (OUT / 'logcat-stream.txt').read_text(errors='replace')
    return adb('logcat', '-d')

def healthy(text):
    name = re.escape(PACKAGE)
    pids = set(re.findall(r"Start proc (\d+):" + name + r"(?=[:/ ])", text))
    pids.update(re.findall(r"Process: " + name + r"(?::rook)?, PID: (\d+)", text))
    for line in text.splitlines():
        fields = line.split(maxsplit=5)
        app_line = PACKAGE in line or (len(fields) >= 5 and fields[2] in pids)
        if not app_line:
            continue
        for marker in ['FATAL EXCEPTION', "panicked at", 'Fatal signal',
                       'failed to create gpui_wgpu renderer', 'wgpu device lost',
                       'GPU error during frame', 'no icon named', 'ANR in ' + PACKAGE]:
            if marker in line:
                raise RuntimeError('Rbx Studio failed Android startup/lifecycle: ' + marker)

def default_avatar_pixels(frame):
    # The fresh Baseplate uses the default yellow and blue avatar. Locate its
    # torso first, then count yellow pixels directly above it so its arms
    # cannot satisfy the head check.
    center = frame.width // 2
    pixels = frame.load()
    torso = []
    for y in range(frame.height * 39 // 100, frame.height * 74 // 100):
        for x in range(center - 70, center + 71):
            r, g, b = pixels[x, y]
            if b >= 65 and 5 * b > 8 * g and 5 * b > 9 * r:
                torso.append((x, y))
    if len(torso) < 120:
        return len(torso), 0
    left = min(x for x, _ in torso)
    right = max(x for x, _ in torso)
    top = min(y for _, y in torso)
    head = 0
    for y in range(max(0, top - 70), top - 3):
        for x in range(left + 3, right - 2):
            r, g, b = pixels[x, y]
            if r >= 65 and g >= 55 and 5 * r > 9 * b and 5 * g > 8 * b:
                head += 1
    return len(torso), head

def clear_emulator_system_dialog():
    # Observed Android "Process system isn't responding" dialog at this
    # exact 1280x576 geometry. Select Wait, never close an application.
    # An ANR belonging to our package remains a hard failure in healthy().
    for attempt in range(2):
        windows = adb('shell', 'dumpsys', 'window', 'windows')
        if 'Application Not Responding: system' not in windows:
            return
        (OUT / f'emulator-system-anr-{attempt}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        adb('shell', 'input', 'tap', '380', '350')
        time.sleep(2)
    if 'Application Not Responding: system' in adb('shell', 'dumpsys', 'window', 'windows'):
        raise RuntimeError('The Android emulator system remains unresponsive; UI taps are blocked')

def dismiss_fullscreen_hint():
    # This system overlay was visually inspected at this exact emulator
    # geometry. It is not necessarily exposed by accessibility while another
    # system dialog exists, so identify its window before using its button.
    clear_emulator_system_dialog()
    windows = adb('shell', 'dumpsys', 'window', 'windows')
    if 'ImmersiveModeConfirmation' in windows:
        (OUT / 'fullscreen-system-hint.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        adb('shell', 'input', 'tap', '890', '247')
        time.sleep(2)

try:
    version = os.environ.get('STUDIO_APK_VERSION', '0.6.7')
    apk = ROOT / f'dist/rbx-native-android-{version}-x86_64-debug.apk'
    subprocess.run(['adb', 'install', '-r', str(apk)], check=True, timeout=240)
    test_quality = os.environ.get('STUDIO_GRAPHICS_QUALITY')
    settings = {}
    if test_quality:
        if test_quality != 'Level01': raise RuntimeError('Unsupported diagnostic quality')
        settings['quality'] = test_quality
    if os.environ.get('STUDIO_OUTPUT_COLLAPSED') == '1':
        settings['output_collapsed'] = True
    if settings:
        adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files/config/rbx-native')
        subprocess.run(['adb', 'shell', '-T', 'run-as', PACKAGE, 'tee',
                        'files/config/rbx-native/settings.json'], input=json.dumps(settings), text=True,
                       stdout=subprocess.DEVNULL, check=True, timeout=15)
        saved = json.loads(adb('shell', 'run-as', PACKAGE, 'cat', 'files/config/rbx-native/settings.json'))
        if saved != settings: raise RuntimeError('Diagnostic settings were not persisted')
        (OUT / 'diagnostic-settings.json').write_text(json.dumps(settings))
    adb('logcat', '-c')
    log_file = (OUT / 'logcat-stream.txt').open('wb')
    log_stream = subprocess.Popen(['adb', 'logcat', '-v', 'threadtime'], stdout=log_file, stderr=subprocess.STDOUT)
    # Match the POCO's logical landscape geometry at a software-renderer
    # resolution. Native phone builds retain the device's real resolution.
    adb('shell', 'wm', 'size', '576x1280')
    adb('shell', 'wm', 'density', '208')
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
    # Rotation during the software emulator's initial launcher load can leave
    # the launcher ANR dialog underneath the native app. Start it cleanly.
    adb('shell', 'am', 'force-stop', 'com.android.launcher3')
    adb(*start_activity)
    deadline = time.monotonic() + 120
    while True:
        text = logs()
        healthy(text)
        if 'NATIVE_INITIALIZED = true' in text:
            break
        if time.monotonic() >= deadline:
            raise RuntimeError('The original GPUI launch callback did not complete')
        time.sleep(0.5)
    time.sleep(8)
    dismiss_fullscreen_hint()
    healthy(logs())
    original_pid = adb('shell', 'pidof', PACKAGE).strip()
    if not original_pid:
        raise RuntimeError('Original GPUI process exited after launch')
    (OUT / 'startup.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    (OUT / 'startup-window.txt').write_text(adb('shell', 'dumpsys', 'window', 'windows'))
    cycles = int(os.environ.get('STUDIO_LIFECYCLE_CYCLES', '1'))
    if not 1 <= cycles <= 3: raise RuntimeError('Lifecycle cycle count must be between one and three')
    if resume_first:
        for cycle in range(1, cycles + 1):
            adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(2)
            adb(*start_activity)
            time.sleep(5)
            dismiss_fullscreen_hint()
            healthy(logs())
            if adb('shell', 'pidof', PACKAGE).strip() != original_pid:
                raise RuntimeError('Original GPUI process restarted instead of resuming')
            (OUT / f'resumed-{cycle}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        (OUT / 'resumed.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    if os.environ.get('STUDIO_PROBE_SIGNIN') == '1':
        # Get started was observed on the actual onboarding capture.
        adb('shell', 'input', 'tap', '1160', '535')
        for _ in range(24):
            time.sleep(1)
            healthy(logs())
            if adb('shell', 'pidof', PACKAGE).strip() != original_pid:
                raise RuntimeError('The editor exited when entering Roblox sign-in')
        if os.environ.get('STUDIO_REQUIRE_LOGIN_READY') == '1':
            if 'RBX_ANDROID_LOGIN_CODE_READY' not in logs():
                raise RuntimeError('Roblox did not return a sign-in code')
        print('Roblox sign-in stayed open in the original editor process')
        if os.environ.get('STUDIO_SKIP_SETUP') == '1':
            adb('shell', 'input', 'tap', '430', '535')
            time.sleep(2)
    if os.environ.get('STUDIO_SKIP_SETUP') == '1':
        # Position observed in the real POCO-geometry onboarding capture.
        adb('shell', 'input', 'tap', '430', '535')
        time.sleep(4)
        healthy(logs())
        if adb('shell', 'pidof', PACKAGE).strip() != original_pid:
            raise RuntimeError('The original editor exited while leaving onboarding')
        (OUT / 'after-skip.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        dismiss_fullscreen_hint()
        # New place was observed at (120,145) on the actual Home capture.
        window = adb('shell', 'dumpsys', 'window', 'windows')
        section = window.split(PACKAGE + '/dev.gpui.mobile.GpuiActivity}:', 1)[1]
        frame = re.search(r'Frames:.*?frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]', section)
        if frame is None: raise RuntimeError('The original native window bounds are missing')
        left, top, right, bottom = map(int, frame.groups())
        (OUT / 'native-content-frame.txt').write_text(str((left, top, right, bottom)))
        adb('shell', 'input', 'tap', str(left + 120), str(top + 145))
        # The original editor creates a second GPU device and its real scene
        # pipelines here; SwiftShader can take longer than a UI transition.
        require_editor = os.environ.get('STUDIO_REQUIRE_EDITOR') == '1'
        budget = 180 if require_editor else 60
        for elapsed in range(10, budget + 1, 10):
            time.sleep(10)
            healthy(logs())
            if adb('shell', 'pidof', PACKAGE).strip() != original_pid:
                raise RuntimeError('The original editor exited while opening a new place')
            (OUT / f'new-place-{elapsed}s.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            if require_editor and 'RBX_ANDROID_EDITOR_OPENED' in logs():
                break
        if require_editor and 'RBX_ANDROID_EDITOR_OPENED' not in logs():
            raise RuntimeError('Home did not finish opening the original editor within its startup budget')
        (OUT / 'new-place.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        require_quality = test_quality or os.environ.get('STUDIO_REQUIRE_LOW_DEFAULT') == '1'
        if require_quality and 'allocating viewport at initial quality 1' not in logs():
            raise RuntimeError('The original loader did not apply the requested initial quality')
        if 'rbxview:' not in logs():
            raise RuntimeError('New place never initialized its original viewer; the UI may be blocked')
        if os.environ.get('STUDIO_TEST_CAMERA') == '1':
            adb('shell', 'input', 'touchscreen', 'swipe', '590', '269', '705', '296', '1800')
            time.sleep(5)
            healthy(logs())
            (OUT / 'after-touch-look.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            camera_failure = 'RBX_ANDROID_TOUCH_LOOK' not in logs()
            if camera_failure:
                print('The touch drag did not reach the viewport camera; checking Play separately', flush=True)
        if os.environ.get('STUDIO_INSPECT_VIEWPORT') == '1':
            # The actual 3D document is above the bottom docks. "Viewport"
            # beside Output is its settings dock, not the 3D document.
            time.sleep(15)
            healthy(logs())
            (OUT / 'viewport.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            # Compact Shell Test category observed in its actual new build.
            test_tab_y = 111 if os.environ.get('STUDIO_COMPACT_LAYOUT') == '1' else 155
            adb('shell', 'input', 'tap', str(left + 375), str(top + test_tab_y))
            time.sleep(3)
            healthy(logs())
            (OUT / 'test-ribbon.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            if os.environ.get('STUDIO_TEST_PLAY') == '1':
                before = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/config/rbx-native/places/Baseplate.rbxl', binary=True)
                # Play tile observed in both the original desktop-height and
                # newly compiled compact Test ribbon captures.
                play_tile_y = 180 if os.environ.get('STUDIO_COMPACT_LAYOUT') == '1' else 224
                adb('shell', 'input', 'tap', str(left + 56), str(top + play_tile_y))
                deadline = time.monotonic() + 180
                while 'ROOK_FIRST_FRAME_PRESENTED' not in logs():
                    healthy(logs())
                    if time.monotonic() >= deadline:
                        raise RuntimeError('The original Rook player did not present its first frame')
                    time.sleep(1)
                # Software Vulkan can draw far fewer client frames per second
                # while CoreGui's original loading UI finishes. Give visible
                # scene presentation its own finite budget after first present.
                from PIL import Image
                require_avatar = os.environ.get('STUDIO_REQUIRE_DEFAULT_AVATAR') == '1'
                visible_deadline = time.monotonic() + (90 if require_avatar else 60)
                while True:
                    healthy(logs())
                    (OUT / 'play.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
                    with Image.open(OUT / 'play.png') as capture:
                        frame = capture.convert('RGB')
                        scene = frame.crop((0, 90, frame.width - 120, frame.height))
                        colors = scene.getcolors(scene.width * scene.height)
                        visible = sum(count for count, color in colors if max(color) > 24)
                        scene_visible = visible >= scene.width * scene.height // 20 and len(colors) >= 32
                        torso_pixels, head_pixels = default_avatar_pixels(frame) if require_avatar else (0, 0)
                    avatar_visible = not require_avatar or head_pixels >= 100
                    if (scene_visible and avatar_visible) or time.monotonic() >= visible_deadline: break
                    time.sleep(5)
                # Android Back is the system fallback. The Play menu is
                # drawn by Rook and has no Android accessibility nodes.
                adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
                time.sleep(8)
                healthy(logs())
                if adb('shell', 'pidof', PACKAGE).strip() != original_pid:
                    raise RuntimeError('The original editor restarted after stopping Play')
                after = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/config/rbx-native/places/Baseplate.rbxl', binary=True)
                if before != after: raise RuntimeError('The saved project changed during local Play')
                (OUT / 'after-stop.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
                print('Original Play presented its first frame; Android Back returned to the same editor process; saved project preserved')
                # A swapchain present marker alone also occurred with a fully
                # black compositor capture. Require visible scene pixels clear
                # of system overlays before approving presentation.
                evidence = {'scene_pixels': scene.width * scene.height, 'visible_pixels': visible,
                            'distinct_colors': len(colors), 'first_frame_marker': True}
                if require_avatar:
                    evidence.update(avatar_torso_pixels=torso_pixels, avatar_head_pixels=head_pixels)
                (OUT / 'play-visible.json').write_text(json.dumps(evidence, indent=2))
                if not scene_visible:
                    raise RuntimeError('The player reported presentation but the captured scene is blank')
                if not avatar_visible:
                    raise RuntimeError('The default avatar head did not become visible in Play')
        if os.environ.get('STUDIO_REQUIRE_EDITOR') == '1':
            if 'RBX_ANDROID_EDITOR_OPENED' not in logs():
                raise RuntimeError('Home never finished opening the original editor')
            adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(2)
            adb(*start_activity)
            time.sleep(5)
            dismiss_fullscreen_hint()
            healthy(logs())
            if adb('shell', 'pidof', PACKAGE).strip() != original_pid:
                raise RuntimeError('Original editor process restarted after map resume')
            (OUT / 'new-place-resumed.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    if camera_failure:
        raise RuntimeError('A touch drag never reached the Android viewport camera')
    print('Original GPUI startup completed; resume tested:', resume_first, '; screenshots require visual review')
finally:
    if os.environ.get('STUDIO_PLAYER_PROBE') == '1':
        try:
            (OUT / 'rook-render-probe.png').write_bytes(adb('exec-out', 'run-as', PACKAGE,
                'cat', 'files/cache/rook-render-probe.png', binary=True))
        except Exception as error:
            (OUT / 'render-probe-error.txt').write_text(str(error))
    # The original player's console includes adapter selection, CoreScript
    # output and loading failures. Preserve only its small text diagnostics,
    # never the project snapshot or downloaded official resource files.
    try:
        paths = adb('shell', 'run-as', PACKAGE, 'find', 'files/local-play', '-maxdepth', '2', '-type', 'f')
        for value in paths.splitlines():
            path = Path(value.strip())
            if path.name not in ('state.json', 'output.log') or len(path.parts) != 4: continue
            folder = OUT / 'sessions' / path.parent.name
            folder.mkdir(parents=True, exist_ok=True)
            (folder / path.name).write_bytes(adb('exec-out', 'run-as', PACKAGE, 'head', '-c', '1048576', str(path), binary=True))
    except Exception as error:
        (OUT / 'sessions-error.txt').write_text(str(error))
    # Preserve evidence even when am start is held by a native lifecycle
    # deadlock; no individual adb command may consume the whole CI timeout.
    for name, args, binary in [
        ('logcat.txt', ['logcat', '-d'], False),
        ('final-window.txt', ['shell', 'dumpsys', 'window', 'windows'], False),
        ('final-activity.txt', ['shell', 'dumpsys', 'activity', 'activities'], False),
        ('final-memory.txt', ['shell', 'dumpsys', 'meminfo', PACKAGE], False),
        ('final.png', ['exec-out', 'screencap', '-p'], True),
    ]:
        try:
            value = adb(*args, binary=binary)
            if binary: (OUT / name).write_bytes(value)
            else: (OUT / name).write_text(value)
        except Exception as error:
            (OUT / (name + '.error')).write_text(str(error))
    if log_stream is not None:
        log_stream.terminate()
        try: log_stream.wait(timeout=5)
        except subprocess.TimeoutExpired: log_stream.kill()
    if log_file is not None: log_file.close()
