package com.shelfie.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

/** Keeps the reading-timer state and draws the live notification. */
public final class TimerCore {
    public static final String CHANNEL = "shelfie_timer";
    static final int NOTIF_ID = 4242;
    /** Notification colour (orange). Change this hex to try another colour. */
    static final int ACCENT = 0xFFE8702A;

    private TimerCore() {}

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("shelfie_timer", Context.MODE_PRIVATE);
    }

    public static String fmt(long ms) {
        long k = Math.max(0, ms / 1000);
        long h = k / 3600, m = (k % 3600) / 60, s = k % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    public static void cancel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
        try {
            c.stopService(new Intent(c, TimerService.class));
        } catch (Exception ignored) {
        }
    }

    public static void togglePause(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean("active", false)) return;
        long pausedAt = p.getLong("p", 0);
        if (pausedAt > 0) {
            p.edit().putLong("g", p.getLong("g", 0) + System.currentTimeMillis() - pausedAt)
                    .putLong("p", 0).apply();
        } else {
            p.edit().putLong("p", System.currentTimeMillis()).apply();
        }
        show(c, false);
    }

    public static void show(Context c, boolean startService) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean("active", false)) {
            cancel(c);
            return;
        }
        Notification n = build(c);
        if (n == null) return;
        if (startService) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(c, new Intent(c, TimerService.class));
                return;
            } catch (Exception ignored) {
                // fall back to a normal notification below
            }
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            if (nm != null) nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) {
        }
    }

    public static Notification build(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean("active", false)) return null;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return null;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reading timer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows your running reading session");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        long s = p.getLong("s", 0), pausedAt = p.getLong("p", 0), g = p.getLong("g", 0);
        boolean paused = pausedAt > 0;
        String title = p.getString("title", "Reading");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent openIntent = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent openPi = PendingIntent.getActivity(c, 0, openIntent, flags);

        Intent pauseIntent = new Intent(c, TimerReceiver.class).setAction("shelfie.PAUSE");
        PendingIntent pausePi = PendingIntent.getBroadcast(c, 1, pauseIntent, flags);

        Intent stopIntent = new Intent(c, StopDialogActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent stopPi = PendingIntent.getActivity(c, 2, stopIntent, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_book)
                .setColor(ACCENT)
                .setColorized(true)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .addAction(paused ? R.drawable.ic_play : R.drawable.ic_pause, paused ? "Resume" : "Pause", pausePi)
                .addAction(R.drawable.ic_stop, "Stop", stopPi);

        if (paused) {
            b.setUsesChronometer(false).setShowWhen(false)
                    .setContentText("Paused at " + fmt(pausedAt - s - g));
        } else {
            // The system ticks this chronometer itself, even if the app is closed.
            b.setUsesChronometer(true).setShowWhen(true).setWhen(s + g)
                    .setContentText("Reading session");
        }
        return b.build();
    }
}
