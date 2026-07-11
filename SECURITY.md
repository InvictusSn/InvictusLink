# Security — Invictus Link

Invictus Link is designed for **private, self-hosted** use: your phone talks to **your PC** over **your VPN**. It is not a hosted cloud service.

## Threat model (what we protect)

| Asset | Where it lives | Protection |
|-------|----------------|------------|
| Cursor / AI API keys | PC only (`bridge/.env`, `bridge/config/providers.json`) | Never shipped in the APK; phone sees masked tails only |
| Bridge pairing token | PC `.env` + phone secure storage | `BRIDGE_TOKEN` required; unset token = bridge rejects all auth |
| Session tokens | Phone `EncryptedSharedPreferences` | `allowBackup=false`; not logged by the app |
| Project files | Your PC disks | Agent runs only in allow-listed workspace folders |
| Prompts & replies | Phone ↔ PC over VPN | Not routed through a third-party server |

## What you must do

1. **Use a private network** — Tailscale or WireGuard. Do **not** port-forward TCP 3003 on your home router to the public internet.
2. **Set a strong `BRIDGE_TOKEN`** — long random string in `bridge/.env`. Treat it like a password.
3. **Keep API keys on the PC** — never paste them into public chats, screenshots, or git commits.
4. **Copy config from examples** — use `bridge/.env.example` and `bridge/config/projects.json.example`; never commit your live `.env` or `projects.json`.
5. **Rotate tokens** if you ever shared a zip, backup, or chat log that might contain `BRIDGE_TOKEN` or API keys.

## Bridge behavior

- **Fail-closed auth** — if `BRIDGE_TOKEN` is empty, authenticated routes return unauthorized.
- **Admin routes** (`/admin/*`) require the same auth as tasks and sessions.
- **`/health`** exposes project **names and ids only** — not filesystem paths or keys.
- **Exports and attachments** are path-checked on the PC (`isPathInsideRoot`).
- **Rules vault reads** (optional) are jailed to `OBSIDIAN_VAULT_PATH` with size caps.

## Cleartext HTTP

The app uses **HTTP** (not HTTPS) to your PC on a private VPN address. That is intentional for local-first setups where TLS termination on the LAN is uncommon. **Only use Link over a VPN you trust** — not on untrusted Wi‑Fi without Tailscale/WireGuard.

## Shared Pi VPN hub

If you use a community WireGuard hub, the hub operator can see that your devices are online. They **cannot** read Cursor prompts or agent output — that traffic goes directly between your phone and **your** PC (or through a proxy you control).

## Reporting issues

If you find a security bug in Invictus Link, open a GitHub issue with minimal reproduction steps. Do **not** post live tokens or private keys.
