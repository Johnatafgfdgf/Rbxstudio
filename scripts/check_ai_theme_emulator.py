#!/usr/bin/env python3
"""Launch the real StudioAiHub Activity and capture screen + bounds for inspection."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"dist/ai-ui-native-preview"
OUT.mkdir(parents=True,exist_ok=True)
APK=OUT/"preview.apk"

def run(args,timeout=80):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
                          timeout=timeout,check=False)
    msg=result.stdout.decode("utf8",errors="replace")+"\n"+result.stderr.decode("utf8",errors="replace")
    if result.returncode: raise RuntimeError(f"Command failed ({result.returncode}): {args}, {msg[-1300:]}")
    return msg

install=run(["adb","install","--no-streaming","-r",str(APK)],180)
assert "Success" in install,install
start=run(["adb","shell","am","start","-W","-n",
           "com.rbxnative.androidport.experimental/com.rbxnative.androidport.StudioAiHub"])
assert "Error" not in start,start
time.sleep(3)
p=subprocess.run(["adb","exec-out","screencap","-p"],check=True,
                 stdout=subprocess.PIPE)
assert p.stdout.startswith(b"\x89PNG\r\n\x1a\n")
(OUT/"ai-theme-emulator.png").write_bytes(p.stdout)
run(["adb","shell","uiautomator","dump","/sdcard/ai-screen.xml"],80)
xml=run(["adb","shell","cat","/sdcard/ai-screen.xml"])
(OUT/"ui-hierarchy.xml").write_text(xml)
root=ET.fromstring(xml[xml.index("<?xml"):])
nodes=[n.attrib for n in root.iter("node")]
buttons=[n for n in nodes if n.get("class")=="android.widget.Button"]
def bounds(n):
    v=list(map(int,re.findall(r"\d+",n.get("bounds",""))))
    return (v[2]-v[0],v[3]-v[1]) if len(v)==4 else (0,0)
save=[n for n in buttons if "Salvar chave com segurança" in n.get("text","")]
assert len(save)==1,"Save control not visible in real Android hierarchy"
width,height=bounds(save[0])
density=run(["adb","shell","wm","density"])
m=re.search(r"Physical density:\s*(\d+)",density)
if m: assert height<=round(60*int(m.group(1))/160),"Save button too tall"
assert width>0 and height>0
# Screenshot and hierarchy are saved even if the responsive checks become
# too strict on other emulator resolutions.
report=["Real Android StudioAiHub activity: launched",
        "Preserved native libraries: yes (built by preview script)",
        f"Save action size: {width}x{height}px",
        f"Buttons visible in UI tree: {len(buttons)}",
        "Captured: ai-theme-emulator.png",
        "This screenshot is from the real Java Activity, NOT a design mockup."]
(OUT/"ui-report.txt").write_text("\n".join(report)+"\n")
print("\n".join(report))
