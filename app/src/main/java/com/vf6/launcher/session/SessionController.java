package com.vf6.launcher.session;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import com.vf6.launcher.bridge.IVF6PrivilegedService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SessionController {
    public interface Logger { void log(String message); }

    public static final String PREFS = "vf6launcher";
    public static final String KEY_LEFT = "left_component";
    public static final String KEY_RIGHT = "right_component";
    public static final String KEY_RATIO = "ratio";
    public static final String KEY_W = "display_w";
    public static final String KEY_H = "display_h";
    public static final String KEY_DPI = "display_dpi";
    public static final String KEY_DISPLAY_ID = "active_display_id";
    private static final String KEY_OLD_OVERLAY = "old_overlay_display_devices";

    private SessionController() {}

    public static int getActiveDisplayId(Context context) {
        return prefs(context).getInt(KEY_DISPLAY_ID, -1);
    }

    public static int getWidth(Context context) { return prefs(context).getInt(KEY_W, 1280); }
    public static int getHeight(Context context) { return prefs(context).getInt(KEY_H, 720); }
    public static int getRatio(Context context) { return prefs(context).getInt(KEY_RATIO, 50); }
    public static String getLeft(Context context) { return prefs(context).getString(KEY_LEFT, null); }
    public static String getRight(Context context) { return prefs(context).getString(KEY_RIGHT, null); }

    public static int start(Context context, IVF6PrivilegedService svc, Logger logger) throws Exception {
        SharedPreferences p = prefs(context);
        String left = p.getString(KEY_LEFT, null);
        String right = p.getString(KEY_RIGHT, null);
        int ratio = p.getInt(KEY_RATIO, 50);
        int w = p.getInt(KEY_W, 1280);
        int h = p.getInt(KEY_H, 720);
        int dpi = p.getInt(KEY_DPI, 220);
        if (left == null || right == null) throw new IllegalStateException("Chưa chọn đủ 2 app");

        int active = p.getInt(KEY_DISPLAY_ID, -1);
        if (active > 0 && listDisplayIds(svc).contains(active)) {
            logger.log("Dùng lại virtual display " + active);
            ensureApps(context, svc, active, logger);
            resizeCurrent(context, svc, logger);
            return active;
        }

        logger.log("Đang tạo virtual display " + w + "x" + h + "/" + dpi + "...");
        Set<Integer> before = new HashSet<>(listDisplayIds(svc));
        String oldOverlay = svc.exec("settings get global overlay_display_devices");
        p.edit().putString(KEY_OLD_OVERLAY, oldOverlay).apply();

        svc.exec("settings put global overlay_display_devices '" + w + "x" + h + "/" + dpi + "'");
        Thread.sleep(1800);

        List<Integer> after = listDisplayIds(svc);
        int displayId = -1;
        for (int id : after) {
            if (id != 0 && !before.contains(id)) { displayId = id; break; }
        }
        if (displayId < 0) for (int id : after) if (id != 0) displayId = Math.max(displayId, id);
        if (displayId < 0) throw new IllegalStateException("Không tìm thấy virtual display mới");

        p.edit().putInt(KEY_DISPLAY_ID, displayId).apply();
        logger.log("Virtual display ID = " + displayId);
        svc.exec("wm set-display-windowing-mode -d " + displayId + " 5 || true");
        svc.exec("settings put global enable_freeform_support 1 || true");

        launchComponent(svc, left, displayId, logger);
        Thread.sleep(600);
        launchComponent(svc, right, displayId, logger);
        Thread.sleep(750);
        resizeCurrent(context, svc, logger);
        return displayId;
    }

    public static void replacePane(Context context, IVF6PrivilegedService svc, boolean leftPane,
                                   String component, Logger logger) throws Exception {
        ComponentName c = ComponentName.unflattenFromString(component);
        if (c == null) throw new IllegalArgumentException("Component không hợp lệ: " + component);

        SharedPreferences p = prefs(context);
        String key = leftPane ? KEY_LEFT : KEY_RIGHT;
        String old = p.getString(key, null);
        int displayId = p.getInt(KEY_DISPLAY_ID, -1);
        if (displayId <= 0 || !listDisplayIds(svc).contains(displayId)) {
            p.edit().putString(key, component).apply();
            start(context, svc, logger);
            return;
        }

        if (old != null) {
            ComponentName oldC = ComponentName.unflattenFromString(old);
            if (oldC != null) {
                int oldTask = findTaskForPackage(svc, oldC.getPackageName());
                if (oldTask > 0) svc.exec("am task remove " + oldTask + " || true");
            }
        }

        p.edit().putString(key, component).apply();
        launchComponent(svc, component, displayId, logger);
        Thread.sleep(650);
        resizeCurrent(context, svc, logger);
        logger.log((leftPane ? "Trái: " : "Phải: ") + c.getPackageName());
    }

    public static int cycleRatio(Context context, IVF6PrivilegedService svc, Logger logger) throws Exception {
        int old = getRatio(context);
        int next = old == 50 ? 60 : old == 60 ? 70 : 50;
        prefs(context).edit().putInt(KEY_RATIO, next).apply();
        resizeCurrent(context, svc, logger);
        return next;
    }

    public static void resizeCurrent(Context context, IVF6PrivilegedService svc, Logger logger) throws Exception {
        SharedPreferences p = prefs(context);
        int w = p.getInt(KEY_W, 1280);
        int h = p.getInt(KEY_H, 720);
        int ratio = p.getInt(KEY_RATIO, 50);
        String left = p.getString(KEY_LEFT, null);
        String right = p.getString(KEY_RIGHT, null);
        if (left == null || right == null) return;

        ComponentName lc = ComponentName.unflattenFromString(left);
        ComponentName rc = ComponentName.unflattenFromString(right);
        int leftTask = lc == null ? -1 : findTaskForPackage(svc, lc.getPackageName());
        int rightTask = rc == null ? -1 : findTaskForPackage(svc, rc.getPackageName());
        int split = Math.max(320, Math.min(w - 320, Math.round(w * (ratio / 100f))));

        if (leftTask > 0) svc.exec("am task resize " + leftTask + " 0 0 " + split + " " + h);
        if (rightTask > 0) svc.exec("am task resize " + rightTask + " " + split + " 0 " + w + " " + h);
        logger.log("Layout " + ratio + "/" + (100 - ratio));
    }

    private static void ensureApps(Context context, IVF6PrivilegedService svc, int displayId, Logger logger) throws Exception {
        String left = getLeft(context);
        String right = getRight(context);
        if (left != null) {
            ComponentName c = ComponentName.unflattenFromString(left);
            if (c != null && findTaskForPackage(svc, c.getPackageName()) <= 0) launchComponent(svc, left, displayId, logger);
        }
        if (right != null) {
            ComponentName c = ComponentName.unflattenFromString(right);
            if (c != null && findTaskForPackage(svc, c.getPackageName()) <= 0) launchComponent(svc, right, displayId, logger);
        }
    }

    private static void launchComponent(IVF6PrivilegedService svc, String flat, int displayId, Logger logger) throws Exception {
        ComponentName c = ComponentName.unflattenFromString(flat);
        if (c == null) throw new IllegalArgumentException("Component không hợp lệ: " + flat);
        String cmd = "am start -S --display " + displayId + " --windowingMode 5 -n " + shell(c.flattenToString());
        String out = svc.exec(cmd);
        logger.log("Mở " + c.getPackageName() + ": " + shortLine(out));
    }

    public static void stop(Context context, IVF6PrivilegedService svc, Logger logger) throws Exception {
        SharedPreferences p = prefs(context);
        String old = p.getString(KEY_OLD_OVERLAY, null);
        if (old == null || old.isEmpty() || "null".equals(old.trim())) svc.exec("settings delete global overlay_display_devices");
        else svc.exec("settings put global overlay_display_devices " + shell(old.trim()));
        p.edit().putInt(KEY_DISPLAY_ID, -1).apply();
        logger.log("Đã dọn virtual display.");
    }

    public static void swap(Context context, IVF6PrivilegedService svc, Logger logger) throws Exception {
        SharedPreferences p = prefs(context);
        String a = p.getString(KEY_LEFT, null);
        String b = p.getString(KEY_RIGHT, null);
        p.edit().putString(KEY_LEFT, b).putString(KEY_RIGHT, a).apply();
        resizeCurrent(context, svc, logger);
    }

    public static void key(IVF6PrivilegedService svc, int displayId, int keyCode) throws Exception {
        svc.exec("input -d " + displayId + " keyevent " + keyCode);
    }

    public static void tap(IVF6PrivilegedService svc, int displayId, float x, float y) throws Exception {
        svc.exec("input -d " + displayId + " tap " + Math.round(x) + " " + Math.round(y));
    }

    public static void swipe(IVF6PrivilegedService svc, int displayId,
                             float x1, float y1, float x2, float y2, long durationMs) throws Exception {
        long d = Math.max(80, Math.min(1200, durationMs));
        svc.exec("input -d " + displayId + " swipe " + Math.round(x1) + " " + Math.round(y1) + " "
                + Math.round(x2) + " " + Math.round(y2) + " " + d);
    }

    private static List<Integer> listDisplayIds(IVF6PrivilegedService svc) throws Exception {
        String out = svc.exec("dumpsys display | grep 'mDisplayId=' || true");
        Matcher m = Pattern.compile("mDisplayId=(\\d+)").matcher(out);
        List<Integer> ids = new ArrayList<>();
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private static int findTaskForPackage(IVF6PrivilegedService svc, String pkg) throws Exception {
        String out = svc.exec("am stack list 2>/dev/null || dumpsys activity activities");
        String[] lines = out.split("\\n");
        Pattern p1 = Pattern.compile("taskId=(\\d+)");
        Pattern p2 = Pattern.compile("Task\\{[^#]*#(\\d+)");
        for (String line : lines) {
            if (!line.contains(pkg)) continue;
            Matcher m = p1.matcher(line);
            if (m.find()) return Integer.parseInt(m.group(1));
            m = p2.matcher(line);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String shell(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    private static String shortLine(String s) {
        if (s == null || s.isEmpty()) return "OK";
        String one = s.replace('\n', ' ').trim();
        return one.length() > 120 ? one.substring(0, 120) + "..." : one;
    }
}
