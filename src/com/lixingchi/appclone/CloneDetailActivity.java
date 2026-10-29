package com.lixingchi.appclone;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Installed apps in one profile; adding/removing apps is always an explicit action. */
public final class CloneDetailActivity extends InventoryActivity {
    static final String EXTRA_ID = "id";
    static final String EXTRA_SERIAL = "serial";
    private int id;
    private long serial;
    private MaterialToolbar toolbar;
    private Button add;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getIntExtra(EXTRA_ID, -1);
        serial = getIntent().getLongExtra(EXTRA_SERIAL, -1);
        LinearLayout root = Ui.column(this);
        Ui.applyInsets(root, 16, 0, 16, 8);
        toolbar = Ui.toolbar(this, root, "Profile", true);
        toolbar.getMenu().add(0, 1, 0, "Start / stop profile");
        toolbar.getMenu().add(0, 2, 1, "Show system apps").setCheckable(true).setChecked(Settings.systemApps(this));
        toolbar.getMenu().add(0, 3, 2, "Delete profile");
        toolbar.getMenu().add(0, 4, 3, "Refresh");
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 4) { refresh(); return true; }
            Inventory.Profile profile = current();
            if (profile == null) return true;
            if (item.getItemId() == 1) AppControls.power(this, profile);
            else if (item.getItemId() == 2) {
                Settings.systemApps(this, !Settings.systemApps(this)); render();
            } else new MaterialAlertDialogBuilder(this).setTitle("Delete profile?")
                    .setMessage(Inventory.location(profile) + "\n\nDeletes ALL app data in this profile, including system apps hidden by the filter. Other profiles and the main user are untouched.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) ->
                            work("Deleting profile…", () -> Clones.remove(id, serial), this::finish)).show();
            return true;
        });
        status = Ui.text(this, "", 14, R.color.muted, false);
        root.addView(status, Ui.margins(this, 8, 4, 8, 12));
        ScrollView scroll = new ScrollView(this);
        body = Ui.column(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add = Ui.primary(this, "Add app to profile");
        add.setOnClickListener(v -> { Inventory.Profile profile = current(); if (profile != null) AddClone.chooseApp(this, profile); });
        root.addView(add, Ui.margins(this, 0, 8, 0, 0));
        setContentView(root);
    }

    private Inventory.Profile current() {
        Inventory.Profile profile = inventory == null ? null : inventory.profile(id);
        return profile != null && profile.serial == serial ? profile : null;
    }

    @Override void renderInventory() {
        toolbar.getMenu().findItem(2).setChecked(Settings.systemApps(this));
        body.removeAllViews();
        Inventory.Profile profile = current();
        add.setEnabled(profile != null);
        if (profile == null) { status.setText("Profile removed or replaced. Return to Profiles and refresh."); return; }
        toolbar.setTitle(profile.clone.name);
        toolbar.getMenu().findItem(1).setTitle(profile.clone.running ? "Stop profile" : "Start profile");
        int count = 0;
        for (Inventory.Entry entry : inventory.apps) {
            if (!profile.packages.contains(entry.app.pkg) || (entry.system && !Settings.systemApps(this))) continue;
            count++;
            body.addView(AppControls.row(this, entry, profile, true), Ui.margins(this, 0, 4, 0, 4));
        }
        status.setText("User " + id + " · " + (profile.clone.running ? "Running" : "Stopped")
                + " · " + count + " apps" + (Settings.systemApps(this) ? "" : " · System apps hidden"));
        if (count == 0) body.addView(Ui.text(this, "No apps to show. Add an app below or show system apps from the menu.",
                16, R.color.muted, false), Ui.margins(this, 16, 32, 16, 0));
    }
}
