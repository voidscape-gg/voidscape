#!/usr/bin/env bash
# android-smoke.sh — build/install the Android APK and capture core QA screenshots

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"
AVD_NAME="${AVD_NAME:-voidscape_api35}"
APK="$REPO_ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk"
OUT_DIR="${ANDROID_SCREENSHOT_DIR:-$REPO_ROOT/tmp/android-smoke-$(date +%Y%m%d-%H%M%S)}"
APP_FILES="/data/user/0/com.voidscape.client/files"
SMOKE_NPC_TARGETS_FLAG="$APP_FILES/android-smoke-npc-targets.flag"
SMOKE_PLAYER_TARGETS_FLAG="$APP_FILES/android-smoke-player-targets.flag"
SMOKE_PLAYER_COMMAND_FILE="$APP_FILES/android-smoke-player-command.txt"
SMOKE_OBJECT_TARGETS_FLAG="$APP_FILES/android-smoke-object-targets.flag"
SMOKE_INVENTORY_TARGETS_FLAG="$APP_FILES/android-smoke-inventory-targets.flag"
SMOKE_CAMERA_FLAG="$APP_FILES/android-smoke-camera.flag"
SMOKE_ZOOM_FLAG="$APP_FILES/android-smoke-zoom.flag"
SMOKE_CHAT_TABS_FLAG="$APP_FILES/android-smoke-chat-tabs.flag"
SMOKE_CHAT_SEND_FLAG="$APP_FILES/android-smoke-chat-send.flag"
SMOKE_BANK_FLAG="$APP_FILES/android-smoke-bank.flag"
SMOKE_SHOP_FLAG="$APP_FILES/android-smoke-shop.flag"
SMOKE_EQUIPMENT_FLAG="$APP_FILES/android-smoke-equipment.flag"
SMOKE_MAGIC_PRAYER_FLAG="$APP_FILES/android-smoke-magic-prayer.flag"
SMOKE_WORLD_MAP_FLAG="$APP_FILES/android-smoke-world-map.flag"
SMOKE_SETTINGS_FLAG="$APP_FILES/android-smoke-settings.flag"
SMOKE_GROUND_LOOT_FLAG="$APP_FILES/android-smoke-ground-loot.flag"
BUILD=1
INSTALL=1
ONLY_AUTH_ZOOM=0
ONLY_AUTH_CHAT_TABS=0
ONLY_AUTH_CHAT_SEND=0
ONLY_AUTH_BANK=0
ONLY_AUTH_SHOP=0
ONLY_AUTH_EQUIPMENT=0
ONLY_AUTH_MAGIC_PRAYER=0
ONLY_AUTH_WORLD_MAP=0
ONLY_AUTH_SETTINGS=0
ONLY_AUTH_GROUND_LOOT=0
ONLY_AUTH_WILDERNESS_TARGET=0
ONLY_AUTH_LOGIN=0
ONLY_AUTH_LIFECYCLE=0
AUTH_USER="${ANDROID_SMOKE_AUTH_USER:-}"
AUTH_PASS="${ANDROID_SMOKE_AUTH_PASS:-}"
AUTH_HOST="${ANDROID_SMOKE_AUTH_HOST:-10.0.2.2}"
AUTH_PORT="${ANDROID_SMOKE_AUTH_PORT:-43596}"
AUTH_DB="${ANDROID_SMOKE_AUTH_DB:-}"
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
AUTH_ZOOM_SWIPE_X="${ANDROID_SMOKE_ZOOM_SWIPE_X:-256}"
AUTH_ZOOM_SWIPE_START_Y="${ANDROID_SMOKE_ZOOM_SWIPE_START_Y:-80}"
AUTH_ZOOM_SWIPE_END_Y="${ANDROID_SMOKE_ZOOM_SWIPE_END_Y:-190}"
AUTH_ZOOM_SWIPE_DURATION_MS="${ANDROID_SMOKE_ZOOM_SWIPE_DURATION_MS:-700}"
AUTH_CHAT_TAB_Y="${ANDROID_SMOKE_CHAT_TAB_Y:-338}"
AUTH_CHAT_TAB_SEQUENCE="${ANDROID_SMOKE_CHAT_TAB_SEQUENCE:-CHAT,QUEST,PRIVATE,ALL}"
AUTH_CHAT_TAB_ALL_X="${ANDROID_SMOKE_CHAT_TAB_ALL_X:-55}"
AUTH_CHAT_TAB_CHAT_X="${ANDROID_SMOKE_CHAT_TAB_CHAT_X:-152}"
AUTH_CHAT_TAB_QUEST_X="${ANDROID_SMOKE_CHAT_TAB_QUEST_X:-255}"
AUTH_CHAT_TAB_PRIVATE_X="${ANDROID_SMOKE_CHAT_TAB_PRIVATE_X:-355}"
AUTH_CHAT_TAB_CLAN_X="${ANDROID_SMOKE_CHAT_TAB_CLAN_X:-457}"
AUTH_CHAT_MESSAGE="${ANDROID_SMOKE_CHAT_MESSAGE:-androidchat}"
AUTH_CHAT_KEYBOARD_X="${ANDROID_SMOKE_CHAT_KEYBOARD_X:-291}"
AUTH_CHAT_KEYBOARD_Y="${ANDROID_SMOKE_CHAT_KEYBOARD_Y:-19}"
AUTH_CHAT_ENTRY_X="${ANDROID_SMOKE_CHAT_ENTRY_X:-256}"
AUTH_CHAT_ENTRY_Y="${ANDROID_SMOKE_CHAT_ENTRY_Y:-147}"
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
AUTH_BANK_FIXTURE_BANK_SLOTS="${ANDROID_SMOKE_BANK_FIXTURE_BANK_SLOTS:-48}"
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
AUTH_FIXTURE_ITEM_ID_BASE="${ANDROID_SMOKE_FIXTURE_ITEM_ID_BASE:-1000000}"

