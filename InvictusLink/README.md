# Invictus Link — Setup guide

Invictus Link lets you send prompts from your Android phone to a **Cursor agent on your own PC** — from anywhere you have cell service or Wi‑Fi.

Traffic travels over a **private VPN** — Tailscale or WireGuard (including optional **Invictus Networks**-style Pi hubs). Your prompts go **only to your PC**, not through a public website or someone else's computer.

This folder has quick-start templates and guides. The full project (Android app, bridge, scripts) is in the repository root. You will run a small **bridge** program on your PC.

**No passwords or API keys are included here** — you create those on your own machine.

---

## What's in this folder

| File | Purpose |
|------|---------|
| `START_HERE.txt` | Quick overview — read first |
| `InvictusLink.apk` | Install this on your Android phone |
| `wireguard-phone-qr.png` | Optional — phone WireGuard QR (you create during VPN setup) |
| `wireguard-phone.conf` | Optional — same phone tunnel as text import |
| `README.md` | This guide (for you) |
| `AGENTS.md` | For Cursor on your PC — AI setup help |
| `bridge-env.example` | Template for `bridge/.env` on your PC |
| `projects.json.example` | Template for which folders the agent may use on your PC |
| `VERSION.txt` | App version |

**WireGuard configs** are created during VPN setup (unique per user). The bridge source is in the repository `bridge/` folder at the project root.

---

## Is it safe and secure?

**Yes — when used as designed, this is a private setup meant for you and people you trust on the same VPN hub.**

Here is why:

| Protection | What it means for you |
|------------|----------------------|
| **WireGuard encryption** | Data between your phone, the Pi hub, and your PC is encrypted. Random people on the internet cannot read it. |
| **Private network only** | The app talks to your PC on an internal address (`10.66.66.x`). The bridge is **not** meant to be exposed on the open internet. |
| **Your own PC, your own keys** | You create the **pairing code** (`BRIDGE_TOKEN`) and **Cursor API key** on your PC. Nobody else should know them. |
| **Fingerprint for pairing** | Connecting the app to your PC can require your fingerprint — so someone with your phone still needs you to approve pairing. |
| **Split tunnel (usual setup)** | Your phone's WireGuard config typically sends **only** VPN traffic through the Pi. Your normal web browsing does not go through the hub. |
| **You choose project folders** | The bridge only lets the agent work in folders you allow on **your** PC. |
| **Risky actions need approval** | Prompts that look destructive (delete, deploy, etc.) can wait for your approval on the phone before running. |

**What a shared Pi hub operator can see:** they can tell that your phone and PC are on the VPN. They **cannot** read your Cursor prompts or agent replies — those go directly between your phone and **your** PC.

**What you should do:** keep your pairing code and Cursor API key private, only use **your** bridge URL, and keep WireGuard turned on when using the app.

---

## What you need before you start

Before you start, you need:

1. **Private network** between phone and PC — Tailscale ([docs/TAILSCALE_SETUP.md](../docs/TAILSCALE_SETUP.md)) or WireGuard ([docs/RASPBERRY_PI_VPN_HUB.md](../docs/RASPBERRY_PI_VPN_HUB.md))  
2. **Your PC's VPN IP address** — e.g. `10.66.66.21` or a Tailscale `100.x.x.x` address  
3. **Bridge source** — the `bridge/` folder in this repository  
4. If you use a **shared Pi hub**, your **hub operator** provides WireGuard configs for your phone and PC

You create on your own PC:

- A **pairing code** (`BRIDGE_TOKEN` in `.env`)  
- Your **Cursor API key** (`CURSOR_API_KEY` in `.env`)

---

## How it fits together

```text
  YOUR PHONE                    PI HUB (shared)              YOUR PC
  Invictus Link app    ──►     WireGuard only      ◄──     Bridge + Cursor
  WireGuard ON                 10.66.66.1                  WireGuard ON
       │                                                      │
       └──────────── http://YOUR-PC-VPN-IP:3003 ──────────────┘
```

Example IPs (your admin will assign yours):

| Device | Example IP |
|--------|------------|
| Pi hub | `10.66.66.1` |
| Your phone | `10.66.66.20` |
| Your PC | `10.66.66.21` |

Your app always uses: **`http://<your-pc-ip>:3003`** — for example `http://10.66.66.21:3003`.

---

## Setup — follow in order

### Step 1 — Install the app

1. Copy `InvictusLink.apk` to your phone.  
2. Open it and tap **Install** (allow "unknown apps" if Android asks).  
3. Open **Invictus Link** and read the welcome screens.

---

### Step 2 — Connect your phone to Invictus Networks (WireGuard)

This step links your phone to the shared Pi VPN so it can reach **your** PC securely.

1. Install **WireGuard** from the Google Play Store (official app by WireGuard Development Team).  
2. Open WireGuard → tap **+**.  
3. **Scan from QR code** and open `wireguard-phone-qr.png` from this folder on your phone (or transfer the image first).  
   - Alternative: **Import from file** → choose `wireguard-phone.conf` from this folder.  
4. Name the tunnel something like `Invictus` or `Invictus Networks`.  
5. Turn the tunnel **ON** (toggle should show "Active").

**Check:** leave WireGuard on whenever you use Invictus Link.

---

### Step 3 — Set up your PC

#### A. WireGuard on your PC

1. Install WireGuard for Windows: https://www.wireguard.com/install/  
2. Import your **PC WireGuard config** (from your VPN setup or hub operator).  
3. Activate the tunnel.  
4. Optional test in PowerShell: `ping 10.66.66.1` (should reply if the VPN is up).

