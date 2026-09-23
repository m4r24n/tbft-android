package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONObject;

/** Resolution-independent clothing illustrations. Geometry and colour come from the saved item. */
final class GarmentView extends View {
    private String shape, sleeve;
    private int colour;
    private boolean hanging, hood;
    GarmentView(Context c,JSONObject doc,JSONObject item,boolean hanging) {
        this(c,WardrobeRules.shape(doc,item),WardrobeRules.sleeve(doc,item),WardrobeRules.hood(doc,item),Json.text(item,"color"),Json.text(item,"name"),hanging);
    }
    GarmentView(Context c,String shape,String sleeve,boolean hood,String hex,String description,boolean hanging) {
        super(c); update(shape,sleeve,hood,hex); this.hanging=hanging;
        setContentDescription(description); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void update(String shape,String sleeve,boolean hood,String hex) {
        this.shape=shape;this.sleeve=sleeve;this.hood=hood;
        // A new item and a partially typed hex value are valid preview states.
        colour=hex!=null&&hex.matches("#[0-9a-fA-F]{6}")?Color.parseColor(hex):0xffb8b1a3;
        invalidate();
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); draw(c,shape,sleeve,hood,colour,0,0,getWidth(),getHeight(),hanging);
    }
    static void draw(Canvas canvas,String shape,String sleeve,boolean hood,int colour,float x,float y,float w,float h,boolean hanging) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        float scale=Math.min(w/100f,h/(hanging?119f:100f));
        canvas.save();canvas.translate(x+(w-100*scale)/2,y+(h-(hanging?119:100)*scale)/2);canvas.scale(scale,scale);
        if(hanging) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0xff827359);
            Path hook=new Path();hook.moveTo(50,16);hook.lineTo(50,10);hook.cubicTo(61,6,53,-1,48,4);canvas.drawPath(hook,p);
            Path hanger=new Path();hanger.moveTo(50,14);hanger.lineTo(25,29);hanger.quadTo(50,33,75,29);hanger.close();canvas.drawPath(hanger,p);
            canvas.translate(0,19);
        }
        // A quiet drop shadow, not a background disc, leaves garments looking suspended.
        p.setStyle(Paint.Style.FILL);p.setColor(0x16000000);canvas.drawOval(25,91,77,96,p);
        float[] points;
        switch(shape) {
            case "trousers": case "jeans": points=new float[]{30,14,70,14,73,88,54,88,50,45,46,88,27,88};break;
            case "boxers": case "shorts": points=new float[]{28,20,72,20,76,72,54,72,50,46,46,72,24,72};break;
            case "briefs": points=new float[]{24,30,76,30,72,44,59,57,56,68,44,68,41,57,28,44};break;
            case "skirt": points=new float[]{35,18,65,18,82,85,18,85};break;
            case "shoes": points=new float[]{21,42,37,42,44,56,65,61,79,68,82,80,18,80,16,64};break;
            case "cap": points=new float[]{25,56,26,38,33,27,49,23,65,29,73,45,75,56,91,65,65,68,24,62};break;
            case "bag": points=new float[]{28,31,72,31,79,86,21,86};break;
            case "socks": points=new float[]{31,20,51,20,51,59,73,73,69,84,56,87,28,70};break;
            case "other": points=new float[]{30,24,70,24,77,39,71,80,29,80,23,39};break;
            default:
                boolean dress=shape.equals("dress"),longSleeve=sleeve.equals("long"),none=sleeve.equals("none")||shape.equals("tank");
                float hem=dress?57:68, leftHem=dress?20:32, rightHem=dress?80:68;
                if(none) points=new float[]{36,16,43,20,57,20,64,16,62,35,68,44,hem,50,rightHem,86,leftHem,86,100-hem,50,32,44,38,35};
                else if(longSleeve) points=new float[]{35,16,43,20,57,20,65,16,77,24,91,69,78,74,68,43,hem,50,rightHem,86,leftHem,86,100-hem,50,32,43,22,74,9,69,23,24};
                else points=new float[]{35,16,43,20,57,20,65,16,86,31,78,49,68,43,hem,50,rightHem,86,leftHem,86,100-hem,50,32,43,22,49,14,31};
        }
        Path path=polygon(points);p.setStyle(Paint.Style.FILL);p.setColor(colour);canvas.drawPath(path,p);
        canvas.save();canvas.clipPath(path);
        p.setColor(0x14ffffff);canvas.drawRect(20,12,43,89,p);
        p.setColor(0x13000000);canvas.drawRect(64,12,93,89,p);
        canvas.restore();
        int seam=mix(colour,0xff454237,.46f);p.setColor(seam);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f);canvas.drawPath(path,p);
        switch(shape) {
            case "trousers":case "jeans":case "boxers":case "shorts":
                line(canvas,p,30,25,70,25);line(canvas,p,50,25,50,43);line(canvas,p,34,16,34,24);line(canvas,p,66,16,66,24);
                canvas.drawArc(30,24,47,44,20,110,false,p);canvas.drawArc(53,24,70,44,50,110,false,p);
                if(shape.equals("jeans")){line(canvas,p,31,29,30,85);line(canvas,p,69,29,70,85);line(canvas,p,28,82,45,82);line(canvas,p,55,82,72,82);}break;
            case "briefs":line(canvas,p,25,37,75,37);line(canvas,p,34,46,44,64);line(canvas,p,66,46,56,64);break;
            case "skirt":line(canvas,p,35,25,65,25);line(canvas,p,41,28,33,82);line(canvas,p,59,28,67,82);break;
            case "shoes":
                line(canvas,p,18,75,80,75);line(canvas,p,45,58,41,64);line(canvas,p,52,60,47,67);line(canvas,p,59,63,54,69);canvas.drawArc(20,45,36,69,0,180,false,p);break;
            case "cap":canvas.drawArc(33,24,61,66,205,115,false,p);line(canvas,p,25,56,75,56);break;
            case "bag":canvas.drawRoundRect(36,16,64,45,12,12,p);canvas.drawRoundRect(31,56,69,77,3,3,p);break;
            case "socks":line(canvas,p,31,27,51,27);line(canvas,p,32,31,51,31);line(canvas,p,60,71,55,82);break;
            case "other":canvas.drawRoundRect(33,33,67,70,6,6,p);break;
            default:
                if(hood) {
                    // A folded fabric hood around a recessed opening, not a head-shaped disc.
                    Path outer=new Path();outer.moveTo(35,27);outer.cubicTo(30,15,33,3,43,1);outer.quadTo(50,-1,57,1);outer.cubicTo(67,3,70,15,65,27);outer.lineTo(58,34);outer.lineTo(50,28);outer.lineTo(42,34);outer.close();
                    p.setStyle(Paint.Style.FILL);p.setColor(mix(colour,Color.WHITE,.08f));canvas.drawPath(outer,p);
                    p.setStyle(Paint.Style.STROKE);p.setColor(seam);canvas.drawPath(outer,p);
                    Path opening=new Path();opening.moveTo(41,22);opening.cubicTo(37,11,42,6,50,6);opening.cubicTo(58,6,63,11,59,22);opening.quadTo(55,29,50,29);opening.quadTo(45,29,41,22);opening.close();
                    p.setStyle(Paint.Style.FILL);p.setColor(mix(colour,Color.BLACK,.39f));canvas.drawPath(opening,p);
                    p.setStyle(Paint.Style.STROKE);p.setColor(seam);canvas.drawPath(opening,p);
                    line(canvas,p,36,26,43,31);line(canvas,p,64,26,57,31);line(canvas,p,44,30,43,44);line(canvas,p,56,30,57,44);
                    p.setStrokeWidth(1.8f);line(canvas,p,43,43,43,46);line(canvas,p,57,43,57,46);p.setStrokeWidth(1.1f);
                }
                if(shape.equals("shirt")||shape.equals("jacket")) {
                    line(canvas,p,50,25,50,85);canvas.drawPath(polygon(new float[]{36,16,49,23,43,34}),p);canvas.drawPath(polygon(new float[]{64,16,51,23,57,34}),p);
                    for(int yButton=39;yButton<81;yButton+=11) canvas.drawCircle(52,yButton,.7f,p);
                    canvas.drawRect(56,40,64,50,p);
                    if(shape.equals("jacket")){line(canvas,p,36,62,45,57);line(canvas,p,55,57,64,62);}
                } else if(!hood)canvas.drawArc(42,15,58,31,0,180,false,p);
                if(shape.equals("hoodie"))canvas.drawPath(polygon(new float[]{40,62,60,62,65,77,35,77}),p);
                line(canvas,p,34,81,66,81);
                if(sleeve.equals("long")){line(canvas,p,11,65,24,69);line(canvas,p,76,69,89,65);}
                if(shape.equals("sweater")){for(int xx=35;xx<66;xx+=4)line(canvas,p,xx,81,xx,85);}
        }
        canvas.restore();
    }
    private static Path polygon(float[] a) {Path p=new Path();p.moveTo(a[0],a[1]);for(int i=2;i<a.length;i+=2)p.lineTo(a[i],a[i+1]);p.close();return p;}
    private static void line(Canvas c,Paint p,float x,float y,float x2,float y2){c.drawLine(x,y,x2,y2,p);}
    private static int mix(int a,int b,float t){return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),(int)(Color.green(a)*(1-t)+Color.green(b)*t),(int)(Color.blue(a)*(1-t)+Color.blue(b)*t));}
}
