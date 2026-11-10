package com.shelfie.app;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShelfieTimerPlugin.class);
        registerPlugin(ShelfieFilesPlugin.class);
        registerPlugin(ShelfieIconPlugin.class);
        super.onCreate(savedInstanceState);

        // Draw edge to edge, then pad the page so it never sits under the
        // status bar, the navigation bar or the keyboard.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        final View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets i = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);

        // Android's back button: the app goes back one page itself. It only closes
        // when the app answers "exit" (at the start page, pressed twice).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getBridge() == null || getBridge().getWebView() == null) { finish(); return; }
                getBridge().getWebView().evaluateJavascript(
                    "(function(){try{return window.shBack?window.shBack():'exit'}catch(e){return 'exit'}})()",
                    value -> {
                        if (value != null && value.contains("exit")) finish();
                    });
            }
        });
    }
}
