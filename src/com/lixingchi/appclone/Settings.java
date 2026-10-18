package com.lixingchi.appclone;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferences for shortcut labels and automation. */
final class Settings {
    private static final String NAME = "shortcuts";
    private static final String PREFIX = "prefix";
    private static final String FORMAT = "format";
    private static final String AUTO_PIN = "auto_pin";

    private static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean prefix(Context context) {
        return get(context).getBoolean(PREFIX, false);
    }

    static void prefix(Context context, boolean value) {
        get(context).edit().putBoolean(PREFIX, value).apply();
    }

    static String format(Context context) {
        return get(context).getString(FORMAT, Labels.DEFAULT_FORMAT);
    }

    static void format(Context context, String value) {
        get(context).edit().putString(FORMAT, value).apply();
    }

    static boolean autoPin(Context context) {
        return get(context).getBoolean(AUTO_PIN, false);
    }

    static void autoPin(Context context, boolean value) {
        get(context).edit().putBoolean(AUTO_PIN, value).apply();
    }

    private Settings() {}
}
