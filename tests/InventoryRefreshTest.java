package com.lixingchi.appclone;

import android.content.Intent;
import android.view.View;
import android.widget.ScrollView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real activity lifecycles with a queued fixture loader; never invokes su. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class InventoryRefreshTest {
    private final ArrayDeque<Runnable> reads = new ArrayDeque<>();
    private Inventory value;
    private InventoryCache cache;
    private int scans;
    private boolean fail;

    @Before public void setup() {
        Clones.Clone clone = new Clones.Clone(11, "Personal", " flags=PROFILE (parentId=0)");
        Inventory.Profile profile = new Inventory.Profile(clone, 42,
                Collections.singleton("com.chat"), Collections.emptySet());
        ArrayList<Inventory.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Inventory.Entry entry = new Inventory.Entry(new Apps.App("Chat " + i, i == 0 ? "com.chat" : "com.chat" + i, ""), false);
            entry.profiles.add(profile); entries.add(entry);
        }
        value = new Inventory(Collections.singletonList(profile), entries);
        cache = new InventoryCache(() -> {
            scans++;
            if (fail) throw new IllegalStateException("fixture read failed");
            return value;
        }, reads::add);
        ReflectionHelpers.setField(RuntimeEnvironment.getApplication(), "inventory", cache);
    }

    private ActivityController<MainActivity> main() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume();
    }

    private void finishRead() {
        reads.remove().run();
        shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test public void navigationResumeAndRotationReuseInventory() {
        ActivityController<MainActivity> main = main();
        finishRead();
        View row = main.get().body.getChildAt(0);
        main.pause();
        ActivityController<AppDetailActivity> detail = Robolectric.buildActivity(AppDetailActivity.class,
                new Intent().putExtra(AppDetailActivity.EXTRA_PKG, "com.chat")).create().start().resume();
        assertSame(value, detail.get().inventory);
        assertTrue(reads.isEmpty());
        detail.pause().stop().destroy();
        main.resume();
        assertSame(row, main.get().body.getChildAt(0));
        main.recreate();
        assertSame(value, main.get().inventory);
        assertEquals(1, scans);
        assertTrue(reads.isEmpty());
        main.pause().stop().destroy();
    }

    @Test public void refreshKeepsRowsAndScrollAndCoalescesRepeatedRequests() {
        ActivityController<MainActivity> main = main();
        finishRead();
        MainActivity activity = main.get();
        View decor = activity.getWindow().getDecorView();
        int width = View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY);
        decor.measure(width, height); decor.layout(0, 0, 900, 1200);
        ScrollView scroll = (ScrollView) activity.body.getParent();
        scroll.scrollTo(0, 300);
        View row = activity.body.getChildAt(0);
        activity.refresh(); activity.refresh();
        assertSame(row, activity.body.getChildAt(0));
        assertEquals(300, scroll.getScrollY());
        assertEquals(1, reads.size());
        assertTrue(activity.status.getText().toString().contains("previous list"));
        finishRead();
        decor.measure(width, height); decor.layout(0, 0, 900, 1200);
        assertEquals(300, scroll.getScrollY());
        assertEquals(2, scans);
        main.pause().stop().destroy();
    }

    @Test public void refreshFailureRetainsRowsAndErrorUntilExplicitRetry() {
        ActivityController<MainActivity> main = main();
        finishRead();
        View row = main.get().body.getChildAt(0);
        fail = true;
        main.get().refresh(); finishRead();
        assertSame(row, main.get().body.getChildAt(0));
        assertTrue(main.get().status.getText().toString().contains("Refresh failed; showing previous list"));
        main.pause().resume();
        assertTrue(reads.isEmpty());
        Settings.systemApps(main.get(), true);
        main.pause().resume();
        assertTrue(main.get().status.getText().toString().contains("Refresh failed"));
        assertEquals(31, main.get().body.getChildCount());
        fail = false;
        main.get().body.getChildAt(30).performClick(); finishRead();
        assertFalse(main.get().status.getText().toString().contains("Refresh failed"));
        assertEquals(30, main.get().body.getChildCount());
        assertEquals(3, scans);
        main.pause().stop().destroy();
    }

    @Test public void staleInFlightResultIsNotRenderedAfterInvalidation() {
        ActivityController<MainActivity> main = main();
        cache.invalidate();
        main.pause().resume();
        finishRead();
        assertNull(main.get().inventory);
        value = new Inventory(Collections.emptyList(), Collections.emptyList());
        finishRead();
        assertSame(value, main.get().inventory);
        assertEquals(2, scans);
        main.pause().stop().destroy();
    }

    @Test public void partialActionInvalidatesEvenWhenItsScreenIsDestroyed() throws Exception {
        ActivityController<MainActivity> main = main();
        finishRead();
        CompletableFuture<Inventory> before = cache.read(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        main.get().work("Fixture action", () -> {
            started.countDown();
            try { if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release action"); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            throw new IllegalStateException("partial action failed");
        }, null);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        main.pause().stop().destroy();
        finish.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (cache.current(before) && System.nanoTime() < deadline) Thread.sleep(1);
        assertFalse(cache.current(before));
        ActivityController<MainActivity> replacement = main();
        assertSame(value, replacement.get().inventory);
        finishRead();
        assertEquals(2, scans);
        replacement.pause().stop().destroy();
    }
}
