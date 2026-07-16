#!/usr/bin/env bash
# Starts the existing local server only when its config is an isolated V2 QA target.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF="$ROOT/server/local.conf"

if [[ ! -f "$CONF" ]]; then
  echo "error: server/local.conf is required" >&2
  exit 2
fi

yaml_value() {
	local source_file="${2:-$CONF}"
  awk -v wanted="$1" '
    {
      line=$0
      sub(/#.*/, "", line)
      colon=index(line, ":")
      if (!colon) next
      key=substr(line, 1, colon-1)
      value=substr(line, colon+1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      if (key == wanted) result=value
    }
    END { print result }
	  ' "$source_file"
}

lowercase() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

DB_TYPE="$(yaml_value db_type "$ROOT/server/connections.conf")"
DB_NAME="$(yaml_value db_name)"
MAX_STYLE="$(yaml_value paperdoll_v2_evaluation_hair_style_max)"
AVATARS="$(yaml_value avatar_generator)"
LOCKDOWN="$(yaml_value production_command_lockdown)"
CREATION_MODE="$(yaml_value character_creation_mode)"
DB_TYPE_LOWER="$(lowercase "$DB_TYPE")"
AVATARS_LOWER="$(lowercase "$AVATARS")"
LOCKDOWN_LOWER="$(lowercase "$LOCKDOWN")"

[[ "$DB_TYPE_LOWER" == "sqlite" ]] || {
  echo "error: Paperdoll V2 evaluation requires db_type: sqlite" >&2
  exit 2
}
[[ "$DB_NAME" == *_qa ]] || {
  echo "error: Paperdoll V2 evaluation requires a disposable db_name ending in _qa" >&2
  exit 2
}
[[ "$MAX_STYLE" == "6" ]] || {
  echo "error: set paperdoll_v2_evaluation_hair_style_max: 6 in server/local.conf" >&2
  exit 2
}
[[ "$AVATARS_LOWER" == "false" ]] || {
  echo "error: Paperdoll V2 evaluation requires avatar_generator: false" >&2
  exit 2
}
[[ -z "$LOCKDOWN_LOWER" || "$LOCKDOWN_LOWER" == "false" ]] || {
  echo "error: Paperdoll V2 evaluation refuses production_command_lockdown: true" >&2
  exit 2
}
[[ -z "$CREATION_MODE" || "$CREATION_MODE" == "0" ]] || {
  echo "error: Paperdoll V2 evaluation requires character_creation_mode: 0" >&2
  exit 2
}

echo "==> Starting guarded Paperdoll V2 evaluation server (SQLite QA database: $DB_NAME)"
cd "$ROOT/server"
exec ant runserverzgc -DconfFile=local -DpaperdollV2EvaluationServer=true
