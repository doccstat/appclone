package com.lixingchi.appclone;

import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Reads policy only when requested, not during inventory scans or navigation. */
final class BackgroundControls {
    static void open(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile) {
        BackgroundPolicy policy = BackgroundPolicy.get(activity);
        BackgroundPolicy.State[] state = new BackgroundPolicy.State[1];
        activity.work("Reading background setting…", () -> {
            synchronized (Clones.class) {
                state[0] = policy.read(profile.clone.id, profile.serial, entry.app.pkg);
                policy.previous(profile.clone.id, profile.serial, entry.app.pkg);
            }
            return new Shell.Result(0, "");
        }, () -> show(activity, entry, profile, policy, state[0]), false);
    }

    static void show(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile,
                     BackgroundPolicy policy, BackgroundPolicy.State state) {
        int user = profile.clone.id;
        long serial = profile.serial;
        String pkg = entry.app.pkg;
        BackgroundPolicy.State saved = policy.previous(user, serial, pkg);
        String target = entry.app.label + "\n" + Inventory.location(profile);
        String message = target + "\n\nCurrent: " + BackgroundPolicy.name(state.mode)
                + (state.uidMode == -1 ? "" : "\nUID-wide override: " + BackgroundPolicy.name(state.uidMode) + " (managed elsewhere)")
                + (saved == null ? "\nNo previous setting saved." : "\nPrevious: " + BackgroundPolicy.name(saved.mode))
                + "\n\nRestrict limits Android background activity (RUN_ANY_IN_BACKGROUND). Notifications and jobs may be delayed. "
                + "It does not stop this profile, disable the app or freeze its processes. No setting changes until you choose an action.";
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity).setTitle("Background activity")
                .setMessage(message).setNegativeButton("Keep current", null);
        if (saved != null) {
            dialog.setPositiveButton("Restore previous", (d, which) -> activity.work("Restoring background setting…", () -> {
                policy.restore(user, serial, pkg, state);
                return new Shell.Result(0, "");
            }, () -> Toast.makeText(activity, "Previous background setting restored", Toast.LENGTH_LONG).show(), false));
            dialog.setNeutralButton("Forget saved setting", (d, which) -> new MaterialAlertDialogBuilder(activity)
                    .setTitle("Forget previous setting?")
                    .setMessage(target + "\n\nAndroid's current setting stays unchanged. This removes only App Clone's restore record; Restore will no longer be available.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Forget", (confirm, button) -> activity.work("Forgetting saved setting…", () -> {
                        policy.forget(user, serial, pkg, saved);
                        return new Shell.Result(0, "");
                    }, null, false)).show());
        } else if (state.mode != BackgroundPolicy.RESTRICTED && state.uidMode == -1) {
            dialog.setPositiveButton("Restrict", (d, which) -> activity.work("Restricting background activity…", () -> {
                policy.restrict(user, serial, pkg, state);
                return new Shell.Result(0, "");
            }, () -> Toast.makeText(activity, "Background restricted; previous setting saved", Toast.LENGTH_LONG).show(), false));
        }
        dialog.show();
    }

    private BackgroundControls() { }
}
