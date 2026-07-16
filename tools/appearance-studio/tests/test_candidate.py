from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from appearance_studio.candidate import (
    CandidateError, CandidateFile, _rewrite_md5, build_candidate_outputs,
    stage_candidate, validate_candidate,
)
from appearance_studio.paths import REGISTRY_PATH, REPO_ROOT
from appearance_studio.registry import load_registry


class CandidateTest(unittest.TestCase):
    def registry(self):
        registry, findings = load_registry(REGISTRY_PATH)
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        return registry

    def test_cowboy_candidate_is_a_complete_byte_preserving_stage(self):
        with tempfile.TemporaryDirectory() as temporary:
            staging = Path(temporary) / "candidate"
            manifest = stage_candidate(self.registry(), staging)
            self.assertTrue(manifest["bytePreserving"])
            self.assertTrue(manifest["artAndArchiveBytePreserving"])
            self.assertEqual([], validate_candidate(staging / "appearance-candidate.json"))
            contract = manifest["archiveContracts"][0]
            self.assertEqual({"start": 1890, "end": 1907, "parity": True,
                              "archives": ["Client_Base/Cache/video/Authentic_Sprites.orsc",
                                           "server/conf/server/data/Authentic_Sprites.orsc"]}, contract["worn"])
            self.assertEqual(638, contract["inventoryIcon"]["spriteId"])
            self.assertEqual(2788, contract["inventoryIcon"]["archiveIndex"])
            self.assertTrue(contract["inventoryIcon"]["clientOnly"])
            self.assertFalse(contract["inventoryIcon"]["serverEntryPresent"])
            paths = {item["path"] for item in manifest["outputs"]}
            expected = {
                "Client_Base/Cache/MD5.SUM",
                "Client_Base/Cache/video/Authentic_Sprites.orsc",
                "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java",
                "Client_Base/src/com/openrsc/client/entityhandling/GeneratedAppearanceRegistry.java",
                "Client_Base/src/com/openrsc/client/entityhandling/GeneratedLookPresets.java",
                "content/custom/cowboy_hat/appearance.yaml",
                "content/custom/cowboy_hat/content.yaml",
                "server/conf/server/data/Authentic_Sprites.orsc",
                "server/conf/server/defs/ItemDefsCustom.json",
                "server/src/com/openrsc/server/appearance/GeneratedAppearanceRegistry.java",
                "server/src/com/openrsc/server/appearance/GeneratedLookPresets.java",
                "server/src/com/openrsc/server/constants/AppearanceId.java",
                "server/src/com/openrsc/server/constants/ItemId.java",
            }
            self.assertEqual(expected, paths)
            for output in manifest["outputs"]:
                relative = Path(output["path"])
                candidate = (staging / relative).read_bytes()
                production = (REPO_ROOT / relative).read_bytes()
                self.assertEqual(production, candidate, relative)
                digest = hashlib.sha256(candidate).hexdigest()
                self.assertEqual(digest, output["preimageSha256"])
                self.assertEqual(digest, output["candidateSha256"])
            saved = json.loads((staging / "appearance-candidate.json").read_text())
            self.assertEqual(manifest, saved)
            output_map = build_candidate_outputs(self.registry())
            self.assertEqual(paths, {str(path) for path in output_map})
            modes = {item["path"]: item["mode"] for item in manifest["outputs"]}
            self.assertEqual("generated-region", modes["server/conf/server/defs/ItemDefsCustom.json"])
            self.assertEqual("generated-region", modes["Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"])

    def test_refuses_nonempty_stage_and_repository_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            staging = Path(temporary)
            (staging / "owned.txt").write_text("keep")
            with self.assertRaisesRegex(CandidateError, "must be empty"):
                stage_candidate(self.registry(), staging)
        with self.assertRaisesRegex(CandidateError, "must not be the repository root"):
            stage_candidate(self.registry(), REPO_ROOT)

    def test_md5_rewrite_is_exact_and_requires_one_row(self):
        archive = b"candidate archive"
        source = b"aaaa *./other\n0000 *./video/Authentic_Sprites.orsc\n"
        expected = hashlib.md5(archive).hexdigest().encode()
        rewritten = _rewrite_md5(source, archive)
        self.assertEqual(b"aaaa *./other\n" + expected + b" *./video/Authentic_Sprites.orsc\n", rewritten)
        with self.assertRaisesRegex(CandidateError, "exactly one"):
            _rewrite_md5(b"aaaa *./other\n", archive)

    def test_noop_mismatch_is_rejected_before_any_stage_write(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            staging = Path(temporary) / "stage"
            (root / "one").parent.mkdir(parents=True)
            (root / "one").write_bytes(b"same")
            (root / "two").write_bytes(b"old")
            files = [
                CandidateFile(Path("one"), b"same", "test", "copy"),
                CandidateFile(Path("two"), b"new", "test", "generated"),
            ]
            with patch("appearance_studio.candidate.build_candidate_files", return_value=files):
                with self.assertRaisesRegex(CandidateError, "not a no-op"):
                    stage_candidate(self.registry(), staging, repo_root=root)
            self.assertFalse(staging.exists())


if __name__ == "__main__":
    unittest.main()
