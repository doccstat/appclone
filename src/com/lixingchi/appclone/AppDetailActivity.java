package com.lixingchi.appclone;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.appbar.MaterialToolbar;

/** All instances of one package, including disabled apps and stopped profiles. */
public final class AppDetailActivity extends InventoryActivity {
    static final String EXTRA_PKG = "pkg";
    private String pkg;
    private MaterialToolbar toolbar;
    private Button add;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        pkg = getIntent().getStringExtra(EXTRA_PKG);
        LinearLayout root = Ui.column(this);
        toolbar = Ui.toolbar(this, root, "Clone app", true);
        toolbar.getMenu().add("Refresh").setOnMenuItemClickListener(item -> { refresh(); return true; });
        useList(new InventoryList(this));
        if (state != null) body.restorePosition(state.getParcelable("listPosition"));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        add = Ui.addAction(this, root, "Add another clone");
        Ui.applyInsets(root, 0, 0, 0, 0, add);
        add.setOnClickListener(v -> {
            if (inventory == null) return;
            Inventory.Entry entry = inventory.app(pkg);
            if (entry != null) AddClone.chooseProfile(this, entry.app);
        });
        setContentView(root);
    }

    @Override void renderInventory() {
        List<InventoryList.Item> rows = new ArrayList<>();
        Inventory.Entry entry = inventory == null ? null : inventory.app(pkg);
        add.setEnabled(entry != null);
        if (entry == null) {
            status.setText("No clones for this app");
            rows.add(InventoryList.Item.empty("No clones to show", "Return to Apps to add a clone."));
        } else {
            toolbar.setTitle(entry.app.label);
            status.setText(entry.profiles.size() + " clones · " + pkg);
            for (Inventory.Profile profile : entry.profiles) rows.add(InventoryList.Item.instance(entry, profile, false));
        }
        body.show(rows);
    }
}
