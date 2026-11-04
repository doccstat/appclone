package com.lixingchi.appclone;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.Collections;
import static com.lixingchi.appclone.ScreenTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Debug-only Android-generated pseudolocales exercise the real resource pipeline. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class LocalizationTest {
    private Context localized(String tag) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));
        return app.createConfigurationContext(config);
    }

    private Inventory.Profile profile(String name) {
        return new Inventory.Profile(new Clones.Clone(12, name, "flags=PROFILE (parentId=0)"), 42,
                Collections.singleton("com.chat"), Collections.singleton("com.chat"));
    }

    @Test public void countsUsePluralsAndUnknownLanguagesFallBackToEnglish() {
        Context english = localized("en");
        assertEquals("0 clones", english.getResources().getQuantityString(R.plurals.clone_count, 0, 0));
        assertEquals("1 clone", english.getResources().getQuantityString(R.plurals.clone_count, 1, 1));
        assertEquals("2 clones", english.getResources().getQuantityString(R.plurals.clone_count, 2, 2));
        assertEquals("500 clones", english.getResources().getQuantityString(R.plurals.clone_count, 500, 500));
        assertEquals("Settings", localized("zz-ZZ").getString(R.string.settings));
        assertEquals("Disabled · Profile stopped", Inventory.state(english, profile("Name"), "com.chat"));
        Context pseudo = localized("en-XA");
        assertEquals(pseudo.getString(R.string.background_allowed), BackgroundPolicy.name(pseudo, 0));
        assertNotEquals(BackgroundPolicy.name(english, 0), BackgroundPolicy.name(pseudo, 0));
    }

    @Test @Config(qualifiers = "en-rXA-w320dp-h640dp")
    public void pseudolocaleReachesScreensDialogsAndAccessibilityWithoutChangingNames() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        assertNotEquals("Settings", activity.getString(R.string.settings));
        assertEquals("App Clone", activity.getString(R.string.app_name));
        Inventory.Profile profile = profile("  中文 ' العربية  ");
        Inventory.Entry entry = new Inventory.Entry(new Apps.App("Chat", "com.chat", ".Main"), false);
        entry.profiles.add(profile);
        activity.inventory = new Inventory(Collections.singletonList(profile), Collections.singletonList(entry));
        activity.render();
        View instance = row(activity.body, 2);
        assertTrue(text(instance).contains(profile.clone.name));
        assertTrue(text(instance).contains(activity.getString(R.string.disabled_profile_stopped)));
        assertEquals(activity.getString(R.string.app_actions_in_profile, Ui.bidi(activity, "Chat"), Inventory.location(activity, profile)),
                instance.findViewById(R.id.row_actions).getContentDescription());
        ProfileControls.rename(activity, profile);
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(text(dialog.getWindow().getDecorView()).contains(activity.getString(R.string.rename_profile)));
        assertEquals(profile.clone.name, find(dialog.getWindow().getDecorView(), EditText.class).getText().toString());
        dialog.dismiss();
        AddClone.create(activity, null);
        dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertEquals("Clone Profile", find(dialog.getWindow().getDecorView(), EditText.class).getText().toString());
        dialog.dismiss();
        find(activity.getWindow().getDecorView(), com.google.android.material.appbar.MaterialToolbar.class)
                .getMenu().performIdentifierAction(5, 0);
        dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(text(dialog.getWindow().getDecorView()).contains(activity.getString(R.string.delete_all_question)));
        EditText confirm = find(dialog.getWindow().getDecorView(), EditText.class);
        confirm.setText(" DELETE");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertEquals(activity.getString(R.string.delete_confirmation_error, "DELETE"), confirm.getError().toString());
        dialog.dismiss();
        controller.destroy();
    }

    @Test @Config(qualifiers = "ar-rXB-w320dp-h640dp")
    public void rtlRowsMirrorIndentationAndKeepIdentifiersAndFormatTokens() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create().visible();
        MainActivity activity = controller.get();
        assertEquals(View.LAYOUT_DIRECTION_RTL, activity.getResources().getConfiguration().getLayoutDirection());
        assertNotEquals("Open", activity.getString(R.string.open));
        Inventory.Profile profile = profile("English account");
        Inventory.Entry entry = new Inventory.Entry(new Apps.App("Chat", "com.chat", ".Main"), false);
        entry.profiles.add(profile);
        activity.inventory = new Inventory(Collections.singletonList(profile), Collections.singletonList(entry));
        activity.render();
        View instance = row(activity.body, 2);
        instance.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout(instance, Ui.dp(activity, 320), Ui.dp(activity, 200));
        LinearLayout line = (LinearLayout) ((LinearLayout) instance).getChildAt(0);
        assertEquals(Ui.dp(activity, 12), line.getPaddingRight());
        assertEquals(0, line.getPaddingLeft());
        assertTrue(Inventory.location(activity, profile).contains("12"));
        assertEquals("English account", profile.clone.name);
        assertEquals("DELETE", activity.getString(R.string.delete_token));
        for (String token : new String[] {"{id}", "{name}", "{clone}"})
            assertTrue(activity.getString(R.string.format_tokens).contains(token));
        controller.destroy();
    }

    @Test public void rebootReportsRenderInTheReadersLocaleAndPreserveLegacyAndRawFailures() {
        Context english = localized("en"), pseudo = localized("en-XA");
        SharedPreferences prefs = english.getSharedPreferences("i18n-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        BootPolicy policy = new BootPolicy(prefs);
        assertEquals(english.getString(R.string.boot_not_run), policy.status(english));
        prefs.edit().putString("status", "Old report with User 12").commit();
        assertTrue(policy.status(pseudo).contains("Old report with User 12"));
        policy.save(BootPolicy.START);
        assertEquals(english.getString(R.string.boot_saved), policy.status(english));
        assertEquals(pseudo.getString(R.string.boot_saved), new BootPolicy(prefs).status(pseudo));
        assertNotEquals(policy.status(english), policy.status(pseudo));
        long run = policy.begin();
        assertEquals(pseudo.getString(R.string.boot_queued), policy.status(pseudo));
        policy.fail(run, BootPolicy.START, "Permission denied: user=12");
        assertEquals(pseudo.getString(R.string.boot_failed, "Permission denied: user=12"), policy.status(pseudo));
        run = policy.begin();
        policy.apply(run, BootPolicy.START, () -> true, command -> new Shell.Result(0, "0 users:\n"));
        assertEquals(pseudo.getString(R.string.boot_summary, pseudo.getString(R.string.boot_start), 0, 0, 0), policy.status(pseudo));
    }

    @Test public void generatedLocaleConfigDoesNotAdvertiseUntranslatedLibraryLanguages() throws Exception {
        // Robolectric's manifest parser omits localeConfig; independently inspect compiled XML.
        Context app = RuntimeEnvironment.getApplication();
        int id = app.getResources().getIdentifier("_generated_res_locale_config", "xml", app.getPackageName());
        assertNotEquals(0, id);
        java.util.Set<String> actual = new java.util.HashSet<>();
        try (android.content.res.XmlResourceParser xml = app.getResources().getXml(id)) {
            while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() == org.xmlpull.v1.XmlPullParser.START_TAG && "locale".equals(xml.getName()))
                    actual.add(xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name"));
            }
        }
        java.util.Set<String> expected = new java.util.HashSet<>(java.util.Arrays.asList("en", "en-XA", "ar-XB"));
        java.io.File[] folders = new java.io.File(System.getProperty("appclone.resources")).listFiles();
        assertNotNull(folders);
        for (java.io.File folder : folders) {
            if (!folder.getName().startsWith("values-") || !new java.io.File(folder, "strings.xml").isFile()) continue;
            String qualifier = folder.getName().substring("values-".length());
            expected.add(qualifier.startsWith("b+") ? qualifier.substring(2).replace('+', '-') : qualifier.replace("-r", "-"));
        }
        assertEquals(expected, actual);
    }
}
