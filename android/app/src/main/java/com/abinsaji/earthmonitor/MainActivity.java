package com.abinsaji.earthmonitor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL = "http://192.168.1.25:81/stream";
    private EditText urlInput;
    private WebView webView;
    private TextView status;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,17,31));
        getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        prefs = getSharedPreferences("earth_monitor", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));
        root.setBackgroundColor(Color.rgb(8,17,31));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("◎  EARTH MONITOR");
        title.setTextColor(Color.rgb(99,230,190));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(4), 0, dp(10));
        root.addView(title);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.LTGRAY);
        urlInput.setHint("http://192.168.1.25:81/stream");
        urlInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI );
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setText(prefs.getString("url", DEFAULT_URL));
        root.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = makeButton("Connect");
        Button refresh = makeButton("Refresh");
        Button stop = makeButton("Stop");
        buttons.addView(connect, new LinearLayout.LayoutParams(0, dp(46), 1));
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, dp(46), 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(buttons);
        status = new TextView(this);
        status.setTextColor(Color.rgb(154,171,194));
        status.setText("Ready · Same Wi-Fi as camera required");
        status.setTextSize(12);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                status.setText("Connecting to camera…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("Stream page loaded");
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Could not load stream · Check URL and Wi-Fi");
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        connect.setOnClickListener(v -> loadStream());
        refresh.setOnClickListener(v -> loadStream());
        stop.setOnClickListener(v -> {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            status.setText("Stream stopped");
        });
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadStream(); return true;
            }
            return false;
        });
        loadStream();
    }

    private void loadStream() {
        String url = urlInput.getText().toString().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            status.setText("Enter a valid http:// or https:// URL");
            return;
        }
        prefs.edit().putString("url", url).apply();
        webView.loadUrl(url);
    }
    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
