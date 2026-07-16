#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKER="$ROOT/scripts/check-android-play-release.sh"
PROVENANCE_TOOL="$ROOT/scripts/android-provenance.py"
TRUSTED_SIGNER="$(printf 'ab%.0s' {1..32})"

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
	echo "ERROR: Play release checker and provenance helper must exist." >&2
	exit 1
fi

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-aab-gate-test.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT
fixture_root="$tmp_dir/source"
fixture_artifacts="$tmp_dir/artifacts"
mkdir -p \
	"$fixture_root/Client_Base/src/orsc" \
	"$fixture_root/Client_Base/Cache" \
	"$fixture_root/Duel_Proof/src/main/java/com/voidscape/duelproof" \
	"$fixture_root/Android_Client/Open RSC Android Client" \
	"$fixture_root/scripts" \
	"$fixture_artifacts/base/assets"

python3 - "$fixture_root" "$fixture_artifacts" "$PROVENANCE_TOOL" "$TRUSTED_SIGNER" <<'PY'
import shutil
import stat
import sys
from pathlib import Path

root = Path(sys.argv[1])
artifacts = Path(sys.argv[2])
tool = Path(sys.argv[3])
signer = sys.argv[4]

(root / "Client_Base/src/orsc/Config.java").write_text(
    "public final class Config { public static final int CLIENT_VERSION = 10132; }\n",
    encoding="utf-8",
)
(root / "Client_Base/Cache/fixture.bin").write_bytes(b"fixture-cache\n")
(root / "Duel_Proof/src/main/java/com/voidscape/duelproof/Fixture.java").write_text(
    "package com.voidscape.duelproof; public final class Fixture {}\n",
    encoding="utf-8",
)
(root / "Android_Client/fixture.txt").write_text(
    "fixture android input\n", encoding="utf-8"
)
(root / "Android_Client/Open RSC Android Client/build.gradle").write_text(
    '''android {\n'''
    '''  defaultConfig {\n'''
    '''    applicationId "com.voidscape.gg"\n'''
    '''    minSdk 23\n'''
    '''    targetSdk 35\n'''
    '''    versionCode 9\n'''
    '''    versionName "1.0.8"\n'''
    '''  }\n'''
    '''}\n''',
    encoding="utf-8",
)
(root / "scripts/build-android.sh").write_text("#!/bin/sh\n# fixture\n", encoding="utf-8")
shutil.copyfile(tool, root / "scripts/android-provenance.py")

jarsigner = artifacts / "jarsigner"
jarsigner.write_text(
    "#!/bin/sh\n"
    "echo 'sm 123 Thu Jan 01 01:01:02 UTC 2026 base/assets/voidscape-provenance.json'\n"
    "echo 'sm 12 Thu Jan 01 01:01:02 UTC 2026 base/assets/release-payload.txt'\n"
    "echo 'jar verified.'\n",
    encoding="utf-8",
)
unsigned_jarsigner = artifacts / "unsigned-jarsigner"
unsigned_jarsigner.write_text(
    "#!/bin/sh\n"
    "echo 'sm 123 Thu Jan 01 01:01:02 UTC 2026 base/assets/voidscape-provenance.json'\n"
    "echo '    ? 12 Thu Jan 01 01:01:02 UTC 2026 base/assets/release-payload.txt'\n"
    "echo 'jar verified.'\n",
    encoding="utf-8",
)
keytool = artifacts / "keytool"
fingerprint = ":".join(signer[index:index + 2] for index in range(0, len(signer), 2))
keytool.write_text(
    "#!/bin/sh\n"
    "echo 'Signer #1:'\n"
    f"echo ' SHA256: {fingerprint.upper()}'\n",
    encoding="utf-8",
)
bundletool = artifacts / "bundletool"
bundletool.write_text(
    "#!/bin/sh\n"
    "state=\"$0.state\"\n"
    "case \"${1:-}\" in\n"
    "validate)\n"
    "  [ \"${2#--bundle=}\" != \"${2:-}\" ] || exit 2\n"
    "  : > \"$state\"\n"
    "  exit 0\n"
    "  ;;\n"
    "dump)\n"
    "  [ \"${2:-}\" = manifest ] || exit 2\n"
    "  [ -f \"$state\" ] || { echo 'validate was not called before dump' >&2; exit 3; }\n"
    "  rm -f \"$state\"\n"
    "  ;;\n"
    "*) exit 2 ;;\n"
    "esac\n"
    "cat <<'EOF'\n"
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
    'package="com.voidscape.gg" android:versionCode="9" android:versionName="1.0.8">\n'
    '  <uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35" />\n'
    '  <application android:debuggable="false" />\n'
    "</manifest>\n"
    "EOF\n",
    encoding="utf-8",
)
invalid_bundletool = artifacts / "invalid-bundletool"
invalid_bundletool.write_text(
    "#!/bin/sh\n"
    "if [ \"${1:-}\" = validate ]; then\n"
    "  echo 'fixture bundle structure is invalid' >&2\n"
    "  exit 1\n"
    "fi\n"
    "echo 'dump must not run after failed validation' >&2\n"
    "exit 2\n",
    encoding="utf-8",
)
for path in (jarsigner, unsigned_jarsigner, keytool, bundletool, invalid_bundletool):
    path.chmod(path.stat().st_mode | stat.S_IXUSR)
