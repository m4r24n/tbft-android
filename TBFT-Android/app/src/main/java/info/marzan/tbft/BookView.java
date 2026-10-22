package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONObject;

/** A simple coloured book spine whose height and markings are stable for each title. */
final class BookView extends View {
    private final JSONObject book;
    BookView(Context context,JSONObject book){super(context);this.book=book;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        String title=Json.text(book,"title"),author=Json.text(book,"author");int color;
        try{color=Color.parseColor(Json.text(book,"color"));}catch(Exception e){color=0xff607d68;}
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float w=getWidth(),h=getHeight(),seed=Math.abs(title.hashCode()%23);float top=8+seed%15;
        p.setColor(0x19000000);canvas.drawRoundRect(4,h-7,w-2,h-2,3,3,p);
        p.setColor(color);canvas.drawRoundRect(3,top,w-3,h-7,3,3,p);
        p.setColor(blend(color,Color.WHITE,.18f));canvas.drawRect(6,top+3,9,h-10,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(blend(color,Color.BLACK,.42f));canvas.drawRoundRect(3,top,w-3,h-7,3,3,p);
        canvas.drawLine(5,top+14,w-5,top+14,p);canvas.drawLine(5,h-21,w-5,h-21,p);
        p.setStyle(Paint.Style.FILL);p.setColor(contrast(color));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(8,Math.min(12,w*.23f)));p.setTypeface(Typeface.create("serif",Typeface.BOLD));
        canvas.save();canvas.rotate(-90,w/2,(top+h-7)/2);String label=title.length()>18?title.substring(0,17)+"…":title;canvas.drawText(label,w/2,(top+h-7)/2+4,p);canvas.restore();
        if(!author.isEmpty()){p.setTextSize(7);p.setTypeface(Typeface.DEFAULT);canvas.save();canvas.rotate(-90,w/2,h-13);canvas.drawText(author.length()>12?author.substring(0,11)+"…":author,w/2,h-11,p);canvas.restore();}
    }
    private static int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),(int)(Color.green(a)*(1-t)+Color.green(b)*t),(int)(Color.blue(a)*(1-t)+Color.blue(b)*t));}
    private static int contrast(int color){double light=.299*Color.red(color)+.587*Color.green(color)+.114*Color.blue(color);return light>150?0xff332b25:Color.WHITE;}
}
