package com.lixingchi.appclone;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Clone-profile users, read from {@code cmd user list -v}. */
final class Clones {
    static final class Clone {
        final int id;
        final String name;
        final boolean running;
        private final String details;

        Clone(int id, String name, String details) {
            this.id = id;
            this.name = name;
            this.running = details.contains("(running)");
            this.details = details;
        }
    }

    /** Set by {@link #list()} when the underlying command failed. */
    static String lastError;

    /** Command and output of the last failed {@link #create(String)} call. */
    static volatile String lastCreateError;

    private static final Pattern LINE =
            Pattern.compile("\\d+: id=(\\d+), name=(.*), type=([^,]+),(.*)");
    private static final Pattern CREATED = Pattern.compile("created user id (\\d+)");

    static List<Clone> list() {
        lastError = null;
        Shell.Result result = Shell.root("cmd user list -v");
        if (!result.ok()) {
            lastError = result.trim().isEmpty()
                    ? "Root needed: allow App Clone in your root manager" : result.trim();
            return new ArrayList<>();
        }
        return parse(result.out);
    }

    static List<Clone> parse(String output) {
        List<Clone> clones = new ArrayList<>();
        for (String line : output.split("\n")) {
            Matcher matcher = LINE.matcher(line.trim());
            if (!matcher.matches()) continue;
            String type = matcher.group(3).trim();
            if (!ClonePolicy.CLONE_TYPE.equals(type) && !"profile.CLONE".equals(type)) continue;
            try {
                clones.add(new Clone(Integer.parseInt(matcher.group(1)), matcher.group(2),
                        matcher.group(4)));
            } catch (NumberFormatException ignored) {
                // Reject malformed ids rather than passing them to a privileged command.
            }
        }
        Collections.sort(clones, new Comparator<Clone>() {
            @Override public int compare(Clone a, Clone b) {
                return Integer.compare(a.id, b.id);
            }
        });
        return clones;
    }

    /** Single-quotes a value so spaces and metacharacters survive {@code su -c}. */
    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** Creates one clone and starts it so it becomes initialized. Returns its id, or -1. */
    static int create(String name) {
        lastCreateError = null;
        String command = "pm create-user --profileOf 0 --user-type "
                + ClonePolicy.CLONE_TYPE + " " + quote(name);
        Shell.Result created = Shell.root(command);
        Matcher matcher = CREATED.matcher(created.out);
        if (!matcher.find()) {
            lastCreateError = "$ " + command + "\nexit=" + created.code + "\n"
                    + (created.trim().isEmpty() ? "(no output)" : created.trim());
            Log.w("AppClone", "create-user failed: " + lastCreateError);
            return -1;
        }
        int id = Integer.parseInt(matcher.group(1));
        Shell.root("am start-user " + id);
        return id;
    }

    static synchronized Shell.Result setRunning(int id, boolean running) {
        return setRunning(id, running, Shell::root);
    }

    static Shell.Result setRunning(int id, boolean running, Function<String, Shell.Result> run) {
        if (id <= 0) return new Shell.Result(-1, "Not a clone profile: " + id);
        Shell.Result users = run.apply("cmd user list -v");
        if (!users.ok()) return users;
        Clone target = null;
        for (Clone clone : parse(users.out)) if (clone.id == id) target = clone;
        if (target == null) return new Shell.Result(-1, "Clone profile no longer exists: " + id);
        if (!target.details.contains("(parentId=0)")) {
            return new Shell.Result(-1, "Only clone profiles belonging to user 0 can be started or stopped.");
        }
        if (target.details.contains("EPHEMERAL")) {
            return new Shell.Result(-1, "Cannot control an ephemeral clone: stopping it can delete its data.");
        }
        String stateCommand = running ? "am get-started-user-state " + id : "am is-user-stopped " + id;
        String expected = running ? "RUNNING_UNLOCKED" : "true";
        if (running) {
            Shell.Result state = run.apply(stateCommand);
            if (!state.ok()) return state;
            if (expected.equals(state.trim())) return state;
        }
        // -f stops only this profile when its related user 0 cannot be stopped (Android 13+).
        Shell.Result result = run.apply((running ? "am start-user -w " : "am stop-user -w -f ") + id);
        if (!result.ok()) return result;
        // am can exit 0 on failure or an aborted stop; verify the actual state.
        Shell.Result state = run.apply(stateCommand);
        if (!state.ok()) return state;
        if (!expected.equals(state.trim())) {
            return new Shell.Result(-1, "Profile " + id + (running ? " is not ready. Unlock the main user and retry."
                    : " did not stop. Refresh and retry.") + "\n" + result.trim() + "\n" + state.trim());
        }
        return result;
    }

    static Shell.Result remove(int id) {
        return Shell.root("pm remove-user " + id);
    }

    private Clones() {}
}
