package com.shelfie.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the Pause / Resume button, even when the app is closed. */
public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("shelfie.PAUSE".equals(intent.getAction())) {
            TimerCore.togglePause(context);
            ShelfieTimerPlugin.ping();
        }
    }
}
