package com.lixingchi.appclone;

import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Reads policy only when requested, not during inventory scans or navigation. */
final class BackgroundControls {
    static void open(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile) {
        BackgroundPolicy policy = BackgroundPolicy.get(activity);
        BackgroundPolicy.State[] state = new BackgroundPolicy.State[1];
        activity.work(activity.getString(R.string.reading_background), () -> {
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
        String target = Ui.bidi(activity, entry.app.label) + "\n" + Inventory.location(activity, profile);
        String message = target + "\n\n" + activity.getString(R.string.background_current, BackgroundPolicy.name(activity, state.mode))
                + (state.uidMode == -1 ? "" : "\n" + activity.getString(R.string.background_uid_override, BackgroundPolicy.name(activity, state.uidMode)))
                + "\n" + (saved == null ? activity.getString(R.string.background_no_previous)
                        : activity.getString(R.string.background_previous, BackgroundPolicy.name(activity, saved.mode)))
                + "\n\n" + activity.getString(R.string.background_help);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.background_activity)
                .setMessage(message).setNegativeButton(R.string.keep_current, null);
        if (saved != null) {
            dialog.setPositiveButton(R.string.restore_previous, (d, which) -> activity.work(activity.getString(R.string.restoring_background), () -> {
                policy.restore(user, serial, pkg, state);
                return new Shell.Result(0, "");
            }, () -> Toast.makeText(activity, R.string.background_restored, Toast.LENGTH_LONG).show(), false));
            dialog.setNeutralButton(R.string.forget_saved_setting, (d, which) -> new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.forget_previous_question)
                    .setMessage(activity.getString(R.string.forget_previous_message, target))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.forget, (confirm, button) -> activity.work(activity.getString(R.string.forgetting_setting), () -> {
                        policy.forget(user, serial, pkg, saved);
                        return new Shell.Result(0, "");
                    }, null, false)).show());
        } else if (state.mode != BackgroundPolicy.RESTRICTED && state.uidMode == -1) {
            dialog.setPositiveButton(R.string.restrict, (d, which) -> activity.work(activity.getString(R.string.restricting_background), () -> {
                policy.restrict(user, serial, pkg, state);
                return new Shell.Result(0, "");
            }, () -> Toast.makeText(activity, R.string.background_restricted, Toast.LENGTH_LONG).show(), false));
        }
        dialog.show();
    }

    private BackgroundControls() { }
}
