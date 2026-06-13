from __future__ import annotations

import argparse
import sys

from .manifest import VALID_KINDS, is_valid_slug, scaffold_pack
from .paths import CUSTOM_CONTENT_DIR
from .report import print_report
from .ui_assets import add_ui_subcommands
from .validate import print_validation, validate_repo


def _cmd_new(args: argparse.Namespace) -> int:
    if args.dry_run:
        if not is_valid_slug(args.slug):
            print("error: slug must use lowercase letters, numbers, hyphens, or underscores", file=sys.stderr)
            return 2
        print(f"would create content pack: {CUSTOM_CONTENT_DIR / args.slug}")
        return 0
    try:
        root = scaffold_pack(
            args.kind,
            args.slug,
            name=args.name,
            description=args.description or "",
            like=args.like,
            force=args.force,
        )
    except (ValueError, FileExistsError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(f"created content pack: {root}")
    print(f"next: edit {root / 'content.yaml'} and run scripts/content.sh validate")
    return 0


def _cmd_voidscim(args: argparse.Namespace) -> int:
    try:
        from voidscim.__main__ import main as voidscim_main
    except ImportError as exc:
        print(f"error: could not import legacy voidscim tool: {exc}", file=sys.stderr)
        return 2
    return voidscim_main(args.voidscim_args)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="scripts/content.sh",
        description="Voidscape custom content factory.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_new = sub.add_parser("new", help="scaffold a content pack")
    p_new.add_argument("kind", choices=VALID_KINDS)
    p_new.add_argument("slug")
    p_new.add_argument("--name", default=None)
    p_new.add_argument("--description", default="")
    p_new.add_argument("--like", default=None, help="source item/NPC id or name to imitate")
    p_new.add_argument("--force", action="store_true", help="refresh missing/generated files")
    p_new.add_argument("--dry-run", action="store_true", help="show target without writing files")
    p_new.set_defaults(func=_cmd_new)

    p_validate = sub.add_parser("validate", help="validate content/client/server alignment")
    p_validate.add_argument("--strict", action="store_true", help="treat warnings as failures")
    p_validate.set_defaults(func=lambda args: print_validation(validate_repo(), strict=args.strict))

    p_report = sub.add_parser("report", help="print current allocation and content status")
    p_report.set_defaults(func=lambda args: print_report())

    p_voidscim = sub.add_parser(
        "voidscim",
        help="delegate to the existing item-art pipeline",
        add_help=False,
    )
    p_voidscim.add_argument("voidscim_args", nargs=argparse.REMAINDER)
    p_voidscim.set_defaults(func=_cmd_voidscim)

    p_ui = sub.add_parser("ui", help="inspect, validate, preview, and ingest Voidscape UI assets")
    add_ui_subcommands(p_ui)

    return parser


def main(argv: list[str] | None = None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    if argv and argv[0] == "voidscim":
        return _cmd_voidscim(argparse.Namespace(voidscim_args=argv[1:]))
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
