package com.fcm.statuschecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fired by AlarmManager on each interval: sends the heartbeat, refreshes the notification,
 * and (if still enabled) schedules the next one.
 */
public class HeartbeatAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        HeartbeatManager.sendHeartbeat(context);
        HeartbeatManager.updateNotification(context);
        if (HeartbeatManager.isEnabled(context)) {
            HeartbeatManager.scheduleNext(context);
        }
    }
}
