package com.lixingchi.appclone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;

/** Shortcut trampoline: start the clone, wait for it to unlock, then launch the app. */
public final class LaunchActivity extends Activity {
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_USER = "user";
    static final String EXTRA_ACTIVITY = "activity";
    static final String EXTRA_LABEL = "label";

    private ProgressDialog progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int user = getIntent().getIntExtra(EXTRA_USER, -1);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        String activity = getIntent().getStringExtra(EXTRA_ACTIVITY);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        final Apps.App app = new Apps.App(label == null ? "" : label, pkg, activity);
        progress = new ProgressDialog(this);
        progress.setMessage("Opening clone app\u2026");
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            final Shell.Result result = Apps.launch(user, app);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                if (result.ok()) {
                    finish();
                } else {
                    new AlertDialog.Builder(this)
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
