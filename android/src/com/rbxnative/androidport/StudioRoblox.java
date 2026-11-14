package com.rbxnative.androidport;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/** Sends an experience link to Roblox in its own Android task. */
public final class StudioRoblox {
    private static final String PACKAGE = "com.roblox.client";

    private StudioRoblox() {}

    // Bridge results: ready/opened = 0, missing = 1, no handler = 2, unavailable = 3.
    public static int openPlace(Activity activity, long place, boolean launch) {
        if (place <= 0 || activity.isFinishing() || activity.isDestroyed()) return 3;
        PackageManager packages = activity.getPackageManager();
        try {
            packages.getApplicationInfo(PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException absent) {
            return 1;
        }
        String[] links = {
            "roblox://placeId=" + place,
            "https://www.roblox.com/games/start?placeId=" + place
        };
        for (String link : links) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(packages) == null) continue;
            if (!launch) return 0;
            if (!activity.hasWindowFocus()) return 3;
            try {
                activity.startActivity(intent);
                return 0;
            } catch (ActivityNotFoundException removedOrChanged) {
                // The next format can still have an available handler.
            } catch (SecurityException unavailable) {
                return 3;
            }
        }
        return 2;
    }
}
