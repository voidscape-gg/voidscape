from __future__ import annotations

from collections import deque
from dataclasses import replace
import unittest

from PIL import Image

from appearance_studio.compiler import CompiledFrame, CompileResult
from appearance_studio.geometry import derive_sprite
from appearance_studio.look import load_authoring_layer, load_locked_base_frames, load_look_manifest
from appearance_studio.paths import REPO_ROOT
from appearance_studio.semantic_qa import (
    MULLET_THRESHOLDS, _meets_primary_share, require_semantic_report, validate_composed_look,
    validate_look_conflicts, validate_semantic_layer,
)
from appearance_studio.template import load_template


TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"
MULLET = REPO_ROOT / "content/appearance/proposals/future_mullet.yaml"
MUSTACHE = REPO_ROOT / "content/appearance/proposals/future_mustache.yaml"
LOOK = REPO_ROOT / "content/appearance/proposals/future_mullet_mustache.yaml"


def mask_points(reference):
    if reference is None:
        return set()
    with Image.open(reference.path) as image:
        return {(x, y) for y in range(image.height) for x in range(image.width) if image.getpixel((x, y))}


def shortest_path(allowed, starts, targets):
    pending = deque(sorted(starts, key=lambda point: (point[1], point[0])))
    previous = {point: None for point in pending}
    end = None
    while pending:
        point = pending.popleft()
        if point in targets:
            end = point
            break
        x, y = point
        for neighbour in (
            (x - 1, y - 1), (x, y - 1), (x + 1, y - 1),
            (x - 1, y), (x + 1, y),
            (x - 1, y + 1), (x, y + 1), (x + 1, y + 1),
        ):
            if neighbour in allowed and neighbour not in previous:
                previous[neighbour] = point
                pending.append(neighbour)
    if end is None:
        raise AssertionError("semantic fixture masks have no connected attachment path")
    path = set()
    while end is not None:
        path.add(end); end = previous[end]
    return path


def valid_master_points(template, semantic_profile):
    output = {}
    for master, profile in template.pose_profiles.items():
        if semantic_profile == "mustache" and profile.landmarks["upper_lip"] is None:
            output[master] = set(); continue
        if semantic_profile == "mullet":
            allowed_role, attachment_role = "hair_allowed", "scalp_attachment"
            forbidden_roles = ("face_clearance", "protected_anatomy", "neck_clearance")
        else:
            allowed_role, attachment_role = "facial_hair_allowed", "upper_lip_attachment"
            forbidden_roles = ("protected_anatomy",)
        allowed = mask_points(profile.masks[allowed_role])
        forbidden = set().union(*(mask_points(profile.masks[role]) for role in forbidden_roles))
        valid = allowed - forbidden
        attachment = mask_points(profile.masks[attachment_role]) & valid
        if semantic_profile == "mullet":
            threshold = MULLET_THRESHOLDS[master]
            selected = set()
            scalp_targets = sorted(attachment, key=lambda point: (point[1], point[0]))[:threshold["scalp"]]
            selected.add(scalp_targets[0])
            for target in scalp_targets[1:]:
                selected |= shortest_path(valid, selected, {target})
            if profile.masks["nape_tail"] is not None:
                nape = mask_points(profile.masks["nape_tail"]) & valid
                relative = profile.landmarks["nape"]
                spec = next(frame for frame in template.frames if frame.master == master)
                nape_y = spec.crown[1] + relative[1]
                deepest = max((point for point in nape if point[1] >= nape_y),
                              key=lambda point: (point[1], -point[0]))
                nape_targets = sorted(nape, key=lambda point: (
                    abs(point[0] - deepest[0]) + abs(point[1] - deepest[1]), point[1], point[0]
                ))[:threshold["nape"]]
                if deepest not in nape_targets:
                    nape_targets[-1] = deepest
                for target in nape_targets:
                    selected |= shortest_path(valid, selected, {target})
            pending = deque(sorted(selected, key=lambda point: (point[1], point[0])))
            while len(selected) < threshold["opaque"][0] and pending:
                x, y = pending.popleft()
                for neighbour in (
                    (x - 1, y - 1), (x, y - 1), (x + 1, y - 1),
                    (x - 1, y), (x + 1, y),
                    (x - 1, y + 1), (x, y + 1), (x + 1, y + 1),
                ):
                    if neighbour in valid and neighbour not in selected:
                        selected.add(neighbour); pending.append(neighbour)
                        if len(selected) >= threshold["opaque"][0]:
                            break
            output[master] = selected
        elif semantic_profile == "mustache":
            output[master] = attachment
        else:
            output[master] = {min(attachment, key=lambda point: (point[1], point[0]))}
    return output


