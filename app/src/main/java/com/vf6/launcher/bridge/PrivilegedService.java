package com.vf6.launcher.bridge;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Runs as shell/root through Shizuku UserService.
 * Hidden framework APIs are intentionally isolated in this process.
 */
public class PrivilegedService extends IVF6PrivilegedService.Stub {
    private static final String KEY_SURFACE_CONTROL = "surface_control";
    private static final String KEY_ERROR = "error";

    public PrivilegedService() {}
    public PrivilegedService(Context context) {}

    @Override
    public String exec(String command) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "ERROR: timeout\n" + out;
            }
            return out.toString().trim();
        } catch (Throwable t) {
            return "ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    @Override
    public Bundle mirrorDisplay(int displayId) {
        Bundle result = new Bundle();
        try {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            Constructor<?> ctor = surfaceControlClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object outSurfaceControl = ctor.newInstance();

            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Method getWm = wmg.getDeclaredMethod("getWindowManagerService");
            getWm.setAccessible(true);
            Object wm = getWm.invoke(null);

            Class<?> iwm = Class.forName("android.view.IWindowManager");
            Method mirror = iwm.getMethod("mirrorDisplay", int.class, surfaceControlClass);
            Object ok = mirror.invoke(wm, displayId, outSurfaceControl);
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                result.putString(KEY_ERROR, "WindowManager.mirrorDisplay returned false");
                return result;
            }
            result.putParcelable(KEY_SURFACE_CONTROL, (Parcelable) outSurfaceControl);
        } catch (Throwable t) {
            result.putString(KEY_ERROR, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return result;
    }

    @Override
    public void releaseMirror(Bundle mirrorBundle) {
        if (mirrorBundle == null) return;
        try {
            mirrorBundle.setClassLoader(Class.forName("android.view.SurfaceControl").getClassLoader());
            Parcelable p = mirrorBundle.getParcelable(KEY_SURFACE_CONTROL);
            if (p != null) {
                Method release = p.getClass().getMethod("release");
                release.invoke(p);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
