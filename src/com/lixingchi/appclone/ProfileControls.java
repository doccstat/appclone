package com.lixingchi.appclone;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Profile-wide actions always identify the profile and preserve names verbatim. */
final class ProfileControls {
    static void open(InventoryActivity activity, Inventory.Profile profile) {
        activity.startActivity(new Intent(activity, CloneDetailActivity.class)
                .putExtra(CloneDetailActivity.EXTRA_ID, profile.clone.id)
                .putExtra(CloneDetailActivity.EXTRA_SERIAL, profile.serial));
    }

    static void menu(InventoryActivity activity, Inventory.Profile profile, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, "View apps");
        menu.getMenu().add(0, 2, 1, "Add app");
        menu.getMenu().add(0, 3, 2, "Rename profile");
        menu.getMenu().add(0, 4, 3, profile.clone.running ? "Stop profile" : "Start profile");
        menu.getMenu().add(0, 5, 4, "Delete profile");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: open(activity, profile); break;
                case 2: AddClone.chooseApp(activity, profile); break;
                case 3: rename(activity, profile); break;
                case 4: AppControls.power(activity, profile); break;
                case 5: remove(activity, profile, null); break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }

    static void rename(InventoryActivity activity, Inventory.Profile profile) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 24), 0, Ui.dp(activity, 24), Ui.dp(activity, 8));
        form.addView(Ui.text(activity, "User " + profile.clone.id + " · Serial " + profile.serial
                + "\nChanges the profile name for all its apps. Data and running state stay unchanged. "
                + "Existing shortcut labels stay unchanged; re-pin them if you want new labels.", 14, R.color.muted, false));
        EditText name = Ui.input(activity, form, "Profile name");
        name.setText(profile.clone.name);
        name.selectAll();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle("Rename profile")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = name.getText().toString();
            try { ProfileRename.validate(typed); }
            catch (IllegalArgumentException error) { name.setError(error.getMessage()); return; }
            dialog.dismiss();
            if (typed.equals(profile.clone.name)) return;
            activity.work("Renaming profile…", () -> Clones.rename(profile.clone.id, profile.serial, typed), null);
        }));
        dialog.show();
    }

    static void remove(InventoryActivity activity, Inventory.Profile profile, Runnable success) {
        new MaterialAlertDialogBuilder(activity).setTitle("Delete profile?")
                .setMessage(Inventory.location(profile) + "\n\nDeletes ALL app data in this profile, including system apps hidden by the filter. Other profiles and the main user are untouched.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) ->
                        activity.work("Deleting profile…", () -> Clones.remove(profile.clone.id, profile.serial), success)).show();
    }

    private ProfileControls() { }
}
