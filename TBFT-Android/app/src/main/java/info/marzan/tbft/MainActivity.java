package info.marzan.tbft;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.net.http.SslError;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://tbft.marzan.info";
    private static final String HOME_HOST = "tbft.marzan.info";
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private View errorView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();

        if (savedInstanceState == null) {
            loadHome();
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(17, 17, 17));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                android.graphics.Insets safeInsets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom);
                return windowInsets;
            });
        } else {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                view.setPadding(
                        windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        windowInsets.getSystemWindowInsetBottom());
                return windowInsets;
            });
        }

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 6);
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        errorView = createErrorView();
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        root.requestApplyInsets();
    }

    private View createErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(48, 48, 48, 48);
        box.setBackgroundColor(Color.rgb(17, 17, 17));

        TextView title = new TextView(this);
        title.setText("TBFT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView message = new TextView(this);
        message.setText("Unable to reach tbft.marzan.info.\nCheck your connection and try again.");
        message.setTextColor(Color.LTGRAY);
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, 28, 0, 28);
        box.addView(message, msgParams);

        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(v -> loadHome());
        box.addView(retry);
        return box;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setUserAgentString(settings.getUserAgentString() + " TBFT-Android/1.1");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WidgetSessionBridge(), "TBFTAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && HOME_HOST.equalsIgnoreCase(host)) {
                    return false;
                }
                if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                errorView.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if (url != null && url.startsWith(HOME_URL)) captureWidgetSession();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showError();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }
        });
    }

    private void captureWidgetSession() {
        String script = "(function(){try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(!k)continue;if(k.indexOf('sb-')===0&&k.indexOf('-auth-token')>0){var raw=localStorage.getItem(k);if(!raw)continue;var obj=JSON.parse(raw);var token=obj&&obj.refresh_token;if(token){TBFTAndroid.saveRefreshToken(token);return 'ok';}}}}catch(e){}return 'none';})();";
        webView.evaluateJavascript(script, null);
    }

    private class WidgetSessionBridge {
        @JavascriptInterface
        public void saveRefreshToken(String refreshToken) {
            TbftWidgetProvider.saveRefreshToken(getApplicationContext(), refreshToken);
        }
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null || cm.getActiveNetwork() == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void loadHome() {
        errorView.setVisibility(View.GONE);
        if (!hasNetwork()) {
            showError();
            return;
        }
        webView.loadUrl(HOME_URL);
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.removeJavascriptInterface("TBFTAndroid");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
