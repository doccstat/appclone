package com.lixingchi.appclone;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Runs a command as root. Call off the main thread only. */
final class Shell {
    static final class Result {
        final int code;
        final String out;

        Result(int code, String out) {
            this.code = code;
            this.out = out;
        }

        boolean ok() {
            return code == 0;
        }

        String trim() {
            return out.trim();
        }
    }

    private static final long TIMEOUT_SECONDS = 30;

    static Result root(String command) {
        return run(new ProcessBuilder("su", "-c", command), TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static Result run(ProcessBuilder command, long timeout, TimeUnit unit) {
        Process process = null;
        FutureTask<String> output = null;
        try {
            process = command.redirectErrorStream(true).start();
            final Process child = process;
            // Drain concurrently so a silent/hung command cannot bypass the timeout in readLine().
            output = new FutureTask<>(() -> {
                StringBuilder out = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) out.append(line).append('\n');
                }
                return out.toString();
            });
            Thread reader = new Thread(output, "root-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(timeout, unit)) {
                return new Result(-1, "Root command timed out. Refresh the profile state before retrying.");
            }
            return new Result(process.exitValue(), output.get(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "Root command interrupted. Refresh the profile state before retrying.");
        } catch (Exception e) {
            return new Result(-1, e.toString());
        } finally {
            if (process != null) process.destroyForcibly();
            if (output != null) output.cancel(true);
        }
    }

    private Shell() {}
}
