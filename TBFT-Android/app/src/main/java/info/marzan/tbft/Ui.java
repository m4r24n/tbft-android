package info.marzan.tbft;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** The web app's paper, ink, brass and sage palette, with native touch targets. */
final class Ui {
    static final int BG=0xffece9df, CARD=0xfffaf8f2, INK=0xff292b27, MUTED=0xff666961,
            LINE=0xffddd7c9, ACCENT=0xff607d68, SAGE=0xffe0e7dc, BRASS=0xffa38b57, DARK=0xff202820;
    static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); d.setStroke(dp(c,1),LINE); return d;
    }
    static LinearLayout column(Context c) { LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static TextView text(LinearLayout p,String value,int size,int color) {
        Context c=p.getContext(); TextView t=new TextView(c); t.setText(value); t.setTextColor(color); t.setTextSize(size);
        t.setPadding(0,dp(c,4),0,dp(c,4)); p.addView(t); return t;
    }
    static TextView heading(LinearLayout p,String value,int size) { TextView t=text(p,value,size,INK); t.setTypeface(null,Typeface.BOLD); return t; }
    static LinearLayout card(LinearLayout p) {
        Context c=p.getContext(); LinearLayout v=column(c); v.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14)); v.setBackground(shape(c,CARD,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(c,6),0,dp(c,6)); p.addView(v,lp); return v;
    }
    static Button button(LinearLayout p,String label,Runnable action) {
        Context c=p.getContext(); Button b=new Button(c); b.setText(label); b.setTextColor(INK); b.setTextSize(14); b.setAllCaps(false);
        b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6)); b.setBackground(shape(c,CARD,12)); b.setElevation(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(p.getOrientation()==LinearLayout.VERTICAL?-1:-2,-2); lp.setMargins(dp(c,2),dp(c,4),dp(c,2),dp(c,4));
        p.addView(b,lp); b.setOnClickListener(v->action.run()); return b;
    }
    static void selected(Button b,boolean selected) { b.setBackground(shape(b.getContext(),selected?DARK:CARD,12)); b.setTextColor(selected?Color.WHITE:INK); }
    static LinearLayout chips(LinearLayout parent) {
        HorizontalScrollView scroll=new HorizontalScrollView(parent.getContext()); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(parent.getContext()); row.setGravity(Gravity.CENTER_VERTICAL); scroll.addView(row); parent.addView(scroll); return row;
    }
}