usage() {
    cat <<EOF
Usage: scripts/android-smoke.sh [--no-build] [--no-install] [--only-auth-login] [--only-auth-lifecycle] [--only-auth-zoom] [--only-auth-chat-tabs] [--only-auth-chat-send] [--only-auth-bank] [--only-auth-shop] [--only-auth-equipment] [--only-auth-magic-prayer] [--only-auth-world-map] [--only-auth-settings] [--only-auth-ground-loot] [--only-auth-wilderness-target] [--out DIR]

Builds and installs the debug APK, starts $AVD_NAME when no Android device is
connected, launches the wrapper, and captures the core Android QA screenshots.

Environment:
  ANDROID_HOME / ANDROID_SDK_ROOT  Android SDK root
  AVD_NAME                         Emulator name, default: voidscape_api35
  ANDROID_SCREENSHOT_DIR           Output directory
  ANDROID_SMOKE_AUTH_USER          Optional game username for in-game/logout smoke
  ANDROID_SMOKE_AUTH_PASS          Optional game password for in-game/logout smoke
  ANDROID_SMOKE_AUTH_HOST          Optional auth smoke host, default: 10.0.2.2
  ANDROID_SMOKE_AUTH_PORT          Optional auth smoke port, default: 43596
  ANDROID_SMOKE_AUTH_DB            Optional SQLite DB path for movement assertions
  --only-auth-login                Focused auth smoke; defaults to AndroidMap/androidmap1 and server/inc/sqlite/voidscape.db
  --only-auth-lifecycle            Focused auth smoke for login, resume/relaunch, logout, and crash checks
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
  ANDROID_SMOKE_ZOOM_SWIPE_X         Optional zoom swipe client x, default: 256
  ANDROID_SMOKE_ZOOM_SWIPE_START_Y   Optional zoom swipe start client y, default: 80
  ANDROID_SMOKE_ZOOM_SWIPE_END_Y     Optional zoom swipe end client y, default: 190
  ANDROID_SMOKE_ZOOM_SWIPE_DURATION_MS Optional zoom swipe duration, default: 700
  ANDROID_SMOKE_CHAT_TAB_Y           Optional chat tab client y, default: 338
  ANDROID_SMOKE_CHAT_TAB_SEQUENCE    Optional comma-separated tabs, default: CHAT,QUEST,PRIVATE,ALL
  ANDROID_SMOKE_CHAT_TAB_*_X         Optional tab client x overrides for ALL/CHAT/QUEST/PRIVATE/CLAN
  ANDROID_SMOKE_CHAT_MESSAGE         Optional in-game chat message, default: androidchat
  ANDROID_SMOKE_CHAT_KEYBOARD_X/Y    Optional keyboard toggle client coordinate, default: 291,19
  ANDROID_SMOKE_CHAT_ENTRY_X/Y       Optional keyboard-open chat entry coordinate, default: 256,147
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
  ANDROID_SMOKE_GROUND_LOOT_ITEM_ID Optional rare-beam fixture item id, default: 93 (Rune battle axe)
  ANDROID_SMOKE_GROUND_LOOT_PLAYER_X/Y Optional DB x/y for ground-loot fixture, default: 23,25
  ANDROID_SMOKE_WILDERNESS_PLAYER_X/Y Optional DB x/y for player-target fixture, default: 23,25
  ANDROID_SMOKE_WILDERNESS_BOT_COUNT Optional cinematic player count, default: 1
  ANDROID_SMOKE_WILDERNESS_BOSS_ID   Optional cinematic anchor NPC id, default: 1 (Bob)
  ANDROID_SMOKE_WILDERNESS_RADIUS    Optional cinematic radius, default: 3
  ANDROID_SMOKE_WILDERNESS_TARGET_NAME Optional target player token, default: cinebot0001
  ANDROID_SMOKE_FIXTURE_ITEM_ID_BASE Optional DB-only fixture itemID floor, default: 1000000
EOF
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
        --only-auth-ground-loot)
            ONLY_AUTH_GROUND_LOOT=1
            shift
            ;;
        --only-auth-wilderness-target)
            ONLY_AUTH_WILDERNESS_TARGET=1
            shift
            ;;
        --only-auth-login)
            ONLY_AUTH_LOGIN=1
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

if [[ ! -x "$ADB" ]]; then
    echo "ERROR: adb not found at $ADB. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi

mkdir -p "$OUT_DIR"

if [[ "$BUILD" -eq 1 ]]; then
    "$SCRIPT_DIR/build-android.sh"
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
    if [[ ! -x "$EMULATOR" ]]; then
        echo "ERROR: no connected Android device and emulator not found at $EMULATOR." >&2
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

enable_android_smoke_npc_targets() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_NPC_TARGETS_FLAG'"
}

disable_android_smoke_npc_targets() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_NPC_TARGETS_FLAG" 2>/dev/null || true
}

enable_android_smoke_player_targets() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_PLAYER_TARGETS_FLAG'"
}

disable_android_smoke_player_targets() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_PLAYER_TARGETS_FLAG $SMOKE_PLAYER_COMMAND_FILE" 2>/dev/null || true
}

write_android_smoke_player_command() {
    local command="$1"
    "$ADB" shell "run-as com.voidscape.client sh -c 'echo $command > $SMOKE_PLAYER_COMMAND_FILE'"
}

enable_android_smoke_object_targets() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_OBJECT_TARGETS_FLAG'"
}

disable_android_smoke_object_targets() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_OBJECT_TARGETS_FLAG" 2>/dev/null || true
}

enable_android_smoke_inventory_targets() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_INVENTORY_TARGETS_FLAG'"
}

disable_android_smoke_inventory_targets() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_INVENTORY_TARGETS_FLAG" 2>/dev/null || true
}

enable_android_smoke_camera() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_CAMERA_FLAG'"
}

disable_android_smoke_camera() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_CAMERA_FLAG" 2>/dev/null || true
}

enable_android_smoke_zoom() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_ZOOM_FLAG'"
}

disable_android_smoke_zoom() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_ZOOM_FLAG" 2>/dev/null || true
}

enable_android_smoke_chat_tabs() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_CHAT_TABS_FLAG'"
}

disable_android_smoke_chat_tabs() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_CHAT_TABS_FLAG" 2>/dev/null || true
}

enable_android_smoke_chat_send() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_CHAT_SEND_FLAG'"
}

disable_android_smoke_chat_send() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_CHAT_SEND_FLAG" 2>/dev/null || true
}

enable_android_smoke_bank() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_BANK_FLAG'"
}

disable_android_smoke_bank() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_BANK_FLAG" 2>/dev/null || true
}

enable_android_smoke_shop() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_SHOP_FLAG'"
}

disable_android_smoke_shop() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_SHOP_FLAG" 2>/dev/null || true
}

enable_android_smoke_equipment() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_EQUIPMENT_FLAG'"
}

disable_android_smoke_equipment() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_EQUIPMENT_FLAG" 2>/dev/null || true
}

enable_android_smoke_magic_prayer() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_MAGIC_PRAYER_FLAG'"
}

disable_android_smoke_magic_prayer() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_MAGIC_PRAYER_FLAG" 2>/dev/null || true
}

enable_android_smoke_world_map() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_WORLD_MAP_FLAG'"
}

disable_android_smoke_world_map() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_WORLD_MAP_FLAG" 2>/dev/null || true
}

enable_android_smoke_settings() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_SETTINGS_FLAG'"
}

