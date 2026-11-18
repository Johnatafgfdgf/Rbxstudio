#!/usr/bin/env python3
"""Compile real Android AI Lighting command generation and MCP receipt checker.

The fixture tests fixed, typed Luau strings and error receipts, not an editor
simulation. A real MCP readback and physical Android Play test are still required.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
code = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

def cut(start, end):
    i = code.index(start)
    j = code.index(end, i)
    return code[i:j].strip()

proposal = cut("    private static final class LightingProposal {",
               "    private static final class CompositeProposal {")
luau = cut("    private static String fixedLightingLuau(LightingProposal change) {",
           "    private static boolean lightingMatches(")
receipt = cut("    private static String checkedEditReceipt(String raw, String expected)",
              "    /** Shared real MCP transport")

java = r"""
import java.io.IOException;
public final class LightingHarness {
%s
%s
%s
    static void yes(boolean b) { if (!b) throw new AssertionError("Lighting guard failed"); }
    static void denied(String raw) {
        try { checkedEditReceipt(raw, "UPDATED_LIGHTING:Lighting"); }
        catch (IOException expected) { return; }
        throw new AssertionError("Ambiguous/failed receipt passed: " + raw);
    }
    public static void main(String[] args) throws Exception {
        LightingProposal afternoon = new LightingProposal(
                "Afternoon scenery", 12.0, 2.0, 16.0, null);
        String fixed = fixedLightingLuau(afternoon);
        yes(fixed.contains("l.ClockTime = 16.0"));
        yes(!fixed.contains("l.Brightness = "));
        yes(fixed.contains("l.Brightness - 2.0"));
        yes(fixed.contains("STALE_LIGHTING"));
        yes(fixed.contains("return \"UPDATED_LIGHTING:Lighting\""));
        yes(!fixed.contains("Afternoon scenery"));
        yes(!fixed.contains("ServerScriptService"));

        LightingProposal night = new LightingProposal(
                "delete everything = require(12345)", 8.0, 1.5, null, 0.25);
        String fixedNight = fixedLightingLuau(night);
        yes(fixedNight.contains("l.Brightness = 0.25"));
        yes(!fixedNight.contains("l.ClockTime = "));
        yes(!fixedNight.contains("require(12345)"));
        yes(checkedEditReceipt("UPDATED_LIGHTING:Lighting",
                "UPDATED_LIGHTING:Lighting").equals("UPDATED_LIGHTING:Lighting"));
        denied("STALE_LIGHTING");
        denied("ERROR: unauthorized");
        denied("UPDATED_LIGHTING:Lighting\nPAID");
        denied("UPDATED_LIGHTING:Another");
        try {
            checkedEditReceipt("UPDATED_LIGHTING:Lighting", "unsafe.call()");
            throw new AssertionError("Invalid receipt expectation allowed");
        } catch (IOException expected) { }
        System.out.println("Lighting Java fixed Luau and MCP receipts: 16 assertions passed");
    }
}
""" % (proposal, luau, receipt)

with tempfile.TemporaryDirectory(prefix="rbx-lighting-") as folder:
    source = Path(folder, "LightingHarness.java")
    source.write_text(java)
    subprocess.run(["javac", "--release", "11", str(source)], cwd=folder, check=True)
    subprocess.run(["java", "-cp", folder, "LightingHarness"], cwd=folder, check=True)
