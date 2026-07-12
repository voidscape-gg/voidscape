#!/usr/bin/env bash
# Prove the launch cracker HUD through a real TeaVM 10132 login and server broadcasts.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
WEB_PORT=0
OUT_DIR="${WEB_TEA_CRACKER_HUD_OUT:-$ROOT/tmp/web-teavm-cracker-campaign-10132}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
HOST="127.0.0.1"
WS_PORT="43496"
WS_URL=""
AUTH_USER="${VOIDSCAPE_WEB_CRACKER_HUD_USER:-wbtest}"
AUTH_PASS="${VOIDSCAPE_WEB_CRACKER_HUD_PASS:-voidtest123}"
INITIAL_COUNT="1000"
ALLOW_MUTATION=0

usage() {
	cat <<'EOF'
Usage: scripts/smoke-web-teavm-cracker-campaign.sh [options]

Options:
  --no-build                 Reuse Web_Client_TeaVM/target/teavm.
  --web-port PORT            Local static-server port. Default: free port.
  --out DIR                  Evidence directory.
  --host HOST                WebSocket host. Default: 127.0.0.1.
  --ws-port PORT             WebSocket port. Default: 43496.
  --ws URL                   Full WebSocket URL, instead of host/port.
  --user USER                Isolated owner-character username.
  --pass PASS                Isolated owner-character password.
  --initial-count COUNT      Required login snapshot. Default: 1000.
  --chrome PATH              Chrome/Chromium executable.
  --playwright-core DIR      Directory containing playwright-core.
  --allow-campaign-mutation  Required safety switch: sends ::cracker against the target.
  -h, --help                 Show this help.

SAFETY: this test mutates the target cracker pool through the real owner command and
finishes at zero on success, with a best-effort zero command on failure. Run it only
against an isolated QA database whose initial pool has already been set to
--initial-count. It never starts, stops, or reconfigures a server.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build) BUILD=0; shift ;;
		--web-port) WEB_PORT="${2:-}"; shift 2 ;;
		--out) OUT_DIR="${2:-}"; shift 2 ;;
		--host) HOST="${2:-}"; shift 2 ;;
		--ws-port) WS_PORT="${2:-}"; shift 2 ;;
		--ws) WS_URL="${2:-}"; shift 2 ;;
		--user) AUTH_USER="${2:-}"; shift 2 ;;
		--pass) AUTH_PASS="${2:-}"; shift 2 ;;
		--initial-count) INITIAL_COUNT="${2:-}"; shift 2 ;;
		--chrome) CHROME_PATH="${2:-}"; shift 2 ;;
		--playwright-core) PLAYWRIGHT_CORE_DIR="${2:-}"; shift 2 ;;
		--allow-campaign-mutation) ALLOW_MUTATION=1; shift ;;
		-h|--help) usage; exit 0 ;;
		*) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

if [[ "$ALLOW_MUTATION" -ne 1 ]]; then
	echo "ERROR: --allow-campaign-mutation is required; use only an isolated QA database." >&2
	exit 2
fi
if [[ ! "$INITIAL_COUNT" =~ ^[0-9]+$ || "$INITIAL_COUNT" -lt 1 || "$INITIAL_COUNT" -gt 1000000 ]]; then
	echo "ERROR: --initial-count must be from 1 through 1000000." >&2
	exit 2
fi
if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
	echo "ERROR: --user and --pass must be non-empty." >&2
	exit 2
fi

