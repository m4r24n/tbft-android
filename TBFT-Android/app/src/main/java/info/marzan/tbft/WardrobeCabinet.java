package info.marzan.tbft;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** A painted cabinet around real, accessible controls. No WebView, assets or network. */
final class WardrobeCabinet extends FrameLayout {
    static final int WOOD=0xffbca583, EDGE=0xff806b50, INSIDE=0xffe0d4bd;
    private ValueAnimator animator;
    WardrobeCabinet(Context c,JSONObject doc,List<JSONObject> categories,String use,boolean open,Consumer<String> choose,Runnable opened) {
        super(c);setMinimumHeight(Ui.dp(c,365));
        addView(new Wood(c,false),new LayoutParams(-1,-1));
        LinearLayout shelves=Ui.column(c);shelves.setPadding(Ui.dp(c,18),Ui.dp(c,18),Ui.dp(c,18),Ui.dp(c,23));
        addView(shelves,new LayoutParams(-1,-1));
        for(int row=0;row<3;row++) {
            LinearLayout r=new LinearLayout(c);r.setBaselineAligned(false);shelves.addView(r,new LinearLayout.LayoutParams(-1,0,1));
            for(int col=0;col<2;col++) {
                int index=row*2+col;LinearLayout cell=Ui.column(c);cell.setGravity(Gravity.CENTER);
                cell.setPadding(Ui.dp(c,4),Ui.dp(c,1),Ui.dp(c,4),Ui.dp(c,5));r.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
                if(index>=categories.size())continue;
                JSONObject cat=categories.get(index);String id=Json.text(cat,"id");int count=0;
                LinearLayout garments=new LinearLayout(c);garments.setGravity(Gravity.CENTER);cell.addView(garments,new LinearLayout.LayoutParams(-1,0,1));
                int drawn=0;
                for(JSONObject item:WardrobeRules.list(WardrobeRules.state(doc),"items"))if(id.equals(Json.text(item,"category"))&&matches(item,use)) {
                    int n=WardrobeRules.count(item,"available");count+=n;
                    if(n>0&&drawn<3) {garments.addView(new GarmentView(c,doc,item,true),new LinearLayout.LayoutParams(0,-1,1));drawn++;}
                }
                if(drawn==0) {TextView empty=Ui.text(garments,"—",22,0xffb8a689);empty.setGravity(Gravity.CENTER);}
                TextView name=Ui.text(cell,Json.text(cat,"name"),14,Ui.INK);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setPadding(0,0,0,0);name.setMaxLines(2);
                TextView tally=Ui.text(cell,count==0?"Empty shelf":count+" ready to wear",11,0xff675b48);tally.setGravity(Gravity.CENTER);tally.setPadding(0,0,0,0);
                cell.setClickable(true);cell.setFocusable(true);cell.setContentDescription("Open "+Json.text(cat,"name")+", "+count+" available");cell.setOnClickListener(v->choose.accept(id));
                // Expose one labelled action for each compartment to screen readers.
                for(int n=0;n<cell.getChildCount();n++)cell.getChildAt(n).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        if(!open) {
            shelves.setVisibility(INVISIBLE);
            Wood doors=new Wood(c,true);addView(doors,new LayoutParams(-1,-1));
            LinearLayout invitation=Ui.column(c);invitation.setGravity(Gravity.CENTER);invitation.setPadding(Ui.dp(c,25),0,Ui.dp(c,25),0);
            Button button=Ui.button(invitation,"Open wardrobe",()->{});button.setBackground(Ui.shape(c,0xfff5ecd9,24));
            LayoutParams label=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);label.bottomMargin=Ui.dp(c,32);addView(invitation,label);
            button.setOnClickListener(v->{
                button.setEnabled(false);invitation.setVisibility(GONE);shelves.setVisibility(VISIBLE);shelves.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                if(!ValueAnimator.areAnimatorsEnabled()){opened.run();return;}
                animator=ValueAnimator.ofFloat(0,1);animator.setDuration(420);animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(a->{doors.open=(float)a.getAnimatedValue();doors.invalidate();});
                animator.addListener(new android.animation.AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled)opened.run();}});animator.start();
            });
        }
    }
    static boolean matches(JSONObject item,String use){return use.equals("all")||use.equals(Json.text(item,"use"))||"both".equals(Json.text(item,"use"));}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    private static final class Wood extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean doors;float open;
        Wood(Context c,boolean doors){super(c);this.doors=doors;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),u=getResources().getDisplayMetrics().density;
            if(doors){
                float half=(w-12*u)/2,visible=half*(1-.94f*open);
                panel(c,6*u,9*u,visible,h-23*u,true,u);panel(c,w-6*u-visible,9*u,visible,h-23*u,false,u);return;
            }
            p.setColor(0x1c423322);c.drawOval(w*.05f,h-12*u,w*.95f,h,p);
            p.setColor(EDGE);c.drawRoundRect(9*u,10*u,w-9*u,h-9*u,7*u,7*u,p);
            p.setColor(INSIDE);c.drawRect(18*u,18*u,w-18*u,h-23*u,p);
            p.setColor(0x0f806443);for(int i=1;i<14;i++)c.drawRect(18*u+(w-36*u)*i/14,18*u,19*u+(w-36*u)*i/14,h-23*u,p);
            p.setColor(WOOD);c.drawRoundRect(3*u,3*u,w-3*u,19*u,5*u,5*u,p);c.drawRect(9*u,h-24*u,w-9*u,h-13*u,p);
            c.drawRect(w/2-4*u,18*u,w/2+4*u,h-23*u,p);
            for(int i=1;i<=2;i++){float y=18*u+(h-41*u)*i/3;p.setColor(0x1b423322);c.drawRect(18*u,y,w-18*u,y+9*u,p);p.setColor(WOOD);c.drawRect(13*u,y-4*u,w-13*u,y+2*u,p);}
            p.setColor(EDGE);c.drawRect(20*u,h-14*u,35*u,h-3*u,p);c.drawRect(w-35*u,h-14*u,w-20*u,h-3*u,p);
        }
        private void panel(Canvas c,float x,float y,float w,float h,boolean left,float u){
            if(w<=0)return;c.save();c.clipRect(x,y,x+w,y+h);
            p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(x,y,x+w,y,new int[]{0xff765033,0xffa47b4c,0xff8d613b,0xff69442c},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(x,y,x+w,y+h,4*u,4*u,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u);p.setColor(0xff503723);c.drawRoundRect(x+5*u,y+6*u,x+w-5*u,y+h-6*u,4*u,4*u,p);
            p.setColor(0xffc39a63);c.drawRoundRect(x+8*u,y+9*u,x+w-8*u,y+h-9*u,3*u,3*u,p);
            for(int i=0;i<13;i++){float xx=x+w*(i+1)/14;p.setColor(i%2==0?0x28704c2c:0x20ffe0a1);Path grain=new Path();grain.moveTo(xx,y+12*u);grain.cubicTo(xx+4*u,y+h*.3f,xx-6*u,y+h*.7f,xx,y+h-12*u);c.drawPath(grain,p);}
            c.save();c.translate(x,y);c.scale(w/180f,h/400f);ornament(c);c.restore();
            p.setStyle(Paint.Style.FILL);p.setColor(0xff4d3421);float hx=left?x+w-14*u:x+14*u;
            c.drawOval(hx-5*u,y+h*.57f-6*u,hx+5*u,y+h*.57f+32*u,p);p.setColor(0xffc5a269);
            c.drawRoundRect(hx-2*u,y+h*.57f,hx+2*u,y+h*.57f+24*u,2*u,2*u,p);c.restore();
        }
        private void ornament(Canvas c){
            Path frame=new Path();frame.moveTo(25,344);frame.lineTo(25,75);frame.cubicTo(25,36,56,39,90,21);frame.cubicTo(124,39,155,36,155,75);frame.lineTo(155,344);frame.quadTo(90,376,25,344);carve(c,frame,2.8f);
            Path arch=new Path();arch.moveTo(32,341);arch.lineTo(32,78);arch.cubicTo(32,43,57,48,90,30);arch.cubicTo(123,48,148,43,148,78);arch.lineTo(148,341);arch.quadTo(90,366,32,341);carve(c,arch,1.1f);
            // Mirrored acanthus scrolls with relief highlights, framing a floral medallion.
            for(int side:new int[]{-1,1}){
                c.save();c.translate(90,0);c.scale(side,1);
                Path vine=new Path();vine.moveTo(0,327);vine.cubicTo(61,311,16,277,35,252);vine.cubicTo(71,220,20,203,42,171);vine.cubicTo(69,130,17,111,28,73);vine.quadTo(32,57,43,62);vine.cubicTo(57,70,41,85,36,75);carve(c,vine,2.2f);
                for(int j=0;j<5;j++){
                    float yy=104+j*43;Path leaf=new Path();leaf.moveTo(34,yy+15);leaf.cubicTo(19,yy+7,8,yy+5,7,yy-10);leaf.cubicTo(20,yy-5,40,yy-4,34,yy+15);carve(c,leaf,1.5f);
                    Path vein=new Path();vein.moveTo(12,yy-5);vein.quadTo(27,yy+2,34,yy+15);carve(c,vein,.8f);
                }
                c.restore();
            }
            c.save();c.translate(90,181);p.setStyle(Paint.Style.FILL);p.setColor(0xff855a35);c.drawCircle(0,0,31,p);
            Path ring=new Path();ring.addCircle(0,0,31,Path.Direction.CW);carve(c,ring,2);ring.reset();ring.addCircle(0,0,27,Path.Direction.CW);carve(c,ring,.8f);
            for(int i=0;i<8;i++){c.save();c.rotate(i*45);Path petal=new Path();petal.moveTo(0,-6);petal.cubicTo(-14,-12,-9,-25,0,-25);petal.cubicTo(9,-25,14,-12,0,-6);carve(c,petal,1.5f);c.restore();}
            Path center=new Path();center.addCircle(0,0,6,Path.Direction.CW);carve(c,center,1.7f);c.restore();
            Path crown=new Path();crown.moveTo(65,48);crown.quadTo(79,63,90,45);crown.quadTo(101,63,115,48);carve(c,crown,1.8f);
            Path base=new Path();base.moveTo(66,335);base.quadTo(90,351,114,335);base.moveTo(73,340);base.quadTo(90,327,107,340);carve(c,base,1.4f);
        }
        private void carve(Canvas c,Path path,float width){
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(width+1);
            c.save();c.translate(1,1.4f);p.setColor(0xffd1a96e);c.drawPath(path,p);c.restore();
            p.setStrokeWidth(width);p.setColor(0xff553820);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }

    }
    static final class Basket extends View {
        private final JSONObject doc;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Basket(Context c,JSONObject doc){super(c);this.doc=doc;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float scale=Math.min(getWidth()/130f,getHeight()/108f);c.save();c.translate((getWidth()-130*scale)/2,(getHeight()-108*scale)/2);c.scale(scale,scale);
            p.setStyle(Paint.Style.FILL);p.setColor(0x18000000);c.drawOval(14,94,118,103,p);
            int i=0;for(JSONObject item:WardrobeRules.list(WardrobeRules.state(doc),"items"))if(WardrobeRules.count(item,"laundry")>0&&i<4){
                int color;try{color=Color.parseColor(Json.text(item,"color"));}catch(Exception e){color=Ui.SAGE;}
                GarmentView.draw(c,WardrobeRules.shape(doc,item),WardrobeRules.sleeve(doc,item),WardrobeRules.hood(doc,item),color,12+i*18,7+(i%2)*7,55,60,false);i++;
            }
            Path basket=new Path();basket.moveTo(13,43);basket.quadTo(65,33,117,43);basket.lineTo(105,94);basket.quadTo(65,103,25,94);basket.close();
            p.setColor(0xffc1a779);c.drawPath(basket,p);c.save();c.clipPath(basket);p.setStrokeWidth(1.4f);p.setColor(0xffa08760);
            for(int y=48;y<100;y+=7)c.drawLine(12,y,118,y,p);for(int x=19;x<119;x+=9)c.drawLine(x,41,x+4,99,p);c.restore();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff826d4e);c.drawOval(13,36,117,49,p);p.setStrokeWidth(1.3f);c.drawPath(basket,p);
            c.drawRoundRect(51,55,79,65,4,4,p);p.setStyle(Paint.Style.FILL);c.restore();
        }
    }
}
