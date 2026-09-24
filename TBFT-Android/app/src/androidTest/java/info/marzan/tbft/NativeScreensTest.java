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
    @org.junit.Rule public org.junit.rules.TestWatcher captureFailure=new org.junit.rules.TestWatcher(){
        @Override protected void failed(Throwable e,org.junit.runner.Description description){try{
            Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            shot(c,device,"99-failure");File hierarchy=new File(c.getExternalFilesDir(null),"failure-ui.xml");device.dumpWindowHierarchy(hierarchy);
            device.executeShellCommand("cp "+hierarchy.getAbsolutePath()+" /sdcard/Download/tbft-native-shots/failure-ui.xml");
        }catch(Exception ignored){}}
    };
    @Test public void offlineDailyFlowAndScreenshots() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");device.pressHome();device.waitForIdle();
        device.executeShellCommand("svc wifi disable");device.executeShellCommand("svc data disable");
        String account="cd820422-7a93-43f7-8c9e-7b67e620af66",partner="bad21cf5-83d4-4c6a-88c9-d3d098082757",space="053dbac3-7105-4423-a9a4-4f21d3a5e583",project=UUID.randomUUID().toString();
        context.getSharedPreferences("tbft_session_v1",Context.MODE_PRIVATE).edit().clear().putString("account",account).commit();
        context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putBoolean("wardrobe_open",false).commit();
        context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putBoolean("library_open",false).commit();
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
            WardrobeRules.saveItem(d,"","Long sleeve tee","tshirts","#809377","Sage","both","",1);
            WardrobeRules.saveItem(d,"","Hooded tee","tshirts","#AD785B","Terracotta","both","",1);
            java.util.List<org.json.JSONObject> items=WardrobeRules.list(WardrobeRules.state(d),"items");
            WardrobeRules.appearance(d,Json.text(items.get(5),"id"),"tshirt","long","no");WardrobeRules.appearance(d,Json.text(items.get(6),"id"),"tshirt","short","yes");
            WardrobeRules.saveItem(d,"","Cotton briefs","underwear","#384555","Navy","both","",3);
            WardrobeRules.saveItem(d,"","Everyday tank","tanks","#F5F1E5","Cream","home","",2);
            LibraryRules.saveBook(d,"","The Design of Everyday Things","Don Norman","learning","paper","reading",42,"#A38B57","Useful design principles.");
            LibraryRules.saveBook(d,"","The Little Prince","Antoine de Saint-Exupéry","fiction","paper","finished",100,"#47647A","");
            LibraryRules.saveBook(d,"","Supply Chain Management","Sunil Chopra","work","ebook","unread",0,"#607D68","");
        });
        InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();device.waitForIdle();
        assertTrue(device.wait(Until.hasObject(By.text("Today")),30000));shot(context,device,"01-today");
        device.findObject(By.text("More")).click();assertTrue(device.wait(Until.hasObject(By.text("Your space")),3000));
        assertNull(device.findObject(By.text("Recent activity")));assertNull(device.findObject(By.text("Restore")));shot(context,device,"02-more");
        device.findObject(By.text("Projects")).click();assertTrue(device.wait(Until.hasObject(By.text("A more thoughtful home")),3000));shot(context,device,"03-projects");
        device.findObject(By.text("Calendar")).click();assertTrue(device.wait(Until.hasObject(By.text("Back to today")),3000));shot(context,device,"04-calendar");
        device.findObject(By.text("Wardrobe")).click();assertTrue(device.wait(Until.hasObject(By.text("Open wardrobe")),3000));shot(context,device,"05-wardrobe-closed");
        // Regression: a brand-new garment has no colour yet; opening and typing must not crash.
        device.findObject(By.text("+ Add")).click();assertTrue(device.wait(Until.hasObject(By.text("Add clothes")),3000));
        device.findObject(By.desc("Name")).setText("Test cotton hoodie");
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().description("Garment shape"));
        device.findObject(By.desc("Garment shape")).click();device.wait(Until.findObject(By.text("Hoodie")),3000).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().description("Colour · hex"));
        device.findObject(By.desc("Colour · hex")).setText("");device.waitForIdle();
        device.findObject(By.desc("Colour · hex")).setText("#12");device.waitForIdle();
        device.findObject(By.desc("Colour · hex")).setText("#526D83");
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().text("Save clothes"));
        device.findObject(By.text("Save clothes")).click();device.waitForIdle();
        long addDeadline=android.os.SystemClock.uptimeMillis()+5000;
        while(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").size()!=10&&android.os.SystemClock.uptimeMillis()<addDeadline)android.os.SystemClock.sleep(50);
        assertEquals(10,WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").size());
        assertEquals("#526D83",Json.text(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").get(9),"color"));
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().text("Open wardrobe"));
        device.findObject(By.text("Open wardrobe")).click();assertTrue(device.wait(Until.hasObject(By.descStartsWith("Open T-shirts,")),3000));shot(context,device,"06-wardrobe-open");
        device.findObject(By.descStartsWith("Open T-shirts,")).click();assertTrue(device.wait(Until.hasObject(By.descStartsWith("White T-shirt,")),3000));shot(context,device,"07-clothes-rail");
        device.findObject(By.descStartsWith("White T-shirt,")).click();assertTrue(device.wait(Until.hasObject(By.text("Wear / take out")),3000));
        device.findObject(By.text("Wear / take out")).click();device.wait(Until.hasObject(By.text("1 piece")),3000);device.findObject(By.text("1 piece")).click();
        device.waitForIdle();
        long deadline=android.os.SystemClock.uptimeMillis()+5000;
        while(WardrobeRules.total(repo.wardrobe(),"in_use")!=1 && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(50);
        assertEquals(1,WardrobeRules.total(repo.wardrobe(),"in_use"));
        UiObject2 inUse=device.wait(Until.findObject(By.textStartsWith("In Use ·")),3000);assertNotNull(inUse);inUse.click();shot(context,device,"08-in-use");
        device.findObject(By.descStartsWith("White T-shirt,")).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().text("Edit details & quantity"));
        device.findObject(By.text("Edit details & quantity")).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().description("Sleeve length"));
        device.findObject(By.desc("Sleeve length")).click();device.wait(Until.findObject(By.text("Full sleeve")),3000).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().description("Hood"));
        device.findObject(By.desc("Hood")).click();device.wait(Until.findObject(By.text("With hood")),3000).click();
        shot(context,device,"08b-sleeve-and-hood");
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().text("Save clothes"));
        device.findObject(By.text("Save clothes")).click();device.waitForIdle();
        deadline=android.os.SystemClock.uptimeMillis()+5000;
        while(!"yes".equals(Json.text(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").get(0),"hood"))&&android.os.SystemClock.uptimeMillis()<deadline)android.os.SystemClock.sleep(50);
        assertEquals("long",Json.text(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").get(0),"sleeve"));
        assertEquals("yes",Json.text(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").get(0),"hood"));
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().textStartsWith("Outfits"));
        device.findObject(By.textStartsWith("Outfits")).click();shot(context,device,"09-outfits");
        device.findObject(By.text("Browser")).click();assertTrue(device.wait(Until.hasObject(By.text("Website address")),5000));shot(context,device,"10-browser");
        device.pressBack();device.waitForIdle();
        device.findObject(By.textStartsWith("Closet ·")).click();device.waitForIdle();
        UiScrollable wardrobePages=new UiScrollable(new UiSelector().descriptionStartsWith("Wardrobe compartments"));wardrobePages.setAsHorizontalList();wardrobePages.scrollForward();device.waitForIdle();
        assertNotNull(device.findObject(By.descStartsWith("Open Underwear,")));assertNotNull(device.findObject(By.descStartsWith("Open Tank tops,")));
        shot(context,device,"11-more-compartments");
        device.findObject(By.descStartsWith("Open Tank tops,")).click();assertTrue(device.wait(Until.hasObject(By.descStartsWith("Everyday tank,")),3000));shot(context,device,"12-tank-tops");
        device.findObject(By.descStartsWith("Everyday tank,")).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().text("Edit details & quantity"));
        device.findObject(By.text("Edit details & quantity")).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().description("Garment shape"));
        assertNotNull(device.findObject(By.desc("Garment shape")));shot(context,device,"13-edit-appearance");device.findObject(By.res("android:id/button2")).click();
        // Completing a board task returns the exact load without any connection.
        store.changeWardrobe(space,account,repo.today(),d->{String item=Json.text(WardrobeRules.list(WardrobeRules.state(d),"items").get(0),"id");Json.put(WardrobeRules.state(d),"threshold",1);WardrobeRules.move(d,item,"in_use","laundry",1);});
        repo.changed();device.waitForIdle();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().textStartsWith("Laundry ·"));
        device.findObject(By.textStartsWith("Laundry ·")).click();device.waitForIdle();shot(context,device,"14-laundry-basket");
        assertTrue(device.wait(Until.hasObject(By.text("Laundry finished · return clothes")),3000));
        String task=Json.text(WardrobeRules.activeBatch(repo.wardrobe()),"task_id");assertNotNull(store.get("tasks",task));
        store.saveTaskAndWardrobe(Json.merge(store.get("tasks",task).body,Json.of("completed_at",Json.now())));
        assertEquals(0,WardrobeRules.total(repo.wardrobe(),"laundry"));assertEquals(3,WardrobeRules.count(WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"items").get(0),"available"));
        device.wait(Until.findObject(By.text("Shelves")),5000).click();device.wait(Until.findObject(By.text("+ Add shelf")),3000).click();
        device.wait(Until.findObject(By.desc("Shelf name")),3000).setText("Travel clothes");device.findObject(By.text("Add shelf")).click();device.waitForIdle();
        addDeadline=android.os.SystemClock.uptimeMillis()+5000;
        while(!hasShelf(repo,"Travel clothes")&&android.os.SystemClock.uptimeMillis()<addDeadline)android.os.SystemClock.sleep(50);
        assertTrue(hasShelf(repo,"Travel clothes"));
        device.wait(Until.findObject(By.text("Shelves")),5000).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().textStartsWith("Travel clothes ·"));
        device.findObject(By.textStartsWith("Travel clothes ·")).click();device.wait(Until.findObject(By.text("Delete shelf")),3000).click();
        device.wait(Until.findObject(By.res("android:id/button1")),3000).click();device.waitForIdle();
        addDeadline=android.os.SystemClock.uptimeMillis()+5000;
        while(hasShelf(repo,"Travel clothes")&&android.os.SystemClock.uptimeMillis()<addDeadline)android.os.SystemClock.sleep(50);
        assertFalse(hasShelf(repo,"Travel clothes"));
        device.findObject(By.textStartsWith("Closet ·")).click();
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().text("Close wardrobe doors"));
        device.findObject(By.text("Close wardrobe doors")).click();device.waitForIdle();shot(context,device,"15-carved-doors");
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollToBeginning(20);device.waitForIdle();
        device.findObject(By.text("Library")).click();assertTrue(device.wait(Until.hasObject(By.text("Open library")),3000));shot(context,device,"16-library-closed");
        device.findObject(By.text("Open library")).click();assertTrue(device.wait(Until.hasObject(By.descStartsWith("Open Fiction,")),3000));shot(context,device,"17-library-open");
        device.findObject(By.descStartsWith("Open Learning,")).click();assertTrue(device.wait(Until.hasObject(By.descStartsWith("The Design of Everyday Things,")),3000));shot(context,device,"18-library-shelf");
        device.findObject(By.descStartsWith("The Design of Everyday Things,")).click();assertTrue(device.wait(Until.hasObject(By.text("Edit book")),3000));device.findObject(By.res("android:id/button2")).click();

    }
    private boolean hasShelf(TbftRepository repo,String name){return WardrobeRules.list(WardrobeRules.state(repo.wardrobe()),"categories").stream().anyMatch(c->name.equals(Json.text(c,"name")));}
    private void shot(Context c,UiDevice device,String name) throws Exception {
        device.waitForIdle();InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(300);
        File dir=new File(c.getExternalFilesDir(null),"screenshots");assertTrue(dir.exists()||dir.mkdirs());File shot=new File(dir,name+".png");assertTrue(device.takeScreenshot(shot));
        device.executeShellCommand("mkdir -p /sdcard/Download/tbft-native-shots");
        device.executeShellCommand("cp "+shot.getAbsolutePath()+" /sdcard/Download/tbft-native-shots/"+name+".png");
    }
}
