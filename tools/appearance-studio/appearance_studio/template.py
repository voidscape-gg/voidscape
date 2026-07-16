from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PIL import Image
import yaml

from .paths import REPO_ROOT
from .registry import safe_repo_path


LANDMARK_NAMES = frozenset({
    "forehead", "face_center", "nose_tip", "upper_lip", "chin", "ear", "occiput", "neck", "nape",
})
MASK_NAMES = frozenset({
    "hair_allowed", "facial_hair_allowed", "scalp_attachment", "upper_lip_attachment",
    "nape_tail", "face_clearance", "protected_anatomy",
    "neck_clearance",
})
VISUAL_POSES = frozenset({
    "front", "three-quarter-front", "profile", "three-quarter-rear", "rear", "combat-profile",
})
REAR_VISUAL_POSES = frozenset({"three-quarter-rear", "rear"})
FACIAL_LANDMARK_NAMES = frozenset({"forehead", "face_center", "nose_tip", "upper_lip"})
REFERENCE_NAMES = frozenset({"head4", "head1", "fhead1", "head3", "body", "legs"})
GROUP_CONTRACT = {
    "north": ((0, 1, 2), "walk"),
    "north-west": ((3, 4, 5), "walk"),
    "west": ((6, 7, 8), "walk"),
    "south-west": ((9, 10, 11), "walk"),
    "south": ((12, 13, 14), "walk"),
    "combat-west": ((15, 16, 17), "combat"),
}
RUNTIME_MIRRORS = {
    "south-east": "south-west", "east": "west", "north-east": "north-west", "combat-b": "combat-west",
}
COORDINATE_CONVENTION = {
    "landmarks": "crown-relative",
    "fractional_center_rounding": "floor",
    "masks": "logical-canvas",
    "phase_translation": "crown-delta",
}
POSE_CONTRACT = {
    "north": ("front", {
        "forehead": (0, 5), "face_center": (0, 9), "nose_tip": (0, 11),
        "upper_lip": (0, 14), "chin": (0, 17), "ear": None,
        "occiput": (0, 7), "neck": (0, 17), "nape": (0, 16),
    }),
    "north-west": ("three-quarter-front", {
        "forehead": (4, 5), "face_center": (5, 9), "nose_tip": (8, 11),
        "upper_lip": (5, 14), "chin": (1, 17), "ear": (-6, 9),
        "occiput": (-6, 8), "neck": (1, 17), "nape": (-5, 15),
    }),
    "west": ("profile", {
        "forehead": (3, 5), "face_center": (4, 8), "nose_tip": (7, 9),
        "upper_lip": (3, 12), "chin": (-1, 14), "ear": (-3, 8),
        "occiput": (-7, 7), "neck": (-1, 14), "nape": (-7, 13),
    }),
    "south-west": ("three-quarter-rear", {
        "forehead": None, "face_center": None, "nose_tip": None, "upper_lip": None,
        "chin": (0, 14), "ear": (7, 8), "occiput": (-4, 8),
        "neck": (0, 14), "nape": (-1, 14),
    }),
    "south": ("rear", {
        "forehead": None, "face_center": None, "nose_tip": None, "upper_lip": None,
        "chin": (0, 15), "ear": None, "occiput": (0, 8),
        "neck": (0, 15), "nape": (0, 15),
    }),
    "combat-west": ("combat-profile", {
        "forehead": (3, 5), "face_center": (4, 8), "nose_tip": (7, 9),
        "upper_lip": (3, 12), "chin": (-1, 14), "ear": (-3, 8),
        "occiput": (-7, 7), "neck": (-1, 14), "nape": (-7, 13),
    }),
}


@dataclass(frozen=True)
class FrameSpec:
    offset: int
    master: str
    size: tuple[int, int]
    crown: tuple[int, int]


@dataclass(frozen=True)
class PoseMaskReference:
    path: Path
    sha256: str
    size: tuple[int, int]


@dataclass(frozen=True)
class PoseProfile:
    visual_pose: str
    landmarks: dict[str, tuple[int, int] | None]
    masks: dict[str, PoseMaskReference | None]


