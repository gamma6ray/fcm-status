package com.fcm.statuschecker;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service that keeps the app alive in the background so the heartbeat alarm keeps
 * firing. It sends one heartbeat immediately on start, then relies on the self-rescheduling
 * AlarmManager alarm (HeartbeatAlarmReceiver) for subsequent ones.
 */
public class HeartbeatService extends Service {

    public static final String ACTION_STOP = "com.fcm.statuschecker.STOP_HEARTBEAT";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            HeartbeatManager.setEnabled(this, false);
            HeartbeatManager.cancelAlarm(this);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        HeartbeatManager.ensureChannel(this);
        startForegroundCompat();

        HeartbeatManager.setEnabled(this, true);
        HeartbeatManager.sendHeartbeat(this);
        HeartbeatManager.updateNotification(this);
        HeartbeatManager.scheduleNext(this);
        return START_STICKY;
    }

    private void startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(HeartbeatManager.NOTIF_ID, HeartbeatManager.buildNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(HeartbeatManager.NOTIF_ID, HeartbeatManager.buildNotification(this));
        }
    }

    @Override
    public void onDestroy() {
        HeartbeatManager.cancelAlarm(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
