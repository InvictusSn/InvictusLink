# Connect Invictus Link with Tailscale

**Tailscale** is the fastest way to use Invictus Link: no Raspberry Pi, no port forwarding, no changing public IP after storms.

Both phone and PC join the same tailnet. The app already detects Tailscale IPs (`100.64.0.0/10`) and can open the Tailscale app from the Connection tab.

---

## Overview

```text
[ Phone + Tailscale ]  ──encrypted tailnet──  [ PC + Tailscale + bridge :3003 ]
```

---

## Step 1 — Install Tailscale

| Device | Action |
|--------|--------|
| **PC** | Install from [tailscale.com/download](https://tailscale.com/download) |
| **Phone** | Install **Tailscale** from Play Store |

Sign in with the **same account** (or shared tailnet) on both devices.

---

## Step 2 — Find your PC’s Tailscale IP

On the PC:

```powershell
# Windows PowerShell
tailscale ip -4
```

Example: `100.x.x.x` (your PC’s Tailscale IPv4)

Or open the Tailscale admin console → Machines → your PC.

---

## Step 3 — Start the bridge on your PC

See [PC_BRIDGE_SETUP.md](PC_BRIDGE_SETUP.md).

```powershell
cd bridge
npm install
npm run build
# Edit .env — set BRIDGE_TOKEN, CURSOR_API_KEY, PORT=3003
npm start
```

Test on the PC:

```powershell
curl http://localhost:3003/health
```

---

## Step 4 — Invictus Link on your phone

1. Turn **Tailscale ON** on the phone (verify connected in Tailscale app).
2. Open **Invictus Link** → **Connection**.
3. **Bridge URL:** `http://100.x.x.x:3003` (your PC’s Tailscale IP from step 2).
4. **Pairing code:** same as `BRIDGE_TOKEN` in `bridge/.env`.
5. Tap **Connect Once** (fingerprint if prompted).
6. **Test connection** → should show bridge reachable.

---

## Step 5 — Send a prompt

**Home** → type a short test prompt → **Send to PC**.

---

## Optional: MagicDNS

If you enable MagicDNS in Tailscale, you can use:

```text
http://your-pc-hostname.tail12345.ts.net:3003
```

instead of the numeric `100.x.x.x` IP. Either works in the app.

---

## Firewall

Tailscale traffic is usually allowed on Windows. If the phone cannot reach the bridge:

- Confirm Tailscale is **active** on both devices
- Confirm bridge listens on `0.0.0.0` (default Express behavior)
- Temporarily disable third-party firewalls to test

---

## Tailscale vs self-hosted WireGuard

| | Tailscale | Pi WireGuard hub |
|--|-----------|------------------|
| Setup time | Minutes | Hours |
| Public IP changes | No impact | Need DDNS or manual endpoint update |
| Multi-user hub | Tailscale sharing | Your Pi design |
| Dependency | Tailscale Inc. | Your hardware |

You can document both paths in your README and let users choose.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| “Install Tailscale” in app | Install app; Connection tab → Open Tailscale |
| Bridge not reachable | Wrong IP? Bridge running? Tailscale on? |
| VPN active but no bridge | Use PC tailnet IP, not phone’s |
| Pairing 401 | `BRIDGE_TOKEN` mismatch |
