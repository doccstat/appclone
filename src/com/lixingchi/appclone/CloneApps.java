package com.lixingchi.appclone;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import java.util.List;

/** Shortcut identity and optional clone-badged icons. Inventory is package-based. */
final class CloneApps {
    static final class Target {
        final int userId;
        final String name;
        final Apps.App app;
        final long serial;

        Target(int userId, String name, Apps.App app, long serial) {
            this.userId = userId;
            this.name = name;
            this.app = app;
            this.serial = serial;
        }
    }

    static Bitmap icon(Context context, String pkg, int userId) {
        UserHandle user = userHandle(userId);
        if (user == null) return null;
        LauncherApps launcher = context.getSystemService(LauncherApps.class);
        if (launcher == null) return null;
        try {
            List<LauncherActivityInfo> activities = launcher.getActivityList(pkg, user);
            if (activities.isEmpty()) return null;
            int size = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Drawable icon = activities.get(0).getBadgedIcon(0);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(bitmap));
            return bitmap;
        } catch (RuntimeException error) { return null; }
    }

    static UserHandle userHandle(int id) {
        // Android allocates 100000 UIDs per user; avoid hidden UserHandle.of().
        if (id < 0 || id > Integer.MAX_VALUE / 100000) return null;
        return UserHandle.getUserHandleForUid(id * 100000);
    }

    private CloneApps() {}
}
