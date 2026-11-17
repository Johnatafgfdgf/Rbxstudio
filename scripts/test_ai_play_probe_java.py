#!/usr/bin/env python3
"""Compile and execute the production Play/Output report helpers.

Tests truthful state classification only, not a live Android Play session.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
start = src.index("    /** Only the exact native MCP status fields")
end = src.index("    /** Open local Play through existing editor controls", start)
helpers = src[start:end]
assert "readOnlyMcp(" not in helpers
assert "callMcp(" not in helpers
assert "start_stop_play" not in helpers

harness = r"""
import java.util.Locale;
import java.util.regex.Pattern;
public final class PlayProbeHarness {
    private static final int MAX_PLAY_LOG_LINES = 8;
    private static final int MAX_PLAY_LOG_CHARS = 1800;
%s
    static void yes(boolean b) {
        if (!b) throw new AssertionError("Play probe contract failed");
    }
    public static void main(String[] args) {
        String playing = "- Current Studio Mode: Play\n" +
                "- Available DataModels: Edit, Client, Server\n" +
                "- Focused DataModel in the viewport: Client";
        String live = "- Current Studio Mode: Play\n" +
                "- Available DataModels: Client, Server\n" +
                "- Focused DataModel in the viewport: Client";
        String edit = "- Current Studio Mode: Edit\n" +
                "- Available DataModels: Edit\n" +
                "- Focused DataModel in the viewport: Edit";
        String starting = "- Current Studio Mode: Starting\n" +
                "- Available DataModels: Edit\n" +
                "- Focused DataModel in the viewport: Edit";
        yes(checkedLocalPlayMode(playing).equals("PLAY_LOCAL_CONFIRMADO"));
        yes(checkedLocalPlayMode(live).equals("PLAY_LOCAL_CONFIRMADO"));
        yes(checkedLocalPlayMode(edit).equals("EDIT"));
        yes(checkedLocalPlayMode(starting).equals("INICIANDO"));
        yes(checkedLocalPlayMode(null).equals("INCONCLUSIVO"));
        yes(checkedLocalPlayMode("Play").equals("INCONCLUSIVO"));
        yes(checkedLocalPlayMode(playing.replace("Client", "Edit")).equals("INCONCLUSIVO"));
        yes(checkedLocalPlayMode(playing.replace(
                "Focused DataModel in the viewport: Client",
                "Focused DataModel in the viewport: Edit")).equals("INCONCLUSIVO"));
        yes(checkedLocalPlayMode(playing.replace(
                "Available DataModels: Edit, Client, Server",
                "Available DataModels: Client")).equals("INCONCLUSIVO"));
        String report = buildLocalPlayReport(playing,
                "main: OK\nScript error: bad argument\nwarning: old API\nAnother print");
        yes(report.contains("Play local indicado pelo MCP"));
        yes(report.contains("1 erro(s), 1 aviso(s)"));
        yes(report.contains("Script error: bad argument"));
        yes(report.contains("Nenhum dado enviado à API de IA"));
        yes(!buildLocalPlayReport(edit, "Everything fine").contains(
                "Play local indicado pelo MCP"));
        yes(buildLocalPlayReport(edit, "Everything fine").contains(
                "0 erro(s), 0 aviso(s)"));
        yes(buildLocalPlayReport(playing, null).contains("Inspeção incompleta"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 60; i++) many.append("Error: item").append(i).append("\n");
        String clipped = buildLocalPlayReport(playing, many.toString());
        yes(clipped.contains("[Exibição limitada]"));
        yes(clipped.length() < 5000);
        System.out.println("Play/Output production Java state classification: passed");
    }
}
""" % helpers

with tempfile.TemporaryDirectory(prefix="rbx-play-probe-") as tmp:
    p = Path(tmp) / "PlayProbeHarness.java"
    p.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(p)], check=True, cwd=tmp)
    subprocess.run(["java", "-cp", tmp, "PlayProbeHarness"], check=True, cwd=tmp)
