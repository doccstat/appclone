package com.lixingchi.appclone;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static com.lixingchi.appclone.ScreenTestSupport.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-w360dp-h640dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class CompactDesignTest {
    @org.junit.Before public void resetFontBefore() { RuntimeEnvironment.setFontScale(1f); }
    @org.junit.After public void resetFontAfter() { RuntimeEnvironment.setFontScale(1f); }

    private Inventory fixture(String name) {
        Inventory.Profile profile = new Inventory.Profile(new Clones.Clone(12, name, "flags=PROFILE (parentId=0)"),
                42, Collections.singleton("com.chat"), Collections.emptySet());
        Inventory.Entry entry = new Inventory.Entry(new Apps.App("Chat", "com.chat", ".Main"), false);
        entry.profiles.add(profile);
        return new Inventory(Collections.singletonList(profile), Collections.singletonList(entry));
    }

    private void measureRow(View row, int width) {
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
    }

    @Test public void userAndStatusShareOneCompactLineWithFullSizeActions() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        activity.inventory = fixture("Personal"); activity.render();
        View instance = row(activity.body, 2);
        measureRow(instance, Ui.dp(activity, 328));
        TextView metadata = instance.findViewById(R.id.row_metadata);
        assertEquals("User 12 · Stopped", metadata.getText().toString());
        assertEquals(1, metadata.getLineCount());
        assertEquals(2, ((LinearLayout) metadata.getParent()).getChildCount());
        for (int id : new int[] {R.id.open_clone, R.id.row_actions}) {
            View button = instance.findViewById(id);
            assertEquals(Ui.dp(activity, 48), button.getWidth());
            assertEquals(Ui.dp(activity, 48), button.getHeight());
            assertTrue(button.getContentDescription().toString().contains("Personal"));
            assertTrue(button.getRight() <= instance.getWidth());
        }
        TabLayout tabs = find(activity.getWindow().getDecorView(), TabLayout.class);
        assertEquals(2, tabs.getTabCount());
        assertEquals("Apps", tabs.getTabAt(0).getText());
        assertEquals("Profiles", tabs.getTabAt(1).getText());
        tabs.getTabAt(1).select();
        TextView profileMetadata = row(activity.body, 1).findViewById(R.id.row_metadata);
        assertEquals("User 12 · Stopped · 1 app", profileMetadata.getText().toString());
        controller.destroy();
    }

    @Test public void largeTextAllowsWrappingWithoutClippingNamesOrStatus() {
        RuntimeEnvironment.setFontScale(2f);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        String name = "  A long personal account name with 中文 العربية and spaces  ";
        activity.inventory = fixture(name); activity.render();
        View instance = row(activity.body, 2);
        measureRow(instance, Ui.dp(activity, 288));
        TextView title = findText(instance, name);
        TextView metadata = instance.findViewById(R.id.row_metadata);
        assertNotNull(title);
        assertNull(title.getEllipsize());
        assertNull(metadata.getEllipsize());
        // Legacy host graphics do not shape fonts; check the native wrapping contract, not glyph widths.
        assertEquals(Integer.MAX_VALUE, title.getMaxLines());
        assertEquals(Integer.MAX_VALUE, metadata.getMaxLines());
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, instance.getLayoutParams().height);
        assertTrue(title.getWidth() > 0);
        assertTrue(metadata.getWidth() > 0);
        assertEquals(2f, activity.getResources().getConfiguration().fontScale, 0f);
        assertEquals(name, activity.inventory.profiles.get(0).clone.name);
        assertTrue(instance.findViewById(R.id.row_actions).getRight() <= instance.getWidth());
        controller.destroy();
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test public void floatingAddDoesNotCoverTheLastScrollableRow() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create().visible();
        MainActivity activity = controller.get();
        activity.inventory = fixture("Personal"); activity.render();
        View root = ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        layout(root, Ui.dp(activity, 360), Ui.dp(activity, 640));
        ExtendedFloatingActionButton add = find(root, ExtendedFloatingActionButton.class);
        assertTrue(add.getParent() instanceof android.widget.FrameLayout);
        assertTrue(add.getHeight() >= Ui.dp(activity, 48));
        assertTrue(activity.body.getPaddingBottom() >= add.getHeight() + Ui.dp(activity, 32));
        controller.destroy();
    }

    @Test public void rebootSummaryOpensAllChoicesAndWarningsWithoutSavingOnDisplay() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        Map<String, ?> before = new java.util.HashMap<>(activity.getSharedPreferences("boot", 0).getAll());
        View setting = (View) findText(activity.getWindow().getDecorView(), activity.getString(R.string.after_reboot))
                .getParent().getParent();
        setting.performClick();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue(text(dialog.getWindow().getDecorView()).contains(activity.getString(R.string.boot_help)));
        RadioGroup choices = find(dialog.getWindow().getDecorView(), RadioGroup.class);
        assertEquals(3, choices.getChildCount());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(before, activity.getSharedPreferences("boot", 0).getAll());
        setting.performClick();
        dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        choices = find(dialog.getWindow().getDecorView(), RadioGroup.class);
        choices.check(2);
        assertEquals(BootPolicy.START, BootPolicy.get(activity).mode());
        assertTrue(text(setting).contains(activity.getString(R.string.boot_start)));
        dialog.dismiss();
        controller.destroy();
    }
}
