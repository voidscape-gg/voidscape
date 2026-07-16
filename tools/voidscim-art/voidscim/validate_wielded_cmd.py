"""Validate an archive-backed wearable against the client's real runtime layout.

The wearable contract crosses three independently-numbered things:

* the zero-based position in ``EntityHandler.animations`` used by the client;
* the one-based server ``appearanceID`` stored on the item; and
* the archive base assigned by ``mudclient.loadEntitiesAuthentic``.

This command resolves all three from source, then inspects every frame that the
AnimationDef says the runtime will load.  It never writes game definitions or
sprite archives, which makes it safe both before and after ``pack-wielded``.
"""
from __future__ import annotations

import json
import zipfile
from dataclasses import dataclass
from pathlib import Path

from .pack_wielded_cmd import (
    AnimEntry,
    _appearance_id_from_runtime_index,
    _frame_count_for,
    _runtime_index_from_appearance_id,
    parse_animations,
    simulate,
)
from .paths import ARCHIVE_PATH, ITEMDEFS_FILES
from .score import halo_residue
from .sprite_io import decode
from .register_cmd import ENTITY_HANDLER


class WieldedValidationError(ValueError):
    """The source definitions cannot form a coherent wearable layout."""


@dataclass(frozen=True)
class WieldedLayout:
    animation_name: str
    item_id: int
    item_name: str
    runtime_index: int
    appearance_id: int
    archive_base: int
    has_a: bool
    has_f: bool
    frame_count: int

    @property
    def archive_end(self) -> int:
        return self.archive_base + self.frame_count - 1


@dataclass(frozen=True)
class ValidationIssue:
    code: str
    message: str
    archive_index: int | None = None

    def format(self) -> str:
        location = f"entry {self.archive_index}: " if self.archive_index is not None else ""
        return f"[{self.code}] {location}{self.message}"


@dataclass(frozen=True)
class ArchiveValidationReport:
    archive: Path
    layout: WieldedLayout
    decoded_frames: int
    opaque_pixels: int
    issues: tuple[ValidationIssue, ...]

    @property
    def ok(self) -> bool:
        return not self.issues


def _find_server_item(item_id: int, itemdefs_files: tuple[Path, ...]) -> dict | None:
    for path in itemdefs_files:
        if not path.exists():
            continue
        try:
            payload = json.loads(path.read_text())
        except (OSError, json.JSONDecodeError) as exc:
            raise WieldedValidationError(f"could not read {path}: {exc}") from exc
        if not isinstance(payload, dict):
            continue
        for value in payload.values():
            if not isinstance(value, list):
                continue
            for item in value:
                if isinstance(item, dict) and item.get("id") == item_id:
                    return item
    return None


def _visible_animations(
    entity_handler_text: str,
    *,
    allow_bearded_ladies: bool,
) -> list[AnimEntry]:
    animations, _ = parse_animations(
        entity_handler_text,
        allow_bearded_ladies=allow_bearded_ladies,
    )
    simulate(animations)
    return [animation for animation in animations if not animation.gated]


def resolve_wielded_layout(
    animation_name: str,
    item_id: int,
    *,
    entity_handler_path: Path = ENTITY_HANDLER,
    itemdefs_files: tuple[Path, ...] = tuple(ITEMDEFS_FILES),
    entity_handler_text: str | None = None,
    server_item: dict | None = None,
    allow_bearded_ladies: bool = False,
) -> WieldedLayout:
    """Resolve and cross-check the real client/server wearable numbering.

    ``entity_handler_text`` and ``server_item`` are dependency seams for tests;
    normal callers resolve both directly from the repository.
    """
    if not animation_name.strip():
        raise WieldedValidationError("animation name must not be empty")
    if item_id < 0:
        raise WieldedValidationError(f"item id must be non-negative, got {item_id}")

    if entity_handler_text is None:
        try:
            entity_handler_text = entity_handler_path.read_text()
        except OSError as exc:
            raise WieldedValidationError(
                f"could not read client EntityHandler at {entity_handler_path}: {exc}"
            ) from exc

    visible = _visible_animations(
        entity_handler_text,
        allow_bearded_ladies=allow_bearded_ladies,
    )
    if server_item is None:
        server_item = _find_server_item(item_id, itemdefs_files)
    if server_item is None:
        raise WieldedValidationError(f"item id {item_id} was not found in server ItemDefs")
    if not bool(server_item.get("isWearable")):
        raise WieldedValidationError(f"item id {item_id} is not marked wearable in server ItemDefs")

    raw_appearance_id = server_item.get("appearanceID")
    if isinstance(raw_appearance_id, bool) or not isinstance(raw_appearance_id, int):
        raise WieldedValidationError(
            f"item id {item_id} has non-integer appearanceID {raw_appearance_id!r}"
        )
    try:
        runtime_index = _runtime_index_from_appearance_id(raw_appearance_id)
    except ValueError as exc:
        raise WieldedValidationError(f"item id {item_id}: {exc}") from exc
    if runtime_index >= len(visible):
        raise WieldedValidationError(
            f"item id {item_id} appearanceID {raw_appearance_id} maps to runtime index "
            f"{runtime_index}, but only {len(visible)} active AnimationDefs exist"
        )

    animation = visible[runtime_index]
    if animation.name.casefold() != animation_name.casefold():
        named_positions = [
            index for index, candidate in enumerate(visible)
            if candidate.name.casefold() == animation_name.casefold()
        ]
        hint = (
            f"; {animation_name!r} is at runtime index/indices {named_positions}"
            if named_positions else f"; no active AnimationDef named {animation_name!r} exists"
        )
        raise WieldedValidationError(
            f"item id {item_id} appearanceID {raw_appearance_id} maps to AnimationDef "
            f"{animation.name!r}, not {animation_name!r}{hint}"
        )
    if animation.number < 0:
        raise WieldedValidationError(
            f"AnimationDef {animation.name!r} did not receive a runtime archive base"
        )

    appearance_id = _appearance_id_from_runtime_index(runtime_index)
    if appearance_id != raw_appearance_id:
        # This should be unreachable when the conversion helpers are correct,
        # but keeping the assertion in the validator protects that contract.
        raise WieldedValidationError(
            f"runtime index {runtime_index} maps back to appearanceID {appearance_id}, "
            f"not item value {raw_appearance_id}"
        )
    frame_count = _frame_count_for(animation.has_a, animation.has_f)
    return WieldedLayout(
        animation_name=animation.name,
        item_id=item_id,
        item_name=str(server_item.get("name", "<unnamed>")),
        runtime_index=runtime_index,
        appearance_id=appearance_id,
        archive_base=animation.number,
        has_a=animation.has_a,
        has_f=animation.has_f,
        frame_count=frame_count,
    )


