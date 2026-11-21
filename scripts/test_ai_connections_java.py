#!/usr/bin/env python3
"""Production connection routing/usage logic, not a live provider or Android Keystore test."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1];jar=Path(os.environ['JSON_JAR'])
with tempfile.TemporaryDirectory(prefix='rbx-connections-') as out:
 subprocess.run(['javac','--release','11','-classpath',str(jar),'-d',out,*[str(root/f'android/src/com/rbxnative/androidport/{name}.java') for name in ['StudioAiConnections','StudioAiUsage']],str(root/'tests/StudioAiConnectionsTest.java')],check=True)
 subprocess.run(['java','-ea','-classpath',str(jar)+os.pathsep+out,'com.rbxnative.androidport.StudioAiConnectionsTest'],check=True)
