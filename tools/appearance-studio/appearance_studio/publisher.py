"""Transactional, format-agnostic publisher for generated appearance outputs.

The publisher never generates game data.  It snapshots a set of candidate
writes into a self-contained bundle, verifies that neither the bundle nor its
targets have changed, and then replaces all targets with rollback on failure.
The same bundle contains the byte preimages needed for a guarded undo.
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import tempfile
from typing import Any, Callable, Iterator, Mapping


PLAN_SCHEMA = "voidscape-appearance-publish-plan/v1"
UNDO_SCHEMA = "voidscape-appearance-undo/v1"
CANDIDATE_SCHEMA = "voidscape-appearance-candidate/v1"
TOOL_NAME = "voidscape-appearance-studio"
TOOL_VERSION = "1"
SUPPORTED_PROFILE = "authentic"
FaultInjector = Callable[[str, str, int], None]


class PublisherError(RuntimeError):
    """Base class for a refused or failed publish operation."""


class CandidateValidationError(PublisherError):
    """The candidate bundle is malformed or has been modified."""


class StaleCandidateError(PublisherError):
    """A target no longer matches the preimage captured by the plan."""


class LaterEditError(PublisherError):
    """Undo refused because a published target was edited afterward."""


class TransactionError(PublisherError):
    """A replacement failed; successfully replaced targets were rolled back."""


@dataclass(frozen=True)
class _Snapshot:
    exists: bool
    sha256: str | None
    size: int
    mode: int | None
    content: bytes | None


def _digest(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _canonical(payload: Mapping[str, Any]) -> bytes:
    return (json.dumps(payload, indent=2, sort_keys=True, separators=(",", ": ")) + "\n").encode("utf-8")


def _with_integrity(payload: dict[str, Any], field: str) -> dict[str, Any]:
    unsigned = dict(payload)
    unsigned.pop(field, None)
    result = dict(unsigned)
    result[field] = _digest(_canonical(unsigned))
    return result


def _verify_integrity(payload: dict[str, Any], field: str) -> None:
    value = payload.get(field)
    if not isinstance(value, str) or len(value) != 64:
        raise CandidateValidationError(f"missing or invalid {field}")
    expected = _with_integrity(payload, field)[field]
    if value != expected:
        raise CandidateValidationError(f"{field} does not match document contents")


def _relative_target(value: str) -> str:
    if not isinstance(value, str) or not value:
        raise CandidateValidationError("target must be a non-empty repository-relative path")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "." in path.parts or value != path.as_posix():
        raise CandidateValidationError(f"unsafe or non-canonical target path: {value!r}")
    return value


def _target_path(repo_root: Path, relative: str) -> Path:
    relative = _relative_target(relative)
    root = repo_root.resolve()
    target = root.joinpath(*PurePosixPath(relative).parts)
    try:
        target.resolve(strict=False).relative_to(root)
    except ValueError as exc:
        raise CandidateValidationError(f"target escapes repository: {relative}") from exc
    if target.is_symlink():
        raise CandidateValidationError(f"symbolic-link targets are unsupported: {relative}")
    return target


def _snapshot(path: Path, *, include_content: bool) -> _Snapshot:
    if not path.exists():
        return _Snapshot(False, None, 0, None, None)
    if path.is_symlink() or not path.is_file():
        raise CandidateValidationError(f"target is not a regular file: {path}")
    content = path.read_bytes()
    mode = stat.S_IMODE(path.stat().st_mode)
    return _Snapshot(True, _digest(content), len(content), mode, content if include_content else None)


def _blob_path(bundle_dir: Path, kind: str, digest: str) -> Path:
    if len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest):
        raise CandidateValidationError(f"invalid {kind} digest: {digest!r}")
    path = bundle_dir / kind / digest
    root = bundle_dir.resolve()
    try:
        path.resolve(strict=False).relative_to(root)
    except ValueError as exc:
        raise CandidateValidationError(f"invalid {kind} blob path") from exc
    return path


def _write_new_blob(path: Path, content: bytes) -> None:
    if path.exists():
        if not path.is_file() or path.read_bytes() != content:
            raise CandidateValidationError(f"content-addressed blob collision: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)


def _read_blob(bundle_dir: Path, kind: str, digest: str, expected_size: int) -> bytes:
    path = _blob_path(bundle_dir, kind, digest)
    if not path.is_file():
        raise CandidateValidationError(f"missing {kind} blob {digest}")
    content = path.read_bytes()
    if len(content) != expected_size or _digest(content) != digest:
        raise CandidateValidationError(f"corrupt {kind} blob {digest}")
    return content


def _read_document(path: Path, schema: str, integrity_field: str) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CandidateValidationError(f"cannot read {path.name}: {exc}") from exc
    if not isinstance(payload, dict) or payload.get("schema") != schema:
        raise CandidateValidationError(f"unsupported schema in {path.name}")
    _verify_integrity(payload, integrity_field)
    return payload


def _candidate_bytes(value: bytes | Path) -> bytes:
    if isinstance(value, bytes):
        return value
    if isinstance(value, Path):
        if not value.is_file():
            raise CandidateValidationError(f"candidate source is not a file: {value}")
        return value.read_bytes()
    raise TypeError("candidate values must be bytes or pathlib.Path instances")


def _profile(value: str) -> str:
    if value != SUPPORTED_PROFILE:
        raise CandidateValidationError(
            f"unsupported output profile {value!r}; expected {SUPPORTED_PROFILE!r}"
        )
    return value


def _verify_bindings(payload: dict[str, Any], profile: str) -> None:
    _profile(profile)
    if payload.get("profile") != profile:
        raise CandidateValidationError(
            f"plan profile {payload.get('profile')!r} does not match requested profile {profile!r}"
        )
    if payload.get("candidate_schema") != CANDIDATE_SCHEMA:
        raise CandidateValidationError("candidate schema/version does not match this publisher")
    if payload.get("tool") != {"name": TOOL_NAME, "version": TOOL_VERSION}:
        raise CandidateValidationError("candidate tool/version does not match this publisher")


def build_candidate(
    repo_root: Path,
    writes: Mapping[str, bytes | Path],
    bundle_dir: Path,
    *,
    profile: str = SUPPORTED_PROFILE,
) -> dict[str, Any]:
    """Snapshot candidate payloads and target preimages into a deterministic bundle."""
    profile = _profile(profile)
    root = repo_root.resolve()
    if not root.is_dir():
        raise CandidateValidationError(f"repository root is not a directory: {repo_root}")
    bundle = bundle_dir.resolve()
    if bundle == root or bundle in root.parents:
        raise CandidateValidationError("candidate bundle must not contain the repository root")
    if (bundle / "plan.json").exists():
        raise CandidateValidationError(f"candidate plan already exists: {bundle / 'plan.json'}")
    bundle.mkdir(parents=True, exist_ok=True)

    operations: list[dict[str, Any]] = []
    seen: set[str] = set()
    for raw_target, source in sorted(writes.items(), key=lambda item: item[0]):
        target = _relative_target(raw_target)
        if target in seen:
            raise CandidateValidationError(f"duplicate target: {target}")
        seen.add(target)
        target_path = _target_path(root, target)
        preimage = _snapshot(target_path, include_content=True)
        candidate = _candidate_bytes(source)
        candidate_digest = _digest(candidate)
        _write_new_blob(_blob_path(bundle, "payloads", candidate_digest), candidate)
        preimage_payload: dict[str, Any] = {
            "exists": preimage.exists,
            "mode": preimage.mode,
            "sha256": preimage.sha256,
            "size": preimage.size,
        }
        if preimage.exists:
            assert preimage.content is not None and preimage.sha256 is not None
            _write_new_blob(_blob_path(bundle, "preimages", preimage.sha256), preimage.content)
        operations.append({
            "candidate": {"sha256": candidate_digest, "size": len(candidate)},
            "preimage": preimage_payload,
            "target": target,
        })

    unsigned = {
        "candidate_schema": CANDIDATE_SCHEMA,
        "operations": operations,
        "profile": profile,
        "schema": PLAN_SCHEMA,
        "tool": {"name": TOOL_NAME, "version": TOOL_VERSION},
    }
    plan = _with_integrity(unsigned, "plan_id")
    (bundle / "plan.json").write_bytes(_canonical(plan))
    return plan


def _validate_operations(repo_root: Path, bundle_dir: Path, plan: dict[str, Any], *, require_preimages: bool) -> list[dict[str, Any]]:
    operations = plan.get("operations")
    if not isinstance(operations, list):
        raise CandidateValidationError("plan operations must be a list")
    targets: set[str] = set()
    previous: str | None = None
    for index, operation in enumerate(operations):
        if not isinstance(operation, dict):
            raise CandidateValidationError(f"operation {index} must be an object")
        target = _relative_target(operation.get("target"))
        if target in targets or (previous is not None and target <= previous):
            raise CandidateValidationError("plan targets must be unique and sorted")
        targets.add(target)
        previous = target
        _target_path(repo_root, target)
        candidate = operation.get("candidate")
        preimage = operation.get("preimage")
        if not isinstance(candidate, dict) or not isinstance(preimage, dict):
            raise CandidateValidationError(f"operation {target} lacks candidate/preimage metadata")
        _read_blob(bundle_dir, "payloads", candidate.get("sha256"), candidate.get("size"))
        exists = preimage.get("exists")
        if not isinstance(exists, bool) or not isinstance(preimage.get("size"), int):
            raise CandidateValidationError(f"invalid preimage metadata for {target}")
        if exists:
            if not isinstance(preimage.get("mode"), int) or not isinstance(preimage.get("sha256"), str):
                raise CandidateValidationError(f"invalid file preimage for {target}")
            if require_preimages:
                _read_blob(bundle_dir, "preimages", preimage["sha256"], preimage["size"])
        elif preimage.get("sha256") is not None or preimage.get("mode") is not None or preimage.get("size") != 0:
            raise CandidateValidationError(f"invalid missing-file preimage for {target}")
    return operations


def _preimage_mismatch(repo_root: Path, operation: dict[str, Any]) -> str | None:
    target = operation["target"]
    current = _snapshot(_target_path(repo_root, target), include_content=False)
    expected = operation["preimage"]
    if current.exists != expected["exists"]:
        return f"{target}: existence changed after plan creation"
    if current.exists:
        if current.sha256 != expected["sha256"] or current.size != expected["size"]:
            return f"{target}: content changed after plan creation"
        if current.mode != expected["mode"]:
            return f"{target}: mode changed after plan creation"
    return None


def validate_candidate(
    repo_root: Path,
    bundle_dir: Path,
    *,
    profile: str = SUPPORTED_PROFILE,
) -> dict[str, Any]:
    """Return a non-throwing validation report, including stale-target checks."""
    errors: list[str] = []
    plan_id: str | None = None
    count = 0
    try:
        plan = _read_document(bundle_dir / "plan.json", PLAN_SCHEMA, "plan_id")
        _verify_bindings(plan, profile)
        plan_id = plan["plan_id"]
        operations = _validate_operations(repo_root.resolve(), bundle_dir.resolve(), plan, require_preimages=True)
        count = len(operations)
        errors.extend(filter(None, (_preimage_mismatch(repo_root.resolve(), operation) for operation in operations)))
    except (CandidateValidationError, OSError, TypeError) as exc:
        errors.append(str(exc))
    return {"errors": errors, "operations": count, "plan_id": plan_id, "valid": not errors}


def _atomic_write(path: Path, content: bytes, mode: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.appearance-", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


@contextmanager
def _repository_lock(repo_root: Path) -> Iterator[None]:
    key = _digest(str(repo_root.resolve()).encode("utf-8"))[:24]
    lock_path = Path(tempfile.gettempdir()) / f"voidscape-appearance-publisher-{key}.lock"
    with lock_path.open("a+b") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def _restore_preimage(repo_root: Path, bundle_dir: Path, operation: dict[str, Any]) -> None:
    target = _target_path(repo_root, operation["target"])
    preimage = operation["preimage"]
    if not preimage["exists"]:
        if target.exists():
            target.unlink()
        return
    content = _read_blob(bundle_dir, "preimages", preimage["sha256"], preimage["size"])
    _atomic_write(target, content, preimage["mode"])


def _restore_candidate(repo_root: Path, bundle_dir: Path, operation: dict[str, Any]) -> None:
    target = _target_path(repo_root, operation["target"])
    candidate = operation["candidate"]
    content = _read_blob(bundle_dir, "payloads", candidate["sha256"], candidate["size"])
    mode = operation["preimage"]["mode"] if operation["preimage"]["exists"] else 0o644
    _atomic_write(target, content, mode)


def _run_fault(injector: FaultInjector | None, stage: str, target: str, index: int) -> None:
    if injector is not None:
        injector(stage, target, index)


def apply_candidate(
    repo_root: Path,
    bundle_dir: Path,
    *,
    profile: str,
    fault_injector: FaultInjector | None = None,
) -> dict[str, Any]:
    """Apply a verified bundle and emit a hash-guarded ``undo.json``."""
    root = repo_root.resolve()
    bundle = bundle_dir.resolve()
    with _repository_lock(root):
        plan = _read_document(bundle / "plan.json", PLAN_SCHEMA, "plan_id")
        _verify_bindings(plan, profile)
        operations = _validate_operations(root, bundle, plan, require_preimages=True)
        stale = list(filter(None, (_preimage_mismatch(root, operation) for operation in operations)))
        if stale:
            raise StaleCandidateError("; ".join(stale))

        undo_unsigned = {
            "candidate_schema": plan["candidate_schema"],
            "operations": operations,
            "plan_id": plan["plan_id"],
            "profile": plan["profile"],
            "schema": UNDO_SCHEMA,
            "tool": plan["tool"],
        }
        undo = _with_integrity(undo_unsigned, "undo_id")
        changed: list[dict[str, Any]] = []
        try:
            for index, operation in enumerate(operations):
                candidate = operation["candidate"]
                preimage = operation["preimage"]
                if preimage["exists"] and preimage["sha256"] == candidate["sha256"]:
                    continue
                _run_fault(fault_injector, "apply", operation["target"], index)
                _restore_candidate(root, bundle, operation)
                changed.append(operation)
            _run_fault(fault_injector, "receipt", "undo.json", len(operations))
            _atomic_write(bundle / "undo.json", _canonical(undo), 0o644)
        except Exception as exc:
            rollback_errors: list[str] = []
            for operation in reversed(changed):
                try:
                    _restore_preimage(root, bundle, operation)
                except Exception as rollback_exc:  # pragma: no cover - catastrophic filesystem failure
                    rollback_errors.append(f"{operation['target']}: {rollback_exc}")
            suffix = f"; rollback failures: {rollback_errors}" if rollback_errors else ""
            raise TransactionError(f"publish failed and was rolled back: {exc}{suffix}") from exc
        return {"changed": len(changed), "operations": len(operations), "plan_id": plan["plan_id"], "undo_id": undo["undo_id"]}


def undo_candidate(
    repo_root: Path,
    bundle_dir: Path,
    *,
    profile: str,
    force: bool = False,
    fault_injector: FaultInjector | None = None,
) -> dict[str, Any]:
    """Restore preimages only when every target still equals its postimage."""
    root = repo_root.resolve()
    bundle = bundle_dir.resolve()
    with _repository_lock(root):
        undo = _read_document(bundle / "undo.json", UNDO_SCHEMA, "undo_id")
        _verify_bindings(undo, profile)
        operations = _validate_operations(root, bundle, undo, require_preimages=True)
        later_edits: list[str] = []
        current_snapshots: dict[str, _Snapshot] = {}
        for operation in operations:
            current = _snapshot(_target_path(root, operation["target"]), include_content=True)
            current_snapshots[operation["target"]] = current
            candidate = operation["candidate"]
            preimage = operation["preimage"]
            expected_mode = preimage["mode"] if preimage["exists"] else 0o644
            if (
                not current.exists
                or current.sha256 != candidate["sha256"]
                or current.size != candidate["size"]
                or current.mode != expected_mode
            ):
                later_edits.append(f"{operation['target']}: content changed after apply")
        if later_edits and not force:
            raise LaterEditError("; ".join(later_edits))

        changed: list[dict[str, Any]] = []
        try:
            for index, operation in enumerate(operations):
                candidate = operation["candidate"]
                preimage = operation["preimage"]
                current = current_snapshots[operation["target"]]
                if (
                    preimage["exists"]
                    and preimage["sha256"] == candidate["sha256"]
                    and current.exists
                    and current.sha256 == preimage["sha256"]
                    and current.mode == preimage["mode"]
                ):
                    continue
                _run_fault(fault_injector, "undo", operation["target"], index)
                _restore_preimage(root, bundle, operation)
                changed.append(operation)
        except Exception as exc:
            rollback_errors: list[str] = []
            for operation in reversed(changed):
                try:
                    target = _target_path(root, operation["target"])
                    snapshot = current_snapshots[operation["target"]]
                    if not snapshot.exists:
                        if target.exists():
                            target.unlink()
                    else:
                        assert snapshot.content is not None and snapshot.mode is not None
                        _atomic_write(target, snapshot.content, snapshot.mode)
                except Exception as rollback_exc:  # pragma: no cover - catastrophic filesystem failure
                    rollback_errors.append(f"{operation['target']}: {rollback_exc}")
            suffix = f"; rollback failures: {rollback_errors}" if rollback_errors else ""
            raise TransactionError(f"undo failed and was rolled back: {exc}{suffix}") from exc
        return {
            "changed": len(changed),
            "forced": bool(force and later_edits),
            "operations": len(operations),
            "plan_id": undo["plan_id"],
            "undo_id": undo["undo_id"],
        }


__all__ = [
    "CandidateValidationError",
    "LaterEditError",
    "PublisherError",
    "StaleCandidateError",
    "TransactionError",
    "apply_candidate",
    "build_candidate",
    "undo_candidate",
    "validate_candidate",
]
