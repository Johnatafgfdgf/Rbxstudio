#!/usr/bin/env python3
"""Test the Android agent’s typed Lighting requests and read-back checks."""
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
numeric = cut("    private static double inspectedLightingNumber(",
              "    private static JSONObject verifiedLightingInspection(")
lighting = cut("    private static JSONObject lightingEditArguments(",
               "    private void confirmPendingLighting()")
receipt = cut("    private static String checkedEditReceipt(String raw, String expected)",
              "    /** Shared real MCP transport")

java = r"""
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class LightingHarness {
    static final class JSONObject {
        final Map<String, Object> fields = new HashMap<>();
        JSONObject put(String field, Object value) {
            if (value == null) fields.remove(field);
            else fields.put(field, value);
            return this;
        }
        Object opt(String field) { return fields.get(field); }
    }
%s
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
        JSONObject day = lightingEditArguments(afternoon);
        yes(day.opt("clock_time").equals(16.0));
        yes(day.opt("before_clock_time").equals(12.0));
        yes(day.opt("before_brightness").equals(2.0));
        yes(day.opt("brightness") == null);
        yes(day.fields.size() == 3);
        yes(lightingMatches(new JSONObject().put("ClockTime", 16.0)
                .put("Brightness", 2.0), afternoon));
        yes(!lightingMatches(new JSONObject().put("ClockTime", 12.0)
                .put("Brightness", 2.0), afternoon));

        LightingProposal night = new LightingProposal(
                "reason is chat text", 8.0, 1.5, null, 0.25);
        JSONObject dim = lightingEditArguments(night);
        yes(dim.opt("brightness").equals(0.25));
        yes(dim.opt("clock_time") == null);
        yes(dim.fields.size() == 3);
        yes(!lightingMatches(new JSONObject().put("ClockTime", 20.0)
                .put("Brightness", 0.25), night));

        LightingProposal midnight = new LightingProposal("midnight", 18.0, 2.0, 24.0, null);
        yes(lightingMatches(new JSONObject().put("ClockTime", 0.0)
                .put("Brightness", 2.0), midnight));
        yes(lightingMatches(new JSONObject().put("ClockTime", 23.9999)
                .put("Brightness", 2.0), midnight));
        try {
            lightingMatches(new JSONObject().put("ClockTime", "16")
                    .put("Brightness", 2.0), afternoon);
            throw new AssertionError("Non-numeric inspection time accepted");
        } catch (IOException expected) { }
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
        System.out.println("Lighting Java typed requests, midnight read-back and MCP receipts: passed");
    }
}
""" % (proposal, numeric, lighting, receipt)

with tempfile.TemporaryDirectory(prefix="rbx-lighting-") as folder:
    source = Path(folder, "LightingHarness.java")
    source.write_text(java)
    subprocess.run(["javac", "--release", "11", str(source)], cwd=folder, check=True)
    subprocess.run(["java", "-cp", folder, "LightingHarness"], cwd=folder, check=True)
