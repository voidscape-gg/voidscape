#!/usr/bin/env bash
# Smoke-test the TeaVM iPhone input bridge without requiring a game login.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
WEB_PORT=0
OUT_DIR="${WEB_TEA_CONTROLS_SMOKE_OUT:-$ROOT/tmp/web-teavm-controls-smoke}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
ONLY_WORLD_MAP_INPUT=0

usage() {
	cat <<'EOF'
Usage: scripts/smoke-web-teavm-iphone-controls.sh [options]

Options:
  --no-build              Reuse existing Web_Client_TeaVM/target/teavm output.
  --web-port PORT         Local static-server port. Default: choose a free port.
  --out DIR               Output directory for screenshots/logs. Default: tmp/web-teavm-controls-smoke.
  --chrome PATH           Chrome/Chromium executable path.
  --playwright-core DIR   Directory for playwright-core package.
  --only-world-map-input  Run only the synthetic world-map touch routing proof.
  -h, --help              Show this help.

This is a synthetic iPhone control regression smoke. It loads the TeaVM web
client, waits for the real browser input bridge, and asserts keyboard, touch,
long-press, camera, pinch, scroll, context, Back, viewport-freeze, and
diagnostics behavior.
It does not require a running game server.
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
		--chrome)
			CHROME_PATH="${2:-}"
			shift 2
			;;
			--playwright-core)
				PLAYWRIGHT_CORE_DIR="${2:-}"
				shift 2
				;;
			--only-world-map-input)
				ONLY_WORLD_MAP_INPUT=1
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

if [[ "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ ! -f "$TARGET/index.html" ]]; then
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
	if [[ "${HTTP_PID:-}" =~ ^[0-9]+$ ]]; then
		kill "$HTTP_PID" >/dev/null 2>&1 || true
		wait "$HTTP_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

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

export PLAYWRIGHT_CORE_DIR CHROME_PATH OUT_DIR ONLY_WORLD_MAP_INPUT
export BASE_URL="http://127.0.0.1:$WEB_PORT"

node <<'NODE'
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_DIR);

const baseUrl = process.env.BASE_URL;
const outDir = process.env.OUT_DIR;
const chromePath = process.env.CHROME_PATH;
const onlyWorldMapInput = process.env.ONLY_WORLD_MAP_INPUT === '1';
const QUICK_PANEL_SHORTCUTS = ['inventory', 'magic', 'prayer'];
const QUICK_PANEL_EVENTS = {
  inventory: 'side-inventory',
  magic: 'side-magic',
  prayer: 'side-prayer'
};

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function assertIncludes(events, expected, label) {
  assert(events.includes(expected), `${label}: expected ${expected}, got ${JSON.stringify(events)}`);
}

function assertNoMatch(events, pattern, label) {
  assert(!events.some((event) => pattern.test(event)), `${label}: unexpected event in ${JSON.stringify(events)}`);
}

function textFromEvents(events) {
  return events
    .filter((event) => /^t,\d+$/.test(event))
    .map((event) => String.fromCharCode(Number(event.slice(2))))
    .join('');
}

function assertTextEvents(events, expected, label) {
  const actual = textFromEvents(events);
  assert(actual === expected, `${label}: expected text ${JSON.stringify(expected)}, got ${JSON.stringify(actual)} from ${JSON.stringify(events)}`);
}

async function drain(page) {
  await page.waitForTimeout(180);
  return await page.evaluate(() => {
    const events = (window.__voidscapeInputTranscript || []).slice();
    if (window.__voidscapeInputTranscript) window.__voidscapeInputTranscript.length = 0;
    if (window.__voidscapeInputQueue) window.__voidscapeInputQueue.length = 0;
    return events;
  });
}

async function installInputRecorder(page) {
  await page.evaluate(() => {
    const queue = window.__voidscapeInputQueue;
    if (!queue || queue.__voidscapeRecording) return;
    window.__voidscapeInputTranscript = [];
    const originalPush = queue.push;
    queue.push = function(...items) {
      window.__voidscapeInputTranscript.push(...items);
      return originalPush.apply(this, items);
    };
    queue.__voidscapeRecording = true;
  });
}

async function resetState(page) {
  await page.evaluate(() => {
    window.__voidscapeInputQueue = window.__voidscapeInputQueue || [];
    window.__voidscapeInputQueue.length = 0;
    window.__voidscapeInGame = true;
    window.__voidscapeKeyboardWanted = false;
    window.__voidscapeKeyboardOpen = false;
    window.__voidscapeKeyboardClosingUntil = 0;
	    window.__voidscapeViewportKeyboardFrozen = false;
	    window.__voidscapeViewportKeyboardClosingFrozen = false;
	    window.__voidscapeNextTapSecondary = false;
	    window.__voidscapePanelDrawerOpen = false;
	    window.__voidscapeChatTrayOpen = false;
	    window.__voidscapeTopMenuVisible = false;
    window.__voidscapeLongPressMillis = 250;
    window.__voidscapeScrollableUiActive = false;
    window.__voidscapeMessageScrollActive = false;
    window.__voidscapeWorldMapTouchActive = false;
    window.__voidscapeWorldMapState = {
      visible: false
    };
    document.body.classList.add('game-drawn', 'in-game', 'android-parity-canvas-panels');
		    document.body.classList.remove('keyboard-open', 'context-armed', 'dialog-open', 'panel-drawer-open', 'chat-tray-open', 'canvas-panel-rail-visible', 'canvas-panel-dock-visible', 'chat-helper-visible', 'world-map-open', 'shared-panel-open');
    window.__voidscapeUiState = {
      customUi: true,
      androidProfile: true,
      webBuild: true,
      showUiTab: 0,
	      messageTab: 'ALL',
	      chatPanelHidden: false,
	      phoneLandscapeLooseChat: false,
	      mobileLooseChat: true,
	      chatAccessMode: 'canvas',
      mobilePanelShell: false,
      canvasTopTabsVisible: true,
      canvasPanelRailVisible: false,
      canvasPanelDockVisible: false,
      panelAccessMode: 'canvas',
      blockingDialog: false,
      blockingDialogName: ''
    };
    const canvas = document.getElementById('game');
    canvas.width = 512;
    canvas.height = 872;
    document.documentElement.style.setProperty('--game-aspect', '512 / 872');
    document.documentElement.style.setProperty('--game-aspect-ratio', String(512 / 872));
    canvas.style.aspectRatio = '512 / 872';
    canvas.style.width = '';
    canvas.style.height = '';
    try {
      canvas.focus({ preventScroll: true });
    } catch (error) {
      canvas.focus();
    }
  });
}

async function readControlLayout(page, viewport) {
  await page.setViewportSize(viewport);
  await page.evaluate(() => {
    if (window.__voidscapeUpdateViewportHeight) window.__voidscapeUpdateViewportHeight();
  });
  await page.waitForTimeout(180);
  return await page.evaluate((viewport) => {
    const rect = (selector) => {
      const element = document.querySelector(selector);
      if (!element) {
        return {
          selector,
          present: false,
          display: 'missing',
          text: '',
          backgroundImage: '',
          backgroundColor: '',
          fontSize: '',
          left: 0,
          top: 0,
          right: 0,
          bottom: 0,
          width: 0,
          height: 0
        };
      }
      const style = getComputedStyle(element);
      const box = element.getBoundingClientRect();
      return {
        selector,
        present: true,
        display: style.display,
        text: element.textContent.trim(),
        backgroundImage: style.backgroundImage,
        backgroundColor: style.backgroundColor,
        fontSize: style.fontSize,
        left: Math.round(box.left),
        top: Math.round(box.top),
        right: Math.round(box.right),
        bottom: Math.round(box.bottom),
        width: Math.round(box.width),
        height: Math.round(box.height)
      };
    };
    const cameraButtons = Array.from(document.querySelectorAll('.camera-control-button')).map((element) => {
      const box = element.getBoundingClientRect();
      return {
        label: element.getAttribute('aria-label') || element.textContent || '',
        display: getComputedStyle(element).display,
        left: Math.round(box.left),
        top: Math.round(box.top),
        right: Math.round(box.right),
        bottom: Math.round(box.bottom),
        width: Math.round(box.width),
        height: Math.round(box.height)
      };
    });
    return {
      viewport,
	      keyboard: rect('#keyboard-button'),
	      inventory: rect('#inventory-button'),
	      magic: rect('#magic-button'),
	      prayer: rect('#prayer-button'),
	      bestiary: rect('#bestiary-button'),
	      panel: rect('#panel-button'),
	      panelDrawer: rect('#mobile-panel-drawer'),
	      chat: rect('#chat-button'),
	      chatTray: rect('#mobile-chat-tray'),
	      camera: rect('#camera-controls'),
	      cameraButtons,
      quickButtons: Array.from(document.querySelectorAll('.mobile-quick-button')).map((element) => {
        const box = element.getBoundingClientRect();
        return {
          panel: element.getAttribute('data-panel') || '',
          selector: `#${element.id}`,
          display: getComputedStyle(element).display,
          left: Math.round(box.left),
          top: Math.round(box.top),
          right: Math.round(box.right),
          bottom: Math.round(box.bottom),
          width: Math.round(box.width),
          height: Math.round(box.height)
        };
      }),
      panelButtons: Array.from(document.querySelectorAll('.mobile-panel-button')).map((element) => {
        const box = element.getBoundingClientRect();
        return {
          panel: element.getAttribute('data-panel') || '',
          label: element.getAttribute('aria-label') || element.textContent || '',
          display: getComputedStyle(element).display,
          left: Math.round(box.left),
          top: Math.round(box.top),
          right: Math.round(box.right),
          bottom: Math.round(box.bottom),
          width: Math.round(box.width),
          height: Math.round(box.height)
        };
	      }),
	      chatButtons: Array.from(document.querySelectorAll('.mobile-chat-button')).map((element) => {
	        const box = element.getBoundingClientRect();
	        return {
	          chat: element.getAttribute('data-chat') || '',
	          label: element.getAttribute('aria-label') || element.textContent || '',
	          display: getComputedStyle(element).display,
	          left: Math.round(box.left),
	          top: Math.round(box.top),
	          right: Math.round(box.right),
	          bottom: Math.round(box.bottom),
	          width: Math.round(box.width),
	          height: Math.round(box.height)
	        };
	      }),
	      diagnostics: rect('#diagnostics-button'),
      diagnosticsCopy: rect('#diagnostics-copy-button'),
      canvas: rect('#game'),
      viewportHeightStyle: getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim()
    };
  }, viewport);
}

function assertInsideViewport(box, viewport, label) {
  assert(box.left >= 0 && box.top >= 0 && box.right <= viewport.width && box.bottom <= viewport.height,
    `${label} should fit inside viewport ${JSON.stringify(viewport)}, got ${JSON.stringify(box)}`);
}

function assertNoOverlap(a, b, label) {
  const overlaps = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
  assert(!overlaps, `${label} should not overlap: ${JSON.stringify(a)} vs ${JSON.stringify(b)}`);
}

function assertMinTouchTarget(box, minWidth, minHeight, label) {
  assert(box.width >= minWidth && box.height >= minHeight,
    `${label} should be at least ${minWidth}x${minHeight}, got ${JSON.stringify(box)}`);
}

function assertAboveBottomHud(box, viewport, label) {
  const reserve = viewport.width > viewport.height ? 72 : 84;
  assert(box.bottom < viewport.height - reserve,
    `${label} should stay above bottom HUD reserve ${reserve}px in ${JSON.stringify(viewport)}, got ${JSON.stringify(box)}`);
}

function assertQuickShortcutRail(layout, viewport, label) {
  const controls = {
    inventory: layout.inventory,
    magic: layout.magic,
    prayer: layout.prayer
  };
  assert(layout.quickButtons && layout.quickButtons.length === QUICK_PANEL_SHORTCUTS.length,
    `${label}: should expose ${QUICK_PANEL_SHORTCUTS.length} quick side shortcuts: ${JSON.stringify(layout.quickButtons)}`);
  for (const panel of QUICK_PANEL_SHORTCUTS) {
    const control = controls[panel];
    assert(control && control.display === 'flex',
      `${label}: ${panel} quick shortcut should be visible: ${JSON.stringify(control)}`);
    assertInsideViewport(control, viewport, `${label}: ${panel} quick shortcut`);
    assertMinTouchTarget(control, 44, 44, `${label}: ${panel} quick shortcut`);
    assertAboveBottomHud(control, viewport, `${label}: ${panel} quick shortcut`);
  }
  assert(controls.inventory.top < layout.keyboard.top,
    `${label}: inventory quick shortcut should sit above keyboard: ${JSON.stringify({ inventory: controls.inventory, keyboard: layout.keyboard })}`);
  assert(controls.magic.top > layout.keyboard.top,
    `${label}: magic quick shortcut should sit below keyboard: ${JSON.stringify({ magic: controls.magic, keyboard: layout.keyboard })}`);
  assert(controls.prayer.top > controls.magic.top,
    `${label}: prayer quick shortcut should sit below magic: ${JSON.stringify({ prayer: controls.prayer, magic: controls.magic })}`);
  assertNoOverlap(controls.inventory, layout.keyboard, `${label}: inventory/keyboard rail`);
  assertNoOverlap(controls.magic, layout.keyboard, `${label}: magic/keyboard rail`);
  assertNoOverlap(controls.prayer, controls.magic, `${label}: prayer/magic rail`);
}

function assertQuickShortcutControlsHidden(controls, label) {
  for (const key of ['inventoryButton', 'magicButton', 'prayerButton']) {
    const control = controls && controls[key];
    assert(control && control.present,
      `${label}: diagnostics ${key} should be present: ${JSON.stringify(control)}`);
    assert(control.display === 'none' && control.visible === false,
      `${label}: diagnostics ${key} should be hidden: ${JSON.stringify(control)}`);
  }
  const bestiary = controls && controls.bestiaryButton;
  assert(!bestiary || bestiary.present === false,
    `${label}: diagnostics bestiaryButton should be absent after removing the Best rail shortcut: ${JSON.stringify(bestiary)}`);
}

function assertAndroidParityCanvasControls(controls, label) {
  const chatButton = controls.chatButton || controls.chat;
  const panelButton = controls.panelButton || controls.panel;
  const chatTray = controls.chatTray;
  assert(panelButton && panelButton.display === 'none',
    `${label}: panel button should be hidden while Android-parity canvas tabs own panels: ${JSON.stringify(panelButton)}`);
  assert(chatButton && chatButton.display === 'none',
    `${label}: chat helper should be hidden while canvas chat tabs own chat: ${JSON.stringify(chatButton)}`);
  if (chatTray) {
    assert(chatTray.display === 'none',
      `${label}: chat tray should stay closed in Android-parity canvas-chat mode: ${JSON.stringify(chatTray)}`);
  }
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
    const bestiaryControl = entry.controls && entry.controls.bestiaryButton;
    assert(!bestiaryControl || bestiaryControl.present === false,
      `${label}: controlsHistory.${orientation}.bestiaryButton should be absent: ${JSON.stringify(bestiaryControl)}`);
    const panelControl = entry.controls && entry.controls.panelButton;
    assert(panelControl && panelControl.present,
      `${label}: controlsHistory.${orientation}.panelButton should be present for diagnostics: ${JSON.stringify(panelControl)}`);
    assert(panelControl.display === 'none' && panelControl.visible === false,
      `${label}: controlsHistory.${orientation}.panelButton should be hidden while Android-parity canvas tabs own panels: ${JSON.stringify(panelControl)}`);
    assertAndroidParityCanvasControls(entry.controls, `${label}: controlsHistory.${orientation}`);
  }
}

