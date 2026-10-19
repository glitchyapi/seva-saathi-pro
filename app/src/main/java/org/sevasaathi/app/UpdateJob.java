package org.sevasaathi.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Best-effort six-hour checks. No claim of instant push delivery. */
public final class UpdateJob extends JobService {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Future<?> running;
    public static void schedule(Context context){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler.getPendingJob(8801)!=null)return;
        scheduler.schedule(new JobInfo.Builder(8801,new ComponentName(context,UpdateJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(6L*60*60*1000,60L*60*1000)
            .setPersisted(true).setBackoffCriteria(30*60*1000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    @Override public boolean onStartJob(JobParameters params){
        running=executor.submit(()->{boolean retry=false;try{new UpdateManager(this,null).check(false);}catch(Exception e){retry=true;}jobFinished(params,retry);});return true;
    }
    @Override public boolean onStopJob(JobParameters params){if(running!=null)running.cancel(true);return true;}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
