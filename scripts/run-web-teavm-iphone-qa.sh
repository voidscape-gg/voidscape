#!/usr/bin/env bash
# Serve the TeaVM iPhone client on the LAN and create a real-device QA report.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8088}"
BIND_HOST="${BIND_HOST:-0.0.0.0}"
WS_PORT="${WS_PORT:-43496}"
LAN_IP_OVERRIDE="${WEB_TEA_IPHONE_QA_LAN_IP:-}"
BUILD=1
PRINT_ONLY=0
OUT_DIR="${WEB_TEA_IPHONE_QA_OUT:-$ROOT/tmp/iphone-web-qa}"
BASE_URL="${WEB_TEA_IPHONE_QA_BASE_URL:-}"
WS_URL="${WEB_TEA_IPHONE_QA_WS_URL:-}"
PORTAL_URL="${WEB_TEA_IPHONE_QA_PORTAL_URL:-}"
PORTAL_ACCOUNT_URL="${WEB_TEA_IPHONE_QA_PORTAL_ACCOUNT_URL:-}"
PORTAL_RECOVERY_URL="${WEB_TEA_IPHONE_QA_PORTAL_RECOVERY_URL:-}"
DEPLOYMENT_SUMMARY_FILE="${WEB_TEA_IPHONE_QA_DEPLOYMENT_SUMMARY:-}"
DEPLOYMENT_SUMMARY_JSON=""

usage() {
	cat <<'EOF'
Usage: scripts/run-web-teavm-iphone-qa.sh [options]

Options:
  --no-build          Reuse existing Web_Client_TeaVM/target/teavm output.
  --base-url URL      Create a report for an already deployed HTTPS web root.
  --ws URL            Explicit deployed WebSocket URL, e.g. wss://host/ws/.
  --portal URL        Portal base URL for Create Account/Recover account.
  --portal-account-url URL
                      Explicit Create Account portal URL.
  --portal-recovery-url URL
                      Explicit Recover account portal URL.
  --deployment-summary FILE
                      Prefill the Deployment Verification JSON block from a
                      hosted verify-web-teavm-deployment.sh summary.json.
  --port PORT         Local static-server port. Default: 8088.
  --bind HOST         Static-server bind host. Default: 0.0.0.0.
  --ws-port PORT      Game WebSocket port. Default: 43496.
  --lan-ip IP         Explicit Mac LAN IP for physical iPhone URLs.
                      Use this when auto-detection falls back to 127.0.0.1.
  --out DIR           Directory for the QA report. Default: tmp/iphone-web-qa.
  --print-only        Write/print the report without starting the web server.
  -h, --help          Show this help.

Run scripts/run-server.sh in another terminal first. Open the printed iPhone URL
from an iPhone on the same Wi-Fi as this Mac.

For deployed real-device QA, use:
  scripts/run-web-teavm-iphone-qa.sh --base-url https://play.example.com/ --ws wss://play.example.com/ws/
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--base-url)
			BASE_URL="${2:-}"
			BUILD=0
			PRINT_ONLY=1
			shift 2
			;;
		--ws)
			WS_URL="${2:-}"
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
		--deployment-summary)
			DEPLOYMENT_SUMMARY_FILE="${2:-}"
			shift 2
			;;
		--port)
			PORT="${2:-}"
			shift 2
			;;
		--bind)
			BIND_HOST="${2:-}"
			shift 2
			;;
		--ws-port)
			WS_PORT="${2:-}"
			shift 2
			;;
		--lan-ip)
			LAN_IP_OVERRIDE="${2:-}"
			shift 2
			;;
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--print-only)
			PRINT_ONLY=1
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

if [[ -z "$PORT" || -z "$BIND_HOST" || -z "$WS_PORT" || -z "$OUT_DIR" ]]; then
	echo "ERROR: port, bind, ws-port, and out must be non-empty." >&2
	exit 2
