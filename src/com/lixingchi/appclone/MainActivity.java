package com.lixingchi.appclone;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** App-first inventory, with the complete profile view one tab away. */
public final class MainActivity extends InventoryActivity {
    private boolean profiles;
    private EditText search;
    private Button add;
    private MaterialToolbar toolbar;
    private String pendingReport;
    private static final int REQUEST_EXPORT = 1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = state != null && state.getBoolean("profiles");
        LinearLayout root = Ui.column(this);
        Ui.applyInsets(root, 16, 0, 16, 8);
        toolbar = Ui.toolbar(this, root, "App Clone", false);
        pendingReport = (String) getLastCustomNonConfigurationInstance();
        toolbar.getMenu().add(0, 1, 0, "Settings");
        toolbar.getMenu().add(0, 2, 1, "Show system apps").setCheckable(true).setChecked(Settings.systemApps(this));
        toolbar.getMenu().add(0, 3, 2, "Refresh");
        toolbar.getMenu().add(0, 4, 3, "Export diagnostics");
        toolbar.getMenu().add(0, 5, 4, "Delete all profiles");
        toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: startActivity(new Intent(this, SettingsActivity.class)); break;
                case 2:
                    Settings.systemApps(this, !Settings.systemApps(this)); render(); break;
                case 3: refresh(); break;
                case 4: exportDiagnostics(); break;
                case 5: deleteAll(); break;
                default: return false;
            }
            return true;
        });
        TabLayout tabs = new TabLayout(this);
        tabs.addTab(tabs.newTab().setText("Apps"), !profiles);
        tabs.addTab(tabs.newTab().setText("Profiles"), profiles);
        root.addView(tabs);
        search = Ui.input(this, root, "Search apps, profiles or user IDs");
        search.setId(View.generateViewId());
        if (state != null) search.setText(state.getString("query", ""));
        status = Ui.text(this, "", 14, R.color.muted, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status, Ui.margins(this, 8, 4, 8, 8));
        ScrollView scroll = new ScrollView(this);
        body = Ui.column(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add = Ui.primary(this, profiles ? "New profile" : "Add clone app");
        add.setOnClickListener(v -> {
            if (inventory == null) { refresh(); return; }
            if (profiles) AddClone.create(this, null); else AddClone.chooseApp(this, null);
        });
        root.addView(add, Ui.margins(this, 0, 8, 0, 0));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) { profiles = tab.getPosition() == 1; render(); }
            public void onTabUnselected(TabLayout.Tab tab) {}
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { render(); }
            public void afterTextChanged(Editable s) {}
        });
        setContentView(root);
    }

    @Override void renderInventory() {
        toolbar.getMenu().findItem(2).setChecked(Settings.systemApps(this));
        add.setText(profiles ? "New profile" : "Add clone app");
        if (inventory == null) return;
        body.removeAllViews();
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        int shown = 0;
        if (profiles) {
            int running = 0;
            for (Inventory.Profile profile : inventory.profiles) {
                if (profile.clone.running) running++;
                if (!Inventory.location(profile).toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                LinearLayout row = Ui.column(this);
                row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
                row.setBackground(Ui.shape(this, R.color.surface, 20));
                row.addView(Ui.text(this, Inventory.location(profile), 18, R.color.ink, true));
                int count = 0;
                for (Inventory.Entry entry : inventory.apps) if ((!entry.system || Settings.systemApps(this))
                        && profile.packages.contains(entry.app.pkg)) count++;
                row.addView(Ui.text(this, count + " apps · " + (profile.clone.running ? "Running" : "Stopped"),
                        14, R.color.muted, false));
                LinearLayout actions = Ui.row(this);
                Button manage = Ui.quiet(this, "View apps");
                manage.setOnClickListener(v -> startActivity(new Intent(this, CloneDetailActivity.class)
                        .putExtra(CloneDetailActivity.EXTRA_ID, profile.clone.id)
                        .putExtra(CloneDetailActivity.EXTRA_SERIAL, profile.serial)));
                actions.addView(manage, Ui.weight(1));
                Button power = Ui.quiet(this, profile.clone.running ? "Stop" : "Start");
                power.setContentDescription((profile.clone.running ? "Stop " : "Start ") + Inventory.location(profile));
                power.setOnClickListener(v -> AppControls.power(this, profile));
                actions.addView(power, Ui.weight(1));
                row.addView(actions);
                body.addView(row, Ui.margins(this, 0, 4, 0, 4));
            }
            status.setText(inventory.profiles.size() + " profiles · " + running + " running"
                    + (Settings.systemApps(this) ? "" : " · System apps hidden"));
        } else {
            for (Inventory.Entry entry : inventory.apps) {
                if (entry.system && !Settings.systemApps(this)) continue;
                StringBuilder locations = new StringBuilder();
                for (Inventory.Profile profile : entry.profiles) {
                    if (locations.length() > 0) locations.append("\n");
                    locations.append(Inventory.location(profile));
                }
                if (!(entry.app.label + " " + entry.app.pkg + " " + locations).toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                LinearLayout row = Ui.column(this);
                row.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
                row.setBackground(Ui.rounded(this, R.color.surface, 20));
                row.setFocusable(true);
                row.addView(Ui.appHeading(this, entry, entry.app.label + " · " + entry.profiles.size()
                        + (entry.profiles.size() == 1 ? " clone" : " clones")));
                android.widget.TextView locationText = Ui.text(this, locations, 14, R.color.muted, false);
                locationText.setMaxLines(3);
                locationText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(locationText, Ui.margins(this, 0, 8, 0, 0));
                row.setOnClickListener(v -> startActivity(new Intent(this, AppDetailActivity.class)
                        .putExtra(AppDetailActivity.EXTRA_PKG, entry.app.pkg)));
                body.addView(row, Ui.margins(this, 0, 4, 0, 4));
            }
            status.setText(shown + " cloned apps · " + (Settings.systemApps(this) ? "Including system apps" : "System apps hidden"));
        }
        if (shown == 0) {
            body.addView(Ui.text(this, query.isEmpty() ? (profiles ? "No profiles yet" : "No cloned apps yet") : "No matches",
                    22, R.color.ink, true), Ui.margins(this, 16, 32, 16, 8));
            body.addView(Ui.text(this, profiles ? "Create a profile, or use Apps → Add clone app."
                    : "Only apps installed in a clone appear here. Add one below; system apps are available from the menu.",
                    14, R.color.muted, false), Ui.margins(this, 16, 0, 16, 8));
        }
    }

    private void deleteAll() {
        if (inventory == null || inventory.profiles.isEmpty()) return;
        List<Inventory.Profile> targets = new ArrayList<>(inventory.profiles);
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        form.addView(Ui.text(this, "Deletes all " + targets.size() + " clone profiles and ALL their app data, including hidden system apps. Main user and work/private profiles are untouched.",
                16, R.color.muted, false));
        EditText confirm = Ui.input(this, form, "Type DELETE to confirm");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Delete all profiles?")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Delete all", null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(-1).setOnClickListener(v -> {
            if (!"DELETE".equals(confirm.getText().toString())) { confirm.setError("Type DELETE exactly"); return; }
            dialog.dismiss();
            work("Deleting profiles…", () -> {
                for (Inventory.Profile profile : targets) {
                    Shell.Result result = Clones.remove(profile.clone.id, profile.serial);
                    if (!result.ok()) return new Shell.Result(-1, "Stopped at " + Inventory.location(profile) + ". Earlier removals are not undone.\n" + result.out);
                }
                return new Shell.Result(0, "");
            }, null);
        }));
        dialog.show();
    }

    private void exportDiagnostics() {
        work("Collecting diagnostics…", () -> { pendingReport = Diagnostics.collect(this); return new Shell.Result(0, ""); }, () -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain").putExtra(Intent.EXTRA_TITLE, Diagnostics.fileName());
            startActivityForResult(intent, REQUEST_EXPORT);
        }, false);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (pendingReport == null) {
                new MaterialAlertDialogBuilder(this).setTitle("Report no longer available")
                        .setMessage("The app was restarted. Export diagnostics again to collect a fresh report.")
                        .setPositiveButton("Close", null).show();
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IllegalStateException("No output stream");
                out.write(pendingReport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Diagnostics saved", Toast.LENGTH_LONG).show();
            } catch (Exception error) {
                new MaterialAlertDialogBuilder(this).setTitle("Could not save diagnostics")
                        .setMessage(error.toString()).setPositiveButton("Close", null).show();
            }
        }
        pendingReport = null;
    }

    // Keep potentially large reports out of the saved-state Binder transaction.
    @Override public Object onRetainCustomNonConfigurationInstance() { return pendingReport; }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("profiles", profiles);
        state.putString("query", search.getText().toString());
    }
}
