#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
CLIENT_JAR="$REPO/Client_Base/Open_RSC_Client.jar"
SOURCE="$REPO/tests/java/orsc/CrackerCampaignHudMessageTest.java"

python3 - "$REPO" <<'PY'
import sys
import re
from pathlib import Path

root = Path(sys.argv[1])
packet = (root / "Client_Base/src/orsc/PacketHandler.java").read_text()
mudclient = (root / "Client_Base/src/orsc/mudclient.java").read_text()
android_input = (root / "Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java").read_text()
android_smoke = (root / "scripts/android-smoke.sh").read_text()
android_smoke_commands = android_smoke.replace('\\\n', ' ')

gate = "sender == null && type == MessageType.QUEST\n\t\t\t\t&& mc.handleVoidscapeCrackerCampaignMessage(message)"
assert gate in packet, "campaign envelope must be senderless QUEST metadata"
assert 'private static final String VOIDSCAPE_CRACKER_CAMPAIGN_PREFIX = "@vscrackercampaign@";' in mudclient
assert 'drawCrackerCampaignHud();' in mudclient
assert 'int killFeedBaseY = crackerCampaignKillFeedBaseY();' in mudclient
assert 'ANDROID_SMOKE_CRACKER_CAMPAIGN_FLAG' in mudclient
assert 'event=ENVELOPE' in mudclient and 'event=HUD' in mudclient
assert '+ " ime=" + keyboard' in mudclient and 'lastAndroidSmokeCrackerCampaignKeyboard' in mudclient
reset_game = mudclient[mudclient.index('private void resetGame(int var1) {'):]
reset_game = reset_game[:reset_game.index('private void resetLoginScreenVariables')]
assert 'boolean enteringGameFromLogin = this.currentViewMode == GameMode.LOGIN;' in reset_game
android_login_cleanup = 'if (enteringGameFromLogin && isNativeAndroidClient()) {'
assert android_login_cleanup in reset_game
assert 'this.panelLogin.setFocus(-1);' in reset_game
assert 'this.clientPort.closeKeyboard();' in reset_game
assert reset_game.index(android_login_cleanup) < reset_game.index('this.currentViewMode = GameMode.GAME;')
assert 'osConfig.F_SHOWING_KEYBOARD' not in reset_game[:reset_game.index('this.currentViewMode = GameMode.GAME;')], \
    'successful native login must cancel pending IME requests even when the visible flag is false'
assert 'drawCrackerCampaignItemSprite(x, y, height)' in mudclient, \
    "compact HUD must use the plaque-contained item-575 renderer"
