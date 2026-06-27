#!/usr/bin/env bash
# Smoke-test the TeaVM web client desktop mouse path with a real login.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
WEB_PORT=0
OUT_DIR="${WEB_TEA_DESKTOP_SMOKE_OUT:-$ROOT/tmp/web-teavm-desktop-smoke}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
BASE_URL_OVERRIDE=""
STATIC_DIR="${WEB_TEA_DESKTOP_STATIC_DIR:-}"
HOST="127.0.0.1"
WS_PORT="43496"
WS_URL=""
AUTH_USER="${VOIDSCAPE_WEB_SMOKE_USER:-test}"
AUTH_PASS="${VOIDSCAPE_WEB_SMOKE_PASS:-test}"
IGNORE_HTTPS_ERRORS=0

usage() {
	cat <<'EOF'
Usage: scripts/smoke-web-teavm-desktop.sh [options]

Options:
  --no-build              Reuse existing Web_Client_TeaVM/target/teavm output.
  --web-port PORT         Local static-server port. Default: choose a free port.
  --out DIR               Output directory for screenshots/logs. Default: tmp/web-teavm-desktop-smoke.
  --static-dir DIR        Serve an existing static package instead of Web_Client_TeaVM/target/teavm.
  --base-url URL          Use a deployed web client instead of local static files.
  --host HOST             WebSocket host passed to the client in local mode. Default: 127.0.0.1.
  --ws-port PORT          WebSocket port passed to the client in local mode. Default: 43496.
  --ws URL                Full WebSocket URL passed as ?ws=.
  --user USER             Login username. Default: test.
  --pass PASS             Login password. Default: test.
  --chrome PATH           Chrome/Chromium executable path.
  --playwright-core DIR   Directory for playwright-core package.
  --ignore-https-errors   Ignore HTTPS certificate errors.
  -h, --help              Show this help.

This smoke forces desktop runtime, logs in through the diagnostics-only web
smoke hook, clicks terrain with a desktop mouse, immediately moves the mouse
after release, and requires the local player to move.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--web-port)
			WEB_PORT="${2:-}"
			shift 2
			;;
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--static-dir)
			STATIC_DIR="${2:-}"
			BUILD=0
			shift 2
			;;
		--base-url)
			BASE_URL_OVERRIDE="${2:-}"
			shift 2
			;;
		--host)
			HOST="${2:-}"
			shift 2
			;;
		--ws-port)
			WS_PORT="${2:-}"
			shift 2
			;;
		--ws)
			WS_URL="${2:-}"
			shift 2
			;;
		--user)
			AUTH_USER="${2:-}"
			shift 2
			;;
		--pass)
			AUTH_PASS="${2:-}"
			shift 2
			;;
		--chrome)
			CHROME_PATH="${2:-}"
			shift 2
			;;
		--playwright-core)
			PLAYWRIGHT_CORE_DIR="${2:-}"
			shift 2
			;;
		--ignore-https-errors)
			IGNORE_HTTPS_ERRORS=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

REMOTE_MODE=0
if [[ -n "$BASE_URL_OVERRIDE" ]]; then
	REMOTE_MODE=1
	BUILD=0
fi

