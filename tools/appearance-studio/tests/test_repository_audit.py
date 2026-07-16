from __future__ import annotations

import hashlib
import unittest
import zipfile

from appearance_studio.audit import audit_registry, numeric_members
from appearance_studio.paths import CLIENT_ARCHIVE, MD5_FILE, REGISTRY_PATH, REPO_ROOT, SERVER_ARCHIVE
from appearance_studio.plan import build_plan
from appearance_studio.registry import load_registry


class RepositoryAuditTest(unittest.TestCase):
    def test_cowboy_is_byte_preserving_and_false_free_ranges_are_occupied(self):
        registry, findings = load_registry(REGISTRY_PATH)
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        audit_findings, _ = audit_registry(registry)
        errors = [finding for finding in audit_findings if finding.severity == "error"]
        self.assertFalse(errors, "\n".join(finding.message for finding in errors))
        self.assertNotIn("cowboy-avatar-mapping-missing", {finding.code for finding in audit_findings})

        client = numeric_members(CLIENT_ARCHIVE)
        server = numeric_members(SERVER_ARCHIVE)
        self.assertTrue({1944, 1971}.issubset(client))
        self.assertTrue({1944, 1971}.issubset(server))
        plan = build_plan(registry)
        self.assertTrue(all(not block["available"] for block in plan["hazard_checks"]))
        reserved = next(block for block in plan["managed_namespace"]["candidate"]["blocks"] if block["base"] == 3705)
        self.assertFalse(reserved["available"])
        self.assertEqual(["future_mullet_mustache"], reserved["registry_owners"])
        self.assertEqual([], reserved["archive_entries"])

        digest = hashlib.md5(CLIENT_ARCHIVE.read_bytes()).hexdigest()
        rows = [line for line in MD5_FILE.read_text().splitlines() if line.endswith("*./video/Authentic_Sprites.orsc")]
        self.assertEqual(1, len(rows))
        self.assertTrue(rows[0].startswith(digest + " "))


if __name__ == "__main__":
    unittest.main()
