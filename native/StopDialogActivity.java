package com.shelfie.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

/** Small popup shown over any app when you tap Stop in the notification. */
public class StopDialogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences p = TimerCore.sp(this);
        if (!p.getBoolean("active", false)) {
            finish();
            return;
        }
        long pausedAt = p.getLong("p", 0);
        final long at = pausedAt > 0 ? pausedAt : System.currentTimeMillis();
        long elapsed = at - p.getLong("s", 0) - p.getLong("g", 0);
        String mode = p.getString("mode", "pages");
        String question = mode.equals("pct") ? "What percent are you at now?"
                : mode.equals("pages") ? "What page are you on now?"
                : mode.equals("adv") ? "How many pages did you read this session?"
                : "Pages read this session (optional)";

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (!mode.equals("ch") && !mode.equals("adv")) input.setText(p.getString("cur", ""));
        input.setSelectAllOnFocus(true);

        FrameLayout box = new FrameLayout(this);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Nice session! " + TimerCore.fmt(elapsed))
                .setMessage(question)
                .setView(box)
                .setPositiveButton("Save session", (dlg, w) -> {
                    p.edit().putBoolean("active", false)
                            .putBoolean("pending", true)
                            .putString("pendingN", input.getText().toString().trim())
                            .putLong("pendingAt", at)
                            .apply();
                    TimerCore.cancel(StopDialogActivity.this);
                    ShelfieTimerPlugin.ping();
                })
                .setNegativeButton("Keep reading", null)
                .create();
        d.setCanceledOnTouchOutside(false);
        d.setOnDismissListener(x -> finish());
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }
}