async function readDialogSafeLayout(page, viewport, dialogName) {
  await resetState(page);
  await page.evaluate((dialogName) => {
    document.body.classList.add('dialog-open');
    window.__voidscapeUiState = {
      blockingDialog: true,
      blockingDialogName: dialogName,
      updatedAt: Date.now()
    };
  }, dialogName);
  return await readControlLayout(page, viewport);
}

function assertDialogSafeLayout(layout, dialogRect, label) {
	  assert(layout.keyboard.display === 'flex', `${label}: keyboard button should stay reachable`);
	  assert(layout.inventory.display === 'none', `${label}: inventory shortcut should hide while a blocking dialog is open`);
	  assert(layout.magic.display === 'none', `${label}: magic shortcut should hide while a blocking dialog is open`);
	  assert(layout.prayer.display === 'none', `${label}: prayer shortcut should hide while a blocking dialog is open`);
	  assert(layout.bestiary.present === false, `${label}: bestiary shortcut should be absent`);
	  assert(layout.panel.display === 'none', `${label}: panel drawer trigger should hide while a blocking dialog is open`);
	  assert(layout.panelDrawer.display === 'none', `${label}: panel drawer should hide while a blocking dialog is open`);
	  assert(layout.chat.display === 'none', `${label}: chat tray trigger should hide while a blocking dialog is open`);
	  assert(layout.chatTray.display === 'none', `${label}: chat tray should hide while a blocking dialog is open`);
	  assert(layout.diagnostics.display === 'flex', `${label}: diagnostics button should stay reachable`);
  assert(layout.diagnosticsCopy.display === 'flex', `${label}: diagnostics copy button should stay reachable`);
  assert(layout.camera.display === 'none', `${label}: camera controls should hide while a blocking dialog is open`);
  for (const [key, box] of Object.entries({
    keyboard: layout.keyboard,
    diagnostics: layout.diagnostics,
    diagnosticsCopy: layout.diagnosticsCopy
  })) {
    assertInsideViewport(box, layout.viewport, `${label}: ${key}`);
    assertMinTouchTarget(box, 44, 44, `${label}: ${key}`);
    assertAboveBottomHud(box, layout.viewport, `${label}: ${key}`);
    assertNoOverlap(box, dialogRect, `${label}: ${key}/dialog`);
  }
  assertNoOverlap(layout.diagnostics, layout.keyboard, `${label}: diagnostics/keyboard`);
  assertNoOverlap(layout.diagnosticsCopy, layout.diagnostics, `${label}: diagnostics copy/diagnostics`);
}

async function clientPoint(page, frameX, frameY) {
  return await page.evaluate(({ frameX, frameY }) => {
    const canvas = document.getElementById('game');
    const rect = canvas.getBoundingClientRect();
    return {
      x: rect.left + frameX * rect.width / canvas.width,
      y: rect.top + frameY * rect.height / canvas.height
    };
  }, { frameX, frameY });
}

async function assertCanvasPointUnblocked(page, point, frame, label) {
  const hit = await page.evaluate(({ point }) => {
    const canvas = document.getElementById('game');
    const element = document.elementFromPoint(point.x, point.y);
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
      ok: !!canvas && (element === canvas || canvas.contains(element)),
      point,
      element: describe(element),
      canvas: describe(canvas)
    };
  }, { point });
  assert(hit.ok,
    `${label} canvas point ${JSON.stringify(frame)} maps to ${JSON.stringify(point)} but browser hit ${JSON.stringify(hit.element)}`);
}

