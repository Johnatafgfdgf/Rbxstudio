#!/usr/bin/env python3
"""Exercise the actual local Android Toolbox risk detector as Java code.

This tests deterministic heuristics, not XML pulling on a physical Android
device. End-to-end picker, XML and permission tests remain separately needed.
"""
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[1] /
          "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
start = source.index("    private static int sourceRiskMask(String source)")
end = source.index("    private static final class ScriptDependencies", start)
method = source[start:end].strip()
assert "return mask;" in method and method.endswith("}")
assert "callMcp(" not in method and "request(" not in method

java = """
import java.util.Locale;
import java.util.regex.Pattern;
public class ToolboxScanHarness {
%s
  private static void check(int expected, String code) {
    int got = sourceRiskMask(code);
    if (got != expected) throw new AssertionError("mask " + got + " != " + expected);
  }
  public static void main(String[] args) {
    check(0, "local Module = require(script.Parent.Module)\\nprint('hi')");
    check(0, "-- ordinary Luau module without external dependencies\\nreturn true");
    check(1, "local src = loadstring('print(1)')");
    check(1, "local SRC = LOADSTRING ( 'x' )");
    check(2, "require(123456789)");
    check(0, "require(script.Parent.Module)");
    check(4, "game:GetService('HttpService'):GetAsync('https://example.invalid')");
    check(4, "local t = 'https://example.invalid'");
    check(8, "return getfenv(0)");
    check(8, "local msg = string.char(77,69,83,83)");
    check(16, "game:GetService('InsertService'):LoadAsset(12345)");
    check(32, "game:GetService('DataStoreService')");
    check(1|2|4, "loadstring(game:HttpGet('url'))(); require(123456)");
    check(0, "-- NOT load_string, nothing dangerous executed");
    check(0, "");
    System.out.println("Production Java Toolbox script risk masks: 15 cases passed");
  }
}
""" % method
with tempfile.TemporaryDirectory(prefix="rbx-toolbox-") as tmp:
    path = Path(tmp, "ToolboxScanHarness.java")
    path.write_text(java)
    subprocess.run(["javac", "--release", "11", str(path)], cwd=tmp, check=True)
    subprocess.run(["java", "-cp", tmp, "ToolboxScanHarness"], cwd=tmp, check=True)
