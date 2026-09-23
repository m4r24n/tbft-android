package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Matching walnut and brass marquetry doors for the wardrobe and library. */
final class HeritageDoors extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean library;
    private float open;
    HeritageDoors(Context context,boolean library){super(context);this.library=library;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setOpen(float value){open=value;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float unit=getResources().getDisplayMetrics().density,width=getWidth(),height=getHeight();
        float half=(width-12*unit)/2,visible=half*(1-.94f*open);
        panel(canvas,6*unit,9*unit,visible,height-23*unit,true,unit);
        panel(canvas,width-6*unit-visible,9*unit,visible,height-23*unit,false,unit);
    }
    private void panel(Canvas canvas,float x,float y,float width,float height,boolean left,float unit){
        if(width<=0)return;canvas.save();canvas.clipRect(x,y,x+width,y+height);
        paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(x,y,x+width,y,new int[]{0xff4a2f21,0xff795137,0xff946b49,0xff5b3927},null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x,y,x+width,y+height,5*unit,5*unit,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.3f*unit);paint.setColor(0xff2d1d16);canvas.drawRoundRect(x+6*unit,y+7*unit,x+width-6*unit,y+height-7*unit,4*unit,4*unit,paint);
        paint.setStrokeWidth(.8f*unit);paint.setColor(0xffc49a5a);canvas.drawRoundRect(x+9*unit,y+10*unit,x+width-9*unit,y+height-10*unit,3*unit,3*unit,paint);
        for(int i=1;i<12;i++){float grain=x+width*i/12;paint.setColor(i%2==0?0x28704a31:0x18f5d39b);Path line=new Path();line.moveTo(grain,y+12*unit);line.cubicTo(grain+3*unit,y+height*.28f,grain-4*unit,y+height*.72f,grain,y+height-12*unit);canvas.drawPath(line,paint);}
        canvas.save();canvas.translate(x,y);canvas.scale(width/180f,height/400f);marquetry(canvas,left);canvas.restore();
        float handle=left?x+width-14*unit:x+14*unit;paint.setStyle(Paint.Style.FILL);paint.setColor(0xff241913);canvas.drawOval(handle-5*unit,y+height*.57f-6*unit,handle+5*unit,y+height*.57f+31*unit,paint);
        paint.setColor(0xffcba765);canvas.drawRoundRect(handle-1.8f*unit,y+height*.57f,handle+1.8f*unit,y+height*.57f+23*unit,2*unit,2*unit,paint);canvas.restore();
    }
    private void marquetry(Canvas canvas,boolean left){
        // One calm Arts-and-Crafts composition spans the paired doors.
        Path arch=new Path();arch.moveTo(23,343);arch.lineTo(23,76);arch.cubicTo(23,43,50,39,90,22);arch.cubicTo(130,39,157,43,157,76);arch.lineTo(157,343);arch.quadTo(90,371,23,343);inlay(canvas,arch,2.4f);
        Path inner=new Path();inner.moveTo(31,337);inner.lineTo(31,82);inner.cubicTo(31,55,55,50,90,34);inner.cubicTo(125,50,149,55,149,82);inner.lineTo(149,337);inner.quadTo(90,360,31,337);inlay(canvas,inner,.9f);
        // Curving leaves are deliberately broad so the pattern reads on a phone.
        for(int side:new int[]{-1,1}){canvas.save();canvas.translate(90,0);canvas.scale(side,1);
            Path stem=new Path();stem.moveTo(0,324);stem.cubicTo(51,302,14,270,38,244);stem.cubicTo(63,217,17,185,39,154);stem.cubicTo(58,127,25,98,37,68);inlay(canvas,stem,2.2f);
            for(int i=0;i<5;i++){float yy=104+i*42;Path leaf=new Path();leaf.moveTo(35,yy+15);leaf.cubicTo(18,yy+10,7,yy+1,8,yy-12);leaf.cubicTo(25,yy-9,42,yy-2,35,yy+15);inlay(canvas,leaf,1.3f);}
            canvas.restore();}
        canvas.save();canvas.translate(90,190);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff3d291e);canvas.drawCircle(0,0,34,paint);paint.setColor(0xffa77b43);canvas.drawCircle(0,0,30,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f);paint.setColor(0xffe0be77);canvas.drawCircle(0,0,26,paint);
        if(library)bookMark(canvas);else garmentMark(canvas);canvas.restore();
        Path foot=new Path();foot.moveTo(58,334);foot.quadTo(90,355,122,334);foot.moveTo(69,341);foot.quadTo(90,326,111,341);inlay(canvas,foot,1.5f);
    }
    private void bookMark(Canvas c){
        Path book=new Path();book.moveTo(-21,-12);book.quadTo(-10,-17,0,-7);book.quadTo(10,-17,21,-12);book.lineTo(21,15);book.quadTo(10,10,0,19);book.quadTo(-10,10,-21,15);book.close();inlay(c,book,2);
        Path spine=new Path();spine.moveTo(0,-7);spine.lineTo(0,19);inlay(c,spine,1.1f);
    }
    private void garmentMark(Canvas c){
        Path hanger=new Path();hanger.moveTo(0,-15);hanger.lineTo(0,-20);hanger.cubicTo(10,-23,7,-31,1,-27);hanger.moveTo(0,-15);hanger.lineTo(-24,5);hanger.quadTo(0,12,24,5);hanger.close();inlay(c,hanger,1.8f);
        Path cloth=new Path();cloth.moveTo(-12,3);cloth.lineTo(-4,8);cloth.lineTo(4,8);cloth.lineTo(12,3);cloth.lineTo(24,14);cloth.lineTo(16,25);cloth.lineTo(11,20);cloth.lineTo(11,30);cloth.lineTo(-11,30);cloth.lineTo(-11,20);cloth.lineTo(-16,25);cloth.lineTo(-24,14);cloth.close();inlay(c,cloth,1.5f);
    }
    private void inlay(Canvas canvas,Path path,float width){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width+1.5f);paint.setColor(0xff241812);canvas.drawPath(path,paint);
        paint.setStrokeWidth(width);paint.setColor(0xffd3ae65);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
    }
}