fi
if [[ -n "$DEPLOYMENT_SUMMARY_FILE" ]]; then
	if [[ ! -f "$DEPLOYMENT_SUMMARY_FILE" ]]; then
		echo "ERROR: deployment summary not found: $DEPLOYMENT_SUMMARY_FILE" >&2
		exit 2
	fi
	DEPLOYMENT_SUMMARY_JSON="$(cat "$DEPLOYMENT_SUMMARY_FILE")"
fi

DEPLOYED_MODE=0
if [[ -n "$BASE_URL" ]]; then
	DEPLOYED_MODE=1
fi
if [[ "$DEPLOYED_MODE" -eq 0 && "$LAN_IP_OVERRIDE" == "127.0.0.1" ]]; then
	echo "ERROR: --lan-ip must be this Mac's LAN IP, not 127.0.0.1, for physical iPhone testing." >&2
	exit 2
fi

detect_lan_ip() {
	local candidate
	local iface
	for iface in en0 en1 en2 en3 en4 en5; do
		candidate="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
		if [[ "$candidate" =~ ^[0-9]+([.][0-9]+){3}$ && ! "$candidate" =~ ^127[.] && ! "$candidate" =~ ^169[.]254[.] ]]; then
			printf "%s\n" "$candidate"
			return 0
		fi
	done

	candidate="$(ifconfig 2>/dev/null | awk '$1 == "inet" && $2 !~ /^127[.]/ && $2 !~ /^169[.]254[.]/ { print $2; exit }')"
	if [[ "$candidate" =~ ^[0-9]+([.][0-9]+){3}$ ]]; then
		printf "%s\n" "$candidate"
		return 0
	fi

	return 1
}

if [[ "$DEPLOYED_MODE" -eq 1 ]]; then
	eval "$(python3 - "$BASE_URL" "$WS_URL" "$PORTAL_URL" "$PORTAL_ACCOUNT_URL" "$PORTAL_RECOVERY_URL" <<'PY'
from urllib.parse import urlencode, urljoin, urlparse, urlunparse
import shlex
import sys

base_url = sys.argv[1].strip()
ws_url = sys.argv[2].strip()
portal_url = sys.argv[3].strip()
portal_account_url = sys.argv[4].strip()
portal_recovery_url = sys.argv[5].strip()
parsed = urlparse(base_url)
if parsed.scheme not in {"http", "https"} or not parsed.netloc:
    print(f"echo 'ERROR: --base-url must be an absolute http(s) URL.' >&2")
    print("exit 2")
    raise SystemExit
if parsed.query or parsed.fragment:
    print(f"echo 'ERROR: --base-url must not include query or fragment.' >&2")
    print("exit 2")
    raise SystemExit
if ws_url:
    ws_parsed = urlparse(ws_url)
    if ws_parsed.scheme not in {"ws", "wss"} or not ws_parsed.netloc:
        print(f"echo 'ERROR: --ws must be an absolute ws:// or wss:// URL.' >&2")
        print("exit 2")
        raise SystemExit

if not parsed.path.endswith("/"):
    parsed = parsed._replace(path=(parsed.path or "") + "/")
base_url = parsed.geturl()
index_url = urljoin(base_url, "index.html")

def with_query(**params):
    return index_url + "?" + urlencode(params)

def add_portal_params(params):
    if portal_url:
        params["portal"] = portal_url
    if portal_account_url:
        params["portalAccountUrl"] = portal_account_url
    if portal_recovery_url:
        params["portalRecoveryUrl"] = portal_recovery_url
    return params

def resolve_http_url(value):
    absolute = urljoin(index_url, value)
    resolved = urlparse(absolute)
    if resolved.scheme not in {"http", "https"} or not resolved.netloc:
        print(f"echo 'ERROR: portal URL must resolve to http(s): {value}' >&2")
        print("exit 2")
        raise SystemExit
    return urlunparse(resolved)

def portal_url_from_base(value, fragment):
    resolved = urlparse(resolve_http_url(value or "/"))
    return urlunparse(resolved._replace(fragment=fragment))

