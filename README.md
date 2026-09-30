# VF6 Launcher v0.2

Experimental Android Auto launcher project for Samsung / VF6 testing.

## Two modes

### 1) Parked split launcher (`app-debug.apk`)
- Shizuku-powered virtual display.
- Two independent Android apps on one secondary display.
- Left/right app picker on the car screen.
- 50/50, 60/40, 70/30, Swap.
- Direct touch forwarded to the virtual display.
- No MediaProjection bitmap pipeline.

### 2) Driving companion (`driving-debug.apk`)
Built with AndroidX Car App Library 1.7.0.
- Android Auto template UI intended for driving-safe controls.
- Start/reconnect the configured virtual session.
- Cycle ratio and Swap.
- Global Previous / Play-Pause / Next media controls.
- Launch Android Auto navigation to a destination configured on the phone, using `CarContext.ACTION_NAVIGATE`.
- Stop the virtual session.

The driving companion intentionally does **not** bypass Android Auto's driver-distraction restrictions or embed arbitrary video/social-app surfaces while the car is moving. Full arbitrary-app rendering/touch remains the parked mode.

## Install

Build both APKs in the same GitHub Actions run so the signature-protected control permission matches.

1. Install `app-debug.apk` (main). On devices where Android Auto filters sideloaded apps, use the same installation method that works for your Fermata setup (for example KingInstaller).
2. Install `driving-debug.apk` from the **same Actions run**.
3. Start Shizuku and grant VF6 Launcher permission.
4. Open VF6 Launcher on the phone, select left/right apps, resolution and ratio, then START VF6.
5. Open `VF6 Launcher Driving` on the phone once and save an optional quick-navigation destination.
6. In Android Auto, open `VF6 Launcher Driving` for the template-based driving controls.

## Build

GitHub Actions builds:
- `VF6Launcher-v0.2-main-debug-apk`
- `VF6Launcher-v0.2-driving-debug-apk`

## Notes

This is experimental and not validated on every Samsung / Android Auto / VF6 firmware combination. Hidden SurfaceControl/WindowManager APIs, overlay displays and task resize behavior are OEM-dependent.
