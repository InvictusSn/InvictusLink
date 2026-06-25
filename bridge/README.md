# InvictusLink bridge

This is the PC-side “translator” between your phone and the Cursor local agent.

## Endpoints

- `GET /health`
- `POST /tasks` with JSON body: `{ "prompt": "...", "projectId"?: "test" }`
- `GET /tasks/:taskId`

## Environment variables

- `BRIDGE_TOKEN` (required if you plan to call the bridge remotely)
- `CURSOR_API_KEY` (required to run the Cursor SDK)
- `CURSOR_MODEL_ID` (optional; defaults to `composer-2.5`)

## Project allowlist

Edit `config/projects.json` to include the project folders you want the agent
to be allowed to modify.

Each entry:
- `id`: short id used by the phone (`projectId`)
- `name`: display name
- `cwd`: absolute path on the PC

