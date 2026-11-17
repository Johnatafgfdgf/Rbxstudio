#!/usr/bin/env python3
"""Exercise the production Java fixed Client EquipTool and read-back contracts.

Fixtures only test the Java control logic; they are NOT proof of an Android
playtest or of a simulated Roblox input tap.
"""
from pathlib import Path
import subprocess
import tempfile

src = (Path(__file__).resolve().parents[1] /
       "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
fragment = src[src.index("    /** Fixed, app-authored Client Luau."):
               src.index("    private void runLocalPlaySmoke()")]
assert 'private static String boundedLocalEquipLuau(' in fragment
assert 'private boolean confirmLiveEquippedTool(' in fragment
assert 'private static boolean readBackEquippedTool(' in fragment
assert 'private boolean verifyLocalEquipParent(' in fragment
assert 'callMcp(paired, "execute_luau"' in fragment
assert 'callMcp(paired, "search_game_tree"' in fragment
assert '"datamodel_type", "Client"' in fragment
for forbidden in ['multi_edit', 'publish_animation', 'user_mouse_input',
                  'requestModelWithFreeFallback', 'remoteEvent:FireServer']:
    assert forbidden not in fragment

harness = r"""
import java.io.IOException;
import java.util.*;

public class EquipHarness {
    static class JSONObject {
        final Map<String, Object> fields = new HashMap<>();
        JSONObject put(String k, Object v) { fields.put(k, v); return this; }
        String optString(String k, String def) {
            Object v = fields.get(k);
            return v instanceof String ? (String)v : def;
        }
        String getString(String k) { return (String)fields.get(k); }
    }
    static class JSONArray {
        final List<JSONObject> items = new ArrayList<>();
        JSONArray put(JSONObject o) { items.add(o); return this; }
        int length() { return items.size(); }
        JSONObject optJSONObject(int index) { return items.get(index); }
    }
    JSONArray fixture;
    String nextReceipt = "EQUIPPED";
    int luaCalls, treeCalls;
    JSONObject lastLua, lastTree;
    private String callMcp(String paired, String tool, JSONObject args) throws IOException {
        if (!"session".equals(paired)) throw new IOException("Bad pairing");
        if ("execute_luau".equals(tool)) {
            luaCalls++;
            lastLua = args;
            return nextReceipt;
        }
        if ("search_game_tree".equals(tool)) {
            treeCalls++;
            lastTree = args;
            return "live-tree";
        }
        throw new IOException("Unexpected MCP tool");
    }
    private static JSONArray activeFixture;
    private static JSONArray strictLiveTree(String raw, String root, String klass, int limit)
            throws IOException {
        if (!"live-tree".equals(raw) || activeFixture == null ||
                activeFixture.length() < 1 || activeFixture.length() > limit)
            throw new IOException("Truncated tree");
        JSONObject first = activeFixture.optJSONObject(0);
        if (first == null || !root.equals(first.optString("fullPath", "")) ||
                !klass.equals(first.optString("className", "")))
            throw new IOException("Invalid root");
        return activeFixture;
    }
%s
    static void yes(boolean b) { if (!b) throw new AssertionError("EquipTool contract"); }
    static JSONObject row(String path, String name, String klass, String parent) {
        return new JSONObject().put("fullPath", path).put("name", name)
                .put("className", klass).put("parentName", parent);
    }
    static void bad(CheckedAction a) throws Exception {
        try { a.run(); } catch(IOException expected) { return; }
        throw new AssertionError("Invalid target or receipt accepted");
    }
    interface CheckedAction { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        String p = "Alex", sword = "Players.Alex.Backpack.Magic Sword";
        String code = boundedLocalEquipLuau(p, sword);
        yes(code.contains("Humanoid:EquipTool") || code.contains("humanoid:EquipTool(found)"));
        yes(code.contains("LocalPlayer"));
        yes(code.contains("if count ~= 1 or not found then"));
        yes(code.contains("task.wait(0.25)"));
        yes(code.contains("\"Magic Sword\""));
        yes(code.contains("\"EQUIPPED\""));
        yes(!code.contains("HttpService"));
        yes(!code.contains("require("));
        bad(() -> boundedLocalEquipLuau("Alex", "Players.Bob.Backpack.Magic Sword"));
        bad(() -> boundedLocalEquipLuau("Alex", "Players.Alex.Backpack.Wand\"; evil()"));
        bad(() -> boundedLocalEquipLuau("Alex", "Players.Alex.Backpack.Wand\\nprint(1)"));
        bad(() -> boundedLocalEquipLuau("A-B", sword));
        bad(() -> boundedLocalEquipLuau("Alex", "Workspace.Magic Sword"));
        bad(() -> boundedLocalEquipLuau("Alex", "Players.Alex.Backpack."));
        yes(boundedLocalEquipLuau("Alex", "Players.Alex.Backpack.Test-Sword")
                .contains("\"Test-Sword\""));

        EquipHarness runtime = new EquipHarness();
        yes(runtime.confirmLiveEquippedTool("session", p, sword));
        yes(runtime.luaCalls == 1);
        yes("Client".equals(runtime.lastLua.getString("datamodel_type")));
        yes(code.equals(runtime.lastLua.getString("code")));
        runtime.nextReceipt = "EQUIP_NOT_CONFIRMED";
        bad(() -> runtime.confirmLiveEquippedTool("session", p, sword));
        runtime.nextReceipt = "EQUIPPED and something else";
        bad(() -> runtime.confirmLiveEquippedTool("session", p, sword));
        runtime.nextReceipt = "EQUIPPED";

        JSONArray good = new JSONArray()
                .put(row("Workspace.Alex", "Alex", "Model", "Workspace"))
                .put(row("Workspace.Alex.Magic Sword", "Magic Sword", "Tool", "Alex"));
        activeFixture = good;
        yes(readBackEquippedTool("live-tree", p, sword));
        yes(runtime.verifyLocalEquipParent("session", p, sword));
        yes(runtime.treeCalls == 1);
        yes("Client".equals(runtime.lastTree.getString("datamodel_type")));
        yes("Workspace.Alex".equals(runtime.lastTree.getString("path")));
        yes(1 == (int)runtime.lastTree.fields.get("max_depth"));
        yes(48 == (int)runtime.lastTree.fields.get("head_limit"));

        activeFixture = new JSONArray()
                .put(row("Workspace.Alex", "Alex", "Model", "Workspace"))
                .put(row("Workspace.Alex.Magic Sword", "Magic Sword", "Folder", "Alex"));
        yes(!readBackEquippedTool("live-tree", p, sword));
        activeFixture = new JSONArray()
                .put(row("Workspace.Alex", "Alex", "Model", "Workspace"))
                .put(row("Workspace.Alex.Magic Sword", "Magic Sword", "Tool", "Other"));
        yes(!readBackEquippedTool("live-tree", p, sword));
        activeFixture = new JSONArray()
                .put(row("Workspace.Alex", "Alex", "Model", "Workspace"))
                .put(row("Workspace.Alex.Magic Sword", "Magic Sword", "Tool", "Alex"))
                .put(row("Workspace.Alex.Magic Sword", "Magic Sword", "Tool", "Alex"));
        yes(!readBackEquippedTool("live-tree", p, sword));
        activeFixture = good;
        bad(() -> readBackEquippedTool("live-tree", p, "Players.Bob.Backpack.Magic Sword"));
        activeFixture = null;
        bad(() -> runtime.verifyLocalEquipParent("session", p, sword));
        System.out.println("Production Client EquipTool command and MCP read-back: passed");
    }
}
""" % fragment

with tempfile.TemporaryDirectory(prefix="rbx-equip-") as tmp:
    file = Path(tmp) / "EquipHarness.java"
    file.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(file)], check=True, cwd=tmp)
    subprocess.run(["java", "-cp", tmp, "EquipHarness"], check=True, cwd=tmp)
