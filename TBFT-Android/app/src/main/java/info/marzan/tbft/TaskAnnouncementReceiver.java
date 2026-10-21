package info.marzan.tbft;

import android.content.*;
import java.time.Instant;

public class TaskAnnouncementReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        TaskAnnouncements.schedule(context);
        if(!TaskAnnouncements.enabled(context))return;
        TbftRepository repo=TbftRepository.get(context);
        if(TaskAnnouncementRules.allowed(Instant.now(),repo.timezone()))
            TaskAnnouncements.speak(context,repo.remainingTaskTitles());
    }
}
