package com.lixingchi.appclone;

/** Standalone check for the shortcut label formatter; run by {@code build.py --self-check}. */
public final class LabelsTest {
    public static void main(String[] args) {
        check("{id}: {name}", 12, "Chrome", "Clone 12", "12: Chrome");
        check("{name} ({clone})", 7, "Maps", "Work", "Maps (Work)");
        check("{id}:", 3, "Mail", "", "3: Mail");
        check("", 3, "Mail", "", "3: Mail");
        check(null, 3, "Mail", "", "3: Mail");
        check("{id}: {name}", 3, "A: B", "", "3: A: B");
        check("  {id}:\n{name} ", 4, "Notes", "", "4: Notes");
        check("{id}", 5, "", "", "5");
        System.out.println("ok LabelsTest");
    }

    private static void check(String template, int id, String app, String clone, String want) {
        String got = Labels.format(template, id, app, clone);
        if (!want.equals(got)) {
            throw new AssertionError("[" + template + "] -> [" + got + "] != [" + want + "]");
        }
    }

    private LabelsTest() {}
}
