from __future__ import annotations

from typing import Any, Mapping


REVOKED_DRAFT_REPORT_SCHEMAS = frozenset({"voidscape-draft-look/v1"})
CURRENT_DRAFT_REPORT_SCHEMA = "voidscape-draft-look/v2"
CURRENT_GEOMETRY_CONTRACT = "voidscape-paperdoll-geometry/v2"

REVOCATION_REASON = (
    "it was generated before pose-specific anatomical anchors and a client-faithful "
    "preview geometry contract were required"
)


def require_current_evidence_contract(report: Mapping[str, Any]) -> None:
    """Reject evidence that predates the approved appearance-geometry remediation."""
    schema = report.get("schema")
    if schema in REVOKED_DRAFT_REPORT_SCHEMAS:
        raise ValueError(f"draft evidence schema {schema!r} is revoked because {REVOCATION_REASON}")
    if schema != CURRENT_DRAFT_REPORT_SCHEMA:
        raise ValueError(
            f"draft evidence schema {schema!r} is unsupported; expected {CURRENT_DRAFT_REPORT_SCHEMA!r}"
        )
    geometry_contract = report.get("geometryContract")
    if geometry_contract != CURRENT_GEOMETRY_CONTRACT:
        raise ValueError(
            f"draft evidence geometry contract {geometry_contract!r} is unsupported; "
            f"expected {CURRENT_GEOMETRY_CONTRACT!r}"
        )


__all__ = [
    "CURRENT_DRAFT_REPORT_SCHEMA",
    "CURRENT_GEOMETRY_CONTRACT",
    "REVOKED_DRAFT_REPORT_SCHEMAS",
    "REVOCATION_REASON",
    "require_current_evidence_contract",
]
