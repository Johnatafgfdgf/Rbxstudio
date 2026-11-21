#!/usr/bin/env python3
"""Opt-in production chat Activity test in a UI-only APK.

Requires ANDROID_HOME (SDK35/build-tools35, emulator, platform-tools), javac,
and an Android29 x86_64 AVD named by RBX_UI_AVD (default: test).
Set ANDROID_AVD_HOME and RBX_UI_OUTPUT to override locations.
This does not exercise the native renderer, MCP socket, Play, or API keys.
"""
from pathlib import Path
import os,subprocess,time,zipfile,shutil,json,xml.etree.ElementTree as E,re
repo=Path(__file__).resolve().parents[1];base=repo.parent;sdk=Path(os.environ.get('ANDROID_HOME',base/'sdk'));out=Path(os.environ.get('RBX_UI_OUTPUT',repo/'target/chat-ui-regression'));out.mkdir(parents=True,exist_ok=True);bt=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
env=os.environ.copy();env.update(ANDROID_HOME=str(sdk),ANDROID_I_WANT_MY_TCG='yes');env.setdefault('ANDROID_AVD_HOME',str(base/'r9-avd'))
def run(args,timeout=90):
 r=subprocess.run(list(map(str,args)),env=env,capture_output=True,timeout=timeout)
 if r.returncode:raise RuntimeError((r.stderr+r.stdout).decode(errors='replace')[-2000:])
 return r.stdout
