package com.fcm.statuschecker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.Locale;

/**
 * Central logic for the FCM keep-alive heartbeat: preferences, the AlarmManager schedule,
 * sending the heartbeat broadcasts, and the ongoing notification. Shared by the Activity,
 * the foreground service, and the alarm/boot receivers.
 */
public final class HeartbeatManager {

    public static final int[] INTERVALS_MIN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    public static final int DEFAULT_INTERVAL_MIN = 5;

    private static final String PREFS = "heartbeat_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_INTERVAL = "interval_min";
    static final String KEY_LAST_SENT = "last_sent";
    static final String KEY_LAST_PROBE_OK = "last_probe_ok";
    static final String KEY_LAST_PROBE_PORT = "last_probe_port";
    static final String KEY_LAST_PROBE_AT = "last_probe_at";

    public static final String CHANNEL_ID = "fcm_heartbeat";
    public static final int NOTIF_ID = 42;

    // Undocumented broadcasts that Google Play Services still honours (verified on Android 15).
    private static final String ACTION_GTALK = "com.google.android.intent.action.GTALK_HEARTBEAT";
    private static final String ACTION_MCS = "com.google.android.intent.action.MCS_HEARTBEAT";
    private static final int ALARM_REQ = 7001;

    private HeartbeatManager() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) { return prefs(c).getBoolean(KEY_ENABLED, false); }
    public static void setEnabled(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_ENABLED, v).apply(); }
    public static int getIntervalMin(Context c) { return prefs(c).getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MIN); }
    public static void setIntervalMin(Context c, int m) { prefs(c).edit().putInt(KEY_INTERVAL, m).apply(); }
    public static long getLastSent(Context c) { return prefs(c).getLong(KEY_LAST_SENT, 0L); }
    static boolean getLastProbeOk(Context c) { return prefs(c).getBoolean(KEY_LAST_PROBE_OK, false); }
    static int getLastProbePort(Context c) { return prefs(c).getInt(KEY_LAST_PROBE_PORT, -1); }
    private static void setLastSent(Context c, long t) { prefs(c).edit().putLong(KEY_LAST_SENT, t).apply(); }

    /** Fire the heartbeat broadcasts and record the timestamp. */
    public static void sendHeartbeat(Context c) {
        try { c.sendBroadcast(new Intent(ACTION_GTALK)); } catch (Exception ignored) {}
        try { c.sendBroadcast(new Intent(ACTION_MCS)); } catch (Exception ignored) {}
        setLastSent(c, System.currentTimeMillis());
        probeMcsAsync(c);
    }

    /** Test the primary MCS transport without blocking the heartbeat caller. */
    private static void probeMcsAsync(Context c) {
        Context app = c.getApplicationContext();
        new Thread(() -> probeMcs(app), "fcm-connection-probe").start();
    }

    /** Resolve mtalk.google.com and test the primary FCM MCS socket. */
    private static void probeMcs(Context c) {
        int port = -1;
        boolean ok = false;
        try {
            InetAddress.getByName("mtalk.google.com");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("mtalk.google.com", 5228), 4000);
                port = 5228;
                ok = true;
            }
        } catch (Exception ignored) { }
        prefs(c).edit()
                .putBoolean(KEY_LAST_PROBE_OK, ok)
                .putInt(KEY_LAST_PROBE_PORT, port)
                .putLong(KEY_LAST_PROBE_AT, System.currentTimeMillis())
                .apply();
    }

    private static PendingIntent alarmIntent(Context c) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, ALARM_REQ,
                new Intent(c, HeartbeatAlarmReceiver.class), flags);
    }

    /** Schedule the next heartbeat one interval from now. */
    public static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long triggerAt = System.currentTimeMillis() + getIntervalMin(c) * 60_000L;
        if (Build.VERSION.SDK_INT >= 31) {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, alarmIntent(c)), alarmIntent(c));
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(c));
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(c));
        }
    }

    public static void cancelAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "FCM Keep-Alive",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("Ongoing notification while the FCM heartbeat keep-alive is running.");
            nm.createNotificationChannel(ch);
        }
    }

    public static String formatTime(long t) {
        if (t <= 0) return "never";
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(t));
    }

    public static Notification buildNotification(Context c) {
        ensureChannel(c);
        String last = formatTime(getLastSent(c));
        PendingIntent content = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_neon_concept)
                .setContentTitle("FCM keep-alive active")
                .setContentText("Every " + getIntervalMin(c) + " min · last sent " + last)
                .setOngoing(true)
                .setContentIntent(content)
                .setShowWhen(false)
                .build();
    }

    public static void updateNotification(Context c) {
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, buildNotification(c));
        } catch (Exception ignored) {
            // POST_NOTIFICATIONS not granted; the foreground service still runs.
        }
    }
}
