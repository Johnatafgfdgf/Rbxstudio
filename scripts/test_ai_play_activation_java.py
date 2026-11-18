#!/usr/bin/env python3
"""Compile and run actual Java optional Client Tool.Activated API probe.

Tests a constrained command builder and strict MCP receipt, not actual Android
touch input, GuiButton clicks or functional gameplay on a physical device.
"""
from pathlib import Path
import subprocess
import tempfile

src = (Path(__file__).resolve().parents[1] /
       "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
block = src[src.index("    /** Explicit, opt-in programmatic Tool:Activate"):
            src.index("    private void runLocalPlaySmoke() {")]
assert 'private static String boundedLocalActivateLuau(' in block
assert 'private boolean confirmProgrammaticActivated(' in block
assert 'found.Activated:Connect(function()' in block
assert 'found:Activate()' in block
assert 'found:Deactivate()' in block
assert 'activatedListener:Disconnect()' in block
assert '"datamodel_type", "Client"' in block
for bad in ["multi_edit", "HttpService", "requestModelWithFreeFallback",
            "user_mouse_input", "user_keyboard_input", "InputObject",
            "RemoteEvent", "publish_animation"]:
    assert bad not in block, bad

harness = r"""
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ActivateHarness {
    static class JSONObject {
        final Map<String, Object> properties = new HashMap<>();
        JSONObject put(String k, Object v) {
            properties.put(k, v);
            return this;
        }
        Object get(String k) { return properties.get(k); }
    }
    String result = "ACTIVATED_EVENT", lastMethod = "", lastPaired = "";
    JSONObject lastArgs;
    int calls;
    private String callMcp(String paired, String tool, JSONObject args) throws IOException {
        calls++;
        lastMethod = tool;
        lastPaired = paired;
        lastArgs = args;
        if (!"execute_luau".equals(tool)) throw new IOException("Unknown MCP action");
        return result;
    }
%s
    static void yes(boolean ok) {
        if (!ok) throw new AssertionError("Activation contract failed");
    }
    static void bad(RunnableEx callback) throws Exception {
        try { callback.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Rejected input accepted");
    }
    interface RunnableEx { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        String p = "Alex", path = "Players.Alex.Backpack.Magic Sword";
        String code = boundedLocalActivateLuau(p, path);
        yes(code.contains("LocalPlayer"));
        yes(code.contains("char:GetChildren()"));
        yes(code.contains("count ~= 1"));
        yes(code.contains("found.Activated:Connect("));
        yes(code.contains("found:Activate()"));
        yes(code.contains("found:Deactivate()"));
        yes(code.contains("activatedListener:Disconnect()"));
        yes(code.contains("ACTIVATED_EVENT"));
        yes(code.contains("ACTIVATION_NOT_CONFIRMED"));
        yes(code.contains("\"Magic Sword\""));
        yes(!code.contains("HttpService") && !code.contains("FireServer"));
        bad(() -> boundedLocalActivateLuau("Alex", "Players.Bob.Backpack.Magic Sword"));
        bad(() -> boundedLocalActivateLuau("A-B", path));
        bad(() -> boundedLocalActivateLuau("Alex", "Workspace.Alex.Magic Sword"));
        bad(() -> boundedLocalActivateLuau("Alex",
                "Players.Alex.Backpack.Magic Sword\\nprint(1)"));
        bad(() -> boundedLocalActivateLuau("Alex",
                "Players.Alex.Backpack.A\\\";evil()"));
        bad(() -> boundedLocalActivateLuau("Alex", "Players.Alex.Backpack."));
        yes(boundedLocalActivateLuau("Alex",
                "Players.Alex.Backpack.Toy-1").contains("\"Toy-1\""));

        ActivateHarness runtime = new ActivateHarness();
        yes(runtime.confirmProgrammaticActivated("session", p, path));
        yes(runtime.calls == 1);
        yes("execute_luau".equals(runtime.lastMethod));
        yes("session".equals(runtime.lastPaired));
        yes("Client".equals(runtime.lastArgs.get("datamodel_type")));
        yes(code.equals(runtime.lastArgs.get("code")));
        runtime.result = "ACTIVATION_NOT_CONFIRMED";
        bad(() -> runtime.confirmProgrammaticActivated("session", p, path));
        runtime.result = "ACTIVATED_EVENT; ACTIVATE_FAILED";
        bad(() -> runtime.confirmProgrammaticActivated("session", p, path));
        runtime.result = "TOOL_NOT_READY";
        bad(() -> runtime.confirmProgrammaticActivated("session", p, path));
        runtime.result = null;
        bad(() -> runtime.confirmProgrammaticActivated("session", p, path));
        System.out.println("Production Java Tool.Activated API event observation: passed");
    }
}
""" % block

with tempfile.TemporaryDirectory(prefix="rbx-activate-") as temp:
    file = Path(temp) / "ActivateHarness.java"
    file.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(file)], cwd=temp, check=True)
    subprocess.run(["java", "-cp", temp, "ActivateHarness"], cwd=temp, check=True)
