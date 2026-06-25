# First install, QR download, and in-app updates

This guide is for **you** (the PC owner) setting up Invictus Link for the first time. You can do everything yourself, or paste the prompts below into **Cursor on your PC** and let the agent run the steps for you.

**After the first install, you never need a new QR code** — the app updates itself from your bridge.

---

## Overview

| Step | Once or repeat? | How |
|------|-----------------|-----|
| Install bridge + `.env` | Once | Cursor or manual |
| Build & publish APK to bridge | First time + when you change the app | Script or Cursor |
| Scan QR to install app on phone | **Once** (first install) | Bridge `/qr` page |
| Pair phone with bridge | Once per install (re-pair after app update) | Connection tab |
| Get newer app versions | Anytime | **Settings → Check for update** (no QR, no Cursor) |

---

## What your phone needs

1. **Private network** to your PC — Tailscale or WireGuard (see [TAILSCALE_SETUP.md](TAILSCALE_SETUP.md) or [RASPBERRY_PI_VPN_HUB.md](RASPBERRY_PI_VPN_HUB.md)).
2. **Bridge URL** saved in the app — e.g. `http://100.x.x.x:3003` or `http://10.66.66.11:3003`.
3. For the **first** APK install only: VPN on, bridge running, then scan the install QR (below).

---

## Part 1 — Ask Cursor to set up the bridge

Open the project in **Cursor** on your PC. Start a new agent chat and paste:

```text
Help me set up the Invictus Link bridge on this PC.

1. In bridge/: run npm install and npm run build.
2. If bridge/.env does not exist, copy bridge/.env.example to bridge/.env.
3. Tell me to set BRIDGE_TOKEN (long random pairing code) and CURSOR_API_KEY in bridge/.env — do not invent real secrets for me.
4. Edit bridge/config/projects.json so cwd points to a real workspace folder on this PC.
5. Set PORT=3003 in .env and PUBLIC_URL to my reachable URL (Tailscale 100.x.x.x or WireGuard 10.66.66.x).
6. Start the bridge with npm start (or scripts/invictus-networks/start-bridge.ps1).
7. Confirm GET http://localhost:3003/health returns ok: true.

Use docs/PC_BRIDGE_SETUP.md and InvictusLink/AGENTS.md as reference.
```

Keep the bridge terminal **open** while you use the app.

**Windows + WireGuard:** also ask Cursor to run `scripts/invictus-networks/allow-bridge-firewall.ps1` as Administrator.

---

## Part 2 — Ask Cursor to build the app and publish it to your bridge

After the bridge is running, paste:

```text
Build the Invictus Link Android APK and publish it to my bridge for phone install and OTA updates.

1. Run: powershell -ExecutionPolicy Bypass -File scripts/build-and-publish-apk.ps1 -BaseUrl "http://MY-PC-IP:3003"
   (Replace MY-PC-IP with my Tailscale or WireGuard IP — same host I will use in the phone app.)
2. Confirm bridge/public/download/InvictusLink.apk exists.
3. Confirm bridge/public/download/latest.json has versionCode and versionName.
4. Tell me the install QR URL (see docs/FIRST_INSTALL_AND_UPDATES.md).
```

