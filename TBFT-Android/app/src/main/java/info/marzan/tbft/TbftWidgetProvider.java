package info.marzan.tbft;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TbftWidgetProvider extends AppWidgetProvider {
    public static final String PREFS = "tbft_widget";
    public static final String KEY_TASKS = "remaining_tasks";
    public static final String KEY_SYNC_TIME = "sync_time";

    private static final int[] TASK_IDS = {
            R.id.task1, R.id.task2, R.id.task3,
            R.id.task4, R.id.task5, R.id.task6
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        android.content.ComponentName component =
                new android.content.ComponentName(context, TbftWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_TASKS, "");
        long syncTime = prefs.getLong(KEY_SYNC_TIME, 0L);
        List<String> tasks = decode(stored);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.tbft_widget);
        int count = tasks.size();
        views.setTextViewText(R.id.widget_count,
                count == 0 ? "No remaining tasks" : count + (count == 1 ? " task remaining" : " tasks remaining"));

        for (int i = 0; i < TASK_IDS.length; i++) {
            if (i < tasks.size()) {
                views.setViewVisibility(TASK_IDS[i], View.VISIBLE);
                views.setTextViewText(TASK_IDS[i], "○  " + tasks.get(i));
            } else {
                views.setViewVisibility(TASK_IDS[i], View.GONE);
            }
        }

        if (count == 0) {
            views.setViewVisibility(R.id.empty_text, View.VISIBLE);
            views.setTextViewText(R.id.empty_text,
                    syncTime == 0L ? "Open TBFT once to sync today's board" : "You're clear for now");
        } else {
            views.setViewVisibility(R.id.empty_text, View.GONE);
        }

        if (syncTime > 0L) {
            String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(syncTime));
            views.setTextViewText(R.id.widget_sync, "Synced " + time + " · tap to refresh");
        } else {
            views.setTextViewText(R.id.widget_sync, "Tap to open and sync");
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setAction("info.marzan.tbft.OPEN_FROM_WIDGET");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_refresh, pendingIntent);

        manager.updateAppWidget(widgetId, views);
    }

    public static void saveTasks(Context context, List<String> tasks) {
        StringBuilder sb = new StringBuilder();
        for (String task : tasks) {
            if (task == null) continue;
            String clean = task.replace("\n", " ").replace("\r", " ").trim();
            if (clean.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TASKS, sb.toString())
                .putLong(KEY_SYNC_TIME, System.currentTimeMillis())
                .apply();
        updateAll(context);
    }

    private static List<String> decode(String stored) {
        List<String> out = new ArrayList<>();
        if (stored == null || stored.trim().isEmpty()) return out;
        String[] lines = stored.split("\\n");
        for (String line : lines) {
            String clean = line.trim();
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }
}
