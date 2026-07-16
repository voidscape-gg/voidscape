#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PYTHONPATH="$ROOT/tools/appearance-studio${PYTHONPATH:+:$PYTHONPATH}"

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/appearance-v2.sh init <name> <tmp-workspace> [--force]
  scripts/appearance-v2.sh collection <tmp-workspace> [--force]
  scripts/appearance-v2.sh build <tmp-workspace>
  scripts/appearance-v2.sh validate <tmp-workspace>
  scripts/appearance-v2.sh validate-pack <Paperdoll_V2.orsc>
  scripts/appearance-v2.sh validate-template
  scripts/appearance-v2.sh validate-catalog
  scripts/appearance-v2.sh editor <tmp-workspace> [editor options]
  scripts/appearance-v2.sh compare-oracle <tmp-workspace> <java-report.json> <tmp-output>
  scripts/appearance-v2.sh test
EOF
  exit 2
}

command_name="${1:-}"
case "$command_name" in
  init)
    [[ $# -ge 3 ]] || usage
    name="$2"
    workspace="$3"
    shift 3
    exec python3 -m appearance_studio.v2_cli init --name "$name" --out "$workspace" "$@"
    ;;
  collection)
    [[ $# -ge 2 ]] || usage
    workspace="$2"
    shift 2
    exec python3 -m appearance_studio.v2_cli collection --out "$workspace" "$@"
    ;;
  build)
    [[ $# -eq 2 ]] || usage
    exec python3 -m appearance_studio.v2_cli build --workspace "$2"
    ;;
  validate)
    [[ $# -eq 2 ]] || usage
    exec python3 -m appearance_studio.v2_cli validate --workspace "$2"
    ;;
  validate-pack)
    [[ $# -eq 2 ]] || usage
    exec python3 -m appearance_studio.v2_cli validate-pack --pack "$2"
    ;;
  validate-template)
    [[ $# -eq 1 ]] || usage
    exec python3 -m appearance_studio.v2_cli validate-template
    ;;
  validate-catalog)
    [[ $# -eq 1 ]] || usage
    exec python3 -m appearance_studio.v2_cli validate-catalog
    ;;
  editor)
    [[ $# -ge 2 ]] || usage
    workspace="$2"
    shift 2
    exec python3 -m appearance_studio.v2_editor_server "$workspace" "$@"
    ;;
  compare-oracle)
    [[ $# -eq 4 ]] || usage
    exec python3 -m appearance_studio.v2_cli compare-oracle --workspace "$2" --report "$3" --out "$4"
    ;;
  test)
    [[ $# -eq 1 ]] || usage
    cd "$ROOT/tools/appearance-studio"
    exec python3 -m unittest discover -s tests -p 'test_v2_*.py' -v
    ;;
  *) usage ;;
esac
