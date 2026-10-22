package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Non-interactive phosphor instrumentation laid over the navigation chart. */
final class FieldOverlayView extends View {
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float heading;
    FieldOverlayView(Context context){super(context);setWillNotDraw(false);setClickable(false);
        line.setColor(0x559f9257);line.setStrokeWidth(Ui.dp(context,1));line.setStyle(Paint.Style.STROKE);
        text.setColor(Ui.INK);text.setTextSize(Ui.dp(context,11));text.setTypeface(Typeface.MONOSPACE);
    }
    void setHeading(float value){heading=(value+360f)%360f;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),step=Math.max(Ui.dp(getContext(),48),w/8f);
        for(float x=step;x<w;x+=step)c.drawLine(x,0,x,h,line);
        for(float y=step;y<h;y+=step)c.drawLine(0,y,w,y,line);
        float cx=w/2f,cy=h/2f,r=Ui.dp(getContext(),24);c.drawCircle(cx,cy,r,line);c.drawLine(cx-r-10,cy,cx+r+10,cy,line);c.drawLine(cx,cy-r-10,cx,cy+r+10,line);
        c.save();c.rotate(-heading,w-Ui.dp(getContext(),39),Ui.dp(getContext(),44));c.drawCircle(w-Ui.dp(getContext(),39),Ui.dp(getContext(),44),Ui.dp(getContext(),27),line);c.drawLine(w-Ui.dp(getContext(),39),Ui.dp(getContext(),17),w-Ui.dp(getContext(),39),Ui.dp(getContext(),71),line);c.restore();
        text.setTextAlign(Paint.Align.CENTER);c.drawText("N",w-Ui.dp(getContext(),39),Ui.dp(getContext(),14),text);
        text.setTextAlign(Paint.Align.LEFT);c.drawText(String.format(java.util.Locale.US,"HDG %03.0f°",heading),Ui.dp(getContext(),9),Ui.dp(getContext(),18),text);
        Paint scan=new Paint();scan.setColor(0x16000000);scan.setStrokeWidth(1);for(int y=0;y<h;y+=4)c.drawLine(0,y,w,y,scan);
    }
}
