package info.marzan.tbft;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.*;
import android.location.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.*;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;
import java.util.*;

/** Minimal field-navigation instrument. Beacons remain private in app storage. */
public final class FieldMapActivity extends Activity implements SensorEventListener {
    private static final int LOCATION_PERMISSION=41;
    private static final String STYLE="https://demotiles.maplibre.org/style.json";
    private MapView mapView; private MapLibreMap map; private TextView status,telemetry; private FieldOverlayView overlay;
    private LocationManager locations; private Location current; private SensorManager sensors; private Sensor rotation;
    private final Map<Long,JSONObject> markers=new HashMap<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);MapLibre.getInstance(this);build();mapView.onCreate(state);mapView.getMapAsync(m->{map=m;map.setStyle(STYLE,s->{restoreBeacons();requestPosition();});map.addOnMapLongClickListener(p->{nameBeacon(p);return true;});});}
    private int dp(int n){return Ui.dp(this,n);} private LinearLayout col(){return Ui.column(this);} 
    private void build(){LinearLayout root=col();root.setBackground(new RuggedDrawable(Ui.BG,true));root.setPadding(dp(10),dp(10),dp(10),dp(10));
        TextView plate=Ui.text(root,"NAVIGATION // LOCAL FIELD CHART",15,Ui.INK);plate.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        status=Ui.text(root,"LINKING POSITION AND CHART…",10,Ui.ACCENT);status.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Ui.DARK);mapView=new MapView(this);frame.addView(mapView,new FrameLayout.LayoutParams(-1,-1));overlay=new FieldOverlayView(this);frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        telemetry=Ui.text(root,"POS --  //  ACC --  //  NO ACTIVE BEACON",11,Ui.INK);telemetry.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        LinearLayout controls=new LinearLayout(this);button(controls,"CENTER",this::center);button(controls,"BEACONS",this::chooseBeacon);button(controls,"MARK",()->{if(current!=null)nameBeacon(new LatLng(current.getLatitude(),current.getLongitude()));else toast("Position unavailable");});button(controls,"BACK",this::finish);root.addView(controls);
        for(int i=0;i<controls.getChildCount();i++)controls.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0,dp(48),1));setContentView(root);
    }
    private Button button(LinearLayout p,String label,Runnable r){Button b=Ui.button(p,label,r);b.setTextSize(10);b.setPadding(0,0,0,0);return b;}
    private void requestPosition(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);return;}startPosition();}
    @SuppressWarnings("MissingPermission") private void startPosition(){locations=(LocationManager)getSystemService(LOCATION_SERVICE);LocationListener listener=l->{current=l;updatePosition();};
        try{locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,1,listener);Location last=locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(last!=null){current=last;updatePosition();}}catch(Exception ignored){}
        try{locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,3000,5,listener);}catch(Exception ignored){}
        status.setText("● GPS ACTIVE  //  HOLD MAP TO CREATE BEACON");}
    private void updatePosition(){if(current==null)return;telemetry.setText(String.format(Locale.US,"POS %.5f, %.5f  //  ACC ±%.0fm",current.getLatitude(),current.getLongitude(),current.getAccuracy()));}
    private void center(){if(current==null){requestPosition();return;}map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(new LatLng(current.getLatitude(),current.getLongitude())).zoom(15).build()),700);}
    private JSONArray saved(){try{return new JSONArray(getPreferences(MODE_PRIVATE).getString("beacons","[]"));}catch(Exception e){return new JSONArray();}}
    private void persist(JSONArray a){getPreferences(MODE_PRIVATE).edit().putString("beacons",a.toString()).apply();}
    private void nameBeacon(LatLng p){final EditText input=new EditText(this);input.setHint("Beacon name");input.setSingleLine(true);new AlertDialog.Builder(this).setTitle("MARK FIELD BEACON").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Store",(d,w)->{String n=input.getText().toString().trim();if(n.isEmpty())n="Beacon "+(saved().length()+1);JSONArray a=saved();JSONObject b=Json.of("id",System.currentTimeMillis(),"name",n,"lat",p.getLatitude(),"lon",p.getLongitude());a.put(b);persist(a);addBeacon(b);status.setText("BEACON STORED // "+n.toUpperCase(Locale.ROOT));}).show();}
    private void restoreBeacons(){JSONArray a=saved();for(int i=0;i<a.length();i++)addBeacon(a.optJSONObject(i));}
    private void addBeacon(JSONObject b){if(map==null||b==null)return;Marker marker=map.addMarker(new MarkerOptions().position(new LatLng(b.optDouble("lat"),b.optDouble("lon"))).title(b.optString("name")));markers.put(marker.getId(),b);map.setOnMarkerClickListener(m->{select(markers.get(m.getId()));return false;});}
    private void chooseBeacon(){JSONArray a=saved();if(a.length()==0){toast("No saved beacons. Hold the map or use MARK.");return;}String[] names=new String[a.length()];for(int i=0;i<a.length();i++)names[i]=a.optJSONObject(i).optString("name");new AlertDialog.Builder(this).setTitle("FIELD BEACONS").setItems(names,(d,i)->select(a.optJSONObject(i))).setNeutralButton("Manage",(d,w)->manageBeacons()).show();}
    private void manageBeacons(){JSONArray a=saved();if(a.length()==0)return;String[] names=new String[a.length()];for(int i=0;i<a.length();i++)names[i]="DELETE // "+a.optJSONObject(i).optString("name");new AlertDialog.Builder(this).setTitle("REMOVE BEACON").setItems(names,(d,index)->{JSONArray n=new JSONArray();for(int i=0;i<a.length();i++)if(i!=index)n.put(a.optJSONObject(i));persist(n);toast("Beacon removed. Reopen chart to refresh markers.");}).setNegativeButton("Cancel",null).show();}
    private void select(JSONObject b){if(b==null)return;double lat=b.optDouble("lat"),lon=b.optDouble("lon");map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lon),15),700);if(current==null){telemetry.setText("BEACON "+b.optString("name").toUpperCase(Locale.ROOT)+" // POSITION UNKNOWN");return;}float[] out=new float[2];Location.distanceBetween(current.getLatitude(),current.getLongitude(),lat,lon,out);telemetry.setText(String.format(Locale.US,"BEACON %s  //  %.1f km  //  BRG %03.0f°",b.optString("name").toUpperCase(Locale.ROOT),out[0]/1000f,(out[1]+360)%360));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startPosition();else status.setText("GPS DENIED // MAP AND SAVED BEACONS AVAILABLE");}
    @Override protected void onStart(){super.onStart();mapView.onStart();sensors=(SensorManager)getSystemService(SENSOR_SERVICE);rotation=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(rotation!=null)sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_UI);}
    @Override protected void onResume(){super.onResume();mapView.onResume();} @Override protected void onPause(){mapView.onPause();super.onPause();}
    @Override protected void onStop(){if(sensors!=null)sensors.unregisterListener(this);mapView.onStop();super.onStop();} @Override protected void onDestroy(){mapView.onDestroy();super.onDestroy();} @Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();} @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);mapView.onSaveInstanceState(b);}
    @Override public void onSensorChanged(SensorEvent e){float[] r=new float[9],o=new float[3];SensorManager.getRotationMatrixFromVector(r,e.values);SensorManager.getOrientation(r,o);overlay.setHeading((float)Math.toDegrees(o[0]));} @Override public void onAccuracyChanged(Sensor s,int a){}
}
