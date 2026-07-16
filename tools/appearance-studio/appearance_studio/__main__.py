from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .audit import audit_registry
from .paths import REGISTRY_PATH
from .plan import build_plan, canonical_json
from .registry import load_registry
from .workbench_report import (
    cowboy_asset_snapshot, validate_report as validate_workbench_report,
    verify_cowboy_immutability, write_evidence,
)
from .cowboy_compare import compare_cowboy
from .studio_server import open_workspace, serve_studio
from .synthetic_demo import build_synthetic_demo
from .publisher import PublisherError, apply_candidate, undo_candidate, validate_candidate
from .paths import REPO_ROOT
from .candidate import CandidateError, stage_candidate
from .publisher import build_candidate
from .look import load_look_manifest, recommend_look_allocation
from .draft_look import build_draft_look
from .draft_review import DraftReview, serve_draft_review
from .art_qa import validate_draft_root
from .calibration import render_evidence as render_calibration_evidence, write_masks as write_calibration_masks
from .client_preview import extract_composite_fixture, write_oracle_comparison
from .template import load_template
from .adopted_diagnostic import write_cowboy_diagnostic
from .semantic_evidence import write_semantic_evidence
from .manual_headwear import (
    DEFAULT_TEMPLATE as DEFAULT_HEADWEAR_TEMPLATE,
    REFERENCE_BASES as HEADWEAR_REFERENCES,
    build_headwear_workspace,
    init_headwear_workspace,
)


def _load(path: Path):
    registry, findings = load_registry(path)
    if registry is None:
        for finding in findings:
            print(f"ERROR [{finding.code}] {finding.message}", file=sys.stderr)
        return None, findings
    return registry, findings


def _validate(args: argparse.Namespace) -> int:
    registry, findings = _load(Path(args.registry))
    if registry is None:
        return 2
    audit_findings, inventory = audit_registry(registry)
    findings.extend(audit_findings)
    for finding in sorted(findings, key=lambda value: (value.severity, value.code, value.path or "", value.message)):
        suffix = f" ({finding.path})" if finding.path else ""
        print(f"{finding.severity.upper()} [{finding.code}] {finding.message}{suffix}")
    errors = sum(finding.severity == "error" for finding in findings)
    warnings = sum(finding.severity == "warning" for finding in findings)
    if inventory:
        values = inventory["inventory"]
        print(f"inventory: client={values['client']['count']} entries, server={values['server']['count']} entries, unowned={values['unowned_numeric_entries']}")
    print(f"appearance validation: {errors} error(s), {warnings} warning(s)")
    return 1 if errors or (args.strict and warnings) else 0


def _plan(args: argparse.Namespace) -> int:
    registry, findings = _load(Path(args.registry))
    if registry is None:
        return 2
    errors = [finding for finding in findings if finding.severity == "error"]
    if errors:
        for finding in errors:
            print(f"ERROR [{finding.code}] {finding.message}", file=sys.stderr)
        return 1
    try:
        rendered = canonical_json(build_plan(registry))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    if args.out:
        out = Path(args.out)
        if out.exists() and not args.force:
            print(f"error: refusing to overwrite {out}; pass --force", file=sys.stderr)
            return 2
        out.write_text(rendered)
    else:
        sys.stdout.write(rendered)
    return 0


def _verify_workbench(args: argparse.Namespace) -> int:
    try:
        manifest, contact_sheet = write_evidence(Path(args.report), Path(args.out))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(f"validated 30 appearance states: {manifest}")
    print(f"contact sheet: {contact_sheet}")
    return 0


def _compare_workbench(args: argparse.Namespace) -> int:
    report_path = Path(args.report).resolve()
    fixture_path = Path(args.fixture).resolve()
    try:
        report = json.loads(report_path.read_text())
        errors = validate_workbench_report(report)
        if errors:
            raise ValueError("; ".join(errors))
        if not fixture_path.is_file():
            extract_composite_fixture(report_path, fixture_path)
        result = write_oracle_comparison(
            fixture_path, report_path, Path(args.out), load_template(Path(args.template)),
            fail_on_mismatch=not args.allow_mismatch,
        )
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["valid"] else 1


def _cowboy_asset_snapshot(args: argparse.Namespace) -> int:
    payload = cowboy_asset_snapshot()
    rendered = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered)
    else:
        sys.stdout.write(rendered)
    return 0


def _verify_cowboy_immutability(args: argparse.Namespace) -> int:
    try:
        report = verify_cowboy_immutability(Path(args.before), Path(args.after), Path(args.out))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["unchanged"] else 1


def _cowboy_compare(args: argparse.Namespace) -> int:
    report = compare_cowboy(Path(args.frames))
    print(json.dumps(report, indent=2, sort_keys=True))
    return 1 if report["changedFrames"] else 0


