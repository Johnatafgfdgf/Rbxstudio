#!/usr/bin/env python3
"""Validate native Rook touch *evidence*, not a simulated GUI click.

Compiles the actual pure Java formatter from StudioTouchDiagnostics.java.
Additionally verifies that the Android player forwards real MotionEvents and
that the AI Hub never sends start_stop_play to the unbridged external publisher.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / "android/src/com/rbxnative/androidport"
touch = (base / "StudioTouchDiagnostics.java").read_text()
rook = (base / "RookPlayerActivity.java").read_text()
hub = (base / "StudioAiHub.java").read_text()
patch = (root / "patches/rbx-native--android-port.patch").read_text()

production = touch[touch.index("    /** Pure Java format contract"):
                   touch.index("    /** Clear authorization")]
assert "static boolean consistentTouchTotals" in production
assert "static String formatTouchEvidence" in production

# No input interception, coordinates, image, content, or network.
assert "@Override public boolean dispatchTouchEvent(MotionEvent event)" in rook
assert "super.dispatchTouchEvent(event)" in rook
assert "StudioTouchDiagnostics.begin(this)" in rook
assert "StudioTouchDiagnostics.writeReport(this, touchProbeId" in rook
assert "InputDevice.SOURCE_TOUCHSCREEN" in rook
assert "touchProbeId == null || !ready" in rook
for forbidden in ("event.getX(", "event.getY(", "event.getRawX(",
                  "MotionEvent.obtain(", "injectInputEvent(", "HttpURLConnection",
                  "getPointerId(", "getXPrecision(", "dispatchGesture("):
    assert forbidden not in rook, forbidden

assert "public static boolean hasVerifiedLocalMcpPlayBridge() { return false; }" in touch
assert hub.count("if (!requireVerifiedAndroidMcpPlay()) return;") == 2
assert "if (StudioTouchDiagnostics.hasVerifiedLocalMcpPlayBridge()) return true;" in hub
assert "start_stop_play ainda segue a rota EXTERNA de publicação" in hub
assert 'StudioTouchDiagnostics.arm(this)' in hub
assert 'StudioTouchDiagnostics.readSummary(this)' in hub
assert 'StudioTouchDiagnostics.clear(this)' in hub
assert 'allowRookTouchProbe.setChecked(false)' in hub
assert 'if (!checked) {\n                StudioTouchDiagnostics.clear(this);' in hub
assert 'self.play_start_external(cx);' in patch

java = r"""
public final class TouchContract {
%s
    static void yes(boolean condition) {
        if (!condition) throw new AssertionError("Rook touch evidence contract failed");
    }
    public static void main(String[] args) {
        yes(consistentTouchTotals(0, 0, 0));
        yes(consistentTouchTotals(2, 1, 1));
        yes(consistentTouchTotals(20, 19, 0));
        yes(!consistentTouchTotals(0, 1, 0));
        yes(!consistentTouchTotals(1, 1, 1));
        yes(!consistentTouchTotals(-1, 0, 0));
        yes(!consistentTouchTotals(1, -1, 0));
        yes(!consistentTouchTotals(100001, 100001, 0));
        yes(!consistentTouchTotals(1, 1, 100000));
        String evidence = formatTouchEvidence(true, 2, 2, 0, true);
        yes(evidence.contains("EVIDÊNCIA: a rota Java"));
        yes(evidence.contains("ACTION_DOWN: 2"));
        yes(evidence.contains("ACTION_UP: 2"));
        yes(evidence.contains("Player encerrou: registrado"));
        yes(evidence.contains("Não comprova entrada no motor nativo"));
        yes(evidence.contains("GuiButton.Activated"));
        yes(evidence.contains("Sem coordenadas"));
        yes(!evidence.contains("clique confirmado"));
        String noTouch = formatTouchEvidence(true, 0, 0, 0, false);
        yes(noTouch.contains("INCONCLUSIVO"));
        yes(noTouch.contains("Player encerrou: não confirmado"));
        String notReady = formatTouchEvidence(false, 3, 2, 0, true);
        yes(notReady.contains("INCONCLUSIVO"));
        yes(formatTouchEvidence(true, 0, 1, 0, true).contains("inconsistente"));
        yes(formatTouchEvidence(true, 1, 1, 1, true).contains("inconsistente"));
        yes(!formatTouchEvidence(false, 1, 1, 0, true).contains("EVIDÊNCIA"));
        System.out.println("Real Android Rook touch evidence formatter and guards: passed");
    }
}
""" % production

with tempfile.TemporaryDirectory(prefix="rbx-rook-touch-") as folder:
    path = Path(folder) / "TouchContract.java"
    path.write_text(java)
    subprocess.run(["javac", "--release", "11", str(path)], check=True, cwd=folder)
    subprocess.run(["java", "-cp", folder, "TouchContract"], check=True, cwd=folder)
