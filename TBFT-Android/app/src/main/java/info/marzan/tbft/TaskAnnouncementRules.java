package info.marzan.tbft;

import java.time.*;
import java.util.*;

/** Pure scheduling and speech rules for offline task briefings. */
final class TaskAnnouncementRules {
    static final int MAX_TITLES=8;

    static List<String> normalise(Collection<String> values){
        TreeSet<String> sorted=new TreeSet<>();
        if(values!=null)for(String value:values)try{
            LocalTime time=LocalTime.parse(value);
            sorted.add(String.format(Locale.ROOT,"%02d:%02d",time.getHour(),time.getMinute()));
        }catch(Exception ignored){}
        return new ArrayList<>(sorted);
    }

    static Instant next(Instant now,String timezone,Collection<String> values){
        ZoneId zone=ZoneId.of(timezone);ZonedDateTime local=now.atZone(zone);
        List<String> times=normalise(values);
        if(times.isEmpty())throw new IllegalArgumentException("At least one announcement time is required");
        for(String value:times){
            LocalTime time=LocalTime.parse(value);
            ZonedDateTime candidate=local.toLocalDate().atTime(time).atZone(zone);
            if(candidate.isAfter(local))return candidate.toInstant();
        }
        return local.toLocalDate().plusDays(1).atTime(LocalTime.parse(times.get(0))).atZone(zone).toInstant();
    }

    static boolean allowed(Instant now,String timezone,Collection<String> values){
        LocalTime local=now.atZone(ZoneId.of(timezone)).toLocalTime().withSecond(0).withNano(0);
        String value=String.format(Locale.ROOT,"%02d:%02d",local.getHour(),local.getMinute());
        return normalise(values).contains(value);
    }

    static String speech(List<String> titles){
        if(titles.isEmpty())return "";
        StringBuilder out=new StringBuilder("Field unit, come in. This is TBFT base with your status report. Operational board shows ")
                .append(titles.size()).append(titles.size()==1?" task":" tasks").append(" remaining. ");
        int count=Math.min(titles.size(),MAX_TITLES);
        for(int i=0;i<count;i++)out.append(titles.get(i)).append(". ");
        if(titles.size()>MAX_TITLES)out.append("And ").append(titles.size()-MAX_TITLES).append(" more. ");
        out.append("That's your current brief. I'll be here when you need me.");
        return out.toString();
    }
}
