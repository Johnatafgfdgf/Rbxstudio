#!/usr/bin/env python3
"""Compile production Part styling helpers and verify only typed fixed Luau is emitted.

This is a Java unit test of the real source, not a simulated Android/MCP editor.
Physical device, material rendering, physics and Undo remain separate tests.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

def cut(start, stop):
    p = src.index(start)
    q = src.index(stop, p)
    return src[p:q].strip()

proposal = cut("    private static final class PartStyleProposal {",
               "    private static final class LightingProposal {")
validators = cut("    private static boolean allowedPartMaterial(String value) {",
                 "    private static PartStyleProposal validatePartStyleProposal(")
writers = cut("    private static boolean partStyleMatches(JSONObject props, PartStyleProposal style)",
              "    private void confirmPendingPartStyle()")

assert "return code.append" in writers
assert "execute_luau" not in writers
assert "callMcp(" not in writers

java = r"""
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
public final class PartStyleHarness {
    private static final class JSONObject {
        final Map<String, Object> data = new HashMap<>();
        JSONObject put(String key, Object value) { data.put(key, value); return this; }
        Object opt(String key) { return data.get(key); }
        boolean has(String key) { return data.containsKey(key); }
    }
%s
%s
%s
    private static void yes(boolean flag) {
        if (!flag) throw new AssertionError("Real Part style contract failed");
    }
    private static void bad(Check action) {
        try { action.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Invalid Part properties were accepted");
    }
    interface Check { void run() throws IOException; }
    private static JSONObject props(String material, double transparency,
            boolean anchored, boolean collide) {
        return new JSONObject().put("Material", "Enum.Material." + material)
                .put("Transparency", transparency)
                .put("Anchored", anchored)
                .put("CanCollide", collide);
    }
    public static void main(String[] args) throws Exception {
        yes(allowedStylePath("Workspace.Piso"));
        yes(allowedStylePath("Workspace.Casa.Piso"));
        yes(!allowedStylePath("Workspace.Casa.Andar.Piso"));
        yes(!allowedStylePath("Workspace.Casa.Piso;require(12345)"));
        yes(!allowedStylePath("ReplicatedStorage.Casa.Piso"));
        yes(allowedPartMaterial("Neon"));
        yes(allowedPartMaterial("Concrete"));
        yes(allowedPartMaterial("SmoothPlastic"));
        yes(!allowedPartMaterial("Plastic; require(123456)"));
        yes(!allowedPartMaterial("Foil"));
        yes(!allowedPartMaterial(null));
        JSONObject before = props("Plastic", 0.0, true, true);
        yes(inspectedPartMaterial(before).equals("Plastic"));
        yes(inspectedPartTransparency(before) == 0.0);
        yes(inspectedPartBoolean(before, "Anchored"));
        yes(!Boolean.TRUE.equals(optionalPartBoolean(new JSONObject(), "anchored")));
        yes(Boolean.FALSE.equals(optionalPartBoolean(
                new JSONObject().put("anchored", false), "anchored")));
        bad(() -> optionalPartBoolean(new JSONObject().put("anchored", "false"), "anchored"));
        bad(() -> inspectedPartMaterial(new JSONObject().put("Material", "require(12345)")));
        bad(() -> inspectedPartTransparency(new JSONObject().put("Transparency", 1.1)));
        bad(() -> inspectedPartBoolean(new JSONObject().put("Anchored", "true"), "Anchored"));

        PartStyleProposal style = new PartStyleProposal(
                "Workspace.Piso", "inject require(12345)", "Plastic",
                0.0, true, true, "Concrete", 0.35, false, false);
        String code = fixedPartStyleLuau(style);
        yes(code.contains("p.Material = Enum.Material.Concrete"));
        yes(code.contains("p.Transparency = 0.35"));
        yes(code.contains("p.Anchored = false"));
        yes(code.contains("p.CanCollide = false"));
        yes(code.contains("STALE_PART_STYLE"));
        yes(code.contains("UPDATED_STYLE:Piso"));
        yes(!code.contains("inject require(12345)"));
        yes(!code.contains("require(12345)"));
        yes(partStyleMatches(props("Concrete", 0.35, false, false), style));
        yes(!partStyleMatches(props("Concrete", 0.35, true, false), style));
        yes(!partStyleMatches(props("Plastic", 0.35, false, false), style));
        yes(!partStyleMatches(props("Concrete", 0.50, false, false), style));
        PartStyleProposal onlyGlass = new PartStyleProposal(
                "Workspace.Janela", "glass", "Plastic", 0.0, true, false,
                "Glass", null, null, null);
        String glass = fixedPartStyleLuau(onlyGlass);
        yes(glass.contains("p.Material = Enum.Material.Glass"));
        yes(!glass.contains("p.Transparency = "));
        yes(!glass.contains("p.Anchored = "));
        yes(!glass.contains("p.CanCollide = "));
        yes(partStyleMatches(props("Glass", 0, true, false), onlyGlass));
        PartStyleProposal nested = new PartStyleProposal(
                "Workspace.Casa.Piso", "safe nested style", "Plastic",
                0.0, true, true, "Wood", 0.2, null, null);
        String nestedCode = fixedPartStyleLuau(nested);
        yes(nestedCode.contains("local parent = ws:FindFirstChild(\"Casa\")"));
        yes(nestedCode.contains("parent:IsA(\"Model\")"));
        yes(nestedCode.contains("parent.Parent ~= ws"));
        yes(nestedCode.contains("p.Parent ~= parent"));
        yes(nestedCode.contains("UPDATED_STYLE:Casa.Piso"));
        yes(nestedCode.contains("p.Material = Enum.Material.Wood"));
        yes(!nestedCode.contains("require("));
        boolean invalidRejected = false;
        try {
            fixedPartStyleLuau(new PartStyleProposal("Workspace.Casa.Piso;require(4)",
                    "unsafe", "Plastic", 0, true, true, "Neon", null, null, null));
        } catch (IllegalArgumentException expected) { invalidRejected = true; }
        yes(invalidRejected);
        System.out.println("Production Part style Java values and fixed Luau: passed");
    }
}
""" % (proposal, validators, writers)

with tempfile.TemporaryDirectory(prefix="rbx-part-style-") as tmp:
    file = Path(tmp) / "PartStyleHarness.java"
    file.write_text(java)
    subprocess.run(["javac", "--release", "11", str(file)], cwd=tmp, check=True)
    subprocess.run(["java", "-cp", tmp, "PartStyleHarness"], cwd=tmp, check=True)
