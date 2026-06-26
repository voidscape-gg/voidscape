#!/usr/bin/env bash
# Smoke-test the TeaVM iPhone web client in Chrome's mobile emulation.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
WEB_PORT=0
BASE_URL_OVERRIDE="${WEB_TEA_SMOKE_BASE_URL:-}"
HOST="${WEB_TEA_SMOKE_HOST:-127.0.0.1}"
WS_PORT="${WEB_TEA_SMOKE_WS_PORT:-43496}"
WS_URL="${WEB_TEA_SMOKE_WS_URL:-}"
AUTH_USER="${WEB_TEA_SMOKE_USER:-test}"
AUTH_PASS="${WEB_TEA_SMOKE_PASS:-test}"
PORTAL_URL="${WEB_TEA_SMOKE_PORTAL_URL:-/iphone-account/}"
PORTAL_ACCOUNT_URL="${WEB_TEA_SMOKE_PORTAL_ACCOUNT_URL:-}"
PORTAL_RECOVERY_URL="${WEB_TEA_SMOKE_PORTAL_RECOVERY_URL:-}"
OUT_DIR="${WEB_TEA_SMOKE_OUT:-$ROOT/tmp/web-teavm-smoke}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
IGNORE_HTTPS_ERRORS="${WEB_TEA_SMOKE_IGNORE_HTTPS_ERRORS:-0}"
ONLY_WORLD_MAP_SEARCH=0
ONLY_CHAT=0
ONLY_CONTEXT_MENU=0

usage() {
	cat <<'EOF'
Usage: scripts/smoke-web-teavm-iphone.sh [options]

Options:
  --no-build              Reuse existing Web_Client_TeaVM/target/teavm output.
  --base-url URL          Test an already served web root instead of starting a local static server.
  --web-port PORT         Local static-server port. Default: choose a free port.
  --host HOST             WebSocket host passed to the client. Default: 127.0.0.1.
  --ws-port PORT          WebSocket port passed to the client. Default: 43496.
  --ws URL                Full WebSocket URL passed as ?ws=. Use this for WSS proxy paths.
  --user USER             Login username. Default: test.
  --pass PASS             Login password. Default: test.
  --portal URL            Portal base URL for login Create Account/Recover account checks.
  --portal-account-url URL
                          Explicit Create Account portal URL.
  --portal-recovery-url URL
                          Explicit Recover account portal URL.
  --out DIR               Output directory for screenshots/logs. Default: tmp/web-teavm-smoke.
  --chrome PATH           Chrome/Chromium executable path.
  --playwright-core DIR   Directory for playwright-core package.
  --ignore-https-errors   Allow self-signed HTTPS certificates. Use only for local smoke.
  --only-world-map-search Login and run only the real world-map open/pan/zoom/search/walker/close proof.
  --only-chat             Login and run only the authenticated in-game mobile chat proof.
  --only-context-menu     Login and run only the real mobile long-press context-menu proof.
  -h, --help              Show this help.

Requires a running Voidscape server with WebSockets enabled.

With --base-url, pass the deployed root/directory URL, for example:
  scripts/smoke-web-teavm-iphone.sh --base-url https://play.example.com/ --ws wss://play.example.com/ws/

Playwright resolution order:
  1. --playwright-core / PLAYWRIGHT_CORE_DIR
  2. ./node_modules/playwright-core
  3. /tmp/voidscape-playwright-smoke/node_modules/playwright-core
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--base-url)
			BASE_URL_OVERRIDE="${2:-}"
			BUILD=0
			shift 2
			;;
		--web-port)
			WEB_PORT="${2:-}"
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
		--portal)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--portal-account-url)
			PORTAL_ACCOUNT_URL="${2:-}"
			shift 2
			;;
		--portal-recovery-url)
			PORTAL_RECOVERY_URL="${2:-}"
			shift 2
			;;
		--out)
			OUT_DIR="${2:-}"
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
		--only-world-map-search)
			ONLY_WORLD_MAP_SEARCH=1
			shift
			;;
		--only-chat)
			ONLY_CHAT=1
			shift
			;;
		--only-context-menu)
			ONLY_CONTEXT_MENU=1
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

if [[ -z "$HOST" || -z "$WS_PORT" || -z "$AUTH_USER" || -z "$AUTH_PASS" || -z "$PORTAL_URL" ]]; then
	echo "ERROR: host, ws-port, user, pass, and portal must be non-empty." >&2
	exit 2
fi

REMOTE_MODE=0
if [[ -n "$BASE_URL_OVERRIDE" ]]; then
	REMOTE_MODE=1
fi

