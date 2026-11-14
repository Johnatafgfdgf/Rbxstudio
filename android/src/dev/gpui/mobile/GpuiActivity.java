package dev.gpui.mobile;

import android.app.NativeActivity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;

/** Native host for the original GPUI editor; no web rendering layer. */
public final class GpuiActivity extends NativeActivity {
    private GpuiImeView editorIme;

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) return;
        if (editorIme != null && editorIme.isEditing()) {
            com.rbxnative.androidport.StudioWindow.configureWhileTyping(this);
        } else {
            com.rbxnative.androidport.StudioWindow.configure(this);
        }
    }
    @Override protected void onCreate(Bundle state) {
        String backend = getIntent().getStringExtra("nativeViewerBackend");
        if (backend == null && getIntent().getBooleanExtra("nativeVulkanProbe", false)) backend = "vulkan";
        // Test-only override of the original offscreen viewer. GPUI still
        // creates its own context; ordinary launcher intents change nothing.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && ("vulkan".equals(backend) || "gl".equals(backend))) {
            try { android.system.Os.setenv("WGPU_BACKEND", backend, true); }
            catch (android.system.ErrnoException error) { throw new IllegalStateException(error); }
        }
        System.loadLibrary("rbx_android_studio");
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        super.onCreate(state);
        com.rbxnative.androidport.StudioWindow.configure(this);
        editorIme = new GpuiImeView(this);
        addContentView(editorIme, new android.widget.FrameLayout.LayoutParams(1, 1));
    }

    // Called by the native GPUI thread. View and IME operations belong to the
    // Activity thread, including focus changes and restarting a connection.
    public void setEditorIme(long session, boolean show, boolean multiline) {
        runOnUiThread(() -> {
            if (editorIme != null && !isFinishing() && !isDestroyed()) {
                editorIme.setSession(session, show, multiline);
            }
        });
    }

    public void updateEditorImeSelection(long session, int start, int end, int markedStart, int markedEnd) {
        runOnUiThread(() -> {
            if (editorIme != null && !isDestroyed()) {
                editorIme.updateSelection(session, start, end, markedStart, markedEnd);
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getData() != null) nativeOnDeepLink(intent.getData().toString());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                int direction = code == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                        : code == KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
                if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public static native boolean nativeIsInitialized();
    private static native void nativeOnDeepLink(String url);
}