async function pointer(page, type, frameX, frameY, options = {}) {
  const point = await clientPoint(page, frameX, frameY);
  if (type === 'pointerdown' && options.skipHitTest !== true) {
    await assertCanvasPointUnblocked(page, point, { x: frameX, y: frameY }, options.label || type);
  }
  await page.evaluate(({ type, point, options }) => {
    const canvas = document.getElementById('game');
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

async function pointerAtClient(page, type, point, options = {}) {
  return await page.evaluate(({ type, point, options }) => {
    const canvas = document.getElementById('game');
    const main = document.querySelector('main');
    const target = document.elementFromPoint(point.x, point.y) || main || canvas || document.body;
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
    target.dispatchEvent(event);
    function describe(node) {
      if (!node) return null;
      return {
        tag: node.tagName,
        id: node.id || '',
        className: typeof node.className === 'string' ? node.className : ''
      };
    }
    return {
      target: describe(target),
      canvas: describe(canvas),
      main: describe(main)
    };
  }, { type, point, options });
}

async function tap(page, frameX, frameY) {
  await pointer(page, 'pointerdown', frameX, frameY, { buttons: 1 });
  await pointer(page, 'pointerup', frameX, frameY, { buttons: 0 });
}

function landscapeFramebufferWidthForViewport(viewport) {
  return Math.max(512, Math.min(1152, Math.round(346 * viewport.width / viewport.height)));
}

async function setSimulatedCanvasFramebuffer(page, width, height) {
  await page.evaluate(({ width, height }) => {
    const canvas = document.getElementById('game');
    canvas.width = width;
    canvas.height = height;
    document.documentElement.style.setProperty('--game-aspect', `${width} / ${height}`);
    document.documentElement.style.setProperty('--game-aspect-ratio', String(width / Math.max(1, height)));
    canvas.style.aspectRatio = `${width} / ${height}`;
    canvas.style.width = '';
    canvas.style.height = '';
  }, { width, height });
}

async function runLandscapeFillSmoke(page) {
  await page.setViewportSize({ width: 932, height: 430 });
  await resetState(page);
  const viewport = { width: 932, height: 430 };
  const framebufferWidth = landscapeFramebufferWidthForViewport(viewport);
  await setSimulatedCanvasFramebuffer(page, framebufferWidth, 346);
  await page.waitForTimeout(220);
  const layout = await page.evaluate(() => {
    const canvas = document.getElementById('game');
    const main = document.querySelector('main');
    const canvasRect = canvas.getBoundingClientRect();
    const mainRect = main.getBoundingClientRect();
    const y = Math.round(canvasRect.top + canvasRect.height / 2);
    const expectedY = Math.max(0, Math.min(canvas.height - 1,
      Math.floor((y - canvasRect.top) * canvas.height / canvasRect.height)));
    const leftPoint = { x: Math.round(canvasRect.left + 1), y };
    const rightPoint = { x: Math.round(canvasRect.right - 1), y };
    function hit(point) {
      const node = document.elementFromPoint(point.x, point.y);
      return {
        tag: node && node.tagName,
        id: node && (node.id || ''),
        className: node && (typeof node.className === 'string' ? node.className : '')
      };
    }
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      canvas: {
        left: canvasRect.left,
        right: canvasRect.right,
        top: canvasRect.top,
        bottom: canvasRect.bottom,
        width: canvasRect.width,
        height: canvasRect.height,
        framebufferWidth: canvas.width,
        framebufferHeight: canvas.height
      },
      main: {
        left: mainRect.left,
        right: mainRect.right,
        top: mainRect.top,
        bottom: mainRect.bottom,
        width: mainRect.width,
        height: mainRect.height
      },
      leftPoint,
      rightPoint,
      expectedY,
      leftHit: hit(leftPoint),
      rightHit: hit(rightPoint)
    };
  });
  assert(layout.canvas.framebufferWidth === framebufferWidth && layout.canvas.framebufferHeight === 346,
    `phone landscape should use widened framebuffer ${framebufferWidth}x346, got ${JSON.stringify(layout.canvas)}`);
  assert(layout.canvas.width >= layout.viewport.width - 2,
    `phone landscape canvas should fill viewport width, got ${JSON.stringify(layout)}`);
  assert(layout.canvas.left <= 1 && layout.canvas.right >= layout.viewport.width - 1,
    `phone landscape canvas should not leave side gutters, got ${JSON.stringify(layout)}`);
  assert(layout.leftHit && layout.leftHit.id === 'game',
    `left landscape edge should hit the game canvas, got ${JSON.stringify(layout.leftHit)} from ${JSON.stringify(layout)}`);
  assert(layout.rightHit && layout.rightHit.id === 'game',
    `right landscape edge should hit the game canvas, got ${JSON.stringify(layout.rightHit)} from ${JSON.stringify(layout)}`);

  await pointerAtClient(page, 'pointerdown', layout.leftPoint, { pointerId: 71, buttons: 1 });
  await pointerAtClient(page, 'pointerup', layout.leftPoint, { pointerId: 71, buttons: 0 });
  let events = await drain(page);
  assertIncludes(events, `p,0,0,${layout.expectedY},0`, 'left landscape edge touch should map to left framebuffer edge');
  assertIncludes(events, `p,1,0,${layout.expectedY},1`, 'left landscape edge touch should click left framebuffer edge');
  assertIncludes(events, `p,2,0,${layout.expectedY},1`, 'left landscape edge touch should release left framebuffer edge');

  await resetState(page);
  await setSimulatedCanvasFramebuffer(page, framebufferWidth, 346);
  await page.waitForTimeout(80);
  await pointerAtClient(page, 'pointerdown', layout.rightPoint, { pointerId: 72, buttons: 1 });
  await pointerAtClient(page, 'pointerup', layout.rightPoint, { pointerId: 72, buttons: 0 });
  events = await drain(page);
  const expectedRightX = layout.canvas.framebufferWidth - 1;
  assertIncludes(events, `p,0,${expectedRightX},${layout.expectedY},0`, 'right landscape edge touch should map to right framebuffer edge');
  assertIncludes(events, `p,1,${expectedRightX},${layout.expectedY},1`, 'right landscape edge touch should click right framebuffer edge');
  assertIncludes(events, `p,2,${expectedRightX},${layout.expectedY},1`, 'right landscape edge touch should release right framebuffer edge');

  return layout;
}

async function touchButton(page, selector) {
  await page.evaluate((selector) => {
    const button = document.querySelector(selector);
    const event = new PointerEvent('pointerdown', {
      bubbles: true,
      cancelable: true,
      pointerId: 77,
      pointerType: 'touch',
      isPrimary: true,
      button: 0,
      buttons: 1
    });
    button.dispatchEvent(event);
  }, selector);
}

async function openKeyboard(page) {
  await touchButton(page, '#keyboard-button');
  await page.waitForTimeout(80);
  let events = await drain(page);
  if (!events.includes('s,1')) {
    await page.evaluate(() => {
      if (window.__voidscapeFocusKeyboard) window.__voidscapeFocusKeyboard();
      const capture = document.getElementById('keyboard-capture');
      if (capture) capture.dispatchEvent(new Event('focus'));
    });
    events = events.concat(await drain(page));
  }
  return events;
}

async function closeKeyboard(page) {
	await touchButton(page, '#keyboard-button');
  await page.waitForTimeout(80);
  let events = await drain(page);
  if (!events.includes('s,0')) {
    await page.evaluate(() => {
      const capture = document.getElementById('keyboard-capture');
      if (capture) capture.dispatchEvent(new Event('blur'));
      if (window.__voidscapeBlurKeyboard) window.__voidscapeBlurKeyboard();
    });
    events = events.concat(await drain(page));
  }
	return events;
}

async function runMobileQuickShortcutSmoke(page) {
  await page.setViewportSize({ width: 390, height: 664 });
  await resetState(page);
  await page.waitForTimeout(120);

  const shortcutEvents = [];
  for (const panel of QUICK_PANEL_SHORTCUTS) {
    await drain(page);
    await touchButton(page, `#${panel}-button`);
    await page.waitForTimeout(100);
    const events = await drain(page);
    assertIncludes(events, `u,${QUICK_PANEL_EVENTS[panel]}`, `${panel} side shortcut`);
    const state = await page.evaluate((panel) => ({
      panelDrawerOpen: !!window.__voidscapePanelDrawerOpen,
      chatTrayOpen: !!window.__voidscapeChatTrayOpen,
      actionArmed: !!window.__voidscapeNextTapSecondary,
      button: window.__voidscapeCollectDiagnostics().controls[`${panel}Button`]
    }), panel);
    assert(state.button && state.button.hitSelf === true,
      `${panel} side shortcut should remain hittable at its center: ${JSON.stringify(state)}`);
    assert(state.panelDrawerOpen === false && state.chatTrayOpen === false && state.actionArmed === false,
      `${panel} side shortcut should close helper modes: ${JSON.stringify(state)}`);
    shortcutEvents.push({ panel, events, state });
  }

  await resetState(page);
  let events = await openKeyboard(page);
  assertIncludes(events, 's,1', 'quick shortcut keyboard precondition');
  await drain(page);
  await touchButton(page, '#magic-button');
  await page.waitForTimeout(120);
  events = await drain(page);
  assertIncludes(events, 's,0', 'magic side shortcut should close keyboard first');
  assertIncludes(events, 'u,side-magic', 'magic side shortcut should still open magic after keyboard close');
  const keyboardCloseState = await page.evaluate(() => ({
    keyboardOpen: !!window.__voidscapeKeyboardOpen,
    keyboardWanted: !!window.__voidscapeKeyboardWanted,
    closingFrozen: !!window.__voidscapeViewportKeyboardClosingFrozen,
    closingUntil: window.__voidscapeKeyboardClosingUntil || 0
  }));
  assert(keyboardCloseState.keyboardOpen === false && keyboardCloseState.keyboardWanted === false,
    `magic side shortcut should leave keyboard closed: ${JSON.stringify(keyboardCloseState)}`);

  return {
    shortcutEvents,
    keyboardClose: {
      events,
      state: keyboardCloseState
    }
  };
}

async function runMobilePanelDrawerSmoke(page) {
  await resetState(page);
  const state = await page.evaluate(() => {
    const button = document.getElementById('panel-button');
    const drawer = document.getElementById('mobile-panel-drawer');
    const buttonBox = button.getBoundingClientRect();
    return {
      open: !!window.__voidscapePanelDrawerOpen,
      bodyClass: document.body.className,
      buttonAriaPressed: button.getAttribute('aria-pressed') || '',
      buttonRect: {
        left: Math.round(buttonBox.left),
        top: Math.round(buttonBox.top),
        right: Math.round(buttonBox.right),
        bottom: Math.round(buttonBox.bottom),
        width: Math.round(buttonBox.width),
        height: Math.round(buttonBox.height)
      },
      drawerDisplay: getComputedStyle(drawer).display,
      panels: Array.from(document.querySelectorAll('.mobile-panel-button')).map((element) => {
        const box = element.getBoundingClientRect();
        return {
          panel: element.getAttribute('data-panel') || '',
          display: getComputedStyle(element).display,
          width: Math.round(box.width),
          height: Math.round(box.height)
        };
      })
    };
  });
  assert(state.open === false && !state.bodyClass.includes('panel-drawer-open'),
    `panel drawer fallback should stay closed while Android-parity canvas tabs own panels: ${JSON.stringify(state)}`);
  assert(state.buttonAriaPressed === 'false',
    `hidden panel drawer button should not report pressed state: ${JSON.stringify(state)}`);
  assert(state.buttonRect.width === 0 && state.buttonRect.height === 0,
    `panel drawer trigger should be hidden in Android-parity canvas mode: ${JSON.stringify(state.buttonRect)}`);
  assert(state.drawerDisplay === 'none', `panel drawer should stay hidden in Android-parity canvas mode: ${JSON.stringify(state)}`);
  const expectedPanels = ['inventory', 'map', 'magic', 'prayer', 'skills', 'quests', 'friends', 'options'];
  assert(state.panels.length === expectedPanels.length,
    `panel drawer fallback should retain ${expectedPanels.length} shortcut metadata: ${JSON.stringify(state.panels)}`);
  for (const panel of expectedPanels) {
    const entry = state.panels.find((candidate) => candidate.panel === panel);
    assert(entry, `panel drawer fallback should include ${panel}: ${JSON.stringify(state.panels)}`);
    assert(entry.display === 'flex', `panel drawer ${panel} metadata should remain configured: ${JSON.stringify(entry)}`);
  }

  return {
    initialState: state,
    expectedPanels
  };
}

async function runMobileChatTraySmoke(page) {
  await resetState(page);
  await page.evaluate(() => {
    document.body.classList.add('chat-helper-visible');
    window.__voidscapeUiState = {
      customUi: true,
      androidProfile: true,
      webBuild: true,
	      messageTab: 'ALL',
	      chatPanelHidden: true,
	      phoneLandscapeLooseChat: false,
	      mobileLooseChat: true,
	      chatAccessMode: 'collapsed-helper',
      blockingDialog: false,
      blockingDialogName: '',
      updatedAt: Date.now()
    };
  });
  await touchButton(page, '#chat-button');
  await page.waitForTimeout(120);
  const state = await page.evaluate(() => {
    const button = document.getElementById('chat-button');
    const tray = document.getElementById('mobile-chat-tray');
    const buttonBox = button.getBoundingClientRect();
    return {
      open: !!window.__voidscapeChatTrayOpen,
      bodyClass: document.body.className,
      buttonAriaPressed: button.getAttribute('aria-pressed') || '',
      buttonRect: {
        left: Math.round(buttonBox.left),
        top: Math.round(buttonBox.top),
        right: Math.round(buttonBox.right),
        bottom: Math.round(buttonBox.bottom),
        width: Math.round(buttonBox.width),
        height: Math.round(buttonBox.height)
      },
      trayDisplay: getComputedStyle(tray).display,
      shortcuts: Array.from(document.querySelectorAll('.mobile-chat-button')).map((element) => {
        const box = element.getBoundingClientRect();
        return {
          chat: element.getAttribute('data-chat') || '',
          display: getComputedStyle(element).display,
          width: Math.round(box.width),
          height: Math.round(box.height)
        };
      })
    };
  });
  assert(state.open === true && state.bodyClass.includes('chat-tray-open'),
    `chat tray should open from Chat button: ${JSON.stringify(state)}`);
  assert(state.buttonAriaPressed === 'true',
    `chat tray button should expose pressed state: ${JSON.stringify(state)}`);
  assertMinTouchTarget(state.buttonRect, 44, 44, 'chat tray trigger');
  assert(state.trayDisplay === 'grid', `chat tray should display as grid when open: ${JSON.stringify(state)}`);
  const expectedShortcuts = ['all', 'chat', 'quest', 'global', 'private', 'compose'];
  assert(state.shortcuts.length === expectedShortcuts.length,
    `chat tray should expose ${expectedShortcuts.length} shortcuts: ${JSON.stringify(state.shortcuts)}`);
  for (const shortcut of expectedShortcuts) {
    const entry = state.shortcuts.find((candidate) => candidate.chat === shortcut);
    assert(entry, `chat tray should include ${shortcut}: ${JSON.stringify(state.shortcuts)}`);
    assert(entry.display === 'flex', `chat tray ${shortcut} button should be visible: ${JSON.stringify(entry)}`);
    assertMinTouchTarget(entry, 44, 44, `chat tray ${shortcut} button`);
  }

  const shortcutEvents = [];
  for (const shortcut of expectedShortcuts) {
    await drain(page);
    await page.evaluate(() => {
      if (window.__voidscapeSetChatTrayOpen) window.__voidscapeSetChatTrayOpen(true);
    });
    await page.waitForTimeout(60);
    await touchButton(page, `.mobile-chat-button[data-chat="${shortcut}"]`);
    await page.waitForTimeout(shortcut === 'compose' ? 160 : 100);
    const events = await drain(page);
    assertIncludes(events, `c,${shortcut}`, `chat tray ${shortcut} shortcut`);
    if (shortcut === 'compose') {
      assertIncludes(events, 's,1', 'chat tray compose should open keyboard');
      await closeKeyboard(page);
    }
    const closed = await page.evaluate(() => ({
      open: !!window.__voidscapeChatTrayOpen,
      bodyClass: document.body.className,
      trayDisplay: getComputedStyle(document.getElementById('mobile-chat-tray')).display
    }));
    assert(closed.open === false && !closed.bodyClass.includes('chat-tray-open') && closed.trayDisplay === 'none',
      `chat tray should close after ${shortcut} shortcut: ${JSON.stringify(closed)}`);
    shortcutEvents.push({ shortcut, events });
  }

  await page.evaluate(() => {
    if (window.__voidscapeSetChatTrayOpen) window.__voidscapeSetChatTrayOpen(true);
    history.back();
  });
  await page.waitForTimeout(350);
  const backEvents = await drain(page);
  const backClosed = await page.evaluate(() => ({
    open: !!window.__voidscapeChatTrayOpen,
    bodyClass: document.body.className
  }));
  assert(!backClosed.open && !backClosed.bodyClass.includes('chat-tray-open'),
    `browser Back should close chat tray first: ${JSON.stringify(backClosed)}`);
  assert(!backEvents.includes('b,1'),
    `browser Back should not send shared Back when it only closes chat tray: ${JSON.stringify(backEvents)}`);

  return {
    initialState: state,
    shortcutEvents,
    backClosed,
    backEvents
  };
}

async function runWorldMapInputSmoke(page) {
  await resetState(page);
  await page.evaluate(() => {
    window.__voidscapeWorldMapTouchActive = true;
    window.__voidscapeWorldMapState = {
      visible: true,
      window: { x: 100, y: 100, width: 300, height: 360 }
    };
  });
  const diagnosticsBefore = await page.evaluate(() => window.__voidscapeCollectDiagnostics());
  assert(diagnosticsBefore.input && diagnosticsBefore.input.worldMap === true,
    `world-map input hint should be visible in diagnostics: ${JSON.stringify(diagnosticsBefore.input)}`);

  await pointer(page, 'pointerdown', 180, 210, { buttons: 1 });
  await pointer(page, 'pointermove', 194, 226, { buttons: 1 });
  await pointer(page, 'pointerup', 194, 226, { buttons: 0 });
  let events = await drain(page);
  ['g,0,180,210', 'g,1,194,226', 'g,2,194,226'].forEach((expected) => assertIncludes(events, expected, 'world-map drag/tap'));
  assertNoMatch(events, /^p,/, 'world-map touch should not leak terrain pointer events');
  assertNoMatch(events, /^k,1,(37|38|39|40)$/, 'world-map touch should not trigger camera keys');

  await resetState(page);
  await page.evaluate(() => {
    window.__voidscapeWorldMapTouchActive = true;
    window.__voidscapeWorldMapState = {
      visible: true,
      window: { x: 100, y: 100, width: 300, height: 360 }
    };
  });
  await pointer(page, 'pointerdown', 40, 60, { buttons: 1 });
  await pointer(page, 'pointerup', 40, 60, { buttons: 0 });
  const outsideEvents = await drain(page);
  assertIncludes(outsideEvents, 'p,0,40,60,0', 'world-map outside-window touch should use normal pointer down');
  assertIncludes(outsideEvents, 'p,1,40,60,1', 'world-map outside-window touch should use normal primary click');
  assertIncludes(outsideEvents, 'p,2,40,60,1', 'world-map outside-window touch should release normal primary click');
  assertNoMatch(outsideEvents, /^g,/, 'world-map outside-window touch should not use map gesture stream');

  await resetState(page);
  await page.evaluate(() => {
    window.__voidscapeWorldMapTouchActive = true;
    window.__voidscapeWorldMapState = {
      visible: true,
      window: { x: 100, y: 100, width: 300, height: 360 }
    };
  });
  await pointer(page, 'pointerdown', 210, 240, { buttons: 1 });
  await page.waitForTimeout(660);
  await pointer(page, 'pointerup', 210, 240, { buttons: 0 });
  const longPressEvents = await drain(page);
  assertIncludes(longPressEvents, 'g,0,210,240', 'world-map long press start');
  assertIncludes(longPressEvents, 'g,2,210,240', 'world-map long press release');
  assertNoMatch(longPressEvents, /^p,3,\d+,\d+,2$/, 'world-map long press should not open context click');
  assertNoMatch(longPressEvents, /^p,/, 'world-map long press should not leak pointer events');

  await resetState(page);
  await page.evaluate(() => {
    window.__voidscapeWorldMapTouchActive = true;
    window.__voidscapeWorldMapState = {
      visible: true,
      window: { x: 100, y: 100, width: 300, height: 360 }
    };
  });
  await pointer(page, 'pointerdown', 190, 260, { pointerId: 1, isPrimary: true, buttons: 1 });
  await pointer(page, 'pointerdown', 250, 260, { pointerId: 2, isPrimary: false, buttons: 1 });
  const pinchStartEvents = await drain(page);
  assertIncludes(pinchStartEvents, 'g,0,190,260', 'world-map pinch primary start');
  assertIncludes(pinchStartEvents, 'g,3,250,260', 'world-map pinch should cancel active map drag');
  assertNoMatch(pinchStartEvents, /^p,/, 'world-map pinch start should not leak pointer events');
  await pointer(page, 'pointermove', 310, 260, { pointerId: 2, isPrimary: false, buttons: 1 });
  const pinchZoomEvents = await drain(page);
  assert(pinchZoomEvents.some((event) => /^w,[1-9]/.test(event)),
    `world-map pinch-out should queue positive map zoom scroll ticks: ${JSON.stringify(pinchZoomEvents)}`);
  assertNoMatch(pinchZoomEvents, /^k,1,(37|38|39|40)$/, 'world-map pinch should not trigger camera keys');
  await pointer(page, 'pointercancel', 250, 260, { pointerId: 2, isPrimary: false, buttons: 0 });
  await pointer(page, 'pointercancel', 190, 260, { pointerId: 1, isPrimary: true, buttons: 0 });
  await drain(page);

  return {
    diagnosticsBefore,
    dragTapEvents: events,
    outsideEvents,
    longPressEvents,
    pinchStartEvents,
    pinchZoomEvents
  };
}

async function runOverlaySuppressionSmoke(page) {
  await page.setViewportSize({ width: 390, height: 664 });
  await resetState(page);
  await page.waitForTimeout(120);

  const normal = await page.evaluate(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return {
      bodyClass: snapshot.bodyClass,
      ui: snapshot.ui,
      controls: snapshot.controls
    };
  });
  assert(normal.ui && normal.ui.panelAccessMode === 'canvas',
    `normal play should use shared canvas panel ownership: ${JSON.stringify(normal.ui)}`);
	  assert(normal.ui && normal.ui.chatAccessMode === 'canvas',
	    `normal play should use shared canvas chat ownership: ${JSON.stringify(normal.ui)}`);
	  assert(normal.ui && normal.ui.mobileLooseChat === true,
	    `normal play should use mobile loose chat instead of a framed chat container: ${JSON.stringify(normal.ui)}`);
  assert(normal.controls.panelButton.display === 'none' && normal.controls.panelButton.visible === false,
    `normal play should hide redundant DOM panel button: ${JSON.stringify(normal.controls.panelButton)}`);
  assert(normal.controls.panelDrawer.display === 'none' && normal.controls.panelDrawer.visible === false,
    `normal play should hide redundant DOM panel drawer: ${JSON.stringify(normal.controls.panelDrawer)}`);
  assert(normal.controls.chatButton.display === 'none' && normal.controls.chatButton.visible === false,
    `normal play should hide redundant DOM chat button: ${JSON.stringify(normal.controls.chatButton)}`);
  assert(normal.controls.chatTray.display === 'none' && normal.controls.chatTray.visible === false,
    `normal play should hide redundant DOM chat tray: ${JSON.stringify(normal.controls.chatTray)}`);
  assert(normal.controls.inventoryButton.hitSelf === true,
    `normal play inventory side shortcut should be hittable at its center: ${JSON.stringify(normal.controls.inventoryButton)}`);
  assert(normal.controls.keyboardButton.hitSelf === true,
    `normal play keyboard helper should be hittable at its center: ${JSON.stringify(normal.controls.keyboardButton)}`);
  for (const key of ['magicButton', 'prayerButton']) {
    const control = normal.controls[key];
    assert(control && control.display === 'flex' && control.visible && control.hitSelf === true,
      `normal play ${key} should be visible and hittable: ${JSON.stringify(control)}`);
  }
  assert(!normal.controls.bestiaryButton || normal.controls.bestiaryButton.present === false,
    `normal play bestiaryButton should be absent after removing Best from the side rail: ${JSON.stringify(normal.controls.bestiaryButton)}`);

  await page.evaluate(() => {
    document.body.classList.add('world-map-open');
    window.__voidscapeWorldMapTouchActive = true;
    window.__voidscapeWorldMapState = {
      visible: true,
      window: { x: 64, y: 107, width: 384, height: 645 },
      searchFocused: false,
      searchQuery: ''
    };
  });
  await page.waitForTimeout(120);
  const worldMap = await page.evaluate(() => {
    const snapshot = window.__voidscapeCollectDiagnostics();
    return {
      bodyClass: snapshot.bodyClass,
      input: snapshot.input,
      controls: snapshot.controls
    };
  });
  assert(worldMap.bodyClass.includes('world-map-open'),
    `world-map overlay state should be reflected on body: ${JSON.stringify(worldMap.bodyClass)}`);
  assert(worldMap.input && worldMap.input.worldMap === true,
    `world-map input mode should be visible to diagnostics: ${JSON.stringify(worldMap.input)}`);
  assert(worldMap.controls.inventoryButton.display === 'none' && worldMap.controls.inventoryButton.visible === false,
    `world map should hide inventory side shortcut so it cannot cover map controls: ${JSON.stringify(worldMap.controls.inventoryButton)}`);
  assert(worldMap.controls.keyboardButton.display === 'none' && worldMap.controls.keyboardButton.visible === false,
    `world map should hide keyboard helper so it cannot cover map search/controls: ${JSON.stringify(worldMap.controls.keyboardButton)}`);
  assertQuickShortcutControlsHidden(worldMap.controls, 'world map');
  assert(worldMap.controls.panelButton.display === 'none' && worldMap.controls.panelButton.visible === false,
    `world map should keep redundant DOM panel button hidden: ${JSON.stringify(worldMap.controls.panelButton)}`);
  assert(worldMap.controls.chatButton.display === 'none' && worldMap.controls.chatButton.visible === false,
    `world map should keep redundant DOM chat button hidden: ${JSON.stringify(worldMap.controls.chatButton)}`);

  await resetState(page);

  return {
    normal,
    worldMap
  };
}

async function probeRuntimeMode(browser, name, contextOptions, query, expected) {
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  const url = `${baseUrl}/index.html?${query}&profile=mode-${name}&endpoint=reset&diag=1&host=127.0.0.1&port=65534`;
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForFunction(() => typeof window.__voidscapeCollectDiagnostics === 'function', null, { timeout: 30000 });
  await page.waitForTimeout(500);
  const state = await page.evaluate(() => {
    document.body.classList.add('game-drawn', 'in-game', 'android-parity-canvas-panels');
    document.body.classList.remove('dialog-open', 'world-map-open', 'keyboard-open', 'context-armed');
    const read = (selector) => {
      const element = document.querySelector(selector);
      if (!element) {
        return {
          present: false,
          display: 'missing',
          visible: false,
          width: 0,
          height: 0
        };
      }
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return {
        present: true,
        display: style.display,
        visible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0,
        width: Math.round(rect.width),
        height: Math.round(rect.height)
      };
    };
    const snapshot = window.__voidscapeCollectDiagnostics();
    return {
      htmlClass: document.documentElement.className,
      snapshot,
      controls: {
        keyboardButton: read('#keyboard-button'),
        inventoryButton: read('#inventory-button'),
        magicButton: read('#magic-button'),
        prayerButton: read('#prayer-button'),
        bestiaryButton: read('#bestiary-button'),
        panelButton: read('#panel-button'),
        chatButton: read('#chat-button'),
        diagnosticsButton: read('#diagnostics-button')
      }
    };
  });
  await context.close();

  const runtime = state.snapshot && state.snapshot.runtimeMode;
  assert(runtime && runtime.mode === expected.mode,
    `${name}: expected runtime mode ${expected.mode}, got ${JSON.stringify(runtime)} in ${state.htmlClass}`);
  assert(state.snapshot.touchProfile === expected.phoneShell,
    `${name}: expected touchProfile ${expected.phoneShell}, got ${state.snapshot.touchProfile}`);
  assert((state.snapshot.tabletProfile === true) === (expected.mode === 'tablet'),
    `${name}: tabletProfile mismatch: ${JSON.stringify(state.snapshot)}`);
  assert(state.htmlClass.includes(expected.mode),
    `${name}: html class should include ${expected.mode}: ${state.htmlClass}`);
  const classTokens = state.htmlClass.split(/\s+/).filter(Boolean);
  assert(classTokens.includes('touch') === expected.phoneShell,
    `${name}: html.touch should mean phone shell only: ${state.htmlClass}`);
  if (expected.phoneShell) {
    assert(state.controls.keyboardButton.display === 'flex' && state.controls.keyboardButton.visible,
      `${name}: phone keyboard helper should be visible: ${JSON.stringify(state.controls.keyboardButton)}`);
    assert(state.controls.inventoryButton.display === 'flex' && state.controls.inventoryButton.visible,
      `${name}: phone inventory side shortcut should be visible: ${JSON.stringify(state.controls.inventoryButton)}`);
    for (const key of ['magicButton', 'prayerButton']) {
      assert(state.controls[key].display === 'flex' && state.controls[key].visible,
        `${name}: phone ${key} should be visible: ${JSON.stringify(state.controls[key])}`);
    }
    assert(state.controls.bestiaryButton.present === false,
      `${name}: phone bestiaryButton should be absent: ${JSON.stringify(state.controls.bestiaryButton)}`);
  } else {
    assert(state.controls.keyboardButton.display === 'none' && !state.controls.keyboardButton.visible,
      `${name}: non-phone keyboard helper should be hidden: ${JSON.stringify(state.controls.keyboardButton)}`);
    assert(state.controls.inventoryButton.display === 'none' && !state.controls.inventoryButton.visible,
      `${name}: non-phone inventory side shortcut should be hidden: ${JSON.stringify(state.controls.inventoryButton)}`);
    for (const key of ['magicButton', 'prayerButton']) {
      assert(state.controls[key].display === 'none' && !state.controls[key].visible,
        `${name}: non-phone ${key} should be hidden: ${JSON.stringify(state.controls[key])}`);
    }
    assert(state.controls.bestiaryButton.present === false,
      `${name}: non-phone bestiaryButton should be absent: ${JSON.stringify(state.controls.bestiaryButton)}`);
  }
  assert(state.controls.panelButton.display === 'none',
    `${name}: redundant DOM panel button should remain hidden: ${JSON.stringify(state.controls.panelButton)}`);
  assert(state.controls.chatButton.display === 'none',
    `${name}: redundant DOM chat button should remain hidden: ${JSON.stringify(state.controls.chatButton)}`);
  assert(state.controls.diagnosticsButton.display === 'flex',
    `${name}: diagnostics button should still be available under diag=1: ${JSON.stringify(state.controls.diagnosticsButton)}`);
  return state;
}

async function runRuntimeModeSmoke(browser) {
  const desktop = await probeRuntimeMode(browser, 'desktop-auto', {
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 1,
    isMobile: false,
    hasTouch: false,
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
  }, 'modecheck=desktop-auto', { mode: 'desktop', phoneShell: false });

  const phone = await probeRuntimeMode(browser, 'phone-auto', {
    viewport: { width: 390, height: 664 },
    screen: { width: 390, height: 844 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
  }, 'modecheck=phone-auto', { mode: 'phone', phoneShell: true });

  const androidDesktopSite = await probeRuntimeMode(browser, 'android-desktop-site', {
    viewport: { width: 980, height: 1900 },
    screen: { width: 412, height: 915 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
  }, 'modecheck=android-desktop-site', { mode: 'phone', phoneShell: true });

  const tablet = await probeRuntimeMode(browser, 'tablet-auto', {
    viewport: { width: 820, height: 1180 },
    screen: { width: 820, height: 1180 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
  }, 'modecheck=tablet-auto', { mode: 'tablet', phoneShell: false });

  const desktopOverride = await probeRuntimeMode(browser, 'desktop-override', {
    viewport: { width: 390, height: 664 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
  }, 'desktop=1', { mode: 'desktop', phoneShell: false });

  const phoneOverride = await probeRuntimeMode(browser, 'phone-override', {
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 1,
    isMobile: false,
    hasTouch: false,
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
  }, 'phone=1', { mode: 'phone', phoneShell: true });

  return {
    desktop: desktop.snapshot.runtimeMode,
    phone: phone.snapshot.runtimeMode,
    androidDesktopSite: androidDesktopSite.snapshot.runtimeMode,
    tablet: tablet.snapshot.runtimeMode,
    desktopOverride: desktopOverride.snapshot.runtimeMode,
    phoneOverride: phoneOverride.snapshot.runtimeMode
  };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({ executablePath: chromePath, headless: true });
  const runtimeModes = await runRuntimeModeSmoke(browser);
  const context = await browser.newContext({
    viewport: { width: 390, height: 664 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
  });
  const page = await context.newPage();
  const failed = [];
  const consoleMessages = [];
  page.on('requestfailed', (req) => failed.push(`${req.method()} ${req.url()} ${req.failure() && req.failure().errorText}`));
  page.on('console', (msg) => consoleMessages.push(`${msg.type()}: ${msg.text()}`));

	  await page.goto(`${baseUrl}/index.html?mobile=1&profile=smoke&endpoint=reset&diag=1&host=127.0.0.1&port=65534&portal=/iphone-account/&portalRecoveryUrl=/iphone-recovery/`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('canvas', { timeout: 30000 });
  await page.waitForFunction(() => !!window.__voidscapeInputInstalled, null, { timeout: 30000 });
  await page.waitForFunction(() => typeof window.__voidscapeCollectDiagnostics === 'function', null, { timeout: 30000 });
  await installInputRecorder(page);
  await page.waitForTimeout(800);

  if (onlyWorldMapInput) {
    const worldMapInput = await runWorldMapInputSmoke(page);
    const screenshot = path.join(outDir, 'iphone-controls-world-map-input.png');
    await page.screenshot({ path: screenshot, fullPage: true });
    await browser.close();

    const unexpectedFailures = failed.filter((failure) => !failure.includes('ws://127.0.0.1:65534/'));
    const ignoredNetworkFailures = failed.filter((failure) => failure.includes('ws://127.0.0.1:65534/'));
    const summary = {
      baseUrl,
      worldMapInput,
      failedRequests: unexpectedFailures,
      ignoredNetworkFailures,
      consoleTail: consoleMessages.slice(-12),
      screenshot
    };
    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify(summary, null, 2));
    assert(unexpectedFailures.length === 0, `unexpected request failures observed: ${unexpectedFailures.join('; ')}`);
    return;
  }

  await page.evaluate(() => {
    window.__voidscapePortalNavigationMode = 'capture';
    window.__voidscapeOpenedUrls = [];
  });
	  const portalHandoff = await page.evaluate(() => ({
	    profile: window.__voidscapeProfile,
	    endpoint: window.__voidscapeEndpoint,
	    storedEndpoint: window.__voidscapeCollectDiagnostics().storedEndpoint,
	    config: window.__voidscapePortalConfig,
	    accountOpened: window.__voidscapeOpenClientUrl('voidscape:account'),
	    recoveryOpened: window.__voidscapeOpenClientUrl('voidscape:recovery'),
	    shopOpened: window.__voidscapeOpenClientUrl('voidscape:shop'),
    javascriptUrlOpened: window.__voidscapeOpenClientUrl('javascript:alert(1)'),
    opened: (window.__voidscapeOpenedUrls || []).slice()
  }));
	  const expectedAccountUrl = new URL('/iphone-account/#account', baseUrl).href;
	  const expectedRecoveryUrl = new URL('/iphone-recovery/', baseUrl).href;
	  const expectedShopUrl = new URL('/portal#subscription-buy', baseUrl).href;
	  assert(portalHandoff.profile && portalHandoff.profile.id === 'smoke' && portalHandoff.profile.namespaced === true,
	    `profile query should select a namespaced web-client profile: ${JSON.stringify(portalHandoff)}`);
	  assert(portalHandoff.endpoint && portalHandoff.endpoint.profile === 'smoke'
	      && portalHandoff.endpoint.storageKey && portalHandoff.endpoint.storageKey.endsWith('.profile.smoke'),
	    `endpoint storage should be namespaced by profile: ${JSON.stringify(portalHandoff.endpoint)}`);
	  assert(portalHandoff.storedEndpoint && portalHandoff.storedEndpoint.includes('"host":"127.0.0.1"'),
	    `profile endpoint should be stored under the active profile key: ${JSON.stringify(portalHandoff)}`);
	  assert(portalHandoff.config && portalHandoff.config.accountUrl === expectedAccountUrl,
	    `account portal URL should resolve from query config: ${JSON.stringify(portalHandoff)}`);
  assert(portalHandoff.config && portalHandoff.config.recoveryUrl === expectedRecoveryUrl,
    `recovery portal URL should resolve from query config: ${JSON.stringify(portalHandoff)}`);
	  assert(portalHandoff.config && portalHandoff.config.shopUrl === expectedShopUrl,
	    `shop portal URL should resolve from the allowlisted account portal: ${JSON.stringify(portalHandoff)}`);
  assert(portalHandoff.opened.some((entry) => entry.kind === 'account' && entry.target === '_self' && entry.url === expectedAccountUrl),
    `Create Account should navigate current tab to account portal: ${JSON.stringify(portalHandoff.opened)}`);
  assert(portalHandoff.opened.some((entry) => entry.kind === 'recovery' && entry.target === '_self' && entry.url === expectedRecoveryUrl),
    `Recover account should navigate current tab to recovery portal: ${JSON.stringify(portalHandoff.opened)}`);
	  assert(portalHandoff.opened.some((entry) => entry.kind === 'shop' && entry.target === '_self' && entry.url === expectedShopUrl),
	    `Shop should navigate the current tab to the subscription storefront: ${JSON.stringify(portalHandoff.opened)}`);
	  assert(portalHandoff.accountOpened && portalHandoff.recoveryOpened && portalHandoff.shopOpened
	      && !portalHandoff.javascriptUrlOpened,
	    `portal resolver should open sentinels and reject unsafe URLs: ${JSON.stringify(portalHandoff)}`);
	  await drain(page);

		  const resourceScreenshot = path.join(outDir, 'iphone-controls-resource-gfx-off.png');
		  const resourceModeBeforeResume = await page.evaluate(() => {
		    document.body.classList.add('game-drawn', 'in-game');
		    window.__voidscapeInGame = true;
		    function box(selector) {
	      const element = document.querySelector(selector);
	      const style = getComputedStyle(element);
	      const rect = element.getBoundingClientRect();
	      return {
	        display: style.display,
	        visible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0,
	        left: Math.round(rect.left),
	        top: Math.round(rect.top),
	        right: Math.round(rect.right),
	        bottom: Math.round(rect.bottom),
	        width: Math.round(rect.width),
	        height: Math.round(rect.height)
	      };
	    }
	    const initial = window.__voidscapeCollectDiagnostics().resource;
	    const legacyLowMode = window.__voidscapeSetResourceMode('low-resource', 'smoke');
	    const legacyLow = window.__voidscapeCollectDiagnostics().resource;

	    const gfxMode = window.__voidscapeSetResourceMode('gfx-off', 'smoke');
	    window.__voidscapeForceResourceUpload('smoke-gfx-first');
	    const gfxFirstUpload = window.__voidscapePrepareFrameUpload(512, 872, false);
	    if (gfxFirstUpload) window.__voidscapeRecordFrameUpload(512, 872);
	    const gfxSkip = window.__voidscapePrepareFrameUpload(512, 872, false);
	    const gfx = window.__voidscapeCollectDiagnostics().resource;
	    const resourceModeControl = box('#resource-mode-control');
	    const resourceButtons = Array.from(document.querySelectorAll('.resource-mode-button')).map((element) => {
	      const mode = element.getAttribute('data-resource-mode') || '';
	      return { mode, ...box(`.resource-mode-button[data-resource-mode="${mode}"]`) };
	    });
	    const overlay = box('#low-resource-overlay');
	    const resumeButton = box('#resource-resume-button');
	    return {
	      initial,
	      legacyLowMode,
	      legacyLow,
	      gfxMode,
	      gfxFirstUpload,
	      gfxSkip,
	      gfx,
	      viewport: {
	        width: window.innerWidth || 0,
	        height: window.innerHeight || 0
	      },
	      resourceModeControl,
	      resourceButtons,
	      overlay,
	      resumeButton
	    };
	  });
	  await page.screenshot({ path: resourceScreenshot, fullPage: true });
	  const resourceResume = await page.evaluate(async () => {
	    function box(selector) {
	      const element = document.querySelector(selector);
	      const style = getComputedStyle(element);
	      const rect = element.getBoundingClientRect();
	      return {
	        display: style.display,
	        visible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0,
	        left: Math.round(rect.left),
	        top: Math.round(rect.top),
	        right: Math.round(rect.right),
	        bottom: Math.round(rect.bottom),
	        width: Math.round(rect.width),
	        height: Math.round(rect.height)
	      };
	    }
	    document.getElementById('resource-resume-button').dispatchEvent(new PointerEvent('pointerdown', {
	      bubbles: true,
	      cancelable: true,
	      pointerId: 91,
	      pointerType: 'touch',
	      button: 0,
	      buttons: 1
	    }));
	    await new Promise((resolve) => setTimeout(resolve, 80));
	    const resumed = window.__voidscapeCollectDiagnostics().resource;
	    const overlayAfterResume = box('#low-resource-overlay');
	    return {
	      resumed,
	      overlayAfterResume
	    };
	  });
	  const resourceModeSmoke = {
	    ...resourceModeBeforeResume,
	    ...resourceResume,
	    screenshot: resourceScreenshot
	  };
	  assert(resourceModeSmoke.initial.mode === 'normal',
	    `resource mode should default to normal: ${JSON.stringify(resourceModeSmoke)}`);
	  assert(resourceModeSmoke.resourceModeControl.visible,
	    `phone resource mode control should be visible: ${JSON.stringify(resourceModeSmoke.resourceModeControl)}`);
	  assert(resourceModeSmoke.resourceButtons.length === 2 && resourceModeSmoke.resourceButtons.every((button) => button.visible)
	      && resourceModeSmoke.resourceButtons.some((button) => button.mode === 'normal')
	      && resourceModeSmoke.resourceButtons.some((button) => button.mode === 'gfx-off'),
	    `phone resource mode should expose only Normal and GFX off buttons: ${JSON.stringify(resourceModeSmoke.resourceButtons)}`);
	  assert(resourceModeSmoke.resourceModeControl.bottom > resourceModeSmoke.viewport.height - 190
	      && resourceModeSmoke.resourceModeControl.right > resourceModeSmoke.viewport.width - 230,
	    `phone resource controls should sit near the bottom-right account area: ${JSON.stringify(resourceModeSmoke)}`);
	  assert(resourceModeSmoke.legacyLowMode === 'normal' && resourceModeSmoke.legacyLow.mode === 'normal'
	      && resourceModeSmoke.legacyLow.targetFps === 0,
	    `legacy low-resource requests should normalize to normal mode: ${JSON.stringify(resourceModeSmoke.legacyLow)}`);
	  assert(resourceModeSmoke.gfxMode === 'gfx-off' && resourceModeSmoke.gfx.mode === 'gfx-off',
	    `gfx-off mode should be settable: ${JSON.stringify(resourceModeSmoke)}`);
	  assert(resourceModeSmoke.gfxFirstUpload === true && resourceModeSmoke.gfxSkip === false,
	    `gfx-off mode should allow a forced transition frame then stop uploads: ${JSON.stringify(resourceModeSmoke)}`);
	  assert(resourceModeSmoke.overlay.visible && resourceModeSmoke.resumeButton.visible,
	    `gfx-off overlay and resume button should be visible: ${JSON.stringify(resourceModeSmoke)}`);
	  assert(resourceModeSmoke.resumed.mode === 'normal' && resourceModeSmoke.overlayAfterResume.display === 'none',
	    `resume button should restore normal rendering mode: ${JSON.stringify(resourceModeSmoke)}`);

	  const shellBeforeGame = await page.evaluate(() => {
    const rect = (selector) => {
      const element = document.querySelector(selector);
      if (!element) {
        return {
          selector,
          present: false,
          display: 'missing',
          left: 0,
          top: 0,
          right: 0,
          bottom: 0,
          width: 0,
          height: 0
        };
      }
      const box = element.getBoundingClientRect();
      return {
        selector,
        present: true,
        display: getComputedStyle(element).display,
        left: Math.round(box.left),
        top: Math.round(box.top),
        right: Math.round(box.right),
        bottom: Math.round(box.bottom),
        width: Math.round(box.width),
        height: Math.round(box.height)
      };
    };
    const display = (selector) => {
      const element = document.querySelector(selector);
      return element ? getComputedStyle(element).display : 'missing';
    };
    document.body.classList.add('game-drawn');
    document.body.classList.remove('in-game');
    window.__voidscapeInGame = false;
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
	      keyboard: display('#keyboard-button'),
	      inventory: display('#inventory-button'),
	      magic: display('#magic-button'),
	      prayer: display('#prayer-button'),
	      bestiary: display('#bestiary-button'),
	      panel: display('#panel-button'),
	      panelDrawer: display('#mobile-panel-drawer'),
	      chat: display('#chat-button'),
	      chatTray: display('#mobile-chat-tray'),
	      camera: display('#camera-controls'),
      diagnostics: display('#diagnostics-button'),
      diagnosticsCopy: display('#diagnostics-copy-button'),
      keyboardBox: rect('#keyboard-button'),
      diagnosticsBox: rect('#diagnostics-button'),
      diagnosticsCopyBox: rect('#diagnostics-copy-button')
    };
  });
  assert(shellBeforeGame.keyboard === 'flex', `keyboard button should be visible after draw, got ${shellBeforeGame.keyboard}`);
	  assert(shellBeforeGame.inventory === 'none', `inventory shortcut should be hidden before in-game, got ${shellBeforeGame.inventory}`);
	  assert(shellBeforeGame.magic === 'none', `magic shortcut should be hidden before in-game, got ${shellBeforeGame.magic}`);
	  assert(shellBeforeGame.prayer === 'none', `prayer shortcut should be hidden before in-game, got ${shellBeforeGame.prayer}`);
	  assert(shellBeforeGame.bestiary === 'missing', `bestiary shortcut should be absent before in-game, got ${shellBeforeGame.bestiary}`);
	  assert(shellBeforeGame.panel === 'none', `panel button should be hidden before in-game, got ${shellBeforeGame.panel}`);
	  assert(shellBeforeGame.panelDrawer === 'none', `panel drawer should be hidden before in-game, got ${shellBeforeGame.panelDrawer}`);
	  assert(shellBeforeGame.chat === 'none', `chat button should be hidden before in-game, got ${shellBeforeGame.chat}`);
	  assert(shellBeforeGame.chatTray === 'none', `chat tray should be hidden before in-game, got ${shellBeforeGame.chatTray}`);
	  assert(shellBeforeGame.camera === 'none', `camera controls should be hidden before in-game, got ${shellBeforeGame.camera}`);
  assert(shellBeforeGame.diagnostics === 'flex' && shellBeforeGame.diagnosticsCopy === 'flex', 'diagnostics controls should be visible under diag=1');
  assertInsideViewport(shellBeforeGame.keyboardBox, shellBeforeGame.viewport, 'pre-game keyboard button');
  assertInsideViewport(shellBeforeGame.diagnosticsBox, shellBeforeGame.viewport, 'pre-game diagnostics button');
  assertInsideViewport(shellBeforeGame.diagnosticsCopyBox, shellBeforeGame.viewport, 'pre-game diagnostics copy button');
  assertMinTouchTarget(shellBeforeGame.keyboardBox, 44, 44, 'pre-game keyboard button');
  assertMinTouchTarget(shellBeforeGame.diagnosticsBox, 44, 44, 'pre-game diagnostics button');
  assertMinTouchTarget(shellBeforeGame.diagnosticsCopyBox, 44, 44, 'pre-game diagnostics copy button');
  assert(shellBeforeGame.diagnosticsBox.top > shellBeforeGame.viewport.height * 0.7,
    `pre-game diagnostics button should dock below the login logo art: ${JSON.stringify(shellBeforeGame)}`);
  assert(shellBeforeGame.diagnosticsCopyBox.top > shellBeforeGame.viewport.height * 0.7,
    `pre-game diagnostics copy button should dock below the login logo art: ${JSON.stringify(shellBeforeGame)}`);
  assertNoOverlap(shellBeforeGame.diagnosticsBox, shellBeforeGame.keyboardBox, 'pre-game diagnostics/keyboard controls');
  assertNoOverlap(shellBeforeGame.diagnosticsCopyBox, shellBeforeGame.keyboardBox, 'pre-game diagnostics copy/keyboard controls');
  assertNoOverlap(shellBeforeGame.diagnosticsCopyBox, shellBeforeGame.diagnosticsBox, 'pre-game diagnostics controls');

  await resetState(page);
  const shellInGame = await page.evaluate(() => {
    const display = (selector) => {
      const element = document.querySelector(selector);
      return element ? getComputedStyle(element).display : 'missing';
	  };
    return {
	    keyboard: display('#keyboard-button'),
	    inventory: display('#inventory-button'),
	    magic: display('#magic-button'),
	    prayer: display('#prayer-button'),
	    bestiary: display('#bestiary-button'),
	    panel: display('#panel-button'),
	    panelDrawer: display('#mobile-panel-drawer'),
	    chat: display('#chat-button'),
	    chatTray: display('#mobile-chat-tray'),
	    camera: display('#camera-controls')
	  };
  });
  assert(shellInGame.keyboard === 'flex', `keyboard button should stay visible in-game, got ${shellInGame.keyboard}`);
	  assert(shellInGame.inventory === 'flex', `inventory shortcut should be visible in-game, got ${shellInGame.inventory}`);
	  assert(shellInGame.magic === 'flex', `magic shortcut should be visible in-game, got ${shellInGame.magic}`);
	  assert(shellInGame.prayer === 'flex', `prayer shortcut should be visible in-game, got ${shellInGame.prayer}`);
	  assert(shellInGame.bestiary === 'missing', `bestiary shortcut should be absent in-game, got ${shellInGame.bestiary}`);
	  assert(shellInGame.panel === 'none', `panel button should be hidden while Android-parity canvas tabs own panels, got ${shellInGame.panel}`);
	  assert(shellInGame.panelDrawer === 'none', `panel drawer should be closed by default, got ${shellInGame.panelDrawer}`);
		  assert(shellInGame.chat === 'none', `chat button should be hidden while canvas chat tabs own chat, got ${shellInGame.chat}`);
	  assert(shellInGame.chatTray === 'none', `chat tray should be closed by default, got ${shellInGame.chatTray}`);
	  assert(shellInGame.camera === 'grid', `camera controls should be visible in-game, got ${shellInGame.camera}`);

  const viewportMatrix = [
    { name: 'iphone-se-portrait', viewport: { width: 320, height: 568 } },
    { name: 'iphone-standard-portrait', viewport: { width: 390, height: 664 } },
    { name: 'iphone-plus-portrait', viewport: { width: 430, height: 746 } },
    { name: 'iphone-se-landscape', viewport: { width: 568, height: 320 } },
    { name: 'iphone-plus-landscape', viewport: { width: 932, height: 430 } }
  ];
  const viewportLayouts = [];
  let diagnosticsControlSnapshot = null;
  let diagnosticsControlHistory = null;
  for (const entry of viewportMatrix) {
    await resetState(page);
    const layout = await readControlLayout(page, entry.viewport);
    viewportLayouts.push({ name: entry.name, ...layout });
    assert(layout.keyboard.display === 'flex', `${entry.name}: keyboard button should be visible`);
	    assert(layout.panel.display === 'none', `${entry.name}: panel button should be hidden while Android-parity canvas tabs own panels`);
	    assert(layout.panelDrawer.display === 'none', `${entry.name}: panel drawer should be closed by default`);
    assert(layout.chat.display === 'none', `${entry.name}: chat button should be hidden while canvas chat tabs own chat`);
	    assert(layout.chatTray.display === 'none', `${entry.name}: chat tray should be closed by default`);
	    assert(layout.camera.display === 'grid', `${entry.name}: camera controls should be visible`);
    assert(layout.diagnostics.display === 'flex', `${entry.name}: diagnostics button should be visible`);
    assert(layout.diagnosticsCopy.display === 'flex', `${entry.name}: diagnostics copy button should be visible`);
    assertAndroidParityCanvasControls(layout, entry.name);
    assertInsideViewport(layout.keyboard, entry.viewport, `${entry.name}: keyboard button`);
	    assertInsideViewport(layout.camera, entry.viewport, `${entry.name}: camera controls`);
    assertInsideViewport(layout.diagnostics, entry.viewport, `${entry.name}: diagnostics button`);
    assertInsideViewport(layout.diagnosticsCopy, entry.viewport, `${entry.name}: diagnostics copy button`);
	    assertMinTouchTarget(layout.keyboard, 44, 44, `${entry.name}: keyboard button`);
	    assertMinTouchTarget(layout.diagnostics, 44, 44, `${entry.name}: diagnostics button`);
    assertMinTouchTarget(layout.diagnosticsCopy, 44, 44, `${entry.name}: diagnostics copy button`);
    const minCameraTarget = entry.viewport.width > entry.viewport.height ? 40 : 44;
    assert(layout.cameraButtons.length === 4, `${entry.name}: camera pad should have four buttons`);
    for (const button of layout.cameraButtons) {
      assertMinTouchTarget(button, minCameraTarget, minCameraTarget, `${entry.name}: camera ${button.label}`);
    }
	    assertAboveBottomHud(layout.keyboard, entry.viewport, `${entry.name}: keyboard button`);
	    assertAboveBottomHud(layout.camera, entry.viewport, `${entry.name}: camera controls`);
    assertNoOverlap(layout.camera, layout.keyboard, `${entry.name}: camera/keyboard controls`);
    const portrait = entry.viewport.width <= entry.viewport.height;
    if (portrait) {
      assertQuickShortcutRail(layout, entry.viewport, `${entry.name}: quick side rail`);
      for (const quick of [layout.inventory, layout.magic, layout.prayer]) {
        assertNoOverlap(layout.camera, quick, `${entry.name}: camera/${quick.selector} controls`);
        assertNoOverlap(layout.diagnostics, quick, `${entry.name}: diagnostics/${quick.selector} controls`);
        assertNoOverlap(layout.diagnosticsCopy, quick, `${entry.name}: diagnostics copy/${quick.selector} controls`);
      }
    } else {
      for (const quick of [layout.inventory, layout.magic, layout.prayer]) {
        assert(quick.display === 'none',
          `${entry.name}: quick side shortcut should hide in phone landscape: ${JSON.stringify(quick)}`);
      }
    }
    assertNoOverlap(layout.diagnostics, layout.keyboard, `${entry.name}: diagnostics/keyboard controls`);
    assertNoOverlap(layout.diagnosticsCopy, layout.keyboard, `${entry.name}: diagnostics copy/keyboard controls`);
    assertNoOverlap(layout.diagnosticsCopy, layout.diagnostics, `${entry.name}: diagnostics controls`);
    diagnosticsControlSnapshot = await page.evaluate(() => window.__voidscapeCollectDiagnostics().controls);
    const expectedDisplays = portrait
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
      const control = diagnosticsControlSnapshot[key];
      assert(control && control.present,
        `${entry.name}: diagnostics controls.${key} should be present: ${JSON.stringify(control)}`);
      assert(control.visible === (expectedDisplay !== 'none'),
        `${entry.name}: diagnostics controls.${key} visibility should match ${expectedDisplay}: ${JSON.stringify(control)}`);
      assert(control.display === expectedDisplay,
        `${entry.name}: diagnostics controls.${key} display should be ${expectedDisplay}: ${JSON.stringify(control)}`);
      if (expectedDisplay !== 'none') {
        assert(control.hitSelf === true,
          `${entry.name}: diagnostics controls.${key} should be topmost at its center: ${JSON.stringify(control)}`);
        assert(control.rect && control.rect.width > 0 && control.rect.height > 0,
          `${entry.name}: diagnostics controls.${key} should include a nonzero rect: ${JSON.stringify(control)}`);
      }
    }
    const bestiaryDiagnosticControl = diagnosticsControlSnapshot.bestiaryButton;
    assert(!bestiaryDiagnosticControl || bestiaryDiagnosticControl.present === false,
      `${entry.name}: diagnostics controls.bestiaryButton should be absent: ${JSON.stringify(bestiaryDiagnosticControl)}`);
    const panelControl = diagnosticsControlSnapshot.panelButton;
    assert(panelControl && panelControl.present,
      `${entry.name}: diagnostics controls.panelButton should be present for diagnostics: ${JSON.stringify(panelControl)}`);
    assert(panelControl.display === 'none' && panelControl.visible === false,
      `${entry.name}: diagnostics controls.panelButton should be hidden while Android-parity canvas tabs own panels: ${JSON.stringify(panelControl)}`);
    const chatControl = diagnosticsControlSnapshot.chatButton;
    assert(chatControl && chatControl.present,
      `${entry.name}: diagnostics controls.chatButton should be present for diagnostics: ${JSON.stringify(chatControl)}`);
    assert(chatControl.display === 'none' && chatControl.visible === false,
      `${entry.name}: diagnostics controls.chatButton should be hidden while canvas chat tabs own chat: ${JSON.stringify(chatControl)}`);
    assertAndroidParityCanvasControls(diagnosticsControlSnapshot, `${entry.name}: diagnostics controls`);
  }
  diagnosticsControlHistory = await page.evaluate(() => window.__voidscapeCollectDiagnostics().controlsHistory);
  assertDiagnosticsControlHistory(diagnosticsControlHistory, 'viewport matrix');
  const landscapeFill = await runLandscapeFillSmoke(page);

  const dialogSafeLayouts = [];
  const portraitDialogLayout = await readDialogSafeLayout(page, { width: 390, height: 664 }, 'welcome');
  const portraitDialogRect = { left: 70, top: 250, right: 320, bottom: 480 };
  assertDialogSafeLayout(portraitDialogLayout, portraitDialogRect, 'portrait blocking dialog controls');
  dialogSafeLayouts.push({
    name: 'portrait-blocking-dialog',
    dialogRect: portraitDialogRect,
    ...portraitDialogLayout
  });

  const landscapeDialogLayout = await readDialogSafeLayout(page, { width: 844, height: 390 }, 'wildernessWarning');
  const landscapeDialogRect = { left: 222, top: 118, right: 622, bottom: 286 };
  assertDialogSafeLayout(landscapeDialogLayout, landscapeDialogRect, 'landscape blocking dialog controls');
  dialogSafeLayouts.push({
    name: 'landscape-blocking-dialog',
    dialogRect: landscapeDialogRect,
    ...landscapeDialogLayout
  });

  const overlaySuppression = await runOverlaySuppressionSmoke(page);

  await page.setViewportSize({ width: 390, height: 664 });
  await resetState(page);

  let events = await openKeyboard(page);
  assertIncludes(events, 's,1', 'keyboard focus');
  let keyboardState = await page.evaluate(() => ({
    wanted: window.__voidscapeKeyboardWanted,
    open: window.__voidscapeKeyboardOpen,
    bodyClass: document.body.className,
    viewportHeight: getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim()
  }));
  assert(keyboardState.wanted && keyboardState.open && keyboardState.bodyClass.includes('keyboard-open'), `keyboard should be open: ${JSON.stringify(keyboardState)}`);

  const originalViewportHeight = keyboardState.viewportHeight;
  await page.setViewportSize({ width: 390, height: 420 });
  await page.waitForTimeout(250);
  const frozenHeight = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim());
  assert(frozenHeight === originalViewportHeight, `keyboard-open viewport should stay frozen at ${originalViewportHeight}, got ${frozenHeight}`);
  await page.setViewportSize({ width: 390, height: 664 });

  await page.evaluate(() => {
    const capture = document.getElementById('keyboard-capture');
    capture.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: 'A\nB😀£' }));
    capture.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'deleteContentBackward', data: null }));
    capture.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertLineBreak', data: null }));
  });
  events = await drain(page);
  ['t,65', 't,32', 't,66', 't,163', 'k,1,8', 'k,1,10'].forEach((expected) => assertIncludes(events, expected, 'keyboard input normalization'));
  assertNoMatch(events, /^t,553/, 'keyboard input should filter emoji surrogate units');
  const keyboardBeforeInputText = textFromEvents(events);

  await page.evaluate(() => {
    const capture = document.getElementById('keyboard-capture');
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: {
        getData: () => 'Paste\nA😀£'
      }
    });
    capture.dispatchEvent(event);
  });
  events = await drain(page);
  assertTextEvents(events, 'Paste A£', 'keyboard paste normalization');
  assertNoMatch(events, /^t,553/, 'keyboard paste should filter emoji surrogate units');
  const keyboardPasteText = textFromEvents(events);

  await page.evaluate(() => {
    const capture = document.getElementById('keyboard-capture');
    const makeCompositionEvent = (type, data) => {
      if (typeof CompositionEvent === 'function') {
        return new CompositionEvent(type, { bubbles: true, cancelable: true, data });
      }
      const event = new Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(event, 'data', { value: data });
      return event;
    };
    capture.dispatchEvent(makeCompositionEvent('compositionstart', ''));
    capture.dispatchEvent(makeCompositionEvent('compositionend', 'Compose OK😀£'));
  });
  events = await drain(page);
  assertTextEvents(events, 'Compose OK£', 'keyboard composition commit normalization');
  assertNoMatch(events, /^t,553/, 'keyboard composition should filter emoji surrogate units');
  const keyboardCompositionText = textFromEvents(events);

  events = await closeKeyboard(page);
  assertIncludes(events, 's,0', 'keyboard blur');
  let keyboardCloseGrace = await page.evaluate(() => ({
    closingFrozen: !!window.__voidscapeViewportKeyboardClosingFrozen,
    closingUntil: window.__voidscapeKeyboardClosingUntil || 0,
    viewportHeight: getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim()
  }));
  assert(keyboardCloseGrace.closingFrozen === true,
    `keyboard close should enter a short viewport freeze: ${JSON.stringify(keyboardCloseGrace)}`);
  await page.setViewportSize({ width: 390, height: 430 });
  await page.evaluate(() => {
    if (window.__voidscapeUpdateViewportHeight) window.__voidscapeUpdateViewportHeight();
  });
  await page.waitForTimeout(140);
  const closeGraceHeight = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim());
  assert(closeGraceHeight === originalViewportHeight,
    `keyboard-close grace should ignore transient viewport height and stay at ${originalViewportHeight}, got ${closeGraceHeight}`);
  await page.waitForTimeout(560);
  keyboardCloseGrace = await page.evaluate(() => {
    if (window.__voidscapeUpdateViewportHeight) window.__voidscapeUpdateViewportHeight();
    return {
      closingFrozen: !!window.__voidscapeViewportKeyboardClosingFrozen,
      closingUntil: window.__voidscapeKeyboardClosingUntil || 0,
      viewportHeight: getComputedStyle(document.documentElement).getPropertyValue('--viewport-height').trim()
    };
  });
  assert(keyboardCloseGrace.closingFrozen === false,
    `keyboard-close grace should expire after visual viewport settles: ${JSON.stringify(keyboardCloseGrace)}`);
  await page.setViewportSize({ width: 390, height: 664 });
  await page.evaluate(() => {
    if (window.__voidscapeUpdateViewportHeight) window.__voidscapeUpdateViewportHeight();
  });
  await page.waitForTimeout(120);

  await resetState(page);
  await tap(page, 128, 160);
  events = await drain(page);
  ['p,0,128,160,0', 'p,1,128,160,1', 'p,2,128,160,1'].forEach((expected) => assertIncludes(events, expected, 'tap release'));

  await resetState(page);
  await pointer(page, 'pointerdown', 176, 208, { buttons: 1 });
  const longPressWaitMillis = 300;
  await page.waitForTimeout(longPressWaitMillis);
  events = await drain(page);
  assertIncludes(events, 'p,0,176,208,0', 'long-press down');
  assertIncludes(events, 'p,3,176,208,2', 'Android-parity long-press secondary down by 300ms');
  const longPressDownEvents = events.slice();
  await pointer(page, 'pointerup', 176, 208, { buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'p,4,176,208,0', 'long-press secondary release should clear without selecting');
  assertNoMatch(events, /^p,1,176,208,1$/, 'long-press should not also release as a primary tap');
  const longPressReleaseEvents = events.slice();
  const longPressMillis = await page.evaluate(() => window.__voidscapeLongPressMillis || null);
  assert(longPressMillis === 250, `web long-press timing should mirror Android default 250ms, got ${longPressMillis}`);

  await resetState(page);
  await page.evaluate(() => { window.__voidscapeLongPressMillis = 900; });
  await pointer(page, 'pointerdown', 188, 214, { buttons: 1 });
  const contextMenuPoint = await clientPoint(page, 188, 214);
  await page.evaluate(({ point }) => {
    const canvas = document.getElementById('game');
    canvas.dispatchEvent(new MouseEvent('contextmenu', {
      bubbles: true,
      cancelable: true,
      clientX: point.x,
      clientY: point.y,
      button: 2,
      buttons: 0
    }));
  }, { point: contextMenuPoint });
  events = await drain(page);
  assert(events.some((event) => /^p,3,\d+,\d+,2$/.test(event)),
    `Safari contextmenu fallback should open long-press context action: ${JSON.stringify(events)}`);
  await pointer(page, 'pointerup', 188, 214, { buttons: 0 });
  events = await drain(page);
  assert(events.some((event) => /^p,4,\d+,\d+,0$/.test(event)),
    `Safari contextmenu fallback release should clear held context action: ${JSON.stringify(events)}`);
  assertNoMatch(events, /^p,1,188,214,1$/, 'Safari contextmenu fallback should not also release as a primary tap');

  await resetState(page);
  await page.evaluate(() => { window.__voidscapeLongPressMillis = 700; });
  await pointer(page, 'pointerdown', 192, 216, { buttons: 1 });
  await page.waitForTimeout(650);
  await pointer(page, 'pointercancel', 192, 216, { buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'p,3,192,216,2', 'late pointercancel fallback should open long-press context action');
  assertIncludes(events, 'p,4,192,216,0', 'late pointercancel fallback should clear held context action');
  assertNoMatch(events, /^p,1,192,216,1$/, 'late pointercancel fallback should not release as a primary tap');

  await resetState(page);
  await pointer(page, 'pointerdown', 140, 180, { buttons: 1 });
  await pointer(page, 'pointermove', 154, 190, { buttons: 1 });
  await page.waitForTimeout(660);
  await pointer(page, 'pointerup', 154, 190, { buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'p,3,154,190,2', 'small drift hold should still open long-press context action');
  assertIncludes(events, 'p,4,154,190,0', 'small drift long-press release should clear without selecting');
  assertNoMatch(events, /^p,1,154,190,1$/, 'small drift long-press should not release as a primary tap');

  await resetState(page);
  await pointer(page, 'pointerdown', 140, 180, { buttons: 1 });
  await pointer(page, 'pointermove', 176, 180, { buttons: 1 });
  await page.waitForTimeout(660);
  await pointer(page, 'pointerup', 176, 180, { buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'p,3,176,180,2', 'phone-scale thumb wobble should still open long-press context action');
  assertIncludes(events, 'p,4,176,180,0', 'phone-scale thumb wobble long-press release should clear without selecting');
  assertNoMatch(events, /^p,1,176,180,1$/, 'phone-scale thumb wobble should not release as a primary tap');

  await resetState(page);
  await pointer(page, 'pointerdown', 200, 220, { buttons: 1 });
  await pointer(page, 'pointermove', 275, 220, { buttons: 1 });
  await page.waitForTimeout(240);
  await pointer(page, 'pointerup', 275, 220, { buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'k,1,39', 'right camera drag');
  assertIncludes(events, 'k,0,39', 'right camera drag release');
  assertNoMatch(events, /^p,1,/, 'camera drag should not release as primary tap');

  await resetState(page);
  await pointer(page, 'pointerdown', 220, 260, { buttons: 1 });
  await pointer(page, 'pointermove', 220, 190, { buttons: 1 });
  await page.waitForTimeout(240);
  await pointer(page, 'pointerup', 220, 190, { buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'k,1,38', 'upward camera drag zoom-in');
  assertIncludes(events, 'k,0,38', 'upward camera drag release');
  assertNoMatch(events, /^p,1,/, 'vertical camera drag should not release as primary tap');

  await resetState(page);
  await page.evaluate(() => { window.__voidscapeScrollableUiActive = true; });
  await pointer(page, 'pointerdown', 240, 420, { buttons: 1 });
  await pointer(page, 'pointermove', 240, 330, { buttons: 1 });
  await pointer(page, 'pointerup', 240, 330, { buttons: 0 });
  events = await drain(page);
  assert(events.some((event) => /^w,[1-9]/.test(event)), `scrollable panel drag should queue positive scroll ticks: ${JSON.stringify(events)}`);
  assertNoMatch(events, /^k,1,(37|38|39|40)$/, 'scrollable panel drag should not rotate camera');

  await resetState(page);
  await pointer(page, 'pointerdown', 170, 260, { pointerId: 1, isPrimary: true, buttons: 1 });
  await pointer(page, 'pointerdown', 250, 260, { pointerId: 2, isPrimary: false, buttons: 1 });
  await pointer(page, 'pointermove', 300, 260, { pointerId: 2, isPrimary: false, buttons: 1 });
  await page.waitForTimeout(240);
  await pointer(page, 'pointerup', 300, 260, { pointerId: 2, isPrimary: false, buttons: 0 });
  await pointer(page, 'pointerup', 170, 260, { pointerId: 1, isPrimary: true, buttons: 0 });
  events = await drain(page);
  assertIncludes(events, 'k,1,38', 'pinch-out zoom-in');
  assertIncludes(events, 'k,0,38', 'pinch-out zoom-in release');
  assertNoMatch(events, /^p,1,/, 'pinch should not release as primary tap');
  await page.waitForTimeout(420);

  await resetState(page);
  await page.evaluate(() => {
    const canvas = document.getElementById('game');
    const rect = canvas.getBoundingClientRect();
    const dispatch = (type, frameX, frameY, buttons) => {
      const event = new PointerEvent(type, {
        bubbles: true,
        cancelable: true,
        pointerId: 91,
        pointerType: 'touch',
        isPrimary: true,
        clientX: rect.left + frameX * rect.width / canvas.width,
        clientY: rect.top + frameY * rect.height / canvas.height,
        button: 0,
        buttons
      });
      canvas.dispatchEvent(event);
    };
    window.__voidscapeTopMenuVisible = true;
    dispatch('pointerdown', 180, 210, 1);
    dispatch('pointermove', 185, 228, 1);
    dispatch('pointerup', 185, 228, 0);
  });
  events = await drain(page);
  assertIncludes(events, 'm,185,228', 'top menu touch release');
  assertNoMatch(events, /^p,/, 'top menu touch should not leak pointer events');

	  const mobileQuickShortcuts = await runMobileQuickShortcutSmoke(page);
	  const mobilePanelDrawer = await runMobilePanelDrawerSmoke(page);
	  const mobileChatTray = await runMobileChatTraySmoke(page);

	  await resetState(page);
  await page.evaluate(() => {
    history.back();
  });
  await page.waitForTimeout(350);
  events = await drain(page);
  assert(events.filter((event) => event === 'b,1').length === 1, `browser Back should queue one Back event: ${JSON.stringify(events)}`);

  await resetState(page);
  await page.keyboard.press('Escape');
  events = await drain(page);
  const externalEscapeBackEvents = events.slice();
  assert(externalEscapeBackEvents.filter((event) => event === 'b,1').length === 1,
    `external Escape should queue one shared Back event: ${JSON.stringify(externalEscapeBackEvents)}`);
  assertNoMatch(externalEscapeBackEvents, /^k,[01],27$/, 'external Escape should not leak raw Escape key events');

  const lifecycleResume = await page.evaluate(async () => {
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
  assert(lifecycleResume.after.resumeCount > lifecycleResume.before.resumeCount,
    `lifecycle resume should increment resumeCount: ${JSON.stringify(lifecycleResume)}`);
  assert(lifecycleResume.after.pageShowCount > lifecycleResume.before.pageShowCount,
    `lifecycle pageshow should be recorded: ${JSON.stringify(lifecycleResume)}`);
  assert(lifecycleResume.after.focusCount > lifecycleResume.before.focusCount,
    `lifecycle focus should be recorded: ${JSON.stringify(lifecycleResume)}`);
  assert(lifecycleResume.after.viewportUpdateCount > lifecycleResume.before.viewportUpdateCount,
    `lifecycle resume should refresh viewport height: ${JSON.stringify(lifecycleResume)}`);
  assert(lifecycleResume.viewport.scrollX === 0 && lifecycleResume.viewport.scrollY === 0,
    `lifecycle resume should leave page scroll locked: ${JSON.stringify(lifecycleResume.viewport)}`);

  await resetState(page);
  const copyReport = await page.evaluate(async () => {
    if (window.navigator.clipboard) {
      window.navigator.clipboard.writeText = function () {
        return Promise.reject(new Error('forced clipboard failure'));
      };
    }
    document.execCommand = function () {
      return false;
    };
    const button = document.getElementById('diagnostics-copy-button');
    button.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerId: 55, pointerType: 'touch', button: 0, buttons: 1 }));
    await new Promise((resolve) => setTimeout(resolve, 80));
    const exportField = document.getElementById('diagnostics-export');
    const bubbled = [];
    const mark = (event) => bubbled.push(event.type);
    ['pointerdown', 'pointerup', 'click', 'dblclick', 'keydown', 'keyup', 'touchstart', 'touchmove', 'touchend', 'touchcancel']
      .forEach((type) => document.addEventListener(type, mark, { once: true }));
    exportField.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerId: 56, pointerType: 'touch', button: 0, buttons: 1 }));
    exportField.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true, pointerId: 56, pointerType: 'touch', button: 0, buttons: 0 }));
    exportField.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    exportField.dispatchEvent(new MouseEvent('dblclick', { bubbles: true, cancelable: true }));
    exportField.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'a' }));
    exportField.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true, key: 'a' }));
    ['touchstart', 'touchmove', 'touchend', 'touchcancel']
      .forEach((type) => exportField.dispatchEvent(new Event(type, { bubbles: true, cancelable: true })));
    return {
      label: button.textContent,
      exportDisplay: getComputedStyle(exportField).display,
      exportValue: exportField.value,
      exportVisible: !!window.__voidscapeDiagnosticsExportVisible,
      exportLength: window.__voidscapeDiagnosticsExportLength || 0,
      bubbled,
      snapshot: window.__voidscapeDiagnosticsSnapshot || window.__voidscapeCollectDiagnostics()
    };
  });
  assert(copyReport.label === 'select', `diagnostics blocked-copy fallback should prompt selection, got ${copyReport.label}`);
  assert(copyReport.exportDisplay === 'block' && copyReport.exportVisible, `diagnostics export fallback should be visible: ${JSON.stringify(copyReport)}`);
  assert(copyReport.exportLength > 200 && copyReport.exportValue.includes('"snapshot"'), 'diagnostics export fallback should contain the JSON report');
  assert(copyReport.bubbled.length === 0, `diagnostics export interactions should not bubble to game document: ${JSON.stringify(copyReport.bubbled)}`);
  assert(!copyReport.snapshot.recentErrors.some((entry) => entry.includes('diagnostics copy failed')),
    `blocked clipboard should not dirty recentErrors: ${JSON.stringify(copyReport.snapshot.recentErrors)}`);
  assert(copyReport.snapshot && copyReport.snapshot.touchProfile === true, 'diagnostics copy should refresh snapshot');

  const telemetryReport = await page.evaluate(async () => {
    console.error('voidscape smoke synthetic console diagnostic');
    const link = document.createElement('link');
    link.href = 'synthetic-resource-diagnostic.css';
    document.body.appendChild(link);
    link.dispatchEvent(new Event('error'));
    await new Promise((resolve) => setTimeout(resolve, 80));
    return window.__voidscapeCollectDiagnostics();
  });
  assert(telemetryReport.recentErrors.some((entry) => entry.includes('console.error: voidscape smoke synthetic console diagnostic')),
    `diagnostics should capture console.error entries: ${JSON.stringify(telemetryReport.recentErrors)}`);
  assert(telemetryReport.recentErrors.some((entry) => entry.includes('resource error: LINK') && entry.includes('synthetic-resource-diagnostic.css')),
    `diagnostics should capture resource load errors: ${JSON.stringify(telemetryReport.recentErrors)}`);

  const screenshot = path.join(outDir, 'iphone-controls-smoke.png');
  await page.screenshot({ path: screenshot, fullPage: true });
  await browser.close();

  const unexpectedFailures = failed.filter((failure) => !failure.includes('ws://127.0.0.1:65534/'));
  const ignoredNetworkFailures = failed.filter((failure) => failure.includes('ws://127.0.0.1:65534/'));

  const summary = {
    baseUrl,
    runtimeModes,
    controls: {
      shellBeforeGame,
      shellInGame,
      viewportLayouts,
      dialogSafeLayouts,
      keyboardViewportFrozen: frozenHeight === originalViewportHeight,
      keyboardViewportCloseGrace: {
        frozenHeight: closeGraceHeight,
        finalState: keyboardCloseGrace
      },
      diagnosticsControlSnapshot,
      diagnosticsControlHistory,
      portalHandoff,
      keyboardTextPaths: {
        beforeInput: keyboardBeforeInputText,
        paste: keyboardPasteText,
        composition: keyboardCompositionText
	      },
	      longPressTiming: {
	        configuredMillis: longPressMillis,
	        waitMillis: longPressWaitMillis,
	        downEvents: longPressDownEvents,
	        releaseEvents: longPressReleaseEvents
	      },
      mobileQuickShortcuts,
      mobilePanelDrawer,
	      mobileChatTray,
	      overlaySuppression,
	      landscapeFill,
		      resourceModeSmoke,
	      diagnosticsCopyLabel: copyReport.label,
      diagnosticsTelemetryTail: telemetryReport.recentErrors,
      externalEscapeBack: externalEscapeBackEvents,
      lifecycleResume
    },
    failedRequests: unexpectedFailures,
    ignoredNetworkFailures,
    consoleTail: consoleMessages.slice(-12),
    screenshot
  };
  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
  assert(unexpectedFailures.length === 0, `unexpected request failures observed: ${unexpectedFailures.join('; ')}`);
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
NODE

echo
echo "iPhone TeaVM controls smoke passed."
echo "Artifacts: $OUT_DIR"
