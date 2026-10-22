package info.marzan.tbft;
import android.content.Context;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class OfflineStoreTest {
    private Context context;
    private OfflineStore store;
    private String account;
    private final String id = "822610ba-d98c-49f7-81f7-e98396a51bd3";
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication(); account = UUID.randomUUID().toString(); store = new OfflineStore(context,account);
    }
    @After public void cleanup() { store.close(); context.deleteDatabase("tbft-offline-"+account+".db"); }
    private JSONObject task(String note) { return Json.of("id",id,"title","Task","completion_note",note,"deleted_at",null); }
    @Test public void saveAndQueueSurviveDatabaseCloseAndReopen() {
        store.ingest("tasks",Collections.singletonList(task("Server")));
        store.save("tasks",task("Offline"));
        store.close(); store = new OfflineStore(context,account);
        assertEquals("Offline",Json.text(store.get("tasks",id).body,"completion_note"));
        assertEquals("Server",Json.text(store.get("tasks",id).base,"completion_note"));
        assertEquals(1,store.pending().size());
    }
    @Test public void staleSnapshotCannotOverwriteOrRemoveDirtyRows() {
        store.ingest("tasks",Collections.singletonList(task("Server")));
        store.save("tasks",task("Offline"));
        store.ingest("tasks",Collections.singletonList(task("Different server")));
        store.ingest("tasks",Collections.emptyList());
        assertEquals("Offline",Json.text(store.get("tasks",id).body,"completion_note"));
        assertTrue(store.get("tasks",id).dirty);
    }
    @Test public void inFlightAcknowledgementPreservesNewerNotes() {
        store.ingest("tasks",Collections.singletonList(task("Server")));
        store.save("tasks",task("Sent"));
        OfflineStore.Record sent = store.get("tasks",id);
        store.save("tasks",task("New typing"));
        store.acknowledge(sent,Json.merge(task("Sent"),Json.of("title","Server title")));
        OfflineStore.Record current = store.get("tasks",id);
        assertTrue(current.dirty);
        assertEquals("New typing",Json.text(current.body,"completion_note"));
        assertEquals("Server title",Json.text(current.body,"title"));
        assertEquals("Sent",Json.text(current.base,"completion_note"));
    }
    @Test public void confirmedWriteLeavesNoPendingMutation() {
        store.save("tasks",task("New"));
        OfflineStore.Record sent = store.get("tasks",id);
        store.acknowledge(sent,task("New"));
        assertTrue(store.pending().isEmpty());
    }
    @Test public void reminderDeletionSurvivesRestartAndFailedSync() {
        store.ingest("reminders",Collections.singletonList(Json.of("id",id,"title","Reminder")));
        store.removeReminder(id); store.close(); store = new OfflineStore(context,account);
        assertTrue(store.records("reminders").isEmpty());
        OfflineStore.Record pending = store.pending().get(0);
        assertTrue(pending.removed);
        store.conflict(pending,"Service rejected"); store.ingest("reminders",Collections.emptyList());
        assertTrue(store.get("reminders",id).dirty);
    }
    @Test public void deletionResponseCannotEraseNewerLocalEdit() {
        store.ingest("reminders",Collections.singletonList(Json.of("id",id,"title","Before")));
        store.removeReminder(id); OfflineStore.Record sent = store.get("reminders",id);
        store.save("reminders",Json.of("id",id,"title","After"));
        store.acknowledge(sent,null);
        assertEquals("After",Json.text(store.get("reminders",id).body,"title"));
        assertFalse(store.get("reminders",id).error.isEmpty());
    }
    @Test public void reviewCannotDiscardEditsMadeAfterReviewOpened() {
        store.save("tasks",task("Before review")); OfflineStore.Record reviewed = store.get("tasks",id);
        store.save("tasks",task("New work"));
        try { store.resolve(reviewed,task("Remote"),false); fail("Expected stale review rejection"); } catch (IllegalStateException expected) {}
        assertEquals("New work",Json.text(store.get("tasks",id).body,"completion_note"));
    }
    @Test public void explicitLocalResolutionRebasesOnlyChangedFields() {
        store.ingest("tasks",Collections.singletonList(task("Before")));
        store.save("tasks",task("Local"));
        store.resolve(store.get("tasks",id),Json.merge(task("Remote"),Json.of("title","New server title")),true);
        OfflineStore.Record resolved = store.get("tasks",id);
        assertEquals("Local",Json.text(resolved.body,"completion_note"));
        assertEquals("New server title",Json.text(resolved.body,"title"));
        assertTrue(resolved.dirty);
    }
    @Test public void cleanMissingRowsAreRemovedButOtherAccountIsIsolated() {
        store.ingest("tasks",Collections.singletonList(task("Clean")));
        try (OfflineStore other = new OfflineStore(context,UUID.randomUUID().toString())) { assertTrue(other.records("tasks").isEmpty()); }
        store.ingest("tasks",Collections.emptyList()); assertNull(store.get("tasks",id));
    }
    @Test public void backupContainsPendingCopyAndBase() {
        store.ingest("tasks",Collections.singletonList(task("Remote")));
        store.save("tasks",task("Offline"));
        JSONObject row = store.export().optJSONArray("records").optJSONObject(0);
        assertTrue(row.optBoolean("pending"));
        assertEquals("Offline",Json.text(row.optJSONObject("body"),"completion_note"));
        assertEquals("Remote",Json.text(row.optJSONObject("serverBase"),"completion_note"));
    }
}
