# Invictus Link — public release package

This folder is a **public release** copy of Invictus Link (v1.60). It contains what you need to build, install, and run the app on your own PC and phone.

**No API keys, pairing tokens, WireGuard private keys, or personal paths are included.**

---

## What’s inside

| Path | Purpose |
|------|---------|
| [START_HERE.txt](START_HERE.txt) | Fastest path to first prompt |
| [release/InvictusLink.apk](release/InvictusLink.apk) | Pre-built Android app (v1.60) |
| [docs/FIRST_INSTALL_AND_UPDATES.md](docs/FIRST_INSTALL_AND_UPDATES.md) | Cursor setup, one-time install QR, in-app updates |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Daily use (sessions, prompts, updates) |
| [docs/PC_BRIDGE_SETUP.md](docs/PC_BRIDGE_SETUP.md) | Run the bridge on a PC |
| [docs/TAILSCALE_SETUP.md](docs/TAILSCALE_SETUP.md) | Easiest VPN path |
| [docs/RASPBERRY_PI_VPN_HUB.md](docs/RASPBERRY_PI_VPN_HUB.md) | Self-hosted WireGuard hub |
| [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md) | Build APK, publish OTA updates |
| [android/](android/) | App source |
| [bridge/](bridge/) | PC bridge source |
| [scripts/](scripts/) | Build, firewall, start bridge |
| [InvictusLink/](InvictusLink/) | Quick-start templates and Cursor agent guide |
| [examples/](examples/) | Safe config templates |
| [release/](release/) | APK + OTA manifest example |
| [ATTRIBUTIONS.md](ATTRIBUTIONS.md) | Creator and contributor credits |
| [NOTICE](NOTICE) | Copy-paste attribution for derivative projects |

---

## Quick start

1. **Install the app** — use `release/InvictusLink.apk`, or build from source / scan the one-time install QR ([FIRST_INSTALL_AND_UPDATES.md](docs/FIRST_INSTALL_AND_UPDATES.md)).
2. Set up **Tailscale** (or WireGuard) on phone + PC — see docs.
3. On PC: `cd bridge` → `npm install` → `npm run build` → copy `.env.example` to `.env` → set **your own** `BRIDGE_TOKEN` and `CURSOR_API_KEY` → `npm start`.
4. Edit `bridge/config/projects.json` with **your** workspace path.
5. In the app **Connection** tab: `http://YOUR-PC-IP:3003` + pairing code → **Connect Once**.
6. **Home** → **New Session** or pick a session → send a prompt.

### Updates after first install

**Settings → Check for update → Install update** — no new QR code needed. See [FIRST_INSTALL_AND_UPDATES.md](docs/FIRST_INSTALL_AND_UPDATES.md).

---

## License

MIT — see [LICENSE](LICENSE). If you ship a project based on this code, credit **Seth Naasko** and include the license text — see [ATTRIBUTIONS.md](ATTRIBUTIONS.md) and [NOTICE](NOTICE). Invictus Link is not affiliated with Cursor Inc.

---

## Public release review

This tree was reviewed before release:

- No `.env`, session files, or live API keys
- No personal usernames, home LAN IPs, or real Tailscale addresses
- Example configs and placeholders only (`YOUR_NAME`, `YOUR-PC-IP`, `change-me-to-a-long-random-string`)
