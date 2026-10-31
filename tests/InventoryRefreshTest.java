package com.lixingchi.appclone;

import android.content.Intent;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.tabs.TabLayout;
import android.widget.EditText;
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
import static com.lixingchi.appclone.ScreenTestSupport.*;

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
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
    }

    private void finishRead() {
        reads.remove().run();
        shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test public void navigationResumeAndRotationReuseInventory() {
        ActivityController<MainActivity> main = main();
        finishRead();
        layout(main.get().body, 400, 600);
        View row = main.get().body.getChildAt(0);
        assertNotNull(row);
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
        InventoryList list = activity.body;
        layout(list, 400, 600);
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        manager.scrollToPositionWithOffset(12, -20);
        layout(list, 400, 600);
        View row = manager.findViewByPosition(12);
        assertNotNull(row);
        assertEquals(-20, row.getTop());
        activity.refresh(); activity.refresh();
        assertSame(row, manager.findViewByPosition(12));
        assertEquals(-20, row.getTop());
        assertEquals(1, reads.size());
        assertTrue(activity.status.getText().toString().contains("previous list"));
        finishRead();
        layout(list, 400, 600);
        assertEquals(12, manager.findFirstVisibleItemPosition());
        assertEquals(-20, manager.findViewByPosition(12).getTop());
        assertEquals(2, scans);
        main.pause().stop().destroy();
    }

    @Test public void refreshFailureRetainsRowsAndErrorUntilExplicitRetry() {
        ActivityController<MainActivity> main = main();
        finishRead();
        layout(main.get().body, 400, 600);
        View row = main.get().body.getChildAt(0);
        assertNotNull(row);
        fail = true;
        main.get().refresh(); finishRead();
        assertSame(row, main.get().body.getChildAt(0));
        assertTrue(main.get().status.getText().toString().contains("Refresh failed; showing previous list"));
        main.pause().resume();
        assertTrue(reads.isEmpty());
        Settings.systemApps(main.get(), true);
        main.pause().resume();
        assertTrue(main.get().status.getText().toString().contains("Refresh failed"));
        assertEquals(61, main.get().body.getAdapter().getItemCount());
        assertEquals(View.VISIBLE, main.get().body.retry.getVisibility());
        main.get().status.performClick();
        android.app.Dialog details = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(text(details.getWindow().getDecorView()).contains("fixture read failed"));
        details.dismiss();
        fail = false;
        main.get().body.retry.performClick(); finishRead();
        assertFalse(main.get().status.getText().toString().contains("Refresh failed"));
        assertEquals(View.GONE, main.get().body.retry.getVisibility());
        assertEquals(61, main.get().body.getAdapter().getItemCount());
        assertEquals(3, scans);
        main.pause().stop().destroy();
    }

    @Test public void tabsKeepIndependentScrollAndQueriesThroughRecreation() {
        ActivityController<MainActivity> main = main();
        finishRead();
        MainActivity activity = main.get();
        InventoryList apps = activity.body;
        EditText appSearch = find(apps.header, EditText.class);
        appSearch.setText("Chat");
        layout(apps, 400, 600);
        ((LinearLayoutManager) apps.getLayoutManager()).scrollToPositionWithOffset(16, -12);
        layout(apps, 400, 600);
        TabLayout tabs = find(activity.getWindow().getDecorView(), TabLayout.class);
        tabs.getTabAt(1).select();
        InventoryList profiles = activity.body;
        assertNotSame(apps, profiles);
        assertEquals("", find(profiles.header, EditText.class).getText().toString());
        find(profiles.header, EditText.class).setText("Personal");
        layout(profiles, 400, 600);
        assertEquals(0, ((LinearLayoutManager) profiles.getLayoutManager()).findFirstVisibleItemPosition());
        tabs.getTabAt(0).select();
        assertSame(apps, activity.body);
        layout(apps, 400, 600);
        assertEquals(16, ((LinearLayoutManager) apps.getLayoutManager()).findFirstVisibleItemPosition());
        assertEquals(-12, apps.getLayoutManager().findViewByPosition(16).getTop());
        assertEquals("Chat", appSearch.getText().toString());
        tabs.getTabAt(1).select();
        main.recreate();
        activity = main.get();
        assertEquals("Personal", find(activity.body.header, EditText.class).getText().toString());
        tabs = find(activity.getWindow().getDecorView(), TabLayout.class);
        assertEquals(1, tabs.getSelectedTabPosition());
        tabs.getTabAt(0).select();
        layout(activity.body, 400, 600);
        assertEquals("Chat", find(activity.body.header, EditText.class).getText().toString());
        assertEquals(16, ((LinearLayoutManager) activity.body.getLayoutManager()).findFirstVisibleItemPosition());
        assertEquals(-12, activity.body.getLayoutManager().findViewByPosition(16).getTop());
        assertEquals(1, scans);
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
