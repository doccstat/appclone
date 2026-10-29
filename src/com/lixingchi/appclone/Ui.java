package com.lixingchi.appclone;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared Material 3 Expressive widgets and dynamic colour roles. */
final class Ui {
    private static final String MEDIUM = "sans-serif-medium";

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Edge-to-edge is on by default, so inset the root past the status and nav bars. */
    static void applyInsets(final View root, final int leftDp, final int topDp,
                            final int rightDp, final int bottomDp) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                Context context = view.getContext();
                view.setPadding(bars.left + dp(context, leftDp), bars.top + dp(context, topDp),
                        bars.right + dp(context, rightDp), bars.bottom + dp(context, bottomDp));
                return insets;
            }
        });
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return layout;
    }

    static TextView text(Context context, CharSequence value, float sizeSp, int colorRes, boolean medium) {
        TextView view = new com.google.android.material.textview.MaterialTextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color(context, colorRes));
        if (medium) view.setTypeface(Typeface.create(MEDIUM, Typeface.NORMAL));
        return view;
    }

    static TextView label(Context context, CharSequence value) {
        TextView view = text(context, value.toString().toUpperCase(java.util.Locale.getDefault()), 12, R.color.muted, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    /** Tinted square showing a clone's user id. */
    static TextView avatar(Context context, CharSequence value) {
        TextView view = text(context, value, 16, R.color.accent, true);
        view.setGravity(android.view.Gravity.CENTER);
        view.setBackground(shape(context, R.color.accent_tint, 14));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        return view;
    }

    static Button primary(Context context, CharSequence value) {
        Button button = new com.google.android.material.button.MaterialButton(context);
        button.setText(value);
        return button;
    }

    static Button quiet(Context context, CharSequence value) {
        return button(context, value, R.color.accent, false);
    }

    static Button danger(Context context, CharSequence value) {
        com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(context);
        button.setText(value);
        button.setBackgroundTintList(ColorStateList.valueOf(color(context, R.color.danger)));
        button.setTextColor(com.google.android.material.color.MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnError, "Ui"));
        return button;
    }

    /** Destructive but secondary; no heavy fill competing with the screen's real action. */
    static Button quietDanger(Context context, CharSequence value) {
        return button(context, value, R.color.danger, false);
    }

    private static Button button(Context context, CharSequence value, int colorRes, boolean medium) {
        Button button = new com.google.android.material.button.MaterialButton(context, null,
                androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTextColor(color(context, colorRes));
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        if (medium) button.setTypeface(Typeface.create(MEDIUM, Typeface.NORMAL));
        return button;
    }

    static Drawable shape(Context context, int colorRes, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, colorRes));
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static Drawable rounded(Context context, int colorRes, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(color(context, R.color.ripple)),
                shape(context, colorRes, radiusDp), null);
    }

    static Drawable selectable(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return androidx.appcompat.content.res.AppCompatResources.getDrawable(context, value.resourceId);
    }

    static View divider(Context context) {
        return divider(context, 0);
    }

    static View divider(Context context, int startDp) {
        View view = new View(context);
        view.setBackgroundColor(color(context, R.color.line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1)));
        params.setMarginStart(dp(context, startDp));
        view.setLayoutParams(params);
        return view;
    }

    static View divider(Context context, int startDp, int topDp) {
        View view = divider(context, startDp);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = dp(context, topDp);
        view.setLayoutParams(params);
        return view;
    }

    static LinearLayout.LayoutParams margins(Context context, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    static LinearLayout.LayoutParams weight(int value) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, value);
    }

    static LinearLayout.LayoutParams weightWithStart(Context context, int value, int startDp) {
        LinearLayout.LayoutParams params = weight(value);
        params.setMarginStart(dp(context, startDp));
        return params;
    }

    static int color(Context context, int role) {
        int attr = com.google.android.material.R.attr.colorOnSurface;
        if (role == R.color.paper || role == R.color.surface) attr = com.google.android.material.R.attr.colorSurface;
        if (role == R.color.surface) attr = com.google.android.material.R.attr.colorSurfaceContainerLow;
        else if (role == R.color.muted) attr = com.google.android.material.R.attr.colorOnSurfaceVariant;
        else if (role == R.color.accent) attr = androidx.appcompat.R.attr.colorPrimary;
        else if (role == R.color.accent_tint) attr = com.google.android.material.R.attr.colorPrimaryContainer;
        else if (role == R.color.on_accent) attr = com.google.android.material.R.attr.colorOnPrimary;
        else if (role == R.color.danger) attr = androidx.appcompat.R.attr.colorError;
        else if (role == R.color.line) attr = com.google.android.material.R.attr.colorOutlineVariant;
        else if (role == R.color.ripple) attr = androidx.appcompat.R.attr.colorControlHighlight;
        return com.google.android.material.color.MaterialColors.getColor(context, attr, "App Clone");
    }

    static LinearLayout appHeading(Context context, Inventory.Entry entry, String title) {
        LinearLayout row = row(context);
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageDrawable(entry.icon);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));
        LinearLayout labels = column(context);
        labels.addView(text(context, title, 18, R.color.ink, true));
        labels.addView(text(context, entry.app.pkg, 12, R.color.muted, false));
        row.addView(labels, weightWithStart(context, 1, 16));
        return row;
    }

    static com.google.android.material.textfield.TextInputEditText input(Context context,
            LinearLayout parent, String hint) {
        com.google.android.material.textfield.TextInputLayout field =
                new com.google.android.material.textfield.TextInputLayout(context);
        field.setHint(hint);
        com.google.android.material.textfield.TextInputEditText edit =
                new com.google.android.material.textfield.TextInputEditText(field.getContext());
        edit.setSingleLine(true);
        field.addView(edit);
        parent.addView(field, margins(context, 0, 8, 0, 8));
        return edit;
    }

    static androidx.appcompat.app.AlertDialog progress(Context context, String message) {
        LinearLayout body = row(context);
        body.setPadding(dp(context, 24), dp(context, 16), dp(context, 24), dp(context, 16));
        com.google.android.material.progressindicator.CircularProgressIndicator spinner =
                new com.google.android.material.progressindicator.CircularProgressIndicator(context);
        spinner.setIndeterminate(true);
        body.addView(spinner);
        body.addView(text(context, message, 16, R.color.ink, false), weightWithStart(context, 1, 16));
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setView(body).setCancelable(false).create();
        dialog.show();
        return dialog;
    }

    static com.google.android.material.appbar.MaterialToolbar toolbar(
            androidx.appcompat.app.AppCompatActivity activity, LinearLayout root, String title, boolean back) {
        com.google.android.material.appbar.MaterialToolbar toolbar =
                new com.google.android.material.appbar.MaterialToolbar(activity);
        toolbar.setTitle(title);
        if (back) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
            toolbar.setNavigationContentDescription("Back");
            toolbar.setNavigationOnClickListener(v -> activity.finish());
        }
        root.addView(toolbar);
        return toolbar;
    }

    private Ui() {}
}
