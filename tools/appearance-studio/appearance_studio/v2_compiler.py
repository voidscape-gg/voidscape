from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from PIL import Image

from .v2_template import MASTERS, PaperdollV2Template
from .v2_workspace import load_v2_workspace, safe_workspace_path


@dataclass(frozen=True)
class V2Sidecar:
    requires_shift: bool
    x_shift: int
    y_shift: int
    logical_width: int
    logical_height: int


@dataclass(frozen=True)
class V2CompiledFrame:
    asset_id: str
    channel_id: str
    tint_role: str
    offset: int
    canvas: Image.Image
    cropped: Image.Image
    sidecar: V2Sidecar
    empty: bool
    source_path: Path
    source_sha256: str


@dataclass(frozen=True)
class V2CompiledChannel:
    id: str
    tint_role: str
    frames: tuple[V2CompiledFrame, ...]


@dataclass(frozen=True)
class V2CompiledAsset:
    id: str
    kind: str
    paperdoll_slot: int
    source_mode: str
    propagation: str
    channels: tuple[V2CompiledChannel, ...]


@dataclass(frozen=True)
class V2CompileResult:
    root: Path
    manifest: Mapping[str, Any]
    template: PaperdollV2Template
    assets: tuple[V2CompiledAsset, ...]

    def asset(self, asset_id: str) -> V2CompiledAsset:
        try:
            return next(asset for asset in self.assets if asset.id == asset_id)
        except StopIteration as exc:
            raise ValueError(f"unknown compiled asset {asset_id!r}") from exc


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _load_rgba(path: Path, expected_size: tuple[int, int], label: str) -> Image.Image:
    with Image.open(path) as source:
        if source.format != "PNG" or source.mode != "RGBA":
            raise ValueError(f"{label} must be an RGBA PNG")
        image = source.copy()
    if image.size != expected_size:
        raise ValueError(f"{label} size {image.size} != {expected_size}")
    return image


def translate_rgba(image: Image.Image, dx: int, dy: int, size: tuple[int, int]) -> Image.Image:
    """Translate without alpha-compositing so every source RGBA byte remains intact."""
    if any(isinstance(value, bool) or not isinstance(value, int) for value in (dx, dy)):
        raise ValueError("V2 translations must use integer pixels")
    output = Image.new("RGBA", size, (0, 0, 0, 0))
    output.paste(image, (dx, dy))
    return output


def crop_rgba(canvas: Image.Image) -> tuple[Image.Image, V2Sidecar, bool]:
    """Alpha-aware crop that preserves arbitrary RGB, grayscale, and 0..255 alpha."""
    rgba = canvas.convert("RGBA")
    box = rgba.getchannel("A").getbbox()
    logical_width, logical_height = rgba.size
    if box is None:
        return Image.new("RGBA", (1, 1), (0, 0, 0, 0)), V2Sidecar(
            True, 0, 0, logical_width, logical_height,
        ), True
    left, top, right, bottom = box
    # V2 always carries its logical canvas explicitly, including a full-canvas
    # crop. This removes the legacy Sprite ambiguity between physical and
    # logical dimensions and is enforced by both pack readers.
    return rgba.crop(box), V2Sidecar(True, left, top, logical_width, logical_height), False


def expand_rgba(cropped: Image.Image, sidecar: V2Sidecar) -> Image.Image:
    output = Image.new("RGBA", (sidecar.logical_width, sidecar.logical_height), (0, 0, 0, 0))
    if cropped.getchannel("A").getbbox() is not None:
        output.paste(cropped.convert("RGBA"), (sidecar.x_shift, sidecar.y_shift))
    return output


