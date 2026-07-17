#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="$ROOT/web/portal/dev-server.mjs"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-store.XXXXXX")"
SERVER_PID=""

cleanup() {
	if [[ -n "$SERVER_PID" ]]; then
		kill "$SERVER_PID" >/dev/null 2>&1 || true
		wait "$SERVER_PID" >/dev/null 2>&1 || true
	fi
	rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
	echo "portal-store safety test: $*" >&2
	exit 1
}

file_mode() {
	if stat -f '%Lp' "$1" >/dev/null 2>&1; then
		stat -f '%Lp' "$1"
	else
		stat -c '%a' "$1"
	fi
}

file_hash() {
	shasum -a 256 "$1" | awk '{print $1}'
}

free_port() {
	node -e '
const net = require("node:net");
const server = net.createServer();
server.listen(0, "127.0.0.1", () => {
	process.stdout.write(String(server.address().port));
	server.close();
});
'
}

initialize_store() {
	local dir="$1"
	PORTAL_DATA_DIR="$dir" node "$SERVER" --initialize-store
}

expect_unchanged_startup_failure() {
	local dir="$1"
	local reason="$2"
	local label="$3"
	local store="$dir/dev-store.json"
	local before
	before="$(file_hash "$store")"
	if PORTAL_DATA_DIR="$dir" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
		>"$TMP_DIR/${label}.out" 2>"$TMP_DIR/${label}.err"; then
		fail "$label unexpectedly started"
	fi
	grep -q "$reason" "$TMP_DIR/${label}.err" \
		|| fail "$label did not report $reason"
	[[ "$(file_hash "$store")" == "$before" ]] \
		|| fail "$label changed the protected store"
	if find "$dir" -maxdepth 1 -name 'dev-store.json.*.tmp' -print -quit | grep -q .; then
		fail "$label left a replacement temp file"
	fi
}

# Initialization is an explicit, absent-only command and never starts the server.
INIT_DIR="$TMP_DIR/initialized"
INIT_LOG="$TMP_DIR/initialize.log"
PORTAL_DATA_DIR="$INIT_DIR" node "$SERVER" --initialize-store >"$INIT_LOG"
grep -q 'portal_store_initialized' "$INIT_LOG" \
	|| fail "initializer did not emit its audit message"
[[ "$(file_mode "$INIT_DIR")" == "700" ]] \
	|| fail "initializer data directory is not mode 0700"
[[ "$(file_mode "$INIT_DIR/dev-store.json")" == "600" ]] \
	|| fail "initializer store is not mode 0600"
node - "$INIT_DIR/dev-store.json" <<'NODE'
const store = JSON.parse(require("node:fs").readFileSync(process.argv[2], "utf8"));
if (!store.nextIds || !Array.isArray(store.accounts) || !Array.isArray(store.characters)
	|| !Array.isArray(store.founders) || !Array.isArray(store.audit)) {
	throw new Error("initializer did not write the canonical empty store");
}
NODE
init_hash="$(file_hash "$INIT_DIR/dev-store.json")"
chmod 0755 "$INIT_DIR"
init_dir_mode_before="$(file_mode "$INIT_DIR")"
if PORTAL_DATA_DIR="$INIT_DIR" node "$SERVER" --initialize-store \
	>"$TMP_DIR/reinitialize.out" 2>"$TMP_DIR/reinitialize.err"; then
	fail "initializer overwrote an existing store"
fi
grep -q 'store_already_exists' "$TMP_DIR/reinitialize.err" \
	|| fail "second initialization did not explain its refusal"
[[ "$(file_hash "$INIT_DIR/dev-store.json")" == "$init_hash" ]] \
	|| fail "second initialization changed the existing store"
[[ "$(file_mode "$INIT_DIR")" == "$init_dir_mode_before" ]] \
	|| fail "second initialization changed the existing directory mode"
chmod 0700 "$INIT_DIR"
if node "$SERVER" --initialize-store >"$TMP_DIR/no-data.out" 2>"$TMP_DIR/no-data.err"; then
	fail "initializer accepted the default temp data directory"
fi
grep -q 'data_dir_required' "$TMP_DIR/no-data.err" \
	|| fail "initializer did not require PORTAL_DATA_DIR"

# Initialization tightens a pre-existing service-owned data directory.
PREEXISTING_INIT_DIR="$TMP_DIR/preexisting-init"
mkdir -m 0755 "$PREEXISTING_INIT_DIR"
initialize_store "$PREEXISTING_INIT_DIR" >"$TMP_DIR/preexisting-init.log"
[[ "$(file_mode "$PREEXISTING_INIT_DIR")" == "700" ]] \
	|| fail "initializer did not tighten a pre-existing data directory to 0700"