def compile_points(template, points_by_master):
    source = {}
    frames = []
    for spec in template.frames:
        source.setdefault(spec.master, spec)
        first = source[spec.master]
        dx, dy = spec.crown[0] - first.crown[0], spec.crown[1] - first.crown[1]
        canvas = Image.new("RGBA", spec.size, (0, 0, 0, 0))
        for x, y in points_by_master[spec.master]:
            canvas.putpixel((x + dx, y + dy), (132, 132, 132, 255))
        frames.append(CompiledFrame(spec.offset, spec.master, canvas, derive_sprite(canvas) if canvas.getbbox() else None))
    return CompileResult(tuple(frames), ())


def mutate_master(result, template, master, mutate):
    source = next(spec for spec in template.frames if spec.master == master)
    frames = list(result.frames)
    for spec in (item for item in template.frames if item.master == master):
        dx, dy = spec.crown[0] - source.crown[0], spec.crown[1] - source.crown[1]
        normalized = {(x - dx, y - dy) for x, y in {
            (x, y) for y in range(frames[spec.offset].canvas.height)
            for x in range(frames[spec.offset].canvas.width)
            if frames[spec.offset].canvas.getpixel((x, y))[3]
        }}
        changed = mutate(set(normalized))
        canvas = Image.new("RGBA", spec.size, (0, 0, 0, 0))
        for x, y in changed:
            canvas.putpixel((x + dx, y + dy), (132, 132, 132, 255))
        frames[spec.offset] = CompiledFrame(spec.offset, master, canvas, derive_sprite(canvas) if canvas.getbbox() else None)
    return CompileResult(tuple(frames), result.findings)


class SemanticQaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = load_template(TEMPLATE)
        cls.mullet = load_authoring_layer(MULLET)
        cls.mustache = load_authoring_layer(MUSTACHE)
        cls.look = load_look_manifest(LOOK)
        cls.valid_hair_points = valid_master_points(cls.template, "mullet")
        cls.valid_mustache_points = valid_master_points(cls.template, "mustache")
        cls.hair = compile_points(cls.template, cls.valid_hair_points)
        cls.facial = compile_points(cls.template, cls.valid_mustache_points)
        cls.base = load_locked_base_frames(cls.template)

    def codes(self, report):
        return {finding.code for finding in report.findings}

    def test_positive_semantic_layers_are_deterministic_versioned_and_valid(self):
        hair = validate_semantic_layer(self.hair, self.mullet, self.template)
        facial = validate_semantic_layer(self.facial, self.mustache, self.template)
        self.assertTrue(hair.valid, hair.to_dict())
        self.assertTrue(facial.valid, facial.to_dict())
        self.assertEqual(hair.to_dict(), validate_semantic_layer(self.hair, self.mullet, self.template).to_dict())
        self.assertEqual("voidscape-semantic-art-qa/v2", hair.to_dict()["schema"])
        self.assertEqual("voidscape-rigid-head-semantics/v1", hair.to_dict()["contract"])
        self.assertEqual("mullet", hair.to_dict()["semanticProfile"])
        self.assertEqual((False, True, False), (
            hair.to_dict()["shipping"], hair.to_dict()["automatedContractOnly"],
            hair.to_dict()["humanVisualApproval"],
        ))
        require_semantic_report(hair.to_dict())
        with self.assertRaisesRegex(ValueError, "requires schema.*v2"):
            require_semantic_report({**hair.to_dict(), "schema": "voidscape-appearance-art-qa/v1"})

    def test_all_pose_masks_have_hard_rule_capacity_and_corridors(self):
        for master, profile in self.template.pose_profiles.items():
            valid = mask_points(profile.masks["hair_allowed"]) - mask_points(profile.masks["face_clearance"]) \
                - mask_points(profile.masks["protected_anatomy"]) - mask_points(profile.masks["neck_clearance"])
            threshold = MULLET_THRESHOLDS[master]
            self.assertGreaterEqual(len(valid), threshold["opaque"][0], master)
            scalp = valid & mask_points(profile.masks["scalp_attachment"])
            self.assertGreaterEqual(len(scalp), threshold["scalp"], master)
            if profile.masks["nape_tail"] is not None:
                nape = valid & mask_points(profile.masks["nape_tail"])
                self.assertGreaterEqual(len(nape), threshold["nape"], master)
                self.assertTrue(shortest_path(valid, scalp, nape), master)

    def test_detached_blob_and_face_covering_hair_fail(self):
        profile = self.template.pose_profiles["north"]
        allowed = mask_points(profile.masks["hair_allowed"])
        base = self.valid_hair_points["north"]
        detached = min((point for point in allowed if all(
            abs(point[0] - x) + abs(point[1] - y) > 1 for x, y in base
        )), key=lambda point: (point[1], point[0]))
        result = mutate_master(self.hair, self.template, "north", lambda points: points | {detached})
        self.assertIn("mullet-debris", self.codes(validate_semantic_layer(
            result, self.mullet, self.template
        )))

        clearance = mask_points(profile.masks["face_clearance"])
        face = min(clearance, key=lambda point: (point[1], point[0]))
        result = mutate_master(self.hair, self.template, "north", lambda points: points | {face})
        self.assertIn("forbidden-overlap", self.codes(validate_semantic_layer(
            result, self.mullet, self.template
        )))

    def test_helmet_like_hair_and_missing_nape_fail(self):
        profile = self.template.pose_profiles["west"]
        valid = mask_points(profile.masks["hair_allowed"]) - mask_points(profile.masks["protected_anatomy"]) \
            - mask_points(profile.masks["neck_clearance"])
        scalp = mask_points(profile.masks["scalp_attachment"]) & valid
        helmet = {min(scalp, key=lambda point: (point[1], point[0]))}
        result = mutate_master(self.hair, self.template, "west", lambda _: helmet)
        codes = self.codes(validate_semantic_layer(result, self.mullet, self.template))
        self.assertIn("mullet-nape-missing", codes)
        self.assertIn("mullet-helmet-like", codes)

    def test_detached_deep_pixel_cannot_cheat_primary_nape_depth(self):
        profile = self.template.pose_profiles["west"]
        valid = mask_points(profile.masks["hair_allowed"]) - mask_points(profile.masks["protected_anatomy"]) \
            - mask_points(profile.masks["face_clearance"]) - mask_points(profile.masks["neck_clearance"])
        nape = valid & mask_points(profile.masks["nape_tail"])
        outside_nape = valid - nape
        scalp = outside_nape & mask_points(profile.masks["scalp_attachment"])
        spec = next(frame for frame in self.template.frames if frame.master == "west")
        nape_y = spec.crown[1] + profile.landmarks["nape"][1]
        deep_targets = {point for point in outside_nape if point[1] >= nape_y}
        tendril = shortest_path(outside_nape, scalp, deep_targets)
        result = mutate_master(self.hair, self.template, "west", lambda _: tendril)
        codes = self.codes(validate_semantic_layer(result, self.mullet, self.template))
        self.assertIn("mullet-nape-missing", codes)
        self.assertIn("mullet-nape-depth", codes)

    def test_wrong_side_profile_mustache_and_hidden_rear_fail(self):
        profile = self.template.pose_profiles["west"]
        allowed = mask_points(profile.masks["facial_hair_allowed"]) - mask_points(profile.masks["protected_anatomy"])
        lip = next(spec for spec in self.template.frames if spec.master == "west").crown
        lip_x = lip[0] + profile.landmarks["upper_lip"][0]
        wrong = {min(allowed, key=lambda point: (point[0], point[1]))}
        self.assertLess(next(iter(wrong))[0], lip_x)
        result = mutate_master(self.facial, self.template, "west", lambda _: wrong)
        codes = self.codes(validate_semantic_layer(result, self.mustache, self.template))
        self.assertTrue({"mustache-off-center", "mustache-wrong-profile-side"} & codes)

        result = mutate_master(self.facial, self.template, "south", lambda _: {(32, 20)})
        self.assertIn("hidden-master-visible", self.codes(validate_semantic_layer(
            result, self.mustache, self.template
        )))

    def test_mustache_is_separated_from_mouth_and_chin_regions(self):
        profile = self.template.pose_profiles["north"]
        spec = next(frame for frame in self.template.frames if frame.master == "north")
        chin = spec.crown[0] + profile.landmarks["upper_lip"][0], spec.crown[1] + profile.landmarks["chin"][1]
        result = mutate_master(self.facial, self.template, "north", lambda points: points | {chin})
        codes = self.codes(validate_semantic_layer(result, self.mustache, self.template))
        self.assertIn("mustache-chin-overlap", codes)
        self.assertIn("mustache-envelope", codes)

    def test_singleton_and_one_sided_mustaches_fail_topology(self):
        profile = self.template.pose_profiles["north"]
        spec = next(frame for frame in self.template.frames if frame.master == "north")
        lip_x = spec.crown[0] + profile.landmarks["upper_lip"][0]
        attachment = self.valid_mustache_points["north"]
        singleton = {min(attachment, key=lambda point: (abs(point[0] - lip_x), point[1], point[0]))}
        result = mutate_master(self.facial, self.template, "north", lambda _: singleton)
        self.assertIn("mustache-lobe-too-small", self.codes(validate_semantic_layer(
            result, self.mustache, self.template
        )))
        one_side = {point for point in attachment if point[0] < lip_x}
        one_side.add(min((point for point in attachment if point[0] > lip_x),
                         key=lambda point: (point[1], point[0])))
        result = mutate_master(self.facial, self.template, "north", lambda _: one_side)
        self.assertIn("mustache-both-sides", self.codes(validate_semantic_layer(
            result, self.mustache, self.template
        )))

    def test_phase_flicker_and_runtime_sidecar_mismatch_fail(self):
        frames = list(self.hair.frames)
        changed = frames[1].canvas.copy(); changed.putpixel((0, 0), (132, 132, 132, 255))
        frames[1] = replace(frames[1], canvas=changed, sprite=derive_sprite(changed))
        report = validate_semantic_layer(CompileResult(tuple(frames), ()), self.mullet, self.template)
        self.assertIn("propagation-flicker", self.codes(report))

        frames = list(self.hair.frames)
        frames[0] = replace(frames[0], sprite=replace(frames[0].sprite, sidecar={**frames[0].sprite.sidecar, "xShift": 999}))
        report = validate_semantic_layer(CompileResult(tuple(frames), ()), self.mullet, self.template)
        self.assertIn("stored-sidecar-mismatch", self.codes(report))

    def test_cross_layer_collision_fails(self):
        positive = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        )
        self.assertTrue(positive.valid, positive.to_dict())
        self.assertEqual(18, len(positive.metrics))
        self.assertEqual(30, len(positive.runtime_metrics))
        collision = next(iter(self.valid_mustache_points["north"]))
        hair = mutate_master(self.hair, self.template, "north", lambda points: points | {collision})
        report = validate_look_conflicts({"future_mullet": hair, "future_mustache": self.facial},
                                         self.look, self.template)
        self.assertFalse(report.valid)
        self.assertIn("cross-layer-collision", self.codes(report))

    def test_composed_look_rejects_any_mutated_locked_base_frame(self):
        base = list(self.base)
        base[0] = base[0].copy()
        base[0].putpixel((0, 0), (1, 2, 3, 255))
        with self.assertRaisesRegex(ValueError, "base frame 0 differs from the locked base"):
            validate_composed_look(
                {"future_mullet": self.hair, "future_mustache": self.facial},
                self.look, self.template, base,
            )

    def test_primary_share_boundary_is_exactly_ninety_percent(self):
        self.assertFalse(_meets_primary_share(89, 100))
        self.assertTrue(_meets_primary_share(90, 100))

    def test_semantic_report_rejects_forged_validity_thresholds_metrics_and_digests(self):
        report = validate_semantic_layer(self.hair, self.mullet, self.template).to_dict()
        require_semantic_report(report)
        for mutate, message in (
            (lambda value: value.update(valid=False), "valid flag"),
            (lambda value: value["thresholds"]["poses"]["north"].update(scalp=0), "thresholds"),
            (lambda value: value["metrics"].pop(), "stored-18"),
            (lambda value: value["metrics"][0].clear(), "stored-18"),
            (lambda value: value["runtimeMetrics"].pop(), "runtime-30"),
            (lambda value: value["runtimeMetrics"][0].pop("bbox"), "mirror/crop evidence"),
            (lambda value: value["metrics"][0].update(outsidePixels=999), "containment failures"),
            (lambda value: value["runtimeMetrics"][0].update(bbox="bogus"), "mirror/crop geometry"),
            (lambda value: value["runtimeMetrics"][0].update(opaquePixels=999), "opaque count differs"),
            (lambda value: value["metrics"][3].update(landmarkDistance=-1), "hard thresholds"),
            (lambda value: value["provenance"].update(templateSha256="0" * 64), "digest changed"),
        ):
            forged = __import__("copy").deepcopy(report)
            mutate(forged)
            with self.assertRaisesRegex(ValueError, message):
                require_semantic_report(forged)
        invalid = __import__("copy").deepcopy(report)
        invalid["findings"] = [{"severity": "error", "code": "forged", "layer": "x",
                                "master": None, "offset": None, "details": {}}]
        invalid["valid"] = False
        with self.assertRaisesRegex(ValueError, "contains error findings"):
            require_semantic_report(invalid)

        composed = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        ).to_dict()
        require_semantic_report(composed)
        composed["metrics"][0]["collisionPixels"] = 1
        with self.assertRaisesRegex(ValueError, "cross-layer collisions"):
            require_semantic_report(composed)
        composed = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        ).to_dict()
        composed["provenance"]["layerManifestPaths"] = {}
        composed["provenance"]["layerManifestDigests"] = {}
        with self.assertRaisesRegex(ValueError, "declared layer manifests"):
            require_semantic_report(composed)
        composed = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        ).to_dict()
        composed["metrics"][0]["layerVisibility"] = {}
        with self.assertRaisesRegex(ValueError, "visibility keys"):
            require_semantic_report(composed)
        composed = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        ).to_dict()
        composed["provenance"]["baseFrameDigests"][0] = "0" * 64
        with self.assertRaisesRegex(ValueError, "locked base digests"):
            require_semantic_report(composed)
        composed = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        ).to_dict()
        for item in composed["metrics"]:
            item["layerVisibility"] = {
                key: {"authoredPixels": 0, "occludedPixels": 0, "visiblePixels": 0}
                for key in item["layerVisibility"]
            }
        for item in composed["runtimeMetrics"]:
            item["layerVisibility"] = {
                key: {"authoredPixels": 0, "occludedPixels": 0, "visiblePixels": 0}
                for key in item["layerVisibility"]
            }
        with self.assertRaisesRegex(ValueError, "authored union disagrees"):
            require_semantic_report(composed)
        composed = validate_composed_look(
            {"future_mullet": self.hair, "future_mustache": self.facial},
            self.look, self.template, self.base,
        ).to_dict()
        composed["metrics"][0]["composedBbox"] = [0, 0, 1, 1]
        composed["metrics"][0]["cropXShift"] = 999
        with self.assertRaisesRegex(ValueError, "stored bbox/crop differs"):
            require_semantic_report(composed)
        malformed = __import__("copy").deepcopy(report)
        malformed["findings"] = [{"severity": "warning", "code": "x"}]
        with self.assertRaisesRegex(ValueError, "malformed findings"):
            require_semantic_report(malformed)
        facial = validate_semantic_layer(self.facial, self.mustache, self.template).to_dict()
        facial["metrics"][0]["centerDeltaTwice"] = 99
        with self.assertRaisesRegex(ValueError, "topology/envelope"):
            require_semantic_report(facial)
        facial = validate_semantic_layer(self.facial, self.mustache, self.template).to_dict()
        facial["metrics"][0]["landmarkDistance"] = -999
        with self.assertRaisesRegex(ValueError, "topology/envelope"):
            require_semantic_report(facial)


if __name__ == "__main__":
    unittest.main()
