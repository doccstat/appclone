package com.lixingchi.appclone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.widget.Toast;

/** Pinned home-screen shortcuts that open one app in one clone. */
final class Shortcuts {
    /** The name shown on the home screen; the prefix toggle controls the clone id. */
    static String label(Context context, CloneApps.Target target) {
        if (!Settings.prefix(context)) return target.app.label;
        String value = Labels.format(Settings.format(context), target.userId,
                target.app.label, target.name);
        return value == null || value.trim().isEmpty() ? target.app.pkg : value;
    }

    static void pin(Context context, CloneApps.Target target) {
        ShortcutManager manager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(context, "This launcher cannot pin shortcuts", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap icon = CloneApps.icon(context, target.app.pkg, target.userId);
        if (icon == null) {
            Toast.makeText(context, "That app is no longer in the clone", Toast.LENGTH_SHORT).show();
            return;
        }
        String label = label(context, target);
        Intent intent = new Intent(context, LaunchActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(LaunchActivity.EXTRA_PKG, target.app.pkg)
                .putExtra(LaunchActivity.EXTRA_USER, target.userId)
                .putExtra(LaunchActivity.EXTRA_ACTIVITY, target.app.activity)
                .putExtra(LaunchActivity.EXTRA_LABEL, label);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context,
                "clone-" + target.userId + "-" + target.app.pkg)
                .setShortLabel(label)
                .setLongLabel(label + " \u00b7 " + target.name)
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(intent)
                .build();
        manager.requestPinShortcut(shortcut, null);
    }

    private Shortcuts() {}
}
