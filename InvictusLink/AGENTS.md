# Invictus Link — Cursor agent guide

Read this file when helping someone set up **Invictus Link** from this repository. It explains what the product is, how the pieces fit together, and how you should assist them.

This document contains **no secrets**. Each user creates their own bridge token and Cursor API key on their PC.

---

## What Invictus Link is

**Invictus Link** is a private Android app that lets someone send text prompts from their phone to a **Cursor agent running on their own PC**.

| Layer | What it does |
|-------|----------------|
| **Phone app** | Compose UI: connect, send prompts, read responses, approve risky actions, check activity |
| **PC bridge** | Small Node/Express server on the user's PC; receives HTTP requests from the phone |
| **Cursor SDK** | Bridge calls `Agent.prompt()` against folders the user allow-listed on their PC |

**Invictus Networks** is optional **WireGuard VPN** connectivity on a Raspberry Pi hub (operator-maintained) or self-hosted. It is **not** the bridge and does **not** run Cursor. It only routes encrypted traffic between each person's phone and **their own** PC on a private `10.66.66.x` network. **Tailscale** is a simpler alternative — see `docs/TAILSCALE_SETUP.md`.

```text
[ User's phone ] ──VPN──► [ Pi hub or Tailscale ] ◄──VPN── [ User's PC + bridge ]
        │                                                      │
        └──────── HTTP to user's PC VPN IP :3003 ──────────────┘
```

Each user connects to **`http://<their-pc-vpn-ip>:3003`** — never another person's IP.

---

## What is in the InvictusLink folder

This folder is a **quick-start kit** (templates and guides). The full open-source tree also includes `android/`, `bridge/`, `docs/`, and `scripts/` at the repository root.

| File | Purpose |
|------|---------|
| `README.md` | Human setup guide (start here with the user) |
| `bridge-env.example` | Template for `bridge/.env` on the user's PC |
| `projects.json.example` | Template for `bridge/config/projects.json` |
| `START_HERE.txt` | One-screen overview |
| `VERSION.txt` | Packaged app version |
| `AGENTS.md` | This file — for you, the Cursor agent |

**WireGuard phone profiles** (QR or `.conf`) are created during VPN setup — they are not secrets and are unique per user. If the user uses a **shared Pi hub**, the hub operator provides phone and PC WireGuard configs separately.

---

## App screens (what to tell the user)

| Tab | Purpose |
|-----|---------|
| **Home** | Type a prompt → **Send to PC** → read agent response |
| **Activity** | Today's stats, agent log, pending approvals |
| **Connection** | VPN checklist, bridge URL, pairing code, connect/disconnect |
| **Settings** | Updates, publish APK (power users), archive version |

**First launch:** setup walkthrough — Invictus branding, then VPN, bridge URL, pairing code.

**Session behavior:** stays paired across app restarts until the user disconnects or installs an app update (then re-pair with the same saved bridge URL).

---

## End-to-end setup (your coaching order)

Help the user in this order. Do not skip VPN setup before bridge connection.

### 1. Private network (pick one)

- **Tailscale** (easiest): install on phone + PC — [TAILSCALE_SETUP.md](../docs/TAILSCALE_SETUP.md)
- **WireGuard + Pi hub**: operator or self-hosted — [RASPBERRY_PI_VPN_HUB.md](../docs/RASPBERRY_PI_VPN_HUB.md)

For a shared Pi hub, the user needs from the **hub operator**:

- WireGuard config for **user's phone** (QR or `.conf`)
- WireGuard config for **user's PC**
- User's assigned **PC VPN IP** (e.g. `10.66.66.21`)

### 2. Phone — VPN

1. **WireGuard:** install official app, import phone tunnel, turn **ON** before opening Invictus Link.
2. **Tailscale:** install app, sign in, ensure connected.

### 3. PC — bridge

