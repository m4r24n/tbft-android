package info.marzan.tbft;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.speech.tts.*;
import java.time.Instant;
import java.util.*;

/** Device-only scheduled task announcements. No network or cloud job is involved. */
final class TaskAnnouncements {
    static final String ACTION="info.marzan.tbft.SCHEDULED_TASK_ANNOUNCEMENT";
    private static final String PREFS="tbft_task_announcements_v1",ENABLED="enabled",TIMES="times_v2";
    private static final Set<String> DEFAULT_TIMES=new LinkedHashSet<>(Arrays.asList("08:00","21:00"));
    private static volatile TextToSpeech active;

    static boolean enabled(Context context){return context.getSharedPreferences(PREFS,0).getBoolean(ENABLED,true);}
    static List<String> times(Context context){
        Set<String> saved=context.getSharedPreferences(PREFS,0).getStringSet(TIMES,null);
        return TaskAnnouncementRules.normalise(saved==null?DEFAULT_TIMES:saved);
    }
    static void addTime(Context context,int hour,int minute){
        Set<String> changed=new LinkedHashSet<>(times(context));
        changed.add(String.format(Locale.ROOT,"%02d:%02d",hour,minute));saveTimes(context,changed);
    }
    static boolean removeTime(Context context,String time){
        Set<String> changed=new LinkedHashSet<>(times(context));
        if(changed.size()<=1)return false;
        changed.remove(time);saveTimes(context,changed);return true;
    }
    private static void saveTimes(Context context,Set<String> values){
        context.getSharedPreferences(PREFS,0).edit().putStringSet(TIMES,new LinkedHashSet<>(values)).apply();schedule(context);
    }
    static void setEnabled(Context context,boolean value){
        context.getSharedPreferences(PREFS,0).edit().putBoolean(ENABLED,value).apply();
        if(value)schedule(context);else cancel(context);
    }
    static void schedule(Context context){
        if(!enabled(context)){cancel(context);return;}
        TbftRepository repo=TbftRepository.get(context);
        long at=TaskAnnouncementRules.next(Instant.now(),repo.timezone(),times(context)).toEpochMilli();
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(alarms==null)return;
        PendingIntent pending=pending(context);
        if(Build.VERSION.SDK_INT>=23)alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);else alarms.set(AlarmManager.RTC_WAKEUP,at,pending);
    }
    static void cancel(Context context){AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(alarms!=null)alarms.cancel(pending(context));}
    private static PendingIntent pending(Context context){
        Intent intent=new Intent(context,TaskAnnouncementReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(context,74,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void speakNow(Context context){speak(context,TbftRepository.get(context).remainingTaskTitles());}
    static void speak(Context context,List<String> titles){
        String words=TaskAnnouncementRules.speech(titles);if(words.isEmpty())return;
        if(active!=null){active.stop();active.shutdown();active=null;}
        final TextToSpeech[] holder=new TextToSpeech[1];
        holder[0]=new TextToSpeech(context.getApplicationContext(),status->{
            TextToSpeech voice=holder[0];
            if(status!=TextToSpeech.SUCCESS||voice==null){if(voice!=null)voice.shutdown();active=null;return;}
            active=voice;voice.setLanguage(Locale.getDefault());voice.setSpeechRate(1.02f);voice.setPitch(.88f);
            voice.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            voice.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                private void done(){signal(ToneGenerator.TONE_PROP_BEEP2,120);TextToSpeech current=active;if(current==voice){active=null;current.shutdown();}}
                @Override public void onStart(String id){} @Override public void onDone(String id){done();} @Override public void onError(String id){done();}
            });
            signal(ToneGenerator.TONE_PROP_BEEP,140);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->voice.speak(words,TextToSpeech.QUEUE_FLUSH,null,"tbft-radio-briefing"),260);
        });
    }
    private static void signal(int tone,int duration){
        ToneGenerator generator=new ToneGenerator(AudioManager.STREAM_MUSIC,55);generator.startTone(tone,duration);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(generator::release,duration+80L);
    }
}
