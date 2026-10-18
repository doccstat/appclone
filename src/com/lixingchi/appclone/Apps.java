package com.lixingchi.appclone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Installed launcher apps plus the per-clone app operations. */
final class Apps {
    static final class App {
        final String label;
        final String pkg;
        final String activity;

        App(String label, String pkg, String activity) {
            this.label = label;
            this.pkg = pkg;
            this.activity = activity;
        }
    }

    static List<App> launcherApps(Context context, String excludePackage) {
        PackageManager manager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : manager.queryIntentActivities(intent, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(excludePackage) || !seen.add(pkg)) continue;
            apps.add(new App(String.valueOf(info.loadLabel(manager)), pkg, info.activityInfo.name));
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App a, App b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    static Set<String> installed(int userId) {
        Set<String> packages = new HashSet<>();
        for (String line : Shell.root("pm list packages --user " + userId).out.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package:")) {
                packages.add(trimmed.substring("package:".length()));
            }
        }
        return packages;
    }

    static Shell.Result install(int userId, String pkg) {
        return Shell.root("pm install-existing --user " + userId + " " + pkg);
    }

    static Shell.Result uninstall(int userId, String pkg) {
        return Shell.root("pm uninstall --user " + userId + " " + pkg);
    }

    static Shell.Result launch(int userId, App app) {
        return Shell.root("am start --user " + userId
                + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "
                + app.pkg + "/" + app.activity);
    }

    private Apps() {}
}
