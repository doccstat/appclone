package com.lixingchi.appclone;

import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.function.Supplier;

/** One off-main-thread inventory read per refresh; never polls profiles. */
abstract class InventoryActivity extends AppCompatActivity {
    Inventory inventory;
    TextView status;
    LinearLayout body;
    private boolean loading;
    private boolean reload;
    private AlertDialog progress;

    @Override protected void onResume() { super.onResume(); refresh(); }

    final void refresh() {
        if (loading) { reload = true; return; }
        loading = true;
        inventory = null;
        body.removeAllViews();
        status.setText("Reading clone apps…");
        new Thread(() -> {
            Inventory found = null;
            String error = null;
            try { found = Inventory.load(this); }
            catch (RuntimeException failure) { error = failure.toString(); }
            final Inventory result = found;
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                inventory = result;
                if (failure == null) render();
                else {
                    status.setText("Could not read clone apps: " + failure);
                    android.widget.Button retry = Ui.primary(this, "Retry");
                    retry.setOnClickListener(v -> refresh());
                    body.addView(retry);
                }
                if (reload) { reload = false; refresh(); }
            });
        }, "clone-inventory").start();
    }

    final void work(String title, Supplier<Shell.Result> action, Runnable success) {
        work(title, action, success, true);
    }

    final void work(String title, Supplier<Shell.Result> action, Runnable success, boolean refreshAfter) {
        if (progress != null && progress.isShowing()) return;
        progress = Ui.progress(this, title);
        new Thread(() -> {
            Shell.Result outcome;
            try { outcome = action.get(); }
            catch (RuntimeException error) { outcome = new Shell.Result(-1, error.toString()); }
            final Shell.Result result = outcome;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                if (!result.ok()) new MaterialAlertDialogBuilder(this).setTitle("Action failed")
                        .setMessage(result.trim().isEmpty() ? "Root command failed." : result.trim())
                        .setPositiveButton("Close", null).show();
                else if (success != null) success.run();
                if (refreshAfter && !isFinishing()) refresh();
            });
        }, "clone-action").start();
    }

    abstract void render();

    @Override protected void onDestroy() {
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }
}
