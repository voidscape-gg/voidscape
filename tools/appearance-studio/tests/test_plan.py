from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

import yaml

from appearance_studio.plan import build_plan, canonical_json
from appearance_studio.registry import load_registry
from test_registry import entry, payload


class PlanTest(unittest.TestCase):
    def make_repo(self, entries):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        registry_path = root / "registry.yaml"
        registry_path.write_text(yaml.safe_dump(payload(entries)))
        namespaces = root / "namespaces.yaml"
        namespaces.write_text(yaml.safe_dump({"fixed_consumers": [], "runtime_capacities": {"client": 4501, "avatar_generator": 4000}}))
        client = root / "client.orsc"
        server = root / "server.orsc"
        for archive in (client, server):
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("1944", b"occupied")
                output.writestr("1971", b"occupied")
                output.writestr("3720", b"hidden")
        registry, findings = load_registry(registry_path, repo_root=root)
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        return root, registry, client, server, namespaces

    def test_plan_is_deterministic_and_read_only(self):
        args = self.make_repo([entry("b", 12, 127), entry("a", 11, 100)])
        root, registry, client, server, namespaces = args
        first = build_plan(registry, repo_root=root, client_archive=client, server_archive=server, namespaces_path=namespaces)
        second = build_plan(registry, repo_root=root, client_archive=client, server_archive=server, namespaces_path=namespaces)
        self.assertEqual(canonical_json(first), canonical_json(second))
        self.assertEqual([], first["writes"])
        self.assertEqual([], first["changes"])
        self.assertNotIn(str(root), canonical_json(first))

    def test_known_false_free_ranges_are_rejected(self):
        root, registry, client, server, namespaces = self.make_repo([])
        plan = build_plan(registry, repo_root=root, client_archive=client, server_archive=server, namespaces_path=namespaces)
        hazards = {block["base"]: block for block in plan["hazard_checks"]}
        self.assertFalse(hazards[1944]["available"])
        self.assertFalse(hazards[1971]["available"])
        self.assertEqual([1944], hazards[1944]["archive_entries"])
        self.assertEqual([1971], hazards[1971]["archive_entries"])

    def test_managed_range_rejects_an_unowned_hidden_entry(self):
        root, registry, client, server, namespaces = self.make_repo([])
        plan = build_plan(registry, repo_root=root, client_archive=client, server_archive=server, namespaces_path=namespaces)
        self.assertFalse(plan["managed_namespace"]["candidate"]["available"])
        block = next(value for value in plan["managed_namespace"]["candidate"]["blocks"] if value["base"] == 3705)
        self.assertEqual([3720], block["archive_entries"])


if __name__ == "__main__":
    unittest.main()
