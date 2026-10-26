package com.lixingchi.appclone;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Builds a text report for issue reports; no network or new permissions. */
final class Diagnostics {
    static String collect(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("App Clone diagnostics\n");
        out.append("Generated: ").append(stamp("yyyy-MM-dd HH:mm:ss")).append('\n');
        out.append("App: ").append(version(context)).append('\n');
        out.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        out.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("Build: ").append(Build.DISPLAY).append('\n');
        out.append("Fingerprint: ").append(Build.FINGERPRINT).append('\n');
        out.append("Review this file for personal data before attaching it.\n");

        BootPolicy boot = BootPolicy.get(context);
        section(out, "Reboot policy", boot.mode() + "\n" + boot.status());
        section(out, "Last create error",
                Clones.lastCreateError == null ? "none this session" : Clones.lastCreateError);
        section(out, "cmd user list -v", shell("cmd user list -v"));
        section(out, "dumpsys user", shell("dumpsys user"));
        section(out, "logcat (filtered)",
                filterLogcat(shell("logcat -d -v threadtime -t 4000")));
        return out.toString();
    }

    static String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "unknown";
        }
    }

    static String fileName() {
        return "appclone-diagnostics-" + stamp("yyyyMMdd-HHmmss") + ".txt";
    }

    /** Keeps module, framework, and system_server failure lines; drops unrelated app data. */
    static String filterLogcat(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("appclone") || lower.contains("xposed")
                    || lower.contains("lsposed") || lower.contains("usermanager")
                    || lower.contains("usercontroller") || lower.contains("androidruntime")
                    || lower.contains("fatal exception") || lower.contains("packagemanager")) {
                out.append(line).append('\n');
            }
        }
        return out.length() == 0 ? "(no matching lines)" : out.toString();
    }

    private static String shell(String command) {
        Shell.Result result = Shell.root(command);
        return "$ " + command + "\nexit=" + result.code + "\n"
                + (result.trim().isEmpty() ? "(no output)" : result.trim());
    }

    private static String stamp(String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(new Date());
    }

    private static void section(StringBuilder out, String title, String body) {
        out.append("\n== ").append(title).append(" ==\n").append(body).append('\n');
    }

    private Diagnostics() {}
}
