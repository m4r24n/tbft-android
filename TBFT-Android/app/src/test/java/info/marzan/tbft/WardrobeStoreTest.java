package info.marzan.tbft;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WardrobeStoreTest {
    private OfflineStore store;
    private final String account=UUID.randomUUID().toString(),space=UUID.randomUUID().toString(),date="2026-09-21";
    @Before public void setup(){store=new OfflineStore(RuntimeEnvironment.getApplication(),account);}
    @After public void close(){store.close();}
    private String laundry(){store.changeWardrobe(space,account,date,d->{WardrobeRules.saveItem(d,"","White T-shirt","tshirts","#FFFFFF","White","home","",2);String item=Json.text(WardrobeRules.list(WardrobeRules.state(d),"items").get(0),"id");Json.put(WardrobeRules.state(d),"threshold",2);WardrobeRules.move(d,item,"available","laundry",2);});return Json.text(WardrobeRules.activeBatch(store.wardrobe(space,account)),"task_id");}
    @Test public void appearancePersistsThroughLaundryAndDatabaseReopen(){
        String task=laundry();store.changeWardrobe(space,account,date,d->{String id=Json.text(WardrobeRules.list(WardrobeRules.state(d),"items").get(0),"id");WardrobeRules.appearance(d,id,"jacket","short","yes");});
        store.saveTaskAndWardrobe(Json.merge(store.get("tasks",task).body,Json.of("completed_at",Json.now())));
        store.close();store=new OfflineStore(RuntimeEnvironment.getApplication(),account);JSONObject doc=store.wardrobe(space,account),g=WardrobeRules.list(WardrobeRules.state(doc),"items").get(0);
        assertEquals("short",WardrobeRules.sleeve(doc,g));assertTrue(WardrobeRules.hood(doc,g));assertEquals(2,WardrobeRules.count(g,"available"));
    }
    @Test public void clothingAndBoardTaskSurviveDatabaseReopen(){String task=laundry();store.close();store=new OfflineStore(RuntimeEnvironment.getApplication(),account);assertEquals(2,WardrobeRules.total(store.wardrobe(space,account),"laundry"));assertTrue(store.get("tasks",task).dirty);assertEquals("wardrobes",store.pending().get(0).table);}
    @Test public void completingBoardTaskReturnsItsClothesAtomically(){String id=laundry();store.saveTaskAndWardrobe(Json.merge(store.get("tasks",id).body,Json.of("completed_at",Json.now())));assertEquals(2,WardrobeRules.total(store.wardrobe(space,account),"available"));assertNotEquals("",Json.text(store.get("tasks",id).body,"completed_at"));}
    @Test public void wardrobeFinishAlsoCompletesExistingTask(){String id=laundry();store.changeWardrobe(space,account,date,d->WardrobeRules.finishBasket(d,date));assertFalse(Json.text(store.get("tasks",id).body,"completed_at").isEmpty());}
    @Test public void failedActionLeavesNoPartialClothesOrTask(){assertThrows(IllegalArgumentException.class,()->store.changeWardrobe(space,account,date,d->{WardrobeRules.saveItem(d,"","Tee","tshirts","#FFFFFF","White","home","",1);throw new IllegalArgumentException("stop");}));assertTrue(store.pending().isEmpty());}
    @Test public void archiveAfterCompletionDoesNotChangeFinishedBatch(){String id=laundry();store.saveTaskAndWardrobe(Json.merge(store.get("tasks",id).body,Json.of("completed_at",Json.now())));JSONObject before=store.wardrobe(space,account);store.saveTaskAndWardrobe(Json.merge(store.get("tasks",id).body,Json.of("deleted_at",Json.now())));assertTrue(SyncRules.equal(before,store.wardrobe(space,account)));}
    @Test public void managedAcknowledgementPreservesNotebookEditsAndNewerReopen(){String id=laundry();JSONObject generated=Json.copy(store.get("tasks",id).body);store.save("tasks",Json.merge(generated,Json.of("completion_note","Keep this note")));OfflineStore.Record sent=store.get("tasks",id);store.acknowledgeManagedTask(sent,generated,Json.merge(generated,Json.of("updated_at",Json.now())));assertTrue(store.get("tasks",id).dirty);assertEquals("Keep this note",Json.text(store.get("tasks",id).body,"completion_note"));}
    @Test public void firstSyncDoesNotDiscardAnOfflineReopen(){String id=laundry();store.saveTaskAndWardrobe(Json.merge(store.get("tasks",id).body,Json.of("completed_at",Json.now())));store.saveTaskAndWardrobe(Json.merge(store.get("tasks",id).body,Json.of("completed_at",null)));JSONObject doc=store.wardrobe(space,account),generated=WardrobeRules.task(doc,WardrobeRules.batch(doc,id));store.acknowledgeManagedTask(store.get("tasks",id),generated,generated);assertTrue(store.get("tasks",id).dirty);assertEquals("",Json.text(store.get("tasks",id).body,"completed_at"));assertEquals(2,WardrobeRules.total(doc,"available"));}
}