assert 'runAndroidSmokeCrackerCampaignFromInput()' in android_input
assert 'keyCode == KeyEvent.KEYCODE_F12 && mudclient.runAndroidSmokeCrackerCampaignFromInput()' in android_input
assert 'keyCode == KeyEvent.KEYCODE_D && mudclient.runAndroidSmokeCrackerCampaignFromInput()' not in android_input
assert '--only-auth-cracker-campaign' in android_smoke
assert 'assert_cracker_campaign_hud_geometry' in android_smoke
assert 'restore_cracker_campaign_fixture' in android_smoke
assert 'delete from staff_logs where id > $CRACKER_CAMPAIGN_ORIGINAL_STAFF_LOG_ID' in android_smoke
assert 'delete from player_cache where playerID=$CRACKER_CAMPAIGN_ORIGINAL_PLAYER_ID' in android_smoke
assert 'delete from player_cache where dbid > $CRACKER_CAMPAIGN_ORIGINAL_PLAYER_CACHE_MAX_ID' in android_smoke
assert 'cracker-campaign-player-cache-before.sql' in android_smoke
assert 'cracker-campaign-player-cache-after.sql' in android_smoke
assert 'playerCacheRowsExact=%s' in android_smoke
assert 'staffTailRowsAfter=%s' in android_smoke
restore_fixture = android_smoke[android_smoke.index('restore_cracker_campaign_fixture() {'):]
restore_fixture = restore_fixture[:restore_fixture.index('read_auth_settings() {')]
assert 'cracker_campaign_restore_authorized' in restore_fixture
assert restore_fixture.index('cracker_campaign_restore_authorized') < restore_fixture.index('begin immediate;')
assert 'record_cracker_campaign_restore_refusal explicit-logout-not-proven' in restore_fixture
assert 'wait_for_cracker_campaign_db_quiescence 12' in restore_fixture
assert 'delayedReverify=true' in restore_fixture
assert 'graceful_cleanup_authenticated_smoke_session 1' in android_smoke
assert 'cracker-campaign-explicit-logout.flag' in android_smoke
logout_readiness = android_smoke[android_smoke.index('wait_for_authenticated_logout_input_ready() {'):]
logout_readiness = logout_readiness[:logout_readiness.index('logout_authenticated_smoke_session() {')]
logout_helper = android_smoke[android_smoke.index('logout_authenticated_smoke_session() {'):]
logout_helper = logout_helper[:logout_helper.index('graceful_cleanup_authenticated_smoke_session() {')]
assert 'wait_for_authenticated_logout_input_ready' in logout_helper
assert 'for attempt in 1 2 3' in logout_helper
assert 'wait_for_activity_input_ready "GameActivity"' in android_smoke
assert 'wait_for_android_mobile_viewport "$orientation" 30' in android_smoke
assert 'logout_authenticated_smoke_session portrait 1' in android_smoke
assert "grep 'ANDROID_SMOKE_CRACKER_CAMPAIGN event=HUD '" in logout_readiness
assert logout_readiness.index('soft_keyboard_is_visible') < logout_readiness.index(
    'wait_for_android_mobile_viewport "$orientation" 30'
)
assert 'restore_sqlite_sequence_exact' in android_smoke
assert 'prepare_cracker_campaign_evidence_dir' in android_smoke
assert 'assert_cracker_campaign_capture_ready' in android_smoke
assert 'cracker_campaign_viewport_is_unobstructed' in android_smoke
assert 'android_input_method_state_is_visible' in android_smoke
assert 'wait_for_android_login_transition_ime_release portrait 20' in android_smoke
assert 'captureBackDismissals=0' in android_smoke
cracker_command_helpers = android_smoke[android_smoke.index('send_android_cracker_campaign_command() {'):]
cracker_command_helpers = cracker_command_helpers[:cracker_command_helpers.index('wait_for_ground_loot_drop() {')]
assert cracker_command_helpers.count('input keyevent 142') == 2
assert 'input keyevent 32' not in cracker_command_helpers
cracker_run = android_smoke[android_smoke.index('run_authenticated_cracker_campaign_smoke() {'):]
cracker_run = cracker_run[:cracker_run.index('run_authenticated_ground_loot_smoke() {')]
assert 'input keyevent 142' in cracker_run and 'input keyevent 32' not in cracker_run
login_ime_release = android_smoke[android_smoke.index('wait_for_android_login_transition_ime_release() {'):]
login_ime_release = login_ime_release[:login_ime_release.index('assert_cracker_campaign_capture_ready() {')]
assert "grep 'ANDROID_SMOKE_CRACKER_CAMPAIGN event=HUD '" in login_ime_release
assert 'extract_log_value "$hud_line" ime' in login_ime_release
assert 'extract_log_value "$hud_line" keyboardTop' in login_ime_release
assert 'wait_for_soft_keyboard_hidden 15' in android_smoke
assert '"$ime_bottom" == "0"' in android_smoke
assert '"$keyboard_top" == "2147483647"' in android_smoke
for capture, orientation in (
    ('108a-auth-cracker-campaign-portrait', 'portrait'),
    ('108b-auth-cracker-campaign-landscape', 'landscape'),
    ('108c-auth-cracker-campaign-zero-hidden', 'landscape'),
    ('108d-auth-cracker-campaign-malformed-hidden', 'landscape'),
):
    assert re.search(
        rf'strict_cracker_campaign_screenshot\s+{capture}\s+{orientation}',
        android_smoke_commands,
    ), f'{capture} must require an explicit IME-free {orientation} viewport'
assert 'ANDROID_SMOKE_CRACKER_CAMPAIGN_HEALTH_ONLY' in android_smoke
assert '107z-auth-cracker-campaign-login-health' in android_smoke
assert mudclient.count('setCrackerCampaignRemaining(0);') >= 3, \
    "logout, fresh-login and login-screen resets must clear stale campaign state"
print("Cracker campaign HUD source contracts passed.")
PY

if [[ ! -f "$CLIENT_JAR" ]]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/cracker-campaign-hud.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

eval "$(sed -n '/^extract_log_value() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"
eval "$(sed -n '/^android_input_method_state_is_visible() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"
eval "$(sed -n '/^cracker_campaign_viewport_is_unobstructed() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"
eval "$(sed -n '/^cracker_campaign_run_authenticated() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"
eval "$(sed -n '/^cracker_campaign_explicit_logout_proven() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"
eval "$(sed -n '/^cracker_campaign_restore_authorized() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"

OUT_DIR="$TMP/restore-authorization"
mkdir -p "$OUT_DIR"
if ! cracker_campaign_restore_authorized; then
	echo "ERROR: unauthenticated cracker fixture unexpectedly requires logout proof" >&2
	exit 1