classes=out/'classes';classes.mkdir(exist_ok=True)
sources=[p for p in (repo/'android/src').rglob('*.java') if p.name not in {'MainActivity.java','NativeSmokeTest.java','PlayerPad.java','StudioIcon.java'}];h=repo/'upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile';sources.extend(h/(n+'.java') for n in ['GpuiClipboard','GpuiFilePicker','GpuiPickerActivity','GpuiDeepLink','GpuiPlatformView','GpuiVideoPlayer','GpuiCamera'])
run(['javac','--release','11','-classpath',jar,'-d',classes,*sources]);run([bt/'d8','--lib',jar,'--min-api','29','--output',out,*classes.rglob('*.class')],120)
manifest=(repo/'android/StudioManifest.xml').read_text().replace('package="com.rbxnative.androidport"','package="com.rbxnative.androidport.uiregression"',1).replace('android:name="com.rbxnative.androidport.StudioAiHub" android:exported="false"','android:name="com.rbxnative.androidport.StudioAiHub" android:exported="true"')
(out/'AndroidManifest.xml').write_text(manifest);run([bt/'aapt','package','-f','-M',out/'AndroidManifest.xml','-I',jar,'-S',repo/'android/res','-F',out/'unsigned.apk'])
with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(out/'classes.dex','classes.dex')
key=out/'ui-disposable.keystore'
if not key.exists():run(['keytool','-genkeypair','-noprompt','-keystore',key,'-alias','androiddebugkey','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Disposable RBX UI Test'])
run([bt/'apksigner','sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'ui-only.apk',out/'unsigned.apk'])
adb=sdk/'platform-tools/adb';run([adb,'start-server']);log=(out/'emulator.log').open('wb');emulator=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',env.get('RBX_UI_AVD','test'),'-no-window','-no-audio','-no-boot-anim','-no-snapshot','-no-metrics','-accel','off','-gpu','swiftshader_indirect','-cores','4','-memory','1536','-partition-size','768','-ports','5554,5555','-show-kernel'],env=env,stdout=log,stderr=subprocess.STDOUT)
try:
 deadline=time.monotonic()+240
 while time.monotonic()<deadline:
  if emulator.poll() is not None:raise RuntimeError('Emulator exited; see emulator.log')
  try:
   if 'emulator-5554\tdevice' in run([adb,'devices'],15).decode() and run([adb,'-s','emulator-5554','shell','getprop','sys.boot_completed'],15).strip()==b'1':break
  except Exception:pass
  time.sleep(3)
 else:raise RuntimeError('Software emulator boot timed out after 240 seconds')
 print('Android emulator booted',flush=True)
 def shell(*args):return run([adb,'-s','emulator-5554','shell',*args],60)
 shell('settings','put','global','window_animation_scale','0');shell('settings','put','global','transition_animation_scale','0');shell('settings','put','global','animator_duration_scale','0')
 # Only the disposable UI test package is reset, never a user installation.
 if b'package:com.rbxnative.androidport.uiregression' in shell('pm','list','packages','com.rbxnative.androidport.uiregression'):shell('pm','uninstall','com.rbxnative.androidport.uiregression')
 run([adb,'-s','emulator-5554','install','--no-incremental',out/'ui-only.apk'],120);shell('wm','size','1280x600');shell('wm','density','240');shell('settings','put','secure','show_ime_with_hard_keyboard','1')
 shell('am','start','-W','-n','com.rbxnative.androidport.uiregression/com.rbxnative.androidport.StudioAiHub','--es','project_name','UIRegression.rbxl','--es','map_identity','ui-only-map');time.sleep(3)
 def state(name):
  shell('uiautomator','dump','/sdcard/window.xml');data=shell('cat','/sdcard/window.xml').decode();(out/(name+'.xml')).write_text(data);(out/(name+'.png')).write_bytes(run([adb,'-s','emulator-5554','exec-out','screencap','-p']));return[n.attrib for n in E.fromstring(data).iter('node')]
 def bounds(n):return list(map(int,re.findall(r'-?\d+',n.get('bounds',''))))
 def click(nodes,description):
  matches=[n for n in nodes if n.get('content-desc')==description];assert len(matches)==1,(description,len(matches));x0,y0,x1,y1=bounds(matches[0]);assert x1>x0 and y1>y0;shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));time.sleep(1)
 nodes=state('chat-before')
 if any(n.get('text')=="System UI isn't responding" for n in nodes):
  wait=[n for n in nodes if n.get('text')=='Wait'];assert len(wait)==1;x0,y0,x1,y1=bounds(wait[0]);shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));time.sleep(3);nodes=state('chat-after-system-ui-wait')
 click(nodes,'Modelos e anexos');nodes=state('plus-landscape')
 for title in ['Escolher modelo','Anexar imagem','Anexar texto ou Luau']:
  matches=[n for n in nodes if n.get('text','').startswith(title)];assert len(matches)==1,(title,matches);x0,y0,x1,y1=bounds(matches[0]);assert 0<=x0<x1<=1280 and 0<=y0<y1<=600
 shell('input','keyevent','4');nodes=state('chat-before-menu');click(nodes,'Menu do chat');nodes=state('menu-before-scroll');scrolls=[n for n in nodes if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true'];assert scrolls,'No native scrollable popup';x0,y0,x1,y1=bounds(scrolls[-1]);shell('input','swipe',str((x0+x1)//2),str(y1-12),str((x0+x1)//2),str(y0+12),'400');time.sleep(1);nodes=state('menu-after-scroll');assert any(n.get('text','').startswith('Minimizar') and bounds(n)[3]>bounds(n)[1] for n in nodes),'Last option inaccessible'
 shell('input','keyevent','4');nodes=state('chat-before-keyboard');edits=[n for n in nodes if n.get('class')=='android.widget.EditText'];assert len(edits)==1;x0,y0,x1,y1=bounds(edits[0]);shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));shell('input','text','teste');time.sleep(2);nodes=state('chat-keyboard');composer=[n for n in nodes if n.get('content-desc')=='Modelos e anexos'];assert composer and bounds(composer[0])[3] < 275,'Composer obscured by keyboard';click(nodes,'Modelos e anexos');state('plus-with-keyboard');shell('input','keyevent','4');shell('wm','size','720x1280');shell('am','start','-W','-n','com.rbxnative.androidport.uiregression/com.rbxnative.androidport.StudioAiHub','--es','project_name','UIRegression.rbxl','--es','map_identity','ui-only-map');time.sleep(3);nodes=state('chat-portrait');click(nodes,'Modelos e anexos');nodes=state('plus-portrait')
 options=[n for n in nodes if n.get('class')=='android.widget.Button' and n.get('text','').startswith(('Escolher modelo','Anexar'))];assert len(options)==3,'Missing portrait options'
 for n in options:
  x0,y0,x1,y1=bounds(n);assert 0<=x0<x1<=720 and 0<=y0<y1<=1280
 report={'scope':'Production Java Activity in Android 29 software emulator using a UI-only APK; no native viewport, MCP, Play or user keys','plus_landscape':'passed','scroll_to_last_item':'passed','keyboard':'captured','plus_portrait':'passed'};(out/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
except Exception:
 try:(out/'logcat.txt').write_bytes(run([adb,'-s','emulator-5554','logcat','-d','-t','250'],30))
 except Exception:pass
 raise
finally:
 emulator.terminate()
 try:emulator.wait(10)
 except subprocess.TimeoutExpired:emulator.kill()
 run([adb,'kill-server'])
