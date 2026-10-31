package com.lixingchi.appclone;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static com.lixingchi.appclone.ScreenTestSupport.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class InlineInventoryTest {
    private Inventory fixture(int count) {
        List<Inventory.Profile> profiles = new ArrayList<>();
        Inventory.Entry entry = new Inventory.Entry(new Apps.App("Chat", "com.chat", ".Main"), false);
        for (int i = 0; i < count; i++) {
            Inventory.Profile profile = new Inventory.Profile(new Clones.Clone(11 + i, "Account " + i,
                    " flags=PROFILE (parentId=0)" + (i % 2 == 0 ? " (running)" : "")), 42 + i,
                    Collections.singleton("com.chat"), i == 0 ? Collections.singleton("com.chat") : Collections.emptySet());
            profiles.add(profile); entry.profiles.add(profile);
        }
        return new Inventory(profiles, Collections.singletonList(entry));
    }

    @Test public void everyInstanceIsInlineEvenWhenSearchMatchesOnlyOneProfile() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        activity.inventory = fixture(20); activity.render();
        assertEquals(22, activity.body.getAdapter().getItemCount());
        String contents = text(activity.body);
        for (int i = 0; i < 20; i++) assertTrue(contents.contains("Account " + i + "\nUser " + (11 + i)));
        assertTrue(contents.contains("Disabled · Profile running"));
        assertTrue(contents.contains("Profile stopped"));
        assertNull(shadowOf(activity).getNextStartedActivity());
        find(activity.body.header, EditText.class).setText("Account 19");
        assertEquals(22, activity.body.getAdapter().getItemCount());
        find(activity.body.header, EditText.class).setText("no match");
        assertEquals(2, activity.body.getAdapter().getItemCount());
        assertTrue(text(activity.body).contains("No matches"));
        controller.destroy();
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    public void recycledRowKeepsExactLaunchTargetAndOverflowControls() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        activity.inventory = fixture(2); activity.render();
        RecyclerView.Adapter adapter = activity.body.getAdapter();
        RecyclerView.ViewHolder holder = adapter.createViewHolder(activity.body, adapter.getItemViewType(2));
        adapter.bindViewHolder(holder, 2);
        assertFalse(findText(holder.itemView, "Open").isEnabled());
        adapter.bindViewHolder(holder, 3);
        assertTrue(findText(holder.itemView, "Open").isEnabled());
        ImageButton more = find(holder.itemView, ImageButton.class);
        assertEquals("Actions for Chat in Account 1 · User 12", more.getContentDescription());
        layout(holder.itemView, Ui.dp(activity, 320), Ui.dp(activity, 200));
        assertEquals(Ui.dp(activity, 48), more.getWidth());
        assertEquals(Ui.dp(activity, 48), more.getHeight());
        findText(holder.itemView, "Open").performClick();
        Intent launch = shadowOf(activity).getNextStartedActivity();
        assertEquals(LaunchActivity.class.getName(), launch.getComponent().getClassName());
        assertEquals(12, launch.getIntExtra(LaunchActivity.EXTRA_USER, -1));
        assertEquals(43L, launch.getLongExtra(LaunchActivity.EXTRA_SERIAL, -1));
        assertEquals("com.chat", launch.getStringExtra(LaunchActivity.EXTRA_PKG));
        PopupMenu menu = AppControls.menu(activity, activity.inventory.apps.get(0), activity.inventory.profiles.get(1), more);
        assertEquals(8, menu.getMenu().size());
        assertEquals("Background activity", menu.getMenu().findItem(6).getTitle());
        assertEquals("Rename profile", menu.getMenu().findItem(7).getTitle());
        menu.getMenu().performIdentifierAction(7, 0);
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertEquals("Account 1", find(dialog.getWindow().getDecorView(), EditText.class).getText().toString());
        assertTrue(text(dialog.getWindow().getDecorView()).contains("for all its apps"));
        dialog.dismiss();
        controller.destroy();
    }

    @Test public void renameCancelInvalidAndUnchangedInputsNeverRunRoot() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        Inventory.Profile profile = fixture(1).profiles.get(0);
        ProfileControls.rename(activity, profile);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        EditText name = find(dialog.getWindow().getDecorView(), EditText.class);
        assertEquals(profile.clone.name, name.getText().toString());
        name.setText("");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertNotNull(name.getError());
        name.setText(new String(new char[101]).replace('\0', 'x'));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        name.setText(profile.clone.name);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        ProfileControls.rename(activity, profile);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        find(dialog.getWindow().getDecorView(), EditText.class).setText("  Another's name  ");
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
        assertEquals("Account 0", profile.clone.name);
        controller.destroy();
    }

    @Test public void compactAndLargeTextLayoutsLeaveAScrollViewportWithTheKeyboard() {
        for (boolean landscape : new boolean[] {false, true}) {
            org.robolectric.RuntimeEnvironment.setQualifiers(landscape ? "w640dp-h320dp-land-night" : "w320dp-h640dp-port-notnight");
            org.robolectric.RuntimeEnvironment.setFontScale(1.6f);
            ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create().visible();
            MainActivity activity = controller.get();
            activity.inventory = fixture(30); activity.render();
            android.view.ViewGroup content = activity.findViewById(android.R.id.content);
            View root = content.getChildAt(0);
            ViewPager pager = find(root, ViewPager.class);
            View add = find(root, com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton.class);
            int width = Ui.dp(activity, landscape ? 640 : 320), height = Ui.dp(activity, landscape ? 320 : 640);
            root.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                    .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, Ui.dp(activity, 24), 0, Ui.dp(activity, 24)))
                    .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, Ui.dp(activity, 140)))
                    .setVisible(android.view.WindowInsets.Type.ime(), true).build());
            layout(root, width, height);
            assertEquals(View.GONE, add.getVisibility());
            assertTrue("tab content must retain a viewport above the IME: " + pager.getHeight(), pager.getHeight() > 0);
            assertTrue(activity.body.canScrollVertically(1));
            root.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                    .setVisible(android.view.WindowInsets.Type.ime(), false).build());
            layout(root, width, height);
            assertEquals(View.VISIBLE, add.getVisibility());
            assertTrue(add.getRight() <= width);
            controller.destroy();
        }
        org.robolectric.RuntimeEnvironment.setFontScale(1f);
    }

    @Test public void longListsRecycleAndPagerSelectionRendersTheOtherTab() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        activity.inventory = fixture(500); activity.render();
        layout(activity.body, Ui.dp(activity, 360), Ui.dp(activity, 480));
        assertEquals(502, activity.body.getAdapter().getItemCount());
        assertTrue(activity.body.getChildCount() > 0);
        assertTrue(activity.body.getChildCount() < 20);
        ViewPager pager = find(activity.getWindow().getDecorView(), ViewPager.class);
        pager.setCurrentItem(1, false);
        assertEquals(501, activity.body.getAdapter().getItemCount());
        pager.setCurrentItem(0, false);
        assertEquals(502, activity.body.getAdapter().getItemCount());
        controller.destroy();
    }
}
