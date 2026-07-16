#!/usr/bin/env bash
# android-smoke.sh — build/install the Android APK and capture core QA screenshots

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

    local local_properties="$ANDROID_DIR/local.properties"
    if [[ -f "$local_properties" ]]; then
        local sdk_dir
        sdk_dir="$(sed -nE 's/^sdk\.dir[[:space:]]*=[[:space:]]*(.*)$/\1/p' "$local_properties" | tail -1)"
        if [[ -n "$sdk_dir" ]]; then
            printf '%s\n' "$sdk_dir"
            return
        fi
    fi

    local candidate
    for candidate in "$HOME/Library/Android/sdk" /opt/homebrew/share/android-commandlinetools /usr/local/share/android-sdk /opt/android-sdk; do
        if [[ -d "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    done
}

SDK_ROOT="$(discover_android_sdk_root || true)"
ADB="${ANDROID_SMOKE_ADB:-}"
if [[ -z "$ADB" && -n "$SDK_ROOT" ]]; then
    ADB="$SDK_ROOT/platform-tools/adb"
elif [[ -z "$ADB" ]] && command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
fi
EMULATOR="${ANDROID_SMOKE_EMULATOR:-}"
if [[ -z "$EMULATOR" && -n "$SDK_ROOT" ]]; then
    EMULATOR="$SDK_ROOT/emulator/emulator"
fi
AVD_NAME="${AVD_NAME:-voidscape_api35}"
APK="$REPO_ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk"
OUT_DIR="${ANDROID_SCREENSHOT_DIR:-$REPO_ROOT/tmp/android-smoke-$(date +%Y%m%d-%H%M%S)}"
APP_ID="${ANDROID_SMOKE_APP_ID:-com.voidscape.gg}"
APP_FILES="/data/user/0/$APP_ID/files"
APP_SMOKE_FILES="/sdcard/Android/data/$APP_ID/files"
SMOKE_NPC_TARGETS_FLAG="$APP_SMOKE_FILES/android-smoke-npc-targets.flag"
SMOKE_PLAYER_TARGETS_FLAG="$APP_SMOKE_FILES/android-smoke-player-targets.flag"
SMOKE_PLAYER_COMMAND_FILE="$APP_SMOKE_FILES/android-smoke-player-command.txt"
SMOKE_OBJECT_TARGETS_FLAG="$APP_SMOKE_FILES/android-smoke-object-targets.flag"
SMOKE_INVENTORY_TARGETS_FLAG="$APP_SMOKE_FILES/android-smoke-inventory-targets.flag"
SMOKE_CAMERA_FLAG="$APP_SMOKE_FILES/android-smoke-camera.flag"
SMOKE_ZOOM_FLAG="$APP_SMOKE_FILES/android-smoke-zoom.flag"
SMOKE_CHAT_TABS_FLAG="$APP_SMOKE_FILES/android-smoke-chat-tabs.flag"
SMOKE_CHAT_SEND_FLAG="$APP_SMOKE_FILES/android-smoke-chat-send.flag"
SMOKE_BANK_FLAG="$APP_SMOKE_FILES/android-smoke-bank.flag"
SMOKE_SHOP_FLAG="$APP_SMOKE_FILES/android-smoke-shop.flag"
SMOKE_EQUIPMENT_FLAG="$APP_SMOKE_FILES/android-smoke-equipment.flag"
SMOKE_MAGIC_PRAYER_FLAG="$APP_SMOKE_FILES/android-smoke-magic-prayer.flag"
SMOKE_AUDIO_FLAG="$APP_SMOKE_FILES/android-smoke-audio.flag"
SMOKE_NETWORK_FLAG="$APP_SMOKE_FILES/android-smoke-network.flag"
SMOKE_WORLD_MAP_FLAG="$APP_SMOKE_FILES/android-smoke-world-map.flag"
SMOKE_SETTINGS_FLAG="$APP_SMOKE_FILES/android-smoke-settings.flag"
SMOKE_AFK_FLAG="$APP_SMOKE_FILES/android-smoke-afk.flag"
SMOKE_GROUND_LOOT_FLAG="$APP_SMOKE_FILES/android-smoke-ground-loot.flag"
SMOKE_APPEARANCE_PROMPT_FLAG="$APP_SMOKE_FILES/android-smoke-appearance-prompt.flag"
SMOKE_WALK_FLAG="$APP_SMOKE_FILES/android-smoke-walk.flag"
SMOKE_LOGIN_FLAG="$APP_SMOKE_FILES/android-smoke-login.flag"
SMOKE_LIFECYCLE_RECONNECT_HOLD_FLAG="$APP_FILES/android-smoke-lifecycle-reconnect-hold.flag"
BUILD=1
INSTALL=1
ONLY_AUTH_CAMERA=0
ONLY_AUTH_ZOOM=0
ONLY_AUTH_CHAT_TABS=0
ONLY_AUTH_CHAT_SEND=0
ONLY_AUTH_BANK=0
ONLY_AUTH_SHOP=0
ONLY_AUTH_EQUIPMENT=0
ONLY_AUTH_MAGIC_PRAYER=0
ONLY_AUTH_WORLD_MAP=0
ONLY_AUTH_SETTINGS=0
ONLY_AUTH_AFK=0
ONLY_AUTH_GROUND_LOOT=0
ONLY_AUTH_WILDERNESS_TARGET=0
ONLY_AUTH_PVP_STRESS=0
ONLY_AUTH_LOGIN=0
ONLY_AUTH_LIFECYCLE=0
ONLY_AUTH_CREDENTIALS=0
ONLY_BOOTSTRAP=0
ONLY_ACCOUNT_HANDOFF=0
ORIGINAL_ACCELEROMETER_ROTATION=""
ORIGINAL_USER_ROTATION=""
ORIGINAL_WINDOW_ROTATION_MODE=""
ORIGINAL_WINDOW_ROTATION=""
ORIGINAL_IGNORE_ORIENTATION_REQUEST=""
ROTATION_STATE_SAVED=0
ORIGINAL_WM_SIZE_OVERRIDE=""
ORIGINAL_WM_DENSITY_OVERRIDE=""
DISPLAY_OVERRIDE_STATE_SAVED=0
SHOP_ACTION_VIEWPORT_LINE=""
ORIGINAL_WIFI_STATE=""
ORIGINAL_MOBILE_DATA_STATE=""
NETWORK_STATE_SAVED=0
ORIGINAL_ALWAYS_FINISH_ACTIVITIES=""
ACTIVITY_POLICY_STATE_SAVED=0
AUTH_USER="${ANDROID_SMOKE_AUTH_USER:-}"
AUTH_PASS="${ANDROID_SMOKE_AUTH_PASS:-}"
AUTH_HOST="${ANDROID_SMOKE_AUTH_HOST:-10.0.2.2}"
AUTH_PORT="${ANDROID_SMOKE_AUTH_PORT:-43596}"
ACCOUNT_HANDOFF_HOST="${ANDROID_SMOKE_ACCOUNT_HANDOFF_HOST:-}"
ACCOUNT_HANDOFF_PORT="${ANDROID_SMOKE_ACCOUNT_HANDOFF_PORT:-43596}"
AUTH_DB="${ANDROID_SMOKE_AUTH_DB:-}"
AUTH_USE_BUNDLED_ENDPOINT="${ANDROID_SMOKE_AUTH_USE_BUNDLED_ENDPOINT:-0}"
AUTH_EXISTING_USER_X_PCT="${ANDROID_SMOKE_AUTH_EXISTING_USER_X_PCT:-50}"
AUTH_EXISTING_USER_Y_PCT="${ANDROID_SMOKE_AUTH_EXISTING_USER_Y_PCT:-20}"
AUTH_USERNAME_X_PCT="${ANDROID_SMOKE_AUTH_USERNAME_X_PCT:-50}"
AUTH_USERNAME_Y_PCT="${ANDROID_SMOKE_AUTH_USERNAME_Y_PCT:-8}"
AUTH_PASSWORD_X_PCT="${ANDROID_SMOKE_AUTH_PASSWORD_X_PCT:-50}"
AUTH_PASSWORD_Y_PCT="${ANDROID_SMOKE_AUTH_PASSWORD_Y_PCT:-12}"
AUTH_OK_X_PCT="${ANDROID_SMOKE_AUTH_OK_X_PCT:-40}"
AUTH_OK_Y_PCT="${ANDROID_SMOKE_AUTH_OK_Y_PCT:-19}"
AUTH_NPC_ID="${ANDROID_SMOKE_NPC_ID:-839}"
AUTH_NPC_ACTION="${ANDROID_SMOKE_NPC_ACTION:-NPC_TALK_TO}"
AUTH_NPC_PLAYER_X="${ANDROID_SMOKE_NPC_PLAYER_X:-23}"
AUTH_NPC_PLAYER_Y="${ANDROID_SMOKE_NPC_PLAYER_Y:-25}"
AUTH_NPC_FALLBACK_CLIENT_X="${ANDROID_SMOKE_NPC_FALLBACK_CLIENT_X:-309}"
AUTH_NPC_FALLBACK_CLIENT_Y="${ANDROID_SMOKE_NPC_FALLBACK_CLIENT_Y:-183}"
AUTH_OBJECT_ID="${ANDROID_SMOKE_OBJECT_ID:-1303}"
AUTH_OBJECT_ACTION="${ANDROID_SMOKE_OBJECT_ACTION:-OBJECT_COMMAND1}"
AUTH_OBJECT_PLAYER_X="${ANDROID_SMOKE_OBJECT_PLAYER_X:-123}"
AUTH_OBJECT_PLAYER_Y="${ANDROID_SMOKE_OBJECT_PLAYER_Y:-326}"
AUTH_INVENTORY_SLOT="${ANDROID_SMOKE_INVENTORY_SLOT:-0}"
AUTH_INVENTORY_ITEM_ID="${ANDROID_SMOKE_INVENTORY_ITEM_ID:-10}"
AUTH_INVENTORY_ITEM_AMOUNT="${ANDROID_SMOKE_INVENTORY_ITEM_AMOUNT:-123}"
AUTH_INVENTORY_ACTION="${ANDROID_SMOKE_INVENTORY_ACTION:-ITEM_USE}"
AUTH_ITEM_ON_ITEM_TARGET_SLOT="${ANDROID_SMOKE_ITEM_ON_ITEM_TARGET_SLOT:-1}"
AUTH_ITEM_ON_ITEM_TARGET_ID="${ANDROID_SMOKE_ITEM_ON_ITEM_TARGET_ID:-166}"
AUTH_ITEM_ON_ITEM_TARGET_AMOUNT="${ANDROID_SMOKE_ITEM_ON_ITEM_TARGET_AMOUNT:-1}"
AUTH_ITEM_ON_ITEM_ACTION="${ANDROID_SMOKE_ITEM_ON_ITEM_ACTION:-ITEM_USE_ITEM}"
AUTH_ITEM_ON_TARGET_SLOT="${ANDROID_SMOKE_ITEM_ON_TARGET_SLOT:-0}"
AUTH_ITEM_ON_TARGET_ITEM_ID="${ANDROID_SMOKE_ITEM_ON_TARGET_ITEM_ID:-10}"
AUTH_ITEM_ON_TARGET_ITEM_AMOUNT="${ANDROID_SMOKE_ITEM_ON_TARGET_ITEM_AMOUNT:-123}"
AUTH_ITEM_ON_OBJECT_ACTION="${ANDROID_SMOKE_ITEM_ON_OBJECT_ACTION:-OBJECT_USE_ITEM}"
AUTH_ITEM_ON_NPC_ACTION="${ANDROID_SMOKE_ITEM_ON_NPC_ACTION:-NPC_USE_ITEM}"
AUTH_CONTEXT_MENU_FIRST_ACTION="${ANDROID_SMOKE_CONTEXT_MENU_FIRST_ACTION:-NPC_TALK_TO}"
AUTH_EDGE_MENU_CLIENT_X="${ANDROID_SMOKE_EDGE_MENU_CLIENT_X:-508}"
AUTH_EDGE_MENU_CLIENT_Y="${ANDROID_SMOKE_EDGE_MENU_CLIENT_Y:-306}"
AUTH_EDGE_MENU_ACTION="${ANDROID_SMOKE_EDGE_MENU_ACTION:-LANDSCAPE_WALK_HERE}"
AUTH_CAMERA_SWIPE_START_X="${ANDROID_SMOKE_CAMERA_SWIPE_START_X:-420}"
AUTH_CAMERA_SWIPE_END_X="${ANDROID_SMOKE_CAMERA_SWIPE_END_X:-120}"
AUTH_CAMERA_SWIPE_Y="${ANDROID_SMOKE_CAMERA_SWIPE_Y:-170}"
AUTH_CAMERA_SWIPE_DURATION_MS="${ANDROID_SMOKE_CAMERA_SWIPE_DURATION_MS:-700}"
AUTH_PANEL_SWIPE_X="${ANDROID_SMOKE_PANEL_SWIPE_X:-420}"
AUTH_PANEL_SWIPE_START_Y="${ANDROID_SMOKE_PANEL_SWIPE_START_Y:-170}"
AUTH_PANEL_SWIPE_END_Y="${ANDROID_SMOKE_PANEL_SWIPE_END_Y:-280}"
AUTH_PANEL_SWIPE_DURATION_MS="${ANDROID_SMOKE_PANEL_SWIPE_DURATION_MS:-700}"
AUTH_ZOOM_DRAG_X="${ANDROID_SMOKE_ZOOM_DRAG_X:-256}"
AUTH_ZOOM_DRAG_START_Y="${ANDROID_SMOKE_ZOOM_DRAG_START_Y:-80}"
AUTH_ZOOM_DRAG_END_Y="${ANDROID_SMOKE_ZOOM_DRAG_END_Y:-190}"
AUTH_ZOOM_DRAG_DURATION_MS="${ANDROID_SMOKE_ZOOM_DRAG_DURATION_MS:-700}"
AUTH_ZOOM_MANUAL_PINCH_SECONDS="${ANDROID_SMOKE_MANUAL_PINCH_SECONDS:-0}"
AUTH_ZOOM_REQUIRE_PINCH="${ANDROID_SMOKE_REQUIRE_PINCH:-0}"
AUTH_LIFECYCLE_BACKGROUND_SECONDS="${ANDROID_SMOKE_LIFECYCLE_BACKGROUND_SECONDS:-35}"
AUTH_LIFECYCLE_INTENT_BACKGROUND_SECONDS="${ANDROID_SMOKE_LIFECYCLE_INTENT_BACKGROUND_SECONDS:-12}"
AUTH_LIFECYCLE_NETWORK_LOSS="${ANDROID_SMOKE_LIFECYCLE_NETWORK_LOSS:-auto}"
AUTH_LIFECYCLE_RECREATE_ACTIVITY="${ANDROID_SMOKE_LIFECYCLE_RECREATE_ACTIVITY:-1}"
REQUIRE_NONZERO_INSET="${ANDROID_SMOKE_REQUIRE_NONZERO_INSET:-0}"
AUTH_OFFLINE_TIMEOUT="${ANDROID_SMOKE_AUTH_OFFLINE_TIMEOUT:-135}"
INPUT_CHAR_DELAY="${ANDROID_SMOKE_INPUT_CHAR_DELAY:-0.2}"
AUTH_CHAT_MESSAGE="${ANDROID_SMOKE_CHAT_MESSAGE:-androidchat}"
AUTH_BANK_OBJECT_ID="${ANDROID_SMOKE_BANK_OBJECT_ID:-942}"
AUTH_BANK_OBJECT_ACTION="${ANDROID_SMOKE_BANK_OBJECT_ACTION:-OBJECT_COMMAND1}"
AUTH_BANK_PLAYER_X="${ANDROID_SMOKE_BANK_PLAYER_X:-210}"
AUTH_BANK_PLAYER_Y="${ANDROID_SMOKE_BANK_PLAYER_Y:-440}"
AUTH_BANK_SEARCH_TEXT="${ANDROID_SMOKE_BANK_SEARCH_TEXT:-coin}"
AUTH_BANK_SCROLL_START_X="${ANDROID_SMOKE_BANK_SCROLL_START_X:-256}"
AUTH_BANK_SCROLL_START_Y="${ANDROID_SMOKE_BANK_SCROLL_START_Y:-165}"
AUTH_BANK_SCROLL_END_Y="${ANDROID_SMOKE_BANK_SCROLL_END_Y:-75}"
AUTH_BANK_SCROLL_DURATION_MS="${ANDROID_SMOKE_BANK_SCROLL_DURATION_MS:-700}"
AUTH_BANK_ITEM_ID="${ANDROID_SMOKE_BANK_ITEM_ID:-10}"
AUTH_BANK_ITEM_AMOUNT="${ANDROID_SMOKE_BANK_ITEM_AMOUNT:-200}"
AUTH_BANK_INVENTORY_AMOUNT="${ANDROID_SMOKE_BANK_INVENTORY_AMOUNT:-20}"
AUTH_BANK_FIXTURE_BANK_SLOTS="${ANDROID_SMOKE_BANK_FIXTURE_BANK_SLOTS:-241}"
AUTH_BANK_FIXTURE_START_ITEM_ID="${ANDROID_SMOKE_BANK_FIXTURE_START_ITEM_ID:-11}"
AUTH_SHOP_NPC_ID="${ANDROID_SMOKE_SHOP_NPC_ID:-185}"
AUTH_SHOP_NPC_ACTION="${ANDROID_SMOKE_SHOP_NPC_ACTION:-NPC_COMMAND1}"
AUTH_SHOP_PLAYER_X="${ANDROID_SMOKE_SHOP_PLAYER_X:-224}"
AUTH_SHOP_PLAYER_Y="${ANDROID_SMOKE_SHOP_PLAYER_Y:-441}"
AUTH_SHOP_BUY_SLOT="${ANDROID_SMOKE_SHOP_BUY_SLOT:-0}"
AUTH_SHOP_COIN_AMOUNT="${ANDROID_SMOKE_SHOP_COIN_AMOUNT:-10000}"
AUTH_EQUIPMENT_INVENTORY_SLOT="${ANDROID_SMOKE_EQUIPMENT_INVENTORY_SLOT:-0}"
AUTH_EQUIPMENT_ITEM_ID="${ANDROID_SMOKE_EQUIPMENT_ITEM_ID:-4}"
AUTH_EQUIPMENT_ITEM_AMOUNT="${ANDROID_SMOKE_EQUIPMENT_ITEM_AMOUNT:-1}"
AUTH_MAGIC_PRAYER_SPELL_ID="${ANDROID_SMOKE_MAGIC_PRAYER_SPELL_ID:-0}"
AUTH_MAGIC_PRAYER_PRAYER_ID="${ANDROID_SMOKE_MAGIC_PRAYER_PRAYER_ID:-0}"
AUTH_MAGIC_PRAYER_HOME_X="${ANDROID_SMOKE_MAGIC_PRAYER_HOME_X:-120}"
AUTH_MAGIC_PRAYER_HOME_Y="${ANDROID_SMOKE_MAGIC_PRAYER_HOME_Y:-648}"
AUTH_WORLD_MAP_SEARCH_TEXT="${ANDROID_SMOKE_WORLD_MAP_SEARCH_TEXT:-varrock}"
AUTH_GROUND_LOOT_ITEM_ID="${ANDROID_SMOKE_GROUND_LOOT_ITEM_ID:-93}"
AUTH_GROUND_LOOT_ITEM_AMOUNT="${ANDROID_SMOKE_GROUND_LOOT_ITEM_AMOUNT:-1}"
AUTH_GROUND_LOOT_PLAYER_X="${ANDROID_SMOKE_GROUND_LOOT_PLAYER_X:-23}"
AUTH_GROUND_LOOT_PLAYER_Y="${ANDROID_SMOKE_GROUND_LOOT_PLAYER_Y:-25}"
AUTH_WILDERNESS_PLAYER_X="${ANDROID_SMOKE_WILDERNESS_PLAYER_X:-23}"
AUTH_WILDERNESS_PLAYER_Y="${ANDROID_SMOKE_WILDERNESS_PLAYER_Y:-25}"
AUTH_WILDERNESS_BOT_COUNT="${ANDROID_SMOKE_WILDERNESS_BOT_COUNT:-1}"
AUTH_WILDERNESS_BOSS_ID="${ANDROID_SMOKE_WILDERNESS_BOSS_ID:-1}"
AUTH_WILDERNESS_RADIUS="${ANDROID_SMOKE_WILDERNESS_RADIUS:-3}"
AUTH_WILDERNESS_TARGET_NAME="${ANDROID_SMOKE_WILDERNESS_TARGET_NAME:-cinebot0001}"
AUTH_WILDERNESS_PLAYER_ACTION="${ANDROID_SMOKE_WILDERNESS_PLAYER_ACTION:-PLAYER_ATTACK}"
AUTH_PVP_STRESS_FOOD_SLOT="${ANDROID_SMOKE_PVP_STRESS_FOOD_SLOT:-0}"
AUTH_PVP_STRESS_FOOD_ID="${ANDROID_SMOKE_PVP_STRESS_FOOD_ID:-370}"
AUTH_PVP_STRESS_POTION_SLOT="${ANDROID_SMOKE_PVP_STRESS_POTION_SLOT:-1}"
AUTH_PVP_STRESS_POTION_ID="${ANDROID_SMOKE_PVP_STRESS_POTION_ID:-221}"
AUTH_PVP_STRESS_POTION_NEXT_ID="${ANDROID_SMOKE_PVP_STRESS_POTION_NEXT_ID:-222}"
AUTH_PVP_STRESS_SPELL_ID="${ANDROID_SMOKE_PVP_STRESS_SPELL_ID:-1}"
AUTH_PVP_STRESS_WALK_CLIENT_X="${ANDROID_SMOKE_PVP_STRESS_WALK_CLIENT_X:-256}"
AUTH_PVP_STRESS_WALK_CLIENT_Y="${ANDROID_SMOKE_PVP_STRESS_WALK_CLIENT_Y:-92}"
AUTH_FIXTURE_ITEM_ID_BASE="${ANDROID_SMOKE_FIXTURE_ITEM_ID_BASE:-1000000}"
PENDING_SERVER_HOST=""
PENDING_SERVER_PORT=""

usage() {
    cat <<EOF
Usage: scripts/android-smoke.sh [--no-build] [--no-install] [--only-bootstrap] [--only-account-handoff] [--only-auth-credentials] [--only-auth-login] [--only-auth-lifecycle] [--only-auth-camera] [--only-auth-zoom] [--only-auth-chat-tabs] [--only-auth-chat-send] [--only-auth-bank] [--only-auth-shop] [--only-auth-equipment] [--only-auth-magic-prayer] [--only-auth-world-map] [--only-auth-settings] [--only-auth-afk] [--only-auth-ground-loot] [--only-auth-wilderness-target] [--only-auth-pvp-stress] [--out DIR]

Builds and installs the debug APK, starts $AVD_NAME when no Android device is
connected, launches the wrapper, and captures the core Android QA screenshots.

Environment:
  ANDROID_HOME / ANDROID_SDK_ROOT  Android SDK root
  ANDROID_SMOKE_ADB                Optional adb executable override
  ANDROID_SMOKE_EMULATOR           Optional emulator executable override
  AVD_NAME                         Emulator name, default: voidscape_api35
  ANDROID_SCREENSHOT_DIR           Output directory
  ANDROID_SMOKE_AUTH_USER          Optional game username for in-game/logout smoke
  ANDROID_SMOKE_AUTH_PASS          Optional game password for in-game/logout smoke
  ANDROID_SMOKE_AUTH_HOST          Optional auth smoke host, default: 10.0.2.2
  ANDROID_SMOKE_AUTH_PORT          Optional auth smoke port, default: 43596
  ANDROID_SMOKE_ACCOUNT_HANDOFF_HOST Optional reachable server used only to load the login home for --only-account-handoff
  ANDROID_SMOKE_ACCOUNT_HANDOFF_PORT Optional account-handoff server port, default: 43596
  ANDROID_SMOKE_AUTH_DB            Optional SQLite DB path for movement assertions
  ANDROID_SMOKE_LEAVE_LOGGED_IN=1  Leave --only-auth-login running in-game for manual testing
  ANDROID_SMOKE_AUTH_USE_BUNDLED_ENDPOINT=1
                                    Optional: do not write the requested endpoint before auth smoke launch
  ANDROID_SMOKE_AUTH_*_X_PCT/Y_PCT Optional login-screen tap percentage overrides
  --only-bootstrap                 Focused offline bundled-cache install/repair/skip and endpoint smoke
  --only-account-handoff           Focused Create Account portal URL smoke; no game server required
  --only-auth-credentials          Focused opt-in encrypted saved-login, persistence, and forget smoke
  --only-auth-login                Focused auth smoke; defaults to android/android and server/inc/sqlite/voidscape.db
  --only-auth-lifecycle            Focused auth smoke for login, resume/relaunch, logout, and crash checks
  --only-auth-chat-tabs            Focused split-rail mobile hub smoke in portrait and landscape
  --only-auth-chat-send            Focused inline composer, optional history filters, send, IME, and Back smoke
  --only-auth-afk                  Focused foreground low-resource AFK monitor entry, cadence, and resume smoke
  ANDROID_SMOKE_NPC_ID             Optional in-game NPC id for tap proof, default: 839
  ANDROID_SMOKE_NPC_ACTION         Expected shared NPC action, default: NPC_TALK_TO
  ANDROID_SMOKE_NPC_PLAYER_X       Optional DB x for NPC fixture, default: 23
  ANDROID_SMOKE_NPC_PLAYER_Y       Optional DB y for NPC fixture, default: 25
  ANDROID_SMOKE_NPC_FALLBACK_CLIENT_X Optional NPC fixture fallback client x, default: 309
  ANDROID_SMOKE_NPC_FALLBACK_CLIENT_Y Optional NPC fixture fallback client y, default: 183
  ANDROID_SMOKE_OBJECT_ID          Optional scenery id for tap proof, default: 1303
  ANDROID_SMOKE_OBJECT_ACTION      Expected shared object action, default: OBJECT_COMMAND1
  ANDROID_SMOKE_OBJECT_PLAYER_X    Optional DB x for object fixture, default: 123
  ANDROID_SMOKE_OBJECT_PLAYER_Y    Optional DB y for object fixture, default: 326
  ANDROID_SMOKE_INVENTORY_SLOT     Optional inventory slot for tap proof, default: 0
  ANDROID_SMOKE_INVENTORY_ITEM_ID  Optional temporary item id, default: 10
  ANDROID_SMOKE_INVENTORY_ITEM_AMOUNT Optional temporary item amount, default: 123
  ANDROID_SMOKE_INVENTORY_ACTION   Expected inventory action, default: ITEM_USE
  ANDROID_SMOKE_ITEM_ON_ITEM_TARGET_SLOT Optional target slot, default: 1
  ANDROID_SMOKE_ITEM_ON_ITEM_TARGET_ID Optional temporary target item id, default: 166
  ANDROID_SMOKE_ITEM_ON_ITEM_TARGET_AMOUNT Optional target item amount, default: 1
  ANDROID_SMOKE_ITEM_ON_ITEM_ACTION Expected item-on-item action, default: ITEM_USE_ITEM
  ANDROID_SMOKE_ITEM_ON_TARGET_SLOT Optional source slot for item-on-target proofs, default: 0
  ANDROID_SMOKE_ITEM_ON_TARGET_ITEM_ID Optional source item id, default: 10
  ANDROID_SMOKE_ITEM_ON_TARGET_ITEM_AMOUNT Optional source item amount, default: 123
  ANDROID_SMOKE_ITEM_ON_OBJECT_ACTION Expected item-on-object action, default: OBJECT_USE_ITEM
  ANDROID_SMOKE_ITEM_ON_NPC_ACTION   Expected item-on-NPC action, default: NPC_USE_ITEM
  ANDROID_SMOKE_CONTEXT_MENU_FIRST_ACTION Expected first long-press menu action, default: NPC_TALK_TO
  ANDROID_SMOKE_EDGE_MENU_CLIENT_X   Optional edge menu client x, default: 508
  ANDROID_SMOKE_EDGE_MENU_CLIENT_Y   Optional edge menu client y, default: 306
  ANDROID_SMOKE_EDGE_MENU_ACTION     Expected edge menu action, default: LANDSCAPE_WALK_HERE
  ANDROID_SMOKE_CAMERA_SWIPE_START_X Optional rotate swipe start client x, default: 420
  ANDROID_SMOKE_CAMERA_SWIPE_END_X   Optional rotate swipe end client x, default: 120
  ANDROID_SMOKE_CAMERA_SWIPE_Y       Optional rotate swipe client y, default: 170
  ANDROID_SMOKE_CAMERA_SWIPE_DURATION_MS Optional rotate swipe duration, default: 700
  ANDROID_SMOKE_ZOOM_DRAG_X          Optional one-finger no-zoom drag client x, default: 256
  ANDROID_SMOKE_ZOOM_DRAG_START_Y    Optional one-finger no-zoom drag start y, default: 80
  ANDROID_SMOKE_ZOOM_DRAG_END_Y      Optional one-finger no-zoom drag end y, default: 190
  ANDROID_SMOKE_ZOOM_DRAG_DURATION_MS Optional one-finger no-zoom drag duration, default: 700
  ANDROID_SMOKE_MANUAL_PINCH_SECONDS Optional physical-device manual pinch window, default: 0
  ANDROID_SMOKE_REQUIRE_PINCH         Set to 1 with a manual pinch window to require real pinch telemetry
  ANDROID_SMOKE_LIFECYCLE_BACKGROUND_SECONDS Optional --only-auth-lifecycle HOME wait, default: 35
  ANDROID_SMOKE_LIFECYCLE_INTENT_BACKGROUND_SECONDS Optional additional reconnect-intent waits, default: 12 (minimum: 10)
  ANDROID_SMOKE_LIFECYCLE_NETWORK_LOSS Optional unrelated-loss stale-intent coverage: auto, 0, or 1; auto runs on emulators
  ANDROID_SMOKE_LIFECYCLE_RECREATE_ACTIVITY Set to 0 to skip safely-restored Don't keep activities coverage, default: 1
  ANDROID_SMOKE_REQUIRE_NONZERO_INSET Set to 1 to require a cutout/system-bar inset in lifecycle viewport assertions
  ANDROID_SMOKE_AUTH_OFFLINE_TIMEOUT  Optional auth DB offline wait, default: 135
  ANDROID_SMOKE_CHAT_MESSAGE         Optional in-game chat message, default: androidchat
  ANDROID_SMOKE_BANK_OBJECT_ID       Optional bank chest object id, default: 942
  ANDROID_SMOKE_BANK_OBJECT_ACTION   Expected bank chest action, default: OBJECT_COMMAND1
  ANDROID_SMOKE_BANK_PLAYER_X/Y      Optional DB x/y for bank fixture, default: 210,440
  ANDROID_SMOKE_BANK_SEARCH_TEXT     Optional bank search text, default: coin
  ANDROID_SMOKE_BANK_SCROLL_*        Optional bank scroll swipe coordinates/duration
  ANDROID_SMOKE_BANK_ITEM_ID         Optional seeded bank item id, default: 10
  ANDROID_SMOKE_BANK_ITEM_AMOUNT     Optional seeded bank item amount, default: 200
  ANDROID_SMOKE_BANK_INVENTORY_AMOUNT Optional seeded inventory item amount, default: 20
  ANDROID_SMOKE_SHOP_NPC_ID          Optional shop NPC id, default: 185 (Edgeville shopkeeper)
  ANDROID_SMOKE_SHOP_NPC_ACTION      Expected shop NPC action, default: NPC_COMMAND1
  ANDROID_SMOKE_SHOP_PLAYER_X/Y      Optional DB x/y for shop fixture, default: 224,441
  ANDROID_SMOKE_SHOP_BUY_SLOT        Optional shop slot to select, default: 0
  ANDROID_SMOKE_SHOP_COIN_AMOUNT     Optional temporary coin amount, default: 10000
  ANDROID_SMOKE_EQUIPMENT_INVENTORY_SLOT Optional wearable source slot, default: 0
  ANDROID_SMOKE_EQUIPMENT_ITEM_ID    Optional temporary wearable item id, default: 4 (Wooden Shield)
  ANDROID_SMOKE_EQUIPMENT_ITEM_AMOUNT Optional temporary wearable amount, default: 1
  ANDROID_SMOKE_MAGIC_PRAYER_SPELL_ID Optional spell id for self-cast proof, default: 0 (Home teleport)
  ANDROID_SMOKE_MAGIC_PRAYER_PRAYER_ID Optional prayer id for toggle proof, default: 0 (Thick skin)
  ANDROID_SMOKE_MAGIC_PRAYER_HOME_X/Y Optional expected Home teleport x/y, default: 120,648
  ANDROID_SMOKE_WORLD_MAP_SEARCH_TEXT Optional world-map search text, default: varrock
  --only-auth-settings requires ANDROID_SMOKE_AUTH_DB and verifies camera/mouse setting persistence
  --only-auth-ground-loot requires ANDROID_SMOKE_AUTH_DB and verifies labels/beams from a real dropped item
  --only-auth-wilderness-target requires ANDROID_SMOKE_AUTH_DB and verifies player target selection in wilderness
  --only-auth-pvp-stress requires ANDROID_SMOKE_AUTH_DB and verifies food/potion/spell/player target/walk-away input
  ANDROID_SMOKE_GROUND_LOOT_ITEM_ID Optional rare-beam fixture item id, default: 93 (Rune battle axe)
  ANDROID_SMOKE_GROUND_LOOT_PLAYER_X/Y Optional DB x/y for ground-loot fixture, default: 23,25
  ANDROID_SMOKE_WILDERNESS_PLAYER_X/Y Optional DB x/y for player-target fixture, default: 23,25
  ANDROID_SMOKE_WILDERNESS_BOT_COUNT Optional cinematic player count, default: 1
  ANDROID_SMOKE_WILDERNESS_BOSS_ID   Optional cinematic anchor NPC id, default: 1 (Bob)
  ANDROID_SMOKE_WILDERNESS_RADIUS    Optional cinematic radius, default: 3
  ANDROID_SMOKE_WILDERNESS_TARGET_NAME Optional target player token, default: cinebot0001
  ANDROID_SMOKE_PVP_STRESS_FOOD_ID   Optional food fixture item id, default: 370 (Swordfish)
  ANDROID_SMOKE_PVP_STRESS_POTION_ID Optional potion fixture item id, default: 221 (Strength potion 4-dose)
  ANDROID_SMOKE_PVP_STRESS_SPELL_ID  Optional player-cast spell id, default: 1 (Wind strike)
  ANDROID_SMOKE_FIXTURE_ITEM_ID_BASE Optional DB-only fixture itemID floor, default: 1000000
EOF
}

ensure_sqlite3_command() {
    if command -v sqlite3 >/dev/null 2>&1; then
        return 0
    fi
    if ! command -v python >/dev/null 2>&1; then
        echo "WARNING: sqlite3 is unavailable and python was not found for the smoke SQLite fallback." >&2
        return 0
    fi

    local shim_dir shim
    shim_dir="$OUT_DIR/.android-smoke-bin"
    shim="$shim_dir/sqlite3"
    mkdir -p "$shim_dir"
    cat > "$shim" <<'PY'
#!/usr/bin/env python
import os
import re
import sqlite3
import sys


def normalize_path(path):
    if re.match(r"^/[a-zA-Z]/", path):
        return path[1] + ":" + path[2:].replace("/", "\\")
    return path


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bytes):
        return "X'" + value.hex() + "'"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def row_value(value):
    return "" if value is None else str(value)


def parse_args(argv):
    commands = []
    options = {
        "separator": "|",
        "header": False,
        "noheader": False,
        "column": False,
    }
    db_path = None
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "-cmd":
            commands.append(argv[i + 1])
            i += 2
        elif arg == "-separator":
            options["separator"] = argv[i + 1]
            i += 2
        elif arg == "-header":
            options["header"] = True
            i += 1
        elif arg == "-noheader":
            options["noheader"] = True
            i += 1
        elif arg == "-column":
            options["column"] = True
            i += 1
        elif arg.startswith("-"):
            i += 1
        else:
            db_path = arg
            commands.extend(argv[i + 1 :])
            break
    return db_path, commands, options


def main():
    db_path, commands, options = parse_args(sys.argv[1:])
    if not db_path:
        print("sqlite3 fallback requires a database path", file=sys.stderr)
        return 1

    if not sys.stdin.isatty():
        stdin_sql = sys.stdin.read()
        if stdin_sql.strip():
            commands.append(stdin_sql)

    mode_insert = None
    try:
        con = sqlite3.connect(normalize_path(db_path), timeout=5)
        con.execute("PRAGMA busy_timeout = 5000")

        def handle_dot(command):
            nonlocal mode_insert
            stripped = command.strip()
            if not stripped:
                return True
            if stripped.startswith(".timeout"):
                parts = stripped.split()
                if len(parts) > 1 and parts[1].isdigit():
                    con.execute(f"PRAGMA busy_timeout = {int(parts[1])}")
                return True
            if stripped.startswith(".mode"):
                parts = stripped.split()
                mode_insert = parts[2] if len(parts) >= 3 and parts[1] == "insert" else None
                return True
            return stripped.startswith(".")

        def strip_dot_lines(command):
            lines = []
            for line in command.splitlines():
                if handle_dot(line):
                    continue
                lines.append(line)
            return "\n".join(lines).strip()

        def emit_rows(cursor):
            if options["header"] and not options["noheader"]:
                print(options["separator"].join(column[0] for column in cursor.description or []))
            for row in cursor.fetchall():
                print(options["separator"].join(row_value(value) for value in row))

        def emit_inserts(cursor, table):
            for row in cursor.fetchall():
                values = ", ".join(sql_literal(value) for value in row)
                print(f"INSERT INTO {table} VALUES({values});")

        for command in commands:
            sql = strip_dot_lines(command)
            if not sql:
                continue
            starts_with_select = sql.lstrip().lower().startswith(("select", "with", "pragma"))
            if starts_with_select:
                cursor = con.execute(sql)
                if mode_insert:
                    emit_inserts(cursor, mode_insert)
                else:
                    emit_rows(cursor)
            else:
                con.executescript(sql)
        con.commit()
        con.close()
        return 0
    except Exception as exc:
        print(f"sqlite3 fallback error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
PY
    chmod +x "$shim"
    export PATH="$shim_dir:$PATH"
    echo "sqlite3 not found; using Python sqlite3 fallback at $shim" >&2
}

while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --no-build)
            BUILD=0
            shift
            ;;
        --no-install)
            INSTALL=0
            shift
            ;;
        --only-bootstrap)
            ONLY_BOOTSTRAP=1
            shift
            ;;
        --only-account-handoff)
            ONLY_ACCOUNT_HANDOFF=1
            shift
            ;;
        --only-auth-camera)
            ONLY_AUTH_CAMERA=1
            shift
            ;;
        --only-auth-zoom)
            ONLY_AUTH_ZOOM=1
            shift
            ;;
        --only-auth-chat-tabs)
            ONLY_AUTH_CHAT_TABS=1
            shift
            ;;
        --only-auth-chat-send)
            ONLY_AUTH_CHAT_SEND=1
            shift
            ;;
        --only-auth-bank)
            ONLY_AUTH_BANK=1
            shift
            ;;
        --only-auth-shop)
            ONLY_AUTH_SHOP=1
            shift
            ;;
        --only-auth-equipment)
            ONLY_AUTH_EQUIPMENT=1
            shift
            ;;
        --only-auth-magic-prayer)
            ONLY_AUTH_MAGIC_PRAYER=1
            shift
            ;;
        --only-auth-world-map)
            ONLY_AUTH_WORLD_MAP=1
            shift
            ;;
        --only-auth-settings)
            ONLY_AUTH_SETTINGS=1
            shift
            ;;
        --only-auth-afk)
            ONLY_AUTH_AFK=1
            shift
            ;;
        --only-auth-ground-loot)
            ONLY_AUTH_GROUND_LOOT=1
            shift
            ;;
        --only-auth-wilderness-target)
            ONLY_AUTH_WILDERNESS_TARGET=1
            shift
            ;;
        --only-auth-pvp-stress)
            ONLY_AUTH_PVP_STRESS=1
            shift
            ;;
        --only-auth-login)
            ONLY_AUTH_LOGIN=1
            shift
            ;;
        --only-auth-credentials)
            ONLY_AUTH_CREDENTIALS=1
            shift
            ;;
        --only-auth-lifecycle)
            ONLY_AUTH_LIFECYCLE=1
            shift
            ;;
        --out)
            OUT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR: unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ "$REQUIRE_NONZERO_INSET" != "0" && "$REQUIRE_NONZERO_INSET" != "1" ]]; then
	echo "ERROR: ANDROID_SMOKE_REQUIRE_NONZERO_INSET must be 0 or 1" >&2
	exit 1
fi

if [[ ! "$AUTH_LIFECYCLE_BACKGROUND_SECONDS" =~ ^[0-9]+$ \
	|| "$AUTH_LIFECYCLE_BACKGROUND_SECONDS" -lt 10 ]]; then
	echo "ERROR: ANDROID_SMOKE_LIFECYCLE_BACKGROUND_SECONDS must be an integer >= 10" >&2
	exit 1
fi
if [[ ! "$AUTH_LIFECYCLE_INTENT_BACKGROUND_SECONDS" =~ ^[0-9]+$ \
	|| "$AUTH_LIFECYCLE_INTENT_BACKGROUND_SECONDS" -lt 10 ]]; then
	echo "ERROR: ANDROID_SMOKE_LIFECYCLE_INTENT_BACKGROUND_SECONDS must be an integer >= 10" >&2
	exit 1
fi
if [[ "$AUTH_LIFECYCLE_NETWORK_LOSS" != "auto" \
	&& "$AUTH_LIFECYCLE_NETWORK_LOSS" != "0" \
	&& "$AUTH_LIFECYCLE_NETWORK_LOSS" != "1" ]]; then
	echo "ERROR: ANDROID_SMOKE_LIFECYCLE_NETWORK_LOSS must be auto, 0, or 1" >&2
	exit 1
fi
if [[ "$AUTH_LIFECYCLE_RECREATE_ACTIVITY" != "0" \
	&& "$AUTH_LIFECYCLE_RECREATE_ACTIVITY" != "1" ]]; then
	echo "ERROR: ANDROID_SMOKE_LIFECYCLE_RECREATE_ACTIVITY must be 0 or 1" >&2
	exit 1
fi

if [[ -z "$ADB" || ! -x "$ADB" ]]; then
    echo "ERROR: adb not found. Set ANDROID_HOME, ANDROID_SDK_ROOT, ANDROID_SMOKE_ADB, or Android_Client/local.properties sdk.dir." >&2
    exit 1
fi

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR/.android-mobile-viewport.log"
ensure_sqlite3_command

if [[ "$BUILD" -eq 1 ]]; then
    "$SCRIPT_DIR/build-android.sh"
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
    if [[ -z "$EMULATOR" || ! -x "$EMULATOR" ]]; then
        echo "ERROR: no connected Android device and emulator not found. Set ANDROID_SMOKE_EMULATOR or an Android SDK root." >&2
        exit 1
    fi
    echo "Starting Android emulator: $AVD_NAME"
    nohup "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -netdelay none -netspeed full -no-snapshot-load -no-snapshot-save \
        > "$OUT_DIR/emulator.log" 2>&1 &
fi

"$ADB" wait-for-device
"$ADB" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'

if [[ "$INSTALL" -eq 1 ]]; then
    "$ADB" install -r "$APK"
fi

write_smoke_file() {
    local file="$1"
    local value="${2:-}"
    "$ADB" shell "mkdir -p '$APP_SMOKE_FILES' && printf %s '$value' > '$file'"
}

touch_smoke_file() {
    local file="$1"
    "$ADB" shell "mkdir -p '$APP_SMOKE_FILES' && touch '$file'"
}

remove_smoke_files() {
    "$ADB" shell "rm -f $*" 2>/dev/null || true
}

enable_android_smoke_npc_targets() {
    touch_smoke_file "$SMOKE_NPC_TARGETS_FLAG"
}

disable_android_smoke_npc_targets() {
    remove_smoke_files "$SMOKE_NPC_TARGETS_FLAG"
}

enable_android_smoke_player_targets() {
    touch_smoke_file "$SMOKE_PLAYER_TARGETS_FLAG"
}

disable_android_smoke_player_targets() {
    remove_smoke_files "$SMOKE_PLAYER_TARGETS_FLAG" "$SMOKE_PLAYER_COMMAND_FILE"
}

write_android_smoke_player_command() {
    local command="$1"
    write_smoke_file "$SMOKE_PLAYER_COMMAND_FILE" "$command"
}

enable_android_smoke_object_targets() {
    touch_smoke_file "$SMOKE_OBJECT_TARGETS_FLAG"
}

disable_android_smoke_object_targets() {
    remove_smoke_files "$SMOKE_OBJECT_TARGETS_FLAG"
}

enable_android_smoke_inventory_targets() {
    touch_smoke_file "$SMOKE_INVENTORY_TARGETS_FLAG"
}

disable_android_smoke_inventory_targets() {
    remove_smoke_files "$SMOKE_INVENTORY_TARGETS_FLAG"
}

enable_android_smoke_walk() {
    touch_smoke_file "$SMOKE_WALK_FLAG"
}

disable_android_smoke_walk() {
    remove_smoke_files "$SMOKE_WALK_FLAG"
}

enable_android_smoke_camera() {
    touch_smoke_file "$SMOKE_CAMERA_FLAG"
}

disable_android_smoke_camera() {
    remove_smoke_files "$SMOKE_CAMERA_FLAG"
}

enable_android_smoke_zoom() {
    touch_smoke_file "$SMOKE_ZOOM_FLAG"
}

disable_android_smoke_zoom() {
    remove_smoke_files "$SMOKE_ZOOM_FLAG"
}

enable_android_smoke_chat_tabs() {
    touch_smoke_file "$SMOKE_CHAT_TABS_FLAG"
}

disable_android_smoke_chat_tabs() {
    remove_smoke_files "$SMOKE_CHAT_TABS_FLAG"
}

enable_android_smoke_chat_send() {
    touch_smoke_file "$SMOKE_CHAT_SEND_FLAG"
}

disable_android_smoke_chat_send() {
    remove_smoke_files "$SMOKE_CHAT_SEND_FLAG"
}

enable_android_smoke_bank() {
    touch_smoke_file "$SMOKE_BANK_FLAG"
}

disable_android_smoke_bank() {
    remove_smoke_files "$SMOKE_BANK_FLAG"
}

enable_android_smoke_shop() {
    touch_smoke_file "$SMOKE_SHOP_FLAG"
}

disable_android_smoke_shop() {
    remove_smoke_files "$SMOKE_SHOP_FLAG"
}

enable_android_smoke_equipment() {
    touch_smoke_file "$SMOKE_EQUIPMENT_FLAG"
}

disable_android_smoke_equipment() {
    remove_smoke_files "$SMOKE_EQUIPMENT_FLAG"
}

enable_android_smoke_magic_prayer() {
    touch_smoke_file "$SMOKE_MAGIC_PRAYER_FLAG"
}

disable_android_smoke_magic_prayer() {
    remove_smoke_files "$SMOKE_MAGIC_PRAYER_FLAG"
}

enable_android_smoke_audio() {
	touch_smoke_file "$SMOKE_AUDIO_FLAG"
}

disable_android_smoke_audio() {
	remove_smoke_files "$SMOKE_AUDIO_FLAG"
}

enable_android_smoke_network() {
	touch_smoke_file "$SMOKE_NETWORK_FLAG"
}

disable_android_smoke_network() {
	remove_smoke_files "$SMOKE_NETWORK_FLAG"
}

enable_android_smoke_world_map() {
    touch_smoke_file "$SMOKE_WORLD_MAP_FLAG"
}

disable_android_smoke_world_map() {
    remove_smoke_files "$SMOKE_WORLD_MAP_FLAG"
}

enable_android_smoke_settings() {
    touch_smoke_file "$SMOKE_SETTINGS_FLAG"
}

disable_android_smoke_settings() {
    remove_smoke_files "$SMOKE_SETTINGS_FLAG"
}

enable_android_smoke_afk() {
	touch_smoke_file "$SMOKE_AFK_FLAG"
	touch_smoke_file "$SMOKE_SETTINGS_FLAG"
}

disable_android_smoke_afk() {
	remove_smoke_files "$SMOKE_AFK_FLAG" "$SMOKE_SETTINGS_FLAG"
}

enable_android_smoke_ground_loot() {
    touch_smoke_file "$SMOKE_GROUND_LOOT_FLAG"
}

disable_android_smoke_ground_loot() {
    remove_smoke_files "$SMOKE_GROUND_LOOT_FLAG"
}

enable_android_smoke_appearance_prompt() {
    touch_smoke_file "$SMOKE_APPEARANCE_PROMPT_FLAG"
}

disable_android_smoke_appearance_prompt() {
    remove_smoke_files "$SMOKE_APPEARANCE_PROMPT_FLAG"
}

enable_android_smoke_login() {
    touch_smoke_file "$SMOKE_LOGIN_FLAG"
}

disable_android_smoke_login() {
    remove_smoke_files "$SMOKE_LOGIN_FLAG"
}

enable_android_smoke_lifecycle_reconnect_hold() {
	"$ADB" shell run-as "$APP_ID" touch "$SMOKE_LIFECYCLE_RECONNECT_HOLD_FLAG"
}

disable_android_smoke_lifecycle_reconnect_hold() {
	"$ADB" shell run-as "$APP_ID" rm -f "$SMOKE_LIFECYCLE_RECONNECT_HOLD_FLAG" \
		>/dev/null 2>&1 || true
}

save_android_rotation() {
    if [[ "$ROTATION_STATE_SAVED" == "1" ]]; then
        return 0
    fi
    ORIGINAL_ACCELEROMETER_ROTATION="$("$ADB" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r' | tail -1 || true)"
    ORIGINAL_USER_ROTATION="$("$ADB" shell settings get system user_rotation 2>/dev/null | tr -d '\r' | tail -1 || true)"
	read -r ORIGINAL_WINDOW_ROTATION_MODE ORIGINAL_WINDOW_ROTATION < <(
		"$ADB" shell cmd window user-rotation 2>/dev/null | tr -d '\r' | tail -1
	)
	ORIGINAL_IGNORE_ORIENTATION_REQUEST="$("$ADB" shell cmd window get-ignore-orientation-request 2>/dev/null \
		| tr -d '\r' | sed -nE 's/.*ignoreOrientationRequest (true|false).*/\1/p' | tail -1)"
    ROTATION_STATE_SAVED=1
}

restore_android_rotation() {
    if [[ "$ROTATION_STATE_SAVED" != "1" ]]; then
        return 0
    fi
	if [[ "$ORIGINAL_WINDOW_ROTATION_MODE" == "free" ]]; then
		"$ADB" shell cmd window user-rotation free >/dev/null 2>&1 || true
	elif [[ "$ORIGINAL_WINDOW_ROTATION_MODE" == "lock" \
		&& "$ORIGINAL_WINDOW_ROTATION" =~ ^[0-3]$ ]]; then
		"$ADB" shell cmd window user-rotation lock "$ORIGINAL_WINDOW_ROTATION" >/dev/null 2>&1 || true
	elif [[ "$ORIGINAL_ACCELEROMETER_ROTATION" == "1" ]]; then
		"$ADB" shell cmd window user-rotation free >/dev/null 2>&1 || true
	elif [[ -n "$ORIGINAL_USER_ROTATION" && "$ORIGINAL_USER_ROTATION" != "null" ]]; then
		"$ADB" shell cmd window user-rotation lock "$ORIGINAL_USER_ROTATION" >/dev/null 2>&1 || true
	fi
    if [[ -n "$ORIGINAL_ACCELEROMETER_ROTATION" && "$ORIGINAL_ACCELEROMETER_ROTATION" != "null" ]]; then
        "$ADB" shell settings put system accelerometer_rotation "$ORIGINAL_ACCELEROMETER_ROTATION" >/dev/null 2>&1 || true
    fi
    if [[ -n "$ORIGINAL_USER_ROTATION" && "$ORIGINAL_USER_ROTATION" != "null" ]]; then
        "$ADB" shell settings put system user_rotation "$ORIGINAL_USER_ROTATION" >/dev/null 2>&1 || true
    fi
	if [[ "$ORIGINAL_IGNORE_ORIENTATION_REQUEST" == "true" \
		|| "$ORIGINAL_IGNORE_ORIENTATION_REQUEST" == "false" ]]; then
		"$ADB" shell cmd window set-ignore-orientation-request \
			"$ORIGINAL_IGNORE_ORIENTATION_REQUEST" >/dev/null 2>&1 || true
	fi
}

save_android_display_override() {
    if [[ "$DISPLAY_OVERRIDE_STATE_SAVED" == "1" ]]; then
        return 0
    fi
    ORIGINAL_WM_SIZE_OVERRIDE="$("$ADB" shell wm size 2>/dev/null | tr -d '\r' \
        | sed -nE 's/^Override size: ([0-9]+x[0-9]+).*$/\1/p' | tail -1)"
    ORIGINAL_WM_DENSITY_OVERRIDE="$("$ADB" shell wm density 2>/dev/null | tr -d '\r' \
        | sed -nE 's/^Override density: ([0-9]+).*$/\1/p' | tail -1)"
    DISPLAY_OVERRIDE_STATE_SAVED=1
}

restore_android_display_override() {
    if [[ "$DISPLAY_OVERRIDE_STATE_SAVED" != "1" ]]; then
        return 0
    fi
    if [[ -n "$ORIGINAL_WM_SIZE_OVERRIDE" ]]; then
        "$ADB" shell wm size "$ORIGINAL_WM_SIZE_OVERRIDE" >/dev/null 2>&1 || true
    else
        "$ADB" shell wm size reset >/dev/null 2>&1 || true
    fi
    if [[ -n "$ORIGINAL_WM_DENSITY_OVERRIDE" ]]; then
        "$ADB" shell wm density "$ORIGINAL_WM_DENSITY_OVERRIDE" >/dev/null 2>&1 || true
    else
        "$ADB" shell wm density reset >/dev/null 2>&1 || true
    fi
    DISPLAY_OVERRIDE_STATE_SAVED=0
}

wait_for_screen_orientation() {
    local expected="$1"
    local timeout="${2:-20}"
    local quiet="${3:-0}"
    local deadline=$((SECONDS + timeout))
    local width height orientation

    while (( SECONDS < deadline )); do
        read -r width height < <(screen_size)
        if [[ "$width" =~ ^[0-9]+$ && "$height" =~ ^[0-9]+$ ]]; then
            if (( width > height )); then
                orientation="landscape"
            else
                orientation="portrait"
            fi
            if [[ "$orientation" == "$expected" ]]; then
                return 0
            fi
        fi
        sleep 1
    done

    if [[ "$quiet" != "1" ]]; then
        echo "ERROR: timed out waiting for Android screen orientation=$expected; last size=${width:-?}x${height:-?}" >&2
    fi
    return 1
}

force_android_rotation() {
    local rotation="$1"
    local expected_orientation="portrait"
    local attempt
    if [[ "$rotation" == "1" || "$rotation" == "3" ]]; then
        expected_orientation="landscape"
    fi

    save_android_rotation
	# The Android activities intentionally use fullSensor. Ignore that request only
	# for the test window so the emulator can be locked deterministically.
	"$ADB" shell cmd window set-ignore-orientation-request true >/dev/null 2>&1 || true
	"$ADB" shell cmd window user-rotation lock "$rotation" >/dev/null 2>&1 || true
    "$ADB" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || return 1
    "$ADB" shell settings put system user_rotation "$rotation" >/dev/null 2>&1 || return 1
    if wait_for_screen_orientation "$expected_orientation" 12 1; then
        return 0
    fi

    for attempt in 1 2; do
        "$ADB" emu rotate >/dev/null 2>&1 || true
        if wait_for_screen_orientation "$expected_orientation" 8 1; then
            return 0
        fi
    done
    echo "ERROR: unable to force Android screen orientation=$expected_orientation" >&2
    return 1
}

force_android_portrait() {
    force_android_rotation 0
}

force_android_landscape() {
    force_android_rotation 1
}

save_android_network_state() {
	if [[ "$NETWORK_STATE_SAVED" -eq 1 ]]; then
		return
	fi
	ORIGINAL_WIFI_STATE="$("$ADB" shell settings get global wifi_on 2>/dev/null | tr -d '\r' || true)"
	ORIGINAL_MOBILE_DATA_STATE="$("$ADB" shell settings get global mobile_data 2>/dev/null | tr -d '\r' || true)"
	NETWORK_STATE_SAVED=1
}

disable_android_network_for_bootstrap() {
	save_android_network_state
	"$ADB" shell svc wifi disable >/dev/null 2>&1 || true
	"$ADB" shell svc data disable >/dev/null 2>&1 || true
}

restore_android_network_state() {
	if [[ "$NETWORK_STATE_SAVED" -ne 1 ]]; then
		return
	fi
	if [[ "$ORIGINAL_WIFI_STATE" == "1" ]]; then
		"$ADB" shell svc wifi enable >/dev/null 2>&1 || true
	elif [[ "$ORIGINAL_WIFI_STATE" == "0" ]]; then
		"$ADB" shell svc wifi disable >/dev/null 2>&1 || true
	fi
	if [[ "$ORIGINAL_MOBILE_DATA_STATE" == "1" ]]; then
		"$ADB" shell svc data enable >/dev/null 2>&1 || true
	elif [[ "$ORIGINAL_MOBILE_DATA_STATE" == "0" ]]; then
		"$ADB" shell svc data disable >/dev/null 2>&1 || true
	fi
	NETWORK_STATE_SAVED=0
}

save_android_activity_policy() {
	if [[ "$ACTIVITY_POLICY_STATE_SAVED" -eq 1 ]]; then
		return 0
	fi
	ORIGINAL_ALWAYS_FINISH_ACTIVITIES="$("$ADB" shell settings get global \
		always_finish_activities 2>/dev/null | tr -d '\r' | tail -1 || true)"
	ACTIVITY_POLICY_STATE_SAVED=1
}

restore_android_activity_policy() {
	if [[ "$ACTIVITY_POLICY_STATE_SAVED" -ne 1 ]]; then
		return 0
	fi
	if [[ -z "$ORIGINAL_ALWAYS_FINISH_ACTIVITIES" \
		|| "$ORIGINAL_ALWAYS_FINISH_ACTIVITIES" == "null" ]]; then
		"$ADB" shell settings delete global always_finish_activities >/dev/null 2>&1 || true
	else
		"$ADB" shell settings put global always_finish_activities \
			"$ORIGINAL_ALWAYS_FINISH_ACTIVITIES" >/dev/null 2>&1 || true
	fi
	ACTIVITY_POLICY_STATE_SAVED=0
}

disable_android_smoke_targets() {
    disable_android_smoke_npc_targets
    disable_android_smoke_player_targets
    disable_android_smoke_object_targets
    disable_android_smoke_inventory_targets
    disable_android_smoke_camera
    disable_android_smoke_zoom
    disable_android_smoke_chat_tabs
    disable_android_smoke_chat_send
    disable_android_smoke_bank
    disable_android_smoke_shop
    disable_android_smoke_equipment
    disable_android_smoke_magic_prayer
	disable_android_smoke_audio
	disable_android_smoke_network
    disable_android_smoke_world_map
    disable_android_smoke_settings
	disable_android_smoke_afk
    disable_android_smoke_ground_loot
    disable_android_smoke_appearance_prompt
    disable_android_smoke_walk
    disable_android_smoke_login
	disable_android_smoke_lifecycle_reconnect_hold
}

android_smoke_cleanup() {
    disable_android_smoke_targets
    restore_android_activity_policy
    restore_android_display_override
    restore_android_rotation
	restore_android_network_state
}

disable_android_smoke_targets
trap android_smoke_cleanup EXIT

"$ADB" logcat -c || true
"$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt $APP_FILES/accounts.txt" 2>/dev/null || true

adb_screencap_to_file() {
    local output="$1"
    local tmp="$output.tmp"
    local status=0

	rm -f "$tmp"
	if command -v timeout >/dev/null 2>&1; then
	    timeout 20 "$ADB" exec-out screencap -p > "$tmp" || status=$?
	elif command -v perl >/dev/null 2>&1; then
	    perl -e 'alarm shift @ARGV; exec @ARGV' 20 "$ADB" exec-out screencap -p > "$tmp" || status=$?
	else
	    "$ADB" exec-out screencap -p > "$tmp" || status=$?
	fi

    if [[ "$status" -ne 0 || ! -s "$tmp" ]]; then
        rm -f "$tmp"
        return 1
    fi

    mv "$tmp" "$output"
}

screen_size() {
    local screenshot_file size
    screenshot_file="$OUT_DIR/.tap-screen.png"
    if adb_screencap_to_file "$screenshot_file"; then
        size="$(file "$screenshot_file" | sed -nE 's/.*PNG image data, ([0-9]+) x ([0-9]+).*/\1 \2/p')"
        if [[ -n "$size" ]]; then
            echo "$size"
            return
        fi
    fi

    size="$("$ADB" shell dumpsys window displays | tr -d '\r' | sed -nE 's/.*cur=([0-9]+)x([0-9]+).*/\1 \2/p' | head -1)"
    if [[ -n "$size" ]]; then
        echo "$size"
        return
    fi
    "$ADB" shell wm size | tr -d '\r' | awk -F'[: x]+' '/Physical size/ {print $(NF-1), $NF; exit}'
}

tap_pct() {
    local x_pct="$1"
    local y_pct="$2"
    local width height x y
    read -r width height < <(screen_size)
    x=$((width * x_pct / 100))
    y=$((height * y_pct / 100))
    "$ADB" shell input tap "$x" "$y"
}

long_press_pct() {
    local x_pct="$1"
    local y_pct="$2"
    local duration_ms="$3"
    local width height x y
    read -r width height < <(screen_size)
    x=$((width * x_pct / 100))
    y=$((height * y_pct / 100))
    "$ADB" shell input swipe "$x" "$y" "$x" "$y" "$duration_ms"
}

android_viewport_target_for_size() {
    local width="$1"
    local height="$2"
    awk -v sw="$width" -v sh="$height" 'BEGIN {
        baseW=512; baseFullH=346; maxLogical=1152;
        if (sw <= 0 || sh <= 0) {
            printf "%d %d %s %d\n", baseW, baseFullH, "landscape", 0;
            exit;
        }
        if (sh > sw) {
            raw=int(baseW * sh / sw + 0.5);
            fullH=raw;
            if (fullH < baseFullH) fullH=baseFullH;
            if (fullH > maxLogical) fullH=maxLogical;
            capped=0;
            if (raw > maxLogical) capped=1;
            printf "%d %d %s %d\n", baseW, fullH, "portrait", capped;
        } else {
            raw=int(baseFullH * sw / sh + 0.5);
            width=raw;
            if (width < baseW) width=baseW;
            if (width > maxLogical) width=maxLogical;
            capped=0;
            if (raw > maxLogical) capped=1;
            printf "%d %d %s %d\n", width, baseFullH, "landscape", capped;
        }
    }'
}

android_mobile_viewport_cache_file() {
    printf '%s\n' "$OUT_DIR/.android-mobile-viewport.log"
}

cache_android_mobile_viewport_line() {
    local line="$1"
    local cache_file
    [[ -n "$line" ]] || return 1
    cache_file="$(android_mobile_viewport_cache_file)"
    printf '%s\n' "$line" > "$cache_file"
}

android_mobile_viewport_log_is_settled_for_surface() {
    local line="$1"
    local current_surface_width="$2"
    local current_surface_height="$3"
    local surface_width surface_height content_width content_height
    local inset_left inset_top inset_right inset_bottom logical_width logical_height scale
    local expected_content_width expected_content_height expected_width expected_height orientation capped

    surface_width="$(extract_log_value "$line" surfaceW)"
    surface_height="$(extract_log_value "$line" surfaceH)"
    content_width="$(extract_log_value "$line" contentW)"
    content_height="$(extract_log_value "$line" contentH)"
    inset_left="$(extract_log_value "$line" insetL)"
    inset_top="$(extract_log_value "$line" insetT)"
    inset_right="$(extract_log_value "$line" insetR)"
    inset_bottom="$(extract_log_value "$line" insetB)"
    logical_width="$(extract_log_value "$line" logicalW)"
    logical_height="$(extract_log_value "$line" logicalH)"
    scale="$(extract_log_value "$line" scale)"

    [[ "$surface_width" =~ ^[0-9]+$ && "$surface_width" -gt 0 \
        && "$surface_height" =~ ^[0-9]+$ && "$surface_height" -gt 0 \
        && "$content_width" =~ ^[0-9]+$ && "$content_width" -gt 0 \
        && "$content_height" =~ ^[0-9]+$ && "$content_height" -gt 0 \
        && "$inset_left" =~ ^[0-9]+$ && "$inset_top" =~ ^[0-9]+$ \
        && "$inset_right" =~ ^[0-9]+$ && "$inset_bottom" =~ ^[0-9]+$ \
        && "$logical_width" =~ ^[0-9]+$ && "$logical_width" -gt 0 \
        && "$logical_height" =~ ^[0-9]+$ && "$logical_height" -gt 0 ]] || return 1
    is_positive_log_number "$scale" || return 1

    [[ "$surface_width" -eq "$current_surface_width" \
        && "$surface_height" -eq "$current_surface_height" ]] || return 1
    expected_content_width=$((surface_width - inset_left - inset_right))
    expected_content_height=$((surface_height - inset_top - inset_bottom))
    (( expected_content_width < 1 )) && expected_content_width=1
    (( expected_content_height < 1 )) && expected_content_height=1
    [[ "$content_width" -eq "$expected_content_width" \
        && "$content_height" -eq "$expected_content_height" ]] || return 1

    read -r expected_width expected_height orientation capped \
        < <(android_viewport_target_for_size "$content_width" "$content_height")
    [[ "$logical_width" -eq "$expected_width" && "$logical_height" -eq "$expected_height" ]] || return 1

    awk -v cw="$content_width" -v ch="$content_height" \
        -v lw="$logical_width" -v lh="$logical_height" -v actual="$scale" 'BEGIN {
        expectedX = cw / lw;
        expectedY = ch / lh;
        expected = expectedX < expectedY ? expectedX : expectedY;
        difference = actual - expected;
        if (difference < 0) difference = -difference;
        tolerance = expected * 0.001;
        if (tolerance < 0.001) tolerance = 0.001;
        exit !(difference <= tolerance);
    }'
}

latest_settled_android_mobile_viewport() {
    local surface_width="$1"
    local surface_height="$2"
    local line line_surface_width line_surface_height matching_line
    local cache_file
    local saw_current_surface=0
    local deadline=$((SECONDS + 3))

    while :; do
        matching_line=""
        saw_current_surface=0
        while IFS= read -r line; do
            line_surface_width="$(extract_log_value "$line" surfaceW)"
            line_surface_height="$(extract_log_value "$line" surfaceH)"
            if [[ "$line_surface_width" == "$surface_width" \
                && "$line_surface_height" == "$surface_height" ]]; then
                # Only the newest record for this surface can be considered
                # settled; a newer resize record invalidates older geometry.
                saw_current_surface=1
                matching_line=""
                if android_mobile_viewport_log_is_settled_for_surface \
                    "$line" "$surface_width" "$surface_height"; then
                    matching_line="$line"
                fi
            fi
        done < <("$ADB" logcat -d -v raw 2>/dev/null \
            | tr -d '\r' \
            | grep "ANDROID_MOBILE_VIEWPORT " \
            | tail -30 || true)

        if [[ -n "$matching_line" ]]; then
            cache_android_mobile_viewport_line "$matching_line"
            printf '%s\n' "$matching_line"
            return 0
        fi
        if [[ "$saw_current_surface" -eq 0 || "$SECONDS" -ge "$deadline" ]]; then
            break
        fi
        sleep 1
    done

    # A clear logcat is common between focused assertions. Reuse only a cache
    # that still matches the current adb surface and a fully settled target.
    [[ "$saw_current_surface" -eq 0 ]] || return 1

    cache_file="$(android_mobile_viewport_cache_file)"
    if [[ -s "$cache_file" ]]; then
        line="$(tail -1 "$cache_file")"
        if android_mobile_viewport_log_is_settled_for_surface \
            "$line" "$surface_width" "$surface_height"; then
            printf '%s\n' "$line"
            return 0
        fi
    fi
    return 1
}

client_xy_to_screen_xy() {
    local client_x="$1"
    local client_y="$2"
    local width height target_width target_full_height orientation capped x y viewport_line
    local content_width content_height inset_left inset_top logical_width logical_height scale
    read -r width height < <(screen_size)
    if [[ -z "${width:-}" || -z "${height:-}" ]]; then
        echo "ERROR: could not determine Android screen size for client input" >&2
        return 1
    fi

    viewport_line="$(latest_settled_android_mobile_viewport "$width" "$height" || true)"
    if [[ -n "$viewport_line" ]]; then
        content_width="$(extract_log_value "$viewport_line" contentW)"
        content_height="$(extract_log_value "$viewport_line" contentH)"
        inset_left="$(extract_log_value "$viewport_line" insetL)"
        inset_top="$(extract_log_value "$viewport_line" insetT)"
        logical_width="$(extract_log_value "$viewport_line" logicalW)"
        logical_height="$(extract_log_value "$viewport_line" logicalH)"
        scale="$(extract_log_value "$viewport_line" scale)"
        read -r x y < <(awk -v cw="$content_width" -v ch="$content_height" \
            -v il="$inset_left" -v it="$inset_top" \
            -v lw="$logical_width" -v lh="$logical_height" \
            -v scale="$scale" -v cx="$client_x" -v cy="$client_y" 'BEGIN {
            ox = il + (cw - lw * scale) / 2;
            oy = it + (ch - lh * scale) / 2;
            printf "%d %d\n", ox + cx * scale + 0.5, oy + cy * scale + 0.5;
        }')
        echo "$x $y"
        return 0
    fi

    # Pre-telemetry fallback for wrapper/loading states and older APKs.
    read -r target_width target_full_height orientation capped < <(android_viewport_target_for_size "$width" "$height")
    read -r x y < <(awk -v sw="$width" -v sh="$height" -v gw="$target_width" -v gh="$target_full_height" -v cx="$client_x" -v cy="$client_y" 'BEGIN {
        scaleX=sw/gw;
        scaleY=sh/gh;
        scale=scaleX < scaleY ? scaleX : scaleY;
        ox=(sw - gw*scale)/2;
        oy=(sh - gh*scale)/2;
        printf "%d %d\n", ox + cx*scale + 0.5, oy + cy*scale + 0.5;
    }')
    echo "$x $y"
}

client_xy_to_screen_xy_from_viewport() {
    local client_x="$1"
    local client_y="$2"
    local viewport_line="$3"
    local surface_width surface_height content_width content_height
    local inset_left inset_top logical_width logical_height scale x y

    surface_width="$(extract_log_value "$viewport_line" surfaceW)"
    surface_height="$(extract_log_value "$viewport_line" surfaceH)"
    content_width="$(extract_log_value "$viewport_line" contentW)"
    content_height="$(extract_log_value "$viewport_line" contentH)"
    inset_left="$(extract_log_value "$viewport_line" insetL)"
    inset_top="$(extract_log_value "$viewport_line" insetT)"
    logical_width="$(extract_log_value "$viewport_line" logicalW)"
    logical_height="$(extract_log_value "$viewport_line" logicalH)"
    scale="$(extract_log_value "$viewport_line" scale)"

    if [[ ! "$client_x" =~ ^[0-9]+$ || ! "$client_y" =~ ^[0-9]+$ \
        || ! "$surface_width" =~ ^[0-9]+$ || ! "$surface_height" =~ ^[0-9]+$ \
        || ! "$logical_width" =~ ^[0-9]+$ || ! "$logical_height" =~ ^[0-9]+$ \
        || "$client_x" -ge "$logical_width" || "$client_y" -ge "$logical_height" ]] \
        || ! is_positive_log_number "$scale" \
        || ! android_mobile_viewport_log_is_settled_for_surface \
            "$viewport_line" "$surface_width" "$surface_height"; then
        echo "ERROR: cannot map client input from unsettled Android viewport telemetry" >&2
        echo "$viewport_line" >&2
        return 1
    fi

    read -r x y < <(awk -v cw="$content_width" -v ch="$content_height" \
        -v il="$inset_left" -v it="$inset_top" \
        -v lw="$logical_width" -v lh="$logical_height" \
        -v scale="$scale" -v cx="$client_x" -v cy="$client_y" 'BEGIN {
        ox = il + (cw - lw * scale) / 2;
        oy = it + (ch - lh * scale) / 2;
        printf "%d %d\n", ox + cx * scale + 0.5, oy + cy * scale + 0.5;
    }')
    if [[ ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ \
        || "$x" -ge "$surface_width" || "$y" -ge "$surface_height" ]]; then
        echo "ERROR: Android telemetry mapped client $client_x,$client_y outside surface ${surface_width}x${surface_height}: ${x:-?},${y:-?}" >&2
        return 1
    fi
    echo "$x $y"
}

tap_client_xy_from_viewport() {
    local client_x="$1"
    local client_y="$2"
    local viewport_line="$3"
    local x y
    read -r x y < <(client_xy_to_screen_xy_from_viewport \
        "$client_x" "$client_y" "$viewport_line") || return 1
    "$ADB" shell input tap "$x" "$y"
}

tap_client_xy() {
    local client_x="$1"
    local client_y="$2"
    local x y
    read -r x y < <(client_xy_to_screen_xy "$client_x" "$client_y")
    "$ADB" shell input tap "$x" "$y"
}

long_press_client_xy() {
    local client_x="$1"
    local client_y="$2"
    local duration_ms="${3:-1200}"
    local x y
    read -r x y < <(client_xy_to_screen_xy "$client_x" "$client_y")
    "$ADB" shell input swipe "$x" "$y" "$x" "$y" "$duration_ms"
}

swipe_client_xy() {
    local start_client_x="$1"
    local start_client_y="$2"
    local end_client_x="$3"
    local end_client_y="$4"
    local duration_ms="${5:-700}"
    local start_x start_y end_x end_y
    read -r start_x start_y < <(client_xy_to_screen_xy "$start_client_x" "$start_client_y")
    read -r end_x end_y < <(client_xy_to_screen_xy "$end_client_x" "$end_client_y")
    "$ADB" shell input swipe "$start_x" "$start_y" "$end_x" "$end_y" "$duration_ms"
}

screenshot() {
    local name="$1"
	# SurfaceView composition can expose the transition frame immediately after
	# a telemetry-triggered action/rotation. Let one rendered frame settle so QA
	# artifacts certify the final UI instead of a partially composed buffer.
	sleep 0.35
    if adb_screencap_to_file "$OUT_DIR/$name.png"; then
        echo "Saved $OUT_DIR/$name.png"
    else
        echo "WARNING: timed out capturing screenshot $OUT_DIR/$name.png" >&2
    fi
}

wait_for_text() {
    local text="$1"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))

    while (( SECONDS < deadline )); do
        if "$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 \
            && "$ADB" shell cat /sdcard/window.xml 2>/dev/null | tr -d '\r' | grep -Fq "text=\"$text\""; then
            return 0
        fi
        sleep 1
    done

    return 1
}

wait_for_log_pattern() {
	local pattern="$1"
	local timeout="${2:-30}"
	local deadline=$((SECONDS + timeout))
	while (( SECONDS < deadline )); do
		if "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -E "$pattern" >/dev/null; then
			return 0
		fi
		sleep 1
	done
	echo "ERROR: timed out waiting for Android log pattern: $pattern" >&2
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | tail -120 >&2 || true
	return 1
}

wait_for_wrapper_ready() {
	if wait_for_text "Play" 45; then
		return 0
	fi
	echo "ERROR: Android wrapper did not reach Ready to play / Play" >&2
	"$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
	"$ADB" shell cat /sdcard/window.xml 2>/dev/null | tr -d '\r' >&2 || true
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | tail -160 >&2 || true
	return 1
}

tap_text() {
    local text="$1"
    local occurrence="${2:-first}"
    local bounds
    local x1 y1 x2 y2 x y

    "$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || return 1
    bounds="$("$ADB" shell cat /sdcard/window.xml 2>/dev/null | tr -d '\r' | TEXT="$text" perl -ne '
        while (/text="\Q$ENV{TEXT}\E"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g) {
            print "$1 $2 $3 $4\n";
        }
    ' | { if [[ "$occurrence" == "last" ]]; then tail -1; else head -1; fi; })"

    if [[ ! "$bounds" =~ ^([0-9]+)\ ([0-9]+)\ ([0-9]+)\ ([0-9]+)$ ]]; then
        return 1
    fi

    x1="${BASH_REMATCH[1]}"
    y1="${BASH_REMATCH[2]}"
    x2="${BASH_REMATCH[3]}"
    y2="${BASH_REMATCH[4]}"
    x=$(((x1 + x2) / 2))
    y=$(((y1 + y2) / 2))
    echo "Tapping text '$text' at $x,$y bounds=[$x1,$y1][$x2,$y2]" >&2
    "$ADB" shell input tap "$x" "$y"
}

tap_resource_id() {
    local resource_id="$1"
    local occurrence="${2:-first}"
    local bounds
    local x1 y1 x2 y2 x y

    "$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || return 1
    bounds="$("$ADB" shell cat /sdcard/window.xml 2>/dev/null | tr -d '\r' | RESOURCE_ID="$resource_id" perl -ne '
        while (/resource-id="\Q$ENV{RESOURCE_ID}\E"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g) {
            print "$1 $2 $3 $4\n";
        }
    ' | { if [[ "$occurrence" == "last" ]]; then tail -1; else head -1; fi; })"

    if [[ ! "$bounds" =~ ^([0-9]+)\ ([0-9]+)\ ([0-9]+)\ ([0-9]+)$ ]]; then
        return 1
    fi

    x1="${BASH_REMATCH[1]}"
    y1="${BASH_REMATCH[2]}"
    x2="${BASH_REMATCH[3]}"
    y2="${BASH_REMATCH[4]}"
    x=$(((x1 + x2) / 2))
    y=$(((y1 + y2) / 2))
    echo "Tapping resource '$resource_id' at $x,$y bounds=[$x1,$y1][$x2,$y2]" >&2
    "$ADB" shell input tap "$x" "$y"
}

tap_play_button() {
    tap_resource_id "$APP_ID:id/launch_client" last || tap_text "Play" last || tap_pct 50 71
}

input_text() {
    local text="$1"
    text="${text// /%s}"
    "$ADB" shell input text "$text"
}

input_text_slow() {
    local text="$1"
    local i ch
    for ((i = 0; i < ${#text}; i++)); do
        ch="${text:i:1}"
        input_text "$ch"
        sleep "$INPUT_CHAR_DELAY"
    done
}

clear_focused_text_field() {
    local i
    for ((i = 0; i < 24; i++)); do
        "$ADB" shell input keyevent DEL
    done
}

login_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_LOGIN_STATE" | tail -30 >&2 || true
}

wait_for_login_state() {
    local expected_screen="$1"
    local timeout="${2:-10}"
    local deadline=$((SECONDS + timeout))
    local line screen

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_LOGIN_STATE " | tail -20 | grep "screen=$expected_screen " | tail -1 || true)"
        screen="$(extract_log_value "$line" screen)"
        if [[ "$screen" == "$expected_screen" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android login screen $expected_screen" >&2
    login_log_tail
    return 1
}

tap_login_state_target() {
    local expected_screen="$1"
    local target_prefix="$2"
    local timeout="${3:-8}"
    local line client_x client_y

    line="$(wait_for_login_state "$expected_screen" "$timeout" || true)"
    client_x="$(extract_log_value "$line" "${target_prefix}X")"
    client_y="$(extract_log_value "$line" "${target_prefix}Y")"
    if [[ "$client_x" =~ ^[0-9]+$ && "$client_y" =~ ^[0-9]+$ ]]; then
        echo "Android login target ${target_prefix} at client $client_x,$client_y"
        tap_client_xy "$client_x" "$client_y"
        return 0
    fi

    return 1
}

wait_for_login_lengths() {
    local expected_user_length="$1"
    local expected_pass_length="$2"
    local timeout="${3:-10}"
	local quiet="${4:-false}"
    local deadline=$((SECONDS + timeout))
    local line user_length pass_length

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_LOGIN_STATE " | tail -20 | grep "screen=2 " | tail -1 || true)"
        user_length="$(extract_log_value "$line" userLength)"
        pass_length="$(extract_log_value "$line" passLength)"
        if [[ "$user_length" == "$expected_user_length" && "$pass_length" == "$expected_pass_length" ]]; then
            echo "Verified Android login text lengths: user=$user_length pass=$pass_length"
            return 0
        fi
        sleep 1
    done

	if [[ "$quiet" != "true" ]]; then
		echo "ERROR: timed out waiting for Android login text lengths user=$expected_user_length pass=$expected_pass_length" >&2
		login_log_tail
	fi
    return 1
}

wait_for_login_remember_state() {
	local expected="$1"
	local timeout="${2:-12}"
	local deadline=$((SECONDS + timeout))
	local line actual
	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep 'ANDROID_SMOKE_LOGIN_STATE ' \
			| tail -20 \
			| grep 'screen=2 ' \
			| tail -1 || true)"
		actual="$(extract_log_value "$line" rememberRequested)"
		if [[ "$actual" == "$expected" ]]; then
			echo "Verified Android remember-login state: $actual"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: expected Android remember-login state $expected" >&2
	login_log_tail
	return 1
}

wait_for_login_response_code() {
	local expected="$1"
	local timeout="${2:-30}"
	local deadline=$((SECONDS + timeout))
	local line code
	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep 'login response:' | tail -1 || true)"
		if [[ "$line" =~ login\ response:([0-9]+) ]]; then
			code="${BASH_REMATCH[1]}"
			if [[ "$code" == "$expected" ]]; then
				echo "Verified Android login response: $code"
				return 0
			fi
			echo "ERROR: expected Android login response $expected, got $code" >&2
			return 1
		fi
		sleep 1
	done
	echo "ERROR: timed out waiting for Android login response $expected" >&2
	return 1
}

tap_existing_user_button() {
    enable_android_smoke_login
    tap_login_state_target 0 homeExisting 8 || tap_pct "$AUTH_EXISTING_USER_X_PCT" "$AUTH_EXISTING_USER_Y_PCT"
}

tap_create_account_button() {
    enable_android_smoke_login
    tap_login_state_target 0 homeNew 8 || tap_pct 50 52
}

tap_login_ok_button() {
    tap_login_state_target 2 ok 8 || tap_pct "$AUTH_OK_X_PCT" "$AUTH_OK_Y_PCT"
}

close_auth_intro_dialog_if_present() {
	enable_android_smoke_settings
	enable_android_smoke_appearance_prompt
	close_welcome_dialog_if_present 4
	accept_appearance_prompt_if_present 10
	close_welcome_dialog_if_present 10
	# Tutorial landing sends a separate server-message modal after the ordinary
	# welcome/appearance overlays. Android Back follows the shared modal priority
	# table and closes it without relying on a device-specific tap coordinate.
	"$ADB" shell input keyevent BACK
	sleep 2
	disable_android_smoke_settings
	disable_android_smoke_appearance_prompt
}

enter_auth_credentials() {
    local line user_x user_y pass_x pass_y

    enable_android_smoke_login
    line="$(wait_for_login_state 2 12 || true)"
    user_x="$(extract_log_value "$line" userX)"
    user_y="$(extract_log_value "$line" userY)"
    pass_x="$(extract_log_value "$line" passX)"
    pass_y="$(extract_log_value "$line" passY)"

    if [[ "$user_x" =~ ^[0-9]+$ && "$user_y" =~ ^[0-9]+$ ]]; then
        echo "Android login username at client $user_x,$user_y"
        tap_client_xy "$user_x" "$user_y"
    else
        tap_pct "$AUTH_USERNAME_X_PCT" "$AUTH_USERNAME_Y_PCT"
    fi
    sleep 1
    clear_focused_text_field
    input_text_slow "$AUTH_USER"
    sleep 1
	if ! wait_for_login_lengths "${#AUTH_USER}" 0 5 true; then
		echo "Android login username input was incomplete; retrying once."
		if [[ "$user_x" =~ ^[0-9]+$ && "$user_y" =~ ^[0-9]+$ ]]; then
			tap_client_xy "$user_x" "$user_y"
		else
			tap_pct "$AUTH_USERNAME_X_PCT" "$AUTH_USERNAME_Y_PCT"
		fi
		sleep 1
		clear_focused_text_field
		input_text_slow "$AUTH_USER"
		sleep 1
		wait_for_login_lengths "${#AUTH_USER}" 0 12 || exit 1
	fi
	# Opening the IME can move the responsive portrait card after the username
	# tap. Refresh password geometry only after that layout has settled.
	line="$(wait_for_login_state 2 12 || true)"
	pass_x="$(extract_log_value "$line" passX)"
	pass_y="$(extract_log_value "$line" passY)"
    if [[ "$pass_x" =~ ^[0-9]+$ && "$pass_y" =~ ^[0-9]+$ ]]; then
        echo "Android login password at client $pass_x,$pass_y"
        tap_client_xy "$pass_x" "$pass_y"
    else
        tap_pct "$AUTH_PASSWORD_X_PCT" "$AUTH_PASSWORD_Y_PCT"
    fi
    sleep 1
    clear_focused_text_field
    input_text_slow "$AUTH_PASS"
    # adb text input does not require a visible IME; BACK can close GameActivity.
    sleep 1
	if ! wait_for_login_lengths "${#AUTH_USER}" "${#AUTH_PASS}" 5 true; then
		echo "Android login password input was incomplete; retrying once."
		line="$(wait_for_login_state 2 12 || true)"
		pass_x="$(extract_log_value "$line" passX)"
		pass_y="$(extract_log_value "$line" passY)"
		if [[ "$pass_x" =~ ^[0-9]+$ && "$pass_y" =~ ^[0-9]+$ ]]; then
			tap_client_xy "$pass_x" "$pass_y"
		else
			tap_pct "$AUTH_PASSWORD_X_PCT" "$AUTH_PASSWORD_Y_PCT"
		fi
		sleep 1
		clear_focused_text_field
		input_text_slow "$AUTH_PASS"
		sleep 1
		wait_for_login_lengths "${#AUTH_USER}" "${#AUTH_PASS}" 12 || exit 1
	fi
}

submit_login_and_wait() {
    "$ADB" logcat -c || true
    "$ADB" shell input keyevent ENTER
    if wait_for_successful_login 20; then
        return 0
    fi
    if "$ADB" logcat -d 2>/dev/null | tr -d '\r' | grep -q 'login response:'; then
        return 1
    fi
    echo "Android hardware Enter did not submit; tapping visible Ok button." >&2
    tap_login_ok_button
    wait_for_successful_login 45 || {
        echo "Android login response log not observed; checking auth DB online state." >&2
        wait_auth_online 30
    }
}

assert_resumed_activity() {
	local expected="$1"
	local activities
	activities="$("$ADB" shell dumpsys activity activities | tr -d '\r')"
	local escaped_app_id="${APP_ID//./\\.}"
	if grep -Eq "(^|[[:space:]])(mResumedActivity|topResumedActivity)[:=].*${escaped_app_id}/.*${expected}" <<< "$activities"; then
		return 0
	fi

	echo "ERROR: expected resumed activity containing $expected" >&2
	grep -E "(mResumedActivity|topResumedActivity|mLastResumedActivity)" <<< "$activities" >&2 || true
	return 1
}

wait_for_external_activity() {
	local label="$1"
	local timeout="${2:-15}"
	local expected_url="${3:-}"
	local deadline=$((SECONDS + timeout))
	local activities resumed escaped_app_id
	escaped_app_id="${APP_ID//./\\.}"
	while (( SECONDS < deadline )); do
		activities="$("$ADB" shell dumpsys activity activities | tr -d '\r')"
		resumed="$(grep -E '(mResumedActivity|topResumedActivity)[:=]' <<< "$activities" | head -1 || true)"
		if [[ -n "$resumed" ]] && ! grep -Eq "${escaped_app_id}/" <<< "$resumed"; then
			if [[ -n "$expected_url" ]] && ! grep -Fq "dat=$expected_url" <<< "$activities"; then
				echo "ERROR: $label opened an external activity without the expected URL: $expected_url" >&2
				grep -E 'Intent \{|mIntent=|intent=' <<< "$activities" | tail -20 >&2 || true
				return 1
			fi
			echo "Verified external Android handoff for $label: $resumed"
			if [[ -n "$expected_url" ]]; then
				echo "Verified external Android URL for $label: $expected_url"
			fi
			return 0
		fi
		sleep 1
	done
	echo "ERROR: $label did not hand off to an external Android activity" >&2
	grep -E '(mResumedActivity|topResumedActivity|mLastResumedActivity)' <<< "${activities:-}" >&2 || true
	return 1
}

assert_game_activity_for_input() {
	local step="$1"
	local screenshot_name="${2:-diagnostic-lost-game-activity}"

	if is_resumed_activity "GameActivity"; then
		return 0
	fi

	echo "ERROR: GameActivity is not foreground before $step" >&2
	assert_resumed_activity "GameActivity" || true
	screenshot "$screenshot_name" || true
	"$ADB" logcat -d -v raw 2>/dev/null \
		| tr -d '\r' \
		| grep -E "AndroidRuntime|ActivityTaskManager|ApplicationUpdater|CacheUpdater|GameActivity|ORSC|login response:|ANDROID_SMOKE_GROUND_LOOT|ANDROID_SMOKE_PLAYER_" \
		| tail -120 >&2 || true
	return 1
}

is_resumed_activity() {
	local expected="$1"
	local activities
	activities="$("$ADB" shell dumpsys activity activities | tr -d '\r')"
	local escaped_app_id="${APP_ID//./\\.}"
	grep -Eq "(^|[[:space:]])(mResumedActivity|topResumedActivity)[:=].*${escaped_app_id}/.*${expected}" <<< "$activities"
}

is_focused_activity() {
	local expected="$1"
	local activities
	activities="$("$ADB" shell dumpsys activity activities | tr -d '\r')"
	local escaped_app_id="${APP_ID//./\\.}"
	grep -Eq "(^|[[:space:]])mCurrentFocus=.*${escaped_app_id}/.*${expected}" <<< "$activities"
}

wait_for_activity_input_ready() {
	local expected="$1"
	local timeout="${2:-20}"
	local deadline=$((SECONDS + timeout))

	while (( SECONDS < deadline )); do
		if is_resumed_activity "$expected" && is_focused_activity "$expected"; then
			return 0
		fi
		sleep 1
	done

	echo "ERROR: $expected did not become resumed and input-focused" >&2
	assert_resumed_activity "$expected" || true
	return 1
}

wait_for_resumed_activity() {
    local expected="$1"
    local timeout="${2:-45}"
    local deadline=$((SECONDS + timeout))

    while (( SECONDS < deadline )); do
        if is_resumed_activity "$expected"; then
            return 0
        fi
        sleep 2
    done

    return 1
}

ensure_game_activity_from_wrapper() {
	local timeout="${1:-120}"
	local deadline=$((SECONDS + timeout))

	while (( SECONDS < deadline )); do
		if is_resumed_activity "GameActivity"; then
			return 0
		fi

		if is_resumed_activity "ApplicationUpdater|CacheUpdater"; then
			wait_for_text "Play" 8 || true
			tap_play_button
		fi
		sleep 3
	done

	assert_resumed_activity "GameActivity"
}

write_server_endpoint() {
	local host="$1"
	local port="$2"
	PENDING_SERVER_HOST="$host"
	PENDING_SERVER_PORT="$port"
	"$ADB" shell "run-as $APP_ID sh -c 'mkdir -p $APP_FILES && printf %s \"$host\" > $APP_FILES/ip.txt && printf %s \"$port\" > $APP_FILES/port.txt'" 2>/dev/null || true
}

launch_game_with_endpoint() {
    local host="$1"
    local port="$2"
    "$ADB" shell am force-stop $APP_ID || true
    write_server_endpoint "$host" "$port"
	enable_android_smoke_network
    launch_wrapper
    wait_for_wrapper_ready
    tap_play_button
    ensure_game_activity_from_wrapper 120 || return 1
    wait_for_selected_server "$host" "$port" 30 || return 1
    sleep 5
    dismiss_fullscreen_education
    return 0
}

launch_authenticated_endpoint() {
	enable_android_smoke_network
	if [[ "$AUTH_USE_BUNDLED_ENDPOINT" == "1" ]]; then
		launch_to_login_home
	else
		launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
	fi
}

launch_authenticated_endpoint_preserving_credentials() {
	enable_android_smoke_network
	if [[ "$AUTH_USE_BUNDLED_ENDPOINT" == "1" ]]; then
		launch_to_login_home 0
		return
	fi
	PENDING_SERVER_HOST="$AUTH_HOST"
	PENDING_SERVER_PORT="$AUTH_PORT"
	launch_wrapper 0
	wait_for_wrapper_ready
	tap_play_button
	ensure_game_activity_from_wrapper 120 || return 1
	wait_for_selected_server "$AUTH_HOST" "$AUTH_PORT" 30 || return 1
	sleep 5
	dismiss_fullscreen_education
}

dismiss_fullscreen_education() {
    if wait_for_text "Got it" 2; then
        tap_text "Got it" last || tap_pct 80 50
        sleep 1
    fi
}

wait_for_successful_login() {
    local timeout="${1:-45}"
    local deadline=$((SECONDS + timeout))
    local last code

    while (( SECONDS < deadline )); do
        last="$("$ADB" logcat -d 2>/dev/null | tr -d '\r' | grep 'login response:' | tail -1 || true)"
        if [[ "$last" =~ login\ response:([0-9]+) ]]; then
            code="${BASH_REMATCH[1]}"
            if (( (code & 64) != 0 )); then
                return 0
            fi
            echo "ERROR: login failed with response code $code" >&2
            return 1
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for successful login response" >&2
    return 1
}

ensure_auth_login_defaults() {
	if [[ -z "$AUTH_USER" ]]; then
		AUTH_USER="android"
	fi
	if [[ -z "$AUTH_PASS" ]]; then
		AUTH_PASS="android"
	fi
	if [[ -z "$AUTH_DB" && -f "$REPO_ROOT/server/inc/sqlite/voidscape.db" ]]; then
		AUTH_DB="$REPO_ROOT/server/inc/sqlite/voidscape.db"
	fi
}

auth_password_hash() {
	local salt="$1"
	local password="$2"
	if ! command -v node >/dev/null 2>&1; then
		return 1
	fi

	AUTH_SMOKE_SALT="$salt" AUTH_SMOKE_PASS="$password" node <<'NODE'
const crypto = require('crypto');
const salt = process.env.AUTH_SMOKE_SALT || '';
const password = process.env.AUTH_SMOKE_PASS || '';
const md5 = crypto.createHash('md5').update(password, 'utf8').digest('hex');
process.stdout.write(crypto.createHash('sha512').update(salt + md5, 'utf8').digest('hex'));
NODE
}

auth_password_matches() {
	local stored_hash="$1"
	local salt="$2"
	local password="$3"
	local expected_hash

	if [[ "$stored_hash" == "$password" ]]; then
		return 0
	fi

	if [[ "$stored_hash" == '$2'* ]] && command -v python3 >/dev/null 2>&1; then
		AUTH_SMOKE_HASH="$stored_hash" AUTH_SMOKE_SALT="$salt" AUTH_SMOKE_PASS="$password" python3 <<'PY'
import hashlib
import os
import sys

try:
    import bcrypt
except Exception:
    sys.exit(2)

stored = os.environ.get("AUTH_SMOKE_HASH", "")
salt = os.environ.get("AUTH_SMOKE_SALT", "")
password = os.environ.get("AUTH_SMOKE_PASS", "")
if not stored or not password:
    sys.exit(1)

if salt:
    md5 = hashlib.md5(password.encode("utf-8")).hexdigest()
    candidate = hashlib.sha512((salt + md5).encode("utf-8")).hexdigest()
else:
    candidate = password

try:
    ok = bcrypt.checkpw(candidate.encode("utf-8"), stored.encode("utf-8"))
except ValueError:
    ok = False

sys.exit(0 if ok else 1)
PY
		case "$?" in
			0) return 0 ;;
			1) return 1 ;;
		esac
	fi

	expected_hash="$(auth_password_hash "$salt" "$password" || true)"
	[[ -n "$expected_hash" && "$expected_hash" == "$stored_hash" ]]
}

preflight_auth_login_fixture() {
	ensure_auth_login_defaults

	if [[ -z "$AUTH_DB" ]]; then
		echo "ERROR: --only-auth-login could not find a default DB; set ANDROID_SMOKE_AUTH_DB" >&2
		exit 1
	fi
	if [[ ! -f "$AUTH_DB" ]]; then
		echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
		exit 1
	fi
	if ! command -v sqlite3 >/dev/null 2>&1; then
		echo "ERROR: sqlite3 is required for --only-auth-login" >&2
		exit 1
	fi

	local safe_user row player_id saved_user saved_pass saved_salt online
	safe_user="$(sql_escape "$AUTH_USER")"
	row="$(sqlite3 -cmd '.timeout 5000' -noheader -separator '|' "$AUTH_DB" \
		"select id, username, pass, coalesce(salt, ''), online from players where lower(username) = lower('$safe_user') limit 1;")"
	if [[ -z "$row" ]]; then
		echo "ERROR: no player row found for $AUTH_USER in $AUTH_DB" >&2
		exit 1
	fi

	IFS='|' read -r player_id saved_user saved_pass saved_salt online <<< "$row"
	if ! auth_password_matches "$saved_pass" "$saved_salt" "$AUTH_PASS"; then
		echo "ERROR: password preflight failed for $saved_user in $AUTH_DB" >&2
		echo "       This usually means the smoke script is pointed at the wrong DB or the wrong credentials." >&2
		exit 1
	fi

	if [[ "$online" != "0" ]]; then
		if [[ "$(printf '%s' "$AUTH_USER" | tr '[:upper:]' '[:lower:]')" == "android" ]]; then
			echo "Auth fixture $saved_user was marked online; clearing stale online flag for smoke login."
			sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
				"update players set online = 0 where id = $player_id;"
		else
			echo "ERROR: $saved_user is currently marked online in $AUTH_DB" >&2
			exit 1
		fi
	fi

	if command -v lsof >/dev/null 2>&1 && [[ "$AUTH_HOST" == "10.0.2.2" || "$AUTH_HOST" == "127.0.0.1" || "$AUTH_HOST" == "localhost" ]]; then
		if ! lsof -nP -iTCP:"$AUTH_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
			echo "ERROR: no local server appears to be listening on port $AUTH_PORT" >&2
			echo "       Start it with scripts/run-server.sh before running the Android login smoke." >&2
			exit 1
		fi
	fi

	echo "Auth preflight OK: user=$saved_user db=$AUTH_DB endpoint=$AUTH_HOST:$AUTH_PORT"
}

wait_for_selected_server() {
	local host="$1"
	local port="$2"
	local timeout="${3:-30}"
	local deadline=$((SECONDS + timeout))
	local line

	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep -E "Selected server ${host}:${port}|Fetching server configs from ${host}:${port}" \
			| tail -1 || true)"
		if [[ -n "$line" ]]; then
			echo "Verified Android endpoint selection: $line"
			return 0
		fi
		sleep 1
	done

	echo "ERROR: expected Android to select $host:$port before login" >&2
	"$ADB" logcat -d -v raw 2>/dev/null \
		| tr -d '\r' \
		| grep -E "Selected server|Fetching server configs|GameActivity|CacheUpdater|ApplicationUpdater|Could not connect|Can't reach" \
		| tail -80 >&2 || true
	return 1
}

assert_no_android_runtime_crash() {
	local label="${1:-after login}"
	local crash
	crash="$("$ADB" logcat -d -v raw 2>/dev/null \
		| tr -d '\r' \
		| awk '
			/FATAL EXCEPTION/ {
				capture = 45;
				block = $0 "\n";
				next;
			}
			capture > 0 {
				block = block $0 "\n";
				capture--;
				if (capture == 0) {
					if (block ~ /Process: com\.voidscape\.client|com\.voidscape\.client|com\.openrsc|orsc\.|RSCBitmapSurfaceView|GameActivity|CacheUpdater/) {
						printf "%s", block;
					}
					block = "";
				}
			}
			END {
				if (capture > 0 && block ~ /Process: com\.voidscape\.client|com\.voidscape\.client|com\.openrsc|orsc\.|RSCBitmapSurfaceView|GameActivity|CacheUpdater/) {
					printf "%s", block;
				}
			}
		' \
		| tail -120 || true)"
	if [[ -n "$crash" ]]; then
		echo "ERROR: Android runtime crash observed $label" >&2
		echo "$crash" >&2
		return 1
	fi
	return 0
}

assert_no_plaintext_saved_logins() {
	local path
	for path in files/credentials.txt files/accounts.txt; do
		if "$ADB" shell run-as "$APP_ID" test -e "$path" 2>/dev/null; then
			echo "ERROR: plaintext saved-login file still exists: $path" >&2
			return 1
		fi
	done
}

assert_no_credential_envelope() {
	if "$ADB" shell run-as "$APP_ID" test -e no_backup/voidscape-credentials.v1.bin 2>/dev/null; then
		echo "ERROR: credentials were persisted before successful authentication" >&2
		return 1
	fi
	assert_no_plaintext_saved_logins
}

assert_encrypted_credential_envelope() {
	local deadline=$((SECONDS + 15))
	local envelope="$OUT_DIR/credential-envelope.bin"
	local size hash
	while (( SECONDS < deadline )); do
		if "$ADB" shell run-as "$APP_ID" test -s no_backup/voidscape-credentials.v1.bin 2>/dev/null; then
			break
		fi
		sleep 1
	done
	if ! "$ADB" shell run-as "$APP_ID" test -s no_backup/voidscape-credentials.v1.bin 2>/dev/null; then
		echo "ERROR: encrypted saved-login envelope was not created" >&2
		return 1
	fi
	if "$ADB" shell run-as "$APP_ID" test -e no_backup/voidscape-credentials.v1.cleared 2>/dev/null; then
		echo "ERROR: credential tombstone remained beside a saved-login envelope" >&2
		return 1
	fi
	assert_no_plaintext_saved_logins
	"$ADB" exec-out run-as "$APP_ID" cat no_backup/voidscape-credentials.v1.bin > "$envelope"
	AUTH_SMOKE_USER="$AUTH_USER" AUTH_SMOKE_PASS="$AUTH_PASS" python3 - "$envelope" <<'PY'
import os
import sys
from pathlib import Path

payload = Path(sys.argv[1]).read_bytes()
for label, value in (("username", os.environ["AUTH_SMOKE_USER"]), ("password", os.environ["AUTH_SMOKE_PASS"])):
    if value.encode("utf-8") in payload:
        raise SystemExit(f"ERROR: encrypted credential envelope exposes the {label} in plaintext")
PY
	size="$(wc -c < "$envelope" | tr -d ' ')"
	hash="$(shasum -a 256 "$envelope" | awk '{print $1}')"
	echo "Verified encrypted credential envelope: size=$size sha256=$hash"
}

credential_envelope_device_hash() {
	"$ADB" exec-out run-as "$APP_ID" cat no_backup/voidscape-credentials.v1.bin \
		| shasum -a 256 \
		| awk '{print $1}'
}

assert_credential_envelope_survives_reconnect() {
	local before_hash after_hash settings_launch_output
	before_hash="$(credential_envelope_device_hash)"
	"$ADB" logcat -c || true
	settings_launch_output="$("$ADB" shell am start -W -a android.settings.SETTINGS 2>&1)"
	if ! grep -q "Status: ok" <<< "$settings_launch_output"; then
		echo "ERROR: credential smoke could not switch to Android Settings" >&2
		return 1
	fi
	sleep "$AUTH_LIFECYCLE_BACKGROUND_SECONDS"
	"$ADB" shell am start -n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater" >/dev/null
	wait_for_resumed_activity "GameActivity" 30 || return 1
	wait_for_log_pattern 'VOIDSCAPE_NETWORK_WRITER event=start active=' 30 || return 1
	wait_for_successful_login 30 || return 1
	wait_auth_online 20 || return 1
	after_hash="$(credential_envelope_device_hash)"
	if [[ -z "$before_hash" || "$after_hash" != "$before_hash" ]]; then
		echo "ERROR: saved-login envelope changed or disappeared during reconnect" >&2
		echo "  before: $before_hash" >&2
		echo "  after:  $after_hash" >&2
		return 1
	fi
	echo "Verified encrypted saved login survived a real app-switch reconnect: $after_hash"
}

assert_credential_store_cleared() {
	local deadline=$((SECONDS + 15))
	while (( SECONDS < deadline )); do
		if ! "$ADB" shell run-as "$APP_ID" test -e no_backup/voidscape-credentials.v1.bin 2>/dev/null \
			&& "$ADB" shell run-as "$APP_ID" test -s no_backup/voidscape-credentials.v1.cleared 2>/dev/null; then
			assert_no_plaintext_saved_logins
			echo "Verified saved-login forget tombstone"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: saved-login store was not cleared authoritatively" >&2
	return 1
}

run_authenticated_login_smoke() {
	preflight_auth_login_fixture

	"$ADB" shell am force-stop $APP_ID || true
	"$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
	"$ADB" logcat -c || true

	launch_authenticated_endpoint
	wait_for_selected_server "$AUTH_HOST" "$AUTH_PORT" 30 || exit 1
	sleep 2
	screenshot 00-auth-login-home

	tap_existing_user_button
	sleep 3
	screenshot 01-auth-existing-user-keyboard
	enter_auth_credentials
	screenshot 02-auth-credentials-entered

	submit_login_and_wait || exit 1
	if [[ -n "$AUTH_DB" ]]; then
		wait_auth_online 20 || exit 1
	fi
	sleep 5
	assert_no_android_runtime_crash "after successful login" || {
		screenshot 03-auth-login-crash || true
		exit 1
	}
	assert_game_activity_for_input "auth login screenshot" "03-auth-login-lost-game-activity" || exit 1
	screenshot 03-auth-post-login
	if [[ "${ANDROID_SMOKE_LEAVE_LOGGED_IN:-0}" == "1" ]]; then
		echo "Android auth/login left running in-game for $AUTH_USER on $AUTH_HOST:$AUTH_PORT"
		return 0
	fi

	"$ADB" shell am force-stop $APP_ID || true
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
	echo "Android auth/login smoke passed for $AUTH_USER on $AUTH_HOST:$AUTH_PORT"
}

run_authenticated_credential_smoke() {
	local wrong_pass="${AUTH_PASS}x"
	local remember_line remember_state

	preflight_auth_login_fixture
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	"$ADB" shell pm clear "$APP_ID" >/dev/null
	"$ADB" logcat -c || true

	launch_authenticated_endpoint_preserving_credentials
	tap_existing_user_button
	sleep 2
	enter_auth_credentials
	tap_login_state_target 2 remember 8 || {
		echo "ERROR: Android credential smoke could not tap Remember" >&2
		exit 1
	}
	wait_for_login_remember_state true 12 || exit 1
	assert_no_credential_envelope || exit 1
	screenshot 00-credentials-remember-pending

	tap_login_state_target 2 pass 8 || exit 1
	clear_focused_text_field
	input_text_slow "$wrong_pass"
	wait_for_login_lengths "${#AUTH_USER}" "${#wrong_pass}" 12 || exit 1
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent ENTER
	wait_for_login_response_code 3 30 || exit 1
	wait_for_login_state 2 20 >/dev/null || exit 1
	assert_no_credential_envelope || exit 1
	screenshot 01-credentials-wrong-password-not-saved

	tap_login_state_target 2 pass 8 || exit 1
	clear_focused_text_field
	input_text_slow "$AUTH_PASS"
	wait_for_login_lengths "${#AUTH_USER}" "${#AUTH_PASS}" 12 || exit 1
	remember_line="$(wait_for_login_state 2 10)" || exit 1
	remember_state="$(extract_log_value "$remember_line" rememberRequested)"
	if [[ "$remember_state" != "true" ]]; then
		tap_login_state_target 2 remember 8 || exit 1
		wait_for_login_remember_state true 12 || exit 1
	fi
	submit_login_and_wait || exit 1
	wait_auth_online 20 || exit 1
	assert_encrypted_credential_envelope || exit 1
	sleep 3
	close_auth_intro_dialog_if_present
	screenshot 02-credentials-saved-after-login
	assert_credential_envelope_survives_reconnect || exit 1
	screenshot 02a-credentials-after-reconnect
	logout_authenticated_smoke_session || exit 1

	"$ADB" logcat -c || true
	launch_authenticated_endpoint_preserving_credentials
	tap_existing_user_button
	wait_for_login_lengths "${#AUTH_USER}" "${#AUTH_PASS}" 20 || exit 1
	wait_for_login_remember_state true 12 || exit 1
	screenshot 03-credentials-restored-after-restart
	tap_login_state_target 2 remember 8 || exit 1
	wait_for_login_remember_state false 12 || exit 1
	"$ADB" logcat -c || true
	tap_login_state_target 2 ok 8 || exit 1
	wait_for_successful_login 45 || exit 1
	wait_auth_online 20 || exit 1
	assert_credential_store_cleared || exit 1
	sleep 3
	close_auth_intro_dialog_if_present
	logout_authenticated_smoke_session || exit 1

	"$ADB" logcat -c || true
	launch_authenticated_endpoint_preserving_credentials
	tap_existing_user_button
	wait_for_login_lengths 0 0 20 || exit 1
	wait_for_login_remember_state false 12 || exit 1
	screenshot 04-credentials-forgotten-after-restart
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
	echo "Android encrypted credential smoke passed for $AUTH_USER"
}

android_log_count_fixed() {
	local needle="$1"
	"$ADB" logcat -d -v raw 2>/dev/null \
		| tr -d '\r' \
		| grep -F -c "$needle" || true
}

wait_for_reconnect_touch_pair_exactly_once() {
	local expected_x="$1"
	local expected_y="$2"
	local timeout="${3:-45}"
	local deadline=$((SECONDS + timeout))
	local queued_count replay_count queued_line replay_line queued_x queued_y replay_x replay_y order_ok
	# The pre-background viewport and the rebound Activity can differ by a safe
	# inset while Android transfers window ownership. The production whitelist
	# has already proven the mapped point is inside the requested launcher/rail;
	# keep this comparison to half a touch target and require queue/replay to be
	# exactly identical below.
	local mapping_tolerance=32

	while (( SECONDS < deadline )); do
		queued_count="$(android_log_count_fixed 'ANDROID_SMOKE_TOUCH stage=reconnect-queued ')"
		replay_count="$(android_log_count_fixed 'ANDROID_SMOKE_TOUCH stage=reconnect-replay ')"
		if (( queued_count > 1 || replay_count > 1 )); then
			break
		fi
		if [[ "$queued_count" == "1" && "$replay_count" == "1" ]]; then
			# Require a short quiet window so a late duplicate cannot pass a
			# transient exactly-once observation.
			sleep 2
			queued_count="$(android_log_count_fixed 'ANDROID_SMOKE_TOUCH stage=reconnect-queued ')"
			replay_count="$(android_log_count_fixed 'ANDROID_SMOKE_TOUCH stage=reconnect-replay ')"
			break
		fi
		sleep 1
	done

	if [[ "$queued_count" != "1" || "$replay_count" != "1" ]]; then
		echo "ERROR: foreground intent expected exactly one reconnect queue/replay; queued=${queued_count:-0} replay=${replay_count:-0}" >&2
		"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep -E 'ANDROID_SMOKE_TOUCH|ANDROID_SMOKE_HUB_ACTION|login response:|Lost connection' \
			| tail -80 >&2 || true
		return 1
	fi

	queued_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| grep -F 'ANDROID_SMOKE_TOUCH stage=reconnect-queued ' | tail -1 || true)"
	replay_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| grep -F 'ANDROID_SMOKE_TOUCH stage=reconnect-replay ' | tail -1 || true)"
	queued_x="$(extract_log_value "$queued_line" clientX)"
	queued_y="$(extract_log_value "$queued_line" clientY)"
	replay_x="$(extract_log_value "$replay_line" clientX)"
	replay_y="$(extract_log_value "$replay_line" clientY)"
	order_ok="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| awk '/ANDROID_SMOKE_TOUCH stage=reconnect-queued / { queued=NR }
			/ANDROID_SMOKE_TOUCH stage=reconnect-replay / { replay=NR }
			END { print (queued > 0 && replay > queued) ? "true" : "false" }')"
	if [[ ! "$queued_x" =~ ^[0-9]+$ || ! "$queued_y" =~ ^[0-9]+$ \
		|| ! "$replay_x" =~ ^[0-9]+$ || ! "$replay_y" =~ ^[0-9]+$ \
		|| "$queued_x" != "$replay_x" || "$queued_y" != "$replay_y" \
		|| "$order_ok" != "true" ]] \
		|| (( queued_x < expected_x - mapping_tolerance || queued_x > expected_x + mapping_tolerance \
			|| queued_y < expected_y - mapping_tolerance || queued_y > expected_y + mapping_tolerance )); then
		echo "ERROR: reconnect queue/replay did not preserve renderer point $expected_x,$expected_y (allowing ${mapping_tolerance}px viewport settling)" >&2
		echo "$queued_line" >&2
		echo "$replay_line" >&2
		return 1
	fi
	echo "Verified exactly-once foreground intent queue/replay at renderer point $expected_x,$expected_y."
}

assert_android_post_resume_hud_unblocked() {
	local expected_orientation="$1"
	local timeout="${2:-30}"
	local deadline=$((SECONDS + timeout))
	local welcome_line visible_surface
	while (( SECONDS < deadline )); do
		welcome_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep 'ANDROID_SMOKE_WELCOME_DIALOG ' | tail -1 || true)"
		if [[ -n "$welcome_line" ]]; then
			echo "ERROR: welcome modal reappeared and blocked the post-resume HUD" >&2
			echo "$welcome_line" >&2
			return 1
		fi
		visible_surface="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep -E 'ANDROID_SMOKE_HUB_LAYOUT |ANDROID_SMOKE_CHAT_LAYOUT open=true ' \
			| tail -1 || true)"
		if [[ -n "$visible_surface" ]]; then
			sleep 2
			welcome_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
				| grep 'ANDROID_SMOKE_WELCOME_DIALOG ' | tail -1 || true)"
			if [[ -n "$welcome_line" ]]; then
				echo "ERROR: welcome modal appeared after the post-resume HUD became visible" >&2
				echo "$welcome_line" >&2
				return 1
			fi
			echo "Verified post-resume HUD is renderer-visible with no welcome/server modal blocking it."
			return 0
		fi
		sleep 1
	done
	echo "ERROR: post-resume HUD never became renderer-visible; a server/message modal may be blocking it" >&2
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| grep -E 'ANDROID_SMOKE_(WELCOME_DIALOG|HUB_LAYOUT|CHAT_LAYOUT)|login response:|Lost connection' \
		| tail -100 >&2 || true
	return 1
}

lifecycle_background_in_android_settings() {
	local seconds="$1"
	local label="$2"
	local output
	output="$("$ADB" shell am start -W -a android.settings.SETTINGS 2>&1)"
	if ! grep -q 'Status: ok' <<< "$output"; then
		echo "ERROR: Android lifecycle smoke could not switch to Settings for $label" >&2
		echo "$output" >&2
		return 1
	fi
	wait_for_audio_event stop-all 0 15 || return 1
	echo "Android lifecycle smoke backgrounded in Settings for ${seconds}s ($label)"
	sleep "$seconds"
	if [[ -n "$AUTH_DB" ]]; then
		wait_auth_online 5 || {
			echo "ERROR: Android lifecycle session disconnected while backgrounded for $label" >&2
			return 1
		}
	fi
}

resume_game_task_with_client_tap() {
	local client_x="$1"
	local client_y="$2"
	local viewport_line="$3"
	local output screen_x screen_y mapped_point
	mapped_point="$(client_xy_to_screen_xy_from_viewport \
		"$client_x" "$client_y" "$viewport_line")" || return 1
	read -r screen_x screen_y <<< "$mapped_point" || return 1
	if [[ ! "$screen_x" =~ ^[0-9]+$ || ! "$screen_y" =~ ^[0-9]+$ ]]; then
		echo "ERROR: renderer point $client_x,$client_y did not map to a physical tap" >&2
		return 1
	fi
	# Dispatch the task switch and first ACTION_DOWN/UP in one device-shell
	# command without `am start -W`: -W waits for launch completion, which can
	# also give a fast retained-session handshake enough time to finish before
	# the intended first foreground tap exists. The short device-side delay lets
	# GameActivity own the input window while keeping the game-thread reconnect
	# in progress. The focused lifecycle run installs an app-private QA hold,
	# so 400ms gives GameActivity time to own focus while remaining inside it.
	if ! output="$("$ADB" shell "am start -n \
		'$APP_ID/com.openrsc.android.updater.ApplicationUpdater' >/dev/null \
		&& sleep 0.4 && input tap '$screen_x' '$screen_y'" 2>&1)"; then
		echo "ERROR: Android lifecycle smoke could not foreground the game task" >&2
		echo "$output" >&2
		return 1
	fi
}

wait_for_quiet_lost_connection() {
	local timeout="${1:-20}"
	local deadline=$((SECONDS + timeout))
	while (( SECONDS < deadline )); do
		if "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep -q '^Lost connection$'; then
			return 0
		fi
		sleep 1
	done
	return 1
}

should_attempt_lifecycle_network_loss() {
	if [[ "$AUTH_LIFECYCLE_NETWORK_LOSS" == "1" ]]; then
		return 0
	fi
	if [[ "$AUTH_LIFECYCLE_NETWORK_LOSS" == "0" ]]; then
		return 1
	fi
	[[ "$("$ADB" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" == "1" ]]
}

enable_android_dont_keep_activities() {
	save_android_activity_policy
	"$ADB" shell settings put global always_finish_activities 1 >/dev/null 2>&1 || return 1
	local deadline=$((SECONDS + 8))
	local state
	while (( SECONDS < deadline )); do
		state="$("$ADB" shell settings get global always_finish_activities \
			2>/dev/null | tr -d '\r' | tail -1)"
		if [[ "$state" == "1" ]]; then
			return 0
		fi
		sleep 1
	done
	return 1
}

resumed_game_activity_token() {
	local escaped_app_id="${APP_ID//./\\.}"
	"$ADB" shell dumpsys activity activities 2>/dev/null | tr -d '\r' \
		| sed -nE "/(mResumedActivity|topResumedActivity).*${escaped_app_id}\/.*GameActivity/s/.*ActivityRecord\\{([^ ]+).*/\\1/p" \
		| tail -1
}

run_authenticated_lifecycle_smoke() {
	local status=0
	(
	if [[ "$ONLY_AUTH_LIFECYCLE" -eq 1 ]]; then
		enable_android_smoke_lifecycle_reconnect_hold
	else
		disable_android_smoke_lifecycle_reconnect_hold
	fi
	preflight_auth_login_fixture
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true

	"$ADB" shell am force-stop $APP_ID || true
	"$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
	force_android_portrait || {
		echo "ERROR: Android lifecycle smoke could not force portrait rotation" >&2
		exit 1
	}
	"$ADB" logcat -c || true
	enable_android_smoke_audio
	enable_android_smoke_chat_tabs

	launch_authenticated_endpoint
	# uiautomator can restore the emulator's pre-smoke rotation after it taps the
	# native launcher. Reassert the lifecycle baseline once GameActivity owns input.
	force_android_portrait || {
		echo "ERROR: Android lifecycle smoke lost portrait rotation after launch" >&2
		exit 1
	}
	enable_android_smoke_audio
	wait_for_selected_server "$AUTH_HOST" "$AUTH_PORT" 30 || exit 1
	screenshot 00-lifecycle-login-home

	tap_existing_user_button
	sleep 3
	enter_auth_credentials
	screenshot 01-lifecycle-credentials-entered
	submit_login_and_wait || exit 1
	wait_auth_online 20 || exit 1
	sleep 6
	assert_no_android_runtime_crash "after lifecycle login" || {
		screenshot 02-lifecycle-login-crash || true
		exit 1
	}
	screenshot 02-lifecycle-post-login

	close_auth_intro_dialog_if_present
	assert_game_activity_for_input "lifecycle game HUD" "03-lifecycle-lost-before-hud" || exit 1
	screenshot 03-lifecycle-game-hud

	local viewport_settings_row viewport_camera viewport_mouse viewport_sound viewport_expected_camera viewport_expected_mouse viewport_line viewport_mobile_line viewport_closed_hub_line
	local viewport_first_orientation="portrait"
	local viewport_second_orientation="landscape"
	viewport_settings_row="$(read_auth_settings)"
	read -r viewport_camera viewport_mouse viewport_sound <<< "$viewport_settings_row"
	viewport_expected_camera="$([[ "$viewport_camera" == "1" ]] && echo true || echo false)"
	viewport_expected_mouse="$([[ "$viewport_mouse" == "1" ]] && echo true || echo false)"
	viewport_mobile_line="$(wait_for_android_mobile_viewport "$viewport_first_orientation" 30)" || exit 1
	enable_android_smoke_settings
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent 43
	viewport_line="$(wait_for_viewport_settings_state "$viewport_first_orientation" "$viewport_expected_camera" "$viewport_expected_mouse" 30)" || exit 1
	assert_android_mobile_viewport_from_log "$viewport_first_orientation" "$viewport_mobile_line" "$viewport_line" || exit 1
	assert_settings_logout_visible "$viewport_line" || exit 1
	screenshot "03a-lifecycle-${viewport_first_orientation}-viewport-settings"
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent BACK
	viewport_closed_hub_line="$(wait_for_mobile_hub_panel "$viewport_first_orientation" none none 20)" || exit 1
	assert_mobile_hub_layout "$viewport_closed_hub_line" "$viewport_mobile_line" \
		"$viewport_first_orientation" none none || exit 1

	"$ADB" logcat -c || true
	if [[ "$viewport_second_orientation" == "landscape" ]]; then
		force_android_landscape
	else
		force_android_portrait
	fi || {
		echo "ERROR: Android lifecycle smoke could not force $viewport_second_orientation rotation" >&2
		exit 1
	}
	assert_game_activity_for_input "lifecycle $viewport_second_orientation viewport" "03b-lifecycle-lost-${viewport_second_orientation}" || exit 1
	viewport_mobile_line="$(wait_for_android_mobile_viewport "$viewport_second_orientation" 30)" || exit 1
	assert_no_android_runtime_crash "after lifecycle $viewport_second_orientation rotation" || {
		screenshot "03b-lifecycle-${viewport_second_orientation}-rotation-crash" || true
		exit 1
	}
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent 43
	viewport_line="$(wait_for_viewport_settings_state "$viewport_second_orientation" "$viewport_expected_camera" "$viewport_expected_mouse" 30)" || exit 1
	assert_android_mobile_viewport_from_log "$viewport_second_orientation" "$viewport_mobile_line" "$viewport_line" || exit 1
	assert_settings_logout_visible "$viewport_line" || exit 1
	screenshot "03b-lifecycle-${viewport_second_orientation}-viewport-settings"
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent BACK
	viewport_closed_hub_line="$(wait_for_mobile_hub_panel "$viewport_second_orientation" none none 20)" || exit 1
	assert_mobile_hub_layout "$viewport_closed_hub_line" "$viewport_mobile_line" \
		"$viewport_second_orientation" none none || exit 1
	disable_android_smoke_settings
	force_android_portrait || {
		echo "ERROR: Android lifecycle smoke could not restore portrait rotation after viewport checks" >&2
		exit 1
	}
	viewport_mobile_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
	assert_no_android_runtime_crash "after lifecycle portrait restore" || {
		screenshot 03c-lifecycle-portrait-restore-crash || true
		exit 1
	}

	# Capture the first post-resume intent from renderer telemetry before leaving
	# the game. On return it is injected immediately, before any logcat/dumpsys
	# polling can let a fast retained-session reconnect swallow the tap.
	local resume_marker resume_viewport_line resume_chat_line resume_chat_x resume_chat_y
	local resume_chat_log_baseline
	resume_marker="resumeqa$(date +%s)$RANDOM"
	resume_chat_log_baseline="$(read_auth_chat_log_max_id)" || exit 1
	enable_android_smoke_chat_send
	resume_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
	resume_chat_line="$(wait_for_native_chat_layout false "" false 20)" || exit 1
	read -r resume_chat_x resume_chat_y \
		<<< "$(native_chat_launcher_client_point_from_line "$resume_chat_line")" || exit 1

	"$ADB" logcat -c || true
	lifecycle_background_in_android_settings "$AUTH_LIFECYCLE_BACKGROUND_SECONDS" \
		"first post-resume Chat intent" || exit 1
	resume_game_task_with_client_tap "$resume_chat_x" "$resume_chat_y" \
		"$resume_viewport_line" || exit 1
	wait_for_resumed_activity "GameActivity" 20 || {
		assert_resumed_activity "GameActivity" || true
		screenshot 04-lifecycle-resume-failed || true
		exit 1
	}
	wait_for_activity_input_ready "GameActivity" 20 || {
		screenshot 04-lifecycle-input-focus-failed || true
		exit 1
	}
	wait_for_audio_event resume 0 15 || exit 1
	wait_auth_online 20 || exit 1
	wait_for_reconnect_touch_pair_exactly_once "$resume_chat_x" "$resume_chat_y" 45 || exit 1
	assert_android_post_resume_hud_unblocked portrait 30 || exit 1
	assert_no_android_runtime_crash "after launcher resume" || {
		screenshot 04-lifecycle-resume-crash || true
		exit 1
	}
	screenshot 04-lifecycle-after-resume

	resume_chat_line="$(wait_for_native_chat_layout true "" false 45)" || exit 1
	assert_native_chat_layout "$resume_chat_line" "$resume_viewport_line" true || exit 1
	# The queued launcher replay is itself the assertion here. Leave Chat open so
	# the next cycle can prove a Compose press made during reconnect survives the
	# retained model reset instead of being silently closed.
	screenshot 04a-lifecycle-first-chat-intent-open

	local compose_viewport_line compose_chat_line compose_x compose_y
	compose_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
	compose_chat_line="$(wait_for_native_chat_layout true "" false 20)" || exit 1
	read -r compose_x compose_y \
		<<< "$(native_chat_rect_client_point_from_line "$compose_chat_line" compose)" || exit 1
	"$ADB" logcat -c || true
	lifecycle_background_in_android_settings "$AUTH_LIFECYCLE_INTENT_BACKGROUND_SECONDS" \
		"open Chat then Compose during reconnect" || exit 1
	resume_game_task_with_client_tap "$compose_x" "$compose_y" \
		"$compose_viewport_line" || exit 1
	wait_for_resumed_activity "GameActivity" 20 || exit 1
	wait_for_activity_input_ready "GameActivity" 20 || exit 1
	wait_for_audio_event resume 0 15 || exit 1
	wait_for_successful_login 45 || exit 1
	wait_auth_online 20 || exit 1
	assert_android_post_resume_hud_unblocked portrait 30 || exit 1
	wait_for_reconnect_touch_pair_exactly_once "$compose_x" "$compose_y" 45 || exit 1
	compose_chat_line="$(wait_for_native_chat_layout true "" true 30)" || exit 1
	assert_native_chat_layout "$compose_chat_line" "$compose_viewport_line" true || exit 1
	assert_soft_keyboard_visible || exit 1
	input_text "$resume_marker"
	"$ADB" shell input keyevent ENTER
	wait_for_chat_send "$resume_marker" 20 || exit 1
	wait_auth_chat_logged "$resume_marker" "$resume_chat_log_baseline" 30 || exit 1
	assert_no_android_runtime_crash "after Compose-during-reconnect roundtrip" || exit 1
	screenshot 04b-lifecycle-compose-during-reconnect
	"$ADB" shell input keyevent BACK
	compose_chat_line="$(wait_for_native_chat_layout true "" false 20)" || exit 1
	"$ADB" shell input keyevent BACK
	wait_for_native_chat_layout false "" false 20 >/dev/null || exit 1

	# A rail press has toggle semantics during normal play. During reconnect the
	# deferred intent must therefore restore the desired final state exactly once,
	# never apply a second toggle that closes the requested drawer.
	local rail_viewport_line rail_hub_line rail_active_line rail_x rail_y
	rail_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
	rail_hub_line="$(wait_for_mobile_hub_panel portrait none none 20)" || exit 1
	read -r rail_x rail_y \
		<<< "$(mobile_hub_control_client_point_from_layout "$rail_hub_line" INVENTORY)" || exit 1
	"$ADB" logcat -c || true
	lifecycle_background_in_android_settings "$AUTH_LIFECYCLE_INTENT_BACKGROUND_SECONDS" \
		"Inventory rail desired-state replay" || exit 1
	resume_game_task_with_client_tap "$rail_x" "$rail_y" "$rail_viewport_line" || exit 1
	wait_for_resumed_activity "GameActivity" 20 || exit 1
	wait_for_activity_input_ready "GameActivity" 20 || exit 1
	wait_for_audio_event resume 0 15 || exit 1
	wait_auth_online 20 || exit 1
	wait_for_reconnect_touch_pair_exactly_once "$rail_x" "$rail_y" 45 || exit 1
	assert_android_post_resume_hud_unblocked portrait 30 || exit 1
	wait_for_mobile_hub_action INVENTORY inventory right 20 >/dev/null || exit 1
	assert_no_mobile_hub_world_or_chat_fallthrough INVENTORY 2 || exit 1
	rail_active_line="$(wait_for_mobile_hub_panel portrait inventory right 20)" || exit 1
	assert_mobile_hub_layout "$rail_active_line" "$rail_viewport_line" \
		portrait inventory right || exit 1
	assert_no_android_runtime_crash "after Inventory reconnect intent" || exit 1
	screenshot 04c-lifecycle-rail-intent
	"$ADB" shell input keyevent BACK
	wait_for_mobile_hub_panel portrait none none 20 >/dev/null || exit 1

	# Short app switches do not request the proactive retained-session handshake.
	# Their first rail tap must be ordinary input, with no stale reconnect intent
	# left behind for a later, unrelated foreground connection loss.
	local stale_viewport_line stale_hub_line stale_x stale_y stale_action_count stale_replay_count
	stale_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
	stale_hub_line="$(wait_for_mobile_hub_panel portrait none none 20)" || exit 1
	read -r stale_x stale_y \
		<<< "$(mobile_hub_control_client_point_from_layout "$stale_hub_line" MAGIC)" || exit 1
	"$ADB" logcat -c || true
	lifecycle_background_in_android_settings 2 "short switch without proactive reconnect" || exit 1
	resume_game_task_with_client_tap "$stale_x" "$stale_y" "$stale_viewport_line" || exit 1
	wait_for_resumed_activity "GameActivity" 20 || exit 1
	wait_for_activity_input_ready "GameActivity" 20 || exit 1
	wait_for_audio_event resume 0 15 || exit 1
	wait_for_mobile_hub_action MAGIC magic right 20 >/dev/null || exit 1
	assert_no_mobile_hub_fallthrough MAGIC 2 || exit 1
	if [[ "$(android_log_count_fixed 'ANDROID_SMOKE_TOUCH stage=reconnect-queued ')" != "0" ]]; then
		echo "ERROR: short app switch incorrectly queued a reconnect intent" >&2
		exit 1
	fi
	"$ADB" shell input keyevent BACK
	wait_for_mobile_hub_panel portrait none none 20 >/dev/null || exit 1

	if should_attempt_lifecycle_network_loss; then
		local network_loss_observed=false
		disable_android_network_for_bootstrap
		if wait_for_quiet_lost_connection 20; then
			network_loss_observed=true
		fi
		restore_android_network_state
		# A transport can report the failed write just after connectivity returns.
		# Include that bounded late edge instead of carrying an unnoticed reconnect
		# into the Activity-recreation case that follows.
		if [[ "$network_loss_observed" == "false" ]] \
			&& wait_for_quiet_lost_connection 5; then
			network_loss_observed=true
		fi
		if [[ "$network_loss_observed" == "true" ]]; then
			wait_for_successful_login 60 || exit 1
			wait_auth_online 20 || exit 1
			sleep 2
			stale_replay_count="$(android_log_count_fixed 'ANDROID_SMOKE_TOUCH stage=reconnect-replay ')"
			stale_action_count="$(android_log_count_fixed 'ANDROID_SMOKE_HUB_ACTION control=MAGIC ')"
			if [[ "$stale_replay_count" != "0" || "$stale_action_count" != "1" ]]; then
				echo "ERROR: unrelated network reconnect replayed a stale MAGIC intent; replay=$stale_replay_count actions=$stale_action_count" >&2
				exit 1
			fi
			wait_for_mobile_hub_panel portrait none none 20 >/dev/null || exit 1
			echo "Verified unrelated foreground connection loss did not replay the short-switch MAGIC tap."
		else
			if [[ "$AUTH_LIFECYCLE_NETWORK_LOSS" == "1" ]]; then
				echo "ERROR: requested unrelated-loss coverage could not revoke the live TCP connection" >&2
				exit 1
			fi
			echo "WARNING: emulator network controls did not revoke the live TCP connection; unrelated-loss replay coverage was not deterministic on this transport." >&2
		fi
	else
		echo "Android unrelated-loss stale-intent coverage disabled for this device."
	fi
	assert_no_android_runtime_crash "after stale reconnect-intent coverage" || exit 1
	screenshot 04d-lifecycle-stale-intent

	# Exercise real Activity destruction without killing the process or its game
	# session. The global developer option is captured and restored even if the
	# assertion exits early (android_smoke_cleanup owns the final safeguard).
	if [[ "$AUTH_LIFECYCLE_RECREATE_ACTIVITY" == "1" ]]; then
		local old_activity_token new_activity_token recreate_marker recreate_baseline
		local recreate_viewport_line recreate_chat_line recreate_activity_count recreate_settings_output
		old_activity_token="$(resumed_game_activity_token)"
		if [[ -z "$old_activity_token" ]]; then
			echo "ERROR: could not identify the retained GameActivity before recreation" >&2
			exit 1
		fi
		if enable_android_dont_keep_activities; then
			recreate_marker="activityqa$(date +%s)$RANDOM"
			recreate_baseline="$(read_auth_chat_log_max_id)" || exit 1
			"$ADB" logcat -c || true
			recreate_settings_output="$("$ADB" shell am start -W -a android.settings.SETTINGS 2>&1)"
			if ! grep -q 'Status: ok' <<< "$recreate_settings_output"; then
				echo "ERROR: could not background GameActivity for recreation" >&2
				exit 1
			fi
			wait_for_audio_event stop-all 0 15 || exit 1
			sleep 3
			"$ADB" shell am start -n \
				"$APP_ID/com.openrsc.android.updater.ApplicationUpdater" >/dev/null
			if ! wait_for_resumed_activity "GameActivity" 8; then
				wait_for_wrapper_ready || exit 1
				tap_play_button
				ensure_game_activity_from_wrapper 60 || exit 1
			fi
			restore_android_activity_policy
			wait_for_activity_input_ready "GameActivity" 20 || exit 1
			wait_for_audio_event resume 0 15 || exit 1
			wait_auth_online 20 || exit 1
			new_activity_token="$(resumed_game_activity_token)"
			if [[ -z "$new_activity_token" || "$new_activity_token" == "$old_activity_token" ]]; then
				echo "ERROR: Don't keep activities did not replace GameActivity; before=$old_activity_token after=${new_activity_token:-missing}" >&2
				exit 1
			fi
			recreate_activity_count="$("$ADB" shell dumpsys activity activities | tr -d '\r' \
				| sed -nE '/Hist[[:space:]]+#[0-9]+: ActivityRecord.*GameActivity/s/.*ActivityRecord\{([^ ]+).*/\1/p' \
				| sort -u | awk 'END { print NR + 0 }')"
			if [[ "$recreate_activity_count" != "1" ]]; then
				echo "ERROR: Activity recreation left $recreate_activity_count GameActivity history records" >&2
				exit 1
			fi
			assert_android_post_resume_hud_unblocked portrait 30 || exit 1
			recreate_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
			recreate_chat_line="$(wait_for_native_chat_layout false "" false 20)" || exit 1
			tap_native_chat_launcher_from_line "$recreate_chat_line" || exit 1
			recreate_chat_line="$(wait_for_native_chat_layout true "" false 20)" || exit 1
			tap_native_chat_rect_from_line "$recreate_chat_line" compose || exit 1
			recreate_chat_line="$(wait_for_native_chat_layout true "" true 30)" || exit 1
			assert_native_chat_layout "$recreate_chat_line" "$recreate_viewport_line" true || exit 1
			input_text "$recreate_marker"
			"$ADB" shell input keyevent ENTER
			wait_for_chat_send "$recreate_marker" 20 || exit 1
			wait_auth_chat_logged "$recreate_marker" "$recreate_baseline" 30 || exit 1
			"$ADB" shell input keyevent BACK
			wait_for_native_chat_layout true "" false 20 >/dev/null || exit 1
			"$ADB" shell input keyevent BACK
			wait_for_native_chat_layout false "" false 20 >/dev/null || exit 1
			echo "Verified retained session/input after GameActivity recreation ($old_activity_token -> $new_activity_token)."
			screenshot 04e-lifecycle-activity-recreated
		else
			restore_android_activity_policy
			echo "WARNING: device did not expose a live Don't keep activities policy; retained Activity recreation coverage skipped." >&2
		fi
	fi
	disable_android_smoke_chat_send

	"$ADB" shell am start -n $APP_ID/com.openrsc.android.updater.ApplicationUpdater >/dev/null
	"$ADB" shell am start -n $APP_ID/com.openrsc.android.updater.ApplicationUpdater >/dev/null
	wait_for_resumed_activity "GameActivity" 20 || {
		assert_resumed_activity "GameActivity" || true
		screenshot 05-lifecycle-relaunch-failed || true
		exit 1
	}
	wait_auth_online 20 || exit 1
	sleep 3
	assert_no_android_runtime_crash "after duplicate launcher relaunch" || {
		screenshot 05-lifecycle-relaunch-crash || true
		exit 1
	}
	local game_activity_count
	game_activity_count="$("$ADB" shell dumpsys activity activities | tr -d '\r' \
		| sed -nE '/Hist[[:space:]]+#[0-9]+: ActivityRecord.*GameActivity/s/.*ActivityRecord\{([^ ]+).*/\1/p' \
		| sort -u | awk 'END { print NR + 0 }')"
	if [[ "$game_activity_count" != "1" ]]; then
		echo "ERROR: duplicate launcher produced $game_activity_count GameActivity history records" >&2
		"$ADB" shell dumpsys activity activities | grep -E 'Hist|GameActivity' >&2 || true
		exit 1
	fi
	echo "Verified duplicate launcher retained exactly one GameActivity"
	screenshot 05-lifecycle-after-duplicate-relaunch

	local settings_row settings_camera settings_mouse settings_sound expected_camera expected_mouse
	settings_row="$(read_auth_settings)"
	read -r settings_camera settings_mouse settings_sound <<< "$settings_row"
	expected_camera="$([[ "$settings_camera" == "1" ]] && echo true || echo false)"
	expected_mouse="$([[ "$settings_mouse" == "1" ]] && echo true || echo false)"
	local settings_line
	enable_android_smoke_settings
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent 43
	settings_line="$(wait_for_settings_state "$expected_camera" "$expected_mouse" 30)" || exit 1
	assert_settings_logout_visible "$settings_line" || exit 1
	sleep 2
	screenshot 06-lifecycle-settings-open
	complete_android_account_logout_from_settings_line "$settings_line" "06-lifecycle" || exit 1
	sleep 10
	disable_android_smoke_settings
	assert_no_android_runtime_crash "after lifecycle logout" || {
		screenshot 07-lifecycle-logout-crash || true
		exit 1
	}
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || exit 1
	wait_for_network_writer_count 0 20 || exit 1
	wait_for_audio_event stop-all 0 15 || exit 1
	screenshot 07-lifecycle-after-logout-login-home
	enable_android_smoke_login
	"$ADB" logcat -c || true
	wait_for_login_state 0 15 >/dev/null || exit 1
	tap_login_state_target 0 homeExisting 8 || tap_pct "$AUTH_EXISTING_USER_X_PCT" "$AUTH_EXISTING_USER_Y_PCT"
	sleep 3
	local post_logout_login_line
	post_logout_login_line="$(wait_for_login_state 2 15)" || exit 1
	if ! assert_soft_keyboard_visible; then
		local login_user_x login_user_y
		login_user_x="$(extract_log_value "$post_logout_login_line" userX)"
		login_user_y="$(extract_log_value "$post_logout_login_line" userY)"
		if [[ "$login_user_x" =~ ^[0-9]+$ && "$login_user_y" =~ ^[0-9]+$ ]]; then
			tap_client_xy "$login_user_x" "$login_user_y"
			sleep 2
		fi
		assert_soft_keyboard_visible
	fi
	screenshot 08-lifecycle-after-logout-keyboard

	"$ADB" shell input keyevent BACK || true
	"$ADB" shell am force-stop $APP_ID || true
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
	disable_android_smoke_audio
	disable_android_smoke_chat_tabs
	disable_android_smoke_lifecycle_reconnect_hold
	echo "Android auth/lifecycle smoke passed for $AUTH_USER on $AUTH_HOST:$AUTH_PORT"
	) || status=$?

	disable_android_smoke_lifecycle_reconnect_hold
	if [[ "$status" -ne 0 ]]; then
		graceful_cleanup_authenticated_smoke_session || status=1
	fi
	disable_android_smoke_audio
	disable_android_smoke_chat_tabs
	if wait_auth_offline 2 >/dev/null 2>&1; then
		"$ADB" shell am force-stop "$APP_ID" || true
	fi
	return "$status"
}

wait_for_npc_target() {
    local npc_id="$1"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_NPC_TARGET id=$npc_id " | tail -1 || true)"
        if [[ "$line" =~ clientX=([0-9]+).*clientY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
            return 0
        fi
        sleep 1
    done

    if [[ -n "$AUTH_NPC_FALLBACK_CLIENT_X" && -n "$AUTH_NPC_FALLBACK_CLIENT_Y" ]]; then
        echo "WARNING: timed out waiting for Android NPC target id $npc_id; using fallback client $AUTH_NPC_FALLBACK_CLIENT_X,$AUTH_NPC_FALLBACK_CLIENT_Y" >&2
        echo "$AUTH_NPC_FALLBACK_CLIENT_X $AUTH_NPC_FALLBACK_CLIENT_Y"
        return 0
    fi

    echo "ERROR: timed out waiting for Android NPC target id $npc_id" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_NPC_" | tail -20 >&2 || true
    return 1
}

wait_for_npc_action() {
    local npc_id="$1"
    local expected="$2"
    local timeout="${3:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_NPC_ACTION action=$expected id=$npc_id " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android NPC tap: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android NPC action $expected on id $npc_id" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_NPC_" | tail -20 >&2 || true
    return 1
}

wait_for_player_target() {
    local target_name="${1:-}"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        if [[ -n "$target_name" ]]; then
            line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
                | grep "ANDROID_SMOKE_PLAYER_TARGET " | grep -i " name=$target_name " | tail -1 || true)"
        else
            line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
                | grep "ANDROID_SMOKE_PLAYER_TARGET " | tail -1 || true)"
        fi
        if [[ "$line" =~ clientX=([0-9]+).*clientY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android player target ${target_name:-any}" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_PLAYER_" | tail -30 >&2 || true
    return 1
}

wait_for_player_action() {
    local expected="${1:-PLAYER_ATTACK}"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line action

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_PLAYER_ACTION " | tail -1 || true)"
        if [[ "$line" =~ action=([^[:space:]]+) ]]; then
            action="${BASH_REMATCH[1]}"
            if [[ "$action" == "$expected" || ( "$expected" == "PLAYER_ATTACK" && "$action" == PLAYER_ATTACK_* ) ]]; then
                echo "Verified Android player action: $line"
                return 0
            fi
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android player action $expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_PLAYER_" | tail -30 >&2 || true
    return 1
}

wait_for_player_command() {
    local expected="$1"
    local timeout="${2:-10}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_PLAYER_COMMAND action=$expected " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android player smoke command: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android player smoke command $expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_PLAYER_" | tail -30 >&2 || true
    return 1
}

tap_player_target() {
    local target_name="${1:-}"
    local coords client_x client_y
    coords="$(wait_for_player_target "$target_name" 30)" || return 1
    read -r client_x client_y <<< "$coords"
    echo "Android player target ${target_name:-any} at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
}

long_press_player_target() {
    local target_name="${1:-}"
    local duration_ms="${2:-1200}"
    local coords client_x client_y
    coords="$(wait_for_player_target "$target_name" 30)" || return 1
    read -r client_x client_y <<< "$coords"
    echo "Android player long-press target ${target_name:-any} at client $client_x,$client_y"
    long_press_client_xy "$client_x" "$client_y" "$duration_ms"
}

wait_for_player_attack_menu_index() {
    local timeout="${1:-15}"
    local deadline=$((SECONDS + timeout))
    local line actions index action
    local -a action_list

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU " | tail -1 || true)"
        actions="$(extract_log_value "$line" actions)"
        if [[ -n "$actions" && "$actions" != "none" ]]; then
            IFS=',' read -r -a action_list <<< "$actions"
            for index in "${!action_list[@]}"; do
                action="${action_list[$index]}"
                if [[ "$action" == PLAYER_ATTACK_* ]]; then
                    echo "$index"
                    return 0
                fi
            done
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android player attack context menu row" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -E "ANDROID_SMOKE_CONTEXT_MENU|ANDROID_SMOKE_PLAYER_" | tail -40 >&2 || true
    return 1
}

tap_npc_target() {
    local npc_id="$1"
    local coords client_x client_y
    coords="$(wait_for_npc_target "$npc_id" 30)" || return 1
    read -r client_x client_y <<< "$coords"
    echo "Android NPC target $npc_id at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
}

long_press_npc_target() {
    local npc_id="$1"
    local duration_ms="${2:-1200}"
    local coords client_x client_y
    coords="$(wait_for_npc_target "$npc_id" 30)" || return 1
    read -r client_x client_y <<< "$coords"
    echo "Android NPC long-press target $npc_id at client $client_x,$client_y"
    long_press_client_xy "$client_x" "$client_y" "$duration_ms"
}

wait_for_context_menu() {
    local timeout="${1:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU " | tail -1 || true)"
        if [[ "$line" =~ x=([0-9]+).*y=([0-9]+).*width=([0-9]+).*height=([0-9]+).*items=([0-9]+).*firstAction=([^[:space:]]+).*mouseX=([0-9]+).*mouseY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} ${BASH_REMATCH[3]} ${BASH_REMATCH[4]} ${BASH_REMATCH[5]} ${BASH_REMATCH[6]} ${BASH_REMATCH[7]} ${BASH_REMATCH[8]}"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android context menu" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -E "ANDROID_SMOKE_CONTEXT_MENU|ANDROID_SMOKE_NPC_" | tail -30 >&2 || true
    return 1
}

wait_for_context_menu_action() {
    local expected="$1"
    local timeout="${2:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU_ACTION action=$expected " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android context menu action: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android context menu action $expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU" | tail -30 >&2 || true
    return 1
}

wait_for_camera_rotate() {
    local timeout="${1:-15}"
    local deadline=$((SECONDS + timeout))
    local line before_rotation after_rotation before_angle after_angle

    while (( SECONDS < deadline )); do
        while IFS= read -r line; do
            if [[ "$line" =~ beforeRotation=([0-9]+).*afterRotation=([0-9]+).*beforeAngle=([0-9]+).*afterAngle=([0-9]+) ]]; then
                before_rotation="${BASH_REMATCH[1]}"
                after_rotation="${BASH_REMATCH[2]}"
                before_angle="${BASH_REMATCH[3]}"
                after_angle="${BASH_REMATCH[4]}"
                if (( before_rotation != after_rotation || before_angle != after_angle )); then
                    echo "Verified Android camera rotate: $line"
                    return 0
                fi
            fi
        done < <("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -E "ANDROID_SMOKE_CAMERA_(ROTATE|GESTURE)" | tail -30 || true)
        sleep 1
    done

    echo "ERROR: timed out waiting for Android camera rotate" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -E "ANDROID_SMOKE_CAMERA_(ROTATE|GESTURE)" | tail -30 >&2 || true
    return 1
}

wait_for_camera_gesture_route() {
	local expected="$1"
	local timeout="${2:-10}"
	local deadline=$((SECONDS + timeout))
	local line route
	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_GESTURE route=$expected " | tail -1 || true)"
		route="$(extract_log_value "$line" route)"
		if [[ "$route" == "$expected" ]]; then
			echo "Verified Android camera gesture routing: $line"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: timed out waiting for Android camera gesture route=$expected" >&2
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA" | tail -40 >&2 || true
	return 1
}

assert_camera_gesture_unchanged() {
	local line before_rotation after_rotation before_angle after_angle before_pitch after_pitch saw_line=0
	if "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -q "ANDROID_SMOKE_CAMERA_GESTURE route=camera "; then
		echo "ERROR: panel swipe escaped scroll routing and reached the world camera" >&2
		"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_GESTURE" | tail -40 >&2 || true
		return 1
	fi
	while IFS= read -r line; do
		[[ -z "$line" ]] && continue
		saw_line=1
		if [[ "$line" =~ beforeRotation=([0-9]+).*afterRotation=([0-9]+).*beforeAngle=([0-9]+).*afterAngle=([0-9]+).*beforePitch=([0-9]+).*afterPitch=([0-9]+) ]]; then
			before_rotation="${BASH_REMATCH[1]}"
			after_rotation="${BASH_REMATCH[2]}"
			before_angle="${BASH_REMATCH[3]}"
			after_angle="${BASH_REMATCH[4]}"
			before_pitch="${BASH_REMATCH[5]}"
			after_pitch="${BASH_REMATCH[6]}"
			if (( before_rotation != after_rotation || before_angle != after_angle || before_pitch != after_pitch )); then
				echo "ERROR: scroll-routed Android gesture changed the world camera: $line" >&2
				return 1
			fi
		fi
	done < <("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_GESTURE route=scroll " || true)
	if (( saw_line == 0 )); then
		echo "ERROR: no scroll-routed Android camera gesture evidence found" >&2
		return 1
	fi
	echo "Verified scroll-routed Android gesture left the world camera unchanged."
}

wait_for_zoom_state() {
    local timeout="${1:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_ZOOM " | tail -1 || true)"
        if [[ "$line" =~ beforeLastZoom=([-0-9]+).*afterLastZoom=([-0-9]+).*beforeCameraZoom=([-0-9]+).*afterCameraZoom=([-0-9]+) ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android zoom state" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_ZOOM" | tail -30 >&2 || true
    return 1
}

wait_for_zoom_change() {
    local timeout="${1:-20}"
    local quiet="${2:-0}"
    local deadline=$((SECONDS + timeout))
    local line before_last_zoom after_last_zoom before_camera_zoom after_camera_zoom

    while (( SECONDS < deadline )); do
        while IFS= read -r line; do
            if [[ "$line" =~ beforeLastZoom=([-0-9]+).*afterLastZoom=([-0-9]+).*beforeCameraZoom=([-0-9]+).*afterCameraZoom=([-0-9]+) ]]; then
                before_last_zoom="${BASH_REMATCH[1]}"
                after_last_zoom="${BASH_REMATCH[2]}"
                before_camera_zoom="${BASH_REMATCH[3]}"
                after_camera_zoom="${BASH_REMATCH[4]}"
                if (( (before_last_zoom >= 0 && before_last_zoom != after_last_zoom) || (before_camera_zoom >= 0 && before_camera_zoom != after_camera_zoom) )); then
                    echo "Verified Android zoom gesture: $line"
                    return 0
                fi
            fi
        done < <("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_ZOOM" | tail -20 || true)
        sleep 1
    done

    if [[ "$quiet" != "1" ]]; then
        echo "ERROR: timed out waiting for Android zoom gesture" >&2
        "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_ZOOM" | tail -30 >&2 || true
    fi
    return 1
}

assert_no_zoom_change_logged() {
    local timeout="${1:-4}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_ZOOM" | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "ERROR: one-finger drag unexpectedly changed Android zoom: $line" >&2
            return 1
        fi
        sleep 1
    done

    echo "Verified Android one-finger drag did not change zoom"
    return 0
}

assert_one_finger_zoom_unchanged() {
	local line before_last_zoom after_last_zoom before_camera_zoom after_camera_zoom saw_line=0
	while IFS= read -r line; do
		[[ -z "$line" ]] && continue
		saw_line=1
		if [[ "$line" =~ beforeLastZoom=([-0-9]+).*afterLastZoom=([-0-9]+).*beforeCameraZoom=([-0-9]+).*afterCameraZoom=([-0-9]+) ]]; then
			before_last_zoom="${BASH_REMATCH[1]}"
			after_last_zoom="${BASH_REMATCH[2]}"
			before_camera_zoom="${BASH_REMATCH[3]}"
			after_camera_zoom="${BASH_REMATCH[4]}"
			if (( before_last_zoom != after_last_zoom || before_camera_zoom != after_camera_zoom )); then
				echo "ERROR: one-finger Android gesture changed zoom: $line" >&2
				return 1
			fi
		fi
	done < <("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_GESTURE " || true)
	if (( saw_line == 0 )); then
		echo "ERROR: no Android one-finger gesture evidence found" >&2
		return 1
	fi
	echo "Verified Android one-finger gesture left zoom unchanged."
}

native_chat_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null \
        | tr -d '\r' \
		| grep -E "ANDROID_SMOKE_(CHAT_(LAYOUT|SEND)|TOUCH) |ANDROID_MOBILE_VIEWPORT|VOIDSCAPE_NETWORK_WRITER" \
		| tail -100 >&2 || true
}

wait_for_native_chat_layout() {
    local expected_open="$1"
    local expected_channel="${2:-}"
    local expected_ime="${3:-}"
    local timeout="${4:-15}"
    local deadline=$((SECONDS + timeout))
    local line open channel ime keyboard_top

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null \
            | tr -d '\r' \
            | grep "ANDROID_SMOKE_CHAT_LAYOUT " \
            | tail -1 || true)"
        open="$(extract_log_value "$line" open)"
        channel="$(extract_log_value "$line" channel)"
        ime="$(extract_log_value "$line" ime)"
        keyboard_top="$(extract_log_value "$line" keyboardTop)"
        if [[ "$open" == "$expected_open" \
            && ( -z "$expected_channel" || "$channel" == "$expected_channel" ) \
            && ( -z "$expected_ime" || "$ime" == "$expected_ime" ) \
            && ( "$expected_ime" != "true" \
                || "$keyboard_top" =~ ^[0-9]+$ ) ]]; then
            printf '%s\n' "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for native Android chat layout open=$expected_open channel=${expected_channel:-any} ime=${expected_ime:-any}" >&2
    native_chat_log_tail
    return 1
}

native_chat_rect_from_payload() {
    local label="$1"
    local payload="$2"
    local expected_state="${3:-positive}"
    local x y width height
    IFS=',' read -r x y width height <<< "$payload"
    if [[ ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ \
        || ! "$width" =~ ^[0-9]+$ || ! "$height" =~ ^[0-9]+$ ]]; then
        echo "ERROR: invalid native Android chat rect for $label: ${payload:-missing}" >&2
        return 1
    fi
    if [[ "$expected_state" == "zero" ]]; then
        if (( x != 0 || y != 0 || width != 0 || height != 0 )); then
            echo "ERROR: closed native Android chat $label must be 0,0,0,0: $payload" >&2
            return 1
        fi
    elif (( width <= 0 || height <= 0 )); then
        echo "ERROR: open native Android chat $label must have positive dimensions: $payload" >&2
        return 1
    fi
    printf '%s %s %s %s\n' "$x" "$y" "$width" "$height"
}

native_chat_layout_rect_from_line() {
    local line="$1"
    local field="$2"
    local expected_state="${3:-positive}"
    local payload
    payload="$(extract_log_value "$line" "$field")"
    native_chat_rect_from_payload "$field" "$payload" "$expected_state" || {
        echo "$line" >&2
        return 1
    }
}

native_chat_filter_rect_from_line() {
    local line="$1"
    local filter="$2"
    local expected_state="${3:-positive}"
    local payload occurrences
    occurrences="$(printf '%s\n' "$line" | tr ' ' '\n' | grep -Ec "^filter=${filter}:" || true)"
    if [[ "$occurrences" != "1" ]]; then
        echo "ERROR: native Android chat reported $occurrences $filter filters instead of 1" >&2
        echo "$line" >&2
        return 1
    fi
    payload="$(printf '%s\n' "$line" \
        | tr ' ' '\n' \
        | sed -nE "s/^filter=${filter}:([^[:space:]]+)$/\\1/p" \
        | tail -1)"
    native_chat_rect_from_payload "filter-$filter" "$payload" "$expected_state" || {
        echo "$line" >&2
        return 1
    }
}

native_chat_channel_for_filter() {
    case "$1" in
        ALL) echo ALL ;;
        PUBLIC) echo CHAT ;;
        QUEST) echo QUEST ;;
        GLOBAL) echo GLOBAL ;;
        PM) echo PRIVATE ;;
        *)
            echo "ERROR: unknown native Android chat filter '$1'" >&2
            return 1
            ;;
    esac
}

assert_native_chat_rect_in_bounds() {
    local label="$1"
    local rect="$2"
    local logical_width="$3"
    local logical_height="$4"
    local x y width height
    read -r x y width height <<< "$rect"
    if (( x < 0 || y < 0 || x + width > logical_width || y + height > logical_height )); then
        echo "ERROR: native Android chat $label is outside ${logical_width}x${logical_height}: $rect" >&2
        return 1
    fi
}

assert_native_chat_rect_contained() {
    local label="$1"
    local rect="$2"
    local container_label="$3"
    local container="$4"
    local x y width height container_x container_y container_width container_height
    read -r x y width height <<< "$rect"
    read -r container_x container_y container_width container_height <<< "$container"
    if (( x < container_x || y < container_y \
        || x + width > container_x + container_width \
        || y + height > container_y + container_height )); then
        echo "ERROR: native Android chat $label is not contained by $container_label: $rect inside $container" >&2
        return 1
    fi
}

assert_native_chat_touch_target() {
    local label="$1"
    local rect="$2"
    local target48="$3"
    local scale="$4"
    local density="$5"
    local x y width height
    read -r x y width height <<< "$rect"
    if (( width < target48 || height < target48 )); then
        echo "ERROR: native Android chat $label ${width}x${height} is below target48=$target48" >&2
        return 1
    fi
    assert_client_rect_extent_at_least_dp "chat-$label" width "$width" "$scale" "$density" 48 || return 1
    assert_client_rect_extent_at_least_dp "chat-$label" height "$height" "$scale" "$density" 48 || return 1
}

assert_native_chat_layout() {
    local line="$1"
    local viewport_line="$2"
    local expected_open="$3"
    local open inline_focused channel ime keyboard_top target48 draft_length
    local logical_width logical_height viewport_touch48 scale density
    local launcher history inline sheet composer compose entry rect filter filter_count
    local entry_x entry_y entry_width entry_height
    local compose_x compose_y compose_width compose_height
    local -a filters=(ALL PUBLIC QUEST GLOBAL PM)

    open="$(extract_log_value "$line" open)"
    inline_focused="$(extract_log_value "$line" inlineFocused)"
    channel="$(extract_log_value "$line" channel)"
    ime="$(extract_log_value "$line" ime)"
    keyboard_top="$(extract_log_value "$line" keyboardTop)"
    target48="$(extract_log_value "$line" target48)"
    draft_length="$(extract_log_value "$line" draftLength)"
    logical_width="$(extract_log_value "$viewport_line" logicalW)"
    logical_height="$(extract_log_value "$viewport_line" logicalH)"
    viewport_touch48="$(extract_log_value "$viewport_line" touch48)"
    scale="$(extract_log_value "$viewport_line" scale)"
    density="$(extract_log_value "$viewport_line" density)"

    if [[ "$open" != "$expected_open" \
        || ( "$inline_focused" != "true" && "$inline_focused" != "false" ) \
        || ( "$ime" != "true" && "$ime" != "false" ) \
        || ( "$ime" == "true" && ! "$keyboard_top" =~ ^[0-9]+$ ) ]]; then
        echo "ERROR: unexpected native Android chat open/IME state: $line" >&2
        return 1
    fi
    case "$channel" in
        ALL|CHAT|QUEST|GLOBAL|PRIVATE) ;;
        *)
            echo "ERROR: native Android chat reported invalid channel '$channel': $line" >&2
            return 1
            ;;
    esac
    if [[ ! "$target48" =~ ^[0-9]+$ || "$target48" -le 0 \
        || ! "$viewport_touch48" =~ ^[0-9]+$ || "$viewport_touch48" -le 0 \
        || "$target48" -ne "$viewport_touch48" \
        || ! "$draft_length" =~ ^[0-9]+$ \
        || ! "$logical_width" =~ ^[0-9]+$ || "$logical_width" -le 0 \
        || ! "$logical_height" =~ ^[0-9]+$ || "$logical_height" -le 0 ]]; then
        echo "ERROR: native Android chat/viewport telemetry is incomplete or inconsistent" >&2
        echo "$line" >&2
        echo "$viewport_line" >&2
        return 1
    fi
    if ! is_positive_log_number "$scale" || ! is_positive_log_number "$density"; then
        echo "ERROR: native Android chat needs positive viewport scale/density: $viewport_line" >&2
        return 1
    fi

    launcher="$(native_chat_layout_rect_from_line "$line" launcher positive)" || return 1
    history="$(native_chat_layout_rect_from_line "$line" history positive)" || return 1
    inline="$(native_chat_layout_rect_from_line "$line" inline positive)" || return 1
    assert_native_chat_rect_in_bounds launcher "$launcher" "$logical_width" "$logical_height" || return 1
    assert_native_chat_touch_target launcher "$launcher" "$target48" "$scale" "$density" || return 1
    assert_native_chat_rect_in_bounds history "$history" "$logical_width" "$logical_height" || return 1
    assert_native_chat_rect_contained history "$history" launcher "$launcher" || return 1
    assert_native_chat_touch_target history "$history" "$target48" "$scale" "$density" || return 1
    assert_native_chat_rect_in_bounds inline "$inline" "$logical_width" "$logical_height" || return 1
    assert_native_chat_rect_contained inline "$inline" launcher "$launcher" || return 1
    assert_native_chat_touch_target inline "$inline" "$target48" "$scale" "$density" || return 1

    if [[ "$ime" == "true" && "$inline_focused" == "true" ]]; then
        local launcher_x launcher_y launcher_width launcher_height
        read -r launcher_x launcher_y launcher_width launcher_height <<< "$launcher"
        if (( launcher_y + launcher_height > keyboard_top )); then
            echo "ERROR: native Android inline chat is covered by the IME: launcher=$launcher keyboardTop=$keyboard_top" >&2
            return 1
        fi
    fi

    filter_count="$(printf '%s\n' "$line" | tr ' ' '\n' | grep -c '^filter=' || true)"
    if [[ "$filter_count" != "5" ]]; then
        echo "ERROR: native Android chat reported $filter_count filters instead of 5: $line" >&2
        return 1
    fi

    if [[ "$expected_open" == "true" ]]; then
        if [[ "$inline_focused" != "false" ]]; then
            echo "ERROR: native Android history sheet cannot also own inline focus: $line" >&2
            return 1
        fi
        sheet="$(native_chat_layout_rect_from_line "$line" sheet positive)" || return 1
        composer="$(native_chat_layout_rect_from_line "$line" composer positive)" || return 1
        compose="$(native_chat_layout_rect_from_line "$line" compose positive)" || return 1
        entry="$(native_chat_layout_rect_from_line "$line" entry positive)" || return 1
        assert_native_chat_rect_in_bounds sheet "$sheet" "$logical_width" "$logical_height" || return 1
        for filter in "${filters[@]}"; do
            rect="$(native_chat_filter_rect_from_line "$line" "$filter" positive)" || return 1
            assert_native_chat_rect_in_bounds "filter-$filter" "$rect" "$logical_width" "$logical_height" || return 1
            assert_native_chat_rect_contained "filter-$filter" "$rect" sheet "$sheet" || return 1
            assert_native_chat_touch_target "filter-$filter" "$rect" "$target48" "$scale" "$density" || return 1
        done
        assert_native_chat_rect_in_bounds composer "$composer" "$logical_width" "$logical_height" || return 1
        assert_native_chat_rect_contained composer "$composer" sheet "$sheet" || return 1
        assert_native_chat_touch_target composer "$composer" "$target48" "$scale" "$density" || return 1
        assert_native_chat_rect_in_bounds compose "$compose" "$logical_width" "$logical_height" || return 1
        assert_native_chat_rect_contained compose "$compose" composer "$composer" || return 1
        assert_native_chat_touch_target compose "$compose" "$target48" "$scale" "$density" || return 1
        assert_native_chat_rect_in_bounds entry "$entry" "$logical_width" "$logical_height" || return 1
        assert_native_chat_rect_contained entry "$entry" composer "$composer" || return 1
        read -r entry_x entry_y entry_width entry_height <<< "$entry"
        if (( entry_height != 14 )); then
            echo "ERROR: native Android chat entry visual must remain 14 client px tall inside its composer target: $entry" >&2
            return 1
        fi
        read -r compose_x compose_y compose_width compose_height <<< "$compose"
		if [[ "$ime" == "true" ]] && (( compose_y + compose_height > keyboard_top )); then
			echo "ERROR: native Android chat composer is covered by the IME: composer=$composer keyboardTop=$keyboard_top" >&2
			return 1
		fi
        if rects_overlap "$entry_x" "$entry_y" "$entry_width" "$entry_height" \
            "$compose_x" "$compose_y" "$compose_width" "$compose_height"; then
            echo "ERROR: native Android chat entry overlaps its compose action: entry=$entry compose=$compose" >&2
            return 1
        fi
    else
        native_chat_layout_rect_from_line "$line" sheet zero >/dev/null || return 1
        for filter in "${filters[@]}"; do
            native_chat_filter_rect_from_line "$line" "$filter" zero >/dev/null || return 1
        done
        native_chat_layout_rect_from_line "$line" composer zero >/dev/null || return 1
        native_chat_layout_rect_from_line "$line" compose zero >/dev/null || return 1
        if [[ "$inline_focused" == "true" ]]; then
            entry="$(native_chat_layout_rect_from_line "$line" entry positive)" || return 1
            assert_native_chat_rect_in_bounds entry "$entry" "$logical_width" "$logical_height" || return 1
            assert_native_chat_rect_contained entry "$entry" inline "$inline" || return 1
        else
            native_chat_layout_rect_from_line "$line" entry zero >/dev/null || return 1
        fi
    fi

    echo "Verified native Android chat layout open=$open inlineFocused=$inline_focused channel=$channel ime=$ime with logged, in-bounds 48dp controls."
}

tap_native_chat_launcher_from_line() {
    local line="$1"
    local rect x y width height
    rect="$(native_chat_layout_rect_from_line "$line" history positive)" || return 1
    read -r x y width height <<< "$rect"
    echo "Android native chat history at client $((x + width / 2)),$((y + height / 2))"
    tap_client_xy $((x + width / 2)) $((y + height / 2))
}

native_chat_launcher_client_point_from_line() {
	local line="$1"
	local rect x y width height
	rect="$(native_chat_layout_rect_from_line "$line" history positive)" || return 1
	read -r x y width height <<< "$rect"
	echo "$((x + width / 2)) $((y + height / 2))"
}

tap_native_chat_inline_from_line() {
    local line="$1"
    local rect x y width height
    rect="$(native_chat_layout_rect_from_line "$line" inline positive)" || return 1
    read -r x y width height <<< "$rect"
    echo "Android native inline chat field at client $((x + width / 2)),$((y + height / 2))"
    tap_client_xy $((x + width / 2)) $((y + height / 2))
}

native_chat_rect_client_point_from_line() {
	local line="$1"
	local field="$2"
	local rect x y width height
	rect="$(native_chat_layout_rect_from_line "$line" "$field" positive)" || return 1
	read -r x y width height <<< "$rect"
	echo "$((x + width / 2)) $((y + height / 2))"
}

tap_native_chat_filter_from_line() {
    local line="$1"
    local filter="$2"
    local rect x y width height
    rect="$(native_chat_filter_rect_from_line "$line" "$filter" positive)" || return 1
    read -r x y width height <<< "$rect"
    echo "Android native chat filter $filter at client $((x + width / 2)),$((y + height / 2))"
    tap_client_xy $((x + width / 2)) $((y + height / 2))
}

tap_native_chat_rect_from_line() {
    local line="$1"
    local field="$2"
    local rect x y width height
    rect="$(native_chat_layout_rect_from_line "$line" "$field" positive)" || return 1
    read -r x y width height <<< "$rect"
    echo "Android native chat $field at client $((x + width / 2)),$((y + height / 2))"
    tap_client_xy $((x + width / 2)) $((y + height / 2))
}

chat_message_log_token() {
    printf "%s" "$1" | sed 's/[^A-Za-z0-9_.-]/_/g'
}

wait_for_chat_send() {
    local expected="$1"
    local timeout="${2:-20}"
    local expected_token deadline line
    expected_token="$(chat_message_log_token "$expected")"
    deadline=$((SECONDS + timeout))

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CHAT_SEND " | tail -1 || true)"
        if [[ "$line" =~ message=([^[:space:]]+) ]] && [[ "${BASH_REMATCH[1]}" == "$expected_token" ]]; then
            echo "Verified Android chat send: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android chat send '$expected_token'" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CHAT_SEND" | tail -30 >&2 || true
    return 1
}

assert_single_chat_send() {
    local expected="$1"
    local expected_token expected_length count line message length
    expected_token="$(chat_message_log_token "$expected")"
    expected_length="${#expected}"
    count="$("$ADB" logcat -d -v raw 2>/dev/null \
        | tr -d '\r' \
        | grep -c "ANDROID_SMOKE_CHAT_SEND " || true)"
    line="$("$ADB" logcat -d -v raw 2>/dev/null \
        | tr -d '\r' \
        | grep "ANDROID_SMOKE_CHAT_SEND " \
        | tail -1 || true)"
    message="$(extract_log_value "$line" message)"
    length="$(extract_log_value "$line" length)"
    if [[ "$count" != "1" || "$message" != "$expected_token" \
        || "$length" != "$expected_length" ]]; then
        echo "ERROR: expected exactly one native Android chat send for '$expected_token' (length=$expected_length); count=$count" >&2
        native_chat_log_tail
        return 1
    fi
    echo "Verified exactly one native Android chat send: $line"
}

extract_log_value() {
    local line="$1"
    local key="$2"
    printf "%s\n" "$line" | tr ' ' '\n' | sed -n "s/^${key}=//p" | tail -1
}

mobile_hub_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null \
        | tr -d '\r' \
        | grep -E "ANDROID_SMOKE_HUB_(LAYOUT|ACTION)" \
        | tail -60 >&2 || true
}

mobile_hub_expected_panel() {
    case "$1" in
        STATS) echo stats ;;
        MAP) echo map ;;
        SOCIAL) echo social ;;
        SETTINGS) echo settings ;;
        INVENTORY) echo inventory ;;
        MAGIC) echo magic ;;
        PRAYER) echo prayer ;;
        *)
            echo "ERROR: unknown Android mobile hub control '$1'" >&2
            return 1
            ;;
    esac
}

mobile_hub_expected_side() {
    case "$1" in
        STATS|MAP|SOCIAL|SETTINGS) echo left ;;
        INVENTORY|MAGIC|PRAYER) echo right ;;
        *)
            echo "ERROR: unknown Android mobile hub control '$1'" >&2
            return 1
            ;;
    esac
}

wait_for_mobile_hub_layout() {
    local expected_orientation="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line orientation logical generation

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null \
            | tr -d '\r' \
            | grep "ANDROID_SMOKE_HUB_LAYOUT " \
            | grep "orientation=$expected_orientation " \
            | tail -1 || true)"
        orientation="$(extract_log_value "$line" orientation)"
        logical="$(extract_log_value "$line" logical)"
        generation="$(extract_log_value "$line" generation)"
        if [[ "$orientation" == "$expected_orientation" \
            && "$logical" =~ ^[0-9]+x[0-9]+$ \
            && "$generation" =~ ^[0-9]+$ ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android mobile hub orientation=$expected_orientation" >&2
    mobile_hub_log_tail
    return 1
}

mobile_hub_rect_from_payload() {
    local label="$1"
    local payload="$2"
    local allow_empty="${3:-0}"
    local x y width height
    payload="${payload#<}"
    payload="${payload%>}"
    IFS=',' read -r x y width height <<< "$payload"
    if [[ ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ \
        || ! "$width" =~ ^[0-9]+$ || ! "$height" =~ ^[0-9]+$ ]]; then
        echo "ERROR: invalid Android mobile hub rect for $label: ${payload:-missing}" >&2
        return 1
    fi
    if [[ "$allow_empty" == "1" ]]; then
        if (( (width == 0) != (height == 0) )); then
            echo "ERROR: Android mobile hub $label has a partially empty rect: $payload" >&2
            return 1
        fi
    elif (( width <= 0 || height <= 0 )); then
        echo "ERROR: Android mobile hub $label must have positive dimensions: $payload" >&2
        return 1
    fi
    printf "%s %s %s %s\n" "$x" "$y" "$width" "$height"
}

mobile_hub_layout_rect_from_line() {
    local line="$1"
    local field="$2"
    local allow_empty="${3:-0}"
    local payload
    payload="$(extract_log_value "$line" "$field")"
    mobile_hub_rect_from_payload "$field" "$payload" "$allow_empty" || {
        echo "$line" >&2
        return 1
    }
}

mobile_hub_target_rect_from_line() {
    local line="$1"
    local key="$2"
    local payload
    payload="$(printf '%s\n' "$line" \
        | tr ' ' '\n' \
        | sed -nE "s/^target=<?${key}:([^>]*)>?$/\\1/p" \
        | tail -1)"
    mobile_hub_rect_from_payload "target-$key" "$payload" 0 || {
        echo "$line" >&2
        return 1
    }
}

mobile_hub_target_occurrences() {
    local line="$1"
    local key="$2"
    printf '%s\n' "$line" \
        | tr ' ' '\n' \
        | grep -Ec "^target=<?${key}:" || true
}

mobile_stats_subtab_expected_name() {
    case "$1" in
        STATS) echo stats ;;
        QUESTS) echo quests ;;
        LOOT) echo loot ;;
        BEASTS) echo beasts ;;
        *)
            echo "ERROR: unknown Android Stats subtab '$1'" >&2
            return 1
            ;;
    esac
}

mobile_stats_subtab_target_rect_from_line() {
    local line="$1"
    local key="$2"
    local payload
    payload="$(printf '%s\n' "$line" \
        | tr ' ' '\n' \
        | sed -nE "s/^playerInfoTarget=<?${key}:([^>]*)>?$/\\1/p" \
        | tail -1)"
    mobile_hub_rect_from_payload "player-info-$key" "$payload" 0 || {
        echo "$line" >&2
        return 1
    }
}

mobile_stats_subtab_target_occurrences() {
    local line="$1"
    local key="$2"
    printf '%s\n' "$line" \
        | tr ' ' '\n' \
        | grep -Ec "^playerInfoTarget=<?${key}:" || true
}

wait_for_mobile_stats_subtab() {
    local expected_key="$1"
    local timeout="${2:-15}"
    local expected_name deadline line active
    expected_name="$(mobile_stats_subtab_expected_name "$expected_key")" || return 1
    deadline=$((SECONDS + timeout))

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null \
            | tr -d '\r' \
            | grep "ANDROID_SMOKE_HUB_LAYOUT " \
            | grep "orientation=portrait " \
            | grep "panel=stats " \
            | tail -1 || true)"
        active="$(extract_log_value "$line" playerInfoSubTab)"
        if [[ "$active" == "$expected_name" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: Android Stats drawer did not select subtab=$expected_name" >&2
    mobile_hub_log_tail
    return 1
}

assert_mobile_stats_subtab_layout() {
    local line="$1"
    local viewport_line="$2"
    local expected_key="$3"
    local expected_name active panel side orientation touch48 scale density
    local player_info_tab_h quest_row_h bestiary_row_h bestiary_control_h
    local drawer_rect quest_viewport bestiary_field
    local drawer_x drawer_y drawer_width drawer_height
    local quest_x quest_y quest_width quest_height
    local field_x field_y field_width field_height
    local key occurrences rect x y width height seen_index seen_count=0
    local -a keys=(STATS QUESTS LOOT BEASTS)
    local -a seen_x=() seen_y=() seen_width=() seen_height=()

    expected_name="$(mobile_stats_subtab_expected_name "$expected_key")" || return 1
    active="$(extract_log_value "$line" playerInfoSubTab)"
    panel="$(extract_log_value "$line" panel)"
    side="$(extract_log_value "$line" panelSide)"
    orientation="$(extract_log_value "$line" orientation)"
    touch48="$(extract_log_value "$viewport_line" touch48)"
    scale="$(extract_log_value "$viewport_line" scale)"
    density="$(extract_log_value "$viewport_line" density)"
    player_info_tab_h="$(extract_log_value "$line" playerInfoTabH)"
    quest_row_h="$(extract_log_value "$line" questRowH)"
    bestiary_row_h="$(extract_log_value "$line" bestiaryRowH)"
    bestiary_control_h="$(extract_log_value "$line" bestiaryControlH)"

    if [[ "$orientation" != "portrait" || "$panel" != "stats" || "$side" != "left" \
        || "$active" != "$expected_name" ]]; then
        echo "ERROR: Android Stats subtab expected portrait stats/left/$expected_name: $line" >&2
        return 1
    fi
    if [[ ! "$touch48" =~ ^[0-9]+$ || "$touch48" -le 0 ]] \
        || ! is_positive_log_number "$scale" || ! is_positive_log_number "$density"; then
        echo "ERROR: Android Stats subtab needs valid physical viewport telemetry: $viewport_line" >&2
        return 1
    fi

    local metric value
    for metric in playerInfoTabH questRowH bestiaryRowH bestiaryControlH; do
        case "$metric" in
            playerInfoTabH) value="$player_info_tab_h" ;;
            questRowH) value="$quest_row_h" ;;
            bestiaryRowH) value="$bestiary_row_h" ;;
            bestiaryControlH) value="$bestiary_control_h" ;;
        esac
        if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value < touch48 )); then
            echo "ERROR: Android Stats $metric=$value is below touch48=$touch48" >&2
            return 1
        fi
        assert_client_rect_extent_at_least_dp "stats-$metric" height \
            "$value" "$scale" "$density" 48 || return 1
    done

    drawer_rect="$(mobile_hub_layout_rect_from_line "$line" drawer)" || return 1
    quest_viewport="$(mobile_hub_layout_rect_from_line "$line" questViewport)" || return 1
    bestiary_field="$(mobile_hub_layout_rect_from_line "$line" bestiaryField)" || return 1
    read -r drawer_x drawer_y drawer_width drawer_height <<< "$drawer_rect"
    read -r quest_x quest_y quest_width quest_height <<< "$quest_viewport"
    read -r field_x field_y field_width field_height <<< "$bestiary_field"

    if (( quest_x < drawer_x || quest_y < drawer_y \
        || quest_x + quest_width > drawer_x + drawer_width \
        || quest_y + quest_height > drawer_y + drawer_height \
        || quest_height < quest_row_h )); then
        echo "ERROR: Android Quest viewport is outside its drawer or cannot show one touch row: quest=$quest_viewport drawer=$drawer_rect" >&2
        return 1
    fi
    if (( field_x < drawer_x || field_y < drawer_y \
        || field_x + field_width > drawer_x + drawer_width \
        || field_y + field_height > drawer_y + drawer_height \
        || field_height != bestiary_control_h || field_height < bestiary_row_h )); then
        echo "ERROR: Android Bestiary field/control geometry is outside its drawer or below its row target: field=$bestiary_field drawer=$drawer_rect" >&2
        return 1
    fi
    assert_client_rect_extent_at_least_dp "stats-quest-viewport" width \
        "$quest_width" "$scale" "$density" 48 || return 1
    assert_client_rect_extent_at_least_dp "stats-bestiary-control" width \
        "$field_width" "$scale" "$density" 48 || return 1
    assert_client_rect_extent_at_least_dp "stats-bestiary-control" height \
        "$field_height" "$scale" "$density" 48 || return 1

    for key in "${keys[@]}"; do
        occurrences="$(mobile_stats_subtab_target_occurrences "$line" "$key")"
        if [[ "$occurrences" != "1" ]]; then
            echo "ERROR: Android Stats reported $occurrences target records for $key instead of 1" >&2
            return 1
        fi
        rect="$(mobile_stats_subtab_target_rect_from_line "$line" "$key")" || return 1
        read -r x y width height <<< "$rect"
        if (( x < drawer_x || y < drawer_y \
            || x + width > drawer_x + drawer_width \
            || y + height > drawer_y + drawer_height \
            || width < touch48 || height < touch48 \
            || height != player_info_tab_h )); then
            echo "ERROR: Android Stats $key target is outside the drawer or below 48dp: target=$rect drawer=$drawer_rect" >&2
            return 1
        fi
        for ((seen_index = 0; seen_index < seen_count; seen_index++)); do
            if rects_overlap "$x" "$y" "$width" "$height" \
                "${seen_x[seen_index]}" "${seen_y[seen_index]}" \
                "${seen_width[seen_index]}" "${seen_height[seen_index]}"; then
                echo "ERROR: Android Stats subtab targets overlap at $key" >&2
                return 1
            fi
        done
        seen_x[seen_count]="$x"
        seen_y[seen_count]="$y"
        seen_width[seen_count]="$width"
        seen_height[seen_count]="$height"
        seen_count=$((seen_count + 1))
        assert_client_rect_extent_at_least_dp "stats-subtab-$key" width \
            "$width" "$scale" "$density" 48 || return 1
        assert_client_rect_extent_at_least_dp "stats-subtab-$key" height \
            "$height" "$scale" "$density" 48 || return 1
    done

    echo "Verified native Android Stats subtab=$expected_name with drawer-contained, physical 48dp tabs/rows/controls."
}

tap_mobile_stats_subtab_from_layout() {
    local line="$1"
    local key="$2"
    local viewport_line="$3"
    local rect x y width height
    rect="$(mobile_stats_subtab_target_rect_from_line "$line" "$key")" || return 1
    read -r x y width height <<< "$rect"
    echo "Android Stats subtab $key at client $((x + width / 2)),$((y + height / 2))"
    tap_client_xy_from_viewport $((x + width / 2)) $((y + height / 2)) \
        "$viewport_line"
}

rects_overlap() {
    local ax="$1" ay="$2" aw="$3" ah="$4"
    local bx="$5" by="$6" bw="$7" bh="$8"
    (( ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by ))
}

assert_client_rect_extent_at_least_dp() {
    local label="$1"
    local dimension="$2"
    local client_pixels="$3"
    local scale="$4"
    local density="$5"
    local requested_dp="${6:-48}"
    awk -v label="$label" -v dimension="$dimension" -v pixels="$client_pixels" -v scale="$scale" \
        -v density="$density" -v requested="$requested_dp" 'BEGIN {
        achieved = pixels * scale / density;
        if (achieved + 0.05 < requested) {
            printf "ERROR: Android %s is only %.3fdp in %s (%d client px, scale=%s density=%s)\n", label, achieved, dimension, pixels, scale, density > "/dev/stderr";
            exit 1;
        }
        printf "Verified Android %s %s: clientPx=%d achievedDp=%.3f\n", label, dimension, pixels, achieved;
    }'
}

assert_mobile_hub_layout() {
    local line="$1"
    local viewport_line="$2"
    local expected_orientation="$3"
    local expected_panel="$4"
    local expected_side="$5"
    local orientation generation logical panel panel_side target48
    local inventory_columns inventory_rows magic_list_h magic_row_h stats_row_h
    local player_info_tab_h social_tab_h settings_tab_h expected_inventory_columns expected_inventory_rows expected_magic_rows
    local logical_width logical_height viewport_width viewport_height touch48 scale density
    local left_rect right_rect chat_rect drawer_rect connector_rect
    local left_x left_y left_width left_height right_x right_y right_width right_height
    local chat_x chat_y chat_width chat_height drawer_x drawer_y drawer_width drawer_height
    local connector_x connector_y connector_width connector_height anchor_key anchor_center_y
    local key expected_control_side rail_x rail_y rail_width rail_height rect x y width height
    local seen_index seen_count=0 target_count=0 occurrences adjacency_limit gap
    local -a keys=(STATS MAP SOCIAL SETTINGS INVENTORY MAGIC PRAYER)
    local -a seen_x=() seen_y=() seen_width=() seen_height=()

    orientation="$(extract_log_value "$line" orientation)"
    generation="$(extract_log_value "$line" generation)"
    logical="$(extract_log_value "$line" logical)"
    panel="$(extract_log_value "$line" panel)"
    panel_side="$(extract_log_value "$line" panelSide)"
    target48="$(extract_log_value "$line" target48)"
    inventory_columns="$(extract_log_value "$line" inventoryColumns)"
    inventory_rows="$(extract_log_value "$line" inventoryRows)"
    magic_list_h="$(extract_log_value "$line" magicListH)"
    magic_row_h="$(extract_log_value "$line" magicRowH)"
    stats_row_h="$(extract_log_value "$line" statsRowH)"
    player_info_tab_h="$(extract_log_value "$line" playerInfoTabH)"
    social_tab_h="$(extract_log_value "$line" socialTabH)"
    settings_tab_h="$(extract_log_value "$line" settingsTabH)"
    IFS='x' read -r logical_width logical_height <<< "$logical"
    viewport_width="$(extract_log_value "$viewport_line" logicalW)"
    viewport_height="$(extract_log_value "$viewport_line" logicalH)"
    touch48="$(extract_log_value "$viewport_line" touch48)"
    scale="$(extract_log_value "$viewport_line" scale)"
    density="$(extract_log_value "$viewport_line" density)"

    if [[ "$orientation" != "$expected_orientation" || ! "$generation" =~ ^[0-9]+$ \
        || ! "$logical_width" =~ ^[0-9]+$ || ! "$logical_height" =~ ^[0-9]+$ ]]; then
        echo "ERROR: unexpected Android mobile hub orientation/generation/logical size: $line" >&2
        return 1
    fi
    if [[ "$panel" != "$expected_panel" || "$panel_side" != "$expected_side" ]]; then
        echo "ERROR: Android mobile hub expected panel=$expected_panel panelSide=$expected_side: $line" >&2
        return 1
    fi
    if (( logical_width <= 0 || logical_height <= 0 )) \
        || [[ ! "$viewport_width" =~ ^[0-9]+$ || ! "$viewport_height" =~ ^[0-9]+$ \
            || ! "$touch48" =~ ^[0-9]+$ || "$touch48" -le 0 ]]; then
        echo "ERROR: Android mobile hub/viewport telemetry has an invalid dimension" >&2
        echo "$line" >&2
        echo "$viewport_line" >&2
        return 1
    fi
    if ! is_positive_log_number "$scale" || ! is_positive_log_number "$density"; then
        echo "ERROR: Android mobile hub needs positive viewport scale/density: $viewport_line" >&2
        return 1
    fi
    if (( logical_width != viewport_width || logical_height != viewport_height )); then
        echo "ERROR: Android mobile hub/client viewport mismatch: hub=${logical_width}x${logical_height} viewport=${viewport_width}x${viewport_height}" >&2
        return 1
    fi
    adjacency_limit="$touch48"
    if [[ -n "$target48" ]]; then
        if [[ ! "$target48" =~ ^[0-9]+$ || "$target48" -le 0 ]]; then
            echo "ERROR: Android mobile hub target48 metadata is invalid: $target48" >&2
            return 1
        fi
        if (( target48 != touch48 )); then
            echo "ERROR: Android mobile hub target48=$target48 differs from viewport touch48=$touch48" >&2
            return 1
        fi
        adjacency_limit="$target48"
    fi
    if [[ "$expected_orientation" == "portrait" ]]; then
        expected_inventory_columns=5
        expected_inventory_rows=6
        expected_magic_rows=4
    else
        expected_inventory_columns=6
        expected_inventory_rows=5
        expected_magic_rows=3
    fi
    if [[ "$inventory_columns" != "$expected_inventory_columns" \
        || "$inventory_rows" != "$expected_inventory_rows" ]]; then
        echo "ERROR: Android $expected_orientation inventory expected ${expected_inventory_columns}x${expected_inventory_rows}, got ${inventory_columns}x${inventory_rows}" >&2
        return 1
    fi
    local mobile_dimension mobile_value
    for mobile_dimension in magicRowH statsRowH playerInfoTabH socialTabH settingsTabH; do
        case "$mobile_dimension" in
            magicRowH) mobile_value="$magic_row_h" ;;
            statsRowH) mobile_value="$stats_row_h" ;;
            playerInfoTabH) mobile_value="$player_info_tab_h" ;;
            socialTabH) mobile_value="$social_tab_h" ;;
            settingsTabH) mobile_value="$settings_tab_h" ;;
        esac
        if [[ ! "$mobile_value" =~ ^[0-9]+$ ]] || (( mobile_value < touch48 )); then
            echo "ERROR: Android hub $mobile_dimension=$mobile_value is below touch48=$touch48" >&2
            return 1
        fi
    done
    if [[ ! "$magic_list_h" =~ ^[0-9]+$ ]] \
        || (( magic_list_h != magic_row_h * expected_magic_rows )); then
        echo "ERROR: Android $expected_orientation Magic/Prayer viewport expected $expected_magic_rows touch rows, got listH=$magic_list_h rowH=$magic_row_h" >&2
        return 1
    fi

    left_rect="$(mobile_hub_layout_rect_from_line "$line" left)" || return 1
    right_rect="$(mobile_hub_layout_rect_from_line "$line" right)" || return 1
    chat_rect="$(mobile_hub_layout_rect_from_line "$line" chat)" || return 1
    drawer_rect="$(mobile_hub_layout_rect_from_line "$line" drawer 1)" || return 1
    connector_rect="$(mobile_hub_layout_rect_from_line "$line" connector 1)" || return 1
    read -r left_x left_y left_width left_height <<< "$left_rect"
    read -r right_x right_y right_width right_height <<< "$right_rect"
    read -r chat_x chat_y chat_width chat_height <<< "$chat_rect"
    read -r drawer_x drawer_y drawer_width drawer_height <<< "$drawer_rect"
    read -r connector_x connector_y connector_width connector_height <<< "$connector_rect"

    local layout_label layout_x layout_y layout_width layout_height
    for layout_label in left right chat; do
        case "$layout_label" in
            left) read -r layout_x layout_y layout_width layout_height <<< "$left_rect" ;;
            right) read -r layout_x layout_y layout_width layout_height <<< "$right_rect" ;;
            chat) read -r layout_x layout_y layout_width layout_height <<< "$chat_rect" ;;
        esac
        if (( layout_x + layout_width > logical_width || layout_y + layout_height > logical_height )); then
            echo "ERROR: Android mobile hub $layout_label is outside logical bounds: $layout_x $layout_y $layout_width $layout_height" >&2
            return 1
        fi
    done
    if rects_overlap "$left_x" "$left_y" "$left_width" "$left_height" \
        "$right_x" "$right_y" "$right_width" "$right_height"; then
        echo "ERROR: Android mobile hub left and right rails overlap" >&2
        return 1
    fi

    for key in "${keys[@]}"; do
        occurrences="$(mobile_hub_target_occurrences "$line" "$key")"
        if [[ "$occurrences" != "1" ]]; then
            echo "ERROR: Android mobile hub reported $occurrences target records for $key instead of 1" >&2
            return 1
        fi
        target_count=$((target_count + occurrences))
        expected_control_side="$(mobile_hub_expected_side "$key")" || return 1
        if [[ "$expected_control_side" == "left" ]]; then
            rail_x="$left_x"
            rail_y="$left_y"
            rail_width="$left_width"
            rail_height="$left_height"
        else
            rail_x="$right_x"
            rail_y="$right_y"
            rail_width="$right_width"
            rail_height="$right_height"
        fi
        rect="$(mobile_hub_target_rect_from_line "$line" "$key")" || return 1
        read -r x y width height <<< "$rect"
        if (( x < rail_x || y < rail_y || x + width > rail_x + rail_width \
            || y + height > rail_y + rail_height || x + width > logical_width \
            || y + height > logical_height )); then
            echo "ERROR: Android mobile hub $key is outside its $expected_control_side rail/logical bounds: $rect" >&2
            return 1
        fi
        for ((seen_index = 0; seen_index < seen_count; seen_index++)); do
            if rects_overlap "$x" "$y" "$width" "$height" \
                "${seen_x[seen_index]}" "${seen_y[seen_index]}" \
                "${seen_width[seen_index]}" "${seen_height[seen_index]}"; then
                echo "ERROR: Android mobile hub targets overlap at $key" >&2
                return 1
            fi
        done
        if rects_overlap "$x" "$y" "$width" "$height" \
            "$chat_x" "$chat_y" "$chat_width" "$chat_height"; then
            echo "ERROR: Android mobile hub $key overlaps the chat control" >&2
            return 1
        fi
        seen_x[seen_count]="$x"
        seen_y[seen_count]="$y"
        seen_width[seen_count]="$width"
        seen_height[seen_count]="$height"
        seen_count=$((seen_count + 1))
        if (( width < touch48 || height < touch48 )); then
            echo "ERROR: Android mobile hub $key ${width}x${height} is below touch48=$touch48 on one axis" >&2
            return 1
        fi
        assert_client_rect_extent_at_least_dp "hub-$key" width "$width" "$scale" "$density" 48 || return 1
        assert_client_rect_extent_at_least_dp "hub-$key" height "$height" "$scale" "$density" 48 || return 1
    done

    if (( target_count != 7 )); then
        echo "ERROR: Android mobile hub reported $target_count required targets instead of 7" >&2
        return 1
    fi
    occurrences="$(printf '%s\n' "$line" | tr ' ' '\n' | grep -Ec '^target=' || true)"
    if [[ "$occurrences" != "7" ]]; then
        echo "ERROR: Android mobile hub reported $occurrences total target records instead of 7" >&2
        return 1
    fi

    if [[ "$expected_panel" == "none" ]]; then
        if [[ "$expected_side" != "none" || "$drawer_width" != "0" || "$drawer_height" != "0" \
            || "$connector_width" != "0" || "$connector_height" != "0" ]]; then
            echo "ERROR: Android mobile hub closed state must use panelSide=none and empty drawer/connector rects: $line" >&2
            return 1
        fi
    else
        if (( drawer_width <= 0 || drawer_height <= 0 \
            || drawer_x + drawer_width > logical_width \
            || drawer_y + drawer_height > logical_height )); then
            echo "ERROR: Android mobile hub open drawer is outside logical bounds: $drawer_rect" >&2
            return 1
        fi
        if [[ "$expected_side" == "left" ]]; then
            if (( drawer_x < left_x + left_width )); then
                echo "ERROR: Android mobile hub left drawer overlaps or opens outside its left rail: $drawer_rect" >&2
                return 1
            fi
            gap=$((drawer_x - (left_x + left_width)))
        elif [[ "$expected_side" == "right" ]]; then
            if (( drawer_x + drawer_width > right_x )); then
                echo "ERROR: Android mobile hub right drawer overlaps or opens outside its right rail: $drawer_rect" >&2
                return 1
            fi
            gap=$((right_x - (drawer_x + drawer_width)))
        else
            echo "ERROR: Android mobile hub open panel has invalid side '$expected_side'" >&2
            return 1
        fi
        if (( gap < 0 || gap > adjacency_limit )); then
            echo "ERROR: Android mobile hub $expected_side drawer gap=$gap is not adjacent (limit=$adjacency_limit)" >&2
            return 1
        fi
        case "$expected_panel" in
            stats) anchor_key=STATS ;;
            map) anchor_key=MAP ;;
            social) anchor_key=SOCIAL ;;
            settings) anchor_key=SETTINGS ;;
            inventory) anchor_key=INVENTORY ;;
            magic) anchor_key=MAGIC ;;
            prayer) anchor_key=PRAYER ;;
            *) echo "ERROR: Android mobile hub has unknown open panel '$expected_panel'" >&2; return 1 ;;
        esac
        rect="$(mobile_hub_target_rect_from_line "$line" "$anchor_key")" || return 1
        read -r x y width height <<< "$rect"
        anchor_center_y=$((y + height / 2))
        if (( anchor_center_y < drawer_y || anchor_center_y >= drawer_y + drawer_height )); then
            echo "ERROR: Android mobile hub $expected_panel drawer is detached vertically from $anchor_key: drawer=$drawer_rect target=$rect" >&2
            return 1
        fi
        if (( connector_width <= 0 || connector_height <= 0 \
            || connector_x < 0 || connector_y < drawer_y \
            || connector_x + connector_width > logical_width \
            || connector_y + connector_height > drawer_y + drawer_height )); then
            echo "ERROR: Android mobile hub connector is missing/outside drawer bounds: $connector_rect drawer=$drawer_rect" >&2
            return 1
        fi
        if [[ "$expected_side" == "left" ]]; then
            if (( connector_x > left_x + left_width \
                || connector_x + connector_width < drawer_x )); then
                echo "ERROR: Android left drawer connector does not bridge rail and drawer: $connector_rect" >&2
                return 1
            fi
        else
            if (( connector_x > drawer_x + drawer_width \
                || connector_x + connector_width < right_x )); then
                echo "ERROR: Android right drawer connector does not bridge drawer and rail: $connector_rect" >&2
                return 1
            fi
        fi
        if [[ "$expected_orientation" == "portrait" ]] \
            && (( drawer_height * 100 > logical_height * 70 )); then
            echo "ERROR: Android portrait $expected_panel drawer is an overlong skyscraper: $drawer_height/$logical_height" >&2
            return 1
        fi
        if [[ "$expected_orientation" == "landscape" ]] \
            && (( drawer_height * 100 > logical_height * 82 )); then
            echo "ERROR: Android landscape $expected_panel drawer consumes too much height: $drawer_height/$logical_height" >&2
            return 1
        fi
    fi

    echo "Verified Android $expected_orientation split-rail hub geometry, seven controls, and panel=$expected_panel/$expected_side."
}

tap_mobile_hub_control_from_layout() {
    local line="$1"
    local key="$2"
    local viewport_line="$3"
    local rect x y width height
    rect="$(mobile_hub_target_rect_from_line "$line" "$key")" || return 1
    read -r x y width height <<< "$rect"
    echo "Android mobile hub $key at client $((x + width / 2)),$((y + height / 2))"
    # Keep the entire focused assertion on the renderer's settled viewport.
    # Some emulators briefly return the previous orientation from screencap
    # between landscape frames, which must not redirect a rail tap.
    tap_client_xy_from_viewport $((x + width / 2)) $((y + height / 2)) \
        "$viewport_line"
}

mobile_hub_control_client_point_from_layout() {
	local line="$1"
	local key="$2"
	local rect x y width height
	rect="$(mobile_hub_target_rect_from_line "$line" "$key")" || return 1
	read -r x y width height <<< "$rect"
	echo "$((x + width / 2)) $((y + height / 2))"
}

tap_mobile_hub_connector_from_layout() {
    local line="$1"
    local viewport_line="$2"
    local rect x y width height
    rect="$(mobile_hub_layout_rect_from_line "$line" connector)" || return 1
    read -r x y width height <<< "$rect"
    echo "Android mobile hub connector at client $((x + width / 2)),$((y + height / 2))"
    tap_client_xy_from_viewport $((x + width / 2)) $((y + height / 2)) \
        "$viewport_line"
}

assert_no_mobile_hub_connector_fallthrough() {
    local label="$1"
    local timeout="${2:-2}"
    local deadline=$((SECONDS + timeout))
    local line
    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
            | grep -E 'ANDROID_SMOKE_(WALK_ACTION|CHAT_TAB|HUB_ACTION)' \
            | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "ERROR: Android $label connector tap fell through or changed destination: $line" >&2
            return 1
        fi
        sleep 1
    done
    echo "Verified Android $label connector consumes its full rail-to-drawer bridge."
}

swipe_mobile_hub_drawer_up() {
    local line="$1"
    local rect x y width height
    rect="$(mobile_hub_layout_rect_from_line "$line" drawer)" || return 1
    read -r x y width height <<< "$rect"
    swipe_client_xy $((x + width / 2)) $((y + height * 2 / 3)) \
        $((x + width / 2)) $((y + height / 3)) 700
}

wait_for_mobile_hub_stats_scroll() {
    local expected_orientation="$1"
    local timeout="${2:-15}"
    local deadline=$((SECONDS + timeout))
    local line scroll
    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
            | grep "ANDROID_SMOKE_HUB_LAYOUT " \
            | grep "orientation=$expected_orientation " \
            | grep "panel=stats " | tail -1 || true)"
        scroll="$(extract_log_value "$line" statsScroll)"
        if [[ "$scroll" =~ ^[0-9]+$ ]] && (( scroll > 0 )); then
            echo "Verified Android $expected_orientation Stats drawer swipe scroll=$scroll"
            return 0
        fi
        sleep 1
    done
    echo "ERROR: Android $expected_orientation Stats drawer did not scroll" >&2
    mobile_hub_log_tail
    return 1
}

wait_for_mobile_hub_action() {
    local expected_key="$1"
    local expected_panel="$2"
    local expected_side="$3"
    local timeout="${4:-15}"
    local deadline=$((SECONDS + timeout))
    local line control panel side

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null \
            | tr -d '\r' \
            | grep "ANDROID_SMOKE_HUB_ACTION control=$expected_key " \
            | tail -1 || true)"
        if [[ -n "$line" ]]; then
            control="$(extract_log_value "$line" control)"
            panel="$(extract_log_value "$line" panel)"
            side="$(extract_log_value "$line" side)"
            if [[ "$control" == "$expected_key" && "$panel" == "$expected_panel" \
                && "$side" == "$expected_side" ]]; then
                echo "$line"
                return 0
            fi
            echo "ERROR: Android mobile hub action did not match its expected state" >&2
            echo "expected control=$expected_key panel=$expected_panel side=$expected_side" >&2
            echo "$line" >&2
            return 1
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android mobile hub action control=$expected_key" >&2
    mobile_hub_log_tail
    return 1
}

wait_for_mobile_hub_panel() {
    local expected_orientation="$1"
    local expected_panel="$2"
    local expected_side="$3"
    local timeout="${4:-12}"
    local deadline=$((SECONDS + timeout))
    local line panel side
    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null \
            | tr -d '\r' \
            | grep "ANDROID_SMOKE_HUB_LAYOUT " \
            | grep "orientation=$expected_orientation " \
            | tail -1 || true)"
        panel="$(extract_log_value "$line" panel)"
        side="$(extract_log_value "$line" panelSide)"
        if [[ "$panel" == "$expected_panel" && "$side" == "$expected_side" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done
    echo "ERROR: Android mobile hub did not settle panel=$expected_panel panelSide=$expected_side" >&2
    mobile_hub_log_tail
    return 1
}

assert_no_mobile_hub_fallthrough() {
    local expected_key="$1"
    local timeout="${2:-2}"
    local deadline=$((SECONDS + timeout))
    local walk_line chat_line action_count
    while (( SECONDS < deadline )); do
        walk_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WALK_ACTION " | tail -1 || true)"
        chat_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CHAT_TAB " | tail -1 || true)"
        if [[ -n "$walk_line" || -n "$chat_line" ]]; then
            echo "ERROR: Android mobile hub $expected_key tap fell through" >&2
            [[ -n "$walk_line" ]] && echo "$walk_line" >&2
            [[ -n "$chat_line" ]] && echo "$chat_line" >&2
            return 1
        fi
        sleep 1
    done
    action_count="$("$ADB" logcat -d -v raw 2>/dev/null \
        | tr -d '\r' \
        | grep -c "ANDROID_SMOKE_HUB_ACTION control=$expected_key " || true)"
    if [[ "$action_count" != "1" ]]; then
        echo "ERROR: Android mobile hub $expected_key tap logged $action_count actions instead of 1" >&2
        mobile_hub_log_tail
        return 1
    fi
    echo "Verified Android mobile hub $expected_key consumed one tap without world/chat fallthrough."
}

assert_no_mobile_hub_world_or_chat_fallthrough() {
	local expected_key="$1"
	local timeout="${2:-2}"
	local deadline=$((SECONDS + timeout))
	local walk_line chat_line
	while (( SECONDS < deadline )); do
		walk_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep 'ANDROID_SMOKE_WALK_ACTION ' | tail -1 || true)"
		chat_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep 'ANDROID_SMOKE_CHAT_TAB ' | tail -1 || true)"
		if [[ -n "$walk_line" || -n "$chat_line" ]]; then
			echo "ERROR: Android mobile hub $expected_key reconnect intent fell through" >&2
			[[ -n "$walk_line" ]] && echo "$walk_line" >&2
			[[ -n "$chat_line" ]] && echo "$chat_line" >&2
			return 1
		fi
		sleep 1
	done
	echo "Verified Android mobile hub $expected_key reconnect intent had no world/chat fallthrough."
}

assert_no_android_walk_action_logged() {
    local timeout="${1:-2}"
    local deadline=$((SECONDS + timeout))
    local line
    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WALK_ACTION " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "ERROR: Android UI tap fell through to a world walk: $line" >&2
            return 1
        fi
        sleep 1
    done
    echo "Verified Android UI tap did not fall through to a world walk."
}

mobile_viewport_log_tail() {
	"$ADB" logcat -d -v raw 2>/dev/null \
		| tr -d '\r' \
		| grep "ANDROID_MOBILE_VIEWPORT " \
		| tail -30 >&2 || true
}

is_positive_log_number() {
	local value="$1"
	awk -v value="$value" 'BEGIN {
		if (value !~ /^([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$/) exit 1;
		exit !((value + 0) > 0);
	}'
}

android_mobile_viewport_log_matches_orientation() {
	local expected_orientation="$1"
	local line="$2"
	local content_width content_height logical_width logical_height

	content_width="$(extract_log_value "$line" contentW)"
	content_height="$(extract_log_value "$line" contentH)"
	logical_width="$(extract_log_value "$line" logicalW)"
	logical_height="$(extract_log_value "$line" logicalH)"
	[[ "$content_width" =~ ^[0-9]+$ && "$content_width" -gt 0 \
		&& "$content_height" =~ ^[0-9]+$ && "$content_height" -gt 0 \
		&& "$logical_width" =~ ^[0-9]+$ && "$logical_width" -gt 0 \
		&& "$logical_height" =~ ^[0-9]+$ && "$logical_height" -gt 0 ]] || return 1

	if [[ "$expected_orientation" == "portrait" ]]; then
		(( content_height > content_width && logical_height > logical_width ))
	else
		(( content_width > content_height && logical_width > logical_height ))
	fi
}

wait_for_android_mobile_viewport() {
	local expected_orientation="$1"
	local timeout="${2:-30}"
	local deadline=$((SECONDS + timeout))
	local matching_line surface_width surface_height

	while (( SECONDS < deadline )); do
		read -r surface_width surface_height < <(screen_size)
		matching_line="$(latest_settled_android_mobile_viewport \
			"$surface_width" "$surface_height" || true)"
		if [[ -n "$matching_line" ]] \
			&& android_mobile_viewport_log_matches_orientation "$expected_orientation" "$matching_line"; then
			cache_android_mobile_viewport_line "$matching_line"
			printf '%s\n' "$matching_line"
			return 0
		fi
		sleep 1
	done

	echo "ERROR: timed out waiting for renderer-reported Android $expected_orientation mobile viewport" >&2
	mobile_viewport_log_tail
	return 1
}

assert_android_touch_target_conversion() {
	local requested_dp="$1"
	local client_pixels="$2"
	local scale="$3"
	local density="$4"

	awk -v requested="$requested_dp" -v pixels="$client_pixels" -v scale="$scale" -v density="$density" 'BEGIN {
		# Java uses ceil(dp * density / scale). Allow a small tolerance only for
		# decimal float logging at an exact integer boundary.
		tolerance = 0.05;
		clientPixelDp = scale / density;
		achieved = pixels * clientPixelDp;
		previous = (pixels - 1) * clientPixelDp;
		if (achieved + tolerance < requested) {
			printf "ERROR: Android %ddp target converts to only %.3fdp (%d client px, scale=%s density=%s)\n", requested, achieved, pixels, scale, density > "/dev/stderr";
			exit 1;
		}
		if (pixels > 1 && previous >= requested + tolerance) {
			printf "ERROR: Android %ddp target conversion is not minimally rounded up (%d client px, previous=%.3fdp)\n", requested, pixels, previous > "/dev/stderr";
			exit 1;
		}
		printf "Verified Android %ddp target: clientPx=%d achievedDp=%.3f\n", requested, pixels, achieved;
	}'
}

assert_android_mobile_viewport_from_log() {
	local expected_orientation="$1"
	local line="$2"
	local client_line="$3"
	local surface_width surface_height content_width content_height
	local inset_left inset_top inset_right inset_bottom logical_width logical_height
	local scale density touch44 touch48 client_width client_game_height client_full_height
	local expected_content_width expected_content_height inset_total

	surface_width="$(extract_log_value "$line" surfaceW)"
	surface_height="$(extract_log_value "$line" surfaceH)"
	content_width="$(extract_log_value "$line" contentW)"
	content_height="$(extract_log_value "$line" contentH)"
	inset_left="$(extract_log_value "$line" insetL)"
	inset_top="$(extract_log_value "$line" insetT)"
	inset_right="$(extract_log_value "$line" insetR)"
	inset_bottom="$(extract_log_value "$line" insetB)"
	logical_width="$(extract_log_value "$line" logicalW)"
	logical_height="$(extract_log_value "$line" logicalH)"
	scale="$(extract_log_value "$line" scale)"
	density="$(extract_log_value "$line" density)"
	touch44="$(extract_log_value "$line" touch44)"
	touch48="$(extract_log_value "$line" touch48)"

	if [[ ! "$surface_width" =~ ^[0-9]+$ || "$surface_width" -le 0 \
		|| ! "$surface_height" =~ ^[0-9]+$ || "$surface_height" -le 0 \
		|| ! "$content_width" =~ ^[0-9]+$ || "$content_width" -le 0 \
		|| ! "$content_height" =~ ^[0-9]+$ || "$content_height" -le 0 \
		|| ! "$logical_width" =~ ^[0-9]+$ || "$logical_width" -le 0 \
		|| ! "$logical_height" =~ ^[0-9]+$ || "$logical_height" -le 0 \
		|| ! "$touch44" =~ ^[0-9]+$ || "$touch44" -le 0 \
		|| ! "$touch48" =~ ^[0-9]+$ || "$touch48" -le 0 ]]; then
		echo "ERROR: Android mobile viewport log has missing/non-positive dimensions or touch targets: $line" >&2
		mobile_viewport_log_tail
		return 1
	fi
	if [[ ! "$inset_left" =~ ^[0-9]+$ || ! "$inset_top" =~ ^[0-9]+$ \
		|| ! "$inset_right" =~ ^[0-9]+$ || ! "$inset_bottom" =~ ^[0-9]+$ ]]; then
		echo "ERROR: Android mobile viewport log has missing/negative insets: $line" >&2
		mobile_viewport_log_tail
		return 1
	fi
	if ! is_positive_log_number "$scale" || ! is_positive_log_number "$density"; then
		echo "ERROR: Android mobile viewport log has non-positive scale/density: $line" >&2
		mobile_viewport_log_tail
		return 1
	fi

	expected_content_width=$((surface_width - inset_left - inset_right))
	expected_content_height=$((surface_height - inset_top - inset_bottom))
	(( expected_content_width < 1 )) && expected_content_width=1
	(( expected_content_height < 1 )) && expected_content_height=1
	if [[ "$content_width" -ne "$expected_content_width" || "$content_height" -ne "$expected_content_height" ]]; then
		echo "ERROR: Android safe content ${content_width}x${content_height} does not match surface ${surface_width}x${surface_height} minus insets ${inset_left},${inset_top},${inset_right},${inset_bottom}" >&2
		return 1
	fi

	if ! android_mobile_viewport_log_matches_orientation "$expected_orientation" "$line"; then
		echo "ERROR: renderer-reported Android viewport does not match expected $expected_orientation orientation: $line" >&2
		return 1
	fi

	client_width="$(extract_log_value "$client_line" gameWidth)"
	client_game_height="$(extract_log_value "$client_line" gameHeight)"
	if [[ ! "$client_width" =~ ^[0-9]+$ || ! "$client_game_height" =~ ^[0-9]+$ ]]; then
		echo "ERROR: Android client settings log did not include its current viewport: $client_line" >&2
		return 1
	fi
	client_full_height=$((client_game_height + 12))
	if [[ "$logical_width" -ne "$client_width" || "$logical_height" -ne "$client_full_height" ]]; then
		echo "ERROR: Android renderer/client viewport mismatch: renderer=${logical_width}x${logical_height} client=${client_width}x${client_full_height}" >&2
		return 1
	fi

	inset_total=$((inset_left + inset_top + inset_right + inset_bottom))
	if [[ "$REQUIRE_NONZERO_INSET" == "1" && "$inset_total" -eq 0 ]]; then
		echo "ERROR: ANDROID_SMOKE_REQUIRE_NONZERO_INSET=1 but renderer reported no safe-area inset: $line" >&2
		return 1
	fi

	assert_android_touch_target_conversion 44 "$touch44" "$scale" "$density" || return 1
	assert_android_touch_target_conversion 48 "$touch48" "$scale" "$density" || return 1
	echo "Verified Android $expected_orientation mobile viewport: surface=${surface_width}x${surface_height} content=${content_width}x${content_height} insets=${inset_left},${inset_top},${inset_right},${inset_bottom} logical=${logical_width}x${logical_height} scale=$scale density=$density"
}

log_int_or_default() {
    local line="$1"
    local key="$2"
    local default="$3"
    local value
    value="$(extract_log_value "$line" "$key")"
    if [[ "$value" =~ ^-?[0-9]+$ ]]; then
        echo "$value"
    else
        echo "$default"
    fi
}

android_viewport_log_matches() {
    local expected_orientation="$1"
    local line="$2"
    local target_width game_height target_full_height

    target_width="$(extract_log_value "$line" gameWidth)"
    game_height="$(extract_log_value "$line" gameHeight)"
    [[ "$target_width" =~ ^[0-9]+$ && "$game_height" =~ ^[0-9]+$ ]] || return 1

    target_full_height=$((game_height + 12))
    if [[ "$expected_orientation" == "portrait" ]]; then
        (( target_full_height > target_width ))
    else
        (( target_width > target_full_height ))
    fi
}

wait_for_viewport_settings_state() {
    local expected_orientation="$1"
    local expected_camera="$2"
    local expected_mouse="$3"
    local timeout="${4:-30}"
    local deadline=$((SECONDS + timeout))
    local line visible setting_tab camera_auto mouse_one

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SETTINGS " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        setting_tab="$(extract_log_value "$line" settingTab)"
        camera_auto="$(extract_log_value "$line" cameraAuto)"
        mouse_one="$(extract_log_value "$line" mouseOne)"
        if [[ "$visible" == "true" && "$setting_tab" == "0" \
            && "$camera_auto" == "$expected_camera" && "$mouse_one" == "$expected_mouse" ]] \
            && android_viewport_log_matches "$expected_orientation" "$line"; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android settings viewport orientation=$expected_orientation" >&2
    settings_log_tail
    return 1
}

ground_loot_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_GROUND_LOOT" | tail -60 >&2 || true
}

wait_for_ground_loot_drop() {
    local expected_id="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line id

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_GROUND_LOOT event=DROP_KEY " | tail -1 || true)"
        id="$(extract_log_value "$line" id)"
        if [[ "$id" == "$expected_id" ]]; then
            echo "Verified Android ground-loot drop: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android ground-loot drop id=$expected_id" >&2
    ground_loot_log_tail
    return 1
}

wait_for_ground_loot_label() {
    local expected_id="$1"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))
    local line event id label_x label_y display_width game_width game_height

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_GROUND_LOOT event=LABEL " | tail -1 || true)"
        event="$(extract_log_value "$line" event)"
        id="$(extract_log_value "$line" id)"
        label_x="$(extract_log_value "$line" labelX)"
        label_y="$(extract_log_value "$line" labelY)"
        display_width="$(extract_log_value "$line" displayWidth)"
        game_width="$(extract_log_value "$line" gameWidth)"
        game_height="$(extract_log_value "$line" gameHeight)"
        if [[ "$event" == "LABEL" && "$id" == "$expected_id" \
            && "$label_x" =~ ^-?[0-9]+$ && "$label_y" =~ ^-?[0-9]+$ \
            && "$display_width" =~ ^[0-9]+$ && "$game_width" =~ ^[0-9]+$ && "$game_height" =~ ^[0-9]+$ \
            && "$display_width" -gt 0 \
            && "$label_x" -ge 0 && $((label_x + display_width)) -le "$game_width" \
            && "$label_y" -ge 0 && "$label_y" -lt "$game_height" ]]; then
            echo "Verified Android ground-item label: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for readable Android ground-item label id=$expected_id" >&2
    ground_loot_log_tail
    return 1
}

wait_for_ground_loot_beam() {
    local expected_id="$1"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))
    local line event id base_x base_y top_x top_y game_width game_height rare

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_GROUND_LOOT event=BEAM " | tail -1 || true)"
        event="$(extract_log_value "$line" event)"
        id="$(extract_log_value "$line" id)"
        base_x="$(extract_log_value "$line" baseX)"
        base_y="$(extract_log_value "$line" baseY)"
        top_x="$(extract_log_value "$line" topX)"
        top_y="$(extract_log_value "$line" topY)"
        game_width="$(extract_log_value "$line" gameWidth)"
        game_height="$(extract_log_value "$line" gameHeight)"
        rare="$(extract_log_value "$line" rare)"
        if [[ "$event" == "BEAM" && "$id" == "$expected_id" && "$rare" == "true" \
            && "$base_x" =~ ^-?[0-9]+$ && "$base_y" =~ ^-?[0-9]+$ \
            && "$top_x" =~ ^-?[0-9]+$ && "$top_y" =~ ^-?[0-9]+$ \
            && "$game_width" =~ ^[0-9]+$ && "$game_height" =~ ^[0-9]+$ \
            && "$base_x" -ge 0 && "$base_x" -lt "$game_width" \
            && "$base_y" -ge 0 && "$base_y" -lt "$game_height" \
            && "$top_x" -ge 0 && "$top_x" -lt "$game_width" \
            && "$top_y" -ge 0 && "$top_y" -lt "$base_y" ]]; then
            echo "Verified Android rare-drop beam: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for readable Android rare-drop beam id=$expected_id" >&2
    ground_loot_log_tail
    return 1
}

wait_for_context_menu_action_index() {
    local expected="$1"
    local timeout="${2:-15}"
    local deadline=$((SECONDS + timeout))
    local line actions index action
    local -a action_list

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU " | tail -1 || true)"
        actions="$(extract_log_value "$line" actions)"
        if [[ -n "$actions" && "$actions" != "none" ]]; then
            IFS=',' read -r -a action_list <<< "$actions"
            for index in "${!action_list[@]}"; do
                action="${action_list[$index]}"
                if [[ "$action" == "$expected" ]]; then
                    echo "$index"
                    return 0
                fi
            done
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android context menu action row $expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU" | tail -30 >&2 || true
    return 1
}

shop_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SHOP" | tail -40 >&2 || true
}

wait_for_shop_open() {
    local timeout="${1:-20}"
    local expected_orientation="${2:-}"
    local deadline=$((SECONDS + timeout))
    local line items orientation

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SHOP_OPEN " | tail -1 || true)"
        items="$(extract_log_value "$line" items)"
        orientation="$(extract_log_value "$line" orientation)"
        if [[ "$items" =~ ^[0-9]+$ ]] && (( items > 0 )) \
            && { [[ -z "$expected_orientation" ]] || [[ "$orientation" == "$expected_orientation" ]]; }; then
            echo "Verified Android shop open: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android shop open" >&2
    shop_log_tail
    return 1
}

assert_native_shop_layout() {
    local line="$1"
    local expected_orientation="$2"
    local expected_amount_rows="${3:-}"
    local layout orientation target targets action_layout amount_columns amount_rows
    local grid_columns grid_rows slot_min_w slot_min_h amount_min_w amount_min_h contained separated

    layout="$(extract_log_value "$line" layout)"
    orientation="$(extract_log_value "$line" orientation)"
    target="$(extract_log_value "$line" target48)"
    targets="$(extract_log_value "$line" targets48)"
    action_layout="$(extract_log_value "$line" actionLayout)"
    amount_columns="$(extract_log_value "$line" amountColumns)"
    amount_rows="$(extract_log_value "$line" amountRows)"
    grid_columns="$(extract_log_value "$line" gridColumns)"
    grid_rows="$(extract_log_value "$line" gridRows)"
    slot_min_w="$(extract_log_value "$line" slotMinW)"
    slot_min_h="$(extract_log_value "$line" slotMinH)"
    amount_min_w="$(extract_log_value "$line" amountMinW)"
    amount_min_h="$(extract_log_value "$line" amountMinH)"
    contained="$(extract_log_value "$line" contained)"
    separated="$(extract_log_value "$line" separated)"

    if [[ "$layout" != "native" || "$orientation" != "$expected_orientation" \
        || "$targets" != "true" || "$contained" != "true" || "$separated" != "true" \
        || "$grid_columns" != "8" || "$grid_rows" != "5" \
        || ! "$target" =~ ^[0-9]+$ || "$target" -le 0 \
        || ! "$slot_min_w" =~ ^[0-9]+$ || "$slot_min_w" -lt "$target" \
        || ! "$slot_min_h" =~ ^[0-9]+$ || "$slot_min_h" -lt "$target" \
        || ! "$amount_min_w" =~ ^[0-9]+$ || "$amount_min_w" -lt "$target" \
        || ! "$amount_min_h" =~ ^[0-9]+$ || "$amount_min_h" -lt "$target" ]]; then
        echo "ERROR: Android $expected_orientation shop failed native 48dp/containment geometry: $line" >&2
        return 1
    fi
    if [[ "$expected_orientation" == "landscape" && "$action_layout" != "side" ]]; then
        echo "ERROR: Android landscape shop fell back to the grid-collapsing stacked layout: $line" >&2
        return 1
    fi
    if [[ "$expected_orientation" == "portrait" && "$action_layout" != "stacked" ]]; then
        echo "ERROR: Android portrait shop expected stacked actions: $line" >&2
        return 1
    fi
    if [[ -n "$expected_amount_rows" && "$amount_rows" != "$expected_amount_rows" ]]; then
        echo "ERROR: Android $expected_orientation shop expected amountRows=$expected_amount_rows, got $amount_rows: $line" >&2
        return 1
    fi
    if [[ "$expected_amount_rows" == "2" && "$amount_columns" != "3" ]]; then
        echo "ERROR: Android narrow-landscape shop expected wrapped 3x2 action groups: $line" >&2
        return 1
    fi

    echo "Verified Android $expected_orientation shop geometry: actionLayout=$action_layout amounts=${amount_columns}x${amount_rows} slotMin=${slot_min_w}x${slot_min_h} amountMin=${amount_min_w}x${amount_min_h} target48=$target"
}

wait_for_shop_selected_owned() {
    local expected_slot="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line selected_slot selected_owned

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SHOP_OPEN " | tail -1 || true)"
        selected_slot="$(extract_log_value "$line" selectedSlot)"
        selected_owned="$(extract_log_value "$line" selectedOwned)"
        if [[ "$selected_slot" == "$expected_slot" && "$selected_owned" =~ ^[0-9]+$ && "$selected_owned" -gt 0 ]]; then
            echo "Verified Android shop selected owned item: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android shop selected slot $expected_slot to become owned" >&2
    shop_log_tail
    return 1
}

wait_for_shop_select() {
    local expected_slot="$1"
    local timeout="${2:-15}"
    local deadline=$((SECONDS + timeout))
    local line slot

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SHOP_SELECT " | tail -1 || true)"
        slot="$(extract_log_value "$line" slot)"
        if [[ "$slot" == "$expected_slot" ]]; then
            echo "Verified Android shop select: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android shop select slot $expected_slot" >&2
    shop_log_tail
    return 1
}

wait_for_shop_action() {
    local expected="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SHOP_ACTION action=$expected " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android shop action $expected: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android shop action $expected" >&2
    shop_log_tail
    return 1
}

tap_shop_action_with_retry() {
    local expected="$1"
    local line="$2"
    local orientation action_key action_x action_y attempt
    local screen_width screen_height viewport_line
    orientation="$(extract_log_value "$line" orientation)"
    if [[ "$expected" == "BUY" ]]; then
        action_key="buy1"
    else
        action_key="sell1"
    fi

    for attempt in 1 2 3; do
        action_x="$(log_int_or_default "$line" "${action_key}X" 1)"
        action_y="$(log_int_or_default "$line" "${action_key}Y" 1)"
        read -r screen_width screen_height < <(screen_size)
        viewport_line="$(latest_settled_android_mobile_viewport "$screen_width" "$screen_height" || true)"
        if [[ -n "$viewport_line" ]]; then
            SHOP_ACTION_VIEWPORT_LINE="$viewport_line"
        elif [[ -n "$SHOP_ACTION_VIEWPORT_LINE" \
            && "$(extract_log_value "$SHOP_ACTION_VIEWPORT_LINE" surfaceW)" == "$screen_width" \
            && "$(extract_log_value "$SHOP_ACTION_VIEWPORT_LINE" surfaceH)" == "$screen_height" ]]; then
            viewport_line="$SHOP_ACTION_VIEWPORT_LINE"
        fi
        "$ADB" logcat -c || true
        if [[ -n "$viewport_line" ]]; then
            tap_client_xy_from_viewport "$action_x" "$action_y" "$viewport_line"
        else
            tap_client_xy "$action_x" "$action_y"
        fi
        if wait_for_shop_action "$expected" 7; then
            return 0
        fi
        line="$(wait_for_shop_open 10 "$orientation")" || return 1
    done

    echo "ERROR: Android shop $expected target did not activate after 3 renderer-coordinate taps" >&2
    return 1
}

bank_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK" | tail -40 >&2 || true
}

wait_for_bank_open() {
    local timeout="${1:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_OPEN " | tail -1 || true)"
        if [[ "$line" =~ bankItems=([0-9]+) ]] && (( BASH_REMATCH[1] > 0 )); then
            echo "Verified Android bank open: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank open" >&2
    bank_log_tail
    return 1
}

wait_for_bank_search() {
    local expected="$1"
    local timeout="${2:-20}"
    local expected_token deadline line query matches
    expected_token="$(chat_message_log_token "$expected")"
    deadline=$((SECONDS + timeout))

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_SEARCH " | tail -1 || true)"
        query="$(extract_log_value "$line" query)"
        matches="$(extract_log_value "$line" matches)"
        if [[ -n "$line" && "$query" == "$expected_token" ]]; then
            if [[ -z "$expected_token" || ( "$matches" =~ ^[0-9]+$ && "$matches" -gt 0 ) ]]; then
                echo "Verified Android bank search: $line" >&2
                echo "$line"
                return 0
            fi
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank search '$expected_token'" >&2
    bank_log_tail
    return 1
}

wait_for_bank_search_focus() {
    local expected="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line focused

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_OPEN " | tail -1 || true)"
        focused="$(extract_log_value "$line" searchFocused)"
        if [[ -n "$line" && "$focused" == "$expected" ]]; then
            echo "Verified Android bank search focus=$expected: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank search focus=$expected" >&2
    bank_log_tail
    return 1
}

wait_for_bank_page() {
    local expected="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line page

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_OPEN " | tail -1 || true)"
        page="$(extract_log_value "$line" bankPage)"
        if [[ -n "$line" && "$page" == "$expected" ]]; then
            echo "Verified Android bank page=$expected: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank page=$expected" >&2
    bank_log_tail
    return 1
}

assert_void_glass_bank_touch_geometry() {
    local line="$1"
    local touch_target bank_items panel_x panel_y panel_w panel_h
    local tab_count tab_columns tab_rows tab_w tab_h tab0_x tab0_y tab_last_x tab_last_y
    local dimension value

    touch_target="$(extract_log_value "$line" touchTarget)"
    bank_items="$(extract_log_value "$line" bankItems)"
    panel_x="$(extract_log_value "$line" panelX)"
    panel_y="$(extract_log_value "$line" panelY)"
    panel_w="$(extract_log_value "$line" panelW)"
    panel_h="$(extract_log_value "$line" panelH)"
    tab_count="$(extract_log_value "$line" tabCount)"
    tab_columns="$(extract_log_value "$line" tabColumns)"
    tab_rows="$(extract_log_value "$line" tabRows)"
    tab_w="$(extract_log_value "$line" tabW)"
    tab_h="$(extract_log_value "$line" tabH)"
    tab0_x="$(extract_log_value "$line" tab0X)"
    tab0_y="$(extract_log_value "$line" tab0Y)"
    tab_last_x="$(extract_log_value "$line" tabLastX)"
    tab_last_y="$(extract_log_value "$line" tabLastY)"

    if [[ ! "$touch_target" =~ ^[0-9]+$ || "$touch_target" -le 0 ]]; then
        echo "ERROR: Void Glass bank omitted its native touch target: $line" >&2
        return 1
    fi
    if (( AUTH_BANK_FIXTURE_BANK_SLOTS > 240 )) \
        && [[ ! "$bank_items" =~ ^[0-9]+$ || "$bank_items" -le 240 ]]; then
        echo "ERROR: over-cap bank fixture was not exposed by the client: expected>${240}, bankItems=$bank_items" >&2
        echo "$line" >&2
        return 1
    fi
    for dimension in searchH cellW cellH closeW closeH loadoutW actionH menuRowH; do
        value="$(extract_log_value "$line" "$dimension")"
        if [[ ! "$value" =~ ^[0-9]+$ || "$value" -lt "$touch_target" ]]; then
            echo "ERROR: Void Glass bank $dimension=$value is below touchTarget=$touch_target" >&2
            echo "$line" >&2
            return 1
        fi
    done

    if [[ "$bank_items" =~ ^[0-9]+$ && "$bank_items" -gt 240 ]]; then
        if [[ "$tab_count" != "7" ]]; then
            echo "ERROR: over-cap Void Glass bank exposes tabCount=$tab_count instead of seven tabs" >&2
            echo "$line" >&2
            return 1
        fi
        for value in "$panel_x" "$panel_y" "$panel_w" "$panel_h" "$tab_columns" "$tab_rows" \
            "$tab_w" "$tab_h" "$tab0_x" "$tab0_y" "$tab_last_x" "$tab_last_y"; do
            [[ "$value" =~ ^-?[0-9]+$ ]] || {
                echo "ERROR: malformed Void Glass bank tab geometry: $line" >&2
                return 1
            }
        done
        if (( tab_columns < 1 || tab_rows < 1 || tab_columns * tab_rows < tab_count \
            || tab_w < touch_target || tab_h < touch_target \
            || tab0_x - tab_w / 2 < panel_x \
            || tab0_x + (tab_w + 1) / 2 > panel_x + panel_w \
            || tab_last_x - tab_w / 2 < panel_x \
            || tab_last_x + (tab_w + 1) / 2 > panel_x + panel_w \
            || tab0_y - tab_h / 2 < panel_y \
            || tab_last_y + (tab_h + 1) / 2 > panel_y + panel_h )); then
            echo "ERROR: Void Glass seven-tab layout is clipped or undersized" >&2
            echo "$line" >&2
            return 1
        fi
    fi

    echo "Verified Android Void Glass touch geometry: touch=$touch_target tabs=${tab_count}/${tab_columns}x${tab_rows} tab=${tab_w}x${tab_h}"
}

wait_for_bank_scroll() {
    local timeout="${1:-20}"
    local deadline=$((SECONDS + timeout))
    local line after

    while (( SECONDS < deadline )); do
        while IFS= read -r line; do
            after="$(extract_log_value "$line" after)"
            if [[ "$after" =~ ^[0-9]+$ ]] && (( after > 0 )); then
                echo "Verified Android bank scroll: $line" >&2
                echo "$line"
                return 0
            fi
        done < <("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_SCROLL " | tail -20 || true)
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank scroll" >&2
    bank_log_tail
    return 1
}

wait_for_bank_action() {
    local expected="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_ACTION " | grep "action=$expected " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android bank action $expected: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank action $expected" >&2
    bank_log_tail
    return 1
}

wait_for_bank_loadouts_panel() {
    local timeout="${1:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_LOADOUT_PANEL " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android bank loadouts panel: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank loadouts panel" >&2
    bank_log_tail
    return 1
}

wait_for_bank_modal() {
    local expected_type="$1"
    local expected_slot="$2"
    local timeout="${3:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_MODAL type=$expected_type slot=$expected_slot " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android bank modal: $line" >&2
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android bank modal $expected_type slot $expected_slot" >&2
    bank_log_tail
    return 1
}

wait_for_bank_preset_saved() {
    local player_id="$1"
    local slot="$2"
    local timeout="${3:-45}"
    local deadline=$((SECONDS + timeout))
    local count

    while (( SECONDS < deadline )); do
        count="$(sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
            "select count(*) from bankpresets where playerID = $player_id and slot = $slot and length(inventory) > 0;")"
        if [[ "$count" =~ ^[0-9]+$ ]] && (( count > 0 )); then
            echo "Verified Android bank preset saved for player $player_id slot $slot"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for saved bank preset player $player_id slot $slot" >&2
    sqlite3 -cmd '.timeout 5000' -header -column "$AUTH_DB" \
        "select playerID, slot, length(inventory) as inventoryLength, length(equipment) as equipmentLength from bankpresets where playerID = $player_id;" >&2 || true
    return 1
}

assert_context_menu_clamped() {
    local menu_x="$1"
    local menu_y="$2"
    local menu_width="$3"
    local menu_height="$4"
    local mouse_x="$5"
    local mouse_y="$6"
    local max_x=$((512 - 2))
    local max_y=$((334 - 19))

    if (( menu_x < 0 || menu_y < 0 || menu_x + menu_width > max_x || menu_y + menu_height > max_y )); then
        echo "ERROR: context menu out of bounds: x=$menu_x y=$menu_y width=$menu_width height=$menu_height" >&2
        return 1
    fi

    if (( mouse_x > max_x - 4 && menu_x + menu_width != max_x )); then
        echo "ERROR: expected right-edge context menu to clamp to $max_x, got x+width=$((menu_x + menu_width))" >&2
        return 1
    fi

    if (( mouse_y > max_y - 4 && menu_y + menu_height != max_y )); then
        echo "ERROR: expected bottom-edge context menu to clamp to $max_y, got y+height=$((menu_y + menu_height))" >&2
        return 1
    fi
}

tap_context_menu_item() {
    local menu_x="$1"
    local menu_y="$2"
    local menu_width="$3"
    local menu_height="$4"
    local menu_items="$5"
    local item_index="${6:-0}"
    local line_height client_x client_y

    if (( menu_items <= 0 )); then
        echo "ERROR: context menu has no selectable rows" >&2
        return 1
    fi

    line_height=$((menu_height / (menu_items + 1)))
    if (( line_height <= 0 )); then
        echo "ERROR: invalid context menu line height from height=$menu_height items=$menu_items" >&2
        return 1
    fi

    client_x=$((menu_x + menu_width / 2))
    client_y=$((menu_y + line_height * (item_index + 1) + line_height / 2))
    echo "Android context menu item $item_index at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
}

wait_for_object_target() {
    local object_id="$1"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_OBJECT_TARGET id=$object_id " | tail -1 || true)"
        if [[ "$line" =~ clientX=([0-9]+).*clientY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android object target id $object_id" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_OBJECT_" | tail -20 >&2 || true
    return 1
}

wait_for_stable_object_target() {
    local object_id="$1"
    local timeout="${2:-30}"
    local required_matches="${3:-2}"
    local deadline=$((SECONDS + timeout))
    local line x y last_x="" last_y="" matches=0

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_OBJECT_TARGET id=$object_id " | tail -1 || true)"
        if [[ "$line" =~ clientX=([0-9]+).*clientY=([0-9]+) ]]; then
            x="${BASH_REMATCH[1]}"
            y="${BASH_REMATCH[2]}"
            if [[ "$x" == "$last_x" && "$y" == "$last_y" ]]; then
                matches=$((matches + 1))
            else
                last_x="$x"
                last_y="$y"
                matches=1
            fi

            if (( matches >= required_matches )); then
                echo "$x $y"
                return 0
            fi
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for stable Android object target id $object_id" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_OBJECT_" | tail -20 >&2 || true
    return 1
}

wait_for_object_action() {
    local object_id="$1"
    local expected="$2"
    local timeout="${3:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_OBJECT_ACTION action=$expected id=$object_id " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android object tap: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android object action $expected on id $object_id" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_OBJECT_" | tail -20 >&2 || true
    return 1
}

tap_object_target() {
    local object_id="$1"
    local coords client_x client_y
    coords="$(wait_for_object_target "$object_id" 30)" || return 1
    read -r client_x client_y <<< "$coords"
    echo "Android object target $object_id at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
}

wait_for_welcome_dialog() {
    local timeout="${1:-8}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WELCOME_DIALOG " | tail -1 || true)"
        if [[ "$line" =~ closeX=([0-9]+).*closeY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
            return 0
        fi
        sleep 1
    done

    return 1
}

close_welcome_dialog_if_present() {
    local timeout="${1:-8}"
    local coords client_x client_y

    "$ADB" logcat -c || true
    coords="$(wait_for_welcome_dialog "$timeout" || true)"
    if [[ -z "$coords" ]]; then
        echo "Android welcome dialog not observed; continuing without a close tap."
        return 0
    fi

    read -r client_x client_y <<< "$coords"
    echo "Closing Android welcome dialog at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
    sleep 2
}

wait_for_appearance_prompt() {
    local timeout="${1:-8}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_APPEARANCE_PROMPT " | tail -1 || true)"
        if [[ "$line" =~ acceptX=([0-9]+).*acceptY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
            return 0
        fi
        sleep 1
    done

    return 1
}

accept_appearance_prompt_if_present() {
    local timeout="${1:-8}"
    local coords client_x client_y

    "$ADB" logcat -c || true
    coords="$(wait_for_appearance_prompt "$timeout" || true)"
    if [[ -z "$coords" ]]; then
        echo "Android appearance prompt not observed; continuing without accepting appearance."
        return 0
    fi

    read -r client_x client_y <<< "$coords"
    echo "Accepting Android appearance prompt at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
    sleep 8
}

wait_for_inventory_target() {
    local slot="$1"
    local timeout="${2:-30}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_INVENTORY_TARGET slot=$slot " | tail -1 || true)"
        if [[ "$line" =~ clientX=([0-9]+).*clientY=([0-9]+) ]]; then
            echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android inventory target slot $slot" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_INVENTORY_" | tail -20 >&2 || true
    return 1
}

wait_for_inventory_action() {
    local slot="$1"
    local expected="$2"
    local timeout="${3:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_INVENTORY_ACTION action=$expected slot=$slot " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android inventory tap: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android inventory action $expected on slot $slot" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_INVENTORY_" | tail -20 >&2 || true
    return 1
}

wait_for_walk_action() {
    local expected="${1:-LANDSCAPE_WALK_HERE}"
    local timeout="${2:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WALK_ACTION action=$expected " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android walk action: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android walk action $expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WALK_" | tail -20 >&2 || true
    return 1
}

wait_for_inventory_equipped_state() {
    local slot="$1"
    local expected="$2"
    local timeout="${3:-20}"
    local deadline=$((SECONDS + timeout))
    local line equipped

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_INVENTORY_TARGET slot=$slot " | tail -1 || true)"
        equipped="$(extract_log_value "$line" equipped)"
        if [[ "$equipped" == "$expected" ]]; then
            echo "Verified Android inventory equipped state: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android inventory slot $slot equipped=$expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_INVENTORY_" | tail -30 >&2 || true
    return 1
}

equipment_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_EQUIPMENT" | tail -40 >&2 || true
}

wait_for_equipment_tab() {
    local expected_tab="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line tab

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_EQUIPMENT_TAB " | tail -1 || true)"
        tab="$(extract_log_value "$line" tab)"
        if [[ "$tab" == "$expected_tab" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android equipment tab $expected_tab" >&2
    equipment_log_tail
    return 1
}

assert_equipment_touch_geometry() {
    local expected_orientation="$1"
    local equipment_line="$2"
    local viewport_line="$3"
    local tab_height slot_width slot_height touch48 scale density dimension value

    tab_height="$(extract_log_value "$equipment_line" tabHeight)"
    slot_width="$(extract_log_value "$equipment_line" slotWidth)"
    slot_height="$(extract_log_value "$equipment_line" slotHeight)"
    touch48="$(extract_log_value "$viewport_line" touch48)"
    scale="$(extract_log_value "$viewport_line" scale)"
    density="$(extract_log_value "$viewport_line" density)"

    if [[ ! "$touch48" =~ ^[0-9]+$ || "$touch48" -le 0 ]] \
        || ! is_positive_log_number "$scale" \
        || ! is_positive_log_number "$density"; then
        echo "ERROR: Android $expected_orientation equipment geometry needs valid physical touch telemetry" >&2
        echo "$equipment_line" >&2
        echo "$viewport_line" >&2
        return 1
    fi

    for dimension in tabHeight slotWidth slotHeight; do
        case "$dimension" in
            tabHeight) value="$tab_height" ;;
            slotWidth) value="$slot_width" ;;
            slotHeight) value="$slot_height" ;;
        esac
        if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value < touch48 )); then
            echo "ERROR: Android $expected_orientation equipment $dimension=$value is below physical touch48=$touch48" >&2
            echo "$equipment_line" >&2
            return 1
        fi
        assert_client_rect_extent_at_least_dp \
            "equipment-$expected_orientation-$dimension" "$dimension" \
            "$value" "$scale" "$density" 48 || return 1
    done

    echo "Verified Android $expected_orientation equipment touch geometry: tabHeight=$tab_height slot=${slot_width}x${slot_height} touch48=$touch48"
}

wait_for_equipment_count() {
    local expected_count="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line count

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_EQUIPMENT_TAB " | tail -1 || true)"
        count="$(extract_log_value "$line" equipped)"
        if [[ "$count" == "$expected_count" ]]; then
            echo "Verified Android equipment count: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android equipment count $expected_count" >&2
    equipment_log_tail
    return 1
}

wait_for_equipped_item() {
    local expected_id="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_EQUIPMENT_SLOT " | grep " id=$expected_id " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android equipped item id $expected_id" >&2
    equipment_log_tail
    return 1
}

wait_for_equipment_action() {
    local expected="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_EQUIPMENT_ACTION action=$expected " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android equipment action $expected: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android equipment action $expected" >&2
    equipment_log_tail
    return 1
}

magic_prayer_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -E "ANDROID_SMOKE_MAGIC_PRAYER|ANDROID_SMOKE_CONTEXT_MENU" | tail -50 >&2 || true
}

wait_for_magic_prayer_tab() {
    local expected_list="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line list show_ui_tab

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_MAGIC_PRAYER_TAB " | tail -1 || true)"
        list="$(extract_log_value "$line" list)"
        show_ui_tab="$(extract_log_value "$line" showUiTab)"
        if [[ "$list" == "$expected_list" && "$show_ui_tab" == "4" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android magic/prayer tab list=$expected_list" >&2
    magic_prayer_log_tail
    return 1
}

wait_for_magic_prayer_action() {
    local expected="$1"
    local index="$2"
    local timeout="${3:-20}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_MAGIC_PRAYER_ACTION action=$expected index=$index " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android magic/prayer action $expected: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android magic/prayer action $expected index $index" >&2
    magic_prayer_log_tail
    return 1
}

wait_for_magic_prayer_action_quiet() {
    local expected="$1"
    local index="$2"
    local timeout="${3:-6}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_MAGIC_PRAYER_ACTION action=$expected index=$index " | tail -1 || true)"
        if [[ -n "$line" ]]; then
            echo "Verified Android magic/prayer action $expected: $line"
            return 0
        fi
        sleep 1
    done

    return 1
}

tap_magic_prayer_row_until_action() {
    local row_x="$1"
    local row_y="$2"
    local expected="$3"
    local index="$4"
    local attempt

    for attempt in 1 2 3; do
        "$ADB" logcat -c || true
        tap_client_xy "$row_x" "$row_y"
        if wait_for_magic_prayer_action_quiet "$expected" "$index" 6; then
            return 0
        fi
        sleep 1
    done

    wait_for_magic_prayer_action "$expected" "$index" 1
}

tap_magic_prayer_tab() {
	local deadline=$((SECONDS + 20))
	local line tab_x tab_y
	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_MAGIC_PRAYER_LAUNCH " | tail -1 || true)"
		tab_x="$(extract_log_value "$line" magicTabX)"
		tab_y="$(extract_log_value "$line" magicTabY)"
		if [[ "$tab_x" =~ ^[0-9]+$ && "$tab_y" =~ ^[0-9]+$ ]]; then
			echo "Android Magic/Prayer tab at client $tab_x,$tab_y"
			tap_client_xy "$tab_x" "$tab_y"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: timed out waiting for Android Magic/Prayer top-tab target" >&2
	magic_prayer_log_tail
	return 1
}

cast_selected_spell_on_self() {
    local spell_id="$1"
    local self_x="$2"
    local self_y="$3"
    local menu_values menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y menu_index

    "$ADB" logcat -c || true
    long_press_client_xy "$self_x" "$self_y" 1200
    menu_values="$(wait_for_context_menu 20)" || return 1
    read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
    menu_index="$(wait_for_context_menu_action_index SELF_CAST_SPELL 10)" || return 1
    tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" "$menu_index" || return 1
    wait_for_magic_prayer_action SELF_CAST_SPELL "$spell_id" 20
}

cast_selected_spell_on_player_target() {
    local target_name="$1"
    local menu_values menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y menu_index

    "$ADB" logcat -c || true
    enable_android_smoke_player_targets
    tap_player_target "$target_name" || return 1
    if wait_for_player_action PLAYER_CAST_SPELL 20; then
        return 0
    fi

    "$ADB" logcat -c || true
    enable_android_smoke_player_targets
    long_press_player_target "$target_name" 1200 || return 1
    menu_values="$(wait_for_context_menu 20)" || return 1
    read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
    menu_index="$(wait_for_context_menu_action_index PLAYER_CAST_SPELL 10)" || return 1
    tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" "$menu_index" || return 1
    wait_for_player_action PLAYER_CAST_SPELL 20
}

world_map_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP" | tail -60 >&2 || true
}

wait_for_world_map_open() {
    local timeout="${1:-30}"
    local deadline=$((SECONDS + timeout))
    local line visible win_w content_x

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        win_w="$(extract_log_value "$line" winW)"
        content_x="$(extract_log_value "$line" contentX)"
        if [[ "$visible" == "true" && "$win_w" =~ ^[0-9]+$ && "$win_w" -gt 0 && "$content_x" =~ ^[0-9]+$ && "$content_x" -gt 0 ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map to open" >&2
    world_map_log_tail
    return 1
}

wait_for_world_map_rendered() {
    local timeout="${1:-45}"
    local deadline=$((SECONDS + timeout))
    local line visible event win_w content_x

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -12 | grep -E "event=(STATE|BEFORE_RENDER) " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        event="$(extract_log_value "$line" event)"
        win_w="$(extract_log_value "$line" winW)"
        content_x="$(extract_log_value "$line" contentX)"
        if [[ ( "$event" == "STATE" || "$event" == "BEFORE_RENDER" ) && "$visible" == "true" && "$win_w" =~ ^[0-9]+$ && "$win_w" -gt 0 \
            && "$content_x" =~ ^[0-9]+$ && "$content_x" -gt 0 ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map rendered state" >&2
    world_map_log_tail
    return 1
}

assert_world_map_touch_layout() {
    local line="$1"
    local layout game_width game_height win_width win_height content_height
    local target min_width min_height targets inside overlap

    layout="$(extract_log_value "$line" layout)"
    game_width="$(extract_log_value "$line" gameWidth)"
    game_height="$(extract_log_value "$line" gameHeight)"
    win_width="$(extract_log_value "$line" winW)"
    win_height="$(extract_log_value "$line" winH)"
    content_height="$(extract_log_value "$line" contentH)"
    target="$(extract_log_value "$line" target48)"
    min_width="$(extract_log_value "$line" targetMinW)"
    min_height="$(extract_log_value "$line" targetMinH)"
    targets="$(extract_log_value "$line" targets48)"
    inside="$(extract_log_value "$line" controlsInside)"
    overlap="$(extract_log_value "$line" controlsOverlap)"

    if [[ "$layout" != "native" || ! "$target" =~ ^[0-9]+$ || "$target" -le 0 ]]; then
        echo "ERROR: Android World Map did not report a native physical touch layout: $line" >&2
        return 1
    fi
    if [[ ! "$min_width" =~ ^[0-9]+$ || ! "$min_height" =~ ^[0-9]+$ \
        || "$min_width" -lt "$target" || "$min_height" -lt "$target" \
        || "$targets" != "true" ]]; then
        echo "ERROR: Android World Map control is below physical 48dp target=$target: $line" >&2
        return 1
    fi
    if [[ "$inside" != "true" || "$overlap" != "false" ]]; then
        echo "ERROR: Android World Map controls escape or overlap their safe frame: $line" >&2
        return 1
    fi
    if [[ ! "$content_height" =~ ^[0-9]+$ || "$content_height" -lt "$target" ]]; then
        echo "ERROR: Android World Map leaves less than one touch row for map content: $line" >&2
        return 1
    fi
    if [[ "$game_height" =~ ^[0-9]+$ && "$game_width" =~ ^[0-9]+$ \
        && "$win_height" =~ ^[0-9]+$ && "$win_width" =~ ^[0-9]+$ \
        && "$game_height" -gt "$game_width" \
        && $((win_height * 100)) -gt $((win_width * 140)) ]]; then
        echo "ERROR: Android portrait World Map is an overlong skyscraper: $line" >&2
        return 1
    fi

    echo "Verified Android World Map native touch geometry: target=$target min=${min_width}x${min_height}." >&2
}

wait_for_world_map_button() {
    local timeout="${1:-20}"
    local deadline=$((SECONDS + timeout))
    local line button_x button_y

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP_BUTTON " | tail -1 || true)"
        button_x="$(extract_log_value "$line" buttonX)"
        button_y="$(extract_log_value "$line" buttonY)"
        if [[ "$button_x" =~ ^[0-9]+$ && "$button_y" =~ ^[0-9]+$ && "$button_x" -gt 0 && "$button_y" -gt 0 ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map button geometry" >&2
    world_map_log_tail
    return 1
}

wait_for_world_map_closed() {
    local timeout="${1:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        if [[ "$visible" == "false" ]]; then
            echo "Verified Android world map close: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map to close" >&2
    world_map_log_tail
    return 1
}

wait_for_world_map_zoom() {
    local expected_zoom="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible zoom

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        zoom="$(extract_log_value "$line" zoom)"
        if [[ "$visible" == "true" && "$zoom" == "$expected_zoom" ]]; then
            echo "Verified Android world map zoom: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map zoom=$expected_zoom" >&2
    world_map_log_tail
    return 1
}

wait_for_world_map_pan_changed() {
    local before_pan_x="$1"
    local before_pan_y="$2"
    local timeout="${3:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible pan_x pan_y

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        pan_x="$(extract_log_value "$line" panX)"
        pan_y="$(extract_log_value "$line" panY)"
        if [[ "$visible" == "true" && "$pan_x" =~ ^-?[0-9]+$ && "$pan_y" =~ ^-?[0-9]+$ \
            && ( "$pan_x" != "$before_pan_x" || "$pan_y" != "$before_pan_y" ) ]]; then
            echo "Verified Android world map pan: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map pan change from $before_pan_x,$before_pan_y" >&2
    world_map_log_tail
    return 1
}

wait_for_world_map_search_focus() {
    local expected="$1"
    local timeout="${2:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible focused

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        focused="$(extract_log_value "$line" searchFocused)"
        if [[ "$visible" == "true" && "$focused" == "$expected" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map searchFocused=$expected" >&2
    world_map_log_tail
    return 1
}

wait_for_world_map_search() {
    local expected="$1"
    local expected_token timeout deadline line visible focused query
    timeout="${2:-20}"
    expected_token="$(chat_message_log_token "$expected")"
    deadline=$((SECONDS + timeout))

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_WORLD_MAP " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        focused="$(extract_log_value "$line" searchFocused)"
        query="$(extract_log_value "$line" search)"
        if [[ "$visible" == "true" && "$focused" == "false" && "$query" == "$expected_token" ]]; then
            echo "Verified Android world map search: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android world map search '$expected_token'" >&2
    world_map_log_tail
    return 1
}

open_world_map_from_hud() {
    local line button_x button_y attempt

    "$ADB" shell input keyevent 41
    if line="$(wait_for_world_map_open 10)"; then
        echo "$line"
        return 0
    fi

    for attempt in 1 2 3; do
        tap_client_xy 460 19
        if line="$(wait_for_world_map_button 8)"; then
            button_x="$(log_int_or_default "$line" buttonX 431)"
            button_y="$(log_int_or_default "$line" buttonY 201)"
            tap_client_xy "$button_x" "$button_y"
            if line="$(wait_for_world_map_open 10)"; then
                echo "$line"
                return 0
            fi
        fi

        tap_client_xy 431 201
        if line="$(wait_for_world_map_open 5)"; then
            echo "$line"
            return 0
        fi

        tap_client_xy 422 177
        if line="$(wait_for_world_map_open 5)"; then
            echo "$line"
            return 0
        fi
    done

    wait_for_world_map_open 1
}

settings_log_tail() {
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SETTINGS" | tail -60 >&2 || true
}

afk_log_tail() {
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| grep "ANDROID_SMOKE_AFK" | tail -60 >&2 || true
}

wait_for_afk_settings_target() {
	local expected_active="${1:-false}"
	local timeout="${2:-20}"
	local deadline=$((SECONDS + timeout))
	local line active

	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep "ANDROID_SMOKE_AFK_SETTINGS " | tail -1 || true)"
		active="$(extract_log_value "$line" active)"
		if [[ "$active" == "$expected_active" ]]; then
			echo "$line"
			return 0
		fi
		sleep 1
	done

	echo "ERROR: timed out waiting for Android AFK settings target active=$expected_active" >&2
	afk_log_tail
	return 1
}

assert_afk_settings_target() {
	local line="$1"
	local expected_active="${2:-false}"
	local x y width height active
	x="$(extract_log_value "$line" x)"
	y="$(extract_log_value "$line" y)"
	width="$(extract_log_value "$line" w)"
	height="$(extract_log_value "$line" h)"
	active="$(extract_log_value "$line" active)"

	if [[ "$active" != "$expected_active" \
		|| ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ \
		|| ! "$width" =~ ^[0-9]+$ || ! "$height" =~ ^[0-9]+$ \
		|| "$width" -lt 48 || "$height" -lt 48 ]]; then
		echo "ERROR: invalid Android AFK settings target (minimum 48dp): $line" >&2
		return 1
	fi
}

tap_afk_settings_target_from_line() {
	local line="$1"
	local x y width height
	x="$(extract_log_value "$line" x)"
	y="$(extract_log_value "$line" y)"
	width="$(extract_log_value "$line" w)"
	height="$(extract_log_value "$line" h)"
	tap_client_xy $((x + width / 2)) $((y + height / 2))
}

wait_for_afk_state() {
	local expected_active="$1"
	local timeout="${2:-20}"
	local minimum_render_count="${3:--1}"
	local deadline=$((SECONDS + timeout))
	local line active render_count

	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep "ANDROID_SMOKE_AFK active=$expected_active " | tail -1 || true)"
		active="$(extract_log_value "$line" active)"
		render_count="$(extract_log_value "$line" renderCount)"
		if [[ "$active" == "$expected_active" ]]; then
			if [[ "$minimum_render_count" -lt 0 \
				|| ( "$render_count" =~ ^[0-9]+$ && "$render_count" -gt "$minimum_render_count" ) ]]; then
				echo "$line"
				return 0
			fi
		fi
		sleep 1
	done

	echo "ERROR: timed out waiting for Android AFK active=$expected_active renderCount>$minimum_render_count" >&2
	afk_log_tail
	return 1
}

assert_afk_monitor_state() {
	local line="$1"
	local expected_active="$2"
	local active resume_x resume_y resume_width resume_height hits prayer
	local hits_current hits_max prayer_current prayer_max xp elapsed remaining render_count
	active="$(extract_log_value "$line" active)"
	if [[ "$active" != "$expected_active" ]]; then
		echo "ERROR: Android AFK state mismatch; expected active=$expected_active: $line" >&2
		return 1
	fi
	if [[ "$expected_active" != "true" ]]; then
		return 0
	fi

	resume_x="$(extract_log_value "$line" resumeX)"
	resume_y="$(extract_log_value "$line" resumeY)"
	resume_width="$(extract_log_value "$line" resumeW)"
	resume_height="$(extract_log_value "$line" resumeH)"
	hits="$(extract_log_value "$line" hits)"
	prayer="$(extract_log_value "$line" prayer)"
	xp="$(extract_log_value "$line" xp)"
	elapsed="$(extract_log_value "$line" elapsedMs)"
	remaining="$(extract_log_value "$line" approxRemainingMs)"
	render_count="$(extract_log_value "$line" renderCount)"
	IFS=/ read -r hits_current hits_max <<< "$hits"
	IFS=/ read -r prayer_current prayer_max <<< "$prayer"

	if [[ ! "$resume_x" =~ ^[0-9]+$ || ! "$resume_y" =~ ^[0-9]+$ \
		|| ! "$resume_width" =~ ^[0-9]+$ || ! "$resume_height" =~ ^[0-9]+$ \
		|| "$resume_width" -lt 48 || "$resume_height" -lt 48 \
		|| ! "$hits_current" =~ ^[0-9]+$ || ! "$hits_max" =~ ^[0-9]+$ || "$hits_max" -le 0 \
		|| ! "$prayer_current" =~ ^[0-9]+$ || ! "$prayer_max" =~ ^[0-9]+$ || "$prayer_max" -le 0 \
		|| ! "$xp" =~ ^[0-9]+$ || ! "$elapsed" =~ ^[0-9]+$ \
		|| ! "$remaining" =~ ^[0-9]+$ || ! "$render_count" =~ ^[0-9]+$ \
		|| "$render_count" -le 0 ]]; then
		echo "ERROR: Android AFK monitor telemetry or 48dp Resume target is invalid: $line" >&2
		return 1
	fi
}

tap_afk_resume_from_line() {
	local line="$1"
	local x y width height
	x="$(extract_log_value "$line" resumeX)"
	y="$(extract_log_value "$line" resumeY)"
	width="$(extract_log_value "$line" resumeW)"
	height="$(extract_log_value "$line" resumeH)"
	tap_client_xy $((x + width / 2)) $((y + height / 2))
}

wait_for_settings_state() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local timeout="${3:-20}"
    wait_for_settings_tab_state 0 "$expected_camera" "$expected_mouse" "$timeout"
}

wait_for_settings_tab_state() {
    local expected_tab="$1"
    local expected_camera="$2"
    local expected_mouse="$3"
    local timeout="${4:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible setting_tab camera_auto mouse_one

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SETTINGS " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        setting_tab="$(extract_log_value "$line" settingTab)"
        camera_auto="$(extract_log_value "$line" cameraAuto)"
        mouse_one="$(extract_log_value "$line" mouseOne)"
        if [[ "$visible" == "true" && "$setting_tab" == "$expected_tab" \
            && "$camera_auto" == "$expected_camera" && "$mouse_one" == "$expected_mouse" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android settings tab=$expected_tab cameraAuto=$expected_camera mouseOne=$expected_mouse" >&2
    settings_log_tail
    return 1
}

wait_for_settings_rendered() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local timeout="${3:-20}"
    wait_for_settings_tab_rendered 0 "$expected_camera" "$expected_mouse" "$timeout"
}

wait_for_settings_tab_rendered() {
    local expected_tab="$1"
    local expected_camera="$2"
    local expected_mouse="$3"
    local timeout="${4:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible event setting_tab camera_auto mouse_one

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SETTINGS " | tail -12 | grep "event=STATE " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        event="$(extract_log_value "$line" event)"
        setting_tab="$(extract_log_value "$line" settingTab)"
        camera_auto="$(extract_log_value "$line" cameraAuto)"
        mouse_one="$(extract_log_value "$line" mouseOne)"
        if [[ "$event" == "STATE" && "$visible" == "true" && "$setting_tab" == "$expected_tab" \
            && "$camera_auto" == "$expected_camera" && "$mouse_one" == "$expected_mouse" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for rendered Android settings tab=$expected_tab cameraAuto=$expected_camera mouseOne=$expected_mouse" >&2
    settings_log_tail
    return 1
}

assert_settings_logout_visible() {
    local line="$1"
    local logout_visible logout_placement logout_top logout_bottom chat_tab_top panel_y panel_bottom game_height tab_height
	local native_account_row=0
    logout_visible="$(extract_log_value "$line" logoutVisible)"
    logout_placement="$(extract_log_value "$line" logoutPlacement)"
    logout_top="$(extract_log_value "$line" logoutTop)"
    logout_bottom="$(extract_log_value "$line" logoutBottom)"
    chat_tab_top="$(extract_log_value "$line" chatTabTop)"
    panel_y="$(extract_log_value "$line" panelY)"
    panel_bottom="$(extract_log_value "$line" panelBottom)"
    game_height="$(extract_log_value "$line" gameHeight)"
	tab_height="$(extract_log_value "$line" tabHeight)"

    if [[ "$logout_visible" != "true" ]]; then
        echo "ERROR: Android settings logout is not visible: $line" >&2
        settings_log_tail
        return 1
    fi
    if [[ ! "$logout_top" =~ ^-?[0-9]+$ || ! "$logout_bottom" =~ ^-?[0-9]+$ || ! "$chat_tab_top" =~ ^-?[0-9]+$ \
        || ! "$panel_y" =~ ^-?[0-9]+$ || ! "$panel_bottom" =~ ^-?[0-9]+$ ]]; then
        echo "ERROR: Android settings logout geometry missing numeric fields: $line" >&2
        settings_log_tail
        return 1
    fi
    if [[ "$logout_placement" == "corner" ]]; then
        if [[ ! "$game_height" =~ ^-?[0-9]+$ ]]; then
            echo "ERROR: Android corner logout geometry missing gameHeight: $line" >&2
            settings_log_tail
            return 1
        fi
        if (( logout_top < 0 || logout_bottom > game_height )); then
            echo "ERROR: Android corner logout is outside viewport: logout=${logout_top}..${logout_bottom} gameHeight=$game_height" >&2
            settings_log_tail
            return 1
        fi
        return 0
    fi
	if [[ "$tab_height" =~ ^[0-9]+$ ]] \
		&& (( tab_height > 25 && tab_height == logout_bottom - logout_top )); then
		native_account_row=1
	fi
    if (( native_account_row == 0 && logout_bottom > chat_tab_top - 4 )); then
        echo "ERROR: Android settings logout overlaps chat dock: logoutBottom=$logout_bottom chatTabTop=$chat_tab_top" >&2
        settings_log_tail
        return 1
    fi
    if (( logout_top < panel_y || logout_bottom > panel_bottom )); then
        echo "ERROR: Android settings logout is outside panel: logout=${logout_top}..${logout_bottom} panel=${panel_y}..${panel_bottom}" >&2
        settings_log_tail
        return 1
    fi
}

tap_settings_logout() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local timeout="${3:-20}"
    local screenshot_prefix="${4:-}"
    local line

    line="$(wait_for_settings_rendered "$expected_camera" "$expected_mouse" "$timeout")" || return 1
    assert_settings_logout_visible "$line" || return 1
    complete_android_account_logout_from_settings_line "$line" "$screenshot_prefix"
}

android_logout_log_tail() {
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| grep "ANDROID_SMOKE_LOGOUT " | tail -40 >&2 || true
}

wait_for_android_logout_event() {
	local expected_event="$1"
	local timeout="${2:-15}"
	local deadline=$((SECONDS + timeout))
	local line event source

	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep "ANDROID_SMOKE_LOGOUT event=$expected_event " | tail -1 || true)"
		event="$(extract_log_value "$line" event)"
		source="$(extract_log_value "$line" source)"
		if [[ "$event" == "$expected_event" && "$source" == "account" ]]; then
			echo "$line"
			return 0
		fi
		sleep 1
	done

	echo "ERROR: timed out waiting for Android Account/logout event=$expected_event source=account" >&2
	android_logout_log_tail
	return 1
}

android_logout_rect_from_line() {
	local line="$1"
	local field="$2"
	local payload x y width height
	payload="$(extract_log_value "$line" "$field")"
	IFS=',' read -r x y width height <<< "$payload"
	if [[ ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ \
		|| ! "$width" =~ ^[0-9]+$ || ! "$height" =~ ^[0-9]+$ \
		|| "$width" -le 0 || "$height" -le 0 ]]; then
		echo "ERROR: invalid Android Account/logout $field rect: ${payload:-missing}" >&2
		echo "$line" >&2
		return 1
	fi
	printf '%s %s %s %s\n' "$x" "$y" "$width" "$height"
}

assert_android_logout_layout() {
	local line="$1"
	local expected_account_open="$2"
	local expected_confirm_open="$3"
	local account_open confirm_open overlay_top blocking_surface target48 game_width game_height
	local field rect x y width height

	account_open="$(extract_log_value "$line" accountOpen)"
	confirm_open="$(extract_log_value "$line" confirmOpen)"
	overlay_top="$(extract_log_value "$line" overlayTop)"
	blocking_surface="$(extract_log_value "$line" blockingSurface)"
	target48="$(extract_log_value "$line" target48)"
	game_width="$(extract_log_value "$line" gameWidth)"
	game_height="$(extract_log_value "$line" gameHeight)"
	if [[ "$account_open" != "$expected_account_open" \
		|| "$confirm_open" != "$expected_confirm_open" \
		|| "$overlay_top" != "true" || "$blocking_surface" != "none" \
		|| ! "$target48" =~ ^[0-9]+$ || "$target48" -le 0 \
		|| ! "$game_width" =~ ^[0-9]+$ || "$game_width" -le 0 \
		|| ! "$game_height" =~ ^[0-9]+$ || "$game_height" -le 0 ]]; then
		echo "ERROR: Android Account/logout state or viewport telemetry is invalid" >&2
		echo "$line" >&2
		return 1
	fi

	for field in account report deletion logout cancel confirm; do
		rect="$(android_logout_rect_from_line "$line" "$field")" || return 1
		read -r x y width height <<< "$rect"
		if (( x + width > game_width || y + height > game_height )); then
			echo "ERROR: Android Account/logout $field rect is outside ${game_width}x${game_height}: $rect" >&2
			return 1
		fi
		if [[ "$field" != "account" ]] && (( height < target48 )); then
			echo "ERROR: Android Account/logout $field height=$height is below target48=$target48" >&2
			return 1
		fi
	done
	return 0
}

tap_android_logout_rect_from_line() {
	local line="$1"
	local field="$2"
	local rect x y width height
	rect="$(android_logout_rect_from_line "$line" "$field")" || return 1
	read -r x y width height <<< "$rect"
	tap_client_xy $((x + width / 2)) $((y + height / 2))
}

assert_auth_still_online_for_account_logout() {
	local phase="$1"
	if [[ -z "$AUTH_DB" ]]; then
		return 0
	fi
	if ! wait_auth_online 5; then
		echo "ERROR: Android session disconnected during Account/logout phase: $phase" >&2
		return 1
	fi
	echo "Verified Android session remains online during Account/logout phase: $phase"
}

complete_android_account_logout_from_settings_line() {
	local line="$1"
	local screenshot_prefix="${2:-}"
	local placement logout_x logout_y logout_top logout_bottom
	local event_line

	placement="$(extract_log_value "$line" logoutPlacement)"
	logout_x="$(extract_log_value "$line" logoutX)"
	logout_y="$(extract_log_value "$line" logoutY)"
	logout_top="$(extract_log_value "$line" logoutTop)"
	logout_bottom="$(extract_log_value "$line" logoutBottom)"
	if [[ "$placement" != "settings" \
		|| ! "$logout_x" =~ ^[0-9]+$ || ! "$logout_y" =~ ^[0-9]+$ \
		|| ! "$logout_top" =~ ^[0-9]+$ || ! "$logout_bottom" =~ ^[0-9]+$ ]]; then
		echo "ERROR: Android Account control telemetry is incomplete or not settings-hosted: $line" >&2
		return 1
	fi
	if (( logout_bottom <= logout_top )); then
		echo "ERROR: Android Account/logout geometry is invalid: $line" >&2
		return 1
	fi

	"$ADB" logcat -c || true
	tap_client_xy "$logout_x" "$logout_y"
	event_line="$(wait_for_android_logout_event request 15)" || return 1
	assert_android_logout_layout "$event_line" true false || return 1
	echo "Verified Android Account/logout event: $event_line"
	assert_auth_still_online_for_account_logout "Account sheet open" || return 1
	if [[ -n "$screenshot_prefix" ]]; then
		screenshot "${screenshot_prefix}-account-open"
	fi

	"$ADB" logcat -c || true
	tap_android_logout_rect_from_line "$event_line" logout || return 1
	event_line="$(wait_for_android_logout_event confirm-request 15)" || return 1
	assert_android_logout_layout "$event_line" true true || return 1
	echo "Verified Android Account/logout event: $event_line"
	assert_auth_still_online_for_account_logout "confirmation pending" || return 1
	if [[ -n "$screenshot_prefix" ]]; then
		screenshot "${screenshot_prefix}-logout-confirmation"
	fi

	"$ADB" logcat -c || true
	"$ADB" shell input keyevent BACK
	event_line="$(wait_for_android_logout_event cancel 15)" || return 1
	assert_android_logout_layout "$event_line" true false || return 1
	echo "Verified Android Account/logout event: $event_line"
	assert_auth_still_online_for_account_logout "confirmation cancelled with Back" || return 1
	if [[ -n "$screenshot_prefix" ]]; then
		screenshot "${screenshot_prefix}-logout-cancelled"
	fi

	"$ADB" logcat -c || true
	tap_android_logout_rect_from_line "$event_line" logout || return 1
	event_line="$(wait_for_android_logout_event confirm-request 15)" || return 1
	assert_android_logout_layout "$event_line" true true || return 1
	echo "Verified Android Account/logout event: $event_line"
	assert_auth_still_online_for_account_logout "confirmation reopened" || return 1

	"$ADB" logcat -c || true
	tap_android_logout_rect_from_line "$event_line" confirm || return 1
	event_line="$(wait_for_android_logout_event confirm 15)" || return 1
	assert_android_logout_layout "$event_line" true true || return 1
	echo "Verified Android Account/logout event: $event_line"
}

wait_for_any_settings_rendered() {
	local timeout="${1:-20}"
	local deadline=$((SECONDS + timeout))
	local line visible logout_visible

	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep "ANDROID_SMOKE_SETTINGS " | tail -12 | grep "event=STATE " | tail -1 || true)"
		visible="$(extract_log_value "$line" visible)"
		logout_visible="$(extract_log_value "$line" logoutVisible)"
		if [[ "$visible" == "true" && "$logout_visible" == "true" ]]; then
			echo "$line"
			return 0
		fi
		sleep 1
	done

	echo "ERROR: timed out waiting for Android settings logout state" >&2
	settings_log_tail
	return 1
}

wait_for_network_writer_count() {
	local expected="$1"
	local timeout="${2:-15}"
	local deadline=$((SECONDS + timeout))
	local line active
	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep -E "VOIDSCAPE_NETWORK_WRITER event=(start|stop) " | tail -1 || true)"
		active="$(extract_log_value "$line" active)"
		if [[ "$active" == "$expected" ]]; then
			echo "Verified Android network writer cleanup: $line"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: Android network writers did not settle at active=$expected" >&2
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "VOIDSCAPE_NETWORK_WRITER" | tail -40 >&2 || true
	return 1
}

wait_for_audio_event() {
	local expected_event="$1"
	local expected_active="$2"
	local timeout="${3:-15}"
	local deadline=$((SECONDS + timeout))
	local line active
	while (( SECONDS < deadline )); do
		line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_AUDIO event=$expected_event " | tail -1 || true)"
		active="$(extract_log_value "$line" active)"
		if [[ "$active" == "$expected_active"
			|| ( "$expected_active" == "+" && "$active" =~ ^[0-9]+$ && "$active" -gt 0 ) ]]; then
			echo "Verified Android audio lifecycle: $line"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: timed out waiting for Android audio event=$expected_event active=$expected_active" >&2
	"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_AUDIO" | tail -40 >&2 || true
	return 1
}

wait_for_audio_cycle() {
	wait_for_audio_event started + 15 || return 1
	wait_for_audio_event completed 0 20
}

logout_authenticated_smoke_session() {
	local line
	enable_android_smoke_settings
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent 43
	line="$(wait_for_any_settings_rendered 20)" || {
		disable_android_smoke_settings
		return 1
	}
	assert_settings_logout_visible "$line" || {
		disable_android_smoke_settings
		return 1
	}
	complete_android_account_logout_from_settings_line "$line" || {
		disable_android_smoke_settings
		return 1
	}
	disable_android_smoke_settings
	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || return 1
	wait_for_network_writer_count 0 20
}

graceful_cleanup_authenticated_smoke_session() {
	# Never force-stop a still-online Android fixture and then rewrite its DB row:
	# the server intentionally retains that player for up to ten minutes. Bring
	# the retained task forward and use the player-visible confirmed logout flow.
	if [[ -n "$AUTH_DB" ]] && wait_auth_offline 1 >/dev/null 2>&1; then
		return 0
	fi
	if ! is_resumed_activity "GameActivity"; then
		"$ADB" shell am start -n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater" >/dev/null 2>&1 || true
		wait_for_resumed_activity "GameActivity" 20 || {
			echo "ERROR: could not foreground GameActivity for graceful smoke cleanup" >&2
			return 1
		}
	fi
	logout_authenticated_smoke_session || {
		echo "ERROR: graceful authenticated smoke cleanup could not log out" >&2
		return 1
	}
	return 0
}

wait_for_auth_settings_row() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local expected_sound="$3"
    local timeout="${4:-45}"
    local deadline=$((SECONDS + timeout))
    local row camera_auto mouse_one sound_off

    while (( SECONDS < deadline )); do
        row="$(read_auth_settings)"
        read -r camera_auto mouse_one sound_off <<< "$row"
        if [[ "$camera_auto" == "$expected_camera" && "$mouse_one" == "$expected_mouse" && "$sound_off" == "$expected_sound" ]]; then
            echo "Verified Android settings persistence: cameraauto=$camera_auto onemouse=$mouse_one soundoff=$sound_off"
            return 0
        fi
        sleep 1
    done

    row="$(read_auth_settings || true)"
    echo "ERROR: expected Android settings cameraauto=$expected_camera onemouse=$expected_mouse soundoff=$expected_sound" >&2
    if [[ -n "$row" ]]; then
        echo "Last observed settings row: $row" >&2
    fi
    return 1
}

tap_inventory_tab() {
	local line rect x y width height
	line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
		| grep "ANDROID_SMOKE_HUB_LAYOUT " | tail -1 || true)"
	if [[ -n "$line" ]]; then
		rect="$(mobile_hub_target_rect_from_line "$line" INVENTORY 2>/dev/null || true)"
		if [[ -n "$rect" ]]; then
			read -r x y width height <<< "$rect"
			tap_client_xy $((x + width / 2)) $((y + height / 2))
			return
		fi
	fi
    tap_client_xy 493 19
}

tap_inventory_slot() {
    local slot="$1"
    local coords client_x client_y
    coords="$(wait_for_inventory_target "$slot" 30)" || return 1
    read -r client_x client_y <<< "$coords"
    echo "Android inventory slot $slot at client $client_x,$client_y"
    tap_client_xy "$client_x" "$client_y"
}

select_inventory_item_for_use() {
    local slot="$1"
    tap_inventory_tab
    wait_for_inventory_target "$slot" 30 >/dev/null || return 1
    tap_inventory_slot "$slot" || return 1
    wait_for_inventory_action "$slot" "ITEM_USE" 20 || return 1
    sleep 1
}

assert_soft_keyboard_visible() {
	local state
	state="$("$ADB" shell dumpsys input_method | tr -d '\r')"
	if grep -Eq "mInputShown=true|(^|[[:space:]])inputShown=true" <<< "$state"; then
		return 0
	fi

	echo "ERROR: expected Android soft keyboard to be visible" >&2
	grep -E "mInputShown|mIsInputViewShown|inputShown" <<< "$state" >&2 || true
	return 1
}

assert_soft_keyboard_hidden() {
	local state
	state="$("$ADB" shell dumpsys input_method | tr -d '\r')"
	if grep -Eq "mInputShown=true|(^|[[:space:]])inputShown=true" <<< "$state"; then
		echo "ERROR: expected Android soft keyboard to be hidden" >&2
		grep -E "mInputShown|mIsInputViewShown|inputShown" <<< "$state" >&2 || true
		return 1
	fi
	return 0
}

wait_for_soft_keyboard_visible() {
	local timeout="${1:-15}"
	local deadline=$((SECONDS + timeout))
	local state
	while (( SECONDS < deadline )); do
		state="$("$ADB" shell dumpsys input_method | tr -d '\r')"
		if grep -Eq "mInputShown=true|(^|[[:space:]])inputShown=true" <<< "$state"; then
			echo "Verified Android soft keyboard is visible"
			return 0
		fi
		sleep 1
	done
	assert_soft_keyboard_visible
}

wait_for_soft_keyboard_hidden() {
	local timeout="${1:-15}"
	local deadline=$((SECONDS + timeout))
	local state
	while (( SECONDS < deadline )); do
		state="$("$ADB" shell dumpsys input_method | tr -d '\r')"
		if ! grep -Eq "mInputShown=true|(^|[[:space:]])inputShown=true" <<< "$state"; then
			echo "Verified Android soft keyboard is hidden"
			return 0
		fi
		sleep 1
	done
	assert_soft_keyboard_hidden
}

sql_escape() {
	printf "%s" "$1" | sed "s/'/''/g"
}

read_auth_position() {
	if [[ -z "$AUTH_DB" ]]; then
		return 1
	fi
	if [[ ! -f "$AUTH_DB" ]]; then
		echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
		return 1
	fi
	if ! command -v sqlite3 >/dev/null 2>&1; then
		echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
		return 1
	fi

	local safe_user row
	safe_user="$(sql_escape "$AUTH_USER")"
	row="$(sqlite3 -cmd '.timeout 5000' -noheader -separator ' ' "$AUTH_DB" \
		"select x, y, online from players where lower(username) = lower('$safe_user') limit 1;")"
	if [[ -z "$row" ]]; then
		echo "ERROR: no player row found for $AUTH_USER in $AUTH_DB" >&2
		return 1
	fi
	echo "$row"
}

update_auth_position() {
    local x="$1"
    local y="$2"
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi
    if [[ ! -f "$AUTH_DB" ]]; then
        echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
        return 1
    fi
    if ! command -v sqlite3 >/dev/null 2>&1; then
        echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
        return 1
    fi

    local safe_user
    safe_user="$(sql_escape "$AUTH_USER")"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "update players set x=$x, y=$y, online=0 where lower(username) = lower('$safe_user');"
}

read_auth_group() {
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi
    if [[ ! -f "$AUTH_DB" ]]; then
        echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
        return 1
    fi
    if ! command -v sqlite3 >/dev/null 2>&1; then
        echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
        return 1
    fi

    local safe_user group_id
    safe_user="$(sql_escape "$AUTH_USER")"
    group_id="$(sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
        "select group_id from players where lower(username) = lower('$safe_user') limit 1;")"
    if [[ -z "$group_id" ]]; then
        echo "ERROR: no player group found for $AUTH_USER in $AUTH_DB" >&2
        return 1
    fi
    echo "$group_id"
}

update_auth_group() {
    local group_id="$1"
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi
    if [[ ! -f "$AUTH_DB" ]]; then
        echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
        return 1
    fi
    if ! command -v sqlite3 >/dev/null 2>&1; then
        echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
        return 1
    fi

    local safe_user
    safe_user="$(sql_escape "$AUTH_USER")"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "update players set group_id=$group_id, online=0 where lower(username) = lower('$safe_user');"
}

read_auth_settings() {
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi
    if [[ ! -f "$AUTH_DB" ]]; then
        echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
        return 1
    fi
    if ! command -v sqlite3 >/dev/null 2>&1; then
        echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
        return 1
    fi

    local safe_user row
    safe_user="$(sql_escape "$AUTH_USER")"
    row="$(sqlite3 -cmd '.timeout 5000' -noheader -separator ' ' "$AUTH_DB" \
        "select cameraauto, onemouse, soundoff from players where lower(username) = lower('$safe_user') limit 1;")"
    if [[ -z "$row" ]]; then
        echo "ERROR: no player settings found for $AUTH_USER in $AUTH_DB" >&2
        return 1
    fi
    echo "$row"
}

update_auth_settings() {
    local cameraauto="$1"
    local onemouse="$2"
    local soundoff="$3"
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi
    if [[ ! -f "$AUTH_DB" ]]; then
        echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
        return 1
    fi
    if ! command -v sqlite3 >/dev/null 2>&1; then
        echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
        return 1
    fi

    local safe_user
    safe_user="$(sql_escape "$AUTH_USER")"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "update players set cameraauto=$cameraauto, onemouse=$onemouse, soundoff=$soundoff, online=0 where lower(username) = lower('$safe_user');"
}

auth_ground_loot_cache_key_sql() {
    local hide_key="lb_hide_${AUTH_GROUND_LOOT_ITEM_ID}"
    local add_key="lb_add_${AUTH_GROUND_LOOT_ITEM_ID}"
    printf "'setting_ground_item_names','setting_rare_drop_beams','setting_ground_items','lootbeam_mode','%s','%s'" \
        "$(sql_escape "$hide_key")" "$(sql_escape "$add_key")"
}

snapshot_auth_ground_loot_cache() {
    local player_id="$1"
    local key_sql
    key_sql="$(auth_ground_loot_cache_key_sql)"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert player_cache" \
        "select c.* from player_cache c where c.playerID = $player_id and c.key in ($key_sql) order by c.dbid;" \
        > "$OUT_DIR/auth-ground-loot-cache.sql"
}

restore_auth_ground_loot_cache() {
    local player_id="$1"
    local key_sql
    if [[ -z "$AUTH_DB" || ! -f "$OUT_DIR/auth-ground-loot-cache.sql" ]]; then
        return 0
    fi

    key_sql="$(auth_ground_loot_cache_key_sql)"
    {
        echo ".timeout 5000"
        echo "BEGIN;"
        echo "DELETE FROM player_cache WHERE playerID = $player_id AND key IN ($key_sql);"
        cat "$OUT_DIR/auth-ground-loot-cache.sql"
        echo "UPDATE players SET online = 0 WHERE id = $player_id;"
        echo "COMMIT;"
    } | sqlite3 "$AUTH_DB"
}

set_auth_cache_value() {
    local player_id="$1"
    local type="$2"
    local key="$3"
    local value="$4"
    local safe_key safe_value
    safe_key="$(sql_escape "$key")"
    safe_value="$(sql_escape "$value")"

    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "delete from player_cache where playerID = $player_id and key = '$safe_key';
         insert into player_cache(playerID, type, key, value) values($player_id, $type, '$safe_key', '$safe_value');
         update players set online=0 where id = $player_id;"
}

prepare_auth_ground_loot_cache() {
    local player_id="$1"
    local key_sql
    key_sql="$(auth_ground_loot_cache_key_sql)"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "delete from player_cache where playerID = $player_id and key in ($key_sql);
         update players set online=0 where id = $player_id;"
    set_auth_cache_value "$player_id" 2 "setting_ground_item_names" "true"
    set_auth_cache_value "$player_id" 2 "setting_rare_drop_beams" "true"
    set_auth_cache_value "$player_id" 0 "setting_ground_items" "0"
    set_auth_cache_value "$player_id" 1 "lootbeam_mode" "default"
}

read_auth_player_id() {
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi
    if [[ ! -f "$AUTH_DB" ]]; then
        echo "ERROR: Android auth DB not found at $AUTH_DB" >&2
        return 1
    fi
    if ! command -v sqlite3 >/dev/null 2>&1; then
        echo "ERROR: sqlite3 is required when ANDROID_SMOKE_AUTH_DB is set" >&2
        return 1
    fi

    local safe_user player_id
    safe_user="$(sql_escape "$AUTH_USER")"
    player_id="$(sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
        "select id from players where lower(username) = lower('$safe_user') limit 1;")"
    if [[ -z "$player_id" ]]; then
        echo "ERROR: no player row found for $AUTH_USER in $AUTH_DB" >&2
        return 1
    fi
    echo "$player_id"
}

read_auth_inventory_slot_catalog() {
    local player_id="$1"
    local slot="$2"

    sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
        "select coalesce(s.catalogID, -1)
         from invitems i
         join itemstatuses s on s.itemID = i.itemID
         where i.playerID = $player_id and i.slot = $slot
         limit 1;"
}

read_auth_curstat() {
    local player_id="$1"
    local stat="$2"

    sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
        "select $stat from curstats where playerID = $player_id limit 1;"
}

set_auth_curstat() {
    local player_id="$1"
    local stat="$2"
    local value="$3"

    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "update curstats set $stat = $value where playerID = $player_id;
         update players set online = 0 where id = $player_id;"
}

ensure_auth_pvp_stress_stats() {
    local player_id="$1"

    sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
UPDATE curstats SET hits = 20, strength = 20, magic = CASE WHEN magic < 10 THEN 10 ELSE magic END
WHERE playerID = $player_id;
UPDATE maxstats SET hits = CASE WHEN hits < 40 THEN 40 ELSE hits END,
    strength = CASE WHEN strength < 20 THEN 20 ELSE strength END,
    magic = CASE WHEN magic < 10 THEN 10 ELSE magic END
WHERE playerID = $player_id;
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
}

wait_for_auth_inventory_slot_not_catalog() {
    local player_id="$1"
    local slot="$2"
    local original_catalog_id="$3"
    local timeout="${4:-45}"
    local deadline=$((SECONDS + timeout))
    local observed

    while (( SECONDS < deadline )); do
        observed="$(read_auth_inventory_slot_catalog "$player_id" "$slot" || true)"
        if [[ -z "$observed" || "$observed" != "$original_catalog_id" ]]; then
            echo "Verified Android inventory slot $slot changed from $original_catalog_id to ${observed:-empty}"
            return 0
        fi
        sleep 1
    done

    observed="$(read_auth_inventory_slot_catalog "$player_id" "$slot" || true)"
    echo "ERROR: expected inventory slot $slot to change from catalog $original_catalog_id; observed ${observed:-empty}" >&2
    return 1
}

wait_for_auth_curstat_greater_than() {
    local player_id="$1"
    local stat="$2"
    local baseline="$3"
    local timeout="${4:-45}"
    local deadline=$((SECONDS + timeout))
    local observed

    while (( SECONDS < deadline )); do
        observed="$(read_auth_curstat "$player_id" "$stat" || true)"
        if [[ "$observed" =~ ^[0-9]+$ && "$observed" -gt "$baseline" ]]; then
            echo "Verified Android stat change: $stat $baseline -> $observed"
            return 0
        fi
        sleep 1
    done

    observed="$(read_auth_curstat "$player_id" "$stat" || true)"
    echo "ERROR: expected $stat to increase above $baseline; observed ${observed:-unknown}" >&2
    return 1
}

clear_auth_tutorial_appearance() {
    if [[ -z "$AUTH_DB" ]]; then
        return 0
    fi

    local player_id
    player_id="$(read_auth_player_id)" || return 1
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "delete from player_cache where playerID = $player_id and key = 'tutorial_appearance';"
}

snapshot_auth_inventory() {
    local player_id="$1"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert itemstatuses" \
        "select s.* from itemstatuses s join invitems i on i.itemID = s.itemID where i.playerID = $player_id order by i.slot;" \
        > "$OUT_DIR/auth-inventory-itemstatuses.sql"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert invitems" \
        "select i.* from invitems i where i.playerID = $player_id order by i.slot;" \
        > "$OUT_DIR/auth-inventory-items.sql"
}

reset_auth_itemstatus_sequence() {
    if [[ -z "$AUTH_DB" ]]; then
        return 0
    fi

    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
        "update sqlite_sequence set seq = (select coalesce(max(itemID), 0) from itemstatuses) where name = 'itemstatuses';" \
        >/dev/null 2>&1 || true
}

auth_table_exists() {
    local table_name="$1"
    sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
        "select count(*) from sqlite_master where type = 'table' and name = '$table_name';" 2>/dev/null
}

restore_auth_inventory() {
    local player_id="$1"
    if [[ -z "$AUTH_DB" || ! -f "$OUT_DIR/auth-inventory-itemstatuses.sql" || ! -f "$OUT_DIR/auth-inventory-items.sql" ]]; then
        return 0
    fi

    {
        echo ".timeout 5000"
        echo "BEGIN;"
        echo "DELETE FROM itemstatuses WHERE itemID IN (SELECT itemID FROM invitems WHERE playerID = $player_id);"
        echo "DELETE FROM invitems WHERE playerID = $player_id;"
        cat "$OUT_DIR/auth-inventory-itemstatuses.sql"
        cat "$OUT_DIR/auth-inventory-items.sql"
        echo "UPDATE players SET online = 0 WHERE id = $player_id;"
        echo "COMMIT;"
    } | sqlite3 "$AUTH_DB"
    reset_auth_itemstatus_sequence
}

snapshot_auth_bank() {
    local player_id="$1"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert itemstatuses" \
        "select s.* from itemstatuses s join bank b on b.itemID = s.itemID where b.playerID = $player_id order by b.slot;" \
        > "$OUT_DIR/auth-bank-itemstatuses.sql"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert bank" \
        "select b.* from bank b where b.playerID = $player_id order by b.slot;" \
        > "$OUT_DIR/auth-bank-items.sql"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert bankpresets" \
        "select bp.* from bankpresets bp where bp.playerID = $player_id order by bp.slot;" \
        > "$OUT_DIR/auth-bank-presets.sql"
}

restore_auth_bank() {
    local player_id="$1"
    if [[ -z "$AUTH_DB" || ! -f "$OUT_DIR/auth-bank-itemstatuses.sql" || ! -f "$OUT_DIR/auth-bank-items.sql" || ! -f "$OUT_DIR/auth-bank-presets.sql" ]]; then
        return 0
    fi

    {
        echo ".timeout 5000"
        echo "BEGIN;"
        echo "DELETE FROM itemstatuses WHERE itemID IN (SELECT itemID FROM bank WHERE playerID = $player_id);"
        echo "DELETE FROM bank WHERE playerID = $player_id;"
        echo "DELETE FROM bankpresets WHERE playerID = $player_id;"
        cat "$OUT_DIR/auth-bank-itemstatuses.sql"
        cat "$OUT_DIR/auth-bank-items.sql"
        cat "$OUT_DIR/auth-bank-presets.sql"
        echo "UPDATE players SET online = 0 WHERE id = $player_id;"
        echo "COMMIT;"
    } | sqlite3 "$AUTH_DB"
    reset_auth_itemstatus_sequence
}

snapshot_auth_equipment() {
    local player_id="$1"
    if [[ "$(auth_table_exists equipped)" != "1" ]]; then
        : > "$OUT_DIR/auth-equipment-itemstatuses.sql"
        : > "$OUT_DIR/auth-equipment-items.sql"
        return 0
    fi

    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert itemstatuses" \
        "select s.* from itemstatuses s join equipped e on e.itemID = s.itemID where e.playerID = $player_id order by e.itemID;" \
        > "$OUT_DIR/auth-equipment-itemstatuses.sql"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert equipped" \
        "select e.* from equipped e where e.playerID = $player_id order by e.itemID;" \
        > "$OUT_DIR/auth-equipment-items.sql"
}

restore_auth_equipment() {
    local player_id="$1"
    if [[ -z "$AUTH_DB" || ! -f "$OUT_DIR/auth-equipment-itemstatuses.sql" || ! -f "$OUT_DIR/auth-equipment-items.sql" ]]; then
        return 0
    fi
    if [[ "$(auth_table_exists equipped)" != "1" ]]; then
        sqlite3 -cmd '.timeout 5000' "$AUTH_DB" \
            "UPDATE players SET online = 0 WHERE id = $player_id;" >/dev/null 2>&1 || true
        return 0
    fi

    {
        echo ".timeout 5000"
        echo "BEGIN;"
        echo "DELETE FROM itemstatuses WHERE itemID IN (SELECT itemID FROM equipped WHERE playerID = $player_id);"
        echo "DELETE FROM equipped WHERE playerID = $player_id;"
        cat "$OUT_DIR/auth-equipment-itemstatuses.sql"
        cat "$OUT_DIR/auth-equipment-items.sql"
        echo "UPDATE players SET online = 0 WHERE id = $player_id;"
        echo "COMMIT;"
    } | sqlite3 "$AUTH_DB"
    reset_auth_itemstatus_sequence
}

snapshot_auth_stats() {
    local player_id="$1"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert curstats" \
        "select c.* from curstats c where c.playerID = $player_id;" \
        > "$OUT_DIR/auth-curstats.sql"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert maxstats" \
        "select m.* from maxstats m where m.playerID = $player_id;" \
        > "$OUT_DIR/auth-maxstats.sql"
    sqlite3 -cmd '.timeout 5000' "$AUTH_DB" ".mode insert experience" \
        "select e.* from experience e where e.playerID = $player_id;" \
        > "$OUT_DIR/auth-experience.sql"
}

restore_auth_stats() {
    local player_id="$1"
    if [[ -z "$AUTH_DB" || ! -f "$OUT_DIR/auth-curstats.sql" || ! -f "$OUT_DIR/auth-maxstats.sql" || ! -f "$OUT_DIR/auth-experience.sql" ]]; then
        return 0
    fi

    {
        echo ".timeout 5000"
        echo "BEGIN;"
        echo "DELETE FROM curstats WHERE playerID = $player_id;"
        echo "DELETE FROM maxstats WHERE playerID = $player_id;"
        echo "DELETE FROM experience WHERE playerID = $player_id;"
        cat "$OUT_DIR/auth-curstats.sql"
        cat "$OUT_DIR/auth-maxstats.sql"
        cat "$OUT_DIR/auth-experience.sql"
        echo "UPDATE players SET online = 0 WHERE id = $player_id;"
        echo "COMMIT;"
    } | sqlite3 "$AUTH_DB"
}

ensure_auth_magic_prayer_stats() {
    local player_id="$1"
    sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
UPDATE curstats SET prayer = CASE WHEN prayer < 1 THEN 1 ELSE prayer END,
    magic = CASE WHEN magic < 10 THEN 10 ELSE magic END
WHERE playerID = $player_id;
UPDATE maxstats SET prayer = CASE WHEN prayer < 1 THEN 1 ELSE prayer END,
    magic = CASE WHEN magic < 10 THEN 10 ELSE magic END
WHERE playerID = $player_id;
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
}

clear_auth_equipment() {
    local player_id="$1"
    if [[ "$(auth_table_exists equipped)" != "1" ]]; then
        sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
UPDATE itemstatuses
SET wielded = 0
WHERE itemID IN (SELECT itemID FROM invitems WHERE playerID = $player_id);
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
        return 0
    fi

    sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
DELETE FROM itemstatuses WHERE itemID IN (SELECT itemID FROM equipped WHERE playerID = $player_id);
DELETE FROM equipped WHERE playerID = $player_id;
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
}

clear_auth_bank_and_presets() {
    local player_id="$1"
    sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
DELETE FROM itemstatuses WHERE itemID IN (SELECT itemID FROM bank WHERE playerID = $player_id);
DELETE FROM bank WHERE playerID = $player_id;
DELETE FROM bankpresets WHERE playerID = $player_id;
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
}

seed_auth_bank_slot() {
    local player_id="$1"
    local slot="$2"
    local catalog_id="$3"
    local amount="$4"

    sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
DELETE FROM itemstatuses WHERE itemID IN (
    SELECT itemID FROM bank WHERE playerID = $player_id AND slot = $slot
);
DELETE FROM bank WHERE playerID = $player_id AND slot = $slot;
INSERT INTO itemstatuses(itemID, catalogID, amount, noted, wielded, durability, kill_log)
VALUES(
    (
        SELECT CASE
            WHEN coalesce(max(itemID), 0) + 1 < $AUTH_FIXTURE_ITEM_ID_BASE THEN $AUTH_FIXTURE_ITEM_ID_BASE
            ELSE coalesce(max(itemID), 0) + 1
        END
        FROM itemstatuses
    ),
    $catalog_id, $amount, 0, 0, 100, NULL
);
INSERT INTO bank(playerID, itemID, slot)
VALUES($player_id, (SELECT max(itemID) FROM itemstatuses), $slot);
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
}

seed_auth_bank_fixture() {
    local player_id="$1"
    local slot catalog_id

    clear_auth_bank_and_presets "$player_id"
    seed_auth_bank_slot "$player_id" 0 "$AUTH_BANK_ITEM_ID" "$AUTH_BANK_ITEM_AMOUNT"
    for ((slot = 1; slot < AUTH_BANK_FIXTURE_BANK_SLOTS; slot++)); do
        catalog_id=$((AUTH_BANK_FIXTURE_START_ITEM_ID + slot - 1))
        seed_auth_bank_slot "$player_id" "$slot" "$catalog_id" 1
    done
}

seed_auth_inventory_slot() {
    local player_id="$1"
    local slot="$2"
    local catalog_id="$3"
    local amount="$4"

    sqlite3 "$AUTH_DB" <<SQL
.timeout 5000
BEGIN;
DELETE FROM itemstatuses WHERE itemID IN (
    SELECT itemID FROM invitems WHERE playerID = $player_id AND slot = $slot
);
DELETE FROM invitems WHERE playerID = $player_id AND slot = $slot;
INSERT INTO itemstatuses(itemID, catalogID, amount, noted, wielded, durability, kill_log)
VALUES(
    (
        SELECT CASE
            WHEN coalesce(max(itemID), 0) + 1 < $AUTH_FIXTURE_ITEM_ID_BASE THEN $AUTH_FIXTURE_ITEM_ID_BASE
            ELSE coalesce(max(itemID), 0) + 1
        END
        FROM itemstatuses
    ),
    $catalog_id, $amount, 0, 0, 100, NULL
);
INSERT INTO invitems(playerID, itemID, slot)
VALUES($player_id, (SELECT max(itemID) FROM itemstatuses), $slot);
UPDATE players SET online = 0 WHERE id = $player_id;
COMMIT;
SQL
}

wait_auth_offline() {
    if [[ -z "$AUTH_DB" ]]; then
        return 0
    fi

    local timeout="${1:-45}"
    local deadline=$((SECONDS + timeout))
    local current current_x current_y current_online
    while (( SECONDS < deadline )); do
        current="$(read_auth_position)"
        read -r current_x current_y current_online <<< "$current"
        if [[ "$current_online" == "0" ]]; then
            return 0
        fi
        sleep 1
    done

    current="$(read_auth_position || true)"
    echo "ERROR: expected $AUTH_USER to be offline before DB positioning" >&2
    if [[ -n "$current" ]]; then
        echo "Last observed DB position: $current" >&2
    fi
    return 1
}

wait_auth_online() {
    if [[ -z "$AUTH_DB" ]]; then
        return 1
    fi

    local timeout="${1:-45}"
    local deadline=$((SECONDS + timeout))
    local current current_x current_y current_online
    while (( SECONDS < deadline )); do
        current="$(read_auth_position)"
        read -r current_x current_y current_online <<< "$current"
        if [[ "$current_online" == "1" ]]; then
            return 0
        fi
        sleep 1
    done

    current="$(read_auth_position || true)"
    echo "ERROR: expected $AUTH_USER to be online after login" >&2
    if [[ -n "$current" ]]; then
        echo "Last observed DB position: $current" >&2
    fi
    return 1
}

wait_auth_chat_logged() {
	local expected_message="$1"
	local baseline_id="${2:-0}"
	local timeout="${3:-30}"
	local deadline=$((SECONDS + timeout))
	local safe_user safe_message count
	safe_user="$(sql_escape "$AUTH_USER")"
	safe_message="$(sql_escape "$expected_message")"
	while (( SECONDS < deadline )); do
		count="$(sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
			"select count(*) from chat_logs where id > $baseline_id and lower(sender)=lower('$safe_user') and lower(message)=lower('$safe_message');" 2>/dev/null || true)"
		if [[ "$count" =~ ^[0-9]+$ ]] && (( count > 1 )); then
			echo "ERROR: server persisted post-resume chat marker '$expected_message' $count times" >&2
			return 1
		fi
		if [[ "$count" == "1" ]]; then
			# Let the asynchronous logger settle so a duplicate send cannot arrive
			# immediately after the first successful query.
			sleep 2
			count="$(sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
				"select count(*) from chat_logs where id > $baseline_id and lower(sender)=lower('$safe_user') and lower(message)=lower('$safe_message');" 2>/dev/null || true)"
			if [[ "$count" == "1" ]]; then
				echo "Verified exactly one post-resume client/server round trip in chat_logs: $expected_message"
				return 0
			fi
			echo "ERROR: post-resume chat marker '$expected_message' settled at count=${count:-unknown}" >&2
			return 1
		fi
		sleep 1
	done
	echo "ERROR: server did not persist post-resume chat marker '$expected_message'" >&2
	return 1
}

read_auth_chat_log_max_id() {
	if [[ -z "$AUTH_DB" || ! -f "$AUTH_DB" ]]; then
		return 1
	fi
	sqlite3 -cmd '.timeout 5000' -noheader "$AUTH_DB" \
		"select coalesce(max(id), 0) from chat_logs;"
}

wait_auth_position() {
    local expected_x="$1"
    local expected_y="$2"
    local timeout="${3:-45}"
    local deadline=$((SECONDS + timeout))
    local current current_x current_y current_online

    while (( SECONDS < deadline )); do
        current="$(read_auth_position)"
        read -r current_x current_y current_online <<< "$current"
        if [[ "$current_x" == "$expected_x" && "$current_y" == "$expected_y" ]]; then
            echo "Verified Android auth position: $current_x,$current_y online=$current_online"
            return 0
        fi
        sleep 1
    done

    current="$(read_auth_position || true)"
    echo "ERROR: expected $AUTH_USER position $expected_x,$expected_y" >&2
    if [[ -n "$current" ]]; then
        echo "Last observed DB position: $current" >&2
    fi
    return 1
}

assert_auth_position_changed_after_logout() {
	local before="$1"
	if [[ -z "$AUTH_DB" || -z "$before" ]]; then
		return 0
	fi

	local before_x before_y before_online current current_x current_y current_online
	read -r before_x before_y before_online <<< "$before"
	local deadline=$((SECONDS + 45))
	while (( SECONDS < deadline )); do
		current="$(read_auth_position)"
		read -r current_x current_y current_online <<< "$current"
		if [[ "$current_online" == "0" && ( "$current_x" != "$before_x" || "$current_y" != "$before_y" ) ]]; then
			echo "Verified Android terrain tap moved $AUTH_USER: $before_x,$before_y -> $current_x,$current_y"
			return 0
		fi
		sleep 1
	done

	current="$(read_auth_position || true)"
	echo "ERROR: expected Android terrain tap to move $AUTH_USER from $before_x,$before_y before logout" >&2
	if [[ -n "$current" ]]; then
		echo "Last observed DB position: $current" >&2
	fi
	return 1
}

run_authenticated_logout_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    "$ADB" logcat -c || true
    "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
    launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
    screenshot 28-auth-login-home
    tap_existing_user_button
    sleep 3
    enter_auth_credentials
    screenshot 29-auth-credentials-entered
	submit_login_and_wait || exit 1
	sleep 8
	screenshot 30-auth-post-login-initial
	tap_pct 50 72
	sleep 2
	screenshot 31-auth-welcome-or-game
	tap_pct 50 72
	sleep 1
	screenshot 32-auth-game-hud
	local auth_position_before_walk=""
	if [[ -n "$AUTH_DB" ]]; then
		auth_position_before_walk="$(read_auth_position)"
		echo "Android auth position before terrain tap: $auth_position_before_walk"
	fi
	screenshot 33-auth-before-terrain-tap
	tap_pct 38 61
	sleep 8
	screenshot 34-auth-after-terrain-tap
	enable_android_smoke_settings
	"$ADB" logcat -c || true
	"$ADB" shell input keyevent 43
	local settings_line
	settings_line="$(wait_for_any_settings_rendered 20)" || exit 1
	assert_settings_logout_visible "$settings_line" || exit 1
	screenshot 35-auth-settings-open
	complete_android_account_logout_from_settings_line "$settings_line" "35-auth" || exit 1
	sleep 10
	disable_android_smoke_settings
	if [[ -n "$AUTH_DB" ]]; then
		wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || exit 1
	fi
    screenshot 36-auth-after-logout-login-home
    tap_existing_user_button
    sleep 3
    assert_soft_keyboard_visible
    screenshot 37-auth-after-logout-existing-user-keyboard
	assert_auth_position_changed_after_logout "$auth_position_before_walk"
    "$ADB" shell input keyevent BACK
    sleep 1
}

run_authenticated_npc_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android NPC tap smoke; ANDROID_SMOKE_AUTH_DB is required to position the NPC fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android NPC smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        screenshot 38-auth-before-npc-tap
        tap_npc_target "$AUTH_NPC_ID" || exit 1
        wait_for_npc_action "$AUTH_NPC_ID" "$AUTH_NPC_ACTION" 20 || exit 1
        sleep 2
        screenshot 39-auth-after-npc-tap
    ) || status=$?

    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_object_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android object tap smoke; ANDROID_SMOKE_AUTH_DB is required to position the object fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android object smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_OBJECT_PLAYER_X,$AUTH_OBJECT_PLAYER_Y"
    update_auth_position "$AUTH_OBJECT_PLAYER_X" "$AUTH_OBJECT_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_object_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_object_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        screenshot 40-auth-before-object-tap
        tap_object_target "$AUTH_OBJECT_ID" || exit 1
        wait_for_object_action "$AUTH_OBJECT_ID" "$AUTH_OBJECT_ACTION" 20 || exit 1
        sleep 2
        screenshot 41-auth-after-object-tap
    ) || status=$?

    disable_android_smoke_object_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_inventory_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android inventory tap smoke; ANDROID_SMOKE_AUTH_DB is required to seed and restore the inventory fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local player_id status
    player_id="$(read_auth_player_id)"
    if [[ "$AUTH_INVENTORY_SLOT" == "$AUTH_ITEM_ON_ITEM_TARGET_SLOT" ]]; then
        echo "ERROR: Android item-on-item smoke needs distinct source and target slots" >&2
        return 1
    fi
    snapshot_auth_inventory "$player_id"
    echo "Android inventory smoke seeding slot $AUTH_INVENTORY_SLOT with item $AUTH_INVENTORY_ITEM_ID x$AUTH_INVENTORY_ITEM_AMOUNT"
    seed_auth_inventory_slot "$player_id" "$AUTH_INVENTORY_SLOT" "$AUTH_INVENTORY_ITEM_ID" "$AUTH_INVENTORY_ITEM_AMOUNT"
    echo "Android item-on-item smoke seeding target slot $AUTH_ITEM_ON_ITEM_TARGET_SLOT with item $AUTH_ITEM_ON_ITEM_TARGET_ID x$AUTH_ITEM_ON_ITEM_TARGET_AMOUNT"
    seed_auth_inventory_slot "$player_id" "$AUTH_ITEM_ON_ITEM_TARGET_SLOT" "$AUTH_ITEM_ON_ITEM_TARGET_ID" "$AUTH_ITEM_ON_ITEM_TARGET_AMOUNT"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
		close_auth_intro_dialog_if_present
        sleep 2
        tap_inventory_tab
        wait_for_inventory_target "$AUTH_INVENTORY_SLOT" 30 >/dev/null || exit 1
        screenshot 42-auth-before-inventory-tap
        tap_inventory_slot "$AUTH_INVENTORY_SLOT" || exit 1
        wait_for_inventory_action "$AUTH_INVENTORY_SLOT" "$AUTH_INVENTORY_ACTION" 20 || exit 1
        sleep 2
        screenshot 43-auth-after-inventory-tap
        tap_inventory_tab
        wait_for_inventory_target "$AUTH_ITEM_ON_ITEM_TARGET_SLOT" 30 >/dev/null || exit 1
        screenshot 44-auth-before-item-on-item
        tap_inventory_slot "$AUTH_ITEM_ON_ITEM_TARGET_SLOT" || exit 1
        wait_for_inventory_action "$AUTH_ITEM_ON_ITEM_TARGET_SLOT" "$AUTH_ITEM_ON_ITEM_ACTION" 20 || exit 1
        sleep 2
        screenshot 45-auth-after-item-on-item
    ) || status=$?

    disable_android_smoke_inventory_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    sleep 1
    return "$status"
}

run_authenticated_item_on_object_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android item-on-object smoke; ANDROID_SMOKE_AUTH_DB is required to seed inventory and position the object fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online player_id status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
    snapshot_auth_inventory "$player_id"
    echo "Android item-on-object smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_OBJECT_PLAYER_X,$AUTH_OBJECT_PLAYER_Y"
    update_auth_position "$AUTH_OBJECT_PLAYER_X" "$AUTH_OBJECT_PLAYER_Y"
    echo "Android item-on-object smoke seeding source slot $AUTH_ITEM_ON_TARGET_SLOT with item $AUTH_ITEM_ON_TARGET_ITEM_ID x$AUTH_ITEM_ON_TARGET_ITEM_AMOUNT"
    seed_auth_inventory_slot "$player_id" "$AUTH_ITEM_ON_TARGET_SLOT" "$AUTH_ITEM_ON_TARGET_ITEM_ID" "$AUTH_ITEM_ON_TARGET_ITEM_AMOUNT"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_object_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        enable_android_smoke_object_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        select_inventory_item_for_use "$AUTH_ITEM_ON_TARGET_SLOT" || exit 1
        wait_for_object_target "$AUTH_OBJECT_ID" 30 >/dev/null || exit 1
        screenshot 46-auth-before-item-on-object
        tap_object_target "$AUTH_OBJECT_ID" || exit 1
        wait_for_object_action "$AUTH_OBJECT_ID" "$AUTH_ITEM_ON_OBJECT_ACTION" 20 || exit 1
        sleep 2
        screenshot 47-auth-after-item-on-object
    ) || status=$?

    disable_android_smoke_inventory_targets
    disable_android_smoke_object_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_item_on_npc_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android item-on-NPC smoke; ANDROID_SMOKE_AUTH_DB is required to seed inventory and position the NPC fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online player_id status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
    snapshot_auth_inventory "$player_id"
    echo "Android item-on-NPC smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"
    echo "Android item-on-NPC smoke seeding source slot $AUTH_ITEM_ON_TARGET_SLOT with item $AUTH_ITEM_ON_TARGET_ITEM_ID x$AUTH_ITEM_ON_TARGET_ITEM_AMOUNT"
    seed_auth_inventory_slot "$player_id" "$AUTH_ITEM_ON_TARGET_SLOT" "$AUTH_ITEM_ON_TARGET_ITEM_ID" "$AUTH_ITEM_ON_TARGET_ITEM_AMOUNT"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        select_inventory_item_for_use "$AUTH_ITEM_ON_TARGET_SLOT" || exit 1
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 48-auth-before-item-on-npc
        tap_npc_target "$AUTH_NPC_ID" || exit 1
        wait_for_npc_action "$AUTH_NPC_ID" "$AUTH_ITEM_ON_NPC_ACTION" 20 || exit 1
        sleep 2
        screenshot 49-auth-after-item-on-npc
    ) || status=$?

    disable_android_smoke_inventory_targets
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_context_menu_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android context menu smoke; ANDROID_SMOKE_AUTH_DB is required to position the NPC fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android context menu smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
	    enter_auth_credentials
	    submit_login_and_wait || exit 1
	    sleep 8
	    close_auth_intro_dialog_if_present
	    wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 50-auth-before-context-long-press
        long_press_npc_target "$AUTH_NPC_ID" 1200 || exit 1
        local menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y
        local menu_values
        menu_values="$(wait_for_context_menu 20)" || exit 1
        read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
        if [[ "$first_action" != "$AUTH_CONTEXT_MENU_FIRST_ACTION" ]]; then
            echo "ERROR: expected Android context menu first action $AUTH_CONTEXT_MENU_FIRST_ACTION, got $first_action" >&2
            "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU" | tail -10 >&2 || true
            exit 1
        fi
        sleep 1
        screenshot 51-auth-context-menu-open
        tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" 0 || exit 1
        wait_for_npc_action "$AUTH_NPC_ID" "$AUTH_CONTEXT_MENU_FIRST_ACTION" 20 || exit 1
        sleep 2
        screenshot 52-auth-after-context-menu-selection
    ) || status=$?

    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_edge_context_menu_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android edge context menu smoke; ANDROID_SMOKE_AUTH_DB is required to position the fixture."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android edge context menu smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        screenshot 53-auth-before-edge-context-long-press
        "$ADB" logcat -c || true
        long_press_client_xy "$AUTH_EDGE_MENU_CLIENT_X" "$AUTH_EDGE_MENU_CLIENT_Y" 1200
        local menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y
        local menu_values
        menu_values="$(wait_for_context_menu 20)" || exit 1
        read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
        if [[ "$first_action" != "$AUTH_EDGE_MENU_ACTION" ]]; then
            echo "ERROR: expected Android edge context menu first action $AUTH_EDGE_MENU_ACTION, got $first_action" >&2
            "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CONTEXT_MENU" | tail -10 >&2 || true
            exit 1
        fi
        assert_context_menu_clamped "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_mouse_x" "$menu_mouse_y" || exit 1
        sleep 1
        screenshot 54-auth-edge-context-menu-open
        tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" 0 || exit 1
        wait_for_context_menu_action "$AUTH_EDGE_MENU_ACTION" 20 || exit 1
        sleep 2
        screenshot 55-auth-after-edge-context-menu-selection
    ) || status=$?

    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_camera_rotate_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position="" original_x="" original_y="" original_online="" status
    if [[ -n "$AUTH_DB" ]]; then
        original_position="$(read_auth_position)"
        read -r original_x original_y original_online <<< "$original_position"
        echo "Android camera rotate smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
        update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"
    fi

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_camera
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_camera
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
		close_auth_intro_dialog_if_present
        sleep 2
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 56-auth-before-camera-rotate
        "$ADB" logcat -c || true
        swipe_client_xy "$AUTH_CAMERA_SWIPE_START_X" "$AUTH_CAMERA_SWIPE_Y" "$AUTH_CAMERA_SWIPE_END_X" "$AUTH_CAMERA_SWIPE_Y" "$AUTH_CAMERA_SWIPE_DURATION_MS"
		wait_for_camera_gesture_route camera 10 || exit 1
        wait_for_camera_rotate 20 || exit 1
        sleep 2
        screenshot 57-auth-after-camera-rotate

		enable_android_smoke_settings
		"$ADB" logcat -c || true
		"$ADB" shell input keyevent 43
		wait_for_any_settings_rendered 20 >/dev/null || exit 1
		"$ADB" logcat -c || true
		swipe_client_xy "$AUTH_PANEL_SWIPE_X" "$AUTH_PANEL_SWIPE_START_Y" "$AUTH_PANEL_SWIPE_X" "$AUTH_PANEL_SWIPE_END_Y" "$AUTH_PANEL_SWIPE_DURATION_MS"
		wait_for_camera_gesture_route scroll 10 || exit 1
		assert_camera_gesture_unchanged || exit 1
		sleep 2
		if "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep -q "ANDROID_SMOKE_CAMERA_ROTATE"; then
			echo "ERROR: Android settings-panel swipe also changed the world camera" >&2
			"$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_ROTATE" | tail -20 >&2 || true
			exit 1
		fi
		screenshot 57a-auth-settings-swipe-no-camera
		logout_authenticated_smoke_session || exit 1
    ) || status=$?

    disable_android_smoke_camera
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    if [[ -n "$AUTH_DB" && -n "$original_x" && -n "$original_y" ]]; then
        update_auth_position "$original_x" "$original_y" || true
    fi
    sleep 1
    return "$status"
}

run_authenticated_zoom_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position="" original_x="" original_y="" original_online="" status
    if [[ -n "$AUTH_DB" ]]; then
        original_position="$(read_auth_position)"
        read -r original_x original_y original_online <<< "$original_position"
        echo "Android zoom smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
        update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"
    fi

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_zoom
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_zoom
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 58-auth-before-zoom
        "$ADB" logcat -c || true
        swipe_client_xy "$AUTH_ZOOM_DRAG_X" "$AUTH_ZOOM_DRAG_START_Y" "$AUTH_ZOOM_DRAG_X" "$AUTH_ZOOM_DRAG_END_Y" "$AUTH_ZOOM_DRAG_DURATION_MS"
		wait_for_camera_gesture_route camera 10 || exit 1
		assert_one_finger_zoom_unchanged || exit 1
        assert_no_zoom_change_logged 4 || exit 1
        screenshot 59-auth-after-one-finger-no-zoom
        if [[ "$AUTH_ZOOM_MANUAL_PINCH_SECONDS" =~ ^[0-9]+$ && "$AUTH_ZOOM_MANUAL_PINCH_SECONDS" -gt 0 ]]; then
            echo "Android zoom smoke waiting ${AUTH_ZOOM_MANUAL_PINCH_SECONDS}s for a real physical two-finger pinch"
            "$ADB" logcat -c || true
            sleep "$AUTH_ZOOM_MANUAL_PINCH_SECONDS"
            wait_for_zoom_change 10 || exit 1
            sleep 2
            screenshot 60-auth-after-manual-pinch-zoom
        elif [[ "$AUTH_ZOOM_REQUIRE_PINCH" == "1" ]]; then
            echo "ERROR: ANDROID_SMOKE_REQUIRE_PINCH=1 needs ANDROID_SMOKE_MANUAL_PINCH_SECONDS on a physical device; ADB input cannot synthesize a real pinch" >&2
            exit 1
        else
            echo "WARNING: Android physical two-finger pinch QA was not run; set ANDROID_SMOKE_MANUAL_PINCH_SECONDS with ANDROID_SMOKE_REQUIRE_PINCH=1 on a real device."
        fi
		logout_authenticated_smoke_session || exit 1
    ) || status=$?

    disable_android_smoke_zoom
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    if [[ -n "$AUTH_DB" && -n "$original_x" && -n "$original_y" ]]; then
        update_auth_position "$original_x" "$original_y" || true
    fi
    sleep 1
    return "$status"
}

run_authenticated_chat_tab_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local status
    force_android_portrait || {
        echo "ERROR: Android mobile hub smoke could not force portrait rotation" >&2
        return 1
    }

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_chat_tabs
        enable_android_smoke_walk
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
		# uiautomator can restore the emulator's pre-smoke rotation after tapping
		# the native launcher. Reassert portrait once the shared client owns input.
        force_android_portrait || exit 1
        enable_android_smoke_chat_tabs
        enable_android_smoke_walk
        tap_existing_user_button
        sleep 3
		enter_auth_credentials
		submit_login_and_wait || exit 1
		sleep 8
		close_auth_intro_dialog_if_present
		sleep 2

		local portrait_viewport_line portrait_hub_line active_hub_line
		local landscape_viewport_line landscape_hub_line action_line
		local key expected_panel expected_side current_panel current_side subtab
		local -a hub_keys=(STATS MAP SOCIAL SETTINGS INVENTORY MAGIC PRAYER)
		local -a stats_subtabs=(QUESTS LOOT BEASTS)
		portrait_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
		portrait_hub_line="$(wait_for_mobile_hub_layout portrait 30)" || exit 1
		current_panel="$(extract_log_value "$portrait_hub_line" panel)"
		current_side="$(extract_log_value "$portrait_hub_line" panelSide)"
		assert_mobile_hub_layout "$portrait_hub_line" "$portrait_viewport_line" \
			portrait "$current_panel" "$current_side" || exit 1
		screenshot 60a-auth-mobile-hub-portrait

		for key in "${hub_keys[@]}"; do
			expected_panel="$(mobile_hub_expected_panel "$key")" || exit 1
			expected_side="$(mobile_hub_expected_side "$key")" || exit 1
			"$ADB" logcat -c || true
			tap_mobile_hub_control_from_layout "$portrait_hub_line" "$key" \
				"$portrait_viewport_line" || exit 1
			action_line="$(wait_for_mobile_hub_action "$key" "$expected_panel" "$expected_side" 20)" || exit 1
			echo "Verified Android portrait mobile hub action: $action_line"
			active_hub_line="$(wait_for_mobile_hub_panel portrait "$expected_panel" "$expected_side" 15)" || exit 1
			assert_mobile_hub_layout "$active_hub_line" "$portrait_viewport_line" \
				portrait "$expected_panel" "$expected_side" || exit 1
			assert_no_mobile_hub_fallthrough "$key" 2 || exit 1
			portrait_hub_line="$active_hub_line"
			sleep 1
			screenshot "60b-auth-mobile-hub-portrait-$(echo "$key" | tr '[:upper:]' '[:lower:]')"
			if [[ "$key" == "STATS" ]]; then
				for subtab in "${stats_subtabs[@]}"; do
					"$ADB" logcat -c || true
					tap_mobile_stats_subtab_from_layout "$active_hub_line" "$subtab" \
						"$portrait_viewport_line" || exit 1
					active_hub_line="$(wait_for_mobile_stats_subtab "$subtab" 20)" || exit 1
					assert_mobile_stats_subtab_layout "$active_hub_line" \
						"$portrait_viewport_line" "$subtab" || exit 1
					case "$subtab" in
						QUESTS) screenshot 60ba-auth-mobile-hub-portrait-quests ;;
						LOOT) screenshot 60bb-auth-mobile-hub-portrait-loot ;;
						BEASTS) screenshot 60bc-auth-mobile-hub-portrait-beasts ;;
					esac
				done
				"$ADB" logcat -c || true
				tap_mobile_stats_subtab_from_layout "$active_hub_line" STATS \
					"$portrait_viewport_line" || exit 1
				active_hub_line="$(wait_for_mobile_stats_subtab STATS 20)" || exit 1
				assert_mobile_stats_subtab_layout "$active_hub_line" \
					"$portrait_viewport_line" STATS || exit 1
				portrait_hub_line="$active_hub_line"
				screenshot 60bd-auth-mobile-hub-portrait-stats-returned
				"$ADB" logcat -c || true
				swipe_mobile_hub_drawer_up "$active_hub_line" || exit 1
				wait_for_mobile_hub_stats_scroll portrait 15 || exit 1
				screenshot 60b-auth-mobile-hub-portrait-stats-scrolled
			fi
			if [[ "$key" == "STATS" || "$key" == "INVENTORY" ]]; then
				"$ADB" logcat -c || true
				tap_mobile_hub_connector_from_layout "$active_hub_line" "$portrait_viewport_line" || exit 1
				wait_for_mobile_hub_panel portrait "$expected_panel" "$expected_side" 10 >/dev/null || exit 1
				assert_no_mobile_hub_connector_fallthrough "portrait-$expected_side" 2 || exit 1
			fi
		done
		screenshot 60c-auth-mobile-hub-portrait-all-panels-reachable

		"$ADB" logcat -c || true
		force_android_landscape || {
			echo "ERROR: Android mobile hub smoke could not force landscape rotation" >&2
			exit 1
		}
		assert_game_activity_for_input "mobile hub landscape rotation" "mobile-hub-lost-landscape" || exit 1
		landscape_viewport_line="$(wait_for_android_mobile_viewport landscape 30)" || exit 1
		# Rotation can draw one final portrait frame after the pre-rotation log clear.
		# Preserve the settled viewport in the telemetry cache, then clear transient
		# geometry before requiring a landscape split-rail record.
		"$ADB" logcat -c || true
		landscape_hub_line="$(wait_for_mobile_hub_layout landscape 30)" || exit 1
		current_panel="$(extract_log_value "$landscape_hub_line" panel)"
		current_side="$(extract_log_value "$landscape_hub_line" panelSide)"
		assert_mobile_hub_layout "$landscape_hub_line" "$landscape_viewport_line" \
			landscape "$current_panel" "$current_side" || exit 1
		screenshot 60d-auth-mobile-hub-landscape

		for key in "${hub_keys[@]}"; do
			expected_panel="$(mobile_hub_expected_panel "$key")" || exit 1
			expected_side="$(mobile_hub_expected_side "$key")" || exit 1
			"$ADB" logcat -c || true
			tap_mobile_hub_control_from_layout "$landscape_hub_line" "$key" \
				"$landscape_viewport_line" || exit 1
			action_line="$(wait_for_mobile_hub_action "$key" "$expected_panel" "$expected_side" 20)" || exit 1
			echo "Verified Android landscape mobile hub action: $action_line"
			active_hub_line="$(wait_for_mobile_hub_panel landscape "$expected_panel" "$expected_side" 15)" || exit 1
			assert_mobile_hub_layout "$active_hub_line" "$landscape_viewport_line" \
				landscape "$expected_panel" "$expected_side" || exit 1
			assert_no_mobile_hub_fallthrough "$key" 2 || exit 1
			landscape_hub_line="$active_hub_line"
			sleep 1
			screenshot "60e-auth-mobile-hub-landscape-$(echo "$key" | tr '[:upper:]' '[:lower:]')"
			if [[ "$key" == "STATS" ]]; then
				"$ADB" logcat -c || true
				swipe_mobile_hub_drawer_up "$active_hub_line" || exit 1
				wait_for_mobile_hub_stats_scroll landscape 15 || exit 1
				screenshot 60e-auth-mobile-hub-landscape-stats-scrolled
			fi
			if [[ "$key" == "STATS" || "$key" == "INVENTORY" ]]; then
				"$ADB" logcat -c || true
				tap_mobile_hub_connector_from_layout "$active_hub_line" "$landscape_viewport_line" || exit 1
				wait_for_mobile_hub_panel landscape "$expected_panel" "$expected_side" 10 >/dev/null || exit 1
				assert_no_mobile_hub_connector_fallthrough "landscape-$expected_side" 2 || exit 1
			fi
		done
		screenshot 60f-auth-mobile-hub-landscape-all-panels-reachable

		"$ADB" logcat -c || true
		force_android_portrait || {
			echo "ERROR: Android mobile hub smoke could not restore portrait rotation" >&2
			exit 1
		}
		portrait_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
		"$ADB" logcat -c || true
		portrait_hub_line="$(wait_for_mobile_hub_layout portrait 30)" || exit 1
		current_panel="$(extract_log_value "$portrait_hub_line" panel)"
		current_side="$(extract_log_value "$portrait_hub_line" panelSide)"
		assert_mobile_hub_layout "$portrait_hub_line" "$portrait_viewport_line" \
			portrait "$current_panel" "$current_side" || exit 1
		assert_no_android_runtime_crash "after mobile hub portrait restore" || exit 1
		screenshot 60g-auth-mobile-hub-portrait-restored
		logout_authenticated_smoke_session || exit 1
	) || status=$?

	if ! graceful_cleanup_authenticated_smoke_session; then
		status=1
	fi
	disable_android_smoke_chat_tabs
	disable_android_smoke_walk
	if wait_auth_offline 2 >/dev/null 2>&1; then
		"$ADB" shell am force-stop $APP_ID || true
	fi
	force_android_portrait || true
	sleep 1
	return "$status"
}

run_authenticated_chat_send_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position="" original_x="" original_y="" original_online="" status
    if [[ -n "$AUTH_DB" ]]; then
        original_position="$(read_auth_position)"
        read -r original_x original_y original_online <<< "$original_position"
        echo "Android chat send smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
        update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"
    fi

    status=0
    (
        set -e
        force_android_portrait || {
            echo "ERROR: Android native chat smoke could not force portrait rotation" >&2
            exit 1
        }
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_chat_send
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_chat_send
        enable_android_smoke_npc_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1

		local viewport_line chat_line filter expected_channel current_channel
        local -a filters=(ALL PUBLIC QUEST GLOBAL PM)
        viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
		chat_line="$(wait_for_native_chat_layout false "" false 20)" || exit 1
		assert_native_chat_layout "$chat_line" "$viewport_line" false || exit 1
		current_channel="$(extract_log_value "$chat_line" channel)"
		screenshot 65-auth-native-chat-collapsed

		"$ADB" logcat -c || true
		tap_native_chat_inline_from_line "$chat_line" || exit 1
		chat_line="$(wait_for_native_chat_layout false "$current_channel" true 20)" || exit 1
		assert_native_chat_layout "$chat_line" "$viewport_line" false || exit 1
		assert_soft_keyboard_visible || exit 1
		screenshot 65a-auth-native-chat-inline-keyboard
		"$ADB" logcat -c || true
		input_text "$AUTH_CHAT_MESSAGE"
		"$ADB" shell input keyevent ENTER
		wait_for_chat_send "$AUTH_CHAT_MESSAGE" 20 || exit 1
		sleep 2
		assert_single_chat_send "$AUTH_CHAT_MESSAGE" || exit 1
		screenshot 65b-auth-native-chat-inline-after-send

		"$ADB" logcat -c || true
		"$ADB" shell input keyevent BACK
		sleep 2
		assert_soft_keyboard_hidden || exit 1
		chat_line="$(wait_for_native_chat_layout false "$current_channel" false 20)" || exit 1
		assert_native_chat_layout "$chat_line" "$viewport_line" false || exit 1
		screenshot 65c-auth-native-chat-inline-dismissed

		"$ADB" logcat -c || true
		tap_native_chat_launcher_from_line "$chat_line" || exit 1
		chat_line="$(wait_for_native_chat_layout true "$current_channel" false 20)" || exit 1
		assert_native_chat_layout "$chat_line" "$viewport_line" true || exit 1
		screenshot 66-auth-native-chat-history-open

		for filter in "${filters[@]}"; do
            expected_channel="$(native_chat_channel_for_filter "$filter")" || exit 1
            "$ADB" logcat -c || true
            tap_native_chat_filter_from_line "$chat_line" "$filter" || exit 1
            chat_line="$(wait_for_native_chat_layout true "$expected_channel" false 20)" || exit 1
            assert_native_chat_layout "$chat_line" "$viewport_line" true || exit 1
            current_channel="$expected_channel"
			echo "Verified native Android chat filter $filter selected channel=$current_channel"
		done
		screenshot 67-auth-native-chat-all-filters

		"$ADB" logcat -c || true
		"$ADB" shell input keyevent BACK
		chat_line="$(wait_for_native_chat_layout false "$current_channel" false 20)" || exit 1
		assert_native_chat_layout "$chat_line" "$viewport_line" false || exit 1
		assert_soft_keyboard_hidden || exit 1
		assert_no_android_runtime_crash "after native chat sheet smoke" || exit 1
		screenshot 67a-auth-native-chat-history-collapsed-by-back
		logout_authenticated_smoke_session || exit 1
	) || status=$?

	local cleanup_offline=true
	if ! graceful_cleanup_authenticated_smoke_session; then
		status=1
		cleanup_offline=false
	fi
	disable_android_smoke_chat_send
	disable_android_smoke_npc_targets
	if [[ "$cleanup_offline" == "true" ]]; then
		"$ADB" shell am force-stop $APP_ID || true
	fi
	if [[ "$cleanup_offline" == "true" && -n "$AUTH_DB" && -n "$original_x" && -n "$original_y" ]]; then
		update_auth_position "$original_x" "$original_y" || true
	fi
    sleep 1
    return "$status"
}

run_authenticated_bank_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android bank smoke; ANDROID_SMOKE_AUTH_DB is required to seed and restore bank fixtures."
        return
    fi

    preflight_auth_login_fixture
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online player_id status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
    snapshot_auth_inventory "$player_id"
    snapshot_auth_bank "$player_id"
    echo "Android bank smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_BANK_PLAYER_X,$AUTH_BANK_PLAYER_Y"
    update_auth_position "$AUTH_BANK_PLAYER_X" "$AUTH_BANK_PLAYER_Y"
    echo "Android bank smoke seeding inventory slot 0 with item $AUTH_BANK_ITEM_ID x$AUTH_BANK_INVENTORY_AMOUNT"
    seed_auth_inventory_slot "$player_id" 0 "$AUTH_BANK_ITEM_ID" "$AUTH_BANK_INVENTORY_AMOUNT"
    echo "Android bank smoke seeding $AUTH_BANK_FIXTURE_BANK_SLOTS bank slots starting with item $AUTH_BANK_ITEM_ID"
    seed_auth_bank_fixture "$player_id"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        force_android_portrait || {
            echo "ERROR: Android bank smoke could not establish its portrait baseline" >&2
            exit 1
        }
        enable_android_smoke_bank
        enable_android_smoke_object_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        # uiautomator may restore the emulator's pre-smoke rotation while it
        # taps the native launcher, so reassert portrait after GameActivity owns
        # the display and before reading any login target geometry.
        force_android_portrait || {
            echo "ERROR: Android bank smoke lost portrait after launch" >&2
            exit 1
        }
        enable_android_smoke_bank
        enable_android_smoke_object_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        "$ADB" logcat -c || true
        local bank_object_coords bank_object_x bank_object_y
        bank_object_coords="$(wait_for_stable_object_target "$AUTH_BANK_OBJECT_ID" 30)" || exit 1
        read -r bank_object_x bank_object_y <<< "$bank_object_coords"
        screenshot 68-auth-before-bank-open
        echo "Android object target $AUTH_BANK_OBJECT_ID at stable client $bank_object_x,$bank_object_y"
        tap_client_xy "$bank_object_x" "$bank_object_y"
        wait_for_object_action "$AUTH_BANK_OBJECT_ID" "$AUTH_BANK_OBJECT_ACTION" 20 || exit 1

        local bank_line bank_renderer save_requires_confirm bank_slot_x bank_slot_y inventory_slot_x inventory_slot_y
        local search_x search_y search_clear_x search_clear_y deposit_all_x deposit_all_y
        local close_x close_y
        local loadouts_x loadouts_y loadout_save_x loadout_save_y loadout_load_x loadout_load_y
        local confirm_save_x confirm_save_y scroll_x scroll_start_y scroll_end_y panel_line modal_line
        local bank_items tab_count tab0_x tab0_y tab_last_x tab_last_y

        bank_line="$(wait_for_bank_open 30)" || exit 1
        bank_renderer="$(extract_log_value "$bank_line" renderer)"
        if [[ -z "$bank_renderer" ]]; then
            bank_renderer="legacyCustom"
        fi
        bank_slot_x="$(log_int_or_default "$bank_line" bankSlotX 30)"
        bank_slot_y="$(log_int_or_default "$bank_line" bankSlotY 72)"
        inventory_slot_x="$(log_int_or_default "$bank_line" inventorySlotX 30)"
        inventory_slot_y="$(log_int_or_default "$bank_line" inventorySlotY 239)"
        search_x="$(log_int_or_default "$bank_line" searchX 439)"
        search_y="$(log_int_or_default "$bank_line" searchY 36)"
        search_clear_x="$(log_int_or_default "$bank_line" searchClearX 487)"
        search_clear_y="$(log_int_or_default "$bank_line" searchClearY 36)"
        close_x="$(log_int_or_default "$bank_line" closeX 494)"
        close_y="$(log_int_or_default "$bank_line" closeY 16)"
        deposit_all_x="$(log_int_or_default "$bank_line" depositAllX 124)"
        deposit_all_y="$(log_int_or_default "$bank_line" depositAllY 210)"
        loadouts_x="$(log_int_or_default "$bank_line" loadoutsX 331)"
        loadouts_y="$(log_int_or_default "$bank_line" loadoutsY 36)"
        loadout_save_x="$(log_int_or_default "$bank_line" loadoutSave0X 337)"
        loadout_save_y="$(log_int_or_default "$bank_line" loadoutSave0Y 97)"
        loadout_load_x="$(log_int_or_default "$bank_line" loadoutLoad0X 289)"
        loadout_load_y="$(log_int_or_default "$bank_line" loadoutLoad0Y 97)"
        confirm_save_x="$(log_int_or_default "$bank_line" confirmSaveX 203)"
        confirm_save_y="$(log_int_or_default "$bank_line" confirmSaveY 271)"
        screenshot 69-auth-bank-open

        if [[ "$bank_renderer" == "voidGlass" ]]; then
            assert_void_glass_bank_touch_geometry "$bank_line" || exit 1
            bank_items="$(log_int_or_default "$bank_line" bankItems 0)"
            tab_count="$(log_int_or_default "$bank_line" tabCount 0)"
            if (( bank_items > 240 && tab_count == 7 )); then
                tab0_x="$(log_int_or_default "$bank_line" tab0X -1)"
                tab0_y="$(log_int_or_default "$bank_line" tab0Y -1)"
                tab_last_x="$(log_int_or_default "$bank_line" tabLastX -1)"
                tab_last_y="$(log_int_or_default "$bank_line" tabLastY -1)"
                "$ADB" logcat -c || true
                tap_client_xy "$tab_last_x" "$tab_last_y"
                wait_for_bank_page 6 20 >/dev/null || exit 1
                screenshot 69a-auth-bank-last-page
                bank_line="$(wait_for_bank_open 5)" || exit 1
                tab0_x="$(log_int_or_default "$bank_line" tab0X "$tab0_x")"
                tab0_y="$(log_int_or_default "$bank_line" tab0Y "$tab0_y")"
                "$ADB" logcat -c || true
                tap_client_xy "$tab0_x" "$tab0_y"
                wait_for_bank_page 0 20 >/dev/null || exit 1
                bank_line="$(wait_for_bank_open 5)" || exit 1
            fi
        fi

        "$ADB" logcat -c || true
        tap_client_xy "$search_x" "$search_y"
        if [[ "$bank_renderer" == "voidGlass" ]]; then
            wait_for_bank_search_focus true 20 >/dev/null || exit 1
            # ADB text injection works without an IME; this OS-level gate proves
            # that a player's ordinary search tap actually summoned the keyboard.
            wait_for_soft_keyboard_visible 15 || exit 1
        fi
        input_text "$AUTH_BANK_SEARCH_TEXT"
        wait_for_bank_search "$AUTH_BANK_SEARCH_TEXT" 20 >/dev/null || exit 1
        sleep 1
        screenshot 70-auth-bank-search

        bank_line="$(wait_for_bank_open 5)" || exit 1
        search_clear_x="$(log_int_or_default "$bank_line" searchClearX "$search_clear_x")"
        search_clear_y="$(log_int_or_default "$bank_line" searchClearY "$search_clear_y")"
        if [[ "$bank_renderer" == "voidGlass" ]]; then
            "$ADB" shell input keyevent BACK
            wait_for_soft_keyboard_hidden 15 || exit 1
            bank_line="$(wait_for_bank_search_focus true 10)" || exit 1
            if [[ "$(extract_log_value "$bank_line" search)" != "$(chat_message_log_token "$AUTH_BANK_SEARCH_TEXT")" ]]; then
                echo "ERROR: Android Back changed or dismissed the active bank search" >&2
                echo "$bank_line" >&2
                exit 1
            fi
            # The focused field stays active after keyboard Back and can summon
            # the IME again with one ordinary tap.
            tap_client_xy "$search_x" "$search_y"
            wait_for_soft_keyboard_visible 15 || exit 1
        fi
        "$ADB" logcat -c || true
        tap_client_xy "$search_clear_x" "$search_clear_y"
        wait_for_bank_search "" 20 >/dev/null || exit 1
        if [[ "$bank_renderer" == "voidGlass" ]]; then
            wait_for_bank_search_focus true 10 >/dev/null || exit 1
            assert_soft_keyboard_visible || exit 1
            "$ADB" shell input keyevent BACK
            wait_for_soft_keyboard_hidden 15 || exit 1
        fi
        sleep 1

        "$ADB" logcat -c || true
        if [[ "$bank_renderer" == "voidGlass" ]]; then
            scroll_x="$AUTH_BANK_SCROLL_START_X"
            scroll_start_y=$((bank_slot_y + 180))
            scroll_end_y=$((bank_slot_y + 40))
            swipe_client_xy "$scroll_x" "$scroll_start_y" "$scroll_x" "$scroll_end_y" "$AUTH_BANK_SCROLL_DURATION_MS"
        else
            bank_line="$(wait_for_bank_open 5)" || exit 1
            scroll_x="$(log_int_or_default "$bank_line" scrollbarX "$AUTH_BANK_SCROLL_START_X")"
            scroll_start_y="$AUTH_BANK_SCROLL_START_Y"
            scroll_end_y="$AUTH_BANK_SCROLL_END_Y"
            swipe_client_xy "$scroll_x" "$scroll_start_y" "$scroll_x" "$scroll_end_y" "$AUTH_BANK_SCROLL_DURATION_MS"
        fi
        wait_for_bank_scroll 20 >/dev/null || exit 1
        sleep 1
        screenshot 71-auth-bank-scrolled

        bank_line="$(wait_for_bank_open 5)" || exit 1
        bank_slot_x="$(log_int_or_default "$bank_line" bankSlotX "$bank_slot_x")"
        bank_slot_y="$(log_int_or_default "$bank_line" bankSlotY "$bank_slot_y")"
        "$ADB" logcat -c || true
        tap_client_xy "$bank_slot_x" "$bank_slot_y"
        wait_for_bank_action WITHDRAW 20 || exit 1
        sleep 2
        screenshot 72-auth-bank-withdraw

        "$ADB" logcat -c || true
        tap_client_xy "$loadouts_x" "$loadouts_y"
        panel_line="$(wait_for_bank_loadouts_panel 15)" || exit 1
        if [[ -z "$(extract_log_value "$panel_line" renderer)" && "$bank_renderer" == "legacyCustom" ]]; then
            save_requires_confirm="1"
        else
            save_requires_confirm="$(log_int_or_default "$panel_line" saveRequiresConfirm "$(log_int_or_default "$bank_line" saveRequiresConfirm 1)")"
        fi
        loadout_save_x="$(log_int_or_default "$panel_line" save0X "$loadout_save_x")"
        loadout_save_y="$(log_int_or_default "$panel_line" save0Y "$loadout_save_y")"
        loadout_load_x="$(log_int_or_default "$panel_line" load0X "$loadout_load_x")"
        loadout_load_y="$(log_int_or_default "$panel_line" load0Y "$loadout_load_y")"
        sleep 1
        screenshot 73-auth-bank-loadouts

        "$ADB" logcat -c || true
        tap_client_xy "$loadout_save_x" "$loadout_save_y"
        if [[ "$save_requires_confirm" == "0" ]]; then
            wait_for_bank_action SAVE_PRESET 20 || exit 1
            sleep 1
            screenshot 74-auth-bank-save-action
        else
            modal_line="$(wait_for_bank_modal SAVE_CONFIRM 0 15)" || exit 1
            confirm_save_x="$(log_int_or_default "$modal_line" saveX "$confirm_save_x")"
            confirm_save_y="$(log_int_or_default "$modal_line" saveY "$confirm_save_y")"
            sleep 1
            screenshot 74-auth-bank-save-modal

            "$ADB" logcat -c || true
            tap_client_xy "$confirm_save_x" "$confirm_save_y"
            wait_for_bank_action SAVE_PRESET 20 || exit 1
        fi
        sleep 3
        screenshot 75-auth-bank-save-loadout

        "$ADB" logcat -c || true
        tap_client_xy "$inventory_slot_x" "$inventory_slot_y"
        wait_for_bank_action DEPOSIT 20 || exit 1
        sleep 2
        screenshot 76-auth-bank-deposit

        "$ADB" logcat -c || true
        tap_client_xy "$deposit_all_x" "$deposit_all_y"
        wait_for_bank_action DEPOSIT_ALL 20 || exit 1
        sleep 2
        screenshot 77-auth-bank-deposit-all

        "$ADB" logcat -c || true
        tap_client_xy "$loadouts_x" "$loadouts_y"
        panel_line="$(wait_for_bank_loadouts_panel 15)" || exit 1
        loadout_load_x="$(log_int_or_default "$panel_line" load0X "$loadout_load_x")"
        loadout_load_y="$(log_int_or_default "$panel_line" load0Y "$loadout_load_y")"
        "$ADB" logcat -c || true
        tap_client_xy "$loadout_load_x" "$loadout_load_y"
        wait_for_bank_action LOAD_PRESET 20 || exit 1
        sleep 4
        screenshot 78-auth-bank-loadout-loaded

        bank_line="$(wait_for_bank_open 5)" || exit 1
        close_x="$(log_int_or_default "$bank_line" closeX "$close_x")"
        close_y="$(log_int_or_default "$bank_line" closeY "$close_y")"
        if [[ "$bank_renderer" == "voidGlass" ]]; then
            "$ADB" logcat -c || true
            tap_client_xy "$search_x" "$search_y"
            wait_for_bank_search_focus true 20 >/dev/null || exit 1
            wait_for_soft_keyboard_visible 15 || exit 1
        fi
        "$ADB" logcat -c || true
        tap_client_xy "$close_x" "$close_y"
        wait_for_bank_action CLOSE 20 || exit 1
        if [[ "$bank_renderer" == "voidGlass" ]]; then
            wait_for_soft_keyboard_hidden 15 || exit 1
        fi
        sleep 1
        screenshot 79-auth-bank-closed

		logout_authenticated_smoke_session || exit 1
        wait_for_bank_preset_saved "$player_id" 0 45 || exit 1
    ) || status=$?

    disable_android_smoke_bank
    disable_android_smoke_object_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    restore_auth_bank "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_shop_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android shop smoke; ANDROID_SMOKE_AUTH_DB is required to seed and restore shop fixtures."
        return
    fi

    force_android_portrait || {
        echo "ERROR: Android shop smoke could not establish portrait baseline" >&2
        return 1
    }

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online player_id status
    local emulator_flag use_narrow_override
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
    snapshot_auth_inventory "$player_id"
    echo "Android shop smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_SHOP_PLAYER_X,$AUTH_SHOP_PLAYER_Y"
    update_auth_position "$AUTH_SHOP_PLAYER_X" "$AUTH_SHOP_PLAYER_Y"
    echo "Android shop smoke seeding coins in inventory slot 0 x$AUTH_SHOP_COIN_AMOUNT"
    seed_auth_inventory_slot "$player_id" 0 10 "$AUTH_SHOP_COIN_AMOUNT"

    save_android_display_override
    emulator_flag="$("$ADB" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' | tail -1)"
    use_narrow_override=0
    if [[ "$emulator_flag" == "1" ]]; then
        use_narrow_override=1
    fi
    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_shop
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_shop
        enable_android_smoke_npc_targets
        sleep 3
        tap_existing_user_button
        sleep 1
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2
        wait_for_npc_target "$AUTH_SHOP_NPC_ID" 30 >/dev/null || exit 1
        screenshot 79-auth-before-shop-open

        local menu_values menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y menu_index shop_menu_attempt
        menu_index=""
        for shop_menu_attempt in 1 2 3; do
            # NPCs and the automatic camera can both move while a hold is in
            # progress. The configured Android hold threshold is 250ms, so a
            # 500ms press is deliberate without leaving enough time for the
            # renderer-reported target to drift a full hit box.
            "$ADB" logcat -c || true
            long_press_npc_target "$AUTH_SHOP_NPC_ID" 500 || continue
            menu_values="$(wait_for_context_menu 8)" || continue
            if menu_index="$(wait_for_context_menu_action_index "$AUTH_SHOP_NPC_ACTION" 4)"; then
                break
            fi
            menu_index=""
            "$ADB" shell input keyevent BACK || true
            sleep 1
        done
        if [[ -z "$menu_index" ]]; then
            echo "ERROR: Android shop could not open NPC action $AUTH_SHOP_NPC_ACTION after 3 fresh target holds" >&2
            exit 1
        fi
        read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
        tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" "$menu_index" || exit 1
        wait_for_context_menu_action "$AUTH_SHOP_NPC_ACTION" 20 || exit 1
        wait_for_npc_action "$AUTH_SHOP_NPC_ID" "$AUTH_SHOP_NPC_ACTION" 20 || exit 1

        local shop_line shop_x shop_y slot_x slot_y buy_x buy_y sell_x sell_y
        shop_line="$(wait_for_shop_open 30 portrait)" || exit 1
        assert_native_shop_layout "$shop_line" portrait || exit 1
        screenshot 80-auth-shop-open
        shop_x="$(log_int_or_default "$shop_line" shopX 52)"
        shop_y="$(log_int_or_default "$shop_line" shopY 50)"
        buy_x="$(log_int_or_default "$shop_line" buy1X 376)"
        buy_y="$(log_int_or_default "$shop_line" buy1Y 260)"
        sell_x="$(log_int_or_default "$shop_line" sell1X 376)"
        sell_y="$(log_int_or_default "$shop_line" sell1Y 285)"
        slot_x=$((shop_x + 7 + (AUTH_SHOP_BUY_SLOT % 8) * 49 + 24))
        slot_y=$((shop_y + 28 + (AUTH_SHOP_BUY_SLOT / 8) * 34 + 17))

        "$ADB" logcat -c || true
        tap_client_xy "$slot_x" "$slot_y"
        wait_for_shop_select "$AUTH_SHOP_BUY_SLOT" 15 || exit 1
        sleep 1
        screenshot 81-auth-shop-selected

        tap_shop_action_with_retry BUY "$shop_line" || exit 1
        shop_line="$(wait_for_shop_selected_owned "$AUTH_SHOP_BUY_SLOT" 25)" || exit 1
        sleep 1
        screenshot 82-auth-shop-buy

        tap_shop_action_with_retry SELL "$shop_line" || exit 1
        sleep 2
        screenshot 83-auth-shop-sell

        "$ADB" logcat -c || true
        swipe_client_xy 256 170 256 80 500
        shop_line="$(wait_for_shop_open 15 portrait)" || exit 1
        assert_native_shop_layout "$shop_line" portrait || exit 1
        sleep 1
        screenshot 84-auth-shop-no-scroll

        # Exercise the pressure case that previously selected the tall portrait
        # stack in landscape and crushed the fixed five-row item grid. Keep
        # physical-device smoke non-mutating; the emulator receives a temporary
        # roughly 571dp-wide landscape profile and must use wrapped 3x2 side
        # action groups.
        local expected_landscape_amount_rows=""
        if [[ "$use_narrow_override" == "1" ]]; then
            # 720x1000 at the AVD's native density represents a common narrow
            # phone without relying on a synthetic density change that a
            # retained Activity may observe in two configuration phases.
            "$ADB" shell wm size 720x1000 >/dev/null
            expected_landscape_amount_rows=2
        fi
        force_android_landscape || exit 1
        # Rotation dispatches several inset/surface snapshots. Let the final
        # edge-to-edge viewport settle before accepting shop geometry.
        sleep 5
        shop_line="$(wait_for_shop_open 30 landscape)" || exit 1
        assert_native_shop_layout "$shop_line" landscape "$expected_landscape_amount_rows" || exit 1
        screenshot 85-auth-shop-narrow-landscape

        buy_x="$(log_int_or_default "$shop_line" buy1X 376)"
        buy_y="$(log_int_or_default "$shop_line" buy1Y 260)"
        sell_x="$(log_int_or_default "$shop_line" sell1X 376)"
        sell_y="$(log_int_or_default "$shop_line" sell1Y 285)"
        tap_shop_action_with_retry BUY "$shop_line" || exit 1
        shop_line="$(wait_for_shop_selected_owned "$AUTH_SHOP_BUY_SLOT" 25)" || exit 1
        tap_shop_action_with_retry SELL "$shop_line" || exit 1
        sleep 1
        screenshot 86-auth-shop-narrow-landscape-actions

        shop_line="$(wait_for_shop_open 15 landscape)" || exit 1
        local close_x close_y
        close_x="$(log_int_or_default "$shop_line" closeX 500)"
        close_y="$(log_int_or_default "$shop_line" closeY 24)"
        if [[ -n "$SHOP_ACTION_VIEWPORT_LINE" ]]; then
            tap_client_xy_from_viewport "$close_x" "$close_y" "$SHOP_ACTION_VIEWPORT_LINE"
        else
            tap_client_xy "$close_x" "$close_y"
        fi
        sleep 2
        force_android_portrait || exit 1
		if [[ "$use_narrow_override" == "1" ]]; then
			restore_android_display_override
			sleep 5
		fi

		logout_authenticated_smoke_session || exit 1
    ) || status=$?

    restore_android_display_override
    force_android_portrait || true

    disable_android_smoke_shop
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_equipment_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android equipment smoke; ANDROID_SMOKE_AUTH_DB is required to seed and restore equipment fixtures."
        return
    fi

    force_android_portrait || {
        echo "ERROR: Android equipment smoke could not force portrait rotation" >&2
        return 1
    }

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local player_id status
    player_id="$(read_auth_player_id)"
    snapshot_auth_inventory "$player_id"
    snapshot_auth_equipment "$player_id"
    echo "Android equipment smoke clearing worn items and seeding inventory slot $AUTH_EQUIPMENT_INVENTORY_SLOT with item $AUTH_EQUIPMENT_ITEM_ID x$AUTH_EQUIPMENT_ITEM_AMOUNT"
    clear_auth_equipment "$player_id"
    seed_auth_inventory_slot "$player_id" "$AUTH_EQUIPMENT_INVENTORY_SLOT" "$AUTH_EQUIPMENT_ITEM_ID" "$AUTH_EQUIPMENT_ITEM_AMOUNT"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_equipment
		enable_android_smoke_chat_tabs
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        # The wrapper transition may restore a previous emulator rotation.
        # Reassert portrait before the shared client starts receiving input.
        force_android_portrait || exit 1
        enable_android_smoke_inventory_targets
        enable_android_smoke_equipment
		enable_android_smoke_chat_tabs
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2

        local portrait_viewport_line landscape_viewport_line
        local equipment_line equipment_tab_x equipment_tab_y
        local equipped_line equipped_slot_x equipped_slot_y
        portrait_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
        tap_inventory_tab
        wait_for_inventory_target "$AUTH_EQUIPMENT_INVENTORY_SLOT" 30 >/dev/null || exit 1
        screenshot 85-auth-before-equipment-equip

        "$ADB" logcat -c || true
        tap_inventory_slot "$AUTH_EQUIPMENT_INVENTORY_SLOT" || exit 1
        wait_for_inventory_action "$AUTH_EQUIPMENT_INVENTORY_SLOT" "ITEM_EQUIP_FROM_INVENTORY" 20 || exit 1
        wait_for_inventory_equipped_state "$AUTH_EQUIPMENT_INVENTORY_SLOT" true 20 || exit 1
        wait_for_equipment_count 1 20 || exit 1

        # Require the native inventory/equipment tabs; falling back to an
        # inventory-only unequip would leave the new drawer geometry untested.
        equipment_line="$(wait_for_equipment_tab 0 20)" || exit 1
        equipment_tab_x="$(log_int_or_default "$equipment_line" equipmentTabX 325)"
        equipment_tab_y="$(log_int_or_default "$equipment_line" equipmentTabY 280)"
        "$ADB" logcat -c || true
        tap_client_xy "$equipment_tab_x" "$equipment_tab_y"
        equipment_line="$(wait_for_equipment_tab 1 20)" || exit 1
        assert_equipment_touch_geometry portrait "$equipment_line" "$portrait_viewport_line" || exit 1
        equipped_line="$(wait_for_equipped_item "$AUTH_EQUIPMENT_ITEM_ID" 20)" || exit 1
        sleep 1
        screenshot 86a-auth-equipment-drawer-portrait

        "$ADB" logcat -c || true
        force_android_landscape || {
            echo "ERROR: Android equipment smoke could not force landscape rotation" >&2
            exit 1
        }
        assert_game_activity_for_input "equipment landscape rotation" "equipment-lost-landscape" || exit 1
        landscape_viewport_line="$(wait_for_android_mobile_viewport landscape 30)" || exit 1
        equipment_line="$(wait_for_equipment_tab 1 20)" || exit 1
        assert_equipment_touch_geometry landscape "$equipment_line" "$landscape_viewport_line" || exit 1
        equipped_line="$(wait_for_equipped_item "$AUTH_EQUIPMENT_ITEM_ID" 20)" || exit 1
        equipped_slot_x="$(log_int_or_default "$equipped_line" clientX 386)"
        equipped_slot_y="$(log_int_or_default "$equipped_line" clientY 120)"
        sleep 1
        screenshot 86b-auth-equipment-drawer-landscape

        "$ADB" logcat -c || true
        tap_client_xy "$equipped_slot_x" "$equipped_slot_y"
        wait_for_equipment_action UNEQUIP_FROM_EQUIPMENT 20 || exit 1
        wait_for_equipment_count 0 20 || exit 1
        sleep 2
        screenshot 87-auth-after-equipment-unequip-landscape

        "$ADB" logcat -c || true
        force_android_portrait || {
            echo "ERROR: Android equipment smoke could not restore portrait rotation" >&2
            exit 1
        }
        assert_game_activity_for_input "equipment portrait restore" "equipment-lost-portrait-restore" || exit 1
        portrait_viewport_line="$(wait_for_android_mobile_viewport portrait 30)" || exit 1
        equipment_line="$(wait_for_equipment_tab 1 20)" || exit 1
        assert_equipment_touch_geometry portrait "$equipment_line" "$portrait_viewport_line" || exit 1
        assert_no_android_runtime_crash "after equipment portrait restore" || exit 1

        "$ADB" shell am force-stop $APP_ID || true
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || exit 1
    ) || status=$?

    disable_android_smoke_inventory_targets
    disable_android_smoke_equipment
	disable_android_smoke_chat_tabs
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    restore_auth_equipment "$player_id" || true
    force_android_portrait || true
    sleep 1
    return "$status"
}

run_authenticated_magic_prayer_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android magic/prayer smoke; ANDROID_SMOKE_AUTH_DB is required to restore stats and position."
        return
    fi

    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    local original_position original_x original_y original_online player_id status
	local original_settings original_camera original_mouse original_sound
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
	original_settings="$(read_auth_settings)" || return 1
	read -r original_camera original_mouse original_sound <<< "$original_settings"
	update_auth_settings "$original_camera" "$original_mouse" 0
    snapshot_auth_stats "$player_id"
    ensure_auth_magic_prayer_stats "$player_id"
    echo "Android magic/prayer smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_magic_prayer
		enable_android_smoke_audio
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_magic_prayer
		enable_android_smoke_audio
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
		close_auth_intro_dialog_if_present
        sleep 2

        tap_magic_prayer_tab
        local magic_line row_x row_y prayer_tab_x prayer_tab_y self_x self_y
        magic_line="$(wait_for_magic_prayer_tab magic 30)" || exit 1
        row_x="$(log_int_or_default "$magic_line" row0X 383)"
        row_y="$(log_int_or_default "$magic_line" row0Y 69)"
        self_x="$(log_int_or_default "$magic_line" selfCastX 256)"
        self_y="$(log_int_or_default "$magic_line" selfCastY 170)"
        screenshot 88-auth-magic-tab-open

        tap_magic_prayer_row_until_action "$row_x" "$row_y" SPELL_SELECTED "$AUTH_MAGIC_PRAYER_SPELL_ID" || exit 1
        sleep 1
        screenshot 89-auth-magic-spell-selected

		"$ADB" logcat -c || true
		"$ADB" shell input keyevent BACK
		wait_for_magic_prayer_action BACK_SELECTION_CLEARED -1 20 || exit 1
		sleep 1
		screenshot 89a-auth-magic-spell-back-cleared
		tap_magic_prayer_row_until_action "$row_x" "$row_y" SPELL_SELECTED "$AUTH_MAGIC_PRAYER_SPELL_ID" || exit 1

        cast_selected_spell_on_self "$AUTH_MAGIC_PRAYER_SPELL_ID" "$self_x" "$self_y" || exit 1
        sleep 8
        screenshot 90-auth-after-self-cast

		logout_authenticated_smoke_session || exit 1
        if [[ "$AUTH_MAGIC_PRAYER_SPELL_ID" == "0" ]]; then
            local post_cast_position post_cast_x post_cast_y post_cast_online
            post_cast_position="$(read_auth_position || true)"
            read -r post_cast_x post_cast_y post_cast_online <<< "$post_cast_position"
            if [[ "$post_cast_x" == "$AUTH_MAGIC_PRAYER_HOME_X" && "$post_cast_y" == "$AUTH_MAGIC_PRAYER_HOME_Y" ]]; then
                echo "Verified Android Home teleport position: $post_cast_x,$post_cast_y online=$post_cast_online"
            else
                echo "WARNING: Android self-cast action fired, but saved position remained ${post_cast_position:-unknown}; continuing with UI/prayer smoke." >&2
            fi
        fi

        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_magic_prayer
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_magic_prayer
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
		close_auth_intro_dialog_if_present
        sleep 2

        tap_magic_prayer_tab
        magic_line="$(wait_for_magic_prayer_tab magic 30)" || exit 1
        prayer_tab_x="$(log_int_or_default "$magic_line" prayerTabX 460)"
        prayer_tab_y="$(log_int_or_default "$magic_line" prayerTabY 48)"
        "$ADB" logcat -c || true
        tap_client_xy "$prayer_tab_x" "$prayer_tab_y"
        local prayer_line
        prayer_line="$(wait_for_magic_prayer_tab prayer 30)" || exit 1
        row_x="$(log_int_or_default "$prayer_line" row0X "$row_x")"
        row_y="$(log_int_or_default "$prayer_line" row0Y "$row_y")"
        sleep 1
        screenshot 91-auth-prayer-tab-open

		"$ADB" logcat -c || true
        tap_magic_prayer_row_until_action "$row_x" "$row_y" PRAYER_ACTIVATED "$AUTH_MAGIC_PRAYER_PRAYER_ID" || exit 1
		wait_for_audio_cycle || exit 1
        sleep 1
        screenshot 92-auth-prayer-activated

		"$ADB" logcat -c || true
        tap_magic_prayer_row_until_action "$row_x" "$row_y" PRAYER_DEACTIVATED "$AUTH_MAGIC_PRAYER_PRAYER_ID" || exit 1
		wait_for_audio_cycle || exit 1
        sleep 1
        screenshot 93-auth-prayer-deactivated

		logout_authenticated_smoke_session || exit 1
		wait_for_audio_event stop-all 0 15 || exit 1
    ) || status=$?

    disable_android_smoke_magic_prayer
	disable_android_smoke_audio
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_stats "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
	update_auth_settings "$original_camera" "$original_mouse" "$original_sound" || true
    sleep 1
    return "$status"
}

run_authenticated_world_map_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    local status
    if [[ -n "$AUTH_DB" ]]; then
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
        clear_auth_tutorial_appearance
    fi

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_world_map
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_world_map
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2
		force_android_portrait || exit 1

        local map_line before_zoom after_zoom before_pan_x before_pan_y
        local zoom_in_x zoom_in_y search_x search_y close_x close_y
        map_line="$(open_world_map_from_hud)" || exit 1
        map_line="$(wait_for_world_map_rendered 20)" || exit 1
        assert_world_map_touch_layout "$map_line" || exit 1
        sleep 1
        screenshot 94-auth-world-map-open

        force_android_landscape || exit 1
        wait_for_android_mobile_viewport landscape 30 >/dev/null || exit 1
        map_line="$(wait_for_world_map_rendered 30)" || exit 1
        assert_world_map_touch_layout "$map_line" || exit 1
        sleep 1
        screenshot 94a-auth-world-map-landscape

        force_android_portrait || exit 1
        wait_for_android_mobile_viewport portrait 30 >/dev/null || exit 1
        map_line="$(wait_for_world_map_rendered 30)" || exit 1
        assert_world_map_touch_layout "$map_line" || exit 1

        before_zoom="$(log_int_or_default "$map_line" zoom 0)"
        after_zoom=$((before_zoom + 1))
        zoom_in_x="$(log_int_or_default "$map_line" zoomInX -1)"
        zoom_in_y="$(log_int_or_default "$map_line" zoomInY -1)"
        "$ADB" logcat -c || true
        tap_client_xy "$zoom_in_x" "$zoom_in_y"
        wait_for_world_map_zoom "$after_zoom" 20 || exit 1
        map_line="$(wait_for_world_map_rendered 20)" || exit 1
        assert_world_map_touch_layout "$map_line" || exit 1
        sleep 1
        screenshot 95-auth-world-map-zoomed

        map_line="$(wait_for_world_map_open 10)" || exit 1
        before_pan_x="$(log_int_or_default "$map_line" panX 0)"
        before_pan_y="$(log_int_or_default "$map_line" panY 0)"
        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 21
        wait_for_world_map_pan_changed "$before_pan_x" "$before_pan_y" 20 || exit 1
        wait_for_world_map_rendered 8 >/dev/null 2>&1 || sleep 1
        sleep 1
        screenshot 96-auth-world-map-panned
        map_line="$(wait_for_world_map_rendered 20)" || exit 1
        assert_world_map_touch_layout "$map_line" || exit 1
        search_x="$(log_int_or_default "$map_line" searchX -1)"
        search_y="$(log_int_or_default "$map_line" searchY -1)"
        "$ADB" logcat -c || true
        tap_client_xy "$search_x" "$search_y"
        wait_for_world_map_search_focus true 20 >/dev/null || exit 1
        input_text "$AUTH_WORLD_MAP_SEARCH_TEXT"
        "$ADB" shell input keyevent ENTER
        wait_for_world_map_search "$AUTH_WORLD_MAP_SEARCH_TEXT" 25 || exit 1
        wait_for_world_map_rendered 8 >/dev/null 2>&1 || sleep 1
        sleep 1
        screenshot 97-auth-world-map-search
        map_line="$(wait_for_world_map_rendered 20)" || exit 1
        assert_world_map_touch_layout "$map_line" || exit 1
        close_x="$(log_int_or_default "$map_line" closeX -1)"
        close_y="$(log_int_or_default "$map_line" closeY -1)"
        "$ADB" logcat -c || true
        tap_client_xy "$close_x" "$close_y"
        wait_for_world_map_closed 20 || exit 1
        sleep 1
        screenshot 98-auth-world-map-closed

		logout_authenticated_smoke_session || exit 1
    ) || status=$?

    disable_android_smoke_world_map
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    sleep 1
    return "$status"
}

run_authenticated_settings_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        return
    fi

    local status original_settings original_camera original_mouse original_sound
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    clear_auth_tutorial_appearance
    original_settings="$(read_auth_settings)" || return 1
    read -r original_camera original_mouse original_sound <<< "$original_settings"
    update_auth_settings 1 0 "$original_sound"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_settings
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_settings
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 43
        local settings_line
        settings_line="$(wait_for_settings_state true false 30)" || exit 1
        assert_settings_logout_visible "$settings_line" || exit 1
        settings_line="$(wait_for_settings_rendered true false 30)" || exit 1
        assert_settings_logout_visible "$settings_line" || exit 1
        sleep 1
        screenshot 99-auth-settings-open

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 8
        sleep 1
        "$ADB" shell input keyevent 9
        settings_line="$(wait_for_settings_state false true 30)" || exit 1
        assert_settings_logout_visible "$settings_line" || exit 1
        settings_line="$(wait_for_settings_rendered false true 30)" || exit 1
        assert_settings_logout_visible "$settings_line" || exit 1
        sleep 1
        screenshot 100-auth-settings-changed

        tap_settings_logout false true 20 "100-auth-settings" || exit 1
        sleep 10
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || exit 1
        wait_for_auth_settings_row 0 1 "$original_sound" 45 || exit 1

        "$ADB" logcat -c || true
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_settings
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 43
        settings_line="$(wait_for_settings_state false true 30)" || exit 1
        assert_settings_logout_visible "$settings_line" || exit 1
        settings_line="$(wait_for_settings_rendered false true 30)" || exit 1
        assert_settings_logout_visible "$settings_line" || exit 1
        sleep 1
        screenshot 101-auth-settings-reloaded

        tap_settings_logout false true 20 "101-auth-settings" || exit 1
        sleep 10
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
	) || status=$?

	local cleanup_offline=true
	if ! graceful_cleanup_authenticated_smoke_session; then
		status=1
		cleanup_offline=false
	fi
	disable_android_smoke_settings
	if [[ "$cleanup_offline" == "true" ]]; then
		"$ADB" shell am force-stop $APP_ID || true
		update_auth_settings "$original_camera" "$original_mouse" "$original_sound" || true
	fi
	sleep 1
    return "$status"
}

run_authenticated_afk_smoke() {
	if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
		return
	fi

	wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
	local status=0
	(
		set -e
		force_android_portrait || {
			echo "ERROR: Android AFK smoke could not establish portrait orientation" >&2
			exit 1
		}
		"$ADB" logcat -c || true
		"$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
		enable_android_smoke_afk
		launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
		force_android_portrait || exit 1
		enable_android_smoke_afk
		tap_existing_user_button
		sleep 3
		enter_auth_credentials
		submit_login_and_wait || exit 1
		sleep 8
		close_auth_intro_dialog_if_present
		sleep 2

		local settings_line first_line second_line resumed_line
		local first_count second_count render_delta first_elapsed second_elapsed
		enable_android_smoke_afk
		"$ADB" logcat -c || true
		"$ADB" shell input keyevent 43
		settings_line="$(wait_for_afk_settings_target false 30)" || exit 1
		assert_afk_settings_target "$settings_line" false || exit 1
		echo "Verified Android AFK Settings entry target: $settings_line"
		screenshot 101a-auth-afk-settings-entry

		"$ADB" logcat -c || true
		tap_afk_settings_target_from_line "$settings_line" || exit 1
		first_line="$(wait_for_afk_state true 20)" || exit 1
		assert_afk_monitor_state "$first_line" true || exit 1
		first_count="$(extract_log_value "$first_line" renderCount)"
		first_elapsed="$(extract_log_value "$first_line" elapsedMs)"
		echo "Verified Android AFK monitor entry: $first_line"
		screenshot 101b-auth-afk-monitor-active
		first_line="$(wait_for_afk_state true 6 "$first_count")" || exit 1
		assert_afk_monitor_state "$first_line" true || exit 1
		first_count="$(extract_log_value "$first_line" renderCount)"
		first_elapsed="$(extract_log_value "$first_line" elapsedMs)"

		# The monitor deliberately redraws at a low cadence while networking and
		# combat updates continue. Two seconds should advance it, but never by a
		# frame-rate-sized amount.
		sleep 2
		second_line="$(wait_for_afk_state true 6 "$first_count")" || exit 1
		assert_afk_monitor_state "$second_line" true || exit 1
		second_count="$(extract_log_value "$second_line" renderCount)"
		second_elapsed="$(extract_log_value "$second_line" elapsedMs)"
		render_delta=$((second_count - first_count))
		if (( render_delta < 1 || render_delta > 6 || second_elapsed <= first_elapsed )); then
			echo "ERROR: Android AFK monitor cadence is not low-rate/progressive: first='$first_line' second='$second_line'" >&2
			exit 1
		fi
		echo "Verified Android AFK monitor low redraw cadence: renderDelta=$render_delta elapsed=${first_elapsed}->${second_elapsed}ms"
		screenshot 101c-auth-afk-monitor-progressed

		"$ADB" logcat -c || true
		tap_afk_resume_from_line "$second_line" || exit 1
		resumed_line="$(wait_for_afk_state false 20)" || exit 1
		assert_afk_monitor_state "$resumed_line" false || exit 1
		echo "Verified Android AFK monitor Resume: $resumed_line"
		assert_no_android_runtime_crash "after AFK monitor resume" || exit 1
		sleep 1
		screenshot 101d-auth-afk-resumed
		logout_authenticated_smoke_session || exit 1
	) || status=$?

	if [[ "$status" -ne 0 ]]; then
		local cleanup_afk_line
		cleanup_afk_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' \
			| grep "ANDROID_SMOKE_AFK active=true " | tail -1 || true)"
		if [[ -n "$cleanup_afk_line" ]]; then
			tap_afk_resume_from_line "$cleanup_afk_line" >/dev/null 2>&1 || \
				"$ADB" shell input keyevent BACK >/dev/null 2>&1 || true
			sleep 1
		fi
	fi
	local cleanup_offline=true
	if [[ "$status" -ne 0 ]] && ! graceful_cleanup_authenticated_smoke_session; then
		status=1
		cleanup_offline=false
	fi
	disable_android_smoke_afk
	if [[ "$cleanup_offline" == "true" ]]; then
		"$ADB" shell am force-stop $APP_ID || true
	fi
	force_android_portrait || true
	sleep 1
	return "$status"
}

run_authenticated_ground_loot_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        return
    fi
    if [[ ! "$AUTH_GROUND_LOOT_ITEM_ID" =~ ^[0-9]+$ || ! "$AUTH_GROUND_LOOT_ITEM_AMOUNT" =~ ^[0-9]+$ ]]; then
        echo "ERROR: Android ground-loot fixture item id/amount must be numeric" >&2
        return 1
    fi

    local status player_id original_position original_x original_y original_online
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    clear_auth_tutorial_appearance
    player_id="$(read_auth_player_id)" || return 1
    original_position="$(read_auth_position)" || return 1
    read -r original_x original_y original_online <<< "$original_position"
    snapshot_auth_inventory "$player_id"
    snapshot_auth_ground_loot_cache "$player_id"
    update_auth_position "$AUTH_GROUND_LOOT_PLAYER_X" "$AUTH_GROUND_LOOT_PLAYER_Y"
    seed_auth_inventory_slot "$player_id" 0 "$AUTH_GROUND_LOOT_ITEM_ID" "$AUTH_GROUND_LOOT_ITEM_AMOUNT"
    prepare_auth_ground_loot_cache "$player_id"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_ground_loot
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT" || exit 1
        assert_game_activity_for_input "ground-loot login launch" "ground-loot-lost-after-launch" || exit 1
        screenshot diagnostic-ground-loot-login-home
        enable_android_smoke_ground_loot
        assert_game_activity_for_input "ground-loot existing-user tap" "ground-loot-lost-before-existing-user" || exit 1
        tap_existing_user_button
        sleep 3
        assert_game_activity_for_input "ground-loot credentials entry" "ground-loot-lost-before-credentials" || exit 1
        screenshot diagnostic-ground-loot-login-form
        enter_auth_credentials
        assert_game_activity_for_input "ground-loot login submit" "ground-loot-lost-before-submit" || exit 1
        screenshot diagnostic-ground-loot-login-filled
        submit_login_and_wait || exit 1
        sleep 8
        assert_game_activity_for_input "ground-loot enter-world tap" "ground-loot-lost-before-enter-world" || exit 1
        tap_pct 50 72
        sleep 2

        "$ADB" logcat -c || true
        enable_android_smoke_ground_loot
        assert_game_activity_for_input "ground-loot drop key" "ground-loot-lost-before-drop-key" || exit 1
        "$ADB" shell input keyevent 35
        wait_for_ground_loot_drop "$AUTH_GROUND_LOOT_ITEM_ID" 20 || exit 1
        wait_for_ground_loot_beam "$AUTH_GROUND_LOOT_ITEM_ID" 30 || exit 1
        wait_for_ground_loot_label "$AUTH_GROUND_LOOT_ITEM_ID" 30 || exit 1
        sleep 2
        screenshot 102-auth-ground-loot-readable

        "$ADB" shell am force-stop $APP_ID || true
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    ) || status=$?

    disable_android_smoke_ground_loot
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    restore_auth_ground_loot_cache "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_wilderness_target_smoke() {
    if [[ "$ONLY_AUTH_WILDERNESS_TARGET" -eq 1 ]]; then
        preflight_auth_login_fixture
    fi
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        if [[ "$ONLY_AUTH_WILDERNESS_TARGET" -eq 1 ]]; then
            echo "ERROR: --only-auth-wilderness-target requires ANDROID_SMOKE_AUTH_DB" >&2
            return 1
        fi
        return
    fi
    if [[ ! "$AUTH_WILDERNESS_PLAYER_X" =~ ^[0-9]+$ || ! "$AUTH_WILDERNESS_PLAYER_Y" =~ ^[0-9]+$ \
        || ! "$AUTH_WILDERNESS_BOT_COUNT" =~ ^[0-9]+$ || ! "$AUTH_WILDERNESS_BOSS_ID" =~ ^[0-9]+$ \
        || ! "$AUTH_WILDERNESS_RADIUS" =~ ^[0-9]+$ ]]; then
        echo "ERROR: Android wilderness target fixture coordinates/count/boss/radius must be numeric" >&2
        return 1
    fi

    local status original_position original_x original_y original_online original_group command
    local menu_values menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y menu_index
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    clear_auth_tutorial_appearance
    original_position="$(read_auth_position)" || return 1
    read -r original_x original_y original_online <<< "$original_position"
    original_group="$(read_auth_group)" || return 1
    command="cinematic bossfight $AUTH_WILDERNESS_BOT_COUNT $AUTH_WILDERNESS_BOSS_ID $AUTH_WILDERNESS_RADIUS"

    echo "Android wilderness target smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_WILDERNESS_PLAYER_X,$AUTH_WILDERNESS_PLAYER_Y"
    update_auth_position "$AUTH_WILDERNESS_PLAYER_X" "$AUTH_WILDERNESS_PLAYER_Y"
    update_auth_group 1
    rm -f "$OUT_DIR/auth-wilderness-command-sent.flag"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_player_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT" || exit 1
        assert_game_activity_for_input "wilderness target login launch" "wilderness-target-lost-after-launch" || exit 1
        screenshot 103-auth-wilderness-login-home
        enable_android_smoke_player_targets
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_client_xy 256 246
        sleep 2
        screenshot 104-auth-wilderness-before-command

        "$ADB" logcat -c || true
        enable_android_smoke_player_targets
        write_android_smoke_player_command "$command"
        "$ADB" shell input keyevent 30
        wait_for_player_command START 10 || exit 1
        touch "$OUT_DIR/auth-wilderness-command-sent.flag"
        sleep 1
        wait_for_player_target "$AUTH_WILDERNESS_TARGET_NAME" 45 >/dev/null || exit 1
        sleep 1
        screenshot 105-auth-wilderness-target-visible

        "$ADB" logcat -c || true
        enable_android_smoke_player_targets
        long_press_player_target "$AUTH_WILDERNESS_TARGET_NAME" 1200 || exit 1
        menu_values="$(wait_for_context_menu 20)" || exit 1
        read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
        screenshot 106-auth-wilderness-player-menu
        menu_index="$(wait_for_player_attack_menu_index 10)" || exit 1
        tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" "$menu_index" || exit 1
        wait_for_player_action "$AUTH_WILDERNESS_PLAYER_ACTION" 20 || exit 1
        sleep 2
        screenshot 107-auth-wilderness-after-player-tap

        "$ADB" shell input keyevent 31 || true
        wait_for_player_command STOP 10 || true
        sleep 2
        "$ADB" shell am force-stop $APP_ID || true
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    ) || status=$?

    if [[ "$status" -ne 0 && -f "$OUT_DIR/auth-wilderness-command-sent.flag" ]]; then
        "$ADB" shell input keyevent 31 >/dev/null 2>&1 || true
        sleep 2
    fi
    disable_android_smoke_player_targets
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    update_auth_position "$original_x" "$original_y" || true
    update_auth_group "$original_group" || true
    sleep 1
    return "$status"
}

run_authenticated_pvp_stress_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "Skipping Android PvP stress smoke; ANDROID_SMOKE_AUTH_DB is required to seed and restore fixtures."
        return
    fi
    if [[ ! "$AUTH_WILDERNESS_PLAYER_X" =~ ^[0-9]+$ || ! "$AUTH_WILDERNESS_PLAYER_Y" =~ ^[0-9]+$ \
        || ! "$AUTH_WILDERNESS_BOT_COUNT" =~ ^[0-9]+$ || ! "$AUTH_WILDERNESS_BOSS_ID" =~ ^[0-9]+$ \
        || ! "$AUTH_WILDERNESS_RADIUS" =~ ^[0-9]+$ || ! "$AUTH_PVP_STRESS_SPELL_ID" =~ ^[0-9]+$ ]]; then
        echo "ERROR: Android PvP stress fixture coordinates/count/boss/radius/spell must be numeric" >&2
        return 1
    fi

    local status original_position original_x original_y original_online original_group player_id command
    local fixture_position
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT"
    clear_auth_tutorial_appearance
    original_position="$(read_auth_position)" || return 1
    read -r original_x original_y original_online <<< "$original_position"
    original_group="$(read_auth_group)" || return 1
    player_id="$(read_auth_player_id)" || return 1
    command="cinematic bossfight $AUTH_WILDERNESS_BOT_COUNT $AUTH_WILDERNESS_BOSS_ID $AUTH_WILDERNESS_RADIUS"
    fixture_position="$AUTH_WILDERNESS_PLAYER_X $AUTH_WILDERNESS_PLAYER_Y 0"

    snapshot_auth_inventory "$player_id"
    snapshot_auth_stats "$player_id"
    echo "Android PvP stress smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_WILDERNESS_PLAYER_X,$AUTH_WILDERNESS_PLAYER_Y"
    update_auth_position "$AUTH_WILDERNESS_PLAYER_X" "$AUTH_WILDERNESS_PLAYER_Y"
    update_auth_group 1
    ensure_auth_pvp_stress_stats "$player_id"
    seed_auth_inventory_slot "$player_id" "$AUTH_PVP_STRESS_FOOD_SLOT" "$AUTH_PVP_STRESS_FOOD_ID" 1
    seed_auth_inventory_slot "$player_id" "$AUTH_PVP_STRESS_POTION_SLOT" "$AUTH_PVP_STRESS_POTION_ID" 1
    seed_auth_inventory_slot "$player_id" 2 33 50
    seed_auth_inventory_slot "$player_id" 3 35 50
    rm -f "$OUT_DIR/auth-pvp-stress-command-sent.flag"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as $APP_ID rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_magic_prayer
        enable_android_smoke_player_targets
        enable_android_smoke_walk
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT" || exit 1
        enable_android_smoke_inventory_targets
        enable_android_smoke_magic_prayer
        enable_android_smoke_player_targets
        enable_android_smoke_walk
        tap_existing_user_button
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        close_auth_intro_dialog_if_present
        sleep 2
        screenshot 115-auth-pvp-stress-ready

        tap_inventory_tab
        wait_for_inventory_target "$AUTH_PVP_STRESS_POTION_SLOT" 30 >/dev/null || exit 1
        tap_inventory_slot "$AUTH_PVP_STRESS_POTION_SLOT" || exit 1
        wait_for_inventory_action "$AUTH_PVP_STRESS_POTION_SLOT" ITEM_USE 20 || exit 1
        sleep 2
        screenshot 116-auth-pvp-stress-after-potion

        wait_for_inventory_target "$AUTH_PVP_STRESS_FOOD_SLOT" 30 >/dev/null || exit 1
        tap_inventory_slot "$AUTH_PVP_STRESS_FOOD_SLOT" || exit 1
        wait_for_inventory_action "$AUTH_PVP_STRESS_FOOD_SLOT" ITEM_USE 20 || exit 1
        sleep 2
        screenshot 117-auth-pvp-stress-after-food

        "$ADB" logcat -c || true
        enable_android_smoke_player_targets
        write_android_smoke_player_command "$command"
        "$ADB" shell input keyevent 30
        wait_for_player_command START 10 || exit 1
        touch "$OUT_DIR/auth-pvp-stress-command-sent.flag"
        sleep 1
        wait_for_player_target "$AUTH_WILDERNESS_TARGET_NAME" 45 >/dev/null || exit 1
        screenshot 118-auth-pvp-stress-target-visible

        tap_magic_prayer_tab
        local magic_line row_x row_y row_height spell_y
        magic_line="$(wait_for_magic_prayer_tab magic 30)" || exit 1
        row_x="$(log_int_or_default "$magic_line" row0X 383)"
        row_y="$(log_int_or_default "$magic_line" row0Y 69)"
        row_height="$(log_int_or_default "$magic_line" rowHeight 12)"
        spell_y=$((row_y + AUTH_PVP_STRESS_SPELL_ID * row_height))
        tap_magic_prayer_row_until_action "$row_x" "$spell_y" SPELL_SELECTED "$AUTH_PVP_STRESS_SPELL_ID" || exit 1
        sleep 1
        screenshot 119-auth-pvp-stress-spell-selected

        cast_selected_spell_on_player_target "$AUTH_WILDERNESS_TARGET_NAME" || exit 1
        sleep 2
        screenshot 120-auth-pvp-stress-after-player-cast

        "$ADB" logcat -c || true
        enable_android_smoke_player_targets
        long_press_player_target "$AUTH_WILDERNESS_TARGET_NAME" 1200 || exit 1
        local menu_values menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y menu_index
        menu_values="$(wait_for_context_menu 20)" || exit 1
        read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
        menu_index="$(wait_for_player_attack_menu_index 10)" || exit 1
        tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" "$menu_index" || exit 1
        wait_for_player_action "$AUTH_WILDERNESS_PLAYER_ACTION" 20 || exit 1
        sleep 2
        screenshot 121-auth-pvp-stress-after-attack

        "$ADB" logcat -c || true
        enable_android_smoke_walk
        tap_client_xy "$AUTH_PVP_STRESS_WALK_CLIENT_X" "$AUTH_PVP_STRESS_WALK_CLIENT_Y"
        wait_for_walk_action LANDSCAPE_WALK_HERE 20 || exit 1
        sleep 8
        screenshot 122-auth-pvp-stress-after-walk

        "$ADB" shell input keyevent 31 || true
        wait_for_player_command STOP 10 || true
        sleep 1
        "$ADB" shell am force-stop $APP_ID || true
        wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
        wait_for_auth_inventory_slot_not_catalog "$player_id" "$AUTH_PVP_STRESS_FOOD_SLOT" "$AUTH_PVP_STRESS_FOOD_ID" 30 || exit 1
        wait_for_auth_curstat_greater_than "$player_id" strength 20 30 || exit 1
        assert_auth_position_changed_after_logout "$fixture_position" || exit 1
    ) || status=$?

    if [[ "$status" -ne 0 && -f "$OUT_DIR/auth-pvp-stress-command-sent.flag" ]]; then
        "$ADB" shell input keyevent 31 >/dev/null 2>&1 || true
        sleep 2
    fi
    disable_android_smoke_inventory_targets
    disable_android_smoke_magic_prayer
    disable_android_smoke_player_targets
    disable_android_smoke_walk
    "$ADB" shell am force-stop $APP_ID || true
    wait_auth_offline "$AUTH_OFFLINE_TIMEOUT" || true
    restore_auth_inventory "$player_id" || true
    restore_auth_stats "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    update_auth_group "$original_group" || true
    sleep 1
    return "$status"
}

app_file_sha256() {
	local relative_path="$1"
	"$ADB" exec-out run-as "$APP_ID" cat "files/$relative_path" \
		| shasum -a 256 \
		| awk '{print $1}'
}

assert_bundled_cache_file() {
	local relative_path="$1"
	local source_file="$REPO_ROOT/Client_Base/Cache/$relative_path"
	local source_hash device_hash
	[[ -s "$source_file" ]] || {
		echo "ERROR: required source cache file is missing or empty: $source_file" >&2
		return 1
	}
	source_hash="$(shasum -a 256 "$source_file" | awk '{print $1}')"
	device_hash="$(app_file_sha256 "$relative_path")"
	if [[ "$device_hash" != "$source_hash" ]]; then
		echo "ERROR: installed cache hash mismatch for $relative_path" >&2
		echo "  source: $source_hash" >&2
		echo "  device: $device_hash" >&2
		return 1
	fi
	echo "Verified bundled cache file: $relative_path sha256=$device_hash"
}

assert_cache_marker_present() {
	if ! "$ADB" shell run-as "$APP_ID" grep -q 'cache.install_identity' \
		shared_prefs/voidscape.bootstrap.xml 2>/dev/null; then
		echo "ERROR: bundled-cache completion marker is missing" >&2
		return 1
	fi
}

assert_cache_marker_absent() {
	if "$ADB" shell run-as "$APP_ID" grep -q 'cache.install_identity' \
		shared_prefs/voidscape.bootstrap.xml 2>/dev/null; then
		echo "ERROR: failed bundled-cache install left a completion marker" >&2
		return 1
	fi
}

assert_no_cache_temp_files() {
	local leftovers
	leftovers="$("$ADB" shell run-as "$APP_ID" find files -type f -print 2>/dev/null \
		| tr -d '\r' \
		| grep '\.voidscape-tmp$' || true)"
	if [[ -n "$leftovers" ]]; then
		echo "ERROR: cache bootstrap left temporary files:" >&2
		printf '%s\n' "$leftovers" >&2
		return 1
	fi
}

cache_file_mtime() {
	local relative_path="$1"
	"$ADB" shell run-as "$APP_ID" stat -c %Y "files/$relative_path" 2>/dev/null | tr -d '\r'
}

assert_endpoint_state() {
	local expected_host="$1"
	local expected_port="$2"
	local timeout="${3:-10}"
	local deadline=$((SECONDS + timeout))
	local mirrored_host=""
	local mirrored_port=""
	local preferences=""
	while (( SECONDS < deadline )); do
		mirrored_host="$("$ADB" shell run-as "$APP_ID" cat files/ip.txt 2>/dev/null | tr -d '\r' || true)"
		mirrored_port="$("$ADB" shell run-as "$APP_ID" cat files/port.txt 2>/dev/null | tr -d '\r' || true)"
		preferences="$("$ADB" shell run-as "$APP_ID" cat shared_prefs/voidscape.bootstrap.xml 2>/dev/null | tr -d '\r' || true)"
		if [[ "$mirrored_host" == "$expected_host" && "$mirrored_port" == "$expected_port" \
			&& "$preferences" == *"name=\"endpoint.host\">$expected_host</string>"* \
			&& "$preferences" == *"name=\"endpoint.port\">$expected_port</string>"* ]]; then
			echo "Verified canonical endpoint and mirrors: $expected_host:$expected_port"
			return 0
		fi
		sleep 1
	done
	echo "ERROR: endpoint transaction mismatch; expected $expected_host:$expected_port" >&2
	echo "  mirrors: $mirrored_host:$mirrored_port" >&2
	printf '%s\n' "$preferences" >&2
	return 1
}

start_bootstrap_wrapper() {
	local fail_after="${1:--1}"
	local release_endpoint_policy="${2:-0}"
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	local args=(
		-n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater"
	)
	if [[ "$fail_after" -ge 0 ]]; then
		args+=(--ei voidscape.smoke.cache_fail_after_files "$fail_after")
	fi
	if [[ "$release_endpoint_policy" == "1" ]]; then
		args+=(--ez voidscape.smoke.release_endpoint_policy true)
	fi
	"$ADB" shell am start "${args[@]}" >/dev/null
}

run_bootstrap_smoke() {
	local marker_probe="video/Authentic_Sprites.orsc"
	local before_mtime after_mtime
	local endpoint_host="127.0.0.2"
	local endpoint_port="45678"

	disable_android_network_for_bootstrap
	"$ADB" shell pm clear "$APP_ID" >/dev/null
	"$ADB" logcat -c || true
	start_bootstrap_wrapper 3 1
	wait_for_text "Game data unavailable" 90 || {
		echo "ERROR: injected cache failure did not reach the retry/close state" >&2
		return 1
	}
	wait_for_log_pattern 'CACHE_BOOTSTRAP injected-failure copied=3' 10
	assert_endpoint_state "voidscape.gg" "43596"
	assert_cache_marker_absent
	assert_no_cache_temp_files
	screenshot 00-bootstrap-injected-failure

	"$ADB" logcat -c || true
	start_bootstrap_wrapper
	wait_for_wrapper_ready
	wait_for_log_pattern 'CACHE_BOOTSTRAP install-complete identity=' 15
	assert_cache_marker_present
	assert_no_cache_temp_files
	assert_bundled_cache_file "video/Authentic_Sprites.orsc"
	assert_bundled_cache_file "video/models.orsc"
	assert_bundled_cache_file "video/Authentic_Landscape.orsc"
	assert_bundled_cache_file "video/library.orsc"
	screenshot 01-bootstrap-recovered-ready

	before_mtime="$(cache_file_mtime "$marker_probe")"
	"$ADB" logcat -c || true
	start_bootstrap_wrapper
	wait_for_wrapper_ready
	wait_for_log_pattern 'CACHE_BOOTSTRAP marker-hit identity=' 30
	after_mtime="$(cache_file_mtime "$marker_probe")"
	if [[ -z "$before_mtime" || "$after_mtime" != "$before_mtime" ]]; then
		echo "ERROR: repeat launch rewrote bundled cache files ($before_mtime -> $after_mtime)" >&2
		return 1
	fi
	echo "Verified repeat launch skipped bundled cache copy (mtime=$after_mtime)"

	"$ADB" shell run-as "$APP_ID" dd if=/dev/null of=files/video/models.orsc >/dev/null 2>&1
	"$ADB" logcat -c || true
	start_bootstrap_wrapper
	wait_for_wrapper_ready
	wait_for_log_pattern 'CACHE_BOOTSTRAP repair-required identity=' 15
	wait_for_log_pattern 'CACHE_BOOTSTRAP install-complete identity=' 90
	assert_bundled_cache_file "video/models.orsc"
	assert_cache_marker_present
	assert_no_cache_temp_files
	screenshot 02-bootstrap-required-file-repaired

	"$ADB" logcat -c || true
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	"$ADB" shell am start -n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater" \
		-e voidscape.smoke.endpoint_host "$endpoint_host" \
		-e voidscape.smoke.endpoint_port "$endpoint_port" >/dev/null
	wait_for_wrapper_ready
	assert_endpoint_state "$endpoint_host" "$endpoint_port"

	printf '%s' 9 | "$ADB" shell run-as "$APP_ID" tee files/port.txt >/dev/null
	"$ADB" logcat -c || true
	start_bootstrap_wrapper
	wait_for_wrapper_ready
	wait_for_log_pattern 'ENDPOINT_BOOTSTRAP mirror-repair endpoint=127\.0\.0\.2:45678' 30
	assert_endpoint_state "$endpoint_host" "$endpoint_port"

	"$ADB" logcat -c || true
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	"$ADB" shell am start -n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater" \
		-e voidscape.smoke.endpoint_host 192.0.2.1 >/dev/null
	wait_for_wrapper_ready
	wait_for_log_pattern 'Ignoring incomplete smoke server endpoint' 30
	assert_endpoint_state "$endpoint_host" "$endpoint_port"

	# Seed the exact endpoint shipped by the prior Play build, then restart under
	# the release endpoint policy to prove an in-place upgrade reaches DNS.
	"$ADB" logcat -c || true
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	"$ADB" shell am start -n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater" \
		-e voidscape.smoke.endpoint_host 5.161.114.251 \
		-e voidscape.smoke.endpoint_port 43596 >/dev/null
	wait_for_wrapper_ready
	assert_endpoint_state "5.161.114.251" "43596"
	"$ADB" logcat -c || true
	start_bootstrap_wrapper -1 1
	wait_for_wrapper_ready
	wait_for_log_pattern 'ENDPOINT_BOOTSTRAP legacy-public-migrated endpoint=voidscape\.gg:43596' 30
	assert_endpoint_state "voidscape.gg" "43596"

	# A non-legacy pair remains a custom endpoint under the same release policy.
	"$ADB" logcat -c || true
	"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
	"$ADB" shell am start -n "$APP_ID/com.openrsc.android.updater.ApplicationUpdater" \
		-e voidscape.smoke.endpoint_host qa.custom.voidscape.invalid \
		-e voidscape.smoke.endpoint_port 45679 >/dev/null
	wait_for_wrapper_ready
	assert_endpoint_state "qa.custom.voidscape.invalid" "45679"
	"$ADB" logcat -c || true
	start_bootstrap_wrapper -1 1
	wait_for_wrapper_ready
	assert_endpoint_state "qa.custom.voidscape.invalid" "45679"
	screenshot 03-bootstrap-endpoint-repaired

	restore_android_network_state
	echo "Android bootstrap smoke evidence written to $OUT_DIR"
}

launch_wrapper() {
	local clear_saved_logins="${1:-1}"
	local credential_clear_value=false
	if [[ "$clear_saved_logins" == "1" ]]; then
		credential_clear_value=true
	fi
    "$ADB" shell am force-stop $APP_ID
    if [[ -n "$PENDING_SERVER_HOST" && -n "$PENDING_SERVER_PORT" ]]; then
        "$ADB" shell am start -n $APP_ID/com.openrsc.android.updater.ApplicationUpdater \
            -e voidscape.smoke.endpoint_host "$PENDING_SERVER_HOST" \
            -e voidscape.smoke.endpoint_port "$PENDING_SERVER_PORT" \
            --ez voidscape.smoke.clear_credentials "$credential_clear_value" >/dev/null
        PENDING_SERVER_HOST=""
        PENDING_SERVER_PORT=""
    else
        "$ADB" shell am start -n $APP_ID/com.openrsc.android.updater.ApplicationUpdater \
            --ez voidscape.smoke.clear_credentials "$credential_clear_value" >/dev/null
    fi
}

launch_to_login_home() {
	local clear_saved_logins="${1:-1}"
	launch_wrapper "$clear_saved_logins"
	wait_for_wrapper_ready
	tap_play_button
	ensure_game_activity_from_wrapper
	dismiss_fullscreen_education
}

run_account_handoff_smoke() {
	if [[ -n "$ACCOUNT_HANDOFF_HOST" ]]; then
		if [[ ! "$ACCOUNT_HANDOFF_PORT" =~ ^[0-9]+$ ]] \
			|| (( ACCOUNT_HANDOFF_PORT < 1 || ACCOUNT_HANDOFF_PORT > 65535 )); then
			echo "ERROR: ANDROID_SMOKE_ACCOUNT_HANDOFF_PORT must be between 1 and 65535" >&2
			exit 1
		fi
		PENDING_SERVER_HOST="$ACCOUNT_HANDOFF_HOST"
		PENDING_SERVER_PORT="$ACCOUNT_HANDOFF_PORT"
	fi
	enable_android_smoke_login
	"$ADB" logcat -c || true
	launch_to_login_home
	local home_line
	home_line="$(wait_for_login_state 0 20)" || exit 1
	screenshot 00-account-home
	tap_login_state_target 0 homeNew 8 || {
		echo "ERROR: Create Account target was absent from Android login telemetry" >&2
		login_log_tail
		exit 1
	}
	wait_for_external_activity "Create Account portal" 15 \
		"https://voidscape.gg/portal?auth=register" || exit 1
	screenshot 01-create-account-portal-handoff
	echo "Android Create Account handoff evidence written to $OUT_DIR"
}

if [[ "$ONLY_BOOTSTRAP" -eq 1 ]]; then
	run_bootstrap_smoke
	exit 0
fi

if [[ "$ONLY_ACCOUNT_HANDOFF" -eq 1 ]]; then
	run_account_handoff_smoke
	exit 0
fi

if [[ "$ONLY_AUTH_CREDENTIALS" -eq 1 ]]; then
	run_authenticated_credential_smoke
	echo "Android encrypted credential smoke evidence written to $OUT_DIR"
	exit 0
fi

if [[ "$ONLY_AUTH_LOGIN" -eq 1 ]]; then
	run_authenticated_login_smoke
	echo "Android auth/login smoke screenshots written to $OUT_DIR"
	exit 0
fi

if [[ "$ONLY_AUTH_LIFECYCLE" -eq 1 ]]; then
	run_authenticated_lifecycle_smoke
	echo "Android auth/lifecycle smoke screenshots written to $OUT_DIR"
	exit 0
fi

if [[ "$ONLY_AUTH_CAMERA" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-camera requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    run_authenticated_camera_rotate_smoke
    echo "Android camera smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_ZOOM" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-zoom requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    run_authenticated_zoom_smoke
    echo "Android zoom smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_CHAT_TABS" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-chat-tabs requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    run_authenticated_chat_tab_smoke
    echo "Android mobile hub smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_CHAT_SEND" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-chat-send requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    run_authenticated_chat_send_smoke
    echo "Android chat send smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_BANK" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-bank requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-bank requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_bank_smoke
    echo "Android bank smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_SHOP" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-shop requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-shop requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_shop_smoke
    echo "Android shop smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_EQUIPMENT" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-equipment requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-equipment requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_equipment_smoke
    echo "Android equipment smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_MAGIC_PRAYER" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-magic-prayer requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-magic-prayer requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_magic_prayer_smoke
    echo "Android magic/prayer smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_WORLD_MAP" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-world-map requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    run_authenticated_world_map_smoke
    echo "Android world-map smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_SETTINGS" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-settings requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-settings requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_settings_smoke
    echo "Android settings smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_AFK" -eq 1 ]]; then
	if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
		echo "ERROR: --only-auth-afk requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
		exit 1
	fi
	run_authenticated_afk_smoke
	echo "Android AFK monitor smoke screenshots written to $OUT_DIR"
	exit 0
fi

if [[ "$ONLY_AUTH_GROUND_LOOT" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-ground-loot requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-ground-loot requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_ground_loot_smoke
    echo "Android ground-loot smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_WILDERNESS_TARGET" -eq 1 ]]; then
    run_authenticated_wilderness_target_smoke
    echo "Android wilderness target smoke screenshots written to $OUT_DIR"
    exit 0
fi

if [[ "$ONLY_AUTH_PVP_STRESS" -eq 1 ]]; then
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        echo "ERROR: --only-auth-pvp-stress requires ANDROID_SMOKE_AUTH_USER and ANDROID_SMOKE_AUTH_PASS" >&2
        exit 1
    fi
    if [[ -z "$AUTH_DB" ]]; then
        echo "ERROR: --only-auth-pvp-stress requires ANDROID_SMOKE_AUTH_DB" >&2
        exit 1
    fi
    run_authenticated_pvp_stress_smoke
    echo "Android PvP stress smoke screenshots written to $OUT_DIR"
    exit 0
fi

launch_wrapper
screenshot 00-bootstrap
wait_for_wrapper_ready
screenshot 01-ready-play

long_press_pct 50 71 1200
wait_for_text "Server Options" 10 || sleep 1
screenshot 02-server-picker
"$ADB" shell input keyevent BACK
sleep 1
screenshot 03-server-picker-back-ready
long_press_pct 50 71 1200
wait_for_text "Server Options" 10 || sleep 1
screenshot 04-server-picker-reopened
tap_pct 33 60
wait_for_text "Manual Server" 10 || sleep 1
screenshot 05-manual-server

launch_to_login_home
screenshot 06-login-home
tap_existing_user_button
sleep 3
screenshot 07-existing-user-keyboard
"$ADB" shell input text testuser
"$ADB" shell input keyevent BACK
sleep 1
tap_login_ok_button
sleep 1
screenshot 08-login-missing-password-error

tap_login_state_target 2 pass 8 || exit 1
sleep 2
assert_soft_keyboard_visible || exit 1
screenshot 08a-login-error-password-keyboard-reopened
tap_login_state_target 2 cancel 8 || exit 1
login_home_line="$(wait_for_login_state 0 10)" || exit 1
[[ "$(extract_log_value "$login_home_line" keyboard)" == "false" ]] || {
	echo "ERROR: Android login state still reports the keyboard open after Cancel" >&2
	login_log_tail
	exit 1
}
sleep 1
assert_soft_keyboard_hidden || exit 1
screenshot 09-login-home-after-error
tap_existing_user_button
sleep 3
screenshot 10-existing-user-keyboard
"$ADB" shell input text testuser
"$ADB" shell input keyevent ENTER
"$ADB" shell input text pass123
sleep 1
screenshot 11-existing-user-typed

"$ADB" shell input keyevent BACK
sleep 1
screenshot 12-existing-user-back-closed-keyboard
tap_pct 50 42
sleep 1
screenshot 13-existing-user-credentials-saved

launch_to_login_home
screenshot 14-login-home-after-credentials-save
tap_existing_user_button
sleep 3
screenshot 15-saved-credentials-loaded
"$ADB" shell input keyevent BACK
sleep 1
tap_pct 50 59
wait_for_external_activity "Recover account portal" 15 "https://voidscape.gg/portal?auth=recovery" || exit 1
screenshot 16-recover-account-handoff

"$ADB" shell input keyevent BACK
wait_for_login_state 2 10 || exit 1
"$ADB" shell input keyevent BACK
wait_for_login_state 0 10 || exit 1
screenshot 17-existing-user-back-home
tap_create_account_button
wait_for_external_activity "Create Account portal" 15 "https://voidscape.gg/portal?auth=register" || exit 1
screenshot 18-create-account-portal-handoff

"$ADB" shell input keyevent BACK
wait_for_login_state 0 10 || exit 1
launch_to_login_home
"$ADB" shell input keyevent HOME
sleep 2
screenshot 19-background-home
"$ADB" shell am start -n $APP_ID/com.openrsc.android.updater.ApplicationUpdater >/dev/null
sleep 3
ensure_game_activity_from_wrapper
screenshot 20-resume-login-home
tap_existing_user_button
sleep 3
screenshot 21-resume-existing-user-keyboard
"$ADB" shell input text resumeuser
sleep 1
screenshot 22-resume-existing-user-typed
"$ADB" shell input keyevent BACK
sleep 1

run_authenticated_logout_smoke
if [[ -n "$AUTH_USER" && -n "$AUTH_PASS" ]]; then
	# App switching without a disconnect is a release contract, not an optional
	# focused check. Keep it in the canonical full authenticated gate as well.
	run_authenticated_lifecycle_smoke
fi
run_authenticated_npc_smoke
run_authenticated_object_smoke
run_authenticated_inventory_smoke
run_authenticated_item_on_object_smoke
run_authenticated_item_on_npc_smoke
run_authenticated_context_menu_smoke
run_authenticated_edge_context_menu_smoke
run_authenticated_camera_rotate_smoke
run_authenticated_zoom_smoke
run_authenticated_chat_tab_smoke
run_authenticated_chat_send_smoke
run_authenticated_bank_smoke
run_authenticated_shop_smoke
run_authenticated_equipment_smoke
run_authenticated_magic_prayer_smoke
run_authenticated_world_map_smoke
run_authenticated_settings_smoke
run_authenticated_afk_smoke
run_authenticated_ground_loot_smoke
run_authenticated_wilderness_target_smoke
run_authenticated_pvp_stress_smoke

launch_wrapper
wait_for_wrapper_ready
screenshot 110-bad-server-ready-play
long_press_pct 50 71 1200
wait_for_text "Server Options" 10 || sleep 1
screenshot 111-bad-server-picker
tap_pct 33 60
wait_for_text "Manual Server" 10 || sleep 1
screenshot 112-bad-server-manual
tap_pct 50 51
input_text 127.0.0.1
tap_pct 50 39
input_text 9
"$ADB" shell input keyevent BACK
sleep 1
screenshot 113-bad-server-manual-filled
tap_play_button
sleep 45
screenshot 114-bad-server-loading-error

write_server_endpoint voidscape.gg 43596 || true
"$ADB" shell am force-stop $APP_ID || true

echo "Android smoke screenshots written to $OUT_DIR"
