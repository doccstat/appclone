package com.lixingchi.appclone;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public final class InventoryCacheTest {
    private static Inventory snapshot(boolean running) {
        Clones.Clone clone = new Clones.Clone(11, "Clone", " (parentId=0)" + (running ? " (running)" : ""));
        return new Inventory(Collections.singletonList(new Inventory.Profile(clone, 42,
                Collections.emptySet(), Collections.emptySet())), Collections.emptyList());
    }

    @Test public void navigationAndRepeatedRefreshShareOneRead() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger scans = new AtomicInteger();
        Inventory value = snapshot(false);
        InventoryCache cache = new InventoryCache(() -> { scans.incrementAndGet(); return value; }, tasks::add);
        CompletableFuture<Inventory> first = cache.read(false);
        assertSame(first, cache.read(false));
        assertSame(first, cache.read(true));
        assertSame(first, cache.read(true));
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertSame(value, first.join());
        assertSame(first, cache.read(false));
        assertSame(value, cache.peek());
        assertEquals(1, scans.get());
        CompletableFuture<Inventory> refresh = cache.read(true);
        assertNotSame(first, refresh);
        assertSame(value, cache.peek());
        assertSame(refresh, cache.read(true));
        tasks.remove().run();
        assertEquals(2, scans.get());
    }

    @Test public void mutationDuringReadRejectsOldResultAndSerializesReload() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger scans = new AtomicInteger();
        InventoryCache cache = new InventoryCache(() -> { scans.incrementAndGet(); return snapshot(false); }, tasks::add);
        CompletableFuture<Inventory> old = cache.read(false);
        cache.invalidate(); cache.invalidate();
        assertFalse(cache.current(old));
        CompletableFuture<Inventory> fresh = cache.read(false);
        assertSame(fresh, cache.read(false));
        assertSame(fresh, cache.read(true));
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertFalse(cache.current(old));
        assertFalse(fresh.isDone());
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertTrue(cache.current(fresh));
        assertEquals(2, scans.get());
        assertTrue(tasks.isEmpty());
    }

    @Test public void failedRefreshPreservesSnapshotWithoutAutomaticRetry() {
        AtomicInteger scans = new AtomicInteger();
        Inventory value = snapshot(false);
        InventoryCache cache = new InventoryCache(() -> {
            if (scans.incrementAndGet() == 2) throw new IllegalStateException("root denied");
            return value;
        }, Runnable::run);
        cache.read(false).join();
        CompletableFuture<Inventory> failure = cache.read(true);
        try { failure.join(); fail("failure must be visible"); }
        catch (CompletionException expected) { assertEquals("root denied", expected.getCause().getMessage()); }
        assertSame(value, cache.peek());
        assertSame(failure, cache.read(false));
        assertEquals(2, scans.get());
        assertSame(value, cache.read(true).join());
        assertEquals(3, scans.get());
    }

    @Test public void initialFailureIsNotAnEmptySnapshot() {
        InventoryCache cache = new InventoryCache(() -> { throw new IllegalStateException("root denied"); }, Runnable::run);
        CompletableFuture<Inventory> failure = cache.read(false);
        assertTrue(failure.isCompletedExceptionally());
        assertNull(cache.peek());
        assertSame(failure, cache.read(false));
    }

    @Test public void successfulLaunchInvalidatesUncertainInventory() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger scans = new AtomicInteger();
        InventoryCache cache = new InventoryCache(() -> {
            if (scans.incrementAndGet() == 2) throw new IllegalStateException("root denied");
            return snapshot(true);
        }, tasks::add);
        CompletableFuture<Inventory> first = cache.read(false);
        cache.afterLaunch(11, true);
        assertFalse(cache.current(first));
        tasks.remove().run();
        CompletableFuture<Inventory> failure = cache.read(false);
        tasks.remove().run();
        assertTrue(failure.isCompletedExceptionally());
        assertNotNull(cache.peek());
        cache.afterLaunch(11, true);
        assertFalse(cache.current(failure));
        CompletableFuture<Inventory> retry = cache.read(false);
        tasks.remove().run();
        assertTrue(cache.current(retry));
        assertEquals(3, scans.get());
    }

    @Test public void launchingRunningProfileDoesNotRescanButStartupAndFailuresDo() {
        for (boolean running : new boolean[] {true, false}) {
            InventoryCache cache = new InventoryCache(() -> snapshot(running), Runnable::run);
            CompletableFuture<Inventory> first = cache.read(false);
            cache.afterLaunch(11, true);
            assertEquals(running, cache.current(first));
            CompletableFuture<Inventory> second = cache.read(false);
            cache.afterLaunch(11, false);
            assertFalse(cache.current(second));
        }
        InventoryCache cache = new InventoryCache(() -> snapshot(true), Runnable::run);
        CompletableFuture<Inventory> first = cache.read(false);
        cache.afterLaunch(99, true);
        assertFalse(cache.current(first));
    }
}