[[ "$(file_mode "$PREEXISTING_INIT_DIR/dev-store.json")" == "600" ]] \
	|| fail "initializer did not create a private store in a pre-existing directory"

# A missing public store fails before listen and stays missing.
MISSING_DIR="$TMP_DIR/missing"
if PORTAL_DATA_DIR="$MISSING_DIR" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
	>"$TMP_DIR/missing.out" 2>"$TMP_DIR/missing.err"; then
	fail "missing public store unexpectedly started"
fi
grep -q 'store_missing' "$TMP_DIR/missing.err" \
	|| fail "missing store did not fail with a sanitized reason"
[[ ! -e "$MISSING_DIR/dev-store.json" ]] \
	|| fail "missing-store startup created portal state"

# Parse and shape failures preserve the exact bytes.
MALFORMED_DIR="$TMP_DIR/malformed"
mkdir -p "$MALFORMED_DIR"
node -e 'require("node:fs").writeFileSync(process.argv[1], "{not-json", { mode: 0o600 })' \
	"$MALFORMED_DIR/dev-store.json"
expect_unchanged_startup_failure "$MALFORMED_DIR" "store_invalid_json" "malformed"

for fixture in null array empty-object accounts-object; do
	dir="$TMP_DIR/$fixture"
	mkdir -p "$dir"
	case "$fixture" in
		null) payload='null' ;;
		array) payload='[]' ;;
		empty-object) payload='{}' ;;
		accounts-object) payload='{"accounts":{}}' ;;
	esac
	node -e 'require("node:fs").writeFileSync(process.argv[1], process.argv[2], { mode: 0o600 })' \
		"$dir/dev-store.json" "$payload"
	expect_unchanged_startup_failure "$dir" "store_invalid_shape" "$fixture"
done

# Canonical public stores with ambiguous or recyclable account/character IDs fail
# before bind, without repairing or replacing the protected bytes.
for fixture in duplicate-account-ids duplicate-character-ids stale-next-account stale-next-character; do
	dir="$TMP_DIR/$fixture"
	initialize_store "$dir" >"$TMP_DIR/${fixture}-init.log"
	node - "$dir/dev-store.json" "$fixture" <<'NODE'
const fs = require("node:fs");
const path = process.argv[2];
const fixture = process.argv[3];
const store = JSON.parse(fs.readFileSync(path, "utf8"));
const account = (id, email) => ({
	id,
	emailCanonical: email,
	emailDisplay: email,
	status: "active",
	createdAt: "2026-07-16T00:00:00.000Z",
	updatedAt: "2026-07-16T00:00:00.000Z"
});
const character = (id, accountId, name) => ({
	id,
	accountId,
	name,
	normalizedName: name.toLowerCase(),
	playerId: null,
	linkStatus: "preview",
	source: "portal-preview",
	createdAt: "2026-07-16T00:00:00.000Z",
	updatedAt: "2026-07-16T00:00:00.000Z"
});

