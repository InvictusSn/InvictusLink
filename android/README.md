# InvictusLink Android App

This is a real native Android app project (APK-capable), not a web wrapper.

## Build in Android Studio (recommended)

1. Open the `android` folder in Android Studio.
2. **One-time:** link build outputs so Run works with OneDrive (build files live outside the synced folder):
   ```powershell
   powershell -ExecutionPolicy Bypass -File ..\scripts\link-android-studio-build.ps1
   ```
3. Let Gradle sync.
4. Run on device/emulator, or use **Build > Build APK(s)** for a debug APK.

If you see `Error loading build artifacts ... redirect.txt`, run a Gradle build then the link script above:
```powershell
cd android
.\gradlew.bat assembleDebug
```

## Manual notes

- Default bridge URL in app: `http://100.x.x.x:3003 (set in Connection tab)`
- Change it in the app UI if your PC IP changes.
- If you set `BRIDGE_TOKEN` on the bridge, paste the same token into the app.

## Fast build/publish (no Android Studio UI)

From the repo root, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-and-publish-apk.ps1
```

This script:
- builds `app-debug.apk` via Gradle wrapper
- copies it to `bridge/public/download/InvictusLink.apk`
- refreshes `bridge/public/download/latest.json` from app version values
- prints a ready-to-scan QR URL for install/update

## Run on PC (Android Emulator) for demos / Discord

The app is Android-only. To show the premium UI on your PC screen, use the Android Emulator. **You do not need the bridge** for a visual walkthrough—just install and open the app.

### One-time: create a virtual phone

1. Open the `android` folder in **Android Studio**.
2. **Device Manager** (phone icon) → **Create Device**.
3. Choose **Pixel 10 Pro XL** in the device list (matches your Pixel 10 Pro XL). If Studio does not list it yet, pick the newest **Pixel Pro XL** profile available, or **New Hardware Profile** with a similar tall phone resolution → pick a system image (**API 35+** if offered, else **API 34+**) → **Finish**.

If prompted, install **Android Emulator** and **Google APIs** system image via SDK Manager.

### Each showcase session

From the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\emulator-demo.ps1
```

The script starts your AVD (if needed), waits for boot, and installs `bridge/public/download/InvictusLink.apk`. Open the app and browse screens for Discord (share the **emulator window** only).

Screens that work without any backend: Home, menu drawer, Connection layout, Pending Approval, Daily Digest, LIVE Work Flow (an empty log is fine for demo).

**Optional:** Start the bridge only if you want live Transmit / pairing / real task data:

```powershell
cd bridge && npm run dev
.\scripts\emulator-demo.ps1 -WithBridge
# Connection > Bridge URL: http://10.0.2.2:3003
```

Useful flags:

```powershell
.\scripts\emulator-demo.ps1 -ListAvds          # show AVD names
.\scripts\emulator-demo.ps1 -AvdName "Pixel_6_API_34"
.\scripts\emulator-demo.ps1 -NoStart -SkipInstall   # emulator already open + app installed
.\scripts\emulator-demo.ps1 -WithBridge        # print bridge setup hints
```

