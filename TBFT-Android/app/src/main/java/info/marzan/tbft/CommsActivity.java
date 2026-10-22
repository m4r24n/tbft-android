package info.marzan.tbft;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Long-running English voice session. The replaceable local-model layer comes next. */
public final class CommsActivity extends Activity implements RecognitionListener,TextToSpeech.OnInitListener {
    private static final int MIC=51; private SpeechRecognizer recognizer; private TextToSpeech voice;
    private LinearLayout transcript; private ScrollView scroll; private TextView state; private Button session,transmit; private boolean active,listening,voiceReady;
    private TbftRepository repo;
    @Override public void onCreate(Bundle b){super.onCreate(b);repo=TbftRepository.get(this);build();voice=new TextToSpeech(this,this);}
    private int dp(int n){return Ui.dp(this,n);} private LinearLayout col(){return Ui.column(this);} 
    private void build(){LinearLayout root=col();root.setPadding(dp(12),dp(12),dp(12),dp(12));root.setBackground(new RuggedDrawable(Ui.BG,true));
        TextView plate=Ui.text(root,"COMMS // PERSONAL OPERATIONS CHANNEL",15,Ui.INK);plate.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        state=Ui.text(root,"CHANNEL CLOSED  //  ENGLISH  //  LOCAL COMMAND CORE",10,Ui.ACCENT);state.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        scroll=new ScrollView(this);scroll.setFillViewport(true);transcript=col();transcript.setPadding(dp(8),dp(8),dp(8),dp(16));transcript.setBackground(new CrtScreenDrawable());scroll.addView(transcript);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        line("SYSTEM","Start a session and speak naturally. This foundation handles local TBFT briefings and navigation commands. The private conversational model will connect to this channel next.",Ui.MUTED);
        LinearLayout row=new LinearLayout(this);session=Ui.button(row,"START SESSION",this::toggleSession);transmit=Ui.button(row,"TRANSMIT",this::listen);Ui.button(row,"BACK",this::finish);for(int i=0;i<row.getChildCount();i++)row.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));root.addView(row);setContentView(root);}
    private void toggleSession(){if(active){active=false;stopListening();session.setText("START SESSION");state.setText("CHANNEL CLOSED  //  SESSION RETAINED ON DEVICE");say("Channel closed.");}else{active=true;session.setText("END SESSION");state.setText("CHANNEL OPEN  //  WAITING FOR TRANSMISSION");line("SYSTEM","Channel open. No fixed session duration.",Ui.ACCENT);listen();}}
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}if(!SpeechRecognizer.isRecognitionAvailable(this)){line("SYSTEM","No speech-recognition service is installed. Text channel will be added with the local model.",Ui.RUST);return;}if(recognizer==null){recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(this);}if(listening)return;
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);recognizer.startListening(i);listening=true;transmit.setText("LISTENING…");state.setText("RECEIVING VOICE  //  AUDIO NOT RETAINED");}
    private void stopListening(){if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}listening=false;transmit.setText("TRANSMIT");}
    private void handle(String words){line("YOU",words,Ui.INK);String q=words.toLowerCase(Locale.ROOT),reply;
        if(q.contains("open")&&q.contains("map")||q.contains("navigation")){reply="Opening the field chart.";say(reply);startActivity(new Intent(this,FieldMapActivity.class));return;}
        if(q.contains("remaining")||q.contains("unfinished")||q.contains("mission brief")||q.contains("tasks left")){List<String> tasks=repo.remainingTaskTitles();if(tasks.isEmpty())reply="Mission board clear. You have no unfinished tasks.";else reply="You have "+tasks.size()+" unfinished operations. "+String.join(". ",tasks)+".";}
        else if(q.contains("where am i")||q.contains("compass")){reply="Opening navigation. Your exact location stays on this device.";say(reply);startActivity(new Intent(this,FieldMapActivity.class));return;}
        else if(q.contains("end session")||q.contains("close channel")){reply="Channel closed.";active=false;session.setText("START SESSION");}
        else reply="Transmission received. The local conversational model is not connected yet. I can brief remaining tasks, open navigation, or close this channel.";
        say(reply);
    }
    private void say(String words){line("TBFT",words,Ui.ACCENT);if(!voiceReady){if(active)new Handler(Looper.getMainLooper()).postDelayed(this::listen,600);return;}Bundle b=new Bundle();voice.speak(words,TextToSpeech.QUEUE_FLUSH,b,"tbft-reply");}
    private void line(String who,String words,int color){TextView v=Ui.text(transcript,who+" // "+words,14,color);v.setTypeface(Typeface.MONOSPACE,who.equals("YOU")?Typeface.NORMAL:Typeface.BOLD);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    @Override public void onInit(int s){voiceReady=s==TextToSpeech.SUCCESS;if(voiceReady){voice.setLanguage(Locale.US);voice.setPitch(.86f);voice.setSpeechRate(.92f);voice.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){public void onStart(String id){}public void onError(String id){}public void onDone(String id){if(active)runOnUiThread(()->new Handler(Looper.getMainLooper()).postDelayed(CommsActivity.this::listen,350));}});}}
    @Override public void onReadyForSpeech(Bundle b){} @Override public void onBeginningOfSpeech(){} @Override public void onRmsChanged(float r){} @Override public void onBufferReceived(byte[] b){} @Override public void onEndOfSpeech(){state.setText("DECODING TRANSMISSION…");}
    @Override public void onError(int error){listening=false;transmit.setText("TRANSMIT");state.setText("CHANNEL OPEN  //  TAP TRANSMIT");if(active&&error!=SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)new Handler(Looper.getMainLooper()).postDelayed(this::listen,900);}
    @Override public void onResults(Bundle b){listening=false;transmit.setText("TRANSMIT");ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(r==null||r.isEmpty()){if(active)listen();return;}handle(r.get(0));}
    @Override public void onPartialResults(Bundle b){} @Override public void onEvent(int t,Bundle b){}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)listen();else state.setText("MICROPHONE DENIED  //  CHANNEL UNAVAILABLE");}
    @Override protected void onDestroy(){active=false;stopListening();if(recognizer!=null)recognizer.destroy();if(voice!=null){voice.stop();voice.shutdown();}super.onDestroy();}
}
