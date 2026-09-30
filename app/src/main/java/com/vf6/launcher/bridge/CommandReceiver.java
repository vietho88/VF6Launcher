package com.vf6.launcher.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.vf6.launcher.session.SessionController;

/**
 * Signature-protected bridge used by the separate Android Auto driving companion.
 * Both APKs must come from the same build/signing key.
 */
public final class CommandReceiver extends BroadcastReceiver {
    public static final String ACTION_COMMAND = "com.vf6.launcher.action.COMMAND";
    public static final String EXTRA_COMMAND = "command";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_COMMAND.equals(intent.getAction())) return;
        String command = intent.getStringExtra(EXTRA_COMMAND);
        if (command == null || command.trim().isEmpty()) return;

        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        ShizukuBridge.withService(app, new ShizukuBridge.Callback() {
            @Override
            public void onReady(IVF6PrivilegedService service) {
                new Thread(() -> {
                    try {
                        switch (command) {
                            case "start":
                                SessionController.start(app, service, m -> {});
                                break;
                            case "ratio":
                                SessionController.cycleRatio(app, service, m -> {});
                                break;
                            case "swap":
                                SessionController.swap(app, service, m -> {});
                                break;
                            case "stop":
                                SessionController.stop(app, service, m -> {});
                                break;
                            case "media_prev":
                                service.exec("input keyevent 88");
                                break;
                            case "media_play_pause":
                                service.exec("input keyevent 85");
                                break;
                            case "media_next":
                                service.exec("input keyevent 87");
                                break;
                            default:
                                break;
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        pending.finish();
                    }
                }, "vf6-command").start();
            }

            @Override
            public void onError(String message) {
                pending.finish();
            }
        });
    }
}