if [[ "$REMOTE_MODE" -eq 0 && "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ "$REMOTE_MODE" -eq 0 && ! -f "$TARGET/index.html" ]]; then
	echo "ERROR: missing TeaVM output at $TARGET/index.html. Run scripts/build-web-teavm-spike.sh first." >&2
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

if [[ "$REMOTE_MODE" -eq 0 && -z "$WS_URL" && ( "$HOST" == "127.0.0.1" || "$HOST" == "localhost" ) ]]; then
	if ! lsof -nP -iTCP:"$WS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
		echo "ERROR: no local listener found on WebSocket port $WS_PORT. Start scripts/run-server.sh first." >&2
		exit 1
	fi
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

if [[ "$REMOTE_MODE" -eq 0 && "$WEB_PORT" == "0" ]]; then
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
HTTP_PID=
if [[ "$REMOTE_MODE" -eq 0 ]]; then
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

export PLAYWRIGHT_CORE_DIR CHROME_PATH OUT_DIR AUTH_USER AUTH_PASS HOST WS_PORT WS_URL REMOTE_MODE IGNORE_HTTPS_ERRORS
export PORTAL_URL PORTAL_ACCOUNT_URL PORTAL_RECOVERY_URL
export ONLY_WORLD_MAP_SEARCH ONLY_CHAT ONLY_CONTEXT_MENU
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
const portalUrl = process.env.PORTAL_URL || '/iphone-account/';
const portalAccountUrl = process.env.PORTAL_ACCOUNT_URL || '';
const portalRecoveryUrl = process.env.PORTAL_RECOVERY_URL || '';
const onlyWorldMapSearch = process.env.ONLY_WORLD_MAP_SEARCH === '1';
const onlyChat = process.env.ONLY_CHAT === '1';
const onlyContextMenu = process.env.ONLY_CONTEXT_MENU === '1';
const focusedSmoke = onlyWorldMapSearch || onlyChat || onlyContextMenu;
let browser = null;

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertNoOverlap(a, b, label) {
  assert(a && b && a.rect && b.rect, `${label}: missing rectangles`);
  const overlaps = a.rect.left < b.rect.right
    && a.rect.right > b.rect.left
    && a.rect.top < b.rect.bottom
    && a.rect.bottom > b.rect.top;
  assert(!overlaps, `${label} should not overlap: ${JSON.stringify(a.rect)} vs ${JSON.stringify(b.rect)}`);
}

function assertAndroidParityCanvasControls(controls, label) {
  const keyboardButton = controls && controls.keyboardButton;
  const panelButton = controls && controls.panelButton;
  const chatButton = controls && controls.chatButton;
  const chatTray = controls && controls.chatTray;
  assert(keyboardButton && keyboardButton.present && keyboardButton.visible,
    `${label}: keyboardButton should remain visible for Safari input parity: ${JSON.stringify(keyboardButton)}`);
  assert(keyboardButton.display === 'flex',
    `${label}: keyboardButton display should be flex: ${JSON.stringify(keyboardButton)}`);
  assert(keyboardButton.hitSelf === true,
    `${label}: keyboardButton should be topmost at center: ${JSON.stringify(keyboardButton)}`);
  assert(panelButton && panelButton.present && panelButton.display === 'none' && panelButton.visible === false,
    `${label}: DOM panel button should stay hidden while Android-parity canvas tabs own panels: ${JSON.stringify(panelButton)}`);
  assert(chatButton && chatButton.present && chatButton.display === 'none' && chatButton.visible === false,
    `${label}: DOM chat helper should stay hidden while canvas chat tabs own chat: ${JSON.stringify(chatButton)}`);
  assert(!chatTray || chatTray.display === 'none',
    `${label}: DOM chat tray should stay closed in Android-parity canvas-chat mode: ${JSON.stringify(chatTray)}`);
}

function assertDiagnosticsControlHistory(history, label) {
  assert(history && history.portrait && history.landscape,
    `${label}: diagnostics should include portrait and landscape control history: ${JSON.stringify(history)}`);
  for (const orientation of ['portrait', 'landscape']) {
    const entry = history[orientation];
    assert(entry.orientation === orientation,
      `${label}: controlsHistory.${orientation}.orientation mismatch: ${JSON.stringify(entry)}`);
    assert(entry.updatedAt,
      `${label}: controlsHistory.${orientation}.updatedAt should be present`);
    assert(entry.viewport && entry.viewport.innerWidth > 0 && entry.viewport.innerHeight > 0,
      `${label}: controlsHistory.${orientation}.viewport should be nonzero: ${JSON.stringify(entry)}`);
    if (orientation === 'portrait') {
      assert(entry.viewport.innerWidth <= entry.viewport.innerHeight,
        `${label}: controlsHistory.portrait should have portrait viewport: ${JSON.stringify(entry.viewport)}`);
    } else {
      assert(entry.viewport.innerWidth > entry.viewport.innerHeight,
        `${label}: controlsHistory.landscape should have landscape viewport: ${JSON.stringify(entry.viewport)}`);
    }
    assert(entry.panelAccessMode === 'canvas' && entry.mobilePanelShell === false
        && entry.canvasTopTabsVisible === true && entry.canvasPanelRailVisible === false
        && entry.canvasPanelDockVisible === false,
      `${label}: controlsHistory.${orientation} should record Android-parity canvas panel access: ${JSON.stringify(entry)}`);
    const expectedDisplays = orientation === 'portrait'
      ? {
        keyboardButton: 'flex',
        inventoryButton: 'flex',
        magicButton: 'flex',
        prayerButton: 'flex',
        cameraControls: 'grid',
        diagnosticsButton: 'flex',
        diagnosticsCopyButton: 'flex'
      }
      : {
        keyboardButton: 'flex',
        inventoryButton: 'none',
        magicButton: 'none',
        prayerButton: 'none',
        cameraControls: 'grid',
        diagnosticsButton: 'flex',
        diagnosticsCopyButton: 'flex'
      };
    for (const [key, expectedDisplay] of Object.entries(expectedDisplays)) {
      const control = entry.controls && entry.controls[key];
      assert(control && control.present,
        `${label}: controlsHistory.${orientation}.${key} should be present: ${JSON.stringify(control)}`);
      assert(control.visible === (expectedDisplay !== 'none'),
        `${label}: controlsHistory.${orientation}.${key} visibility should match ${expectedDisplay}: ${JSON.stringify(control)}`);
      assert(control.display === expectedDisplay,
        `${label}: controlsHistory.${orientation}.${key} display should be ${expectedDisplay}: ${JSON.stringify(control)}`);
      if (expectedDisplay !== 'none') {
        assert(control.hitSelf === true,
          `${label}: controlsHistory.${orientation}.${key} should be topmost at center: ${JSON.stringify(control)}`);
      }
    }
    assertAndroidParityCanvasControls(entry.controls, `${label}: controlsHistory.${orientation}`);
  }
}

function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function pageUrl(pathValue) {
  return new URL(pathValue, baseUrl).href;
}

function launchPath(params = {}) {
  const query = new URLSearchParams();
  query.set('mobile', '1');
  if (params.diag === true) query.set('diag', '1');
  if (params.diag === false) query.set('diag', '0');
  if (params.reset) query.set('endpoint', 'reset');
  if (params.ws) query.set('ws', params.ws);
  if (params.hostPort) {
    query.set('host', host);
    query.set('port', String(wsPort));
  }
  if (params.portal) query.set('portal', params.portal);
  if (params.portalAccountUrl) query.set('portalAccountUrl', params.portalAccountUrl);
  if (params.portalRecoveryUrl) query.set('portalRecoveryUrl', params.portalRecoveryUrl);
  return `index.html?${query.toString()}`;
}

function portalLaunchParams() {
  const params = {};
  if (portalUrl) params.portal = portalUrl;
  if (portalAccountUrl) params.portalAccountUrl = portalAccountUrl;
  if (portalRecoveryUrl) params.portalRecoveryUrl = portalRecoveryUrl;
  return params;
}

function resolvedHttpUrl(value) {
  return new URL(value, baseUrl).href;
}

function derivedPortalUrl(base, hash) {
  const url = new URL(base || '/', baseUrl);
  url.hash = hash;
  return url.href;
}

function expectedPortalAccountUrl() {
  return portalAccountUrl ? resolvedHttpUrl(portalAccountUrl) : derivedPortalUrl(portalUrl, 'account');
}

function expectedPortalRecoveryUrl() {
  return portalRecoveryUrl ? resolvedHttpUrl(portalRecoveryUrl) : derivedPortalUrl(portalUrl, 'security');
}

function localWsUrl() {
  return `ws://${host}:${wsPort}/`;
}

function expectedLocalEndpointUrl() {
  return explicitWs || localWsUrl();
}

async function waitForClient(page) {
  await page.waitForSelector('canvas', { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeEndpoint, null, { timeout: 30000 });
  await page.waitForFunction(() => typeof window.__voidscapeCollectDiagnostics === 'function', null, { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeInputInstalled && Array.isArray(window.__voidscapeInputQueue), null, { timeout: 30000 });
}

async function framePoint(page, x, y) {
  return await page.evaluate(({ x, y }) => {
    const canvas = document.querySelector('canvas');
    const rect = canvas.getBoundingClientRect();
    return {
      x: rect.left + (x * rect.width / canvas.width),
      y: rect.top + (y * rect.height / canvas.height)
    };
  }, { x, y });
}

async function assertCanvasPointUnblocked(page, point, frame, label) {
  const hit = await page.evaluate(({ point }) => {
    const canvas = document.querySelector('canvas');
    const element = document.elementFromPoint(point.x, point.y);
    const bypassDiagnostics = !!window.__voidscapeSmokeBypassDiagnosticsHitTesting
      && !!element
      && (element.id === 'diagnostics-button' || element.id === 'diagnostics-copy-button');
    function describe(node) {
      if (!node) return null;
      const style = window.getComputedStyle(node);
      return {
        tag: node.tagName,
        id: node.id || '',
        className: typeof node.className === 'string' ? node.className : '',
        display: style.display,
        visibility: style.visibility,
        pointerEvents: style.pointerEvents,
        text: (node.textContent || '').trim().slice(0, 80)
      };
    }
    return {
      ok: !!canvas && (element === canvas || canvas.contains(element) || bypassDiagnostics),
      bypassDiagnostics,
      point,
      element: describe(element),
      canvas: describe(canvas)
    };
  }, { point });
  assert(hit.ok,
    `${label} canvas point ${JSON.stringify(frame)} maps to ${JSON.stringify(point)} but browser hit ${JSON.stringify(hit.element)}`);
}

async function clickFrame(page, x, y) {
  const point = await framePoint(page, x, y);
  await page.mouse.click(Math.round(point.x), Math.round(point.y));
}

async function tapFrame(page, x, y) {
  const point = await framePoint(page, x, y);
  await page.touchscreen.tap(Math.round(point.x), Math.round(point.y));
}

async function pointerFrame(page, type, x, y, options = {}) {
  const point = await framePoint(page, x, y);
  if (type === 'pointerdown' && options.skipHitTest !== true) {
    await assertCanvasPointUnblocked(page, point, { x, y }, options.label || type);
  }
  await page.evaluate(({ type, point, options }) => {
    const canvas = document.querySelector('canvas');
    const event = new PointerEvent(type, {
      bubbles: true,
      cancelable: true,
      pointerId: options.pointerId || 1,
      pointerType: options.pointerType || 'touch',
      isPrimary: options.isPrimary !== false,
      clientX: point.x,
      clientY: point.y,
      button: options.button == null ? 0 : options.button,
      buttons: options.buttons == null ? (type === 'pointerup' || type === 'pointercancel' ? 0 : 1) : options.buttons
    });
    canvas.dispatchEvent(event);
  }, { type, point, options });
}

async function pointerTapFrame(page, x, y) {
  await pointerFrame(page, 'pointerdown', x, y, { buttons: 1 });
  await page.waitForTimeout(90);
  await pointerFrame(page, 'pointerup', x, y, { buttons: 0 });
  await page.waitForTimeout(180);
}

async function readPortalHandoff(page) {
  return await page.evaluate(() => ({
    config: window.__voidscapePortalConfig || null,
    opened: (window.__voidscapeOpenedUrls || []).slice(),
    lastOpenedUrl: window.__voidscapeLastOpenedUrl || '',
    recordedInputEvents: (window.__voidscapeSmokeQueuedEvents || []).slice(-32),
    snapshot: typeof window.__voidscapeCollectDiagnostics === 'function'
      ? window.__voidscapeCollectDiagnostics()
      : null
  }));
}

async function waitForPortalKind(page, kind, timeoutMs) {
  return await page.waitForFunction((expectedKind) => {
    return (window.__voidscapeOpenedUrls || []).some((entry) => entry.kind === expectedKind);
  }, kind, { timeout: timeoutMs }).then(() => true).catch(() => false);
}

async function tapPortalButton(page, kind, label, points) {
  let state = await readPortalHandoff(page);
  for (let attempt = 0; attempt < 5; attempt++) {
    const point = points[Math.min(attempt, points.length - 1)];
    await pointerTapFrame(page, point.x, point.y);
    if (await waitForPortalKind(page, kind, 1500)) {
      return await readPortalHandoff(page);
    }
    await page.waitForTimeout(250);
    state = await readPortalHandoff(page);
  }
  throw new Error(`${label} portal handoff did not open ${kind}: ${JSON.stringify(state)}`);
}

async function touchElement(page, selector) {
  const point = await page.evaluate((selector) => {
    const element = document.querySelector(selector);
    if (!element) throw new Error(`missing touch target ${selector}`);
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) throw new Error(`hidden touch target ${selector}`);
    return {
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2
    };
  }, selector);
  await page.touchscreen.tap(Math.round(point.x), Math.round(point.y));
}

async function setDiagnosticsHitTesting(page, enabled) {
  await page.evaluate((enabled) => {
    window.__voidscapeSmokeBypassDiagnosticsHitTesting = !enabled;
    for (const id of ['diagnostics-button', 'diagnostics-copy-button']) {
      const element = document.getElementById(id);
      if (element) element.style.pointerEvents = '';
    }
  }, enabled);
}

async function captureVisualArtifact(page, filename, options = {}) {
  const artifactPath = path.join(outDir, filename);
  const selectors = [];
  if (options.hideDiagnostics) selectors.push('#diagnostics-button', '#diagnostics-copy-button', '#diagnostics-panel');
  if (options.hideCamera) selectors.push('#camera-controls');
  const previous = await page.evaluate((selectors) => {
    return selectors.map((selector) => {
      const element = document.querySelector(selector);
      if (!element) return { selector, missing: true };
      const previous = element.style.visibility;
      element.style.visibility = 'hidden';
      return { selector, previous };
    });
  }, selectors);
  try {
    await page.screenshot({ path: artifactPath, fullPage: true });
  } finally {
    await page.evaluate((previous) => {
      for (const entry of previous) {
        if (!entry || entry.missing) continue;
        const element = document.querySelector(entry.selector);
        if (element) element.style.visibility = entry.previous || '';
      }
      if (typeof window.__voidscapeCollectDiagnostics === 'function') {
        window.__voidscapeCollectDiagnostics();
      }
    }, previous);
  }
  return artifactPath;
}

async function disarmSecondaryTap(page) {
  await page.evaluate(() => {
    window.__voidscapeNextTapSecondary = false;
    if (document.body) document.body.classList.remove('context-armed');
  });
}

async function touchDragFrame(page, start, end) {
  await pointerFrame(page, 'pointerdown', start.x, start.y, { buttons: 1 });
  await pointerFrame(page, 'pointermove', end.x, end.y, { buttons: 1 });
  await page.waitForTimeout(180);
  await pointerFrame(page, 'pointerup', end.x, end.y, { buttons: 0 });
}

async function pinchFrame(page, zoomOut) {
  const points = safeCameraGesturePoints(await currentFrameSize(page));
  const p1 = points.pinchPrimary;
  const startX = p1.x + points.pinchStartDistance;
  await pointerFrame(page, 'pointerdown', p1.x, p1.y, { pointerId: 1, isPrimary: true, buttons: 1 });
  await pointerFrame(page, 'pointerdown', startX, p1.y, { pointerId: 2, isPrimary: false, buttons: 1 });
  const endDistance = zoomOut ? points.pinchInDistance : points.pinchOutDistance;
  const midpointDistance = Math.round((points.pinchStartDistance + endDistance) / 2);
  const moves = [p1.x + midpointDistance, p1.x + endDistance];
  for (const x of moves) {
    await pointerFrame(page, 'pointermove', x, p1.y, { pointerId: 2, isPrimary: false, buttons: 1 });
    await page.waitForTimeout(180);
  }
  const endX = moves[moves.length - 1];
  await pointerFrame(page, 'pointerup', endX, p1.y, { pointerId: 2, isPrimary: false, buttons: 0 });
  await pointerFrame(page, 'pointerup', p1.x, p1.y, { pointerId: 1, isPrimary: true, buttons: 0 });
  await page.waitForTimeout(420);
}

async function diagnosticsSnapshot(page) {
  return await page.evaluate(() => window.__voidscapeCollectDiagnostics());
}

async function proveLifecycleResume(page) {
  const report = await page.evaluate(async () => {
    const before = window.__voidscapeCollectDiagnostics().lifecycle;
    function transitionEvent(type) {
      if (typeof PageTransitionEvent === 'function') {
        return new PageTransitionEvent(type, { persisted: true });
      }
      return new Event(type);
    }
    window.dispatchEvent(new Event('blur'));
    window.dispatchEvent(transitionEvent('pagehide'));
    window.dispatchEvent(transitionEvent('pageshow'));
    window.dispatchEvent(new Event('focus'));
    document.dispatchEvent(new Event('visibilitychange'));
    await new Promise((resolve) => setTimeout(resolve, 950));
    const snapshot = window.__voidscapeCollectDiagnostics();
    return {
      before,
      after: snapshot.lifecycle,
      viewport: snapshot.viewport,
      recentErrors: snapshot.recentErrors
    };
  });
  assert(report.after.resumeCount > report.before.resumeCount,
    `lifecycle resume should increment resumeCount: ${JSON.stringify(report)}`);
  assert(report.after.pageShowCount > report.before.pageShowCount,
    `lifecycle pageshow should be recorded: ${JSON.stringify(report)}`);
  assert(report.after.focusCount > report.before.focusCount,
    `lifecycle focus should be recorded: ${JSON.stringify(report)}`);
  assert(report.after.viewportUpdateCount > report.before.viewportUpdateCount,
    `lifecycle resume should refresh viewport height: ${JSON.stringify(report)}`);
  assert(report.viewport.scrollX === 0 && report.viewport.scrollY === 0,
    `lifecycle resume should leave page scroll locked: ${JSON.stringify(report.viewport)}`);
  assert(report.recentErrors.length === 0,
    `lifecycle resume should not dirty diagnostics: ${report.recentErrors.join('; ')}`);
  return report;
}

async function waitForClientState(page) {
	await page.waitForFunction(() => {
		const state = window.__voidscapeClientState;
		return !!state && state.hasLocalPlayer && window.__voidscapeInGame;
	}, null, { timeout: 10000 });
	return await page.evaluate(() => ({ ...window.__voidscapeClientState }));
}

async function waitForUiState(page) {
	await page.waitForFunction(() => {
		const snapshot = window.__voidscapeCollectDiagnostics();
		const ui = snapshot && snapshot.ui;
		return !!ui && window.__voidscapeInGame;
	}, null, { timeout: 10000 });
	return await page.evaluate(() => ({ ...window.__voidscapeUiState }));
}

function voidscapePanelSizeClass(gameWidth) {
  if (gameWidth <= 680) return 0;
  if (gameWidth <= 760) return 1;
  if (gameWidth <= 860) return 2;
  if (gameWidth <= 960) return 3;
  return 4;
}

function customHudChatTabFramePoint(canvas, index) {
  const gameWidth = canvas.width;
  const gameHeight = canvas.height - 12;
  const sizeClass = voidscapePanelSizeClass(gameWidth);
  const compact = sizeClass === 0;
  const accountButtonWidth = compact ? 54 : 62;
  const bottomButtonGap = compact ? 5 : 7;
  const bottomReservedWidth = bottomButtonGap + accountButtonWidth + 8;
  const frameX = 8;
  const standardPanelVisualWidths = [196, 204, 216, 228, 240];
  const rightPanelInsets = [14, 15, 16, 18, 18];
  const rightPanelReadableInset = rightPanelInsets[sizeClass] + (compact ? 4 : 6);
  const rightPanelBaseWidth = standardPanelVisualWidths[sizeClass] + rightPanelReadableInset * 2;
  const stableHomeX = gameWidth - rightPanelBaseWidth - 18;
  const accountSafeRight = gameWidth - bottomReservedWidth;
  const maxFrameWidth = Math.min(stableHomeX - 20, accountSafeRight - frameX);
  const frameWidth = Math.max(300, Math.min(660, maxFrameWidth));
  const tabCount = 5;
  const margin = compact ? 4 : 6;
  const gap = compact ? 3 : 5;
  const tabHeight = compact ? 32 : 40;
  const tabTop = gameHeight - (compact ? 28 : 32);
  const avail = frameWidth - (margin * 2) - (gap * (tabCount - 1));
  const minWidth = 42;
  const baseWidth = Math.max(minWidth, Math.floor(avail / tabCount));
  const tabWidth = index === tabCount - 1
    ? Math.max(minWidth, avail - baseWidth * (tabCount - 1))
    : baseWidth;
  const tabX = frameX + margin + index * (baseWidth + gap);
  return {
    x: Math.round(tabX + tabWidth / 2),
    y: Math.round(tabTop + tabHeight / 2),
    rect: {
      left: tabX,
      top: tabTop,
      width: tabWidth,
      height: tabHeight
    }
  };
}

function customHudTopTabFramePoint(canvas, index) {
 const gameWidth = canvas.width;
 const sizeClass = voidscapePanelSizeClass(gameWidth);
  const compact = sizeClass === 0;
  const topTabSizes = [52, 54, 56, 58, 60];
  const tabSize = topTabSizes[sizeClass];
  const gap = compact ? 1 : 2;
  const tabCount = 6;
  const span = (tabSize * tabCount) + (gap * (tabCount - 1));
  const startX = gameWidth - span - 3;
  const y = 3;
  const x = startX + index * (tabSize + gap);
  return {
    x: Math.round(x + tabSize / 2),
    y: Math.round(y + tabSize / 2),
    rect: {
      left: x,
      top: y,
      width: tabSize,
      height: tabSize
    }
  };
}

function customHudSafeCanvasClosePoint(canvas) {
  const minX = Math.min(canvas.width - 24, 132);
  const maxX = Math.max(minX, canvas.width - 260);
  const minY = Math.min(canvas.height - 24, 128);
  const maxY = Math.max(minY, canvas.height - 140);
  return {
    x: Math.round(Math.max(minX, Math.min(maxX, canvas.width * 0.33))),
    y: Math.round(Math.max(minY, Math.min(maxY, canvas.height * 0.58)))
  };
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

function safeCameraGesturePoints(frame) {
  const width = Math.max(160, frame.width || 512);
  const height = Math.max(160, frame.height || 346);
  const centerX = clampFramePoint(width * 0.52, 96, width - 96);
  const centerY = clampFramePoint(height * 0.52, 96, height - 96);
  const horizontalDelta = Math.max(48, Math.min(96, Math.floor(width * 0.17)));
  const verticalDelta = Math.max(48, Math.min(86, Math.floor(height * 0.18)));
  return {
    rotationStart: { x: centerX + horizontalDelta, y: centerY },
    rotationEnd: { x: centerX - horizontalDelta, y: centerY },
    zoomUp: { x: centerX, y: centerY - verticalDelta },
    zoomDown: { x: centerX, y: centerY + verticalDelta },
    pinchPrimary: { x: clampFramePoint(width * 0.36, 72, width - 160), y: centerY },
    pinchStartDistance: Math.max(64, Math.min(96, Math.floor(width * 0.16))),
    pinchInDistance: Math.max(18, Math.min(36, Math.floor(width * 0.05))),
    pinchOutDistance: Math.max(112, Math.min(156, Math.floor(width * 0.28)))
  };
}

const REQUIRED_CUSTOM_HUD_MESSAGE_TABS = ['ALL', 'CHAT', 'QUEST', 'PRIVATE'];
const REQUIRED_CUSTOM_HUD_TOP_TABS = [1, 2, 3, 4, 5, 6];

async function waitForMessageTab(page, expected, label) {
  try {
    await page.waitForFunction((expected) => {
      const ui = window.__voidscapeUiState;
      return ui && window.__voidscapeInGame && !ui.blockingDialog && ui.messageTab === expected;
    }, expected, { timeout: 5000 });
  } catch (error) {
    const ui = await page.evaluate(() => window.__voidscapeUiState || null).catch(() => null);
    throw new Error(`${label}: timed out waiting for message tab ${expected}; current ui=${JSON.stringify(ui)}; ${error.message}`);
  }
  const ui = await waitForUiState(page);
  assert(ui.messageTab === expected, `${label}: expected message tab ${expected}, got ${JSON.stringify(ui)}`);
  return ui;
}

async function waitForUiTab(page, expected, label) {
  try {
    await page.waitForFunction((expected) => {
      const ui = window.__voidscapeUiState;
      return ui && window.__voidscapeInGame && !ui.blockingDialog && ui.showUiTab === expected;
    }, expected, { timeout: 5000 });
  } catch (error) {
    const ui = await page.evaluate(() => window.__voidscapeUiState || null).catch(() => null);
    throw new Error(`${label}: timed out waiting for showUiTab ${expected}; current ui=${JSON.stringify(ui)}; ${error.message}`);
  }
  const ui = await waitForUiState(page);
  assert(ui.showUiTab === expected, `${label}: expected showUiTab ${expected}, got ${JSON.stringify(ui)}`);
  return ui;
}

function uiHistoryEvidence(history) {
  const messageTabs = new Set();
  const showUiTabs = new Set();
  let sawAnyTopPanelOpen = false;
  let sawTopPanelCloseAfterOpen = false;
  if (history && Array.isArray(history.messageTabs)) {
    for (const tab of history.messageTabs) {
      messageTabs.add(String(tab));
    }
  }
  if (history && Array.isArray(history.showUiTabs)) {
    for (const tab of history.showUiTabs) {
      const numeric = Number(tab);
      if (Number.isFinite(numeric)) showUiTabs.add(numeric);
    }
  }
  if (history && Array.isArray(history.events)) {
    for (const event of history.events) {
      if (!event || typeof event !== 'object') continue;
      if (event.kind === 'messageTab') {
        messageTabs.add(String(event.messageTab || event.value || ''));
      }
      if (event.kind === 'showUiTab') {
        const raw = event.showUiTab === undefined || event.showUiTab === null ? event.value : event.showUiTab;
        const numeric = Number(raw);
        if (!Number.isFinite(numeric)) continue;
        showUiTabs.add(numeric);
        if (numeric !== 0) sawAnyTopPanelOpen = true;
        if (sawAnyTopPanelOpen && numeric === 0) sawTopPanelCloseAfterOpen = true;
      }
    }
  }
  return {
    messageTabs: Array.from(messageTabs),
    showUiTabs: Array.from(showUiTabs),
    sawAnyTopPanelOpen,
    sawTopPanelCloseAfterOpen
  };
}

function assertCustomHudUiHistory(history, label) {
  assert(history && typeof history === 'object', `${label} should include uiHistory: ${JSON.stringify(history)}`);
  assert(Array.isArray(history.events), `${label} uiHistory.events should be an array: ${JSON.stringify(history)}`);
  const evidence = uiHistoryEvidence(history);
  for (const tab of REQUIRED_CUSTOM_HUD_MESSAGE_TABS) {
    assert(evidence.messageTabs.includes(tab),
      `${label} should record selecting ${tab} chat mode: ${JSON.stringify(evidence)} history=${JSON.stringify(history)}`);
  }
  for (const tabId of REQUIRED_CUSTOM_HUD_TOP_TABS) {
    assert(evidence.showUiTabs.includes(tabId),
      `${label} should record opening shared panel ${tabId}: ${JSON.stringify(evidence)} history=${JSON.stringify(history)}`);
  }
  assert(evidence.sawTopPanelCloseAfterOpen,
    `${label} should record closing a shared panel after opening it: ${JSON.stringify(evidence)} history=${JSON.stringify(history)}`);
  return evidence;
}

function assertScrollHistory(history, label) {
  assert(Array.isArray(history), `${label} should include scrollHistory: ${JSON.stringify(history)}`);
  const panelScroll = history.find((entry) => entry
    && entry.scrollableUi === true
    && [1, 2, 3, 4, 5, 6].includes(Number(entry.showUiTab))
    && Number(entry.amount) !== 0);
  assert(panelScroll,
    `${label} should record a nonzero shared-panel scroll gesture: ${JSON.stringify(history)}`);
  return panelScroll;
}

function assertPostResumeProof(proof, label) {
  assert(proof && typeof proof === 'object',
    `${label} should include postResumeProof: ${JSON.stringify(proof)}`);
  assert(proof.lastResumeAt,
    `${label} postResumeProof.lastResumeAt should be present: ${JSON.stringify(proof)}`);
  assert(Number(proof.resumeCount) > 0,
    `${label} postResumeProof.resumeCount should be > 0: ${JSON.stringify(proof)}`);
  assert(proof.inputAfterResume === true,
    `${label} should record input after resume: ${JSON.stringify(proof)}`);
  assert(proof.movementAfterResume === true,
    `${label} should record movement after post-resume input: ${JSON.stringify(proof)}`);
  assert(proof.lastInputAfterResumeAt,
    `${label} postResumeProof.lastInputAfterResumeAt should be present: ${JSON.stringify(proof)}`);
  assert(proof.lastMovementAfterResumeAt,
    `${label} postResumeProof.lastMovementAfterResumeAt should be present: ${JSON.stringify(proof)}`);
  assert(Array.isArray(proof.events),
    `${label} postResumeProof.events should be an array: ${JSON.stringify(proof)}`);
  assert(proof.events.some((event) => event && event.kind === 'input'),
    `${label} postResumeProof.events should include input: ${JSON.stringify(proof)}`);
  assert(proof.events.some((event) => event && event.kind === 'movement'),
    `${label} postResumeProof.events should include movement: ${JSON.stringify(proof)}`);
  assert(proof.lastMovementFrom && proof.lastMovementTo,
    `${label} postResumeProof should include movement endpoints: ${JSON.stringify(proof)}`);
  return proof;
}

async function waitForPostResumeProof(page, label) {
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const proof = snapshot && snapshot.postResumeProof;
    return !!proof && proof.inputAfterResume === true && proof.movementAfterResume === true;
  }, null, { timeout: 9000 });
  const proof = (await diagnosticsSnapshot(page)).postResumeProof;
  return assertPostResumeProof(proof, label);
}

async function waitForCustomHudUiHistory(page, label) {
  await page.waitForFunction(({ requiredMessageTabs, requiredTopTabs }) => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const history = snapshot && snapshot.uiHistory;
    if (!history || !Array.isArray(history.events)) return false;
    const messageTabs = new Set(Array.isArray(history.messageTabs) ? history.messageTabs.map(String) : []);
    const showUiTabs = new Set(Array.isArray(history.showUiTabs) ? history.showUiTabs.map(Number).filter(Number.isFinite) : []);
    let sawAnyTopPanelOpen = false;
    let sawTopPanelCloseAfterOpen = false;
    for (const event of history.events) {
      if (!event || typeof event !== 'object') continue;
      if (event.kind === 'messageTab') {
        messageTabs.add(String(event.messageTab || event.value || ''));
      }
      if (event.kind === 'showUiTab') {
        const raw = event.showUiTab === undefined || event.showUiTab === null ? event.value : event.showUiTab;
        const numeric = Number(raw);
        if (!Number.isFinite(numeric)) continue;
        showUiTabs.add(numeric);
        if (numeric !== 0) sawAnyTopPanelOpen = true;
        if (sawAnyTopPanelOpen && numeric === 0) sawTopPanelCloseAfterOpen = true;
      }
    }
    return requiredMessageTabs.every((tab) => messageTabs.has(tab))
      && requiredTopTabs.every((tabId) => showUiTabs.has(tabId))
      && sawTopPanelCloseAfterOpen;
  }, {
    requiredMessageTabs: REQUIRED_CUSTOM_HUD_MESSAGE_TABS,
    requiredTopTabs: REQUIRED_CUSTOM_HUD_TOP_TABS
  }, { timeout: 5000 });
  const history = (await diagnosticsSnapshot(page)).uiHistory;
  assertCustomHudUiHistory(history, label);
  return history;
}

async function proveCustomHudChatTabs(page) {
  const before = await waitForUiState(page);
  assert(before.customUi && before.webBuild && before.androidProfile,
    `custom HUD chat modes require shared mobile Voidscape UI: ${JSON.stringify(before)}`);
  assert(!before.blockingDialog, `custom HUD chat modes require blocking dialogs closed: ${JSON.stringify(before)}`);
  const canvas = await page.evaluate(() => {
    const element = document.querySelector('canvas');
    return {
      width: element.width,
      height: element.height
    };
  });
  const targets = [
    { index: 1, tab: 'CHAT' },
    { index: 2, tab: 'QUEST' },
    { index: 3, tab: 'PRIVATE' },
    { index: 0, tab: 'ALL' }
  ];
  const steps = [];
  for (const target of targets) {
    await disarmSecondaryTap(page);
    const point = customHudChatTabFramePoint(canvas, target.index);
    await clearQueueRecorder(page);
    await pointerTapFrame(page, point.x, point.y);
    const after = await waitForMessageTab(page, target.tab, `custom HUD ${target.tab} chat mode`);
    await page.waitForTimeout(180);
    const events = await recordedQueueEvents(page);
    assert(events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
      `custom HUD ${target.tab} chat mode should enqueue a real canvas tap: ${events.join(' ')}`);
    steps.push({ target, point, events, after });
  }
  return {
    before,
    canvas,
    steps,
    after: steps[steps.length - 1].after
  };
}

async function proveCustomHudTopPanels(page) {
  const before = await waitForUiState(page);
  assert(before.customUi && before.webBuild && before.androidProfile,
    `Android-parity panel proof requires shared mobile Voidscape UI: ${JSON.stringify(before)}`);
  assert(before.mobilePanelShell === false && before.panelAccessMode === 'canvas',
    `Android-parity panel proof should use the shared canvas tab path: ${JSON.stringify(before)}`);
  assert(before.canvasTopTabsVisible === true && before.canvasPanelRailVisible === false && before.canvasPanelDockVisible === false,
    `Android-parity panel proof should show canvas top tabs and hide web-only rails/docks: ${JSON.stringify(before)}`);
  assert(!before.blockingDialog, `Android-parity panel proof requires blocking dialogs closed: ${JSON.stringify(before)}`);
  const canvas = await page.evaluate(() => {
    const element = document.querySelector('canvas');
    return {
      width: element.width,
      height: element.height
    };
  });
  const targets = [
    { index: 3, tabId: 3, label: 'SKILLS_QUESTS' },
    { index: 5, tabId: 1, label: 'INVENTORY' },
    { index: 2, tabId: 4, label: 'MAGIC_PRAYER' },
    { index: 4, tabId: 2, label: 'MAP' },
    { index: 1, tabId: 5, label: 'FRIENDS' },
    { index: 0, tabId: 6, label: 'OPTIONS' }
  ];
  const steps = [];
  for (const target of targets) {
    await disarmSecondaryTap(page);
    await page.evaluate(() => window.__voidscapeInputQueue.push('u,hud'));
    const closedBefore = await waitForUiTab(page, 0, `Android-parity panel cleanup before ${target.label}`);
    const point = customHudTopTabFramePoint(canvas, target.index);
    await clearQueueRecorder(page);
    await pointerTapFrame(page, point.x, point.y);
    const opened = await waitForUiTab(page, target.tabId, `Android-parity canvas top tab ${target.label}`);
    const events = await recordedQueueEvents(page);
    assert(events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
      `Android-parity canvas top tab ${target.label} should enqueue a normal canvas tap: ${events.join(' ')}`);
    steps.push({ target, point, events, closedBefore, opened });
  }
  const closePoint = customHudSafeCanvasClosePoint(canvas);
  await clearQueueRecorder(page);
  await pointerFrame(page, 'pointermove', closePoint.x, closePoint.y, {
    buttons: 0,
    label: 'Android-parity canvas top tab hover auto-close'
  });
  const closedAfter = await waitForUiTab(page, 0, 'Android-parity canvas top tab hover auto-close after panel proof');
  const closeEvents = await recordedQueueEvents(page);
  assert(closeEvents.some((entry) => /^p,0,\d+,\d+,0$/.test(entry)),
    `Android-parity canvas top tab auto-close should enqueue a zero-button pointer move: ${closeEvents.join(' ')}`);
  return {
    before,
    canvas,
    steps,
    closePoint,
    closeEvents,
    closedAfter,
    after: closedAfter
  };
}

async function openMobilePanelDrawer(page, label) {
	for (let attempt = 0; attempt < 4; attempt++) {
		const alreadyOpen = await page.evaluate(() => !!window.__voidscapePanelDrawerOpen
			&& document.body.classList.contains('panel-drawer-open'));
		if (!alreadyOpen) {
			await touchElement(page, '#panel-button');
		}
		const opened = await page.waitForFunction(() => {
			const snapshot = window.__voidscapeCollectDiagnostics();
			return !!window.__voidscapePanelDrawerOpen
				&& document.body.classList.contains('panel-drawer-open')
				&& snapshot.controls
				&& snapshot.controls.panelDrawer
				&& snapshot.controls.panelDrawer.display === 'grid';
		}, null, { timeout: 1400 }).then(() => true).catch(() => false);
		if (opened) {
			return await diagnosticsSnapshot(page);
		}
		await page.waitForTimeout(180);
	}
	const snapshot = await diagnosticsSnapshot(page);
	throw new Error(`${label}: mobile panel drawer did not open: ${JSON.stringify({
		bodyClass: snapshot.bodyClass,
		controls: snapshot.controls,
		input: snapshot.input
	})}`);
}

async function openMobilePanelShortcut(page, shortcut, label) {
	await clearQueueRecorder(page);
	const drawerOpen = await openMobilePanelDrawer(page, label);
	assert(drawerOpen.controls.panelButton.ariaPressed === 'true',
		`${label}: mobile panel drawer button should report pressed state: ${JSON.stringify(drawerOpen.controls.panelButton)}`);
	assert(Array.isArray(drawerOpen.controls.panelButtons) && drawerOpen.controls.panelButtons.length >= 8,
		`${label}: mobile panel drawer should publish shortcut diagnostics: ${JSON.stringify(drawerOpen.controls.panelButtons)}`);
	await touchElement(page, `.mobile-panel-button[data-panel="${shortcut.panel}"]`);
	const opened = await waitForUiTab(page, shortcut.tabId, label);
	await page.waitForTimeout(180);
	const events = await recordedQueueEvents(page);
	assert(events.includes(`u,${shortcut.panel}`),
		`${label}: mobile panel drawer should enqueue ${shortcut.panel}: ${events.join(' ')}`);
	const closed = await diagnosticsSnapshot(page);
	assert(closed.controls.panelDrawer.display === 'none' && closed.input && !closed.input.panelDrawer,
		`${label}: mobile panel drawer should close after shortcut: controls=${JSON.stringify(closed.controls.panelDrawer)} input=${JSON.stringify(closed.input)}`);
	return { drawerOpen, opened, events, closed };
}

async function openMobileCanvasRailPanel(page, shortcut, label) {
	const canvas = await page.evaluate(() => {
		const element = document.querySelector('canvas');
		return {
			width: element.width,
			height: element.height
		};
	});
	const point = customHudTopTabFramePoint(canvas, shortcut.index);
	await clearQueueRecorder(page);
	await pointerTapFrame(page, point.x, point.y);
	const opened = await waitForUiTab(page, shortcut.tabId, label);
	await page.waitForTimeout(180);
	const events = await recordedQueueEvents(page);
	assert(events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
		`${label}: Android-parity canvas top tab should enqueue a normal canvas tap: ${events.join(' ')}`);
	return { canvas, point, opened, events };
}

async function proveScrollableTopPanelSwipe(page) {
  const before = await waitForUiState(page);
	assert(before.customUi && before.webBuild && before.androidProfile,
    `shared-panel scroll proof requires shared mobile Voidscape UI: ${JSON.stringify(before)}`);
  assert(before.mobilePanelShell === false && before.panelAccessMode === 'canvas'
      && before.canvasTopTabsVisible === true && before.canvasPanelDockVisible === false,
    `shared-panel scroll proof should use the Android-parity canvas tab path: ${JSON.stringify(before)}`);
  assert(!before.blockingDialog, `shared-panel scroll proof requires blocking dialogs closed: ${JSON.stringify(before)}`);
  const canvas = await page.evaluate(() => {
    const element = document.querySelector('canvas');
    return {
      width: element.width,
      height: element.height
    };
  });
  const magicPrayerTab = { index: 2, panel: 'magic', tabId: 4, label: 'MAGIC_PRAYER' };
  const opened = await openMobileCanvasRailPanel(page, magicPrayerTab, 'Android-parity canvas top tab Magic/Prayer panel for scroll');
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return snapshot && snapshot.input && snapshot.input.scrollableUi === true;
  }, null, { timeout: 5000 });

  const dragX = Math.min(canvas.width - 28, Math.max(28, Math.round(canvas.width * 0.78)));
  const startY = Math.max(150, Math.min(canvas.height - 260, Math.round(canvas.height * 0.26)));
  const endY = Math.max(96, startY - 110);
  await clearQueueRecorder(page);
  await clearScrollHistory(page);
  await pointerFrame(page, 'pointerdown', dragX, startY, { buttons: 1 });
  await pointerFrame(page, 'pointermove', dragX, Math.round((startY + endY) / 2), { buttons: 1 });
  await pointerFrame(page, 'pointermove', dragX, endY, { buttons: 1 });
  await page.waitForTimeout(180);
  await pointerFrame(page, 'pointerup', dragX, endY, { buttons: 0 });
  await page.waitForTimeout(250);
  const events = await recordedQueueEvents(page);
  const history = await readScrollHistory(page);
  assert(events.some((entry) => /^w,[1-9]/.test(entry)),
    `real shared-panel swipe should enqueue positive scroll ticks: events=${events.join(' ')} history=${JSON.stringify(history)}`);
  assert(!events.some((entry) => /^k,1,(37|38|39|40)$/.test(entry)),
    `real shared-panel swipe should not rotate/zoom camera: ${events.join(' ')}`);
  assert(history.some((entry) => entry && entry.scrollableUi === true && entry.showUiTab === magicPrayerTab.tabId && entry.amount > 0),
    `scrollHistory should record a scrollable Magic/Prayer panel gesture: ${JSON.stringify(history)}`);

  const closePoint = customHudSafeCanvasClosePoint(canvas);
  await tapFrame(page, closePoint.x, closePoint.y);
  const closed = await waitForUiTab(page, 0, 'custom HUD Magic/Prayer top panel close after scroll proof');
  return {
    before,
    canvas,
    target: magicPrayerTab,
    drag: { x: dragX, startY, endY },
    opened,
    events,
    history,
    closePoint,
    closed
	};
}

