# Invictus Link — Product summary

> **Your universe, Your way**

Invictus Link is a **free, open-source Android app** plus a **small PC bridge** that lets you send prompts from your phone to a **Cursor agent running on your own computer**.

Nothing runs on a vendor cloud for the agent itself: your phone talks to **your** bridge over a **private network** (Tailscale, WireGuard, or home LAN). Prompts execute in folders **you** allow-list on your PC.

---

## What it is

| Piece | Role |
|-------|------|
| **Invictus Link (Android)** | Compose UI: connect, send prompts, read replies, approve risky actions, check activity |
| **PC bridge (Node.js)** | HTTP API on your PC; calls Cursor SDK `Agent.prompt()` in allowed project folders |
| **Private network** | Tailscale, self-hosted WireGuard hub (e.g. Raspberry Pi), or LAN — **your choice** |

```text
[ Your phone ]  ──VPN or LAN──►  [ Your PC :3003 bridge ]  ──►  Cursor agent
```

---

## What it is not

- Not a hosted SaaS or shared agent server
- Not tied to Invictus Pulse or Invictus Networks branding (those are optional companion projects in this repository)
- Not a replacement for the Cursor desktop app — it **remotes** agent work you already run on your PC

---

## App features (v1.60)

| Tab | Purpose |
|-----|---------|
| **Home** | Session picker; prompt box (“Message your PC agent…”); prompt history (last 20); live elapsed timer while running |
| **Activity** | Agent log, daily digest, pending approvals for risky prompts |
| **Connection** | Bridge URL, one-time pairing (biometric), VPN checklist (WireGuard + Tailscale), test connection |
| **Settings** | In-app OTA update check/install; publish new APK from PC; archive version |

**Security model**

- Bridge token + optional biometric for pairing
- HTTP over private network (cleartext on VPN — normal for home lab; do not port-forward the bridge to the public internet)
- Risky prompts can require phone approval before the agent runs
- One agent task at a time per bridge instance

---

## Who it is for

- Developers who use **Cursor** and want a phone front-end to their own PC
- Homelab / privacy-minded users comfortable running a bridge and VPN
- Other users on a shared WireGuard hub (each person uses **their own** PC bridge URL)

---

## License

MIT — see [LICENSE](LICENSE). Free to use, modify, and distribute with attribution. See [ATTRIBUTIONS.md](ATTRIBUTIONS.md) and [NOTICE](NOTICE) if you ship projects based on this code.
