# PC bridge setup

The bridge is a small Node.js server on **your PC**. It receives prompts from Invictus Link and runs the Cursor SDK agent in folders you allow.

---

## Requirements

- Windows, macOS, or Linux
- **Node.js 20+** (LTS recommended)
- **Cursor API key** ([Cursor dashboard](https://cursor.com))
- Network path from phone → PC (Tailscale, WireGuard, or LAN)

---

## Install

```powershell
cd bridge
npm install
npm run build
```

---

## Configure

Copy the example env file:

```powershell
copy .env.example .env
```

Edit `bridge/.env`:

```ini
BRIDGE_TOKEN="your-long-random-pairing-code"
CURSOR_API_KEY="your-cursor-api-key"
PORT=3003
```

Optional:

```ini
CURSOR_MODEL_ID="composer-2.5"
TASK_TIMEOUT_MINUTES=10
PUBLIC_URL="http://100.x.x.x:3003"
```

`PUBLIC_URL` helps OTA update links when the phone uses a different host than `localhost`.

---

## Workspace and sessions

The bridge uses an **approved workspace** on your PC. Sessions are subfolders (or the root folder) where the agent may work.

### Workspace root

Set the workspace in `bridge/config/projects.json` — either a single entry whose `cwd` is the workspace, or an object with `workspaceRoot`:

```json
{
  "workspaceRoot": "C:\\Users\\YOU\\path\\to\\workspace"
}
```

On first run, the bridge migrates a legacy projects array into `bridge/config/link-sessions.json`.

### Sessions (phone app)

- The Home tab **session dropdown** lists entries from `/health` → `projects`.
- **New Session** → `POST /api/sessions` (auth required) — creates a folder under the workspace.
- **Delete** → `DELETE /api/sessions/:id` (auth required).

Session registry file: `bridge/config/link-sessions.json` (created automatically; do not commit personal copies to git).

After code updates, restart the bridge so new API routes load:

```powershell
npm run build
npm start
```

Or use `scripts/invictus-networks/start-bridge.ps1` (builds and restarts).

---

## Projects allow-list (legacy)

Older setups used a static list in `projects.json`. New installs use **sessions** managed from the app. Example legacy format:

```json
[
  {
    "id": "main",
    "name": "My project",
    "cwd": "C:\\Users\\YOU\\path\\to\\project"
  }
]
```

The phone sends `projectId` (session id) with each prompt. Only registered session folders are used.

---

## Run

```powershell
npm start
```

Verify:

```powershell
curl http://localhost:3003/health
```

You should see JSON with `"ok": true` and a `projects` array.

**First phone install:** after publishing the APK, open  
`http://YOUR-PC-IP:3003/qr?url=http://YOUR-PC-IP:3003/download/InvictusLink.apk`  
See [FIRST_INSTALL_AND_UPDATES.md](FIRST_INSTALL_AND_UPDATES.md).

---

## Firewall (Windows + WireGuard)

If the phone uses a VPN IP to reach the PC:

```powershell
powershell -ExecutionPolicy Bypass -File ..\scripts\invictus-networks\allow-bridge-firewall.ps1
```

Allows inbound TCP **3003** from `10.66.66.0/24`.

For **Tailscale**, Windows usually allows tailnet traffic; if blocked, allow TCP 3003 from `100.64.0.0/10`.

---

## Phone connection URL

Use your PC’s address on the **same network the phone uses**:

| Network | Bridge URL example |
|---------|-------------------|
| Tailscale | `http://100.x.x.x:3003` |
| WireGuard | `http://10.66.66.11:3003` |
| Home LAN only | `http://192.168.1.50:3003` |

Port must match `PORT` in `.env` (default **3003** in docs; code default is 3001 if unset — always set `PORT=3003`).

---

## Keep running

- Leave the terminal open, or
- Run as a Windows Service / `pm2` / systemd user unit (advanced)

The PC must be **awake** with the bridge running for Link to work.

---

## Security

- **Never** port-forward 3003 on your router to the internet.
- Rotate `BRIDGE_TOKEN` if leaked.
- `BRIDGE_TOKEN` is required for pairing and sensitive bridge actions.

See [examples/bridge-env.example](../examples/bridge-env.example).
