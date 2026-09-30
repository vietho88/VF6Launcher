package com.vf6.launcher.driving.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.vf6.launcher.driving.R;

public final class CompanionActivity extends Activity {
    private static final String TARGET_PACKAGE = "com.vf6.launcher";
    private static final String ACTION_COMMAND = "com.vf6.launcher.action.COMMAND";
    private static final String EXTRA_COMMAND = "command";
    private static final String PREFS = "driving";
    private static final String KEY_NAV_QUERY = "nav_query";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion);

        EditText nav = findViewById(R.id.nav_query);
        nav.setText(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_NAV_QUERY, "Cầu Rồng Đà Nẵng"));
        findViewById(R.id.save_nav).setOnClickListener(v -> {
            String q = nav.getText().toString().trim();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_NAV_QUERY, q).apply();
            Toast.makeText(this, "Đã lưu điểm đến", Toast.LENGTH_SHORT).show();
        });

        wire(R.id.test_start, "start");
        wire(R.id.test_ratio, "ratio");
        wire(R.id.test_swap, "swap");
        wire(R.id.test_prev, "media_prev");
        wire(R.id.test_play, "media_play_pause");
        wire(R.id.test_next, "media_next");
        wire(R.id.test_stop, "stop");
    }

    private void wire(int id, String command) {
        Button b = findViewById(id);
        b.setOnClickListener(v -> send(command));
    }

    private void send(String command) {
        Intent i = new Intent(ACTION_COMMAND);
        i.setPackage(TARGET_PACKAGE);
        i.putExtra(EXTRA_COMMAND, command);
        try {
            sendBroadcast(i);
            Toast.makeText(this, "Sent: " + command, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Command failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