Or run the script yourself:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-and-publish-apk.ps1 -BaseUrl "http://100.x.x.x:3003"
```

Use **your** PC’s VPN/LAN IP — the same address you will enter in the app Connection tab.

Optional: add `-AutoBump` to increment `versionCode` / `versionName` in `android/app/build.gradle.kts` before building.

---

## Part 3 — QR code for first phone install (one time)

The bridge can show a **scannable QR** that points to your APK download URL.

### 1. Prerequisites

- Bridge is **running** on your PC.
- APK was **published** (Part 2).
- Phone has **Tailscale or WireGuard ON** (so it can reach your PC IP).

### 2. Open the install QR page on your PC

Replace `YOUR-PC-IP` with your real address (Tailscale `100.x.x.x` or WireGuard `10.66.66.x`):

```text
http://YOUR-PC-IP:3003/qr?url=http://YOUR-PC-IP:3003/download/InvictusLink.apk
```

Example (WireGuard):

```text
http://10.66.66.11:3003/qr?url=http://10.66.66.11:3003/download/InvictusLink.apk
```

Open that URL in a browser on your PC. You will see a large QR code.

### 3. Scan with your phone

1. Turn on **VPN** on the phone.
2. Open the **camera** or a QR scanner.
3. Scan the code → browser opens → download `InvictusLink.apk`.
4. Allow **Install unknown apps** when prompted.
5. Install **Invictus Link**.

### 4. Pair in the app

1. **Connection** tab → Bridge URL: `http://YOUR-PC-IP:3003`
2. Pairing code: same as `BRIDGE_TOKEN` in `bridge/.env`
3. **Connect Once** (fingerprint if asked).
4. **Home** → create or pick a session → send a test prompt.

You do **not** need another QR for future app versions.

---

## Part 4 — Updates from inside the app (no QR, no Cursor)

Once the app is installed and paired, **all future updates** come from **your PC bridge** — the same URL in Connection.

### How it works

Your bridge serves:

- `GET /download/latest.json` — version info
- `GET /download/InvictusLink.apk` — the APK file

The phone compares `versionCode` in the manifest to the installed app. If the bridge has a higher version, **Settings** offers **Install update**.

### When you change the app on your PC

**Option A — From the phone (easiest)**

1. Edit code on your PC.
2. On the phone: **Settings → Publish update** (requires pairing; runs the build on your PC via the bridge).
3. **Settings → Check for update → Install update**.
4. After installing, open **Connection** and **Connect Once** again if prompted (sessions reset on app update).

**Option B — From your PC (script)**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-and-publish-apk.ps1 -BaseUrl "http://YOUR-PC-IP:3003" -AutoBump
```

Then on the phone: **Settings → Check for update → Install update**.

### Daily “am I up to date?”

**Settings → Check for update** — no PC Cursor session needed. Requirements:

- VPN on (if you use Tailscale/WireGuard)
- Bridge running on PC
- Bridge URL correct in Connection tab

If you see “You're on the latest version”, nothing to do.

---

## Quick reference — URLs on your bridge

| URL | Purpose |
|-----|---------|
| `/health` | Test bridge is up |
| `/download/latest.json` | OTA version manifest (app uses this) |
| `/download/InvictusLink.apk` | Direct APK download |
| `/qr?url=…` | QR page for any URL (use APK URL for first install) |

`latest.json` rewrites `apkUrl` to match the host your phone used, so Tailscale and WireGuard URLs both work.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| QR scan does nothing | VPN on phone? Correct `YOUR-PC-IP`? Bridge running? |
| Download 404 | Run `build-and-publish-apk.ps1` first |
| Check for update fails | Bridge down, wrong URL, or VPN off |
| Update available but install fails | Allow installs from browser / package installer |
| After update, prompts fail | **Connection → Connect Once** to re-pair |
| Publish update fails from phone | PC must have Android SDK/Java; bridge token valid |

---

## Prompt for Cursor — “show me the install QR only”

```text
My Invictus Link bridge is running at http://MY-PC-IP:3003 and the APK is already published.
Give me the full browser URL for the install QR page so I can scan it with my phone.
Format: http://MY-PC-IP:3003/qr?url=http://MY-PC-IP:3003/download/InvictusLink.apk
```

---

## See also

- [PC_BRIDGE_SETUP.md](PC_BRIDGE_SETUP.md) — bridge configuration
- [USER_GUIDE.md](USER_GUIDE.md) — daily app use
- [BUILD_AND_RELEASE.md](BUILD_AND_RELEASE.md) — version numbers and release checklist
- `InvictusLink/AGENTS.md` — longer Cursor agent reference
