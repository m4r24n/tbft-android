package info.marzan.tbft;

import android.app.Activity;
import android.view.*;

/** One immersive policy for every TBFT surface; transient system bars remain reachable by edge swipe. */
final class Fullscreen {
    private Fullscreen() {}
    static void apply(Activity activity) {
        try {
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch(RuntimeException ignored) { /* Fullscreen must never stop the organiser starting. */ }
    }
}
