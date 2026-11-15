package dev.gpui.mobile;

import android.app.NativeActivity;
import android.content.Intent;
import android.media.AudioManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;

/** Native host for the original GPUI editor; no web rendering layer. */
public final class GpuiActivity extends NativeActivity {
    private GpuiImeView editorIme;
    private int reportedImeBottom = -1;

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
        View decor = getWindow().getDecorView();
        decor.setOnApplyWindowInsetsListener((view, insets) -> {
            reportImeBounds(insets);
            return view.onApplyWindowInsets(insets);
        });
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() ->
                reportImeBounds(decor.getRootWindowInsets()));
        if (Build.VERSION.SDK_INT >= 30) {
            decor.setWindowInsetsAnimationCallback(new android.view.WindowInsetsAnimation.Callback(
                    android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                @Override public WindowInsets onProgress(WindowInsets insets,
                        java.util.List<android.view.WindowInsetsAnimation> running) {
                    reportImeBounds(insets);
                    return insets;
                }
            });
        }
    }

    private void reportImeBounds(WindowInsets insets) {
        int bottom = 0;
        if (editorIme != null && editorIme.isEditing()) {
            View decor = getWindow().getDecorView();
            if (Build.VERSION.SDK_INT >= 30 && insets != null) {
                int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                if (ime > 0) {
                    int windowHeight = getWindowManager().getCurrentWindowMetrics().getBounds().height();
                    bottom = Math.max(1, windowHeight - ime);
                }
            } else if (Build.VERSION.SDK_INT < 30) {
                Rect visible = new Rect();
                decor.getWindowVisibleDisplayFrame(visible);
                int[] origin = new int[2];
                decor.getLocationOnScreen(origin);
                int available = visible.bottom - origin[1];
                float minimumKeyboard = 80 * getResources().getDisplayMetrics().density;
                if (available > 0 && decor.getHeight() - available > minimumKeyboard) {
                    bottom = available;
                }
            }
        }
        if (bottom != reportedImeBottom) {
            reportedImeBottom = bottom;
            nativeEditorImeBounds(bottom);
        }
    }

    // Called by the native GPUI thread. View and IME operations belong to the
    // Activity thread, including focus changes and restarting a connection.
    public void setEditorIme(long session, boolean show, boolean multiline) {
        runOnUiThread(() -> {
            if (editorIme != null && !isFinishing() && !isDestroyed()) {
                editorIme.setSession(session, show, multiline);
                View decor = getWindow().getDecorView();
                reportImeBounds(decor.getRootWindowInsets());
                decor.requestApplyInsets();
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
    private static native void nativeEditorImeBounds(int visibleBottom);
}
