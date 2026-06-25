# Invictus Link — User guide

Install the app, connect to **your** PC bridge, send prompts, read replies.

---

## Requirements

- Android 8+ (API 26+)
- A PC running the Invictus Link **bridge** (see [PC_BRIDGE_SETUP.md](PC_BRIDGE_SETUP.md))
- **Private network** to your PC:
  - [Tailscale](TAILSCALE_SETUP.md) (recommended), or
  - [WireGuard + Pi hub](RASPBERRY_PI_VPN_HUB.md), or
  - Same home Wi‑Fi (LAN IP only — not for use away from home)

---

## Install the app

1. Download `InvictusLink.apk` from a GitHub Release or build from source ([BUILD_AND_RELEASE.md](BUILD_AND_RELEASE.md)).
2. Allow **Install unknown apps** for your browser or file manager.
3. Open **Invictus Link**.

---

## First-time setup

### 1. Private network

Turn on **Tailscale** or **WireGuard** on your phone before connecting in the app.

### 2. Connection tab

| Field | Example |
|-------|---------|
| **Bridge URL** | `http://100.x.x.x:3003` (Tailscale) or `http://10.66.66.11:3003` (WireGuard) |
| **Pairing code** | Same as `BRIDGE_TOKEN` in your PC `bridge/.env` |

Tap **Connect Once** — fingerprint may be required.

### 3. Checklist (Connection tab)

All should be green:

- VPN active (if using Tailscale or `10.66.66.x` URL)
- Bridge reachable
- Paired with PC

Tap **Test connection** if unsure.

---

## Home tab

1. Pick a **session** from the dropdown (each session is a folder on your PC workspace).
2. Tap **New Session** in the menu to create another project folder on the PC.
3. Use the **×** next to a session to delete it (you will be asked to confirm).
4. Type a prompt → **Send to PC**.
5. Wait for status: Sending → Running → Done.
6. Expand **Response** for the agent summary.
7. **History** — last 20 exchanges; tap to resend.

Long tasks: bridge times out at **10 minutes** by default.

---

## Activity tab

| Section | Purpose |
|---------|---------|
| **Digest** | Today’s run stats from the bridge |
| **Agent log** | Recent prompts and outcomes |
| **Approvals** | Risky prompts waiting for your tap to approve |

---

## Settings tab

- **Check for update** — compares your installed app to `bridge/public/download/latest.json` on **your** PC (via the Connection URL). No QR code needed.
- **Install update** — appears when a newer `versionCode` is published on your bridge.
- **Publish update** — builds a new APK on your PC and copies it to the bridge (for power users; same result as `scripts/build-and-publish-apk.ps1`).
- **Archive current version** — backup before publishing.

### First install vs later updates

| | First install | Later updates |
|---|---------------|---------------|
| **How** | Scan QR from PC bridge (once) or sideload APK | **Settings → Check for update → Install** |
| **Needs Cursor?** | Optional (Cursor can build bridge + APK + give QR URL) | **No** |
| **Needs new QR?** | Yes, once | **No** |

See [FIRST_INSTALL_AND_UPDATES.md](FIRST_INSTALL_AND_UPDATES.md) for Cursor prompts, QR URL format, and publish steps.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Bridge not reachable | VPN on? Correct URL? Bridge running on PC? |
| Tailscale URL | Install Tailscale; use PC’s `100.x.x.x` IP |
| WireGuard URL | Use your PC’s VPN IP, not the Pi’s `.1` (unless using a hub proxy) |
| Pairing fails | `BRIDGE_TOKEN` must match `.env` exactly |
| Stuck on Sending | PC asleep? Cursor API key set? Check bridge terminal |
| New Session fails (404) | Restart the PC bridge after updating (`npm run build` then `npm start`) |
| No sessions listed | Create one with **New Session** (requires pairing) |

---

## Security tips

- Do **not** expose port 3003 to the public internet.
- Keep pairing code and Cursor API key **only on your PC**.
- Use fingerprint pairing on shared phones.
- Only allow workspace folders you trust in `bridge/config/projects.json` (workspace root).
