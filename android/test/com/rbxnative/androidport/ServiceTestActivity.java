package com.rbxnative.androidport;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class ServiceTestActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("Android host service validation");
        setContentView(label);
    }
}
