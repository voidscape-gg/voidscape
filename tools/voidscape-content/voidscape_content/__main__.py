from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .manifest import VALID_KINDS, is_valid_slug, scaffold_pack
from .model_export import (
    DEFAULT_MODELS_ARCHIVE,
    derive_scale,
    import_model_to_archive,
    load_source_mesh,
    source_mesh_to_ob3,
)
from .ob3 import write_ob3
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


def _cmd_appearance(args: argparse.Namespace) -> int:
    try:
        from appearance_studio.__main__ import main as appearance_main
    except ImportError as exc:
        print(f"error: could not import appearance studio tool: {exc}", file=sys.stderr)
        return 2
    return appearance_main(args.appearance_args)


def _normalize_model_name(name: str) -> str:
    return name[:-4] if name.lower().endswith(".ob3") else name


def _print_scale_info(scale_info) -> None:
    if scale_info is None:
        return
    print(
        "scale: "
        f"{scale_info.scale:.6g} "
        f"(matched {scale_info.source_model}.ob3 max dimension "
        f"{scale_info.source_max_dimension} from bounds {scale_info.source_bounds})"
    )


def _cmd_model_import(args: argparse.Namespace) -> int:
    input_path = Path(args.input)
    archive_path = Path(args.archive)
    output_name = _normalize_model_name(args.name)
    try:
        if args.commit:
            model, ob3_bytes, scale_info = import_model_to_archive(
                input_path,
                output_name=output_name,
                archive_path=archive_path,
                scale_from=args.scale_from,
                scale=args.scale,
                axis=args.axis,
                center=not args.no_center,
                ground=not args.no_ground,
                double_sided=not args.single_sided,
                replace=not args.no_replace,
            )
            if args.out_ob3:
                Path(args.out_ob3).write_bytes(ob3_bytes)
            print(f"inserted {output_name}.ob3 into {archive_path}")
        else:
            mesh = load_source_mesh(input_path)
            transformed = [
                # source_mesh_to_ob3 will apply the same axis again; this copy is
                # only for measuring the exact default scale source.
                (vertex[0], -vertex[2], vertex[1]) if args.axis == "blender" else vertex
                for vertex in mesh.vertices
            ]
            scale_info = None
            scale = args.scale
            if scale is None:
                scale_info = derive_scale(
                    archive_path=archive_path,
                    stock_model=args.scale_from,
                    input_vertices=transformed,
                )
                scale = scale_info.scale
            model = source_mesh_to_ob3(
                mesh,
                scale=scale,
                axis=args.axis,
                center=not args.no_center,
                ground=not args.no_ground,
                double_sided=not args.single_sided,
            )
            ob3_bytes = write_ob3(model)
            if args.out_ob3:
                Path(args.out_ob3).write_bytes(ob3_bytes)
                print(f"wrote {args.out_ob3}")
            print(f"dry run: would insert {output_name}.ob3 into {archive_path}")
        _print_scale_info(scale_info)
        print(f"ob3: {len(model.vertices)} vertices, {len(model.faces)} faces, {len(ob3_bytes)} bytes")
        return 0
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


def add_model_subcommands(parser: argparse.ArgumentParser) -> None:
    sub = parser.add_subparsers(dest="model_command", required=True)
    p_import = sub.add_parser("import", help="convert OBJ/glTF to OB3 and insert into models.orsc")
    p_import.add_argument("input", help="source .obj, .gltf, or .glb")
    p_import.add_argument("--name", required=True, help="archive model name, with or without .ob3")
    p_import.add_argument("--archive", default=str(DEFAULT_MODELS_ARCHIVE), help="models.orsc path")
    p_import.add_argument("--scale-from", default="crate", help="stock OB3 model used for empirical scale")
    p_import.add_argument("--scale", type=float, default=None, help="explicit float-to-RSC scale override")
    p_import.add_argument(
        "--axis",
        default="blender",
        help="axis mapping: blender=x,-z,y; rsc=x,y,z; or custom like +x-z+y",
    )
    p_import.add_argument("--single-sided", action="store_true", help="write back faces as transparent")
    p_import.add_argument("--no-center", action="store_true", help="do not center X/Z around the model origin")
    p_import.add_argument("--no-ground", action="store_true", help="do not move the top of RSC Y to ground level")
    p_import.add_argument("--out-ob3", default=None, help="also write the generated OB3 bytes to this path")
    p_import.add_argument("--commit", action="store_true", help="write the archive update")
    p_import.add_argument("--no-replace", action="store_true", help="fail if the archive entry already exists")
    p_import.set_defaults(func=_cmd_model_import)


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

    p_appearance = sub.add_parser(
        "appearance",
        help="validate and plan manifest-driven player appearances",
        add_help=False,
    )
    p_appearance.add_argument("appearance_args", nargs=argparse.REMAINDER)
    p_appearance.set_defaults(func=_cmd_appearance)

    p_ui = sub.add_parser("ui", help="inspect, validate, preview, and ingest Voidscape UI assets")
    add_ui_subcommands(p_ui)

    p_model = sub.add_parser("model", help="convert OBJ/glTF meshes into OB3 model cache entries")
    add_model_subcommands(p_model)

    return parser


def main(argv: list[str] | None = None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    if argv and argv[0] == "voidscim":
        return _cmd_voidscim(argparse.Namespace(voidscim_args=argv[1:]))
    if argv and argv[0] == "appearance":
        return _cmd_appearance(argparse.Namespace(appearance_args=argv[1:]))
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
