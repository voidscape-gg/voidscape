#!/usr/bin/env bash
# Capture real TeaVM web-client menu screenshots for visual QA.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=0
WEB_PORT=0
OUT_DIR="${WEB_TEA_MENU_SCREENSHOT_OUT:-$ROOT/tmp/web-teavm-menu-screenshots}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
BASE_URL_OVERRIDE=""
STATIC_DIR="${WEB_TEA_MENU_STATIC_DIR:-}"
HOST="127.0.0.1"
WS_PORT="43496"
WS_URL=""
AUTH_USER="${VOIDSCAPE_WEB_SMOKE_USER:-test}"
AUTH_PASS="${VOIDSCAPE_WEB_SMOKE_PASS:-test}"
PORTAL_URL="${WEB_TEA_SMOKE_PORTAL_URL:-https://voidscape.gg/}"
IGNORE_HTTPS_ERRORS=0

usage() {
	cat <<'EOF'
Usage: scripts/screenshot-web-teavm-menus.sh [options]

Options:
  --build                 Build TeaVM output before serving local files.
  --no-build              Reuse existing local/static files. Default.
  --base-url URL          Use a deployed web client instead of local static files.
  --static-dir DIR        Serve an existing static package. Default: Web_Client_TeaVM/target/teavm.
  --web-port PORT         Local static-server port. Default: choose a free port.
  --host HOST             WebSocket host passed to the client in local mode. Default: 127.0.0.1.
  --ws-port PORT          WebSocket port passed to the client in local mode. Default: 43496.
  --ws URL                Full WebSocket URL passed as ?ws=.
  --user USER             Login username. Default: test.
  --pass PASS             Login password. Default: test.
  --portal URL            Portal base URL for mobile launch params. Default: https://voidscape.gg/.
  --out DIR               Output directory for screenshots/report.
  --chrome PATH           Chrome/Chromium executable path.
  --playwright-core DIR   Directory for playwright-core package.
  --ignore-https-errors   Ignore HTTPS certificate errors. Use only for local smoke.
  -h, --help              Show this help.

This logs in through the diagnostics-only web smoke hook and screenshots the
real shared Java HUD panels/chat tabs at phone portrait, phone landscape, and
desktop sizes. It does not use a separate renderer.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--build)
			BUILD=1
			shift
			;;
		--no-build)
			BUILD=0
			shift
			;;
		--base-url)
			BASE_URL_OVERRIDE="${2:-}"
			BUILD=0
			shift 2
			;;
		--static-dir)
			STATIC_DIR="${2:-}"
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
fi

if [[ "$REMOTE_MODE" -eq 0 && "$BUILD" -eq 1 ]]; then
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

if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" || -z "$PORTAL_URL" ]]; then
	echo "ERROR: user, pass, and portal must be non-empty." >&2
	exit 2
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

export PLAYWRIGHT_CORE_DIR CHROME_PATH OUT_DIR HOST WS_PORT WS_URL AUTH_USER AUTH_PASS REMOTE_MODE IGNORE_HTTPS_ERRORS PORTAL_URL
if [[ "$REMOTE_MODE" -eq 1 ]]; then
	export BASE_URL="$BASE_URL_OVERRIDE"
else
	export BASE_URL="http://127.0.0.1:$WEB_PORT"
fi

node <<'NODE'
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_DIR);

const baseUrl = process.env.BASE_URL;
const host = process.env.HOST;
const wsPort = Number(process.env.WS_PORT);
const explicitWs = process.env.WS_URL || '';
const remoteMode = process.env.REMOTE_MODE === '1';
const user = process.env.AUTH_USER;
const pass = process.env.AUTH_PASS;
const portalUrl = process.env.PORTAL_URL;
const outDir = process.env.OUT_DIR;
const chromePath = process.env.CHROME_PATH;
const ignoreHttpsErrors = process.env.IGNORE_HTTPS_ERRORS === '1';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function pageUrl(pathValue) {
  return new URL(pathValue, baseUrl).href;
}

