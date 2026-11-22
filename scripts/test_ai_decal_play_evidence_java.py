#!/usr/bin/env python3
"""Execute production read-only Decal Play Output evaluator and consent wiring.

This tests actual Java classification only; no emulator, remote Roblox account,
or visual proof is implied by a green test.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
placement = root / "android/src/com/rbxnative/androidport/StudioRobloxDecalPlacement.java"
evidence = root / "android/src/com/rbxnative/androidport/StudioDecalPlayEvidence.java"
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
java_src = evidence.read_text()
for required in (
    'allowDecalPlayEvidence.setChecked(false)',
    'inspectDecalPlay.setOnClickListener(v -> inspectPlacedDecalPlay())',
    'private void inspectPlacedDecalPlay()',
    'StudioDecalPlayEvidence.inspect(',
    'readOnlyMcp(paired, "get_studio_state", new JSONObject())',
    'readOnlyMcp(paired, "get_console_output", new JSONObject())',
    'lastVerifiedPlacedDecal = confirmed ? p : null',
    'if (probingDecalPlay)',
    'probingDecalPlay = false;',
    'if (epoch != requestEpoch || isFinishing() || !probingDecalPlay',
):
    assert required in hub, required

# Nothing in the opt-in diagnostic can ever execute an editor write or
# invoke AI. Extract production method from fixed Java boundaries.
start = hub.index("    private void inspectPlacedDecalPlay() {")
end = hub.index("    private void requestClose() {", start)
section = hub[start:end]
for disallowed in (
    'executeApproved3dCode(', 'callMcp(', 'multi_edit', 'execute_luau',
    'requestModelWithFreeFallback(', 'StudioRobloxImageUpload.uploadDecal(',
    'StudioPlayFrameEvidence.arm(', 'StudioPlayFrameEvidence.takeOnce(',
):
    assert disallowed not in section, disallowed

for disallowed in (
    'HttpURLConnection', 'new URL(', 'java.net.URL', 'execute_luau',
    'callMcp(', 'sendRequest(', 'getOutputStream(',
):
    assert disallowed not in java_src, disallowed

java = r"""package com.rbxnative.androidport;

import java.io.IOException;

public final class DecalPlayEvidenceContract {
    private static void yes(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    private static void reject(String mode, String log, String asset, String path) {
        try {
            StudioDecalPlayEvidence.inspect(mode, log, asset, path);
            throw new AssertionError("invalid evidence accepted");
        } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        String id = "123456";
        String path = "Workspace.Casa.Parede.Vento";
        String play = "PLAY_LOCAL_CONFIRMADO";
        var noPlay = StudioDecalPlayEvidence.inspect(
            "EDIT", "Failed to load rbxassetid://123456", id, path);
        yes(noPlay.level == StudioDecalPlayEvidence.Level.NOT_PLAY,
            "Edit cannot prove Play failure");
        yes(noPlay.matchingLines == 0, "Edit output must not be analyzed");
        yes(noPlay.summary.contains("não confirmado"), "honest missing Play state");
        var silent = StudioDecalPlayEvidence.inspect(play, "", id, path);
        yes(silent.level == StudioDecalPlayEvidence.Level.NOT_OBSERVED,
            "no console output is not success");
        yes(silent.possibleFailures == 0, "no evidence of failure");
        yes(silent.summary.contains("NÃO confirma"), "explicit uncertainty");

        var fail = StudioDecalPlayEvidence.inspect(play,
            "Failed to load rbxassetid://123456: permission denied\n"
            + "Some other message for 999999\n"
            + "Failed to fetch assetId=123456 because moderation rejected",
            id, path);
        yes(fail.level == StudioDecalPlayEvidence.Level.POSSIBLE_LOAD_FAILURE,
            "identified asset load error");
        yes(fail.matchingLines == 2, "exact ID matching");
        yes(fail.possibleFailures == 2, "two suspect failures");
        yes(fail.summary.contains("possível falha:"), "observed log excerpt");
        yes(!fail.summary.contains("Some other message"), "other asset omitted");

        var goodLog = StudioDecalPlayEvidence.inspect(play,
            "assetId=123456 moderation approved; content available",
            id, path);
        yes(goodLog.possibleFailures == 0, "approved moderation is not failure");
        yes(goodLog.summary.contains("NÃO confirma o carregamento visual"),
            "textual logs are not visual proof");

        var falseHit = StudioDecalPlayEvidence.inspect(play,
            "Failed to load 9123456\n"
            + "Failed to load 1234567\n"
            + "Failed to load 91234567\n"
            + "Failed to load 12345\n",
            id, path);
        yes(falseHit.matchingLines == 0 && falseHit.possibleFailures == 0,
            "do not blame other asset IDs");

        yes(StudioDecalPlayEvidence.containsExactId("asset: 123456/texture", id),
            "non-numeric boundaries are accepted");
        yes(!StudioDecalPlayEvidence.containsExactId("asset: 0123456", id),
            "numeric prefix rejected");
        yes(!StudioDecalPlayEvidence.containsExactId("asset: 1234560", id),
            "numeric suffix rejected");
        yes(!StudioDecalPlayEvidence.containsExactId(null, id),
            "null text safe");

        StringBuilder large = new StringBuilder();
        for (int i=0; i<200; i++)
            large.append("Failed to load asset 123456 on 19 systems\n");
        var limited = StudioDecalPlayEvidence.inspect(play, large.toString(), id, path);
        yes(limited.matchingLines == 200, "all bounded lines counted");
        yes(limited.possibleFailures == 200, "all matching errors counted");
        yes(limited.summary.length() < 1600, "only bounded excerpts shown");
        reject(play, "a".repeat(StudioDecalPlayEvidence.MAX_LOG_CHARS + 1),
            id, path);
        reject(play, null, id, path);
        reject(play, "", "00123456", path);
        reject(play, "", "123456", "ServerStorage.A.Vento");
        reject(play, "", "123456", "Workspace.Casa.Parede.");
        reject(play, "", "123456", "Workspace.Casa.Parede;require(3)");
        yes(StudioDecalPlayEvidence.MAX_MATCHING_LINES == 4,
            "small number of displayed log excerpts");
        System.out.println("Real Java Decal Play Output evidence classifier: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-decal-play-") as folder:
    harness = Path(folder) / "DecalPlayEvidenceContract.java"
    harness.write_text(java)
    subprocess.run(["javac", "--release", "11", "-d", folder,
                    str(placement), str(evidence), str(harness)], check=True)
    subprocess.run(["java", "-cp", folder,
                    "com.rbxnative.androidport.DecalPlayEvidenceContract"],
                   check=True)
