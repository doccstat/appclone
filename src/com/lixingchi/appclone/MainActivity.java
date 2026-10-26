package com.lixingchi.appclone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lists clone profiles and creates, starts, stops, opens, or removes them. */
public final class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private List<Clones.Clone> clones = new ArrayList<>();
    private String error;
    private boolean loading;
    private static final int REQUEST_EXPORT = 1;
    private String pendingReport;
    private ProgressDialog stateProgress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        Ui.applyInsets(root, 20, 22, 20, 16);

        LinearLayout head = Ui.row(this);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, "App Clone", 24, R.color.ink, true));
        titles.addView(Ui.text(this, "Run apps in separate users", 13,
                R.color.muted, false), Ui.margins(this, 0, 2, 0, 0));
        head.addView(titles, Ui.weight(1));
        Button more = Ui.quiet(this, "\u22ee");
        more.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        more.setMinWidth(0);
        more.setMinimumWidth(0);
        more.setMinHeight(Ui.dp(this, 44));
        more.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showMenu(view); }
        });
        head.addView(more);
        root.addView(head);

        status = Ui.text(this, "", 13, R.color.muted, false);
        root.addView(status, Ui.margins(this, 0, 4, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 12));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button create = Ui.primary(this, "+  New profile");
        create.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showCreate(); }
        });
        root.addView(create, Ui.margins(this, 0, 10, 0, 0));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        status.setText("Reading clones\u2026");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Clones.Clone> found = Clones.list();
                final String failure = Clones.lastError;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        loading = false;
                        clones = found;
                        error = failure;
                        render();
                    }
                });
            }
        }, "clone-list").start();
    }

    private void render() {
        list.removeAllViews();
        if (error != null) {
            status.setText(error);
        } else {
            int running = 0;
            for (Clones.Clone clone : clones) if (clone.running) running++;
            status.setText(clones.size() + (clones.size() == 1 ? " profile" : " profiles")
                    + " \u00b7 " + running + " running");
        }
        if (clones.isEmpty()) {
            list.addView(empty());
            list.addView(footer());
            return;
        }
        for (int i = 0; i < clones.size(); i++) {
            if (i > 0) list.addView(Ui.divider(this, 58));
            list.addView(row(clones.get(i)));
        }
        list.addView(footer());
    }

    private View empty() {
        LinearLayout box = Ui.column(this);
        box.setPadding(0, Ui.dp(this, 40), 0, 0);
        box.addView(Ui.text(this, "No profiles yet", 18, R.color.ink, true));
        box.addView(Ui.text(this, "Create a clone profile, then choose the apps to install into it.",
                14, R.color.muted, false), Ui.margins(this, 0, 6, 0, 0));
        return box;
    }

    private View footer() {
        LinearLayout box = Ui.column(this);
        box.setPadding(0, Ui.dp(this, 24), 0, 0);
        TextView link = Ui.text(this, "Source code \u00b7 github.com/doccstat/appclone",
                13, R.color.accent, false);
        link.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        link.setBackground(Ui.selectable(this));
        link.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/doccstat/appclone")));
            }
        });
        box.addView(link);
        return box;
    }

    private View row(final Clones.Clone clone) {
        LinearLayout item = Ui.row(this);
        item.setBackground(Ui.selectable(this));
        item.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));

        item.addView(Ui.avatar(this, String.valueOf(clone.id)));

        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, clone.name, 16, R.color.ink, true));
        texts.addView(Ui.text(this, "User " + clone.id + " \u00b7 "
                        + (clone.running ? "Running" : "Stopped"), 13,
                clone.running ? R.color.accent : R.color.muted, false));
        item.addView(texts, Ui.weightWithStart(this, 1, 14));

        Button power = Ui.quiet(this, clone.running ? "Stop" : "Start");
        power.setContentDescription((clone.running ? "Stop " : "Start ") + clone.name
                + ", user " + clone.id);
        power.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (clone.running) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Stop " + clone.name + "?")
                            .setMessage("Stops all apps and notifications in this profile without deleting data. "
                                    + "Start or opening an app restarts it. Settings controls what happens after reboot.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Stop", (dialog, which) -> setRunning(clone.id, false))
                            .show();
                } else {
                    setRunning(clone.id, true);
                }
            }
        });
        item.addView(power);
        item.addView(Ui.text(this, "\u203a", 22, R.color.muted, false));
        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, CloneDetailActivity.class);
                intent.putExtra(CloneDetailActivity.EXTRA_ID, clone.id);
                intent.putExtra(CloneDetailActivity.EXTRA_NAME, clone.name);
                startActivity(intent);
            }
        });
        return item;
    }

    private void setRunning(final int id, final boolean running) {
        stateProgress = new ProgressDialog(this);
        stateProgress.setMessage(running ? "Starting profile\u2026" : "Stopping profile\u2026");
        stateProgress.setCancelable(false);
        stateProgress.show();
        new Thread(() -> {
            final Shell.Result result = Clones.setRunning(id, running);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                stateProgress.dismiss();
                if (!result.ok()) {
                    new AlertDialog.Builder(this)
                            .setTitle(running ? "Could not start profile" : "Could not stop profile")
                            .setMessage(result.trim().isEmpty() ? "Root command failed." : result.trim())
                            .setPositiveButton("Close", null)
                            .show();
                }
                refresh();
            });
        }, "clone-state").start();
    }

    @Override protected void onDestroy() {
        if (stateProgress != null) stateProgress.dismiss();
        super.onDestroy();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Settings");
        menu.getMenu().add(0, 2, 1, "Pin a shortcut");
        menu.getMenu().add(0, 3, 2, "Delete all profiles");
        menu.getMenu().add(0, 4, 3, "Export diagnostics");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } else if (item.getItemId() == 2) {
                    pickShortcut();
                } else if (item.getItemId() == 3) {
                    if (clones.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No clones to delete",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        confirmDeleteAll();
                    }
                } else if (item.getItemId() == 4) {
                    exportDiagnostics();
                } else {
                    return false;
                }
                return true;
            }
        });
        menu.show();
    }

    private void showCreate() {
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        form.addView(Ui.label(this, "Name"));
        final EditText name = new EditText(this);
        name.setText(defaultName());
        name.setSingleLine(true);
        form.addView(name);
        form.addView(Ui.label(this, "How many"), Ui.margins(this, 0, 18, 0, 0));
        final EditText count = new EditText(this);
        count.setText("1");
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        count.setSingleLine(true);
        form.addView(count);
        final CheckBox append = new CheckBox(this);
        append.setText("Append -1, -2, \u2026 to names");
        append.setChecked(false);
        form.addView(append);

        new AlertDialog.Builder(this)
                .setTitle("New profile")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        int howMany;
                        try {
                            howMany = Integer.parseInt(count.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            howMany = 1;
                        }
                        String typed = name.getText().toString().trim();
                        create(typed.isEmpty() ? "Clone" : typed, clamp(howMany), append.isChecked());
                    }
                })
                .show();
    }

    private static int clamp(int howMany) {
        return Math.max(1, Math.min(500, howMany));
    }

    private String defaultName() {
        return "Clone Profile";
    }

    /** Highest existing {@code base-N} suffix plus one, so a second batch does not reuse names. */
    private int nextIndex(String base) {
        int max = 0;
        Pattern pattern = Pattern.compile(Pattern.quote(base) + "-(\\d+)$");
        for (Clones.Clone clone : clones) {
            Matcher matcher = pattern.matcher(clone.name);
            if (matcher.matches()) max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max + 1;
    }

    private void create(final String base, final int howMany, final boolean append) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Creating " + (howMany == 1 ? "clone" : howMany + " clones") + "\u2026");
        progress.setCancelable(false);
        progress.show();
        final int start = append ? nextIndex(base) : 0;
        new Thread(new Runnable() {
            @Override public void run() {
                int made = 0;
                String failure = null;
                for (int i = 0; i < howMany; i++) {
                    String cloneName = append ? base + "-" + (start + i) : base;
                    if (Clones.create(cloneName) < 0) {
                        failure = Clones.lastCreateError != null
                                ? Clones.lastCreateError : "clone creation failed";
                        break;
                    }
                    made++;
                }
                final int madeFinal = made;
                final String failureFinal = failure;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        progress.dismiss();
                        if (failureFinal != null) {
                            showCreateFailure(madeFinal, failureFinal);
                        } else {
                            Toast.makeText(MainActivity.this,
                                    madeFinal + (madeFinal == 1 ? " profile created" : " profiles created"),
                                    Toast.LENGTH_LONG).show();
                        }
                        refresh();
                    }
                });
            }
        }, "clone-create").start();
    }

    private void showCreateFailure(int made, String error) {
        String message = (made > 0 ? made + (made == 1 ? " profile" : " profiles")
                + " created before the failure.\n\n" : "")
                + error + "\n\nExport the diagnostics file and attach it to the issue.";
        new AlertDialog.Builder(this)
                .setTitle("Clone creation failed")
                .setMessage(message)
                .setPositiveButton("Export logs", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        exportDiagnostics();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void exportDiagnostics() {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Collecting diagnostics\u2026");
        progress.setCancelable(false);
        progress.show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String report = Diagnostics.collect(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        progress.dismiss();
                        pendingReport = report;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TITLE, Diagnostics.fileName());
                        startActivityForResult(intent, REQUEST_EXPORT);
                    }
                });
            }
        }, "diagnostics").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || pendingReport == null) {
            pendingReport = null;
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IllegalStateException("no output stream");
            out.write(pendingReport.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Diagnostics saved. Attach the file to the issue.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save diagnostics: " + e,
                    Toast.LENGTH_LONG).show();
        } finally {
            pendingReport = null;
        }
    }

    private void pickShortcut() {
        final List<CloneApps.Group> groups = CloneApps.pinGroups(this);
        if (groups.isEmpty()) {
            Toast.makeText(this, "No clone apps to pin", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            CloneApps.Group group = groups.get(i);
            labels[i] = group.targets.size() == 1
                    ? group.app.label + "  \u00b7  " + group.targets.get(0).name
                    : group.app.label + "  \u00b7  " + group.targets.size() + " profiles";
        }
        new AlertDialog.Builder(this)
                .setTitle("Pin a clone app")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        CloneApps.Group group = groups.get(which);
                        if (group.targets.size() == 1) {
                            Shortcuts.pin(MainActivity.this, group.targets.get(0));
                        } else {
                            pickProfile(group);
                        }
                    }
                })
                .show();
    }

    private void pickProfile(final CloneApps.Group group) {
        CharSequence[] labels = new CharSequence[group.targets.size()];
        for (int i = 0; i < group.targets.size(); i++) {
            CloneApps.Target target = group.targets.get(i);
            labels[i] = target.name + "  \u00b7  User " + target.userId;
        }
        new AlertDialog.Builder(this)
                .setTitle(group.app.label)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Shortcuts.pin(MainActivity.this, group.targets.get(which));
                    }
                })
                .show();
    }

    private void confirmDeleteAll() {
        final String expected = "DELETE";
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        form.addView(Ui.text(this, "Removes all " + clones.size() + " profiles and their "
                + "app data. Your main user is not affected.", 14, R.color.muted, false));
        form.addView(Ui.text(this, "Type " + expected + " to confirm", 12, R.color.muted, true),
                Ui.margins(this, 0, 18, 0, 4));
        final EditText confirm = new EditText(this);
        confirm.setSingleLine(true);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        confirm.setHint(expected);
        form.addView(confirm);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete all profiles?")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete all", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface unused) {
                final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positive.setEnabled(false);
                positive.setTextColor(getColor(R.color.danger));
                confirm.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {
                        positive.setEnabled(expected.contentEquals(s.toString().trim()));
                    }
                });
                positive.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        dialog.dismiss();
                        deleteAll();
                    }
                });
            }
        });
        dialog.show();
    }

    private void deleteAll() {
        new Thread(new Runnable() {
            @Override public void run() {
                for (Clones.Clone clone : new ArrayList<>(clones)) Clones.remove(clone.id);
                runOnUiThread(new Runnable() {
                    @Override public void run() { refresh(); }
                });
            }
        }, "clone-delete").start();
    }
}
