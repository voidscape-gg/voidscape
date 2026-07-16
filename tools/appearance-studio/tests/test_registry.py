from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import yaml

from appearance_studio.registry import load_registry, safe_repo_path


def payload(entries=None, tombstones=None, profile="authentic"):
    return {
        "schema": "voidscape-appearance-registry/v1",
        "profile": profile,
        "reservation_size": 27,
        "managed_namespace": {"status": "proposed", "base": 3705, "end": 3974, "reservation_count": 10, "capacity_required": 3975},
        "entries": entries or [],
        "external_reservations": [],
        "tombstones": tombstones or [],
    }


def entry(key, appearance_id, sprite_base, manifest=None):
    value = {
        "key": key,
        "state": "active",
        "kind": "hat",
        "appearance_id": appearance_id,
        "animation_name": key,
        "category": "equipment",
        "char_colour": 0,
        "blue_mask": 0,
        "gender_model": 0,
        "has_a": True,
        "has_f": False,
        "paperdoll_slot": 5,
        "sprite_base": sprite_base,
        "frame_count": 18,
    }
    if manifest is not None:
        value["manifest"] = manifest
    return value


class RegistryTest(unittest.TestCase):
    def load(self, value):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        path = root / "registry.yaml"
        path.write_text(yaml.safe_dump(value))
        return load_registry(path, repo_root=root)

    def test_duplicate_and_tombstoned_ids_are_rejected(self):
        registry, findings = self.load(payload([entry("a", 10, 100), entry("b", 10, 127)], [10]))
        self.assertIsNotNone(registry)
        self.assertIn("duplicate-appearance-id", {finding.code for finding in findings})
        self.assertIn("tombstone-reuse", {finding.code for finding in findings})

    def test_reservation_is_27_slots_even_for_18_frames(self):
        _, colliding = self.load(payload([entry("a", 10, 100), entry("b", 11, 118)]))
        self.assertIn("sprite-reservation-collision", {finding.code for finding in colliding})
        _, adjacent = self.load(payload([entry("a", 10, 100), entry("b", 11, 127)]))
        self.assertNotIn("sprite-reservation-collision", {finding.code for finding in adjacent})

    def test_unsupported_profile_fails_closed(self):
        registry, findings = self.load(payload(profile="custom-sprites"))
        self.assertIsNone(registry)
        self.assertIn("unsupported profile", findings[0].message)

    def test_aligned_managed_reservation_is_owned_not_a_collision(self):
        reserved = entry("look", 247, 3705)
        reserved.update(state="reserved", kind="look", category="player", char_colour=1,
                        gender_model=5, paperdoll_slot=0)
        _, findings = self.load(payload([reserved]))
        self.assertNotIn("managed-registry-collision", {finding.code for finding in findings})
        self.assertNotIn("managed-registry-alignment", {finding.code for finding in findings})

        misaligned = dict(reserved, sprite_base=3706)
        _, findings = self.load(payload([misaligned]))
        self.assertIn("managed-registry-alignment", {finding.code for finding in findings})

    def test_external_reservation_state_is_explicitly_runtime_aware(self):
        external = {
            "key": "archived_npc",
            "appearance_id": 246,
            "animation_name": "archivednpc",
            "sprite_base": 1917,
            "frame_count": 18,
        }
        value = payload()
        value["external_reservations"] = [external]
        registry, findings = self.load(value)
        self.assertFalse(findings)
        self.assertEqual("reserved", registry.external_reservations[0].state)

        value["external_reservations"][0]["state"] = "active"
        registry, findings = self.load(value)
        self.assertFalse(findings)
        self.assertEqual("active", registry.external_reservations[0].state)

    def test_paths_must_stay_inside_repository(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for malicious in ("../../escape.yaml", "/tmp/escape.yaml"):
                with self.assertRaises(ValueError):
                    safe_repo_path(malicious, repo_root=root)
            outside = root.parent / "outside-appearance.yaml"
            outside.write_text("outside")
            link = root / "link.yaml"
            link.symlink_to(outside)
            with self.assertRaises(ValueError):
                safe_repo_path("link.yaml", repo_root=root)


if __name__ == "__main__":
    unittest.main()
