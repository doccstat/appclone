package com.lixingchi.appclone;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, out + "\nsu timed out");
            }
            return new Result(process.exitValue(), out.toString());
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return new Result(-1, e.toString());
        }
    }

    private Shell() {}
}
