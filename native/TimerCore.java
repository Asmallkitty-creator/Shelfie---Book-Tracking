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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.Locale;

/** Keeps the reading-timer state and draws the live notification. */
public final class TimerCore {
    public static final String CHANNEL = "shelfie_timer";
    static final int NOTIF_ID = 4242;
    /** Notification colour (orange). Change this hex to try another colour. */
    static final int ACCENT = 0xFFE8702A;
    /** File (inside the app's private folder) that holds the custom notification picture. */
    public static final String CUSTOM_ART_FILE = "nt_custom_art.bin";

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

    /** Notification style chosen in the app: "classic", "custom" or one of the artwork themes. */
    static final String[][] THEMES = {
            {"dreamy", "#4B4B8A"},
            {"sakura", "#7E4260"},
            {"ocean", "#0E4A58"},
            {"library", "#4E2B12"},
            {"sunset", "#80434F"},
            {"aurora", "#173050"},
            {"garden", "#5A4AA8"},
            {"zen", "#4A3FA6"},
            {"rain", "#4A3FB8"},
            {"wreath", "#5F4CC0"},
            {"galaxy", "#1D1050"},
            {"moonsea", "#24428E"},
            {"sword", "#1F6FC0"},
            {"sky", "#2F86DC"},
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
        if ("classic".equals(t) || "custom".equals(t)) return t;   // <- "custom" used to fall through to forest
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

    // ---------------------------------------------------------------- custom style

    static int col(String hex, int def) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return def;
        }
    }

    /** Draws the custom gradient card as a bitmap (RemoteViews can't set a gradient directly). */
    static Bitmap gradientCard(int g1, int g2) {
        int w = 720, h = 260;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setShader(new LinearGradient(0, 0, w, h, g1, g2, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0, 0, w, h), 40, 40, pt);
        return bm;
    }

    static Bitmap customArt(Context c) {
        try {
            File f = new File(c.getFilesDir(), CUSTOM_ART_FILE);
            if (!f.exists() || f.length() == 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int max = Math.max(o.outWidth, o.outHeight), ss = 1;
            while (max / ss > 512) ss *= 2;          // keep it small: notifications reject huge bitmaps
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = ss;
            Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (bm == null) return null;
            int m = Math.max(bm.getWidth(), bm.getHeight());
            if (m > 320) {
                float k = 320f / m;
                bm = Bitmap.createScaledBitmap(bm, Math.max(1, Math.round(bm.getWidth() * k)),
                        Math.max(1, Math.round(bm.getHeight() * k)), true);
            }
            return bm;
        } catch (Throwable e) {
            return null;
        }
    }

    static RemoteViews customView(Context c, boolean big, String title, String sub, int prog,
                                  boolean paused, long elapsed, PendingIntent pausePi,
                                  PendingIntent stopPi, PendingIntent discardPi) {
        SharedPreferences p = sp(c);
        int g1 = col(p.getString("cg1", "#2B2150"), 0xFF2B2150);
        int g2 = col(p.getString("cg2", "#14102E"), 0xFF14102E);
        int ac = col(p.getString("cac", "#B9A6FF"), 0xFFB9A6FF);
        int tx = col(p.getString("ctx", "#F4F0FF"), 0xFFF4F0FF);

        RemoteViews rv = new RemoteViews(c.getPackageName(),
                big ? R.layout.nt_custom_big : R.layout.nt_custom_small);
        rv.setImageViewBitmap(R.id.nt_bg, gradientCard(g1, g2));

        Bitmap art = customArt(c);
        if (art != null) {
            rv.setImageViewBitmap(R.id.nt_art, art);
            rv.setViewVisibility(R.id.nt_art, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.nt_art, View.GONE);
        }

        rv.setTextViewText(R.id.nt_title, title);
        rv.setTextColor(R.id.nt_title, tx);
        rv.setTextViewText(R.id.nt_chip, sub);
        rv.setTextColor(R.id.nt_chip, tx);
        rv.setViewVisibility(R.id.nt_chip, sub.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setTextColor(R.id.nt_label, ac);
        rv.setTextColor(R.id.nt_chrono, tx);
        rv.setTextColor(R.id.nt_paused, tx);

        if (prog >= 0) {
            rv.setViewVisibility(R.id.nt_bar, View.VISIBLE);
            rv.setProgressBar(R.id.nt_bar, 100, Math.min(100, prog), false);
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    rv.setColorStateList(R.id.nt_bar, "setProgressTintList", ColorStateList.valueOf(ac));
                } catch (Throwable ignored) {
                }
            }
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

        rv.setImageViewResource(R.id.nt_ico_pause, paused ? R.drawable.ic_play : R.drawable.ic_pause);
        rv.setOnClickPendingIntent(R.id.nt_btn_pause, pausePi);
        rv.setOnClickPendingIntent(R.id.nt_btn_stop, stopPi);
        rv.setOnClickPendingIntent(R.id.nt_btn_discard, discardPi);
        int[] ico = {R.id.nt_ico_pause, R.id.nt_ico_stop, R.id.nt_ico_discard};
        for (int id : ico) rv.setInt(id, "setColorFilter", tx);
        if (big) {
            rv.setTextViewText(R.id.nt_txt_pause, paused ? "Resume" : "Pause");
            int[] txt = {R.id.nt_txt_pause, R.id.nt_txt_stop, R.id.nt_txt_discard};
            for (int id : txt) rv.setTextColor(id, tx);
        }
        return rv;
    }

    // ---------------------------------------------------------------- built-in themes

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
        {
            rv.setImageViewResource(R.id.nt_ico_pause, paused ? R.drawable.ic_play : R.drawable.ic_pause);
            rv.setTextViewText(R.id.nt_txt_pause, paused ? "Resume" : "Pause");
            rv.setOnClickPendingIntent(R.id.nt_btn_pause, pausePi);
            rv.setOnClickPendingIntent(R.id.nt_btn_stop, stopPi);
            rv.setOnClickPendingIntent(R.id.nt_btn_discard, discardPi);
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
            boolean custom = "custom".equals(th);
            RemoteViews small = custom
                    ? customView(c, false, title, sub, prog, paused, elapsed, pausePi, stopPi, discardPi)
                    : themedView(c, th, false, title, sub, prog, paused, elapsed, pausePi, stopPi, discardPi);
            RemoteViews big = custom
                    ? customView(c, true, title, sub, prog, paused, elapsed, pausePi, stopPi, discardPi)
                    : themedView(c, th, true, title, sub, prog, paused, elapsed, pausePi, stopPi, discardPi);
            int card = custom ? col(p.getString("cg2", "#14102E"), 0xFF14102E) : cardOf(th);
            return new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_book)
                    .setColor(card)
                    .setColorized(true)
                    .setContentTitle(title)
                    .setContentText(paused ? "Paused at " + fmt(elapsed) : "Reading session")
                    .setCustomContentView(small)
                    .setCustomBigContentView(big)
                    .setShowWhen(false)
                    .setOngoing(true)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
