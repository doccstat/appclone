package com.lixingchi.appclone;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static com.lixingchi.appclone.ScreenTestSupport.*;
import static org.junit.Assert.*;

/** Tests the real packaged catalogs in both release and debug, without root or a device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class BundledLocalesTest {
    private Context localized(String tag) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));
        return app.createConfigurationContext(config);
    }

    @Test public void everyBundledLanguageResolvesRealTextAndPreservesNamesAndTokens() {
        String[][] cases = {{"zh-Hans", "设置"}, {"zh-Hant", "設定"}, {"es", "Ajustes"},
                {"hi", "सेटिंग्स"}, {"bn", "সেটিংস"}, {"ru", "Настройки"},
                {"ja", "設定"}, {"tr", "Ayarlar"}};
        String name = "  Personal ' 中文 العربية  ";
        for (String[] item : cases) {
            Context context = localized(item[0]);
            assertEquals(item[0], item[1], context.getString(R.string.settings));
            assertNotEquals(item[0], localized("en").getString(R.string.delete_profile_message, name),
                    context.getString(R.string.delete_profile_message, name));
            assertTrue(context.getString(R.string.profile_location, name, "012").contains(name));
            assertTrue(context.getString(R.string.profile_location, name, "012").contains("012"));
            assertEquals("DELETE", context.getString(R.string.delete_token));
            assertEquals("App Clone 2.3.1", context.getString(R.string.app_version, "2.3.1"));
            for (String token : new String[] {"{id}", "{name}", "{clone}"})
                assertTrue(context.getString(R.string.format_tokens).contains(token));
        }
    }

    @Test public void chineseRegionsChooseScriptsAndRussianCountsChooseTheirPluralForms() {
        for (String tag : new String[] {"zh-CN", "zh-SG"})
            assertEquals(tag, "设置", localized(tag).getString(R.string.settings));
        for (String tag : new String[] {"zh-TW", "zh-HK", "zh-MO"})
            assertEquals(tag, "設定", localized(tag).getString(R.string.settings));
        Context ru = localized("ru");
        int[] counts = {0, 1, 2, 5, 21, 22, 25};
        String[] expected = {"0 клонов", "1 клон", "2 клона", "5 клонов", "21 клон", "22 клона", "25 клонов"};
        for (int i = 0; i < counts.length; i++)
            assertEquals(expected[i], ru.getResources().getQuantityString(R.plurals.clone_count, counts[i], counts[i]));
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    public void settingsScreenUsesChineseCatalog() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        assertEquals("应用语言", activity.getString(R.string.app_language));
        assertNotNull(findText(activity.getWindow().getDecorView(), "应用语言"));
        assertTrue(text(activity.getWindow().getDecorView()).contains("重启后"));
        controller.destroy();
    }
}
