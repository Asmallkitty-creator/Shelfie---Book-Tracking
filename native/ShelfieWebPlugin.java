package com.shelfie.app;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Lets the page read a few book websites (Romanian shops) that the browser engine cannot read
 * directly because of cross-site rules. Only the sites listed below can be fetched.
 */
@CapacitorPlugin(name = "ShelfieWeb")
public class ShelfieWebPlugin extends Plugin {
    private static final String[] ALLOWED = {
        "carturesti.ro", "libris.ro", "elefant.ro", "html.duckduckgo.com"
    };
    private static final int MAX_BYTES = 1500 * 1024;

    private static boolean allowed(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        for (String a : ALLOWED) {
            if (host.equals(a) || host.endsWith("." + a)) return true;
        }
        return false;
    }

    @PluginMethod
    public void httpGet(final PluginCall call) {
        final String u = call.getString("url");
        if (u == null || !u.startsWith("https://")) { call.reject("Only https addresses are allowed"); return; }
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(u);
                if (!allowed(url.getHost())) { call.reject("This website is not allowed"); return; }
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
                c.setRequestProperty("Accept-Language", "ro-RO,ro;q=0.9,en;q=0.7");
                int code = c.getResponseCode();
                // a redirect must not leave the allowed sites
                if (!allowed(c.getURL().getHost())) { call.reject("Redirected to a website that is not allowed"); return; }
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String text = "";
                if (in != null) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n, total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_BYTES) break;
                        bo.write(buf, 0, n);
                    }
                    in.close();
                    text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                }
                JSObject r = new JSObject();
                r.put("status", code);
                r.put("url", c.getURL().toString());
                r.put("text", text);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Could not load the page: " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }
}
