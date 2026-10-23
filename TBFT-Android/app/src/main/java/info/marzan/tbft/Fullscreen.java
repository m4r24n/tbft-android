package info.marzan.tbft;

import android.app.Activity;
import android.os.Build;
import android.view.*;

/** One immersive policy for every TBFT surface; transient system bars remain reachable by edge swipe. */
final class Fullscreen {
    private Fullscreen() {}
    static void apply(Activity activity) {
        Window window=activity.getWindow();
        if(Build.VERSION.SDK_INT>=30){
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller=window.getInsetsController();
            if(controller!=null){
                controller.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
