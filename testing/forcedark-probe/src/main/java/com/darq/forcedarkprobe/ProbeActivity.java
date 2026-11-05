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
        label.setText(labelText());
        label.setTextColor(Color.BLACK);
        label.setTextSize(getClass() == ProbeActivity.class ? 20f : 28f);
        label.setPadding(48, 48, 48, 48);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    // NOTE: The plain probe explains itself, because people install it from a release to check that DarQ works. The opt-out variant keeps its class name, which the device suites read.
    private CharSequence labelText() {
        if (getClass() != ProbeActivity.class) {
            return getClass().getSimpleName();
        }
        String lineBreak = System.lineSeparator();
        String gap = lineBreak + lineBreak;
        return "DarQ Probe" + gap
                + "This page is white on purpose." + gap
                + "1. Open DarQ and select DarQ Probe in the app list." + lineBreak
                + "2. Turn on Enable DarQ." + lineBreak
                + "3. Close this app and open it again." + gap
                + "If this page is now dark, DarQ works on your device. If it stays white, open Report an issue in DarQ.";
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
