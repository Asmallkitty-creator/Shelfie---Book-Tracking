package com.shelfie.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;

/** "Are you sure?" popup shown when you tap Discard in the notification. */
public class DiscardDialogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences p = TimerCore.sp(this);
        if (!p.getBoolean("active", false)) {
            finish();
            return;
        }
        long pausedAt = p.getLong("p", 0);
        long at = pausedAt > 0 ? pausedAt : System.currentTimeMillis();
        long elapsed = at - p.getLong("s", 0) - p.getLong("g", 0);

        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Discard this session?")
                .setMessage("Your " + TimerCore.fmt(elapsed) + " of reading won’t be saved.")
                .setPositiveButton("Discard", (dlg, w) -> {
                    p.edit().putBoolean("active", false)
                            .putBoolean("pending", true)
                            .putString("pendingKind", "discard")
                            .apply();
                    TimerCore.cancel(DiscardDialogActivity.this);
                    ShelfieTimerPlugin.ping();
                })
                .setNegativeButton("Keep reading", null)
                .create();
        d.setCanceledOnTouchOutside(false);
        d.setOnDismissListener(x -> finish());
        d.show();
        TimerCore.tint(d, 0xFFC0392B);
    }
}
