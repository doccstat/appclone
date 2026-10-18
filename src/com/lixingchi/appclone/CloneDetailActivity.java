package com.lixingchi.appclone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shows the apps available to one clone and installs, opens, or removes them. */
public final class CloneDetailActivity extends Activity {
    static final String EXTRA_ID = "id";
    static final String EXTRA_NAME = "name";

    private int userId;
    private String userName;
    private final List<Apps.App> apps = new ArrayList<>();
    private final Set<String> installed = new HashSet<>();
    private final Set<String> busy = new HashSet<>();
    private AppAdapter adapter;
    private TextView status;
    private boolean loading;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        userId = getIntent().getIntExtra(EXTRA_ID, -1);
        userName = getIntent().getStringExtra(EXTRA_NAME);
        if (userName == null) userName = "Clone";

        LinearLayout root = Ui.column(this);
        Ui.applyInsets(root, 20, 22, 20, 16);

        LinearLayout head = Ui.row(this);
        head.addView(Ui.avatar(this, String.valueOf(userId)));
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, userName, 22, R.color.ink, true));
        status = Ui.text(this, "User " + userId, 13, R.color.muted, false);
        titles.addView(status);
        head.addView(titles, Ui.weightWithStart(this, 1, 14));
        root.addView(head);
        root.addView(Ui.label(this, "Apps"), Ui.margins(this, 0, 22, 0, 6));

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.InsetDrawable(
                new android.graphics.drawable.ColorDrawable(getColor(R.color.line)),
                Ui.dp(this, 56), 0, 0, 0));
        list.setDividerHeight(Math.max(1, Ui.dp(this, 1)));
        list.setSelector(Ui.selectable(this));
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button delete = Ui.quietDanger(this, "Delete clone");
        delete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { confirmDelete(); }
        });
        root.addView(delete, Ui.margins(this, 0, 6, 0, 0));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (loading || userId < 0) return;
        loading = true;
        status.setText("User " + userId + " \u00b7 reading apps\u2026");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Apps.App> found = Apps.launcherApps(CloneDetailActivity.this, getPackageName());
                final Set<String> have = Apps.installed(userId);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        loading = false;
                        apps.clear();
                        apps.addAll(found);
                        installed.clear();
                        installed.addAll(have);
                        adapter.notifyDataSetChanged();
                        updateStatus();
                    }
                });
            }
        }, "clone-apps").start();
    }

    private void updateStatus() {
        int count = 0;
        for (Apps.App app : apps) if (installed.contains(app.pkg)) count++;
        status.setText("User " + userId + " \u00b7 " + count + " of " + apps.size() + " apps installed");
    }

    private void toggle(final Apps.App app, final boolean want) {
        if (busy.contains(app.pkg)) return;
        busy.add(app.pkg);
        adapter.notifyDataSetChanged();
        new Thread(new Runnable() {
            @Override public void run() {
                final Shell.Result result = want ? Apps.install(userId, app.pkg)
                        : Apps.uninstall(userId, app.pkg);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy.remove(app.pkg);
                        if (result.ok()) {
                            if (want) {
                                installed.add(app.pkg);
                                if (Settings.autoPin(CloneDetailActivity.this)) {
                                    Shortcuts.pin(CloneDetailActivity.this,
                                            new CloneApps.Target(userId, userName, app));
                                }
                            } else {
                                installed.remove(app.pkg);
                            }
                        } else {
                            Toast.makeText(CloneDetailActivity.this,
                                    app.label + ": " + firstLine(result.out), Toast.LENGTH_LONG).show();
                        }
                        adapter.notifyDataSetChanged();
                        updateStatus();
                    }
                });
            }
        }, "clone-app").start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + userName + "?")
                .setMessage("Removes this clone profile and all of its app data.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { delete(); }
                })
                .show();
    }

    private void delete() {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Deleting\u2026");
        progress.setCancelable(false);
        progress.show();
        new Thread(new Runnable() {
            @Override public void run() {
                Clones.remove(userId);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        progress.dismiss();
                        finish();
                    }
                });
            }
        }, "clone-remove").start();
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Apps.App getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return apps.get(position).pkg.hashCode(); }
        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            AppRow row;
            if (convertView == null) {
                row = new AppRow();
                convertView = row.root;
                convertView.setTag(row);
            } else {
                row = (AppRow) convertView.getTag();
            }
            row.bind(apps.get(position));
            return convertView;
        }
    }

    private final class AppRow {
        final LinearLayout root;
        final CheckBox check;
        final TextView label;
        final TextView meta;
        final Button open;
        Apps.App app;

        AppRow() {
            Activity activity = CloneDetailActivity.this;
            check = new CheckBox(activity);
            check.setClickable(false);
            check.setFocusable(false);
            label = Ui.text(activity, "", 16, R.color.ink, false);
            meta = Ui.text(activity, "", 12, R.color.muted, false);
            open = Ui.quiet(activity, "Open");
            open.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            open.setMinHeight(Ui.dp(activity, 40));
            open.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);

            LinearLayout texts = Ui.column(activity);
            texts.addView(label);
            texts.addView(meta);

            root = Ui.row(activity);
            root.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 10));
            root.addView(check);
            root.addView(texts, Ui.weight(1));
            root.addView(open);

            root.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (app != null) toggle(app, !installed.contains(app.pkg));
                }
            });
            open.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (app == null) return;
                    final Apps.App target = app;
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final Shell.Result result = Apps.launch(userId, target);
                            if (!result.ok()) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        Toast.makeText(CloneDetailActivity.this,
                                                firstLine(result.out), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }, "clone-launch").start();
                }
            });
        }

        void bind(Apps.App value) {
            app = value;
            label.setText(value.label);
            meta.setText(value.pkg);
            boolean isInstalled = installed.contains(value.pkg);
            check.setChecked(isInstalled);
            open.setVisibility(isInstalled ? View.VISIBLE : View.GONE);
            boolean locked = busy.contains(value.pkg);
            root.setEnabled(!locked);
            root.setAlpha(locked ? 0.5f : 1f);
        }
    }
}