async function proveMobilePanelDrawerPanels(page) {
	const before = await waitForUiState(page);
	assert(before.customUi && before.webBuild && before.androidProfile,
		`mobile panel drawer requires shared mobile Voidscape UI: ${JSON.stringify(before)}`);
	assert(!before.blockingDialog, `mobile panel drawer requires blocking dialogs closed: ${JSON.stringify(before)}`);
	const shortcuts = [
		{ panel: 'inventory', tabId: 1 },
		{ panel: 'map', tabId: 2 },
		{ panel: 'magic', tabId: 4 },
		{ panel: 'prayer', tabId: 4 },
		{ panel: 'skills', tabId: 3 },
		{ panel: 'quests', tabId: 3 },
		{ panel: 'friends', tabId: 5 },
		{ panel: 'options', tabId: 6 }
	];
	const steps = [];
	for (const shortcut of shortcuts) {
		const opened = await openMobilePanelShortcut(page, shortcut, `mobile panel drawer ${shortcut.panel}`);
		steps.push({ shortcut, events: opened.events, opened: opened.opened });
	}
	return {
		before,
		steps,
		after: steps.length ? steps[steps.length - 1].opened : before
	};
}

async function waitForWorldMapVisible(page, visible, label) {
	await page.waitForFunction((visible) => {
		const snapshot = window.__voidscapeCollectDiagnostics();
    const worldMap = snapshot && snapshot.worldMap;
    return !!worldMap && worldMap.visible === visible;
  }, visible, { timeout: 7000 });
  if (visible) {
    await page.waitForFunction(() => window.__voidscapeWorldMapTouchActive === true, null, { timeout: 5000 });
  }
  const snapshot = await diagnosticsSnapshot(page);
  assert(snapshot.recentErrors.length === 0, `${label} diagnostics reported errors: ${snapshot.recentErrors.join('; ')}`);
  return snapshot.worldMap;
}

async function waitForWorldMapOpener(page) {
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const opener = snapshot && snapshot.worldMap && snapshot.worldMap.opener;
    return !!opener && opener.visible === true && opener.width > 0 && opener.height > 0
      && opener.center && opener.center.x > 0 && opener.center.y > 0;
  }, null, { timeout: 7000 });
  return (await diagnosticsSnapshot(page)).worldMap.opener;
}

async function clearWorldMapHistory(page) {
  await page.evaluate(() => {
    window.__voidscapeWorldMapHistory = [];
  });
}

async function readWorldMapHistory(page) {
  return await page.evaluate(() => (window.__voidscapeWorldMapHistory || []).slice());
}

async function dragWorldMapFrame(page, start, end) {
  await pointerFrame(page, 'pointerdown', start.x, start.y, { buttons: 1 });
  await page.waitForTimeout(120);
  await pointerFrame(page, 'pointermove', end.x, end.y, { buttons: 1 });
  await page.waitForTimeout(240);
  await pointerFrame(page, 'pointerup', end.x, end.y, { buttons: 0 });
  await page.waitForTimeout(240);
}

async function panWorldMapWithTouch(page, before) {
  const panStart = before.contentCenter;
  const candidates = [
    { dx: 54, dy: 34 },
    { dx: -54, dy: 34 },
    { dx: 54, dy: -34 },
    { dx: -54, dy: -34 }
  ];
  const attempts = [];
  for (const delta of candidates) {
    const startMap = (await diagnosticsSnapshot(page)).worldMap;
    const start = startMap.contentCenter;
    const end = {
      x: Math.max(startMap.window.x + 40, Math.min(startMap.window.x + startMap.window.width - 40, start.x + delta.dx)),
      y: Math.max(startMap.window.y + 70, Math.min(startMap.window.y + startMap.window.height - 40, start.y + delta.dy))
    };
    await clearQueueRecorder(page);
    await dragWorldMapFrame(page, start, end);
    const panned = await page.waitForFunction((startMap) => {
      const snapshot = window.__voidscapeCollectDiagnostics();
      const worldMap = snapshot && snapshot.worldMap;
      return !!worldMap && worldMap.visible
        && (worldMap.panX !== startMap.panX || worldMap.panY !== startMap.panY);
    }, startMap, { timeout: 2500 }).then(() => true).catch(() => false);
    const after = (await diagnosticsSnapshot(page)).worldMap;
    const events = await recordedQueueEvents(page);
    const attempt = { from: start, to: end, delta, after, events, panned };
    attempts.push(attempt);
    if (!panned) continue;
    assert(events.some((entry) => /^g,0,\d+,\d+$/.test(entry))
        && events.some((entry) => /^g,1,\d+,\d+$/.test(entry))
        && events.some((entry) => /^g,2,\d+,\d+$/.test(entry)),
      `world-map pan should use dedicated map touch events: ${events.join(' ')}`);
    assert(!events.some((entry) => /^p,/.test(entry)),
      `world-map pan should not leak generic pointer events: ${events.join(' ')}`);
    return {
      from: panStart,
      to: end,
      after,
      events,
      attempts
    };
  }
  throw new Error(`world-map touch pan did not change pan: ${JSON.stringify({ before, attempts })}`);
}

async function zoomWorldMapWithWheel(page, before) {
  const attempts = [];
  const point = await framePoint(page, before.contentCenter.x, before.contentCenter.y);
  await page.mouse.move(Math.round(point.x), Math.round(point.y));
  for (const dy of [-600, 600, -900, 900]) {
    await clearQueueRecorder(page);
    await clearScrollHistory(page);
    await page.mouse.wheel(0, dy);
    await page.waitForTimeout(450);
    const after = (await diagnosticsSnapshot(page)).worldMap;
    const events = await recordedQueueEvents(page);
    const scrollHistory = await readScrollHistory(page);
    attempts.push({ dy, beforeZoom: before.zoom, afterZoom: after && after.zoom, events, scrollHistory });
    if (after && after.zoom !== before.zoom) {
      assert(events.some((entry) => /^w,-?\d+$/.test(entry)),
        `world-map wheel zoom should enqueue scroll ticks: ${events.join(' ')}`);
      assert(scrollHistory.some((entry) => entry && entry.worldMap === true && entry.amount !== 0),
        `world-map wheel zoom should record worldMap scroll history: ${JSON.stringify(scrollHistory)}`);
      return { before, after, dy, events, scrollHistory, attempts };
    }
  }
  throw new Error(`world-map wheel zoom did not change zoom: ${JSON.stringify(attempts)}`);
}

