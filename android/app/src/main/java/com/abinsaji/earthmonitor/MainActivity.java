package com.abinsaji.earthmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String DEFAULT_IP = "192.168.1.25";
    private static final String STREAM_PATH = ":81/stream";
    private static final int REQUEST_WRITE_STORAGE = 204;

    private EditText ipInput;
    private WebView webView;
    private TextView status;
    private TextView distanceValue;
    private TextView scanStatus;
    private Spinner deviceSpinner;
    private Button scanButton;
    private ArrayAdapter<String> deviceAdapter;
    private SharedPreferences prefs;
    private String selectedIp = DEFAULT_IP;
    private ScheduledExecutorService distanceScheduler;
    private ExecutorService scanExecutor;
    private volatile boolean scanning = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,17,31));
        getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        prefs = getSharedPreferences("earth_monitor", MODE_PRIVATE);
        selectedIp = prefs.getString("ip", DEFAULT_IP);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(8));
        root.setBackgroundColor(Color.rgb(8,17,31));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("◎  EARTH MONITOR");
        title.setTextColor(Color.rgb(99,230,190));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(4), 0, dp(12));
        root.addView(title);

        ScrollView controlsScroll = new ScrollView(this);
        controlsScroll.setFillViewport(false);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controlsScroll.addView(controls);
        root.addView(controlsScroll, new LinearLayout.LayoutParams(-1, -2));

        TextView connectionTitle = sectionTitle("ESP32 CONNECTION");
        controls.addView(connectionTitle);

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setTextSize(15);
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.LTGRAY);
        ipInput.setHint("Enter ESP32 IP address (e.g. 192.168.1.25)");
        ipInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        ipInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        ipInput.setText(selectedIp);
        controls.addView(ipInput, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout connectionButtons = new LinearLayout(this);
        connectionButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = makeButton("Connect");
        Button scan = makeButton("Scan Local Network");
        scanButton = scan;
        connectionButtons.addView(connect, new LinearLayout.LayoutParams(0, dp(44), 1));
        connectionButtons.addView(scan, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(connectionButtons);

        scanStatus = smallText("Scan finds reachable devices on your current Wi-Fi network.");
        controls.addView(scanStatus);

        deviceSpinner = new Spinner(this);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(deviceAdapter);
        controls.addView(deviceSpinner, new LinearLayout.LayoutParams(-1, dp(44)));
        deviceSpinner.setVisibility(View.GONE);

        Button useSelected = makeButton("Use Selected IP");
        controls.addView(useSelected, new LinearLayout.LayoutParams(-1, dp(42)));
        useSelected.setVisibility(View.GONE);

        TextView distanceTitle = sectionTitle("ULTRASONIC DISTANCE");
        controls.addView(distanceTitle);
        LinearLayout distanceCard = new LinearLayout(this);
        distanceCard.setOrientation(LinearLayout.VERTICAL);
        distanceCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        distanceCard.setBackgroundColor(Color.rgb(16,35,48));
        TextView distanceCaption = smallText("Measured distance to the object in front of the ultrasonic sensor");
        distanceCard.addView(distanceCaption);
        distanceValue = new TextView(this);
        distanceValue.setText("-- cm");
        distanceValue.setTextColor(Color.rgb(99,230,190));
        distanceValue.setTextSize(34);
        distanceValue.setGravity(Gravity.CENTER);
        distanceValue.setPadding(0, dp(6), 0, dp(4));
        distanceCard.addView(distanceValue);
        controls.addView(distanceCard, new LinearLayout.LayoutParams(-1, -2));

        status = smallText("Ready · Connect your phone and ESP32 to the same Wi-Fi.");
        status.setPadding(0, dp(10), 0, dp(8));
        controls.addView(status);

        Button saveImage = makeButton("Save Camera Image");
        saveImage.setTextColor(Color.rgb(8,17,31));
        saveImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(99,230,190)));
        controls.addView(saveImage, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView cameraTitle = sectionTitle("LIVE CAMERA");
        controls.addView(cameraTitle);

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
                status.setText("Camera stream loaded · Reading distance…");
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Camera stream unavailable · Check IP and Wi-Fi");
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        connect.setOnClickListener(v -> connectToDevice());
        ipInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                connectToDevice(); return true;
            }
            return false;
        });
        scan.setOnClickListener(v -> scanLocalNetwork());
        useSelected.setOnClickListener(v -> {
            Object item = deviceSpinner.getSelectedItem();
            if (item != null) {
                String value = item.toString();
                Matcher matcher = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3})").matcher(value);
                if (matcher.find()) {
                    ipInput.setText(matcher.group(1));
                    connectToDevice();
                }
            }
        });
        saveImage.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT <= 28 &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            } else {
                captureAndSaveImage();
            }
        });

        connectToDevice();
    }

    private void connectToDevice() {
        String ip = ipInput.getText().toString().trim();
        if (!isValidIpv4(ip)) {
            status.setText("Enter a valid IPv4 address.");
            return;
        }
        selectedIp = ip;
        prefs.edit().putString("ip", ip).apply();
        String streamUrl = "http://" + ip + STREAM_PATH;
        webView.loadDataWithBaseURL(streamUrl,
                "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0;background:#000;overflow:hidden'><img src='" + streamUrl +
                "' style='display:block;width:100%;height:auto' /></body></html>",
                "text/html", "UTF-8", null);
        startDistancePolling(ip);
    }

    private void startDistancePolling(String ip) {
        if (distanceScheduler != null) distanceScheduler.shutdownNow();
        distanceScheduler = Executors.newSingleThreadScheduledExecutor();
        distanceValue.setText("Loading…");
        distanceScheduler.scheduleWithFixedDelay(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://" + ip + "/distance");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setUseCaches(false);
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                InputStream input = connection.getInputStream();
                byte[] data = new byte[2048];
                StringBuilder body = new StringBuilder();
                int count;
                while ((count = input.read(data)) != -1 && body.length() < 8192) {
                    body.append(new String(data, 0, count, java.nio.charset.StandardCharsets.UTF_8));
                }
                input.close();
                String cm = parseDistance(body.toString());
                runOnUiThread(() -> {
                    distanceValue.setText(cm == null ? "No distance data" : cm + " cm");
                    if (cm == null) status.setText("Connected, but /distance returned an unrecognised value.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    distanceValue.setText("-- cm");
                    status.setText("Distance endpoint unavailable · Expected http://" + ip + "/distance");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private String parseDistance(String response) {
        String value = response.trim();
        try {
            JSONObject json = new JSONObject(value);
            String[] keys = {"distance_cm", "distance", "cm", "ultrasonic"};
            for (String key : keys) {
                if (json.has(key) && !json.isNull(key)) return String.format(Locale.US, "%.1f", json.getDouble(key));
            }
        } catch (Exception ignored) {}
        Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(value);
        if (matcher.find()) {
            try { return String.format(Locale.US, "%.1f", Double.parseDouble(matcher.group())); }
            catch (Exception ignored) {}
        }
        return null;
    }

    private void scanLocalNetwork() {
        if (scanning) return;
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi == null || !wifi.isWifiEnabled()) {
            scanStatus.setText("Turn on Wi-Fi and connect to your local network first.");
            return;
        }
        int ipInt = wifi.getDhcpInfo().ipAddress;
        int mask = wifi.getDhcpInfo().netmask;
        if (ipInt == 0) {
            scanStatus.setText("Could not read Wi-Fi network details. Check Wi-Fi connection.");
            return;
        }
        int network = ipInt & mask;
        int broadcast = network | ~mask;
        int first = network + 1;
        int last = broadcast - 1;
        if (last - first > 1022) {
            scanStatus.setText("Network is too large to scan safely (more than 1024 addresses).");
            return;
        }
        scanning = true;
        scanStatus.setText("Scanning local network… This may take a few seconds.");
        scanButton.setEnabled(false);
        if (scanExecutor != null) scanExecutor.shutdownNow();
        scanExecutor = Executors.newFixedThreadPool(32);
        List<String> found = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(Math.max(0, last - first + 1));
        for (int candidate = first; candidate <= last; candidate++) {
            final int address = candidate;
            scanExecutor.execute(() -> {
                try {
                    String host = intToIp(address);
                    if (host.equals(intToIp(ipInt))) return;
                    int openPort = findOpenHttpPort(host);
                    if (openPort > 0) found.add(host + "  (HTTP " + openPort + ")");
                } finally {
                    latch.countDown();
                }
            });
        }
        new Thread(() -> {
            try { latch.await(25, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            scanExecutor.shutdownNow();
            runOnUiThread(() -> {
                scanning = false;
                scanButton.setEnabled(true);
                deviceAdapter.clear();
                deviceAdapter.addAll(found);
                deviceAdapter.notifyDataSetChanged();
                boolean hasResults = !found.isEmpty();
                deviceSpinner.setVisibility(hasResults ? View.VISIBLE : View.GONE);
                useSelected.setVisibility(hasResults ? View.VISIBLE : View.GONE);
                scanStatus.setText(hasResults
                        ? "Found " + found.size() + " device(s) with an HTTP service. Select one below."
                        : "No HTTP devices found. You can still enter the ESP32 IP manually.");
            });
        }).start();
    }

    private int findOpenHttpPort(String host) {
        int[] ports = {81, 80, 8080, 443};
        for (int port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 350);
                return port;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private String intToIp(int value) {
        return (value & 0xff) + "." + ((value >> 8) & 0xff) + "." +
                ((value >> 16) & 0xff) + "." + ((value >> 24) & 0xff);
    }

    private boolean isValidIpv4(String ip) {
        if (ip == null || !ip.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) return false;
        String[] parts = ip.split("\\.");
        for (String part : parts) {
            try { int n = Integer.parseInt(part); if (n < 0 || n > 255) return false; }
            catch (Exception e) { return false; }
        }
        return true;
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
                if (android.os.Build.VERSION.SDK_INT >= 29) {
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
                if (android.os.Build.VERSION.SDK_INT >= 29 && savedUri != null) {
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

    @Override protected void onDestroy() {
        super.onDestroy();
        if (distanceScheduler != null) distanceScheduler.shutdownNow();
        if (scanExecutor != null) scanExecutor.shutdownNow();
        if (webView != null) webView.destroy();
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

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(99,230,190));
        view.setTextSize(12);
        view.setPadding(0, dp(12), 0, dp(5));
        return view;
    }

    private TextView smallText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(154,171,194));
        view.setTextSize(12);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
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
