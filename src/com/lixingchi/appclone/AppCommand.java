package com.lixingchi.appclone;

import android.app.AppOpsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One short-lived root process for guarded app launch or background-policy access. */
public final class AppCommand {
    static String command(String action, int user, long serial, String pkg, String... extra) {
        if (user <= 0 || serial < -1 || (!action.equals("launch") && serial < 0) || !Apps.validPackage(pkg))
            throw new IllegalArgumentException("Invalid clone app identity.");
        StringBuilder cmd = new StringBuilder(ProfileStart.entry(AppCommand.class));
        for (String arg : new String[] {action, Integer.toString(user), Long.toString(serial), pkg})
            cmd.append(' ').append(Clones.quote(arg));
        for (String arg : extra) cmd.append(' ').append(Clones.quote(arg));
        return cmd.toString();
    }

    static final class Target {
        final int uid;
        final long installed;
        final boolean enabled, shared;
        Target(int uid, long installed, boolean enabled, boolean shared) {
            this.uid = uid; this.installed = installed; this.enabled = enabled; this.shared = shared;
        }
    }

    interface Device extends ProfileStart.Device {
        Target app(int user, String pkg) throws Exception;
        Shell.Result shell(String command);
        BackgroundPolicy.State background(int user, String pkg) throws Exception;
        void background(int uid, String pkg, int mode) throws Exception;
    }

    private static long verify(int user, long serial, String pkg, Device device) throws Exception {
        if (user <= 0 || serial < -1 || !Apps.validPackage(pkg)) throw new IllegalArgumentException("Invalid clone app identity.");
        long actual = device.verify(user);
        if (serial != -1 && actual != serial) throw new IllegalStateException("Profile identity changed. Refresh before retrying.");
        return actual;
    }

