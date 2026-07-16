from __future__ import annotations

import hashlib
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from PIL import Image, ImageDraw
import yaml

from .paths import REPO_ROOT
from .registry import safe_repo_path
from .template import MASK_NAMES, PaperdollTemplate, load_template


SCHEMA = "voidscape-paperdoll-v2-template/v1"
DEFAULT_TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-2x-v1/template.yaml"
MASTERS = ("north", "north-west", "west", "south-west", "south", "combat-west")
TINT_ROLES = ("skin", "hair", "facial-hair", "top", "bottom", "primary", "secondary", "fixed")
CANVASES = {"walk": (128, 204), "combat": (168, 204)}
PREVIEW = {"width": 176, "height": 224, "draw_x": 24, "draw_y": 4,
           "draw_width": 128, "draw_height": 204}
UPPER_HAIR_ENVELOPES = {
    "north": ((-10, -18), (10, -18), (26, -6), (24, 8), (-24, 8), (-26, -6)),
    "north-west": ((-22, -18), (-4, -18), (28, -4), (26, 8), (-26, 8), (-28, -4)),
    "west": ((-24, -18), (-8, -18), (28, -6), (26, 8), (-24, 8), (-28, -4)),
    "south-west": ((-26, -16), (-12, -18), (22, -18), (28, -4), (24, 8), (-26, 8)),
    "south": ((-12, -18), (12, -18), (28, -4), (24, 8), (-24, 8), (-28, -4)),
    "combat-west": ((-24, -18), (-8, -18), (28, -6), (26, 8), (-24, 8), (-28, -4)),
}


@dataclass(frozen=True)
class V2FrameSpec:
    offset: int
    master: str
    size: tuple[int, int]
    crown: tuple[int, int]


@dataclass(frozen=True)
class V2PoseProfile:
    visual_pose: str
    landmarks: Mapping[str, tuple[int, int] | None]
    masks: Mapping[str, Image.Image | None]
    mask_sha256: Mapping[str, str | None]


@dataclass(frozen=True)
class PaperdollV2Template:
    path: Path
    digest: str
    key: str
    frames: tuple[V2FrameSpec, ...]
    mirrors: Mapping[str, str]
    pose_profiles: Mapping[str, V2PoseProfile]
    preview: Mapping[str, int]
    source: PaperdollTemplate
    source_digest: str
    derived_mask_tree_sha256: str
    upper_hair_envelopes: Mapping[str, tuple[tuple[int, int], ...]]

    def frame(self, offset: int) -> V2FrameSpec:
        if not 0 <= offset < len(self.frames):
            raise ValueError(f"frame offset must be 0..{len(self.frames) - 1}")
        return self.frames[offset]


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _pair(value: Any, label: str) -> tuple[int, int]:
    if (not isinstance(value, list) or len(value) != 2 or
            any(isinstance(item, bool) or not isinstance(item, int) for item in value)):
        raise ValueError(f"{label} must contain exactly two integers")
    return value[0], value[1]


def _derived_masks(
    source: PaperdollTemplate,
    upper_hair_envelopes: Mapping[str, tuple[tuple[int, int], ...]],
) -> tuple[dict[str, dict[str, Image.Image | None]], str]:
    output: dict[str, dict[str, Image.Image | None]] = {}
    digest = hashlib.sha256()
    for master in sorted(source.pose_profiles):
        output[master] = {}
        for role in sorted(source.pose_profiles[master].masks):
            reference = source.pose_profiles[master].masks[role]
            digest.update(master.encode("ascii") + b"\0" + role.encode("ascii") + b"\0")
            if reference is None:
                output[master][role] = None
                digest.update(b"null\0")
                continue
            # load_template already verifies the source PNG and its embedded digest.
            with Image.open(reference.path) as image:
                derived = image.resize(
                    (image.width * 2, image.height * 2), Image.Resampling.NEAREST,
                ).convert("1")
            if role == "hair_allowed":
                source_frame = next(frame for frame in source.frames if frame.master == master)
                crown = source_frame.crown[0] * 2, source_frame.crown[1] * 2
                points = [
                    (crown[0] + point[0], crown[1] + point[1])
                    for point in upper_hair_envelopes[master]
                ]
                ImageDraw.Draw(derived).polygon(points, fill=1)
            digest.update(struct.pack(">II", *derived.size))
            digest.update(derived.tobytes())
            output[master][role] = derived.copy()
    return output, digest.hexdigest()