def _compile_rigid_channel(
    root: Path,
    asset_id: str,
    channel: Mapping[str, Any],
    template: PaperdollV2Template,
) -> tuple[V2CompiledFrame, ...]:
    masters: dict[str, tuple[Image.Image, Path, str]] = {}
    source_specs = {master: next(frame for frame in template.frames if frame.master == master)
                    for master in MASTERS}
    for master in MASTERS:
        source_path = safe_workspace_path(root, channel["masters"][master])
        source = _load_rgba(source_path, source_specs[master].size, f"{asset_id}.{channel['id']}.{master}")
        masters[master] = source, source_path, _sha256(source_path)

    frames: list[V2CompiledFrame] = []
    for frame in template.frames:
        master, source_path, source_digest = masters[frame.master]
        source_spec = source_specs[frame.master]
        dx = frame.crown[0] - source_spec.crown[0]
        dy = frame.crown[1] - source_spec.crown[1]
        canvas = translate_rgba(master, dx, dy, frame.size)
        cropped, sidecar, empty = crop_rgba(canvas)
        if expand_rgba(cropped, sidecar).tobytes() != canvas.tobytes():
            raise ValueError(f"{asset_id}.{channel['id']} frame {frame.offset} crop/sidecar lost RGBA data")
        frames.append(V2CompiledFrame(
            asset_id, channel["id"], channel["tintRole"], frame.offset,
            canvas, cropped, sidecar, empty, source_path, source_digest,
        ))
    return tuple(frames)


def _compile_explicit_channel(
    root: Path,
    asset_id: str,
    channel: Mapping[str, Any],
    template: PaperdollV2Template,
) -> tuple[V2CompiledFrame, ...]:
    frames: list[V2CompiledFrame] = []
    for frame in template.frames:
        source_path = safe_workspace_path(root, channel["frames"][f"{frame.offset:02d}"])
        canvas = _load_rgba(source_path, frame.size, f"{asset_id}.{channel['id']}.{frame.offset:02d}")
        cropped, sidecar, empty = crop_rgba(canvas)
        if expand_rgba(cropped, sidecar).tobytes() != canvas.tobytes():
            raise ValueError(f"{asset_id}.{channel['id']} frame {frame.offset} crop/sidecar lost RGBA data")
        frames.append(V2CompiledFrame(
            asset_id, channel["id"], channel["tintRole"], frame.offset,
            canvas, cropped, sidecar, empty, source_path, _sha256(source_path),
        ))
    return tuple(frames)


def compile_v2_workspace(root: Path) -> V2CompileResult:
    workspace_root, manifest, template = load_v2_workspace(root)
    assets: list[V2CompiledAsset] = []
    for asset in manifest["assets"]:
        channels: list[V2CompiledChannel] = []
        for channel in asset["channels"]:
            if asset["propagation"] == "rigid-head":
                frames = _compile_rigid_channel(workspace_root, asset["id"], channel, template)
            elif asset["propagation"] == "explicit-frames":
                frames = _compile_explicit_channel(workspace_root, asset["id"], channel, template)
            else:
                raise ValueError(f"unsupported propagation for {asset['id']}")
            if len(frames) != 18 or [frame.offset for frame in frames] != list(range(18)):
                raise ValueError(f"compiled channel is incomplete: {asset['id']}.{channel['id']}")
            channels.append(V2CompiledChannel(channel["id"], channel["tintRole"], frames))
        assets.append(V2CompiledAsset(
            asset["id"], asset["kind"], int(asset["paperdollSlot"]),
            asset["sourceMode"], asset["propagation"], tuple(channels),
        ))
    return V2CompileResult(workspace_root, manifest, template, tuple(assets))


def alpha_statistics(image: Image.Image) -> dict[str, Any]:
    alpha = [pixel[3] for pixel in image.convert("RGBA").getdata()]
    nonzero = [value for value in alpha if value]
    return {
        "nonzeroPixels": len(nonzero),
        "softAlphaPixels": sum(0 < value < 255 for value in alpha),
        "distinctAlpha": sorted(set(nonzero)),
        "minimumNonzero": min(nonzero) if nonzero else None,
        "maximum": max(nonzero) if nonzero else 0,
    }


def grayscale_statistics(image: Image.Image) -> dict[str, Any]:
    values = sorted({red for red, green, blue, alpha in image.convert("RGBA").getdata()
                     if alpha and red == green == blue})
    return {"distinctValues": values, "count": len(values), "arbitraryValuesPreserved": True}


__all__ = [
    "V2CompileResult", "V2CompiledAsset", "V2CompiledChannel", "V2CompiledFrame", "V2Sidecar",
    "alpha_statistics", "compile_v2_workspace", "crop_rgba", "expand_rgba",
    "grayscale_statistics", "translate_rgba",
]
