"""voidscim CLI entry — `python -m voidscim <verb> ...`"""
from __future__ import annotations
import argparse
import sys


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="voidscim", description="RSC sprite HD pipeline.")
    sub = p.add_subparsers(dest="verb", required=True)

    sp_inspect = sub.add_parser("inspect", help="extract+name a sprite by archive index")
    sp_inspect.add_argument("archive_index", type=int)

    sp_init = sub.add_parser("init", help="scaffold a registry entry from a client item id")
    sp_init.add_argument("item_id", type=int)
    sp_init.add_argument("--key", default=None, help="override the auto-slugged item_key")

    sp_gen = sub.add_parser("generate", help="generate N variants with silhouette + scoring")
    sp_gen.add_argument("item_key")
    sp_gen.add_argument("--n", type=int, default=4)
    sp_gen.add_argument("--dry-run", action="store_true")
    sp_gen.add_argument("--confirm-cost", action="store_true", help="required for --n > 4")

    sp_rev = sub.add_parser("review", help="render scored comparison sheet for latest run")
    sp_rev.add_argument("item_key")

    sp_new = sub.add_parser("new-icon", help="generate a brand-new item icon in coherent RSC style")
    sp_new.add_argument("description", help="what the new item is, e.g. 'a glowing void crystal'")
    sp_new.add_argument("--n", type=int, default=4, help="variants per call (default 4)")
    sp_new.add_argument("--dry-run", action="store_true", help="build reference + prompt only, no API call")

    sp_var = sub.add_parser("variations", help="generate 2×2 grid variations of an existing item")
    sp_var.add_argument("item_id", type=int, help="client item id to riff on, e.g. 81 for rune 2H sword")
    sp_var.add_argument("description", nargs="?", default=None, help="how the variations should differ (optional)")
    sp_var.add_argument("--n", type=int, default=1, help="API calls (each returns 4 variants in a 2×2 grid)")
    sp_var.add_argument("--dry-run", action="store_true", help="build reference + prompt only, no API call")

    sp_fit = sub.add_parser("fit", help="auto-fit a chroma-keyed PNG into the RSC 48×32 inventory canvas")
    sp_fit.add_argument("input_path", help="path to a keyed cell PNG (e.g. cell_3_keyed.png)")
    sp_fit.add_argument("--lanczos", action="store_true", help="use LANCZOS downscale (default: NEAREST)")

    sp_reg = sub.add_parser("register", help="wire a fitted sprite into the game as a custom item")
    sp_reg.add_argument("--png", required=True, help="path to the fitted PNG (with sidecar .json next to it)")
    sp_reg.add_argument("--name", required=True, help="display name, e.g. 'Cursed Greatsword'")
    sp_reg.add_argument("--description", required=True, help="description shown in-game")
    sp_reg.add_argument("--base-price", type=int, default=100)
    sp_reg.add_argument("--members", action="store_true", help="members-only item")
    sp_reg.add_argument("--stackable", action="store_true")
    sp_reg.add_argument("--no-noteable", action="store_true", help="not bank-noteable (default: noteable)")
    sp_reg.add_argument("--like", type=int, default=None,
                        help="inherit wieldability + combat stats from this source item id (e.g. 81 for rune 2H sword)")
    sp_reg.add_argument("--commit", action="store_true", help="apply changes; default is dry-run")

    sp_rest = sub.add_parser("restore", help="undo a register by item_id")
    sp_rest.add_argument("item_id", type=int)
    sp_rest.add_argument("--commit", action="store_true", help="apply changes; default is dry-run")

    sp_rw = sub.add_parser("recolor-wielded", help="extract + recolor an existing item's wielded-sprite frames (preview only; no archive write)")
    sp_rw.add_argument("--source-item", type=int, required=True, help="source item id to recolor from (e.g. 81 for rune 2H sword)")
    sp_rw.add_argument("--color-map", required=True, help="comma-separated 'src:dst' hex pairs, e.g. 'd2d2d2:3c3c4b,a5a5a5:1c1c20,ffc932:6b21a8'")
    sp_rw.add_argument("--out", default=None, help="output directory (default: new_items/wielded_recolor_from_<id>)")

    sp_pw = sub.add_parser("pack-wielded", help="pack recolored wielded frames into archive + wire AnimationDef + update item appearanceID")
    sp_pw.add_argument("--target-item", type=int, required=True, help="item id to attach the new wielded sprite to (must be in ItemDefsCustom.json)")
    sp_pw.add_argument("--source-item", type=int, required=True, help="source item id whose AnimationDef shape we're imitating (e.g. 81)")
    sp_pw.add_argument("--frames-dir", required=True, help="directory containing frame_NN.png + sidecars (output of recolor-wielded)")
    sp_pw.add_argument("--commit", action="store_true", help="apply changes; default is dry-run")

    sp_vw = sub.add_parser("validate-wielded", help="verify a wearable's runtime mapping and every packed archive frame")
    sp_vw.add_argument("--animation", required=True, help="AnimationDef name, e.g. cowboyhat")
    sp_vw.add_argument("--item-id", required=True, type=int, help="server item id whose appearanceID must map to the AnimationDef")
    sp_vw.add_argument("--archive", default=None, help="Authentic_Sprites.orsc to inspect (default: the client archive)")
    sp_vw.add_argument("--expect-runtime-index", type=int, default=None, help="fail unless the zero-based AnimationDef index matches")
    sp_vw.add_argument("--expect-appearance-id", type=int, default=None, help="fail unless the one-based item appearanceID matches")
    sp_vw.add_argument("--expect-base", type=int, default=None, help="fail unless the simulated archive base matches")
    sp_vw.add_argument("--allow-bearded-ladies", action="store_true", help="simulate the alternate head-definition branch")
    sp_vw.add_argument("--layout-only", action="store_true", help="validate definitions and expected numbering before art is packed")

    for v in ("approve", "preview", "pack", "verify", "launch"):
        sub.add_parser(v, help=f"{v} (slice TBD)")

    args = p.parse_args(argv)

    if args.verb == "inspect":
        from .inspect_cmd import cmd_inspect
        return cmd_inspect(args.archive_index)
    if args.verb == "init":
        from .init_cmd import cmd_init
        return cmd_init(args.item_id, key=args.key)
    if args.verb == "generate":
        from .generate_cmd import cmd_generate
        return cmd_generate(args.item_key, n=args.n, dry_run=args.dry_run, confirm_cost=args.confirm_cost)
    if args.verb == "review":
        from .review_cmd import cmd_review
        return cmd_review(args.item_key)
    if args.verb == "new-icon":
        from .new_icon_cmd import cmd_new_icon
        return cmd_new_icon(args.description, n=args.n, dry_run=args.dry_run)
    if args.verb == "variations":
        from .variations_cmd import cmd_variations
        return cmd_variations(args.item_id, description=args.description, n=args.n, dry_run=args.dry_run)
    if args.verb == "fit":
        from .fit_cmd import cmd_fit
        return cmd_fit(args.input_path, lanczos=args.lanczos)
    if args.verb == "register":
        from .register_cmd import cmd_register
        return cmd_register(
            args.png, args.name, args.description,
            base_price=args.base_price,
            members=args.members,
            stackable=args.stackable,
            noteable=not args.no_noteable,
            like=args.like,
            commit=args.commit,
        )
    if args.verb == "restore":
        from .register_cmd import cmd_restore
        return cmd_restore(args.item_id, commit=args.commit)
    if args.verb == "recolor-wielded":
        from .recolor_wielded_cmd import cmd_recolor_wielded
        return cmd_recolor_wielded(args.source_item, args.color_map, out_dir=args.out)
    if args.verb == "pack-wielded":
        from .pack_wielded_cmd import cmd_pack_wielded
        return cmd_pack_wielded(args.target_item, args.source_item, args.frames_dir, commit=args.commit)
    if args.verb == "validate-wielded":
        from .paths import ARCHIVE_PATH
        from .validate_wielded_cmd import cmd_validate_wielded
        return cmd_validate_wielded(
            args.animation,
            args.item_id,
            archive=args.archive or ARCHIVE_PATH,
            expected_runtime_index=args.expect_runtime_index,
            expected_appearance_id=args.expect_appearance_id,
            expected_archive_base=args.expect_base,
            allow_bearded_ladies=args.allow_bearded_ladies,
            layout_only=args.layout_only,
        )
    print(f"verb '{args.verb}' not implemented in this slice")
    return 2


if __name__ == "__main__":
    sys.exit(main())
