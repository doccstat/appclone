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
        activity.work("Reading available apps…", () -> {
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
        EditText search = Ui.input(activity, form, "Search apps or packages");
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle("Add clone app")
                .setView(form).setNegativeButton("Cancel", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            Apps.App app = shown.get(position);
            dialog.dismiss();
            if (destination == null) chooseProfile(activity, app);
            else install(activity, app, destination);
        });
        dialog.show();
        if (apps.isEmpty()) search.setHint("No available apps (system filter is in the menu)");
    }

    static void chooseProfile(InventoryActivity activity, Apps.App app) {
        if (activity.inventory == null) { activity.refresh(); return; }
        List<Inventory.Profile> choices = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Inventory.Profile profile : activity.inventory.profiles) if (!profile.packages.contains(app.pkg)) {
            choices.add(profile);
            labels.add(Inventory.location(profile) + (profile.clone.running ? " · Running" : " · Stopped"));
        }
        new MaterialAlertDialogBuilder(activity).setTitle("Add " + app.label + " to…")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> install(activity, app, choices.get(which)))
                .setNeutralButton("New profile", (dialog, which) -> create(activity, app))
                .setNegativeButton("Cancel", null).show();
    }

    private static void install(InventoryActivity activity, Apps.App app, Inventory.Profile profile) {
        new MaterialAlertDialogBuilder(activity).setTitle("Add clone?")
                .setMessage(app.label + "\n" + Inventory.location(profile)
                        + "\n\nUses the existing profile. Other apps and the profile's running state are unchanged.")
                .setNegativeButton("Cancel", null).setPositiveButton("Add", (dialog, which) -> activity.work("Adding clone…",
                        () -> Apps.change(profile.clone.id, profile.serial, app.pkg, Apps.Action.INSTALL),
                        () -> autoPin(activity, app, profile.clone.id, profile.clone.name, profile.serial))).show();
    }

    static void create(InventoryActivity activity, Apps.App app) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 24), 0, Ui.dp(activity, 24), 0);
        EditText name = Ui.input(activity, form, "Profile name");
        name.setText("Clone Profile");
        EditText count = Ui.input(activity, form, "How many profiles (1–500)");
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setText("1");
        com.google.android.material.checkbox.MaterialCheckBox append = new com.google.android.material.checkbox.MaterialCheckBox(activity);
        append.setText("Append -1, -2, … to names");
        append.setChecked(false);
        form.addView(append);
        form.addView(Ui.text(activity, "Creates and starts the requested profiles. Names are kept exactly as entered unless numbering is selected."
                + (app == null ? "" : "\nThen installs " + app.label + ". If installation fails, the new profile is kept."),
                14, R.color.muted, false));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle("New profile")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Create", null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = name.getText().toString();
            List<String> names;
            try {
                List<Clones.Clone> existing = new ArrayList<>();
                if (activity.inventory == null) throw new IllegalArgumentException("Refresh the profile list first.");
                for (Inventory.Profile profile : activity.inventory.profiles) existing.add(profile.clone);
                names = Clones.names(typed, Integer.parseInt(count.getText().toString()), append.isChecked(), existing);
            } catch (IllegalArgumentException error) { count.setError(error.getMessage()); return; }
            dialog.dismiss();
            activity.work("Creating profiles…", () -> {
                int made = 0;
                for (String profileName : names) {
                    int id = Clones.create(profileName);
                    if (id < 0) return new Shell.Result(-1, made + " profiles created.\n" + Clones.lastCreateError
                            + "\nUse Export diagnostics from the main menu for details.");
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
                        return new Shell.Result(-1, made + " profiles created. User " + id + " is kept, but setup failed.\n" + error);
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