if [[ "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ ! -f "$TARGET/index.html" || ! -f "$TARGET/voidscape-web-client.js" ]]; then
	echo "ERROR: missing TeaVM output; run scripts/build-web-teavm-spike.sh first." >&2
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
	echo "ERROR: playwright-core not found; pass --playwright-core DIR." >&2
	exit 1
fi

if [[ -z "$CHROME_PATH" ]]; then
	for candidate in \
		"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
		"/Applications/Chromium.app/Contents/MacOS/Chromium" \
		"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"; do
		if [[ -x "$candidate" ]]; then CHROME_PATH="$candidate"; break; fi
	done
fi
if [[ -z "$CHROME_PATH" || ! -x "$CHROME_PATH" ]]; then
	echo "ERROR: Chrome/Chromium not found; pass --chrome PATH." >&2
	exit 1
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
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
cleanup() {
	kill "$HTTP_PID" >/dev/null 2>&1 || true
	wait "$HTTP_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

python3 - "$WEB_PORT" <<'PY'
import sys, time, urllib.request
url = f"http://127.0.0.1:{sys.argv[1]}/index.html"
deadline = time.time() + 10
while time.time() < deadline:
    try:
        with urllib.request.urlopen(url, timeout=1) as response:
            if response.status == 200:
                raise SystemExit(0)
    except Exception:
        time.sleep(0.2)
raise SystemExit("static TeaVM server did not become ready")
PY

export PLAYWRIGHT_CORE_DIR CHROME_PATH OUT_DIR HOST WS_PORT WS_URL AUTH_USER AUTH_PASS INITIAL_COUNT WEB_PORT
node <<'NODE'
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_DIR);

const outDir = process.env.OUT_DIR;
const initialCount = Number(process.env.INITIAL_COUNT);
const user = process.env.AUTH_USER;
const pass = process.env.AUTH_PASS;
const host = process.env.HOST;
const wsPort = Number(process.env.WS_PORT);
const wsUrl = process.env.WS_URL || '';
const baseUrl = `http://127.0.0.1:${process.env.WEB_PORT}`;
let browser = null;
let page = null;
let serverPoolZeroObserved = false;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function expectedLabel(remaining) {
  if (remaining <= 0) return '';
  const formatted = remaining.toLocaleString('en-US');
  return `${formatted} ${remaining === 1 ? 'cracker' : 'crackers'} available`;
}

function assertHidden(state, label) {
  assert(state, `${label}: missing HUD diagnostics`);
  assert(state.remaining === 0, `${label}: expected remaining 0, got ${JSON.stringify(state)}`);
  assert(state.visible === false, `${label}: HUD should be hidden: ${JSON.stringify(state)}`);
  assert(state.label === '', `${label}: hidden label should be empty: ${JSON.stringify(state)}`);
  assert(state.chatLeakCount === 0, `${label}: hidden envelope leaked into chat: ${JSON.stringify(state)}`);
}

function assertPositive(state, remaining, label) {
  assert(state, `${label}: missing HUD diagnostics`);
  assert(state.clientVersion === 10132, `${label}: expected TeaVM client 10132: ${JSON.stringify(state)}`);
  assert(state.remaining === remaining, `${label}: expected ${remaining}: ${JSON.stringify(state)}`);
  assert(state.visible === true, `${label}: HUD should render: ${JSON.stringify(state)}`);
  assert(state.label === expectedLabel(remaining), `${label}: wrong grammar: ${JSON.stringify(state)}`);
  assert(state.chatLeakCount === 0, `${label}: hidden envelope leaked into chat: ${JSON.stringify(state)}`);
  const b = state.bounds || {};
  const f = state.frame || {};
  assert(b.x >= 0 && b.y >= 0 && b.width >= 132 && (b.height === 24 || b.height === 26),
    `${label}: invalid plaque geometry: ${JSON.stringify(state)}`);
  assert(b.x + b.width <= f.width && b.y + b.height <= f.height,
    `${label}: plaque is out of frame: ${JSON.stringify(state)}`);
}

async function diagnostics(page) {
  return await page.evaluate(() => window.__voidscapeCollectDiagnostics());
}

async function hud(page) {
  return await page.evaluate(() => ({ ...(window.__voidscapeCrackerCampaignHudState || {}) }));
}

async function captureCanvas(page, fileName) {
  const destination = path.join(outDir, fileName);
  await page.locator('canvas').screenshot({ path: destination });
  return destination;
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

async function waitForPlayable(page) {
  await page.waitForFunction(() => {
    const state = window.__voidscapeClientState;
    return !!state && state.hasLocalPlayer && state.viewMode === 'GAME' && window.__voidscapeInGame;
  }, null, { timeout: 30000 });
}

async function dismissBlockingDialog(page) {
  const attempts = [];
  for (let attempt = 0; attempt < 8; attempt++) {
    const snapshot = await diagnostics(page);
    const ui = snapshot.ui || {};
    if (!ui.blockingDialog && !String(snapshot.bodyClass || '').split(/\s+/).includes('dialog-open')) {
      return attempts;
    }
    const frameWidth = Math.max(512, Number(snapshot.client && snapshot.client.gameWidth) || 0);
    const frameHeight = Math.max(346, Number(snapshot.client && snapshot.client.gameHeight) || 0);
    const canvasWidth = Math.max(frameWidth, Number(snapshot.canvas && snapshot.canvas.width) || 0);
    const canvasHeight = Math.max(frameHeight, Number(snapshot.canvas && snapshot.canvas.height) || 0);
    const x = Math.round(canvasWidth / 2);
    const candidates = [];
    if (String(ui.blockingDialogName || '') === 'wildernessWarning') {
      candidates.push({ y: Math.round(canvasHeight / 2 + 68), reason: 'wilderness-center-button' });
      candidates.push({ y: Math.round(canvasHeight / 2 + 68), reason: 'wilderness-center-button-repeat' });
    } else if (String(ui.blockingDialogName || '') === 'welcome'
        || String(snapshot.bodyClass || '').split(/\s+/).includes('dialog-open')) {
      for (const dialogHeight of [135, 180]) {
        candidates.push({ y: Math.round((canvasHeight + dialogHeight) / 2 - 15), reason: `welcome-${dialogHeight}` });
      }
    }
    candidates.push({ y: Math.round(canvasHeight / 2 + 64), reason: 'center-lower' });
    candidates.push({ y: 505, reason: 'legacy' });
    const candidate = candidates[Math.min(attempt, candidates.length - 1)];
    const y = Math.max(8, Math.min(canvasHeight - 8, candidate.y));
    await clickFrame(page, x, y);
    attempts.push({ attempt: attempt + 1, name: ui.blockingDialogName || '', x, y, reason: candidate.reason });
    await page.waitForTimeout(450);
  }
  throw new Error(`unable to dismiss blocking dialog: ${JSON.stringify(await diagnostics(page))}`);
}

async function sendCrackerCount(page, remaining) {
  const beforeSequence = await page.evaluate(() =>
    Number((window.__voidscapeCrackerCampaignSmokeCommandResult || {}).sequence || 0));
  await page.evaluate((count) => {
    window.__voidscapeCrackerCampaignSmokeCommand = count;
  }, remaining);
  await page.waitForFunction((sequence) => {
    const result = window.__voidscapeCrackerCampaignSmokeCommandResult;
    return !!result && result.sequence > sequence;
  }, beforeSequence, { timeout: 5000 });
  const commandResult = await page.evaluate(() => ({ ...window.__voidscapeCrackerCampaignSmokeCommandResult }));
  assert(commandResult.sent && commandResult.remaining === remaining,
    `client did not send cracker ${remaining}: ${JSON.stringify(commandResult)}`);
  await page.waitForFunction((count) => {
    const state = window.__voidscapeCrackerCampaignHudState;
    return !!state && state.remaining === count && state.visible === (count > 0)
      && state.label === (count > 0
        ? `${count.toLocaleString('en-US')} ${count === 1 ? 'cracker' : 'crackers'} available`
        : '');
  }, remaining, { timeout: 15000 });
  return { commandResult, state: await hud(page) };
}

async function injectCompiledEnvelope(page, envelope, expectedRemaining) {
  const beforeSequence = await page.evaluate(() =>
    Number((window.__voidscapeCrackerCampaignSmokeInjection || {}).sequence || 0));
  await page.evaluate((value) => {
    window.__voidscapeCrackerCampaignSmokeEnvelope = value;
  }, envelope);
  await page.waitForFunction((sequence) => {
    const result = window.__voidscapeCrackerCampaignSmokeInjection;
    return !!result && result.sequence > sequence;
  }, beforeSequence, { timeout: 5000 });
  await page.waitForFunction((remaining) => {
    const state = window.__voidscapeCrackerCampaignHudState;
    return !!state && state.remaining === remaining && state.visible === (remaining > 0)
      && state.label === (remaining > 0
        ? `${remaining.toLocaleString('en-US')} ${remaining === 1 ? 'cracker' : 'crackers'} available`
        : '');
  }, expectedRemaining, { timeout: 5000 });
  const injection = await page.evaluate(() => ({ ...window.__voidscapeCrackerCampaignSmokeInjection }));
  assert(injection.consumed === true,
    `recognized campaign envelope should be consumed: ${JSON.stringify({ envelope, injection })}`);
  const state = await hud(page);
  return { envelope, injection, state };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH, headless: true });
  const context = await browser.newContext({
    viewport: { width: 844, height: 390 },
    deviceScaleFactor: 1,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1'
  });
  page = await context.newPage();
  const consoleMessages = [];
  const failedRequests = [];
  page.on('console', (msg) => consoleMessages.push(`${msg.type()}: ${msg.text()}`));
  page.on('requestfailed', (request) => failedRequests.push({
    method: request.method(), url: request.url(), error: request.failure() && request.failure().errorText
  }));

  const query = new URLSearchParams({ mobile: '1', diag: '1', endpoint: 'reset' });
  if (wsUrl) query.set('ws', wsUrl);
  else { query.set('host', host); query.set('port', String(wsPort)); }
  const loginUrl = `${baseUrl}/index.html?${query.toString()}`;
  await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('canvas', { timeout: 30000 });
  await page.waitForFunction(() => typeof window.__voidscapeCollectDiagnostics === 'function'
    && !!window.__voidscapeEndpoint && !!window.__voidscapeDiagnosticsEnabled, null, { timeout: 30000 });
  await page.evaluate(({ user, pass }) => {
    window.__voidscapeSmokeLoginRequest = { user, pass };
  }, { user, pass });
  await page.waitForFunction(() => document.title.startsWith('Voidscape -- '), null, { timeout: 30000 });
  await waitForPlayable(page);
  // World entry can precede publication of the server-owned welcome modal by a
  // few browser frames. Let that state settle before deciding there is nothing
  // to close, then use the same computed mobile coordinates as the main iPhone smoke.
  await page.waitForTimeout(1200);
  const dismiss = await dismissBlockingDialog(page);
  await page.waitForFunction(() => {
    const ui = window.__voidscapeUiState;
    return !!ui && !ui.blockingDialog && !document.body.classList.contains('dialog-open');
  }, null, { timeout: 10000 });

  // Network proof: no local envelope injection has occurred here. The first state
  // is the login snapshot; subsequent states are real owner command broadcasts.
  const networkInjectionBaseline = await page.evaluate(() =>
    Number((window.__voidscapeCrackerCampaignSmokeInjection || {}).sequence || 0));
  assert(networkInjectionBaseline === 0,
    `local envelope injection occurred before network proof: ${networkInjectionBaseline}`);
  await page.waitForFunction((count) => {
    const state = window.__voidscapeCrackerCampaignHudState;
    return !!state && state.clientVersion === 10132 && state.remaining === count && state.visible;
  }, initialCount, { timeout: 15000 });
  const loginSnapshot = await hud(page);
  assertPositive(loginSnapshot, initialCount, 'server login snapshot');
  const loginCapture = await captureCanvas(page, `network-login-${initialCount}.png`);

  const networkProofs = [{ source: 'login-snapshot', remaining: initialCount, state: loginSnapshot, capture: loginCapture }];
  for (const remaining of [999, 1, 0]) {
    const proof = await sendCrackerCount(page, remaining);
    if (remaining > 0) assertPositive(proof.state, remaining, `server broadcast ${remaining}`);
    else assertHidden(proof.state, 'server broadcast zero');
    proof.source = 'owner-command-broadcast';
    proof.remaining = remaining;
    proof.capture = await captureCanvas(page, `network-broadcast-${remaining}.png`);
    networkProofs.push(proof);
    if (remaining === 0) serverPoolZeroObserved = true;
  }
  const networkInjectionFinal = await page.evaluate(() =>
    Number((window.__voidscapeCrackerCampaignSmokeInjection || {}).sequence || 0));
  assert(networkInjectionFinal === networkInjectionBaseline,
    `local envelope injection occurred during network proof: ${JSON.stringify({ networkInjectionBaseline, networkInjectionFinal })}`);

  // Parser proof is explicitly separate from the network proof above. It drives
  // the package-private parser inside the compiled TeaVM program via a diag-only bridge.
  const malformedProofs = [];
  for (const envelope of [
    '@vscrackercampaign@v2|999',
    '@vscrackercampaign@v1|-1',
    '@vscrackercampaign@v1|1000001',
    '@vscrackercampaign@v1|abc',
    '@vscrackercampaign@v1|1|extra'
  ]) {
    const restored = await injectCompiledEnvelope(page, '@vscrackercampaign@v1|1', 1);
    assertPositive(restored.state, 1, `pre-malformed restore ${envelope}`);
    const malformed = await injectCompiledEnvelope(page, envelope, 0);
    assertHidden(malformed.state, `malformed ${envelope}`);
    malformedProofs.push({ restored, malformed });
  }
  const malformedCapture = await captureCanvas(page, 'local-malformed-hidden.png');

  const finalDiagnostics = await diagnostics(page);
  assert((finalDiagnostics.recentErrors || []).length === 0,
    `TeaVM diagnostics errors: ${JSON.stringify(finalDiagnostics.recentErrors)}`);
  const unexpectedFailures = failedRequests.filter((failure) =>
    !/net::ERR_ABORTED/.test(String(failure.error || '')));
  assert(unexpectedFailures.length === 0,
    `unexpected browser request failures: ${JSON.stringify(unexpectedFailures)}`);

  const summary = {
    ok: true,
    clientVersion: loginSnapshot.clientVersion,
    baseUrl,
    websocket: wsUrl || `ws://${host}:${wsPort}`,
    user,
    initialCount,
    dismiss,
    networkProof: {
      observedLocalEnvelopeInjectionSequence: {
        baseline: networkInjectionBaseline,
        final: networkInjectionFinal
      },
      states: networkProofs
    },
    localCompiledParserProof: {
      note: 'Diagnostics-only injection after the real network proofs',
      malformed: malformedProofs,
      capture: malformedCapture
    },
    cleanup: {
      requestedServerRemaining: 0,
      state: networkProofs[networkProofs.length - 1].state,
      commandResult: networkProofs[networkProofs.length - 1].commandResult,
      note: 'The last real server mutation happened before diagnostics-only parser proof.'
    },
    finalDiagnostics: {
      runtimeMode: finalDiagnostics.runtimeMode,
      endpoint: finalDiagnostics.endpoint,
      effectiveWs: finalDiagnostics.effectiveWs,
      crackerCampaignHud: finalDiagnostics.crackerCampaignHud,
      recentErrors: finalDiagnostics.recentErrors
    },
    failedRequests: unexpectedFailures,
    consoleTail: consoleMessages.slice(-24)
  };
  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  await browser.close();
  browser = null;
  console.log(JSON.stringify(summary, null, 2));
})().catch(async (error) => {
  const cleanup = { attempted: false, passed: serverPoolZeroObserved, error: '' };
  if (page && !serverPoolZeroObserved) {
    cleanup.attempted = true;
    try {
      const proof = await sendCrackerCount(page, 0);
      assertHidden(proof.state, 'best-effort failure cleanup zero');
      cleanup.passed = true;
      cleanup.commandResult = proof.commandResult;
      cleanup.state = proof.state;
    } catch (cleanupError) {
      cleanup.error = String(cleanupError && cleanupError.stack || cleanupError);
    }
  }
  try {
    if (page) await page.screenshot({ path: path.join(outDir, 'failure.png'), fullPage: true });
  } catch (ignored) {}
  try {
    if (browser) await browser.close();
  } catch (ignored) {}
  const failure = { ok: false, error: String(error && error.stack || error), cleanup };
  fs.writeFileSync(path.join(outDir, 'failure.json'), JSON.stringify(failure, null, 2));
  fs.writeFileSync(path.join(outDir, 'failure.txt'), `${failure.error}\n\ncleanup=${JSON.stringify(cleanup, null, 2)}\n`);
  throw error;
});
NODE

echo "TeaVM 10132 cracker campaign HUD smoke passed: $OUT_DIR/summary.json"
