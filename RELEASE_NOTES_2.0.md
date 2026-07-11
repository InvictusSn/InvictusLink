# Invictus Link v2.0 — Release notes

**Released:** 2026-07-11  
**App:** `versionName` **2.0** · `versionCode` **129**  
**Bridge:** **2.0.0** (current live sources)

---

## Summary

Invictus Link v2.0 is the public phone → PC agent stack. Send prompts from Android to Cursor, Grok, Claude, OpenAI, Gemini, or local models on **your own PC**, over your own network — with spending visibility, Grok prompt caching, rules, templates, and Markdown export.

This package matches the live app/bridge feature set as of the release date, scrubbed of personal config and secrets.

---

## Highlights

### Home
- Chat-style layout with streaming replies
- Session picker (new / rename / delete)
- Attachments (camera / gallery / files — up to 10 per prompt)
- Prompt library with `{{variables}}`
- Markdown export of conversations
- Stop an in-flight reply; restart the bridge from the phone

### Activity
- Cost dashboard — spend, per-provider breakdown, daily chart
- Grok prompt-cache savings from live token usage
- Local-model savings estimates
- Optional monthly/daily spend limits with phone alerts

### Settings
- AI Providers — Cursor, OpenAI, Anthropic, xAI/Grok, Google, Ollama, LM Studio, Custom
- Auto mode — agentic → Cursor, research → Grok, quick tasks → local when available
- Rules — global / per-provider / per-session; optional Obsidian note refs
- OTA check/install; publish APK from PC

### Bridge
- Multi-provider routing + Auto router
- Grok append-only threads with prompt caching (`prompt_cache_key` / `x-grok-conv-id`)
- System-prompt + rules hashing, compaction after huge replies, smart image detail / reasoning effort
- Live xAI per-model pricing when reachable
- Fail-closed auth; path-jailed uploads/exports

### Branding
- Cosmic IV app icon
- Navy Invictus Compose UI

---

## Grok caching (measured on maintainer setup)

From live bridge logs (~36 xAI completes):
- Average cache hit rate: **~57%**
- Estimated input-cost savings vs no cache: **~43%**
- Warm follow-up streaks often **mid–high 90%s** hit rate

Your results will vary with thread length and model/prompt changes.

---

## Tested for this release

| Provider | Notes |
|----------|--------|
| xAI / Grok | Daily driver — chat, images, caching |
| Cursor | Agent path on the PC |
| Ollama (local Llama) | Connected and chatted |

OpenAI, Claude, Gemini, and LM Studio share the same connect paths; connect from Settings when you have a key or local server.

---

## Install / upgrade

**New users:** See [docs/FIRST_INSTALL_AND_UPDATES.md](docs/FIRST_INSTALL_AND_UPDATES.md)

**Bridge:** Copy `.env.example` → `.env`, set `BRIDGE_TOKEN` + keys, `npm install`, start the bridge. Restart after updating bridge sources.

---

## Credits

**Seth Naasko** — creator. **Gavin Naasko** helped originate the name **Invictus** — see [ATTRIBUTIONS.md](ATTRIBUTIONS.md).

MIT License.