switch (fixture) {
	case "duplicate-account-ids":
		store.accounts.push(account(1, "first@example.test"), account(1, "second@example.test"));
		store.nextIds.account = 2;
		break;
	case "duplicate-character-ids":
		store.accounts.push(account(1, "owner@example.test"));
		store.nextIds.account = 2;
		store.characters.push(character(1, 1, "First"), character(1, 1, "Second"));
		store.nextIds.character = 2;
		break;
	case "stale-next-account":
		store.accounts.push(account(7, "owner@example.test"));
		store.nextIds.account = 7;
		break;
	case "stale-next-character":
		store.accounts.push(account(1, "owner@example.test"));
		store.nextIds.account = 2;
		store.characters.push(character(9, 1, "Ninth"));
		store.nextIds.character = 9;
		break;
	default:
		throw new Error(`unknown fixture: ${fixture}`);
}
fs.writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`, { mode: 0o600 });
NODE
	expect_unchanged_startup_failure "$dir" "store_invalid_ids" "$fixture"
done

# Non-regular and symlink stores fail closed.
NONREGULAR_DIR="$TMP_DIR/nonregular"
mkdir -p "$NONREGULAR_DIR/dev-store.json"
if PORTAL_DATA_DIR="$NONREGULAR_DIR" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
	>"$TMP_DIR/nonregular.out" 2>"$TMP_DIR/nonregular.err"; then
	fail "directory store unexpectedly started"
fi
grep -q 'store_not_regular' "$TMP_DIR/nonregular.err" \
	|| fail "directory store was not rejected"

SYMLINK_DIR="$TMP_DIR/symlink"
mkdir -p "$SYMLINK_DIR"
ln -s "$INIT_DIR/dev-store.json" "$SYMLINK_DIR/dev-store.json"
if PORTAL_DATA_DIR="$SYMLINK_DIR" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
	>"$TMP_DIR/symlink.out" 2>"$TMP_DIR/symlink.err"; then
	fail "symlink store unexpectedly started"
fi
grep -q 'store_not_regular' "$TMP_DIR/symlink.err" \
	|| fail "symlink store was not rejected"

UNREADABLE_DIR="$TMP_DIR/unreadable"
initialize_store "$UNREADABLE_DIR" >"$TMP_DIR/unreadable-init.log"
unreadable_hash="$(file_hash "$UNREADABLE_DIR/dev-store.json")"
chmod 000 "$UNREADABLE_DIR"
if PORTAL_DATA_DIR="$UNREADABLE_DIR" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
	>"$TMP_DIR/unreadable.out" 2>"$TMP_DIR/unreadable.err"; then
	chmod 700 "$UNREADABLE_DIR"
	fail "unreadable store unexpectedly started"
fi
chmod 700 "$UNREADABLE_DIR"
grep -q 'store_unreadable' "$TMP_DIR/unreadable.err" \
	|| grep -q 'data_dir_permissions_insecure' "$TMP_DIR/unreadable.err" \
	|| fail "inaccessible store directory was not rejected"
[[ "$(file_hash "$UNREADABLE_DIR/dev-store.json")" == "$unreadable_hash" ]] \
	|| fail "inaccessible-directory startup changed the protected bytes"

NONWRITABLE_DIR="$TMP_DIR/nonwritable-directory"
initialize_store "$NONWRITABLE_DIR" >"$TMP_DIR/nonwritable-init.log"
nonwritable_hash="$(file_hash "$NONWRITABLE_DIR/dev-store.json")"
chmod 0500 "$NONWRITABLE_DIR"
if PORTAL_DATA_DIR="$NONWRITABLE_DIR" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
	>"$TMP_DIR/nonwritable.out" 2>"$TMP_DIR/nonwritable.err"; then
	chmod 0700 "$NONWRITABLE_DIR"
	fail "non-writable store directory unexpectedly reported ready"
fi
chmod 0700 "$NONWRITABLE_DIR"
grep -q 'data_dir_permissions_insecure' "$TMP_DIR/nonwritable.err" \
	|| fail "non-writable store directory was not rejected"
[[ "$(file_hash "$NONWRITABLE_DIR/dev-store.json")" == "$nonwritable_hash" ]] \
	|| fail "non-writable-directory startup changed the protected bytes"

INSECURE_DIR="$TMP_DIR/insecure-mode"
initialize_store "$INSECURE_DIR" >"$TMP_DIR/insecure-init.log"
insecure_hash="$(file_hash "$INSECURE_DIR/dev-store.json")"
chmod 0644 "$INSECURE_DIR/dev-store.json"
if PORTAL_DATA_DIR="$INSECURE_DIR" PORTAL_PUBLIC_MODE=1 node "$SERVER" \
	>"$TMP_DIR/insecure.out" 2>"$TMP_DIR/insecure.err"; then
	fail "public store with insecure permissions unexpectedly started"
fi
grep -q 'store_permissions_insecure' "$TMP_DIR/insecure.err" \
	|| fail "insecure store permissions were not rejected"
[[ "$(file_hash "$INSECURE_DIR/dev-store.json")" == "$insecure_hash" ]] \
	|| fail "insecure-mode startup changed the protected bytes"

NONEXACT_MODE_DIR="$TMP_DIR/nonexact-mode"
initialize_store "$NONEXACT_MODE_DIR" >"$TMP_DIR/nonexact-init.log"
chmod 0400 "$NONEXACT_MODE_DIR/dev-store.json"
expect_unchanged_startup_failure \
	"$NONEXACT_MODE_DIR" "store_permissions_insecure" "nonexact-mode"
chmod 0600 "$NONEXACT_MODE_DIR/dev-store.json"

# A valid store stays ready, survives a mutation, and remains private.
VALID_DIR="$TMP_DIR/valid"
initialize_store "$VALID_DIR" >"$TMP_DIR/valid-init.log"
node - "$VALID_DIR/dev-store.json" <<'NODE'
const fs = require("node:fs");
const path = process.argv[2];
const store = JSON.parse(fs.readFileSync(path, "utf8"));
store.accounts.push({ id: 1, emailCanonical: "sentinel@example.test", status: "active" });
store.nextIds.account = 2;
fs.writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`, { mode: 0o600 });
NODE
PORT="$(free_port)"
PORT="$PORT" \
	PORTAL_DATA_DIR="$VALID_DIR" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_ABUSE_HASH_SALT="portal-store-safety-abuse-salt-1234567890" \
	node "$SERVER" >"$TMP_DIR/valid-server.log" 2>&1 &