def _expectation_issues(
    layout: WieldedLayout,
    *,
    expected_runtime_index: int | None,
    expected_appearance_id: int | None,
    expected_archive_base: int | None,
) -> list[ValidationIssue]:
    issues: list[ValidationIssue] = []
    expectations = (
        ("runtime index", expected_runtime_index, layout.runtime_index),
        ("appearanceID", expected_appearance_id, layout.appearance_id),
        ("archive base", expected_archive_base, layout.archive_base),
    )
    for label, expected, actual in expectations:
        if expected is not None and expected != actual:
            issues.append(ValidationIssue(
                "layout-expectation",
                f"expected {label} {expected}, resolved {actual}",
            ))
    return issues


def _frame_issues(data: bytes, archive_index: int) -> tuple[int, list[ValidationIssue]]:
    issues: list[ValidationIssue] = []
    try:
        image, sidecar = decode(data)
    except Exception as exc:  # decode may raise struct, Pillow, or value errors
        return 0, [ValidationIssue("decode", str(exc), archive_index)]

    width, height = image.size
    if width <= 0 or height <= 0:
        issues.append(ValidationIssue(
            "dimensions", f"stored dimensions must be positive, got {width}x{height}", archive_index,
        ))

    logical_width = sidecar["something1"]
    logical_height = sidecar["something2"]
    if logical_width <= 0 or logical_height <= 0:
        issues.append(ValidationIssue(
            "logical-dimensions",
            f"logical canvas must be positive, got {logical_width}x{logical_height}",
            archive_index,
        ))
    else:
        x_shift = sidecar["xShift"]
        y_shift = sidecar["yShift"]
        if x_shift < 0 or x_shift + width > logical_width:
            issues.append(ValidationIssue(
                "sidecar-bounds",
                f"xShift {x_shift} + width {width} falls outside logical width {logical_width}",
                archive_index,
            ))
        if y_shift < 0 or y_shift + height > logical_height:
            issues.append(ValidationIssue(
                "sidecar-bounds",
                f"yShift {y_shift} + height {height} falls outside logical height {logical_height}",
                archive_index,
            ))

    alpha_histogram = image.getchannel("A").histogram()
    opaque_pixels = sum(alpha_histogram[1:])
    if opaque_pixels == 0:
        issues.append(ValidationIssue(
            "empty-frame", "frame has no non-transparent pixels", archive_index,
        ))

    # Legacy wearable sprites are int32 0x00RRGGBB with all-zero transparency.
    # Any non-zero high byte means this entry was packed as ARGB, which the
    # classic hard-mask render path does not promise to handle consistently.
    pixel_bytes = data[25:25 + max(0, width * height * 4)]
    argb_pixels = sum(1 for high_byte in pixel_bytes[0::4] if high_byte != 0)
    if argb_pixels:
        issues.append(ValidationIssue(
            "hard-mask",
            f"{argb_pixels} pixel(s) have a non-zero alpha byte; repack in legacy-mask mode",
            archive_index,
        ))
    soft_alpha_pixels = sum(alpha_histogram[1:255])
    if soft_alpha_pixels:
        issues.append(ValidationIssue(
            "hard-mask",
            f"{soft_alpha_pixels} pixel(s) decode with partial alpha",
            archive_index,
        ))

    for chroma_key in ("#FF00FF", "#00FF00"):
        residue = halo_residue(image, key_hex=chroma_key)
        if residue > 0:
            issues.append(ValidationIssue(
                "chroma-residue",
                f"{residue * 100:.3f}% of non-transparent pixels remain near {chroma_key}",
                archive_index,
            ))
    return opaque_pixels, issues


