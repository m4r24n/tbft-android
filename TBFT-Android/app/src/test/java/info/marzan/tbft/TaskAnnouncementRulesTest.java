package info.marzan.tbft;

import org.junit.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;

public class TaskAnnouncementRulesTest {
    private static final String ZONE="Europe/Berlin";
    @Test public void nextHourStaysInsideDailyWindow(){
        assertEquals(Instant.parse("2026-10-21T12:00:00Z"),TaskAnnouncementRules.next(Instant.parse("2026-10-21T11:20:00Z"),ZONE));
    }
    @Test public void afterFinalAnnouncementMovesToEightTomorrow(){
        assertEquals(Instant.parse("2026-10-22T06:00:00Z"),TaskAnnouncementRules.next(Instant.parse("2026-10-21T20:05:00Z"),ZONE));
    }
    @Test public void windowIncludesEightAndTwentyTwo(){
        assertTrue(TaskAnnouncementRules.allowed(Instant.parse("2026-10-21T06:00:00Z"),ZONE));
        assertTrue(TaskAnnouncementRules.allowed(Instant.parse("2026-10-21T20:00:00Z"),ZONE));
        assertFalse(TaskAnnouncementRules.allowed(Instant.parse("2026-10-21T21:00:00Z"),ZONE));
    }
    @Test public void emptyBoardStaysSilent(){assertEquals("",TaskAnnouncementRules.speech(Collections.emptyList()));}
    @Test public void longBoardIsSummarised(){
        List<String> tasks=Arrays.asList("One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Ten");
        String words=TaskAnnouncementRules.speech(tasks);
        assertTrue(words.startsWith("You have 10 tasks remaining."));
        assertTrue(words.endsWith("And 2 more."));assertFalse(words.contains("Nine"));
    }
}
