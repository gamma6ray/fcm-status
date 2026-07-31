package com.fcm.statuschecker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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

    private static final int BG = Color.rgb(5, 6, 9);
    private static final int CARD = Color.rgb(20, 22, 27);
    private static final int CARD_BORDER = Color.rgb(57, 60, 70);
    private static final int WHITE = Color.rgb(248, 248, 250);
    private static final int MUTED = Color.rgb(166, 168, 180);
    private static final int BLUE = Color.rgb(35, 139, 255);
    private static final int MAGENTA = Color.rgb(224, 38, 245);
    private static final int GREEN = Color.rgb(70, 235, 102);
    private static final int RED = Color.rgb(255, 83, 99);
    private static final int PURPLE = Color.rgb(190, 77, 255);

    private LinearLayout content;
    private FrameLayout rootFrame;
    private LinearLayout bottomNavigation;
    private boolean settingsPage;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private int probeGeneration;
    private volatile boolean probing;
    private volatile boolean probeDone;
    private volatile String mcsIp;
    private volatile int usedPort = -1;
    private volatile int usedPortMs = -1;
    private TextView lastSentValue;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateLastSent();
            uiHandler.postDelayed(this, 1000);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> { if (HeartbeatManager.KEY_LAST_SENT.equals(key)) updateLastSent(); };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildRoot();
        startProbe();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
        HeartbeatManager.prefs(this).registerOnSharedPreferenceChangeListener(prefListener);
        uiHandler.removeCallbacks(ticker);
        uiHandler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        HeartbeatManager.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        uiHandler.removeCallbacks(ticker);
    }

    @Override public void onBackPressed() {
        if (settingsPage) {
            settingsPage = false;
            render();
        } else {
            super.onBackPressed();
        }
    }

    private void buildRoot() {
        rootFrame = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(110));
        scroll.addView(content);
        rootFrame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootFrame);
    }

    private void startProbe() {
        final int generation = ++probeGeneration;
        probing = true;
        probeDone = false;
        usedPort = -1;
        usedPortMs = -1;
        mcsIp = null;
        new Thread(() -> {
            try { mcsIp = InetAddress.getByName(MCS_HOST).getHostAddress(); }
            catch (Exception ignored) { mcsIp = null; }
            for (int port : MCS_PORTS) {
                long started = SystemClock.elapsedRealtime();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(MCS_HOST, port), CONNECT_TIMEOUT_MS);
                    usedPort = port;
                    usedPortMs = (int) (SystemClock.elapsedRealtime() - started);
                    break;
                } catch (Exception ignored) { }
            }
            probing = false;
            probeDone = true;
            if (generation == probeGeneration) runOnUiThread(this::render);
        }, "fcm-probe").start();
    }

    private void render() {
        content.removeAllViews();
        lastSentValue = null;
        if (settingsPage) renderSettings(); else renderStatus();
    }

    private void renderStatus() {
        boolean playOk = isPlayServicesOk();
        boolean online = isOnline();
        boolean reachable = !probeDone || (playOk && online && usedPort > 0);
        int stateColor = reachable ? GREEN : RED;

        FrameLayout banner = new FrameLayout(this);
        banner.setBackgroundColor(BG);
        banner.addView(new NeonBanner(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        LinearLayout bannerContent = new LinearLayout(this);
        bannerContent.setOrientation(LinearLayout.VERTICAL);
        bannerContent.setPadding(0, dp(16), 0, 0);
        TextView title = text("FCM is\n" + (reachable ? "reachable" : "unreachable"), WHITE, 36, true);
        title.setLetterSpacing(0.01f);
        bannerContent.addView(title);

        LinearLayout subtitleRow = new LinearLayout(this);
        subtitleRow.setGravity(Gravity.CENTER_VERTICAL);
        subtitleRow.setPadding(0, dp(5), 0, 0);
        ImageView statusLight = new ImageView(this);
        statusLight.setImageResource(reachable ? R.drawable.ic_status_green : R.drawable.ic_status_red);
        statusLight.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        lightLp.setMargins(0, 0, dp(8), 0);
        subtitleRow.addView(statusLight, lightLp);
        subtitleRow.addView(text("Google Play Services + FCM server", MUTED, 13, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton refresh = new ImageButton(this);
        refresh.setImageResource(R.drawable.ic_recheck);
        refresh.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        refresh.setBackgroundColor(Color.TRANSPARENT);
        refresh.setPadding(0, 0, 0, 0);
        refresh.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        refresh.setContentDescription("Recheck connection");
        refresh.setOnClickListener(v -> { startProbe(); render(); });
        subtitleRow.addView(refresh);
        bannerContent.addView(subtitleRow);
        banner.addView(bannerContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(banner);

        LinearLayout metrics = card(14);
        metrics.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout metricRow = new LinearLayout(this);
        metricRow.setGravity(Gravity.CENTER_VERTICAL);
        metricRow.setMinimumHeight(dp(60));
        metricRow.addView(metric("SERVER", probing ? "Checking" : (usedPort > 0 ? "Reachable" : "Unreachable"),
                probing ? MUTED : stateColor), weight(1));
        addMetricDivider(metricRow);
        metricRow.addView(metric("PORT", usedPort > 0 ? String.valueOf(usedPort) : "—", MUTED), weight(1));
        addMetricDivider(metricRow);
        metricRow.addView(metric("NETWORK", networkType(), MUTED), weight(1));
        metrics.addView(metricRow);
        content.addView(metrics);

        addSpacer(22);
        addGradientButton("Send heartbeat now", v -> {
            HeartbeatManager.sendHeartbeat(this);
            updateLastSent();
            Toast.makeText(this, "Heartbeat sent", Toast.LENGTH_SHORT).show();
        });
        addSpacer(22);

        LinearLayout keepAlive = card(16);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Keep-alive", WHITE, 21, true), weight(1));
        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setChecked(HeartbeatManager.isEnabled(this));
        toggle.setText("ON");
        toggle.setTextColor(WHITE);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        toggle.setButtonTintList(null);
        toggle.setOnCheckedChangeListener((button, checked) -> setKeepAlive(checked));
        heading.addView(toggle);
        keepAlive.addView(heading);
        addDividerTo(keepAlive);

        LinearLayout intervalRow = settingRow(R.drawable.ic_clock, "Heartbeat interval", "", false, false);
        ((LinearLayout) intervalRow.getChildAt(intervalRow.getChildCount() - 1)).addView(intervalSelector());
        keepAlive.addView(intervalRow);
        addDividerTo(keepAlive);
        LinearLayout lastRow = settingRow(R.drawable.ic_heartbeat, "Last heartbeat", "", false, false);
        lastSentValue = text("never", MUTED, 14, true);
        lastSentValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ((LinearLayout) lastRow.getChildAt(lastRow.getChildCount() - 1)).addView(lastSentValue,
                new LinearLayout.LayoutParams(dp(250), dp(42)));
        keepAlive.addView(lastRow);
        updateLastSent();
        content.addView(keepAlive);
        addSpacer(14);
        bottomNav(false);
    }

    private void renderSettings() {
        addTopBar("SETTINGS", true);
        addSpacer(18);
        content.addView(text("Keep FCM\nreliable", WHITE, 32, true));
        content.addView(text("Tune background behavior for your device", MUTED, 14, false));
        addSpacer(18);

        LinearLayout access = card(16);
        access.addView(text("Background access", WHITE, 20, true));
        access.addView(settingRow(R.drawable.ic_battery, "Battery optimization",
                isIgnoringBatteryOptimizations() ? "Exempt" : "Open"), full());
        addDividerTo(access);
        access.addView(settingRow(R.drawable.ic_autostart, "Autostart guidance", "Open"), full());
        addDividerTo(access);
        access.addView(settingRow(R.drawable.ic_notification, "Post notification",
                notificationsGranted() ? "Granted" : "Open"), full());
        addDividerTo(access);
        access.addView(settingRow(R.drawable.ic_lock, "Lock in recents guidance", "View"), full());
        content.addView(access);
        addSpacer(16);

        LinearLayout about = card(16);
        about.addView(text("About", WHITE, 20, true));
        about.addView(settingRow(R.drawable.ic_info, "Version", appVersion()), full());
        about.setOnClickListener(v -> showAbout());
        content.addView(about);
        addSpacer(24);
        bottomNav(true);
    }

    private void addTopBar(String label, boolean back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(text(label, WHITE, 15, true), weight(1));
        if (back) {
            ImageButton action = new ImageButton(this);
            action.setImageResource(R.drawable.ic_back);
            action.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            action.setBackgroundColor(Color.TRANSPARENT);
            action.setPadding(dp(8), dp(8), dp(8), dp(8));
            action.setContentDescription("Back");
            action.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
            action.setOnClickListener(v -> { settingsPage = false; render(); });
            bar.addView(action);
        }
        content.addView(bar);
    }

    private void bottomNav(boolean settingsSelected) {
        if (bottomNavigation != null) rootFrame.removeView(bottomNavigation);
        LinearLayout nav = card(18);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(8), dp(8), dp(6));
        LinearLayout status = navButton(R.drawable.ic_status_wave, "Status", !settingsSelected);
        LinearLayout settings = navButton(R.drawable.ic_settings, "Settings", settingsSelected);
        status.setOnClickListener(v -> { settingsPage = false; render(); });
        settings.setOnClickListener(v -> { settingsPage = true; render(); });
        nav.addView(status, weight(1));
        nav.addView(settings, weight(1));
        bottomNavigation = nav;
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        navLp.setMargins(dp(18), 0, dp(18), dp(16));
        rootFrame.addView(bottomNavigation, navLp);
    }

    private LinearLayout navButton(int iconRes, String label, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(3), 0, dp(3));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setAlpha(selected ? 1f : 0.55f);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (selected) {
            icon.setBackground(round(0xff0b1f3a, Color.TRANSPARENT, 18));
            icon.setPadding(dp(7), dp(4), dp(7), dp(4));
        }
        item.addView(icon, new LinearLayout.LayoutParams(selected ? dp(44) : dp(28), dp(28)));
        TextView title = text(label, selected ? BLUE : MUTED, 12, selected);
        title.setGravity(Gravity.CENTER);
        item.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)));
        item.setBackgroundColor(Color.TRANSPARENT);
        return item;
    }

    private void setKeepAlive(boolean enabled) {
        if (enabled == HeartbeatManager.isEnabled(this)) return;
        HeartbeatManager.setEnabled(this, enabled);
        if (enabled) {
            HeartbeatManager.scheduleNext(this);
            try { ContextCompat.startForegroundService(this, new Intent(this, HeartbeatService.class)); }
            catch (Exception e) { Toast.makeText(this, "Background service could not start", Toast.LENGTH_LONG).show(); }
        } else {
            HeartbeatManager.cancelAlarm(this);
            stopService(new Intent(this, HeartbeatService.class));
        }
        render();
    }

    private View intervalSelector() {
        LinearLayout selector = new LinearLayout(this);
        selector.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selector.setClickable(true);
        selector.setFocusable(true);
        TextView value = text(HeartbeatManager.getIntervalMin(this) + " minutes", WHITE, 14, false);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selector.addView(value, new LinearLayout.LayoutParams(0, dp(42), 1f));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        selector.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(42)));
        selector.setOnClickListener(v -> showIntervalPicker());
        selector.setContentDescription("Heartbeat interval");
        selector.setLayoutParams(new LinearLayout.LayoutParams(dp(250), dp(42)));
        return selector;
    }

    private void showIntervalPicker() {
        String[] labels = new String[HeartbeatManager.INTERVALS_MIN.length];
        int selected = 0;
        int current = HeartbeatManager.getIntervalMin(this);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = HeartbeatManager.INTERVALS_MIN[i] + " minutes";
            if (HeartbeatManager.INTERVALS_MIN[i] == current) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Heartbeat interval")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    int chosen = HeartbeatManager.INTERVALS_MIN[which];
                    if (chosen != HeartbeatManager.getIntervalMin(MainActivity.this)) {
                        HeartbeatManager.setIntervalMin(MainActivity.this, chosen);
                        if (HeartbeatManager.isEnabled(MainActivity.this)) {
                            HeartbeatManager.cancelAlarm(MainActivity.this);
                            HeartbeatManager.scheduleNext(MainActivity.this);
                            HeartbeatManager.updateNotification(MainActivity.this);
                        }
                        render();
                    }
                    dialog.dismiss();
                }).show();
    }

    private LinearLayout settingRow(int iconRes, String label, String value) {
        return settingRow(iconRes, label, value, true);
    }

    private LinearLayout settingRow(int iconRes, String label, String value, boolean showChevron) {
        return settingRow(iconRes, label, value, showChevron, true);
    }

    private LinearLayout settingRow(int iconRes, String label, String value,
                                    boolean showChevron, boolean showIcon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (label.equals("Last heartbeat")) iconView.setTranslationX(-dp(4));
        if (showIcon) row.addView(iconView, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout middle = new LinearLayout(this);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        middle.setMinimumHeight(dp(42));
        TextView labelView = text(label, WHITE, 14, false);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        middle.addView(labelView, weight(1));
        if (!value.isEmpty()) middle.addView(text(value, value.equals("Exempt") || value.equals("Granted") ? GREEN : BLUE, 14, true));
        if (showChevron) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron);
            arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            middle.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(38)));
        }
        row.addView(middle, weight(1));
        if (label.equals("Battery optimization")) row.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
        if (label.equals("Post notification")) row.setOnClickListener(v -> requestNotificationPermission());
        if (label.contains("Autostart")) row.setOnClickListener(v -> showGuidance("Autostart guidance", "Open App info → Autostart and allow FCM Status. vivo may call this Autostart or Background start."));
        if (label.contains("Lock")) row.setOnClickListener(v -> showGuidance("Lock in recents", "Open recent apps, find FCM Status, then tap the lock icon. Also set Battery to Unrestricted if available."));
        return row;
    }

    private void updateLastSent() {
        if (lastSentValue == null) return;
        long time = HeartbeatManager.getLastSent(this);
        if (time <= 0) { lastSentValue.setText("never"); lastSentValue.setTextColor(MUTED); return; }
        long seconds = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        String ago = seconds < 60 ? seconds + "s ago" : (seconds / 60) + "m " + (seconds % 60) + "s ago";
        lastSentValue.setText(HeartbeatManager.formatTime(time) + "  (" + ago + ")");
        lastSentValue.setTextColor(GREEN);
    }

    private void addGradientButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(gradient(0xff168fff, 0xffe52bf5, 100));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = full();
        lp.height = dp(58);
        lp.setMargins(dp(26), 0, dp(26), 0);
        content.addView(b, lp);
    }

    private TextView text(String value, int color, float size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        v.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return v;
    }

    private LinearLayout metric(String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView labelView = text(label, MUTED, 11, false);
        labelView.setGravity(Gravity.CENTER);
        box.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = text(value, valueColor, 15, true);
        valueView.setGravity(Gravity.CENTER);
        box.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void addMetricDivider(LinearLayout row) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(57, 60, 70));
        row.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(42)));
    }

    private LinearLayout card(int radius) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackground(round(CARD, CARD_BORDER, radius));
        v.setLayoutParams(full());
        return v;
    }


    private void addDividerTo(LinearLayout parent) {
        View d = new View(this);
        d.setBackgroundColor(Color.rgb(48, 50, 58));
        parent.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addSpacer(int dp) { View v = new View(this); content.addView(v, new LinearLayout.LayoutParams(1, this.dp(dp))); }

    private boolean isPlayServicesOk() { return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS; }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private String networkType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities c = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (c == null) return "Offline";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Cellular";
        return "Online";
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean notificationsGranted() {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }

    @SuppressWarnings("BatteryLife")
    private void requestIgnoreBatteryOptimizations() {
        try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    private void showGuidance(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }

    private void showAbout() {
        showGuidance("FCM Status Checker", "Version " + appVersion() + "\n\nA lightweight FCM connectivity and keep-alive helper. No Firebase project is required.");
    }

    private String appVersion() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName != null ? p.versionName : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        render();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class NeonBanner extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        NeonBanner(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            Path arc = new Path();
            arc.moveTo(w * 0.36f, h * 0.40f);
            arc.cubicTo(w * 0.58f, -h * 0.02f, w * 0.88f, h * 0.02f,
                    w * 1.18f, h * 0.78f);

            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(17 * density);
            glow.setAlpha(80);
            glow.setShader(new LinearGradient(w * 0.35f, 0, w, h,
                    Color.rgb(20, 110, 255), Color.rgb(225, 35, 245), Shader.TileMode.CLAMP));
            glow.setShadowLayer(26 * density, 0, 0, Color.rgb(50, 95, 255));
            canvas.drawPath(arc, glow);

            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.8f * density);
            line.setShader(new LinearGradient(w * 0.4f, 0, w, h,
                    Color.rgb(30, 125, 255), Color.rgb(230, 35, 245), Shader.TileMode.CLAMP));
            line.setShadowLayer(8 * density, 0, 0, Color.rgb(102, 60, 255));
            canvas.drawPath(arc, line);
        }
    }
}
