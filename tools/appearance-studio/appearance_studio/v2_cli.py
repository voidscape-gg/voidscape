from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .v2_archive import validate_v2_pack
from .v2_build import build_v2_workspace
from .v2_catalog import DEFAULT_CATALOG, load_v2_catalog
from .v2_oracle import compare_v2_java_oracle
from .v2_template import DEFAULT_TEMPLATE, load_v2_template
from .v2_workspace import init_v2_collection_workspace, init_v2_workspace, load_v2_workspace


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Voidscape Paperdoll V2 non-shipping proof compiler")
    commands = parser.add_subparsers(dest="command", required=True)
    init = commands.add_parser("init", help="create a tmp-only six-master proof workspace")
    init.add_argument("--name", required=True)
    init.add_argument("--out", type=Path, required=True)
    init.add_argument("--template", type=Path, default=DEFAULT_TEMPLATE)
    init.add_argument("--force", action="store_true")
    collection = commands.add_parser("collection", help="materialize the content-owned V2 catalog under tmp")
    collection.add_argument("--out", type=Path, required=True)
    collection.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    collection.add_argument("--force", action="store_true")
    build = commands.add_parser("build", help="compile, pack, validate, and render the proof workspace")
    build.add_argument("--workspace", type=Path, required=True)
    validate = commands.add_parser("validate", help="validate a workspace without producing output")
    validate.add_argument("--workspace", type=Path, required=True)
    pack = commands.add_parser("validate-pack", help="strictly validate Paperdoll_V2.orsc")
    pack.add_argument("--pack", type=Path, required=True)
    template = commands.add_parser("validate-template", help="validate the locked 2x template")
    template.add_argument("--template", type=Path, default=DEFAULT_TEMPLATE)
    catalog = commands.add_parser("validate-catalog", help="validate selectors, bases, sources, QA, and hat policy")
    catalog.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    oracle = commands.add_parser("compare-oracle", help="compare Python pack/V2-only rasters to Java")
    oracle.add_argument("--workspace", type=Path, required=True)
    oracle.add_argument("--report", type=Path, required=True)
    oracle.add_argument("--out", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "init":
            payload = init_v2_workspace(args.name, args.out, template_path=args.template, force=args.force)
            output: object = {"workspace": str(args.out.resolve()), "manifest": payload}
        elif args.command == "collection":
            payload = init_v2_collection_workspace(args.out, catalog_path=args.catalog, force=args.force)
            output = {"workspace": str(args.out.resolve()), "manifest": payload}
        elif args.command == "build":
            output = build_v2_workspace(args.workspace)
        elif args.command == "validate":
            root, payload, template = load_v2_workspace(args.workspace)
            output = {"valid": True, "workspace": str(root), "name": payload["name"],
                      "template": template.key, "templateSha256": template.digest}
        elif args.command == "validate-pack":
            output = validate_v2_pack(args.pack)
        elif args.command == "validate-template":
            template = load_v2_template(args.template)
            output = {"valid": True, "template": template.key, "sha256": template.digest,
                      "sourceV1Sha256": template.source_digest,
                      "derivedMaskTreeSha256": template.derived_mask_tree_sha256}
        elif args.command == "validate-catalog":
            catalog = load_v2_catalog(args.catalog)
            output = {"valid": True, "catalog": str(catalog.path), "sha256": catalog.digest,
                      "baseProfiles": [item["id"] for item in catalog.base_profiles],
                      "selectors": [0] + [item["selectorId"] for item in catalog.hairstyles]}
        elif args.command == "compare-oracle":
            output = compare_v2_java_oracle(args.workspace, args.report, args.out)
        else:  # pragma: no cover - argparse makes this unreachable
            raise ValueError(f"unsupported command {args.command}")
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(output, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