1. Open the repository `bridge/` folder (or unzip a bridge source copy).
2. Ensure **Node.js 18+** is installed.
3. Copy `bridge/.env.example` → `bridge/.env` (or `InvictusLink/bridge-env.example` as reference).
4. Copy `projects.json.example` → `bridge/config/projects.json`; set `cwd` to a real path on the user's PC.
5. Set `BRIDGE_TOKEN` (long random string — the app's "pairing code").
6. Set `CURSOR_API_KEY` (user's Cursor account).
7. `npm install` → `npm run build` → `npm start`.
8. Windows + WireGuard: run `scripts/invictus-networks/allow-bridge-firewall.ps1` as Administrator (TCP 3003 from `10.66.66.0/24`).
9. Activate **PC WireGuard tunnel** if using WireGuard.

### 4. Phone — Invictus Link

1. Install the APK (`release/InvictusLink.apk` or build from `android/`).
2. **Connection** tab:
   - Bridge URL: `http://<user-pc-vpn-ip>:3003`
   - Pairing code: value of `BRIDGE_TOKEN` from their `.env`
3. Tap **Connect Once** (biometric if prompted).
4. **Home** → send a test prompt.

---

## Security model (explain this to users)

When users ask "is this safe?", use these points:

1. **Private VPN, not the public internet** — The bridge is reached over Tailscale or WireGuard. It is not meant to be port-forwarded to the world as an open HTTP API.
2. **Encryption in transit** — VPN traffic is encrypted between phone and PC.
3. **User-owned credentials** — Each user sets their own `BRIDGE_TOKEN` and `CURSOR_API_KEY` on their PC. This repository ships without them.
4. **Pairing + session** — Phone must present the bridge token once to obtain a session token. Biometric may be required for connect and approvals.
5. **Split tunnel (typical WireGuard phone config)** — Only traffic to the VPN subnet goes through the Pi; general browsing does not route through the hub.
6. **Project allowlist** — The bridge only runs agents in folders listed in `bridge/config/projects.json` on that PC.
7. **Risky prompt gate** — Destructive-sounding prompts can require explicit phone approval before the agent runs.
8. **Hub operator scope** — On a shared Pi, the operator can add/remove WireGuard peers and see that devices are on the VPN, but **cannot read Cursor prompts** unless the user shares them. Prompt content flows phone → user's PC only.

---

## Common problems and fixes

| Symptom | Likely cause | What to do |
|---------|--------------|------------|
| Bridge unreachable | VPN off on phone or PC | Turn tunnels on; verify connectivity |
| Bridge unreachable | Bridge not running | `npm start` in `bridge/`; check port 3003 |
| Bridge unreachable | Windows Firewall | Allow TCP 3003 from VPN subnet |
| Pairing failed | Token mismatch | `BRIDGE_TOKEN` in `.env` must match pairing code exactly |
| Wrong PC / no response | Wrong URL | URL must be **user's** VPN IP, not the hub or another user |
| Works on Wi‑Fi, not cellular | Pi endpoint / port-forward | Check UDP 51820 to Pi; user may need updated Endpoint in WG config |
| Session lost after update | By design | Re-pair on Connection tab; URL should still be saved |
| Activity empty | Not paired or no tasks yet | Connect first; send a prompt; check Activity → Agent log |
| Agent errors | Missing `CURSOR_API_KEY` | Set in `.env` and restart bridge |

---

## What you should and should not do

**Do:**

- Walk the user through `README.md` and `docs/FIRST_INSTALL_AND_UPDATES.md` step by step.
- Help them create `bridge/.env`, start the bridge, and fix firewall issues.
- Help them edit `bridge/config/projects.json` to add their project folders.
- Use this file + project docs as source of truth for Invictus Link behavior.
- Reassure them using the security model above when they ask about VPN/shared hubs.

**Do not:**

- Put real `BRIDGE_TOKEN`, `CURSOR_API_KEY`, or hub operator secrets into chat logs or commits.
- Tell them to use another person's bridge URL or VPN IP.
- Expose the bridge to `0.0.0.0` on the public internet without understanding the risk.
- Assume the Pi runs Cursor or the bridge — it only runs WireGuard (when used).

---

## Quick reference — bridge API (for debugging)

Base URL: `http://<user-pc-vpn-ip>:3003`

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /health` | None | Liveness check |
| `POST /auth/login` | Bridge token | Pair phone → session token |
| `POST /tasks` | Session | Submit prompt |
| `GET /tasks/:id` | Session | Poll task status |
| `GET /admin/pending-approvals` | Session | List approvals |
| `POST /admin/approve-task/:id` | Session | Approve risky task |
| `GET /download/latest.json` | None | OTA update manifest |
| `GET /download/InvictusLink.apk` | None | APK file |
| `GET /qr?url=` | None | QR install page (encode APK URL) |
| `POST /api/sessions` | Session | Create workspace session |
| `DELETE /api/sessions/:id` | Session | Delete session |

---

## App updates — independent per user

The VPN hub does **not** synchronize app versions. Each user's phone uses the **bridge URL saved on that phone** (their PC's VPN IP) for:

- `GET /download/latest.json` — **Check for update**
- `GET /download/InvictusLink.apk` — direct APK download
- `POST /admin/build-apk` — **Publish update** (from Settings)
- `GET /qr?url=…` — HTML page with QR (first install only)

### First install (one-time QR)

1. Help user build and publish APK: `scripts/build-and-publish-apk.ps1 -BaseUrl "http://<user-pc-vpn-ip>:3003"`.
2. Bridge must be running; phone VPN on.
3. User opens on PC browser:
   `http://<user-pc-vpn-ip>:3003/qr?url=http://<user-pc-vpn-ip>:3003/download/InvictusLink.apk`
4. Scan → install APK → pair on Connection tab.

Full walkthrough: `docs/FIRST_INSTALL_AND_UPDATES.md`.

### Later updates (no QR, no Cursor required)

1. User changes code on **their** PC (optional).
2. **Publish update** in app Settings, or run `build-and-publish-apk.ps1 -AutoBump` on PC.
3. **Settings → Check for update → Install update** on phone.
4. Re-pair on Connection tab if session was cleared by the install.

Do not tell users to point at another person's bridge URL.

---

## Version

Check `VERSION.txt` in this folder for the packaged APK version. Newer builds come from the user's own bridge after they publish.

---

*For humans: see `README.md`. For building from scratch: see `REMOTE_AGENT_BLUEPRINT.txt` in the repository root.*