async function pinchWorldMapFrame(page, worldMap, zoomOut) {
  const win = worldMap.window;
  const center = worldMap.contentCenter;
  const maxRightDistance = Math.max(34, (win.x + win.width - 34) - center.x);
  const maxLeftDistance = Math.max(34, center.x - (win.x + 34));
  const startDistance = Math.max(46, Math.min(72, maxRightDistance - 42));
  const endDistance = zoomOut
    ? Math.max(24, Math.min(startDistance - 30, maxRightDistance - 70))
    : Math.min(maxRightDistance, Math.max(startDistance + 44, Math.min(maxRightDistance, startDistance + 82)));
  const primary = {
    x: Math.max(win.x + 34, Math.min(win.x + win.width - 34, center.x - Math.min(24, maxLeftDistance - 24))),
    y: Math.max(win.y + 84, Math.min(win.y + win.height - 64, center.y))
  };
  const start = {
    x: Math.max(win.x + 34, Math.min(win.x + win.width - 34, primary.x + startDistance)),
    y: primary.y
  };
  const end = {
    x: Math.max(win.x + 34, Math.min(win.x + win.width - 34, primary.x + endDistance)),
    y: primary.y
  };
  const midpoint = {
    x: Math.round((start.x + end.x) / 2),
    y: primary.y
  };
  assert(Math.abs(end.x - start.x) >= 24,
    `world-map pinch points need enough separation delta: ${JSON.stringify({ worldMap, primary, start, end, zoomOut })}`);
  await pointerFrame(page, 'pointerdown', primary.x, primary.y, { pointerId: 81, isPrimary: true, buttons: 1 });
  await page.waitForTimeout(120);
  await pointerFrame(page, 'pointerdown', start.x, start.y, { pointerId: 82, isPrimary: false, buttons: 1 });
  await page.waitForTimeout(120);
  await pointerFrame(page, 'pointermove', midpoint.x, midpoint.y, { pointerId: 82, isPrimary: false, buttons: 1 });
  await page.waitForTimeout(180);
  await pointerFrame(page, 'pointermove', end.x, end.y, { pointerId: 82, isPrimary: false, buttons: 1 });
  await page.waitForTimeout(240);
  await pointerFrame(page, 'pointerup', end.x, end.y, { pointerId: 82, isPrimary: false, buttons: 0 });
  await pointerFrame(page, 'pointerup', primary.x, primary.y, { pointerId: 81, isPrimary: true, buttons: 0 });
  await page.waitForTimeout(420);
  return { primary, start, midpoint, end, zoomOut };
}

async function zoomWorldMapWithPinch(page, before) {
  const attempts = [];
  for (const zoomOut of [false, true, false]) {
    const startMap = (await diagnosticsSnapshot(page)).worldMap;
    await clearQueueRecorder(page);
    await clearScrollHistory(page);
    const gesture = await pinchWorldMapFrame(page, startMap, zoomOut);
    const after = (await diagnosticsSnapshot(page)).worldMap;
    const events = await recordedQueueEvents(page);
    const scrollHistory = await readScrollHistory(page);
    const changed = !!after && after.zoom !== startMap.zoom;
    attempts.push({ beforeZoom: startMap.zoom, afterZoom: after && after.zoom, gesture, events, scrollHistory, changed });
    if (!changed) continue;
    assert(events.some((entry) => /^g,0,\d+,\d+$/.test(entry)),
      `world-map pinch should begin as a dedicated map touch before second finger: ${events.join(' ')}`);
    assert(events.some((entry) => /^g,3,\d+,\d+$/.test(entry)),
      `world-map pinch should cancel active map touch when second finger starts: ${events.join(' ')}`);
    assert(events.some((entry) => /^w,-?\d+$/.test(entry)),
      `world-map pinch should enqueue world-map scroll ticks: ${events.join(' ')}`);
    assert(!events.some((entry) => /^p,/.test(entry)),
      `world-map pinch should not leak generic pointer events: ${events.join(' ')}`);
    assert(scrollHistory.some((entry) => entry && entry.worldMap === true && entry.amount !== 0),
      `world-map pinch should record worldMap scroll history: ${JSON.stringify(scrollHistory)}`);
    const beforeWalkRequestAt = startMap.walker && startMap.walker.lastRequest ? startMap.walker.lastRequest.at : 0;
    const afterWalkRequestAt = after.walker && after.walker.lastRequest ? after.walker.lastRequest.at : 0;
    assert(afterWalkRequestAt === beforeWalkRequestAt,
      `world-map pinch should not request world walker: ${JSON.stringify({ before: startMap.walker, after: after.walker, events })}`);
    return { before: startMap, after, gesture, events, scrollHistory, attempts };
  }
  throw new Error(`world-map pinch zoom did not change zoom: ${JSON.stringify(attempts)}`);
}

function worldMapScale(zoomLevel) {
  const scales = {
    '-1': 0.5,
    0: 1,
    1: 2,
    2: 4
  };
  return scales[zoomLevel] || 1;
}

function worldToMapFramePoint(worldMap, worldX, worldY) {
  const scale = worldMapScale(worldMap.zoom);
  const pngX = 2446 - worldX * 3;
  const pngY = (worldY % 944) * 3 - 1;
  return {
    x: Math.round(worldMap.panX + pngX * scale),
    y: Math.round(worldMap.panY + pngY * scale)
  };
}

function pointInsideWorldMapContent(worldMap, point) {
  const win = worldMap && worldMap.window;
  if (!win || !point) return false;
  const contentLeft = win.x + 2;
  const contentTop = win.y + 23;
  const contentRight = win.x + win.width - 2;
  const contentBottom = win.y + win.height - 2;
  return point.x >= contentLeft + 12
    && point.x <= contentRight - 12
    && point.y >= contentTop + 12
    && point.y <= contentBottom - 12;
}

async function waitForMovementFrom(page, before, timeout = 8000) {
  const moved = await page.waitForFunction((before) => {
    const after = window.__voidscapeClientState;
    return before
      && after
      && after.hasLocalPlayer
      && (after.worldX !== before.worldX
        || after.worldY !== before.worldY
        || Math.abs(after.currentX - before.currentX) >= 128
        || Math.abs(after.currentZ - before.currentZ) >= 128);
  }, before, { timeout }).then(() => true).catch(() => false);
  if (!moved) return null;
  return await waitForClientState(page);
}

async function waitForWorldMapRouteUpdate(page, beforeRequestAt, beforeRouteAt) {
  const updated = await page.waitForFunction(({ beforeRequestAt, beforeRouteAt }) => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const walker = snapshot && snapshot.worldMap && snapshot.worldMap.walker;
    const request = walker && walker.lastRequest;
    const route = walker && walker.lastRoute;
    return !!request && !!route
      && request.at > beforeRequestAt
      && route.at > beforeRouteAt
      && route.at >= request.at;
  }, { beforeRequestAt, beforeRouteAt }, { timeout: 12000 }).then(() => true).catch(() => false);
  const worldMap = (await diagnosticsSnapshot(page)).worldMap;
  return { updated, worldMap };
}

async function resetWorldMapToPlayer(page, before) {
  await clearQueueRecorder(page);
  await pointerTapFrame(page, before.resetCenter.x, before.resetCenter.y);
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const worldMap = snapshot && snapshot.worldMap;
    return !!worldMap && worldMap.visible && worldMap.zoom === 0;
  }, null, { timeout: 5000 });
  await page.waitForTimeout(450);
  return (await diagnosticsSnapshot(page)).worldMap;
}

async function proveWorldMapWalkerSuccess(page, beforeResetMap) {
  const resetMap = await resetWorldMapToPlayer(page, beforeResetMap);
  const initialClient = await waitForClientState(page);
  assert(initialClient.hasLocalPlayer, `world-map walker proof needs local player state: ${JSON.stringify(initialClient)}`);
  const playerFloor = Math.floor(initialClient.worldY / 944);
  assert(playerFloor === resetMap.floor,
    `world-map walker proof requires player and map on same floor: ${JSON.stringify({ player: initialClient, worldMap: resetMap })}`);

  const offsets = [
    { dx: 8, dy: 0 },
    { dx: -8, dy: 0 },
    { dx: 0, dy: 8 },
    { dx: 0, dy: -8 },
    { dx: 6, dy: 6 },
    { dx: -6, dy: 6 },
    { dx: 6, dy: -6 },
    { dx: -6, dy: -6 },
    { dx: 12, dy: 0 },
    { dx: 0, dy: 12 }
  ];
  const attempts = [];
  for (const offset of offsets) {
    const beforeClient = await waitForClientState(page);
    const currentMap = (await diagnosticsSnapshot(page)).worldMap;
    const beforeWalker = currentMap && currentMap.walker ? currentMap.walker : {};
    const beforeRequestAt = beforeWalker.lastRequest ? beforeWalker.lastRequest.at : 0;
    const beforeRouteAt = beforeWalker.lastRoute ? beforeWalker.lastRoute.at : 0;
    const target = {
      worldX: beforeClient.worldX + offset.dx,
      worldY: beforeClient.worldY + offset.dy
    };
    if (Math.floor(target.worldY / 944) !== currentMap.floor) {
      attempts.push({ offset, target, skipped: 'floor-mismatch', currentMap });
      continue;
    }
    const point = worldToMapFramePoint(currentMap, target.worldX, target.worldY);
    if (!pointInsideWorldMapContent(currentMap, point)) {
      attempts.push({ offset, target, point, skipped: 'outside-content', currentMap });
      continue;
    }

    await clearQueueRecorder(page);
    await pointerTapFrame(page, point.x, point.y);
    const { updated, worldMap: routeMap } = await waitForWorldMapRouteUpdate(page, beforeRequestAt, beforeRouteAt);
    const events = await recordedQueueEvents(page);
    const request = routeMap && routeMap.walker ? routeMap.walker.lastRequest : null;
    const route = routeMap && routeMap.walker ? routeMap.walker.lastRoute : null;
    const movementAfterRoute = route && route.ok ? await waitForMovementFrom(page, beforeClient, 12000) : null;
    const afterClient = movementAfterRoute || await waitForClientState(page);
    const attempt = {
      offset,
      target,
      point,
      updated,
      request,
      route,
      moved: !!movementAfterRoute,
      beforeClient,
      afterClient,
      events
    };
    attempts.push(attempt);

    assert(events.some((entry) => /^g,0,\d+,\d+$/.test(entry))
        && events.some((entry) => /^g,2,\d+,\d+$/.test(entry)),
      `world-map walker tap should use dedicated map touch events: ${events.join(' ')}`);
    assert(!events.some((entry) => /^p,/.test(entry)),
      `world-map walker tap should not leak generic pointer events: ${events.join(' ')}`);

    if (updated && route && route.ok && route.count > 0 && movementAfterRoute) {
      return {
        initialClient,
        resetMap,
        success: attempt,
        attempts
      };
    }
  }

  throw new Error(`world-map walker did not produce a successful route and movement: ${JSON.stringify({
    initialClient,
    attempts
  })}`);
}

async function proveWorldMapMobileSearch(page) {
  const before = await waitForUiState(page);
  assert(before.customUi && before.webBuild && before.androidProfile,
    `world-map mobile proof requires shared mobile Voidscape UI: ${JSON.stringify(before)}`);
  assert(!before.blockingDialog, `world-map mobile proof requires blocking dialogs closed: ${JSON.stringify(before)}`);
  const canvas = await page.evaluate(() => {
    const element = document.querySelector('canvas');
    return { width: element.width, height: element.height };
  });

  const mapPanel = await openMobileCanvasRailPanel(page, { index: 4, panel: 'map', tabId: 2 }, 'world-map Android-parity canvas top tab map panel');

  const opener = await waitForWorldMapOpener(page);
  await clearQueueRecorder(page);
  await clearWorldMapHistory(page);
  await pointerTapFrame(page, opener.center.x, opener.center.y);
  const opened = await waitForWorldMapVisible(page, true, 'world-map open');
  const screenshots = {
    open: await captureVisualArtifact(page, 'iphone-world-map-open.png', { hideDiagnostics: true })
  };
  const openEvents = await recordedQueueEvents(page);
  assert(openEvents.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
    `world-map opener should use a primary tap before map touch routing starts: ${openEvents.join(' ')}`);

  const pan = await panWorldMapWithTouch(page, opened);
  const panned = pan.after;
  screenshots.panned = await captureVisualArtifact(page, 'iphone-world-map-panned.png', { hideDiagnostics: true });

  const pinchZoom = await zoomWorldMapWithPinch(page, panned);
  screenshots.pinched = await captureVisualArtifact(page, 'iphone-world-map-pinched.png', { hideDiagnostics: true });

  const zoom = await zoomWorldMapWithWheel(page, pinchZoom.after);
  screenshots.zoomed = await captureVisualArtifact(page, 'iphone-world-map-zoomed.png', { hideDiagnostics: true });

  const escapeSearchText = 'edge';
  await clearQueueRecorder(page);
  await pointerTapFrame(page, zoom.after.searchCenter.x, zoom.after.searchCenter.y);
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return !!(snapshot && snapshot.worldMap && snapshot.worldMap.searchFocused);
  }, null, { timeout: 5000 });
  await page.keyboard.type(escapeSearchText, { delay: 15 });
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const worldMap = snapshot && snapshot.worldMap;
    return !!worldMap && worldMap.searchQuery === '' && worldMap.searchFocused === false;
  }, null, { timeout: 7000 });
  const escapeSearchEvents = await recordedQueueEvents(page);
  assert(escapeSearchEvents.some((entry) => entry === `t,${escapeSearchText.charCodeAt(0)}`),
    `world-map Escape search should enqueue text events before Escape: ${escapeSearchEvents.join(' ')}`);
  assert(escapeSearchEvents.includes('k,1,27') && escapeSearchEvents.includes('k,0,27'),
    `world-map focused Escape should route raw Escape to map search: ${escapeSearchEvents.join(' ')}`);
  assert(!escapeSearchEvents.includes('b,1'),
    `world-map focused Escape should not become mobile Back: ${escapeSearchEvents.join(' ')}`);

  const searchText = 'varrock';
  await clearQueueRecorder(page);
  await pointerTapFrame(page, zoom.after.searchCenter.x, zoom.after.searchCenter.y);
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return !!(snapshot && snapshot.worldMap && snapshot.worldMap.searchFocused);
  }, null, { timeout: 5000 });
  await page.keyboard.type(searchText, { delay: 15 });
  await page.waitForFunction((searchText) => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const worldMap = snapshot && snapshot.worldMap;
    return !!worldMap && worldMap.searchQuery === searchText && worldMap.searchFocused === true;
  }, searchText, { timeout: 7000 });
  screenshots.searchFocused = await captureVisualArtifact(page, 'iphone-world-map-search-focused.png', { hideDiagnostics: true });
  await clearQueueRecorder(page);
  await page.evaluate(() => history.back());
  await waitForMobileKeyboard(page, false, 'world-map search browser Back close');
  const searchBackEvents = await recordedQueueEvents(page);
  assert(searchBackEvents.includes('k,1,27') && searchBackEvents.includes('k,0,27'),
    `world-map search browser Back should route Escape to map search first: ${searchBackEvents.join(' ')}`);
  assert(!searchBackEvents.includes('b,1'),
    `world-map search browser Back should not close map before keyboard: ${searchBackEvents.join(' ')}`);
  const afterSearchBack = (await diagnosticsSnapshot(page)).worldMap;
  assert(afterSearchBack.visible === true,
    `world-map search browser Back should leave map open: ${JSON.stringify(afterSearchBack)}`);
  assert(afterSearchBack.searchFocused === false && afterSearchBack.searchQuery === '',
    `world-map search browser Back should close map search before shared Back: ${JSON.stringify(afterSearchBack)}`);
  await pointerTapFrame(page, afterSearchBack.searchCenter.x, afterSearchBack.searchCenter.y);
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return !!(snapshot && snapshot.worldMap && snapshot.worldMap.searchFocused);
  }, null, { timeout: 5000 });
  await page.keyboard.type(searchText, { delay: 15 });
  await page.waitForFunction((searchText) => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const worldMap = snapshot && snapshot.worldMap;
    return !!worldMap && worldMap.searchQuery === searchText && worldMap.searchFocused === true;
  }, searchText, { timeout: 7000 });
  await page.keyboard.press('Enter');
  await page.waitForFunction((searchText) => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    const worldMap = snapshot && snapshot.worldMap;
    return !!worldMap && worldMap.searchQuery === searchText && worldMap.searchFocused === false;
  }, searchText, { timeout: 7000 });
  const searched = (await diagnosticsSnapshot(page)).worldMap;
  const searchEvents = await recordedQueueEvents(page);
  assert(searchEvents.some((entry) => entry === `t,${searchText.charCodeAt(0)}`),
    `world-map search should enqueue text events: ${searchEvents.join(' ')}`);
  assert(searchEvents.includes('k,1,10'),
    `world-map search Enter should enqueue shared Enter key: ${searchEvents.join(' ')}`);

  const walker = await proveWorldMapWalkerSuccess(page, searched);
  const walked = (await diagnosticsSnapshot(page)).worldMap;
  screenshots.walkerRoute = await captureVisualArtifact(page, 'iphone-world-map-walker-route.png', { hideDiagnostics: true });

  await clearQueueRecorder(page);
  await pointerTapFrame(page, walked.closeCenter.x, walked.closeCenter.y);
  const closed = await waitForWorldMapVisible(page, false, 'world-map close');
  const closeEvents = await recordedQueueEvents(page);
  const history = await readWorldMapHistory(page);
  assert(closeEvents.some((entry) => /^g,0,\d+,\d+$/.test(entry))
      && closeEvents.some((entry) => /^g,2,\d+,\d+$/.test(entry)),
    `world-map close should use dedicated map touch events: ${closeEvents.join(' ')}`);
  assert(history.some((entry) => entry && entry.kind === 'visible' && entry.detail && entry.detail.visible === true),
    `world-map history should record open: ${JSON.stringify(history)}`);
  assert(history.some((entry) => entry && entry.kind === 'visible' && entry.detail && entry.detail.visible === false),
    `world-map history should record close: ${JSON.stringify(history)}`);
  assert(history.some((entry) => entry && entry.kind === 'walkRequest'),
    `world-map history should record walker request: ${JSON.stringify(history)}`);
  assert(history.some((entry) => entry && entry.kind === 'walkRoute'
      && entry.detail && entry.detail.ok === true && entry.detail.count > 0),
    `world-map history should record successful walker route: ${JSON.stringify(history)}`);

  return {
    before,
    canvas,
    mapPanel,
    opener,
    opened,
	    openEvents,
	    pan,
	    pinchZoom,
	    zoom,
	    escapeSearch: { text: escapeSearchText, events: escapeSearchEvents },
	    search: { text: searchText, after: searched, events: searchEvents },
	    walker,
	    screenshots,
    close: { after: closed, events: closeEvents },
    history
  };
}

