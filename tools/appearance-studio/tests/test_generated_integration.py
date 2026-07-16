from __future__ import annotations

import hashlib
import io
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

import yaml

from appearance_studio.java_bridge import rendered_sources
from appearance_studio.publisher import (
    apply_candidate,
    build_candidate,
    undo_candidate,
    validate_candidate,
)
from appearance_studio.registry import load_registry


TOOL_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = TOOL_ROOT.parent.parent
FIXTURE = TOOL_ROOT / "tests/fixtures/integration/cowboy_hat.yaml"


def _zip_bytes(entries: dict[str, bytes]) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, entries[name])
    return buffer.getvalue()


def _zip_entries(payload: bytes) -> dict[str, bytes]:
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        return {name: archive.read(name) for name in archive.namelist()}


class GeneratedIntegrationAgreementTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = yaml.safe_load(FIXTURE.read_text())

    def test_cowboy_identity_agrees_across_generated_and_legacy_outputs(self):
        expected = self.fixture
        appearance = expected["appearance"]
        item = expected["item"]
        outputs = expected["outputs"]

        registry, findings = load_registry(REPO_ROOT / "content/appearance/registry.yaml")
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        entry = next(value for value in registry.entries if value.key == expected["key"])
        self.assertEqual(appearance["id"], entry.appearance_id)
        self.assertEqual(appearance["legacy_name"], entry.animation_name)
        self.assertEqual(appearance["sprite_base"], entry.sprite_base)
        self.assertEqual(appearance["frame_count"], entry.frame_count)
        self.assertEqual(appearance["paperdoll_slot"], entry.paperdoll_slot)
        self.assertEqual(item["id"], entry.item_id)

        constructor = (
            f'new Entry({appearance["id"]}, "{expected["key"]}", '
            f'new AnimationDef("{appearance["legacy_name"]}", "{appearance["category"]}", '
            f'0, 0, 0, true, false, {appearance["sprite_base"]}), '
            f'{appearance["frame_count"]}, {appearance["paperdoll_slot"]})'
        )
        for source in rendered_sources(registry).values():
            self.assertIn(constructor, source)

        server_defs = json.loads((REPO_ROOT / outputs["server_item_definitions"]).read_text())
        server_item = next(value for value in server_defs["items"] if value["id"] == item["id"])
        for field, value in (
            ("name", item["name"]),
            ("description", item["description"]),
            ("appearanceID", item["appearance_id"]),
            ("wearableID", item["wearable_id"]),
            ("wearSlot", item["wear_slot"]),
        ):
            self.assertEqual(value, server_item[field])

        item_constants = (REPO_ROOT / outputs["item_constants"]).read_text()
        appearance_constants = (REPO_ROOT / outputs["appearance_constants"]).read_text()
        self.assertRegex(item_constants, rf'\b{item["constant"]}\({item["id"]}\)')
        self.assertRegex(item_constants, rf'\bmaxCustom\s*=\s*{item["expected_max_custom"]}\s*;')
        self.assertRegex(appearance_constants, rf'\b{item["constant"]}\({appearance["id"]},\s*HAT\)')

        client_items = (REPO_ROOT / outputs["client_item_definitions"]).read_text()
        client_pattern = re.compile(
            rf'new ItemDef\("{re.escape(item["name"])}",\s*'
            rf'"{re.escape(item["description"])}".*?\b{item["inventory_sprite_id"]},\s*'
            rf'"items:{item["inventory_sprite_id"]}".*?\b{item["id"]}\)'
        )
        self.assertRegex(client_items, client_pattern)

        manifest = yaml.safe_load((REPO_ROOT / outputs["appearance_manifest"]).read_text())
        self.assertEqual(expected["key"], manifest["key"])
        self.assertEqual(appearance["id"], manifest["registry"]["appearance_id"])
        self.assertEqual(appearance["sprite_base"], manifest["registry"]["sprite_base"])
        self.assertEqual(item["id"], manifest["legacy"]["item_id"])

    def test_content_metadata_names_every_generated_target(self):
        metadata_path = REPO_ROOT / self.fixture["outputs"]["content_metadata"]
        metadata = yaml.safe_load(metadata_path.read_text())
        contract = self.fixture["content_metadata_contract"]
        for section, required in contract.items():
            self.assertTrue(
                set(required).issubset(metadata["integration"][section]),
                f"{section} is missing {sorted(set(required) - set(metadata['integration'][section]))}",
            )

    def test_copy_specific_archive_and_client_md5_contract(self):
        outputs = self.fixture["outputs"]
        archive_contract = self.fixture["archive_contract"]
        item = self.fixture["item"]
        first = archive_contract["managed_entries"]["first"]
        last = archive_contract["managed_entries"]["last"]
        reserved_first = archive_contract["reserved_but_unwritten"]["first"]
        reserved_last = archive_contract["reserved_but_unwritten"]["last"]
        with zipfile.ZipFile(REPO_ROOT / outputs["client_archive"]) as client, zipfile.ZipFile(
            REPO_ROOT / outputs["server_archive"]
        ) as server:
            for index in range(first, last + 1):
                self.assertEqual(client.read(str(index)), server.read(str(index)))
            for index in range(reserved_first, reserved_last + 1):
                self.assertNotIn(str(index), client.namelist())
                self.assertNotIn(str(index), server.namelist())
            self.assertIn(str(item["inventory_archive_entry"]), client.namelist())
            self.assertNotIn(str(item["inventory_archive_entry"]), server.namelist())

        client_archive = REPO_ROOT / outputs["client_archive"]
        client_md5 = hashlib.md5(client_archive.read_bytes()).hexdigest()
        server_md5 = hashlib.md5((REPO_ROOT / outputs["server_archive"]).read_bytes()).hexdigest()
        rows = (REPO_ROOT / outputs["client_md5"]).read_text().splitlines()
        matching = [row for row in rows if row.endswith(archive_contract["md5_row_suffix"])]
        self.assertEqual([f'{client_md5} {archive_contract["md5_row_suffix"]}'], matching)
        if server_md5 != client_md5:
            self.assertFalse(matching[0].startswith(server_md5))


