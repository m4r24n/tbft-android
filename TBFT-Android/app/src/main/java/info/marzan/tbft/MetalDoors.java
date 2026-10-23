package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Sliding steel fronts: oxidised locker for Inventory, restrained archive cabinet for Library. */
final class MetalDoors extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean library;private float open;
    MetalDoors(Context context,boolean library){super(context);this.library=library;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setOpen(float value){open=value;invalidate();}
    @Override protected void onDraw(Canvas canvas){float u=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),half=(w-12*u)/2,visible=half*(1-.94f*open);door(canvas,6*u,9*u,visible,h-23*u,true,u);door(canvas,w-6*u-visible,9*u,visible,h-23*u,false,u);}
    private void door(Canvas c,float x,float y,float w,float h,boolean left,float u){
        if(w<=0)return;c.save();c.clipRect(x,y,x+w,y+h);p.setStyle(Paint.Style.FILL);int[] colors=library?new int[]{0xff181b1b,0xff343733,0xff202322}:new int[]{0xff242522,0xff494039,0xff292b28};
        p.setShader(new LinearGradient(x,y,x+w,y,colors,null,Shader.TileMode.CLAMP));c.drawRoundRect(x,y,x+w,y+h,2*u,2*u,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*u);p.setColor(0xff0d0f0f);c.drawRoundRect(x+5*u,y+5*u,x+w-5*u,y+h-5*u,u,u,p);p.setStrokeWidth(.7f*u);p.setColor(library?0xff686c67:0xff776451);
        for(int i=1;i<6;i++){float yy=y+h*i/6;c.drawLine(x+8*u,yy,x+w-8*u,yy,p);}if(!library){p.setStyle(Paint.Style.FILL);p.setColor(0x356f321f);for(int i=0;i<9;i++){float xx=x+(i*37%91)/100f*w,yy=y+(i*61%87)/100f*h;c.drawCircle(xx,yy,(2+i%3)*u,p);}}
        p.setStyle(Paint.Style.FILL);p.setColor(0xff111312);float hx=left?x+w-11*u:x+11*u,hy=y+h*.50f;c.drawRoundRect(hx-4*u,hy-18*u,hx+4*u,hy+18*u,2*u,2*u,p);p.setColor(0xffb88a45);c.drawCircle(hx,hy,2.2f*u,p);
        p.setColor(0xff151817);float[][] bolts={{x+9*u,y+9*u},{x+w-9*u,y+9*u},{x+9*u,y+h-9*u},{x+w-9*u,y+h-9*u}};for(float[] bolt:bolts)c.drawCircle(bolt[0],bolt[1],2*u,p);
        p.setColor(0xffaaa488);p.setTextSize(7*u);p.setTypeface(Typeface.MONOSPACE);p.setTextAlign(Paint.Align.CENTER);c.drawText(library?"ARCHIVE":"INVENTORY",x+w/2,y+19*u,p);c.restore();
    }
}
