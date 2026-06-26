#!/usr/bin/env bash
# List or remove generated iPhone web QA artifacts.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPLY=0
INCLUDE_CURRENT_PREFLIGHT=0
DROP_CURRENT_VIDEO=0
INCLUDE_PACKAGE=0
INCLUDE_TEAVM_TARGET=0
INCLUDE_PLAYWRIGHT_CACHE=0
INCLUDE_XCODE_DERIVED_DATA=0
XCODE_DERIVED_DATA="$HOME/Library/Developer/Xcode/DerivedData"

usage() {
	cat <<'EOF'
Usage: scripts/clean-web-teavm-iphone-artifacts.sh [options]

Options:
  --apply                    Actually delete listed candidates. Default is dry run.
  --include-current-preflight
                             Include tmp/web-teavm-iphone-release-preflight.
  --drop-current-video       Remove only simulator-session.* and simulator-video-checks.json
                             from the current preflight, preserving the rest.
  --include-package          Include dist/web-teavm.
  --include-teavm-target     Include Web_Client_TeaVM/target/teavm.
  --include-playwright-cache Include /tmp/voidscape-playwright-smoke. This makes
                             Playwright prerequisites fail until reinstalled.
  --include-xcode-derived-data
                             Include ~/Library/Developer/Xcode/DerivedData.
                             This is generated Xcode build cache and may make
                             Xcode rebuild slower next time.
  -h, --help                 Show this help.

The script only targets generated/ignored artifacts. It is meant to make enough
room for iPhone Simulator video preflight without guessing which files are safe.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--apply)
			APPLY=1
			shift
			;;
		--include-current-preflight)
			INCLUDE_CURRENT_PREFLIGHT=1
			shift
			;;
		--drop-current-video)
			DROP_CURRENT_VIDEO=1
			shift
			;;
		--include-package)
			INCLUDE_PACKAGE=1
			shift
			;;
		--include-teavm-target)
			INCLUDE_TEAVM_TARGET=1
			shift
			;;
		--include-playwright-cache)
			INCLUDE_PLAYWRIGHT_CACHE=1
			shift
			;;
		--include-xcode-derived-data)
			INCLUDE_XCODE_DERIVED_DATA=1
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

candidate_paths=()
candidate_reasons=()

has_candidate() {
	local path="$1"
	local existing
	if [[ "${#candidate_paths[@]}" -eq 0 ]]; then
		return 1
	fi
	for existing in "${candidate_paths[@]}"; do
		if [[ "$existing" == "$path" ]]; then
			return 0
		fi
	done
	return 1
}

add_candidate() {
	local path="$1"
	local reason="$2"
	if [[ ! -e "$path" ]]; then
		return 0
	fi
	if has_candidate "$path"; then
		return 0
	fi
	candidate_paths+=("$path")
	candidate_reasons+=("$reason")
}

add_glob() {
	local reason="$1"
	shift
	local path
	for path in "$@"; do
		add_candidate "$path" "$reason"
	done
}

add_glob "standalone Simulator runs" "$ROOT"/tmp/web-teavm-simulator "$ROOT"/tmp/web-teavm-simulator-*
add_glob "Chrome iPhone smoke artifacts" "$ROOT"/tmp/web-teavm-smoke "$ROOT"/tmp/web-teavm-smoke-*
add_glob "Chrome controls smoke artifacts" "$ROOT"/tmp/web-teavm-controls-smoke "$ROOT"/tmp/web-teavm-controls-smoke-*
add_glob "local HTTPS/WSS smoke artifacts" "$ROOT"/tmp/web-teavm-https-wss-smoke "$ROOT"/tmp/web-teavm-https-wss-smoke-*
add_glob "local deployment verifier artifacts" "$ROOT"/tmp/web-teavm-deployment-verify "$ROOT"/tmp/web-teavm-deployment-verify-*
add_glob "temporary negative preflight fixtures" "$ROOT"/tmp/web-teavm-preflight-* "$ROOT"/tmp/web-teavm-final-audit-*
add_glob "generated build output" \
	"$ROOT"/PC_Launcher/build \
	"$ROOT"/PC_Launcher/OpenRSC.jar \
	"$ROOT"/Client_Base/build \
	"$ROOT"/Android_Client/build \
	"$ROOT"/Android_Client/.gradle