    static Shell.Result launch(int user, long serial, String pkg, String activity, Device device) throws Exception {
        serial = verify(user, serial, pkg, device);
        Target before = device.app(user, pkg);
        if (!before.enabled) throw new IllegalStateException("App is disabled. Enable it explicitly before opening.");
        ProfileStart.start(user, device, 20_000, serial);
        String component = pkg + "/" + activity;
        if (activity.isEmpty()) {
            Shell.Result resolved = device.shell("cmd package resolve-activity --brief --user " + user
                    + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p " + Clones.quote(pkg));
            if (!resolved.ok()) return resolved;
            component = null;
            for (String line : resolved.out.split("\n")) if (line.trim().startsWith(pkg + "/")) component = line.trim();
            if (component == null) return new Shell.Result(-1, "No enabled launcher activity in this profile.");
        }
        verify(user, serial, pkg, device);
        Target now = device.app(user, pkg);
        if (!now.enabled || now.uid != before.uid || now.installed != before.installed)
            throw new IllegalStateException("App changed while opening. Refresh before retrying.");
        if (!device.unlocked(user)) throw new IllegalStateException("Profile is no longer unlocked. Refresh before retrying.");
        Shell.Result result = device.shell("cmd activity start-activity --user " + user
                + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n " + Clones.quote(component));
        return result.ok() && java.util.regex.Pattern.compile("(?m)^Error(?: type \\d+|:)").matcher(result.out).find()
                ? new Shell.Result(-1, result.out) : result;
    }

    static BackgroundPolicy.State read(int user, long serial, String pkg, Device device) throws Exception {
        if (serial < 0) throw new IllegalArgumentException("A profile serial is required.");
        verify(user, serial, pkg, device);
        Target target = device.app(user, pkg);
        if (target.shared || target.uid % 100000 < android.os.Process.FIRST_APPLICATION_UID)
            throw new IllegalStateException("Shared/system UID apps cannot be restricted independently.");
        BackgroundPolicy.State state = device.background(user, pkg);
        if (state.uid != target.uid || state.installed != target.installed)
            throw new IllegalStateException("App identity changed. Refresh before retrying.");
        verify(user, serial, pkg, device);
        return state;
    }

    static BackgroundPolicy.State set(int user, long serial, String pkg, BackgroundPolicy.State expected,
                                      int mode, Device device) throws Exception {
        BackgroundPolicy.State current = read(user, serial, pkg, device);
        if (!current.equals(expected)) throw new IllegalStateException("Background setting changed. Reopen its controls.");
        if (current.uidMode != -1) throw new IllegalStateException("A UID-wide override is controlled elsewhere; nothing changed.");
        BackgroundPolicy.State desired = current.withMode(mode);
        device.background(current.uid, pkg, mode);
        if (!read(user, serial, pkg, device).equals(desired))
            throw new IllegalStateException("Could not verify background setting. The saved previous mode is retained.");
        return desired;
    }

    public static void main(String[] args) {
        try {
            if (android.os.Process.myUid() != 0) throw new IllegalStateException("Root is required.");
            if (args.length < 4) throw new IllegalArgumentException("Expected action, user, serial and package.");
            int user = Integer.parseInt(args[1]);
            long serial = Long.parseLong(args[2]);
            Device device = new AndroidDevice();
            if (args[0].equals("launch") && args.length == 5) {
                Shell.Result result = launch(user, serial, args[3], args[4], device);
                System.out.println(result.out);
                System.exit(result.ok() ? 0 : 1);
            } else if (args[0].equals("background-get") && args.length == 4) {
                System.out.println(read(user, serial, args[3], device).encode());
            } else if (args[0].equals("background-set") && args.length == 6) {
                System.out.println(set(user, serial, args[3], BackgroundPolicy.State.parse(args[4]),
                        Integer.parseInt(args[5]), device).encode());
            } else throw new IllegalArgumentException("Invalid app command.");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            System.err.println("Clone app command failed: " + cause);
            System.exit(1);
        }
    }

    private static final class AndroidDevice extends ProfileStart.AndroidDevice implements Device {
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(27);
        private final Class<?> packages = Class.forName("android.content.pm.IPackageManager");
        private final Object manager = ProfileStart.service("package", packages.getName());
        AndroidDevice() throws Exception { }

        private PackageInfo info(int user, String pkg) throws Exception {
            PackageInfo info = (PackageInfo) packages.getMethod("getPackageInfo", String.class, long.class, int.class)
                    .invoke(manager, pkg, (long) PackageManager.MATCH_DISABLED_COMPONENTS, user);
            if (info == null || info.applicationInfo == null || !pkg.equals(info.packageName)
                    || !pkg.equals(info.applicationInfo.packageName) || info.applicationInfo.uid / 100000 != user
                    || (info.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) == 0)
                throw new IllegalStateException("App is not installed in this profile.");
            return info;
        }

        @Override public Target app(int user, String pkg) throws Exception {
            PackageInfo info = info(user, pkg);
            String[] names = (String[]) packages.getMethod("getPackagesForUid", int.class).invoke(manager, info.applicationInfo.uid);
            return new Target(info.applicationInfo.uid, info.firstInstallTime, info.applicationInfo.enabled,
                    info.sharedUserId != null || names == null || names.length != 1);
        }

        @Override public Shell.Result shell(String command) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return new Shell.Result(-1, "Clone app command timed out. Refresh before retrying.");
            return Shell.run(new ProcessBuilder("/system/bin/sh", "-c", command),
                    Math.min(TimeUnit.SECONDS.toNanos(8), remaining), TimeUnit.NANOSECONDS);
        }

        private int op() throws Exception {
            return (Integer) AppOpsManager.class.getMethod("strOpToOp", String.class)
                    .invoke(null, "android:run_any_in_background");
        }

        @Override public BackgroundPolicy.State background(int user, String pkg) throws Exception {
            PackageInfo info = info(user, pkg);
            Class<?> api = Class.forName("com.android.internal.app.IAppOpsService");
            Object service = ProfileStart.service("appops", api.getName());
            int op = op(), uid = info.applicationInfo.uid;
            int fallback = (Integer) AppOpsManager.class.getMethod("opToDefaultMode", int.class).invoke(null, op);
            Object packageOps = api.getMethod("getOpsForPackage", int.class, String.class, int[].class)
                    .invoke(service, uid, pkg, new int[] {op});
            Object uidOps = api.getMethod("getUidOps", int.class, int[].class).invoke(service, uid, new int[] {op});
            return new BackgroundPolicy.State(uid, info.firstInstallTime, mode(packageOps, op, fallback), mode(uidOps, op, -1));
        }

        private int mode(Object packages, int op, int fallback) throws Exception {
            if (packages != null) for (Object pkg : (List<?>) packages) {
                for (Object entry : (List<?>) pkg.getClass().getMethod("getOps").invoke(pkg)) {
                    if ((Integer) entry.getClass().getMethod("getOp").invoke(entry) == op)
                        return (Integer) entry.getClass().getMethod("getMode").invoke(entry);
                }
            }
            return fallback;
        }

        @Override public void background(int uid, String pkg, int mode) throws Exception {
            Class<?> api = Class.forName("com.android.internal.app.IAppOpsService");
            api.getMethod("setMode", int.class, int.class, String.class, int.class)
                    .invoke(ProfileStart.service("appops", api.getName()), op(), uid, pkg, mode);
        }
    }
}
