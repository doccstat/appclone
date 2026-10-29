package com.lixingchi.appclone;

import android.os.IBinder;
import java.util.concurrent.TimeUnit;

/** Short-lived root entry point loaded from this APK; credentials remain inside Android. */
public final class ProfileStart {
    static String apkPath;

    static String command(int user) {
        if (user <= 0 || apkPath == null || apkPath.isEmpty())
            throw new IllegalStateException("Clone startup is unavailable.");
        return "CLASSPATH=" + Clones.quote(apkPath)
                + " app_process /system/bin com.lixingchi.appclone.ProfileStart " + user;
    }

    interface Device {
        long verify(int user) throws Exception;
        boolean unlocked(int user) throws Exception;
        boolean start(int user) throws Exception;
        boolean unlockCached(int user) throws Exception;
    }

    static long identity(int requested, int actual, long serial, String type, int parent, boolean ephemeral) {
        if (requested <= 0 || requested != actual || serial < 0
                || !"android.os.usertype.profile.CLONE".equals(type) || parent != 0 || ephemeral)
            throw new IllegalStateException("Not a non-ephemeral clone of user 0: " + requested);
        return serial;
    }

    static void start(int user, Device device, long timeoutMillis) throws Exception {
        if (user <= 0) throw new IllegalArgumentException("Invalid clone profile.");
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long serial = device.verify(user);
        if (device.unlocked(user)) return;
        if (!device.unlocked(0)) throw new IllegalStateException("Unlock the main user before starting a clone.");
        unchanged(device, user, serial);
        if (!device.start(user)) throw new IllegalStateException("Android could not start profile " + user + ".");
        unchanged(device, user, serial);
        // Starting a secured profile does not unlock its CE key, even with an unlocked parent.
        boolean accepted = device.unlocked(user) || device.unlockCached(user);
        while (!device.unlocked(user)) {
            if (System.nanoTime() >= deadline) throw new IllegalStateException(accepted
                    ? "Profile " + user + " did not become ready. Refresh and retry."
                    : "Profile " + user + " is locked. Lock and unlock the main user with its PIN, pattern or password, then retry.");
            Thread.sleep(100);
        }
        unchanged(device, user, serial);
    }

    private static void unchanged(Device device, int user, long serial) throws Exception {
        if (device.verify(user) != serial) throw new IllegalStateException("Profile identity changed. Refresh before retrying.");
    }

    public static void main(String[] args) {
        try {
            if (android.os.Process.myUid() != 0) throw new IllegalStateException("Root is required.");
            if (args.length != 1) throw new IllegalArgumentException("Expected one clone user ID.");
            int user = Integer.parseInt(args[0]);
            start(user, new AndroidDevice(), 20_000);
            System.out.println("Profile " + user + " is unlocked.");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            System.err.println("Could not start clone: " + cause);
            System.exit(1);
        }
    }

    private static Object service(String name, String api) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        if (binder == null) throw new IllegalStateException("Missing Android service: " + name);
        return Class.forName(api + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private static final class AndroidDevice implements Device {
        private final Class<?> users = Class.forName("android.os.IUserManager");
        private final Object manager = service("user", users.getName());

        AndroidDevice() throws Exception { }

        @Override public long verify(int user) throws Exception {
            Object info = users.getMethod("getUserInfo", int.class).invoke(manager, user);
            if (info == null) throw new IllegalStateException("Clone profile no longer exists: " + user);
            Class<?> type = info.getClass();
            Object parent = users.getMethod("getProfileParent", int.class).invoke(manager, user);
            return identity(user, type.getField("id").getInt(info), type.getField("serialNumber").getInt(info),
                    (String) type.getField("userType").get(info), parent == null ? -1 : type.getField("id").getInt(parent),
                    (Boolean) type.getMethod("isEphemeral").invoke(info));
        }

        @Override public boolean unlocked(int user) throws Exception {
            return (Boolean) users.getMethod("isUserUnlocked", int.class).invoke(manager, user);
        }

        @Override public boolean start(int user) throws Exception {
            String api = "android.app.IActivityManager";
            return (Boolean) Class.forName(api).getMethod("startProfile", int.class)
                    .invoke(service("activity", api), user);
        }

        @Override public boolean unlockCached(int user) throws Exception {
            String api = "com.android.internal.widget.ILockSettings";
            return (Boolean) Class.forName(api).getMethod("tryUnlockWithCachedUnifiedChallenge", int.class)
                    .invoke(service("lock_settings", api), user);
        }
    }
}
