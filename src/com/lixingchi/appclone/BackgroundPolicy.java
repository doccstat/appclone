package com.lixingchi.appclone;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Objects;
import java.util.function.Function;

/** Saves the previous package AppOp before mutation; never writes UID-wide policy. */
final class BackgroundPolicy {
    static final int RESTRICTED = android.app.AppOpsManager.MODE_IGNORED;
    final SharedPreferences prefs;
    private final Function<String, Shell.Result> shell;

    static BackgroundPolicy get(Context context) {
        return new BackgroundPolicy(context.getSharedPreferences("background", Context.MODE_PRIVATE), Shell::root);
    }

    BackgroundPolicy(SharedPreferences prefs, Function<String, Shell.Result> shell) {
        this.prefs = prefs; this.shell = shell;
    }

    static final class State {
        final int uid, mode, uidMode;
        final long installed;
        State(int uid, long installed, int mode, int uidMode) {
            if (uid < 100000 || installed < 0 || mode < 0 || mode > 4 || uidMode < -1 || uidMode > 4)
                throw new IllegalArgumentException("Unsupported background setting.");
            this.uid = uid; this.installed = installed; this.mode = mode; this.uidMode = uidMode;
        }
        State withMode(int mode) { return new State(uid, installed, mode, uidMode); }
        String encode() { return uid + ":" + installed + ":" + mode + ":" + uidMode; }
        static State parse(String text) {
            String[] fields = text.split(":", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Could not read the exact background setting.");
            return new State(Integer.parseInt(fields[0]), Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return uid == state.uid && installed == state.installed && mode == state.mode && uidMode == state.uidMode;
        }
        @Override public int hashCode() { return Objects.hash(uid, installed, mode, uidMode); }
    }

    static String name(int mode) {
        switch (mode) {
            case -1: return "None";
            case 0: return "Allowed (Android-managed)";
            case 1: return "Restricted";
            case 2: return "Denied";
            case 3: return "System default";
            case 4: return "Foreground only";
            default: throw new IllegalArgumentException("Unknown background mode.");
        }
    }

    private String key(int user, long serial, String pkg) {
        if (user <= 0 || serial < 0 || !Apps.validPackage(pkg)) throw new IllegalArgumentException("Invalid clone app identity.");
        return user + ":" + serial + ":" + pkg;
    }

    State previous(int user, long serial, String pkg) {
        String saved = prefs.getString(key(user, serial, pkg), null);
        return saved == null ? null : State.parse(saved);
    }

    State read(int user, long serial, String pkg) {
        key(user, serial, pkg);
        State state = result(shell.apply(AppCommand.command("background-get", user, serial, pkg)));
        if (state.uid / 100000 != user) throw new IllegalStateException("Background setting belongs to another profile.");
        return state;
    }

    void restrict(int user, long serial, String pkg, State preview) {
        synchronized (Clones.class) {
            State current = fresh(user, serial, pkg, preview);
            if (previous(user, serial, pkg) != null) throw new IllegalStateException("Restore or forget the saved setting first.");
            if (current.uidMode != -1) throw new IllegalStateException("A UID-wide override is controlled elsewhere; nothing changed.");
            if (current.mode == RESTRICTED) throw new IllegalStateException("Already restricted; nothing changed.");
            // Persist before root so interruption or a partial failure cannot erase the restore point.
            save(key(user, serial, pkg), current.encode());
            set(user, serial, pkg, current, RESTRICTED);
        }
    }

    void restore(int user, long serial, String pkg, State preview) {
        synchronized (Clones.class) {
            State current = fresh(user, serial, pkg, preview);
            State saved = previous(user, serial, pkg);
            if (saved == null) throw new IllegalStateException("No previous setting is saved.");
            if (!current.equals(saved)) {
                if (!current.equals(saved.withMode(RESTRICTED)))
                    throw new IllegalStateException("The app or its setting changed elsewhere. Nothing restored; you can forget the saved record without changing Android.");
                set(user, serial, pkg, current, saved.mode);
            }
            save(key(user, serial, pkg), null);
        }
    }

    void forget(int user, long serial, String pkg, State expected) {
        synchronized (Clones.class) {
            if (!Objects.equals(previous(user, serial, pkg), expected))
                throw new IllegalStateException("Saved setting changed. Reopen background controls.");
            save(key(user, serial, pkg), null);
        }
    }

    private State fresh(int user, long serial, String pkg, State preview) {
        State current = read(user, serial, pkg);
        if (!current.equals(preview)) throw new IllegalStateException("Background setting changed. Reopen its controls.");
        return current;
    }

    private void set(int user, long serial, String pkg, State current, int mode) {
        Shell.Result response = shell.apply(AppCommand.command("background-set", user, serial, pkg,
                current.encode(), Integer.toString(mode)));
        if (!response.ok()) throw new IllegalStateException(response.trim()
                + "\nPrevious setting is saved. Reopen Background activity to restore or inspect it.");
        if (!result(response).equals(current.withMode(mode)))
            throw new IllegalStateException("Could not verify the setting. Previous mode is still saved.");
    }

    private static State result(Shell.Result result) {
        if (!result.ok()) throw new IllegalStateException(result.trim().isEmpty() ? "Could not read background setting." : result.trim());
        return State.parse(result.trim());
    }

    private void save(String key, String value) {
        String previous = prefs.getString(key, null);
        if (!edit(key, value).commit()) {
            edit(key, previous).commit();
            throw new IllegalStateException("Could not save the background restore record. No further changes applied.");
        }
    }

    private SharedPreferences.Editor edit(String key, String value) {
        SharedPreferences.Editor editor = prefs.edit();
        return value == null ? editor.remove(key) : editor.putString(key, value);
    }
}
