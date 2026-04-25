#!/usr/bin/env bash
# reset-db.sh — wipe + reseed the dev SQLite database
#
# Default: imports authentic preservation data into server/inc/sqlite/preservation.db
# Override db name with: scripts/reset-db.sh <dbname>
# Override variant with: VARIANT=custom scripts/reset-db.sh
#
# VARIANT options:
#   authentic (default) — pure RSC schema + data
#   custom              — RSC + OpenRSC additions (auction, clans, runecraft, …)
#   retro               — 2001-era data set
# Always uses SQLite. For MariaDB, see the Makefile import-*-mariadb targets.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

DB_NAME="${1:-preservation}"
VARIANT="${VARIANT:-authentic}"

case "$VARIANT" in
    authentic|custom|retro) ;;
    *)
        echo "ERROR: VARIANT must be one of: authentic, custom, retro (got '$VARIANT')" >&2
        exit 1
        ;;
esac

echo "==> Resetting SQLite DB '$DB_NAME' (variant: $VARIANT)"
echo "    (deletes server/inc/sqlite/$DB_NAME.db then reimports)"

DB_FILE="server/inc/sqlite/$DB_NAME.db"
if [[ -f "$DB_FILE" ]]; then
    rm -f "$DB_FILE"
    echo "    deleted $DB_FILE"
fi

make "import-$VARIANT-sqlite" "db=$DB_NAME"
echo "==> Done. DB at $DB_FILE"
