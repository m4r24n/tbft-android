package info.marzan.tbft;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;

/** Deliberately small browser in its own process and WebView data directory. No native bridge. */
public class BrowserActivity extends Activity {
    private static boolean dataDirectorySet;
    private FieldWebView web;
    private EditText address;
    private TextView status;
    private String downloadUrl="",downloadAgent="",downloadMime="application/octet-stream";
    private volatile boolean saving,cancelled;
    private final java.util.concurrent.ExecutorService downloads=java.util.concurrent.Executors.newSingleThreadExecutor();
    private Button cancelDownload;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);Fullscreen.apply(this);
        if (Build.VERSION.SDK_INT >= 28 && !dataDirectorySet) {
            WebView.setDataDirectorySuffix("tbft-browser"); dataDirectorySet = true;
        }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new RuggedDrawable(Ui.BG,true));root.setPadding(8,8,8,8);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(8+insets.getSystemWindowInsetLeft(), 8+insets.getSystemWindowInsetTop(),
                    8+insets.getSystemWindowInsetRight(), 8+insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView unit=new TextView(this);unit.setText("06 / SIGNAL RECEIVER");unit.setTextColor(Ui.ACCENT);unit.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);unit.setTextSize(11);unit.setLetterSpacing(.12f);unit.setPadding(16,10,16,6);root.addView(unit);
        LinearLayout controls = new LinearLayout(this);
        button(controls, "Close", this::closeBrowser); button(controls, "‹", () -> { if (web.canGoBack()) web.goBack(); });
        button(controls, "›", () -> { if (web.canGoForward()) web.goForward(); });
        button(controls, "Reload", () -> web.reload());
        boolean fieldRate=getPreferences(0).getBoolean("field_rate",true);
        root.addView(controls);
        LinearLayout location = new LinearLayout(this);
        address = new EditText(this); address.setSingleLine(true); address.setTextColor(Ui.INK);address.setBackground(Ui.shape(this,Ui.DARK,4));address.setPadding(14,8,14,8);
        address.setHintTextColor(Ui.MUTED); address.setHint("Website address");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_GO) { navigate(); return true; } return false; });
        location.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        button(location, "Go", this::navigate); root.addView(location);
        status = new TextView(this); status.setTextColor(Ui.MUTED); status.setPadding(16, 8, 16, 8);
        status.setText("RECEIVER STANDBY  //  MONO "+(fieldRate?"8 FPS":"SMOOTH")+" · visual video feed suppressed");status.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.NORMAL); root.addView(status);
        web = new FieldWebView(this);web.setFieldRate(fieldRate);web.setBackgroundColor(Ui.SCREEN);
        Paint mono=new Paint();ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);mono.setColorFilter(new ColorMatrixColorFilter(matrix));web.setLayerType(View.LAYER_TYPE_SOFTWARE,mono);
        Button rate=Ui.button(controls,fieldRate?"8 FPS":"SMOOTH",()->{});rate.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);rate.setTextSize(10);
        rate.setOnClickListener(v->{boolean on=!web.fieldRate;web.setFieldRate(on);getPreferences(0).edit().putBoolean("field_rate",on).apply();rate.setText(on?"8 FPS":"SMOOTH");status.setText(on?"FIELD DISPLAY // 8 FPS scan engaged":"FIELD DISPLAY // smooth scan engaged");});
        rate.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (safe(request.getUrl())) return false;
                status.setText("Only HTTPS websites can open here."); return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) { address.setText(url); if(!saving) status.setText("Loading…"); }
            @Override public void onPageFinished(WebView view, String url) {
                installAudioOnlyMode(view);
                if(!saving) status.setText("SIGNAL LOCKED  //  audio channel available · video feed suppressed");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Website unavailable. Your organiser still works offline.");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); status.setText("This website's secure connection could not be verified.");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin, false, false); }
            @Override public void onShowCustomView(View view,WebChromeClient.CustomViewCallback callback) {
                callback.onCustomViewHidden();status.setText("Fullscreen video is hidden. Use the audio-only control on the page.");
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, size) -> download(url,agent,disposition,mime));
        web.setOnLongClickListener(v->{
            WebView.HitTestResult hit=web.getHitTestResult();
            if(hit!=null && hit.getExtra()!=null && hit.getExtra().startsWith("https://")) {
                String link=hit.getExtra();new AlertDialog.Builder(this).setItems(new String[]{"Open link","Download linked file"},(d,w)->{if(w==0)web.loadUrl(link);else download(link,web.getSettings().getUserAgentString(),null,null);}).show();return true;
            }return false;
        });
        cancelDownload=Ui.button(root,"Cancel download",()->cancelled=true);cancelDownload.setVisibility(View.GONE);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); root.requestApplyInsets();
        if (state != null) { web.restoreState(state);downloadUrl=state.getString("downloadUrl","");downloadAgent=state.getString("downloadAgent","");downloadMime=state.getString("downloadMime","application/octet-stream"); }
        else if (getIntent().getData() != null && safe(getIntent().getData())) web.loadUrl(getIntent().getData().toString());
    }
    @Override protected void onResume(){super.onResume();Fullscreen.apply(this);if(web!=null)web.onResume();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Fullscreen.apply(this);}
    private void button(LinearLayout row, String text, Runnable action) {
        Button b = Ui.button(row,text,action); b.setContentDescription(text.equals("‹") ? "Back" : text.equals("›") ? "Forward" : text);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
    }
    private void download(String url,String agent,String disposition,String mime){
        if(saving||!downloadUrl.isEmpty()){status.setText("Finish or cancel the current download first.");return;}
        try{BrowserDownloads.secure(url);}catch(Exception e){status.setText("This download needs a direct HTTPS file link. Browser-generated blob files are not supported yet.");return;}
        downloadUrl=url;downloadAgent=agent;downloadMime=mime!=null&&mime.contains("/")?mime:"application/octet-stream";
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(downloadMime)
                .putExtra(Intent.EXTRA_TITLE,BrowserDownloads.filename(URLUtil.guessFileName(url,disposition,mime)));
        try{startActivityForResult(save,61);}catch(android.content.ActivityNotFoundException e){downloadUrl="";status.setText("No file picker is available on this device.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=61)return;
        String url=downloadUrl;downloadUrl="";
        if(result!=RESULT_OK||data==null||data.getData()==null||url.isEmpty())return;
        android.net.Uri destination=data.getData();String mime=downloadMime,agent=downloadAgent;
        saving=true;cancelled=false;cancelDownload.setVisibility(View.VISIBLE);status.setText("Saving file… Keep this browser open.");
        downloads.execute(()->{
            String error="";
            try(java.io.OutputStream output=getContentResolver().openOutputStream(destination,"wt")){
                if(output==null)throw new java.io.IOException("Cannot write to this location.");
                BrowserDownloads.save(url,agent,u->CookieManager.getInstance().getCookie(u),output,()->cancelled,
                    bytes->runOnUiThread(()->{if(!isDestroyed())status.setText("Downloading · "+android.text.format.Formatter.formatFileSize(this,bytes));}));
            }catch(Exception e){
                error=cancelled?"Download cancelled.":e.getMessage()==null?"Download failed. Try again.":e.getMessage();
                try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}
            }
            final String failure=error;saving=false;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;cancelDownload.setVisibility(View.GONE);
                if(!failure.isEmpty()){status.setText(failure);return;}
                status.setText("Download saved");
                new AlertDialog.Builder(this).setTitle("File saved").setMessage("Your file is in the location you selected.").setNegativeButton("Done",null)
                    .setPositiveButton("Open",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(destination,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(android.content.ActivityNotFoundException e){status.setText("Saved. Open this file with a compatible app from Files.");}}).show();
            });
        });
    }
    private void closeBrowser(){
        if(saving)new AlertDialog.Builder(this).setTitle("Cancel this download?").setMessage("Keep the browser open until your file has finished saving.").setNegativeButton("Keep downloading",null).setPositiveButton("Cancel & close",(d,w)->{cancelled=true;finish();}).show();
        else finish();
    }
    private static boolean safe(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
    }
    private void navigate() {
        String value = address.getText().toString().trim();
        if (!value.contains("://")) value = "https://" + value;
        Uri uri = Uri.parse(value);
        if (!safe(uri)) { status.setText("Enter an HTTPS website address."); return; }
        web.loadUrl(uri.toString());
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        web.requestFocus();
    }
    private void installAudioOnlyMode(WebView view){
        String script="(function(){if(window.__tbftAudioOnly)return;window.__tbftAudioOnly=true;"+
            "var css=document.createElement('style');css.textContent='html{filter:grayscale(1) contrast(1.08)!important;background:#050505!important}body{background:#080808!important}body,body *:not([class*=icon]):not(i){font-family:monospace!important}video{opacity:0!important;visibility:hidden!important;pointer-events:none!important} video::cue{display:none!important}.tbft-audio-only{display:flex!important;align-items:center!important;justify-content:center!important;min-height:64px!important;background:#161616!important;color:#fff!important;border:1px solid #777!important;border-radius:2px!important;padding:14px 18px!important;margin:8px auto!important;font:600 16px monospace!important;position:relative!important;z-index:2147483647!important}';document.documentElement.appendChild(css);"+
            "function convert(v){if(v.dataset.tbftAudioOnly)return;v.dataset.tbftAudioOnly='1';v.disablePictureInPicture=true;v.removeAttribute('poster');var b=document.createElement('button');b.className='tbft-audio-only';b.textContent=v.paused?'Play audio':'Pause audio';b.setAttribute('aria-label','Audio-only video control');b.onclick=function(e){e.preventDefault();e.stopPropagation();if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}else v.pause();};v.addEventListener('play',function(){b.textContent='Pause audio'});v.addEventListener('pause',function(){b.textContent='Play audio'});v.parentNode.insertBefore(b,v.nextSibling);}"+
            "function scan(){document.querySelectorAll('video').forEach(convert);}"+
            "scan();new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});"+
            "document.addEventListener('fullscreenchange',function(){if(document.fullscreenElement)document.exitFullscreen().catch(function(){})},true);"+
            "})();";
        view.evaluateJavascript(script,null);
    }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else closeBrowser(); }
    @Override public void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); out.putString("downloadUrl",downloadUrl);out.putString("downloadAgent",downloadAgent);out.putString("downloadMime",downloadMime); }
    @Override protected void onDestroy() { cancelled=true;downloads.shutdown(); if (web != null) { web.stopLoading(); web.destroy(); } super.onDestroy(); }

    /** Low-refresh display affects painting only; page scripts, audio and downloads remain full speed. */
    private static final class FieldWebView extends WebView {
        boolean fieldRate;private long nextFrame;private boolean queued;
        FieldWebView(android.content.Context context){super(context);}
        void setFieldRate(boolean enabled){fieldRate=enabled;nextFrame=0;super.invalidate();}
        @Override public void invalidate(){
            if(!fieldRate){super.invalidate();return;}long now=android.os.SystemClock.uptimeMillis();
            if(now>=nextFrame){nextFrame=now+125;super.invalidate();return;}
            if(!queued){queued=true;postDelayed(()->{queued=false;nextFrame=android.os.SystemClock.uptimeMillis()+125;drawNow();},Math.max(1,nextFrame-now));}
        }
        @Override public void invalidate(int left,int top,int right,int bottom){invalidate();}
        private void drawNow(){super.invalidate();}
    }
}
