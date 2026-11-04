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
        toolbar = Ui.toolbar(this, root, getString(R.string.profile), true);
        toolbar.getMenu().add(0, 1, 0, R.string.start_stop_profile);
        toolbar.getMenu().add(0, 2, 1, R.string.show_system_apps).setCheckable(true).setChecked(Settings.systemApps(this));
        toolbar.getMenu().add(0, 3, 2, R.string.delete_profile);
        toolbar.getMenu().add(0, 4, 3, R.string.refresh).setIcon(R.drawable.ic_refresh)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        toolbar.getMenu().add(0, 5, 0, R.string.rename_profile);
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
        android.widget.FrameLayout content = Ui.content(this, root, body);
        add = Ui.addAction(this, content, getString(R.string.add_app_to_profile), body);
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
            status.setText(R.string.profile_replaced);
            rows.add(InventoryList.Item.empty(getString(R.string.profile_unavailable), getString(R.string.return_to_profiles)));
            body.show(rows);
            return;
        }
        toolbar.setTitle(profile.clone.name);
        toolbar.getMenu().findItem(1).setTitle(profile.clone.running ? R.string.stop_profile : R.string.start_profile);
        int count = 0;
        for (Inventory.Entry entry : inventory.apps) {
            if (!profile.packages.contains(entry.app.pkg) || (entry.system && !Settings.systemApps(this))) continue;
            count++;
            rows.add(InventoryList.Item.instance(this, entry, profile, true));
        }
        String summary = getString(R.string.profile_status, Integer.toString(id),
                getString(profile.clone.running ? R.string.running : R.string.stopped),
                getResources().getQuantityString(R.plurals.app_count, count, count));
        status.setText(Settings.systemApps(this) ? summary
                : getString(R.string.filtered_summary, summary, getString(R.string.system_apps_hidden)));
        if (count == 0) rows.add(InventoryList.Item.empty(getString(R.string.no_apps_to_show), getString(R.string.no_apps_help)));
        body.show(rows);
    }
}
