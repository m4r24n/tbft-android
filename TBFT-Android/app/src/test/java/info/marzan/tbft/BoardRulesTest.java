package info.marzan.tbft;
import org.junit.Test;
import org.json.JSONObject;
import java.time.Instant;
import static org.junit.Assert.*;
public class BoardRulesTest {
    private final String zone = "Europe/Berlin";
    @Test public void rolloverUsesLocalClockAcrossSpringDst() {
        assertEquals("2026-03-28",BoardRules.boardDate(zone,6,Instant.parse("2026-03-29T03:59:00Z")));
        assertEquals("2026-03-29",BoardRules.boardDate(zone,6,Instant.parse("2026-03-29T04:00:00Z")));
    }
    @Test public void rolloverUsesLocalClockAcrossAutumnDst() {
        assertEquals("2026-10-24",BoardRules.boardDate(zone,6,Instant.parse("2026-10-25T04:59:00Z")));
        assertEquals("2026-10-25",BoardRules.boardDate(zone,6,Instant.parse("2026-10-25T05:00:00Z")));
    }
    @Test public void unfinishedTaskCarriesUntilTodayButNotIntoFuture() {
        JSONObject t = Json.of("original_date","2026-09-10");
        Instant now = Instant.parse("2026-09-12T12:00:00Z");
        assertTrue(BoardRules.appears(t,"2026-09-12",zone,6,now));
        assertFalse(BoardRules.appears(t,"2026-09-13",zone,6,now));
        assertEquals("Carried",BoardRules.state(t,"2026-09-12",zone,6,now));
    }
    @Test public void futureTaskAppearsOnlyOnScheduledDate() {
        JSONObject t = Json.of("original_date","2026-09-14");
        Instant now = Instant.parse("2026-09-12T12:00:00Z");
        assertTrue(BoardRules.appears(t,"2026-09-14",zone,6,now));
        assertFalse(BoardRules.appears(t,"2026-09-15",zone,6,now));
    }
    @Test public void completionBeforeRolloverBelongsToPreviousDay() {
        JSONObject t = Json.of("original_date","2026-09-10","completed_at","2026-09-12T01:00:00+00:00");
        Instant now = Instant.parse("2026-09-12T12:00:00Z");
        assertTrue(BoardRules.appears(t,"2026-09-11",zone,6,now));
        assertFalse(BoardRules.appears(t,"2026-09-12",zone,6,now));
    }
    @Test public void midnightGraceAndArchiveMatchBoardRules() {
        JSONObject t = Json.of("original_date","2026-09-11");
        Instant now = Instant.parse("2026-09-12T01:00:00Z");
        assertEquals("Overdue",BoardRules.state(t,"2026-09-11",zone,6,now));
        Json.put(t,"deleted_at",now.toString());
        assertFalse(BoardRules.appears(t,"2026-09-11",zone,6,now));
    }
}