def _mask_pixels_sha256(image: Image.Image | None) -> str | None:
    if image is None:
        return None
    digest = hashlib.sha256()
    digest.update(struct.pack(">II", *image.size))
    digest.update(image.convert("1").tobytes())
    return digest.hexdigest()


def load_v2_template(path: Path = DEFAULT_TEMPLATE, *, repo_root: Path = REPO_ROOT) -> PaperdollV2Template:
    payload = yaml.safe_load(path.read_text())
    if payload.get("schema") != SCHEMA or payload.get("key") != "rsc-player-2x-v1":
        raise ValueError("unsupported paperdoll V2 template")
    if payload.get("frame_count") != 18 or payload.get("scale") != 2:
        raise ValueError("paperdoll V2 must contain exactly 18 frames at scale 2")

    source_ref = payload.get("source_template", {})
    source_path = safe_repo_path(source_ref.get("path", ""), repo_root=repo_root)
    source_digest = _sha256(source_path)
    if source_digest != source_ref.get("sha256"):
        raise ValueError("digest-locked V1 source template changed")
    source = load_template(source_path, repo_root=repo_root)

    canvases = {name: _pair(value, f"logical canvas {name}")
                for name, value in payload.get("logical_canvases", {}).items()}
    if canvases != CANVASES:
        raise ValueError("paperdoll V2 canvases must be exact 2x V1 canvases")
    source_groups: dict[str, list[Any]] = {master: [] for master in MASTERS}
    for frame in source.frames:
        source_groups[frame.master].append(frame)

    groups = payload.get("groups")
    if not isinstance(groups, list) or len(groups) != len(MASTERS):
        raise ValueError("paperdoll V2 must declare the six locked master groups")
    frames: list[V2FrameSpec] = []
    seen: set[str] = set()
    for group in groups:
        master = group.get("master")
        if master not in MASTERS or master in seen:
            raise ValueError("paperdoll V2 master groups are missing, duplicated, or unknown")
        seen.add(master)
        source_frames = sorted(source_groups[master], key=lambda item: item.offset)
        expected_offsets = [frame.offset for frame in source_frames]
        if group.get("offsets") != expected_offsets:
            raise ValueError(f"paperdoll V2 offsets for {master} differ from V1")
        expected_canvas = "combat" if master == "combat-west" else "walk"
        if group.get("canvas") != expected_canvas:
            raise ValueError(f"paperdoll V2 canvas group for {master} differs from V1")
        crowns = group.get("crowns")
        expected_crowns = [[frame.crown[0] * 2, frame.crown[1] * 2] for frame in source_frames]
        if crowns != expected_crowns:
            raise ValueError(f"paperdoll V2 crowns for {master} must be exact doubled V1 crowns")
        for source_frame, crown in zip(source_frames, crowns):
            expected_size = source_frame.size[0] * 2, source_frame.size[1] * 2
            if expected_size != canvases[expected_canvas]:
                raise ValueError(f"paperdoll V2 frame {source_frame.offset} is not exact 2x V1")
            frames.append(V2FrameSpec(source_frame.offset, master, expected_size, _pair(crown, "crown")))
    frames.sort(key=lambda item: item.offset)
    if [frame.offset for frame in frames] != list(range(18)):
        raise ValueError("paperdoll V2 template must define offsets 0..17 exactly once")

    if payload.get("runtime_mirrors") != source.mirrors:
        raise ValueError("paperdoll V2 runtime mirrors must remain identical to V1")
    if payload.get("coordinate_convention") != {
        "landmarks": "crown-relative-scaled-integer",
        "masks": "nearest-neighbor-2x-plus-upper-hair-envelope",
        "phase_translation": "doubled-crown-delta",
    }:
        raise ValueError("unsupported paperdoll V2 coordinate convention")

    declared_envelopes = payload.get("upper_hair_envelopes")
    expected_envelopes = {
        master: [list(point) for point in UPPER_HAIR_ENVELOPES[master]] for master in MASTERS
    }
    if declared_envelopes != expected_envelopes:
        raise ValueError("paperdoll V2 upper hair envelopes differ from the locked pose contract")
    masks, mask_tree_digest = _derived_masks(source, UPPER_HAIR_ENVELOPES)
    mask_contract = payload.get("derived_masks", {})
    if mask_contract.get("algorithm") != "nearest-neighbor-2x-plus-upper-hair-envelope":
        raise ValueError("paperdoll V2 mask derivation algorithm is unsupported")
    if mask_tree_digest != mask_contract.get("tree_sha256"):
        raise ValueError("derived 2x mask tree digest changed")

    profiles: dict[str, V2PoseProfile] = {}
    for master in MASTERS:
        source_profile = source.pose_profiles[master]
        landmarks = {
            name: None if point is None else (point[0] * 2, point[1] * 2)
            for name, point in source_profile.landmarks.items()
        }
        master_size = next(frame.size for frame in frames if frame.master == master)
        if any(image is not None and image.size != master_size for image in masks[master].values()):
            raise ValueError(f"derived V2 mask size mismatch for {master}")
        profiles[master] = V2PoseProfile(
            source_profile.visual_pose,
            landmarks,
            masks[master],
            {role: _mask_pixels_sha256(image) for role, image in masks[master].items()},
        )
    if set(profiles) != set(MASTERS) or any(set(profile.masks) != MASK_NAMES for profile in profiles.values()):
        raise ValueError("derived V2 pose profiles are incomplete")
    declared_profiles = payload.get("pose_profiles")
    if not isinstance(declared_profiles, dict) or set(declared_profiles) != set(MASTERS):
        raise ValueError("paperdoll V2 must declare every named pose profile")
    for master, profile in profiles.items():
        declared = declared_profiles[master]
        expected_masks = {
            role: None if digest is None else {
                "workspace_path": f"guides/{master}/masks/{role}.png",
                "pixels_sha256": digest,
            }
            for role, digest in profile.mask_sha256.items()
        }
        expected_landmarks = {
            name: None if point is None else list(point) for name, point in profile.landmarks.items()
        }
        if declared != {
            "visual_pose": profile.visual_pose,
            "landmarks": expected_landmarks,
            "masks": expected_masks,
        }:
            raise ValueError(f"paperdoll V2 pose profile {master} differs from doubled V1 contract")

    if payload.get("preview") != PREVIEW:
        raise ValueError("paperdoll V2 preview must be exact 2x of the locked 1x oracle")
    if payload.get("palette") != {
        "preserve_rgba": True, "arbitrary_grayscale": True, "tint_roles": list(TINT_ROLES),
    }:
        raise ValueError("paperdoll V2 palette/tint contract is unsupported")
    return PaperdollV2Template(
        path.resolve(), _sha256(path), payload["key"], tuple(frames), dict(source.mirrors),
        profiles, dict(PREVIEW), source, source_digest, mask_tree_digest, UPPER_HAIR_ENVELOPES,
    )


__all__ = [
    "CANVASES", "DEFAULT_TEMPLATE", "MASTERS", "PREVIEW", "PaperdollV2Template",
    "TINT_ROLES", "UPPER_HAIR_ENVELOPES", "V2FrameSpec", "V2PoseProfile", "load_v2_template",
]
