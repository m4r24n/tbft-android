package info.marzan.tbft;

import android.app.Activity;
import android.graphics.Color;
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
    private WebView web;
    private EditText address;
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 28 && !dataDirectorySet) {
            WebView.setDataDirectorySuffix("tbft-browser"); dataDirectorySet = true;
        }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 24, 31));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout controls = new LinearLayout(this);
        button(controls, "Close", this::finish); button(controls, "‹", () -> { if (web.canGoBack()) web.goBack(); });
        button(controls, "›", () -> { if (web.canGoForward()) web.goForward(); });
        button(controls, "Reload", () -> web.reload());
        root.addView(controls);
        LinearLayout location = new LinearLayout(this);
        address = new EditText(this); address.setSingleLine(true); address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY); address.setHint("Website address");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_GO) { navigate(); return true; } return false; });
        location.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        button(location, "Go", this::navigate); root.addView(location);
        status = new TextView(this); status.setTextColor(Color.LTGRAY); status.setPadding(16, 8, 16, 8);
        status.setText("Enter a website. Close returns to your organiser."); root.addView(status);
        web = new WebView(this);
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
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) { address.setText(url); status.setText("Loading…"); }
            @Override public void onPageFinished(WebView view, String url) { status.setText("Close returns to your organiser"); }
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
        });
        web.setDownloadListener((url, agent, disposition, mime, size) -> status.setText("Downloads are not available in this simple browser."));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); root.requestApplyInsets();
        if (state != null) web.restoreState(state);
        else if (getIntent().getData() != null && safe(getIntent().getData())) web.loadUrl(getIntent().getData().toString());
    }
    private void button(LinearLayout row, String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setContentDescription(text.equals("‹") ? "Back" : text.equals("›") ? "Forward" : text);
        b.setOnClickListener(v -> action.run()); row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
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
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else finish(); }
    @Override public void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onDestroy() { if (web != null) { web.stopLoading(); web.destroy(); } super.onDestroy(); }
}
