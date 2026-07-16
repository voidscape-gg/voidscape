#!/usr/bin/env bash
# Guided, silent Android promo capture for Voidscape.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$REPO_ROOT/Android_Client"

discover_android_sdk_root() {
	if [[ -n "${ANDROID_HOME:-}" ]]; then
		printf '%s\n' "$ANDROID_HOME"
		return
	fi
	if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
		printf '%s\n' "$ANDROID_SDK_ROOT"
		return
	fi
	if [[ -f "$ANDROID_DIR/local.properties" ]]; then
		local sdk_dir
		sdk_dir="$(sed -nE 's/^sdk\.dir[[:space:]]*=[[:space:]]*(.*)$/\1/p' \
			"$ANDROID_DIR/local.properties" | tail -1)"
		if [[ -n "$sdk_dir" ]]; then
			printf '%s\n' "$sdk_dir"
			return
		fi
	fi
	local candidate
	for candidate in "$HOME/Library/Android/sdk" /opt/homebrew/share/android-commandlinetools \
		/usr/local/share/android-sdk /opt/android-sdk; do
		if [[ -d "$candidate" ]]; then
			printf '%s\n' "$candidate"
			return
		fi
	done
}

SDK_ROOT="$(discover_android_sdk_root || true)"
ADB_BIN="${VOIDSCAPE_PROMO_ADB:-${ANDROID_PROMO_ADB:-}}"
if [[ -z "$ADB_BIN" && -n "$SDK_ROOT" ]]; then
	ADB_BIN="$SDK_ROOT/platform-tools/adb"
elif [[ -z "$ADB_BIN" ]] && command -v adb >/dev/null 2>&1; then
	ADB_BIN="$(command -v adb)"
fi
EMULATOR_BIN="${VOIDSCAPE_PROMO_EMULATOR:-${ANDROID_PROMO_EMULATOR:-}}"
if [[ -z "$EMULATOR_BIN" && -n "$SDK_ROOT" ]]; then
	EMULATOR_BIN="$SDK_ROOT/emulator/emulator"
fi
FFMPEG_BIN="${VOIDSCAPE_PROMO_FFMPEG:-$(command -v ffmpeg 2>/dev/null || true)}"
FFPROBE_BIN="${VOIDSCAPE_PROMO_FFPROBE:-$(command -v ffprobe 2>/dev/null || true)}"

AVD_NAME="${VOIDSCAPE_PROMO_AVD:-voidscape_api35}"
SERIAL="${VOIDSCAPE_PROMO_SERIAL:-}"
APP_ID="${VOIDSCAPE_PROMO_APP_ID:-com.voidscape.gg}"
OUT_DIR="${VOIDSCAPE_PROMO_OUT:-$REPO_ROOT/tmp/android-promo-$(date +%Y%m%d-%H%M%S)}"
NATURAL_SIZE="${VOIDSCAPE_PROMO_NATURAL_SIZE:-1080x1920}"
PORTRAIT_SIZE="1080x1920"
LANDSCAPE_SIZE="1920x1080"
DENSITY="${VOIDSCAPE_PROMO_DENSITY:-320}"
FPS="${VOIDSCAPE_PROMO_FPS:-30}"
BIT_RATE="${VOIDSCAPE_PROMO_BIT_RATE:-16M}"
AUTO_STOP_SECONDS=0
SELECTED_CLIP=""
DRY_RUN=0
LIST_SHOTS=0
MONTAGE=1
PREPARE_CRACKERS=1
CRACKER_COUNT="${VOIDSCAPE_PROMO_CRACKERS:-6}"
PROMO_PLAYER="${VOIDSCAPE_PROMO_PLAYER:-wbtest}"
ADMIN_USER="${VOIDSCAPE_PROMO_ADMIN_USER:-qabot01}"
ADMIN_PASS="${VOIDSCAPE_PROMO_ADMIN_PASS:-voidqa123}"
GAME_PORT="${VOIDSCAPE_PROMO_GAME_PORT:-}"
VOIDBOT_CTRL_PORT="${VOIDSCAPE_PROMO_VOIDBOT_PORT:-18941}"
VB="$REPO_ROOT/tools/voidbot/voidbot"

