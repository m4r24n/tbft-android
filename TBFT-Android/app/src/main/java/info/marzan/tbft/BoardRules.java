package info.marzan.tbft;

import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class BoardRules {
    static String boardDate(String timezone, int rollover, Instant now) {
        ZonedDateTime local = now.atZone(ZoneId.of(timezone));
        return (local.getHour() < rollover ? local.toLocalDate().minusDays(1) : local.toLocalDate()).toString();
    }
    static boolean appears(JSONObject task, String date, String timezone, int rollover, Instant now) {
        String original = Json.text(task, "original_date");
        if (!Json.text(task, "deleted_at").isEmpty() || original.isEmpty() || date.compareTo(original) < 0) return false;
        String today = boardDate(timezone, rollover, now);
        if (date.compareTo(today) > 0) return date.equals(original);
        String completed = Json.text(task, "completed_at");
        String last = completed.isEmpty() ? today : boardDate(timezone, rollover, Instant.parse(completed));
        return date.compareTo(last) <= 0;
    }
    static String state(JSONObject task, String date, String timezone, int rollover, Instant now) {
        if (!Json.text(task, "completed_at").isEmpty()) return "Completed";
        String today = boardDate(timezone, rollover, now);
        if (date.compareTo(today) > 0) return "Scheduled";
        String calendar = now.atZone(ZoneId.of(timezone)).toLocalDate().toString();
        if (date.equals(today) && !calendar.equals(today)) return "Overdue";
        if (date.compareTo(Json.text(task, "original_date")) > 0) return "Carried";
        return date.compareTo(today) < 0 ? "Overdue" : "Today";
    }
    static String validDate(String value) { return LocalDate.parse(value).toString(); }
}
