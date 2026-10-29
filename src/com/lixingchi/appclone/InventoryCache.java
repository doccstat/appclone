package com.lixingchi.appclone;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Process-local snapshot: navigation reuses it; mutations and Refresh invalidate it. */
final class InventoryCache {
    private final Supplier<Inventory> load;
    private final Executor executor;
    private CompletableFuture<Inventory> pending;
    private Inventory previous;
    private boolean dirty;

    InventoryCache(Supplier<Inventory> load, Executor executor) {
        this.load = load;
        this.executor = executor;
    }

    synchronized CompletableFuture<Inventory> read(boolean refresh) {
        if (refresh && (pending == null || pending.isDone())) dirty = true;
        if (pending == null || dirty) {
            previous = peek();
            dirty = false;
            // Chain after an obsolete read instead of scanning profiles concurrently.
            pending = pending == null ? CompletableFuture.supplyAsync(load, executor)
                    : pending.handleAsync((value, error) -> load.get(), executor);
        }
        return pending;
    }

    synchronized Inventory peek() {
        return pending != null && pending.isDone() && !pending.isCompletedExceptionally()
                ? pending.getNow(null) : previous;
    }

    synchronized boolean current(CompletableFuture<Inventory> request) {
        return pending == request && !dirty;
    }

    synchronized void invalidate() { dirty = true; }

    synchronized void afterLaunch(int user, boolean success) {
        Inventory known = peek();
        Inventory.Profile profile = known == null ? null : known.profile(user);
        if (!success || pending == null || !pending.isDone() || pending.isCompletedExceptionally()
                || profile == null || !profile.clone.running) {
            invalidate();
        }
    }
}
