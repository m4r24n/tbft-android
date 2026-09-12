package info.marzan.tbft;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class SyncRulesTest {
    @Test public void postgresNumericFormattingIsEquivalent() {
        assertTrue(SyncRules.equal(1,1.0));
        assertFalse(SyncRules.equal(1,1.1));
    }
    @Test public void unrelatedEditsMergeWithoutConflict() {
        JSONObject base = Json.of("title","Original","completion_note","Before");
        JSONObject local = Json.merge(base,Json.of("completion_note","Offline work"));
        JSONObject remote = Json.merge(base,Json.of("title","Partner's title"));
        assertEquals("",SyncRules.conflict(base,local,remote));
        JSONObject merged = Json.merge(remote,SyncRules.delta(base,local));
        assertEquals("Partner's title",Json.text(merged,"title"));
        assertEquals("Offline work",Json.text(merged,"completion_note"));
    }
    @Test public void overlappingNotesRequireReview() {
        JSONObject base = Json.of("completion_note","Before");
        assertFalse(SyncRules.conflict(base,Json.of("completion_note","Phone"),Json.of("completion_note","Web")).isEmpty());
    }
    @Test public void lostResponseCanBeAcknowledgedWithoutAnotherMutation() {
        JSONObject desired = Json.of("title","Task","completed_at","2026-09-12T12:30:00Z","deadline","12:30");
        JSONObject server = Json.of("title","Task","completed_at","2026-09-12T12:30:00+00:00","deadline","12:30:00","updated_at","2026-09-12T12:31:00Z");
        assertTrue(SyncRules.matches(server,SyncRules.delta(new JSONObject(),desired)));
    }
    @Test public void timestampLookingNoteIsStillLiteralText() {
        assertFalse(SyncRules.equal("completion_note","2026-09-12T12:30:00Z","2026-09-12T12:30:00+00:00"));
    }
    @Test public void remoteArchiveRequiresReviewEvenForUnrelatedEdit() {
        JSONObject base = Json.of("title","Task","deleted_at",null);
        assertFalse(SyncRules.conflict(base,Json.merge(base,Json.of("title","New")),Json.merge(base,Json.of("deleted_at","2026-09-12T12:00:00Z"))).isEmpty());
    }
    @Test public void editsDuringUploadRemainPendingOnNewServerBase() {
        JSONObject sent = Json.of("title","Original","completion_note","Sent");
        JSONObject current = Json.merge(sent,Json.of("completion_note","Typed while syncing"));
        JSONObject ack = Json.merge(sent,Json.of("title","Remote title","updated_at","now"));
        JSONObject result = SyncRules.rebase(sent,current,ack);
        assertEquals("Remote title",Json.text(result,"title"));
        assertEquals("Typed while syncing",Json.text(result,"completion_note"));
        assertEquals(1,SyncRules.delta(ack,result).length());
    }
    @Test public void clearingNoteIsAnExplicitChange() {
        JSONObject base = Json.of("completion_note","Before");
        assertEquals("",Json.text(SyncRules.delta(base,Json.of("completion_note","")),"completion_note"));
        assertEquals(1,SyncRules.delta(base,Json.of("completion_note","")).length());
    }
    @Test public void deletedRemoteIsNeverAutomaticallyRecreated() {
        assertFalse(SyncRules.conflict(Json.of("title","A"),Json.of("title","B"),null).isEmpty());
    }
}