RECORDING_ACTIVE=0
EMULATOR_STARTED=0
STATE_SAVED=0
CRACKER_BOT_STARTED=0
ORIGINAL_WM_SIZE=""
ORIGINAL_WM_DENSITY=""
ORIGINAL_ROTATION_MODE=""
ORIGINAL_ROTATION=""
ORIGINAL_ACCELEROMETER_ROTATION=""
ORIGINAL_USER_ROTATION=""
ORIGINAL_IGNORE_ORIENTATION_REQUEST=""
ORIGINAL_SHOW_TOUCHES=""
ORIGINAL_POINTER_LOCATION=""
ORIGINAL_HEADS_UP=""
ORIGINAL_STAY_ON=""

usage() {
	cat <<'EOF'
Usage: scripts/record-android-promo.sh [options]

Records a guided, silent Voidscape Android promo session from the emulator.
The default session produces five clips plus a 16:9 rough-cut montage:
  portrait-hud, portrait-afk, landscape-hud,
  portrait-crackers, landscape-crackers

Options:
  --out DIR                 Output directory.
  --clip NAME               Record only one named clip.
  --auto-stop SECONDS       Stop each clip automatically (useful for tests).
  --player NAME             Local Android character receiving crackers.
  --crackers N              Number of Christmas crackers to prepare. Default: 6.
  --game-port PORT          Local game port. Otherwise read Android's saved port.
  --admin-user NAME         Separate local admin used for cracker preparation.
  --admin-pass PASS         Local admin password. Prefer the environment variable.
  --serial SERIAL           adb device serial when more than one is connected.
  --avd NAME                AVD to start when no device is connected.
  --skip-cracker-prep       Do not mint local promo crackers.
  --no-montage              Keep individual clips only.
  --dry-run                 Print the plan without changing emulator state.
  --list-shots              Print the shot list and exit.
  -h, --help                Show this help.

Environment equivalents use the VOIDSCAPE_PROMO_* prefix. The recorder never
types, taps, walks, or opens UI for the player; the person recording performs
the prompted actions while the script owns orientation and capture.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--out) OUT_DIR="${2:-}"; shift 2 ;;
		--clip) SELECTED_CLIP="${2:-}"; shift 2 ;;
		--auto-stop) AUTO_STOP_SECONDS="${2:-}"; shift 2 ;;
		--player) PROMO_PLAYER="${2:-}"; shift 2 ;;
		--crackers) CRACKER_COUNT="${2:-}"; shift 2 ;;
		--game-port) GAME_PORT="${2:-}"; shift 2 ;;
		--admin-user) ADMIN_USER="${2:-}"; shift 2 ;;
		--admin-pass) ADMIN_PASS="${2:-}"; shift 2 ;;
		--serial) SERIAL="${2:-}"; shift 2 ;;
		--avd) AVD_NAME="${2:-}"; shift 2 ;;
		--skip-cracker-prep) PREPARE_CRACKERS=0; shift ;;
		--no-montage) MONTAGE=0; shift ;;
		--dry-run) DRY_RUN=1; shift ;;
		--list-shots) LIST_SHOTS=1; shift ;;
		-h|--help) usage; exit 0 ;;
		*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

