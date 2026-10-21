package info.marzan.tbft;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Lightweight, deterministic worn-metal treatment; no bitmap assets or network required. */
final class RuggedDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int base;private final boolean shell;
    RuggedDrawable(int base){this(base,false);} RuggedDrawable(int base,boolean shell){this.base=base;this.shell=shell;}
    @Override public void draw(Canvas c){Rect b=getBounds();paint.setColor(base);c.drawRect(b,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(shell?2:3);paint.setColor(shell?0xff30251f:Ui.OXIDE);c.drawRect(b.left+1,b.top+1,b.right-1,b.bottom-1,paint);
        paint.setStrokeWidth(1);paint.setColor(shell?0x304f5a50:0x405f382b);
        for(int i=0;i<7;i++){float y=b.top+(b.height()*(i+1)/8f);float x=b.left+((i*47)%Math.max(1,b.width()/3));c.drawLine(x,y,Math.min(b.right,x+28+(i*11)%55),y+(i%3-1)*2,paint);}
        if(!shell){paint.setStyle(Paint.Style.FILL);paint.setColor(0xff6f3b2b);float r=4;c.drawCircle(b.left+8,b.top+8,r,paint);c.drawCircle(b.right-8,b.top+8,r,paint);c.drawCircle(b.left+8,b.bottom-8,r,paint);c.drawCircle(b.right-8,b.bottom-8,r,paint);}
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int a){paint.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
