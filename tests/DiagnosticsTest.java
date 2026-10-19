package com.lixingchi.appclone;

/** Standalone check for diagnostics log filtering; run by {@code build.py --self-check}. */
public final class DiagnosticsTest {
    public static void main(String[] args) {
        String raw = "10-19 01:00:00.000 I/AppClone( 1): hooks installed: 5/5\n"
                + "10-19 01:00:01.000 I/Other( 1): private app name\n"
                + "10-19 01:00:02.000 E/AndroidRuntime( 1): FATAL EXCEPTION: main\n"
                + "10-19 01:00:03.000 W/UserManagerService( 1): cannot add user\n"
                + "10-19 01:00:04.000 I/PackageManager( 1): create user\n";
        String filtered = Diagnostics.filterLogcat(raw);
        check(filtered.contains("AppClone"), "keeps AppClone");
        check(filtered.contains("AndroidRuntime"), "keeps AndroidRuntime");
        check(filtered.contains("UserManagerService"), "keeps UserManagerService");
        check(filtered.contains("PackageManager"), "keeps PackageManager");
        check(!filtered.contains("Other"), "drops unrelated logs");
        check("(no matching lines)".equals(Diagnostics.filterLogcat("")), "empty filter");
        System.out.println("ok DiagnosticsTest");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
