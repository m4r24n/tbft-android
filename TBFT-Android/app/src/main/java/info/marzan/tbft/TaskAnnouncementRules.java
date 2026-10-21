package info.marzan.tbft;

import java.time.*;
import java.util.List;

/** Pure scheduling and speech rules for the offline hourly briefing. */
final class TaskAnnouncementRules {
    static final int START_HOUR=8, END_HOUR=22, MAX_TITLES=8;

    static Instant next(Instant now,String timezone){
        ZoneId zone=ZoneId.of(timezone);ZonedDateTime local=now.atZone(zone);
        ZonedDateTime candidate=local.withMinute(0).withSecond(0).withNano(0);
        if(!candidate.isAfter(local))candidate=candidate.plusHours(1);
        if(candidate.getHour()<START_HOUR)candidate=candidate.withHour(START_HOUR);
        else if(candidate.getHour()>END_HOUR)candidate=candidate.plusDays(1).withHour(START_HOUR);
        return candidate.toInstant();
    }

    static boolean allowed(Instant now,String timezone){
        int hour=now.atZone(ZoneId.of(timezone)).getHour();
        return hour>=START_HOUR&&hour<=END_HOUR;
    }

    static String speech(List<String> titles){
        if(titles.isEmpty())return "";
        StringBuilder out=new StringBuilder("You have ").append(titles.size())
                .append(titles.size()==1?" task":" tasks").append(" remaining. ");
        int count=Math.min(titles.size(),MAX_TITLES);
        for(int i=0;i<count;i++)out.append(titles.get(i)).append(i==count-1?". ":". ");
        if(titles.size()>MAX_TITLES)out.append("And ").append(titles.size()-MAX_TITLES).append(" more.");
        return out.toString();
    }
}
