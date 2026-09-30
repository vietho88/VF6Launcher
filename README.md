# VF6 Launcher v0.3

Experimental single-APK Android Auto launcher for Samsung / VF6 testing.

## One APK, two car interfaces

- **Driving:** `VF6 Launcher` uses an Android for Cars list template for a saved navigation destination, session controls, and media keys. It is part of `app-debug.apk`; a second APK is no longer needed.
- **Parked:** The same APK contains the split-screen `CarActivity`, which mirrors a virtual display and forwards touch to two selected phone apps. Android Auto decides when this parked interface is available.

The driving template does not show arbitrary app surfaces. Android Auto does not offer a general launcher category for third-party apps, so availability of this sideloaded prototype in the car launcher depends on the Android Auto host and device. The service currently declares the IoT category for testing; its functionality is not an IoT app and this declaration should not be treated as suitable for Play distribution.

The driving screen's Start action can start the configured apps on a phone-side virtual display, but it does not make those surfaces visible through the driving template.

## Install and use

1. Build and install `app-debug.apk`. If an older separate `VF6 Launcher Driving` APK is installed, uninstall it to avoid two entries.
2. Start Shizuku and grant VF6 Launcher permission on the phone.
3. Open VF6 Launcher on the phone. Select the two apps and split ratio, then save a quick navigation destination if desired.
4. Open VF6 Launcher in Android Auto. Use the driving template while moving. When parked and supported by the host, use the split display interface.

The project has not been validated on every Samsung / Android Auto / VF6 firmware combination. Hidden SurfaceControl/WindowManager APIs, overlay displays and task resizing are device dependent. Test with Android Auto Desktop Head Unit and on the target vehicle before relying on the workflow.
