#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
FIXTURE_RELATIVE="Cache/.voidscape-package-hygiene-test"
FIXTURE_DIR="$TARGET/$FIXTURE_RELATIVE"
OUTPUT_DIR="$ROOT/tmp/web-teavm-package-hygiene-unit"

if [[ ! -f "$TARGET/index.html" ]]; then
	echo "ERROR: build the TeaVM client before running this test." >&2
	exit 1
fi
if [[ -e "$FIXTURE_DIR" || -L "$FIXTURE_DIR" ]]; then
	echo "ERROR: refusing to replace existing fixture path: $FIXTURE_DIR" >&2
	exit 1
fi

cleanup() {
	rm -rf -- "$FIXTURE_DIR" "$OUTPUT_DIR"
}
trap cleanup EXIT

mkdir -p "$FIXTURE_DIR"
printf 'keep\n' > "$FIXTURE_DIR/archive.bakery"
printf 'remove\n' > "$FIXTURE_DIR/.DS_Store"
printf 'remove\n' > "$FIXTURE_DIR/Thumbs.DB"
printf 'remove\n' > "$FIXTURE_DIR/editor.BAK"
printf 'remove\n' > "$FIXTURE_DIR/partial.DoWnLoAd"
printf 'remove\n' > "$FIXTURE_DIR/swap.SWP"
printf 'remove\n' > "$FIXTURE_DIR/note~"
ln -s archive.bakery "$FIXTURE_DIR/link.ReJ"

"$ROOT/scripts/package-web-teavm.sh" --skip-build --output-dir "$OUTPUT_DIR" >/dev/null

if [[ ! -f "$OUTPUT_DIR/$FIXTURE_RELATIVE/archive.bakery" ]]; then
	echo "ERROR: legitimate suffix extension was pruned from the web package." >&2
	exit 1
fi
if [[ "$(< "$OUTPUT_DIR/$FIXTURE_RELATIVE/archive.bakery")" != "keep" ]]; then
	echo "ERROR: legitimate package fixture content changed." >&2
	exit 1
fi

for relative in \
	"$FIXTURE_RELATIVE/.DS_Store" \
	"$FIXTURE_RELATIVE/Thumbs.DB" \
	"$FIXTURE_RELATIVE/editor.BAK" \
	"$FIXTURE_RELATIVE/partial.DoWnLoAd" \
	"$FIXTURE_RELATIVE/swap.SWP" \
	"$FIXTURE_RELATIVE/note~" \
	"$FIXTURE_RELATIVE/link.ReJ"; do
	if [[ -e "$OUTPUT_DIR/$relative" || -L "$OUTPUT_DIR/$relative" ]]; then
		echo "ERROR: scratch fixture survived production packaging: $relative" >&2
		exit 1
	fi
done

python3 - "$OUTPUT_DIR/voidscape-web-build.json" "$FIXTURE_RELATIVE" <<'PY'
import json
import sys

manifest_path, fixture = sys.argv[1:]
manifest = json.load(open(manifest_path, encoding="utf-8"))
paths = {entry.get("path") for entry in manifest.get("files", [])}
expected = f"{fixture}/archive.bakery"
if expected not in paths:
    raise SystemExit(f"manifest omitted legitimate fixture: {expected}")
for path in paths:
    basename = str(path or "").rsplit("/", 1)[-1].lower()
    if basename in {".ds_store", "thumbs.db"} or basename.endswith(
        (".bak", ".download", ".new", ".orig", ".part", ".predungeon", ".rej", ".swp", ".temp", ".tmp", "~")
    ):
        raise SystemExit(f"manifest retained scratch fixture: {path}")
PY

echo "TeaVM package hygiene unit test passed."
