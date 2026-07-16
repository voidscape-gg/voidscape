from __future__ import annotations

from dataclasses import dataclass

from PIL import Image

from .geometry import DerivedSprite, derive_sprite, translate_integer
from .palette import validate_palette
from .template import PaperdollTemplate, PoseMaskReference


@dataclass(frozen=True)
class CompiledFrame:
    offset: int
    master: str
    canvas: Image.Image
    sprite: DerivedSprite | None


@dataclass(frozen=True)
class CompileResult:
    frames: tuple[CompiledFrame, ...]
    findings: tuple[str, ...]


def _load_mask(reference: PoseMaskReference | None, label: str) -> Image.Image:
    if reference is None:
        raise ValueError(f"pose profile has no applicable {label} mask")
    with Image.open(reference.path) as image:
        return image.copy()


def _mask_contains(mask: Image.Image, x: int, y: int, dx: int, dy: int) -> bool:
    source_x, source_y = x - dx, y - dy
    return 0 <= source_x < mask.width and 0 <= source_y < mask.height and bool(mask.getpixel((source_x, source_y)))


def validate_overlap(canvas: Image.Image, kind: str, template: PaperdollTemplate,
                     master: str, *, phase_delta: tuple[int, int] = (0, 0)) -> list[str]:
    roles = {
        "hair": ("hair_allowed", "scalp_attachment"),
        "facial-hair": ("facial_hair_allowed", "upper_lip_attachment"),
    }
    if kind not in roles:
        raise ValueError(f"rigid-head compiler has no pose-mask contract for kind {kind!r}")
    profile = template.pose_profiles[master]
    allowed_role, contact_role = roles[kind]
    allowed = _load_mask(profile.masks[allowed_role], allowed_role)
    attachment = _load_mask(profile.masks[contact_role], contact_role)
    forbidden_roles = ("protected_anatomy", "face_clearance", "neck_clearance") \
        if kind == "hair" else ("protected_anatomy",)
    forbidden = [
        _load_mask(profile.masks[role], role)
        for role in forbidden_roles
        if profile.masks[role] is not None
    ]
    dx, dy = phase_delta
    contact = 0
    errors: list[str] = []
    for y in range(canvas.height):
        for x in range(canvas.width):
            if canvas.getpixel((x, y))[3] < 128:
                continue
            if not _mask_contains(allowed, x, y, dx, dy):
                errors.append(f"{kind} pixel outside allowed zone at {x},{y}")
            if any(_mask_contains(mask, x, y, dx, dy) for mask in forbidden):
                errors.append(f"{kind} pixel overlaps protected anatomy at {x},{y}")
            if _mask_contains(attachment, x, y, dx, dy):
                contact += 1
    if contact == 0:
        errors.append(f"{kind} layer does not contact its attachment zone")
    return errors


def expand_rigid_layer(masters: dict[str, Image.Image], kind: str, policy: str,
                       template: PaperdollTemplate, *, whitelist=None,
                       hidden_masters: tuple[str, ...] | set[str] = (),
                       propagation: str = "rigid-head") -> CompileResult:
    if propagation != "rigid-head":
        raise ValueError(
            f"unsupported propagation mode {propagation!r}; legacy head compilation requires 'rigid-head'"
        )
    if kind not in {"hair", "facial-hair"}:
        raise ValueError(f"rigid-head compiler has no pose-mask contract for kind {kind!r}")
    findings: list[str] = []
    frames: list[CompiledFrame] = []
    source_frames = {frame.master: frame for frame in template.frames if frame.offset in {0, 3, 6, 9, 12, 15}}
    if set(masters) != set(source_frames):
        raise ValueError(f"masters must be exactly {sorted(source_frames)}")
    unknown_hidden = set(hidden_masters) - set(source_frames)
    if unknown_hidden:
        raise ValueError(f"hidden masters are unknown: {sorted(unknown_hidden)}")
    for frame in template.frames:
        source = source_frames[frame.master]
        master = masters[frame.master].convert("RGBA")
        if master.size != source.size:
            raise ValueError(f"master {frame.master} size {master.size} != {source.size}")
        dx, dy = frame.crown[0] - source.crown[0], frame.crown[1] - source.crown[1]
        canvas = Image.new("RGBA", frame.size, (0, 0, 0, 0)) if frame.master in hidden_masters \
            else translate_integer(master, dx, dy, frame.size)
        if frame.master not in hidden_masters:
            findings.extend(f"frame {frame.offset}: {message}" for message in validate_palette(canvas, policy, whitelist=whitelist))
            findings.extend(
                f"frame {frame.offset}: {message}"
                for message in validate_overlap(canvas, kind, template, frame.master, phase_delta=(dx, dy))
            )
        frames.append(CompiledFrame(frame.offset, frame.master, canvas, derive_sprite(canvas) if canvas.getbbox() else None))
    return CompileResult(tuple(frames), tuple(findings))


def compose_look(base_frames: list[Image.Image], layers: list[CompileResult]) -> tuple[Image.Image, ...]:
    if len(base_frames) != 18 or any(len(layer.frames) != 18 for layer in layers):
        raise ValueError("legacy Looks require exactly 18 frames")
    output: list[Image.Image] = []
    for offset, base in enumerate(base_frames):
        canvas = base.convert("RGBA").copy()
        for layer in layers:
            canvas.alpha_composite(layer.frames[offset].canvas)
        output.append(canvas)
    return tuple(output)