fi
touch "$OUT_DIR/cracker-campaign-authenticated.flag"
if cracker_campaign_restore_authorized; then
	echo "ERROR: authenticated cracker fixture authorized DB restoration without explicit logout" >&2
	exit 1
fi
touch "$OUT_DIR/cracker-campaign-explicit-logout.flag"
if ! cracker_campaign_restore_authorized; then
	echo "ERROR: explicit cracker logout proof did not authorize fixture restoration" >&2
	exit 1
fi
echo "Authenticated cracker fixture restoration requires explicit logout proof."

sticky_ime_state=$'mInputShown=false\nmIsInputViewShown=true'
if android_input_method_state_is_visible "$sticky_ime_state"; then
	echo "ERROR: sticky mIsInputViewShown was treated as an active Android IME" >&2
	exit 1
fi
if ! android_input_method_state_is_visible $'mInputShown=true\nmIsInputViewShown=true'; then
	echo "ERROR: active mInputShown=true was not treated as a visible Android IME" >&2
	exit 1
fi
android_mobile_viewport_log_is_settled_for_surface() { return 0; }
android_mobile_viewport_log_matches_orientation() { return 0; }
clean_viewport='ANDROID_MOBILE_VIEWPORT surfaceW=720 surfaceH=1600 imeBottom=0 keyboardTop=2147483647'
visible_ime_viewport='ANDROID_MOBILE_VIEWPORT surfaceW=720 surfaceH=1600 imeBottom=588 keyboardTop=689'
if ! cracker_campaign_viewport_is_unobstructed portrait "$clean_viewport" 720 1600; then
	echo "ERROR: clean renderer viewport did not satisfy the cracker capture gate" >&2
	exit 1
fi
if cracker_campaign_viewport_is_unobstructed portrait "$visible_ime_viewport" 720 1600; then
	echo "ERROR: renderer viewport with a visible IME satisfied the cracker capture gate" >&2
	exit 1
fi
if cracker_campaign_viewport_is_unobstructed portrait "$clean_viewport" 1600 720; then
	echo "ERROR: physical/renderer viewport mismatch satisfied the cracker capture gate" >&2
	exit 1
fi
echo "Android IME gate accepts sticky service state only with a clean renderer viewport."

# Execute the real strict wrapper with a screenshot backend that falsely returns
# success but emits no file. A stale same-name PNG must be removed and cannot
# satisfy the gate.
eval "$(sed -n '/^strict_cracker_campaign_screenshot() {$/,/^}$/p' \
	"$REPO/scripts/android-smoke.sh")"
OUT_DIR="$TMP/strict-capture"
mkdir -p "$OUT_DIR"
printf 'stale' > "$OUT_DIR/108a-auth-cracker-campaign-portrait.png"
CAPTURE_READY_CALLS=0
SCREENSHOT_CALLS=0
assert_cracker_campaign_capture_ready() {
	CAPTURE_READY_CALLS=$((CAPTURE_READY_CALLS + 1))
	return 1
}
screenshot() {
	SCREENSHOT_CALLS=$((SCREENSHOT_CALLS + 1))
	return 0
}
if strict_cracker_campaign_screenshot 108a-auth-cracker-campaign-portrait portrait; then
	echo "ERROR: strict Android cracker capture ignored a failed IME/viewport gate" >&2
	exit 1
fi
if [[ "$CAPTURE_READY_CALLS" -ne 1 || "$SCREENSHOT_CALLS" -ne 0 ]]; then
	echo "ERROR: strict Android cracker capture did not fail before screenshot after viewport rejection" >&2
	exit 1
fi
if [[ -e "$OUT_DIR/108a-auth-cracker-campaign-portrait.png" ]]; then
	echo "ERROR: strict Android cracker capture did not purge stale evidence before viewport rejection" >&2
	exit 1
fi
echo "Strict Android cracker capture rejects a blocked IME/viewport gate."

printf 'stale' > "$OUT_DIR/108a-auth-cracker-campaign-portrait.png"
assert_cracker_campaign_capture_ready() { return 0; }
if strict_cracker_campaign_screenshot 108a-auth-cracker-campaign-portrait portrait; then
	echo "ERROR: strict Android cracker capture accepted a missing/stale PNG" >&2
	exit 1
fi
if [[ -e "$OUT_DIR/108a-auth-cracker-campaign-portrait.png" ]]; then
	echo "ERROR: strict Android cracker capture did not purge the stale PNG" >&2
	exit 1
fi
echo "Strict Android cracker capture rejects missing/stale evidence."

javac -source 8 -target 8 -cp "$CLIENT_JAR" -d "$TMP" "$SOURCE"
java -cp "$TMP:$CLIENT_JAR" orsc.CrackerCampaignHudMessageTest
