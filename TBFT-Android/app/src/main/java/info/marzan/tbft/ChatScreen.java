package info.marzan.tbft;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** Offline-first, participant-only field communicator. */
final class ChatScreen {
    private final Activity activity;private final TbftRepository repo;private final Runnable refresh;private String partner="";
    ChatScreen(Activity a,TbftRepository r,Runnable refresh){activity=a;repo=r;this.refresh=refresh;}
    void render(LinearLayout body){
        label(body,"03 / PARTNER COMMS",Ui.ACCENT);title(body,"Rustline communicator",25);label(body,"Encrypted by workspace access · transmissions queue on this device",Ui.MUTED);
        List<OfflineStore.Record> profiles=repo.rows("profiles");String self=repo.vault.account();
        LinearLayout partners=Ui.chips(body);for(OfflineStore.Record p:profiles)if(!p.id.equals(self)){if(partner.isEmpty())partner=p.id;Button b=Ui.button(partners,repo.name(p.id),()->{partner=p.id;refresh.run();});Ui.selected(b,p.id.equals(partner));}
        if(partner.isEmpty()){label(body,"No connected partner is available in this workspace.",Ui.MUTED);return;}
        LinearLayout receiver=Ui.card(body);receiver.setBackground(new RuggedDrawable(0xff171a17));
        label(receiver,"RX/TX  41.7  //  LINK: "+repo.name(partner).toUpperCase(Locale.ROOT),Ui.BRASS);
        List<OfflineStore.Record> messages=new ArrayList<>();for(OfflineStore.Record r:repo.rows("chat_messages")){String s=Json.text(r.body,"sender_id"),to=Json.text(r.body,"recipient_id");if((s.equals(self)&&to.equals(partner))||(s.equals(partner)&&to.equals(self)))messages.add(r);}
        messages.sort(Comparator.comparing(r->Json.text(r.body,"created_at")));
        if(messages.isEmpty())label(receiver,"— channel quiet —",Ui.MUTED);
        for(OfflineStore.Record r:messages){boolean mine=self.equals(Json.text(r.body,"sender_id"));LinearLayout packet=Ui.card(receiver);packet.setBackground(Ui.shape(activity,mine?Ui.OXIDE:0xff202923,5));
            label(packet,(mine?"OUTGOING":"INCOMING")+(r.dirty?"  //  QUEUED LOCAL":""),mine?Ui.BRASS:Ui.ACCENT);TextView msg=label(packet,Json.text(r.body,"body"),Ui.INK);msg.setTextSize(16);
            String time=Json.text(r.body,"created_at");label(packet,time.length()>16?time.substring(0,16).replace('T',' ')+" UTC":time,Ui.MUTED);
        }
        EditText input=new EditText(activity);input.setHint("Type field transmission…");input.setTextColor(Ui.INK);input.setHintTextColor(Ui.MUTED);input.setMinLines(2);input.setGravity(Gravity.TOP);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setBackground(Ui.shape(activity,Ui.DARK,4));input.setPadding(Ui.dp(activity,12),Ui.dp(activity,10),Ui.dp(activity,12),Ui.dp(activity,10));body.addView(input,new LinearLayout.LayoutParams(-1,-2));
        Ui.button(body,"TRANSMIT",()->{String words=input.getText().toString().trim();if(words.isEmpty()){Toast.makeText(activity,"Enter a transmission first.",Toast.LENGTH_SHORT).show();return;}if(words.length()>2000){Toast.makeText(activity,"Transmission must be under 2000 characters.",Toast.LENGTH_SHORT).show();return;}
            JSONObject row=Json.of("id",UUID.randomUUID().toString(),"workspace_id",repo.workspaceId(),"sender_id",self,"recipient_id",partner,"body",words,"created_at",Json.now());
            repo.edit("chat_messages",null,row,error->activity.runOnUiThread(()->{if(!error.isEmpty())Toast.makeText(activity,error,Toast.LENGTH_LONG).show();refresh.run();}));
        });
    }
    private TextView label(LinearLayout p,String s,int color){TextView t=Ui.text(p,s,12,color);t.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);return t;}
    private void title(LinearLayout p,String s,int size){TextView t=Ui.text(p,s,size,Ui.INK);t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);}
}
