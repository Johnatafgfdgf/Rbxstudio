#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1];sdk=Path(os.environ.get('ANDROID_HOME',root.parent/'sdk'))
sources=[p for p in (root/'android/src').rglob('*.java') if p.name not in {'MainActivity.java','NativeSmokeTest.java','PlayerPad.java','StudioIcon.java'}]
helpers=root/'upstream/gpui-mobile/example/android/gradle/app/src/main/java/dev/gpui/mobile'
sources.extend(helpers/(n+'.java') for n in ['GpuiClipboard','GpuiFilePicker','GpuiPickerActivity','GpuiDeepLink','GpuiPlatformView','GpuiVideoPlayer','GpuiCamera'])
with tempfile.TemporaryDirectory(prefix='rbx-java-') as output:
 subprocess.run(['javac','--release','11','-classpath',str(sdk/'platforms/android-35/android.jar'),'-d',output,*map(str,sources)],check=True)
print('Compiled all',len(sources),'shipped Android Java files')
