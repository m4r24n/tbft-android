package info.marzan.tbft;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GarmentViewTest {
    @Test public void newGarmentAndPartiallyEnteredColoursCanRender() {
        JSONObject doc=WardrobeRules.fresh("space","owner");
        GarmentView view=new GarmentView(RuntimeEnvironment.getApplication(),doc,new JSONObject(),true);
        view.layout(0,0,180,220);
        Canvas canvas=new Canvas(Bitmap.createBitmap(180,220,Bitmap.Config.ARGB_8888));
        view.draw(canvas);
        for(String value:new String[]{null,"","#","#1","#12345","#GGGGGG","#536F82"}){
            view.update("hoodie","long",true,value);view.draw(canvas);
        }
    }
}
