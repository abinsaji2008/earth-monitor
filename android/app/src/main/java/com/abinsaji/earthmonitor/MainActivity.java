package com.abinsaji.earthmonitor;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL = "http://192.168.1.25:81/stream";
    private static final int REQUEST_WRITE_STORAGE = 204;
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

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("◎  EARTH MONITOR");
        title.setTextColor(Color.rgb(99,230,190));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(4), 0, dp(10));
        headerRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button editButton = makeButton("Edit");
        editButton.setTextColor(Color.rgb(99,230,190));
        headerRow.addView(editButton, new LinearLayout.LayoutParams(dp(78), dp(40)));
        root.addView(headerRow);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.LTGRAY);
        urlInput.setHint(DEFAULT_URL);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setText(prefs.getString("url", DEFAULT_URL));
        root.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(52)));
        urlInput.setVisibility(View.GONE);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = makeButton("Connect");
        Button refresh = makeButton("Refresh");
        Button stop = makeButton("Stop");
        buttons.addView(connect, new LinearLayout.LayoutParams(0, dp(42), 1));
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, dp(42), 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(buttons);
        buttons.setVisibility(View.GONE);

        editButton.setOnClickListener(v -> {
            boolean showEditor = urlInput.getVisibility() != View.VISIBLE;
            urlInput.setVisibility(showEditor ? View.VISIBLE : View.GONE);
            buttons.setVisibility(showEditor ? View.VISIBLE : View.GONE);
            editButton.setText(showEditor ? "Done" : "Edit");
        });

        LinearLayout captureRow = new LinearLayout(this);
        captureRow.setOrientation(LinearLayout.HORIZONTAL);
        Button saveImage = makeButton("Save Image");
        saveImage.setTextColor(Color.rgb(8,17,31));
        saveImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(99,230,190)));
        captureRow.addView(saveImage, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(captureRow);

        status = new TextView(this);
        status.setTextColor(Color.rgb(154,171,194));
        status.setText("Ready · Same Wi-Fi as camera required");
        status.setTextSize(12);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        LinearLayout ledPanel = new LinearLayout(this);
        ledPanel.setOrientation(LinearLayout.VERTICAL);
        ledPanel.setPadding(0, dp(2), 0, dp(2));

        LinearLayout ledHeader = new LinearLayout(this);
        ledHeader.setOrientation(LinearLayout.HORIZONTAL);
        ledHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView ledTitle = new TextView(this);
        ledTitle.setText("Camera LED");
        ledTitle.setTextColor(Color.WHITE);
        ledTitle.setTextSize(14);
        ledHeader.addView(ledTitle, new LinearLayout.LayoutParams(0, dp(36), 1));

        Switch ledSwitch = new Switch(this);
        ledSwitch.setTextOn("On");
        ledSwitch.setTextOff("Off");
        ledSwitch.setTextColor(Color.rgb(154,171,194));
        ledSwitch.setChecked(false);
        ledHeader.addView(ledSwitch, new LinearLayout.LayoutParams(-2, dp(40)));
        ledPanel.addView(ledHeader);

        TextView brightnessLabel = new TextView(this);
        brightnessLabel.setText("Brightness: 128 / 255");
        brightnessLabel.setTextColor(Color.rgb(154,171,194));
        brightnessLabel.setTextSize(12);
        ledPanel.addView(brightnessLabel);

        SeekBar brightnessSeek = new SeekBar(this);
        brightnessSeek.setMax(255);
        brightnessSeek.setProgress(128);
        if (Build.VERSION.SDK_INT >= 21) {
            brightnessSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(99,230,190)));
            brightnessSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(99,230,190)));
        }
        ledPanel.addView(brightnessSeek, new LinearLayout.LayoutParams(-1, dp(34)));
        root.addView(ledPanel);

        ledSwitch.setOnCheckedChangeListener((button, enabled) -> {
            ledSwitch.setText(enabled ? "On" : "Off");
            setLedIntensity(enabled ? Math.max(1, brightnessSeek.getProgress()) : 0);
        });
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessLabel.setText("Brightness: " + progress + " / 255");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (ledSwitch.isChecked()) setLedIntensity(Math.max(1, seekBar.getProgress()));
            }
        });

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
        saveImage.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT <= 28 &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            } else {
                captureAndSaveImage();
            }
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

    /*
     * Controls the built-in flash LED used by the standard ESP32-CAM
     * CameraWebServer firmware. The stream is commonly hosted on port 81,
     * while camera controls are handled by port 80.
     */
    private void setLedIntensity(int intensity) {
        final String controlUrl;
        try {
            Uri streamUri = Uri.parse(urlInput.getText().toString().trim());
            String scheme = streamUri.getScheme();
            String host = streamUri.getHost();
            if (scheme == null || host == null) throw new IllegalArgumentException("Invalid camera URL");
            int streamPort = streamUri.getPort();
            int controlPort = (streamPort == 81 || streamPort == -1)
                    ? ("https".equalsIgnoreCase(scheme) ? 443 : 80) : streamPort;
            String authorityHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
            controlUrl = new Uri.Builder()
                    .scheme(scheme)
                    .encodedAuthority(authorityHost + ":" + controlPort)
                    .appendPath("control")
                    .appendQueryParameter("var", "led_intensity")
                    .appendQueryParameter("val", String.valueOf(intensity))
                    .build().toString();
        } catch (Exception e) {
            Toast.makeText(this, "Open Edit and enter the camera stream URL first", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(controlUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setUseCaches(false);
                int responseCode = connection.getResponseCode();
                InputStream response = responseCode >= 200 && responseCode < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                if (response != null) {
                    byte[] buffer = new byte[512];
                    while (response.read(buffer) != -1) { /* drain response */ }
                    response.close();
                }
                final boolean success = responseCode >= 200 && responseCode < 300;
                runOnUiThread(() -> {
                    if (success) {
                        status.setText(intensity == 0
                                ? "Camera LED off"
                                : "Camera LED on · brightness " + intensity + "/255");
                    } else {
                        status.setText("LED control rejected · check camera firmware");
                        Toast.makeText(this, "Camera did not accept LED control. Firmware must support led_intensity.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("LED control failed · check camera Wi-Fi/firmware");
                    Toast.makeText(this, "Could not control LED. The camera must support /control?var=led_intensity.", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void captureAndSaveImage() {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            Toast.makeText(this, "Camera view is not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
            background.setColor(Color.argb(190, 0, 0, 0));
            float pad = dp(12);
            float textSize = Math.max(dp(14), bitmap.getWidth() * 0.025f);
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.WHITE);
            text.setTextSize(textSize);
            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            float textWidth = text.measureText(timestamp);
            float boxHeight = textSize + pad;
            canvas.drawRect(0, bitmap.getHeight() - boxHeight, Math.min(bitmap.getWidth(), textWidth + pad * 2), bitmap.getHeight(), background);
            canvas.drawText(timestamp, pad, bitmap.getHeight() - pad * 0.45f, text);

            String fileStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "EarthMonitor_" + fileStamp + ".jpg";
            OutputStream output = null;
            Uri savedUri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Earth Monitor");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    savedUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (savedUri != null) output = getContentResolver().openOutputStream(savedUri);
                } else {
                    File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File folder = new File(pictures, "Earth Monitor");
                    if (!folder.exists() && !folder.mkdirs()) throw new java.io.IOException("Could not create Pictures folder");
                    File file = new File(folder, fileName);
                    values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
                    savedUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    output = new FileOutputStream(file);
                }
                if (output == null) throw new java.io.IOException("Could not open gallery image output");
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw new java.io.IOException("JPEG encoding failed");
                output.flush();
                output.close();
                output = null;
                if (Build.VERSION.SDK_INT >= 29 && savedUri != null) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(savedUri, done, null, null);
                }
                Toast.makeText(this, "Image saved to Gallery with date and time", Toast.LENGTH_LONG).show();
                status.setText("Saved: " + fileName);
            } finally {
                if (output != null) try { output.close(); } catch (Exception ignored) {}
            }
            bitmap.recycle();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            status.setText("Image save failed");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            captureAndSaveImage();
        } else if (requestCode == REQUEST_WRITE_STORAGE) {
            Toast.makeText(this, "Storage permission is needed to save images on this Android version", Toast.LENGTH_LONG).show();
        }
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