#### B. Bridge (the small server the app talks to)

1. Open the **`bridge/`** folder from this repository on your PC.  
2. Install **Node.js 18+** if you do not have it: https://nodejs.org  
3. Open the `bridge` folder from that project.  
4. Copy `bridge-env.example` from this package to `bridge/.env`.  
5. Copy `projects.json.example` to `bridge/config/projects.json` and set `cwd` to a real folder on **your** PC (where you want the agent to work).  
6. Edit `.env` — use your own secrets:

```env
BRIDGE_TOKEN="make-up-a-long-random-string-only-you-know"
CURSOR_API_KEY="your-cursor-api-key-from-cursor-settings"
PORT=3003
```

7. In PowerShell, from the `bridge` folder:

```powershell
npm install
npm run build
npm start
```

Leave this running while you use the app.

8. **Windows Firewall:** allow **TCP port 3003** from the VPN subnet. Run `scripts/invictus-networks/allow-bridge-firewall.ps1` once as Administrator, or ask Cursor (with `AGENTS.md`) to help.

---

### Step 4 — Connect the app to your PC

1. On your phone: WireGuard **ON**.  
2. Open **Invictus Link** → **Connection** tab.  
3. Check the checklist (VPN active, bridge reachable, paired).  
4. Fill in:
   - **Bridge URL:** `http://<your-pc-vpn-ip>:3003`  
     - Example: `http://10.66.66.21:3003`  
   - **Pairing code:** the exact `BRIDGE_TOKEN` from your PC's `.env` file  
5. Tap **Connect Once** — use fingerprint if asked.  
6. When you see a success message, go to **Home**.

**Test:** type a short message (e.g. "What time is it?") → **Send to PC** → read the reply in **Agent response**.

The app remembers your bridge URL and stays connected after you close it, until you tap **Disconnect** or install an app update.

---

### Step 5 — Using the app day to day

| Tab | Use it for |
|-----|------------|
| **Home** | Send prompts to your PC agent |
| **Activity** | See today's runs, agent log, approve risky actions |
| **Connection** | VPN/bridge status, connect or disconnect |
| **Settings** | Check for app updates from your PC |

**Before each session:** turn WireGuard **ON** on your phone (and make sure the bridge is running on your PC).

---

---

## First install on your phone (one-time QR)

If you do not have the APK yet, your PC bridge can show a **QR code** to download it. You only do this **once**; later versions use **Settings → Check for update** (below).

**Prerequisites:** bridge running, APK published on your PC, VPN on your phone.

1. On your PC, open in a browser (use **your** VPN IP):

   `http://YOUR-PC-VPN-IP:3003/qr?url=http://YOUR-PC-VPN-IP:3003/download/InvictusLink.apk`

2. Scan the QR with your phone → install the APK.

3. Open **Invictus Link** → **Connection** → pair with your bridge URL and pairing code.

**Using Cursor on your PC?** Open `AGENTS.md` or the full guide `docs/FIRST_INSTALL_AND_UPDATES.md` — it has copy-paste prompts for Cursor to build the bridge, publish the APK, and give you the QR URL.

---

## Updates (your app, your PC — independent from everyone else)

**Short answer:** The VPN only connects your phone to **your** PC. It does **not** link app versions between users. Each person updates their own app from **their own** PC bridge.

| Question | Answer |
|----------|--------|
| Do we all share one app version? | **No.** Your phone only talks to the bridge URL you configured (your PC's VPN IP). |
| Who publishes updates for me? | **You do** — on your own PC, with your own Cursor agent and project. |
| Does the hub operator push updates to my phone? | **No** (unless you mistakenly set your bridge URL to someone else's PC). |
| How do I get a newer APK? | **Settings** → **Check for update** → **Install update** (no new QR). Or **Publish update** first if you changed the app on your PC. |

**Typical flow when you change the app:**

1. On your PC: edit the Android project with Cursor, or use **Settings → Publish update** in the app (runs the build on your bridge).  
2. Your bridge serves the new APK at `http://<your-pc-vpn-ip>:3003/download/`.  
3. On your phone: **Settings** → **Check for update** → **Install update**.  
4. After a version bump, **pair again** on the Connection tab (your bridge URL stays saved).

Other users on the same Pi hub do the same on **their** PCs. Your publish does not affect their phones, and theirs does not affect yours.

---

## Troubleshooting

| Problem | Try this |
|---------|----------|
| "Can't reach bridge" | WireGuard ON on phone **and** PC; bridge running (`npm start`); correct URL |
| "Pairing failed" | Pairing code must match `BRIDGE_TOKEN` in `.env` exactly — no extra spaces |
| VPN works, app doesn't | Windows Firewall: allow TCP **3003** from `10.66.66.0/24` |
| Used wrong URL | Must be **your** PC VPN IP, not the Pi (`10.66.66.1`) or someone else's |
| Works at home, not on mobile data | Check router port-forward **UDP 51820** to the Pi (WireGuard) or Tailscale connectivity |
| Agent doesn't run | Check `CURSOR_API_KEY` in `.env` and restart the bridge |

---

## Get help from Cursor on your PC

Open **`AGENTS.md`** from this folder in Cursor on your computer. It tells the AI what Invictus Link is and how to walk you through setup step by step.

---

## Privacy reminder

- Never share your `BRIDGE_TOKEN` or `CURSOR_API_KEY`.  
- Never put them in this folder or in chat with untrusted people.  
- Only connect to **your** PC's bridge URL.

---

*Invictus Networks · Invictus Link · Your universe, Your way*
