package com.lixingchi.appclone;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

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

    static Set<String> packages(int userId, String flags, Function<String, Shell.Result> run) {
        Shell.Result result = run.apply("pm list packages " + flags + "--user " + userId);
        if (!result.ok()) throw new IllegalStateException(result.trim());
        Set<String> packages = new HashSet<>();
        for (String line : result.out.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.startsWith("package:") || !validPackage(trimmed.substring(8))) {
                throw new IllegalStateException("Could not read apps for user " + userId + ": " + trimmed);
            }
            packages.add(trimmed.substring(8));
        }
        return packages;
    }

    static boolean validPackage(String pkg) {
        return pkg != null && pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*");
    }

    enum Action { INSTALL, REMOVE, DISABLE, ENABLE, DEFAULT, FORCE_STOP }

    static Shell.Result change(int id, long serial, String pkg, Action action) {
        synchronized (Clones.class) { return change(id, serial, pkg, action, Shell::root); }
    }

    static Shell.Result change(int id, long serial, String pkg, Action action,
                               Function<String, Shell.Result> run) {
        if (!validPackage(pkg) || action == null) return new Shell.Result(-1, "Invalid app action.");
        Shell.Result checked = Clones.verify(id, serial, run);
        if (!checked.ok()) return checked;
        try {
            boolean installed = packages(id, "", run).contains(pkg);
            if (action == Action.INSTALL && installed) return new Shell.Result(-1, "Already in this profile.");
            if (action != Action.INSTALL && !installed) return new Shell.Result(-1, "App is no longer in this profile.");
            String command;
            switch (action) {
                case INSTALL: command = "pm install-existing"; break;
                case REMOVE: command = "pm uninstall"; break;
                case DISABLE: command = "pm disable-user"; break;
                case ENABLE: command = "pm enable"; break;
                case DEFAULT: command = "pm default-state"; break;
                default: command = "am force-stop";
            }
            Shell.Result result = run.apply(command + " --user " + id + " " + Clones.quote(pkg));
            if (!result.ok()) return result;
            if (action == Action.INSTALL || action == Action.REMOVE) {
                boolean present = packages(id, "", run).contains(pkg);
                if (present != (action == Action.INSTALL)) return new Shell.Result(-1, "App installation state did not change.\n" + result.out);
            } else if (action != Action.FORCE_STOP) {
                String state = action == Action.DISABLE ? "disabled-user" : action == Action.ENABLE ? "enabled" : "default";
                if (!result.trim().equals("Package " + pkg + " new state: " + state)) {
                    return new Shell.Result(-1, "Could not verify app state.\n" + result.out);
                }
                if (action != Action.DEFAULT && packages(id, "-d ", run).contains(pkg) != (action == Action.DISABLE)) {
                    return new Shell.Result(-1, "App enabled state did not change.");
                }
            } else {
                Shell.Result dump = run.apply("dumpsys package " + Clones.quote(pkg));
                if (!dump.ok()) return dump;
                java.util.regex.Matcher state = java.util.regex.Pattern.compile(
                        "(?m)^\\s*User " + id + ": ([^\\r\\n]*)").matcher(dump.out);
                if (!state.find() || !state.group(1).contains("stopped=true")) {
                    return new Shell.Result(-1, "Could not verify force-stop. Refresh before retrying.");
                }
            }
            return result;
        } catch (IllegalStateException error) { return new Shell.Result(-1, error.getMessage()); }
    }

    static Shell.Result launch(int userId, App app) {
        synchronized (Clones.class) {
            return launch(userId, app, Shell::root);
        }
    }

    static Shell.Result launch(int userId, App app, Function<String, Shell.Result> run) {
        if (!validPackage(app.pkg) || app.activity == null) return new Shell.Result(-1, "Invalid app shortcut.");
        Shell.Result started = Clones.setRunning(userId, true, run);
        if (!started.ok()) return started;
        String component = app.pkg + "/" + app.activity;
        if (app.activity.isEmpty()) {
            Shell.Result resolved = run.apply("cmd package resolve-activity --brief --user " + userId
                    + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p " + Clones.quote(app.pkg));
            if (!resolved.ok()) return resolved;
            component = null;
            for (String line : resolved.out.split("\n")) if (line.trim().startsWith(app.pkg + "/")) component = line.trim();
            if (component == null) return new Shell.Result(-1, "No enabled launcher activity in this profile. Enable the app first, or use an app with a launcher.");
        }
        Shell.Result result = run.apply("am start --user " + userId
                + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n " + Clones.quote(component));
        if (result.ok() && java.util.regex.Pattern.compile("(?m)^Error(?: type \\d+|:)")
                .matcher(result.out).find()) {
            return new Shell.Result(-1, result.out);
        }
        return result;
    }

    private Apps() {}
}
