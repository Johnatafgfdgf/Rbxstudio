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
acceleration=env.get('RBX_UI_ACCEL','off')
if acceleration not in {'off','auto'}:raise ValueError('RBX_UI_ACCEL must be off or auto')
def run(args,timeout=90):
 r=subprocess.run(list(map(str,args)),env=env,capture_output=True,timeout=timeout)
 if r.returncode:raise RuntimeError((r.stderr+r.stdout).decode(errors='replace')[-2000:])
 return r.stdout
classes=out/'classes';classes.mkdir(exist_ok=True)
sources=[p for p in (repo/'android/src').rglob('*.java') if p.name not in {'MainActivity.java','NativeSmokeTest.java','PlayerPad.java','StudioIcon.java'}];h=repo/'upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile';sources.extend(h/(n+'.java') for n in ['GpuiClipboard','GpuiFilePicker','GpuiPickerActivity','GpuiDeepLink','GpuiPlatformView','GpuiVideoPlayer','GpuiCamera']);sources.append(repo/'tests/StudioAiStoreProbe.java')
run(['javac','--release','11','-classpath',jar,'-d',classes,*sources]);run([bt/'d8','--lib',jar,'--min-api','29','--output',out,*classes.rglob('*.class')],120)
manifest=(repo/'android/StudioManifest.xml').read_text().replace('package="com.rbxnative.androidport"','package="com.rbxnative.androidport.uiregression"',1).replace('android:name="com.rbxnative.androidport.StudioAiHub" android:exported="false"','android:name="com.rbxnative.androidport.StudioAiHub" android:exported="true"')
manifest=manifest.replace('</application>','<activity android:name="com.rbxnative.androidport.StudioAiStoreProbe" android:exported="true" /></application>')
(out/'AndroidManifest.xml').write_text(manifest);run([bt/'aapt','package','-f','-M',out/'AndroidManifest.xml','-I',jar,'-S',repo/'android/res','-F',out/'unsigned.apk'])
with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(out/'classes.dex','classes.dex')
key=out/'ui-disposable.keystore'
if not key.exists():run(['keytool','-genkeypair','-noprompt','-keystore',key,'-alias','androiddebugkey','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Disposable RBX UI Test'])
run([bt/'apksigner','sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'ui-only.apk',out/'unsigned.apk'])
adb=sdk/'platform-tools/adb';run([adb,'start-server']);log=(out/'emulator.log').open('wb');emulator=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',env.get('RBX_UI_AVD','test'),'-no-window','-read-only','-no-audio','-no-boot-anim','-no-snapshot','-no-metrics','-accel',acceleration,'-gpu','swiftshader_indirect','-cores','4','-memory','1536','-partition-size','768','-ports','5554,5555','-show-kernel'],env=env,stdout=log,stderr=subprocess.STDOUT)
try:
 deadline=time.monotonic()+600
 while time.monotonic()<deadline:
  if emulator.poll() is not None:raise RuntimeError('Emulator exited; see emulator.log')
  try:
   if 'emulator-5554\tdevice' in run([adb,'devices'],15).decode() and run([adb,'-s','emulator-5554','shell','getprop','sys.boot_completed'],15).strip()==b'1':break
  except Exception:pass
  time.sleep(3)
 else:raise RuntimeError('Software emulator boot timed out after 600 seconds')
 print('Android emulator booted',flush=True)
 def shell(*args):return run([adb,'-s','emulator-5554','shell',*args],60)
 shell('settings','put','global','window_animation_scale','0');shell('settings','put','global','transition_animation_scale','0');shell('settings','put','global','animator_duration_scale','0')
 shell('settings','put','secure','immersive_mode_confirmations','confirmed')
 for _ in range(6):
  shell('pm','path','com.android.systemui');time.sleep(3)
 # Only the disposable UI test package is reset, never a user installation.
 if b'package:com.rbxnative.androidport.uiregression' in shell('pm','list','packages','com.rbxnative.androidport.uiregression'):shell('pm','uninstall','com.rbxnative.androidport.uiregression')
 run([adb,'-s','emulator-5554','install','--no-incremental',out/'ui-only.apk'],120);shell('wm','size','1280x600');shell('wm','density','240');shell('settings','put','secure','show_ime_with_hard_keyboard','1')
 for phase in ['write','read']:
  shell('am','start','-W','-n','com.rbxnative.androidport.uiregression/com.rbxnative.androidport.StudioAiStoreProbe','--es','phase',phase)
  found=False
  for attempt in range(6):
   time.sleep(1);shell('uiautomator','dump','/sdcard/store.xml');xml=shell('cat','/sdcard/store.xml').decode();(out/('store-'+phase+'.xml')).write_text(xml)
   if 'RBX_STORE_'+phase.upper()+'_PASS' in xml:found=True;break
   if 'RBX_STORE_FAIL' in xml:break
  assert found,'Real Android credential backend failed: '+phase
  shell('am','force-stop','com.rbxnative.androidport.uiregression')
 (out/'android-store-report.json').write_text(json.dumps({'scope':'Production connection registry and credential backend using real Android Keystore, synthetic secrets, app process restart, CRUD and task-state storage. No live provider or user keys.','write':'passed','restart_read':'passed','edit_delete':'passed','plaintext_absence':'passed'},indent=2))
 print('Android Keystore and multi-key restart test passed',flush=True)
 shell('am','start','-W','-n','com.rbxnative.androidport.uiregression/com.rbxnative.androidport.StudioAiHub','--es','project_name','UIRegression.rbxl','--es','map_identity','ui-only-map');time.sleep(3)
 def state(name):
  # Android can report success without writing a hierarchy while the window is
  # changing. Retry the observation, never an application mutation or assertion.
  last=''
  for attempt in range(8):
   try:
    shell('rm','-f','/sdcard/window.xml');last=shell('uiautomator','dump','/sdcard/window.xml').decode()
    data=shell('cat','/sdcard/window.xml').decode();tree=E.fromstring(data)
    (out/(name+'.xml')).write_text(data);(out/(name+'.png')).write_bytes(run([adb,'-s','emulator-5554','exec-out','screencap','-p']))
    return[n.attrib for n in tree.iter('node')]
   except (RuntimeError,E.ParseError) as error:last=str(error);time.sleep(1)
  raise RuntimeError('No stable Android accessibility hierarchy: '+last)
 def bounds(n):return list(map(int,re.findall(r'-?\d+',n.get('bounds',''))))
 def click(nodes,description):
  matches=[n for n in nodes if n.get('content-desc')==description];assert len(matches)==1,(description,len(matches));x0,y0,x1,y1=bounds(matches[0]);assert x1>x0 and y1>y0;shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));time.sleep(1)
 nodes=state('chat-before')
 if any(n.get('text')=="System UI isn't responding" for n in nodes):
  wait=[n for n in nodes if n.get('text')=='Wait'];assert len(wait)==1;x0,y0,x1,y1=bounds(wait[0]);shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));time.sleep(3);nodes=state('chat-after-system-ui-wait')
 for attempt in range(8):
  got=[n for n in nodes if n.get('resource-id')=='android:id/ok' and n.get('text')=='GOT IT']
  waits=[n for n in nodes if n.get('resource-id')=='android:id/aerr_wait']
  target=got[0] if got else waits[0] if waits else None
  if target is not None:
   x0,y0,x1,y1=bounds(target);shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));time.sleep(2)
  if any(n.get('content-desc')=='Modelos e anexos' for n in nodes):break
  time.sleep(2);nodes=state('chat-ready-'+str(attempt))
 click(nodes,'Modelos e anexos');nodes=state('plus-landscape')
 for title in ['Escolher modelo','Anexar imagem','Anexar texto ou Luau']:
  matches=[n for n in nodes if n.get('text','').startswith(title)];assert len(matches)==1,(title,matches);x0,y0,x1,y1=bounds(matches[0]);assert 0<=x0<x1<=1280 and 0<=y0<y1<=600
 shell('input','keyevent','4');nodes=state('chat-before-menu');click(nodes,'Menu do chat');nodes=state('menu-before-scroll');scrolls=[n for n in nodes if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true'];assert scrolls,'No native scrollable popup';x0,y0,x1,y1=bounds(scrolls[-1]);shell('input','swipe',str((x0+x1)//2),str(y1-12),str((x0+x1)//2),str(y0+12),'400');time.sleep(1);nodes=state('menu-after-scroll');assert any(n.get('text','').startswith('Minimizar') and bounds(n)[3]>bounds(n)[1] for n in nodes),'Last option inaccessible'
 shell('input','keyevent','4');nodes=state('chat-before-keyboard');edits=[n for n in nodes if n.get('class')=='android.widget.EditText'];assert len(edits)==1;x0,y0,x1,y1=bounds(edits[0]);shell('input','tap',str((x0+x1)//2),str((y0+y1)//2));shell('input','text','teste');time.sleep(2);nodes=state('chat-keyboard');composer=[n for n in nodes if n.get('content-desc')=='Modelos e anexos'];send=[n for n in nodes if n.get('content-desc')=='Enviar mensagem'];assert len(composer)==1 and len(send)==1,'Missing composer controls'
 for button in composer+send:
  x0,y0,x1,y1=bounds(button);assert y1<275 and y1-y0>=60 and x1-x0>=60,'Composer touch target clipped by keyboard or panel'
 click(nodes,'Modelos e anexos');state('plus-with-keyboard');shell('input','keyevent','4')
 # BACK can hide IME before dismissing the focused popup. Close the observed
 # popup explicitly before rotating; never assume one BACK closed both.
 for attempt in range(3):
  nodes=state('before-rotation-'+str(attempt))
  if any(n.get('content-desc')=='Modelos e anexos' for n in nodes):break
  if any(n.get('text','').startswith('Escolher modelo') for n in nodes):shell('input','keyevent','4');time.sleep(1)
 shell('wm','size','720x1280');shell('am','start','-W','-n','com.rbxnative.androidport.uiregression/com.rbxnative.androidport.StudioAiHub','--es','project_name','UIRegression.rbxl','--es','map_identity','ui-only-map')
 previous=None
 for attempt in range(12):
  time.sleep(1);nodes=state('chat-portrait-'+str(attempt))
  anchors=[n for n in nodes if n.get('content-desc')=='Modelos e anexos']
  current=bounds(anchors[0]) if len(anchors)==1 else None
  if current and current[2]<=720 and current[3]>640 and current[3]-current[1]>=50:
   if current==previous:break
   previous=current
 else:raise AssertionError('Portrait layout did not stabilize above system bars')
 click(nodes,'Modelos e anexos');nodes=state('plus-portrait')
 options=[n for n in nodes if n.get('class')=='android.widget.Button' and n.get('text','').startswith(('Escolher modelo','Anexar'))];assert len(options)==3,'Missing portrait options'
 for n in options:
  x0,y0,x1,y1=bounds(n);assert 0<=x0<x1<=720 and 0<=y0<y1<=1280
 report={'scope':'Production Java Activity in Android 29 software emulator using a UI-only APK; no native viewport, MCP, Play or user keys','plus_landscape':'passed','scroll_to_last_item':'passed','keyboard':'captured','plus_portrait':'passed','android_keystore_restart':'passed'};(out/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
except Exception:
 try:(out/'logcat.txt').write_bytes(run([adb,'-s','emulator-5554','logcat','-d','-t','250'],30))
 except Exception:pass
 raise
finally:
 try:run([adb,'-s','emulator-5554','emu','kill'],15)
 except Exception:pass
 emulator.terminate()
 try:emulator.wait(10)
 except subprocess.TimeoutExpired:emulator.kill()
 run([adb,'kill-server'])