async function waitForViewportShape(page, label, expected) {
	await page.waitForFunction(({ expected }) => {
		const snapshot = window.__voidscapeCollectDiagnostics();
    if (!snapshot || !snapshot.inGame) return false;
    if (snapshot.viewport.scrollX !== 0 || snapshot.viewport.scrollY !== 0) return false;
    if (snapshot.canvas.cssWidth !== expected.cssWidth || snapshot.canvas.cssHeight !== expected.cssHeight) return false;
    if (expected.frameWidth && snapshot.canvas.width !== expected.frameWidth) return false;
    if (expected.frameHeight && snapshot.canvas.height !== expected.frameHeight) return false;
    if (expected.minFrameWidth && snapshot.canvas.width < expected.minFrameWidth) return false;
    if (expected.maxFrameWidth && snapshot.canvas.width > expected.maxFrameWidth) return false;
    if (expected.minFrameHeight && snapshot.canvas.height < expected.minFrameHeight) return false;
    if (expected.maxFrameHeight && snapshot.canvas.height > expected.maxFrameHeight) return false;
    return true;
  }, { expected }, { timeout: 8000 });
  const snapshot = await diagnosticsSnapshot(page);
  assert(snapshot.recentErrors.length === 0, `${label} diagnostics reported errors: ${snapshot.recentErrors.join('; ')}`);
  return snapshot;
}

async function waitForCameraStateChange(page, before, kind, label) {
  await page.waitForFunction(({ before, kind }) => {
    const after = window.__voidscapeClientState;
    if (!before || !after || !after.hasLocalPlayer) return false;
    if (kind === 'rotation') return after.cameraRotation !== before.cameraRotation;
    return after.lastZoom !== before.lastZoom || after.cameraZoom !== before.cameraZoom;
  }, { before, kind }, { timeout: 6000 });
  const after = await waitForClientState(page);
  return after;
}

async function proveCameraButtonRotation(page) {
  const before = await waitForClientState(page);
  await clearQueueRecorder(page);
  await page.click('.camera-control-button[data-key="37"]');
  const after = await waitForCameraStateChange(page, before, 'rotation', 'camera button rotation');
  await page.waitForTimeout(260);
  const events = await recordedQueueEvents(page);
  assert(events.includes('k,1,37'), `camera left button should enqueue ArrowLeft down: ${events.join(' ')}`);
  assert(events.includes('k,0,37'), `camera left button should enqueue ArrowLeft up: ${events.join(' ')}`);
  return { before, after, events };
}

async function proveTouchDragRotation(page) {
  const before = await waitForClientState(page);
  const points = safeCameraGesturePoints(await currentFrameSize(page));
  await clearQueueRecorder(page);
  await touchDragFrame(page, points.rotationStart, points.rotationEnd);
  const after = await waitForCameraStateChange(page, before, 'rotation', 'touch-drag rotation');
  await page.waitForTimeout(260);
  const events = await recordedQueueEvents(page);
  assert(events.some((entry) => /^k,1,(37|39)$/.test(entry)),
    `touch drag should enqueue a horizontal camera key: ${events.join(' ')}`);
  assert(events.some((entry) => /^k,0,(37|39)$/.test(entry)),
    `touch drag should release a horizontal camera key: ${events.join(' ')}`);
  assert(!events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
    `touch drag rotation should not leak a primary tap: ${events.join(' ')}`);
  return { before, after, points, events };
}

async function proveTouchDragZoom(page) {
  const before = await waitForClientState(page);
  const zoomOut = before.lastZoom <= 8;
  const key = zoomOut ? 40 : 38;
  const points = safeCameraGesturePoints(await currentFrameSize(page));
  await clearQueueRecorder(page);
  await touchDragFrame(page, zoomOut ? points.zoomUp : points.zoomDown, zoomOut ? points.zoomDown : points.zoomUp);
  const after = await waitForCameraStateChange(page, before, 'zoom', 'touch-drag zoom');
  await page.waitForTimeout(260);
  const events = await recordedQueueEvents(page);
  assert(events.includes(`k,1,${key}`), `touch drag zoom should enqueue key ${key}: ${events.join(' ')}`);
  assert(events.includes(`k,0,${key}`), `touch drag zoom should release key ${key}: ${events.join(' ')}`);
  assert(!events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
    `touch drag zoom should not leak a primary tap: ${events.join(' ')}`);
  return { before, after, key, points, events };
}

async function provePinchZoom(page) {
  const initial = await waitForClientState(page);
  const preferredZoomOut = initial.lastZoom <= 8;
  const attempts = [preferredZoomOut, !preferredZoomOut];
  const failures = [];
  for (const zoomOut of attempts) {
    const before = await waitForClientState(page);
    const key = zoomOut ? 40 : 38;
    await clearQueueRecorder(page);
    await pinchFrame(page, zoomOut);
    await page.waitForTimeout(260);
    const events = await recordedQueueEvents(page);
    assert(events.includes(`k,1,${key}`), `pinch zoom should enqueue key ${key}: ${events.join(' ')}`);
    assert(events.includes(`k,0,${key}`), `pinch zoom should release key ${key}: ${events.join(' ')}`);
    assert(!events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
      `pinch zoom should not leak a primary tap: ${events.join(' ')}`);
    try {
      const after = await waitForCameraStateChange(page, before, 'zoom', 'pinch zoom');
      return { before, after, key, zoomOut, events, retried: zoomOut !== preferredZoomOut };
    } catch (error) {
      const current = await waitForClientState(page).catch(() => null);
      failures.push({ before, current, key, zoomOut, events, error: error.message });
    }
  }
  throw new Error(`pinch zoom should change shared camera zoom after retry: ${JSON.stringify(failures)}`);
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

async function proveTerrainTapMovement(page) {
  await normalizeCameraZoomForTerrain(page);
  const before = await waitForClientState(page);
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
    await tapFrame(page, candidate.x, candidate.y);
    const movedAfter = await waitForMovementFrom(page, before, 8000);
    const moved = !!movedAfter;
    const after = movedAfter || await page.evaluate(() => ({ ...window.__voidscapeClientState }));
    const events = await recordedQueueEvents(page);
    attempts.push({ candidate, moved, events, after });
    if (movedFrom(before, after)) {
      return { before, after, candidate, events, attempts };
    }
  }
  throw new Error(`terrain tap did not move local player: ${JSON.stringify({ before, attempts })}`);
}

async function proveLongPressContextMenu(page) {
  await normalizeCameraZoomForTerrain(page);
  const frame = await currentFrameSize(page);
  const width = Math.max(160, frame.width || 512);
  const height = Math.max(160, frame.height || 346);
  const xAt = (ratio) => clampFramePoint(width * ratio, 32, width - 32);
  const yAt = (ratio) => clampFramePoint(height * ratio, 54, height - 84);
  const candidates = [
    { x: xAt(0.50), y: yAt(0.54) },
    { x: xAt(0.42), y: yAt(0.58) },
    { x: xAt(0.58), y: yAt(0.58) },
    { x: xAt(0.34), y: yAt(0.48) },
    { x: xAt(0.66), y: yAt(0.48) },
    { x: xAt(0.50), y: yAt(0.42) }
  ];
  const attempts = [];
  for (const candidate of candidates) {
    await clearQueueRecorder(page);
    await pointerFrame(page, 'pointerdown', candidate.x, candidate.y, { buttons: 1 });
    const opened = await page.waitForFunction(() => {
      const snapshot = window.__voidscapeCollectDiagnostics();
      return !!(snapshot && snapshot.input && snapshot.input.topMenu);
    }, null, { timeout: 2500 }).then(() => true).catch(() => false);
    const during = await diagnosticsSnapshot(page);
    await pointerFrame(page, 'pointerup', candidate.x, candidate.y, { buttons: 0 });
    await page.waitForTimeout(420);
    const afterRelease = await diagnosticsSnapshot(page);
    const events = await recordedQueueEvents(page);
    const menu = during && during.ui ? {
      x: during.ui.menuX | 0,
      y: during.ui.menuY | 0,
      width: during.ui.menuWidth | 0,
      height: during.ui.menuHeight | 0
    } : null;
    const anchoredNearPress = !!(menu && menu.width > 0 && menu.height > 0
      && candidate.x >= menu.x - 18
      && candidate.x <= menu.x + menu.width + 18
      && candidate.y >= menu.y - 18
      && candidate.y <= menu.y + menu.height + 18);
    const attempt = {
      candidate,
      opened,
      duringTopMenu: !!(during.input && during.input.topMenu),
      afterReleaseTopMenu: !!(afterRelease.input && afterRelease.input.topMenu),
      menu,
      anchoredNearPress,
      events
    };
    attempts.push(attempt);

    if (opened && attempt.afterReleaseTopMenu && anchoredNearPress) {
      const lineHeight = Math.max(18, Math.min(34, Math.round(menu.height / Math.max(2, Math.round(menu.height / 28)))));
      const optionPoint = {
        x: Math.round(menu.x + Math.max(8, Math.min(menu.width - 8, menu.width / 2))),
        y: Math.round(menu.y + lineHeight + Math.max(6, Math.min(lineHeight - 4, lineHeight / 2)))
      };
      await clearQueueRecorder(page);
      await pointerTapFrame(page, optionPoint.x, optionPoint.y);
      await page.waitForFunction(() => {
        const snapshot = window.__voidscapeCollectDiagnostics();
        return !(snapshot && snapshot.input && snapshot.input.topMenu);
      }, null, { timeout: 5000 });
      const selectEvents = await recordedQueueEvents(page);
      assert(selectEvents.some((entry) => /^m,\d+,\d+$/.test(entry)),
        `context-menu option tap should queue a menu touch release: ${selectEvents.join(' ')}`);
      return {
        candidate,
        opened: true,
        stableAfterRelease: true,
        anchoredNearPress: true,
        menu,
        optionPoint,
        events,
        selectEvents,
        attempts
      };
    }

    if (attempt.afterReleaseTopMenu) {
      await clearQueueRecorder(page);
      await page.evaluate(() => history.back());
      await page.waitForTimeout(350);
      await recordedQueueEvents(page);
    }
  }

  throw new Error(`mobile long-press context menu did not stay open after release: ${JSON.stringify({ frame, attempts })}`);
}

async function normalizeCameraZoomForTerrain(page) {
  await page.evaluate(() => {
    if (!Array.isArray(window.__voidscapeInputQueue)) return;
    for (let i = 0; i < 8; i++) {
      window.__voidscapeInputQueue.push('k,1,40');
      window.__voidscapeInputQueue.push('k,0,40');
    }
  });
  await page.waitForFunction(() => {
    const state = window.__voidscapeClientState;
    return state && state.hasLocalPlayer && state.lastZoom >= 35;
  }, null, { timeout: 5000 }).catch(() => {});
}

function typedTextFromEvents(events) {
  return events
    .filter((entry) => /^t,\d+$/.test(entry))
    .map((entry) => String.fromCharCode(Number(entry.slice(2))))
    .join('');
}

function assertTextEventsContain(events, expected, label) {
  const text = typedTextFromEvents(events);
  assert(text.includes(expected),
    `${label} should enqueue text ${JSON.stringify(expected)}, got ${JSON.stringify(text)} events=${events.join(' ')}`);
  return text;
}

async function waitForMobileKeyboard(page, open, label) {
  const matched = await page.waitForFunction((open) => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return snapshot
      && snapshot.keyboard
      && snapshot.keyboard.open === open
      && document.body.classList.contains('keyboard-open') === open;
  }, open, { timeout: 5000 }).then(() => true).catch(() => false);
  const state = await page.evaluate(() => ({
    activeElementId: document.activeElement ? document.activeElement.id : '',
    inputInstalled: !!window.__voidscapeInputInstalled,
    hasFocusKeyboard: typeof window.__voidscapeFocusKeyboard === 'function',
    keyboardButton: (() => {
      const element = document.getElementById('keyboard-button');
      const style = element ? getComputedStyle(element) : null;
      const rect = element ? element.getBoundingClientRect() : null;
      return {
        display: style ? style.display : '',
        pointerEvents: style ? style.pointerEvents : '',
        rect: rect ? {
          left: Math.round(rect.left),
          top: Math.round(rect.top),
          width: Math.round(rect.width),
          height: Math.round(rect.height)
        } : null
      };
    })(),
    snapshot: window.__voidscapeCollectDiagnostics()
  }));
  assert(matched, `${label} keyboard did not ${open ? 'open' : 'close'}: ${JSON.stringify(state)}`);
  assert(state.snapshot.keyboard.open === open, `${label} keyboard state mismatch: ${JSON.stringify(state)}`);
  if (open) {
    assert(state.activeElementId === 'keyboard-capture',
      `${label} should focus keyboard capture, got ${state.activeElementId || '<none>'}`);
  }
  return state;
}

async function openMobileKeyboard(page, label) {
  await page.waitForSelector('#keyboard-button', { state: 'visible', timeout: 5000 });
  const alreadyOpen = await page.evaluate(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return !!(snapshot && snapshot.keyboard && snapshot.keyboard.open);
  });
  if (alreadyOpen) {
    return await waitForMobileKeyboard(page, true, label);
  }
  await touchElement(page, '#keyboard-button');
  const opened = await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return !!(snapshot && snapshot.keyboard && snapshot.keyboard.open);
  }, null, { timeout: 1400 }).then(() => true).catch(() => false);
  if (!opened) {
    await page.waitForTimeout(320);
    await touchElement(page, '#keyboard-button');
  }
  return await waitForMobileKeyboard(page, true, label);
}

async function closeMobileKeyboardWithButton(page, label) {
  await touchElement(page, '#keyboard-button');
  const state = await waitForMobileKeyboard(page, false, label);
  const events = await recordedQueueEvents(page);
  assert(events.includes('s,0'), `${label} should enqueue keyboard close state: ${events.join(' ')}`);
  await page.waitForTimeout(320);
  return { state, events };
}

async function assertKeyboardViewportFrozen(page, label) {
  const before = await page.evaluate(() => ({
    viewportHeight: getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim(),
    scrollX: window.scrollX,
    scrollY: window.scrollY,
    snapshot: window.__voidscapeCollectDiagnostics()
  }));
  assert(before.snapshot && before.snapshot.keyboard && before.snapshot.keyboard.open,
    `${label} requires an open keyboard: ${JSON.stringify(before)}`);
  await page.setViewportSize({ width: 390, height: 420 });
  await page.waitForTimeout(300);
  const during = await page.evaluate(() => ({
    viewportHeight: getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim(),
    scrollX: window.scrollX,
    scrollY: window.scrollY,
    snapshot: window.__voidscapeCollectDiagnostics()
  }));
  assert(during.viewportHeight === before.viewportHeight,
    `${label} should freeze CSS viewport height while keyboard is open: before=${before.viewportHeight} during=${during.viewportHeight}`);
  assert(during.scrollX === 0 && during.scrollY === 0,
    `${label} should not scroll page while keyboard viewport changes: ${JSON.stringify(during)}`);
  await page.setViewportSize({ width: 390, height: 664 });
  await page.waitForTimeout(300);
  return { before, during };
}

async function dispatchKeyboardBeforeInput(page, inputType, data) {
  await page.evaluate(({ inputType, data }) => {
    const capture = document.getElementById('keyboard-capture');
    if (!capture) throw new Error('missing keyboard capture');
    capture.dispatchEvent(new InputEvent('beforeinput', {
      bubbles: true,
      cancelable: true,
      inputType,
      data
    }));
  }, { inputType, data });
}

async function dispatchKeyboardPaste(page, text) {
  await page.evaluate((text) => {
    const capture = document.getElementById('keyboard-capture');
    if (!capture) throw new Error('missing keyboard capture');
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: { getData: () => text }
    });
    capture.dispatchEvent(event);
  }, text);
}

async function dispatchKeyboardComposition(page, text) {
  await page.evaluate((text) => {
    const capture = document.getElementById('keyboard-capture');
    if (!capture) throw new Error('missing keyboard capture');
    const makeCompositionEvent = (type, data) => {
      if (typeof CompositionEvent === 'function') {
        return new CompositionEvent(type, { bubbles: true, cancelable: true, data });
      }
      const event = new Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(event, 'data', { value: data });
      return event;
    };
    capture.dispatchEvent(makeCompositionEvent('compositionstart', ''));
    capture.dispatchEvent(makeCompositionEvent('compositionend', text));
  }, text);
  await page.waitForTimeout(120);
}

async function installQueueRecorder(page) {
  await page.evaluate(() => {
    const queue = window.__voidscapeInputQueue;
    if (!queue || queue.__voidscapeSmokeWrapped) {
      window.__voidscapeSmokeQueuedEvents = window.__voidscapeSmokeQueuedEvents || [];
      return;
    }
    const originalPush = queue.push.bind(queue);
    window.__voidscapeSmokeQueuedEvents = [];
    queue.push = function(...items) {
      window.__voidscapeSmokeQueuedEvents.push(...items);
      return originalPush(...items);
    };
    queue.__voidscapeSmokeWrapped = true;
  });
}

async function clearQueueRecorder(page) {
  await page.evaluate(() => {
    window.__voidscapeSmokeQueuedEvents = [];
  });
}

async function recordedQueueEvents(page) {
  return await page.evaluate(() => (window.__voidscapeSmokeQueuedEvents || []).slice());
}

async function clearScrollHistory(page) {
  await page.evaluate(() => {
    window.__voidscapeScrollHistory = [];
  });
}

async function readScrollHistory(page) {
  return await page.evaluate(() => (window.__voidscapeScrollHistory || []).slice());
}