def validate_wielded_archive(
    layout: WieldedLayout,
    archive: Path,
    *,
    expected_runtime_index: int | None = None,
    expected_appearance_id: int | None = None,
    expected_archive_base: int | None = None,
) -> ArchiveValidationReport:
    """Inspect all frames the resolved AnimationDef will load from ``archive``."""
    archive = Path(archive).resolve()
    issues = _expectation_issues(
        layout,
        expected_runtime_index=expected_runtime_index,
        expected_appearance_id=expected_appearance_id,
        expected_archive_base=expected_archive_base,
    )
    decoded_frames = 0
    opaque_pixels = 0
    if not archive.exists():
        issues.append(ValidationIssue("archive", f"archive not found: {archive}"))
        return ArchiveValidationReport(
            archive, layout, decoded_frames, opaque_pixels, tuple(issues),
        )

    try:
        with zipfile.ZipFile(archive, "r") as sprite_archive:
            for archive_index in range(layout.archive_base, layout.archive_end + 1):
                try:
                    data = sprite_archive.read(str(archive_index))
                except KeyError:
                    issues.append(ValidationIssue(
                        "missing-frame", "numeric archive entry is missing", archive_index,
                    ))
                    continue
                except (OSError, zipfile.BadZipFile) as exc:
                    issues.append(ValidationIssue("archive-read", str(exc), archive_index))
                    continue
                frame_opaque, frame_issues = _frame_issues(data, archive_index)
                if not any(issue.code == "decode" for issue in frame_issues):
                    decoded_frames += 1
                opaque_pixels += frame_opaque
                issues.extend(frame_issues)
    except (OSError, zipfile.BadZipFile) as exc:
        issues.append(ValidationIssue("archive", f"could not open {archive}: {exc}"))

    return ArchiveValidationReport(
        archive=archive,
        layout=layout,
        decoded_frames=decoded_frames,
        opaque_pixels=opaque_pixels,
        issues=tuple(issues),
    )


def _print_layout(layout: WieldedLayout, archive: Path) -> None:
    print(f"item:           id={layout.item_id} ({layout.item_name!r})")
    print(f"AnimationDef:   {layout.animation_name!r}")
    print(f"runtime index:  {layout.runtime_index}")
    print(f"appearanceID:   {layout.appearance_id} (= runtime index + 1)")
    print(f"flags:          hasA={str(layout.has_a).lower()} hasF={str(layout.has_f).lower()}")
    print(f"frame count:    {layout.frame_count} (15 + A:3 + F:9)")
    print(f"archive:        {archive}")
    print(f"archive base:   {layout.archive_base}")
    print(f"archive range:  [{layout.archive_base}..{layout.archive_end}]")


def cmd_validate_wielded(
    animation_name: str,
    item_id: int,
    archive: str | Path = ARCHIVE_PATH,
    *,
    expected_runtime_index: int | None = None,
    expected_appearance_id: int | None = None,
    expected_archive_base: int | None = None,
    allow_bearded_ladies: bool = False,
    layout_only: bool = False,
) -> int:
    try:
        layout = resolve_wielded_layout(
            animation_name,
            item_id,
            allow_bearded_ladies=allow_bearded_ladies,
        )
    except WieldedValidationError as exc:
        print(f"FAIL: {exc}")
        return 1

    archive_path = Path(archive).resolve()
    _print_layout(layout, archive_path)
    expectation_issues = _expectation_issues(
        layout,
        expected_runtime_index=expected_runtime_index,
        expected_appearance_id=expected_appearance_id,
        expected_archive_base=expected_archive_base,
    )
    if layout_only:
        if expectation_issues:
            print(f"\nFAIL: {len(expectation_issues)} layout issue(s)")
            for issue in expectation_issues:
                print(f"  - {issue.format()}")
            return 1
        print("\nPASS: definition layout is coherent (archive frames not inspected)")
        return 0

    report = validate_wielded_archive(
        layout,
        archive_path,
        expected_runtime_index=expected_runtime_index,
        expected_appearance_id=expected_appearance_id,
        expected_archive_base=expected_archive_base,
    )
    print(
        f"decoded:        {report.decoded_frames}/{layout.frame_count} frame(s), "
        f"{report.opaque_pixels} non-transparent pixel(s)"
    )
    if report.ok:
        print("\nPASS: wearable layout and packed frames satisfy the runtime contract")
        return 0

    print(f"\nFAIL: {len(report.issues)} issue(s)")
    for issue in report.issues:
        print(f"  - {issue.format()}")
    return 1
