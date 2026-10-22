package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Two deliberately different cabinet fronts: crafted oak wardrobe and minimal ash library. */
final class HeritageDoors extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean library;
    private float open;

    HeritageDoors(Context context,boolean library){
        super(context);this.library=library;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setOpen(float value){open=value;invalidate();}

    @Override protected void onDraw(Canvas canvas){
        float u=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
        float half=(w-12*u)/2,visible=half*(1-.94f*open);
        if(library){
            libraryDoor(canvas,6*u,9*u,visible,h-23*u,true,u);
            libraryDoor(canvas,w-6*u-visible,9*u,visible,h-23*u,false,u);
        }else{
            wardrobeDoor(canvas,6*u,9*u,visible,h-23*u,true,u);
            wardrobeDoor(canvas,w-6*u-visible,9*u,visible,h-23*u,false,u);
        }
    }

    private void wardrobeDoor(Canvas c,float x,float y,float w,float h,boolean left,float u){
        if(w<=0)return;c.save();c.clipRect(x,y,x+w,y+h);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(x,y,x+w,y,new int[]{0xff4a2e1d,0xff754b2d,0xff8d613b,0xff684126,0xff402719},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(x,y,x+w,y+h,4*u,4*u,p);p.setShader(null);

        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        for(int i=1;i<11;i++){
            float gx=x+w*i/11;p.setStrokeWidth(.7f*u);p.setColor(i%2==0?0x227c5737:0x18e2b77b);
            Path grain=new Path();grain.moveTo(gx,y+7*u);grain.cubicTo(gx+2*u,y+h*.28f,gx-3*u,y+h*.68f,gx+1*u,y+h-7*u);c.drawPath(grain,p);
        }

        float l=x+10*u,r=x+w-10*u,top=y+12*u,mid=y+h*.68f,bottom=y+h-12*u;
        Path upper=new Path();upper.moveTo(l,mid-7*u);upper.lineTo(l,top+30*u);
        upper.cubicTo(l,top+11*u,x+w*.30f,top+4*u,x+w*.50f,top);
        upper.cubicTo(x+w*.70f,top+4*u,r,top+11*u,r,top+30*u);
        upper.lineTo(r,mid-7*u);upper.close();raisedPanel(c,upper,u);
        Path lower=new Path();lower.addRoundRect(l,mid+5*u,r,bottom,2*u,2*u,Path.Direction.CW);raisedPanel(c,lower,u);

        c.save();c.translate(x+w/2,top+20*u);
        for(int i=-2;i<=2;i++){
            Path ray=new Path();ray.moveTo(0,11*u);ray.quadTo(i*5*u,-1*u,i*11*u,-5*u);
            relief(c,ray,0xff2c1a10,0xffbd8c50,u);
        }
        Path fan=new Path();fan.moveTo(-25*u,8*u);fan.quadTo(0,-15*u,25*u,8*u);relief(c,fan,0xff2c1a10,0xffbd8c50,u);c.restore();

        float hx=left?x+w-12*u:x+12*u,hy=y+h*.55f;
        p.setStyle(Paint.Style.FILL);p.setColor(0xff271a12);c.drawRoundRect(hx-4.5f*u,hy-8*u,hx+4.5f*u,hy+29*u,4*u,4*u,p);
        p.setColor(0xffb88b4d);c.drawCircle(hx,hy,2.8f*u,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*u);p.setColor(0xffc8a060);
        Path pull=new Path();pull.moveTo(hx,hy+3*u);pull.cubicTo(hx-5*u,hy+11*u,hx-5*u,hy+19*u,hx,hy+23*u);c.drawPath(pull,p);
        p.setStyle(Paint.Style.FILL);c.drawOval(hx-3.5f*u,hy+21*u,hx+3.5f*u,hy+27*u,p);c.restore();
    }

    private void raisedPanel(Canvas c,Path shape,float u){
        p.setStyle(Paint.Style.FILL);p.setColor(0x22754a2c);c.drawPath(shape,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(4.5f*u);p.setColor(0xff3b2417);c.drawPath(shape,p);
        p.setStrokeWidth(2.2f*u);p.setColor(0xffb07b45);c.drawPath(shape,p);
        p.setStrokeWidth(.75f*u);p.setColor(0xffd1a06a);c.drawPath(shape,p);
    }

    private void libraryDoor(Canvas c,float x,float y,float w,float h,boolean left,float u){
        if(w<=0)return;c.save();c.clipRect(x,y,x+w,y+h);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(x,y,x+w,y,new int[]{0xffd8cbb5,0xffeee6d8,0xffd3c3aa},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(x,y,x+w,y+h,4*u,4*u,p);p.setShader(null);
        p.setColor(0x187b6d5c);for(int i=1;i<8;i++)c.drawRect(x+w*i/8,y+5*u,x+w*i/8+.6f*u,y+h-5*u,p);

        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f*u);p.setColor(0xffaa9a82);
        c.drawRoundRect(x+8*u,y+8*u,x+w-8*u,y+h-8*u,2*u,2*u,p);
        p.setStrokeWidth(.6f*u);p.setColor(0xfff8f3ea);
        c.drawRoundRect(x+10*u,y+10*u,x+w-10*u,y+h-10*u,2*u,2*u,p);

        float base=y+h*.78f,center=x+w*.50f;
        p.setStyle(Paint.Style.FILL);int[] tones={0xff51665d,0xffb09262,0xff7a6252};float[] heights={22,31,17};
        for(int i=0;i<3;i++){float bx=center+(i-1)*9*u;p.setColor(tones[i]);c.drawRoundRect(bx-3*u,base-heights[i]*u,bx+3*u,base,1*u,1*u,p);}
        p.setColor(0xff9c8b72);c.drawRoundRect(center-15*u,base+3*u,center+15*u,base+4*u,.5f*u,.5f*u,p);

        float hx=left?x+w-10*u:x+10*u,hy=y+h*.47f;
        p.setColor(0xff343832);c.drawRoundRect(hx-1.7f*u,hy-18*u,hx+1.7f*u,hy+18*u,2*u,2*u,p);
        p.setColor(0xff697069);c.drawRoundRect(hx-.55f*u,hy-15*u,hx+.55f*u,hy+15*u,1*u,1*u,p);c.restore();
    }

    private void relief(Canvas c,Path path,int shadow,int highlight,float u){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f*u);p.setColor(highlight);c.save();c.translate(.8f*u,1.1f*u);c.drawPath(path,p);c.restore();
        p.setStrokeWidth(1.05f*u);p.setColor(shadow);c.drawPath(path,p);
    }
}
