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
rook_patch = (root / "patches/rook--android-port.patch").read_text()
player = (root / "player/src/lib.rs").read_text()
platform = (root / "player/src/platform.rs").read_text()

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
assert 'case "touch-probe-enabled"' in rook
assert 'case "native-touch-counts"' in rook
assert "writeNativeCounts(this, touchProbeId" in rook
assert "StudioTouchDiagnostics.writeNativeCounts" in rook
assert "Rook.winit.WindowEvent.Touch" in touch
assert "nativeReported" in production
assert "guiActivated" in production
assert "Rook.touch.GuiButton.Activated" in touch
assert "guiConsent(context, id)" in touch
assert "noteGuiActivation(this, touchProbeId" in rook
assert 'case "touch-gui-enabled"' in rook
assert 'case "native-gui-activated"' in rook
assert "allowRookGuiProbe.setChecked(false)" in hub
assert "StudioTouchDiagnostics.arm(this, guiPermission" in hub
assert "rook_engine::touch_probe::install(Some" in player
assert "rook_engine::touch_probe::install(None)" in player
assert "crate::touch_probe::activated(&path, stats.queued, stats.attempted," in rook_patch
assert "stats.completed, stats.unresolved, stats.failed, stats.errors," in rook_patch
assert "stats.handler_dom_changes" in rook_patch
assert "stats.handlers_with_changes" in rook_patch
assert "journal_before" in rook_patch
assert "crate::signal_probe::finished(outcome, dom_changes)" in rook_patch
assert "activation.getInt(\"handlerDomChanges\")" in rook
assert "activation.getInt(\"handlersWithChanges\")" in rook
assert "\"handlerDomChanges\":handler_dom_changes" in platform
assert "handlersWithChanges > handlerDomChanges" in touch
assert "journal_changes" in rook_patch
assert "self.runtime.world().dom.journal().end()" in rook_patch
assert "self.runtime.world().dom.journal().is_enabled()" in rook_patch
assert "activation.getInt(\"journalChanges\")" in rook
assert "\"journalChanges\":journal_changes" in platform
assert "journalChanges > 1000" in touch
assert "guiWithJournalChanges" in touch
assert "rook_lua::signal_probe::capture" in rook_patch
assert "crate::signal_probe::queued(connection)" in rook_patch
assert "crate::signal_probe::attempted(connection)" in rook_patch
assert "crate::signal_probe::finished(outcome, dom_changes)" in rook_patch
assert "thread.status() == ThreadStatus::Finished" in rook_patch
assert "touch_activation_updates_visible_gui_state" in rook_patch
assert "touch_cancel_does_not_activate_or_change_gui_state" in rook_patch
assert "touch_activation_updates_client_workspace_after_yield" in rook_patch
assert "touch_cancel_never_resumes_delayed_gameplay_change" in rook_patch
assert 'probe.Transparency = 0.625' in rook_patch
assert 'task.wait(0.15)' in rook_patch
assert "TOQUE_CONFIRMADO" in rook_patch
assert "TouchPhase::Cancel" in rook_patch

assert "errors_before = observe.then(" in rook_patch
assert "if let Some(before) = errors_before" in rook_patch
assert "if seen >= 199" in player
assert "completed < 0 || unresolved < 0 || failed < 0" in touch
assert "pub struct DispatchStats" in rook_patch
assert 'call("native-gui-activated", &data.to_string())' in platform
assert "activation.getInt(\"attempted\")" in rook
assert 'self.fire(button, "Activated", (object, count));' in rook_patch
assert "Rook.winit.WindowEvent.Touch" in touch
assert "on_native_touch: observe_native_touch.then" in rook_patch
assert "WindowEvent::Touch(touch)" in rook_patch
assert "winit::event::TouchPhase::Started" in rook_patch
assert "winit::event::TouchPhase::Ended" in rook_patch
assert "winit::event::TouchPhase::Cancelled" in rook_patch
assert "if let Some(launch) = &mut self.launch { launch.window_event(event_loop, id, event); }" in rook_patch
assert "platform::report_native_touch_counts" in player
assert "platform::touch_probe_active()" in player
assert 'call("native-touch-counts"' in platform
assert 'call("touch-probe-enabled"' in platform
for forbidden in ("event.getX(", "event.getY(", "event.getRawX(",
                  "MotionEvent.obtain(", "injectInputEvent(", "HttpURLConnection",
                  "getPointerId(", "getXPrecision(", "dispatchGesture("):
    assert forbidden not in rook, forbidden

