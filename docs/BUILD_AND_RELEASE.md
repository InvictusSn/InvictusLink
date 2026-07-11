# Build and release

How to build Invictus Link APK and publish updates through your PC bridge.

**New users:** see [FIRST_INSTALL_AND_UPDATES.md](FIRST_INSTALL_AND_UPDATES.md) for Cursor agent prompts, the **one-time install QR**, and how **in-app updates** replace QR after the first install.

---

## Build APK (debug)

```powershell
cd android
.\gradlew.bat assembleDebug
```

Output (default build dir):

```text
%LOCALAPPDATA%\InvictusLinkBuild\android-app\outputs\apk\debug\app-debug.apk
```

Rename for release: `InvictusLink.apk`

---

## Version numbers

Edit `android/app/build.gradle.kts`:

```kotlin
versionCode = 119   // increment every release
versionName = "2.0"
```

Sync `InvictusLink/VERSION.txt` when cutting a release.

---

## Publish to bridge (OTA)

1. Copy APK to `bridge/public/download/`:

```powershell
Copy-Item "$env:LOCALAPPDATA\InvictusLinkBuild\android-app\outputs\apk\debug\app-debug.apk" `
  "bridge\public\download\InvictusLink.apk"
```

2. Write manifest `bridge/public/download/latest.json`:

```json
{
  "versionCode": 119,
  "versionName": "2.0",
  "apkUrl": "/download/InvictusLink.apk"
}
```

(`apkUrl` is rewritten at request time to match the caller’s host.)

3. Start bridge; on phone: **Settings → Check for update → Install**.

Or use:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-and-publish-apk.ps1 -BaseUrl "http://YOUR-PC-IP:3003"
```

Optional auto-bump version:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-and-publish-apk.ps1 -BaseUrl "http://YOUR-PC-IP:3003" -AutoBump
```

### First-install QR (one time)

After publishing, open in a PC browser (replace `YOUR-PC-IP`):

```text
http://YOUR-PC-IP:3003/qr?url=http://YOUR-PC-IP:3003/download/InvictusLink.apk
```

Phone: VPN on → scan → install APK. Details: [FIRST_INSTALL_AND_UPDATES.md](FIRST_INSTALL_AND_UPDATES.md).

### After first install

Users update from the app: **Settings → Check for update → Install update**. No new QR.

---

## GitHub Release checklist

1. Tag `v2.0`
2. Attach `InvictusLink.apk`
3. Paste [RELEASE_NOTES_2.0.md](../RELEASE_NOTES_2.0.md) in release notes
4. Link to `docs/TAILSCALE_SETUP.md` and `docs/RASPBERRY_PI_VPN_HUB.md`
