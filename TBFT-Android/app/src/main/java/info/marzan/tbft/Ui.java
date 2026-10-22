package info.marzan.tbft;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** TBFT field-terminal palette: readable steel, phosphor and amber instrumentation. */
final class Ui {
    static final int BG=0xff252724, CARD=0xff171b1a, INK=0xffd6c88e, MUTED=0xff928d72,
            LINE=0xff565344, ACCENT=0xffc8b766, SAGE=0xff282b23, BRASS=0xffd39a45, DARK=0xff080a09,
            RUST=0xff8e4e32, OXIDE=0xff512d24, SCREEN=0xff101514;
    static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,Math.min(radius,2))); d.setStroke(dp(c,1),LINE); return d;
    }
    static LinearLayout column(Context c) { LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static TextView text(LinearLayout p,String value,int size,int color) {
        Context c=p.getContext(); TextView t=new TextView(c); t.setText(value); t.setTextColor(color); t.setTextSize(size);
        t.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);t.setShadowLayer(.7f,0,0,0x667d7141);
        t.setPadding(0,dp(c,4),0,dp(c,4)); p.addView(t); return t;
    }
    static TextView heading(LinearLayout p,String value,int size) { TextView t=text(p,value,size,INK); t.setTypeface(null,Typeface.BOLD); return t; }
    static LinearLayout card(LinearLayout p) {
        Context c=p.getContext(); LinearLayout v=column(c); v.setPadding(dp(c,13),dp(c,10),dp(c,13),dp(c,10)); v.setBackground(new TerminalPanelDrawable());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(c,3),0,dp(c,3)); p.addView(v,lp); return v;
    }
    static Button button(LinearLayout p,String label,Runnable action) {
        Context c=p.getContext(); Button b=new Button(c); b.setText(label); b.setTextColor(INK); b.setTextSize(14); b.setAllCaps(false);
        b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6)); b.setBackground(shape(c,0xff22241f,1)); b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);b.setLetterSpacing(.04f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(p.getOrientation()==LinearLayout.VERTICAL?-1:-2,-2); lp.setMargins(dp(c,2),dp(c,4),dp(c,2),dp(c,4));
        p.addView(b,lp); b.setOnClickListener(v->action.run()); return b;
    }
    static void selected(Button b,boolean selected) { b.setBackground(shape(b.getContext(),selected?0xffc5bb82:CARD,1)); b.setTextColor(selected?0xff24251f:INK); }
    static LinearLayout chips(LinearLayout parent) {
        HorizontalScrollView scroll=new HorizontalScrollView(parent.getContext()); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(parent.getContext()); row.setGravity(Gravity.CENTER_VERTICAL); scroll.addView(row); parent.addView(scroll); return row;
    }
}
