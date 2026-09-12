package info.marzan.tbft;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class LocalClockReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SyncJobs.schedule(context);
        TbftRepository.get(context).changed();
    }
}
