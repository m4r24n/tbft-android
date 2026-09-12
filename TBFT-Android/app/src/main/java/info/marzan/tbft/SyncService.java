package info.marzan.tbft;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SyncService extends JobService {
    private final Set<Integer> active = ConcurrentHashMap.newKeySet();
    @Override public boolean onStartJob(JobParameters params) {
        if (params.getJobId() == SyncJobs.LOCAL_TICK) { TbftRepository.get(this).changed(); return false; }
        active.add(params.getJobId());
        TbftRepository.get(this).syncAsync(success -> {
            if (active.remove(params.getJobId())) jobFinished(params, !success);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        active.remove(params.getJobId());
        // HTTP requests are bounded. Unacknowledged local rows remain durable for retry.
        return true;
    }
}
