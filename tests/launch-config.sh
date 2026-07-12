#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKER="$ROOT/scripts/check-launch-config.mjs"
SOURCE="$ROOT/server/voidscape-launch.conf"
DEFAULT_SOURCE="$ROOT/server/default.conf"
CONFIG_SOURCE="$ROOT/server/src/com/openrsc/server/ServerConfiguration.java"
CLIENT_SOURCE="$ROOT/Client_Base/src/orsc/Config.java"
VOIDBOT_SOURCE="$ROOT/tools/voidbot/protocol.py"
PC_BUILD_SOURCE="$ROOT/Client_Base/build.xml"
ANDROID_BUILD_SOURCE="$ROOT/Android_Client/Open RSC Android Client/build.gradle"
TEAVM_BUILD_SOURCE="$ROOT/Web_Client_TeaVM/pom.xml"
EXPECTED_VERSION="10132"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-launch-config.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

"$CHECKER" "$SOURCE" --expected-client-version "$EXPECTED_VERSION" >/dev/null
"$CHECKER" "$SOURCE" >/dev/null
rg -Fq "public static final int CLIENT_VERSION = $EXPECTED_VERSION;" "$CLIENT_SOURCE"
rg -Fq "VOIDBOT_CLIENT_VERSION\", \"$EXPECTED_VERSION\"" "$VOIDBOT_SOURCE"
rg -Fq 'srcdir="${src}:${pc_client}"' "$PC_BUILD_SOURCE"
rg -Fq 'src/main/../../../../Client_Base/src' "$ANDROID_BUILD_SOURCE"
rg -Fq '${project.basedir}/../Client_Base/src' "$TEAVM_BUILD_SOURCE"

assert_rejected() {
	local label="$1"
	local file="$2"
	if "$CHECKER" "$file" --expected-client-version "$EXPECTED_VERSION" >/dev/null 2>&1; then
		echo "FAIL: unsafe config was accepted: $label" >&2
		exit 1
	fi
}

cp "$SOURCE" "$TMP_DIR/colossus.conf"
sed -i.bak 's/want_void_colossus: false/want_void_colossus: true/' "$TMP_DIR/colossus.conf"
assert_rejected "enabled Void Colossus" "$TMP_DIR/colossus.conf"

cp "$SOURCE" "$TMP_DIR/dungeon.conf"
sed -i.bak 's/want_void_dungeon: true/want_void_dungeon: false/' "$TMP_DIR/dungeon.conf"
assert_rejected "disabled launch dungeon" "$TMP_DIR/dungeon.conf"

cp "$SOURCE" "$TMP_DIR/commands.conf"
sed -i.bak 's/production_command_lockdown: true/production_command_lockdown: false/' "$TMP_DIR/commands.conf"
assert_rejected "disabled command lockdown" "$TMP_DIR/commands.conf"

cp "$SOURCE" "$TMP_DIR/cracker-campaign.conf"
sed -i.bak 's/want_cracker_campaign: true/want_cracker_campaign: false/' "$TMP_DIR/cracker-campaign.conf"
assert_rejected "disabled launch cracker campaign" "$TMP_DIR/cracker-campaign.conf"

cp "$SOURCE" "$TMP_DIR/world-achievements.conf"
sed -i.bak 's/want_world_achievements: true/want_world_achievements: false/' "$TMP_DIR/world-achievements.conf"
assert_rejected "disabled launch world achievements" "$TMP_DIR/world-achievements.conf"

cp "$SOURCE" "$TMP_DIR/world-achievement-season.conf"
sed -i.bak 's/world_achievement_season_id: launch-2026/world_achievement_season_id: other-season/' "$TMP_DIR/world-achievement-season.conf"
assert_rejected "unexpected world achievement season" "$TMP_DIR/world-achievement-season.conf"

cp "$SOURCE" "$TMP_DIR/world-pk-loot-minimum.conf"
sed -i.bak 's/world_pk_loot_minimum: 5000/world_pk_loot_minimum: 1/' "$TMP_DIR/world-pk-loot-minimum.conf"
assert_rejected "unexpected qualified-PK loot floor" "$TMP_DIR/world-pk-loot-minimum.conf"

cp "$SOURCE" "$TMP_DIR/cracker-kill-odds.conf"
sed -i.bak 's/cracker_campaign_npc_kill_denominator: 500/cracker_campaign_npc_kill_denominator: 1/' "$TMP_DIR/cracker-kill-odds.conf"
assert_rejected "unexpected cracker NPC-kill odds" "$TMP_DIR/cracker-kill-odds.conf"

cp "$SOURCE" "$TMP_DIR/cracker-skill-odds.conf"
sed -i.bak 's/cracker_campaign_skilling_denominator: 1000/cracker_campaign_skilling_denominator: 1/' "$TMP_DIR/cracker-skill-odds.conf"
assert_rejected "unexpected cracker skilling odds" "$TMP_DIR/cracker-skill-odds.conf"

cp "$SOURCE" "$TMP_DIR/webhook.conf"
webhook_host="discord.com"
webhook_url="https://$webhook_host/api/webhooks/example/example"
sed -i.bak "s#discord_global_chat_webhook_url: null#discord_global_chat_webhook_url: $webhook_url#" "$TMP_DIR/webhook.conf"
assert_rejected "embedded webhook credential" "$TMP_DIR/webhook.conf"

rg -Fq 'WANT_WORLD_ACHIEVEMENTS = tryReadBool("want_world_achievements").orElse(false);' "$CONFIG_SOURCE"
rg -Fq 'tryReadString("world_achievement_season_id").orElse("launch-2026");' "$CONFIG_SOURCE"
rg -Fq 'WORLD_PK_LOOT_MINIMUM = tryReadInt("world_pk_loot_minimum").orElse(5000);' "$CONFIG_SOURCE"
rg -Fq 'want_world_achievements: false' "$DEFAULT_SOURCE"
rg -Fq 'world_achievement_season_id: launch-2026' "$DEFAULT_SOURCE"
rg -Fq 'world_pk_loot_minimum: 5000' "$DEFAULT_SOURCE"
rg -Fq 'want_world_achievements: true' "$SOURCE"
rg -Fq 'world_achievement_season_id: launch-2026' "$SOURCE"
rg -Fq 'world_pk_loot_minimum: 5000' "$SOURCE"

echo "launch config policy tests passed"
