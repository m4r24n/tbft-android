package info.marzan.tbft;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class SyncJobs {
    static final int SYNC = 3101, PERIODIC = 3102, LOCAL_TICK = 3103;
    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        ComponentName service = new ComponentName(context, SyncService.class);
        if (scheduler.getPendingJob(PERIODIC) == null) scheduler.schedule(new JobInfo.Builder(PERIODIC, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30 * 60 * 1000L).setPersisted(true).build());
        // Recompute rollover without network, even if cloud services are down.
        if (scheduler.getPendingJob(LOCAL_TICK) == null) scheduler.schedule(new JobInfo.Builder(LOCAL_TICK, service)
                .setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
    }
    static void request(Context context) {
        schedule(context);
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null && scheduler.getPendingJob(SYNC) == null) scheduler.schedule(new JobInfo.Builder(SYNC, new ComponentName(context, SyncService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
    }
}