disable_android_smoke_settings() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_SETTINGS_FLAG" 2>/dev/null || true
}

enable_android_smoke_ground_loot() {
    "$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && touch $SMOKE_GROUND_LOOT_FLAG'"
}

disable_android_smoke_ground_loot() {
    "$ADB" shell "run-as com.voidscape.client rm -f $SMOKE_GROUND_LOOT_FLAG" 2>/dev/null || true
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
    disable_android_smoke_world_map
    disable_android_smoke_settings
    disable_android_smoke_ground_loot
}

disable_android_smoke_targets
trap disable_android_smoke_targets EXIT

"$ADB" logcat -c || true
"$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true

screen_size() {
    local screenshot_file size
    screenshot_file="$OUT_DIR/.tap-screen.png"
    "$ADB" exec-out screencap -p > "$screenshot_file"
    size="$(file "$screenshot_file" | sed -nE 's/.*PNG image data, ([0-9]+) x ([0-9]+).*/\1 \2/p')"
    if [[ -n "$size" ]]; then
        echo "$size"
        return
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

client_xy_to_screen_xy() {
    local client_x="$1"
    local client_y="$2"
    local width height x y
    read -r width height < <(screen_size)
    if [[ -z "${width:-}" || -z "${height:-}" ]]; then
        echo "ERROR: could not determine Android screen size for client input" >&2
        return 1
    fi

    read -r x y < <(awk -v sw="$width" -v sh="$height" -v cx="$client_x" -v cy="$client_y" 'BEGIN {
        gw=512; gh=346;
        scale=sw/gw;
        if (sh/gh < scale) scale=sh/gh;
        ox=(sw - gw*scale)/2;
        oy=(sh - gh*scale)/2;
        printf "%d %d\n", ox + cx*scale + 0.5, oy + cy*scale + 0.5;
    }')
    echo "$x $y"
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
    "$ADB" exec-out screencap -p > "$OUT_DIR/$name.png"
    echo "Saved $OUT_DIR/$name.png"
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

wait_for_wrapper_ready() {
    wait_for_text "Play" 45 || sleep 2
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
    "$ADB" shell input tap "$x" "$y"
}

input_text() {
    local text="$1"
    text="${text// /%s}"
    "$ADB" shell input text "$text"
}

clear_focused_text_field() {
    local i
    for ((i = 0; i < 24; i++)); do
        "$ADB" shell input keyevent DEL
    done
}

enter_auth_credentials() {
    tap_pct 50 22
    sleep 1
    clear_focused_text_field
    input_text "$AUTH_USER"
    sleep 1
    tap_pct 50 32
    sleep 1
    clear_focused_text_field
    input_text "$AUTH_PASS"
    # adb text input does not require a visible IME; BACK can close GameActivity.
    sleep 1
}

submit_login_and_wait() {
    "$ADB" logcat -c || true
    "$ADB" shell input keyevent ENTER
    wait_for_successful_login 60 || {
        echo "Android login response log not observed; checking auth DB online state." >&2
        wait_auth_online 30
    }
}

assert_resumed_activity() {
	local expected="$1"
	local activities
	activities="$("$ADB" shell dumpsys activity activities | tr -d '\r')"
	if grep -Eq "(^|[[:space:]])(mResumedActivity|topResumedActivity)[:=].*com\\.voidscape\\.client/.*${expected}" <<< "$activities"; then
		return 0
	fi

	echo "ERROR: expected resumed activity containing $expected" >&2
	grep -E "(mResumedActivity|topResumedActivity|mLastResumedActivity)" <<< "$activities" >&2 || true
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
	grep -Eq "(^|[[:space:]])(mResumedActivity|topResumedActivity)[:=].*com\\.voidscape\\.client/.*${expected}" <<< "$activities"
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
			tap_text "Play" last || tap_pct 50 60
		fi
		sleep 3
	done

	assert_resumed_activity "GameActivity"
}

write_server_endpoint() {
	local host="$1"
	local port="$2"
	"$ADB" shell "run-as com.voidscape.client sh -c 'mkdir -p $APP_FILES && printf %s \"$host\" > $APP_FILES/ip.txt && printf %s \"$port\" > $APP_FILES/port.txt'"
}

launch_game_with_endpoint() {
    local host="$1"
    local port="$2"
    "$ADB" shell am force-stop com.voidscape.client || true
    write_server_endpoint "$host" "$port"
    launch_wrapper
    wait_for_wrapper_ready
    long_press_pct 50 60 1200
    wait_for_text "Server Options" 10 || sleep 1
    tap_pct 33 60
    wait_for_text "Manual Server" 10 || sleep 1
    tap_pct 50 51
    input_text "$host"
    tap_pct 50 39
    input_text "$port"
    "$ADB" shell input keyevent BACK
    sleep 1
    tap_text "Play" last || tap_pct 74 60
    ensure_game_activity_from_wrapper 120 || return 1
    sleep 5
    dismiss_fullscreen_education
    return 0
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
		AUTH_USER="AndroidMap"
	fi
	if [[ -z "$AUTH_PASS" ]]; then
		AUTH_PASS="androidmap1"
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

	local safe_user row player_id saved_user saved_pass saved_salt online expected_hash
	safe_user="$(sql_escape "$AUTH_USER")"
	row="$(sqlite3 -cmd '.timeout 5000' -noheader -separator $'\t' "$AUTH_DB" \
		"select id, username, pass, salt, online from players where lower(username) = lower('$safe_user') limit 1;")"
	if [[ -z "$row" ]]; then
		echo "ERROR: no player row found for $AUTH_USER in $AUTH_DB" >&2
		exit 1
	fi

	IFS=$'\t' read -r player_id saved_user saved_pass saved_salt online <<< "$row"
	expected_hash="$(auth_password_hash "$saved_salt" "$AUTH_PASS" || true)"
	if [[ -n "$expected_hash" && "$expected_hash" != "$saved_pass" ]]; then
		echo "ERROR: password preflight failed for $saved_user in $AUTH_DB" >&2
		echo "       This usually means the smoke script is pointed at the wrong DB or the wrong credentials." >&2
		exit 1
	fi
	if [[ -z "$expected_hash" ]]; then
		echo "WARNING: node is unavailable; skipping local password-hash preflight" >&2
	fi

	if [[ "$online" != "0" ]]; then
		if [[ "$saved_user" == "AndroidMap" ]]; then
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

run_authenticated_login_smoke() {
	preflight_auth_login_fixture

	"$ADB" shell am force-stop com.voidscape.client || true
	"$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
	"$ADB" logcat -c || true

	if [[ "$AUTH_HOST" == "10.0.2.2" && "$AUTH_PORT" == "43596" ]]; then
		launch_to_login_home
	else
		launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
	fi
	wait_for_selected_server "$AUTH_HOST" "$AUTH_PORT" 30 || exit 1
	sleep 2
	screenshot 00-auth-login-home

	tap_pct 50 65
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

	"$ADB" shell am force-stop com.voidscape.client || true
	wait_auth_offline 45 || true
	echo "Android auth/login smoke passed for $AUTH_USER on $AUTH_HOST:$AUTH_PORT"
}

run_authenticated_lifecycle_smoke() {
	preflight_auth_login_fixture
	wait_auth_offline 45 || true

	"$ADB" shell am force-stop com.voidscape.client || true
	"$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
	"$ADB" logcat -c || true

	if [[ "$AUTH_HOST" == "10.0.2.2" && "$AUTH_PORT" == "43596" ]]; then
		launch_to_login_home
	else
		launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
	fi
	wait_for_selected_server "$AUTH_HOST" "$AUTH_PORT" 30 || exit 1
	screenshot 00-lifecycle-login-home

	tap_pct 50 65
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

	tap_pct 50 72
	sleep 1
	tap_pct 50 72
	sleep 2
	assert_game_activity_for_input "lifecycle game HUD" "03-lifecycle-lost-before-hud" || exit 1
	screenshot 03-lifecycle-game-hud

	"$ADB" shell input keyevent HOME
	sleep 2
	"$ADB" shell am start -n com.voidscape.client/com.openrsc.android.updater.ApplicationUpdater >/dev/null
	wait_for_resumed_activity "GameActivity" 20 || {
		assert_resumed_activity "GameActivity" || true
		screenshot 04-lifecycle-resume-failed || true
		exit 1
	}
	sleep 2
	assert_no_android_runtime_crash "after launcher resume" || {
		screenshot 04-lifecycle-resume-crash || true
		exit 1
	}
	screenshot 04-lifecycle-after-resume

	"$ADB" shell am start -n com.voidscape.client/com.openrsc.android.updater.ApplicationUpdater >/dev/null
	"$ADB" shell am start -n com.voidscape.client/com.openrsc.android.updater.ApplicationUpdater >/dev/null
	wait_for_resumed_activity "GameActivity" 20 || {
		assert_resumed_activity "GameActivity" || true
		screenshot 05-lifecycle-relaunch-failed || true
		exit 1
	}
	sleep 3
	assert_no_android_runtime_crash "after duplicate launcher relaunch" || {
		screenshot 05-lifecycle-relaunch-crash || true
		exit 1
	}
	screenshot 05-lifecycle-after-duplicate-relaunch

	enable_android_smoke_settings
	tap_client_xy 330 19
	sleep 2
	screenshot 06-lifecycle-settings-open
	tap_client_xy 385 293
	sleep 10
	disable_android_smoke_settings
	assert_no_android_runtime_crash "after lifecycle logout" || {
		screenshot 07-lifecycle-logout-crash || true
		exit 1
	}
	wait_auth_offline 45 || exit 1
	screenshot 07-lifecycle-after-logout-login-home
	tap_pct 50 65
	sleep 3
	assert_soft_keyboard_visible
	screenshot 08-lifecycle-after-logout-keyboard

	"$ADB" shell input keyevent BACK || true
	"$ADB" shell am force-stop com.voidscape.client || true
	wait_auth_offline 45 || true
	echo "Android auth/lifecycle smoke passed for $AUTH_USER on $AUTH_HOST:$AUTH_PORT"
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
        done < <("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_ROTATE" | tail -20 || true)
        sleep 1
    done

    echo "ERROR: timed out waiting for Android camera rotate" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CAMERA_ROTATE" | tail -30 >&2 || true
    return 1
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

    echo "ERROR: timed out waiting for Android zoom gesture" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_ZOOM" | tail -30 >&2 || true
    return 1
}

wait_for_chat_tab() {
    local expected="$1"
    local timeout="${2:-15}"
    local deadline=$((SECONDS + timeout))
    local line

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CHAT_TAB " | tail -1 || true)"
        if [[ "$line" =~ after=([^[:space:]]+) ]] && [[ "${BASH_REMATCH[1]}" == "$expected" ]]; then
            echo "Verified Android chat tab: $line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android chat tab $expected" >&2
    "$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_CHAT_TAB" | tail -30 >&2 || true
    return 1
}

chat_tab_client_x() {
    local tab="$1"
    case "$tab" in
        ALL) echo "$AUTH_CHAT_TAB_ALL_X" ;;
        CHAT) echo "$AUTH_CHAT_TAB_CHAT_X" ;;
        QUEST) echo "$AUTH_CHAT_TAB_QUEST_X" ;;
        PRIVATE) echo "$AUTH_CHAT_TAB_PRIVATE_X" ;;
        CLAN) echo "$AUTH_CHAT_TAB_CLAN_X" ;;
        *)
            echo "ERROR: unknown Android chat tab '$tab'" >&2
            return 1
            ;;
    esac
}

