package info.marzan.tbft;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TbftWidgetProvider extends AppWidgetProvider {
    public static final String PREFS = "tbft_widget";
    public static final String KEY_TASKS = "remaining_tasks";
    public static final String KEY_SYNC_TIME = "sync_time";
    public static final String KEY_REFRESH_TOKEN = "refresh_token";
    public static final String KEY_ERROR = "sync_error";
    public static final String KEY_BOARD_DATE = "board_date";

    private static final String WIDGET_API = "https://tbft.marzan.info/api/widget/tasks";

    private static final int[] TASK_IDS = {
            R.id.task1, R.id.task2, R.id.task3,
            R.id.task4, R.id.task5, R.id.task6
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
        startSync(context.getApplicationContext(), goAsync());
    }

    public static void saveRefreshToken(Context context, String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REFRESH_TOKEN, refreshToken.trim())
                .remove(KEY_ERROR)
                .apply();
        requestImmediateSync(context);
    }

    public static void requestImmediateSync(Context context) {
        startSync(context.getApplicationContext(), null);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        android.content.ComponentName component =
                new android.content.ComponentName(context, TbftWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
        TbftAodWidgetProvider.updateAll(context);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_TASKS, "");
        String error = prefs.getString(KEY_ERROR, "");
        long syncTime = prefs.getLong(KEY_SYNC_TIME, 0L);
        boolean hasSession = !prefs.getString(KEY_REFRESH_TOKEN, "").isEmpty();
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
            if (!error.isEmpty()) {
                views.setTextViewText(R.id.empty_text, error);
            } else if (!hasSession) {
                views.setTextViewText(R.id.empty_text, "Open TBFT once to connect");
            } else {
                views.setTextViewText(R.id.empty_text, "You're clear for now");
            }
        } else {
            views.setViewVisibility(R.id.empty_text, View.GONE);
        }

        if (!error.isEmpty()) {
            views.setTextViewText(R.id.widget_sync, "Sync needs attention · open TBFT");
        } else if (syncTime > 0L) {
            String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(syncTime));
            views.setTextViewText(R.id.widget_sync, "Updated " + time + " · every 30 min");
        } else {
            views.setTextViewText(R.id.widget_sync, "Updates automatically every 30 min");
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setAction("info.marzan.tbft.OPEN_FROM_WIDGET");
        PendingIntent openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_root, openPending);
        manager.updateAppWidget(widgetId, views);
    }

    private static void startSync(Context context, PendingResult pendingResult) {
        new Thread(() -> {
            try {
                syncNow(context);
            } finally {
                if (pendingResult != null) {
                    try { pendingResult.finish(); } catch (Exception ignored) { }
                }
            }
        }, "tbft-widget-sync").start();
    }

    private static void syncNow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "");
        if (refreshToken.isEmpty()) {
            updateAll(context);
            return;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(WIDGET_API);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject requestBody = new JSONObject();
            requestBody.put("refreshToken", refreshToken);
            byte[] body = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
            reader.close();

            JSONObject response = new JSONObject(raw.toString());
            if (code < 200 || code >= 300) {
                String message = response.optString("error", "Unable to refresh TBFT");
                prefs.edit().putString(KEY_ERROR, message).apply();
                updateAll(context);
                return;
            }

            JSONArray taskArray = response.optJSONArray("tasks");
            List<String> tasks = new ArrayList<>();
            if (taskArray != null) {
                for (int i = 0; i < taskArray.length(); i++) {
                    JSONObject task = taskArray.optJSONObject(i);
                    if (task == null) continue;
                    String title = task.optString("title", "").trim();
                    if (title.isEmpty()) continue;
                    String deadline = task.optString("deadline", "").trim();
                    boolean carried = task.optBoolean("carried", false);
                    String prefix = carried ? "↪ " : "";
                    String suffix = deadline.isEmpty() || "null".equals(deadline) ? "" : " · " + deadline;
                    tasks.add(prefix + title + suffix);
                }
            }

            String rotatedRefreshToken = response.optString("refreshToken", "").trim();
            String boardDate = response.optString("boardDate", "").trim();
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_TASKS, encode(tasks))
                    .putLong(KEY_SYNC_TIME, System.currentTimeMillis())
                    .remove(KEY_ERROR);
            if (!rotatedRefreshToken.isEmpty()) editor.putString(KEY_REFRESH_TOKEN, rotatedRefreshToken);
            if (!boardDate.isEmpty() && !"null".equals(boardDate)) editor.putString(KEY_BOARD_DATE, boardDate);
            editor.apply();
            updateAll(context);
        } catch (Exception e) {
            prefs.edit().putString(KEY_ERROR, "Couldn't sync automatically. Open TBFT to reconnect.").apply();
            updateAll(context);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String encode(List<String> tasks) {
        StringBuilder sb = new StringBuilder();
        for (String task : tasks) {
            if (task == null) continue;
            String clean = task.replace("\n", " ").replace("\r", " ").trim();
            if (clean.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean);
        }
        return sb.toString();
    }

    public static List<String> getCachedTasks(Context context) {
        return decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TASKS, ""));
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
