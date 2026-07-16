from __future__ import annotations

import json
import os
from pathlib import Path
import tempfile
import unittest

from appearance_studio.publisher import (
    CandidateValidationError,
    LaterEditError,
    StaleCandidateError,
    TransactionError,
    apply_candidate,
    build_candidate,
    undo_candidate,
    validate_candidate,
)


class PublisherTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name) / "repo"
        self.root.mkdir()
        self.bundle = self.root / "tmp" / "candidate"
        (self.root / "a.txt").write_bytes(b"old-a")
        (self.root / "nested").mkdir()
        (self.root / "nested/b.txt").write_bytes(b"old-b")

    def writes(self):
        return {
            "a.txt": b"new-a",
            "nested/b.txt": b"new-b",
            "new/c.txt": b"new-c",
        }

    def assert_preimages(self):
        self.assertEqual(b"old-a", (self.root / "a.txt").read_bytes())
        self.assertEqual(b"old-b", (self.root / "nested/b.txt").read_bytes())
        self.assertFalse((self.root / "new/c.txt").exists())

    def assert_candidates(self):
        self.assertEqual(b"new-a", (self.root / "a.txt").read_bytes())
        self.assertEqual(b"new-b", (self.root / "nested/b.txt").read_bytes())
        self.assertEqual(b"new-c", (self.root / "new/c.txt").read_bytes())

    def test_build_is_deterministic_and_self_contained(self):
        first = build_candidate(self.root, self.writes(), self.bundle)
        second_bundle = self.root / "tmp" / "candidate-2"
        second = build_candidate(self.root, self.writes(), second_bundle)
        self.assertEqual(first, second)
        self.assertEqual((self.bundle / "plan.json").read_bytes(), (second_bundle / "plan.json").read_bytes())
        self.assertTrue(validate_candidate(self.root, self.bundle)["valid"])
        self.assertEqual(3, validate_candidate(self.root, self.bundle)["operations"])
        self.assertEqual([], list(self.bundle.rglob("*.tmp")))

    def test_apply_and_undo_restore_every_byte_and_new_file_state(self):
        plan = build_candidate(self.root, self.writes(), self.bundle)
        applied = apply_candidate(self.root, self.bundle, profile="authentic")
        self.assertEqual(plan["plan_id"], applied["plan_id"])
        self.assertEqual(3, applied["changed"])
        self.assert_candidates()
        self.assertTrue((self.bundle / "undo.json").is_file())

        undone = undo_candidate(self.root, self.bundle, profile="authentic")
        self.assertEqual(applied["undo_id"], undone["undo_id"])
        self.assert_preimages()

    def test_noop_is_not_rewritten_but_is_guarded(self):
        original_mode = (self.root / "a.txt").stat().st_mode
        build_candidate(self.root, {"a.txt": b"old-a"}, self.bundle)
        result = apply_candidate(self.root, self.bundle, profile="authentic")
        self.assertEqual(0, result["changed"])
        self.assertEqual(original_mode, (self.root / "a.txt").stat().st_mode)
        self.assertEqual(0, undo_candidate(self.root, self.bundle, profile="authentic")["changed"])

    def test_stale_plan_refuses_before_any_write(self):
        build_candidate(self.root, self.writes(), self.bundle)
        (self.root / "nested/b.txt").write_bytes(b"later")
        report = validate_candidate(self.root, self.bundle)
        self.assertFalse(report["valid"])
        self.assertTrue(any("nested/b.txt" in error for error in report["errors"]))
        with self.assertRaises(StaleCandidateError):
            apply_candidate(self.root, self.bundle, profile="authentic")
        self.assertEqual(b"old-a", (self.root / "a.txt").read_bytes())
        self.assertFalse((self.root / "new/c.txt").exists())

    def test_later_edit_refuses_undo_before_any_restore(self):
        build_candidate(self.root, self.writes(), self.bundle)
        apply_candidate(self.root, self.bundle, profile="authentic")
        (self.root / "nested/b.txt").write_bytes(b"later")
        with self.assertRaises(LaterEditError):
            undo_candidate(self.root, self.bundle, profile="authentic")
        self.assertEqual(b"new-a", (self.root / "a.txt").read_bytes())
        self.assertEqual(b"later", (self.root / "nested/b.txt").read_bytes())
        self.assertEqual(b"new-c", (self.root / "new/c.txt").read_bytes())

    def test_explicit_force_undo_discards_later_edit(self):
        build_candidate(self.root, self.writes(), self.bundle)
        apply_candidate(self.root, self.bundle, profile="authentic")
        (self.root / "nested/b.txt").write_bytes(b"later")
        result = undo_candidate(self.root, self.bundle, profile="authentic", force=True)
        self.assertTrue(result["forced"])
        self.assert_preimages()

    def test_explicit_force_restores_later_edit_of_noop_target(self):
        build_candidate(self.root, {"a.txt": b"old-a"}, self.bundle)
        apply_candidate(self.root, self.bundle, profile="authentic")
        (self.root / "a.txt").write_bytes(b"later")
        result = undo_candidate(self.root, self.bundle, profile="authentic", force=True)
        self.assertTrue(result["forced"])
        self.assertEqual(1, result["changed"])
        self.assertEqual(b"old-a", (self.root / "a.txt").read_bytes())

    def test_forced_undo_failure_restores_later_edits(self):
        build_candidate(self.root, self.writes(), self.bundle)
        apply_candidate(self.root, self.bundle, profile="authentic")
        (self.root / "a.txt").write_bytes(b"later-a")
        (self.root / "nested/b.txt").write_bytes(b"later-b")

        def fail(stage, _target, index):
            if stage == "undo" and index == 1:
                raise OSError("injected forced undo failure")

        with self.assertRaises(TransactionError):
            undo_candidate(
                self.root,
                self.bundle,
                profile="authentic",
                force=True,
                fault_injector=fail,
            )
        self.assertEqual(b"later-a", (self.root / "a.txt").read_bytes())
        self.assertEqual(b"later-b", (self.root / "nested/b.txt").read_bytes())
        self.assertEqual(b"new-c", (self.root / "new/c.txt").read_bytes())

    def test_apply_failure_at_each_operation_rolls_back(self):
        for failure_index in range(3):
            with self.subTest(failure_index=failure_index):
                bundle = self.root / "tmp" / f"failure-{failure_index}"
                build_candidate(self.root, self.writes(), bundle)

                def fail(stage, _target, index):
                    if stage == "apply" and index == failure_index:
                        raise OSError("injected apply failure")

                with self.assertRaises(TransactionError):
                    apply_candidate(self.root, bundle, profile="authentic", fault_injector=fail)
                self.assert_preimages()

    def test_receipt_failure_rolls_back(self):
        build_candidate(self.root, self.writes(), self.bundle)

        def fail(stage, _target, _index):
            if stage == "receipt":
                raise OSError("injected receipt failure")

        with self.assertRaises(TransactionError):
            apply_candidate(self.root, self.bundle, profile="authentic", fault_injector=fail)
        self.assert_preimages()
        self.assertFalse((self.bundle / "undo.json").exists())

    def test_undo_failure_at_each_operation_rolls_forward(self):
        for failure_index in range(3):
            with self.subTest(failure_index=failure_index):
                bundle = self.root / "tmp" / f"undo-failure-{failure_index}"
                build_candidate(self.root, self.writes(), bundle)
                apply_candidate(self.root, bundle, profile="authentic")

                def fail(stage, _target, index):
                    if stage == "undo" and index == failure_index:
                        raise OSError("injected undo failure")

                with self.assertRaises(TransactionError):
                    undo_candidate(self.root, bundle, profile="authentic", fault_injector=fail)
                self.assert_candidates()
                undo_candidate(self.root, bundle, profile="authentic")
                self.assert_preimages()

    def test_tampered_plan_and_payload_are_rejected(self):
        plan = build_candidate(self.root, self.writes(), self.bundle)
        plan_path = self.bundle / "plan.json"
        tampered = json.loads(plan_path.read_text())
        tampered["operations"][0]["target"] = "different.txt"
        plan_path.write_text(json.dumps(tampered))
        with self.assertRaises(CandidateValidationError):
            apply_candidate(self.root, self.bundle, profile="authentic")

        clean_bundle = self.root / "tmp" / "tampered-payload"
        clean_plan = build_candidate(self.root, self.writes(), clean_bundle)
        digest = clean_plan["operations"][0]["candidate"]["sha256"]
        (clean_bundle / "payloads" / digest).write_bytes(b"tampered")
        self.assertFalse(validate_candidate(self.root, clean_bundle)["valid"])
        with self.assertRaises(CandidateValidationError):
            apply_candidate(self.root, clean_bundle, profile="authentic")

    def test_apply_requires_matching_supported_profile(self):
        build_candidate(self.root, self.writes(), self.bundle)
        with self.assertRaises(CandidateValidationError):
            apply_candidate(self.root, self.bundle, profile="custom-sprites")
        self.assert_preimages()

    def test_mode_change_makes_plan_stale(self):
        build_candidate(self.root, self.writes(), self.bundle)
        os.chmod(self.root / "a.txt", 0o600)
        with self.assertRaises(StaleCandidateError):
            apply_candidate(self.root, self.bundle, profile="authentic")

    def test_unsafe_targets_and_symlinks_are_rejected(self):
        for target in ("../escape", "/absolute", "a/../b", "./a"):
            with self.subTest(target=target), self.assertRaises(CandidateValidationError):
                build_candidate(self.root, {target: b"x"}, self.root / "tmp" / target.replace("/", "_"))
        if hasattr(os, "symlink"):
            (self.root / "link").symlink_to(self.root / "a.txt")
            with self.assertRaises(CandidateValidationError):
                build_candidate(self.root, {"link": b"x"}, self.root / "tmp" / "symlink")


if __name__ == "__main__":
    unittest.main()
