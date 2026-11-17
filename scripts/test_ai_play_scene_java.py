#!/usr/bin/env python3
"""Execute the production Java Client Player/GUI/Humanoid row evaluator.

The in-memory JSONObject/JSONArray replacements supply controlled rows, not
live gameplay. A phone Play/Output test remains separately required.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

snapshot = src[src.index("    /** Structural snapshot, not an assertion"):
               src.index("    /** Distinguish an actual Play MCP session")]
evaluator = src[src.index("    /** Parse bounded live Client tree rows"):
                src.index("    /** Fixed Client-side gameplay probes")]
assert "evaluateLocalPlayerScenario" in evaluator
assert "callMcp(" not in evaluator and "execute_luau" not in evaluator

harness = r"""
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerSceneHarness {
    static final class JSONObject {
        private final Map<String, String> fields = new HashMap<>();
        JSONObject put(String k, String v) { fields.put(k, v); return this; }
        String optString(String k, String fallback) {
            return fields.getOrDefault(k, fallback);
        }
    }
    static final class JSONArray {
        private final List<JSONObject> rows = new ArrayList<>();
        JSONArray put(JSONObject v) { rows.add(v); return this; }
        int length() { return rows.size(); }
        JSONObject optJSONObject(int i) { return rows.get(i); }
    }
%s
%s
    static void yes(boolean b) {
        if (!b) throw new AssertionError("Play scene evaluator contract failed");
    }
    static JSONObject row(String name, String clazz, String parent, String path) {
        return new JSONObject().put("name", name).put("className", clazz)
                .put("parentName", parent).put("fullPath", path);
    }
    static JSONArray basic(String user) {
        return new JSONArray()
                .put(row("Players", "Players", "Place", "Players"))
                .put(row(user, "Player", "Players", "Players." + user));
    }
    public static void main(String[] args) {
        JSONArray player = basic("Alex")
                .put(row("Backpack", "Backpack", "Alex", "Players.Alex.Backpack"))
                .put(row("PlayerGui", "PlayerGui", "Alex", "Players.Alex.PlayerGui"));
        JSONArray humanoid = new JSONArray()
                .put(row("Humanoid", "Humanoid", "Alex", "Workspace.Alex.Humanoid"));
        LocalPlayerScenario complete = evaluateLocalPlayerScenario(player, humanoid);
        yes(complete.complete());
        yes(complete.player && complete.backpack && complete.playerGui && complete.humanoid);
        yes(!evaluateLocalPlayerScenario(null, humanoid).complete());
        yes(!evaluateLocalPlayerScenario(player, null).complete());
        yes(!evaluateLocalPlayerScenario(new JSONArray(), humanoid).player);

        JSONArray fakeBackpack = basic("Alex")
                .put(row("Backpack", "Folder", "Alex", "Players.Alex.Backpack"))
                .put(row("PlayerGui", "PlayerGui", "Alex", "Players.Alex.PlayerGui"));
        LocalPlayerScenario fakeResult = evaluateLocalPlayerScenario(fakeBackpack, humanoid);
        yes(fakeResult.player && fakeResult.playerGui && !fakeResult.backpack);
        JSONArray fakePath = basic("Alex")
                .put(row("Backpack", "Backpack", "Alex", "Players.Other.Backpack"))
                .put(row("PlayerGui", "PlayerGui", "Alex", "Players.Alex.PlayerGui"));
        yes(!evaluateLocalPlayerScenario(fakePath, humanoid).backpack);
        JSONArray wrongHumanoid = new JSONArray()
                .put(row("Humanoid", "Humanoid", "Other", "Workspace.Other.Humanoid"));
        yes(!evaluateLocalPlayerScenario(player, wrongHumanoid).humanoid);

        JSONArray twoPlayers = basic("Alex")
                .put(row("Backpack", "Backpack", "Alex", "Players.Alex.Backpack"))
                .put(row("Bob", "Player", "Players", "Players.Bob"))
                .put(row("PlayerGui", "PlayerGui", "Bob", "Players.Bob.PlayerGui"));
        JSONArray bobHumanoid = new JSONArray()
                .put(row("Humanoid", "Humanoid", "Bob", "Workspace.Bob.Humanoid"));
        LocalPlayerScenario split = evaluateLocalPlayerScenario(twoPlayers, bobHumanoid);
        yes(!split.complete()); // never combine two separate Players
        JSONArray bobComplete = basic("Alex")
                .put(row("Bob", "Player", "Players", "Players.Bob"))
                .put(row("Backpack", "Backpack", "Bob", "Players.Bob.Backpack"))
                .put(row("PlayerGui", "PlayerGui", "Bob", "Players.Bob.PlayerGui"));
        yes(evaluateLocalPlayerScenario(bobComplete, bobHumanoid).complete());
        yes(!evaluateLocalPlayerScenario(
                new JSONArray().put(row("Alex", "Model", "Players", "Players.Alex")),
                humanoid).player);
        yes(!evaluateLocalPlayerScenario(
                new JSONArray().put(row("Alex", "Player", "Workspace", "Players.Alex")),
                humanoid).player);
        System.out.println("Production Java Client Player scene evaluation: passed");
    }
}
""" % (snapshot, evaluator)

with tempfile.TemporaryDirectory(prefix="rbx-player-scene-") as tmp:
    file = Path(tmp) / "PlayerSceneHarness.java"
    file.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(file)], cwd=tmp, check=True)
    subprocess.run(["java", "-cp", tmp, "PlayerSceneHarness"], cwd=tmp, check=True)
