#!/usr/bin/env python3
"""Compile and exercise real local Play smoke contract helpers from StudioAiHub.

No fake MCP server or emulator is treated as actual Play functional validation.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
receipts = src[src.index("    /** Accept only an exact shell receipt"):
               src.index("    /** Only this user-confirmed code path")]
mode = src[src.index("    /** Only the exact native MCP status fields"):
           src.index("    /** Report only observed MCP data.")]
live = src[src.index("    /** Real MCP Client properties"):
           src.index("    /** Only actual native JSON booleans")]

for name in ("checkedLocalPlayReceipt", "buildPlaySmokeSummary"):
    assert name in receipts
assert "callMcp(" not in receipts
assert "executeApproved3dCode(" not in receipts
assert "requestModelWithFreeFallback(" not in receipts

harness = r"""
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PlaySmokeHarness {
    private static final int MAX_PLAY_LOG_CHARS = 1800;
%s
%s
%s
    private static void yes(boolean check) {
        if (!check) throw new AssertionError("Local Play smoke contract failed");
    }
    private static void bad(ThrowingAction action) throws Exception {
        try { action.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("MCP receipt accepted despite mismatch");
    }
    interface ThrowingAction { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        String play = "- Current Studio Mode: Play\n" +
                "- Available DataModels: Edit, Client, Server\n" +
                "- Focused DataModel in the viewport: Client";
        String edit = "- Current Studio Mode: Edit\n" +
                "- Available DataModels: Edit\n" +
                "- Focused DataModel in the viewport: Edit";
        String starting = "- Current Studio Mode: Starting\n" +
                "- Available DataModels: Edit\n" +
                "- Focused DataModel in the viewport: Edit";
        yes("Game Started".equals(checkedLocalPlayReceipt("Game Started", true)));
        yes("Game Stopped".equals(checkedLocalPlayReceipt("Game Stopped", false)));
        bad(() -> checkedLocalPlayReceipt("Game Stopped", true));
        bad(() -> checkedLocalPlayReceipt("Game Started", false));
        bad(() -> checkedLocalPlayReceipt("Game Started\nGame Stopped", true));
        bad(() -> checkedLocalPlayReceipt("Start play hasn't finished yet", true));
        bad(() -> checkedLocalPlayReceipt("Game Started!!", true));
        bad(() -> checkedLocalPlayReceipt(null, true));
        yes(checkedLocalPlayMode(edit).equals("EDIT"));
        yes(checkedLocalPlayMode(starting).equals("INICIANDO"));

        String all = buildPlaySmokeSummary(true, play, true, true, "no errors", true, "", false, null, false, null);
        yes(all.contains("Início autorizado com recibo: confirmado"));
        yes(all.contains("Client/Server durante Play: confirmados"));
        yes(all.contains("Retorno ao Edit: confirmado"));
        yes(all.contains("Árvore Server.Workspace: confirmada"));
        yes(all.contains("Árvore Client.Players: confirmada"));
        yes(buildPlaySmokeSummary(true, play, false, true, "log", true, "", false, null, false, null)
                .contains("INCONCLUSIVO"));
        yes(buildPlaySmokeSummary(true, play, true, false, "log", true, "", false, null, false, null)
                .contains("INCONCLUSIVO"));
        yes(all.contains("PASSOU somente nas verificações"));
        LiveToolGuiAudit ready = new LiveToolGuiAudit(
                "Players.Alex.Backpack.Sword", "Players.Alex.PlayerGui.Menu.Buy",
                true, true, true, true, true, true);
        yes(ready.complete());
        yes(buildPlaySmokeSummary(true, play, true, true, "log", true, "",
                true, new LocalPlayerScenario(true, true, true, true), true, ready).contains("PASSOU somente nas verificações"));
        yes(buildPlaySmokeSummary(true, play, true, true, "log", true, "",
                true, new LocalPlayerScenario(true, true, true, true), true, null).contains("INCONCLUSIVO"));
        LiveToolGuiAudit unready = new LiveToolGuiAudit(
                "Players.Alex.Backpack.Sword", "Players.Alex.PlayerGui.Menu.Buy",
                true, true, false, true, true, true);
        yes(!unready.complete() && !unready.toolReady());
        yes(buildPlaySmokeSummary(true, play, true, true, "log", true, "",
                true, new LocalPlayerScenario(true, true, true, true), true, unready).contains("INCONCLUSIVO"));
        yes(new LiveToolGuiAudit("Sword", "Gui", true, false, false,
                true, true, true).complete());
        yes(!new LiveToolGuiAudit("Sword", "Gui", null, false, false,
                true, true, true).complete());
        yes(!new LiveToolGuiAudit("Sword", "Gui", true, false, false,
                false, true, true).complete());
        LocalPlayerScenario complete = new LocalPlayerScenario(true, true, true, true);
        LocalPlayerScenario missingHumanoid = new LocalPlayerScenario(true, true, true, false);
        yes(buildPlaySmokeSummary(true, play, true, true, "log", true, "", true, complete, false, null)
                .contains("PASSOU somente nas verificações"));
        String missing = buildPlaySmokeSummary(true, play, true, true,
                "log", true, "", true, missingHumanoid, false, null);
        yes(missing.contains("Humanoid do personagem correspondente: não confirmado"));
        yes(missing.contains("INCONCLUSIVO"));
        yes(buildPlaySmokeSummary(true, play, true, true, "log", true, "", true, null, false, null)
                .contains("INCONCLUSIVO"));
        yes(all.contains("não verifica") || all.contains("Não verifica"));
        String partial = buildPlaySmokeSummary(true, edit, true, true, "log", true,
                "Client/Server ausentes", false, null, false, null);
        yes(partial.contains("INCONCLUSIVO"));
        yes(!partial.contains("PASSOU somente"));
        String failedStop = buildPlaySmokeSummary(true, play, true, true, "log", false, "", false, null, false, null);
        yes(failedStop.contains("Retorno ao Edit: NÃO CONFIRMADO"));
        yes(failedStop.contains("INCONCLUSIVO"));
        yes(buildPlaySmokeSummary(false, play, true, true, "log", true, "", false, null, false, null)
                .contains("INCONCLUSIVO"));
        yes(buildPlaySmokeSummary(true, play, true, true, null, true, "", false, null, false, null)
                .contains("Output durante o teste: indisponível"));
        String longText = "E".repeat(3000);
        String trimmed = buildPlaySmokeSummary(true, play, true, true, longText, true, "", false, null, false, null);
        yes(trimmed.contains("[Output limitado]"));
        yes(trimmed.length() < 2600);
        System.out.println("Local Play smoke production Java helpers: passed");
    }
}
""" % (mode, receipts, live)

with tempfile.TemporaryDirectory(prefix="rbx-play-smoke-") as tmp:
    file = Path(tmp) / "PlaySmokeHarness.java"
    file.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(file)], cwd=tmp, check=True)
    subprocess.run(["java", "-cp", tmp, "PlaySmokeHarness"], cwd=tmp, check=True)