normal_params = {"mobile": "1"}
diag_params = {"mobile": "1", "diag": "1"}
if ws_url:
    normal_params["ws"] = ws_url
    diag_params["ws"] = ws_url
add_portal_params(normal_params)
add_portal_params(diag_params)

host = parsed.hostname or ""
display_host = f"[{host}]" if ":" in host and not host.startswith("[") else host
if ws_url:
    expected_ws = ws_url
elif parsed.scheme == "https":
    expected_ws = f"wss://{display_host}:{parsed.port or 443}/"
else:
    expected_ws = f"ws://{display_host}:43496/"

values = {
    "BASE_URL": base_url,
    "NORMAL_URL": with_query(**normal_params),
    "DIAG_URL": with_query(**diag_params),
    "RESET_URL": with_query(mobile="1", endpoint="reset"),
    "RESET_PORTAL_URL": with_query(mobile="1", resetPortal="1"),
    "HOME_SCREEN_URL": with_query(mobile="1"),
    "MAC_URL": with_query(**normal_params),
    "EXPECTED_EFFECTIVE_WS": expected_ws,
    "EXPECTED_PORTAL_ACCOUNT_URL": resolve_http_url(portal_account_url) if portal_account_url else portal_url_from_base(portal_url or "/", "account"),
    "EXPECTED_PORTAL_RECOVERY_URL": resolve_http_url(portal_recovery_url) if portal_recovery_url else portal_url_from_base(portal_url or "/", "security"),
}
for key, value in values.items():
    print(f"{key}={shlex.quote(value)}")
PY
)"
	LAN_IP="$(python3 - "$BASE_URL" <<'PY'
from urllib.parse import urlparse
import sys
print(urlparse(sys.argv[1]).hostname or "")
PY
)"
	WS_STATUS="not checked - deployed endpoint"
	TARGET="deployed web root: $BASE_URL"
