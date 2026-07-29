package com.fcm.statuschecker;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 101;

    private static final String MCS_HOST = "mtalk.google.com";
    private static final int[] MCS_PORTS = {5228, 5229, 5230, 443};
    private static final int CONNECT_TIMEOUT_MS = 4000;

    private static final int GREEN = Color.parseColor("#2E7D32");
    private static final int RED = Color.parseColor("#C62828");
    private static final int AMBER = Color.parseColor("#EF6C00");
    private static final int TEXT = Color.parseColor("#212121");
    private static final int MUTED = Color.parseColor("#616161");

    private LinearLayout content;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // Server probe state.
    private int probeGeneration = 0;
    private volatile boolean probing = false;
    private volatile boolean probeDone = false;
    private volatile String mcsIp = null;
    private volatile int usedPort = -1;
    private volatile int usedPortMs = -1;

    // Live "last heartbeat" view + updaters.
    private TextView lastSentValue;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateLastSent();
            uiHandler.postDelayed(this, 1000);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> { if (HeartbeatManager.KEY_LAST_SENT.equals(key)) updateLastSent(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#FAFAFA"));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);

        setContentView(scroll);
        startProbe();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        HeartbeatManager.prefs(this).registerOnSharedPreferenceChangeListener(prefListener);
        uiHandler.removeCallbacks(ticker);
        uiHandler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        HeartbeatManager.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        uiHandler.removeCallbacks(ticker);
    }

    // ---------- Server probe ----------

    private void startProbe() {
        final int gen = ++probeGeneration;
        probing = true;
        probeDone = false;
        usedPort = -1;
        usedPortMs = -1;
        mcsIp = null;
        new Thread(() -> {
            try {
                mcsIp = InetAddress.getByName(MCS_HOST).getHostAddress();
            } catch (Exception e) {
                mcsIp = null;
            }
            for (int port : MCS_PORTS) {
                long t0 = SystemClock.elapsedRealtime();
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(MCS_HOST, port), CONNECT_TIMEOUT_MS);
                    usedPort = port;
                    usedPortMs = (int) (SystemClock.elapsedRealtime() - t0);
                    break;
                } catch (Exception e) {
                    // try next port
                }
            }
            probing = false;
            probeDone = true;
            if (gen == probeGeneration) {
                runOnUiThread(this::render);
            }
        }, "fcm-probe").start();
    }

    // ---------- Render ----------

    private void render() {
        content.removeAllViews();
        lastSentValue = null;

        addTitle("FCM Status Checker");
        addSubtitle("Firebase Cloud Messaging connection diagnostics");
        addSpacer(dp(8));

        boolean playOk = isPlayServicesOk();
        boolean online = isOnline();
        boolean serverReachable = usedPort > 0;

        addVerdict(playOk, online, serverReachable);
        addSpacer(dp(12));

        renderHeartbeatSection();
        addSpacer(dp(12));

        // --- FCM server probe ---
        addSectionHeader("FCM Server Connection");
        addSection("Tests whether this device can reach Google's FCM server (" + MCS_HOST
                + ") and which port it uses. First reachable of 5228, 5229, 5230, 443.");
        addRow("Host", mcsIp != null ? MCS_HOST + " / " + mcsIp : MCS_HOST, MUTED);
        if (probing && !probeDone) {
            addRow("Status", "Checking…", MUTED);
        } else if (probeDone) {
            addRow("Server", serverReachable ? "Reachable" : "Unreachable", serverReachable ? GREEN : RED);
            addRow("Port used", serverReachable ? usedPort + "  (" + usedPortMs + " ms)" : "None reachable",
                    serverReachable ? GREEN : RED);
        }
        addButton(probing ? "Checking…" : "Re-check now", v -> { startProbe(); render(); });

        addSpacer(dp(12));

        // --- Google Play Services ---
        addSectionHeader("Google Play Services");
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        addRow("Availability", playStatusText(code), code == ConnectionResult.SUCCESS ? GREEN : RED);
        addRow("Play Services version", playServicesVersion(), MUTED);

        addSpacer(dp(12));

        // --- Notifications ---
        addSectionHeader("Notifications");
        boolean notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        addRow("Notifications enabled", notifEnabled ? "Yes" : "No (blocked)", notifEnabled ? GREEN : RED);
        if (Build.VERSION.SDK_INT >= 33) {
            boolean granted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            addRow("POST_NOTIFICATIONS", granted ? "Granted" : "Not granted", granted ? GREEN : AMBER);
            if (!granted) {
                addButton("Request notification permission", v ->
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS));
            }
        }

        addSpacer(dp(12));

        // --- Network / Device ---
        addSectionHeader("Network & Device");
        addRow("Internet", online ? "Online (" + networkType() + ")" : "Offline", online ? GREEN : RED);
        addRow("Model", Build.MANUFACTURER + " " + Build.MODEL, MUTED);
        addRow("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", MUTED);

        addSpacer(dp(16));
        addButton("Refresh", v -> render());
        addSpacer(dp(8));
        addFootnote("Heartbeats use undocumented Play Services broadcasts (verified working on this "
                + "device). In deep sleep, intervals under ~9 min may be stretched by Doze; keep the app "
                + "exempt from battery optimization for best results. This app uses no Firebase project.");
    }

    // ---------- Heartbeat section ----------

    private void renderHeartbeatSection() {
        boolean running = HeartbeatManager.isEnabled(this);

        addSectionHeader("FCM Keep-Alive (Heartbeat)");
        addSection("Automatically nudges Google Play Services to send an FCM heartbeat on the interval "
                + "you choose, keeping the connection fresh (helps when a VPN or long heartbeat delays "
                + "notifications). Runs in the background as an ongoing notification.");

        addRow("Status", running ? "Running" : "Stopped", running ? GREEN : MUTED);

        // Interval spinner row.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView l = new TextView(this);
        l.setText("Interval (minutes)");
        l.setTextColor(TEXT);
        l.setTextSize(14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        Spinner spinner = new Spinner(this);
        String[] labels = new String[HeartbeatManager.INTERVALS_MIN.length];
        int selectedIndex = 0;
        int current = HeartbeatManager.getIntervalMin(this);
        for (int i = 0; i < HeartbeatManager.INTERVALS_MIN.length; i++) {
            labels[i] = String.valueOf(HeartbeatManager.INTERVALS_MIN[i]);
            if (HeartbeatManager.INTERVALS_MIN[i] == current) selectedIndex = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(GREEN);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int chosen = HeartbeatManager.INTERVALS_MIN[position];
                if (chosen != HeartbeatManager.getIntervalMin(MainActivity.this)) {
                    HeartbeatManager.setIntervalMin(MainActivity.this, chosen);
                    if (HeartbeatManager.isEnabled(MainActivity.this)) {
                        HeartbeatManager.cancelAlarm(MainActivity.this);
                        HeartbeatManager.scheduleNext(MainActivity.this);
                        HeartbeatManager.updateNotification(MainActivity.this);
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        row.addView(spinner);
        content.addView(row);
        addDivider();

        lastSentValue = addValueRow("Last heartbeat sent", "…", MUTED);
        updateLastSent();

        addButton(running ? "Stop keep-alive" : "Start keep-alive", v -> {
            if (HeartbeatManager.isEnabled(this)) {
                HeartbeatManager.setEnabled(this, false);
                HeartbeatManager.cancelAlarm(this);
                stopService(new Intent(this, HeartbeatService.class));
            } else {
                HeartbeatManager.setEnabled(this, true);
                ContextCompat.startForegroundService(this, new Intent(this, HeartbeatService.class));
            }
            render();
        });

        addButton("Send heartbeat now", v -> {
            HeartbeatManager.sendHeartbeat(this);
            updateLastSent();
            Toast.makeText(this, "Heartbeat broadcast sent.", Toast.LENGTH_SHORT).show();
        });

        addButton("Open FCM Diagnostics (Google Play Services)", v -> openGcmDiagnostics());

        if (!isIgnoringBatteryOptimizations()) {
            addButton("Exempt from battery optimization", v -> requestIgnoreBatteryOptimizations());
        }
    }

    private void updateLastSent() {
        if (lastSentValue == null) return;
        long t = HeartbeatManager.getLastSent(this);
        if (t <= 0) {
            lastSentValue.setText("never");
            lastSentValue.setTextColor(MUTED);
            return;
        }
        long agoSec = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        String ago = agoSec < 60 ? agoSec + "s ago" : (agoSec / 60) + "m " + (agoSec % 60) + "s ago";
        lastSentValue.setText(HeartbeatManager.formatTime(t) + "  (" + ago + ")");
        lastSentValue.setTextColor(GREEN);
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    @SuppressWarnings("BatteryLife")
    private void requestIgnoreBatteryOptimizations() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "Open Settings → Battery to exempt this app.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openGcmDiagnostics() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.google.android.gms",
                    "com.google.android.gms.gcm.GcmDiagnostics"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception e) {
            // fall back to dialer code
        }
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("secret code", "*#*#426#*#*"));
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
            Toast.makeText(this, "Couldn't open it directly. Code copied — type *#*#426#*#* in the dialer.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e2) {
            Toast.makeText(this, "Could not open FCM diagnostics on this device.", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Checks ----------

    private boolean isPlayServicesOk() {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
                == ConnectionResult.SUCCESS;
    }

    private String playStatusText(int code) {
        switch (code) {
            case ConnectionResult.SUCCESS: return "Available and up to date";
            case ConnectionResult.SERVICE_MISSING: return "Missing on this device";
            case ConnectionResult.SERVICE_UPDATING: return "Currently updating";
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED: return "Update required";
            case ConnectionResult.SERVICE_DISABLED: return "Disabled";
            case ConnectionResult.SERVICE_INVALID: return "Invalid / corrupt";
            default: return "Unavailable (code " + code + ")";
        }
    }

    private String playServicesVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return pi.versionName != null ? pi.versionName : "unknown";
        } catch (PackageManager.NameNotFoundException e) {
            return "not installed";
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private String networkType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "unknown";
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return "unknown";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Cellular";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        return "other";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    // ---------- UI helpers ----------

    private void addVerdict(boolean playOk, boolean online, boolean serverReachable) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        card.setPadding(p, p, p, p);

        boolean canConnect = playOk && online && (serverReachable || !probeDone);
        card.setBackgroundColor(canConnect ? Color.parseColor("#E8F5E9") : Color.parseColor("#FFEBEE"));

        TextView t = new TextView(this);
        t.setText(canConnect ? "FCM should be able to connect" : "FCM may not be able to connect");
        t.setTextColor(canConnect ? GREEN : RED);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView d = new TextView(this);
        String reason;
        if (!playOk) reason = "Google Play Services is unavailable, so FCM has no transport.";
        else if (!online) reason = "The device is offline, so FCM cannot reach Google's servers.";
        else if (probeDone && !serverReachable) reason = "Google's FCM server is not reachable on any "
                + "port (5228/5229/5230/443) — a firewall or VPN may be blocking it.";
        else reason = "Play Services is available, the device is online, and the FCM server is reachable.";
        d.setText(reason);
        d.setTextColor(TEXT);
        d.setTextSize(13);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d);

        content.addView(card);
    }

    private void addTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(t);
    }

    private void addSubtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(13);
        content.addView(t);
    }

    private void addSectionHeader(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase());
        t.setTextColor(Color.parseColor("#1565C0"));
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        content.addView(t);
    }

    private void addSection(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(12);
        t.setPadding(0, 0, 0, dp(6));
        content.addView(t);
    }

    private void addRow(String label, String value, int valueColor) {
        addValueRow(label, value, valueColor);
    }

    private TextView addValueRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(TEXT);
        l.setTextSize(14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(valueColor);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
        row.addView(v);

        content.addView(row);
        addDivider();
        return v;
    }

    private void addDivider() {
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#E0E0E0"));
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        content.addView(div);
    }

    private void addButton(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        content.addView(b);
    }

    private void addFootnote(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(11);
        t.setPadding(0, dp(8), 0, 0);
        content.addView(t);
    }

    private void addSpacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        content.addView(v);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
