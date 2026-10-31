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
        open.setContentDescription("Open " + entry.app.label + " in " + Inventory.location(profile));
        open.setEnabled(!profile.disabled.contains(entry.app.pkg));
        open.setOnClickListener(v -> activity.startActivity(new Intent(activity, LaunchActivity.class)
                .putExtra(LaunchActivity.EXTRA_USER, profile.clone.id)
                .putExtra(LaunchActivity.EXTRA_SERIAL, profile.serial)
                .putExtra(LaunchActivity.EXTRA_PKG, entry.app.pkg)
                .putExtra(LaunchActivity.EXTRA_ACTIVITY, entry.app.activity)
                .putExtra(LaunchActivity.EXTRA_LABEL, entry.app.label)));
        more.setContentDescription("Actions for " + entry.app.label + " in " + Inventory.location(profile));
        more.setTooltipText("App actions");
        more.setOnClickListener(v -> menu(activity, entry, profile, more).show());
    }

    static PopupMenu menu(InventoryActivity activity, Inventory.Entry entry, Inventory.Profile profile, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        boolean disabled = profile.disabled.contains(entry.app.pkg);
        menu.getMenu().add(0, 1, 0, disabled ? "Enable app" : "Disable app (freeze)");
        menu.getMenu().add(0, 2, 1, "Use manifest default state");
        menu.getMenu().add(0, 3, 2, "Force stop app");
        menu.getMenu().add(0, 4, 3, "Pin shortcut");
        menu.getMenu().add(0, 6, 4, "Background activity");
        menu.getMenu().add(0, 7, 5, "Rename profile");
        menu.getMenu().add(0, 8, 6, "View profile");
        menu.getMenu().add(0, 5, 7, "Remove from this profile");
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
        String title;
        String detail;
        switch (action) {
            case DISABLE:
                title = "Disable app";
                detail = "Stops this app and its notifications until you explicitly enable it. Data is kept. Other apps in this profile keep running.";
                break;
            case ENABLE:
                title = "Enable app";
                detail = "Explicitly enables this app. This does not automatically start a stopped profile.";
                break;
            case DEFAULT:
                title = "Use manifest default";
                detail = "Removes the enabled/disabled override and uses the app manifest's default. It does not reconstruct a previous override.";
                break;
            case FORCE_STOP:
                title = "Force stop app";
                detail = "Stops this app now without disabling it or deleting data. Notifications may stop until it is opened again. This is not a permanent freeze.";
                break;
            default:
                title = "Remove clone app";
                detail = "Deletes this app's data in this profile. The profile, its other apps and the main user's copy are kept.";
        }
        new MaterialAlertDialogBuilder(activity).setTitle(title + "?")
                .setMessage(entry.app.label + "\n" + Inventory.location(profile) + "\n\n" + detail
                        + (entry.system ? "\n\nSystem app: changing it can break other apps in this profile." : ""))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(title, (dialog, which) -> activity.work(title + "…",
                        () -> Apps.change(profile.clone.id, profile.serial, entry.app.pkg, action), null)).show();
    }

    static void power(InventoryActivity activity, Inventory.Profile profile) {
        boolean running = profile.clone.running;
        Runnable change = () -> activity.work(running ? "Stopping profile…" : "Starting profile…", () -> {
            synchronized (Clones.class) {
                Shell.Result checked = Clones.verify(profile.clone.id, profile.serial, Shell::root);
                return checked.ok() ? Clones.setRunning(profile.clone.id, !running) : checked;
            }
        }, null);
        if (!running) { change.run(); return; }
        new MaterialAlertDialogBuilder(activity).setTitle("Stop profile?")
                .setMessage(Inventory.location(profile) + "\n\nStops ALL apps and notifications in this profile, keeping data. "
                        + "Opening an app starts it again. Reboot behavior is controlled in Settings.")
                .setNegativeButton("Cancel", null).setPositiveButton("Stop", (dialog, which) -> change.run()).show();
    }
}
