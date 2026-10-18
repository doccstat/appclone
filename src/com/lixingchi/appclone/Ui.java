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

/** Framework-only view helpers; the app deliberately avoids AndroidX. */
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
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                Context context = view.getContext();
                view.setPadding(dp(context, leftDp), bars.top + dp(context, topDp),
                        dp(context, rightDp), bars.bottom + dp(context, bottomDp));
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
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(context.getColor(colorRes));
        if (medium) view.setTypeface(Typeface.create(MEDIUM, Typeface.NORMAL));
        return view;
    }

    static TextView label(Context context, CharSequence value) {
        TextView view = text(context, value.toString().toUpperCase(), 12, R.color.muted, true);
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
        Button button = button(context, value, R.color.on_accent, true);
        button.setBackground(rounded(context, R.color.accent, 14));
        return button;
    }

    static Button quiet(Context context, CharSequence value) {
        Button button = button(context, value, R.color.accent, false);
        button.setBackground(selectable(context));
        return button;
    }

    static Button danger(Context context, CharSequence value) {
        Button button = button(context, value, R.color.paper, true);
        button.setBackground(rounded(context, R.color.danger, 14));
        return button;
    }

    /** Destructive but secondary; no heavy fill competing with the screen's real action. */
    static Button quietDanger(Context context, CharSequence value) {
        Button button = button(context, value, R.color.danger, false);
        button.setBackground(selectable(context));
        return button;
    }

    private static Button button(Context context, CharSequence value, int colorRes, boolean medium) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTextColor(context.getColor(colorRes));
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        if (medium) button.setTypeface(Typeface.create(MEDIUM, Typeface.NORMAL));
        return button;
    }

    static Drawable shape(Context context, int colorRes, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(context.getColor(colorRes));
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static Drawable rounded(Context context, int colorRes, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.ripple)),
                shape(context, colorRes, radiusDp), null);
    }

    static Drawable selectable(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return context.getDrawable(value.resourceId);
    }

    static View divider(Context context) {
        return divider(context, 0);
    }

    static View divider(Context context, int startDp) {
        View view = new View(context);
        view.setBackgroundColor(context.getColor(R.color.line));
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

    private Ui() {}
}
