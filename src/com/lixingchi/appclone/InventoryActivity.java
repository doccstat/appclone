package com.lixingchi.appclone;

import android.view.View;
import android.os.Bundle;
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
    InventoryList body;
    private CompletableFuture<Inventory> request;
    private boolean systemApps;
    private String readError;
    private AlertDialog progress;

    @Override protected void onResume() {
        super.onResume();
        if (inventory != null && systemApps != Settings.systemApps(this)) display(inventory);
        read(false);
    }

    final void useList(InventoryList list) {
        body = list;
        status = list.status;
        status.setOnClickListener(v -> {
            if (readError != null) new MaterialAlertDialogBuilder(this).setTitle(R.string.could_not_refresh)
                    .setMessage(getString(R.string.error_details, readError)).setNegativeButton(R.string.close, null)
                    .setPositiveButton(R.string.retry, (dialog, which) -> refresh()).show();
        });
    }

    final void refresh() { read(true); }

    private void read(boolean refresh) {
        InventoryCache cache = CloneApplication.inventory(this);
        CompletableFuture<Inventory> next = cache.read(refresh);
        if (request == next) return;
        request = next;
        readError = null;
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
                if (inventory != null) com.google.android.material.snackbar.Snackbar.make(body,
                        R.string.refresh_failed_kept, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> refresh()).show();
            }
        }));
    }

    private void display(Inventory found) {
        inventory = found;
        systemApps = Settings.systemApps(this);
        render();
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
                if (!result.ok()) new MaterialAlertDialogBuilder(this).setTitle(R.string.action_failed)
                        .setMessage(result.trim().isEmpty() ? getString(R.string.root_command_failed) : getString(R.string.error_details, result.trim()))
                        .setPositiveButton(R.string.close, null).show();
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
        body.retry.setVisibility(readError == null ? View.GONE : View.VISIBLE);
        status.setClickable(readError != null);
        status.setFocusable(readError != null);
        status.setContentDescription(null);
        if (readError != null) {
            status.setText(inventory == null ? R.string.read_failed_details : R.string.refresh_failed_details);
            status.setContentDescription(status.getText() + " " + readError);
        } else if (request != null && !request.isDone()) {
            status.setText(inventory == null ? R.string.reading_clone_apps : R.string.refreshing_kept);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("listPosition", body.position());
    }

    abstract void renderInventory();

    @Override protected void onDestroy() {
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }
}
