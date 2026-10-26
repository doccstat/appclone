package com.lixingchi.appclone;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Process;
import android.util.Log;

/** A finite reboot batch, not a resident or periodic service. */
public final class BootJob extends JobService {
    private static final Object RUN_LOCK = new Object();
    private Task active;

    private static final class Task {
        volatile boolean cancelled;
    }

    @Override public boolean onStartJob(JobParameters params) {
        if (Process.myUid() / 100000 != 0) return false;
        BootPolicy policy = BootPolicy.get(this);
        long run = params.getExtras().getLong("run", -1);
        String mode = params.getExtras().getString("mode", BootPolicy.SYSTEM);
        if (!policy.current(run, mode)) return false;
        Task task = new Task();
        active = task;
        new Thread(() -> {
            try {
                // A rescheduled slice must wait for the previous in-flight command/checkpoint.
                synchronized (RUN_LOCK) {
                    policy.apply(run, mode, () -> !task.cancelled, Shell::root);
                }
            } catch (RuntimeException e) {
                Log.e("AppClone", "Reboot action failed", e);
                try { policy.fail(run, mode, e.toString()); }
                catch (RuntimeException storageFailure) { Log.e("AppClone", "Could not save reboot error", storageFailure); }
            } finally {
                new Handler(getMainLooper()).post(() -> {
                    if (active == task) {
                        active = null;
                        jobFinished(params, false);
                    }
                });
            }
        }, "clone-boot").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (active != null) active.cancelled = true;
        active = null;
        return BootPolicy.get(this).current(params.getExtras().getLong("run", -1),
                params.getExtras().getString("mode", BootPolicy.SYSTEM));
    }

    @Override public void onDestroy() {
        if (active != null) active.cancelled = true;
        active = null;
        super.onDestroy();
    }
}
