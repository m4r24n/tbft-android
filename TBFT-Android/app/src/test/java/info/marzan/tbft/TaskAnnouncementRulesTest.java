package info.marzan.tbft;

import org.junit.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;

public class TaskAnnouncementRulesTest {
    private static final String ZONE="Europe/Berlin";
    private static final List<String> TIMES=Arrays.asList("08:00","21:00");
    @Test public void nextMorningTimeIsChosen(){
        assertEquals(Instant.parse("2026-10-21T06:00:00Z"),TaskAnnouncementRules.next(Instant.parse("2026-10-21T05:20:00Z"),ZONE,TIMES));
    }
    @Test public void eveningTimeIsChosenAfterMorning(){
        assertEquals(Instant.parse("2026-10-21T19:00:00Z"),TaskAnnouncementRules.next(Instant.parse("2026-10-21T07:05:00Z"),ZONE,TIMES));
    }
    @Test public void afterEveningMovesToTomorrowMorning(){
        assertEquals(Instant.parse("2026-10-22T06:00:00Z"),TaskAnnouncementRules.next(Instant.parse("2026-10-21T20:05:00Z"),ZONE,TIMES));
    }
    @Test public void onlyConfiguredMinutesAreAllowed(){
        assertTrue(TaskAnnouncementRules.allowed(Instant.parse("2026-10-21T06:00:00Z"),ZONE,TIMES));
        assertTrue(TaskAnnouncementRules.allowed(Instant.parse("2026-10-21T19:00:00Z"),ZONE,TIMES));
        assertFalse(TaskAnnouncementRules.allowed(Instant.parse("2026-10-21T12:00:00Z"),ZONE,TIMES));
    }
    @Test public void timesAreValidatedSortedAndDeduplicated(){
        assertEquals(Arrays.asList("08:00","21:00"),TaskAnnouncementRules.normalise(Arrays.asList("21:00","bad","08:00","08:00")));
    }
    @Test public void emptyBoardStaysSilent(){assertEquals("",TaskAnnouncementRules.speech(Collections.emptyList()));}
    @Test public void longBoardIsSummarised(){
        List<String> tasks=Arrays.asList("One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Ten");
        String words=TaskAnnouncementRules.speech(tasks);
        assertTrue(words.startsWith("You have 10 tasks remaining."));assertTrue(words.endsWith("And 2 more."));assertFalse(words.contains("Nine"));
    }
}
