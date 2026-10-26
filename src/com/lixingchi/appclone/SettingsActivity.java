package com.lixingchi.appclone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobScheduler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Reboot policy, shortcut label format, and automation settings. */
public final class SettingsActivity extends Activity {
    private Switch prefix;
    private Switch autoPin;
    private EditText format;
    private TextView preview;
    private LinearLayout tokenRow;
    private TextView bootStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = Ui.column(this);

        LinearLayout head = Ui.row(this);
        TextView back = Ui.text(this, "\u2039", 28, R.color.ink, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        head.addView(back);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, "Settings", 24, R.color.ink, true));
        titles.addView(Ui.text(this, "Profiles and shortcuts", 13,
                R.color.muted, false), Ui.margins(this, 0, 2, 0, 0));
        head.addView(titles, Ui.weight(1));
        root.addView(head);

        root.addView(Ui.label(this, "After reboot"), Ui.margins(this, 0, 26, 0, 8));
        BootPolicy boot = BootPolicy.get(this);
        RadioGroup bootChoices = new RadioGroup(this);
        for (int i = 0; i < BootPolicy.VALUES.length; i++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(i + 1);
            choice.setText(BootPolicy.LABELS[i]);
            bootChoices.addView(choice);
            if (BootPolicy.VALUES[i].equals(boot.mode())) bootChoices.check(choice.getId());
        }
        root.addView(bootChoices);
        root.addView(Ui.text(this,
                "Applies once, at least 10 seconds after the main user unlocks after reboot; Android may delay it. "
                        + "System default leaves profiles alone. Stop all preserves data but pauses notifications. "
                        + "Root must already be granted and available at boot. Start and shortcuts still work afterward.",
                13, R.color.muted, false));
        bootStatus = Ui.text(this, boot.status(), 12, R.color.muted, false);
        root.addView(bootStatus, Ui.margins(this, 0, 8, 0, 0));
        bootChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId < 1 || checkedId > BootPolicy.VALUES.length) return;
            String mode = BootPolicy.VALUES[checkedId - 1];
            try {
                boot.save(mode);
                JobScheduler jobs = getSystemService(JobScheduler.class);
                if (jobs != null) jobs.cancel(BootReceiver.JOB_ID);
            } catch (RuntimeException error) {
                new AlertDialog.Builder(this).setTitle("Could not save reboot setting")
                        .setMessage(error.toString()).setPositiveButton("Close", null).show();
                bootStatus.setText("Reboot setting could not be saved. Select it again to retry.");
                group.clearCheck();
                return;
            }
            bootStatus.setText(boot.status());
        });

        root.addView(Ui.label(this, "Shortcut labels"), Ui.margins(this, 0, 26, 0, 8));

        prefix = new Switch(this);
        prefix.setChecked(Settings.prefix(this));
        LinearLayout prefixRow = Ui.row(this);
        prefixRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout prefixText = Ui.column(this);
        prefixText.addView(Ui.text(this, "Prefix clone app names", 16, R.color.ink, false));
        prefixText.addView(Ui.text(this,
                "Put the clone id before the app name, so cloned shortcuts are distinguishable.",
                13, R.color.muted, false));
        prefixRow.addView(prefixText, Ui.weight(1));
        prefixRow.addView(prefix);
        root.addView(prefixRow);

        root.addView(Ui.divider(this));

        root.addView(Ui.label(this, "Format"), Ui.margins(this, 0, 18, 0, 6));
        format = new EditText(this);
        format.setSingleLine(true);
        format.setText(Settings.format(this));
        root.addView(format);
        root.addView(Ui.text(this,
                "Tokens: {id} clone user id \u00b7 {name} app name \u00b7 {clone} clone name. "
                        + "Colons and other characters are allowed.",
                12, R.color.muted, false), Ui.margins(this, 0, 6, 0, 0));
        tokenRow = Ui.row(this);
        tokenRow.addView(tokenButton("{id}"));
        tokenRow.addView(tokenButton("{name}"));
        tokenRow.addView(tokenButton("{clone}"));
        root.addView(tokenRow, Ui.margins(this, 0, 6, 0, 0));
        preview = Ui.text(this, "", 14, R.color.accent, true);
        root.addView(preview, Ui.margins(this, 0, 12, 0, 0));

        root.addView(Ui.divider(this, 0, 20));

        root.addView(Ui.label(this, "Automation"), Ui.margins(this, 0, 18, 0, 8));

        autoPin = new Switch(this);
        autoPin.setChecked(Settings.autoPin(this));
        LinearLayout autoRow = Ui.row(this);
        autoRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout autoText = Ui.column(this);
        autoText.addView(Ui.text(this, "Auto-pin new clone apps", 16, R.color.ink, false));
        autoText.addView(Ui.text(this,
                "Request a home-screen shortcut as each app is added to a clone. "
                        + "The launcher still asks you to confirm.",
                13, R.color.muted, false));
        autoRow.addView(autoText, Ui.weight(1));
        autoRow.addView(autoPin);
        root.addView(autoRow);

        root.addView(Ui.text(this,
                "Labels apply to shortcuts created from now on. Re-pin an existing shortcut "
                        + "to update its name.",
                12, R.color.muted, false), Ui.margins(this, 0, 20, 0, 0));

        root.addView(Ui.divider(this, 0, 24));
        root.addView(Ui.label(this, "About"), Ui.margins(this, 0, 18, 0, 6));
        root.addView(Ui.text(this, "App Clone " + versionName(), 15, R.color.ink, false));
        TextView source = Ui.text(this, "Source code \u00b7 github.com/doccstat/appclone",
                13, R.color.accent, false);
        source.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        source.setBackground(Ui.selectable(this));
        source.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/doccstat/appclone")));
            }
        });
        root.addView(source, Ui.margins(this, 0, 4, 0, 0));

        ScrollView scroll = new ScrollView(this);
        Ui.applyInsets(scroll, 20, 22, 20, 16);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

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
        bootStatus.setText(BootPolicy.get(this).status());
    }

    private Button tokenButton(final String token) {
        Button button = Ui.quiet(this, token);
        button.setTextSize(13);
        button.setMinHeight(Ui.dp(this, 40));
        button.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Ui.dp(this, 6));
        button.setLayoutParams(params);
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
        preview.setText("Preview: "
                + Labels.format(format.getText().toString(), 12, "Chrome", "Clone 12"));
    }
}
