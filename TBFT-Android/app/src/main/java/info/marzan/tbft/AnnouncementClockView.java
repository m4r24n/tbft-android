package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.List;

/** A compact field receiver: steel case, speaker grille, tuning scale and amber presets. */
final class AnnouncementClockView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final List<String> times;
    AnnouncementClockView(Context c,List<String> times){super(c);this.times=times;setContentDescription("Field broadcast unit, presets "+String.join(" and ",times));}
    private void fill(Canvas c,int color,float l,float t,float r,float b,float radius){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();fill(c,0xff0b0e0c,4,5,w-4,h-5,12);fill(c,0xff272b27,10,11,w-10,h-11,8);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff596057);c.drawRoundRect(11,12,w-11,h-12,8,8,p);
        // carry handle
        p.setStrokeWidth(8);p.setColor(0xff171a17);c.drawLine(w*.31f,12,w*.69f,12,p);p.setStrokeWidth(3);p.setColor(0xff6b7068);c.drawLine(w*.33f,10,w*.67f,10,p);
        // speaker grille
        fill(c,0xff151915,w*.06f,h*.25f,w*.43f,h*.78f,7);p.setColor(0xff495048);p.setStrokeWidth(2);
        for(int row=0;row<6;row++)for(int col=0;col<7;col++)c.drawCircle(w*(.095f+col*.047f),h*(.31f+row*.075f),2.2f,p);
        // instrument display
        fill(c,0xff080a08,w*.48f,h*.25f,w*.94f,h*.61f,5);p.setColor(0xffc59446);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);c.drawRoundRect(w*.49f,h*.26f,w*.93f,h*.60f,4,4,p);
        p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.min(w*.075f,h*.18f));
        String display=times.size()==1?times.get(0):times.get(0)+"  "+times.get(times.size()-1);c.drawText(display,w*.705f,h*.48f,p);
        p.setTextSize(Math.min(w*.027f,h*.068f));p.setColor(0xff9fbd72);c.drawText("AUTO BRIEFING / LOCAL",w*.705f,h*.56f,p);
        // tuning scale and two knobs
        p.setColor(0xffa6a998);p.setTextSize(Math.min(w*.023f,h*.055f));c.drawText("FIELD BROADCAST UNIT",w*.705f,h*.20f,p);
        for(int i=0;i<2;i++){float x=w*(.60f+i*.22f),y=h*.76f;fill(c,0xff0e110f,x-18,y-18,x+18,y+18,18);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(0xff666b63);c.drawCircle(x,y,15,p);c.drawLine(x,y-13,x,y-5,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(0xffc59446);c.drawCircle(w*.91f,h*.16f,4,p);
        // case screws
        p.setColor(0xff777c73);for(float x:new float[]{20,w-20})for(float y:new float[]{24,h-24})c.drawCircle(x,y,3,p);
    }
}
