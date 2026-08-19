package info.marzan.tbft;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class TbftAodWidgetProvider extends AppWidgetProvider {
    private static final int[] TASK_IDS = {
            R.id.aod_task1, R.id.aod_task2, R.id.aod_task3, R.id.aod_task4
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
        TbftWidgetProvider.requestImmediateSync(context.getApplicationContext());
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, TbftAodWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(TbftWidgetProvider.PREFS, Context.MODE_PRIVATE);
        List<String> tasks = TbftWidgetProvider.getCachedTasks(context);
        boolean connected = !prefs.getString(TbftWidgetProvider.KEY_REFRESH_TOKEN, "").isEmpty();

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.tbft_aod_widget);
        int count = tasks.size();

        if (count > 0) {
            views.setTextViewText(R.id.aod_count, count + " remaining");
            views.setViewVisibility(R.id.aod_count, View.VISIBLE);
        } else {
            views.setTextViewText(R.id.aod_count, connected ? "clear" : "open TBFT once");
            views.setViewVisibility(R.id.aod_count, View.VISIBLE);
        }

        for (int i = 0; i < TASK_IDS.length; i++) {
            if (i < count) {
                views.setViewVisibility(TASK_IDS[i], View.VISIBLE);
                views.setTextViewText(TASK_IDS[i], "○  " + tasks.get(i));
            } else {
                views.setViewVisibility(TASK_IDS[i], View.GONE);
            }
        }

        if (count > TASK_IDS.length) {
            views.setViewVisibility(R.id.aod_more, View.VISIBLE);
            views.setTextViewText(R.id.aod_more, "+" + (count - TASK_IDS.length) + " more");
        } else {
            views.setViewVisibility(R.id.aod_more, View.GONE);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setAction("info.marzan.tbft.OPEN_FROM_AOD");
        PendingIntent openPending = PendingIntent.getActivity(
                context,
                2,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.aod_root, openPending);

        manager.updateAppWidget(widgetId, views);
    }
}
