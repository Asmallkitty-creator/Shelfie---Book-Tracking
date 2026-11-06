package com.shelfie.app;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
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

    /** Colour the dialog buttons to match the app. */
    public static void tint(AlertDialog d, int positiveColor) {
        try {
            d.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(positiveColor);
            d.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(0xFF6B6B6B);
        } catch (Exception ignored) {
        }
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
                ContextCompat.startForegroundService(c, new Intent(c, TimerService.class));
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

    /** Notification style chosen in the app: "classic" or one of the artwork themes. */
    static final String[][] THEMES = {
        {"dreamy", "#4B4B8A"},
        {"galaxy", "#1A0D38"},
        {"sakura", "#7E4260"},
        {"ocean", "#0E4A58"},
        {"library", "#4E2B12"},
        {"sunset", "#80434F"},
        {"aurora", "#173050"},
        {"swan", "#5A57C8"},
        {"park", "#5B54C8"},
        {"candy", "#9B5AA6"},
        {"pixel", "#7A3E4A"},
        {"fireflies", "#171B4A"},
        {"dunes", "#5B4A78"},
        {"forest", "#26311F"},
        {"amber", "#7A3D1E"},
        {"twilight", "#2B2238"}
    };

    public static String theme(Context c) {
        String t = sp(c).getString("ntheme", "forest");
        if ("classic".equals(t)) return t;
        for (String[] r : THEMES) if (r[0].equals(t)) return t;
        return "forest";
    }

    static int cardOf(String t) {
        for (String[] r : THEMES) if (r[0].equals(t)) return android.graphics.Color.parseColor(r[1]);
        return 0xFF26311F;
    }

    static int layoutOf(Context c, String t, boolean big) {
        return c.getResources().getIdentifier("nt_" + t + (big ? "_big" : "_small"), "layout", c.getPackageName());
    }

    static RemoteViews themedView(Context c, String t, boolean big, String title, String sub, int prog,
                                  boolean paused, long elapsed, PendingIntent pausePi, PendingIntent stopPi,
                                  PendingIntent discardPi) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), layoutOf(c, t, big));
        rv.setTextViewText(R.id.nt_title, title);
        rv.setTextViewText(R.id.nt_chip, sub);
        rv.setViewVisibility(R.id.nt_chip, sub.isEmpty() ? View.GONE : View.VISIBLE);
        if (prog >= 0) {
            rv.setViewVisibility(R.id.nt_bar, View.VISIBLE);
            rv.setProgressBar(R.id.nt_bar, 100, Math.min(100, prog), false);
        } else {
            rv.setViewVisibility(R.id.nt_bar, View.GONE);
        }
        if (paused) {
            rv.setTextViewText(R.id.nt_label, "PAUSED");
            rv.setViewVisibility(R.id.nt_chrono, View.GONE);
            rv.setViewVisibility(R.id.nt_paused, View.VISIBLE);
            rv.setTextViewText(R.id.nt_paused, fmt(elapsed));
        } else {
            rv.setTextViewText(R.id.nt_label, "READING");
            rv.setViewVisibility(R.id.nt_paused, View.GONE);
            rv.setViewVisibility(R.id.nt_chrono, View.VISIBLE);
            rv.setChronometer(R.id.nt_chrono, SystemClock.elapsedRealtime() - elapsed, null, true);
        }
        if (big) {
            rv.setImageViewResource(R.id.nt_ico_pause, paused ? R.drawable.ic_play : R.drawable.ic_pause);
            rv.setTextViewText(R.id.nt_txt_pause, paused ? "Resume" : "Pause");
            rv.setOnClickPendingIntent(R.id.nt_btn_pause, pausePi);
            rv.setOnClickPendingIntent(R.id.nt_btn_stop, stopPi);
            rv.setOnClickPendingIntent(R.id.nt_btn_discard, discardPi);
        } else {
            rv.setImageViewResource(R.id.nt_c_pause, paused ? R.drawable.ic_play : R.drawable.ic_pause);
            rv.setOnClickPendingIntent(R.id.nt_c_pause, pausePi);
        }
        return rv;
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
        String sub = p.getString("sub", "");
        long elapsed = (paused ? pausedAt : System.currentTimeMillis()) - s - g;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent openIntent = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent openPi = PendingIntent.getActivity(c, 0, openIntent, flags);

        Intent pauseIntent = new Intent(c, TimerReceiver.class).setAction("shelfie.PAUSE");
        PendingIntent pausePi = PendingIntent.getBroadcast(c, 1, pauseIntent, flags);

        Intent stopIntent = new Intent(c, StopDialogActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent stopPi = PendingIntent.getActivity(c, 2, stopIntent, flags);

        Intent discardIntent = new Intent(c, DiscardDialogActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent discardPi = PendingIntent.getActivity(c, 3, discardIntent, flags);

        String th = theme(c);
        if (!"classic".equals(th)) {
            int prog = p.getInt("prog", -1);
            RemoteViews small = themedView(c, th, false, title, sub, prog, paused, elapsed, pausePi, stopPi, discardPi);
            RemoteViews big = themedView(c, th, true, title, sub, prog, paused, elapsed, pausePi, stopPi, discardPi);
            return new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_book)
                    .setColor(cardOf(th))
                    .setColorized(true)
                    .setContentTitle(title)
                    .setContentText(paused ? "Paused at " + fmt(elapsed) : "Reading session")
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(small)
                    .setCustomBigContentView(big)
                    .setShowWhen(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(openPi)
                    .build();
        }

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.notif_timer);
        rv.setTextViewText(R.id.nt_title, title);
        rv.setTextViewText(R.id.nt_sub, sub);
        rv.setViewVisibility(R.id.nt_sub, sub.isEmpty() ? View.GONE : View.VISIBLE);
        if (paused) {
            rv.setTextViewText(R.id.nt_label, "PAUSED");
            rv.setViewVisibility(R.id.nt_chrono, View.GONE);
            rv.setViewVisibility(R.id.nt_paused, View.VISIBLE);
            rv.setTextViewText(R.id.nt_paused, fmt(elapsed));
        } else {
            rv.setTextViewText(R.id.nt_label, "READING");
            rv.setViewVisibility(R.id.nt_paused, View.GONE);
            rv.setViewVisibility(R.id.nt_chrono, View.VISIBLE);
            // The system ticks this chronometer itself, even if the app is closed.
            rv.setChronometer(R.id.nt_chrono, SystemClock.elapsedRealtime() - elapsed, null, true);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_book)
                .setColor(ACCENT)
                .setColorized(true)
                .setContentTitle(title)
                .setContentText(paused ? "Paused at " + fmt(elapsed) : "Reading session")
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(rv)
                .setCustomBigContentView(rv)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .addAction(paused ? R.drawable.ic_play : R.drawable.ic_pause, paused ? "Resume" : "Pause", pausePi)
                .addAction(R.drawable.ic_stop, "Stop", stopPi)
                .addAction(R.drawable.ic_discard, "Discard", discardPi);
        return b.build();
    }
}
