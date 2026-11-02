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
        menu.getMenu().add(0, 1, 0, R.string.view_apps);
        menu.getMenu().add(0, 2, 1, R.string.add_app);
        menu.getMenu().add(0, 3, 2, R.string.rename_profile);
        menu.getMenu().add(0, 4, 3, profile.clone.running ? R.string.stop_profile : R.string.start_profile);
        menu.getMenu().add(0, 5, 4, R.string.delete_profile);
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
        form.addView(Ui.text(activity, activity.getString(R.string.rename_profile_help,
                Integer.toString(profile.clone.id), Long.toString(profile.serial)), 14, R.color.muted, false));
        EditText name = Ui.input(activity, form, activity.getString(R.string.profile_name));
        name.setText(profile.clone.name);
        name.selectAll();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.rename_profile)
                .setView(scroll).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = name.getText().toString();
            try { ProfileRename.validate(typed); }
            catch (IllegalArgumentException error) { name.setError(activity.getString(R.string.invalid_profile_name)); return; }
            dialog.dismiss();
            if (typed.equals(profile.clone.name)) return;
            activity.work(activity.getString(R.string.renaming_profile), () -> Clones.rename(profile.clone.id, profile.serial, typed), null);
        }));
        dialog.show();
    }

    static void remove(InventoryActivity activity, Inventory.Profile profile, Runnable success) {
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.delete_profile_question)
                .setMessage(activity.getString(R.string.delete_profile_message, Inventory.location(activity, profile)))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (dialog, which) ->
                        activity.work(activity.getString(R.string.deleting_profile), () -> Clones.remove(profile.clone.id, profile.serial), success)).show();
    }

    private ProfileControls() { }
}
