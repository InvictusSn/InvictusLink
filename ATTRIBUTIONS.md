# Attributions

Thank you to everyone who helped bring Invictus Link into the world.

---

## Creator

**Seth Naasko** — Creator of Invictus Link. Original concept, architecture, Android app, PC bridge, documentation, and release packaging.

---

## Contributors

**Gavin Naasko** — Early brainstorming, ideas, and feedback that shaped the product direction.

**Jason Adkins** — Brainstorming, feedback, and support during development.

---

## Development assistance

**Auto** (Cursor AI agent) — Implementation help in Cursor, including session management, bridge APIs, release documentation, install QR and in-app update guides, security review of the public release package, and ongoing refinement of setup docs.

Auto is an agent router built by **[Cursor](https://cursor.com)**. It runs on large language models trained and provided by Cursor and its model partners. Thank you to the Cursor team and the researchers and engineers behind the models and tools that made this collaboration possible.

---

## Third-party software

Invictus Link builds on open-source and commercial tools, including but not limited to:

- **Cursor** and the **Cursor SDK** — agent execution on the PC bridge
- **Kotlin / Jetpack Compose** — Android app UI
- **Node.js / Express** — PC bridge server
- **WireGuard** and **Tailscale** — optional private networking (user-configured)

See `LICENSE` for MIT terms on Invictus Link’s own code. Third-party packages retain their respective licenses (`bridge/package.json`, Android Gradle dependencies).

---

## If you use this in your own projects

Invictus Link is released under the **MIT License**. That means you may use, modify, and ship your own projects built from this code. In return, standard open source practice (and the license itself) asks you to **give credit**.

### Required (MIT License)

If you distribute **any copy or substantial portion** of this software, you **must**:

1. Include the **copyright notice**: `Copyright (c) 2026 Seth Naasko`
2. Include the **full MIT License text** (see `LICENSE`)

### Requested when you ship something based on this work

If you **release** an app, service, fork, or product that uses Invictus Link source, docs, or other resources from this project, please also:

1. **Credit Seth Naasko** as the original creator of Invictus Link in a place users or developers can see, such as:
   - An **About** / **Credits** screen in your app
   - `ATTRIBUTIONS.md`, `NOTICE`, or `README.md` in your repository
   - Release notes or your project website
2. **Say what you used** — e.g. “Based on Invictus Link” or “Includes code from Invictus Link”
3. **Keep third-party notices** — Do not remove license terms for Cursor SDK, Kotlin, Node, or other dependencies you redistribute

**Example credit (README or About screen):**

```text
Based on Invictus Link by Seth Naasko
https://github.com/YOUR-ORG/YOUR-REPO (optional)
Licensed under the MIT License — see LICENSE.
```

Personal or private use without redistribution does not require public attribution, but the MIT conditions above apply if you **share or ship** the code or binaries.

See also [NOTICE](NOTICE) for a short copy-paste attribution block.

---

*Your universe, Your way.*
