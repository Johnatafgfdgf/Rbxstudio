#!/usr/bin/env python3
"""Compile production Java dependency scanners, then exercise bounded require rules.

No Roblox asset download, Luau execution, or game simulation is performed.
It tests static inventory and exact in-tree relative path resolution only.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
start = source.index("    private static final class ScriptDependencies {")
end = source.index("    private static final class ToolboxXmlItem", start)
production = source[start:end].strip()
assert "return result;" in production
assert "callMcp(" not in production
assert "requestModelWithFreeFallback(" not in production
assert "execute_luau" not in production

harness = """
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
public class DependencyScanHarness {
%s
    private static void yes(boolean value) {
        if (!value) throw new AssertionError("Unexpected dependency inventory");
    }
    public static void main(String[] args) {
        ScriptDependencies local = scanScriptDependencies(
            "local a = require(script.Parent.Helper)\\n" +
            "local b = require(script.Parent.Parent.Config)\\n" +
            "local c = require(script.Parent.Helper)\\n");
        yes(local.relativeModules.size() == 2);
        yes(local.relativeModules.contains("script.Parent.Helper"));
        yes(local.relativeModules.contains("script.Parent.Parent.Config"));
        yes(local.externalAssets.isEmpty());
        yes(local.dynamicRequires == 0);
        yes(localModulePath("Workspace.Fort.Pack.Client", "script.Parent.Helper",
                "Workspace.Fort").equals("Workspace.Fort.Pack.Helper"));
        yes(localModulePath("Workspace.Fort.Pack.Client", "script.Parent.Parent.Config",
                "Workspace.Fort").equals("Workspace.Fort.Config"));
        yes(localModulePath("Workspace.Fort.Pack.Client", "script.Parent.Parent.Parent.Bad",
                "Workspace.Fort").isEmpty());
        yes(localModulePath("Workspace.Other.Pack.Client", "script.Parent.Helper",
                "Workspace.Fort").isEmpty());
        yes(localModulePath("Workspace.Fort.Pack.Client", "game.ServerStorage.Secret",
                "Workspace.Fort").isEmpty());

        ScriptDependencies external = scanScriptDependencies(
            "local a = require(123456789)\\n" +
            "local b = require(234567891)\\n" +
            "local c = require(123456789)\\n" +
            "local d = require(game.ReplicatedStorage.Modules.X)\\n" +
            "local e = require(dynamicAsset)\\n" +
            "print(game:GetService('HttpService'))\\n");
        yes(external.externalAssets.size() == 2);
        yes(external.externalAssets.contains("123456789"));
        yes(external.dynamicRequires == 2);
        yes(external.externalServices.contains("ReplicatedStorage"));
        yes(external.externalServices.contains("HttpService"));
        yes(external.relativeModules.isEmpty());

        ScriptDependencies empty = scanScriptDependencies("");
        yes(empty.relativeModules.isEmpty());
        yes(empty.externalAssets.isEmpty());
        yes(empty.dynamicRequires == 0);
        yes(empty.externalServices.isEmpty());
        ScriptDependencies longCode = scanScriptDependencies("x".repeat(25000));
        yes(longCode.overflow);
        StringBuilder repeats = new StringBuilder();
        for (int i = 0; i < 42; i++) repeats.append("require(id)\\n");
        ScriptDependencies bounded = scanScriptDependencies(repeats.toString());
        yes(bounded.overflow);
        yes(bounded.dynamicRequires <= 40);
        yes(scanScriptDependencies("require(script.Parent:WaitForChild('M'))")
                .dynamicRequires == 1); // not guessed as a resolved local module

        System.out.println("Production dependency inventory and module path tests passed");
    }
}
""" % production

with tempfile.TemporaryDirectory(prefix="rbx-dependency-") as tmp:
    file = Path(tmp, "DependencyScanHarness.java")
    file.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(file)], cwd=tmp, check=True)
    subprocess.run(["java", "-cp", tmp, "DependencyScanHarness"], cwd=tmp, check=True)
