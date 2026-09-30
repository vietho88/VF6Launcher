package com.vf6.launcher.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.vf6.launcher.BuildConfig;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public interface Callback {
        void onReady(IVF6PrivilegedService service);
        void onError(String message);
    }

    public static final int REQUEST_CODE = 9001;
    private static IVF6PrivilegedService service;
    private static boolean binding;
    private static final List<Callback> waiting = new ArrayList<>();

    private ShizukuBridge() {}

    public static boolean binderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean permissionGranted() {
        try {
            return binderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        if (!binderAlive()) return;
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE);
        }
    }

    public static synchronized void withService(Context context, Callback callback) {
        if (service != null) {
            callback.onReady(service);
            return;
        }
        if (!permissionGranted()) {
            callback.onError("Shizuku chưa chạy hoặc chưa được cấp quyền");
            return;
        }
        waiting.add(callback);
        if (binding) return;
        binding = true;

        ComponentName component = new ComponentName(context.getPackageName(), PrivilegedService.class.getName());
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(component)
                .processNameSuffix("vf6priv")
                .daemon(false)
                .debuggable(BuildConfig.DEBUG)
                .version(1);

        Shizuku.bindUserService(args, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                synchronized (ShizukuBridge.class) {
                    service = IVF6PrivilegedService.Stub.asInterface(binder);
                    binding = false;
                    for (Callback cb : waiting) cb.onReady(service);
                    waiting.clear();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                synchronized (ShizukuBridge.class) {
                    service = null;
                    binding = false;
                }
            }
        });
    }
}
