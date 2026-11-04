package com.lixingchi.appclone;

import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.os.LocaleList;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static com.lixingchi.appclone.ScreenTestSupport.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 35}, qualifiers = "en")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class LanguagePickerTest {
    @Before public void connectGeneratedManifestResource() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        int id = app.getResources().getIdentifier("_generated_res_locale_config", "xml", app.getPackageName());
        assertNotEquals(0, id);
        // Robolectric omits this manifest field; use the actual platform parser and compiled resource.
        ReflectionHelpers.setField(app.getApplicationInfo(), "localeConfigRes", id);
        assertEquals(LocaleConfig.STATUS_SUCCESS, new LocaleConfig(app).getStatus());
    }

    private AlertDialog dialog() {
        shadowOf(android.os.Looper.getMainLooper()).idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private void choose(AlertDialog dialog, int index) {
        dialog.getListView().performItemClick(null, index, index);
        shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test public void settingsListsActualCatalogsWithoutInventedRegions() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        ((View) findText(activity.getWindow().getDecorView(), activity.getString(R.string.app_language))
                .getParent().getParent()).performClick();
        AlertDialog dialog = dialog();
        List<Locale> locales = LanguagePicker.supported(activity);
        Set<String> tags = new HashSet<>();
        for (Locale locale : locales) tags.add(locale.toLanguageTag());
        assertTrue(tags.containsAll(Arrays.asList("en", "zh-Hans", "zh-Hant", "es", "hi", "bn", "ru", "ja", "tr")));
        assertTrue(new HashSet<>(Arrays.asList("en", "zh-Hans", "zh-Hant", "es", "hi", "bn", "ru", "ja", "tr", "en-XA", "ar-XB")).containsAll(tags));
        assertEquals(locales.size() + 1, dialog.getListView().getCount());
        assertEquals(activity.getString(R.string.system_language), dialog.getListView().getItemAtPosition(0));
        assertEquals(0, dialog.getListView().getCheckedItemPosition());
        for (int i = 0; i < locales.size(); i++)
            assertEquals(locales.get(i).getDisplayName(locales.get(i)), dialog.getListView().getItemAtPosition(i + 1));
        assertFalse(tags.contains("en-CN"));
        assertFalse(tags.contains("en-HK"));
        assertFalse(tags.contains("en-SG"));
        assertNull(shadowOf(activity).getNextStartedActivity());
        dialog.dismiss();
        controller.destroy();
    }

    @Test public void choiceUsesNativePerAppStorageAndSystemDefaultClearsIt() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        LocaleManager manager = activity.getSystemService(LocaleManager.class);
        LocaleList system = manager.getSystemLocales();
        LanguagePicker.show(activity);
        int index = LanguagePicker.supported(activity).indexOf(Locale.forLanguageTag("zh-Hant")) + 1;
        assertTrue(index > 0);
        choose(dialog(), index);
        assertEquals("zh-Hant", manager.getApplicationLocales().toLanguageTags());
        assertEquals(Locale.forLanguageTag("zh-Hant").getDisplayName(Locale.forLanguageTag("zh-Hant")), LanguagePicker.current(activity));
        controller.recreate();
        activity = controller.get();
        LanguagePicker.show(activity);
        assertEquals(index, dialog().getListView().getCheckedItemPosition());
        choose(dialog(), 0);
        assertTrue(manager.getApplicationLocales().isEmpty());
        assertEquals(system, manager.getSystemLocales());
        controller.destroy();
    }

    @Test public void displayingAndCancelingNeverNormalizesExternalSelections() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        LocaleManager manager = activity.getSystemService(LocaleManager.class);
        for (String tags : new String[] {"en-CN-u-mu-celsius", "fr-FR,zh-Hans"}) {
            manager.setApplicationLocales(LocaleList.forLanguageTags(tags));
            LanguagePicker.show(activity);
            assertEquals(-1, dialog().getListView().getCheckedItemPosition());
            dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertEquals(tags, manager.getApplicationLocales().toLanguageTags());
        }
        controller.destroy();
    }

    @Test public void unavailableCatalogFailsVisiblyWithoutChangingLanguage() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        LocaleManager manager = activity.getSystemService(LocaleManager.class);
        manager.setApplicationLocales(LocaleList.forLanguageTags("zh-Hans"));
        ReflectionHelpers.setField(activity.getApplicationInfo(), "localeConfigRes", 0);
        LanguagePicker.show(activity);
        assertTrue(text(dialog().getWindow().getDecorView()).contains(activity.getString(R.string.language_settings_unavailable)));
        assertEquals("zh-Hans", manager.getApplicationLocales().toLanguageTags());
        dialog().dismiss();
        controller.destroy();
    }
}
