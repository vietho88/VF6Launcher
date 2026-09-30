package com.vf6.launcher.driving.car;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

public final class DrivingScreen extends Screen {
    private static final String TARGET_PACKAGE = "com.vf6.launcher";
    private static final String ACTION_COMMAND = "com.vf6.launcher.action.COMMAND";
    private static final String EXTRA_COMMAND = "command";
    private static final String PREFS = "driving";
    private static final String KEY_NAV_QUERY = "nav_query";

    private String lastStatus = "Sẵn sàng";

    public DrivingScreen(CarContext carContext) {
        super(carContext);
    }

    @Override
    public Template onGetTemplate() {
        boolean installed = isMainInstalled();
        String destination = getCarContext().getSharedPreferences(PREFS, 0)
                .getString(KEY_NAV_QUERY, "Cầu Rồng Đà Nẵng");

        ItemList.Builder items = new ItemList.Builder();

        items.addItem(new Row.Builder()
                .setTitle(installed ? "VF6 Launcher main: OK" : "VF6 Launcher main: chưa cài")
                .addText(lastStatus)
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Điều hướng Android Auto")
                .addText(destination)
                .setOnClickListener(() -> startNavigation(destination))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Start / reconnect session")
                .addText("Khởi tạo lại virtual session đã cấu hình trên điện thoại")
                .setOnClickListener(() -> sendCommand("start", "START sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Đổi tỷ lệ split")
                .addText("50/50 → 60/40 → 70/30")
                .setOnClickListener(() -> sendCommand("ratio", "RATIO sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Swap left / right")
                .addText("Đổi vị trí hai app trong session")
                .setOnClickListener(() -> sendCommand("swap", "SWAP sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Media: Previous")
                .setOnClickListener(() -> sendCommand("media_prev", "PREVIOUS sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Media: Play / Pause")
                .setOnClickListener(() -> sendCommand("media_play_pause", "PLAY/PAUSE sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Media: Next")
                .setOnClickListener(() -> sendCommand("media_next", "NEXT sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Stop virtual session")
                .setOnClickListener(() -> sendCommand("stop", "STOP sent"))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Driving mode")
                .addText("Android Auto template + navigation/media controls. Full arbitrary-app surface remains parked-only.")
                .build());

        return new ListTemplate.Builder()
                .setTitle("VF6 Launcher · Driving")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(items.build())
                .build();
    }

    private boolean isMainInstalled() {
        try {
            getCarContext().getPackageManager().getPackageInfo(TARGET_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void startNavigation(String query) {
        try {
            String q = (query == null || query.trim().isEmpty()) ? "Cầu Rồng Đà Nẵng" : query.trim();
            Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(q));
            Intent intent = new Intent(CarContext.ACTION_NAVIGATE, uri);
            getCarContext().startCarApp(intent);
            lastStatus = "Navigation: " + q;
        } catch (Throwable t) {
            lastStatus = "Navigation lỗi: " + t.getClass().getSimpleName();
            CarToast.makeText(getCarContext(), lastStatus, CarToast.LENGTH_LONG).show();
        }
        invalidate();
    }

    private void sendCommand(String command, String message) {
        Intent i = new Intent(ACTION_COMMAND);
        i.setPackage(TARGET_PACKAGE);
        i.putExtra(EXTRA_COMMAND, command);
        try {
            getCarContext().sendBroadcast(i);
            lastStatus = message;
            CarToast.makeText(getCarContext(), message, CarToast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            lastStatus = "Command lỗi: " + t.getClass().getSimpleName();
            CarToast.makeText(getCarContext(), lastStatus, CarToast.LENGTH_LONG).show();
        }
        invalidate();
    }
}
