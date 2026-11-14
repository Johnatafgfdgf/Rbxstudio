package com.rbxnative.androidport;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.Handler;
import android.graphics.Bitmap;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Lifecycle of the app's own Rook process. No external Roblox launch. */
public final class StudioPlayer {
    // Read and written only on the UI thread. Stop invalidates any pending capture.
    private static String pendingSession;
    private StudioPlayer() {}
    private static int pid(Activity activity) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null || manager.getRunningAppProcesses() == null) return 0;
        for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
            if (process.uid == Process.myUid() && process.processName.equals(activity.getPackageName() + ":rook")) return process.pid;
        }
        return 0;
    }
    public static void start(Activity activity, String session) {
        if (pid(activity) != 0) throw new IllegalStateException("Uma sessão de Play já está aberta");
        File directory = new File(session);
        int accent = (int) appearance(directory).optLong("accent", RbxGeometry.ACCENT);
        Bitmap menu = RbxBrand.menuImage(accent);
        try (FileOutputStream file = new FileOutputStream(new File(directory, "menu.png"))) {
            menu.compress(Bitmap.CompressFormat.PNG, 100, file);
        } catch (IOException ignored) { /* Play can start with the packaged menu image. */ }
        finally { menu.recycle(); }
        Intent intent = new Intent(activity, RookPlayerActivity.class).putExtra("session", session);
        String backend = activity.getIntent().getStringExtra("nativePlayerBackend");
        if (backend != null) intent.putExtra("nativePlayerBackend", backend);
        if (activity.getIntent().getBooleanExtra("nativePlayerProbe", false)) intent.putExtra("nativePlayerProbe", true);
        activity.runOnUiThread(() -> {
            pendingSession = session;
            Handler handler = new Handler(activity.getMainLooper());
            Runnable launch = () -> {
                if (!session.equals(pendingSession)) return;
                pendingSession = null;
                if (!activity.isFinishing() && !activity.isDestroyed()) activity.startActivity(intent);
            };
            View decor = activity.getWindow().getDecorView();
            SurfaceView surface = surfaceIn(decor);
            // Just enough pixels for a soft, project-specific backdrop.
            Bitmap preview = Bitmap.createBitmap(42, 24, Bitmap.Config.ARGB_8888);
            handler.postDelayed(launch, 180);
            try {
                PixelCopy.OnPixelCopyFinishedListener copied = result -> {
                    if (session.equals(pendingSession) && result == PixelCopy.SUCCESS) {
                        try (FileOutputStream file = new FileOutputStream(new File(session, "preview.png"))) {
                            preview.compress(Bitmap.CompressFormat.PNG, 100, file);
                        } catch (IOException ignored) { /* The player has a themed backdrop fallback. */ }
                    }
                    launch.run();
                };
                if (surface != null && surface.getHolder().getSurface().isValid())
                    PixelCopy.request(surface, preview, copied, handler);
                else
                    PixelCopy.request(activity.getWindow(), preview, copied, handler);
            } catch (IllegalArgumentException unavailable) {
                launch.run();
            }
        });
    }
    static JSONObject appearance(File session) {
        try {
            return new JSONObject(new String(Files.readAllBytes(new File(session, "appearance.json").toPath()),
                    StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static SurfaceView surfaceIn(View view) {
        if (view instanceof SurfaceView) return (SurfaceView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SurfaceView found = surfaceIn(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public static boolean alive(Activity activity) { return pid(activity) != 0; }
    public static void stop(Activity activity) {
        activity.runOnUiThread(() -> {
            pendingSession = null;
            int process = pid(activity);
            if (process != 0) Process.killProcess(process);
        });
    }
}