@dataclass(frozen=True)
class PaperdollTemplate:
    path: Path
    digest: str
    key: str
    frames: tuple[FrameSpec, ...]
    mirrors: dict[str, str]
    pose_profiles: dict[str, PoseProfile]
    palette: dict[str, Any]
    reference_dirs: dict[str, Path]
    reference_digests: dict[str, str]
    locked_base_reference: Path
    coordinate_convention: dict[str, str]

    @property
    def reference_dir(self) -> Path:
        """Compatibility alias for consumers of the locked bald-head base."""
        return self.locked_base_reference


def tree_sha256(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.glob("frame_*")):
        digest.update(path.name.encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def _integer_tuple(value: Any, length: int, label: str) -> tuple[int, ...]:
    if not isinstance(value, list) or len(value) != length or any(
        isinstance(item, bool) or not isinstance(item, int) for item in value
    ):
        raise ValueError(f"{label} must contain exactly {length} integers")
    return tuple(value)


def load_template(path: Path, *, repo_root: Path = REPO_ROOT) -> PaperdollTemplate:
    payload = yaml.safe_load(path.read_text())
    if payload.get("schema") != "voidscape-paperdoll-template/v2" or payload.get("frame_count") != 18:
        raise ValueError("unsupported paperdoll template")
    canvases = payload["logical_canvases"]
    if canvases != {"walk": [64, 102], "combat": [84, 102]}:
        raise ValueError("paperdoll logical canvases do not match the locked v2 contract")
    groups = payload["groups"]
    actual_groups = {group["master"]: (tuple(group["offsets"]), group["canvas"]) for group in groups}
    if actual_groups != GROUP_CONTRACT or len(groups) != len(GROUP_CONTRACT):
        raise ValueError("paperdoll groups do not match the locked v2 master/offset/canvas contract")
    if payload.get("runtime_mirrors") != RUNTIME_MIRRORS:
        raise ValueError("paperdoll runtime mirrors do not match the locked v2 contract")
    frames: list[FrameSpec] = []
    for group in groups:
        if len(group.get("crowns", ())) != 3:
            raise ValueError(f"paperdoll group {group['master']} must define exactly three crowns")
        size = _integer_tuple(canvases[group["canvas"]], 2, f"canvas {group['canvas']}")
        for offset, crown in zip(group["offsets"], group["crowns"]):
            frames.append(FrameSpec(offset, group["master"], size, _integer_tuple(crown, 2, "frame crown")))
    frames.sort(key=lambda frame: frame.offset)
    if [frame.offset for frame in frames] != list(range(18)):
        raise ValueError("template must define offsets 0..17 exactly once")
    references = payload.get("references", {})
    if set(references) != REFERENCE_NAMES:
        raise ValueError(f"template references must be exactly {sorted(REFERENCE_NAMES)}")
    reference_dirs: dict[str, Path] = {}
    reference_digests: dict[str, str] = {}
    for name, reference in references.items():
        directory = safe_repo_path(reference["frames_dir"], repo_root=repo_root)
        expected = reference["tree_sha256"]
        if tree_sha256(directory) != expected:
            raise ValueError(f"locked {name} reference digest changed")
        reference_dirs[name] = directory
        reference_digests[name] = expected
    locked_base_name = payload.get("locked_base_reference")
    if locked_base_name != "head4":
        raise ValueError("locked_base_reference must be head4")
    coordinate_convention = payload.get("coordinate_convention")
    if coordinate_convention != COORDINATE_CONVENTION:
        raise ValueError("unsupported paperdoll coordinate convention")

    masters = {frame.master for frame in frames}
    profiles = payload.get("pose_profiles", {})
    if set(profiles) != masters:
        raise ValueError(f"pose profiles must be exactly {sorted(masters)}")
    pose_profiles: dict[str, PoseProfile] = {}
    for master, profile in profiles.items():
        visual_pose = profile.get("visual_pose")
        if visual_pose not in VISUAL_POSES:
            raise ValueError(f"pose profile {master} has unsupported visual pose {visual_pose!r}")
        landmark_values = profile.get("landmarks", {})
        if set(landmark_values) != LANDMARK_NAMES:
            raise ValueError(f"pose profile {master} landmarks must be exactly {sorted(LANDMARK_NAMES)}")
        landmarks = {
            name: None if value is None else _integer_tuple(value, 2, f"{master}.{name}")
            for name, value in landmark_values.items()
        }
        for name, point in landmarks.items():
            if point is None:
                continue
            for frame in (item for item in frames if item.master == master):
                absolute = frame.crown[0] + point[0], frame.crown[1] + point[1]
                if not (0 <= absolute[0] < frame.size[0] and 0 <= absolute[1] < frame.size[1]):
                    raise ValueError(
                        f"pose profile {master} landmark {name} falls outside frame {frame.offset}"
                    )
        mask_values = profile.get("masks", {})
        if set(mask_values) != MASK_NAMES:
            raise ValueError(f"pose profile {master} masks must be exactly {sorted(MASK_NAMES)}")
        master_size = next(frame.size for frame in frames if frame.master == master)
        masks: dict[str, PoseMaskReference | None] = {}
        for name, value in mask_values.items():
            if value is None:
                masks[name] = None
                continue
            mask_path = safe_repo_path(value["path"], repo_root=repo_root)
            digest = hashlib.sha256(mask_path.read_bytes()).hexdigest()
            if digest != value["sha256"]:
                raise ValueError(f"pose profile {master} mask {name} digest changed")
            try:
                with Image.open(mask_path) as image:
                    if image.format != "PNG" or image.mode != "1":
                        raise ValueError(f"pose profile {master} mask {name} must be a 1-bit PNG")
                    if image.size != master_size:
                        raise ValueError(
                            f"pose profile {master} mask {name} size {image.size} != {master_size}"
                        )
            except OSError as exc:
                raise ValueError(f"could not read pose profile {master} mask {name}: {exc}") from exc
            masks[name] = PoseMaskReference(mask_path, digest, master_size)
        pose_profiles[master] = PoseProfile(visual_pose, landmarks, masks)
    if {profile.visual_pose for profile in pose_profiles.values()} != VISUAL_POSES:
        raise ValueError("pose profiles must cover each visual pose exactly once")
    for master, profile in pose_profiles.items():
        rear = profile.visual_pose in REAR_VISUAL_POSES
        for name in FACIAL_LANDMARK_NAMES:
            if rear and profile.landmarks[name] is not None:
                raise ValueError(f"rear pose profile {master} landmark {name} must be null")
            if not rear and profile.landmarks[name] is None:
                raise ValueError(f"visible-face pose profile {master} landmark {name} is required")
        for role in ("hair_allowed", "scalp_attachment", "neck_clearance", "protected_anatomy"):
            if profile.masks[role] is None:
                raise ValueError(f"pose profile {master} mask {role} is required")
        for role in ("facial_hair_allowed", "upper_lip_attachment", "face_clearance"):
            if rear and profile.masks[role] is not None:
                raise ValueError(f"rear pose profile {master} mask {role} must be null")
            if not rear and profile.masks[role] is None:
                raise ValueError(f"visible-face pose profile {master} mask {role} is required")
        if profile.visual_pose == "front":
            if profile.masks["nape_tail"] is not None:
                raise ValueError(f"front pose profile {master} nape_tail mask must be null")
        elif profile.masks["nape_tail"] is None:
            raise ValueError(f"pose profile {master} nape_tail mask is required")
        expected_pose, expected_landmarks = POSE_CONTRACT[master]
        if profile.visual_pose != expected_pose:
            raise ValueError(
                f"pose profile {master} visual pose {profile.visual_pose!r} != locked {expected_pose!r}"
            )
        if profile.landmarks != expected_landmarks:
            raise ValueError(f"pose profile {master} landmarks differ from the locked v2 calibration")

    return PaperdollTemplate(
        path.resolve(), hashlib.sha256(path.read_bytes()).hexdigest(),
        payload["key"], tuple(frames), dict(payload["runtime_mirrors"]), pose_profiles,
        dict(payload["palette"]), reference_dirs, reference_digests, reference_dirs[locked_base_name],
        dict(coordinate_convention),
    )
