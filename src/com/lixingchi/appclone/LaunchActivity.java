package com.lixingchi.appclone;

import android.app.Activity;
import android.os.Bundle;

/** Transparent trampoline: the shortcut starts it, it launches the clone app and exits. */
public final class LaunchActivity extends Activity {
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_USER = "user";
    static final String EXTRA_ACTIVITY = "activity";
    static final String EXTRA_LABEL = "label";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int user = getIntent().getIntExtra(EXTRA_USER, -1);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        String activity = getIntent().getStringExtra(EXTRA_ACTIVITY);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        if (user >= 0 && pkg != null && activity != null) {
            Apps.launch(user, new Apps.App(label == null ? "" : label, pkg, activity));
        }
        finish();
    }
}
