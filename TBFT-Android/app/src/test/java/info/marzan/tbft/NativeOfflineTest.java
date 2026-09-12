package info.marzan.tbft;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NativeOfflineTest {
    private TbftRepository repo;
    private Context context;
    private final String account = "cd820422-7a93-43f7-8c9e-7b67e620af66";
    private final String taskId = "822610ba-d98c-49f7-81f7-e98396a51bd3";
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        java.lang.reflect.Field singleton = TbftRepository.class.getDeclaredField("instance");
        singleton.setAccessible(true); singleton.set(null,null);
        context.getSharedPreferences("tbft_session_v1",Context.MODE_PRIVATE).edit().clear().putString("account",account).commit();
        context.deleteDatabase("tbft-offline-"+account+".db");
        repo = TbftRepository.get(context); repo.store().setMeta("workspace","053dbac3-7105-4423-a9a4-4f21d3a5e583");
        repo.store().ingest("tasks",Collections.singletonList(Json.of("id",taskId,"title","Offline test task","owner_user_id",account,"original_date",repo.today(),"priority","normal","completion_note","Before")));
    }
    @After public void cleanup() throws Exception {
        repo.io.submit(() -> {}).get(5,TimeUnit.SECONDS);
    }
    private View find(View view, String text) {
        if (view instanceof TextView && text.equals(((TextView)view).getText().toString())) return view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View result = find(((ViewGroup)view).getChildAt(i),text); if (result != null) return result;
        }
        return null;
    }
    private boolean hasWebView(View view) {
        if (view instanceof android.webkit.WebView) return true;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) if (hasWebView(((ViewGroup)view).getChildAt(i))) return true;
        return false;
    }
    @Test public void notebookSavesOfflineWithoutAWebView() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            assertFalse(hasWebView(root));
            View title = find(root,"Offline test task"); assertNotNull(title);
            ((View)title.getParent()).performClick();
            android.app.AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            View form = dialog.getWindow().getDecorView();
            EditText notes = (EditText)find(form,"Before"); assertNotNull(notes);
            notes.setText("Saved while offline");
            find(form,"Save on device").performClick();
            repo.io.submit(() -> {}).get(5,TimeUnit.SECONDS);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try (OfflineStore reopened = new OfflineStore(context,account)) {
                assertEquals("Saved while offline",Json.text(reopened.get("tasks",taskId).body,"completion_note"));
                assertTrue(reopened.get("tasks",taskId).dirty);
            }
        }
    }
    @Test public void widgetProjectionUsesPendingLocalRemindersAndCompletion() {
        repo.store().save("reminders",Json.of("id",UUID.randomUUID().toString(),"title","Local reminder","owner_user_id",account,"reminder_date",repo.today()));
        List<String> lines = repo.widgetLines();
        assertEquals(2,lines.size()); assertTrue(lines.get(0).contains("Local reminder"));
        repo.store().save("tasks",Json.merge(repo.store().get("tasks",taskId).body,Json.of("completed_at",java.time.Instant.now().toString())));
        assertEquals(1,repo.widgetLines().size());
    }
}
