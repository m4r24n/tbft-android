package info.marzan.tbft;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A flat row from the recessed phosphor display: separators, grid and faint scan lines. */
final class TerminalPanelDrawable extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override public void draw(Canvas c){Rect b=getBounds();p.setStyle(Paint.Style.FILL);p.setColor(0xe8151918);c.drawRect(b,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xff4d4b3e);c.drawRect(b.left+.5f,b.top+.5f,b.right-.5f,b.bottom-.5f,p);
        p.setColor(0x182f382f);for(float x=b.left+24;x<b.right;x+=24)c.drawLine(x,b.top,x,b.bottom,p);
        p.setColor(0x143f443b);for(float y=b.top+4;y<b.bottom;y+=4)c.drawLine(b.left,y,b.right,y,p);p.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