# `adb emu screenrecord` resolves its host output path inside the emulator
# process rather than relative to this script's working directory. Always pass
# it an absolute destination, including when the caller supplied --out as a
# relative path.
case "$OUT_DIR" in
	/*) ;;
	*) OUT_DIR="$PWD/$OUT_DIR" ;;
esac

adb() {
	# macOS still ships Bash 3.2, where expanding an empty array under `set -u`
	# raises an unbound-variable error. Keep the no-serial path scalar.
	if [[ -n "$SERIAL" ]]; then
		"$ADB_BIN" -s "$SERIAL" "$@"
	else
		"$ADB_BIN" "$@"
	fi
}

shot_orientation() {
	case "$1" in
		portrait-*) printf 'portrait\n' ;;
		landscape-*) printf 'landscape\n' ;;
		*) return 1 ;;
	esac
}

shot_index() {
	case "$1" in
		portrait-hud) printf '01\n' ;;
		portrait-afk) printf '02\n' ;;
		landscape-hud) printf '03\n' ;;
		portrait-crackers) printf '04\n' ;;
		landscape-crackers) printf '05\n' ;;
		*) return 1 ;;
	esac
}

shot_instructions() {
	case "$1" in
		portrait-hud)
			printf '%s\n' \
				'Portrait HUD: start with the world unobstructed.' \
				'Open Stats, Inventory, Magic, Prayer, and Chat one at a time.' \
				'Pause briefly on each attached drawer and finish on the world.'
			;;
		portrait-afk)
			printf '%s\n' \
				'Portrait AFK Monitor: open Settings, then AFK Mode.' \
				'Hold on the live Hits/Prayer/XP/timer card for several seconds.' \
				'Tap Resume near the end and finish on normal gameplay.'
			;;
		landscape-hud)
			printf '%s\n' \
				'Landscape HUD: show the wider world first.' \
				'Open Inventory, Magic, Prayer, Stats, Map, and Chat.' \
				'Use one slow camera drag only if it helps the composition.'
			;;
		portrait-crackers)
			printf '%s\n' \
				'Portrait crackers: open Inventory and select Christmas cracker -> Open.' \
				'Let each reel settle completely, then Continue.' \
				'Repeat for two or three authentic server-authoritative rolls.'
			;;
		landscape-crackers)
			printf '%s\n' \
				'Landscape crackers: repeat two or three rolls in the wide layout.' \
				'Keep the character and world visible behind the reel.' \
				'Finish after the final result card has been readable for two seconds.'
			;;
		*) return 1 ;;
	esac
}

print_shot_list() {
	local shot
	for shot in portrait-hud portrait-afk landscape-hud portrait-crackers landscape-crackers; do
		printf '\n[%s] %s\n' "$(shot_index "$shot")" "$shot"
		shot_instructions "$shot" | sed 's/^/  /'
	done
}

if [[ "$LIST_SHOTS" -eq 1 ]]; then
	print_shot_list
	exit 0
fi

if [[ -n "$SELECTED_CLIP" ]] && ! shot_index "$SELECTED_CLIP" >/dev/null 2>&1; then
	echo "ERROR: unknown clip '$SELECTED_CLIP'. Use --list-shots." >&2
	exit 2
fi
if [[ ! "$AUTO_STOP_SECONDS" =~ ^[0-9]+$ || "$AUTO_STOP_SECONDS" -gt 180 ]]; then
	echo "ERROR: --auto-stop must be an integer from 0 to 180." >&2
	exit 2
fi
if [[ ! "$CRACKER_COUNT" =~ ^[0-9]+$ || "$CRACKER_COUNT" -lt 1 || "$CRACKER_COUNT" -gt 20 ]]; then
	echo "ERROR: --crackers must be an integer from 1 to 20." >&2
	exit 2
fi
if [[ -n "$GAME_PORT" && ! "$GAME_PORT" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --game-port must be numeric." >&2
	exit 2
fi

require_tools() {
	if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
		echo "ERROR: adb not found. Set ANDROID_HOME or VOIDSCAPE_PROMO_ADB." >&2
		exit 1
	fi
	if [[ -z "$FFMPEG_BIN" || ! -x "$FFMPEG_BIN" || -z "$FFPROBE_BIN" || ! -x "$FFPROBE_BIN" ]]; then
		echo "ERROR: ffmpeg and ffprobe are required (Homebrew: brew install ffmpeg)." >&2
		exit 1
	fi
}

ensure_emulator() {
	if adb get-state >/dev/null 2>&1; then
		return
	fi
	if [[ -z "$EMULATOR_BIN" || ! -x "$EMULATOR_BIN" ]]; then
		echo "ERROR: no Android device is connected and emulator was not found." >&2
		exit 1
	fi
	mkdir -p "$OUT_DIR"
	echo "Starting visible promo emulator: $AVD_NAME"
	nohup "$EMULATOR_BIN" -avd "$AVD_NAME" -skin "$NATURAL_SIZE" -no-audio \
		-no-boot-anim -gpu host -netdelay none -netspeed full -no-snapshot-load \
		> "$OUT_DIR/emulator.log" 2>&1 &
	EMULATOR_STARTED=1
	adb wait-for-device
	adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
}

save_state() {
	ORIGINAL_WM_SIZE="$(adb shell wm size 2>/dev/null | tr -d '\r' \
		| sed -nE 's/^Override size: ([0-9]+x[0-9]+).*$/\1/p' | tail -1)"
	ORIGINAL_WM_DENSITY="$(adb shell wm density 2>/dev/null | tr -d '\r' \
		| sed -nE 's/^Override density: ([0-9]+).*$/\1/p' | tail -1)"
	read -r ORIGINAL_ROTATION_MODE ORIGINAL_ROTATION < <(
		adb shell cmd window user-rotation 2>/dev/null | tr -d '\r' | tail -1
	)
	ORIGINAL_ACCELEROMETER_ROTATION="$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r' | tail -1)"
	ORIGINAL_USER_ROTATION="$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r' | tail -1)"
	ORIGINAL_IGNORE_ORIENTATION_REQUEST="$(adb shell cmd window get-ignore-orientation-request 2>/dev/null \
		| tr -d '\r' | sed -nE 's/.*ignoreOrientationRequest (true|false).*/\1/p' | tail -1)"
	ORIGINAL_SHOW_TOUCHES="$(adb shell settings get system show_touches 2>/dev/null | tr -d '\r' | tail -1)"
	ORIGINAL_POINTER_LOCATION="$(adb shell settings get system pointer_location 2>/dev/null | tr -d '\r' | tail -1)"
	ORIGINAL_HEADS_UP="$(adb shell settings get global heads_up_notifications_enabled 2>/dev/null | tr -d '\r' | tail -1)"
	ORIGINAL_STAY_ON="$(adb shell settings get global stay_on_while_plugged_in 2>/dev/null | tr -d '\r' | tail -1)"
	STATE_SAVED=1
}

