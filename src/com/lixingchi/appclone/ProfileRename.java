package com.lixingchi.appclone;

/** Short-lived root rename; profile identity, data and running state are unchanged. */
public final class ProfileRename {
    interface Device {
        long verify(int user) throws Exception;
        String name(int user) throws Exception;
        void name(int user, String name) throws Exception;
    }

    static void validate(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Enter a nonempty single-line name without NUL characters.");
        // Android truncates persisted user names to 100 UTF-16 code units.
        if (name.length() > 100) throw new IllegalArgumentException("Use at most 100 characters (emoji may count as two).");
    }

    static String command(int user, long serial, String name) {
        validate(name);
        if (user <= 0 || serial < 0) throw new IllegalArgumentException("Invalid clone identity.");
        return ProfileStart.entry(ProfileRename.class) + " " + user + " " + serial + " " + Clones.quote(name);
    }

    static void rename(int user, long serial, String name, Device device) throws Exception {
        validate(name);
        if (user <= 0 || serial < 0) throw new IllegalArgumentException("Invalid clone identity.");
        verify(user, serial, device);
        if (name.equals(device.name(user))) return;
        verify(user, serial, device);
        device.name(user, name);
        verify(user, serial, device);
        if (!name.equals(device.name(user)))
            throw new IllegalStateException("Could not verify the new name. Refresh before retrying.");
    }

    private static void verify(int user, long serial, Device device) throws Exception {
        if (device.verify(user) != serial)
            throw new IllegalStateException("Profile identity changed. Refresh before retrying.");
    }

    public static void main(String[] args) {
        try {
            if (android.os.Process.myUid() != 0) throw new IllegalStateException("Root is required.");
            if (args.length != 3) throw new IllegalArgumentException("Expected user, serial and name.");
            rename(Integer.parseInt(args[0]), Long.parseLong(args[1]), args[2], new AndroidDevice());
            System.out.println("Profile renamed.");
        } catch (Exception error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            System.err.println("Could not rename profile: " + cause);
            System.exit(1);
        }
    }

    private static final class AndroidDevice extends ProfileStart.AndroidDevice implements Device {
        AndroidDevice() throws Exception { }
        @Override public String name(int user) throws Exception {
            Object info = users.getMethod("getUserInfo", int.class).invoke(manager, user);
            if (info == null) throw new IllegalStateException("Profile no longer exists.");
            return (String) info.getClass().getField("name").get(info);
        }
        @Override public void name(int user, String name) throws Exception {
            users.getMethod("setUserName", int.class, String.class).invoke(manager, user, name);
        }
    }
}