tap_chat_tab() {
    local tab="$1"
    local client_x
    client_x="$(chat_tab_client_x "$tab")" || return 1
    echo "Android chat tab $tab at client $client_x,$AUTH_CHAT_TAB_Y"
    tap_client_xy "$client_x" "$AUTH_CHAT_TAB_Y"
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

extract_log_value() {
    local line="$1"
    local key="$2"
    printf "%s\n" "$line" | tr ' ' '\n' | sed -n "s/^${key}=//p" | tail -1
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
    local deadline=$((SECONDS + timeout))
    local line items

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SHOP_OPEN " | tail -1 || true)"
        items="$(extract_log_value "$line" items)"
        if [[ "$items" =~ ^[0-9]+$ ]] && (( items > 0 )); then
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
        if [[ "$query" == "$expected_token" ]]; then
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
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_BANK_ACTION action=$expected " | tail -1 || true)"
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
    tap_client_xy 394 19
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

wait_for_settings_state() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local timeout="${3:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible setting_tab camera_auto mouse_one

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SETTINGS " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        setting_tab="$(extract_log_value "$line" settingTab)"
        camera_auto="$(extract_log_value "$line" cameraAuto)"
        mouse_one="$(extract_log_value "$line" mouseOne)"
        if [[ "$visible" == "true" && "$setting_tab" == "1" \
            && "$camera_auto" == "$expected_camera" && "$mouse_one" == "$expected_mouse" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for Android settings cameraAuto=$expected_camera mouseOne=$expected_mouse" >&2
    settings_log_tail
    return 1
}

wait_for_settings_rendered() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local timeout="${3:-20}"
    local deadline=$((SECONDS + timeout))
    local line visible event setting_tab camera_auto mouse_one

    while (( SECONDS < deadline )); do
        line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_SETTINGS " | tail -12 | grep "event=STATE " | tail -1 || true)"
        visible="$(extract_log_value "$line" visible)"
        event="$(extract_log_value "$line" event)"
        setting_tab="$(extract_log_value "$line" settingTab)"
        camera_auto="$(extract_log_value "$line" cameraAuto)"
        mouse_one="$(extract_log_value "$line" mouseOne)"
        if [[ "$event" == "STATE" && "$visible" == "true" && "$setting_tab" == "1" \
            && "$camera_auto" == "$expected_camera" && "$mouse_one" == "$expected_mouse" ]]; then
            echo "$line"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: timed out waiting for rendered Android settings cameraAuto=$expected_camera mouseOne=$expected_mouse" >&2
    settings_log_tail
    return 1
}

tap_settings_logout() {
    local expected_camera="$1"
    local expected_mouse="$2"
    local timeout="${3:-20}"
    local line logout_x logout_y

    line="$(wait_for_settings_rendered "$expected_camera" "$expected_mouse" "$timeout")" || return 1
    logout_x="$(log_int_or_default "$line" logoutX 385)"
    logout_y="$(log_int_or_default "$line" logoutY 293)"
    tap_client_xy "$logout_x" "$logout_y"
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
	if grep -Eq "mInputShown=true|mIsInputViewShown=true|inputShown=true" <<< "$state"; then
		return 0
	fi

	echo "ERROR: expected Android soft keyboard to be visible" >&2
	grep -E "mInputShown|mIsInputViewShown|inputShown" <<< "$state" >&2 || true
	return 1
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
    "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
    launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
    screenshot 28-auth-login-home
    tap_pct 50 65
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
	tap_pct 60 6
	sleep 2
	screenshot 35-auth-settings-open
	tap_pct 67 85
	sleep 10
    screenshot 36-auth-after-logout-login-home
    tap_pct 50 65
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

    wait_auth_offline 45
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android NPC smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_npc_targets
        tap_pct 50 65
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android object smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_OBJECT_PLAYER_X,$AUTH_OBJECT_PLAYER_Y"
    update_auth_position "$AUTH_OBJECT_PLAYER_X" "$AUTH_OBJECT_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_object_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_object_targets
        tap_pct 50 65
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_object_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        enable_android_smoke_object_targets
        tap_pct 50 65
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        enable_android_smoke_npc_targets
        tap_pct 50 65
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android context menu smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_npc_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
    local original_position original_x original_y original_online status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    echo "Android edge context menu smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_npc_targets
        tap_pct 50 65
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
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_camera_rotate_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_camera
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_camera
        enable_android_smoke_npc_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 56-auth-before-camera-rotate
        "$ADB" logcat -c || true
        swipe_client_xy "$AUTH_CAMERA_SWIPE_START_X" "$AUTH_CAMERA_SWIPE_Y" "$AUTH_CAMERA_SWIPE_END_X" "$AUTH_CAMERA_SWIPE_Y" "$AUTH_CAMERA_SWIPE_DURATION_MS"
        wait_for_camera_rotate 20 || exit 1
        sleep 2
        screenshot 57-auth-after-camera-rotate
    ) || status=$?

    disable_android_smoke_camera
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_zoom
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_zoom
        enable_android_smoke_npc_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        wait_for_zoom_state 20 >/dev/null || exit 1
        screenshot 58-auth-before-zoom
        "$ADB" logcat -c || true
        swipe_client_xy "$AUTH_ZOOM_SWIPE_X" "$AUTH_ZOOM_SWIPE_START_Y" "$AUTH_ZOOM_SWIPE_X" "$AUTH_ZOOM_SWIPE_END_Y" "$AUTH_ZOOM_SWIPE_DURATION_MS"
        wait_for_zoom_change 20 || exit 1
        sleep 2
        screenshot 59-auth-after-zoom
    ) || status=$?

    disable_android_smoke_zoom
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
    local original_position="" original_x="" original_y="" original_online="" status
    if [[ -n "$AUTH_DB" ]]; then
        original_position="$(read_auth_position)"
        read -r original_x original_y original_online <<< "$original_position"
        echo "Android chat tab smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
        update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"
    fi

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_chat_tabs
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_chat_tabs
        enable_android_smoke_npc_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 60-auth-before-chat-tabs

        local raw_tabs tab screenshot_index
        IFS=',' read -ra raw_tabs <<< "$AUTH_CHAT_TAB_SEQUENCE"
        screenshot_index=61
        for tab in "${raw_tabs[@]}"; do
            tab="${tab//[[:space:]]/}"
            tab="$(printf "%s" "$tab" | tr '[:lower:]' '[:upper:]')"
            [[ -n "$tab" ]] || continue
            "$ADB" logcat -c || true
            tap_chat_tab "$tab"
            wait_for_chat_tab "$tab" 15 || exit 1
            sleep 1
            screenshot "$(printf '%02d-auth-chat-tab-%s' "$screenshot_index" "$(echo "$tab" | tr '[:upper:]' '[:lower:]')")"
            screenshot_index=$((screenshot_index + 1))
        done
    ) || status=$?

    disable_android_smoke_chat_tabs
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    if [[ -n "$AUTH_DB" && -n "$original_x" && -n "$original_y" ]]; then
        update_auth_position "$original_x" "$original_y" || true
    fi
    sleep 1
    return "$status"
}