PY

git -C "$fixture_root" init -q
git -C "$fixture_root" config user.name "Voidscape Release Gate"
git -C "$fixture_root" config user.email "release-gate@voidscape.invalid"
git -C "$fixture_root" add .
git -C "$fixture_root" commit -qm "Create Play release fixture"

fixture_provenance="$fixture_artifacts/base/assets/voidscape-provenance.json"
python3 "$PROVENANCE_TOOL" write \
	--repo-root "$fixture_root" \
	--output "$fixture_provenance" \
	--dirty-release-override false >/dev/null
fixture_aab="$fixture_artifacts/release-fixture.aab"
fixture_meta="$fixture_aab.json"
python3 - "$fixture_provenance" "$fixture_aab" "$fixture_meta" <<'PY'
import hashlib
import json
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

provenance_path, aab_path, meta_path = map(Path, sys.argv[1:])
with zipfile.ZipFile(aab_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(provenance_path, "base/assets/voidscape-provenance.json")
    archive.writestr("base/assets/release-payload.txt", "payload data\n")
provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
metadata = {
    "clientVersion": 10132,
    "sha256": hashlib.sha256(aab_path.read_bytes()).hexdigest(),
    "sizeBytes": aab_path.stat().st_size,
    "builtAt": datetime.now(timezone.utc).isoformat(),
    "buildType": "release",
    "artifactType": "android-app-bundle",
    "applicationId": "com.voidscape.gg",
    "versionCode": 9,
    "versionName": "1.0.8",
    "minSdk": 23,
    "targetSdk": 35,
}
metadata.update(provenance)
meta_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY

fixture_args=(
	--aab "$fixture_aab"
	--source-config "$fixture_root/Client_Base/src/orsc/Config.java"
	--android-build-config "$fixture_root/Android_Client/Open RSC Android Client/build.gradle"
	--provenance-root "$fixture_root"
	--server-client-version 10132
	--current-play-version-code 8
	--expected-signer-sha256 "$TRUSTED_SIGNER"
	--jarsigner "$fixture_artifacts/jarsigner"
	--keytool "$fixture_artifacts/keytool"
	--bundletool "$fixture_artifacts/bundletool"
)

"$CHECKER" "${fixture_args[@]}" --meta "$fixture_meta" >/dev/null
echo "PASS: signed, provenance-bound versionCode 9 fixture is accepted above Play code 8"

equal_version_args=("${fixture_args[@]}")
for index in "${!equal_version_args[@]}"; do
	if [[ "${equal_version_args[$index]}" == "8" \
		&& "$index" -gt 0 \
		&& "${equal_version_args[$((index - 1))]}" == "--current-play-version-code" ]]; then
		equal_version_args[$index]="9"
	fi
done
expect_failure \
	"versionCode equal to Play's highest code is rejected" \
	"AAB versionCode 9 must be greater than current Play highest 9" \
	"$CHECKER" "${equal_version_args[@]}" --meta "$fixture_meta"

wrong_signer_args=("${fixture_args[@]}")
for index in "${!wrong_signer_args[@]}"; do
	if [[ "${wrong_signer_args[$index]}" == "$TRUSTED_SIGNER" ]]; then
		wrong_signer_args[$index]="$(printf 'cd%.0s' {1..32})"
	fi
done
expect_failure \
	"wrong upload signer is rejected" \
	"does not match the trusted upload signer" \
	"$CHECKER" "${wrong_signer_args[@]}" --meta "$fixture_meta"

unsigned_args=("${fixture_args[@]}")
for index in "${!unsigned_args[@]}"; do
	if [[ "${unsigned_args[$index]}" == "$fixture_artifacts/jarsigner" ]]; then
		unsigned_args[$index]="$fixture_artifacts/unsigned-jarsigner"
	fi
done
expect_failure \
	"signed provenance cannot hide another unsigned payload entry" \
	"non-signature payload entries not covered by the verified JAR signature" \
	"$CHECKER" "${unsigned_args[@]}" --meta "$fixture_meta"

invalid_structure_args=("${fixture_args[@]}")
for index in "${!invalid_structure_args[@]}"; do
	if [[ "${invalid_structure_args[$index]}" == "$fixture_artifacts/bundletool" ]]; then
		invalid_structure_args[$index]="$fixture_artifacts/invalid-bundletool"
	fi
done
expect_failure \
	"bundletool structural validation failure is rejected before manifest inspection" \
	"bundletool validation failed for the AAB" \
	"$CHECKER" "${invalid_structure_args[@]}" --meta "$fixture_meta"

malformed_meta="$fixture_artifacts/malformed.json"
printf '{not valid json\n' > "$malformed_meta"
expect_failure \
	"malformed metadata is rejected" \
	"could not parse AAB metadata" \
	"$CHECKER" "${fixture_args[@]}" --meta "$malformed_meta"

forged_version_meta="$fixture_artifacts/forged-version.json"
python3 - "$fixture_meta" "$forged_version_meta" <<'PY'
import json
import sys
from pathlib import Path

source, output = map(Path, sys.argv[1:])
metadata = json.loads(source.read_text(encoding="utf-8"))
metadata["versionCode"] = 10
output.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY
expect_failure \
	"sidecar cannot forge a higher AAB versionCode" \
	"AAB manifest versionCode 9 != metadata versionCode 10" \
	"$CHECKER" "${fixture_args[@]}" --meta "$forged_version_meta"

python3 - "$fixture_aab" "$fixture_meta" "$fixture_artifacts" <<'PY'
import hashlib
import json
import sys
import zipfile
from pathlib import Path

source_aab, source_meta, output = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
metadata = json.loads(source_meta.read_text(encoding="utf-8"))
with zipfile.ZipFile(source_aab) as archive:
    provenance = json.loads(
        archive.read("base/assets/voidscape-provenance.json").decode("utf-8")
    )


def variant(name, changes):
    aab = output / f"{name}.aab"
    embedded = dict(provenance)
    embedded.update(changes)
    with zipfile.ZipFile(aab, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "base/assets/voidscape-provenance.json",
            json.dumps(embedded, sort_keys=True) + "\n",
        )
        archive.writestr("base/assets/release-payload.txt", "payload data\n")
    sidecar = dict(metadata)
    sidecar.update(changes)
    sidecar["sha256"] = hashlib.sha256(aab.read_bytes()).hexdigest()
    sidecar["sizeBytes"] = aab.stat().st_size
    aab.with_suffix(".json").write_text(
        json.dumps(sidecar, indent=2) + "\n", encoding="utf-8"
    )


variant("dirty-input", {"relevantInputDirty": True})
variant("dirty-override", {"dirtyReleaseOverride": True})
variant("schema-two", {"metadataSchemaVersion": 2})
variant("short-commit", {"gitCommit": "abc123"})

missing = output / "missing-provenance.aab"
with zipfile.ZipFile(missing, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/assets/release-payload.txt", "payload data\n")
missing_meta = dict(metadata)
missing_meta["sha256"] = hashlib.sha256(missing.read_bytes()).hexdigest()
missing_meta["sizeBytes"] = missing.stat().st_size
missing.with_suffix(".json").write_text(
    json.dumps(missing_meta, indent=2) + "\n", encoding="utf-8"
)

forged = dict(metadata)
forged["relevantInputDigest"] = "0" * 64
(output / "forged-digest.json").write_text(
    json.dumps(forged, indent=2) + "\n", encoding="utf-8"
)
PY

expect_variant_failure() {
	local label="$1"
	local expected_text="$2"
	local variant_name="$3"
	local variant_aab="$fixture_artifacts/$variant_name.aab"
	local variant_meta="$fixture_artifacts/$variant_name.json"
	local variant_args=("${fixture_args[@]}")
	local index
	for index in "${!variant_args[@]}"; do
		if [[ "${variant_args[$index]}" == "$fixture_aab" ]]; then
			variant_args[$index]="$variant_aab"
		fi
	done
	expect_failure "$label" "$expected_text" \
		"$CHECKER" "${variant_args[@]}" --meta "$variant_meta"
}

expect_variant_failure \
	"relevantInputDirty provenance is rejected" \
	"relevantInputDirty must be false for Play promotion" \
	"dirty-input"
expect_variant_failure \
	"dirtyReleaseOverride provenance is rejected" \
	"dirtyReleaseOverride must be false for Play promotion" \
	"dirty-override"
expect_variant_failure \
	"non-v3 provenance is rejected" \
	"metadataSchemaVersion must be exactly 3" \
	"schema-two"
expect_variant_failure \
	"short provenance commit is rejected" \
	"gitCommit must be a full 40-hex commit id" \
	"short-commit"
expect_variant_failure \
	"missing embedded provenance is rejected" \
	"signed AAB provenance asset is missing or invalid" \
	"missing-provenance"
expect_failure \
	"forged sidecar digest is rejected" \
	"!= signed AAB provenance" \
	"$CHECKER" "${fixture_args[@]}" --meta "$fixture_artifacts/forged-digest.json"

expect_failure \
	"deprecated inclusive minimum option is rejected" \
	"--min-version-code is no longer accepted" \
	"$CHECKER" --min-version-code 9

duel_fixture="Duel_Proof/src/main/java/com/voidscape/duelproof/Fixture.java"
git -C "$fixture_root" update-index --assume-unchanged "$duel_fixture"
printf '// dirty but hidden from status\n' >> "$fixture_root/$duel_fixture"
if [[ -n "$(git -C "$fixture_root" status --porcelain -- "$duel_fixture")" ]]; then
	echo "ERROR: assume-unchanged regression fixture is still visible to git status." >&2
	exit 1
fi
expect_failure \
	"assume-unchanged Duel Proof modification is rejected" \
	"Android/shared relevant inputs are dirty" \
	"$CHECKER" "${fixture_args[@]}" --meta "$fixture_meta"

echo "Android Play AAB release-gate regression checks passed."