if [[ "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ -n "$STATIC_DIR" ]]; then
	TARGET="$STATIC_DIR"
fi
if [[ "$REMOTE_MODE" -eq 0 && ! -f "$TARGET/index.html" ]]; then
	echo "ERROR: missing TeaVM web output at $TARGET/index.html." >&2
	exit 1
fi

if [[ -z "$PLAYWRIGHT_CORE_DIR" ]]; then
	if [[ -d "$ROOT/node_modules/playwright-core" ]]; then
		PLAYWRIGHT_CORE_DIR="$ROOT/node_modules/playwright-core"
	elif [[ -d "/tmp/voidscape-playwright-smoke/node_modules/playwright-core" ]]; then
		PLAYWRIGHT_CORE_DIR="/tmp/voidscape-playwright-smoke/node_modules/playwright-core"
	fi
fi

if [[ -z "$PLAYWRIGHT_CORE_DIR" || ! -d "$PLAYWRIGHT_CORE_DIR" ]]; then
	cat >&2 <<'EOF'
ERROR: playwright-core was not found.

Install it outside the repo or point PLAYWRIGHT_CORE_DIR at an existing package:
  mkdir -p /tmp/voidscape-playwright-smoke
  cd /tmp/voidscape-playwright-smoke
  npm init -y
  npm install playwright-core
EOF
	exit 1
fi

if [[ -z "$CHROME_PATH" ]]; then
	for candidate in \
		"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
		"/Applications/Chromium.app/Contents/MacOS/Chromium" \
		"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"; do
		if [[ -x "$candidate" ]]; then
			CHROME_PATH="$candidate"
			break
		fi
	done
fi

if [[ -z "$CHROME_PATH" || ! -x "$CHROME_PATH" ]]; then
	echo "ERROR: Chrome/Chromium executable not found. Pass --chrome PATH or set CHROME_PATH." >&2
	exit 1
fi

if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
	echo "ERROR: --user and --pass must be non-empty." >&2
	exit 1
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

HTTP_PID=""
if [[ "$REMOTE_MODE" -eq 0 ]]; then
	if [[ "$WEB_PORT" == "0" ]]; then
		WEB_PORT="$(python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
)"
	fi
	HTTP_LOG="$OUT_DIR/http-server.log"
	python3 -u -m http.server "$WEB_PORT" --bind 127.0.0.1 -d "$TARGET" >"$HTTP_LOG" 2>&1 &
	HTTP_PID=$!
fi

cleanup() {
	if [[ "${HTTP_PID:-}" =~ ^[0-9]+$ ]]; then
		kill "$HTTP_PID" >/dev/null 2>&1 || true
		wait "$HTTP_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

if [[ "$REMOTE_MODE" -eq 0 ]]; then
	python3 - "$WEB_PORT" <<'PY'
import sys
import time
import urllib.request

port = sys.argv[1]
url = f"http://127.0.0.1:{port}/index.html"
deadline = time.time() + 10
last_error = None
while time.time() < deadline:
    try:
        with urllib.request.urlopen(url, timeout=1) as response:
            if response.status == 200:
                sys.exit(0)
    except Exception as exc:
        last_error = exc
    time.sleep(0.2)
print(f"ERROR: static web server did not become ready: {last_error}", file=sys.stderr)
sys.exit(1)
PY
fi

export PLAYWRIGHT_CORE_DIR CHROME_PATH OUT_DIR HOST WS_PORT WS_URL AUTH_USER AUTH_PASS REMOTE_MODE IGNORE_HTTPS_ERRORS
if [[ "$REMOTE_MODE" -eq 1 ]]; then
	export BASE_URL="$BASE_URL_OVERRIDE"
else
	export BASE_URL="http://127.0.0.1:$WEB_PORT"
fi

node <<'NODE'
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_DIR);

const baseUrl = process.env.BASE_URL;
const host = process.env.HOST;
const wsPort = Number(process.env.WS_PORT);
const explicitWs = process.env.WS_URL || '';
const remoteMode = process.env.REMOTE_MODE === '1';
const user = process.env.AUTH_USER;
const pass = process.env.AUTH_PASS;
const outDir = process.env.OUT_DIR;
const chromePath = process.env.CHROME_PATH;
const ignoreHttpsErrors = process.env.IGNORE_HTTPS_ERRORS === '1';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function pageUrl(pathValue) {
  return new URL(pathValue, baseUrl).href;
}

function launchPath() {
  const query = new URLSearchParams();
  query.set('desktop', '1');
  query.set('diag', '1');
  query.set('endpoint', 'reset');
  if (explicitWs) {
    query.set('ws', explicitWs);
  } else if (!remoteMode) {
    query.set('host', host);
    query.set('port', String(wsPort));
  }
  return `index.html?${query.toString()}`;
}

async function waitForClient(page) {
  await page.waitForSelector('canvas', { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeEndpoint, null, { timeout: 30000 });
  await page.waitForFunction(() => typeof window.__voidscapeCollectDiagnostics === 'function', null, { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeInputInstalled && Array.isArray(window.__voidscapeInputQueue), null, { timeout: 30000 });
}

async function installQueueRecorder(page) {
  await page.evaluate(() => {
    const queue = window.__voidscapeInputQueue;
    if (!queue || queue.__voidscapeDesktopSmokeWrapped) return;
    window.__voidscapeDesktopSmokeQueuedEvents = [];
    const originalPush = queue.push;
    queue.push = function(...items) {
      window.__voidscapeDesktopSmokeQueuedEvents.push(...items);
      return originalPush.apply(this, items);
    };
    queue.__voidscapeDesktopSmokeWrapped = true;
  });
}

async function clearQueueRecorder(page) {
  await page.evaluate(() => {
    if (Array.isArray(window.__voidscapeInputQueue)) window.__voidscapeInputQueue.length = 0;
    window.__voidscapeDesktopSmokeQueuedEvents = [];
  });
}

async function recordedQueueEvents(page) {
  return await page.evaluate(() => (window.__voidscapeDesktopSmokeQueuedEvents || []).slice());
}

async function diagnosticsSnapshot(page) {
  return await page.evaluate(() => window.__voidscapeCollectDiagnostics());
}

async function framePoint(page, x, y) {
  return await page.evaluate(({ x, y }) => {
    const canvas = document.querySelector('canvas');
    const rect = canvas.getBoundingClientRect();
    return {
      x: rect.left + x * rect.width / canvas.width,
      y: rect.top + y * rect.height / canvas.height
    };
  }, { x, y });
}

async function clickFrame(page, x, y) {
  const point = await framePoint(page, x, y);
  await page.mouse.click(Math.round(point.x), Math.round(point.y));
}

async function waitForClientState(page, timeout = 10000) {
  await page.waitForFunction(() => {
    const state = window.__voidscapeClientState;
    return !!state && state.hasLocalPlayer && window.__voidscapeInGame;
  }, null, { timeout });
  return await page.evaluate(() => ({ ...window.__voidscapeClientState }));
}

async function waitForPlayableClientState(page, timeout = 12000) {
  await page.waitForFunction(() => {
    const state = window.__voidscapeClientState;
    return !!state
      && state.hasLocalPlayer
      && window.__voidscapeInGame
      && state.viewMode === 'GAME'
      && state.currentX > 0
      && state.currentZ > 0
      && state.worldX > 0
      && state.worldY > 0;
  }, null, { timeout });
  return await page.evaluate(() => ({ ...window.__voidscapeClientState }));
}

async function waitForStablePlayableClientState(page, timeout = 15000) {
  const deadline = Date.now() + timeout;
  let previous = await waitForPlayableClientState(page, timeout);
  while (Date.now() < deadline) {
    await page.waitForTimeout(500);
    const current = await waitForPlayableClientState(page, Math.max(1000, deadline - Date.now()));
    if (current.worldX === previous.worldX
        && current.worldY === previous.worldY
        && current.currentX === previous.currentX
        && current.currentZ === previous.currentZ) {
      return current;
    }
    previous = current;
  }
  return previous;
}

function movedFrom(before, after) {
  return before
    && after
    && after.hasLocalPlayer
    && (after.worldX !== before.worldX
      || after.worldY !== before.worldY
      || Math.abs(after.currentX - before.currentX) >= 128
      || Math.abs(after.currentZ - before.currentZ) >= 128);
}

function isVoidCouncilIntroState(state) {
  if (!state) return false;
  const worldX = Number(state.worldX);
  const worldY = Number(state.worldY);
  return worldX >= 17 && worldX <= 31 && worldY >= 31 && worldY <= 42;
}

async function waitForMovementFrom(page, before, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const after = await page.evaluate(() => window.__voidscapeClientState ? ({ ...window.__voidscapeClientState }) : null);
    if (movedFrom(before, after)) return after;
    await page.waitForTimeout(250);
  }
  return null;
}

function clampFramePoint(value, min, max) {
  return Math.round(Math.max(min, Math.min(max, value)));
}

async function currentFrameSize(page) {
  return await page.evaluate(() => {
    const canvas = document.querySelector('canvas');
    return {
      width: canvas ? canvas.width : 512,
      height: canvas ? canvas.height : 346
    };
  });
}

async function dismissInitialBlockingDialog(page, label) {
  const attempts = [];
  for (let attempt = 0; attempt < 6; attempt++) {
    const snapshot = await diagnosticsSnapshot(page);
    const ui = snapshot.ui || {};
    if (!ui.blockingDialog) return { attempts, closed: true };
    const gameWidth = Math.max(
      512,
      (snapshot.client && snapshot.client.gameWidth) || 0,
      (snapshot.canvas && snapshot.canvas.width) || 0
    );
    const gameHeight = Math.max(
      346,
      (snapshot.client && snapshot.client.gameHeight) || 0,
      (snapshot.canvas && snapshot.canvas.height) || 0
    );
    const centerX = Math.round(gameWidth / 2);
    const candidates = [];
    if (String(ui.blockingDialogName || '') === 'wildernessWarning') {
      const wildOffset = ui.androidProfile ? 74 : 68;
      candidates.push({
        x: centerX,
        y: Math.round(gameHeight / 2 + wildOffset),
        reason: 'wilderness-center-button'
      });
      candidates.push({
        x: centerX,
        y: Math.round(gameHeight / 2 + wildOffset),
        reason: 'wilderness-center-button-repeat'
      });
    } else if (String(ui.blockingDialogName || '') === 'welcome') {
      for (const dialogHeight of [135, 180]) {
        candidates.push({
          x: centerX,
          y: Math.round((gameHeight + dialogHeight) / 2 - 15),
          reason: `welcome-${dialogHeight}`
        });
      }
      candidates.push({ x: centerX, y: Math.round(gameHeight / 2 + 64), reason: 'center-lower' });
      candidates.push({ x: centerX, y: 505, reason: 'legacy' });
    } else {
      candidates.push({ x: centerX, y: Math.round(gameHeight / 2 + 64), reason: 'center-lower' });
      candidates.push({ x: centerX, y: 505, reason: 'legacy' });
    }
    const candidate = candidates[Math.min(attempt, candidates.length - 1)];
    candidate.y = Math.max(8, Math.min(gameHeight - 8, candidate.y));
    attempts.push({ attempt: attempt + 1, dialog: ui.blockingDialogName || '', gameHeight, candidate });
    await clickFrame(page, candidate.x, candidate.y);
    await page.waitForTimeout(450);
    const closed = await page.evaluate(() => {
      const ui = window.__voidscapeUiState;
      return !!ui && !ui.blockingDialog && !document.body.classList.contains('dialog-open');
    });
    if (closed) return { attempts, closed: true };
  }
  const snapshot = await diagnosticsSnapshot(page);
  throw new Error(`${label} did not close: ${JSON.stringify({ attempts, ui: snapshot.ui, client: snapshot.client, canvas: snapshot.canvas })}`);
}

async function performLogin(page, loginUrl, consoleMessages) {
  for (let attempt = 1; attempt <= 2; attempt++) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await waitForClient(page);
    await installQueueRecorder(page);
    await page.evaluate(({ user, pass }) => {
      window.__voidscapeSmokeLoginRequest = { user, pass };
    }, { user, pass });
    const loggedIn = await page.waitForFunction(() => document.title.startsWith('Voidscape -- '), null, { timeout: 25000 })
      .then(() => true)
      .catch(() => false);
    if (loggedIn) {
      await page.waitForFunction(() => {
        if (typeof window.__voidscapeCollectDiagnostics !== 'function') return false;
        const snapshot = window.__voidscapeCollectDiagnostics();
        return snapshot && snapshot.canvas
          && snapshot.canvas.width >= 512
          && snapshot.canvas.height >= 346
          && snapshot.canvas.cssWidth >= 500
          && snapshot.canvas.cssHeight >= 336;
      }, null, { timeout: 8000 });
      const snapshot = await diagnosticsSnapshot(page);
      const state = await waitForPlayableClientState(page, 12000);
      return {
        attempt,
        title: await page.title(),
        effectiveWs: snapshot.effectiveWs,
        runtimeMode: snapshot.runtimeMode,
        canvas: snapshot.canvas,
        client: state
      };
    }
    const busy = consoleMessages.some((entry) => /login response:\s*4|already logged in/i.test(entry));
    if (!busy || attempt === 2) {
      const snapshot = await diagnosticsSnapshot(page).catch(() => null);
      throw new Error(`desktop login failed: ${JSON.stringify({ attempt, title: await page.title(), snapshot, consoleTail: consoleMessages.slice(-16) })}`);
    }
  }
  throw new Error('desktop login failed without result');
}

async function desktopResourceControlSmoke(page) {
  await page.waitForSelector('#play-shell', { state: 'visible', timeout: 8000 });
  await page.waitForSelector('#resource-mode-control', { state: 'visible', timeout: 8000 });
  const initial = await diagnosticsSnapshot(page);
  assert(initial.controls && initial.controls.playShell && initial.controls.playShell.visible,
    `desktop shell should be visible: ${JSON.stringify(initial.controls && initial.controls.playShell)}`);
  assert(initial.controls.resourceModeControl && initial.controls.resourceModeControl.visible,
    `desktop resource controls should be visible: ${JSON.stringify(initial.controls.resourceModeControl)}`);
  assert((initial.controls.resourceButtons || []).length === 2
      && (initial.controls.resourceButtons || []).some((button) => button.selector.includes('data-resource-mode="normal"'))
      && (initial.controls.resourceButtons || []).some((button) => button.selector.includes('data-resource-mode="gfx-off"')),
    `desktop shell should expose only Normal and GFX off resource controls: ${JSON.stringify(initial.controls.resourceButtons)}`);

  const clicks = [];
  for (const mode of ['gfx-off', 'normal']) {
    await clearQueueRecorder(page);
    await page.locator(`.resource-mode-button[data-resource-mode="${mode}"]`).click({ timeout: 5000 });
    await page.waitForFunction((expected) => {
      return window.__voidscapeResourceState && window.__voidscapeResourceState.mode === expected;
    }, mode, { timeout: 5000 });
    const snapshot = await diagnosticsSnapshot(page);
    const events = await recordedQueueEvents(page);
    const active = (snapshot.controls.resourceButtons || []).find((button) => button.selector.includes(`data-resource-mode="${mode}"`));
    assert(active && active.visible && active.ariaPressed === 'true',
      `resource button ${mode} should be visible/pressed: ${JSON.stringify(active)}`);
    assert(!events.some((entry) => /^p,/.test(entry)),
      `resource button ${mode} should not enqueue game pointer events: ${events.join(' ')}`);
    if (mode === 'gfx-off') {
      assert(snapshot.resource && snapshot.resource.mode === 'gfx-off',
        `gfx-off resource mode not reflected in diagnostics: ${JSON.stringify(snapshot.resource)}`);
      assert(snapshot.controls.lowResourceOverlay && snapshot.controls.lowResourceOverlay.visible,
        `gfx-off overlay should be visible: ${JSON.stringify(snapshot.controls.lowResourceOverlay)}`);
    }
    if (mode === 'normal') {
      assert(snapshot.resource && snapshot.resource.mode === 'normal',
        `normal resource mode not restored: ${JSON.stringify(snapshot.resource)}`);
      assert(snapshot.controls.lowResourceOverlay && !snapshot.controls.lowResourceOverlay.visible,
        `gfx-off overlay should hide after normal mode: ${JSON.stringify(snapshot.controls.lowResourceOverlay)}`);
    }
    clicks.push({
      mode,
      resource: snapshot.resource,
      activeButton: active,
      queuedEvents: events,
      overlay: snapshot.controls.lowResourceOverlay
    });
  }
  return {
    initial: {
      runtimeMode: initial.runtimeMode,
      resource: initial.resource,
      playShell: initial.controls.playShell,
      resourceModeControl: initial.controls.resourceModeControl
    },
    clicks
  };
}

async function desktopMouseClickMovement(page) {
  const before = await waitForStablePlayableClientState(page);
  const frame = await currentFrameSize(page);
  const width = Math.max(160, frame.width || 512);
  const height = Math.max(160, frame.height || 346);
  const xAt = (ratio) => clampFramePoint(width * ratio, 32, width - 32);
  const yAt = (ratio) => clampFramePoint(height * ratio, 48, height - 42);
  const candidates = [
    { x: xAt(0.50), y: yAt(0.58) },
    { x: xAt(0.34), y: yAt(0.63) },
    { x: xAt(0.68), y: yAt(0.58) },
    { x: xAt(0.28), y: yAt(0.48) },
    { x: xAt(0.72), y: yAt(0.48) },
    { x: xAt(0.50), y: yAt(0.42) },
    { x: xAt(0.40), y: yAt(0.72) },
    { x: xAt(0.60), y: yAt(0.72) }
  ];
  const attempts = [];
  for (const candidate of candidates) {
    await clearQueueRecorder(page);
    const point = await framePoint(page, candidate.x, candidate.y);
    await page.mouse.move(Math.round(point.x), Math.round(point.y));
    await page.mouse.down({ button: 'left' });
    await page.waitForTimeout(25);
    await page.mouse.up({ button: 'left' });
    await page.mouse.move(Math.round(point.x) + 3, Math.round(point.y) + 2);
    const movedAfter = await waitForMovementFrom(page, before, 8000);
    const after = movedAfter || await page.evaluate(() => window.__voidscapeClientState ? ({ ...window.__voidscapeClientState }) : null);
    const events = await recordedQueueEvents(page);
    attempts.push({ candidate, moved: movedFrom(before, after), events, after });
    assert(events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
      `desktop mouse should enqueue primary click: ${events.join(' ')}`);
    assert(events.some((entry) => /^p,2,\d+,\d+,1$/.test(entry)),
      `desktop mouse should enqueue release: ${events.join(' ')}`);
    assert(events.some((entry) => /^p,0,\d+,\d+,0$/.test(entry)),
      `desktop mouse should record follow-up hover move: ${events.join(' ')}`);
    if (movedFrom(before, after)) {
      return { before, after, candidate, events, attempts };
    }
    if (isVoidCouncilIntroState(before)) {
      return { before, after, candidate, events, attempts, onboardingIntro: true };
    }
  }
  throw new Error(`desktop mouse terrain click did not move local player: ${JSON.stringify({ before, attempts })}`);
}

async function desktopKeyboardSmoke(page) {
  await clearQueueRecorder(page);
  await page.locator('canvas').focus({ timeout: 5000 });
  const beforeText = await page.evaluate(() => {
    const state = window.__voidscapeClientState || {};
    return {
      chatMessageInput: state.chatMessageInput || '',
      inputTextCurrent: state.inputTextCurrent || ''
    };
  });
  const keys = [
    { key: 'PageUp', code: 33, legacy: '!' },
    { key: 'PageDown', code: 34, legacy: '"' },
    { key: 'ArrowLeft', code: 37, legacy: '%' },
    { key: 'ArrowUp', code: 38, legacy: '&' },
    { key: 'ArrowRight', code: 39, legacy: "'" },
    { key: 'ArrowDown', code: 40, legacy: '(' }
  ];
  for (const entry of keys) {
    await page.keyboard.down(entry.key);
    await page.waitForTimeout(30);
    await page.keyboard.up(entry.key);
    await page.evaluate(({ key, code, legacy }) => {
      const event = new KeyboardEvent('keypress', {
        key: legacy,
        code: key,
        keyCode: code,
        which: code,
        charCode: code,
        bubbles: true,
        cancelable: true
      });
      document.dispatchEvent(event);
    }, entry);
  }
  await page.waitForTimeout(250);
  const afterText = await page.evaluate(() => {
    const state = window.__voidscapeClientState || {};
    return {
      chatMessageInput: state.chatMessageInput || '',
      inputTextCurrent: state.inputTextCurrent || ''
    };
  });
  const events = await recordedQueueEvents(page);
  for (const entry of keys) {
    assert(events.includes(`k,1,${entry.code}`),
      `desktop keyboard should enqueue ${entry.key} key down: ${events.join(' ')}`);
    assert(events.includes(`k,0,${entry.code}`),
      `desktop keyboard should enqueue ${entry.key} key up: ${events.join(' ')}`);
  }
  assert(!events.some((entry) => entry.startsWith('t,')),
    `desktop navigation keys must not enqueue text/chat input events: ${events.join(' ')}`);
  assert(afterText.chatMessageInput === beforeText.chatMessageInput
      && afterText.inputTextCurrent === beforeText.inputTextCurrent,
    `desktop navigation keys must not mutate Java text buffers: ${JSON.stringify({
      beforeText,
      afterText,
      events
    })}`);
  await clearQueueRecorder(page);
  await page.evaluate(() => {
    const event = new KeyboardEvent('keypress', {
      key: '%',
      code: 'Digit5',
      keyCode: 37,
      which: 37,
      charCode: 37,
      bubbles: true,
      cancelable: true
    });
    document.dispatchEvent(event);
  });
  await page.waitForTimeout(120);
  const percentEvents = await recordedQueueEvents(page);
  assert(percentEvents.includes('t,37'),
    `ordinary printable percent keypress should still enqueue text input: ${percentEvents.join(' ')}`);
  return { keys: keys.map(({ key, code, legacy }) => ({ key, code, legacy })), events, beforeText, afterText, percentEvents };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({ executablePath: chromePath, headless: true });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 1,
    isMobile: false,
    hasTouch: false,
    ignoreHTTPSErrors: ignoreHttpsErrors,
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
  });
  const page = await context.newPage();
  const failed = [];
  const consoleMessages = [];
  page.on('requestfailed', (req) => failed.push(`${req.method()} ${req.url()} ${req.failure() && req.failure().errorText}`));
  page.on('console', (msg) => consoleMessages.push(`${msg.type()}: ${msg.text()}`));

  const loginUrl = pageUrl(launchPath());
  const login = await performLogin(page, loginUrl, consoleMessages);
  assert(login.runtimeMode && login.runtimeMode.mode === 'desktop',
    `expected desktop runtime, got ${JSON.stringify(login.runtimeMode)}`);
  assert(login.canvas && login.canvas.width >= 512 && login.canvas.height >= 346,
    `desktop canvas should use the native 512x346 framebuffer, got ${JSON.stringify(login.canvas)}`);
  assert(login.canvas && login.canvas.cssWidth >= 500 && login.canvas.cssWidth <= 532
    && login.canvas.cssHeight >= 336 && login.canvas.cssHeight <= 366,
    `desktop canvas should render at native 512x346 presentation size, got ${JSON.stringify(login.canvas)}`);

  await page.waitForTimeout(1200);
  const dismiss = await dismissInitialBlockingDialog(page, 'desktop initial blocking dialog');
  await page.waitForFunction(() => {
    const ui = window.__voidscapeUiState;
    return ui && !ui.blockingDialog && window.__voidscapeInGame;
  }, null, { timeout: 8000 });

  const resourceControls = await desktopResourceControlSmoke(page);
  const movement = await desktopMouseClickMovement(page);
  const keyboard = await desktopKeyboardSmoke(page);
  const snapshot = await diagnosticsSnapshot(page);
  assert(snapshot.recentErrors.length === 0,
    `desktop diagnostics reported errors: ${snapshot.recentErrors.join('; ')}`);

  const screenshot = path.join(outDir, 'desktop-mouse-smoke.png');
  await page.screenshot({ path: screenshot, fullPage: true });
  await browser.close();

  const ignoredNetworkFailures = failed.filter((failure) => /ws:\/\/127\.0\.0\.1:\d+\//.test(failure));
  const unexpectedFailures = failed.filter((failure) => !ignoredNetworkFailures.includes(failure));
  const summary = {
    baseUrl,
    loginUrl,
    user,
    login,
    dismiss,
    resourceControls,
    movement: {
      onboardingIntro: movement.onboardingIntro === true,
      before: movement.before,
      after: movement.after,
      candidate: movement.candidate,
      events: movement.events,
      attempts: movement.attempts.map((attempt) => ({
        candidate: attempt.candidate,
        moved: attempt.moved,
        events: attempt.events
      }))
    },
    keyboard,
    snapshot: {
      runtimeMode: snapshot.runtimeMode,
      canvas: snapshot.canvas,
      ui: snapshot.ui,
      recentErrors: snapshot.recentErrors
    },
    failedRequests: unexpectedFailures,
    ignoredNetworkFailures,
    consoleTail: consoleMessages.slice(-16),
    screenshot
  };
  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
  assert(unexpectedFailures.length === 0, `unexpected request failures observed: ${unexpectedFailures.join('; ')}`);
})().catch(async (error) => {
  try {
    const screenshot = path.join(outDir, 'desktop-mouse-smoke-failed.png');
    fs.mkdirSync(outDir, { recursive: true });
    if (global.page) await global.page.screenshot({ path: screenshot, fullPage: true });
  } catch (ignored) {}
  throw error;
});
NODE

echo "Desktop TeaVM mouse/keyboard smoke passed."
