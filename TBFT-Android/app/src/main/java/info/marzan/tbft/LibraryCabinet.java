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

/** Bookcase companion to the wardrobe, using the same wood and door language. */
final class LibraryCabinet extends FrameLayout {
    private ValueAnimator animator;
    LibraryCabinet(Context context,JSONObject doc,List<JSONObject> shelves,boolean open,Consumer<String> choose,Runnable opened){
        super(context);setMinimumHeight(Ui.dp(context,365));addView(new Interior(context),new LayoutParams(-1,-1));
        LinearLayout rows=Ui.column(context);rows.setPadding(Ui.dp(context,18),Ui.dp(context,18),Ui.dp(context,18),Ui.dp(context,23));addView(rows,new LayoutParams(-1,-1));
        for(int row=0;row<3;row++){
            LinearLayout line=new LinearLayout(context);line.setBaselineAligned(false);rows.addView(line,new LinearLayout.LayoutParams(-1,0,1));
            for(int col=0;col<2;col++){
                int index=row*2+col;LinearLayout cell=Ui.column(context);cell.setGravity(Gravity.CENTER);cell.setPadding(Ui.dp(context,5),Ui.dp(context,3),Ui.dp(context,5),Ui.dp(context,4));line.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
                if(index>=shelves.size())continue;JSONObject shelf=shelves.get(index);String id=Json.text(shelf,"id");
                LinearLayout books=new LinearLayout(context);books.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cell.addView(books,new LinearLayout.LayoutParams(-1,0,1));int shown=0;
                for(JSONObject book:LibraryRules.books(doc))if(id.equals(Json.text(book,"shelf"))&&shown<5){books.addView(new BookView(context,book),new LinearLayout.LayoutParams(Ui.dp(context,24),-1));shown++;}
                if(shown==0){TextView empty=Ui.text(books,"—",22,0xffb8a689);empty.setGravity(Gravity.CENTER);}
                int count=LibraryRules.count(doc,id,"all");TextView name=Ui.text(cell,Json.text(shelf,"name"),14,Ui.INK);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setPadding(0,0,0,0);
                TextView tally=Ui.text(cell,count==0?"Empty shelf":count+" book"+(count==1?"":"s"),11,0xff675b48);tally.setGravity(Gravity.CENTER);tally.setPadding(0,0,0,0);
                cell.setFocusable(true);cell.setClickable(true);cell.setContentDescription("Open "+Json.text(shelf,"name")+", "+count+" books");cell.setOnClickListener(v->choose.accept(id));
                for(int n=0;n<cell.getChildCount();n++)cell.getChildAt(n).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        if(!open){
            rows.setVisibility(INVISIBLE);HeritageDoors doors=new HeritageDoors(context,true);addView(doors,new LayoutParams(-1,-1));
            LinearLayout action=Ui.column(context);action.setGravity(Gravity.CENTER);Button button=Ui.button(action,"Open library",()->{});button.setBackground(Ui.shape(context,0xfff5ecd9,24));
            LayoutParams params=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);params.bottomMargin=Ui.dp(context,32);addView(action,params);
            button.setOnClickListener(v->{button.setEnabled(false);action.setVisibility(GONE);rows.setVisibility(VISIBLE);
                if(!ValueAnimator.areAnimatorsEnabled()){opened.run();return;}animator=ValueAnimator.ofFloat(0,1);animator.setDuration(420);animator.setInterpolator(new DecelerateInterpolator());animator.addUpdateListener(a->doors.setOpen((float)a.getAnimatedValue()));animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){opened.run();}});animator.start();});
        }
    }
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    private static final class Interior extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Interior(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),u=getResources().getDisplayMetrics().density;p.setColor(0x1c423322);c.drawOval(w*.05f,h-12*u,w*.95f,h,p);p.setColor(WardrobeCabinet.EDGE);c.drawRoundRect(9*u,10*u,w-9*u,h-9*u,7*u,7*u,p);p.setColor(WardrobeCabinet.INSIDE);c.drawRect(18*u,18*u,w-18*u,h-23*u,p);p.setColor(WardrobeCabinet.WOOD);c.drawRoundRect(3*u,3*u,w-3*u,19*u,5*u,5*u,p);c.drawRect(w/2-4*u,18*u,w/2+4*u,h-23*u,p);for(int i=1;i<=2;i++){float y=18*u+(h-41*u)*i/3;p.setColor(WardrobeCabinet.EDGE);c.drawRect(13*u,y-4*u,w-13*u,y+4*u,p);}c.drawRect(9*u,h-24*u,w-9*u,h-13*u,p);}
    }
}
