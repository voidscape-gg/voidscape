#!/usr/bin/env bash
# run-server.sh — start the voidscape game server
#
# With no arguments, uses server/local.conf. An explicit named config requires
# both --config NAME and --explicit-config so local.conf cannot shadow it.
# See docs/SERVER-PRESETS.md for choosing a base preset.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/server"

usage() {
	cat <<'EOF'
Usage:
    scripts/run-server.sh
    scripts/run-server.sh --config NAME --explicit-config

NAME must be a config basename such as headless-demo or headless-demo.conf.
Explicit mode loads exactly server/NAME.conf and fails if it is unavailable.
EOF
}

CONFIG_NAME=""
EXPLICIT_CONFIG=false

while [[ $# -gt 0 ]]; do
	case "$1" in
		--config)
			if [[ $# -lt 2 || -z "$2" ]]; then
				echo "ERROR: --config requires a config basename." >&2
				usage >&2
				exit 2
			fi
			CONFIG_NAME="$2"
			shift 2
			;;
		--explicit-config)
			EXPLICIT_CONFIG=true
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "ERROR: unknown argument: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

if [[ -z "$CONFIG_NAME" && "$EXPLICIT_CONFIG" == false ]]; then
	if [[ ! -f local.conf ]]; then
		cat >&2 <<'EOF'
ERROR: server/local.conf not found.

Voidscape's working preset lives in server/local.conf (gitignored). Bootstrap it:

    cp server/preservation.conf server/local.conf
    # then apply voidscape QoL overrides per docs/SERVER-PRESETS.md

EOF
		exit 1
	fi

	echo "==> Starting voidscape server (local.conf, ZGC)"
	ant runserverzgc -DconfFile=local
	exit 0
fi

if [[ -z "$CONFIG_NAME" || "$EXPLICIT_CONFIG" != true ]]; then
	echo "ERROR: named configs require both --config NAME and --explicit-config." >&2
	usage >&2
	exit 2
fi

if [[ ! "$CONFIG_NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
	echo "ERROR: config NAME must be a safe basename (letters, digits, dot, underscore, or hyphen)." >&2
	exit 2
fi

CONFIG_NAME="${CONFIG_NAME%.conf}"
if [[ -z "$CONFIG_NAME" || "$CONFIG_NAME" == "." || "$CONFIG_NAME" == ".." ]]; then
	echo "ERROR: config NAME must identify a named .conf file." >&2
	exit 2
fi

CONFIG_FILE="$CONFIG_NAME.conf"
if [[ ! -f "$CONFIG_FILE" ]]; then
	echo "ERROR: explicit server config not found: server/$CONFIG_FILE" >&2
	exit 1
fi

echo "==> Starting voidscape server ($CONFIG_FILE, explicit-only, ZGC)"
ant runserverzgc -DconfFile="$CONFIG_NAME" -DexplicitConfig=true
