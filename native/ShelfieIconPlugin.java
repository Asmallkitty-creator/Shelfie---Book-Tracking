package com.shelfie.app;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Switches the home-screen icon by enabling one launcher alias and disabling the others.
 * The aliases (Icon_terracotta, Icon_forest, ...) are added to the manifest by patch_android.py.
 */
@CapacitorPlugin(name = "ShelfieIcon")
public class ShelfieIconPlugin extends Plugin {
    private static final String[] NAMES = {
        "terracotta", "forest", "midnight", "rose", "ocean", "lavender", "mocha", "cream"
    };

    @PluginMethod
    public void setIcon(PluginCall call) {
        String name = call.getString("name", "terracotta");
        boolean known = false;
        for (String n : NAMES) if (n.equals(name)) known = true;
        if (!known) { call.reject("Unknown icon: " + name); return; }
        try {
            PackageManager pm = getContext().getPackageManager();
            String pkg = getContext().getPackageName();
            // enable the chosen one first, so there is never a moment with no launcher icon
            pm.setComponentEnabledSetting(new ComponentName(pkg, "com.shelfie.app.Icon_" + name),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String n : NAMES) {
                if (n.equals(name)) continue;
                pm.setComponentEnabledSetting(new ComponentName(pkg, "com.shelfie.app.Icon_" + n),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            JSObject r = new JSObject();
            r.put("name", name);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Could not change the icon: " + e.getMessage());
        }
    }
}
