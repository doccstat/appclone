package com.lixingchi.appclone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Package inventory, not launcher visibility: stopped/disabled clones remain listed. */
final class Inventory {
    static final class Profile {
        final Clones.Clone clone;
        final long serial;
        final Set<String> packages;
        final Set<String> disabled;
        Profile(Clones.Clone clone, long serial, Set<String> packages, Set<String> disabled) {
            this.clone = clone;
            this.serial = serial;
            this.packages = packages;
            this.disabled = disabled;
        }
    }

    static final class Entry {
        final Apps.App app;
        final boolean system;
        android.graphics.drawable.Drawable icon;
        final List<Profile> profiles = new ArrayList<>();
        Entry(Apps.App app, boolean system) { this.app = app; this.system = system; }
    }

    final List<Profile> profiles;
    final List<Entry> apps;
    Inventory(List<Profile> profiles, List<Entry> apps) { this.profiles = profiles; this.apps = apps; }

    static Inventory load(Context context) {
        List<Clones.Clone> clones = Clones.list();
        List<Profile> profiles = new ArrayList<>();
        for (Clones.Clone clone : clones) {
            if (!clone.owned()) continue;
            profiles.add(new Profile(clone, serial(context, clone.id), Apps.packages(clone.id, "", Shell::root),
                    Apps.packages(clone.id, "-d ", Shell::root)));
        }
        PackageManager pm = context.getPackageManager();
        return new Inventory(profiles, group(profiles, pkg -> {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                String activity = launch == null || launch.getComponent() == null ? ""
                        : launch.getComponent().getClassName();
                Entry entry = new Entry(new Apps.App(String.valueOf(pm.getApplicationLabel(info)), pkg, activity),
                        (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                entry.icon = pm.getApplicationIcon(info);
                return entry;
            } catch (PackageManager.NameNotFoundException error) {
                throw new IllegalStateException("Cannot read metadata for " + pkg + "; refresh and retry.");
            }
        }));
    }

    static long serial(Context context, int id) {
        UserManager users = context.getSystemService(UserManager.class);
        android.os.UserHandle handle = CloneApps.userHandle(id);
        long serial = handle == null || users == null ? -1 : users.getSerialNumberForUser(handle);
        if (serial < 0) throw new IllegalStateException("Cannot identify profile " + id);
        return serial;
    }

    static List<Entry> group(List<Profile> profiles, Function<String, Entry> metadata) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Profile profile : profiles) for (String pkg : profile.packages) {
            Entry entry = entries.get(pkg);
            if (entry == null) { entry = metadata.apply(pkg); entries.put(pkg, entry); }
            entry.profiles.add(profile);
        }
        List<Entry> result = new ArrayList<>(entries.values());
        Collections.sort(result, (a, b) -> {
            int label = a.app.label.compareToIgnoreCase(b.app.label);
            return label != 0 ? label : a.app.pkg.compareTo(b.app.pkg);
        });
        return result;
    }

    Profile profile(int id) {
        for (Profile profile : profiles) if (profile.clone.id == id) return profile;
        return null;
    }

    Entry app(String pkg) {
        for (Entry entry : apps) if (entry.app.pkg.equals(pkg)) return entry;
        return null;
    }

    static String location(Profile profile) {
        return profile.clone.name + " · User " + profile.clone.id;
    }

    static String state(Profile profile, String pkg) {
        return (profile.disabled.contains(pkg) ? "Disabled · " : "")
                + (profile.clone.running ? "Profile running" : "Profile stopped");
    }
}
