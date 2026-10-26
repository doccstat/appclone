package com.lixingchi.appclone;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Pattern;

/** One reboot action, with checkpoints so a rescheduled job does not replay completed profiles. */
final class BootPolicy {
    static final String SYSTEM = "system";
    static final String START = "start";
    static final String STOP = "stop";
    static final String[] VALUES = {SYSTEM, START, STOP};
    static final String[] LABELS = {"System default", "Start all profiles", "Stop all profiles"};
    private final SharedPreferences prefs;

    BootPolicy(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    static BootPolicy get(Context context) {
        return new BootPolicy(context.getSharedPreferences("boot", Context.MODE_PRIVATE));
    }

    String mode() {
        String value = prefs.getString("mode", SYSTEM);
        return Arrays.asList(VALUES).contains(value) ? value : SYSTEM;
    }

    String status() {
        return prefs.getString("status", "No reboot action has run.");
    }

    void save(String mode) {
        if (!Arrays.asList(VALUES).contains(mode)) throw new IllegalArgumentException("Unknown reboot policy");
        synchronized (BootPolicy.class) {
            String previous = mode();
            long run = prefs.getLong("run", 0);
            if (!prefs.edit().putString("mode", mode).putLong("run", run + 1)
                    .putString("status", "Saved. Applies after the next reboot and unlock.").commit()) {
                // Failed commits still update the in-memory preferences; restore the prior choice.
                prefs.edit().putString("mode", previous).putLong("run", run)
                        .putString("status", "Could not save reboot setting. Select it again to retry.").commit();
                throw new IllegalStateException("Could not save reboot setting.");
            }
        }
    }

    long begin() {
        synchronized (BootPolicy.class) {
            if (SYSTEM.equals(mode())) return -1;
            long run = prefs.getLong("run", 0) + 1;
            store(prefs.edit().putLong("run", run).remove("pending").putBoolean("finished", false)
                    .putInt("total", 0).putInt("failed", 0).putString("error", "")
                    .putString("status", "Reboot action queued; waiting for Android to run it."));
            return run;
        }
    }

    boolean current(long run, String mode) {
        synchronized (BootPolicy.class) {
            return run >= 0 && !SYSTEM.equals(mode) && mode.equals(mode())
                    && prefs.getLong("run", 0) == run && !prefs.getBoolean("finished", false);
        }
    }

    void fail(long run, String mode, String message) {
        synchronized (BootPolicy.class) {
            if (current(run, mode)) store(prefs.edit().putBoolean("finished", true)
                    .putString("status", "Reboot action failed: " + message));
        }
    }

    void apply(long run, String mode, BooleanSupplier keepGoing, Function<String, Shell.Result> shell) {
        if (!current(run, mode) || !keepGoing.getAsBoolean()) return;
        Set<String> pending = prefs.getStringSet("pending", null);
        if (pending == null) {
            Shell.Result users = shell.apply("cmd user list -v");
            if (!users.ok()) {
                fail(run, mode, users.trim().isEmpty() ? "Root access unavailable." : users.trim());
                return;
            }
            if (!Pattern.compile("(?m)^\\d+ users:\\s*$").matcher(users.out).find()) {
                fail(run, mode, "Could not read the profile list.\n" + users.trim());
                return;
            }
            pending = new HashSet<>();
            for (Clones.Clone clone : Clones.parse(users.out)) pending.add(String.valueOf(clone.id));
            synchronized (BootPolicy.class) {
                if (!current(run, mode) || !keepGoing.getAsBoolean()) return;
                store(prefs.edit().putStringSet("pending", pending).putInt("total", pending.size()));
            }
        }
        // Work on a copy: SharedPreferences owns the set it returns.
        pending = new TreeSet<>(pending);
        for (String id : new TreeSet<>(pending)) {
            Shell.Result result;
            synchronized (Clones.class) {
                if (!current(run, mode) || !keepGoing.getAsBoolean()) return;
                result = Clones.setRunning(Integer.parseInt(id), START.equals(mode), shell);
            }
            synchronized (BootPolicy.class) {
                if (!current(run, mode)) return;
                pending.remove(id);
                int failed = prefs.getInt("failed", 0) + (result.ok() ? 0 : 1);
                String error = result.ok() ? prefs.getString("error", "")
                        : "User " + id + ": " + (result.trim().isEmpty() ? "Root command failed." : result.trim());
                store(prefs.edit().putStringSet("pending", pending).putInt("failed", failed)
                        .putString("error", error).putString("status", summary(mode, pending.size(), failed, error)));
            }
        }
        synchronized (BootPolicy.class) {
            if (current(run, mode)) {
                store(prefs.edit().putBoolean("finished", true).putString("status", summary(mode, 0,
                        prefs.getInt("failed", 0), prefs.getString("error", ""))));
            }
        }
    }

    private String summary(String mode, int remaining, int failed, String error) {
        int total = prefs.getInt("total", 0);
        return (START.equals(mode) ? "Start all" : "Stop all") + ": "
                + (total - remaining - failed) + " succeeded, " + failed + " failed, "
                + remaining + " pending."
                + (error.isEmpty() ? "" : "\nLast failure: " + error);
    }

    private static void store(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Could not save reboot settings/progress.");
    }
}
