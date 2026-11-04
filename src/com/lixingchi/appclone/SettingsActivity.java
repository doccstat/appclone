package com.lixingchi.appclone;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import android.app.job.JobScheduler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Compact summaries lead to explicit choices; saved policy and formats stay unchanged. */
public final class SettingsActivity extends AppCompatActivity {
    private MaterialSwitch prefix;
    private EditText format;
    private TextView preview, languageSummary, bootSummary;
    private LinearLayout formatFields;
    private com.google.android.material.chip.ChipGroup tokenRow;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout screen = Ui.column(this);
        Ui.applyInsets(screen, 0, 0, 0, 0);
        Ui.toolbar(this, screen, getString(R.string.settings), true);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        languageSummary = Ui.setting(this, root, R.string.app_language, LanguagePicker.current(this),
                () -> LanguagePicker.show(this));
        root.addView(Ui.divider(this));
        bootSummary = Ui.setting(this, root, R.string.after_reboot, bootLabel(), this::bootChoices);
        root.addView(Ui.divider(this));

        root.addView(Ui.label(this, getString(R.string.shortcut_labels)), Ui.margins(this, 0, 24, 0, 8));
        prefix = toggle(root, R.string.prefix_app_names, R.string.prefix_help, Settings.prefix(this));
        formatFields = Ui.column(this);
        root.addView(formatFields);
        format = Ui.input(this, formatFields, getString(R.string.shortcut_format));
        format.setText(Settings.format(this));
        formatFields.addView(Ui.text(this, getString(R.string.format_tokens), 13, R.color.muted, false));
        tokenRow = new com.google.android.material.chip.ChipGroup(this);
        tokenRow.setChipSpacingHorizontal(Ui.dp(this, 8));
        tokenRow.addView(tokenButton("{id}"));
        tokenRow.addView(tokenButton("{name}"));
        tokenRow.addView(tokenButton("{clone}"));
        formatFields.addView(tokenRow, Ui.margins(this, 0, 4, 0, 0));
        preview = Ui.text(this, "", 14, R.color.accent, true);
        formatFields.addView(preview, Ui.margins(this, 0, 8, 0, 8));
        root.addView(Ui.text(this, getString(R.string.shortcut_labels_help), 13, R.color.muted, false),
                Ui.margins(this, 0, 8, 0, 16));
        MaterialSwitch autoPin = toggle(root, R.string.auto_pin, R.string.auto_pin_help, Settings.autoPin(this));

        root.addView(Ui.divider(this, 0, 24));
        root.addView(Ui.label(this, getString(R.string.about)), Ui.margins(this, 0, 24, 0, 8));
        root.addView(Ui.text(this, getString(R.string.app_version, versionName()), 16, R.color.ink, false));
        root.addView(Ui.text(this, getString(R.string.appearance_help), 14, R.color.muted, false),
                Ui.margins(this, 0, 4, 0, 8));
        TextView source = Ui.text(this, getString(R.string.source_code), 14, R.color.accent, false);
        source.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        source.setMinHeight(Ui.dp(this, 48));
        source.setBackground(Ui.selectable(this));
        source.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/doccstat/appclone"))));
        root.addView(source);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        prefix.setOnCheckedChangeListener((button, checked) -> {
            Settings.prefix(this, checked);
            updateEnabled();
        });
        autoPin.setOnCheckedChangeListener((button, checked) -> Settings.autoPin(this, checked));
        format.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                Settings.format(SettingsActivity.this, s.toString());
                updatePreview();
            }
        });
        updateEnabled();
    }

    private MaterialSwitch toggle(LinearLayout parent, int label, int help, boolean checked) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(label);
        toggle.setMinHeight(Ui.dp(this, 48));
        toggle.setChecked(checked);
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(Ui.text(this, getString(help), 14, R.color.muted, false));
        return toggle;
    }

    private String bootLabel() {
        String mode = BootPolicy.get(this).mode();
        for (int i = 0; i < BootPolicy.VALUES.length; i++)
            if (BootPolicy.VALUES[i].equals(mode)) return getString(BootPolicy.LABELS[i]);
        throw new IllegalStateException("Unknown reboot policy: " + mode);
    }

    private void bootChoices() {
        BootPolicy boot = BootPolicy.get(this);
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), Ui.dp(this, 16));
        RadioGroup choices = new RadioGroup(this);
        for (int i = 0; i < BootPolicy.VALUES.length; i++) {
            RadioButton choice = new MaterialRadioButton(this);
            choice.setId(i + 1);
            choice.setText(BootPolicy.LABELS[i]);
            choice.setMinHeight(Ui.dp(this, 48));
            choices.addView(choice);
            if (BootPolicy.VALUES[i].equals(boot.mode())) choices.check(choice.getId());
        }
        body.addView(choices);
        body.addView(Ui.text(this, getString(R.string.boot_help), 14, R.color.muted, false));
        TextView report = Ui.text(this, boot.status(this), 13, R.color.muted, false);
        body.addView(report, Ui.margins(this, 0, 16, 0, 0));
        choices.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId < 1 || checkedId > BootPolicy.VALUES.length) return;
            String mode = BootPolicy.VALUES[checkedId - 1];
            try {
                boot.save(mode);
                JobScheduler jobs = getSystemService(JobScheduler.class);
                if (jobs != null) jobs.cancel(BootReceiver.JOB_ID);
            } catch (RuntimeException error) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.boot_save_failed)
                        .setMessage(getString(R.string.error_details, error.toString())).setPositiveButton(R.string.close, null).show();
                report.setText(R.string.boot_save_retry);
                group.clearCheck();
                bootSummary.setText(bootLabel());
                return;
            }
            bootSummary.setText(bootLabel());
            report.setText(boot.status(this));
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.after_reboot).setView(scroll)
                .setPositiveButton(R.string.close, null).show();
    }

    @Override protected void onResume() {
        super.onResume();
        languageSummary.setText(LanguagePicker.current(this));
        bootSummary.setText(bootLabel());
    }

    private com.google.android.material.chip.Chip tokenButton(final String token) {
        com.google.android.material.chip.Chip button = new com.google.android.material.chip.Chip(this);
        button.setText(token);
        button.setCheckable(false);
        button.setEnsureMinTouchTargetSize(true);
        button.setContentDescription(getString(R.string.insert_format_token, Ui.bidi(this, token)));
        button.setOnClickListener(v -> {
            int start = Math.max(0, format.getSelectionStart());
            int end = Math.max(start, format.getSelectionEnd());
            format.getText().replace(start, end, token);
        });
        return button;
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return ""; }
    }

    private void updateEnabled() {
        boolean on = prefix.isChecked();
        formatFields.setVisibility(on ? View.VISIBLE : View.GONE);
        format.setEnabled(on);
        for (int i = 0; i < tokenRow.getChildCount(); i++) tokenRow.getChildAt(i).setEnabled(on);
        updatePreview();
    }

    private void updatePreview() {
        preview.setText(getString(R.string.label_preview,
                Ui.bidi(this, Labels.format(format.getText().toString(), 12,
                        getString(R.string.preview_app_name), getString(R.string.preview_profile_name)))));
    }
}