restore_setting() {
	local namespace="$1" key="$2" value="$3"
	if [[ -z "$value" || "$value" == "null" ]]; then
		adb shell settings delete "$namespace" "$key" >/dev/null 2>&1 || true
	else
		adb shell settings put "$namespace" "$key" "$value" >/dev/null 2>&1 || true
	fi
}

stop_cracker_bot() {
	if [[ "$CRACKER_BOT_STARTED" -eq 1 ]]; then
		VOIDBOT_CTRL_PORT="$VOIDBOT_CTRL_PORT" "$VB" stop >/dev/null 2>&1 || true
		CRACKER_BOT_STARTED=0
	fi
}

cleanup() {
	set +e
	if [[ "$RECORDING_ACTIVE" -eq 1 ]]; then
		adb emu screenrecord stop >/dev/null 2>&1 || true
		RECORDING_ACTIVE=0
	fi
	stop_cracker_bot
	if [[ "$STATE_SAVED" -eq 1 ]]; then
		if [[ -n "$ORIGINAL_WM_SIZE" ]]; then
			adb shell wm size "$ORIGINAL_WM_SIZE" >/dev/null 2>&1 || true
		else
			adb shell wm size reset >/dev/null 2>&1 || true
		fi
		if [[ -n "$ORIGINAL_WM_DENSITY" ]]; then
			adb shell wm density "$ORIGINAL_WM_DENSITY" >/dev/null 2>&1 || true
		else
			adb shell wm density reset >/dev/null 2>&1 || true
		fi
		if [[ "$ORIGINAL_ROTATION_MODE" == "free" ]]; then
			adb shell cmd window user-rotation free >/dev/null 2>&1 || true
		elif [[ "$ORIGINAL_ROTATION_MODE" == "lock" && "$ORIGINAL_ROTATION" =~ ^[0-3]$ ]]; then
			adb shell cmd window user-rotation lock "$ORIGINAL_ROTATION" >/dev/null 2>&1 || true
		fi
		restore_setting system accelerometer_rotation "$ORIGINAL_ACCELEROMETER_ROTATION"
		restore_setting system user_rotation "$ORIGINAL_USER_ROTATION"
		if [[ "$ORIGINAL_IGNORE_ORIENTATION_REQUEST" == "true" || "$ORIGINAL_IGNORE_ORIENTATION_REQUEST" == "false" ]]; then
			adb shell cmd window set-ignore-orientation-request "$ORIGINAL_IGNORE_ORIENTATION_REQUEST" >/dev/null 2>&1 || true
		fi
		restore_setting system show_touches "$ORIGINAL_SHOW_TOUCHES"
		restore_setting system pointer_location "$ORIGINAL_POINTER_LOCATION"
		restore_setting global heads_up_notifications_enabled "$ORIGINAL_HEADS_UP"
		restore_setting global stay_on_while_plugged_in "$ORIGINAL_STAY_ON"
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

apply_promo_display() {
	adb shell wm size "$NATURAL_SIZE" >/dev/null
	adb shell wm density "$DENSITY" >/dev/null
	adb shell settings put system show_touches 0 >/dev/null
	adb shell settings put system pointer_location 0 >/dev/null
	adb shell settings put global heads_up_notifications_enabled 0 >/dev/null 2>&1 || true
	adb shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
}

screen_size() {
	local size
	size="$(adb shell dumpsys window displays 2>/dev/null | tr -d '\r' \
		| sed -nE 's/.*cur=([0-9]+)x([0-9]+).*/\1 \2/p' | head -1)"
	if [[ -n "$size" ]]; then
		printf '%s\n' "$size"
		return
	fi
	size="$(adb shell dumpsys display 2>/dev/null | tr -d '\r' \
		| sed -nE 's/.*mOverrideDisplayInfo=.*real ([0-9]+) x ([0-9]+).*/\1 \2/p' | head -1)"
	if [[ -n "$size" ]]; then
		printf '%s\n' "$size"
		return
	fi
	adb shell wm size | tr -d '\r' | awk -F'[: x]+' '/Override size/ {print $(NF-1), $NF; exit}'
}

force_orientation() {
	local orientation="$1" rotation=0 expected_width=1080 expected_height=1920
	if [[ "$orientation" == "landscape" ]]; then
		rotation=1
		expected_width=1920
		expected_height=1080
	fi
	adb shell cmd window set-ignore-orientation-request true >/dev/null 2>&1 || true
	adb shell cmd window user-rotation lock "$rotation" >/dev/null 2>&1 || true
	adb shell settings put system accelerometer_rotation 0 >/dev/null
	adb shell settings put system user_rotation "$rotation" >/dev/null

	local deadline=$((SECONDS + 20)) width="" height=""
	while (( SECONDS < deadline )); do
		read -r width height < <(screen_size) || true
		if [[ "$width" == "$expected_width" && "$height" == "$expected_height" ]]; then
			return
		fi
		sleep 1
	done
	echo "ERROR: Android did not settle at ${expected_width}x${expected_height}; last=${width:-?}x${height:-?}." >&2
	exit 1
}

detect_saved_endpoint() {
	local saved_host saved_port
	saved_host="$(adb shell run-as "$APP_ID" cat files/ip.txt 2>/dev/null | tr -d '\r' | tail -1 || true)"
	saved_port="$(adb shell run-as "$APP_ID" cat files/port.txt 2>/dev/null | tr -d '\r' | tail -1 || true)"
	printf '%s %s\n' "$saved_host" "$saved_port"
}

prepare_crackers() {
	[[ "$PREPARE_CRACKERS" -eq 1 ]] || return 0
	if [[ ! -x "$VB" ]]; then
		echo "ERROR: voidbot not found: $VB" >&2
		exit 1
	fi
	local saved_host saved_port messages
	read -r saved_host saved_port < <(detect_saved_endpoint)
	if [[ -z "$GAME_PORT" ]]; then
		GAME_PORT="$saved_port"
	fi
	if [[ "$saved_host" != "10.0.2.2" && "$saved_host" != "127.0.0.1" && "$saved_host" != "localhost" ]]; then
		echo "ERROR: cracker preparation is local-only; Android is saved to host '${saved_host:-unknown}'." >&2
		echo "Use --skip-cracker-prep or point the debug APK at 10.0.2.2 first." >&2
		exit 1
	fi
	if [[ -z "$GAME_PORT" || ! "$GAME_PORT" =~ ^[0-9]+$ ]]; then
		echo "ERROR: could not determine the local game port; pass --game-port." >&2
		exit 1
	fi
	if command -v nc >/dev/null 2>&1 && ! nc -z 127.0.0.1 "$GAME_PORT" >/dev/null 2>&1; then
		echo "ERROR: local game server is not listening on 127.0.0.1:$GAME_PORT." >&2
		exit 1
	fi
	echo "Preparing $CRACKER_COUNT Christmas crackers for $PROMO_PLAYER on local port $GAME_PORT..."
	VOIDBOT_CTRL_PORT="$VOIDBOT_CTRL_PORT" VOIDBOT_GAME_PORT="$GAME_PORT" \
		"$VB" start --user "$ADMIN_USER" --pass "$ADMIN_PASS" >/dev/null
	CRACKER_BOT_STARTED=1
	VOIDBOT_CTRL_PORT="$VOIDBOT_CTRL_PORT" "$VB" wait logged-in --timeout 20 >/dev/null
	VOIDBOT_CTRL_PORT="$VOIDBOT_CTRL_PORT" "$VB" admin \
		"::item 575 $CRACKER_COUNT $PROMO_PLAYER" >/dev/null
	sleep 1
	messages="$(VOIDBOT_CTRL_PORT="$VOIDBOT_CTRL_PORT" "$VB" state messages)"
	if ! printf '%s' "$messages" | python3 -c '
import json, sys
target = sys.argv[1].lower()
data = json.load(sys.stdin)
texts = [str(row.get("text", "")).lower() for row in data.get("state", {}).get("messages", [])]
ok = any("christmas cracker" in text and (target in text or "spawned" in text) for text in texts)
raise SystemExit(0 if ok else 1)
' "$PROMO_PLAYER"
	then
		echo "ERROR: local admin did not confirm cracker delivery. Is $PROMO_PLAYER online with inventory space?" >&2
		exit 1
	fi
	stop_cracker_bot
	echo "Crackers are ready in $PROMO_PLAYER's inventory."
}

write_shot_list() {
	cat > "$OUT_DIR/SHOT-LIST.md" <<'EOF'
# Voidscape Android promo shot list

The recorder never drives the game. Perform the prompted taps naturally and
pause briefly whenever a panel or result is fully visible.

1. **Portrait HUD** — world reveal; Stats, Inventory, Magic, Prayer, Chat; clean close.
2. **Portrait AFK Monitor** — Settings -> AFK Mode; hold telemetry; Resume.
3. **Landscape HUD** — wide world; attached drawers; optional slow camera drag.
4. **Portrait crackers** — two or three full server-authoritative reels.
5. **Landscape crackers** — two or three full reels with the wide world behind them.

Editing notes: preserve the first and last two seconds of each source clip,
prefer straight cuts, and add music/voice-over only after the silent master.
EOF
}

record_clip() {
	local shot="$1" orientation index output_size output_width output_height raw mp4 max_time
	orientation="$(shot_orientation "$shot")"
	index="$(shot_index "$shot")"
	output_size="$PORTRAIT_SIZE"
	if [[ "$orientation" == "landscape" ]]; then
		output_size="$LANDSCAPE_SIZE"
	fi
	output_width="${output_size%x*}"
	output_height="${output_size#*x}"
	raw="$OUT_DIR/raw/$index-$shot.webm"
	mp4="$OUT_DIR/clips/$index-$shot.mp4"
	max_time=180
	if [[ "$AUTO_STOP_SECONDS" -gt 0 ]]; then
		max_time="$AUTO_STOP_SECONDS"
	fi

	force_orientation "$orientation"
	printf '\n============================================================\n'
	printf 'SHOT %s: %s (%s)\n' "$index" "$shot" "$output_size"
	shot_instructions "$shot"
	if [[ "$AUTO_STOP_SECONDS" -eq 0 ]]; then
		printf '\nArrange the scene, then press Return to start recording... '
		read -r _
	fi
	printf 'Starting in 3...'; sleep 1; printf ' 2...'; sleep 1; printf ' 1...\n'; sleep 1
	rm -f "$raw" "$mp4"
	adb emu screenrecord start --size "$output_size" --bit-rate "$BIT_RATE" \
		--fps "$FPS" --time-limit "$max_time" "$raw" >/dev/null
	RECORDING_ACTIVE=1
	if [[ "$AUTO_STOP_SECONDS" -gt 0 ]]; then
		echo "Recording for $AUTO_STOP_SECONDS seconds..."
		sleep "$AUTO_STOP_SECONDS"
	else
		printf 'RECORDING — perform the shot now. Press Return when finished... '
		read -r _
	fi
	adb emu screenrecord stop >/dev/null
	RECORDING_ACTIVE=0
	local deadline=$((SECONDS + 15)) previous_size=0 current_size=0
	while (( SECONDS < deadline )); do
		current_size="$(wc -c < "$raw" 2>/dev/null | tr -d ' ' || true)"
		if [[ "$current_size" =~ ^[0-9]+$ && "$current_size" -gt 0 && "$current_size" == "$previous_size" ]]; then
			break
		fi
		previous_size="$current_size"
		sleep 1
	done

	if [[ ! -s "$raw" ]]; then
		echo "ERROR: emulator recorder did not produce $raw" >&2
		exit 1
	fi
	"$FFMPEG_BIN" -hide_banner -loglevel error -y -i "$raw" -an \
		-vf "fps=$FPS,scale=${output_width}:${output_height}:flags=lanczos,setsar=1" \
		-c:v libx264 -preset medium -crf 17 -pix_fmt yuv420p -movflags +faststart "$mp4"
	validate_clip "$mp4" "$output_size"
	echo "Saved: $mp4"
}

validate_clip() {
	local clip="$1" expected="$2" expected_width expected_height actual
	expected_width="${expected%x*}"
	expected_height="${expected#*x}"
	actual="$("$FFPROBE_BIN" -v error -select_streams v:0 \
		-show_entries stream=width,height -show_entries format=duration \
		-of default=noprint_wrappers=1:nokey=1 "$clip" | tr '\n' ' ')"
	local width height duration
	read -r width height duration <<< "$actual"
	if [[ "$width" != "$expected_width" || "$height" != "$expected_height" ]]; then
		echo "ERROR: $clip is ${width:-?}x${height:-?}; expected $expected." >&2
		exit 1
	fi
	if ! awk -v d="${duration:-0}" 'BEGIN { exit !(d >= 1.0) }'; then
		echo "ERROR: $clip duration is too short: ${duration:-unknown}s" >&2
		exit 1
	fi
}

make_montage_segment() {
	local input="$1" orientation="$2" output="$3"
	if [[ "$orientation" == "portrait" ]]; then
		"$FFMPEG_BIN" -hide_banner -loglevel error -y -i "$input" -t 10 -an \
			-filter_complex "[0:v]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,boxblur=18:2[bg];[0:v]scale=-2:1080[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,fps=$FPS,format=yuv420p" \
			-c:v libx264 -preset medium -crf 18 -movflags +faststart "$output"
	else
		"$FFMPEG_BIN" -hide_banner -loglevel error -y -i "$input" -t 10 -an \
			-vf "scale=1920:1080:flags=lanczos,setsar=1,fps=$FPS,format=yuv420p" \
			-c:v libx264 -preset medium -crf 18 -movflags +faststart "$output"
	fi
}

build_montage() {
	[[ "$MONTAGE" -eq 1 ]] || return 0
	local concat_file="$OUT_DIR/montage-segments/concat.txt"
	mkdir -p "$OUT_DIR/montage-segments"
	: > "$concat_file"
	local shot index input segment orientation
	for shot in portrait-hud portrait-afk landscape-hud portrait-crackers landscape-crackers; do
		index="$(shot_index "$shot")"
		input="$OUT_DIR/clips/$index-$shot.mp4"
		[[ -f "$input" ]] || continue
		orientation="$(shot_orientation "$shot")"
		segment="$OUT_DIR/montage-segments/$index-$shot.mp4"
		make_montage_segment "$input" "$orientation" "$segment"
		printf "file '%s'\n" "$segment" >> "$concat_file"
	done
	if [[ "$(wc -l < "$concat_file" | tr -d ' ')" -lt 2 ]]; then
		echo "Skipping montage: record at least two clips."
		return
	fi
	"$FFMPEG_BIN" -hide_banner -loglevel error -y -f concat -safe 0 -i "$concat_file" \
		-c copy -movflags +faststart "$OUT_DIR/voidscape-android-promo-rough-cut.mp4"
	validate_clip "$OUT_DIR/voidscape-android-promo-rough-cut.mp4" "1920x1080"
	echo "Saved rough cut: $OUT_DIR/voidscape-android-promo-rough-cut.mp4"
}

write_manifest() {
	python3 - "$OUT_DIR" "$FPS" "$BIT_RATE" "$NATURAL_SIZE" <<'PY'
import datetime as dt
import json
import subprocess
import sys
from pathlib import Path

out = Path(sys.argv[1])
clips = []
for path in sorted((out / "clips").glob("*.mp4")):
    probe = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=width,height,avg_frame_rate",
        "-show_entries", "format=duration,size", "-of", "json", str(path)
    ], text=True))
    stream = probe["streams"][0]
    fmt = probe["format"]
    clips.append({
        "file": str(path.relative_to(out)),
        "width": stream["width"],
        "height": stream["height"],
        "averageFrameRate": stream.get("avg_frame_rate", ""),
        "durationSeconds": round(float(fmt["duration"]), 3),
        "sizeBytes": int(fmt["size"]),
        "silent": True,
    })
