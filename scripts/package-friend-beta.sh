#!/usr/bin/env bash
# package-friend-beta.sh - build a launcher-only friend beta package.
#
# The output contains:
#   dist/friend-beta/VoidscapeLauncher.jar  -> give this to testers
#   dist/friend-beta/update/               -> upload/serve this static folder
#
# Example:
#   scripts/package-friend-beta.sh \
#     --host play.example.com \
#     --port 43596 \
#     --base-url https://play.example.com/voidscape/update \
#     --discord-url https://discord.gg/example \
#     --discord-application-id 123456789012345678

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

HOST=""
PORT="43596"
BASE_URL=""
WEBSITE_URL=""
PORTAL_URL=""
DISCORD_URL=""
DISCORD_APPLICATION_ID=""
DISCORD_LARGE_IMAGE_KEY="voidscape_logo"
DISCORD_LARGE_IMAGE_TEXT="Voidscape"
OUTPUT_DIR="$REPO_ROOT/dist/friend-beta"
SKIP_BUILD=0

usage() {
	cat <<'EOF'
Usage: scripts/package-friend-beta.sh --host <server-host> --base-url <update-base-url> [options]

Required:
  --host HOST              Game server hostname or IP written into launcher ip.txt.
  --base-url URL           Public URL that serves the generated update folder.

Options:
  --port PORT              Game server TCP port. Default: 43596.
  --website-url URL        Optional website button URL.
  --portal-url URL         Optional account button URL.
  --discord-url URL        Optional Discord button URL.
  --discord-application-id ID
                           Optional Discord application ID for Voidscape rich presence.
  --discord-large-image-key KEY
                           Optional Discord rich presence image asset key. Default: voidscape_logo.
  --discord-large-image-text TEXT
                           Optional Discord rich presence image hover text. Default: Voidscape.
  --output-dir DIR         Output directory. Default: dist/friend-beta.
  --skip-build             Reuse existing Client_Base/Open_RSC_Client.jar.
  -h, --help               Show this help.

The launcher embeds the server host, port, and manifest URL at build time, so
testers can double-click the jar without setting environment variables.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--host)
			HOST="${2:-}"
			shift 2
			;;
		--port)
			PORT="${2:-}"
			shift 2
			;;
		--base-url)
			BASE_URL="${2:-}"
			shift 2
			;;
		--website-url)
			WEBSITE_URL="${2:-}"
			shift 2
			;;
		--portal-url)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--discord-url)
			DISCORD_URL="${2:-}"
			shift 2
			;;
		--discord-application-id)
			DISCORD_APPLICATION_ID="${2:-}"
			shift 2
			;;
		--discord-large-image-key)
			DISCORD_LARGE_IMAGE_KEY="${2:-}"
			shift 2
			;;
		--discord-large-image-text)
			DISCORD_LARGE_IMAGE_TEXT="${2:-}"
			shift 2
			;;
		--output-dir)
			OUTPUT_DIR="${2:-}"
			shift 2
			;;
		--skip-build)
			SKIP_BUILD=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 1
			;;
	esac
done

if [[ -z "$HOST" ]]; then
	echo "ERROR: --host is required." >&2
	exit 1
fi

if [[ -z "$BASE_URL" ]]; then
	echo "ERROR: --base-url is required." >&2
	exit 1
fi

if ! [[ "$PORT" =~ ^[0-9]+$ ]] || (( PORT < 1 || PORT > 65535 )); then
	echo "ERROR: --port must be a number from 1 to 65535." >&2
	exit 1
fi

BASE_URL="${BASE_URL%/}"
OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
UPDATE_DIR="$OUTPUT_DIR/update"
MANIFEST_FILE="$UPDATE_DIR/manifest.properties"
CLIENT_JAR="$REPO_ROOT/Client_Base/Open_RSC_Client.jar"
CLIENT_CACHE="$REPO_ROOT/Client_Base/Cache"
LAUNCHER_RESOURCE="$REPO_ROOT/PC_Launcher/src/main/resources/voidscape-launcher.properties"
LAUNCHER_RESOURCE_BACKUP=""
LAUNCHER_RESOURCE_EXISTED=0

restore_launcher_resource() {
	if [[ "$LAUNCHER_RESOURCE_EXISTED" -eq 1 && -n "$LAUNCHER_RESOURCE_BACKUP" && -f "$LAUNCHER_RESOURCE_BACKUP" ]]; then
		cp "$LAUNCHER_RESOURCE_BACKUP" "$LAUNCHER_RESOURCE"
	elif [[ "$LAUNCHER_RESOURCE_EXISTED" -eq 0 ]]; then
		rm -f "$LAUNCHER_RESOURCE"
	fi
	if [[ -n "$LAUNCHER_RESOURCE_BACKUP" ]]; then
		rm -f "$LAUNCHER_RESOURCE_BACKUP"
	fi
}
trap restore_launcher_resource EXIT

sha256_file() {
	shasum -a 256 "$1" | awk '{print $1}'
}

file_size() {
	local size
	if size="$(stat -f%z "$1" 2>/dev/null)" && [[ "$size" =~ ^[0-9]+$ ]]; then
		printf '%s\n' "$size"
	else
		stat -c%s "$1"
	fi
}

client_version() {
	awk '/CLIENT_VERSION[[:space:]]*=/ {
		gsub(/[^0-9]/, "", $0);
		print $0;
		exit
	}' "$REPO_ROOT/Client_Base/src/orsc/Config.java"
}

is_runtime_file() {
	case "$(basename "$1")" in
		accounts.txt|credentials.txt|uid.dat|ip.txt|port.txt|hideIp.txt|config.txt|client.properties|discord_inuse.txt|launcherSettings.conf|voidscapeLauncher.properties)
			return 0
			;;
		*)
			return 1
			;;
	esac
}

