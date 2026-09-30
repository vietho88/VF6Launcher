package com.vf6.launcher.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.vf6.launcher.R;
import com.vf6.launcher.bridge.ShizukuBridge;
import com.vf6.launcher.model.AppEntry;
import com.vf6.launcher.model.AppRepository;
import com.vf6.launcher.session.SessionController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<AppEntry> apps = new ArrayList<>();
    private TextView status, logView;
    private Spinner leftSpinner, rightSpinner, ratioSpinner;
    private CheckBox use720p;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == ShizukuBridge.REQUEST_CODE) refreshStatus();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        logView = findViewById(R.id.log_view);
        leftSpinner = findViewById(R.id.app_left);
        rightSpinner = findViewById(R.id.app_right);
        ratioSpinner = findViewById(R.id.layout_ratio);
        use720p = findViewById(R.id.use_720p);
        Button grant = findViewById(R.id.grant_button);
        Button start = findViewById(R.id.start_button);
        Button stop = findViewById(R.id.stop_button);

        ratioSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"50 / 50", "60 / 40", "70 / 30"}));
        loadApps();
        restoreSelections();

        grant.setOnClickListener(v -> ShizukuBridge.requestPermission());
        start.setOnClickListener(v -> {
            saveSelections();
            append("START…");
            ShizukuBridge.withService(this, new ShizukuBridge.Callback() {
                @Override public void onReady(com.vf6.launcher.bridge.IVF6PrivilegedService service) {
                    io.execute(() -> {
                        try {
                            int id = SessionController.start(MainActivity.this, service, MainActivity.this::append);
                            append("Sẵn sàng. Display ID=" + id + ". Giờ mở VF6 Launcher trong Android Auto.");
                        } catch (Throwable t) {
                            append("LỖI: " + t.getMessage());
                        }
                    });
                }
                @Override public void onError(String message) { append(message); }
            });
        });
        stop.setOnClickListener(v -> ShizukuBridge.withService(this, new ShizukuBridge.Callback() {
            @Override public void onReady(com.vf6.launcher.bridge.IVF6PrivilegedService service) {
                io.execute(() -> {
                    try { SessionController.stop(MainActivity.this, service, MainActivity.this::append); }
                    catch (Throwable t) { append("LỖI stop: " + t.getMessage()); }
                });
            }
            @Override public void onError(String message) { append(message); }
        }));

        Shizuku.addRequestPermissionResultListener(permissionListener);
        refreshStatus();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        io.shutdownNow();
        super.onDestroy();
    }

    private void refreshStatus() {
        if (!ShizukuBridge.binderAlive()) {
            status.setText("Shizuku chưa chạy. Bật Shizuku trước rồi quay lại app.");
        } else if (!ShizukuBridge.permissionGranted()) {
            status.setText("Shizuku đang chạy • cần cấp quyền cho VF6 Launcher");
        } else {
            status.setText("Shizuku OK • sẵn sàng tạo virtual display");
        }
    }

    private void loadApps() {
        apps.clear();
        apps.addAll(AppRepository.load(this));
        ArrayAdapter<AppEntry> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, apps);
        leftSpinner.setAdapter(adapter);
        rightSpinner.setAdapter(adapter);
    }

    private void restoreSelections() {
        SharedPreferences p = getSharedPreferences(SessionController.PREFS, MODE_PRIVATE);
        selectComponent(leftSpinner, p.getString(SessionController.KEY_LEFT, "com.google.android.apps.maps/com.google.android.maps.MapsActivity"));
        selectComponent(rightSpinner, p.getString(SessionController.KEY_RIGHT, "com.google.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity"));
        int ratio = p.getInt(SessionController.KEY_RATIO, 50);
        ratioSpinner.setSelection(ratio == 70 ? 2 : ratio == 60 ? 1 : 0);
        use720p.setChecked(p.getInt(SessionController.KEY_W, 1280) == 1280);
    }

    private void selectComponent(Spinner s, String flat) {
        if (flat == null) return;
        for (int n = 0; n < apps.size(); n++) {
            if (flat.equals(apps.get(n).flatten())) { s.setSelection(n); return; }
        }
    }

    private void saveSelections() {
        if (apps.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy app launcher", Toast.LENGTH_SHORT).show();
            return;
        }
        AppEntry left = (AppEntry) leftSpinner.getSelectedItem();
        AppEntry right = (AppEntry) rightSpinner.getSelectedItem();
        int ratio = ratioSpinner.getSelectedItemPosition() == 2 ? 70 : ratioSpinner.getSelectedItemPosition() == 1 ? 60 : 50;
        boolean low = use720p.isChecked();
        getSharedPreferences(SessionController.PREFS, MODE_PRIVATE).edit()
                .putString(SessionController.KEY_LEFT, left.flatten())
                .putString(SessionController.KEY_RIGHT, right.flatten())
                .putInt(SessionController.KEY_RATIO, ratio)
                .putInt(SessionController.KEY_W, low ? 1280 : 1920)
                .putInt(SessionController.KEY_H, low ? 720 : 1080)
                .putInt(SessionController.KEY_DPI, low ? 220 : 240)
                .apply();
    }

    private void append(String s) {
        main.post(() -> logView.append((logView.length() == 0 ? "" : "\n") + s));
    }
}
