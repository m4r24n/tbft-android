package info.marzan.tbft;

import android.content.Context;
import android.graphics.Typeface;
import android.os.*;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Two-line, one-pass local mission briefing. Its controller survives ordinary screen rerenders. */
final class MissionTickerView extends LinearLayout {
    private final Handler handler=new Handler(Looper.getMainLooper());private final List<String> tasks;private final TerminalAudio audio;
    private final TextView line,title;private int item,characters;private boolean cursor=true,stopped,deleting;
    MissionTickerView(Context context,List<String> titles,TerminalAudio audio,Runnable openToday){super(context);this.tasks=new ArrayList<>(titles);this.audio=audio;
        setOrientation(VERTICAL);setPadding(Ui.dp(context,11),Ui.dp(context,6),Ui.dp(context,11),Ui.dp(context,6));setBackground(new TerminalPanelDrawable());setOnClickListener(v->openToday.run());setFocusable(true);
        line=new TextView(context);line.setTextColor(Ui.ACCENT);line.setTextSize(10);line.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);line.setSingleLine(true);addView(line,new LayoutParams(-1,-2));
        title=new TextView(context);title.setTextColor(Ui.INK);title.setTextSize(14);title.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);addView(title,new LayoutParams(-1,-2));
        line.setText("ONGOING MISSION BRIEF // "+tasks.size()+" OPERATION"+(tasks.size()==1?"":"S")+" REMAIN");setContentDescription(tasks.size()+" unfinished tasks. Tap to open Today.");
        handler.postDelayed(blink,500);handler.postDelayed(this::begin,300);
    }
    private void begin(){if(stopped)return;if(tasks.isEmpty()){display("MISSION BOARD CLEAR // STANDING BY");return;}item=0;characters=0;deleting=false;type();}
    private void type(){if(stopped)return;String task=tasks.get(item);if(characters<task.length()){characters++;display(task.substring(0,characters));audio.character(false);handler.postDelayed(this::type,45);}
        else{title.setContentDescription(task);audio.transition();handler.postDelayed(()->{deleting=true;erase();},5000);}}
    private void erase(){if(stopped)return;String task=tasks.get(item);if(characters>0){characters--;display(task.substring(0,characters));audio.character(true);handler.postDelayed(this::erase,18);return;}
        deleting=false;item++;if(item>=tasks.size()){display("END OF BRIEF // AWAITING INPUT");title.setContentDescription("End of mission brief");return;}audio.transition();handler.postDelayed(this::type,260);
    }
    private void display(String value){title.setText("> "+value+(cursor?"_":" "));}
    private final Runnable blink=new Runnable(){@Override public void run(){if(stopped)return;cursor=!cursor;String shown=title.getText().toString();if(!shown.isEmpty())title.setText(shown.substring(0,shown.length()-1)+(cursor?"_":" "));handler.postDelayed(this,500);}};
    void stop(){stopped=true;handler.removeCallbacksAndMessages(null);}
}
