package info.marzan.tbft;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

/** Widgets render the organiser database; they never need a successful network request. */
public class TbftWidgetProvider extends AppWidgetProvider {
    private static final int[] TASK_IDS = {R.id.task1, R.id.task2, R.id.task3, R.id.task4, R.id.task5, R.id.task6};
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
        SyncJobs.schedule(context);
    }
    public static void requestImmediateSync(Context context) { SyncJobs.schedule(context); SyncJobs.request(context); }
    public static List<String> getCachedTasks(Context context) { return TbftRepository.get(context).widgetLines(); }
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, TbftWidgetProvider.class))) render(context, manager, id);
        TbftAodWidgetProvider.updateAll(context);
    }
    private static void render(Context context, AppWidgetManager manager, int id) {
        TbftRepository repo = TbftRepository.get(context);
        List<String> lines = repo.widgetLines();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.tbft_widget);
        views.setTextViewText(R.id.widget_count, lines.size() + " items · " + repo.today());
        for (int i = 0; i < TASK_IDS.length; i++) {
            views.setViewVisibility(TASK_IDS[i], i < lines.size() ? View.VISIBLE : View.GONE);
            if (i < lines.size()) views.setTextViewText(TASK_IDS[i], "○  " + lines.get(i));
        }
        views.setViewVisibility(R.id.empty_text, lines.isEmpty() ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.empty_text, repo.workspaceId().isEmpty() ? "Open TBFT to download your workspace" : "You're clear for now");
        views.setTextViewText(R.id.widget_sync, (lines.size() > TASK_IDS.length ? "+" + (lines.size() - TASK_IDS.length) + " more · " : "") + repo.status());
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, open);
        manager.updateAppWidget(id, views);
    }
}
