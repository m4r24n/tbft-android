package info.marzan.tbft;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Lightweight, deterministic worn-metal treatment; no bitmap assets or network required. */
final class RuggedDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int base;private final boolean shell;
    RuggedDrawable(int base){this(base,false);} RuggedDrawable(int base,boolean shell){this.base=base;this.shell=shell;}
    @Override public void draw(Canvas c){Rect b=getBounds();
        LinearGradient metal=new LinearGradient(b.left,b.top,b.right,b.bottom,base,shell?0xff141715:base,Shader.TileMode.CLAMP);paint.setShader(metal);c.drawRect(b,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(shell?5:3);paint.setColor(shell?0xff454640:Ui.OXIDE);c.drawRect(b.left+2,b.top+2,b.right-2,b.bottom-2,paint);
        paint.setStrokeWidth(1);paint.setColor(shell?0x557a7468:0x405f382b);
        for(int i=0;i<19;i++){float y=b.top+13+((i*67)%Math.max(14,b.height()-20));float x=b.left+5+((i*43)%Math.max(10,b.width()-55));c.drawLine(x,y,Math.min(b.right-5,x+18+(i*17)%62),y+(i%3-1)*2,paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(shell?0xff171816:0xff6f3b2b);float r=shell?7:4;
        float inset=shell?13:8;for(float x:new float[]{b.left+inset,b.right-inset})for(float y:new float[]{b.top+inset,b.bottom-inset}){c.drawCircle(x,y,r,paint);paint.setColor(0xff797469);c.drawRect(x-r/2,y-1,x+r/2,y+1,paint);paint.setColor(shell?0xff171816:0xff6f3b2b);}
        if(shell){paint.setColor(0x607d3f29);c.drawRect(b.left,b.top+b.height()*.72f,b.left+6,b.bottom-20,paint);c.drawRect(b.right-5,b.top+30,b.right,b.top+b.height()*.3f,paint);}
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int a){paint.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
