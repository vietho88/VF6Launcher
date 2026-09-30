package com.vf6.launcher.driving.car;

import android.content.Intent;
import android.net.Uri;

import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.vf6.launcher.bridge.CommandReceiver;
import com.vf6.launcher.session.SessionController;

public final class DrivingScreen extends Screen {
    private static final String PREFS = "driving";
    private static final String KEY_NAV_QUERY = "nav_query";

    private String lastStatus = "Sẵn sàng";

    public DrivingScreen(CarContext carContext) {
        super(carContext);
    }

    @Override
    public Template onGetTemplate() {
        String destination = getCarContext().getSharedPreferences(PREFS, 0)
                .getString(KEY_NAV_QUERY, "Cầu Rồng Đà Nẵng");

        ItemList.Builder items = new ItemList.Builder();

        items.addItem(new Row.Builder()
                .setTitle("Điều hướng Android Auto")
                .addText(destination)
                .setOnClickListener(() -> startNavigation(destination))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Phiên ứng dụng")
                .addText("Đã chọn trên điện thoại: " + configuredApps())
                .setOnClickListener(() -> getScreenManager().push(new ControlsScreen(false)))
                .build());

        items.addItem(new Row.Builder()
                .setTitle("Điều khiển media")
                .addText(lastStatus)
                .setOnClickListener(() -> getScreenManager().push(new ControlsScreen(true)))
                .build());

        return new ListTemplate.Builder()
                .setTitle("VF6 Launcher · Driving")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(items.build())
                .build();
    }

    private String configuredApps() {
        String left = SessionController.getLeft(getCarContext());
        String right = SessionController.getRight(getCarContext());
        return left == null || right == null ? "chưa chọn đủ 2 app" : "đã lưu";
    }

    private final class ControlsScreen extends Screen {
        private final boolean media;

        ControlsScreen(boolean media) {
            super(DrivingScreen.this.getCarContext());
            this.media = media;
        }

        @Override
        public Template onGetTemplate() {
            ItemList.Builder items = new ItemList.Builder();
            if (media) {
                items.addItem(commandRow("Bài trước", "media_prev", "PREVIOUS sent"));
                items.addItem(commandRow("Phát / Tạm dừng", "media_play_pause", "PLAY/PAUSE sent"));
                items.addItem(commandRow("Bài tiếp", "media_next", "NEXT sent"));
            } else {
                items.addItem(commandRow("Bắt đầu / Kết nối lại", "start", "START sent"));
                items.addItem(commandRow("Đổi tỷ lệ split", "ratio", "RATIO sent"));
                items.addItem(commandRow("Đổi trái / phải", "swap", "SWAP sent"));
                items.addItem(commandRow("Dừng phiên", "stop", "STOP sent"));
            }
            return new ListTemplate.Builder()
                    .setTitle(media ? "Media" : "Phiên ứng dụng")
                    .setHeaderAction(Action.BACK)
                    .setSingleList(items.build())
                    .build();
        }

        private Row commandRow(String title, String command, String message) {
            return new Row.Builder()
                    .setTitle(title)
                    .setOnClickListener(() -> sendCommand(command, message))
                    .build();
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
        Intent i = new Intent(getCarContext(), CommandReceiver.class);
        i.setAction(CommandReceiver.ACTION_COMMAND);
        i.putExtra(CommandReceiver.EXTRA_COMMAND, command);
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
