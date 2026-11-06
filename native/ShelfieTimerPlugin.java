package com.shelfie.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
        name = "ShelfieTimer",
        permissions = {@Permission(strings = {Manifest.permission.POST_NOTIFICATIONS}, alias = "notifications")}
)
public class ShelfieTimerPlugin extends Plugin {
    private static ShelfieTimerPlugin inst;

    @Override
    public void load() {
        inst = this;
    }

    /** Tell the web page that the timer changed from the notification. */
    static void ping() {
        if (inst != null) inst.notifyListeners("timerChanged", new JSObject());
    }

    @PluginMethod
    public void start(PluginCall call) {
        JSObject d = call.getData();
        TimerCore.sp(getContext()).edit()
                .putBoolean("active", true)
                .putBoolean("pending", false)
                .putString("id", d.optString("id", ""))
                .putString("title", d.optString("title", "Reading"))
                .putString("mode", d.optString("mode", "pages"))
                .putString("cur", d.optString("cur", ""))
                .putLong("s", d.optLong("s", System.currentTimeMillis()))
                .putLong("p", d.optLong("p", 0))
                .putLong("g", d.optLong("g", 0))
                .apply();
        TimerCore.show(getContext(), true);
        call.resolve();
    }

    @PluginMethod
    public void setBars(PluginCall call) {
        JSObject d = call.getData();
        final int col = Color.rgb(d.optInt("r", 0), d.optInt("g", 0), d.optInt("b", 0));
        final boolean dark = d.optBoolean("dark", true);
        if (getActivity() == null) {
            call.resolve();
            return;
        }
        getActivity().runOnUiThread(() -> {
            try {
                View content = getActivity().findViewById(android.R.id.content);
                content.setBackgroundColor(col);
                getActivity().getWindow().getDecorView().setBackgroundColor(col);
                WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getActivity().getWindow(), content);
                c.setAppearanceLightStatusBars(!dark);
                c.setAppearanceLightNavigationBars(!dark);
            } catch (Exception ignored) {
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        TimerCore.sp(getContext()).edit().putBoolean("active", false).apply();
        TimerCore.cancel(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        SharedPreferences p = TimerCore.sp(getContext());
        JSObject r = new JSObject();
        r.put("active", p.getBoolean("active", false));
        r.put("p", p.getLong("p", 0));
        r.put("g", p.getLong("g", 0));
        r.put("pending", p.getBoolean("pending", false));
        r.put("pendingN", p.getString("pendingN", ""));
        r.put("pendingAt", p.getLong("pendingAt", 0));
        call.resolve(r);
    }

    @PluginMethod
    public void clearPending(PluginCall call) {
        TimerCore.sp(getContext()).edit().putBoolean("pending", false).apply();
        call.resolve();
    }

    @PluginMethod
    public void requestPerm(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            JSObject r = new JSObject();
            r.put("granted", true);
            call.resolve(r);
            return;
        }
        requestPermissionForAlias("notifications", call, "permDone");
    }

    @PermissionCallback
    private void permDone(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", getPermissionState("notifications") == PermissionState.GRANTED);
        call.resolve(r);
    }
}
