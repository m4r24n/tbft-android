package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.util.List;

/** Small code-drawn bedside alarm clock; no decorative image download is required. */
final class AnnouncementClockView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final List<String> times;
    AnnouncementClockView(Context context,List<String> times){super(context);this.times=times;setContentDescription("Task announcement alarm clock, "+String.join(" and ",times));}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f;
        paint.setStrokeWidth(7);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.rgb(73,61,51));
        c.drawLine(cx-w*.20f,h*.78f,cx-w*.25f,h*.92f,paint);c.drawLine(cx+w*.20f,h*.78f,cx+w*.25f,h*.92f,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(105,86,68));
        c.drawOval(cx-w*.31f,h*.08f,cx-w*.05f,h*.31f,paint);c.drawOval(cx+w*.05f,h*.08f,cx+w*.31f,h*.31f,paint);
        paint.setColor(Color.rgb(73,61,51));c.drawRoundRect(cx-w*.34f,h*.24f,cx+w*.34f,h*.83f,28,28,paint);
        paint.setColor(Color.rgb(35,33,28));c.drawRoundRect(cx-w*.27f,h*.36f,cx+w*.27f,h*.70f,16,16,paint);
        String display=times.size()==1?times.get(0):times.get(0)+"  ·  "+times.get(times.size()-1);
        paint.setColor(Color.rgb(239,170,64));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));paint.setTextSize(Math.min(w*.10f,h*.24f));
        c.drawText(display,cx,h*.59f,paint);
        paint.setColor(Color.rgb(73,61,51));c.drawCircle(cx,h*.15f,8,paint);c.drawLine(cx,h*.15f,cx,h*.04f,paint);
    }
}