run_authenticated_chat_send_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    wait_auth_offline 45
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
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_chat_send
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_chat_send
        enable_android_smoke_npc_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        wait_for_npc_target "$AUTH_NPC_ID" 30 >/dev/null || exit 1
        screenshot 65-auth-before-chat-send
        tap_client_xy "$AUTH_CHAT_KEYBOARD_X" "$AUTH_CHAT_KEYBOARD_Y"
        sleep 2
        assert_soft_keyboard_visible || exit 1
        screenshot 66-auth-chat-keyboard-open
        tap_client_xy "$AUTH_CHAT_ENTRY_X" "$AUTH_CHAT_ENTRY_Y"
        sleep 1
        "$ADB" logcat -c || true
        input_text "$AUTH_CHAT_MESSAGE"
        "$ADB" shell input keyevent ENTER
        wait_for_chat_send "$AUTH_CHAT_MESSAGE" 20 || exit 1
        sleep 2
        screenshot 67-auth-after-chat-send
    ) || status=$?

    disable_android_smoke_chat_send
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    if [[ -n "$AUTH_DB" && -n "$original_x" && -n "$original_y" ]]; then
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

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_bank
        enable_android_smoke_object_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_bank
        enable_android_smoke_object_targets
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        wait_for_object_target "$AUTH_BANK_OBJECT_ID" 30 >/dev/null || exit 1
        screenshot 68-auth-before-bank-open
        tap_object_target "$AUTH_BANK_OBJECT_ID" || exit 1
        wait_for_object_action "$AUTH_BANK_OBJECT_ID" "$AUTH_BANK_OBJECT_ACTION" 20 || exit 1

        local bank_line bank_slot_x bank_slot_y inventory_slot_x inventory_slot_y
        local search_x search_y search_clear_x search_clear_y deposit_all_x deposit_all_y
        local loadouts_x loadouts_y loadout_save_x loadout_save_y loadout_load_x loadout_load_y
        local confirm_save_x confirm_save_y panel_line modal_line

        bank_line="$(wait_for_bank_open 30)" || exit 1
        bank_slot_x="$(log_int_or_default "$bank_line" bankSlotX 30)"
        bank_slot_y="$(log_int_or_default "$bank_line" bankSlotY 72)"
        inventory_slot_x="$(log_int_or_default "$bank_line" inventorySlotX 30)"
        inventory_slot_y="$(log_int_or_default "$bank_line" inventorySlotY 239)"
        search_x="$(log_int_or_default "$bank_line" searchX 439)"
        search_y="$(log_int_or_default "$bank_line" searchY 36)"
        search_clear_x="$(log_int_or_default "$bank_line" searchClearX 487)"
        search_clear_y="$(log_int_or_default "$bank_line" searchClearY 36)"
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

        tap_client_xy "$search_x" "$search_y"
        sleep 1
        "$ADB" logcat -c || true
        input_text "$AUTH_BANK_SEARCH_TEXT"
        wait_for_bank_search "$AUTH_BANK_SEARCH_TEXT" 20 >/dev/null || exit 1
        sleep 1
        screenshot 70-auth-bank-search

        "$ADB" logcat -c || true
        tap_client_xy "$search_clear_x" "$search_clear_y"
        wait_for_bank_search "" 20 >/dev/null || exit 1
        sleep 1

        "$ADB" logcat -c || true
        swipe_client_xy "$AUTH_BANK_SCROLL_START_X" "$AUTH_BANK_SCROLL_START_Y" "$AUTH_BANK_SCROLL_START_X" "$AUTH_BANK_SCROLL_END_Y" "$AUTH_BANK_SCROLL_DURATION_MS"
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
        loadout_save_x="$(log_int_or_default "$panel_line" save0X "$loadout_save_x")"
        loadout_save_y="$(log_int_or_default "$panel_line" save0Y "$loadout_save_y")"
        loadout_load_x="$(log_int_or_default "$panel_line" load0X "$loadout_load_x")"
        loadout_load_y="$(log_int_or_default "$panel_line" load0Y "$loadout_load_y")"
        sleep 1
        screenshot 73-auth-bank-loadouts

        "$ADB" logcat -c || true
        tap_client_xy "$loadout_save_x" "$loadout_save_y"
        modal_line="$(wait_for_bank_modal SAVE_CONFIRM 0 15)" || exit 1
        confirm_save_x="$(log_int_or_default "$modal_line" saveX "$confirm_save_x")"
        confirm_save_y="$(log_int_or_default "$modal_line" saveY "$confirm_save_y")"
        sleep 1
        screenshot 74-auth-bank-save-modal

        "$ADB" logcat -c || true
        tap_client_xy "$confirm_save_x" "$confirm_save_y"
        wait_for_bank_action SAVE_PRESET 20 || exit 1
        sleep 3
        screenshot 75-auth-bank-save-loadout

        "$ADB" logcat -c || true
        tap_client_xy "$inventory_slot_x" "$inventory_slot_y"
        wait_for_bank_action DEPOSIT 20 || exit 1
        sleep 2
        screenshot 76-auth-bank-deposit

        "$ADB" logcat -c || true
        tap_client_xy "$bank_slot_x" "$bank_slot_y"
        wait_for_bank_action WITHDRAW 20 || exit 1
        sleep 1
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

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || exit 1
        wait_for_bank_preset_saved "$player_id" 0 45 || exit 1
    ) || status=$?

    disable_android_smoke_bank
    disable_android_smoke_object_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
    local original_position original_x original_y original_online player_id status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
    snapshot_auth_inventory "$player_id"
    echo "Android shop smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_SHOP_PLAYER_X,$AUTH_SHOP_PLAYER_Y"
    update_auth_position "$AUTH_SHOP_PLAYER_X" "$AUTH_SHOP_PLAYER_Y"
    echo "Android shop smoke seeding coins in inventory slot 0 x$AUTH_SHOP_COIN_AMOUNT"
    seed_auth_inventory_slot "$player_id" 0 10 "$AUTH_SHOP_COIN_AMOUNT"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_shop
        enable_android_smoke_npc_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_shop
        enable_android_smoke_npc_targets
        sleep 3
        tap_pct 50 65
        sleep 1
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        wait_for_npc_target "$AUTH_SHOP_NPC_ID" 30 >/dev/null || exit 1
        screenshot 79-auth-before-shop-open
        long_press_npc_target "$AUTH_SHOP_NPC_ID" 1200 || exit 1

        local menu_values menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y menu_index
        menu_values="$(wait_for_context_menu 15)" || exit 1
        read -r menu_x menu_y menu_width menu_height menu_items first_action menu_mouse_x menu_mouse_y <<< "$menu_values"
        menu_index="$(wait_for_context_menu_action_index "$AUTH_SHOP_NPC_ACTION" 10)" || exit 1
        tap_context_menu_item "$menu_x" "$menu_y" "$menu_width" "$menu_height" "$menu_items" "$menu_index" || exit 1
        wait_for_context_menu_action "$AUTH_SHOP_NPC_ACTION" 20 || exit 1
        wait_for_npc_action "$AUTH_SHOP_NPC_ID" "$AUTH_SHOP_NPC_ACTION" 20 || exit 1

        local shop_line shop_x shop_y slot_x slot_y buy_x buy_y sell_x sell_y
        shop_line="$(wait_for_shop_open 30)" || exit 1
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

        "$ADB" logcat -c || true
        tap_client_xy "$buy_x" "$buy_y"
        wait_for_shop_action BUY 20 || exit 1
        wait_for_shop_selected_owned "$AUTH_SHOP_BUY_SLOT" 25 || exit 1
        sleep 1
        screenshot 82-auth-shop-buy

        "$ADB" logcat -c || true
        tap_client_xy "$sell_x" "$sell_y"
        wait_for_shop_action SELL 20 || exit 1
        sleep 2
        screenshot 83-auth-shop-sell

        "$ADB" logcat -c || true
        swipe_client_xy 256 170 256 80 500
        shop_line="$(wait_for_shop_open 15)" || exit 1
        sleep 1
        screenshot 84-auth-shop-no-scroll

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || exit 1
    ) || status=$?

    disable_android_smoke_shop
    disable_android_smoke_npc_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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

    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_inventory_targets
        enable_android_smoke_equipment
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_inventory_targets
        enable_android_smoke_equipment
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2
        tap_inventory_tab
        wait_for_inventory_target "$AUTH_EQUIPMENT_INVENTORY_SLOT" 30 >/dev/null || exit 1
        screenshot 85-auth-before-equipment-equip

        "$ADB" logcat -c || true
        tap_inventory_slot "$AUTH_EQUIPMENT_INVENTORY_SLOT" || exit 1
        wait_for_inventory_action "$AUTH_EQUIPMENT_INVENTORY_SLOT" "ITEM_EQUIP_FROM_INVENTORY" 20 || exit 1
        sleep 2

        local equipment_line equipment_tab_x equipment_tab_y equipped_line equipped_slot_x equipped_slot_y
        equipment_line="$("$ADB" logcat -d -v raw 2>/dev/null | tr -d '\r' | grep "ANDROID_SMOKE_EQUIPMENT_TAB " | tail -1 || true)"
        if [[ "$(extract_log_value "$equipment_line" tab)" != "0" ]]; then
            equipment_line=""
        fi
        if [[ -n "$equipment_line" ]]; then
            equipment_tab_x="$(log_int_or_default "$equipment_line" equipmentTabX 325)"
            equipment_tab_y="$(log_int_or_default "$equipment_line" equipmentTabY 280)"
            "$ADB" logcat -c || true
            tap_client_xy "$equipment_tab_x" "$equipment_tab_y"
            wait_for_equipment_tab 1 20 >/dev/null || exit 1
            equipped_line="$(wait_for_equipped_item "$AUTH_EQUIPMENT_ITEM_ID" 20)" || exit 1
            equipped_slot_x="$(log_int_or_default "$equipped_line" clientX 386)"
            equipped_slot_y="$(log_int_or_default "$equipped_line" clientY 120)"
            sleep 1
            screenshot 86-auth-equipment-tab-open

            "$ADB" logcat -c || true
            tap_client_xy "$equipped_slot_x" "$equipped_slot_y"
            wait_for_equipment_action UNEQUIP_FROM_EQUIPMENT 20 || exit 1
            wait_for_equipment_count 0 20 || exit 1
            sleep 2
            screenshot 87-auth-after-equipment-unequip
        else
            wait_for_inventory_equipped_state "$AUTH_EQUIPMENT_INVENTORY_SLOT" true 20 || exit 1
            sleep 1
            screenshot 86-auth-inventory-equipped

            "$ADB" logcat -c || true
            tap_inventory_slot "$AUTH_EQUIPMENT_INVENTORY_SLOT" || exit 1
            wait_for_inventory_action "$AUTH_EQUIPMENT_INVENTORY_SLOT" "ITEM_UNEQUIP_FROM_INVENTORY" 20 || exit 1
            wait_for_inventory_equipped_state "$AUTH_EQUIPMENT_INVENTORY_SLOT" false 20 || exit 1
            sleep 2
            screenshot 87-auth-after-inventory-unequip
        fi

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || exit 1
    ) || status=$?

    disable_android_smoke_inventory_targets
    disable_android_smoke_equipment
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    restore_auth_inventory "$player_id" || true
    restore_auth_equipment "$player_id" || true
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

    wait_auth_offline 45
    local original_position original_x original_y original_online player_id status
    original_position="$(read_auth_position)"
    read -r original_x original_y original_online <<< "$original_position"
    player_id="$(read_auth_player_id)"
    snapshot_auth_stats "$player_id"
    ensure_auth_magic_prayer_stats "$player_id"
    echo "Android magic/prayer smoke moving $AUTH_USER from $original_x,$original_y to $AUTH_NPC_PLAYER_X,$AUTH_NPC_PLAYER_Y"
    update_auth_position "$AUTH_NPC_PLAYER_X" "$AUTH_NPC_PLAYER_Y"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_magic_prayer
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_magic_prayer
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
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

        cast_selected_spell_on_self "$AUTH_MAGIC_PRAYER_SPELL_ID" "$self_x" "$self_y" || exit 1
        sleep 8
        screenshot 90-auth-after-self-cast

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || exit 1
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_magic_prayer
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_magic_prayer
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
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

        tap_magic_prayer_row_until_action "$row_x" "$row_y" PRAYER_ACTIVATED "$AUTH_MAGIC_PRAYER_PRAYER_ID" || exit 1
        sleep 1
        screenshot 92-auth-prayer-activated

        tap_magic_prayer_row_until_action "$row_x" "$row_y" PRAYER_DEACTIVATED "$AUTH_MAGIC_PRAYER_PRAYER_ID" || exit 1
        sleep 1
        screenshot 93-auth-prayer-deactivated

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || exit 1
    ) || status=$?

    disable_android_smoke_magic_prayer
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    restore_auth_stats "$player_id" || true
    update_auth_position "$original_x" "$original_y" || true
    sleep 1
    return "$status"
}

