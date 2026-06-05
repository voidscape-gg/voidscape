#!/usr/bin/env bash
# run-launcher.sh — build and run the Voidscape desktop launcher

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/PC_Launcher"

ant compile
java -jar OpenRSC.jar "$@"