property_line() {
	local key="$1"
	local value="$2"
	printf '%s=%s\n' "$key" "$value"
}

cd "$REPO_ROOT"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
	scripts/build.sh
fi

if [[ ! -f "$CLIENT_JAR" ]]; then
	echo "ERROR: missing $CLIENT_JAR. Run scripts/build.sh first." >&2
	exit 1
fi

if [[ ! -d "$CLIENT_CACHE" ]]; then
	echo "ERROR: missing $CLIENT_CACHE. Build or restore the client cache first." >&2
	exit 1
fi

rm -rf "$UPDATE_DIR"
mkdir -p "$UPDATE_DIR"

cp "$CLIENT_JAR" "$UPDATE_DIR/Open_RSC_Client.jar"

while IFS= read -r -d '' source; do
	relative="${source#"$CLIENT_CACHE"/}"
	if is_runtime_file "$relative"; then
		continue
	fi
	mkdir -p "$UPDATE_DIR/$(dirname "$relative")"
	cp "$source" "$UPDATE_DIR/$relative"
done < <(find "$CLIENT_CACHE" -type f -print0)

if [[ -f "$LAUNCHER_RESOURCE" ]]; then
	LAUNCHER_RESOURCE_EXISTED=1
	LAUNCHER_RESOURCE_BACKUP="$(mktemp)"
	cp "$LAUNCHER_RESOURCE" "$LAUNCHER_RESOURCE_BACKUP"
fi

{
	property_line "voidscape.serverHost" "$HOST"
	property_line "voidscape.serverPort" "$PORT"
	property_line "voidscape.manifestUrl" "$BASE_URL/manifest.properties"
	if [[ -n "$WEBSITE_URL" ]]; then
		property_line "voidscape.websiteUrl" "$WEBSITE_URL"
	fi
	if [[ -n "$PORTAL_URL" ]]; then
		property_line "voidscape.portalUrl" "$PORTAL_URL"
	fi
	if [[ -n "$DISCORD_URL" ]]; then
		property_line "voidscape.discordUrl" "$DISCORD_URL"
	fi
	if [[ -n "$DISCORD_APPLICATION_ID" ]]; then
		property_line "voidscape.discordApplicationId" "$DISCORD_APPLICATION_ID"
	fi
	if [[ -n "$DISCORD_LARGE_IMAGE_KEY" ]]; then
		property_line "voidscape.discordLargeImageKey" "$DISCORD_LARGE_IMAGE_KEY"
	fi
	if [[ -n "$DISCORD_LARGE_IMAGE_TEXT" ]]; then
		property_line "voidscape.discordLargeImageText" "$DISCORD_LARGE_IMAGE_TEXT"
	fi
} > "$LAUNCHER_RESOURCE"

(cd "$REPO_ROOT/PC_Launcher" && ant compile)
cp "$REPO_ROOT/PC_Launcher/OpenRSC.jar" "$OUTPUT_DIR/VoidscapeLauncher.jar"
mkdir -p "$UPDATE_DIR/launcher"
cp "$REPO_ROOT/PC_Launcher/OpenRSC.jar" "$UPDATE_DIR/launcher/VoidscapeLauncher.jar"

CLIENT_VERSION="$(client_version)"
if ! [[ "$CLIENT_VERSION" =~ ^[0-9]+$ ]]; then
	echo "ERROR: could not parse CLIENT_VERSION from Client_Base/src/orsc/Config.java." >&2
	exit 1
fi
RELEASE_STAMP="$(date -u +%Y%m%d%H%M%S)"
LAUNCHER_CHANNEL_JAR="$UPDATE_DIR/launcher/VoidscapeLauncher.jar"

{
	property_line "version" "$CLIENT_VERSION-$RELEASE_STAMP"
	property_line "clientVersion" "$CLIENT_VERSION"
	property_line "baseUrl" "$BASE_URL/"
	index=1
	while IFS= read -r file; do
		relative="${file#"$UPDATE_DIR"/}"
		if [[ "$relative" == "manifest.properties" ]]; then
			continue
		fi
		# The launcher jar ships under launcher.* only; v1 launchers must not
		# download it as a plain cache file.
		if [[ "$relative" == launcher/* ]]; then
			continue
		fi
		property_line "file.$index.path" "$relative"
		property_line "file.$index.sha256" "$(sha256_file "$file")"
		property_line "file.$index.size" "$(file_size "$file")"
		index=$((index + 1))
	done < <(find "$UPDATE_DIR" -type f | LC_ALL=C sort)
	property_line "launcher.sha256" "$(sha256_file "$LAUNCHER_CHANNEL_JAR")"
	property_line "launcher.url" "$BASE_URL/launcher/VoidscapeLauncher.jar"
	property_line "launcher.size" "$(file_size "$LAUNCHER_CHANNEL_JAR")"
	property_line "launcher.version" "$CLIENT_VERSION-$RELEASE_STAMP"
} > "$MANIFEST_FILE"

cp "$REPO_ROOT/docs/BETA-PLAYTEST-GUIDE.md" "$OUTPUT_DIR/BETA-PLAYTEST-GUIDE.md"

cat <<EOF
Friend beta package created:

  Launcher for testers:
    $OUTPUT_DIR/VoidscapeLauncher.jar

  Static update folder to upload/serve:
    $UPDATE_DIR

Launcher endpoint:
  $HOST:$PORT

Launcher manifest:
  $BASE_URL/manifest.properties

Launcher self-update entry (manifest launcher.sha256/url/size/version):
  $BASE_URL/launcher/VoidscapeLauncher.jar

After uploading the update folder, verify from another machine:
  curl -I "$BASE_URL/manifest.properties"
EOF
