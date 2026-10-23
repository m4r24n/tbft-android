package info.marzan.tbft;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import java.util.Locale;

/** Native field map. MapLibre keeps an ambient tile cache, so visited areas survive outages. */
public final class MapActivity extends Activity {
    private static final String STYLE="https://tiles.openfreemap.org/styles/dark";
    private MapView mapView; private TextView telemetry,status;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Fullscreen.apply(this);MapLibre.getInstance(this);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.DARK);
        mapView=new MapView(this);mapView.onCreate(state);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));
        View amber=new View(this);amber.setBackgroundColor(0x102a1600);root.addView(amber,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout hud=Ui.column(this);hud.setPadding(Ui.dp(this,12),Ui.dp(this,9),Ui.dp(this,12),Ui.dp(this,9));hud.setBackground(new TerminalPanelDrawable());
        TextView title=Ui.text(hud,"FIELD MAP // TOPOGRAPHIC RELAY",11,Ui.ACCENT);title.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
        status=Ui.text(hud,"ACQUIRING TERRAIN DATA…",11,Ui.MUTED);telemetry=Ui.text(hud,"51.4818 N  //  7.2162 E  //  Z 11.0",11,Ui.INK);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);hp.setMargins(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10),0);root.addView(hud,hp);
        TextView cross=new TextView(this);cross.setText("＋");cross.setTextColor(0xffd6a44c);cross.setTextSize(30);cross.setGravity(Gravity.CENTER);root.addView(cross,new FrameLayout.LayoutParams(-1,-1));
        Button back=new Button(this);back.setText("‹ RETURN");back.setTextColor(Ui.INK);back.setTextSize(11);back.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);back.setBackground(Ui.shape(this,0xdd171b1a,1));back.setOnClickListener(v->finish());
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,48),Gravity.BOTTOM|Gravity.START);bp.setMargins(Ui.dp(this,10),0,0,Ui.dp(this,10));root.addView(back,bp);
        setContentView(root);
        mapView.getMapAsync(map->{
            map.setCameraPosition(new CameraPosition.Builder().target(new LatLng(51.4818,7.2162)).zoom(11).build());
            map.setStyle(STYLE,style->status.setText("TERRAIN LINK ACTIVE // VISITED TILES CACHED"));
            map.addOnCameraMoveListener(()->showPosition(map));
            showPosition(map);
        });
    }
    private void showPosition(MapLibreMap map){LatLng p=map.getCameraPosition().target;telemetry.setText(String.format(Locale.ROOT,"%.4f N  //  %.4f E  //  Z %.1f",p.getLatitude(),p.getLongitude(),map.getCameraPosition().zoom));}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Fullscreen.apply(this);}
    @Override protected void onStart(){super.onStart();mapView.onStart();}
    @Override protected void onResume(){super.onResume();Fullscreen.apply(this);mapView.onResume();}
    @Override protected void onPause(){mapView.onPause();super.onPause();}
    @Override protected void onStop(){mapView.onStop();super.onStop();}
    @Override protected void onDestroy(){mapView.onDestroy();super.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);mapView.onSaveInstanceState(out);}
}
