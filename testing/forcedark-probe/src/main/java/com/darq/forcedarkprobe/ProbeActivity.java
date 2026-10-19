package com.darq.forcedarkprobe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Full-screen white panel with a label, logging what the force dark inputs looked like at creation. */
public class ProbeActivity extends Activity {

    static final String TAG = "DarqProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        TextView label = new TextView(this);
        label.setText(getClass().getSimpleName());
        label.setTextColor(Color.BLACK);
        label.setTextSize(28f);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, getClass().getSimpleName() + " resumed forceDarkProp=" + readForceDarkProp()
                + " nightMode=" + (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK));
    }

    private static String readForceDarkProp() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return (String) properties.getMethod("get", String.class).invoke(null, "debug.hwui.force_dark");
        } catch (Exception e) {
            return "unreadable";
        }
    }

}
