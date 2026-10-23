package info.marzan.tbft;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Recessed glass display with bezel, plotting grid and persistent horizontal scan lines. */
final class CrtScreenDrawable extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override public void draw(Canvas c){Rect b=getBounds();
        p.setStyle(Paint.Style.FILL);p.setColor(0xff050706);c.drawRect(b,p);p.setColor(Ui.SCREEN);c.drawRect(b.left+8,b.top+8,b.right-8,b.bottom-8,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xff32332e);c.drawRect(b.left+3,b.top+3,b.right-3,b.bottom-3,p);
        p.setStrokeWidth(1);p.setColor(0x263f493f);for(float x=b.left+20;x<b.right-8;x+=24)c.drawLine(x,b.top+8,x,b.bottom-8,p);
        for(float y=b.top+16;y<b.bottom-8;y+=24)c.drawLine(b.left+8,y,b.right-8,y,p);
        p.setColor(0x205d6255);for(float y=b.top+10;y<b.bottom-8;y+=4)c.drawLine(b.left+8,y,b.right-8,y,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0x186c734e);c.drawRect(b.left+8,b.top+8,b.right-8,b.top+42,p);
    }
    @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