class PublisherCopyRootTest(unittest.TestCase):
    def test_apply_preserves_each_archive_root_updates_client_md5_and_undoes_exactly(self):
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary) / "repo"
            bundle = Path(temporary) / "bundle"
            client_path = repo / "Client_Base/Cache/video/Authentic_Sprites.orsc"
            server_path = repo / "server/conf/server/data/Authentic_Sprites.orsc"
            md5_path = repo / "Client_Base/Cache/MD5.SUM"
            for path in (client_path, server_path, md5_path):
                path.parent.mkdir(parents=True, exist_ok=True)

            client_before = _zip_bytes({"10": b"client-only", "1890": b"old", "2788": b"icon"})
            server_before = _zip_bytes({"20": b"server-only", "1890": b"old"})
            client_path.write_bytes(client_before)
            server_path.write_bytes(server_before)
            md5_before = b"unrelated *./other\nold *./video/Authentic_Sprites.orsc\n"
            md5_path.write_bytes(md5_before)

            client_entries = _zip_entries(client_before)
            server_entries = _zip_entries(server_before)
            client_entries["1890"] = b"new-managed-frame"
            server_entries["1890"] = b"new-managed-frame"
            client_after = _zip_bytes(client_entries)
            server_after = _zip_bytes(server_entries)
            client_digest = hashlib.md5(client_after).hexdigest()
            md5_after = f"unrelated *./other\n{client_digest} *./video/Authentic_Sprites.orsc\n".encode()

            targets = {
                "Client_Base/Cache/video/Authentic_Sprites.orsc": client_after,
                "server/conf/server/data/Authentic_Sprites.orsc": server_after,
                "Client_Base/Cache/MD5.SUM": md5_after,
            }
            build_candidate(repo, targets, bundle)
            validation = validate_candidate(repo, bundle)
            self.assertTrue(validation["valid"], validation["errors"])
            apply_candidate(repo, bundle, profile="authentic")

            installed_client = _zip_entries(client_path.read_bytes())
            installed_server = _zip_entries(server_path.read_bytes())
            self.assertEqual(b"client-only", installed_client["10"])
            self.assertEqual(b"icon", installed_client["2788"])
            self.assertNotIn("20", installed_client)
            self.assertEqual(b"server-only", installed_server["20"])
            self.assertNotIn("10", installed_server)
            self.assertNotIn("2788", installed_server)
            self.assertEqual(installed_client["1890"], installed_server["1890"])
            self.assertEqual(md5_after, md5_path.read_bytes())

            undo_candidate(repo, bundle, profile="authentic")
            self.assertEqual(client_before, client_path.read_bytes())
            self.assertEqual(server_before, server_path.read_bytes())
            self.assertEqual(md5_before, md5_path.read_bytes())


if __name__ == "__main__":
    unittest.main()
