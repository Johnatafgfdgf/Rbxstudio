#!/usr/bin/env python3
"""Compile production Java plan category guards and exercise positive/negative cases.

This is a unit-level safety check, not a simulation of running the Android
editor. The real device and MCP write/readback need separate integration tests.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
def extract(start_token, end_token):
    start = java.index(start_token)
    end = java.index(end_token, start)
    result = java[start:end].strip()
    assert result.endswith("}"), start_token
    return result

matcher = extract("    private static boolean planStepMatches(",
                  "    private static final class AgentOutcome {")
dispatcher = extract("    private static boolean actionMatchesPlannedKind(",
                     "    /** Bounded read-only agent loop")

harness = r"""
public final class PlanGuardHarness {
%s
%s
    static void yes(boolean pass) {
        if (!pass) throw new AssertionError("Valid plan operation rejected");
    }
    static void no(boolean pass) {
        if (pass) throw new AssertionError("Unexpected tool permitted");
    }
    public static void main(String[] args) {
        yes(planStepMatches("SCRIPT", "CREATE_SCRIPT"));
        yes(planStepMatches("SCRIPT", "UPDATE_SCRIPT"));
        yes(planStepMatches("PART", "CREATE_PART"));
        yes(planStepMatches("PART", "CREATE_MODEL"));
        yes(planStepMatches("PART", "UPDATE_PART"));
        yes(planStepMatches("PART", "UPDATE_PART_STYLE"));
        no(planStepMatches("GUI", "UPDATE_PART_STYLE"));
        yes(planStepMatches("GUI", "CREATE_SCREEN_GUI"));
        yes(planStepMatches("GUI", "CREATE_BILLBOARD"));
        yes(planStepMatches("ITEM", "CREATE_TOOL_KIT"));
        yes(planStepMatches("LIGHTING", "UPDATE_LIGHTING"));
        no(planStepMatches("LIGHTING", "CREATE_TOOL_KIT"));
        no(planStepMatches("PART", "UPDATE_LIGHTING"));
        no(planStepMatches("ITEM", "CREATE_SCRIPT"));
        no(planStepMatches("SCRIPT", "CREATE_MODEL"));
        no(planStepMatches("PART", "CREATE_TOOL_KIT"));
        no(planStepMatches("PUBLISH", "PUBLISH_ANIMATION"));
        yes(actionMatchesPlannedKind("propose_script_edit", "SCRIPT"));
        yes(actionMatchesPlannedKind("propose_script_create", "SCRIPT"));
        yes(actionMatchesPlannedKind("propose_part_create", "PART"));
        yes(actionMatchesPlannedKind("propose_part_update", "PART"));
        yes(actionMatchesPlannedKind("propose_part_style_update", "PART"));
        no(actionMatchesPlannedKind("propose_part_style_update", "SCRIPT"));
        yes(actionMatchesPlannedKind("propose_composite_create", "PART"));
        yes(actionMatchesPlannedKind("propose_billboard_create", "GUI"));
        yes(actionMatchesPlannedKind("propose_screen_gui_create", "GUI"));
        yes(actionMatchesPlannedKind("propose_receive_tool_create", "ITEM"));
        yes(actionMatchesPlannedKind("propose_lighting_update", "LIGHTING"));
        no(actionMatchesPlannedKind("propose_lighting_update", "PART"));
        no(actionMatchesPlannedKind("propose_receive_tool_create", "SCRIPT"));
        no(actionMatchesPlannedKind("propose_part_create", "GUI"));
        no(actionMatchesPlannedKind("execute_luau", "PART"));
        no(actionMatchesPlannedKind("publish_animation", "ITEM"));
        no(actionMatchesPlannedKind("propose_task_plan", "SCRIPT"));
        System.out.println("Production Java plan guards: 34 acceptance/rejection cases passed");
    }
}
""" % (matcher, dispatcher)

with tempfile.TemporaryDirectory(prefix="rbx-plan-guard-") as d:
    file = Path(d) / "PlanGuardHarness.java"
    file.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(file)], check=True, cwd=d)
    subprocess.run(["java", "-cp", d, "PlanGuardHarness"], check=True, cwd=d)
