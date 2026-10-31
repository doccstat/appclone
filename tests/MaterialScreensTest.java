package com.lixingchi.appclone;

import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.tabs.TabLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static org.junit.Assert.*;
import static com.lixingchi.appclone.ScreenTestSupport.*;

/** Create/render only: never resumes the inventory loader and never invokes su. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class MaterialScreensTest {
    private Inventory fixture() {
        Clones.Clone clone = new Clones.Clone(11, "Personal", " flags=PROFILE (parentId=0)");
        Inventory.Profile profile = new Inventory.Profile(clone, 42,
                new HashSet<>(Arrays.asList("com.chat", "com.system")), Collections.singleton("com.chat"));
        Inventory.Entry chat = new Inventory.Entry(new Apps.App("Chat", "com.chat", ""), false);
        Inventory.Entry system = new Inventory.Entry(new Apps.App("System fixture", "com.system", ""), true);
        chat.profiles.add(profile); system.profiles.add(profile);
        return new Inventory(Collections.singletonList(profile), Arrays.asList(chat, system));
    }

    @Test public void appFirstEmptyStateAndSystemFilter() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        Settings.systemApps(activity, false);
        activity.inventory = new Inventory(Collections.emptyList(), Collections.emptyList());
        activity.render();
        assertTrue(text(activity.body).contains("No cloned apps yet"));
        activity.inventory = fixture(); activity.render();
        assertTrue(text(activity.body).contains("Chat\ncom.chat\n1 clone"));
        assertTrue(text(activity.body).contains("Personal\nUser 11"));
        assertTrue(text(activity.body).contains("Disabled · Profile stopped"));
        assertFalse(text(activity.body).contains("Manage"));
        assertFalse(text(activity.body).contains("System fixture"));
        Settings.systemApps(activity, true); activity.render();
        assertTrue(text(activity.body).contains("System fixture"));
        assertTrue(find(activity.getWindow().getDecorView(), com.google.android.material.appbar.MaterialToolbar.class)
                .getMenu().findItem(2).isChecked());
        TabLayout tabs = find(activity.getWindow().getDecorView(), TabLayout.class);
        tabs.getTabAt(1).select();
        assertTrue(text(activity.body).contains("Personal"));
        assertTrue(text(activity.body).contains("Stopped"));
        controller.destroy();
    }

    @Test public void appAndProfileDetailsKeepDisabledStoppedApps() {
        Intent appIntent = new Intent().putExtra(AppDetailActivity.EXTRA_PKG, "com.chat");
        ActivityController<AppDetailActivity> app = Robolectric.buildActivity(AppDetailActivity.class, appIntent).create();
        app.get().inventory = fixture(); app.get().render();
        assertTrue(text(app.get().body).contains("Disabled · Profile stopped"));
        assertTrue(text(app.get().body).contains("Personal\nUser 11"));
        assertFalse(findText(row(app.get().body, 1), "Open").isEnabled());
        app.destroy();
        Intent profileIntent = new Intent().putExtra(CloneDetailActivity.EXTRA_ID, 11)
                .putExtra(CloneDetailActivity.EXTRA_SERIAL, 42L);
        ActivityController<CloneDetailActivity> profile = Robolectric.buildActivity(CloneDetailActivity.class, profileIntent).create();
        profile.get().inventory = fixture(); profile.get().render();
        assertTrue(text(profile.get().body).contains("Chat"));
        profile.get().inventory = new Inventory(Collections.emptyList(), Collections.emptyList()); profile.get().render();
        assertTrue(profile.get().status.getText().toString().contains("removed or replaced"));
        profile.destroy();
    }

    @Test public void materialSettingsInflateInLightAndDark() {
        for (String qualifier : Arrays.asList("notnight", "night")) {
            org.robolectric.RuntimeEnvironment.setQualifiers(qualifier);
            ActivityController<SettingsActivity> activity = Robolectric.buildActivity(SettingsActivity.class).create();
            assertTrue(text(activity.get().getWindow().getDecorView()).contains("Material You"));
            com.google.android.material.materialswitch.MaterialSwitch prefix = find(activity.get().getWindow().getDecorView(),
                    com.google.android.material.materialswitch.MaterialSwitch.class);
            prefix.setChecked(true);
            android.widget.EditText format = find(activity.get().getWindow().getDecorView(), android.widget.EditText.class);
            format.setText("  X{name}  "); format.setSelection(3);
            com.google.android.material.chip.ChipGroup tokens = find(activity.get().getWindow().getDecorView(),
                    com.google.android.material.chip.ChipGroup.class);
            assertEquals(3, tokens.getChildCount());
            assertFalse(((com.google.android.material.chip.Chip) tokens.getChildAt(0)).isCheckable());
            tokens.getChildAt(0).performClick();
            assertEquals("  X{id}{name}  ", format.getText().toString());
            prefix.setChecked(false);
            assertFalse(format.isEnabled());
            for (int i = 0; i < tokens.getChildCount(); i++) assertFalse(tokens.getChildAt(i).isEnabled());
            activity.destroy();
        }
    }

    @Test public void materialCreationAndProgressDialogsInflate() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        activity.inventory = fixture();
        AddClone.create(activity, null);
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());
        assertTrue(text(dialog.getWindow().getDecorView()).contains("Clone Profile"));
        assertFalse(find(dialog.getWindow().getDecorView(), com.google.android.material.checkbox.MaterialCheckBox.class).isChecked());
        dialog.dismiss();
        AlertDialog progress = Ui.progress(activity, "Testing…");
        assertTrue(progress.isShowing());
        progress.dismiss();
        AppControls.confirm(activity, activity.inventory.app("com.chat"), activity.inventory.profile(11), Apps.Action.DISABLE);
        dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(text(dialog.getWindow().getDecorView()).contains("User 11"));
        assertTrue(text(dialog.getWindow().getDecorView()).contains("Data is kept"));
        dialog.dismiss();
        controller.destroy();
    }

    @Test public void backgroundChoicesAreExplicitAndDoNotChangePolicyOnDisplayOrCancel() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        android.content.SharedPreferences prefs = activity.getSharedPreferences("background-ui-test", 0);
        prefs.edit().clear().commit();
        BackgroundPolicy policy = new BackgroundPolicy(prefs, command -> { throw new AssertionError("unexpected root: " + command); });
        Inventory inventory = fixture();
        BackgroundPolicy.State state = new BackgroundPolicy.State(1110123, 1234, 0, -1);
        BackgroundControls.show(activity, inventory.app("com.chat"), inventory.profile(11), policy, state);
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(text(dialog.getWindow().getDecorView()).contains("Personal · User 11"));
        assertTrue(text(dialog.getWindow().getDecorView()).contains("Notifications and jobs may be delayed"));
        assertEquals("Restrict", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals("Keep current", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(prefs.getAll().isEmpty());
        prefs.edit().putString("11:42:com.chat", state.encode()).commit();
        BackgroundControls.show(activity, inventory.app("com.chat"), inventory.profile(11), policy, state.withMode(1));
        dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertEquals("Restore previous", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(text(dialog.getWindow().getDecorView()).contains("Android's current setting stays unchanged"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(state, policy.previous(11, 42, "com.chat"));
        controller.destroy();
    }

    @Test public void profileHandlesUsePublicSdkWithoutOverflow() {
        for (int id : new int[] {0, 11, 900, Integer.MAX_VALUE / 100000}) {
            assertEquals(id, CloneApps.userHandle(id).hashCode());
        }
        assertNull(CloneApps.userHandle(-1));
        assertNull(CloneApps.userHandle(Integer.MAX_VALUE / 100000 + 1));
    }

    @Test public void insetsIncludeLandscapeCutoutsAndKeyboard() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        View view = new View(controller.get());
        Ui.applyInsets(view, 0, 0, 0, 0);
        view.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 10, 0, 20))
                .setInsets(android.view.WindowInsets.Type.displayCutout(), android.graphics.Insets.of(4, 0, 6, 0))
                .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 100)).build());
        assertEquals(4, view.getPaddingLeft());
        assertEquals(10, view.getPaddingTop());
        assertEquals(6, view.getPaddingRight());
        assertEquals(100, view.getPaddingBottom());
        controller.destroy();
    }

}
