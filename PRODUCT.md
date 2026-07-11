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

## App features (v2.0)

| Tab | Purpose |
|-----|---------|
| **Home** | Session picker; growing prompt composer with attachments + prompt library (templates with `{{variables}}`); streamed markdown replies; history with export to Markdown (PC or share sheet) |
| **Activity** | Daily digest with stat rings; **cost dashboard** (per-device spend, limits + alerts, local-model & prompt-caching savings); agent log; pending approvals |
| **Connection** | Bridge URL + QR scan, one-time pairing (biometric), VPN checklist (WireGuard + Tailscale), test connection |
| **Settings** | **AI Providers** (OpenAI, Claude, xAI/Grok, Gemini, Ollama, LM Studio) with Auto smart routing; **Rules** (persistent instructions — global / per-provider / per-session, optional Obsidian vault references); OTA update check/install; publish new APK from PC; crash diagnostics |

**v2 highlights**

- **Multi-provider + Auto mode** — smart routing: agentic work → Cursor, research → Grok (live web search), quick or private tasks → local models
- **Rules** — standing instructions injected into every prompt, with cache-safe handling and optional vault note context
- **Cost tracking** — exact xAI pricing from the live API, per-device spend on shared keys, monthly/daily limits with phone alerts
- **Grok prompt caching** — append-only threads, sticky cache routing, automatic thread compaction to keep long chats cheap
- **Prompt library & Markdown export** — reusable templates; take phone research to your PC as clean `.md` files

**Security model**

- Bridge token + biometric pairing; auth fails closed (no token → every request rejected)
- API keys live only on your PC — the phone sees masked tails
- HTTP over private network (cleartext on VPN — normal for home lab; do not port-forward the bridge to the public internet)
- Risky prompts can require phone approval before the agent runs
- Sanitized, path-jailed uploads and exports; one agent task at a time per bridge instance

---

## Who it is for

- Developers who use **Cursor** and want a phone front-end to their own PC
- Homelab / privacy-minded users comfortable running a bridge and VPN
- Other users on a shared WireGuard hub (each person uses **their own** PC bridge URL)

---

## License

MIT — see [LICENSE](LICENSE). Free to use, modify, and distribute with attribution. See [ATTRIBUTIONS.md](ATTRIBUTIONS.md) and [NOTICE](NOTICE) if you ship projects based on this code.
