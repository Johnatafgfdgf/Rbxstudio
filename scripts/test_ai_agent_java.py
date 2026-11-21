#!/usr/bin/env python3
"""Run the production orchestration loop against transport contracts.

This is a unit test. It does not claim API connectivity or device execution.
Set JSON_JAR to an installed org.json JAR.
"""
from pathlib import Path
import os, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
jar = Path(os.environ.get("JSON_JAR",root.parent/"sdk-download/json.jar")).resolve()
if not jar.is_file():
    raise SystemExit("Set JSON_JAR to the org.json dependency")
with tempfile.TemporaryDirectory(prefix="rbx-agent-tests-") as tmp:
    src=root/"android/src/com/rbxnative/androidport"
    subprocess.run(["javac","--release","11","-classpath",str(jar),"-d",tmp,
        *[str(src/(name+".java")) for name in ["StudioDeveloperEngine","StudioAiVisualBuilder","StudioProceduralCompiler","StudioDeveloperKnowledge","StudioAiScriptPolicy","StudioAiConversation"]],
        str(src/"StudioAiAgent.java"),str(src/"StudioAiProjectEdit.java"),str(src/"StudioAiToolProtocol.java"),
        str(root/"tests/StudioAiAgentTest.java")],check=True)
    subprocess.run(["java","-ea","-classpath",os.pathsep.join([str(jar),tmp]),
        "com.rbxnative.androidport.StudioAiAgentTest"],check=True)
