#!/usr/bin/env python3
"""Compile and execute production Java Tool/GUI Client readiness helpers.

Fixtures represent MCP tree rows, not a simulated passing gameplay session.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
status = src[src.index("    /** Real MCP Client properties"):
             src.index("    /** Client inspection must match")]
selection = src[src.index("    /** Select one exact Tool"):
                src.index("    /** No gameplay event is triggered")]
assert "strictMcpFlag" in status and "verifiedFlag" in status
assert "selectToolGuiCandidates" in selection and "hasDirectToolHandle" in selection
assert "execute_luau" not in status + selection
assert "user_mouse_input" not in status + selection

harness = r"""
import java.util.*;
public final class ToolGuiHarness {
    static final class JSONObject {
        final Map<String, Object> fields = new HashMap<>();
        JSONObject put(String key, Object value) { fields.put(key, value); return this; }
        Object opt(String key) { return fields.get(key); }
        String optString(String key, String def) {
            Object value = fields.get(key);
            return value instanceof String ? (String) value : def;
        }
        JSONObject optJSONObject(String key) {
            Object value = fields.get(key);
            return value instanceof JSONObject ? (JSONObject) value : null;
        }
    }
    static final class JSONArray {
        final List<JSONObject> nodes = new ArrayList<>();
        JSONArray put(JSONObject value) { nodes.add(value); return this; }
        int length() { return nodes.size(); }
        JSONObject optJSONObject(int index) { return nodes.get(index); }
    }
%s
%s
    static JSONObject row(String path, String name, String className, String parent) {
        return new JSONObject().put("fullPath", path).put("name", name)
                .put("className", className).put("parentName", parent);
    }
    static void yes(boolean condition) {
        if (!condition) throw new AssertionError("Live Tool/GUI Java contract failed");
    }
    static void equal(Object a, Object b) { yes(Objects.equals(a,b)); }
    static final String BAG = "Players.Alex.Backpack";
    static final String GUI = "Players.Alex.PlayerGui";
    static final String TOOL = BAG + ".Sword";
    static final String BTN = GUI + ".Menu.Frame.Play";
    public static void main(String[] args) {
        JSONArray backpack = new JSONArray().put(row(BAG, "Backpack", "Backpack", "Alex"))
                .put(row(TOOL, "Sword", "Tool", "Backpack"))
                .put(row(TOOL + ".Handle", "Handle", "MeshPart", "Sword"));
        JSONArray gui = new JSONArray().put(row(GUI, "PlayerGui", "PlayerGui", "Alex"))
                // The first ScreenGui is decorative and has no button.
                .put(row(GUI + ".Decoration", "Decoration", "ScreenGui", "PlayerGui"))
                .put(row(GUI + ".Menu", "Menu", "ScreenGui", "PlayerGui"))
                .put(row(GUI + ".Menu.Frame", "Frame", "Frame", "Menu"))
                .put(row(BTN, "Play", "TextButton", "Frame"));
        String[] selected = selectToolGuiCandidates(backpack, gui, "Alex");
        equal(selected[0], TOOL);
        equal(selected[1], GUI + ".Menu");
        equal(selected[2], BTN);
        yes(hasDirectToolHandle(backpack, TOOL));
        yes(!hasDirectToolHandle(backpack, "Players.Other.Backpack.Sword"));
        yes(!hasDirectToolHandle(backpack, ""));
        JSONArray falseHandle = new JSONArray().put(row(BAG, "Backpack", "Backpack", "Alex"))
                .put(row(TOOL, "Sword", "Tool", "Backpack"))
                .put(row(TOOL + ".Handle", "Handle", "Folder", "Sword"));
        yes(!hasDirectToolHandle(falseHandle, TOOL));
        JSONArray wrongParent = new JSONArray().put(row(BAG, "Backpack", "Backpack", "Alex"))
                .put(row(TOOL + ".Handle", "Handle", "Part", "Other"));
        yes(!hasDirectToolHandle(wrongParent, TOOL));

        JSONArray falseTool = new JSONArray().put(row(BAG, "Backpack", "Backpack", "Alex"))
                .put(row(TOOL, "Sword", "Model", "Backpack"));
        equal(selectToolGuiCandidates(falseTool, gui, "Alex")[0], "");
        equal(selectToolGuiCandidates(backpack, new JSONArray()
                .put(row(GUI, "PlayerGui", "PlayerGui", "Alex")), "Alex")[2], "");
        JSONArray otherScreenButton = new JSONArray().put(row(GUI, "PlayerGui", "PlayerGui", "Alex"))
                .put(row(GUI + ".Menu", "Menu", "ScreenGui", "PlayerGui"))
                .put(row("Players.Other.PlayerGui.Menu.Frame.Play", "Play", "TextButton", "Frame"));
        equal(selectToolGuiCandidates(backpack, otherScreenButton, "Alex")[2], "");
        JSONArray imageButton = new JSONArray().put(row(GUI, "PlayerGui", "PlayerGui", "Alex"))
                .put(row(GUI + ".Menu", "Menu", "ScreenGui", "PlayerGui"))
                .put(row(GUI + ".Menu.Go", "Go", "ImageButton", "Menu"));
        equal(selectToolGuiCandidates(backpack, imageButton, "Alex")[2], GUI + ".Menu.Go");
        JSONArray spacedBag = new JSONArray().put(row(BAG, "Backpack", "Backpack", "Alex"))
                .put(row(BAG + ".Magic Sword", "Magic Sword", "Tool", "Backpack"))
                .put(row(BAG + ".Magic Sword.Handle", "Handle", "Part", "Magic Sword"));
        JSONArray spacedGui = new JSONArray().put(row(GUI, "PlayerGui", "PlayerGui", "Alex"))
                .put(row(GUI + ".Main HUD", "Main HUD", "ScreenGui", "PlayerGui"))
                .put(row(GUI + ".Main HUD.Play Button", "Play Button", "TextButton", "Main HUD"));
        String[] spaced = selectToolGuiCandidates(spacedBag, spacedGui, "Alex");
        equal(spaced[0], BAG + ".Magic Sword");
        equal(spaced[1], GUI + ".Main HUD");
        equal(spaced[2], GUI + ".Main HUD.Play Button");
        yes(hasDirectToolHandle(spacedBag, BAG + ".Magic Sword"));


        JSONObject props = new JSONObject().put("properties",
                new JSONObject().put("Enabled", true).put("RequiresHandle", false));
        equal(strictMcpFlag(props, "Enabled"), Boolean.TRUE);
        equal(strictMcpFlag(props, "RequiresHandle"), Boolean.FALSE);
        equal(strictMcpFlag(props, "Visible"), null);
        equal(strictMcpFlag(new JSONObject().put("properties",
                new JSONObject().put("Enabled", "true")), "Enabled"), null);
        equal(strictMcpFlag(new JSONObject(), "Enabled"), null);
        equal(verifiedFlag(null), "sem confirmação");
        equal(verifiedFlag(true), "sim");
        equal(verifiedFlag(false), "não");

        LiveToolGuiAudit ready = new LiveToolGuiAudit(TOOL, BTN,
                true, true, true, true, true, true);
        yes(ready.toolReady() && ready.guiReady() && ready.complete());
        yes(new LiveToolGuiAudit(TOOL, BTN, true, false, false,
                true, true, true).complete());
        yes(!new LiveToolGuiAudit(TOOL, BTN, true, true, false,
                true, true, true).toolReady());
        yes(!new LiveToolGuiAudit(TOOL, BTN, true, true, true,
                true, false, true).guiReady());
        yes(!new LiveToolGuiAudit(TOOL, BTN, true, true, true,
                null, true, true).guiReady());
        yes(!new LiveToolGuiAudit("", BTN, true, true, true,
                true, true, true).complete());
        System.out.println("Production Java Tool/GUI read-only readiness: passed");
    }
}
""" % (status, selection)

with tempfile.TemporaryDirectory(prefix="rbx-tool-gui-") as tmp:
    path = Path(tmp) / "ToolGuiHarness.java"
    path.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(path)], check=True, cwd=tmp)
    subprocess.run(["java", "-cp", tmp, "ToolGuiHarness"], check=True, cwd=tmp)