manifest = {
    "schemaVersion": 1,
    "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
    "capture": {"fps": int(sys.argv[2]), "bitRate": sys.argv[3], "naturalDisplay": sys.argv[4]},
    "clips": clips,
    "roughCut": "voidscape-android-promo-rough-cut.mp4" if (out / "voidscape-android-promo-rough-cut.mp4").exists() else "",
}
(out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
PY
}

if [[ "$DRY_RUN" -eq 1 ]]; then
	echo "Voidscape Android promo capture plan"
	echo "  output: $OUT_DIR"
	echo "  AVD: $AVD_NAME"
	echo "  natural display: $NATURAL_SIZE @ ${DENSITY}dpi"
	echo "  video: portrait=$PORTRAIT_SIZE landscape=$LANDSCAPE_SIZE ${FPS}fps ${BIT_RATE} silent"
	echo "  cracker target: $PROMO_PLAYER x$CRACKER_COUNT (prep=$PREPARE_CRACKERS)"
	echo "  montage: $MONTAGE"
	print_shot_list
	exit 0
fi

require_tools
mkdir -p "$OUT_DIR/raw" "$OUT_DIR/clips"
ensure_emulator
save_state
apply_promo_display
write_shot_list

if ! adb shell dumpsys activity activities 2>/dev/null | grep -q "$APP_ID.*GameActivity"; then
	echo "WARNING: Voidscape GameActivity is not currently visible."
	echo "Log in and enter the world on the emulator before starting the first shot."
	printf 'Press Return when the character is in game... '
	read -r _
fi

if [[ -z "$SELECTED_CLIP" || "$SELECTED_CLIP" == *crackers ]]; then
	prepare_crackers
fi

if [[ -n "$SELECTED_CLIP" ]]; then
	record_clip "$SELECTED_CLIP"
else
	for shot in portrait-hud portrait-afk landscape-hud portrait-crackers landscape-crackers; do
		record_clip "$shot"
	done
fi

build_montage
write_manifest

echo
echo "Voidscape Android promo capture complete:"
echo "  clips: $OUT_DIR/clips"
echo "  shot list: $OUT_DIR/SHOT-LIST.md"
echo "  manifest: $OUT_DIR/manifest.json"
if [[ -f "$OUT_DIR/voidscape-android-promo-rough-cut.mp4" ]]; then
	echo "  rough cut: $OUT_DIR/voidscape-android-promo-rough-cut.mp4"
fi
