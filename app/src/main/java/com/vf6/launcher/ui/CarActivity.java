package com.vf6.launcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vf6.launcher.R;
import com.vf6.launcher.bridge.IVF6PrivilegedService;
import com.vf6.launcher.bridge.ShizukuBridge;
import com.vf6.launcher.model.AppEntry;
import com.vf6.launcher.model.AppRepository;
import com.vf6.launcher.session.SessionController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CarActivity extends Activity implements SurfaceHolder.Callback {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private SurfaceView surface;
    private TextView status;
    private Button leftButton, rightButton, ratioButton;
    private FrameLayout mirrorFrame;
    private View splitDivider;
    private LinearLayout shortcuts;
    private boolean leftPaneSelected = true;
    private IVF6PrivilegedService service;
    private SurfaceControl mirrorControl;
    private Bundle mirrorBundle;
    private int displayId = -1;
    private float downX, downY;
    private long downAt;
    private List<AppEntry> apps;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        surface = findViewById(R.id.display_surface);
        status = findViewById(R.id.car_status);
        leftButton = findViewById(R.id.car_left_app);
        rightButton = findViewById(R.id.car_right_app);
        ratioButton = findViewById(R.id.car_ratio);
        mirrorFrame = findViewById(R.id.car_mirror_frame);
        splitDivider = findViewById(R.id.car_split_divider);
        shortcuts = findViewById(R.id.car_app_shortcuts);
        Button back = findViewById(R.id.car_back);
        Button home = findViewById(R.id.car_home);
        Button swap = findViewById(R.id.car_swap);
        Button stop = findViewById(R.id.car_stop);
        Button allApps = findViewById(R.id.car_all_apps);

        apps = AppRepository.load(this);
        populateShortcuts();
        refreshLabels();
        mirrorFrame.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateDivider());

        surface.getHolder().addCallback(this);
        surface.setOnTouchListener(this::onSurfaceTouch);

        leftButton.setOnClickListener(v -> {
            leftPaneSelected = true;
            refreshLabels();
            chooseApp(true);
        });
        rightButton.setOnClickListener(v -> {
            leftPaneSelected = false;
            refreshLabels();
            chooseApp(false);
        });
        allApps.setOnClickListener(v -> chooseApp(leftPaneSelected));
        ratioButton.setOnClickListener(v -> {
            if (service == null) return;
            io.execute(() -> {
                try {
                    SessionController.cycleRatio(this, service, this::setStatus);
                    main.post(this::refreshLabels);
                } catch (Throwable t) { setStatus("Layout lỗi: " + t.getMessage()); }
            });
        });

        back.setOnClickListener(v -> sendKey(4));
        home.setOnClickListener(v -> sendKey(3));
        swap.setOnClickListener(v -> {
            if (service == null) return;
            io.execute(() -> {
                try {
                    SessionController.swap(this, service, this::setStatus);
                    main.post(this::refreshLabels);
                } catch (Throwable t) { setStatus("Swap lỗi: " + t.getMessage()); }
            });
        });
        stop.setOnClickListener(v -> {
            if (service == null) return;
            io.execute(() -> {
                try {
                    detachMirror();
                    SessionController.stop(this, service, this::setStatus);
                } catch (Throwable t) { setStatus("Stop lỗi: " + t.getMessage()); }
            });
        });

        bindAndStart();
    }

    private void populateShortcuts() {
        if (apps == null || apps.isEmpty()) return;
        Set<String> shown = new HashSet<>();
        String[] featured = {"YouTube", "TikTok", "VTVgo", "FPT Play", "Maps", "Grab"};
        for (String name : featured) {
            for (AppEntry app : apps) {
                if (app.label.toLowerCase().contains(name.toLowerCase())
                        && shown.add(app.flatten())) {
                    addShortcut(app);
                    break;
                }
            }
        }
        for (AppEntry app : apps) {
            if (shortcuts.getChildCount() >= 6) break;
            if (shown.add(app.flatten())) addShortcut(app);
        }
    }

    private void addShortcut(AppEntry app) {
        TextView shortcut = new TextView(this);
        shortcut.setText(app.label);
        shortcut.setTextColor(Color.WHITE);
        shortcut.setTextSize(11);
        shortcut.setMaxLines(2);
        shortcut.setGravity(Gravity.CENTER);
        shortcut.setPadding(dp(4), dp(6), dp(4), dp(6));
        shortcut.setCompoundDrawablePadding(dp(3));
        try {
            Drawable icon = getPackageManager().getActivityIcon(app.component());
            int size = dp(28);
            icon.setBounds(0, 0, size, size);
            shortcut.setCompoundDrawables(null, icon, null, null);
        } catch (Exception ignored) {
        }
        shortcut.setOnClickListener(v -> replaceSelectedPane(app));
        shortcuts.addView(shortcut, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));
    }

    private void replaceSelectedPane(AppEntry selected) {
        if (service == null) {
            setStatus("Shizuku chưa sẵn sàng");
            return;
        }
        boolean leftPane = leftPaneSelected;
        setStatus("Đang mở " + selected.label + "...");
        io.execute(() -> {
            try {
                SessionController.replacePane(this, service, leftPane,
                        selected.flatten(), this::setStatus);
                main.post(this::refreshLabels);
            } catch (Throwable t) {
                setStatus("Đổi app lỗi: " + t.getMessage());
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void chooseApp(boolean leftPane) {
        if (apps == null || apps.isEmpty()) {
            setStatus("Không tìm thấy app launcher");
            return;
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(leftPane ? "Chọn app bên trái" : "Chọn app bên phải")
                .setItems(labels, (dialog, which) -> {
                    if (service == null) {
                        setStatus("Shizuku chưa sẵn sàng");
                        return;
                    }
                    leftPaneSelected = leftPane;
                    replaceSelectedPane(apps.get(which));
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void refreshLabels() {
        leftButton.setText("L: " + labelFor(SessionController.getLeft(this)) + " ▼");
        rightButton.setText("R: " + labelFor(SessionController.getRight(this)) + " ▼");
        int ratio = SessionController.getRatio(this);
        ratioButton.setText(ratio + "/" + (100 - ratio));
        leftButton.setAlpha(leftPaneSelected ? 1f : 0.58f);
        rightButton.setAlpha(leftPaneSelected ? 0.58f : 1f);
        leftButton.setBackgroundResource(leftPaneSelected
                ? R.drawable.car_selected_bg : R.drawable.car_bar_bg);
        rightButton.setBackgroundResource(leftPaneSelected
                ? R.drawable.car_bar_bg : R.drawable.car_selected_bg);
        updateDivider();
    }

    private void updateDivider() {
        if (surface == null || splitDivider == null || surface.getWidth() <= 0) return;
        int width = Math.max(1, SessionController.getWidth(this));
        int split = Math.max(320, Math.min(width - 320,
                Math.round(width * SessionController.getRatio(this) / 100f)));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) splitDivider.getLayoutParams();
        int margin = dp(4) + Math.round(surface.getWidth() * split / (float) width);
        if (params.leftMargin != margin) {
            params.leftMargin = margin;
            splitDivider.setLayoutParams(params);
        }
    }

    private String labelFor(String component) {
        if (component == null) return "Chưa chọn";
        if (apps != null) for (AppEntry a : apps) if (component.equals(a.flatten())) return a.label;
        int slash = component.indexOf('/');
        String pkg = slash > 0 ? component.substring(0, slash) : component;
        int dot = pkg.lastIndexOf('.');
        return dot >= 0 ? pkg.substring(dot + 1) : pkg;
    }

    private void bindAndStart() {
        ShizukuBridge.withService(this, new ShizukuBridge.Callback() {
            @Override public void onReady(IVF6PrivilegedService s) {
                service = s;
                io.execute(() -> {
                    try {
                        displayId = SessionController.start(CarActivity.this, s, CarActivity.this::setStatus);
                        setStatus("VF6 Launcher • display " + displayId);
                        main.post(() -> {
                            refreshLabels();
                            attachMirrorIfPossible();
                        });
                    } catch (Throwable t) {
                        setStatus("Không start được: " + t.getMessage());
                    }
                });
            }
            @Override public void onError(String message) {
                setStatus(message + " • mở app trên điện thoại để cấp quyền");
            }
        });
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { attachMirrorIfPossible(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { attachMirrorIfPossible(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { detachMirror(); }

    private void attachMirrorIfPossible() {
        if (service == null || displayId < 0 || surface.getWidth() <= 0 || surface.getHeight() <= 0) return;
        io.execute(() -> {
            try {
                detachMirror();
                Bundle b = service.mirrorDisplay(displayId);
                b.setClassLoader(SurfaceControl.class.getClassLoader());
                String error = b.getString("error");
                if (error != null) throw new IllegalStateException(error);
                Parcelable parcelable = b.getParcelable("surface_control");
                if (!(parcelable instanceof SurfaceControl)) throw new IllegalStateException("Không nhận được SurfaceControl mirror");
                SurfaceControl mirror = (SurfaceControl) parcelable;
                main.post(() -> {
                    try {
                        SurfaceControl parent = surface.getSurfaceControl();
                        if (parent == null || !parent.isValid()) throw new IllegalStateException("SurfaceView chưa sẵn sàng");
                        int srcW = SessionController.getWidth(this);
                        int srcH = SessionController.getHeight(this);
                        Rect src = new Rect(0, 0, srcW, srcH);
                        Rect dst = new Rect(0, 0, surface.getWidth(), surface.getHeight());
                        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
                        tx.reparent(mirror, parent)
                                .setGeometry(mirror, src, dst, Surface.ROTATION_0)
                                .setLayer(mirror, 1)
                                .setVisibility(mirror, true)
                                .apply();
                        tx.close();
                        mirrorBundle = b;
                        mirrorControl = mirror;
                        setStatus("Split sẵn sàng • chạm trực tiếp");
                    } catch (Throwable t) { setStatus("Mirror attach lỗi: " + t.getMessage()); }
                });
            } catch (Throwable t) { setStatus("Mirror lỗi: " + t.getMessage()); }
        });
    }

    private boolean onSurfaceTouch(View v, MotionEvent e) {
        if (service == null || displayId < 0) return true;
        float sx = SessionController.getWidth(this) / (float) Math.max(1, surface.getWidth());
        float sy = SessionController.getHeight(this) / (float) Math.max(1, surface.getHeight());
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX() * sx;
            downY = e.getY() * sy;
            downAt = System.currentTimeMillis();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float x = e.getX() * sx, y = e.getY() * sy;
            long d = System.currentTimeMillis() - downAt;
            float dx = x - downX, dy = y - downY;
            io.execute(() -> {
                try {
                    if (dx * dx + dy * dy < 18 * 18) SessionController.tap(service, displayId, x, y);
                    else SessionController.swipe(service, displayId, downX, downY, x, y, d);
                } catch (Throwable t) { setStatus("Touch lỗi: " + t.getMessage()); }
            });
            return true;
        }
        return true;
    }

    private void sendKey(int keyCode) {
        if (service == null || displayId < 0) return;
        io.execute(() -> {
            try { SessionController.key(service, displayId, keyCode); }
            catch (Throwable t) { setStatus("Key lỗi: " + t.getMessage()); }
        });
    }

    private void detachMirror() {
        SurfaceControl m = mirrorControl;
        Bundle b = mirrorBundle;
        mirrorControl = null;
        mirrorBundle = null;
        if (m != null) {
            main.post(() -> {
                try {
                    SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
                    tx.reparent(m, null).apply();
                    tx.close();
                    m.release();
                } catch (Throwable ignored) {}
            });
        }
        if (service != null && b != null) {
            try { service.releaseMirror(b); } catch (Throwable ignored) {}
        }
    }

    private void setStatus(String text) { main.post(() -> status.setText(text)); }

    @Override protected void onDestroy() {
        detachMirror();
        io.shutdownNow();
        super.onDestroy();
    }
}
