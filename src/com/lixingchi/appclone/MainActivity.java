package com.lixingchi.appclone;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
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
    private final EditText[] searches = new EditText[2];
    private final InventoryList[] pages = new InventoryList[2];
    private Button add;
    private MaterialToolbar toolbar;
    private String pendingReport;
    private static final int REQUEST_EXPORT = 1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = state != null && state.getBoolean("profiles");
        LinearLayout root = Ui.column(this);
        toolbar = Ui.toolbar(this, root, getString(R.string.app_name), false);
        pendingReport = (String) getLastCustomNonConfigurationInstance();
        toolbar.getMenu().add(0, 1, 0, R.string.settings).setIcon(R.drawable.ic_settings)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(0, 2, 1, R.string.show_system_apps).setCheckable(true).setChecked(Settings.systemApps(this));
        toolbar.getMenu().add(0, 3, 2, R.string.refresh).setIcon(R.drawable.ic_refresh)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        toolbar.getMenu().add(0, 4, 3, R.string.export_diagnostics);
        toolbar.getMenu().add(0, 5, 4, R.string.delete_all_profiles);
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
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setBackground(Ui.shape(this, R.color.surface, 24));
        tabs.setSelectedTabIndicator(Ui.shape(this, R.color.accent_tint, 24));
        tabs.setSelectedTabIndicatorColor(Ui.color(this, R.color.accent_tint));
        tabs.setSelectedTabIndicatorGravity(TabLayout.INDICATOR_GRAVITY_STRETCH);
        tabs.setTabIndicatorFullWidth(true);
        tabs.setTabTextColors(Ui.color(this, R.color.muted),
                com.google.android.material.color.MaterialColors.getColor(this,
                        com.google.android.material.R.attr.colorOnPrimaryContainer, "App Clone"));
        root.addView(tabs, Ui.margins(this, 16, 0, 16, 8));
        for (int i = 0; i < pages.length; i++) {
            final int page = i;
            pages[i] = new InventoryList(this);
            if (state != null) pages[i].restorePosition(state.getParcelable("position" + i));
            LinearLayout searchForm = Ui.column(this);
            searches[i] = Ui.search(this, searchForm, getString(i == 0 ? R.string.search_apps_profiles : R.string.search_profiles_ids));
            if (state != null) searches[i].setText(state.getString("query" + i, ""));
            pages[i].header.addView(searchForm, 0);
            searches[i].addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    if (page == (profiles ? 1 : 0)) { render(); pages[page].top(); }
                }
                public void afterTextChanged(Editable s) {}
            });
        }
        useList(pages[profiles ? 1 : 0]);
        ViewPager pager = new ViewPager(this);
        pager.setOffscreenPageLimit(1);
        android.widget.FrameLayout content = Ui.content(this, root, pager);
        add = Ui.addAction(this, content, getString(profiles ? R.string.new_profile : R.string.add_clone_app), pages);
        Ui.applyInsets(root, 0, 0, 0, 0, add);
        add.setOnClickListener(v -> {
            if (inventory == null) { refresh(); return; }
            if (profiles) AddClone.create(this, null); else AddClone.chooseApp(this, null);
        });
        pager.setAdapter(new PagerAdapter() {
            public int getCount() { return pages.length; }
            public boolean isViewFromObject(View view, Object object) { return view == object; }
            public CharSequence getPageTitle(int position) { return getString(position == 0 ? R.string.apps : R.string.profiles); }
            public Object instantiateItem(ViewGroup parent, int position) { parent.addView(pages[position]); return pages[position]; }
            public void destroyItem(ViewGroup parent, int position, Object object) { parent.removeView((View) object); }
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) {
                profiles = position == 1;
                useList(pages[position]);
                render();
            }
        });
        tabs.setupWithViewPager(pager);
        pager.setCurrentItem(profiles ? 1 : 0, false);
        setContentView(root);
    }

    @Override void renderInventory() {
        toolbar.getMenu().findItem(2).setChecked(Settings.systemApps(this));
        add.setText(profiles ? R.string.new_profile : R.string.add_clone_app);
        if (inventory == null) return;
        List<InventoryList.Item> rows = new ArrayList<>();
        String query = searches[profiles ? 1 : 0].getText().toString().toLowerCase(Locale.ROOT);
        int shown = 0;
        if (profiles) {
            int running = 0;
            for (Inventory.Profile profile : inventory.profiles) {
                if (profile.clone.running) running++;
                if (!Inventory.location(this, profile).toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                int count = 0;
                for (Inventory.Entry entry : inventory.apps) if ((!entry.system || Settings.systemApps(this))
                        && profile.packages.contains(entry.app.pkg)) count++;
                rows.add(InventoryList.Item.profile(this, profile, count));
            }
            String summary = getString(R.string.profiles_summary,
                    getResources().getQuantityString(R.plurals.profile_count, inventory.profiles.size(), inventory.profiles.size()),
                    getResources().getQuantityString(R.plurals.running_count, running, running));
            status.setText(Settings.systemApps(this) ? summary
                    : getString(R.string.filtered_summary, summary, getString(R.string.system_apps_hidden)));
        } else {
            for (Inventory.Entry entry : inventory.apps) {
                if (entry.system && !Settings.systemApps(this)) continue;
                StringBuilder locations = new StringBuilder();
                for (Inventory.Profile profile : entry.profiles) {
                    if (locations.length() > 0) locations.append("\n");
                    locations.append(Inventory.location(this, profile));
                }
                if (!(entry.app.label + " " + entry.app.pkg + " " + locations).toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                rows.add(InventoryList.Item.heading(this, entry));
                for (Inventory.Profile profile : entry.profiles) rows.add(InventoryList.Item.instance(this, entry, profile, false));
            }
            status.setText(getString(R.string.filtered_summary,
                    getResources().getQuantityString(R.plurals.cloned_app_count, shown, shown),
                    getString(Settings.systemApps(this) ? R.string.including_system_apps : R.string.system_apps_hidden)));
        }
        if (shown == 0) {
            rows.add(InventoryList.Item.empty(getString(query.isEmpty() ? (profiles ? R.string.no_profiles : R.string.no_cloned_apps) : R.string.no_matches),
                    getString(!query.isEmpty() ? R.string.search_help
                            : profiles ? R.string.no_profiles_help : R.string.no_cloned_apps_help)));
        }
        body.show(rows);
    }

    private void deleteAll() {
        if (inventory == null || inventory.profiles.isEmpty()) return;
        List<Inventory.Profile> targets = new ArrayList<>(inventory.profiles);
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        form.addView(Ui.text(this, getResources().getQuantityString(R.plurals.delete_all_message, targets.size(), targets.size()),
                16, R.color.muted, false));
        String token = getString(R.string.delete_token);
        EditText confirm = Ui.input(this, form, getString(R.string.delete_confirmation, token));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.delete_all_question)
                .setView(scroll).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete_all, null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(-1).setOnClickListener(v -> {
            if (!token.equals(confirm.getText().toString())) { confirm.setError(getString(R.string.delete_confirmation_error, token)); return; }
            dialog.dismiss();
            work(getString(R.string.deleting_profiles), () -> {
                for (Inventory.Profile profile : targets) {
                    Shell.Result result = Clones.remove(profile.clone.id, profile.serial);
                    if (!result.ok()) return new Shell.Result(-1, getString(R.string.delete_batch_failed, Inventory.location(this, profile), result.out));
                }
                return new Shell.Result(0, "");
            }, null);
        }));
        dialog.show();
    }

    private void exportDiagnostics() {
        work(getString(R.string.collecting_diagnostics), () -> { pendingReport = Diagnostics.collect(this); return new Shell.Result(0, ""); }, () -> {
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
                new MaterialAlertDialogBuilder(this).setTitle(R.string.report_unavailable)
                        .setMessage(R.string.report_unavailable_help)
                        .setPositiveButton(R.string.close, null).show();
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IllegalStateException("No output stream");
                out.write(pendingReport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, R.string.diagnostics_saved, Toast.LENGTH_LONG).show();
            } catch (Exception error) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.could_not_save_diagnostics)
                        .setMessage(getString(R.string.error_details, error.toString())).setPositiveButton(R.string.close, null).show();
            }
        }
        pendingReport = null;
    }

    // Keep potentially large reports out of the saved-state Binder transaction.
    @Override public Object onRetainCustomNonConfigurationInstance() { return pendingReport; }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("profiles", profiles);
        for (int i = 0; i < pages.length; i++) {
            state.putString("query" + i, searches[i].getText().toString());
            state.putParcelable("position" + i, pages[i].position());
        }
    }
}
