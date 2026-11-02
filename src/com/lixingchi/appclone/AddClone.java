package com.lixingchi.appclone;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AddClone {
    static void chooseApp(InventoryActivity activity, Inventory.Profile destination) {
        if (activity.inventory == null) return;
        final List<Apps.App> apps = new ArrayList<>();
        activity.work(activity.getString(R.string.reading_available_apps), () -> {
            PackageManager pm = activity.getPackageManager();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if (info.packageName.equals(activity.getPackageName())) continue;
                if (!Settings.systemApps(activity) && (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (destination != null && destination.packages.contains(info.packageName)) continue;
                apps.add(new Apps.App(String.valueOf(pm.getApplicationLabel(info)), info.packageName, ""));
            }
            apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            return new Shell.Result(0, "");
        }, () -> picker(activity, apps, destination), false);
    }

    private static void picker(InventoryActivity activity, List<Apps.App> apps, Inventory.Profile destination) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 24), 0, Ui.dp(activity, 24), 0);
        EditText search = Ui.input(activity, form, activity.getString(R.string.search_apps_packages));
        ListView list = new ListView(activity);
        form.addView(list, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 320)));
        List<Apps.App> shown = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String query = search.getText().toString().toLowerCase(Locale.ROOT);
            shown.clear(); adapter.clear();
            for (Apps.App app : apps) if ((app.label + " " + app.pkg).toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(app); adapter.add(app.label + "\n" + app.pkg);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter.run(); }
            public void afterTextChanged(Editable s) {}
        });
        filter.run();
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.add_clone_app)
                .setView(form).setNegativeButton(R.string.cancel, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            Apps.App app = shown.get(position);
            dialog.dismiss();
            if (destination == null) chooseProfile(activity, app);
            else install(activity, app, destination);
        });
        dialog.show();
        if (apps.isEmpty()) search.setHint(R.string.no_available_apps);
    }

    static void chooseProfile(InventoryActivity activity, Apps.App app) {
        if (activity.inventory == null) { activity.refresh(); return; }
        List<Inventory.Profile> choices = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Inventory.Profile profile : activity.inventory.profiles) if (!profile.packages.contains(app.pkg)) {
            choices.add(profile);
            labels.add(activity.getString(R.string.profile_picker_item, Inventory.location(activity, profile),
                    activity.getString(profile.clone.running ? R.string.running : R.string.stopped)));
        }
        new MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.add_app_to, Ui.bidi(activity, app.label)))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> install(activity, app, choices.get(which)))
                .setNeutralButton(R.string.new_profile, (dialog, which) -> create(activity, app))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private static void install(InventoryActivity activity, Apps.App app, Inventory.Profile profile) {
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.add_clone_question)
                .setMessage(activity.getString(R.string.add_clone_message, Ui.bidi(activity, app.label), Inventory.location(activity, profile)))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.add, (dialog, which) -> activity.work(activity.getString(R.string.adding_clone),
                        () -> Apps.change(profile.clone.id, profile.serial, app.pkg, Apps.Action.INSTALL),
                        () -> autoPin(activity, app, profile.clone.id, profile.clone.name, profile.serial))).show();
    }

    static void create(InventoryActivity activity, Apps.App app) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 24), 0, Ui.dp(activity, 24), 0);
        EditText name = Ui.input(activity, form, activity.getString(R.string.profile_name));
        name.setText(R.string.default_profile_name);
        EditText count = Ui.input(activity, form, activity.getString(R.string.profile_quantity));
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setText("1");
        com.google.android.material.checkbox.MaterialCheckBox append = new com.google.android.material.checkbox.MaterialCheckBox(activity);
        append.setText(R.string.append_numbering);
        append.setChecked(false);
        form.addView(append);
        String help = activity.getString(R.string.create_profiles_help);
        form.addView(Ui.text(activity, app == null ? help
                : activity.getString(R.string.create_profiles_app_help, help, Ui.bidi(activity, app.label)),
                14, R.color.muted, false));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.new_profile)
                .setView(scroll).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.create, null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = name.getText().toString();
            List<String> names;
            try {
                List<Clones.Clone> existing = new ArrayList<>();
                if (activity.inventory == null) throw new IllegalArgumentException(activity.getString(R.string.refresh_profiles_first));
                for (Inventory.Profile profile : activity.inventory.profiles) existing.add(profile.clone);
                names = Clones.names(typed, Integer.parseInt(count.getText().toString()), append.isChecked(), existing);
            } catch (IllegalArgumentException error) { count.setError(activity.getString(R.string.invalid_profile_input, error.getMessage())); return; }
            dialog.dismiss();
            activity.work(activity.getString(R.string.creating_profiles), () -> {
                int made = 0;
                for (String profileName : names) {
                    int id = Clones.create(profileName);
                    if (id < 0) return new Shell.Result(-1, activity.getResources().getQuantityString(
                            R.plurals.create_profiles_failed, made, made, Clones.lastCreateError));
                    made++;
                    try {
                        long serial = Inventory.serial(activity, id);
                        synchronized (Clones.class) {
                            Shell.Result checked = Clones.verify(id, serial, Shell::root);
                            if (!checked.ok()) throw new IllegalStateException(checked.out);
                            Shell.Result started = Clones.setRunning(id, true);
                            if (!started.ok()) throw new IllegalStateException(started.out);
                        }
                        if (app != null) {
                            Shell.Result installed = Apps.change(id, serial, app.pkg, Apps.Action.INSTALL);
                            if (!installed.ok()) throw new IllegalStateException(installed.out);
                            if (Settings.autoPin(activity)) activity.runOnUiThread(() -> {
                                if (!activity.isFinishing() && !activity.isDestroyed()) autoPin(activity, app, id, profileName, serial);
                            });
                        }
                    } catch (RuntimeException error) {
                        return new Shell.Result(-1, activity.getResources().getQuantityString(
                                R.plurals.profile_setup_failed, made, made, Integer.toString(id), error.toString()));
                    }
                }
                return new Shell.Result(0, "");
            }, null);
        }));
        dialog.show();
    }

    private static void autoPin(InventoryActivity activity, Apps.App app, int id, String name, long serial) {
        if (Settings.autoPin(activity)) Shortcuts.pin(activity, new CloneApps.Target(id, name, app, serial));
    }
}
