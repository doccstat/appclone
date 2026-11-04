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

    static android.widget.ImageButton iconButton(Context context, int icon) {
        androidx.appcompat.widget.AppCompatImageButton button = new androidx.appcompat.widget.AppCompatImageButton(context);
        button.setImageResource(icon);
        button.setBackground(selectable(context));
        button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        return button;
    }

    static android.widget.ImageButton overflow(Context context) {
        return iconButton(context, R.drawable.ic_more);
    }

    static Button addAction(Context context, android.widget.FrameLayout root, String label, InventoryList... lists) {
        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton button =
                new com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(context);
        button.setText(label);
        button.setIconResource(R.drawable.ic_add);
        button.setSingleLine(false);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.END);
        params.setMargins(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16));
        root.addView(button, params);
        button.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            for (InventoryList list : lists) list.setPadding(0, 0, 0, button.getHeight() + dp(context, 32));
        });
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            // A hidden FAB must not consume a short landscape keyboard viewport.
            int bottom = insets.isVisible(WindowInsets.Type.ime()) ? 0 : button.getHeight() + dp(context, 32);
            for (InventoryList list : lists) list.setPadding(0, 0, 0, bottom);
            return insets;
        });
        return button;
    }

    static android.widget.FrameLayout content(Context context, LinearLayout root, View body) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.addView(body, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        return frame;
    }

    static TextView setting(Context context, LinearLayout parent, int title, String summary, Runnable action) {
        LinearLayout row = row(context), labels = column(context);
        row.setPadding(0, dp(context, 12), 0, dp(context, 12));
        row.setMinimumHeight(dp(context, 64));
        row.setBackground(selectable(context));
        row.setScreenReaderFocusable(true);
        labels.addView(type(context, context.getString(title),
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, R.color.ink));
        TextView value = type(context, summary,
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium, R.color.muted);
        labels.addView(value);
        row.addView(labels, weight(1));
        android.widget.ImageView arrow = new android.widget.ImageView(context);
        arrow.setImageResource(R.drawable.ic_next);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
        return value;
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

    static com.google.android.material.textfield.TextInputEditText search(Context context,
            LinearLayout parent, String hint) {
        com.google.android.material.textfield.TextInputEditText edit = input(context, parent, hint);
        com.google.android.material.textfield.TextInputLayout field =
                (com.google.android.material.textfield.TextInputLayout) parent.getChildAt(parent.getChildCount() - 1);
        field.setHintEnabled(false);
        edit.setHint(hint);
        field.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_FILLED);
        field.setBoxCornerRadii(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        field.setBoxBackgroundColor(color(context, R.color.surface));
        field.setBoxStrokeWidth(0);
        field.setBoxStrokeWidthFocused(0);
        field.setStartIconDrawable(R.drawable.ic_search);
        field.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
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
            toolbar.setNavigationContentDescription(R.string.back);
            toolbar.setNavigationOnClickListener(v -> activity.finish());
        }
        toolbar.setTitleTextAppearance(activity, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        root.addView(toolbar);
        return toolbar;
    }

    /** Isolate display-only names in mixed-direction sentences; never alter stored input. */
    static String bidi(Context context, String value) {
        return android.text.BidiFormatter.getInstance(context.getResources().getConfiguration().getLocales().get(0))
                .unicodeWrap(value);
    }

    private Ui() {}
}
