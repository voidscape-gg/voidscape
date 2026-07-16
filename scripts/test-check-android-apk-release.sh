#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKER="$ROOT/scripts/check-android-apk-release.sh"
PROVENANCE_TOOL="$ROOT/scripts/android-provenance.py"
DEBUG_APK="${ANDROID_RELEASE_GATE_TEST_APK:-$ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk}"
DEBUG_META="${DEBUG_APK}.json"
APKSIGNER_BIN="${VOIDSCAPE_ANDROID_APKSIGNER:-}"

resolve_apksigner() {
	local candidate=""
	local found=""
	local sdk_root=""

	if [[ -n "$APKSIGNER_BIN" && -x "$APKSIGNER_BIN" ]]; then
		printf '%s\n' "$APKSIGNER_BIN"
		return 0
	fi
	if candidate="$(command -v apksigner 2>/dev/null)" && [[ -x "$candidate" ]]; then
		printf '%s\n' "$candidate"
		return 0
	fi
	for sdk_root in \
		"${ANDROID_SDK_ROOT:-}" \
		"${ANDROID_HOME:-}" \
		"$HOME/Library/Android/sdk" \
		"/opt/homebrew/share/android-commandlinetools"; do
		[[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]] || continue
		for candidate in "$sdk_root"/build-tools/*/apksigner; do
			[[ -x "$candidate" ]] && found="$candidate"
		done
	done
	[[ -n "$found" ]] || return 1
	printf '%s\n' "$found"
}

expect_failure() {
	local label="$1"
	local expected_text="$2"
	shift 2
	local output=""
	local status=0

	set +e
	output="$("$@" 2>&1)"
	status=$?
	set -e
	if [[ "$status" -eq 0 ]]; then
		echo "ERROR: $label unexpectedly passed." >&2
		exit 1
	fi
	if [[ "$output" != *"$expected_text"* ]]; then
		echo "ERROR: $label failed without expected diagnostic: $expected_text" >&2
		printf '%s\n' "$output" >&2
		exit 1
	fi
	echo "PASS: $label"
}

if [[ ! -x "$CHECKER" || ! -f "$PROVENANCE_TOOL" ]]; then
	echo "ERROR: release checker and provenance helper must exist." >&2
	exit 1
fi
if [[ ! -f "$DEBUG_APK" || ! -f "$DEBUG_META" ]]; then
	echo "ERROR: build the current debug APK first with scripts/build-android.sh --debug." >&2
	exit 1
fi
if ! APKSIGNER_BIN="$(resolve_apksigner)"; then
	echo "ERROR: apksigner is required for the APK release-gate regression test." >&2
	exit 1
fi

signature_output="$("$APKSIGNER_BIN" verify --print-certs "$DEBUG_APK" 2>&1)"
debug_signer="$(printf '%s\n' "$signature_output" | awk -F': ' '/Signer #[0-9]+ certificate SHA-256 digest:/ {
	print tolower($2);
	exit;
}')"
debug_signer="$(printf '%s' "$debug_signer" | tr -d '[:space:]:')"
if [[ ! "$debug_signer" =~ ^[0-9a-f]{64}$ ]]; then
	echo "ERROR: could not read the current debug APK signer certificate." >&2
	exit 1
fi

source_client_version="$(awk '/CLIENT_VERSION[[:space:]]*=/ {
	for (i = 1; i <= NF; i++) {
		if ($i ~ /^[0-9]+;?$/) {
			gsub(/;/, "", $i);
			print $i;
			exit;
		}
	}
}' "$ROOT/Client_Base/src/orsc/Config.java")"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-apk-gate-test.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT
release_meta="$tmp_dir/release-shaped-debug-apk.json"
malformed_meta="$tmp_dir/malformed.json"

python3 - "$DEBUG_META" "$release_meta" "$DEBUG_APK" "$(git -C "$ROOT" rev-parse HEAD)" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

source_path, output_path, apk_path, commit = sys.argv[1:]
meta = json.loads(Path(source_path).read_text(encoding="utf-8"))
apk = Path(apk_path)
meta.update({
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "sizeBytes": apk.stat().st_size,
    "gitCommit": commit,
    "buildType": "release",
    "artifactType": "apk",
})
Path(output_path).write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
PY
printf '{not valid json\n' > "$malformed_meta"

debug_args=(
	--apk "$DEBUG_APK"
	--server-client-version "$source_client_version"
	--expected-signer-sha256 "$debug_signer"
	--apksigner "$APKSIGNER_BIN"
)
expect_failure \
	"current debug APK is rejected even with release-shaped metadata" \
	"APK manifest is debuggable" \
	"$CHECKER" "${debug_args[@]}" --meta "$release_meta"
expect_failure \
	"malformed metadata is rejected" \
	"could not parse APK metadata" \
	"$CHECKER" "${debug_args[@]}" --meta "$malformed_meta"

fixture_root="$tmp_dir/source"
fixture_artifacts="$tmp_dir/artifacts"
mkdir -p \
	"$fixture_root/Client_Base/src/orsc" \
	"$fixture_root/Client_Base/Cache" \
	"$fixture_root/Duel_Proof/src/main/java/com/voidscape/duelproof" \
	"$fixture_root/Android_Client/Open RSC Android Client" \
	"$fixture_root/scripts" \
	"$fixture_artifacts/assets"

python3 - "$fixture_root" "$fixture_artifacts" "$PROVENANCE_TOOL" <<'PY'
import shutil
import stat
import sys
from pathlib import Path

root = Path(sys.argv[1])
artifacts = Path(sys.argv[2])
tool = Path(sys.argv[3])
(root / "Client_Base/src/orsc/Config.java").write_text(
    "public final class Config { public static final int CLIENT_VERSION = 10127; }\n",
    encoding="utf-8",
)
(root / "Client_Base/Cache/fixture.bin").write_bytes(b"fixture-cache\n")
(root / "Duel_Proof/src/main/java/com/voidscape/duelproof/Fixture.java").write_text(
    "package com.voidscape.duelproof; public final class Fixture {}\n",
    encoding="utf-8",
)
(root / "Android_Client/fixture.txt").write_text("fixture android input\n", encoding="utf-8")
(root / "Android_Client/Open RSC Android Client/build.gradle").write_text(
    '''android {\n'''
    '''  defaultConfig {\n'''
    '''    applicationId "com.voidscape.gg"\n'''
    '''    minSdk 23\n'''
    '''    targetSdk 35\n'''
    '''    versionCode 7\n'''
    '''    versionName "1.0.6"\n'''
    '''  }\n'''
    '''}\n''',
    encoding="utf-8",
)
(root / "scripts/build-android.sh").write_text("#!/bin/sh\n# fixture\n", encoding="utf-8")
shutil.copyfile(tool, root / "scripts/android-provenance.py")

aapt = artifacts / "aapt"
aapt.write_text(
    "#!/bin/sh\n"
    "cat <<'EOF'\n"
    "package: name='com.voidscape.gg' versionCode='7' versionName='1.0.6'\n"
    "sdkVersion:'23'\n"
    "targetSdkVersion:'35'\n"
    "EOF\n",
    encoding="utf-8",
)
apksigner = artifacts / "apksigner"
apksigner.write_text(
    "#!/bin/sh\n"
    "echo 'Signer #1 certificate SHA-256 digest: " + ("ab" * 32) + "'\n",
    encoding="utf-8",
)
for path in (aapt, apksigner):
    path.chmod(path.stat().st_mode | stat.S_IXUSR)
PY

git -C "$fixture_root" init -q
git -C "$fixture_root" config user.name "Voidscape Release Gate"
git -C "$fixture_root" config user.email "release-gate@voidscape.invalid"
git -C "$fixture_root" add .
git -C "$fixture_root" commit -qm "Create release fixture"

fixture_provenance="$fixture_artifacts/assets/voidscape-provenance.json"
python3 "$PROVENANCE_TOOL" write \
	--repo-root "$fixture_root" \
	--output "$fixture_provenance" \
	--dirty-release-override false >/dev/null
fixture_apk="$fixture_artifacts/release-fixture.apk"
fixture_meta="$fixture_apk.json"
python3 - "$fixture_artifacts" "$fixture_apk" "$fixture_meta" <<'PY'
import hashlib
import json
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

artifacts, apk_path, meta_path = map(Path, sys.argv[1:])
provenance_path = artifacts / "assets/voidscape-provenance.json"
with zipfile.ZipFile(apk_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(provenance_path, "assets/voidscape-provenance.json")
    archive.writestr("assets/cache/archive.bakery", "legitimate payload\n")
provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
metadata = {
    "clientVersion": 10127,
    "sha256": hashlib.sha256(apk_path.read_bytes()).hexdigest(),
    "sizeBytes": apk_path.stat().st_size,
    "builtAt": datetime.now(timezone.utc).isoformat(),
    "buildType": "release",
    "artifactType": "apk",
}
metadata.update(provenance)
meta_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY

fixture_args=(
	--apk "$fixture_apk"
	--meta "$fixture_meta"
	--source-config "$fixture_root/Client_Base/src/orsc/Config.java"
	--android-build-config "$fixture_root/Android_Client/Open RSC Android Client/build.gradle"
	--provenance-root "$fixture_root"
	--server-client-version 10127
	--expected-signer-sha256 "$(printf 'ab%.0s' {1..32})"
	--aapt "$fixture_artifacts/aapt"
	--apksigner "$fixture_artifacts/apksigner"
)
"$CHECKER" "${fixture_args[@]}" >/dev/null
echo "PASS: signed release-shaped fixture is accepted"

scratch_apk="$fixture_artifacts/scratch-path.apk"
scratch_meta="$scratch_apk.json"
python3 - "$fixture_apk" "$fixture_meta" "$scratch_apk" "$scratch_meta" <<'PY'
import hashlib
import json
import sys
import zipfile
from pathlib import Path

source_apk, source_meta, output_apk, output_meta = map(Path, sys.argv[1:])
with zipfile.ZipFile(source_apk) as source, zipfile.ZipFile(
    output_apk, "w", compression=zipfile.ZIP_DEFLATED
) as output:
    for info in source.infolist():
        output.writestr(info, source.read(info.filename))
    output.writestr("assets/cache/video/editor.BaK", "scratch payload\n")
metadata = json.loads(source_meta.read_text(encoding="utf-8"))
metadata["sha256"] = hashlib.sha256(output_apk.read_bytes()).hexdigest()
metadata["sizeBytes"] = output_apk.stat().st_size
output_meta.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY
scratch_args=("${fixture_args[@]}")
for index in "${!scratch_args[@]}"; do
	if [[ "${scratch_args[$index]}" == "$fixture_apk" ]]; then
		scratch_args[$index]="$scratch_apk"
	elif [[ "${scratch_args[$index]}" == "$fixture_meta" ]]; then
		scratch_args[$index]="$scratch_meta"
	fi
done
expect_failure \
	"mixed-case APK scratch path is rejected while archive.bakery is accepted" \
	"APK contains forbidden runtime/scratch path: assets/cache/video/editor.BaK" \
	"$CHECKER" "${scratch_args[@]}"

forged_digest_meta="$fixture_artifacts/forged-digest.json"
forged_commit_meta="$fixture_artifacts/forged-commit.json"
python3 - "$fixture_meta" "$forged_digest_meta" "$forged_commit_meta" <<'PY'
import json
import sys
from pathlib import Path

source, digest_output, commit_output = map(Path, sys.argv[1:])
meta = json.loads(source.read_text(encoding="utf-8"))
forged_digest = dict(meta)
forged_digest["relevantInputDigest"] = "0" * 64
digest_output.write_text(json.dumps(forged_digest), encoding="utf-8")
forged_commit = dict(meta)
forged_commit["gitCommit"] = "0" * 40
commit_output.write_text(json.dumps(forged_commit), encoding="utf-8")
PY
expect_failure \
	"forged sidecar digest is rejected" \
	"!= signed APK provenance" \
	"$CHECKER" "${fixture_args[@]/$fixture_meta/$forged_digest_meta}"
expect_failure \
	"forged sidecar commit is rejected" \
	"!= signed APK provenance" \
	"$CHECKER" "${fixture_args[@]/$fixture_meta/$forged_commit_meta}"
expect_failure \
	"wrong trusted signer is rejected" \
	"does not match the trusted release signer" \
	"$CHECKER" "${fixture_args[@]/$(printf 'ab%.0s' {1..32})/$(printf 'cd%.0s' {1..32})}"

duel_fixture="Duel_Proof/src/main/java/com/voidscape/duelproof/Fixture.java"
git -C "$fixture_root" update-index --assume-unchanged "$duel_fixture"
printf '// dirty but hidden from status\n' >> "$fixture_root/$duel_fixture"
if [[ -n "$(git -C "$fixture_root" status --porcelain -- "$duel_fixture")" ]]; then
	echo "ERROR: assume-unchanged regression fixture is still visible to git status." >&2
	exit 1
fi
expect_failure \
	"assume-unchanged Duel Proof modification is rejected" \
	"promotion requires a clean checkout" \
	"$CHECKER" "${fixture_args[@]}"

echo "Android APK release-gate regression checks passed."
