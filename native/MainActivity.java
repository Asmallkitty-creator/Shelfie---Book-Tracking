package com.shelfie.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShelfieTimerPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
