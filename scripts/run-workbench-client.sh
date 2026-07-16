#!/usr/bin/env bash
# run-workbench-client.sh - launch the PC client with the local AI workbench.
#
# Dev shortcut:
#   scripts/run-workbench-client.sh --login StepAlt:stepalt
#   scripts/run-workbench-client.sh --user StepAlt --pass stepalt --workbench-port 18787

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

CLIENT_HOST="${VOIDSCAPE_CLIENT_HOST:-127.0.0.1}"
DEFAULT_PORT=43594
if [[ -f server/local.conf ]]; then
    PORT_FROM_CONF=$(grep -E '^[[:space:]]*server_port:' server/local.conf | head -1 | awk '{print $2}' || true)
    DEFAULT_PORT="${PORT_FROM_CONF:-43594}"
fi
CLIENT_PORT="${VOIDSCAPE_CLIENT_PORT:-$DEFAULT_PORT}"

WORKBENCH_PORT="${VOIDSCAPE_WORKBENCH_PORT:-18787}"
WORKBENCH_DIR="${VOIDSCAPE_WORKBENCH_DIR:-$REPO_ROOT/tmp/workbench}"
AUTO_LOGIN_USER="${VOIDSCAPE_AUTO_LOGIN_USER:-}"
AUTO_LOGIN_PASS="${VOIDSCAPE_AUTO_LOGIN_PASS:-}"
PAPERDOLL_V2_PACK="${VOIDSCAPE_PAPERDOLL_V2_PACK:-}"
PAPERDOLL_V2_STACK="${VOIDSCAPE_PAPERDOLL_V2_STACK:-rare_hair}"
PAPERDOLL_V2_SELECTOR_REGISTRY="${VOIDSCAPE_PAPERDOLL_V2_SELECTOR_REGISTRY:-}"
PAPERDOLL_V2_LEGACY_COMPATIBILITY="${VOIDSCAPE_PAPERDOLL_V2_LEGACY_COMPATIBILITY:-}"
PAPERDOLL_V2_FORCE_LEGACY="${VOIDSCAPE_PAPERDOLL_V2_FORCE_LEGACY:-false}"
PAPERDOLL_V2_DESIGNER_EVALUATION="${VOIDSCAPE_PAPERDOLL_V2_DESIGNER_EVALUATION:-false}"
PAPERDOLL_V2_COMPATIBILITY_ONLY=false

