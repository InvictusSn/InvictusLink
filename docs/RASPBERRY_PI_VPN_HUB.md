# Self-host a Raspberry Pi WireGuard VPN hub

This guide explains how to run **Invictus Networks-style** connectivity: a Raspberry Pi as a 24/7 WireGuard hub so phones and PCs reach each other on a private `10.66.66.x` network.

Invictus Link does **not** run on the Pi — only **WireGuard** (and optionally a small HTTP proxy). Each user runs the **bridge on their own PC**.

---

## What the Pi does

| On Pi | On each PC | On each phone |
|-------|------------|---------------|
| WireGuard hub | Bridge + Cursor | Invictus Link + WireGuard |

```text
[ Peer phone ] ──┐
[ Peer PC ]    ──┼──► [ Pi WireGuard ] ◄── [ Your phone ]
[ Your PC ]    ──┘         10.66.66.1          [ Your PC ]
```

Each person’s app uses **`http://<their-pc-vpn-ip>:3003`** — never someone else’s PC IP.

---

## Hardware

- Raspberry Pi 3B+ or newer (Pi 4/5 recommended if you add more services later)
- Ethernet to router (preferred for 24/7)
- MicroSD (high endurance) or SSD for Pi 5
- Official PSU

---

## Phase 1 — OS

1. Flash **Raspberry Pi OS Lite (64-bit)** with Raspberry Pi Imager.
2. Enable SSH, set hostname (e.g. `vpn-hub`), user/password.
3. Boot, SSH in, run updates:

```bash
sudo apt update && sudo apt full-upgrade -y
sudo reboot
```

4. Set **DHCP reservation** on your router for the Pi’s MAC → fixed LAN IP (e.g. `192.168.1.50`).

---

## Phase 2 — WireGuard on the Pi

```bash
sudo apt install -y wireguard
mkdir -p ~/wg-keys && chmod 700 ~/wg-keys
cd ~/wg-keys

# Pi (server)
wg genkey | tee pi-private.key | wg pubkey > pi-public.key

# Your PC
wg genkey | tee you-pc-private.key | wg pubkey > you-pc-public.key

# Your phone
wg genkey | tee you-phone-private.key | wg pubkey > you-phone-public.key
```

### IP plan (example)

| Device | VPN IP |
|--------|--------|
| Pi hub | `10.66.66.1` |
| Your phone | `10.66.66.10` |
| Your PC | `10.66.66.11` |
| User B phone | `10.66.66.20` |
| User B PC | `10.66.66.21` |

### `/etc/wireguard/wg0.conf` (Pi server)

```ini
[Interface]
Address = 10.66.66.1/24
ListenPort = 51820
PrivateKey = PASTE_PI_PRIVATE_KEY
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE

[Peer]
# Your PC
PublicKey = PASTE_YOU_PC_PUBLIC_KEY
AllowedIPs = 10.66.66.11/32

[Peer]
# Your phone
PublicKey = PASTE_YOU_PHONE_PUBLIC_KEY
AllowedIPs = 10.66.66.10/32
```

Replace `eth0` with your LAN interface if different (`wlan0`, etc.).

```bash
sudo chmod 600 /etc/wireguard/wg0.conf
echo 'net.ipv4.ip_forward=1' | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
sudo systemctl enable wg-quick@wg0
sudo systemctl start wg-quick@wg0
```

---

## Phase 3 — Router

| Setting | Value |
|---------|--------|
| Protocol | UDP |
| External port | 51820 |
| Internal IP | Pi LAN IP |
| Internal port | 51820 |

For phone use **away from home**, set WireGuard **Endpoint** to your home **public IP** or a **DuckDNS** hostname (see storm recovery below).

---

## Phase 4 — PC client (Windows)

Install WireGuard, create tunnel:

```ini
[Interface]
PrivateKey = PASTE_YOU_PC_PRIVATE_KEY
Address = 10.66.66.11/24

[Peer]
PublicKey = PASTE_PI_PUBLIC_KEY
Endpoint = YOUR_HOME_PUBLIC_IP:51820
AllowedIPs = 10.66.66.0/24
PersistentKeepalive = 25
```

At home on LAN you can use `Endpoint = 192.168.x.x:51820` (Pi LAN IP).

---

## Phase 5 — Phone client

```ini
[Interface]
PrivateKey = PASTE_YOU_PHONE_PRIVATE_KEY
Address = 10.66.66.10/24

[Peer]
PublicKey = PASTE_PI_PUBLIC_KEY
Endpoint = YOUR_HOME_PUBLIC_IP:51820
AllowedIPs = 10.66.66.0/24
PersistentKeepalive = 25
```

Import in **WireGuard** app (QR or file).

**Important:** `AllowedIPs = 10.66.66.0/24` lets the phone reach Pi **and** other peers (your PC).

---

## Phase 6 — PC bridge + firewall

1. Start bridge on PC ([PC_BRIDGE_SETUP.md](PC_BRIDGE_SETUP.md)).
2. Windows firewall for VPN subnet:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\invictus-networks\allow-bridge-firewall.ps1
```

3. In Link: **`http://10.66.66.11:3003`** (your PC VPN IP).

### Pi link proxy (optional)

If phone → PC peer routing is flaky, run a TCP proxy on the Pi at `10.66.66.1:3003` → PC LAN IP. See `scripts/invictus-networks/setup-pi-link-proxy.sh` in the repo.

---

## Phase 7 — Invictus Link

1. WireGuard **ON** on phone.
2. Connection URL: `http://10.66.66.11:3003` (or `http://10.66.66.1:3003` if using Pi proxy).
3. Pair with `BRIDGE_TOKEN`.

---

## Survive power / ISP outages

| Problem | Mitigation |
|---------|------------|
| Pi reboot | `systemctl enable wg-quick@wg0`; optional `invictus-boot-recovery` service in repo |
| Public IP changed | **DuckDNS** on Pi — document your recovery steps for endpoint updates |
| Phone won’t reconnect | Toggle WireGuard off/on |
| At home | Use phone profile with `Endpoint = <pi-lan-ip>:51820` |

---

## Adding additional users

1. Generate new key pairs per user on the Pi.
2. Assign unique `.20/.21`, `.30/.31`, etc.
3. Add `[Peer]` blocks to `wg0.conf`.
4. Provide each user: phone QR/conf, PC conf (separately), bridge setup instructions, and their bridge URL `http://10.66.66.21:3003`.

---

## Security notes

- Pi sees VPN metadata (who is online), not Cursor prompt content.
- Do **not** port-forward TCP 3003 — only UDP 51820 to the Pi.
- Consider per-user `AllowedIPs` isolation if you do not trust all peers on the hub.

For the full original walkthrough with more detail, see `docs/PI3_WIREGUARD_HUB.md` in the repository root.