run_authenticated_world_map_smoke() {
    if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
        return
    fi

    local status
    if [[ -n "$AUTH_DB" ]]; then
        wait_auth_offline 45
        clear_auth_tutorial_appearance
    fi

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_world_map
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_world_map
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2

        local map_line before_zoom after_zoom before_pan_x before_pan_y
        map_line="$(open_world_map_from_hud)" || exit 1
        wait_for_world_map_rendered 8 >/dev/null 2>&1 || sleep 1
        sleep 1
        screenshot 94-auth-world-map-open

        before_zoom="$(log_int_or_default "$map_line" zoom 0)"
        after_zoom=$((before_zoom + 1))
        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 70
        wait_for_world_map_zoom "$after_zoom" 20 || exit 1
        wait_for_world_map_rendered 8 >/dev/null 2>&1 || sleep 1
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

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 47
        wait_for_world_map_search_focus true 20 >/dev/null || exit 1
        input_text "$AUTH_WORLD_MAP_SEARCH_TEXT"
        "$ADB" shell input keyevent ENTER
        wait_for_world_map_search "$AUTH_WORLD_MAP_SEARCH_TEXT" 25 || exit 1
        wait_for_world_map_rendered 8 >/dev/null 2>&1 || sleep 1
        sleep 1
        screenshot 97-auth-world-map-search

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent BACK
        wait_for_world_map_closed 20 || exit 1
        sleep 1
        screenshot 98-auth-world-map-closed

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || true
    ) || status=$?

    disable_android_smoke_world_map
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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
    wait_auth_offline 45
    clear_auth_tutorial_appearance
    original_settings="$(read_auth_settings)" || return 1
    read -r original_camera original_mouse original_sound <<< "$original_settings"
    update_auth_settings 1 0 "$original_sound"

    status=0
    (
        set -e
        "$ADB" logcat -c || true
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_settings
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_settings
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 43
        wait_for_settings_state true false 30 >/dev/null || exit 1
        wait_for_settings_rendered true false 30 >/dev/null || exit 1
        sleep 1
        screenshot 99-auth-settings-open

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 8
        sleep 1
        "$ADB" shell input keyevent 9
        wait_for_settings_state false true 30 >/dev/null || exit 1
        wait_for_settings_rendered false true 30 >/dev/null || exit 1
        sleep 1
        screenshot 100-auth-settings-changed

        tap_settings_logout false true 20 || exit 1
        sleep 10
        wait_auth_offline 45 || exit 1
        wait_for_auth_settings_row 0 1 "$original_sound" 45 || exit 1

        "$ADB" logcat -c || true
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT"
        enable_android_smoke_settings
        tap_pct 50 65
        sleep 3
        enter_auth_credentials
        submit_login_and_wait || exit 1
        sleep 8
        tap_pct 50 72
        sleep 2

        "$ADB" logcat -c || true
        "$ADB" shell input keyevent 43
        wait_for_settings_state false true 30 >/dev/null || exit 1
        wait_for_settings_rendered false true 30 >/dev/null || exit 1
        sleep 1
        screenshot 101-auth-settings-reloaded

        tap_settings_logout false true 20 || exit 1
        sleep 10
        wait_auth_offline 45 || true
    ) || status=$?

    disable_android_smoke_settings
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    update_auth_settings "$original_camera" "$original_mouse" "$original_sound" || true
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
    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_ground_loot
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT" || exit 1
        assert_game_activity_for_input "ground-loot login launch" "ground-loot-lost-after-launch" || exit 1
        screenshot diagnostic-ground-loot-login-home
        enable_android_smoke_ground_loot
        assert_game_activity_for_input "ground-loot existing-user tap" "ground-loot-lost-before-existing-user" || exit 1
        tap_pct 50 65
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

        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || true
    ) || status=$?

    disable_android_smoke_ground_loot
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
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
    wait_auth_offline 45
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
        "$ADB" shell "run-as com.voidscape.client rm -f $APP_FILES/credentials.txt" 2>/dev/null || true
        enable_android_smoke_player_targets
        launch_game_with_endpoint "$AUTH_HOST" "$AUTH_PORT" || exit 1
        assert_game_activity_for_input "wilderness target login launch" "wilderness-target-lost-after-launch" || exit 1
        screenshot 103-auth-wilderness-login-home
        enable_android_smoke_player_targets
        tap_pct 50 65
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
        "$ADB" shell am force-stop com.voidscape.client || true
        wait_auth_offline 45 || true
    ) || status=$?

    if [[ "$status" -ne 0 && -f "$OUT_DIR/auth-wilderness-command-sent.flag" ]]; then
        "$ADB" shell input keyevent 31 >/dev/null 2>&1 || true
        sleep 2
    fi
    disable_android_smoke_player_targets
    "$ADB" shell am force-stop com.voidscape.client || true
    wait_auth_offline 45 || true
    update_auth_position "$original_x" "$original_y" || true
    update_auth_group "$original_group" || true
    sleep 1
    return "$status"
}

