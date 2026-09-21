package info.marzan.tbft;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small, coloured garment silhouettes; white clothes retain a visible outline. */
final class GarmentView extends View {
    private final String kind;
    private final int colour;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    GarmentView(Context c,String kind,String hex,String description) {
        super(c); this.kind=kind; int value; try { value=Color.parseColor(hex); } catch(Exception e) { value=Color.GRAY; } colour=value;
        setContentDescription(description); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); float scale=Math.min(getWidth(),getHeight())/100f;
        canvas.save(); canvas.translate((getWidth()-100*scale)/2,(getHeight()-100*scale)/2); canvas.scale(scale,scale);
        paint.setColor(0xffeeeee5); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(50,51,43,paint);
        float[] points;
        switch(kind) {
            case "bottom": points=new float[]{30,16,70,16,76,84,54,84,50,47,46,84,24,84}; break;
            case "shorts": points=new float[]{28,23,72,23,78,72,53,72,50,50,47,72,22,72}; break;
            case "one_piece": points=new float[]{37,16,44,20,56,20,63,16,69,37,60,43,79,83,21,83,40,43,31,37}; break;
            case "shoes": points=new float[]{23,36,42,36,49,54,77,62,84,71,80,81,19,81,16,68}; break;
            case "other": points=new float[]{30,23,70,23,80,37,73,77,27,77,20,37}; break;
            default: points=new float[]{35,18,44,25,56,25,65,18,87,34,77,51,68,45,68,81,32,81,32,45,23,51,13,34};
        }
        Path path=new Path(); path.moveTo(points[0],points[1]); for(int i=2;i<points.length;i+=2) path.lineTo(points[i],points[i+1]); path.close();
        paint.setColor(colour); canvas.drawPath(path,paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setColor(0xff8f9489); canvas.drawPath(path,paint);
        if(kind.equals("shirt")||kind.equals("layer")) { canvas.drawLine(50,26,50,80,paint); canvas.drawLine(35,18,42,37,paint); canvas.drawLine(65,18,58,37,paint); }
        if(kind.equals("bottom")||kind.equals("shorts")) canvas.drawLine(30,29,70,29,paint);
        if(kind.equals("shoes")) canvas.drawLine(18,73,81,73,paint);
        paint.setStyle(Paint.Style.FILL); canvas.restore();
    }
}
