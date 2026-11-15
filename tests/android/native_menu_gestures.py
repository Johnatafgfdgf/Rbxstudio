"""Exercise menu actions on the same APK used for the native screenshots."""

import io
import json
from pathlib import Path
import runpy
import time

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[2]
smoke = runpy.run_path(str(ROOT / 'scripts/studio_smoke.py'))
adb, healthy, output = (smoke[key] for key in ('adb', 'healthy', 'OUT'))
pid = adb('shell', 'pidof', smoke['PACKAGE']).strip()
results = []
failures = []


def check_app():
    healthy(adb('logcat', '-d'))
    if adb('shell', 'pidof', smoke['PACKAGE']).strip() != pid:
        raise RuntimeError('Editor process changed during the gestures')


def shot(name):
    check_app()
    png = adb('exec-out', 'screencap', '-p', binary=True)
    (output / f'{name}.png').write_bytes(png)
    img = Image.open(io.BytesIO(png)).convert('RGB')
    if img.size != (1280, 576):
        raise RuntimeError(f'Unexpected capture size {img.size}')
    return img


def touch(x, y):
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1.4)


def swipe(x, start, end):
    adb('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '550')
    time.sleep(1.8)


def compare(name, before, after, bounds, minimum):
    difference = ImageChops.difference(before.crop(bounds), after.crop(bounds))
    pixels = sum(1 for rgb in difference.getdata() if max(rgb) > 24)
    results.append({'gesture': name, 'changed_pixels': pixels, 'minimum': minimum})
    if pixels < minimum:
        failures.append(name)


# Coordinates observed on the earlier, verified 1280 × 576 native captures.
# The startup smoke has just opened Explorer and returned from Android Home.
try:
    before = shot('touch-explorer-before')
    touch(858, 322)
    expanded = shot('touch-explorer-expanded')
    compare('expand Workspace', before, expanded, (827, 291, 1264, 495), 400)
    touch(995, 324)
    touch(852, 533)
    actions = shot('touch-explorer-actions')
    compare('Explorer selection menu', expanded, actions, (815, 158, 1268, 497), 1000)
    touch(735, 450)
    touch(1016, 126)
    shot('touch-toolbox')
    touch(1243, 126)

    touch(178, 34)
    tools_before = shot('touch-tools-before')
    swipe(286, 362, 170)
    tools_after = shot('touch-tools-scrolled')
    compare('scroll tools', tools_before, tools_after, (20, 152, 510, 388), 1400)
    touch(178, 34)

    touch(308, 34)
    panels_before = shot('touch-panels-before')
    swipe(350, 504, 150)
    panels_after = shot('touch-panels-scrolled')
    compare('scroll panels', panels_before, panels_after, (265, 86, 552, 545), 600)
    touch(308, 34)

    touch(55, 34)
    menu = shot('touch-main-menu')
    for name, x in [('file', 48), ('edit', 117), ('model', 199), ('view', 282)]:
        touch(x, 118)
        popup = shot(f'touch-menu-{name}')
        compare(f'open {name}', menu, popup, (7, 152, 545, 510), 1500)
        touch(x, 118)
finally:
    (output / 'native-menu-gestures.json').write_text(json.dumps(results, indent=2) + '\n')

if failures:
    raise RuntimeError('Gestures did not visibly change the target: ' + ', '.join(failures))
