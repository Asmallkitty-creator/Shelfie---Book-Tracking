package com.shelfie.app;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the reading notification alive (and lets it be coloured). */
public class TimerService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = TimerCore.build(this);
        if (n == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(TimerCore.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(TimerCore.NOTIF_ID, n);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
