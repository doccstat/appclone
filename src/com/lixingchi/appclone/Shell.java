package com.lixingchi.appclone;

import java.io.BufferedReader;
import java.io.IOException;
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
        return run(new ProcessBuilder("su", "-c", command),
                new ProcessBuilder("/system/bin/su", "-c", command), TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static Result run(ProcessBuilder command, long timeout, TimeUnit unit) {
        return run(command, null, timeout, unit);
    }

    static Result run(ProcessBuilder command, ProcessBuilder fallback, long timeout, TimeUnit unit) {
        Process process = null;
        FutureTask<String> output = null;
        try {
            try {
                process = command.redirectErrorStream(true).start();
            } catch (IOException error) {
                // Only exec ENOENT may fall back; a started command must never be replayed.
                if (fallback == null || !missingExecutable(error)) throw error;
                // KernelSU can virtualize this path, so File.exists/canExecute is not a reliable gate.
                process = fallback.redirectErrorStream(true).start();
            }
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
        } catch (IOException e) {
            return new Result(-1, fallback == null ? e.toString()
                    : "Cannot start the root shell. Check that root is active and App Clone is allowed in your root manager, "
                    + "then reopen the app and tap Retry. LSPosed scope alone does not grant root.\n" + e);
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

    static boolean missingExecutable(IOException error) {
        // Android and host OpenJDK expose exec errno in different IOException message formats.
        String message = error.getMessage();
        return message != null && (message.contains("error=2,") || message.contains("error: 2 ("));
    }

    private Shell() {}
}
