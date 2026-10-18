package com.lixingchi.appclone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Clone-profile users, read from {@code cmd user list -v}. */
final class Clones {
    static final class Clone {
        final int id;
        final String name;
        final boolean running;

        Clone(int id, String name, boolean running) {
            this.id = id;
            this.name = name;
            this.running = running;
        }
    }

    /** Set by {@link #list()} when the underlying command failed. */
    static String lastError;

    private static final Pattern LINE =
            Pattern.compile("\\d+: id=(\\d+), name=(.*?), type=([^,]+),(.*)");
    private static final Pattern CREATED = Pattern.compile("created user id (\\d+)");

    static List<Clone> list() {
        lastError = null;
        Shell.Result result = Shell.root("cmd user list -v");
        List<Clone> clones = new ArrayList<>();
        for (String line : result.out.split("\n")) {
            Matcher matcher = LINE.matcher(line.trim());
            if (!matcher.matches()) continue;
            String type = matcher.group(3).trim();
            if (!ClonePolicy.CLONE_TYPE.endsWith(type)) continue;
            clones.add(new Clone(Integer.parseInt(matcher.group(1)), matcher.group(2).trim(),
                    matcher.group(4).contains("(running)")));
        }
        if (clones.isEmpty() && !result.ok()) {
            lastError = result.trim().isEmpty()
                    ? "Root needed: allow App Clone in KernelSU" : result.trim();
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
        Shell.Result created = Shell.root("pm create-user --profileOf 0 --user-type "
                + ClonePolicy.CLONE_TYPE + " " + quote(name));
        Matcher matcher = CREATED.matcher(created.out);
        if (!matcher.find()) return -1;
        int id = Integer.parseInt(matcher.group(1));
        Shell.root("am start-user " + id);
        return id;
    }

    static Shell.Result remove(int id) {
        return Shell.root("pm remove-user " + id);
    }

    private Clones() {}
}
