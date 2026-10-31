package com.lixingchi.appclone;

import java.io.File;
import java.io.IOException;
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
                if ("not-found".equals(args[0])) System.exit(127);
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
        for (String message : new String[] {"Cannot run program \"su\": error=2, No such file or directory",
                "Exec failed, error: 2 (No such file or directory)"}) {
            check(Shell.missingExecutable(new IOException(message)), "recognizes exec ENOENT: " + message);
        }
        for (String message : new String[] {null, "", "error=13, Permission denied", "error=20, Not a directory",
                "Exec failed, error: 20 (Not a directory)", "error=8, Exec format error"}) {
            check(!Shell.missingExecutable(new IOException(message)), "other startup errors must not retry: " + message);
        }
        ProcessBuilder missing = new ProcessBuilder("appclone-test-missing-su-" + java.util.UUID.randomUUID());
        Shell.Result recovered = Shell.run(missing, child("output"), 10, TimeUnit.SECONDS);
        check(recovered.ok() && recovered.out.contains("error output"),
                "missing PATH executable can use the explicit fallback, with stderr captured: " + recovered.out);
        Shell.Result noFallback = Shell.run(missing, 10, TimeUnit.SECONDS);
        check(!noFallback.ok() && !noFallback.out.contains("root manager"), "non-root commands keep the original startup error");
        Shell.Result started = Shell.run(child("output"), child("fail"), 10, TimeUnit.SECONDS);
        check(started.ok(), "successful startup must not try the fallback");
        Shell.Result unavailable = Shell.run(missing, missing, 10, TimeUnit.SECONDS);
        check(!unavailable.ok() && unavailable.out.contains("root manager")
                        && unavailable.out.contains("appclone-test-missing-su"),
                "missing root frontend gives recovery instructions and original failure: " + unavailable.out);
        Shell.Result denied = Shell.run(new ProcessBuilder(System.getProperty("java.home")),
                child("output"), 10, TimeUnit.SECONDS);
        check(!denied.ok() && denied.out.contains("root manager"), "permission failure must not try another executable: " + denied.out);
        for (String mode : new String[] {"fail", "not-found", "sleep"}) {
            Shell.Result noReplay = Shell.run(child(mode), child("output"),
                    mode.equals("sleep") ? 200 : 10000, TimeUnit.MILLISECONDS);
            check(noReplay.code == (mode.equals("fail") ? 7 : mode.equals("not-found") ? 127 : -1),
                    "never replay a command after the process started: " + mode);
        }
        Shell.Result fallbackTimeout = Shell.run(missing, child("sleep"), 200, TimeUnit.MILLISECONDS);
        check(!fallbackTimeout.ok() && fallbackTimeout.out.contains("timed out"), "fallback retains the timeout");
        Thread.currentThread().interrupt();
        Shell.Result interrupted = Shell.run(child("sleep"), child("output"), 10, TimeUnit.SECONDS);
        check(!interrupted.ok() && interrupted.out.contains("interrupted") && Thread.currentThread().isInterrupted(),
                "interruption does not replay the command and preserves interrupt status");
        Thread.interrupted();
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
