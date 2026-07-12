#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_VERSION_OVERRIDE=""

usage() {
	cat <<'EOF'
Usage: scripts/build-web-teavm-spike.sh [options]

Options:
  --client-version VERSION  Temporarily compile the web client with this protocol version.
  -h, --help                Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--client-version)
			CLIENT_VERSION_OVERRIDE="${2:-}"
			shift 2
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

restore_config() {
	if [[ -n "${CONFIG_BACKUP:-}" && -f "$CONFIG_BACKUP" ]]; then
		cp "$CONFIG_BACKUP" "$CONFIG_FILE"
	fi
}

cd "$ROOT"
if [[ -n "$CLIENT_VERSION_OVERRIDE" ]]; then
	if [[ ! "$CLIENT_VERSION_OVERRIDE" =~ ^[0-9]+$ ]]; then
		echo "ERROR: --client-version must be numeric." >&2
		exit 1
	fi
	CONFIG_FILE="$ROOT/Client_Base/src/orsc/Config.java"
	CONFIG_BACKUP="$(mktemp)"
	cp "$CONFIG_FILE" "$CONFIG_BACKUP"
	trap restore_config EXIT
	perl -0pi -e "s/public static final int CLIENT_VERSION = \\d+;/public static final int CLIENT_VERSION = $CLIENT_VERSION_OVERRIDE;/" "$CONFIG_FILE"
	if ! grep -q "public static final int CLIENT_VERSION = $CLIENT_VERSION_OVERRIDE;" "$CONFIG_FILE"; then
		echo "ERROR: failed to apply CLIENT_VERSION override." >&2
		exit 1
	fi
	echo "Building TeaVM web client with CLIENT_VERSION=$CLIENT_VERSION_OVERRIDE"
fi

mvn -f Web_Client_TeaVM/pom.xml clean package
