package info.marzan.tbft;

import android.content.Context;
import android.media.*;
import java.util.Random;

/** Quiet, device-local interface sounds. Never bypasses silent mode or media volume. */
final class TerminalAudio {
    private static final String PREFS="tbft_terminal_audio",TICKER="mission_ticker_sound";
    private final Context context;private final AudioManager manager;private final AudioTrack[] ticks=new AudioTrack[3];private final AudioTrack erase,chirp;private final Random random=new Random();
    TerminalAudio(Context context){this.context=context.getApplicationContext();manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        ticks[0]=track(tone(940,14,850));ticks[1]=track(tone(1020,13,760));ticks[2]=track(tone(880,15,820));erase=track(tone(470,10,520));chirp=track(sweep(560,1040,75,950));}
    static boolean tickerEnabled(Context c){return c.getSharedPreferences(PREFS,0).getBoolean(TICKER,true);}
    static void setTickerEnabled(Context c,boolean enabled){c.getSharedPreferences(PREFS,0).edit().putBoolean(TICKER,enabled).apply();}
    void character(boolean deleting){if(!allowed())return;play(deleting?erase:ticks[random.nextInt(ticks.length)]);}
    void transition(){if(allowed())play(chirp);}
    private boolean allowed(){return tickerEnabled(context)&&manager!=null&&manager.getRingerMode()==AudioManager.RINGER_MODE_NORMAL&&manager.getStreamVolume(AudioManager.STREAM_MUSIC)>0;}
    private synchronized void play(AudioTrack t){try{if(t.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)t.stop();t.setPlaybackHeadPosition(0);t.play();}catch(Exception ignored){}}
    void release(){for(AudioTrack t:ticks)try{t.release();}catch(Exception ignored){}try{erase.release();}catch(Exception ignored){}try{chirp.release();}catch(Exception ignored){}}
    private static AudioTrack track(short[] pcm){AudioTrack t=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(12000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length*2).build();t.write(pcm,0,pcm.length);t.setVolume(.42f);return t;}
    private static short[] tone(double hz,int ms,double amplitude){int n=12000*ms/1000;short[] out=new short[n];for(int i=0;i<n;i++){double decay=1d-(double)i/n;out[i]=(short)(Math.sin(2*Math.PI*hz*i/12000d)*amplitude*decay);}return out;}
    private static short[] sweep(double from,double to,int ms,double amplitude){int n=12000*ms/1000;short[] out=new short[n];double phase=0;for(int i=0;i<n;i++){double x=(double)i/n;phase+=2*Math.PI*(from+(to-from)*x)/12000d;out[i]=(short)(Math.sin(phase)*amplitude*Math.sin(Math.PI*x));}return out;}
}
