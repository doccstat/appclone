package com.lixingchi.appclone;

import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Context;
import android.os.LocaleList;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Show only packaged languages; Android Settings also suggests regional variants. */
final class LanguagePicker {
    static List<Locale> supported(Context context) {
        LocaleConfig config = new LocaleConfig(context);
        LocaleList locales = config.getSupportedLocales();
        if (config.getStatus() != LocaleConfig.STATUS_SUCCESS || locales == null || locales.isEmpty())
            throw new IllegalStateException("Packaged language list unavailable");
        List<Locale> result = new ArrayList<>();
        for (int i = 0; i < locales.size(); i++) result.add(locales.get(i));
        Collator collator = Collator.getInstance(context.getResources().getConfiguration().getLocales().get(0));
        result.sort((a, b) -> collator.compare(a.getDisplayName(a), b.getDisplayName(b)));
        return result;
    }

    static String current(Context context) {
        try {
            LocaleList selected = context.getSystemService(LocaleManager.class).getApplicationLocales();
            return selected.isEmpty() ? context.getString(R.string.system_language)
                    : selected.get(0).getDisplayName(selected.get(0));
        } catch (RuntimeException error) { return context.getString(R.string.language_settings_unavailable); }
    }

    static void show(Context context) {
        try {
            List<Locale> locales = supported(context);
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            LocaleList current = manager.getApplicationLocales();
            String[] labels = new String[locales.size() + 1];
            labels[0] = context.getString(R.string.system_language);
            int selected = current.isEmpty() ? 0 : -1;
            for (int i = 0; i < locales.size(); i++) {
                Locale locale = locales.get(i);
                labels[i + 1] = locale.getDisplayName(locale);
                // Regional selections made in Android Settings stay untouched until an explicit choice.
                if (!current.isEmpty() && locale.equals(current.get(0))) selected = i + 1;
            }
            android.widget.TextView help = Ui.text(context, context.getString(R.string.app_language_help), 14, R.color.muted, false);
            help.setPadding(Ui.dp(context, 24), Ui.dp(context, 8), Ui.dp(context, 24), Ui.dp(context, 8));
            new MaterialAlertDialogBuilder(context).setTitle(R.string.app_language).setView(help)
                    .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                        try {
                            manager.setApplicationLocales(which == 0 ? LocaleList.getEmptyLocaleList()
                                    : new LocaleList(locales.get(which - 1)));
                            dialog.dismiss();
                        } catch (RuntimeException error) { failure(context); }
                    }).setNegativeButton(R.string.cancel, null).show();
        } catch (RuntimeException error) { failure(context); }
    }

    private static void failure(Context context) {
        new MaterialAlertDialogBuilder(context).setTitle(R.string.app_language)
                .setMessage(R.string.language_settings_unavailable).setPositiveButton(R.string.close, null).show();
    }

    private LanguagePicker() { }
}