function launchPath(mode) {
  const query = new URLSearchParams();
  if (mode.profile === 'desktop') {
    query.set('desktop', '1');
  } else if (mode.profile === 'tablet') {
    query.set('tablet', '1');
    query.set('portal', portalUrl);
  } else {
    query.set('mobile', '1');
    query.set('portal', portalUrl);
  }
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

function safeFileName(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'shot';
}

async function waitForClient(page) {
  await page.waitForSelector('canvas', { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeEndpoint, null, { timeout: 30000 });
  await page.waitForFunction(() => typeof window.__voidscapeCollectDiagnostics === 'function', null, { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeInputInstalled && Array.isArray(window.__voidscapeInputQueue), null, { timeout: 30000 });
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

async function waitForPlayableClientState(page, timeout = 15000) {
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
        reason: 'wilderness-lower-button'
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
  throw new Error(`${label} dialog did not close: ${JSON.stringify({ attempts, ui: snapshot.ui, client: snapshot.client, canvas: snapshot.canvas })}`);
}

async function hideDiagnosticsChrome(page) {
  await page.addStyleTag({
    content: `
      #diagnostics-button,
      #diagnostics-copy-button,
      #diagnostics-panel,
      #diagnostics-export {
        display: none !important;
        visibility: hidden !important;
        pointer-events: none !important;
      }
    `
  });
}

async function performLogin(page, loginUrl, consoleMessages, label) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await waitForClient(page);
    await page.evaluate(({ user, pass }) => {
      window.__voidscapeSmokeLoginRequest = { user, pass };
    }, { user, pass });
    const loggedIn = await page.waitForFunction(() => document.title.startsWith('Voidscape -- '), null, { timeout: 25000 })
      .then(() => true)
      .catch(() => false);
    if (loggedIn) {
      const snapshot = await diagnosticsSnapshot(page);
      const state = await waitForPlayableClientState(page, 15000);
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
    if (!busy || attempt === 3) {
      const snapshot = await diagnosticsSnapshot(page).catch(() => null);
      throw new Error(`${label} login failed: ${JSON.stringify({ attempt, title: await page.title(), snapshot, consoleTail: consoleMessages.slice(-16) })}`);
    }
    await page.waitForTimeout(65000);
  }
  throw new Error(`${label} login failed without result`);
}

async function waitForUi(page, predicateSource, label, timeout = 6000) {
  await page.waitForFunction(new Function(`return (${predicateSource});`)(), null, { timeout }).catch(async (error) => {
    const snapshot = await diagnosticsSnapshot(page).catch(() => null);
    throw new Error(`${label}: timed out waiting for UI state: ${JSON.stringify(snapshot && snapshot.ui)}; ${error.message}`);
  });
}

async function settle(page) {
  await page.waitForTimeout(700);
}

async function queuePanel(page, key, expectedTab, label, extraWait = 0, dialogName = '') {
  await page.evaluate((key) => {
    window.__voidscapeInputQueue.push(`u,${key}`);
  }, key);
  if (dialogName) {
    await waitForUi(page, `() => {
      const ui = window.__voidscapeUiState;
      return !!ui && window.__voidscapeInGame && ui.showUiTab === ${expectedTab}
        && ui.blockingDialog && ui.blockingDialogName === '${dialogName}';
    }`, label);
  } else if (typeof expectedTab === 'number') {
    await waitForUi(page, `() => {
      const ui = window.__voidscapeUiState;
      return !!ui && window.__voidscapeInGame && !ui.blockingDialog && ui.showUiTab === ${expectedTab};
    }`, label);
  } else {
    await settle(page);
  }
  if (extraWait > 0) await page.waitForTimeout(extraWait);
  await settle(page);
}

async function queueChat(page, key, expectedTab, label) {
  await queuePanel(page, 'hud', 0, `${label} cleanup`);
  await page.evaluate((key) => {
    window.__voidscapeInputQueue.push(`c,${key}`);
  }, key);
  await waitForUi(page, `() => {
    const ui = window.__voidscapeUiState;
    return !!ui && window.__voidscapeInGame && !ui.blockingDialog && ui.messageTab === '${expectedTab}';
  }`, label);
  await settle(page);
}

async function capture(page, modeDir, order, label) {
  const file = path.join(modeDir, `${String(order).padStart(2, '0')}-${safeFileName(label)}.png`);
  await page.screenshot({ path: file, fullPage: false });
  const snapshot = await diagnosticsSnapshot(page);
  return {
    order,
    label,
    file,
    relFile: path.relative(outDir, file),
    ui: snapshot.ui,
    canvas: snapshot.canvas,
    controls: snapshot.controls,
    runtimeMode: snapshot.runtimeMode
  };
}

function panelTargets(mode) {
  const base = [
    { key: 'hud', label: 'HUD', tab: 0 },
    { key: 'inventory', label: 'Inventory', tab: 1 },
    { key: 'equipment', label: 'Equipment', tab: 1 },
    { key: 'map', label: 'Map', tab: 2 },
    { key: 'skills', label: 'Skills', tab: 3 },
    { key: 'quests', label: 'Quests', tab: 3 },
    { key: 'loot', label: 'Loot', tab: 3, extraWait: 1200 },
    { key: 'bestiary', label: 'Bestiary', tab: 3, extraWait: 1600 },
    { key: 'magic', label: 'Magic', tab: 4 },
    { key: 'prayer', label: 'Prayer', tab: 4 },
    { key: 'friends', label: 'Friends', tab: 5 },
    { key: 'ignore', label: 'Ignore', tab: 5 },
    { key: 'options', label: 'Options', tab: 6 },
    { key: 'settings', label: 'Advanced Settings', tab: 6, dialog: 'advancedSettings', extraWait: 800 },
    { key: 'account', label: 'Account Manager', tab: null, extraWait: 800 }
  ];
  if (mode.profile !== 'phone' || mode.width >= mode.height) return base;
  return [
    { key: 'hud', label: 'HUD', tab: 0 },
    { key: 'side-inventory', label: 'Side Inv Button', tab: 1, sideSelector: '#inventory-button' },
    { key: 'side-magic', label: 'Side Mag Button', tab: 4, sideSelector: '#magic-button' },
    { key: 'side-prayer', label: 'Side Pray Button', tab: 4, sideSelector: '#prayer-button' },
    ...base.slice(1)
  ];
}

const chatTargets = [
  { key: 'all', label: 'Chat All', tab: 'ALL' },
  { key: 'chat', label: 'Chat', tab: 'CHAT' },
  { key: 'quest', label: 'Quest Chat', tab: 'QUEST' },
  { key: 'private', label: 'Private Chat', tab: 'PRIVATE' }
];

async function captureMode(browser, mode) {
  const modeDir = path.join(outDir, mode.name);
  fs.mkdirSync(modeDir, { recursive: true });
  const context = await browser.newContext({
    viewport: { width: mode.width, height: mode.height },
    deviceScaleFactor: mode.deviceScaleFactor,
    isMobile: mode.profile === 'phone' || mode.profile === 'tablet',
    hasTouch: mode.profile === 'phone' || mode.profile === 'tablet',
    userAgent: mode.userAgent,
    ignoreHTTPSErrors: ignoreHttpsErrors
  });
  const page = await context.newPage();
  const consoleMessages = [];
  const failedRequests = [];
  page.on('console', (message) => consoleMessages.push(`${message.type()}: ${message.text()}`));
  page.on('requestfailed', (request) => {
    failedRequests.push(`${request.method()} ${request.url()} ${request.failure() ? request.failure().errorText : 'failed'}`);
  });

  const loginUrl = pageUrl(launchPath(mode));
  const login = await performLogin(page, loginUrl, consoleMessages, mode.name);
  const dismiss = await dismissInitialBlockingDialog(page, mode.name);
  await hideDiagnosticsChrome(page);
  await queuePanel(page, 'hud', 0, `${mode.name} HUD cleanup`);

  const screenshots = [];
  let order = 0;
  for (const target of panelTargets(mode)) {
    if (target.sideSelector) {
      await queuePanel(page, 'hud', 0, `${mode.name} side cleanup`);
      await page.locator(target.sideSelector).tap({ timeout: 5000 });
      await waitForUi(page, `() => {
        const ui = window.__voidscapeUiState;
        return !!ui && window.__voidscapeInGame && !ui.blockingDialog && ui.showUiTab === ${target.tab};
      }`, `${mode.name} ${target.label}`);
      await settle(page);
    } else {
      await queuePanel(page, target.key, target.tab, `${mode.name} ${target.label}`, target.extraWait || 0, target.dialog || '');
    }
    screenshots.push(await capture(page, modeDir, order++, target.label));
  }
  for (const target of chatTargets) {
    await queueChat(page, target.key, target.tab, `${mode.name} ${target.label}`);
    screenshots.push(await capture(page, modeDir, order++, target.label));
  }

  const finalSnapshot = await diagnosticsSnapshot(page);
  await context.close();
  const ignoredFailures = failedRequests.filter((failure) => /top-tab-normal\.png.*net::ERR_ABORTED/.test(failure));
  return {
    mode: mode.name,
    loginUrl,
    viewport: { width: mode.width, height: mode.height },
    login,
    dismiss,
    screenshots,
    final: {
      runtimeMode: finalSnapshot.runtimeMode,
      canvas: finalSnapshot.canvas,
      ui: finalSnapshot.ui,
      controls: finalSnapshot.controls,
      recentErrors: finalSnapshot.recentErrors || []
    },
    failedRequests: failedRequests.filter((failure) => !ignoredFailures.includes(failure)),
    ignoredFailures,
    consoleTail: consoleMessages.slice(-20)
  };
}

function renderContactHtml(modeResult) {
  const cards = modeResult.screenshots.map((shot) => {
    const src = shot.relFile.split(path.sep).map(encodeURIComponent).join('/');
    return `<figure><img src="${src}" alt="${shot.label}"><figcaption>${String(shot.order).padStart(2, '0')} ${shot.label}</figcaption></figure>`;
  }).join('\n');
  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>${modeResult.mode} Voidscape menu screenshots</title>
<style>
body { margin: 0; padding: 18px; background: #111; color: #eee; font: 14px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
h1 { margin: 0 0 12px; font-size: 20px; font-weight: 650; }
.grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
figure { margin: 0; background: #1c1c1f; border: 1px solid #333; padding: 8px; }
img { display: block; width: 100%; height: auto; background: #000; }
figcaption { margin-top: 7px; color: #ddd; }
</style>
</head>
<body>
<h1>${modeResult.mode} (${modeResult.viewport.width}x${modeResult.viewport.height})</h1>
<div class="grid">${cards}</div>
</body>
</html>`;
}

async function captureContactSheets(browser, results) {
  const reportPaths = [];
  const context = await browser.newContext({ viewport: { width: 1800, height: 1400 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  for (const result of results) {
    const htmlPath = path.join(outDir, `contact-${result.mode}.html`);
    const pngPath = path.join(outDir, `contact-${result.mode}.png`);
    fs.writeFileSync(htmlPath, renderContactHtml(result));
    await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'load' });
    await page.screenshot({ path: pngPath, fullPage: true });
    reportPaths.push({ mode: result.mode, html: htmlPath, png: pngPath });
  }
  await context.close();
  return reportPaths;
}

(async () => {
  const browser = await chromium.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  });

  const modes = [
    {
      name: 'mobile-portrait',
      profile: 'phone',
      width: 390,
      height: 664,
      deviceScaleFactor: 3,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
    },
    {
      name: 'mobile-landscape',
      profile: 'phone',
      width: 932,
      height: 430,
      deviceScaleFactor: 3,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
    },
    {
      name: 'tablet',
      profile: 'tablet',
      width: 1024,
      height: 768,
      deviceScaleFactor: 2,
      userAgent: 'Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
    },
    {
      name: 'desktop',
      profile: 'desktop',
      width: 1280,
      height: 800,
      deviceScaleFactor: 1,
      userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
    }
  ];

  const results = [];
  for (const mode of modes) {
    results.push(await captureMode(browser, mode));
    await new Promise((resolve) => setTimeout(resolve, 7000));
  }
  const contacts = await captureContactSheets(browser, results);
  await browser.close();

  const summary = {
    baseUrl,
    remoteMode,
    user,
    results,
    contacts,
    generatedAt: new Date().toISOString()
  };
  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify({
    baseUrl,
    remoteMode,
    modes: results.map((result) => ({
      mode: result.mode,
      viewport: result.viewport,
      screenshots: result.screenshots.length,
      runtimeMode: result.final.runtimeMode,
      recentErrors: result.final.recentErrors,
      failedRequests: result.failedRequests
    })),
    contacts,
    summary: path.join(outDir, 'summary.json')
  }, null, 2));
  for (const result of results) {
    assert(result.failedRequests.length === 0, `${result.mode}: unexpected request failures: ${result.failedRequests.join('; ')}`);
    assert((result.final.recentErrors || []).length === 0, `${result.mode}: diagnostics errors: ${result.final.recentErrors.join('; ')}`);
  }
})().catch(async (error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
NODE
