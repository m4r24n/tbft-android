package info.marzan.tbft;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

/** Fixture data is compiled into the test APK only; no live account or network is needed. */
public class NativeScreensTest {
    @Test public void offlineDailyFlowAndScreenshots() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.executeShellCommand("svc wifi disable");device.executeShellCommand("svc data disable");
        String account="cd820422-7a93-43f7-8c9e-7b67e620af66",partner="bad21cf5-83d4-4c6a-88c9-d3d098082757",space="053dbac3-7105-4423-a9a4-4f21d3a5e583",project=UUID.randomUUID().toString();
        context.getSharedPreferences("tbft_session_v1",Context.MODE_PRIVATE).edit().clear().putString("account",account).commit();
        TbftRepository repo=TbftRepository.get(context);OfflineStore store=repo.store();store.setMeta("workspace",space);store.setMeta("core_sync","fixture");
        store.ingest("workspaces",Collections.singletonList(Json.of("id",space,"name","Our workspace","timezone","Europe/Berlin","rollover_hour",6)));
        store.ingest("profiles",Arrays.asList(Json.of("id",account,"display_name","Marzan"),Json.of("id",partner,"display_name","Partner")));
        store.ingest("projects",Collections.singletonList(Json.of("id",project,"workspace_id",space,"owner_user_id",account,"name","A more thoughtful home","description","Small improvements for a calmer everyday life.","target_date","2026-10-15")));
        store.ingest("tasks",Arrays.asList(
            Json.of("id",UUID.randomUUID().toString(),"workspace_id",space,"title","Plan the weekend","owner_user_id",account,"original_date",repo.today(),"priority","normal","project_id",project),
            Json.of("id",UUID.randomUUID().toString(),"workspace_id",space,"title","Pick up groceries","owner_user_id",account,"original_date",repo.today(),"priority","normal","deadline","18:00:00")));
        store.ingest("reminders",Collections.singletonList(Json.of("id",UUID.randomUUID().toString(),"title","A little time outside","note","Take a walk before sunset.","owner_user_id",account,"reminder_date",repo.today())));
        store.changeWardrobe(space,account,repo.today(),d->{
            WardrobeRules.saveItem(d,"","White T-shirt","tshirts","#FFFFFF","White","both","",3);
            WardrobeRules.saveItem(d,"","Everyday jeans","jeans","#54708D","Blue","outdoor","",2);
            WardrobeRules.saveItem(d,"","Sage shirt","shirts","#809377","Sage","outdoor","",1);
            WardrobeRules.saveItem(d,"","Linen shorts","shorts","#CCBA97","Sand","home","",2);
            WardrobeRules.saveItem(d,"","Black jacket","jackets","#343C39","Charcoal","outdoor","",1);
        });
        context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue(device.wait(Until.hasObject(By.text("Today")),10000));shot(context,device,"01-today");
        device.findObject(By.text("More")).click();assertTrue(device.wait(Until.hasObject(By.text("Your space")),3000));
        assertNull(device.findObject(By.text("Recent activity")));assertNull(device.findObject(By.text("Restore")));shot(context,device,"02-more");
        device.findObject(By.text("Projects")).click();assertTrue(device.wait(Until.hasObject(By.text("A more thoughtful home")),3000));shot(context,device,"03-projects");
        device.findObject(By.text("Calendar")).click();assertTrue(device.wait(Until.hasObject(By.text("Back to today")),3000));shot(context,device,"04-calendar");
        device.findObject(By.text("Wardrobe")).click();assertTrue(device.wait(Until.hasObject(By.text("White T-shirt")),3000));shot(context,device,"05-wardrobe");
        device.findObject(By.text("White T-shirt")).click();assertTrue(device.wait(Until.hasObject(By.text("Wear / take out")),3000));
        device.findObject(By.text("Wear / take out")).click();device.wait(Until.hasObject(By.text("1 piece")),3000);device.findObject(By.text("1 piece")).click();
        device.waitForIdle();
        long deadline=android.os.SystemClock.uptimeMillis()+5000;
        while(WardrobeRules.total(repo.wardrobe(),"in_use")!=1 && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(50);
        assertEquals(1,WardrobeRules.total(repo.wardrobe(),"in_use"));
        UiObject2 inUse=device.wait(Until.findObject(By.textStartsWith("In Use ·")),3000);assertNotNull(inUse);inUse.click();shot(context,device,"06-in-use");
        device.findObject(By.textStartsWith("Outfits")).click();shot(context,device,"07-outfits");
        device.findObject(By.text("Browser")).click();assertTrue(device.wait(Until.hasObject(By.text("Website address")),5000));shot(context,device,"08-browser");
        device.pressBack();device.waitForIdle();
        // Completing a board task returns the exact load without any connection.
        store.changeWardrobe(space,account,repo.today(),d->{String item=Json.text(WardrobeRules.list(WardrobeRules.state(d),"items").get(0),"id");Json.put(WardrobeRules.state(d),"threshold",1);WardrobeRules.move(d,item,"in_use","laundry",1);});
        String task=Json.text(WardrobeRules.activeBatch(repo.wardrobe()),"task_id");assertNotNull(store.get("tasks",task));
        store.saveTaskAndWardrobe(Json.merge(store.get("tasks",task).body,Json.of("completed_at",Json.now())));
        assertEquals(0,WardrobeRules.total(repo.wardrobe(),"laundry"));assertEquals(3,WardrobeRules.count(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").get(0),"available"));
    }
    private void shot(Context c,UiDevice device,String name) throws Exception {
        device.waitForIdle();File dir=new File(c.getExternalFilesDir(null),"screenshots");assertTrue(dir.exists()||dir.mkdirs());File shot=new File(dir,name+".png");assertTrue(device.takeScreenshot(shot));
        device.executeShellCommand("mkdir -p /sdcard/Download/tbft-native-shots");
        device.executeShellCommand("cp "+shot.getAbsolutePath()+" /sdcard/Download/tbft-native-shots/"+name+".png");
    }
}
