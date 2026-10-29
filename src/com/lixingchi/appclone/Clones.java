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

        boolean owned() { return details.contains("(parentId=0)"); }
        boolean ephemeral() { return details.contains("EPHEMERAL"); }
    }

    /** Command and output of the last failed {@link #create(String)} call. */
    static volatile String lastCreateError;

    private static final Pattern LINE =
            Pattern.compile("\\d+: id=(\\d+), name=(.*), type=([^,]+),(.*)");
    private static final Pattern CREATED = Pattern.compile("created user id (\\d+)");

    static List<Clone> list() { return list(Shell::root); }

    static List<Clone> list(Function<String, Shell.Result> run) {
        Shell.Result result = run.apply("cmd user list -v");
        if (!result.ok()) throw new IllegalStateException(result.trim().isEmpty()
                ? "Root needed: allow App Clone in your root manager" : result.trim());
        if (!Pattern.compile("(?m)^\\s*0: id=0, name=.*?, type=full\\.SYSTEM,").matcher(result.out).find()) {
            throw new IllegalStateException("Could not read the owner profile. Refresh and retry.\n" + result.trim());
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

    static List<String> names(String base, int count, boolean append, List<Clone> clones) {
        if (base.isEmpty() || base.indexOf('\0') >= 0 || base.indexOf('\n') >= 0 || base.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Enter a nonempty single-line name without NUL characters.");
        if (count < 1 || count > 500) throw new IllegalArgumentException("Choose between 1 and 500 profiles.");
        long last = 0;
        if (append) {
            Pattern suffix = Pattern.compile(Pattern.quote(base) + "-(\\d+)$");
            for (Clone clone : clones) {
                Matcher match = suffix.matcher(clone.name);
                if (match.matches()) {
                    try { last = Math.max(last, Long.parseLong(match.group(1))); }
                    catch (NumberFormatException error) { throw new IllegalArgumentException("Existing name suffix is too large."); }
                }
            }
        }
        if (last > Long.MAX_VALUE - count) throw new IllegalArgumentException("Name suffix would overflow.");
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) result.add(append ? base + "-" + (last + i) : base);
        return result;
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
        if (!created.ok() || !matcher.find()) {
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
        Shell.Result result = run.apply(running ? ProfileStart.command(id) : "am stop-user -w -f " + id);
        if (!result.ok()) return result;
        // am can exit 0 on failure or an aborted stop; verify the actual state.
        Shell.Result state = run.apply(stateCommand);
        if (!state.ok()) return state;
        if (!expected.equals(state.trim())) {
            return new Shell.Result(-1, "Profile " + id + (running ? " is not ready. Unlock the main user with its PIN, pattern or password and retry."
                    : " did not stop. Refresh and retry.") + "\n" + result.trim() + "\n" + state.trim());
        }
        return result;
    }

    static Shell.Result verify(int id, long serial, Function<String, Shell.Result> run) {
        if (id <= 0 || serial < 0) return new Shell.Result(-1, "Invalid clone identity.");
        Shell.Result users = run.apply("cmd user list -v");
        if (!users.ok()) return users;
        Clone target = null;
        for (Clone clone : parse(users.out)) if (clone.id == id) target = clone;
        if (target == null || !target.owned() || target.ephemeral()) {
            return new Shell.Result(-1, "Not a non-ephemeral clone of user 0: " + id);
        }
        Shell.Result dump = run.apply("dumpsys user");
        if (!dump.ok()) return dump;
        Matcher identity = Pattern.compile("(?m)^\\s*UserInfo\\{" + id
                + ":[^\\r\\n]*\\}[^\\r\\n]* serialNo=(\\d+)(?:\\s|$)").matcher(dump.out);
        if (!identity.find() || !Long.toString(serial).equals(identity.group(1))) {
            return new Shell.Result(-1, "Profile identity changed. Refresh before retrying.");
        }
        return new Shell.Result(0, "");
    }

    static Shell.Result remove(int id, long serial) {
        synchronized (Clones.class) { return remove(id, serial, Shell::root); }
    }

    static Shell.Result remove(int id, long serial, Function<String, Shell.Result> run) {
        Shell.Result checked = verify(id, serial, run);
        if (!checked.ok()) return checked;
        Shell.Result result = run.apply("pm remove-user --wait " + id);
        if (!result.ok() || !result.out.contains("Success")) return new Shell.Result(-1, result.out);
        try {
            for (Clone clone : list(run)) if (clone.id == id) {
                return new Shell.Result(-1, "Profile still exists. Refresh before retrying.");
            }
            return result;
        } catch (IllegalStateException error) { return new Shell.Result(-1, error.getMessage()); }
    }

    private Clones() {}
}
