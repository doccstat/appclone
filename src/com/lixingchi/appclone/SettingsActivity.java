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
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Reboot policy, shortcut label format, and automation settings. */
public final class SettingsActivity extends AppCompatActivity {
    private MaterialSwitch prefix;
    private MaterialSwitch autoPin;
    private EditText format;
    private TextView preview;
    private com.google.android.material.chip.ChipGroup tokenRow;
    private TextView bootStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout screen = Ui.column(this);
        Ui.applyInsets(screen, 0, 0, 0, 0);
        Ui.toolbar(this, screen, getString(R.string.settings), true);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 24));
        root.addView(Ui.text(this, getString(R.string.appearance_help), 14, R.color.muted, false));
        android.widget.Button language = Ui.quiet(this, getString(R.string.app_language));
        language.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException error) {
                new MaterialAlertDialogBuilder(this).setMessage(R.string.language_settings_unavailable)
                        .setPositiveButton(R.string.close, null).show();
            }
        });
        root.addView(language);
        root.addView(Ui.text(this, getString(R.string.app_language_help), 13, R.color.muted, false));

        root.addView(Ui.label(this, getString(R.string.after_reboot)), Ui.margins(this, 0, 26, 0, 8));
        BootPolicy boot = BootPolicy.get(this);
        RadioGroup bootChoices = new RadioGroup(this);
        for (int i = 0; i < BootPolicy.VALUES.length; i++) {
            RadioButton choice = new MaterialRadioButton(this);
            choice.setId(i + 1);
            choice.setText(BootPolicy.LABELS[i]);
            bootChoices.addView(choice);
            if (BootPolicy.VALUES[i].equals(boot.mode())) bootChoices.check(choice.getId());
        }
        root.addView(bootChoices);
        root.addView(Ui.text(this, getString(R.string.boot_help), 13, R.color.muted, false));
        bootStatus = Ui.text(this, boot.status(this), 12, R.color.muted, false);
        root.addView(bootStatus, Ui.margins(this, 0, 8, 0, 0));
        bootChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId < 1 || checkedId > BootPolicy.VALUES.length) return;
            String mode = BootPolicy.VALUES[checkedId - 1];
            try {
                boot.save(mode);
                JobScheduler jobs = getSystemService(JobScheduler.class);
                if (jobs != null) jobs.cancel(BootReceiver.JOB_ID);
            } catch (RuntimeException error) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.boot_save_failed)
                        .setMessage(getString(R.string.error_details, error.toString())).setPositiveButton(R.string.close, null).show();
                bootStatus.setText(R.string.boot_save_retry);
                group.clearCheck();
                return;
            }
            bootStatus.setText(boot.status(this));
        });

        root.addView(Ui.label(this, getString(R.string.shortcut_labels)), Ui.margins(this, 0, 26, 0, 8));

        prefix = new MaterialSwitch(this);
        prefix.setContentDescription(getString(R.string.prefix_app_names));
        prefix.setChecked(Settings.prefix(this));
        LinearLayout prefixRow = Ui.row(this);
        prefixRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout prefixText = Ui.column(this);
        prefixText.addView(Ui.text(this, getString(R.string.prefix_app_names), 16, R.color.ink, false));
        prefixText.addView(Ui.text(this, getString(R.string.prefix_help), 13, R.color.muted, false));
        prefixRow.addView(prefixText, Ui.weight(1));
        prefixRow.addView(prefix);
        root.addView(prefixRow);

        root.addView(Ui.divider(this));

        root.addView(Ui.label(this, getString(R.string.format)), Ui.margins(this, 0, 18, 0, 6));
        format = Ui.input(this, root, getString(R.string.shortcut_format));
        format.setText(Settings.format(this));
        root.addView(Ui.text(this, getString(R.string.format_tokens), 12, R.color.muted, false), Ui.margins(this, 0, 6, 0, 0));
        tokenRow = new com.google.android.material.chip.ChipGroup(this);
        tokenRow.setChipSpacingHorizontal(Ui.dp(this, 8));
        tokenRow.addView(tokenButton("{id}"));
        tokenRow.addView(tokenButton("{name}"));
        tokenRow.addView(tokenButton("{clone}"));
        root.addView(tokenRow, Ui.margins(this, 0, 6, 0, 0));
        preview = Ui.text(this, "", 14, R.color.accent, true);
        root.addView(preview, Ui.margins(this, 0, 12, 0, 0));

        root.addView(Ui.divider(this, 0, 20));

        root.addView(Ui.label(this, getString(R.string.automation)), Ui.margins(this, 0, 18, 0, 8));

        autoPin = new MaterialSwitch(this);
        autoPin.setContentDescription(getString(R.string.auto_pin));
        autoPin.setChecked(Settings.autoPin(this));
        LinearLayout autoRow = Ui.row(this);
        autoRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout autoText = Ui.column(this);
        autoText.addView(Ui.text(this, getString(R.string.auto_pin), 16, R.color.ink, false));
        autoText.addView(Ui.text(this, getString(R.string.auto_pin_help), 13, R.color.muted, false));
        autoRow.addView(autoText, Ui.weight(1));
        autoRow.addView(autoPin);
        root.addView(autoRow);

        root.addView(Ui.text(this, getString(R.string.shortcut_labels_help), 12, R.color.muted, false), Ui.margins(this, 0, 20, 0, 0));

        root.addView(Ui.divider(this, 0, 24));
        root.addView(Ui.label(this, getString(R.string.about)), Ui.margins(this, 0, 18, 0, 6));
        root.addView(Ui.text(this, getString(R.string.app_version, versionName()), 15, R.color.ink, false));
        TextView source = Ui.text(this, getString(R.string.source_code),
                13, R.color.accent, false);
        source.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        source.setMinHeight(Ui.dp(this, 48));
        source.setBackground(Ui.selectable(this));
        source.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/doccstat/appclone")));
            }
        });
        root.addView(source, Ui.margins(this, 0, 4, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        prefix.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.prefix(SettingsActivity.this, checked);
                updateEnabled();
            }
        });
        autoPin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.autoPin(SettingsActivity.this, checked);
            }
        });
        format.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Settings.format(SettingsActivity.this, s.toString());
                updatePreview();
            }
        });

        updateEnabled();
    }

    @Override protected void onResume() {
        super.onResume();
        bootStatus.setText(BootPolicy.get(this).status(this));
    }

    private com.google.android.material.chip.Chip tokenButton(final String token) {
        com.google.android.material.chip.Chip button = new com.google.android.material.chip.Chip(this);
        button.setText(token);
        button.setCheckable(false);
        button.setEnsureMinTouchTargetSize(true);
        button.setContentDescription(getString(R.string.insert_format_token, Ui.bidi(this, token)));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int start = Math.max(0, format.getSelectionStart());
                int end = Math.max(start, format.getSelectionEnd());
                format.getText().replace(start, end, token);
            }
        });
        return button;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void updateEnabled() {
        boolean on = prefix.isChecked();
        format.setEnabled(on);
        format.setAlpha(on ? 1f : 0.45f);
        for (int i = 0; i < tokenRow.getChildCount(); i++) {
            tokenRow.getChildAt(i).setEnabled(on);
        }
        tokenRow.setAlpha(on ? 1f : 0.45f);
        preview.setVisibility(on ? View.VISIBLE : View.GONE);
        updatePreview();
    }

    private void updatePreview() {
        preview.setText(getString(R.string.label_preview,
                Ui.bidi(this, Labels.format(format.getText().toString(), 12,
                        getString(R.string.preview_app_name), getString(R.string.preview_profile_name)))));
    }
}