if [[ "$DROP_CURRENT_VIDEO" -eq 1 ]]; then
	add_glob "current preflight Simulator video only" \
		"$ROOT"/tmp/web-teavm-iphone-release-preflight/simulator/simulator-session.mov \
		"$ROOT"/tmp/web-teavm-iphone-release-preflight/simulator/simulator-session.log \
		"$ROOT"/tmp/web-teavm-iphone-release-preflight/simulator/simulator-video-checks.json
fi

if [[ "$INCLUDE_CURRENT_PREFLIGHT" -eq 1 ]]; then
	add_candidate "$ROOT/tmp/web-teavm-iphone-release-preflight" "current aggregate preflight evidence"
fi
if [[ "$INCLUDE_PACKAGE" -eq 1 ]]; then
	add_candidate "$ROOT/dist/web-teavm" "current packaged static web root"
fi
if [[ "$INCLUDE_TEAVM_TARGET" -eq 1 ]]; then
	add_candidate "$ROOT/Web_Client_TeaVM/target/teavm" "current TeaVM build output"
fi
if [[ "$INCLUDE_PLAYWRIGHT_CACHE" -eq 1 ]]; then
	add_candidate "/tmp/voidscape-playwright-smoke" "Playwright smoke dependency cache"
fi
if [[ "$INCLUDE_XCODE_DERIVED_DATA" -eq 1 ]]; then
	add_candidate "$XCODE_DERIVED_DATA" "Xcode generated build cache"
fi

format_kib() {
	local kib="$1"
	awk -v kib="$kib" 'BEGIN { printf "%.1f MB", kib / 1024 }'
}

size_kib() {
	du -sk "$1" 2>/dev/null | awk '{ print $1 }'
}

safe_to_delete() {
	local path="$1"
	case "$path" in
		"$ROOT"/tmp/*|"$ROOT"/PC_Launcher/build|"$ROOT"/PC_Launcher/OpenRSC.jar|"$ROOT"/Client_Base/build|"$ROOT"/Android_Client/build|"$ROOT"/Android_Client/.gradle|"$ROOT"/dist/web-teavm|"$ROOT"/Web_Client_TeaVM/target/teavm|/tmp/voidscape-playwright-smoke|"$XCODE_DERIVED_DATA")
			return 0
			;;
	esac
	return 1
}

echo "==> iPhone web generated artifact cleanup"
if [[ "$APPLY" -eq 1 ]]; then
	echo "Mode: delete"
else
	echo "Mode: dry run; pass --apply to delete"
fi

if [[ "${#candidate_paths[@]}" -eq 0 ]]; then
	echo "No matching cleanup candidates found."
	exit 0
fi

total_kib=0
for i in "${!candidate_paths[@]}"; do
	path="${candidate_paths[$i]}"
	reason="${candidate_reasons[$i]}"
	kib="$(size_kib "$path")"
	if [[ ! "$kib" =~ ^[0-9]+$ ]]; then
		kib=0
	fi
	total_kib=$((total_kib + kib))
	printf "  %8s  %s\n      %s\n" "$(format_kib "$kib")" "$path" "$reason"
done

echo "Potential reclaimed space: $(format_kib "$total_kib")"

if [[ "$APPLY" -eq 0 ]]; then
	cat <<'EOF'

Protected by default:
  - tmp/web-teavm-iphone-release-preflight  (use --include-current-preflight)
  - dist/web-teavm                          (use --include-package)
  - Web_Client_TeaVM/target/teavm           (use --include-teavm-target)
  - /tmp/voidscape-playwright-smoke         (use --include-playwright-cache)
  - ~/Library/Developer/Xcode/DerivedData   (use --include-xcode-derived-data)
EOF
	exit 0
fi

for path in "${candidate_paths[@]}"; do
	if ! safe_to_delete "$path"; then
		echo "ERROR: refusing to delete unexpected path: $path" >&2
		exit 1
	fi
	rm -rf "$path"
done

echo "Deleted $(format_kib "$total_kib") of generated artifacts."
