package com.lixingchi.appclone;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Tests process output/timeout handling with an unprivileged Java child, never su. */
public final class ShellTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("sleep".equals(args[0])) {
                Thread.sleep(10000);
            } else {
                for (int i = 0; i < 20000; i++) System.out.println("output");
                System.err.println("error output");
                if ("fail".equals(args[0])) System.exit(7);
            }
            return;
        }
        Shell.Result success = Shell.run(child("output"), 10, TimeUnit.SECONDS);
        check(success.ok() && success.out.contains("output\n") && success.out.contains("error output"),
                "drains stdout and stderr without blocking on a full pipe");
        Shell.Result failure = Shell.run(child("fail"), 10, TimeUnit.SECONDS);
        check(failure.code == 7 && failure.out.contains("error output"), "preserves command failure");
        long start = System.nanoTime();
        Shell.Result timeout = Shell.run(child("sleep"), 200, TimeUnit.MILLISECONDS);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        check(!timeout.ok() && timeout.out.contains("timed out"), "silent process times out");
        check(elapsed < 5000, "timeout must not wait for EOF: " + elapsed + "ms");
        System.out.println("ok ShellTest");
    }

    private static ProcessBuilder child(String mode) {
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                ShellTest.class.getName(), mode);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