launch_wrapper() {
    "$ADB" shell am force-stop com.voidscape.client
    "$ADB" shell am start -n com.voidscape.client/com.openrsc.android.updater.ApplicationUpdater >/dev/null
}

launch_to_login_home() {
	launch_wrapper
	wait_for_wrapper_ready
	tap_pct 50 60
	ensure_game_activity_from_wrapper
	dismiss_fullscreen_education
}

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
    echo "Android chat tab smoke screenshots written to $OUT_DIR"
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

launch_wrapper
screenshot 00-bootstrap
wait_for_wrapper_ready
screenshot 01-ready-play

long_press_pct 50 60 1200
wait_for_text "Server Options" 10 || sleep 1
screenshot 02-server-picker
"$ADB" shell input keyevent BACK
sleep 1
screenshot 03-server-picker-back-ready
long_press_pct 50 60 1200
wait_for_text "Server Options" 10 || sleep 1
screenshot 04-server-picker-reopened
tap_pct 33 60
wait_for_text "Manual Server" 10 || sleep 1
screenshot 05-manual-server

launch_to_login_home
screenshot 06-login-home
tap_pct 50 65
sleep 3
screenshot 07-existing-user-keyboard
"$ADB" shell input text testuser
"$ADB" shell input keyevent BACK
sleep 1
tap_pct 44 50
sleep 1
screenshot 08-login-missing-password-error