SERVER_PID="$!"
health_status=""
for _ in {1..60}; do
	health_status="$(curl -sS -o "$TMP_DIR/health.json" -w '%{http_code}' \
		"http://127.0.0.1:$PORT/api/health" 2>/dev/null || true)"
	[[ "$health_status" == "200" ]] && break
	if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
		fail "valid store server exited before health"
	fi
	sleep 0.05
done
[[ "$health_status" == "200" ]] || fail "valid store health did not become ready"
node - "$TMP_DIR/health.json" <<'NODE'
const health = JSON.parse(require("node:fs").readFileSync(process.argv[2], "utf8"));
if (health.ok !== true || !health.storage || health.storage.storeReady !== true
	|| health.config.publicReady !== true) {
	throw new Error(`unexpected valid-store health: ${JSON.stringify(health)}`);
}
NODE
mutation_status="$(curl -sS -o "$TMP_DIR/mutation.json" -w '%{http_code}' \
	-X POST -H 'content-type: application/json' \
	--data '{"event":"click","target":"portal-store-safety"}' \
	"http://127.0.0.1:$PORT/api/funnel/click")"
[[ "$mutation_status" == "202" ]] || fail "valid store mutation returned $mutation_status"
node - "$VALID_DIR/dev-store.json" <<'NODE'
const store = JSON.parse(require("node:fs").readFileSync(process.argv[2], "utf8"));
if (!store.accounts.some((row) => row.emailCanonical === "sentinel@example.test")) {
	throw new Error("valid-store mutation lost the sentinel account");
}
if (!store.audit.some((row) => row.type === "funnel_click")) {
	throw new Error("valid-store mutation did not persist its audit row");
}
NODE
[[ "$(file_mode "$VALID_DIR/dev-store.json")" == "600" ]] \
	|| fail "atomic save widened the store permissions"

# Runtime damage flips readiness to 503 and cannot be overwritten by a mutation.
node -e 'require("node:fs").writeFileSync(process.argv[1], "{runtime-damage", { mode: 0o600 })' \
	"$VALID_DIR/dev-store.json"
damaged_hash="$(file_hash "$VALID_DIR/dev-store.json")"
health_status="$(curl -sS -o "$TMP_DIR/damaged-health.json" -w '%{http_code}' \
	"http://127.0.0.1:$PORT/api/health")"
[[ "$health_status" == "503" ]] || fail "damaged runtime store health returned $health_status"
node - "$TMP_DIR/damaged-health.json" <<'NODE'
const health = JSON.parse(require("node:fs").readFileSync(process.argv[2], "utf8"));
if (health.ok !== false || health.error !== "portal_store_unavailable"
	|| !health.storage || health.storage.storeReady !== false
	|| health.config.publicReady !== false) {
	throw new Error(`unexpected damaged-store health: ${JSON.stringify(health)}`);
}
NODE
mutation_status="$(curl -sS -o "$TMP_DIR/damaged-mutation.json" -w '%{http_code}' \
	-X POST -H 'content-type: application/json' \
	--data '{"event":"click","target":"must-not-save"}' \
	"http://127.0.0.1:$PORT/api/funnel/click")"
[[ "$mutation_status" == "500" ]] || fail "damaged-store mutation returned $mutation_status"
[[ "$(file_hash "$VALID_DIR/dev-store.json")" == "$damaged_hash" ]] \
	|| fail "damaged-store mutation replaced the protected bytes"
if find "$VALID_DIR" -maxdepth 1 -name 'dev-store.json.*.tmp' -print -quit | grep -q .; then
	fail "damaged-store mutation left a replacement temp file"
fi

kill "$SERVER_PID" >/dev/null 2>&1 || true
wait "$SERVER_PID" >/dev/null 2>&1 || true
SERVER_PID=""

echo "portal-store safety tests passed"
