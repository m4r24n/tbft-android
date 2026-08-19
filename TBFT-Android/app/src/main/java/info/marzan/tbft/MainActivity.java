package info.marzan.tbft;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.net.http.SslError;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://tbft.marzan.info";
    private static final String HOME_HOST = "tbft.marzan.info";
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private static final int PAPER = Color.rgb(236, 233, 223);
    private static final int INK = Color.rgb(41, 43, 39);
    private static final int INK_SOFT = Color.rgb(102, 105, 97);
    private static final int INK_FAINT = Color.rgb(132, 134, 126);

    private FrameLayout root;
    private LinearLayout nativeScreen;
    private LinearLayout taskList;
    private TextView countText;
    private TextView dateText;
    private TextView syncText;
    private WebView webView;
    private ProgressBar progressBar;
    private View errorView;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean showingWeb = false;

    private SharedPreferences widgetPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (prefs, key) -> runOnUiThread(this::renderTodayScreen);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetPrefs = getSharedPreferences(TbftWidgetProvider.PREFS, MODE_PRIVATE);
        buildNativeUi();
        TbftWidgetProvider.requestImmediateSync(getApplicationContext());
    }

    private void applySafeInsets(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener((target, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                target.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else {
            view.setOnApplyWindowInsetsListener((target, insets) -> {
                target.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
                return insets;
            });
        }
    }

    private void buildNativeUi() {
        root = new FrameLayout(this);
        root.setBackground(createAppBackground());
        applySafeInsets(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);

        nativeScreen = new LinearLayout(this);
        nativeScreen.setOrientation(LinearLayout.VERTICAL);
        nativeScreen.setPadding(dp(22), dp(24), dp(22), dp(26));
        scroll.addView(nativeScreen, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("TBFT");
        title.setTextColor(INK);
        title.setTextSize(18);
        title.setLetterSpacing(0.08f);
        titles.addView(title);

        dateText = new TextView(this);
        dateText.setTextColor(INK_SOFT);
        dateText.setTextSize(12);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.setMargins(0, dp(3), 0, 0);
        titles.addView(dateText, dateParams);

        Button openFull = minimalButton("Full app  →");
        openFull.setOnClickListener(v -> showFullApp());
        header.addView(openFull);
        nativeScreen.addView(header);

        countText = new TextView(this);
        countText.setTextColor(INK);
        countText.setTextSize(28);
        countText.setLetterSpacing(-0.015f);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMargins(0, dp(34), 0, dp(18));
        nativeScreen.addView(countText, countParams);

        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        nativeScreen.addView(taskList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        syncText = new TextView(this);
        syncText.setTextColor(INK_FAINT);
        syncText.setTextSize(11);
        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        syncParams.setMargins(0, dp(20), 0, 0);
        nativeScreen.addView(syncText, syncParams);

        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        root.requestApplyInsets();
        renderTodayScreen();
    }

    private GradientDrawable createAppBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(246, 243, 235), PAPER, Color.rgb(229, 224, 212)});
        return background;
    }

    private Button minimalButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(INK_SOFT);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setMinHeight(0);
        button.setMinWidth(0);
        return button;
    }

    private void renderTodayScreen() {
        if (taskList == null) return;

        List<String> tasks = TbftWidgetProvider.getCachedTasks(this);
        String boardDate = widgetPrefs.getString(TbftWidgetProvider.KEY_BOARD_DATE, "");
        String error = widgetPrefs.getString(TbftWidgetProvider.KEY_ERROR, "");
        long syncTime = widgetPrefs.getLong(TbftWidgetProvider.KEY_SYNC_TIME, 0L);
        boolean connected = !widgetPrefs.getString(TbftWidgetProvider.KEY_REFRESH_TOKEN, "").isEmpty();

        dateText.setText(formatBoardDate(boardDate));
        countText.setText(tasks.isEmpty()
                ? "Nothing left today"
                : tasks.size() + (tasks.size() == 1 ? " task left" : " tasks left"));

        taskList.removeAllViews();
        if (tasks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(connected ? "Your board is clear." : "Open the full app once to connect your TBFT account.");
            empty.setTextColor(INK_SOFT);
            empty.setTextSize(15);
            empty.setPadding(dp(2), dp(10), dp(2), dp(10));
            taskList.addView(empty);

            if (!connected) {
                Button connect = minimalButton("Connect TBFT  →");
                connect.setOnClickListener(v -> showFullApp());
                taskList.addView(connect);
            }
        } else {
            for (String task : tasks) taskList.addView(createTaskRow(task));
        }

        if (!error.isEmpty()) {
            syncText.setText(error);
        } else if (syncTime > 0L) {
            String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(syncTime));
            syncText.setText("Updated " + time + " · widget refreshes every 30 min");
        } else {
            syncText.setText("Syncing…");
        }
    }

    private View createTaskRow(String task) {
        TextView row = new TextView(this);
        row.setText("○   " + task);
        row.setTextColor(INK);
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setSingleLine(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(188, 250, 248, 242));
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), Color.argb(110, 202, 196, 181));
        row.setBackground(bg);
        row.setElevation(dp(1));
        row.setOnClickListener(v -> showFullApp());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(9));
        row.setLayoutParams(params);
        return row;
    }

    private String formatBoardDate(String dateKey) {
        try {
            if (dateKey != null && !dateKey.isEmpty()) {
                Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey);
                if (parsed != null) {
                    return new SimpleDateFormat("EEEE · MMM d", Locale.getDefault()).format(parsed);
                }
            }
        } catch (Exception ignored) { }
        return new SimpleDateFormat("EEEE · MMM d", Locale.getDefault()).format(new Date());
    }

    private void showFullApp() {
        if (showingWeb) return;
        showingWeb = true;

        if (webView == null) {
            webView = new WebView(this);
            configureWebView();
            root.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
            progressParams.gravity = Gravity.TOP;
            root.addView(progressBar, progressParams);

            errorView = createErrorView();
            errorView.setVisibility(View.GONE);
            root.addView(errorView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        webView.setVisibility(View.VISIBLE);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (errorView != null) errorView.setVisibility(View.GONE);
        loadHome();
    }

    private void hideFullApp() {
        showingWeb = false;
        if (webView != null) webView.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.GONE);
        renderTodayScreen();
        TbftWidgetProvider.requestImmediateSync(getApplicationContext());
    }

    private View createErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(32), dp(32), dp(32), dp(32));
        box.setBackgroundColor(PAPER);

        TextView message = new TextView(this);
        message.setText("Couldn't reach TBFT.");
        message.setTextColor(INK);
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        box.addView(message);

        Button retry = minimalButton("Retry");
        retry.setOnClickListener(v -> loadHome());
        box.addView(retry);

        Button back = minimalButton("Back to Today");
        back.setOnClickListener(v -> hideFullApp());
        box.addView(back);
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
        settings.setUserAgentString(settings.getUserAgentString() + " TBFT-Android/2.1");

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
                if ("https".equalsIgnoreCase(scheme) && HOME_HOST.equalsIgnoreCase(host)) return false;
                if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (errorView != null) errorView.setVisibility(View.GONE);
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
                if (progressBar == null) return;
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
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
        if (!hasNetwork()) {
            showError();
            return;
        }
        webView.loadUrl(HOME_URL);
    }

    private void showError() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        widgetPrefs.registerOnSharedPreferenceChangeListener(prefsListener);
        renderTodayScreen();
        TbftWidgetProvider.requestImmediateSync(getApplicationContext());
    }

    @Override
    protected void onPause() {
        widgetPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (showingWeb) {
            if (webView != null && webView.canGoBack()) webView.goBack();
            else hideFullApp();
        } else {
            super.onBackPressed();
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
