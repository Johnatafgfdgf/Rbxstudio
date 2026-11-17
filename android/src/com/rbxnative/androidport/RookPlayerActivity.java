package com.rbxnative.androidport;

import android.app.NativeActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONObject;

/** The original Rook player owns this NativeActivity and its native surface. */
public final class RookPlayerActivity extends NativeActivity {
    private PopupWindow progressPopup;
    private PlayLoadingView loading;
    private volatile String stage = "Preparando o Play local…";
    private volatile boolean ready;
    private String touchProbeId;
    private int touchDownCount, touchUpCount, touchCancelCount;
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) {
            com.rbxnative.androidport.StudioWindow.configure(this);
            getWindow().getDecorView().post(this::showControls);
        }
    }
    @Override protected void onCreate(Bundle state) {
        System.loadLibrary("rbx_android_player");
        if (getIntent().getBooleanExtra("nativePlayerProbe", false)) {
            try { android.system.Os.setenv("RBX_NATIVE_RENDER_PROBE", "1", true); }
            catch (android.system.ErrnoException error) { throw new IllegalStateException(error); }
        }
        String backend = getIntent().getStringExtra("nativePlayerBackend");
        if (backend != null) {
            if (!backend.equals("vulkan") && !backend.equals("gl"))
                throw new IllegalArgumentException("Unknown diagnostic player backend");
            try { android.system.Os.setenv("WGPU_BACKEND", backend, true); }
            catch (android.system.ErrnoException error) { throw new IllegalStateException(error); }
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        super.onCreate(state);
        touchProbeId = StudioTouchDiagnostics.begin(this);
        StudioWindow.configure(this);
        // The loading card owns its surface until the first game frame.
        // During Play the CoreScript menu owns Leave and the game owns touch.
        File session = new File(getIntent().getStringExtra("session"));
        JSONObject appearance = StudioPlayer.appearance(session);
        int accent = (int) appearance.optLong("accent", RbxGeometry.ACCENT);
        String title = appearance.optString("title", "Projeto local");
        loading = new PlayLoadingView(this, session, title, accent, this::finish);
        progressPopup = popup(loading, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        getWindow().getDecorView().post(this::showControls);
    }
    private PopupWindow popup(View content, int width, int height) {
        PopupWindow popup = new PopupWindow(content, width, height, false);
        popup.setTouchable(true);
        popup.setTouchModal(false);
        popup.setOutsideTouchable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAnimationStyle(0);
        return popup;
    }
    private void showControls() {
        if (isFinishing() || isDestroyed() || progressPopup == null) return;
        View decor = getWindow().getDecorView();
        if (decor.getWindowToken() == null) return;
        if (ready) {
            progressPopup.dismiss();
        } else {
            loading.stage(stage);
            if (!progressPopup.isShowing())
                progressPopup.showAtLocation(decor, Gravity.TOP | Gravity.START, 0, 0);
        }
    }

    /** Called by the Rust service bridge; credential bytes never enter a log. */
    public String service(String operation, String value) throws Exception {
        switch (operation) {
            case "resources": return StudioServices.resourcePaths(this);
            case "session": return getIntent().getStringExtra("session");
            case "asset-key": {
                String packed = StudioServices.credential(this, "read",
                    "https://apis.roblox.com/rbx-native", "", "");
                return packed == null ? "" : new String(StudioServices.unhex(
                    packed.substring(packed.indexOf(':') + 1)), StandardCharsets.UTF_8);
            }
            case "loading-stage": stage = value; runOnUiThread(this::showControls); return "";
            case "ready":
                ready = true;
                runOnUiThread(() -> {
                    StudioTouchDiagnostics.writeReport(this, touchProbeId, ready,
                            touchDownCount, touchUpCount, touchCancelCount, false);
                    showControls();
                });
                return "";
            case "finish": runOnUiThread(this::finish); return "";
            case "clipboard-read": {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
                return clip == null || clip.getItemCount() == 0 ? "" : clip.getItemAt(0).coerceToText(this).toString();
            }
            case "clipboard-write": {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
                clipboard.setPrimaryClip(ClipData.newPlainText("", value)); return "";
            }
            case "credential": {
                JSONObject request = new JSONObject(value);
                String packed = StudioServices.credential(this, request.getString("operation"),
                    "Rook:" + request.getString("name"), request.getString("name"),
                    hex(request.getString("secret").getBytes(StandardCharsets.UTF_8)));
                if (packed == null) return "";
                return new String(unhex(packed.substring(packed.indexOf(':') + 1)), StandardCharsets.UTF_8);
            }
            default: throw new IllegalArgumentException("Unknown player service");
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append("0123456789abcdef".charAt((value & 255) >> 4)).append("0123456789abcdef".charAt(value & 15));
        return out.toString();
    }
    private static byte[] unhex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    /**
     * Real touchscreen MotionEvents received by the Java Activity. Never
     * consume, inject, replay or transform native input. NativeActivity may
     * deliver some/all game input through AInputQueue instead; zero events
     * is inconclusive and this is NOT a GuiButton.Activated verification.
     */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        boolean consumed = super.dispatchTouchEvent(event);
        if (touchProbeId == null || !ready || event == null ||
                (event.getSource() & InputDevice.SOURCE_TOUCHSCREEN) !=
                        InputDevice.SOURCE_TOUCHSCREEN) return consumed;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (touchDownCount < 100000) touchDownCount++;
                break;
            case MotionEvent.ACTION_UP:
                if (touchUpCount < 100000) touchUpCount++;
                break;
            case MotionEvent.ACTION_CANCEL:
                if (touchCancelCount < 100000) touchCancelCount++;
                break;
            default:
                return consumed;
        }
        StudioTouchDiagnostics.writeReport(this, touchProbeId, true,
                touchDownCount, touchUpCount, touchCancelCount, false);
        return consumed;
    }

    @Override public void onBackPressed() { finish(); }
    @Override protected void onDestroy() {
        StudioTouchDiagnostics.writeReport(this, touchProbeId, ready,
                touchDownCount, touchUpCount, touchCancelCount, true);
        if (progressPopup != null) progressPopup.dismiss();
        try {
            File session = new File(getIntent().getStringExtra("session"));
            File state = new File(session, "state.json");
            JSONObject current = state.isFile() ? new JSONObject(new String(Files.readAllBytes(state.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
            if (!current.optString("status").equals("failed")) {
                File temp = new File(session, "state.java");
                Files.write(temp.toPath(), new JSONObject().put("status", "stopped").toString().getBytes(StandardCharsets.UTF_8));
                Files.move(temp.toPath(), state.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) { /* Editor also detects process death. */ }
        super.onDestroy();
        // A fresh process gives each Play a fresh winit/NativeActivity context.
        new Handler(Looper.getMainLooper()).postDelayed(() -> Process.killProcess(Process.myPid()), 200);
    }
}
