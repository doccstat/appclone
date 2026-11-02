package com.lixingchi.appclone;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The same explicitly profile-scoped actions in both navigation directions. */
final class AppControls {
    static void bind(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile,
                     Button open, View more) {
        open.setContentDescription(activity.getString(R.string.open_in_profile, Ui.bidi(activity, entry.app.label), Inventory.location(activity, profile)));
        open.setEnabled(!profile.disabled.contains(entry.app.pkg));
        open.setOnClickListener(v -> activity.startActivity(new Intent(activity, LaunchActivity.class)
                .putExtra(LaunchActivity.EXTRA_USER, profile.clone.id)
                .putExtra(LaunchActivity.EXTRA_SERIAL, profile.serial)
                .putExtra(LaunchActivity.EXTRA_PKG, entry.app.pkg)
                .putExtra(LaunchActivity.EXTRA_ACTIVITY, entry.app.activity)
                .putExtra(LaunchActivity.EXTRA_LABEL, entry.app.label)));
        more.setContentDescription(activity.getString(R.string.app_actions_in_profile, Ui.bidi(activity, entry.app.label), Inventory.location(activity, profile)));
        more.setTooltipText(activity.getString(R.string.app_actions));
        more.setOnClickListener(v -> menu(activity, entry, profile, more).show());
    }

    static PopupMenu menu(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        boolean disabled = profile.disabled.contains(entry.app.pkg);
        menu.getMenu().add(0, 1, 0, disabled ? R.string.enable_app : R.string.disable_app_freeze);
        menu.getMenu().add(0, 2, 1, R.string.manifest_default_state);
        menu.getMenu().add(0, 3, 2, R.string.force_stop_app);
        menu.getMenu().add(0, 4, 3, R.string.pin_shortcut);
        menu.getMenu().add(0, 6, 4, R.string.background_activity);
        menu.getMenu().add(0, 7, 5, R.string.rename_profile);
        menu.getMenu().add(0, 8, 6, R.string.view_profile);
        menu.getMenu().add(0, 5, 7, R.string.remove_from_profile);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 7) { ProfileControls.rename(activity, profile); return true; }
            if (item.getItemId() == 8) { ProfileControls.open(activity, profile); return true; }
            if (item.getItemId() == 6) { BackgroundControls.open(activity, entry, profile); return true; }
            if (item.getItemId() == 4) {
                Shortcuts.pin(activity, new CloneApps.Target(profile.clone.id, profile.clone.name, entry.app, profile.serial));
                return true;
            }
            Apps.Action action;
            switch (item.getItemId()) {
                case 1: action = disabled ? Apps.Action.ENABLE : Apps.Action.DISABLE; break;
                case 2: action = Apps.Action.DEFAULT; break;
                case 3: action = Apps.Action.FORCE_STOP; break;
                case 5: action = Apps.Action.REMOVE; break;
                default: return false;
            }
            confirm(activity, entry, profile, action);
            return true;
        });
        return menu;
    }

    static void confirm(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile, Apps.Action action) {
        int titleRes;
        int detailRes;
        switch (action) {
            case DISABLE:
                titleRes = R.string.disable_app;
                detailRes = R.string.disable_app_help;
                break;
            case ENABLE:
                titleRes = R.string.enable_app;
                detailRes = R.string.enable_app_help;
                break;
            case DEFAULT:
                titleRes = R.string.manifest_default;
                detailRes = R.string.manifest_default_help;
                break;
            case FORCE_STOP:
                titleRes = R.string.force_stop_app;
                detailRes = R.string.force_stop_help;
                break;
            default:
                titleRes = R.string.remove_clone_app;
                detailRes = R.string.remove_clone_app_help;
        }
        String title = activity.getString(titleRes);
        String detail = activity.getString(detailRes);
        new MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.confirm_action, title))
                .setMessage(activity.getString(R.string.app_action_message, Ui.bidi(activity, entry.app.label), Inventory.location(activity, profile),
                        entry.system ? activity.getString(R.string.system_app_warning, detail) : detail))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(title, (dialog, which) -> activity.work(activity.getString(R.string.action_progress, title),
                        () -> Apps.change(profile.clone.id, profile.serial, entry.app.pkg, action), null)).show();
    }

    static void power(InventoryActivity activity, Inventory.Profile profile) {
        boolean running = profile.clone.running;
        Runnable change = () -> activity.work(activity.getString(running ? R.string.stopping_profile : R.string.starting_profile), () -> {
            synchronized (Clones.class) {
                Shell.Result checked = Clones.verify(profile.clone.id, profile.serial, Shell::root);
                return checked.ok() ? Clones.setRunning(profile.clone.id, !running) : checked;
            }
        }, null);
        if (!running) { change.run(); return; }
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.stop_profile_question)
                .setMessage(activity.getString(R.string.stop_profile_message, Inventory.location(activity, profile)))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.stop, (dialog, which) -> change.run()).show();
    }
}
