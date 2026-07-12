#!/usr/bin/env bash
# Create a fillable physical Android QA report for APK/public-channel signoff.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ANDROID_DEVICE_QA_OUT:-$ROOT/tmp/android-device-qa-current}"
APK_UNDER_TEST="${ANDROID_DEVICE_QA_APK:-}"
GAME_ENDPOINT="${ANDROID_DEVICE_QA_ENDPOINT:-voidscape.gg:43596}"
PORTAL_URL="${ANDROID_DEVICE_QA_PORTAL_URL:-https://voidscape.gg/}"
CHANNEL="${ANDROID_DEVICE_QA_CHANNEL:-web-client-first}"
PRINT_ONLY=0

usage() {
	cat <<'EOF'
Usage: scripts/run-android-device-qa.sh [options]

Writes a physical Android QA report template. Fill it on a real Android device,
then validate it with scripts/validate-android-device-qa-report.py.

Options:
  --out DIR           Output directory. Default: tmp/android-device-qa-current.
  --apk PATH_OR_URL   APK/build under test to prefill.
  --endpoint HOST:PORT
                      Game endpoint to prefill. Default: voidscape.gg:43596.
  --portal-url URL    Portal URL to prefill. Default: https://voidscape.gg/.
  --channel NAME      Channel under test. Default: web-client-first.
  --print-only        Print the template path and contents after writing.
  -h, --help          Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--apk)
			APK_UNDER_TEST="${2:-}"
			shift 2
			;;
		--endpoint)
			GAME_ENDPOINT="${2:-}"
			shift 2
			;;
		--portal-url)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--channel)
			CHANNEL="${2:-}"
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

if [[ -z "$OUT_DIR" || -z "$GAME_ENDPOINT" || -z "$PORTAL_URL" || -z "$CHANNEL" ]]; then
	echo "ERROR: out, endpoint, portal URL, and channel must be non-empty." >&2
	exit 2
fi

mkdir -p "$OUT_DIR"
REPORT="$OUT_DIR/android-device-qa-report.md"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
APK_VALUE="${APK_UNDER_TEST:-TODO}"

cat > "$REPORT" <<EOF
# Android Device QA Report

Generated: $STAMP

This report is for real Android hardware. Emulator screenshots are useful
preflight evidence, but they do not satisfy the public APK gate.

Validate after filling:

\`\`\`bash
scripts/validate-android-device-qa-report.py "$REPORT"
\`\`\`

## Metadata

- **Tester:** TODO
- **Date:** TODO
- **Device model:** TODO
- **Android version:** TODO
- **Screen size/density:** TODO
- **RAM/storage class:** TODO
- **Network tested:** TODO
- **APK/build under test:** $APK_VALUE
- **Distribution channel:** $CHANNEL
- **Game endpoint:** $GAME_ENDPOINT
- **Portal URL:** $PORTAL_URL
- **Result:** HOLD

## Mandatory Checks

- [ ] Fresh install reaches the branded Voidscape splash/ready screen without Android system dialogs covering app UI.
- [ ] Play reaches the intended game endpoint and the selected server is understandable.
- [ ] Create Account opens the in-client username/password character creation form, creates a fresh test character, and returns to Existing User with credentials prefilled.
- [ ] Recover account opens the portal security/recovery route in the browser.
- [ ] Existing-user login accepts username/password with the soft keyboard.
- [ ] Portrait gameplay framing is acceptable: HUD is not clipped, touch targets are reachable, and unused vertical space is not release-blocking.
- [ ] Game settings panel is readable in portrait, with no heading/first-row crowding and a tappable logout row.
- [ ] Landscape gameplay is usable after rotation or app relaunch.
- [ ] Tap movement, NPC/object tap, inventory tap, and long-press context menu reach the expected shared game actions.
- [ ] Chat keyboard opens, sends a short message, closes cleanly, and does not corrupt login/game input.
- [ ] Bank or another scrollable panel can be opened and touch-scrolled without covering critical controls.
- [ ] Camera rotate and zoom gestures work without interfering with normal taps.
- [ ] Background/resume returns to the same playable state.
- [ ] Reopening the launcher/app icon does not start duplicate broken game activities.
- [ ] Logout returns to the branded login home, and Existing User keyboard still opens afterward.
- [ ] Network failure or bad endpoint messaging is understandable enough for player support.
- [ ] No Android runtime crash, ANR, or repeated severe logcat error is observed during the session.
- [ ] Screenshots or video evidence are attached for login, portrait gameplay, settings, logout, and any visual issue.

## Evidence

- **Screenshots/videos:** TODO
- **Logcat summary:** TODO
- **Notes for portrait framing/settings:** TODO
- **Known issues:** TODO

## Sign-Off

- **Tester sign-off:** TODO
- **Release owner decision:** HOLD
EOF

echo "Android device QA report: $REPORT"
if [[ "$PRINT_ONLY" -eq 1 ]]; then
	echo
	cat "$REPORT"
fi
