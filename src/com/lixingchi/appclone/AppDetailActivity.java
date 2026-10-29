package com.lixingchi.appclone;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        Ui.applyInsets(root, 16, 0, 16, 8);
        toolbar = Ui.toolbar(this, root, "Clone app", true);
        status = Ui.text(this, "", 14, R.color.muted, false);
        root.addView(status, Ui.margins(this, 8, 4, 8, 12));
        ScrollView scroll = new ScrollView(this);
        body = Ui.column(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add = Ui.primary(this, "Add another clone");
        add.setOnClickListener(v -> {
            if (inventory == null) return;
            Inventory.Entry entry = inventory.app(pkg);
            if (entry != null) AddClone.chooseProfile(this, entry.app);
        });
        root.addView(add, Ui.margins(this, 0, 8, 0, 0));
        setContentView(root);
    }

    @Override void render() {
        body.removeAllViews();
        Inventory.Entry entry = inventory.app(pkg);
        add.setEnabled(entry != null);
        if (entry == null) { status.setText("This app has no clones. Use Add clone app from Apps to create one."); return; }
        toolbar.setTitle(entry.app.label);
        status.setText(entry.profiles.size() + " clones · " + pkg);
        for (Inventory.Profile profile : entry.profiles) {
            body.addView(AppControls.row(this, entry, profile, false), Ui.margins(this, 0, 4, 0, 4));
        }
    }
}