def _studio(args: argparse.Namespace) -> int:
    try:
        workspace = open_workspace(Path(args.template), args.workspace, cowboy=args.cowboy)
        serve_studio(workspace, args.host, args.port)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    return 0


def _synthetic_demo(args: argparse.Namespace) -> int:
    try:
        report = build_synthetic_demo(Path(args.template), Path(args.out))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr); return 1
    print(json.dumps(report, indent=2, sort_keys=True)); return 0


def _candidate_bundle(document: str, expected_name: str) -> Path:
    path = Path(document).resolve()
    if path.name != expected_name:
        raise ValueError(f"expected a path ending in {expected_name}")
    return path.parent


def _validate_publish_plan(args: argparse.Namespace) -> int:
    try:
        report = validate_candidate(
            REPO_ROOT,
            _candidate_bundle(args.plan, "plan.json"),
            profile=args.profile,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["valid"] else 1


def _publish_plan(args: argparse.Namespace) -> int:
    registry, findings = _load(Path(args.registry))
    if registry is None:
        return 2
    errors = [finding for finding in findings if finding.severity == "error"]
    if errors:
        for finding in errors:
            print(f"ERROR [{finding.code}] {finding.message}", file=sys.stderr)
        return 1
    bundle = Path(args.bundle).resolve()
    candidate_root = bundle / "candidate"
    try:
        candidate = stage_candidate(registry, candidate_root, repo_root=REPO_ROOT)
        writes = {
            output["path"]: candidate_root / output["path"]
            for output in candidate["outputs"]
        }
        plan = build_candidate(REPO_ROOT, writes, bundle, profile=registry.profile)
    except (OSError, ValueError, CandidateError, PublisherError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({
        "bytePreserving": candidate["bytePreserving"],
        "operations": len(plan["operations"]),
        "plan": str(bundle / "plan.json"),
        "plan_id": plan["plan_id"],
        "profile": plan["profile"],
    }, indent=2, sort_keys=True))
    return 0


def _look_plan(args: argparse.Namespace) -> int:
    registry, findings = _load(Path(args.registry))
    if registry is None:
        return 2
    errors = [finding for finding in findings if finding.severity == "error"]
    if errors:
        for finding in errors:
            print(f"ERROR [{finding.code}] {finding.message}", file=sys.stderr)
        return 1
    try:
        look = load_look_manifest(Path(args.manifest))
        plan = recommend_look_allocation(look, registry)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(plan, indent=2, sort_keys=True))
    return 0


def _draft_look(args: argparse.Namespace) -> int:
    try:
        report = build_draft_look(Path(args.manifest), Path(args.template), Path(args.out))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


def _review_draft_look(args: argparse.Namespace) -> int:
    try:
        serve_draft_review(DraftReview(Path(args.root)), args.host, args.port)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


def _art_qa(args: argparse.Namespace) -> int:
    try:
        report = validate_draft_root(Path(args.root), Path(args.manifest), Path(args.template))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["valid"] else 1


def _calibrate_template(args: argparse.Namespace) -> int:
    try:
        if args.write_masks:
            write_calibration_masks()
        report = render_calibration_evidence(Path(args.out).resolve())
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


def _diagnose_adopted_cowboy(args: argparse.Namespace) -> int:
    try:
        report = write_cowboy_diagnostic(
            Path(args.fixture), load_template(Path(args.template)), Path(args.out),
        )
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({
        "schema": report["schema"], "mechanicallyValid": report["mechanicallyValid"],
        "knownBad": report["knownBad"], "visuallyAcceptable": report["visuallyAcceptable"],
        "warningCount": report["warningCount"], "report": str(Path(args.out) / "report.json"),
    }, indent=2, sort_keys=True))
    # Visual findings are warnings by contract. Mechanical drift is an input
    # integrity failure and must not be mistaken for a successful diagnostic.
    return 0 if report["mechanicallyValid"] else 1


def _semantic_evidence(args: argparse.Namespace) -> int:
    try:
        report = write_semantic_evidence(
            load_template(Path(args.template)), Path(args.out),
        )
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({
        "schema": report["schema"], "shipping": report["shipping"],
        "containsArt": report["containsArt"], "humanVisualApproval": report["humanVisualApproval"],
        "artifactCount": len(report["artifacts"]), "report": str(Path(args.out) / "summary.json"),
    }, indent=2, sort_keys=True))
    return 0


def _headwear_init(args: argparse.Namespace) -> int:
    try:
        workspace = init_headwear_workspace(
            args.name, args.reference, Path(args.out),
            template_path=Path(args.template), force=args.force,
        )
    except (OSError, ValueError, FileExistsError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({
        "schema": workspace["schema"], "shipping": workspace["shipping"],
        "workspace": str(Path(args.out).resolve()), "reference": workspace["reference"]["name"],
        "next": f"edit the six PNGs under {Path(args.out) / 'masters'}, then run headwear-build",
    }, indent=2, sort_keys=True))
    return 0