usage() {
    sed -n '1,8p' "$0"
    cat <<'EOF'

Options:
  --host HOST             Game server host written to Client_Base/Cache/ip.txt.
  --port PORT             Game server port written to Client_Base/Cache/port.txt.
  --workbench-port PORT   Loopback workbench HTTP port.
  --out DIR               Workbench screenshot/output directory.
  --login USER:PASS       Enable dev auto-login.
  --user USER             Enable dev auto-login username.
  --pass PASS             Enable dev auto-login password.
  --paperdoll-v2-pack FILE
                          Enable the PC-only V2 scene proof from an explicit tmp pack.
  --paperdoll-v2-workspace DIR
                          Use DIR/build/Paperdoll_V2.orsc (or DIR/Paperdoll_V2.orsc).
                          Auto-select its sibling selector-registry.properties when present.
  --paperdoll-v2-selector-registry FILE
                          Use the explicit sibling selector registry for per-player styles.
  --paperdoll-v2-legacy-compatibility FILE
                          Use an explicit build/legacy-compatibility/runtime.properties.
  --paperdoll-v2-stack ID Select a global live-controls stack when no selector sidecar is used
                          (legacy V2 proof default: rare_hair).
  --paperdoll-v2-force-legacy
                          Keep the 1024x680/shift-10 proof scene but render legacy baseline.
  --paperdoll-v2-designer-evaluation
                          Enable the in-game PC Workbench 0..6 Style selector.
  --paperdoll-v2-compatibility-only
                          Launch explicit missing-pack fallback with only runtime.properties.
  -h, --help              Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --host)
            [[ $# -ge 2 ]] || { echo "error: --host needs a value" >&2; exit 2; }
            CLIENT_HOST="$2"
            shift 2
            ;;
        --port)
            [[ $# -ge 2 ]] || { echo "error: --port needs a value" >&2; exit 2; }
            CLIENT_PORT="$2"
            shift 2
            ;;
        --workbench-port)
            [[ $# -ge 2 ]] || { echo "error: --workbench-port needs a value" >&2; exit 2; }
            WORKBENCH_PORT="$2"
            shift 2
            ;;
        --out|--workbench-dir)
            [[ $# -ge 2 ]] || { echo "error: $1 needs a value" >&2; exit 2; }
            WORKBENCH_DIR="$2"
            shift 2
            ;;
        --login)
            [[ $# -ge 2 ]] || { echo "error: --login needs USER:PASS" >&2; exit 2; }
            AUTO_LOGIN_USER="${2%%:*}"
            AUTO_LOGIN_PASS="${2#*:}"
            [[ "$AUTO_LOGIN_USER" != "$2" ]] || { echo "error: --login needs USER:PASS" >&2; exit 2; }
            shift 2
            ;;
        --user)
            [[ $# -ge 2 ]] || { echo "error: --user needs a value" >&2; exit 2; }
            AUTO_LOGIN_USER="$2"
            shift 2
            ;;
        --pass|--password)
            [[ $# -ge 2 ]] || { echo "error: $1 needs a value" >&2; exit 2; }
            AUTO_LOGIN_PASS="$2"
            shift 2
            ;;
        --paperdoll-v2-pack)
            [[ $# -ge 2 ]] || { echo "error: --paperdoll-v2-pack needs a value" >&2; exit 2; }
            PAPERDOLL_V2_PACK="$2"
            shift 2
            ;;
        --paperdoll-v2-workspace)
            [[ $# -ge 2 ]] || { echo "error: --paperdoll-v2-workspace needs a value" >&2; exit 2; }
            if [[ -f "$2/build/Paperdoll_V2.orsc" ]]; then
                PAPERDOLL_V2_PACK="$2/build/Paperdoll_V2.orsc"
                if [[ -f "$2/build/selector-registry.properties" ]]; then
                    PAPERDOLL_V2_SELECTOR_REGISTRY="$2/build/selector-registry.properties"
                fi
                if [[ -f "$2/build/legacy-compatibility/runtime.properties" ]]; then
                    PAPERDOLL_V2_LEGACY_COMPATIBILITY="$2/build/legacy-compatibility/runtime.properties"
                fi
            else
                PAPERDOLL_V2_PACK="$2/Paperdoll_V2.orsc"
                if [[ -f "$2/selector-registry.properties" ]]; then
                    PAPERDOLL_V2_SELECTOR_REGISTRY="$2/selector-registry.properties"
                fi
                if [[ -f "$2/legacy-compatibility/runtime.properties" ]]; then
                    PAPERDOLL_V2_LEGACY_COMPATIBILITY="$2/legacy-compatibility/runtime.properties"
                fi
            fi
            shift 2
            ;;
        --paperdoll-v2-selector-registry)
            [[ $# -ge 2 ]] || { echo "error: --paperdoll-v2-selector-registry needs a value" >&2; exit 2; }
            PAPERDOLL_V2_SELECTOR_REGISTRY="$2"
            shift 2
            ;;
        --paperdoll-v2-legacy-compatibility)
            [[ $# -ge 2 ]] || { echo "error: --paperdoll-v2-legacy-compatibility needs a value" >&2; exit 2; }
            PAPERDOLL_V2_LEGACY_COMPATIBILITY="$2"
            shift 2
            ;;
        --paperdoll-v2-stack)
            [[ $# -ge 2 ]] || { echo "error: --paperdoll-v2-stack needs a value" >&2; exit 2; }
            PAPERDOLL_V2_STACK="$2"
            shift 2
            ;;
        --paperdoll-v2-force-legacy)
            PAPERDOLL_V2_FORCE_LEGACY=true
            shift
            ;;
        --paperdoll-v2-designer-evaluation)
            PAPERDOLL_V2_DESIGNER_EVALUATION=true
            shift
            ;;
        --paperdoll-v2-compatibility-only)
            PAPERDOLL_V2_COMPATIBILITY_ONLY=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "error: unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ "$PAPERDOLL_V2_DESIGNER_EVALUATION" == "true" ]]; then
    case "$CLIENT_HOST" in
        127.0.0.1|localhost|::1) ;;
        *)
            echo "error: Paperdoll V2 designer evaluation may connect only to a loopback QA server" >&2
            exit 2
            ;;
    esac
fi

if [[ -n "$AUTO_LOGIN_USER" || -n "$AUTO_LOGIN_PASS" ]]; then
    [[ -n "$AUTO_LOGIN_USER" && -n "$AUTO_LOGIN_PASS" ]] || {
        echo "error: auto-login needs both username and password" >&2
        exit 2
    }
    export VOIDSCAPE_AUTO_LOGIN_USER="$AUTO_LOGIN_USER"
    export VOIDSCAPE_AUTO_LOGIN_PASS="$AUTO_LOGIN_PASS"
fi

mkdir -p Client_Base/Cache "$WORKBENCH_DIR"
WORKBENCH_DIR="$(cd "$WORKBENCH_DIR" && pwd)"
echo "$CLIENT_HOST" > Client_Base/Cache/ip.txt
echo "$CLIENT_PORT" > Client_Base/Cache/port.txt

echo "==> Client will connect to $CLIENT_HOST:$CLIENT_PORT"
echo "==> Workbench will listen on http://127.0.0.1:$WORKBENCH_PORT"
echo "==> Workbench captures will be saved under $WORKBENCH_DIR"
if [[ -n "$AUTO_LOGIN_USER" ]]; then
    echo "==> Dev auto-login enabled for user: $AUTO_LOGIN_USER"
fi
PAPERDOLL_V2_JVMARGS=""
if [[ -n "$PAPERDOLL_V2_PACK" ]]; then
	[[ "$PAPERDOLL_V2_COMPATIBILITY_ONLY" == "false" ]] || {
		echo "error: --paperdoll-v2-compatibility-only cannot be combined with a pack/workspace" >&2
		exit 2
	}
    [[ -f "$PAPERDOLL_V2_PACK" ]] || {
        echo "error: Paperdoll V2 pack does not exist: $PAPERDOLL_V2_PACK" >&2
        exit 2
    }
    PAPERDOLL_V2_PACK="$(cd "$(dirname "$PAPERDOLL_V2_PACK")" && pwd)/$(basename "$PAPERDOLL_V2_PACK")"
    PAPERDOLL_V2_LEGACY_COMPATIBILITY_JVMARG=""
    if [[ -n "$PAPERDOLL_V2_LEGACY_COMPATIBILITY" ]]; then
        [[ -f "$PAPERDOLL_V2_LEGACY_COMPATIBILITY" ]] || {
            echo "error: Paperdoll V2 legacy compatibility contract does not exist: $PAPERDOLL_V2_LEGACY_COMPATIBILITY" >&2
            exit 2
        }
        PAPERDOLL_V2_LEGACY_COMPATIBILITY="$(cd "$(dirname "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")" && pwd)/$(basename "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")"
        [[ "$(basename "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")" == "runtime.properties" ]] || {
            echo "error: Paperdoll V2 legacy compatibility contract must be named runtime.properties" >&2
            exit 2
        }
        [[ "$(basename "$(dirname "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")")" == "legacy-compatibility" ]] || {
            echo "error: Paperdoll V2 legacy compatibility contract must be below legacy-compatibility/" >&2
            exit 2
        }
        PAPERDOLL_V2_LEGACY_COMPATIBILITY_JVMARG=" -Dvoidscape.paperdollV2.legacyCompatibility=$PAPERDOLL_V2_LEGACY_COMPATIBILITY"
        echo "==> Paperdoll V2 legacy compatibility: $PAPERDOLL_V2_LEGACY_COMPATIBILITY"
    fi
    if [[ -n "$PAPERDOLL_V2_SELECTOR_REGISTRY" ]]; then
        [[ -f "$PAPERDOLL_V2_SELECTOR_REGISTRY" ]] || {
            echo "error: Paperdoll V2 selector registry does not exist: $PAPERDOLL_V2_SELECTOR_REGISTRY" >&2
            exit 2
        }
        PAPERDOLL_V2_SELECTOR_REGISTRY="$(cd "$(dirname "$PAPERDOLL_V2_SELECTOR_REGISTRY")" && pwd)/$(basename "$PAPERDOLL_V2_SELECTOR_REGISTRY")"
        [[ "$(basename "$PAPERDOLL_V2_SELECTOR_REGISTRY")" == "selector-registry.properties" ]] || {
            echo "error: Paperdoll V2 selector registry must be named selector-registry.properties" >&2
            exit 2
        }
        [[ "$(dirname "$PAPERDOLL_V2_SELECTOR_REGISTRY")" == "$(dirname "$PAPERDOLL_V2_PACK")" ]] || {
            echo "error: Paperdoll V2 selector registry must be the sibling of Paperdoll_V2.orsc" >&2
            exit 2
        }
        [[ "$PAPERDOLL_V2_DESIGNER_EVALUATION" == "false" || "$PAPERDOLL_V2_FORCE_LEGACY" == "false" ]] || {
            echo "error: designer evaluation cannot be forced to legacy" >&2
            exit 2
        }
        PAPERDOLL_V2_JVMARGS="-Dvoidscape.paperdollV2=true -Dvoidscape.paperdollV2.pack=$PAPERDOLL_V2_PACK -Dvoidscape.paperdollV2.selectorRegistry=$PAPERDOLL_V2_SELECTOR_REGISTRY -Dvoidscape.paperdollV2.forceLegacy=$PAPERDOLL_V2_FORCE_LEGACY -Dvoidscape.paperdollV2.designerEvaluation=$PAPERDOLL_V2_DESIGNER_EVALUATION$PAPERDOLL_V2_LEGACY_COMPATIBILITY_JVMARG -Dvoidscape.framePacing=true -Dvoidscape.framePacing.out=$WORKBENCH_DIR/paperdoll-v2-frame-pacing.log"
        echo "==> Paperdoll V2 scene proof: selector-registry=$PAPERDOLL_V2_SELECTOR_REGISTRY forceLegacy=$PAPERDOLL_V2_FORCE_LEGACY"
    else
		[[ "$PAPERDOLL_V2_DESIGNER_EVALUATION" == "false" ]] || {
			echo "error: designer evaluation requires a sibling selector registry" >&2
			exit 2
		}
        PAPERDOLL_V2_JVMARGS="-Dvoidscape.paperdollV2=true -Dvoidscape.paperdollV2.pack=$PAPERDOLL_V2_PACK -Dvoidscape.paperdollV2.stack=$PAPERDOLL_V2_STACK -Dvoidscape.paperdollV2.forceLegacy=$PAPERDOLL_V2_FORCE_LEGACY$PAPERDOLL_V2_LEGACY_COMPATIBILITY_JVMARG -Dvoidscape.framePacing=true -Dvoidscape.framePacing.out=$WORKBENCH_DIR/paperdoll-v2-frame-pacing.log"
        echo "==> Paperdoll V2 scene proof: stack=$PAPERDOLL_V2_STACK forceLegacy=$PAPERDOLL_V2_FORCE_LEGACY"
    fi
    echo "==> Paperdoll V2 pack: $PAPERDOLL_V2_PACK"
elif [[ "$PAPERDOLL_V2_COMPATIBILITY_ONLY" == "true" ]]; then
    [[ -n "$PAPERDOLL_V2_LEGACY_COMPATIBILITY" ]] || {
        echo "error: compatibility-only mode requires --paperdoll-v2-legacy-compatibility" >&2
        exit 2
    }
    [[ -f "$PAPERDOLL_V2_LEGACY_COMPATIBILITY" ]] || {
        echo "error: Paperdoll V2 legacy compatibility contract does not exist: $PAPERDOLL_V2_LEGACY_COMPATIBILITY" >&2
        exit 2
    }
    [[ -z "$PAPERDOLL_V2_SELECTOR_REGISTRY" && "$PAPERDOLL_V2_FORCE_LEGACY" == "false"
        && "$PAPERDOLL_V2_DESIGNER_EVALUATION" == "false" ]] || {
        echo "error: compatibility-only mode cannot use selector, force-legacy, or designer flags" >&2
        exit 2
    }
    PAPERDOLL_V2_LEGACY_COMPATIBILITY="$(cd "$(dirname "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")" && pwd)/$(basename "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")"
    [[ "$(basename "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")" == "runtime.properties"
        && "$(basename "$(dirname "$PAPERDOLL_V2_LEGACY_COMPATIBILITY")")" == "legacy-compatibility" ]] || {
        echo "error: compatibility-only contract must be legacy-compatibility/runtime.properties" >&2
        exit 2
    }
    PAPERDOLL_V2_JVMARGS="-Dvoidscape.paperdollV2=true -Dvoidscape.paperdollV2.legacyCompatibility=$PAPERDOLL_V2_LEGACY_COMPATIBILITY -Dvoidscape.paperdollV2.designerEvaluation=false"
    echo "==> Paperdoll V2 compatibility-only missing-pack proof: $PAPERDOLL_V2_LEGACY_COMPATIBILITY"
elif [[ -n "$PAPERDOLL_V2_SELECTOR_REGISTRY" ]]; then
    echo "error: --paperdoll-v2-selector-registry requires a V2 pack/workspace" >&2
    exit 2
elif [[ -n "$PAPERDOLL_V2_LEGACY_COMPATIBILITY" ]]; then
    echo "error: --paperdoll-v2-legacy-compatibility requires a V2 pack/workspace" >&2
    exit 2
elif [[ "$PAPERDOLL_V2_FORCE_LEGACY" == "true" ]]; then
    echo "error: --paperdoll-v2-force-legacy requires a V2 pack/workspace" >&2
	exit 2
elif [[ "$PAPERDOLL_V2_DESIGNER_EVALUATION" == "true" ]]; then
    echo "error: --paperdoll-v2-designer-evaluation requires a V2 workspace and selector registry" >&2
    exit 2
fi
echo "==> Building + launching PC client"

cd Client_Base
ant \
	    -Dworkbench.jvmargs="-Dvoidscape.workbench=true -Dvoidscape.workbench.port=$WORKBENCH_PORT -Dvoidscape.workbench.dir=$WORKBENCH_DIR $PAPERDOLL_V2_JVMARGS ${VOIDSCAPE_WORKBENCH_JVMARGS:-}" \
	    compile-and-run
