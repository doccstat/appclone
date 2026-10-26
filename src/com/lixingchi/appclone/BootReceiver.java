package com.lixingchi.appclone;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.Process;
import android.util.Log;

/** BOOT_COMPLETED is delivered after this user's credential storage is unlocked. */
public final class BootReceiver extends BroadcastReceiver {
    static final int JOB_ID = 1;

    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Process.myUid() / 100000 != 0) return;
        BootPolicy policy = BootPolicy.get(context);
        String mode = policy.mode();
        long run = -1;
        try {
            run = policy.begin();
            if (run < 0) return;
            PersistableBundle extras = new PersistableBundle();
            extras.putLong("run", run);
            extras.putString("mode", mode);
            // Allow Android's delayed profile-start pass to settle before a Stop all pass.
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, BootJob.class))
                    .setExtras(extras).setMinimumLatency(10_000).setOverrideDeadline(30_000).build();
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null || scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                policy.fail(run, mode, "Android could not schedule the job.");
            }
        } catch (RuntimeException e) {
            Log.e("AppClone", "Could not schedule reboot action", e);
            try { policy.fail(run, mode, e.toString()); }
            catch (RuntimeException storageFailure) { Log.e("AppClone", "Could not save reboot error", storageFailure); }
        }
    }
}