launch_to_login_home
screenshot 09-login-home-after-error
tap_pct 50 65
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
tap_pct 50 65
sleep 3
screenshot 15-saved-credentials-loaded
"$ADB" shell input keyevent BACK
sleep 1
tap_pct 50 59
sleep 1
screenshot 16-recover-account-handoff

"$ADB" shell input keyevent BACK
sleep 1
screenshot 17-existing-user-back-home
tap_pct 50 52
sleep 1
screenshot 18-create-account-handoff

"$ADB" shell input keyevent BACK
sleep 1
launch_to_login_home
"$ADB" shell input keyevent HOME
sleep 2
screenshot 19-background-home
"$ADB" shell am start -n com.voidscape.client/com.openrsc.android.updater.ApplicationUpdater >/dev/null
sleep 3
ensure_game_activity_from_wrapper
screenshot 20-resume-login-home
tap_pct 50 65
sleep 3
screenshot 21-resume-existing-user-keyboard
"$ADB" shell input text resumeuser
sleep 1
screenshot 22-resume-existing-user-typed
"$ADB" shell input keyevent BACK
sleep 1

run_authenticated_logout_smoke
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
run_authenticated_ground_loot_smoke
run_authenticated_wilderness_target_smoke

launch_wrapper
wait_for_wrapper_ready
screenshot 110-bad-server-ready-play
long_press_pct 50 60 1200
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
tap_text "Play" last || tap_pct 74 60
sleep 45
screenshot 114-bad-server-loading-error

write_server_endpoint 5.161.114.251 43596 || true
"$ADB" shell am force-stop com.voidscape.client || true

echo "Android smoke screenshots written to $OUT_DIR"
