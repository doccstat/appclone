package com.lixingchi.appclone;

import android.app.Application;
import android.content.Context;
import com.google.android.material.color.DynamicColors;

public final class CloneApplication extends Application {
    final InventoryCache inventory = new InventoryCache(() -> Inventory.load(this),
            task -> new Thread(task, "clone-inventory").start());

    static InventoryCache inventory(Context context) {
        return ((CloneApplication) context.getApplicationContext()).inventory;
    }

    @Override public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
