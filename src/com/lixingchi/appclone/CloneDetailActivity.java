package com.lixingchi.appclone;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.appbar.MaterialToolbar;

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
        toolbar = Ui.toolbar(this, root, "Profile", true);
        toolbar.getMenu().add(0, 1, 0, "Start / stop profile");
        toolbar.getMenu().add(0, 2, 1, "Show system apps").setCheckable(true).setChecked(Settings.systemApps(this));
        toolbar.getMenu().add(0, 3, 2, "Delete profile");
        toolbar.getMenu().add(0, 4, 3, "Refresh");
        toolbar.getMenu().add(0, 5, 0, "Rename profile");
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 4) { refresh(); return true; }
            Inventory.Profile profile = current();
            if (profile == null) return true;
            if (item.getItemId() == 1) AppControls.power(this, profile);
            else if (item.getItemId() == 2) {
                Settings.systemApps(this, !Settings.systemApps(this)); render();
            } else if (item.getItemId() == 5) ProfileControls.rename(this, profile);
            else if (item.getItemId() == 3) ProfileControls.remove(this, profile, this::finish);
            return true;
        });
        useList(new InventoryList(this));
        if (state != null) body.restorePosition(state.getParcelable("listPosition"));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        add = Ui.addAction(this, root, "Add app to profile");
        Ui.applyInsets(root, 0, 0, 0, 0, add);
        add.setOnClickListener(v -> { Inventory.Profile profile = current(); if (profile != null) AddClone.chooseApp(this, profile); });
        setContentView(root);
    }

    private Inventory.Profile current() {
        Inventory.Profile profile = inventory == null ? null : inventory.profile(id);
        return profile != null && profile.serial == serial ? profile : null;
    }

    @Override void renderInventory() {
        toolbar.getMenu().findItem(2).setChecked(Settings.systemApps(this));
        List<InventoryList.Item> rows = new ArrayList<>();
        Inventory.Profile profile = current();
        add.setEnabled(profile != null);
        if (profile == null) {
            status.setText("Profile removed or replaced. Return to Profiles and refresh.");
            rows.add(InventoryList.Item.empty("Profile unavailable", "Return to Profiles and refresh before taking any action."));
            body.show(rows);
            return;
        }
        toolbar.setTitle(profile.clone.name);
        toolbar.getMenu().findItem(1).setTitle(profile.clone.running ? "Stop profile" : "Start profile");
        int count = 0;
        for (Inventory.Entry entry : inventory.apps) {
            if (!profile.packages.contains(entry.app.pkg) || (entry.system && !Settings.systemApps(this))) continue;
            count++;
            rows.add(InventoryList.Item.instance(entry, profile, true));
        }
        status.setText("User " + id + " · " + (profile.clone.running ? "Running" : "Stopped")
                + " · " + count + " apps" + (Settings.systemApps(this) ? "" : " · System apps hidden"));
        if (count == 0) rows.add(InventoryList.Item.empty("No apps to show", "Add an app or show system apps from the menu."));
        body.show(rows);
    }
}
