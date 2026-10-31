package com.lixingchi.appclone;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
    static void applyInsets(View root, int leftDp, int topDp, int rightDp, int bottomDp) {
        applyInsets(root, leftDp, topDp, rightDp, bottomDp, null);
    }

    static void applyInsets(final View root, final int leftDp, final int topDp,
                            final int rightDp, final int bottomDp, final View keyboardAccessory) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                Context context = view.getContext();
                if (keyboardAccessory != null) keyboardAccessory.setVisibility(
                        insets.isVisible(WindowInsets.Type.ime()) ? View.GONE : View.VISIBLE);
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
        int appearance = sizeSp >= 22 ? com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall
                : sizeSp >= 18 ? com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
                : sizeSp >= 16 ? com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
                : sizeSp >= 14 ? com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
                : com.google.android.material.R.style.TextAppearance_Material3_BodySmall;
        TextView view = type(context, value, appearance, colorRes);
        if (medium) view.setTypeface(Typeface.create(MEDIUM, Typeface.NORMAL));
        return view;
    }

    static TextView type(Context context, CharSequence value, int appearance, int colorRes) {
        TextView view = new com.google.android.material.textview.MaterialTextView(context);
        view.setTextAppearance(appearance);
        view.setText(value);
        view.setTextColor(color(context, colorRes));
        return view;
    }

    static TextView label(Context context, CharSequence value) {
        TextView view = type(context, value, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall, R.color.accent);
        view.setAccessibilityHeading(true);
        return view;
    }

    static android.widget.ImageButton overflow(Context context) {
        androidx.appcompat.widget.AppCompatImageButton button = new androidx.appcompat.widget.AppCompatImageButton(context);
        button.setImageResource(R.drawable.ic_more);
        button.setBackground(selectable(context));
        button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        return button;
    }

    static Button addAction(Context context, LinearLayout root, String label) {
        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton button =
                new com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(context);
        button.setText(label);
        button.setIconResource(R.drawable.ic_add);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = android.view.Gravity.END;
        params.setMargins(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        root.addView(button, params);
        return button;
    }

    /** Tinted square showing a clone's user id. */
    static TextView avatar(Context context, CharSequence value) {
        TextView view = type(context, value, com.google.android.material.R.style.TextAppearance_Material3_LabelMedium, R.color.accent);
        view.setGravity(android.view.Gravity.CENTER);
        view.setBackground(shape(context, R.color.accent_tint, 14));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        return view;
    }

    static Button quiet(Context context, CharSequence value) {
        Button button = new com.google.android.material.button.MaterialButton(
                new androidx.appcompat.view.ContextThemeWrapper(context, R.style.ThemeOverlay_AppClone_TextButton), null,
                com.google.android.material.R.attr.materialButtonStyle);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        return button;
    }

    static Drawable shape(Context context, int colorRes, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, colorRes));
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
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

    static com.google.android.material.textfield.TextInputEditText input(Context context,
            LinearLayout parent, String hint) {
        com.google.android.material.textfield.TextInputLayout field =
                new com.google.android.material.textfield.TextInputLayout(context);
        field.setHint(hint);
        field.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
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
        toolbar.setTitleTextAppearance(activity, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        root.addView(toolbar);
        return toolbar;
    }

    private Ui() {}
}
