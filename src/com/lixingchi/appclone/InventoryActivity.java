package com.lixingchi.appclone;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Shares inventory reads across screens without clearing the last displayed list. */
abstract class InventoryActivity extends AppCompatActivity {
    Inventory inventory;
    TextView status;
    LinearLayout body;
    private CompletableFuture<Inventory> request;
    private boolean systemApps;
    private Button retry;
    private String readError;
    private AlertDialog progress;

    @Override protected void onResume() {
        super.onResume();
        if (inventory != null && systemApps != Settings.systemApps(this)) display(inventory);
        read(false);
    }

    final void refresh() { read(true); }

    private void read(boolean refresh) {
        InventoryCache cache = CloneApplication.inventory(this);
        CompletableFuture<Inventory> next = cache.read(refresh);
        if (request == next) return;
        request = next;
        readError = null;
        if (retry != null) { body.removeView(retry); retry = null; }
        if (inventory == null && (!next.isDone() || next.isCompletedExceptionally()) && cache.peek() != null) {
            display(cache.peek());
        }
        showReadState();
        next.whenComplete((result, failure) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || request != next) return;
            if (!cache.current(next)) { read(false); return; }
            if (failure == null) display(result);
            else {
                readError = (failure.getCause() == null ? failure : failure.getCause()).toString();
                showReadState();
            }
        }));
    }

    private void display(Inventory found) {
        View scroll = (View) body.getParent();
        int y = scroll.getScrollY();
        inventory = found;
        systemApps = Settings.systemApps(this);
        render();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    final void work(String title, Supplier<Shell.Result> action, Runnable success) {
        work(title, action, success, true);
    }

    final void work(String title, Supplier<Shell.Result> action, Runnable success, boolean refreshAfter) {
        if (progress != null && progress.isShowing()) return;
        progress = Ui.progress(this, title);
        InventoryCache cache = CloneApplication.inventory(this);
        new Thread(() -> {
            Shell.Result outcome;
            try { outcome = action.get(); }
            catch (RuntimeException error) { outcome = new Shell.Result(-1, error.toString()); }
            finally { if (refreshAfter) cache.invalidate(); }
            final Shell.Result result = outcome;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                if (!result.ok()) new MaterialAlertDialogBuilder(this).setTitle("Action failed")
                        .setMessage(result.trim().isEmpty() ? "Root command failed." : result.trim())
                        .setPositiveButton("Close", null).show();
                else if (success != null) success.run();
                if (refreshAfter && !isFinishing()) read(false);
            });
        }, "clone-action").start();
    }

    final void render() {
        renderInventory();
        showReadState();
    }

    private void showReadState() {
        if (readError != null) {
            status.setText((inventory == null ? "Could not read clone apps: " : "Refresh failed; showing previous list: ") + readError);
            if (retry == null) {
                retry = Ui.primary(this, "Retry");
                retry.setOnClickListener(v -> refresh());
            }
            if (retry.getParent() == null) body.addView(retry);
        } else if (request != null && !request.isDone()) {
            status.setText(inventory == null ? "Reading clone apps…" : "Refreshing… Showing previous list.");
        }
    }

    abstract void renderInventory();

    @Override protected void onDestroy() {
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }
}