assert "public static boolean hasVerifiedLocalMcpPlayBridge() { return false; }" in touch
assert hub.count("if (!requireVerifiedAndroidMcpPlay()) return;") == 2
assert "if (StudioTouchDiagnostics.hasVerifiedLocalMcpPlayBridge()) return true;" in hub
assert "start_stop_play ainda segue a rota EXTERNA de publicação" in hub
assert 'StudioTouchDiagnostics.arm(this, guiPermission' in hub
assert 'allowRookGuiProbe.setChecked(false)' in hub
assert 'guiConsent(context, id)' in touch
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
        String nativeEvidence = formatTouchEvidence(true, 0, 0, 0, true, true, 2, 2, 0);
        yes(nativeEvidence.contains("EVIDÊNCIA: eventos de toque chegaram ao loop winit"));
        yes(nativeEvidence.contains("Touch Started: 2"));
        yes(nativeEvidence.contains("Touch Ended: 2"));
        yes(nativeEvidence.contains("GuiButton.Activated por toque: não confirmado"));
        yes(!nativeEvidence.contains("clique confirmado"));
        yes(formatTouchEvidence(true, 0, 0, 0, true, true, 0, 0, 0).contains("INCONCLUSIVO"));
        yes(formatTouchEvidence(true, 0, 0, 0, true, true, 0, 1, 0).contains("inconsistente"));
        yes(formatTouchEvidence(false, 0, 0, 0, false, true, 2, 2, 0).contains("INCONCLUSIVO"));
        String guiSignal = formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "Players.PlayerGui.Inventory.Open");
        yes(guiSignal.contains("SINAL REAL GuiButton.Activated"));
        yes(guiSignal.contains("Players.PlayerGui.Inventory.Open"));
        yes(guiSignal.contains("não comprovam conclusão do callback Luau"));
        yes(formatTouchEvidence(true, 0, 0, 0, false,
                true, 2, 2, 0, 1, "").contains("inconsistente"));
        yes(formatTouchEvidence(true, 0, 0, 0, false,
                true, 2, 2, 0, 201, "Button").contains("inconsistente"));
        String withHandler = formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "PlayerGui.Inventory.Open", 1, 1, 0);
        yes(withHandler.contains("Handlers Luau encaminhados: 1"));
        yes(withHandler.contains("Tentativas de iniciar handlers: 1"));
        yes(withHandler.contains("não comprovam conclusão do callback Luau"));
        yes(formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "Button", 1, 2, 0).contains("inconsistente"));
        String complete = formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "PlayerGui.Inventory.Open", 2, 2, 0,
                1, 1, 0);
        yes(complete.contains("Handlers concluídos na primeira retomada sem erro: 1"));
        yes(complete.contains("Handlers sem conclusão observada na primeira retomada: 1"));
        yes(complete.contains("Handlers não acompanhados fora do sinal (ex.: Deferred): 0"));
        String deferred = formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "PlayerGui.Inventory.Open", 2, 0, 0,
                0, 0, 0);
        yes(deferred.contains("Handlers não acompanhados fora do sinal (ex.: Deferred): 2"));
        yes(formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "PlayerGui.Inventory.Open", 2, 1, 0,
                2, 0, 0).contains("inconsistente"));
        String domChanges = formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 2, "PlayerGui.Shop.Buy", 2, 2, 0,
                2, 0, 0, 3, 1);
        yes(domChanges.contains("Registros de mudança no DOM durante Activated: 3"));
        yes(domChanges.contains("Sinais Activated com mudança DOM observada: 1"));
        yes(domChanges.contains("não prova que determinado script as causou"));
        yes(formatTouchEvidence(true, 0, 0, 0, true, true, 2, 2, 0,
                2, "Button", 2, 2, 0, 2, 0, 0, 0, 1).contains("inconsistente"));
        yes(formatTouchEvidence(true, 0, 0, 0, true, true, 2, 2, 0,
                2, "Button", 2, 2, 0, 2, 0, 0, 1, 3).contains("inconsistente"));
        String tracked = formatTouchEvidence(true, 0, 0, 0, true,
                true, 2, 2, 0, 1, "PlayerGui.Shop.Buy", 2, 2, 0,
                1, 1, 0, 4, 1, 3, 1);
        yes(tracked.contains("Registros DOM durante handlers Luau observados: 3"));
        yes(tracked.contains("Handlers com mutações DOM na primeira retomada: 1"));
        yes(formatTouchEvidence(true, 0, 0, 0, true, true, 2, 2, 0,
                1, "Button", 2, 1, 0, 1, 0, 0, 1, 1, 2, 2)
                .contains("inconsistente"));
        yes(formatTouchEvidence(true, 0, 0, 0, true, true, 2, 2, 0,
                1, "Button", 2, 2, 0, 1, 1, 0, 1, 1, 0, 1)
                .contains("inconsistente"));
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