async function readMobileShell(page) {
	return await page.evaluate(() => {
		const read = (selector) => {
			const element = document.querySelector(selector);
			const style = element ? getComputedStyle(element) : null;
			const rect = element ? element.getBoundingClientRect() : null;
			return {
				display: style ? style.display : '',
				visibility: style ? style.visibility : '',
				ariaPressed: element ? element.getAttribute('aria-pressed') || '' : '',
				active: !!(element && element.matches(':active')),
				rect: rect ? {
					left: Math.round(rect.left),
					top: Math.round(rect.top),
					right: Math.round(rect.right),
					bottom: Math.round(rect.bottom),
					width: Math.round(rect.width),
					height: Math.round(rect.height)
				} : null
			};
		};
		return {
			bodyClass: document.body.className,
			htmlClass: document.documentElement.className,
			viewport: {
				innerWidth: window.innerWidth,
				innerHeight: window.innerHeight
			},
			keyboardButton: read('#keyboard-button'),
			inventoryButton: read('#inventory-button'),
			magicButton: read('#magic-button'),
			prayerButton: read('#prayer-button'),
				panelButton: read('#panel-button'),
				panelDrawer: read('#mobile-panel-drawer'),
				chatButton: read('#chat-button'),
				chatTray: read('#mobile-chat-tray'),
				cameraControls: read('#camera-controls'),
			cameraLeft: read('.camera-control-button[data-key="37"]'),
			diagnosticsButton: read('#diagnostics-button'),
			diagnosticsCopyButton: read('#diagnostics-copy-button')
    };
  });
}

function assertControlInsideViewport(control, viewport, label) {
  assert(control && control.rect, `${label}: missing rect`);
  assert(control.rect.left >= 0 && control.rect.top >= 0
    && control.rect.right <= viewport.innerWidth && control.rect.bottom <= viewport.innerHeight,
    `${label} should fit inside viewport ${JSON.stringify(viewport)}, got ${JSON.stringify(control.rect)}`);
}

function assertControlAboveBottomHud(control, viewport, label) {
  const reserve = viewport.innerWidth > viewport.innerHeight ? 72 : 84;
  assert(control && control.rect && control.rect.bottom < viewport.innerHeight - reserve,
    `${label} should stay above bottom HUD reserve ${reserve}px in ${JSON.stringify(viewport)}, got ${JSON.stringify(control && control.rect)}`);
}

function assertDialogSafeShell(shell, uiState, label) {
  assert(uiState && uiState.blockingDialog, `${label}: expected blocking dialog state: ${JSON.stringify(uiState)}`);
	assert(shell.bodyClass.includes('dialog-open'), `${label}: body should include dialog-open: ${shell.bodyClass}`);
	assert(shell.cameraControls.display === 'none', `${label}: camera controls should hide over blocking dialogs: ${JSON.stringify(shell.cameraControls)}`);
	assert(shell.panelButton.display === 'none', `${label}: panel button should hide over blocking dialogs: ${JSON.stringify(shell.panelButton)}`);
	assert(shell.panelDrawer.display === 'none', `${label}: panel drawer should hide over blocking dialogs: ${JSON.stringify(shell.panelDrawer)}`);
	assert(shell.chatButton.display === 'none', `${label}: chat button should hide over blocking dialogs: ${JSON.stringify(shell.chatButton)}`);
	assert(shell.chatTray.display === 'none', `${label}: chat tray should hide over blocking dialogs: ${JSON.stringify(shell.chatTray)}`);
	assert(shell.inventoryButton.display === 'none', `${label}: inventory shortcut should hide over blocking dialogs: ${JSON.stringify(shell.inventoryButton)}`);
	assert(shell.magicButton.display === 'none', `${label}: magic shortcut should hide over blocking dialogs: ${JSON.stringify(shell.magicButton)}`);
	assert(shell.prayerButton.display === 'none', `${label}: prayer shortcut should hide over blocking dialogs: ${JSON.stringify(shell.prayerButton)}`);
	for (const [key, control] of Object.entries({
		keyboardButton: shell.keyboardButton,
    diagnosticsButton: shell.diagnosticsButton,
    diagnosticsCopyButton: shell.diagnosticsCopyButton
  })) {
    assert(control.display === 'flex', `${label}: ${key} should remain reachable: ${JSON.stringify(control)}`);
    assertControlInsideViewport(control, shell.viewport, `${label}: ${key}`);
    assertControlAboveBottomHud(control, shell.viewport, `${label}: ${key}`);
  }
  const dialogRect = shell.viewport.innerWidth > shell.viewport.innerHeight
    ? { rect: { left: 222, top: 118, right: 622, bottom: 286 } }
    : { rect: { left: 70, top: 250, right: 320, bottom: 480 } };
  assertNoOverlap(shell.keyboardButton, dialogRect, `${label}: keyboard/dialog`);
  assertNoOverlap(shell.diagnosticsButton, dialogRect, `${label}: diagnostics/dialog`);
  assertNoOverlap(shell.diagnosticsCopyButton, dialogRect, `${label}: diagnostics copy/dialog`);
  assertNoOverlap(shell.diagnosticsButton, shell.keyboardButton, `${label}: diagnostics/keyboard controls`);
  assertNoOverlap(shell.diagnosticsCopyButton, shell.diagnosticsButton, `${label}: diagnostics controls`);
}