elif [[ "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"

fi

if [[ "$DEPLOYED_MODE" -eq 0 ]]; then
	TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
	if [[ ! -f "$TARGET/index.html" ]]; then
		echo "ERROR: missing TeaVM output at $TARGET/index.html. Run scripts/build-web-teavm-spike.sh first." >&2
		exit 1
	fi

	LAN_IP="$LAN_IP_OVERRIDE"
	if [[ -z "$LAN_IP" ]]; then
		LAN_IP="$(detect_lan_ip || true)"
	fi
	if [[ -z "$LAN_IP" ]]; then
		LAN_IP="127.0.0.1"
	fi

	eval "$(python3 - "$LAN_IP" "$PORT" "$WS_PORT" "$PORTAL_URL" "$PORTAL_ACCOUNT_URL" "$PORTAL_RECOVERY_URL" <<'PY'
from urllib.parse import urlencode, urljoin, urlparse, urlunparse
import shlex
import sys

lan_ip, port, ws_port, portal_url, portal_account_url, portal_recovery_url = [arg.strip() for arg in sys.argv[1:]]
base_url = f"http://{lan_ip}:{port}/"
index_url = urljoin(base_url, "index.html")

def with_query(**params):
    return index_url + "?" + urlencode(params)

def add_portal_params(params):
    if portal_url:
        params["portal"] = portal_url
    if portal_account_url:
        params["portalAccountUrl"] = portal_account_url
    if portal_recovery_url:
        params["portalRecoveryUrl"] = portal_recovery_url
    return params

def resolve_http_url(value):
    absolute = urljoin(index_url, value)
    resolved = urlparse(absolute)
    if resolved.scheme not in {"http", "https"} or not resolved.netloc:
        print(f"echo 'ERROR: portal URL must resolve to http(s): {value}' >&2")
        print("exit 2")
        raise SystemExit
    return urlunparse(resolved)

def portal_url_from_base(value, fragment):
    resolved = urlparse(resolve_http_url(value or "/"))
    return urlunparse(resolved._replace(fragment=fragment))

normal_params = add_portal_params({"mobile": "1", "host": lan_ip, "port": ws_port})
diag_params = add_portal_params({"mobile": "1", "diag": "1", "host": lan_ip, "port": ws_port})
mac_params = add_portal_params({"mobile": "1", "host": "127.0.0.1", "port": ws_port})
values = {
    "NORMAL_URL": with_query(**normal_params),
    "DIAG_URL": with_query(**diag_params),
    "RESET_URL": with_query(mobile="1", endpoint="reset"),
    "RESET_PORTAL_URL": with_query(mobile="1", resetPortal="1"),
    "HOME_SCREEN_URL": with_query(mobile="1"),
    "MAC_URL": urljoin(f"http://127.0.0.1:{port}/", "index.html") + "?" + urlencode(mac_params),
    "EXPECTED_EFFECTIVE_WS": f"ws://{lan_ip}:{ws_port}/",
    "EXPECTED_PORTAL_ACCOUNT_URL": resolve_http_url(portal_account_url) if portal_account_url else portal_url_from_base(portal_url or "/", "account"),
    "EXPECTED_PORTAL_RECOVERY_URL": resolve_http_url(portal_recovery_url) if portal_recovery_url else portal_url_from_base(portal_url or "/", "security"),
}
for key, value in values.items():
    print(f"{key}={shlex.quote(value)}")
PY
)"

	WS_STATUS="not checked"
	if lsof -nP -iTCP:"$WS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
		WS_STATUS="listening on TCP $WS_PORT"
	else
		WS_STATUS="NOT LISTENING on TCP $WS_PORT - start scripts/run-server.sh before testing"
	fi
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
REPORT="$OUT_DIR/iphone-safari-qa-report.md"

cat > "$REPORT" <<EOF
# Voidscape iPhone Safari QA Report

Created: $(date -u '+%Y-%m-%dT%H:%M:%SZ')

## Mac-side setup

- Web root: \`$TARGET\`
- Static server bind: \`$BIND_HOST:$PORT\`
- LAN IP: \`$LAN_IP\`
- WebSocket status: \`$WS_STATUS\`
- Deployment mode: \`$([[ "$DEPLOYED_MODE" -eq 1 ]] && echo deployed || echo lan)\`
- Mac URL: <$MAC_URL>
- iPhone normal URL: <$NORMAL_URL>
- iPhone diagnostics URL: <$DIAG_URL>
- Home Screen URL after endpoint is saved: <$HOME_SCREEN_URL>
- Reset saved endpoint URL: <$RESET_URL>
- Reset saved portal URL: <$RESET_PORTAL_URL>

$([[ "$DEPLOYED_MODE" -eq 0 && "$LAN_IP" == "127.0.0.1" ]] && echo '**Warning:** LAN IP auto-detection fell back to `127.0.0.1`. A physical iPhone cannot use these iPhone URLs. Rerun with `--lan-ip <this Mac LAN IP>` before real phone testing.' || true)

## Deployment Verification

Before real iPhone release testing, run the hosted deployment verifier with \`--smoke\` against the same URL and WSS/portal settings used for this report. Final release validation requires \`--expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest\` so the uploaded package manifest is matched and every packaged asset is fetched, then paste its \`summary.json\` here. For local LAN development reports, leave this blank and validate with \`--allow-no-deployment-verification\`.

\`\`\`json
$DEPLOYMENT_SUMMARY_JSON
\`\`\`

Expected deployment values:

- \`baseUrl\` matches the deployed static root for this report
- \`smokeRequested: true\`
- \`smokeRan: true\`
- \`smokePassed: true\`
- \`allowHttp: false\`
- \`insecureTls: false\`
- \`allowDebug: false\`
- \`allowInsecureWs: false\`
- \`failures: []\`
- \`buildManifestSha256\` is a SHA-256 digest for the deployed \`voidscape-web-build.json\`
- \`buildManifestMatchesExpected: true\`
- \`deepManifestRequested: true\`
- \`deepManifestChecked: true\`
- \`deepManifestVerifiedCount\` equals \`deepManifestFileCount\`, with \`deepManifestFailureCount: 0\`
- \`cachePolicyChecked: true\`
- \`cachePolicyFailureCount: 0\`
- \`results\` includes successful checks for \`index.html\`, \`manifest.webmanifest\`, \`voidscape-web-client.js\`, PNG icons, \`Cache/MD5.SUM\`, and \`voidscape-web-build.json\`
- Forbidden runtime/cache and TeaVM debug paths were checked and not publicly served

## Device

Fill every field below. Final validation rejects blank, \`n/a\`, \`todo\`, \`tbd\`, \`unknown\`, and Simulator-only device records.

- iPhone model:
- iOS version:
- Safari or Home Screen: Home Screen
- External keyboard tested:
- Network:
- Tester:

## Required Diagnostics

Open the diagnostics URL once to save the endpoint and diagnostics mode. Add the app to Home Screen, launch it from the Home Screen URL, log in, copy the blocking-dialog diagnostics below before closing the welcome/wilderness modal, then close the modal and continue normal smoke testing. Background the Home Screen app, return to it, tap-to-move or send chat, rotate once through portrait and landscape, return to the orientation you want to capture, open the \`i\` panel there, tap \`copy\`, and paste that final Home Screen JSON below. If Mobile Safari blocks clipboard access and the button changes to \`select\`, select/copy the JSON field that appears instead.

\`\`\`json

\`\`\`

Expected values:

- \`touchProfile: true\`
- \`standalone: true\` from the Home Screen launch
- \`inGame: true\` after a successful login
- \`endpoint.source: stored\` after the Home Screen launch reuses the saved endpoint
- \`effectiveWs: "$EXPECTED_EFFECTIVE_WS"\`
- Final \`href\` does not include \`diag=1\` or \`debug=1\`; diagnostics are visible because \`diagnostics.enabled: true\`, \`diagnostics.source: "stored"\`, and \`diagnostics.stored: true\`
- \`portal.accountUrl: "$EXPECTED_PORTAL_ACCOUNT_URL"\` unless production is launched with an explicit \`portalAccountUrl\`
- \`portal.recoveryUrl: "$EXPECTED_PORTAL_RECOVERY_URL"\` unless production is launched with an explicit \`portalRecoveryUrl\`
- \`lifecycle.resumeCount > 0\` after backgrounding and returning to the Home Screen app
- \`lifecycle.viewportUpdateCount > 0\` and non-empty \`lifecycle.lastViewportUpdateAt\`
- \`postResumeProof.inputAfterResume: true\` and \`postResumeProof.movementAfterResume: true\` after post-resume touch/chat plus tap-to-move
- \`canvas.width: 512\`
- \`canvas.height\` nonzero, typically near the portrait viewport height
- \`viewport.scrollX: 0\` and \`viewport.scrollY: 0\`
- With stored diagnostics enabled, \`controls.keyboardButton/actionButton/cameraControls/diagnosticsButton/diagnosticsCopyButton\` visible, hit-tested, inside the viewport, non-overlapping, clear of the gameplay HUD reserves, and with touch targets large enough for iPhone QA. Clean player screenshots intentionally hide diagnostics/camera controls.
- \`ui.chatAccessMode: "canvas"\`, \`ui.chatPanelHidden: false\`, and \`controls.chatButton.display: "none"\` in normal final diagnostics so the real canvas chat tabs own chat without a redundant web tray
- \`ui.panelAccessMode: "canvas"\`, \`ui.mobilePanelShell: false\`, \`ui.canvasTopTabsVisible: true\`, \`ui.canvasPanelRailVisible: false\`, and \`ui.canvasPanelDockVisible: false\` in normal final diagnostics so the Android-parity canvas HUD owns panel access without the old web-only dock/drawer
- \`controls.panelButton.display: "none"\`, \`controls.panelDrawer.display: "none"\`, \`controls.chatButton.display: "none"\`, and \`controls.chatTray.display: "none"\` in final copied diagnostics after closing transient trays/drawers
- \`controls.panelButtons\` still includes inventory, map, magic, prayer, skills, quests, friends, and options shortcut metadata as hidden fallback/debug wiring
- \`controls.chatButtons\` includes all, chat, quest, private, and compose shortcuts
- \`controlsHistory.portrait\` and \`controlsHistory.landscape\` both present after rotating through both orientations, with the same hit-tested overlay control proof
- \`client.hasLocalPlayer: true\` with nonzero \`client.worldX/worldY\` and numeric \`client.cameraRotation/cameraZoom/lastZoom\`
- \`ui.webBuild: true\`, \`ui.androidProfile: true\`, \`ui.customUi: true\`, boolean \`ui.blockingDialog\`, and string \`ui.blockingDialogName\`
- \`uiHistory.messageTabs\` includes \`ALL\`, \`CHAT\`, \`QUEST\`, and \`PRIVATE\`, and \`uiHistory.events\` records Android-parity canvas-tab-opened shared panel ids \`1\` through \`6\` plus a later \`showUiTab: 0\` after closing a panel
- \`scrollHistory\` includes at least one nonzero scrollable canvas-tab-opened panel gesture with \`scrollableUi: true\` and \`showUiTab\` in \`1\` through \`6\`
- \`recentErrors: []\` after login; this includes JavaScript errors, unhandled promises, \`console.error(...)\`, and resource load failures

## Blocking Dialog Diagnostics

After login, before closing the first welcome/wilderness modal, open diagnostics, tap \`copy\`, and paste that JSON here. This proves the web overlay entered dialog-safe mode while the game modal was actually on screen.

\`\`\`json

\`\`\`

Expected blocking-dialog values:

- \`bodyClass\` includes \`dialog-open\`
- \`href\` does not include \`diag=1\` or \`debug=1\`; diagnostics are visible from stored Home Screen state
- \`diagnostics.enabled: true\`, \`diagnostics.source: "stored"\`, and \`diagnostics.stored: true\`
- \`ui.blockingDialog: true\`
- \`ui.blockingDialogName\` is non-empty, usually \`"welcome"\` or \`"wildernessWarning"\`
- \`controls.cameraControls.display: "none"\`
- \`controls.panelButton\`, \`controls.panelDrawer\`, \`controls.chatButton\`, \`controls.chatTray\`, and \`controls.cameraControls\` are hidden while the blocking dialog is open
- \`controls.keyboardButton/actionButton/diagnosticsButton/diagnosticsCopyButton\` visible, hit-tested, inside the viewport, non-overlapping, above the bottom HUD reserve, and at least 44px tall/wide where applicable

## World Map Diagnostics

After closing the welcome/wilderness modal, open the world map, pan it, pinch or zoom it, search once, press browser Back while search is focused and confirm only search closes, then intentionally tap a nearby reachable destination from the map. After the route succeeds, keep the world map open, open diagnostics, tap \`copy\`, and paste that JSON here. This proves the real phone sees the same large mobile map modal and walker path that local smokes prove.

\`\`\`json

\`\`\`

Expected world-map values:

- \`bodyClass\` includes \`world-map-open\`
- \`href\` does not include \`diag=1\` or \`debug=1\`; diagnostics are visible from stored Home Screen state
- \`diagnostics.enabled: true\`, \`diagnostics.source: "stored"\`, and \`diagnostics.stored: true\`
- \`canvas.width: 512\` and \`canvas.height >= 600\`
- \`worldMap.visible: true\`
- \`worldMap.window.width\` is at least 90% of \`canvas.width\`
- \`worldMap.window.height\` is at least 78% of \`canvas.height\`
- \`worldMap.window.y >= 50\` so the title starts below the top HUD/FPS cluster
- \`worldMap.searchCenter\` and \`worldMap.closeCenter\` are inside \`worldMap.window\`
- \`worldMap.walker.lastRequest.at > 0\`
- \`worldMap.walker.lastRoute.ok: true\` and \`worldMap.walker.lastRoute.count > 0\`
- \`controls.keyboardButton/actionButton/panelButton/chatButton\` are hidden while the world map is open so they cannot cover map/search/walker controls

## Smoke Checklist

- [ ] Diagnostics URL opens on iPhone Safari.
- [ ] Home Screen launch still shows the diagnostics \`i\` and \`copy\` controls after opening the diagnostics URL once.
- [ ] Background the Home Screen app, return to it, and confirm copied diagnostics show \`lifecycle.resumeCount > 0\`.
- [ ] After Home Screen background/resume, tap-to-move or chat still works and copied diagnostics record post-resume input plus movement in \`postResumeProof\`.
- [ ] Login screen shows real Voidscape art, not a fallback/prototype renderer.
- [ ] \`Create Account\` opens the intended account portal flow, then Back/return reaches the client again.
- [ ] \`Recover account\` opens the intended recovery portal flow, then Back/return reaches the client again.
- [ ] Existing-user login succeeds with username/password entered through the iPhone keyboard/\`Aa\` path.
- [ ] Voidscape custom HUD skin is visible after login; copied diagnostics show \`ui.customUi: true\`.
- [ ] Voidscape canvas top tabs open every real shared panel, hidden DOM drawer controls stay hidden in normal play, and copied diagnostics record those changes in \`uiHistory\`.
- [ ] Canvas chat owns the visible chat frame/tabs, hidden DOM chat tray controls stay hidden in normal play, and \`Aa\` opens the keyboard without leaving duplicate chat launchers on screen.
- [ ] Compact Safari \`...\` and \`Aa\` helpers stay reachable in normal portrait play without feeling like a second HUD.
- [ ] In-game diagnostics \`i\` and \`copy\` controls stay reachable in portrait and landscape and do not cover the active gameplay HUD.
- [ ] Copied diagnostics include both \`controlsHistory.portrait\` and \`controlsHistory.landscape\` after rotating through both orientations.
- [ ] Blocking-dialog diagnostics were copied while the welcome/wilderness modal was open and show \`ui.blockingDialog: true\` with a non-empty \`ui.blockingDialogName\`.
- [ ] Welcome/wilderness modal text and close button are not covered by web overlay controls.
- [ ] Welcome and wilderness dialogs can be closed.
- [ ] Tap-to-move works without page scroll; copied diagnostics after movement show \`client.worldX/worldY\` and camera state fields.
- [ ] \`Aa\` opens and closes the keyboard.
- [ ] Typing chat works.
- [ ] Paste/autocorrect/composition text does not duplicate or drop committed text.
- [ ] Keyboard open/close does not resize the game framebuffer mid-entry.
- [ ] After sending public chat with the keyboard still open, browser Back closes only the keyboard first; a second browser Back reaches shared mobile Back.
- [ ] Browser Back or Home Screen back-style navigation closes shared mobile states before leaving.
- [ ] Long-press opens the in-game context menu.
- [ ] The compact \`...\` context helper makes the next tap a context/right-click action.
- [ ] Opened context-menu rows can be selected after small finger drift.
- [ ] One-finger horizontal drag rotates camera.
- [ ] One-finger vertical drag zooms.
- [ ] Camera pad buttons rotate/zoom and stop after release.
- [ ] Pinch zoom works and does not emit a stray tap.
- [ ] Canvas-tab-opened shared panels can be swipe-scrolled by touch, and copied diagnostics record a nonzero scrollable panel gesture in \`scrollHistory\`; bank/settings/friends/chat panels can also be swipe-scrolled where available.
- [ ] World map opens, pans, zooms, searches, and world-walks without the \`...\` or \`Aa\` helpers covering the map/search/walker controls.
- [ ] World-map pinch zooms in/out and does not start world-walking unless you intentionally tap a destination.
- [ ] With world-map search focused, browser Back closes the search field while leaving the map open; search text does not leak into chat.
- [ ] World-map diagnostics were copied while the map was open after pan/zoom/search/world-walk, and show a near-full-canvas mobile map window with search/close controls inside it.
- [ ] Orientation changes keep the canvas coherent.
- [ ] Add to Home Screen, launch, and confirm endpoint persists without query parameters.
- [ ] Reset URL clears stale endpoint state when moving between environments.
- [ ] Reset portal URL clears stale account/recovery portal state when moving between environments.

Optional hardware-keyboard check: if a paired keyboard is available, press Escape once in-game and confirm it behaves like mobile Back without typing into chat or leaving the page. Set \`External keyboard tested\` to \`yes\` only when this passed; otherwise set it to \`no\`.

## Notes / Bugs

-

## Validate

After pasting diagnostics JSON and checking every required smoke item:

\`\`\`bash
scripts/validate-web-teavm-iphone-qa-report.py "$REPORT"
\`\`\`

Then tie this report to the simulator-inclusive local preflight and uploaded package:

\`\`\`bash
scripts/check-web-teavm-iphone-final-release.py --qa-report "$REPORT" --local-preflight tmp/web-teavm-iphone-release-preflight/summary.json --package-dir dist/web-teavm
\`\`\`
EOF

cat <<EOF
==> Voidscape iPhone Safari QA
    Report:   $REPORT
    Web root: $TARGET
    Bind:     $BIND_HOST:$PORT
    WebSocket: $WS_STATUS

EOF

if [[ "$DEPLOYED_MODE" -eq 0 ]]; then
	cat <<EOF
Keep scripts/run-server.sh running in another terminal.
Make sure the iPhone is on the same Wi-Fi as this Mac and macOS firewall allows
both the Java game server and this Python static server.

EOF
else
	VERIFY_COMMAND="scripts/verify-web-teavm-deployment.sh \"$BASE_URL\" --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --smoke"
	if [[ -n "$WS_URL" ]]; then
		VERIFY_COMMAND="scripts/verify-web-teavm-deployment.sh \"$BASE_URL\" --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws \"$WS_URL\" --smoke"
	fi
	cat <<EOF
Use this report after the deployed static root and WSS endpoint pass:
    $VERIFY_COMMAND

EOF
fi

cat <<EOF
Mac URL:
    $MAC_URL

iPhone URL:
    $NORMAL_URL

iPhone diagnostics URL:
    $DIAG_URL

Home Screen expected URL after endpoint is saved:
    $HOME_SCREEN_URL

Reset saved endpoint:
    $RESET_URL

Reset saved portal URLs:
    $RESET_PORTAL_URL

EOF

if [[ "$DEPLOYED_MODE" -eq 0 && "$LAN_IP" == "127.0.0.1" ]]; then
	cat <<'EOF'
WARNING: LAN IP auto-detection fell back to 127.0.0.1.
A physical iPhone cannot open these iPhone URLs. Rerun with:
    scripts/run-web-teavm-iphone-qa.sh --no-build --lan-ip <this Mac LAN IP>

EOF
fi

cat <<EOF
Paste the deployment verifier summary JSON and diagnostics copy output into:
    $REPORT

If the diagnostics button changes to "select", Safari blocked direct clipboard access. Select/copy the JSON field that appears and paste that text into the report.

EOF

if command -v qrencode >/dev/null 2>&1; then
	echo "iPhone diagnostics QR:"
	qrencode -t ANSIUTF8 "$DIAG_URL"
	echo
else
	echo "Tip: install qrencode for terminal QR codes, or paste the diagnostics URL into Messages/AirDrop."
	echo
fi

if [[ "$PRINT_ONLY" -eq 1 ]]; then
	exit 0
fi

if [[ "$DEPLOYED_MODE" -eq 1 ]]; then
	exit 0
fi

python3 -u -m http.server "$PORT" --bind "$BIND_HOST" -d "$TARGET"
