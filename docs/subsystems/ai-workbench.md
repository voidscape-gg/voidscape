# AI Workbench

The AI workbench is a dev-only PC client helper for visual iteration and local automation. It is disabled by default and only starts when the client is launched with `-Dvoidscape.workbench=true`, normally through `scripts/run-workbench-client.sh`.

## Entry Points

- `scripts/run-workbench-client.sh` mirrors `scripts/run-client.sh`, writes `Client_Base/Cache/ip.txt` and `port.txt`, then runs the PC client with workbench JVM properties.
- `PC_Client/src/orsc/OpenRSC.java` starts the workbench after the desktop client window and applet are initialized.
- `PC_Client/src/orsc/WorkbenchServer.java` owns the loopback HTTP server, JSON state snapshots, and screenshot file writes.
- `PC_Client/src/orsc/ORSCApplet.java` exposes a synchronized copy of the current rendered game frame and routes the backtick screenshot hotkey through the same capture path.

## Local HTTP Endpoints

Default base URL: `http://127.0.0.1:18787`.

- `GET /health` returns a minimal readiness payload with `ok`, `clientVersion`, `port`, and `generatedAt`.
- `GET /state` returns a JSON snapshot of client state: game dimensions/state, mouse state, local player basics when logged in, and selected interface state for the Auction House, world map, and native shop window.
- `GET /screenshot` saves an exact PNG copy of the current game frame plus a JSON sidecar under `tmp/workbench/screenshots/`, then returns the saved paths and image dimensions.
- `GET /captures/latest` returns the most recent saved capture path and metadata.
- `POST /input/click` accepts `{"x":301,"y":147,"button":"left"}` in unscaled game-frame coordinates and sends a synthetic click through the applet mouse handler.
- `POST /input/key` accepts `{"key":"ENTER"}` or `{"char":"a"}` and sends a synthetic key press through the applet key handler.
- `POST /input/type` accepts `{"text":"hello"}` and types each character through the key handler.
- `POST /input/command` accepts `{"command":"quickauction"}` or `{"command":"::quickauction"}` and sends the existing client command packet.
- `POST /dev/ready` uses the saved Existing User credentials to reach an in-game state, waits for the local player to appear, then clears blocking welcome/server-message dialogs and hides dev-iteration panels such as the Auction House and world map.
- `POST /fixture/auction-house` requires the logged-in player to be an admin, dispatches `::workbenchauctionfixture`, and reseeds deterministic Auction House listings plus recent sales through the server DB layer.
- `POST /scenario/auction-house-open` opens the Auction House, captures Browse, first-listing selection, My Auctions, Intel, and Food category states, then returns a report with all saved image paths.
- `POST /scenario/subscription-vendor-claim` requires a logged-in account whose save has `web_account_id` plus a matching `starter_card:<webAccountId> = 1` global marker, teleports to the Lumbridge Void Subscription Vendor, sends the real client NPC command packet for `Subscribe`, verifies no vendor shop opens, and captures before/after screenshots.
- `POST /scenario/subscription-card-redeem` requires a logged-in account with item `1602` in inventory, sends the real inventory item command packet for `Redeem`, waits for the card to be consumed, and captures before/after screenshots.

The server binds to loopback only. It does not touch server packets, opcodes, player saves, DB schema, cache versioning, or gameplay behavior. Fixture seeding uses an admin-only server command and marks rows as `wb-fixture`/`wb-buyer` so they can be cleared and reseeded safely.

## Configuration

The wrapper script accepts optional environment overrides:

```bash
VOIDSCAPE_WORKBENCH_PORT=18788 scripts/run-workbench-client.sh
VOIDSCAPE_WORKBENCH_DIR=/tmp/voidscape-workbench scripts/run-workbench-client.sh
```

When the workbench is disabled, pressing backtick still saves an exact client-frame screenshot to the default workbench directory. This replaces the earlier macOS full-screen `screencapture` hook and makes screenshots independent of OS window placement.

## Current Boundaries

The workbench currently supports capture, state, synthetic local input, command dispatch, dev-ready session setup, Auction House fixture seeding, an Auction House smoke scenario, Lumbridge Subscription Vendor claim/redeem scenarios, and screenshots around those flows. It deliberately has no reusable scenario DSL, image diffing, browser UI, or server packet changes yet. Those should be added as separately testable slices so each step can be verified in-game.