async function waitForChatEcho(consoleMessages, chatMessage, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  const chatLinePattern = new RegExp(`\\[CHAT\\].*${escapeRegExp(chatMessage)}`, 'i');
  const smokeLinePattern = new RegExp(`ANDROID_SMOKE_CHAT_SEND.*message=${escapeRegExp(chatMessage)}`, 'i');
  while (Date.now() < deadline) {
    if (consoleMessages.some((entry) => chatLinePattern.test(entry) || smokeLinePattern.test(entry))) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return false;
}

async function sendAuthenticatedChatMessage(page, consoleMessages, mode, message, options = {}) {
  await clearQueueRecorder(page);
  await openMobileKeyboard(page, `chat ${mode}`);
  const viewportFreeze = options.checkViewportFreeze
    ? await assertKeyboardViewportFrozen(page, `chat ${mode}`)
    : null;

  if (mode === 'typed') {
    await page.keyboard.type(message, { delay: 15 });
  } else if (mode === 'beforeinput') {
    await dispatchKeyboardBeforeInput(page, 'insertText', message);
  } else if (mode === 'paste') {
    await dispatchKeyboardPaste(page, message.replace(/ /g, '\n'));
  } else if (mode === 'composition') {
    await dispatchKeyboardComposition(page, message);
  } else if (mode === 'backspace') {
    await dispatchKeyboardBeforeInput(page, 'insertText', `${message}x`);
    await dispatchKeyboardBeforeInput(page, 'deleteContentBackward', null);
  } else {
    throw new Error(`unsupported chat mode ${mode}`);
  }

  await page.keyboard.press('Enter');
  await page.waitForTimeout(1200);
  const events = await recordedQueueEvents(page);
  const typedText = typedTextFromEvents(events);
  assert(events.includes('s,1'), `chat ${mode} should enqueue keyboard open state: ${events.join(' ')}`);
  if (mode === 'backspace') {
    assertTextEventsContain(events, `${message}x`, `chat ${mode}`);
    assert(events.includes('k,1,8'), `chat ${mode} should enqueue Backspace: ${events.join(' ')}`);
  } else {
    assertTextEventsContain(events, message, `chat ${mode}`);
  }
  assert(events.includes('k,1,10'), `chat ${mode} should enqueue Enter: ${events.join(' ')}`);
  const echoObserved = await waitForChatEcho(consoleMessages, message, remoteMode ? 2500 : 7000);
  if (!remoteMode) {
    assert(echoObserved, `local chat ${mode} should observe chat echo/send for ${JSON.stringify(message)}`);
  }
  const close = await closeMobileKeyboardWithButton(page, `chat ${mode} cleanup`);
  return {
    mode,
    message,
    events,
    typedText,
    echoObserved,
    viewportFreeze,
    close
  };
}

async function proveKeyboardBackClose(page) {
  await clearQueueRecorder(page);
  await openMobileKeyboard(page, 'chat browser Back close');
  await page.evaluate(() => history.back());
  await waitForMobileKeyboard(page, false, 'chat browser Back close');
  const events = await recordedQueueEvents(page);
  assert(events.includes('s,1'), `chat browser Back should start with keyboard open: ${events.join(' ')}`);
  assert(!events.includes('b,1'), `chat browser Back should close keyboard before shared Back: ${events.join(' ')}`);
  assert(events.includes('s,0'), `chat browser Back should close keyboard: ${events.join(' ')}`);
  return { events };
}

async function proveKeyboardEscapeClose(page) {
  await clearQueueRecorder(page);
  await openMobileKeyboard(page, 'chat Escape close');
  await page.keyboard.press('Escape');
  await waitForMobileKeyboard(page, false, 'chat Escape close');
  const events = await recordedQueueEvents(page);
  assert(events.includes('s,1'), `chat Escape should start with keyboard open: ${events.join(' ')}`);
  assert(!events.includes('b,1'), `chat Escape should close keyboard before shared Back: ${events.join(' ')}`);
  assert(events.includes('s,0'), `chat Escape should close keyboard: ${events.join(' ')}`);
  assert(!events.some((entry) => /^k,[01],27$/.test(entry)),
    `chat Escape should not leak raw Escape key events: ${events.join(' ')}`);
  return { events };
}

async function proveAuthenticatedChat(page, consoleMessages) {
  const before = await waitForUiState(page);
  assert(before.customUi && before.webBuild && before.androidProfile,
    `authenticated chat proof requires shared mobile Voidscape UI: ${JSON.stringify(before)}`);
  assert(!before.blockingDialog, `authenticated chat proof requires blocking dialogs closed: ${JSON.stringify(before)}`);
  const suffix = Date.now().toString(36).slice(-5);
  const typed = await sendAuthenticatedChatMessage(page, consoleMessages, 'typed', `typed ${suffix}`, {
    checkViewportFreeze: true
  });
  const beforeInput = await sendAuthenticatedChatMessage(page, consoleMessages, 'beforeinput', `input ${suffix}`);
  const paste = await sendAuthenticatedChatMessage(page, consoleMessages, 'paste', `paste ${suffix}`);
  const composition = await sendAuthenticatedChatMessage(page, consoleMessages, 'composition', `comp ${suffix}`);
  const backspace = await sendAuthenticatedChatMessage(page, consoleMessages, 'backspace', `edit ${suffix}`);
  const backClose = await proveKeyboardBackClose(page);
  const escapeClose = await proveKeyboardEscapeClose(page);
  const snapshot = await diagnosticsSnapshot(page);
  assert(snapshot.inGame === true, 'authenticated chat proof should leave the client in-game');
  assert(snapshot.keyboard && snapshot.keyboard.open === false,
    `authenticated chat proof should leave keyboard closed: ${JSON.stringify(snapshot.keyboard)}`);
  assert(snapshot.viewport.scrollX === 0 && snapshot.viewport.scrollY === 0,
    `authenticated chat proof scrolled the page: ${JSON.stringify(snapshot.viewport)}`);
  assert(snapshot.recentErrors.length === 0,
    `authenticated chat diagnostics reported errors: ${snapshot.recentErrors.join('; ')}`);
  return {
    before,
    messages: {
      typed,
      beforeInput,
      paste,
      composition,
      backspace
    },
    backClose,
    escapeClose,
    snapshot
  };
}

async function readLaunchState(page, path) {
  await page.goto(pageUrl(path), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForClient(page);
  await page.waitForFunction(() => !!window.__voidscapeEffectiveWebSocketUrl, null, { timeout: 30000 }).catch(() => null);
  await page.waitForTimeout(800);
  return await page.evaluate(() => ({
    endpoint: window.__voidscapeEndpoint,
    effectiveWs: window.__voidscapeEffectiveWebSocketUrl || '',
    stored: (() => {
      try { return localStorage.getItem('voidscape.web.endpoint.v1') || ''; } catch (error) { return 'unavailable'; }
    })(),
    htmlClass: document.documentElement.className,
    bodyClass: document.body.className,
    diagnosticsButtonDisplay: getComputedStyle(document.getElementById('diagnostics-button')).display,
    diagnosticsCopyButtonDisplay: getComputedStyle(document.getElementById('diagnostics-copy-button')).display,
    diagnosticsPanelDisplay: getComputedStyle(document.getElementById('diagnostics-panel')).display,
    diagnosticsEnabled: !!window.__voidscapeDiagnosticsEnabled,
    diagnosticsSource: window.__voidscapeDiagnosticsSource || '',
    diagnosticsStored: (() => {
      try { return localStorage.getItem('voidscape.web.diagnostics.v1') || ''; } catch (error) { return 'unavailable'; }
    })(),
    snapshot: window.__voidscapeCollectDiagnostics()
  }));
}

async function readLoginState(page) {
  return await page.evaluate(() => {
    const canvas = document.querySelector('canvas');
    const rect = canvas.getBoundingClientRect();
    return {
      title: document.title,
      bodyClass: document.body.className,
      htmlClass: document.documentElement.className,
      scrollX: window.scrollX,
      scrollY: window.scrollY,
      canvas: {
        width: canvas.width,
        height: canvas.height,
        cssWidth: Math.round(rect.width),
        cssHeight: Math.round(rect.height)
      },
      snapshot: window.__voidscapeDiagnosticsSnapshot || window.__voidscapeCollectDiagnostics()
    };
  });
}

async function dismissInitialBlockingDialog(page, label) {
  const attempts = [];
  for (let attempt = 0; attempt < 6; attempt++) {
    const snapshot = await diagnosticsSnapshot(page);
    const ui = snapshot.ui || {};
    if (!ui.blockingDialog) {
      return { attempts, closed: true };
    }

    const gameHeight = Math.max(
      346,
      (snapshot.client && snapshot.client.gameHeight) || 0,
      (snapshot.canvas && snapshot.canvas.height) || 0
    );
    const candidates = [];
    if (String(ui.blockingDialogName || '') === 'welcome') {
      for (const dialogHeight of [135, 180]) {
        candidates.push({
          x: 254,
          y: Math.round((gameHeight + dialogHeight) / 2 - 15),
          reason: `welcome-${dialogHeight}`
        });
      }
    }
    candidates.push({ x: 256, y: Math.round(gameHeight / 2 + 64), reason: 'center-lower' });
    candidates.push({ x: 256, y: 505, reason: 'legacy' });

    const candidate = candidates[Math.min(attempt, candidates.length - 1)];
    candidate.y = Math.max(8, Math.min(gameHeight - 8, candidate.y));
    attempts.push({
      attempt: attempt + 1,
      dialog: ui.blockingDialogName || '',
      gameHeight,
      candidate
    });
    await clickFrame(page, candidate.x, candidate.y);
    await page.waitForTimeout(450);

    const closed = await page.evaluate(() => {
      const ui = window.__voidscapeUiState;
      return !!ui && !ui.blockingDialog && !document.body.classList.contains('dialog-open');
    });
    if (closed) {
      return { attempts, closed: true };
    }
  }

  const snapshot = await diagnosticsSnapshot(page);
  const screenshot = path.join(outDir, 'iphone-blocking-dialog-dismiss-failed.png');
  await page.screenshot({ path: screenshot, fullPage: true });
  throw new Error(`${label} did not close after computed taps: ${JSON.stringify({ attempts, ui: snapshot.ui, client: snapshot.client, canvas: snapshot.canvas, screenshot })}`);
}

async function performLoginAttempt(page, loginUrl) {
  await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForClient(page);
  await page.waitForTimeout(3000);
  await installQueueRecorder(page);
  await clearQueueRecorder(page);

  let portalHandoff = null;
  if (!focusedSmoke) {
    await page.evaluate(() => {
      window.__voidscapePortalNavigationMode = 'capture';
      window.__voidscapeOpenedUrls = [];
    });
    await tapPortalButton(page, 'account', 'login Create Account', [
      { x: 256, y: 181 },
      { x: 256, y: 184 },
      { x: 256, y: 178 }
    ]);
    await pointerTapFrame(page, 256, 223);
    await page.waitForTimeout(600);
    portalHandoff = await tapPortalButton(page, 'recovery', 'login Recover account', [
      { x: 256, y: 203 },
      { x: 256, y: 206 },
      { x: 256, y: 200 }
    ]);
    const expectedAccountUrl = expectedPortalAccountUrl();
    const expectedRecoveryUrl = expectedPortalRecoveryUrl();
    assert(portalHandoff.config && portalHandoff.config.accountUrl === expectedAccountUrl,
      `login Create Account URL should resolve from portal config: ${JSON.stringify(portalHandoff)}`);
    assert(portalHandoff.config && portalHandoff.config.recoveryUrl === expectedRecoveryUrl,
      `login Recover account URL should resolve from portal config: ${JSON.stringify(portalHandoff)}`);
    assert(portalHandoff.opened.some((entry) => entry.kind === 'account' && entry.target === '_self' && entry.url === expectedAccountUrl),
      `login Create Account should use current-tab portal handoff: ${JSON.stringify(portalHandoff.opened)}`);
    assert(portalHandoff.opened.some((entry) => entry.kind === 'recovery' && entry.target === '_self' && entry.url === expectedRecoveryUrl),
      `login Recover account should use current-tab portal handoff: ${JSON.stringify(portalHandoff.opened)}`);
  }
  await clearQueueRecorder(page);
  await page.waitForTimeout(1200);

  let loginKeyboardEvents = [];
  let typedText = '';
  let enterDownCount = 0;
  let keyboardClosedAfterSubmit = true;
  if (focusedSmoke) {
    await page.evaluate(({ user, pass }) => {
      window.__voidscapeSmokeLoginRequest = { user, pass };
    }, { user, pass });
    await page.waitForTimeout(500);
  } else {
    await openMobileKeyboard(page, 'login');
    await page.keyboard.type(user, { delay: 15 });
    await page.waitForFunction((user) => {
      const snapshot = window.__voidscapeCollectDiagnostics();
      return snapshot && snapshot.login && snapshot.login.userText === user;
    }, user, { timeout: 5000 });
    let loginState = (await diagnosticsSnapshot(page)).login;
    assert(loginState && loginState.userText === user,
      `login username should be present immediately after typing: ${JSON.stringify(loginState)}`);
    await page.evaluate(() => {
      if (window.__voidscapeBlurKeyboard) window.__voidscapeBlurKeyboard();
    });
    await waitForMobileKeyboard(page, false, 'login username outside blur');
    await page.setViewportSize({ width: 390, height: 604 });
    await page.waitForTimeout(350);
    await page.setViewportSize({ width: 390, height: 664 });
    await page.waitForTimeout(500);
    loginState = (await diagnosticsSnapshot(page)).login;
    assert(loginState && loginState.userText === user,
      `login username should survive keyboard close/viewport rebuild: ${JSON.stringify(loginState)}`);
    await openMobileKeyboard(page, 'login after username blur');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(350);
    loginState = (await diagnosticsSnapshot(page)).login;
    assert(loginState && loginState.userText === user,
      `login username should survive iPhone Done/checkmark: ${JSON.stringify(loginState)}`);
    assert(loginState && loginState.passwordFocused,
      `login Done/checkmark should advance focus to password without clearing username: ${JSON.stringify(loginState)}`);
    assert(!String((loginState && loginState.status1) || '').includes('You must enter both')
      && !String((loginState && loginState.status2) || '').includes('password - Please try again'),
      `login Done/checkmark should not submit empty password: ${JSON.stringify(loginState)}`);
    await pointerTapFrame(page, 256, 111);
    await waitForMobileKeyboard(page, true, 'login password field tap');
    await page.keyboard.type(pass, { delay: 15 });
    await page.keyboard.press('Enter');
    await page.waitForTimeout(500);
    loginKeyboardEvents = await recordedQueueEvents(page);
    typedText = typedTextFromEvents(loginKeyboardEvents);
    enterDownCount = loginKeyboardEvents.filter((entry) => entry === 'k,1,10').length;
    assert(loginKeyboardEvents.includes('s,1'), `login mobile keyboard should enqueue open state: ${loginKeyboardEvents.join(' ')}`);
    assert(typedText === `${user}${pass}`,
      `login mobile keyboard should keep username while tapping password and then type password; got ${JSON.stringify(typedText)}`);
    assert(enterDownCount >= 1, `login mobile keyboard should enqueue Enter for submit: ${loginKeyboardEvents.join(' ')}`);
    keyboardClosedAfterSubmit = await page.waitForFunction(() => {
      const snapshot = window.__voidscapeCollectDiagnostics();
      return snapshot
        && snapshot.keyboard
        && !snapshot.keyboard.open
        && !document.body.classList.contains('keyboard-open');
    }, null, { timeout: 5000 }).then(() => true).catch(() => false);
    assert(keyboardClosedAfterSubmit, `login submit should close the mobile keyboard: ${loginKeyboardEvents.join(' ')}`);
  }

  await page.waitForTimeout(8000);
  await page.click('#diagnostics-button');
  await page.waitForTimeout(600);
  const loginState = await readLoginState(page);
  loginState.mobileKeyboardLogin = {
    opened: loginKeyboardEvents.includes('s,1'),
    closedAfterSubmit: keyboardClosedAfterSubmit,
    typedCharacters: typedText.length,
    enterDownCount,
    portalHandoff
  };
  return loginState;
}

async function performLoginWithBusyRetry(page, loginUrl, consoleMessages) {
  const firstConsoleIndex = consoleMessages.length;
  let loginState = await performLoginAttempt(page, loginUrl);
  if (loginState.title.startsWith('Voidscape -- ')) {
    return loginState;
  }
  const firstConsole = consoleMessages.slice(firstConsoleIndex);
  const busy = firstConsole.some((entry) => /login response:\s*4|already logged in/i.test(entry));
  if (!busy) {
    return loginState;
  }
  await page.waitForTimeout(15000);
  loginState = await performLoginAttempt(page, loginUrl);
  loginState.retryAfterBusyLogin = true;
  return loginState;
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });

  browser = await chromium.launch({
    executablePath: chromePath,
    headless: true,
    args: ignoreHttpsErrors ? ['--ignore-certificate-errors'] : []
  });

  const context = await browser.newContext({
    viewport: { width: 390, height: 664 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    ignoreHTTPSErrors: ignoreHttpsErrors,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
  });

  const page = await context.newPage();
  const failed = [];
  const consoleMessages = [];
  page.on('requestfailed', (req) => {
    const failure = `${req.method()} ${req.url()} ${req.failure() && req.failure().errorText}`;
    if (/^GET .*\/Cache\/voidscape\/ui\/skin\/top-tab-normal\.png net::ERR_ABORTED$/.test(failure)) {
      return;
    }
    failed.push(failure);
  });
  page.on('console', (msg) => consoleMessages.push(`${msg.type()}: ${msg.text()}`));

  const reset = await readLaunchState(page, launchPath({ reset: true }));
  assert(reset.endpoint && reset.endpoint.source === 'default', 'endpoint reset should use default endpoint');
  if (remoteMode && !explicitWs) {
    assert(reset.effectiveWs, 'remote default launch should expose an effective WebSocket URL');
    if (baseUrl.startsWith('https:')) {
      assert(reset.effectiveWs.startsWith('wss://'), `HTTPS remote launch should default to WSS, got ${reset.effectiveWs}`);
    }
  }

  let endpointPersistence;
  let expectedEffectiveWs = reset.effectiveWs;
  if (remoteMode && explicitWs) {
    const fullWs = await readLaunchState(page, launchPath({ ws: explicitWs }));
    assert(fullWs.endpoint.mode === 'ws' && fullWs.endpoint.source === 'query', 'remote full ws launch should come from query');
    assert(fullWs.effectiveWs === explicitWs, `unexpected remote query effectiveWs: ${fullWs.effectiveWs}`);

    const storedFullWs = await readLaunchState(page, launchPath());
    assert(storedFullWs.endpoint.mode === 'ws' && storedFullWs.endpoint.source === 'stored', 'remote plain mobile launch should reuse stored full ws endpoint');
    assert(storedFullWs.effectiveWs === explicitWs, `unexpected remote stored effectiveWs: ${storedFullWs.effectiveWs}`);

    endpointPersistence = {
      fullWs: fullWs.endpoint,
      storedFullWs: storedFullWs.endpoint
    };
    expectedEffectiveWs = explicitWs;
  } else if (!remoteMode) {
    const hostPort = await readLaunchState(page, launchPath({ hostPort: true }));
    assert(hostPort.endpoint.source === 'query', 'host/port launch should come from query');
    assert(hostPort.endpoint.host === host, `expected query host ${host}, got ${hostPort.endpoint.host}`);
    assert(hostPort.endpoint.port === wsPort, `expected query port ${wsPort}, got ${hostPort.endpoint.port}`);

    const storedHostPort = await readLaunchState(page, launchPath());
    assert(storedHostPort.endpoint.source === 'stored', 'plain mobile launch should reuse stored host/port endpoint');
    assert(storedHostPort.effectiveWs === localWsUrl(), `unexpected stored effectiveWs: ${storedHostPort.effectiveWs}`);

    const fullWs = await readLaunchState(page, launchPath({ ws: explicitWs || localWsUrl() }));
    assert(fullWs.endpoint.mode === 'ws' && fullWs.endpoint.source === 'query', 'full ws launch should come from query');

    const storedFullWs = await readLaunchState(page, launchPath());
    assert(storedFullWs.endpoint.mode === 'ws' && storedFullWs.endpoint.source === 'stored', 'plain mobile launch should reuse stored full ws endpoint');

    endpointPersistence = {
      hostPort: hostPort.endpoint,
      storedHostPort: storedHostPort.endpoint,
      fullWs: fullWs.endpoint,
      storedFullWs: storedFullWs.endpoint
    };
    expectedEffectiveWs = expectedLocalEndpointUrl();
  } else {
    endpointPersistence = {
      defaultEndpoint: reset.endpoint
    };
  }

  const endpointParams = explicitWs
    ? { ws: explicitWs }
    : (!remoteMode ? { hostPort: true } : {});

  const normal = await readLaunchState(page, launchPath(endpointParams));
  assert(normal.diagnosticsButtonDisplay === 'none', 'diagnostics button should be hidden without diag=1');
  assert(normal.diagnosticsCopyButtonDisplay === 'none', 'diagnostics copy button should be hidden without diag=1');

  const diag = await readLaunchState(page, launchPath({ ...endpointParams, diag: true }));
  assert(diag.htmlClass.includes('diagnostics'), 'diag launch should add diagnostics class');
  assert(diag.diagnosticsButtonDisplay === 'flex', 'diagnostics button should be visible with diag=1');
  assert(diag.diagnosticsCopyButtonDisplay === 'flex', 'diagnostics copy button should be visible with diag=1');
  assert(diag.diagnosticsSource === 'query' && diag.diagnosticsStored === '1',
    `diag=1 should persist diagnostics for Home Screen launch: ${JSON.stringify(diag)}`);
  const storedDiag = await readLaunchState(page, launchPath(endpointParams));
  assert(storedDiag.htmlClass.includes('diagnostics'), 'plain mobile launch should reuse stored diagnostics state');
  assert(storedDiag.diagnosticsButtonDisplay === 'flex', 'stored diagnostics button should be visible after diag=1');
  assert(storedDiag.diagnosticsSource === 'stored',
    `plain mobile launch should report stored diagnostics source: ${JSON.stringify(storedDiag)}`);
  const diagOff = await readLaunchState(page, launchPath({ ...endpointParams, diag: false }));
  assert(!diagOff.htmlClass.includes('diagnostics'), 'diag=0 should clear stored diagnostics state');
  assert(diagOff.diagnosticsButtonDisplay === 'none', 'diagnostics button should be hidden after diag=0');
  assert(diagOff.diagnosticsStored === '', `diag=0 should clear diagnostics storage: ${JSON.stringify(diagOff)}`);

  await readLaunchState(page, launchPath({ ...endpointParams, diag: true }));
  await page.click('#diagnostics-button');
  await page.waitForTimeout(500);
  const diagOpen = await page.evaluate(() => ({
    htmlClass: document.documentElement.className,
    panelDisplay: getComputedStyle(document.getElementById('diagnostics-panel')).display,
    panelText: document.getElementById('diagnostics-panel').textContent,
    snapshot: window.__voidscapeDiagnosticsSnapshot || window.__voidscapeCollectDiagnostics()
  }));
  assert(diagOpen.htmlClass.includes('diagnostics-open'), 'diagnostics panel should open');
  assert(diagOpen.panelDisplay === 'block', 'diagnostics panel should display');
  assert(diagOpen.panelText.includes(`effectiveWs: ${diagOpen.snapshot.effectiveWs}`), 'diagnostics panel should show effective WebSocket URL');
  if (expectedEffectiveWs) {
    assert(diagOpen.snapshot.effectiveWs === expectedEffectiveWs, `unexpected diagnostics effectiveWs: ${diagOpen.snapshot.effectiveWs}`);
  }

  const login = await performLoginWithBusyRetry(page, pageUrl(launchPath({
    ...endpointParams,
    diag: true,
    ...portalLaunchParams()
  })), consoleMessages);
  assert(login.title.startsWith('Voidscape -- '), `expected logged-in title, got ${login.title}`);
  assert(login.bodyClass.includes('in-game'), `expected in-game body class, got ${login.bodyClass}`);
  assert(login.snapshot && login.snapshot.inGame === true, 'diagnostics snapshot should report inGame true');
  if (expectedEffectiveWs) {
    assert(login.snapshot.effectiveWs === expectedEffectiveWs, `unexpected login effectiveWs: ${login.snapshot.effectiveWs}`);
  } else {
    assert(login.snapshot.effectiveWs, 'login diagnostics should report an effective WebSocket URL');
  }
  assert(login.canvas.width === 512 && login.canvas.height > 0, `unexpected framebuffer ${JSON.stringify(login.canvas)}`);
  assert(login.scrollX === 0 && login.scrollY === 0, `page scrolled unexpectedly: ${login.scrollX},${login.scrollY}`);

  await installQueueRecorder(page);
  await page.click('#diagnostics-button');
  await page.waitForTimeout(300);
  let shell = await readMobileShell(page);
  let uiState = await waitForUiState(page);
  let initialDialogSafeShell = null;
  if (uiState.blockingDialog) {
    assertDialogSafeShell(shell, uiState, 'initial post-login blocking dialog controls');
    initialDialogSafeShell = { shell, uiState };
    initialDialogSafeShell.dismiss = await dismissInitialBlockingDialog(page, 'initial post-login blocking dialog');
    await page.waitForFunction(() => {
      const ui = window.__voidscapeUiState;
      return ui && !ui.blockingDialog && !document.body.classList.contains('dialog-open');
    }, null, { timeout: 5000 });
    shell = await readMobileShell(page);
    uiState = await waitForUiState(page);
	  }
	  assert(!uiState.blockingDialog, `blocking dialogs should be closed before normal control assertions: ${JSON.stringify(uiState)}`);
	  assert(uiState.chatPanelHidden === false && uiState.chatAccessMode === 'canvas',
	    `canvas chat should own the normal mobile HUD after login: ${JSON.stringify(uiState)}`);
	  assert(uiState.mobilePanelShell === false && uiState.panelAccessMode === 'canvas'
	      && uiState.canvasTopTabsVisible === true && uiState.canvasPanelRailVisible === false
	      && uiState.canvasPanelDockVisible === false,
	    `Android-parity canvas tabs should own panel access after login: ${JSON.stringify(uiState)}`);

	  if (onlyWorldMapSearch) {
    await setDiagnosticsHitTesting(page, false);
	    const worldMap = await proveWorldMapMobileSearch(page);
	    const snapshot = await diagnosticsSnapshot(page);
    assert(snapshot.inGame === true, 'world-map focused smoke should leave the client in-game');
    assert(snapshot.viewport.scrollX === 0 && snapshot.viewport.scrollY === 0,
      `world-map focused smoke scrolled the page: ${JSON.stringify(snapshot.viewport)}`);
    assert(snapshot.recentErrors.length === 0, `world-map focused diagnostics reported errors: ${snapshot.recentErrors.join('; ')}`);
    const screenshot = path.join(outDir, 'iphone-world-map-diagnostics.png');
    await page.screenshot({ path: screenshot, fullPage: true });
    await browser.close();

    const summary = {
      baseUrl,
      remoteMode,
      host,
      wsPort,
      wsUrl: explicitWs,
      user,
      focused: 'world-map-search',
      endpointPersistence,
      diagnostics: {
        hiddenByDefault: normal.diagnosticsButtonDisplay === 'none',
        copyHiddenByDefault: normal.diagnosticsCopyButtonDisplay === 'none',
        open: diagOpen.panelDisplay === 'block'
      },
      login: {
        title: login.title,
        canvas: login.canvas,
        effectiveWs: login.snapshot.effectiveWs,
        recentErrors: login.snapshot.recentErrors,
        mobileKeyboard: login.mobileKeyboardLogin
      },
      initialDialogSafeShell,
      worldMap,
      snapshot,
      failedRequests: failed,
      unexpectedConsole: consoleMessages.filter((entry) =>
        /sprite missing:null/i.test(entry) ||
        /Cannot read properties of null/i.test(entry)
      ),
      consoleTail: consoleMessages.slice(-12),
      screenshot
    };
    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify(summary, null, 2));
    assert(summary.unexpectedConsole.length === 0,
      `unexpected console messages observed: ${summary.unexpectedConsole.join('; ')}`);
    return;
  }

	  if (onlyChat) {
    await setDiagnosticsHitTesting(page, false);
	    const chat = await proveAuthenticatedChat(page, consoleMessages);
	    const snapshot = await diagnosticsSnapshot(page);
    const screenshot = path.join(outDir, 'iphone-chat-diagnostics.png');
    await page.screenshot({ path: screenshot, fullPage: true });
    await browser.close();

    const summary = {
      baseUrl,
      remoteMode,
      host,
      wsPort,
      wsUrl: explicitWs,
      user,
      focused: 'chat',
      endpointPersistence,
      diagnostics: {
        hiddenByDefault: normal.diagnosticsButtonDisplay === 'none',
        copyHiddenByDefault: normal.diagnosticsCopyButtonDisplay === 'none',
        open: diagOpen.panelDisplay === 'block'
      },
      login: {
        title: login.title,
        canvas: login.canvas,
        effectiveWs: login.snapshot.effectiveWs,
        recentErrors: login.snapshot.recentErrors,
        mobileKeyboard: login.mobileKeyboardLogin
      },
      initialDialogSafeShell,
      chat,
      snapshot,
      failedRequests: failed,
      unexpectedConsole: consoleMessages.filter((entry) =>
        /sprite missing:null/i.test(entry) ||
        /Cannot read properties of null/i.test(entry)
      ),
      consoleTail: consoleMessages.slice(-20),
      screenshot
    };
    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify(summary, null, 2));
    assert(summary.unexpectedConsole.length === 0,
      `unexpected console messages observed: ${summary.unexpectedConsole.join('; ')}`);
    return;
  }

  if (onlyContextMenu) {
    await setDiagnosticsHitTesting(page, false);
    const contextMenu = await proveLongPressContextMenu(page);
    const snapshot = await diagnosticsSnapshot(page);
    const screenshot = path.join(outDir, 'iphone-context-menu-diagnostics.png');
    await page.screenshot({ path: screenshot, fullPage: true });
    await browser.close();

    const summary = {
      baseUrl,
      remoteMode,
      host,
      wsPort,
      wsUrl: explicitWs,
      user,
      focused: 'context-menu',
      endpointPersistence,
      diagnostics: {
        hiddenByDefault: normal.diagnosticsButtonDisplay === 'none',
        copyHiddenByDefault: normal.diagnosticsCopyButtonDisplay === 'none',
        open: diagOpen.panelDisplay === 'block'
      },
      login: {
        title: login.title,
        canvas: login.canvas,
        effectiveWs: login.snapshot.effectiveWs,
        recentErrors: login.snapshot.recentErrors,
        mobileKeyboard: login.mobileKeyboardLogin
      },
      initialDialogSafeShell,
      contextMenu,
      snapshot,
      failedRequests: failed,
      unexpectedConsole: consoleMessages.filter((entry) =>
        /sprite missing:null/i.test(entry) ||
        /Cannot read properties of null/i.test(entry)
      ),
      consoleTail: consoleMessages.slice(-20),
      screenshot
    };
    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify(summary, null, 2));
    assert(summary.unexpectedConsole.length === 0,
      `unexpected console messages observed: ${summary.unexpectedConsole.join('; ')}`);
    return;
  }

  assert(shell.keyboardButton.display === 'flex', `keyboard button should be visible after login: ${JSON.stringify(shell.keyboardButton)}`);
	  assert(shell.inventoryButton.display === 'flex', `inventory shortcut should be visible after login: ${JSON.stringify(shell.inventoryButton)}`);
	  assert(shell.magicButton.display === 'flex', `magic shortcut should be visible after login: ${JSON.stringify(shell.magicButton)}`);
	  assert(shell.prayerButton.display === 'flex', `prayer shortcut should be visible after login: ${JSON.stringify(shell.prayerButton)}`);
	  assert(shell.panelButton.display === 'none', `DOM panel button should be hidden while Android-parity canvas tabs own panels after login: ${JSON.stringify(shell.panelButton)}`);
	  assert(shell.panelDrawer.display === 'none', `panel drawer should be hidden while Android-parity canvas tabs own panels after login: ${JSON.stringify(shell.panelDrawer)}`);
	  assert(shell.chatButton.display === 'none',
	    `DOM chat helper should be hidden while canvas chat tabs own chat after login: ${JSON.stringify(shell.chatButton)}`);
	  assert(shell.chatTray.display === 'none', `chat tray should be closed by default after login: ${JSON.stringify(shell.chatTray)}`);
  assert(shell.cameraControls.display === 'grid', `camera controls should be visible after login: ${JSON.stringify(shell.cameraControls)}`);
  assert(shell.diagnosticsButton.display === 'flex', `diagnostics button should be visible after login: ${JSON.stringify(shell.diagnosticsButton)}`);
  assert(shell.diagnosticsCopyButton.display === 'flex', `diagnostics copy button should be visible after login: ${JSON.stringify(shell.diagnosticsCopyButton)}`);
  assertControlAboveBottomHud(shell.keyboardButton, shell.viewport, 'keyboard button');
  for (const quick of [shell.inventoryButton, shell.magicButton, shell.prayerButton]) {
    assertControlAboveBottomHud(quick, shell.viewport, `${quick.selector || 'quick side shortcut'}`);
    assertNoOverlap(shell.diagnosticsButton, quick, 'diagnostics/quick side controls');
    assertNoOverlap(shell.diagnosticsCopyButton, quick, 'diagnostics copy/quick side controls');
  }
  assertNoOverlap(shell.diagnosticsButton, shell.keyboardButton, 'diagnostics/keyboard controls');
  assertNoOverlap(shell.diagnosticsCopyButton, shell.keyboardButton, 'diagnostics copy/keyboard controls');
  assertNoOverlap(shell.diagnosticsCopyButton, shell.diagnosticsButton, 'diagnostics controls');
  const diagnosticsControls = (await diagnosticsSnapshot(page)).controls;
	  for (const [key, expectedDisplay] of Object.entries({
	    keyboardButton: 'flex',
	    inventoryButton: 'flex',
	    magicButton: 'flex',
	    prayerButton: 'flex',
	    cameraControls: 'grid',
	    diagnosticsButton: 'flex',
	    diagnosticsCopyButton: 'flex'
  })) {
    const control = diagnosticsControls && diagnosticsControls[key];
    assert(control && control.present && control.visible,
      `diagnostics controls.${key} should be present and visible after login: ${JSON.stringify(control)}`);
    assert(control.display === expectedDisplay,
      `diagnostics controls.${key} display should be ${expectedDisplay}: ${JSON.stringify(control)}`);
    assert(control.hitSelf === true,
      `diagnostics controls.${key} should be topmost at its center after login: ${JSON.stringify(control)}`);
	    assert(control.rect && control.rect.width > 0 && control.rect.height > 0,
	      `diagnostics controls.${key} should include a nonzero rect: ${JSON.stringify(control)}`);
	  }
	  const diagnosticsChatButton = diagnosticsControls && diagnosticsControls.chatButton;
	  assert(diagnosticsChatButton && diagnosticsChatButton.present,
	    `diagnostics controls.chatButton should be present for diagnostics after login: ${JSON.stringify(diagnosticsChatButton)}`);
	  assert(diagnosticsChatButton.display === 'none' && diagnosticsChatButton.visible === false,
	    `diagnostics controls.chatButton should be hidden while canvas chat tabs own chat after login: ${JSON.stringify(diagnosticsChatButton)}`);
	assert(uiState.customUi && uiState.webBuild && uiState.androidProfile,
		`shared Voidscape custom UI should be active in web mobile mode: ${JSON.stringify(uiState)}`);
	const diagnosticsPanelButton = diagnosticsControls && diagnosticsControls.panelButton;
		assert(diagnosticsPanelButton && diagnosticsPanelButton.present,
			`diagnostics controls.panelButton should be present for diagnostics after login: ${JSON.stringify(diagnosticsPanelButton)}`);
		assert(diagnosticsPanelButton.display === 'none' && diagnosticsPanelButton.visible === false,
			`diagnostics controls.panelButton should be hidden while Android-parity canvas tabs own panels after login: ${JSON.stringify(diagnosticsPanelButton)}`);
    await setDiagnosticsHitTesting(page, false);
		const mobileChatTray = { skipped: true, reason: 'Android parity uses shared canvas chat tabs instead of the web chat tray' };
		const customHudChatTabs = await proveCustomHudChatTabs(page);
	const customHudTopPanels = await proveCustomHudTopPanels(page);
  const customHudTopPanelScroll = await proveScrollableTopPanelSwipe(page);
  const customHudUiHistory = await waitForCustomHudUiHistory(page, 'custom HUD diagnostics');

  const chatMessage = `websmoke ${Date.now().toString(36).slice(-6)}`;
  await clearQueueRecorder(page);
  await page.click('#keyboard-button');
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return snapshot.keyboard.open && document.body.classList.contains('keyboard-open');
  }, null, { timeout: 5000 });
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return snapshot.ui && snapshot.ui.messageTab === 'CHAT' && snapshot.ui.chatPanelHidden === false;
  }, null, { timeout: 5000 });
  const chatComposeState = await diagnosticsSnapshot(page);
  assert(chatComposeState.ui && chatComposeState.ui.messageTab === 'CHAT' && chatComposeState.ui.chatPanelHidden === false,
    `Aa should put plain in-game input into the shared public chat entry: ${JSON.stringify(chatComposeState.ui)}`);
  await page.keyboard.type(chatMessage, { delay: 15 });
  await page.keyboard.press('Enter');
  await page.waitForTimeout(1200);
  const keyboardEvents = await recordedQueueEvents(page);
  assert(keyboardEvents.includes('s,1'), `keyboard focus should enqueue open state: ${keyboardEvents.join(' ')}`);
  assert(keyboardEvents.includes('c,compose-main'), `Aa should enqueue public-chat compose before typing: ${keyboardEvents.join(' ')}`);
  assert(keyboardEvents.some((entry) => entry === `t,${chatMessage.charCodeAt(0)}`), `keyboard typing should enqueue text events: ${keyboardEvents.join(' ')}`);
  assert(keyboardEvents.includes('k,1,10'), `keyboard Enter should enqueue shared Enter key: ${keyboardEvents.join(' ')}`);
  const chatEchoObserved = await waitForChatEcho(consoleMessages, chatMessage, remoteMode ? 2500 : 7000);
  if (!remoteMode) {
    assert(chatEchoObserved, `local smoke should observe chat echo/send for "${chatMessage}"`);
  }

  await clearQueueRecorder(page);
  await page.evaluate(() => history.back());
  await page.waitForFunction(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return !snapshot.keyboard.open && !document.body.classList.contains('keyboard-open');
  }, null, { timeout: 5000 });
  const backEvents = await recordedQueueEvents(page);
  assert(backEvents.includes('s,0'), `browser Back should close the open keyboard first: ${backEvents.join(' ')}`);
  assert(!backEvents.includes('b,1'), `browser Back should not enqueue shared Back while keyboard is open: ${backEvents.join(' ')}`);

  await clearQueueRecorder(page);
  await page.evaluate(() => history.back());
  await page.waitForTimeout(300);
  const secondBackEvents = await recordedQueueEvents(page);
  assert(secondBackEvents.includes('b,1'), `browser Back should enqueue shared mobile Back after keyboard is closed: ${secondBackEvents.join(' ')}`);

  await clearQueueRecorder(page);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
  const escapeBackEvents = await recordedQueueEvents(page);
  assert(escapeBackEvents.filter((entry) => entry === 'b,1').length === 1,
    `external Escape should enqueue one shared mobile Back: ${escapeBackEvents.join(' ')}`);
  assert(!escapeBackEvents.some((entry) => /^k,[01],27$/.test(entry)),
    `external Escape should not leak raw Escape key events: ${escapeBackEvents.join(' ')}`);

  await page.waitForTimeout(600);
  let menuBackEvents = [];
  if ((await diagnosticsSnapshot(page)).input.topMenu) {
    await clearQueueRecorder(page);
    await page.evaluate(() => history.back());
    await page.waitForFunction(() => {
      const snapshot = window.__voidscapeCollectDiagnostics();
      return !snapshot.input.topMenu;
    }, null, { timeout: 5000 });
    menuBackEvents = await recordedQueueEvents(page);
    assert(menuBackEvents.includes('b,1'), `browser Back should enqueue shared Back while closing context menu: ${menuBackEvents.join(' ')}`);
  }

  const cameraButton = await proveCameraButtonRotation(page);
  const cameraDrag = await proveTouchDragRotation(page);
  const dragZoom = await proveTouchDragZoom(page);
  const pinchZoom = await provePinchZoom(page);

	const movement = await proveTerrainTapMovement(page);
	assert(movement.events.some((entry) => /^p,1,\d+,\d+,1$/.test(entry)),
	    `terrain tap should enqueue a primary tap: ${movement.events.join(' ')}`);

    const cleanScreenshots = {};
    await setDiagnosticsHitTesting(page, true);
		  await page.setViewportSize({ width: 844, height: 390 });
		  const landscapeSnapshot = await waitForViewportShape(page, 'landscape rotation', {
	    cssWidth: 844,
	    cssHeight: 390,
	    minFrameWidth: 740,
	    maxFrameWidth: 760,
	    frameHeight: 346,
  });
  await page.waitForFunction(() => {
    if (typeof window.__voidscapeCollectDiagnostics !== 'function') return false;
    const snapshot = window.__voidscapeCollectDiagnostics();
    return snapshot && snapshot.ui && snapshot.ui.phoneLandscapeLooseChat === true;
  }, null, { timeout: 30000 });
  const landscapeUi = await diagnosticsSnapshot(page);
  const landscapeShell = await readMobileShell(page);
  assert(landscapeShell.keyboardButton.display === 'flex', `keyboard button should remain visible in landscape: ${JSON.stringify(landscapeShell.keyboardButton)}`);
	  assert(landscapeShell.inventoryButton.display === 'none', `inventory shortcut should hide in landscape: ${JSON.stringify(landscapeShell.inventoryButton)}`);
	  assert(landscapeShell.magicButton.display === 'none', `magic shortcut should hide in landscape: ${JSON.stringify(landscapeShell.magicButton)}`);
	  assert(landscapeShell.prayerButton.display === 'none', `prayer shortcut should hide in landscape: ${JSON.stringify(landscapeShell.prayerButton)}`);
	  assert(landscapeShell.panelButton.display === 'none', `DOM panel button should remain hidden while Android-parity canvas tabs own panels in landscape: ${JSON.stringify(landscapeShell.panelButton)}`);
	  assert(landscapeShell.panelDrawer.display === 'none', `panel drawer should remain hidden while Android-parity canvas tabs own panels in landscape: ${JSON.stringify(landscapeShell.panelDrawer)}`);
	  assert(landscapeShell.chatButton.display === 'none', `DOM chat helper should remain hidden while canvas chat tabs own chat in landscape: ${JSON.stringify(landscapeShell.chatButton)}`);
	  assert(landscapeShell.chatTray.display === 'none', `chat tray should be closed by default in landscape: ${JSON.stringify(landscapeShell.chatTray)}`);
  assert(landscapeShell.cameraControls.display === 'grid', `camera controls should remain visible in landscape: ${JSON.stringify(landscapeShell.cameraControls)}`);
  assert(landscapeShell.diagnosticsButton.display === 'flex',
    `diagnostics button should remain visible in landscape: ${JSON.stringify(landscapeShell.diagnosticsButton)}`);
  assert(landscapeShell.diagnosticsCopyButton.display === 'flex',
    `diagnostics copy button should remain visible in landscape: ${JSON.stringify(landscapeShell.diagnosticsCopyButton)}`);
  assertControlAboveBottomHud(landscapeShell.keyboardButton, landscapeShell.viewport, 'landscape keyboard button');
  assertNoOverlap(landscapeShell.diagnosticsButton, landscapeShell.keyboardButton, 'landscape diagnostics/keyboard controls');
  assertNoOverlap(landscapeShell.diagnosticsCopyButton, landscapeShell.keyboardButton, 'landscape diagnostics copy/keyboard controls');
  assertNoOverlap(landscapeShell.diagnosticsCopyButton, landscapeShell.diagnosticsButton, 'landscape diagnostics controls');

	  const landscapeScreenshot = path.join(outDir, 'iphone-landscape-diagnostics.png');
	  await page.screenshot({ path: landscapeScreenshot, fullPage: true });
    cleanScreenshots.landscape = await captureVisualArtifact(page, 'iphone-landscape-clean.png', {
      hideDiagnostics: true,
      hideCamera: true
    });

	  await page.setViewportSize({ width: 390, height: 664 });
			  const portraitReturnSnapshot = await waitForViewportShape(page, 'portrait rotation return', {
		    cssWidth: 390,
		    cssHeight: 664,
		    frameWidth: 512,
		    minFrameHeight: 860,
			    maxFrameHeight: 880
	  });
    await page.waitForFunction(() => {
      if (typeof window.__voidscapeCollectDiagnostics !== 'function') return false;
      const snapshot = window.__voidscapeCollectDiagnostics();
      return snapshot && snapshot.ui && snapshot.ui.phoneLandscapeLooseChat === false;
    }, null, { timeout: 30000 });
    const portraitReturnUi = await diagnosticsSnapshot(page);
    cleanScreenshots.portrait = await captureVisualArtifact(page, 'iphone-portrait-clean.png', {
      hideDiagnostics: true,
      hideCamera: true
    });

    await setDiagnosticsHitTesting(page, false);
	  const lifecycleResume = await proveLifecycleResume(page);
  const postResumeMovement = await proveTerrainTapMovement(page);
  const postResumeProof = await waitForPostResumeProof(page, 'post-resume diagnostics');

  const postLogin = {
			initialDialogSafeShell,
    shell,
			uiState,
			mobileChatTray,
			keyboardEvents,
    customHudChatTabs,
    customHudTopPanels,
    customHudTopPanelScroll,
    customHudUiHistory,
    chatMessage,
	    chatEchoObserved,
	    backEvents,
	    escapeBackEvents,
	    menuBackEvents,
	    camera: {
      button: cameraButton,
      drag: cameraDrag,
      dragZoom,
      pinchZoom
    },
    movement,
    postResumeMovement,
    postResumeProof,
	    landscape: {
	      canvas: landscapeSnapshot.canvas,
	      viewport: landscapeSnapshot.viewport,
	      shell: landscapeShell,
	      ui: landscapeUi.ui,
	      screenshot: landscapeScreenshot,
	      cleanScreenshot: cleanScreenshots.landscape
	    },
	    portraitReturn: {
	      canvas: portraitReturnSnapshot.canvas,
	      viewport: portraitReturnSnapshot.viewport,
	      ui: portraitReturnUi.ui,
	      cleanScreenshot: cleanScreenshots.portrait
	    },
    lifecycleResume,
    snapshot: await diagnosticsSnapshot(page)
  };
  assert(postLogin.snapshot.inGame === true, 'post-login mobile controls should leave the client in-game');
  assert(postLogin.snapshot.viewport.scrollX === 0 && postLogin.snapshot.viewport.scrollY === 0,
    `post-login mobile controls scrolled the page: ${JSON.stringify(postLogin.snapshot.viewport)}`);
  assertAndroidParityCanvasControls(postLogin.snapshot.controls, 'post-login diagnostics.controls');
  assertDiagnosticsControlHistory(postLogin.snapshot.controlsHistory, 'post-login diagnostics');
  assertCustomHudUiHistory(postLogin.snapshot.uiHistory, 'post-login diagnostics');
  assertScrollHistory(postLogin.snapshot.scrollHistory, 'post-login diagnostics');
  assertPostResumeProof(postLogin.snapshot.postResumeProof, 'post-login diagnostics');
  assert(postLogin.snapshot.recentErrors.length === 0, `post-login diagnostics reported errors: ${postLogin.snapshot.recentErrors.join('; ')}`);

  const screenshot = path.join(outDir, 'iphone-login-diagnostics.png');
  await page.screenshot({ path: screenshot, fullPage: true });

  await browser.close();

  const summary = {
    baseUrl,
    remoteMode,
    host,
    wsPort,
    wsUrl: explicitWs,
    user,
    portal: {
      configuredBase: portalUrl,
      configuredAccountUrl: portalAccountUrl,
      configuredRecoveryUrl: portalRecoveryUrl,
      expectedAccountUrl: expectedPortalAccountUrl(),
      expectedRecoveryUrl: expectedPortalRecoveryUrl()
    },
    endpointPersistence,
    diagnostics: {
      hiddenByDefault: normal.diagnosticsButtonDisplay === 'none',
      copyHiddenByDefault: normal.diagnosticsCopyButtonDisplay === 'none',
      open: diagOpen.panelDisplay === 'block'
    },
    login: {
      title: login.title,
      canvas: login.canvas,
      effectiveWs: login.snapshot.effectiveWs,
      recentErrors: login.snapshot.recentErrors,
      mobileKeyboard: login.mobileKeyboardLogin
    },
    postLogin,
    failedRequests: failed,
    unexpectedConsole: consoleMessages.filter((entry) =>
      /sprite missing:null/i.test(entry) ||
      /Cannot read properties of null/i.test(entry)
    ),
    consoleTail: consoleMessages.slice(-12),
    screenshot
  };

  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
  assert(failed.length === 0, `request failures observed: ${failed.join('; ')}`);
  assert(summary.unexpectedConsole.length === 0, `unexpected console messages observed: ${summary.unexpectedConsole.join('; ')}`);
})().catch(async (error) => {
  if (browser) {
    try {
      await browser.close();
    } catch (ignored) {
    }
  }
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
NODE

echo
echo "iPhone TeaVM smoke passed."
echo "Artifacts: $OUT_DIR"
