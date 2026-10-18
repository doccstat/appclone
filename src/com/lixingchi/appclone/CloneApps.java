package com.lixingchi.appclone;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Clone profiles and their launcher apps, read without a shell. */
final class CloneApps {
    static final class Target {
        final int userId;
        final String name;
        final Apps.App app;

        Target(int userId, String name, Apps.App app) {
            this.userId = userId;
            this.name = name;
            this.app = app;
        }
    }

    private static final class Profile {
        final int id;
        final String name;
        final UserHandle handle;

        Profile(int id, String name, UserHandle handle) {
            this.id = id;
            this.name = name;
            this.handle = handle;
        }
    }

    /** Every app in every clone, so a widget can be assigned one entry. */
    static List<Target> assignments(Context context) {
        List<Target> targets = new ArrayList<>();
        for (Profile profile : profiles(context)) {
            for (LauncherActivityInfo info : activities(context, null, profile.handle)) {
                targets.add(new Target(profile.id, profile.name, app(info)));
            }
        }
        Collections.sort(targets, new Comparator<Target>() {
            @Override public int compare(Target a, Target b) {
                int byLabel = a.app.label.compareToIgnoreCase(b.app.label);
                return byLabel != 0 ? byLabel : a.name.compareToIgnoreCase(b.name);
            }
        });
        return targets;
    }

    static Bitmap icon(Context context, String pkg, int userId) {
        UserHandle user = userHandle(userId);
        if (user == null) return null;
        List<LauncherActivityInfo> list = activities(context, pkg, user);
        if (list.isEmpty()) return null;
        return bitmap(list.get(0).getBadgedIcon(0), iconSize(context));
    }

    private static Apps.App app(LauncherActivityInfo info) {
        return new Apps.App(String.valueOf(info.getLabel()),
                info.getComponentName().getPackageName(),
                info.getComponentName().getClassName());
    }

    private static List<LauncherActivityInfo> activities(Context context, String pkg, UserHandle user) {
        LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        try {
            return launcherApps.getActivityList(pkg, user);
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static List<Profile> profiles(Context context) {
        List<Profile> found = reflectedProfiles(context);
        return found.isEmpty() ? rootProfiles() : found;
    }

    /** Reads the user type from LauncherUserInfo; falls back to the shell if hidden. */
    private static List<Profile> reflectedProfiles(Context context) {
        LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<Profile> profiles = new ArrayList<>();
        for (UserHandle user : users.getUserProfiles()) {
            if (user.equals(Process.myUserHandle())) continue;
            Object info = launcherUserInfo(launcherApps, user);
            if (!ClonePolicy.CLONE_TYPE.equals(userType(info))) continue;
            profiles.add(new Profile(user.hashCode(), label(info, user), user));
        }
        return profiles;
    }

    private static List<Profile> rootProfiles() {
        List<Profile> profiles = new ArrayList<>();
        for (Clones.Clone clone : Clones.list()) {
            UserHandle handle = userHandle(clone.id);
            if (handle != null) profiles.add(new Profile(clone.id, clone.name, handle));
        }
        return profiles;
    }

    static UserHandle userHandle(int id) {
        try {
            return (UserHandle) UserHandle.class.getMethod("of", int.class).invoke(null, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object launcherUserInfo(LauncherApps launcherApps, UserHandle user) {
        try {
            return LauncherApps.class.getMethod("getLauncherUserInfo", UserHandle.class)
                    .invoke(launcherApps, user);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String userType(Object info) {
        return info == null ? null : string(info, "getUserType");
    }

    private static String label(Object info, UserHandle user) {
        String serial = string(info, "getSerialNumber");
        return serial == null ? "Clone " + user.hashCode() : "Clone " + serial;
    }

    private static String string(Object target, String method) {
        try {
            return String.valueOf(target.getClass().getMethod(method).invoke(target));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int iconSize(Context context) {
        int size = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        return size > 0 ? size : Ui.dp(context, 48);
    }

    private static Bitmap bitmap(Drawable drawable, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }

    private CloneApps() {}
}
