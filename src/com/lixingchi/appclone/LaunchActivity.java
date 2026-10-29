package com.lixingchi.appclone;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.os.Bundle;

/** Shortcut trampoline: start the clone, wait for it to unlock, then launch the app. */
public final class LaunchActivity extends AppCompatActivity {
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_USER = "user";
    static final String EXTRA_ACTIVITY = "activity";
    static final String EXTRA_LABEL = "label";
    static final String EXTRA_SERIAL = "serial";

    private AlertDialog progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int user = getIntent().getIntExtra(EXTRA_USER, -1);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        String activity = getIntent().getStringExtra(EXTRA_ACTIVITY);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        final Apps.App app = new Apps.App(label == null ? "" : label, pkg, activity);
        long serial = getIntent().getLongExtra(EXTRA_SERIAL, -1);
        progress = Ui.progress(this, "Opening clone app…");
        new Thread(() -> {
            Shell.Result outcome;
            try {
                synchronized (Clones.class) {
                    Shell.Result checked = serial < 0 ? new Shell.Result(0, "") : Clones.verify(user, serial, Shell::root);
                    outcome = checked.ok() ? Apps.launch(user, app) : checked;
                }
            } catch (RuntimeException error) { outcome = new Shell.Result(-1, error.toString()); }
            final Shell.Result result = outcome;
            CloneApplication.inventory(this).afterLaunch(user, result.ok());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                if (result.ok()) {
                    finish();
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Could not open clone app")
                            .setMessage(result.trim().isEmpty() ? "Root command failed." : result.trim())
                            .setPositiveButton("Close", null)
                            .setOnDismissListener(dialog -> finish())
                            .show();
                }
            });
        }, "clone-launch").start();
    }

    @Override protected void onDestroy() {
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }
}
