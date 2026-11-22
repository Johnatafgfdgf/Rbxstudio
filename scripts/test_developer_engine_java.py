#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,tempfile,sys,urllib.parse
root=Path(__file__).resolve().parents[1];jar=Path(os.environ.get('JSON_JAR',root.parent/'bootstrap/json.jar')).resolve()
names=['StudioAiAgent','StudioAiProjectEdit','StudioAiToolProtocol','StudioAiConversation','StudioDeveloperEngine','StudioAiVisualBuilder','StudioProceduralCompiler','StudioDeveloperKnowledge','StudioAiPopupBounds','StudioResourceHub','StudioAiScriptPolicy','StudioAiRetry','StudioAiStoredDocument']
with tempfile.TemporaryDirectory(prefix='rbx-developer-') as tmp:
 subprocess.run(['javac','--release','11','-classpath',str(jar),'-d',tmp,*[str(root/f'android/src/com/rbxnative/androidport/{n}.java') for n in names],str(root/'tests/StudioDeveloperEngineTest.java')],check=True)
 opts=[]
 if sys.argv[1:]==['resources'] and os.environ.get('HTTPS_PROXY'):
  p=urllib.parse.urlparse(os.environ['HTTPS_PROXY']);opts=[f'-Dhttps.proxyHost={p.hostname}',f'-Dhttps.proxyPort={p.port}','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
 subprocess.run(['java',*opts,'-ea','-classpath',os.pathsep.join([str(jar),tmp]),'com.rbxnative.androidport.StudioDeveloperEngineTest',*sys.argv[1:]],check=True)
