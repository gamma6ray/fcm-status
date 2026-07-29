package com.fcm.statuschecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/** Restarts the heartbeat keep-alive after reboot if it was enabled. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (HeartbeatManager.isEnabled(context)) {
            ContextCompat.startForegroundService(context, new Intent(context, HeartbeatService.class));
        }
    }
}