def _headwear_build(args: argparse.Namespace) -> int:
    try:
        report = build_headwear_workspace(Path(args.root))
    except (OSError, ValueError, FileNotFoundError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({
        "schema": report["schema"], "valid": report["valid"],
        "shipping": report["shipping"], "frames": report["frameCount"],
        "previews": report["previewCount"],
        "contactSheet": str((Path(args.root) / "build/contact-sheet.png").resolve()),
        "report": str((Path(args.root) / "build/report.json").resolve()),
    }, indent=2, sort_keys=True))
    return 0


def _apply_publish_plan(args: argparse.Namespace) -> int:
    try:
        result = apply_candidate(
            REPO_ROOT,
            _candidate_bundle(args.plan, "plan.json"),
            profile=args.profile,
        )
    except (OSError, ValueError, PublisherError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


def _undo_publish(args: argparse.Namespace) -> int:
    try:
        result = undo_candidate(
            REPO_ROOT,
            _candidate_bundle(args.manifest, "undo.json"),
            profile=args.profile,
            force=args.force,
        )
    except (OSError, ValueError, PublisherError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="scripts/content.sh appearance", description="Voidscape Appearance Studio read-only registry audit")
    parser.add_argument("--registry", default=str(REGISTRY_PATH), help="appearance registry YAML")
    sub = parser.add_subparsers(dest="command", required=True)
    validate = sub.add_parser("validate", help="validate registry ownership and adopted repository assets")
    validate.add_argument("--strict", action="store_true", help="treat warnings as failures")
    validate.set_defaults(func=_validate)
    plan = sub.add_parser("plan", help="emit a deterministic read-only allocation plan")
    plan.add_argument("--out", default=None, help="optional plan JSON path")
    plan.add_argument("--force", action="store_true", help="overwrite --out")
    plan.set_defaults(func=_plan)
    verify = sub.add_parser("verify-workbench", help="validate a 30-state Workbench report and write QA evidence")
    verify.add_argument("--report", required=True, help="Workbench response JSON")
    verify.add_argument("--out", required=True, help="QA evidence output directory")
    verify.set_defaults(func=_verify_workbench)
    compare_workbench = sub.add_parser("compare-workbench", help="compare the offline compositor with authoritative isolated Workbench rasters")
    compare_workbench.add_argument("--report", required=True, help="30-state Workbench appearance report")
    compare_workbench.add_argument("--fixture", required=True, help="extracted Workbench composite fixture JSON")
    compare_workbench.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    compare_workbench.add_argument("--out", required=True, help="comparison evidence directory under tmp/")
    compare_workbench.add_argument("--allow-mismatch", action="store_true", help="write diagnostics before returning failure")
    compare_workbench.set_defaults(func=_compare_workbench)
    asset_snapshot = sub.add_parser("cowboy-asset-snapshot", help="hash Cowboy source frames and both Authentic archives")
    asset_snapshot.add_argument("--out", default=None)
    asset_snapshot.set_defaults(func=_cowboy_asset_snapshot)
    immutable = sub.add_parser("verify-cowboy-immutability", help="prove Cowboy frames and archives did not change during QA")
    immutable.add_argument("--before", required=True)
    immutable.add_argument("--after", required=True)
    immutable.add_argument("--out", required=True)
    immutable.set_defaults(func=_verify_cowboy_immutability)
    compare = sub.add_parser("cowboy-compare", help="prove adopted Cowboy art still matches both archives")
    compare.add_argument("--frames", default="content/custom/cowboy_hat/art/final/worn")
    compare.set_defaults(func=_cowboy_compare)
    studio = sub.add_parser("studio", help="serve the local layer editor")
    studio.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    studio.add_argument("--workspace", default="tmp/appearance-studio/workspace")
    studio.add_argument("--cowboy", action="store_true", help="open adopted Cowboy in read-only comparison mode")
    studio.add_argument("--host", default="127.0.0.1")
    studio.add_argument("--port", type=int, default=18789)
    studio.set_defaults(func=_studio)
    demo = sub.add_parser("synthetic-demo", help="compile the non-shipping hair + mustache Gate 3 fixture")
    demo.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    demo.add_argument("--out", default="tmp/appearance-studio/gate3/synthetic-look")
    demo.set_defaults(func=_synthetic_demo)
    validate_plan = sub.add_parser("validate-plan", help="verify a candidate plan and all target preimages")
    validate_plan.add_argument("--plan", required=True, help="path to the candidate plan.json")
    validate_plan.add_argument("--profile", required=True, choices=["authentic"])
    validate_plan.set_defaults(func=_validate_publish_plan)
    publish_plan = sub.add_parser("publish-plan", help="stage generated outputs and emit a preimage-hashed plan")
    publish_plan.add_argument("--bundle", required=True, help="empty run directory, normally under tmp/appearance-studio")
    publish_plan.set_defaults(func=_publish_plan)
    look_plan = sub.add_parser("look-plan", help="validate a Look and emit advisory-only allocation guidance")
    look_plan.add_argument("--manifest", required=True, help="Look manifest YAML")
    look_plan.set_defaults(func=_look_plan)
    draft_look = sub.add_parser("draft-look", help="generate deterministic non-shipping Look masters and previews under tmp/")
    draft_look.add_argument("--manifest", default="content/appearance/proposals/future_mullet_mustache.yaml")
    draft_look.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    draft_look.add_argument("--out", default="tmp/appearance-studio/gate5/mullet-mustache-draft")
    draft_look.set_defaults(func=_draft_look)
    review_look = sub.add_parser("review-draft-look", help="serve read-only browser review for generated tmp Look frames")
    review_look.add_argument("--root", default="tmp/appearance-studio/gate5/mullet-mustache-draft")
    review_look.add_argument("--host", default="127.0.0.1")
    review_look.add_argument("--port", type=int, default=18790)
    review_look.set_defaults(func=_review_draft_look)
    art_qa = sub.add_parser("art-qa", help="validate deterministic non-shipping Look draft geometry and hashes")
    art_qa.add_argument("--root", default="tmp/appearance-studio/gate5/mullet-mustache-draft")
    art_qa.add_argument("--manifest", default="content/appearance/proposals/future_mullet_mustache.yaml")
    art_qa.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    art_qa.set_defaults(func=_art_qa)
    calibrate = sub.add_parser("calibrate-template", help="verify canonical pose masks and render non-shipping calibration evidence")
    calibrate.add_argument("--out", default="tmp/appearance-studio/r1/calibration")
    calibrate.add_argument("--write-masks", action="store_true", help="deterministically rebuild checked-in normative masks before verification")
    calibrate.set_defaults(func=_calibrate_template)
    diagnose = sub.add_parser(
        "diagnose-adopted-cowboy",
        help="write non-shipping warnings/evidence for the adopted known-bad Cowboy hat",
    )
    diagnose.add_argument(
        "--fixture", default="tmp/workbench-r2/appearance-qa/appearance-245/composite-fixture.json",
        help="digest-bound exact-compositor fixture from R2 Workbench QA",
    )
    diagnose.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    diagnose.add_argument("--out", default="tmp/appearance-studio/r3/cowboy-diagnostic")
    diagnose.set_defaults(func=_diagnose_adopted_cowboy)
    semantic_evidence = sub.add_parser(
        "semantic-evidence",
        help="write deterministic non-art semantic contract fixtures under tmp/",
    )
    semantic_evidence.add_argument("--template", default="content/appearance/templates/rsc-player-v1/template.yaml")
    semantic_evidence.add_argument("--out", default="tmp/appearance-studio/r3/semantic-evidence")
    semantic_evidence.set_defaults(func=_semantic_evidence)
    headwear_init = sub.add_parser(
        "headwear-init",
        help="create a tmp-only six-pose headwear workspace from an authentic reference",
    )
    headwear_init.add_argument("--name", required=True, help="workspace slug")
    headwear_init.add_argument("--reference", default="partyhat", choices=sorted(HEADWEAR_REFERENCES))
    headwear_init.add_argument("--out", required=True, help="workspace directory under tmp/")
    headwear_init.add_argument("--template", default=str(DEFAULT_HEADWEAR_TEMPLATE))
    headwear_init.add_argument("--force", action="store_true", help="replace an existing tmp workspace")
    headwear_init.set_defaults(func=_headwear_init)
    headwear_build = sub.add_parser(
        "headwear-build",
        help="compile six headwear masters into 18 frames and 30 actual-size previews",
    )
    headwear_build.add_argument("--root", required=True, help="headwear workspace under tmp/")
    headwear_build.set_defaults(func=_headwear_build)
    apply = sub.add_parser("apply", help="atomically apply a preimage-hashed candidate plan")
    apply.add_argument("--plan", required=True, help="path to the candidate plan.json")
    apply.add_argument("--profile", required=True, choices=["authentic"])
    apply.set_defaults(func=_apply_publish_plan)
    undo = sub.add_parser("undo", help="restore a publish using its guarded undo manifest")
    undo.add_argument("--manifest", required=True, help="path to the applied candidate's undo.json")
    undo.add_argument("--profile", required=True, choices=["authentic"])
    undo.add_argument("--force", action="store_true", help="discard later target edits and restore plan preimages")
    undo.set_defaults(func=_undo_publish)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
