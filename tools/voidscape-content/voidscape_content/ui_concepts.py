from __future__ import annotations

import argparse
import base64
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any

from .paths import REPO_ROOT, UI_CONTENT_DIR


DEFAULT_WORKBENCH = "http://127.0.0.1:18787"
DEFAULT_MENU_DIR = UI_CONTENT_DIR / "menu-redesign"

PANEL_TITLES = {
    "hud": "Default HUD",
    "options-profile": "Options profile panel",
    "options-settings": "Advanced settings window",
    "friends": "Friends panel",
    "ignore": "Ignore panel",
    "magic": "Magic panel",
    "prayers": "Prayers panel",
    "skills": "Skills panel",
    "quests": "Quests panel",
    "loot": "Loot panel",
    "minimap": "Minimap panel",
    "inventory": "Inventory panel",
    "account": "Account manager drawer",
}

COMMON_ASSETS = [
    "outer panel frame",
    "panel title plaque",
    "sub-tab strip normal state",
    "sub-tab strip selected state",
    "section header strip",
    "list row background",
    "thin divider line",
    "scrollbar track",
    "scrollbar thumb",
]

PANEL_ASSETS = {
    "hud": [
        "chat box frame",
        "location plaque",
        "bottom chat tab normal state",
        "bottom chat tab selected state",
    ],
    "options-profile": ["settings value row", "logout row button"],
    "options-settings": ["modal backdrop frame", "category rail button", "checkbox/toggle glyph"],
    "friends": ["friend row", "remove action button"],
    "ignore": ["ignore row", "remove action button"],
    "magic": ["spell list row", "spell detail footer", "rune requirement chip"],
    "prayers": ["prayer list row", "prayer detail footer", "active prayer row"],
    "skills": ["skill row", "xp-lock icon button", "equipment status row"],
    "quests": ["quest list row", "quest detail row"],
    "loot": ["npc loot header row", "loot item cell", "loot scrollbar"],
    "minimap": ["minimap ornate frame", "world map button"],
    "inventory": ["inventory slot background", "inventory header tabs", "stack count badge"],
    "account": ["account drawer button", "account row", "add account row", "slide arrow cue"],
}


def _repo_path(value: str | Path) -> Path:
    path = Path(value)
    return path if path.is_absolute() else REPO_ROOT / path


def _repo_rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT.resolve()).as_posix()
    except ValueError:
        return str(path)


def _timestamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def _asset_slug(value: str) -> str:
    return value.lower().replace("/", "-").replace(" ", "-")


def _asset_index_order(panel: str, common_only: bool, no_common: bool) -> list[str]:
    if common_only:
        assets = list(COMMON_ASSETS)
    else:
        assets = [] if no_common else list(COMMON_ASSETS)
        assets.extend(PANEL_ASSETS.get(panel, []))
    return [_asset_slug(asset) for asset in assets]


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def _post_json(url: str, body: dict[str, str], timeout: int = 120) -> dict[str, Any]:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail[:500]}") from exc


def _capture_panel_key(capture: dict[str, Any]) -> str:
    reason = str(capture.get("reason", ""))
    prefix = "ui-panel-"
    if reason.startswith(prefix):
        return reason[len(prefix):]
    return reason or "panel"


def _load_captures(manifest: Path) -> list[dict[str, Any]]:
    data = _read_json(manifest)
    captures = data.get("captures")
    if not isinstance(captures, list):
        raise ValueError(f"manifest has no captures list: {_repo_rel(manifest)}")
    return captures


def _style_reference_text(style_refs: list[Path]) -> str:
    if not style_refs:
        return "No separate style reference was provided; infer style from the current Voidscape HUD screenshot."
    names = ", ".join(path.name for path in style_refs)
    return f"Additional style reference image(s): {names}. Match their dark fantasy Voidscape material language."


def _asset_geometry_text(asset: str) -> str:
    normalized = asset.lower()
    if "remove action button" in normalized:
        return "Geometry: one small compact remove/action button with a clear X/minus-style control cue; no list panel, no row, no embedded menu."
    if "bottom chat tab" in normalized:
        return "Geometry: one empty wide low chat-tab button sized for a short label; no speech bubbles, no text, no full chatbox."
    if "sub-tab strip" in normalized:
        return "Geometry: one empty horizontal tab-strip segment, not a chain of icons or multiple separate buttons; no text."
    if "inventory header tabs" in normalized or "equipment status row" in normalized:
        return "Geometry: one empty compact horizontal strip for header/status content; no top-menu icons, no item icons, no text."
    if "rune requirement chip" in normalized:
        return "Geometry: one small empty pill/chip background for a rune requirement; no rune symbol, no spell icon, no text."
    if "row" in normalized:
        return "Geometry: one long low empty horizontal row background; no complete panel, no icons, no text."
    if "button" in normalized or "glyph" in normalized or "cue" in normalized:
        return "Geometry: one isolated button/glyph only, centered, with no surrounding panel or row."
    if "frame" in normalized:
        return "Geometry: one complete empty frame or panel body, centered, with a clean usable interior."
    if "scrollbar" in normalized:
        return "Geometry: one isolated vertical scrollbar part, centered, straight, and not attached to a panel."
    return "Geometry: one isolated reusable UI part only, centered, with no surrounding screenshot content."


def _concept_prompt(panel: str, style_refs: list[Path]) -> str:
    title = PANEL_TITLES.get(panel, panel.replace("-", " ").title())
    return (
        "Use case: ui-mockup\n"
        f"Asset type: full in-game screenshot concept for the {title}\n"
        "Primary request: redesign only the visible Voidscape UI panel/HUD elements in the screenshot so they match the new cohesive dark-fantasy UI aesthetic.\n"
        "Input images: Image 1 is the current live game screenshot and is the geometry contract. "
        + _style_reference_text(style_refs) + "\n"
        "Preserve: exact game viewport size, visible world/camera, panel position, panel footprint, menu function, runtime text hierarchy, item icons, NPC/player/world content, and the readable density of RuneScape Classic.\n"
        "Change: panel chrome, frames, title plaques, sub-tab treatments, row backgrounds, dividers, button styling, scrollbars, and ornamental material details.\n"
        "Style/medium: crisp pixel-art-inspired UI concept, dark wrought iron, obsidian stone, deep violet cloth/enamel, pale gold trim, small purple gems, warm cream/gold text accents.\n"
        "Classic RSC spatial rule: keep panels compact and roomy. Inventory, friends/ignore, magic, and prayer should retain a clear glass/tinted content surface so the world remains visible behind them; stats, loot, options, and account may be more opaque but still must not feel heavy.\n"
        "Chrome rule: prefer clean narrow borders around the actual content over large nested fantasy frames. Do not add a big outer frame plus separate inner frame when a compact title, transparent content area, and tasteful border are enough.\n"
        "Typography/layout: do not crowd text against borders; keep header text smaller than plaques; align rows cleanly; leave practical padding around all labels and numbers.\n"
        "Constraints: no marketing layout, no modern OSRS/RS3 sidebar redesign, no oversized fantasy painting, no baked unreadable microtext, no new gameplay widgets, no overlapping UI. Output a full screenshot concept, not isolated assets.\n"
        "Avoid: pure black holes, neon gradients, one-note purple wash, soft blurry edges, dark-on-dark unreadable rows, green-screen background.\n"
    )


def _asset_prompt(panel: str, asset: str) -> str:
    title = PANEL_TITLES.get(panel, panel.replace("-", " ").title())
    return (
        "Use case: ui-mockup\n"
        f"Asset type: isolated game UI asset for the {title}\n"
        f"Primary request: create one reusable asset: {asset}.\n"
        "Input images: Image 1 is the approved UI concept. Additional images, if present, are style references.\n"
        "Scene/backdrop: perfectly flat solid #00FF00 chroma-key background only.\n"
        "Style/medium: crisp pixel-art-inspired dark fantasy UI asset, dark wrought iron/obsidian, pale gold trim, deep violet accents, small purple gems where appropriate.\n"
        + _asset_geometry_text(asset) + "\n"
        "Composition/framing: centered asset with generous padding, no cast shadow on the background, no text unless the asset name explicitly requires decorative text.\n"
        "Constraints: the background must be one uniform #00FF00 with no gradients, no shadows, no texture, no floor plane. Do not use green inside the asset. Do not use pure #000000 or #FF00FF in the asset. Hard readable silhouette at small in-game size.\n"
        "Avoid: full screenshots, panels with embedded gameplay, icon rows unless explicitly requested, soft glow spilling into the background, thin ornamental clutter, baked labels.\n"
    )


def _call_image_edit(
    images: list[Path],
    prompt: str,
    out: Path,
    model: str,
    size: str,
    quality: str,
    timeout: int,
) -> None:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY is not set in this process")
    try:
        import requests
    except ImportError as exc:
        raise RuntimeError("Python package 'requests' is required for live image API calls") from exc

    files = []
    image_field = "image[]" if len(images) > 1 else "image"
    for path in images:
        files.append((image_field, (path.name, path.read_bytes(), "image/png")))
    data = {
        "model": model,
        "prompt": prompt,
        "n": "1",
        "size": size,
        "quality": quality,
        "output_format": "png",
    }
    headers = {"Authorization": f"Bearer {api_key}"}
    backoff = 2.0
    for attempt in range(4):
        response = requests.post(
            "https://api.openai.com/v1/images/edits",
            headers=headers,
            data=data,
            files=files,
            timeout=timeout,
        )
        if response.status_code == 429 or response.status_code >= 500:
            print(f"warn: HTTP {response.status_code}; retrying in {backoff:.0f}s")
            time.sleep(backoff)
            backoff *= 2
            continue
        if response.status_code >= 400:
            raise RuntimeError(f"OpenAI image API HTTP {response.status_code}: {response.text[:1000]}")
        body = response.json()
        raw = base64.b64decode(body["data"][0]["b64_json"])
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(raw)
        return
    raise RuntimeError("OpenAI image API retries exhausted")


def cmd_capture_panels(args: argparse.Namespace) -> int:
    body = {}
    if args.panels:
        body["panels"] = args.panels
    result = _post_json(args.workbench.rstrip("/") + "/scenario/ui-panels", body, timeout=args.timeout)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    captures = result.get("captures") or []
    print(f"wrote manifest: {_repo_rel(args.out)}")
    print(f"captures: {len(captures)}")
    for capture in captures:
        print(f"  {_capture_panel_key(capture)} -> {capture.get('pngPath')}")
    return 0


def cmd_concepts(args: argparse.Namespace) -> int:
    captures = _load_captures(args.manifest)
    style_refs = [_repo_path(path) for path in (args.style_ref or [])]
    out_dir = args.out_dir or (DEFAULT_MENU_DIR / "concepts" / _timestamp())
    out_dir.mkdir(parents=True, exist_ok=True)

    index: list[dict[str, str]] = []
    for capture in captures:
        panel = _capture_panel_key(capture)
        if args.only and panel not in args.only:
            continue
        png_value = capture.get("pngPath")
        if not png_value:
            print(f"warn: capture without pngPath for {panel}")
            continue
        screenshot = _repo_path(str(png_value))
        panel_dir = out_dir / panel
        panel_dir.mkdir(parents=True, exist_ok=True)
        prompt = _concept_prompt(panel, style_refs)
        prompt_path = panel_dir / "concept-prompt.txt"
        refs_path = panel_dir / "references.json"
        prompt_path.write_text(prompt, encoding="utf-8")
        refs_path.write_text(json.dumps({
            "panel": panel,
            "screenshot": str(screenshot),
            "style_refs": [str(path) for path in style_refs],
        }, indent=2) + "\n", encoding="utf-8")

        concept_path = panel_dir / "concept.png"
        if args.call:
            _call_image_edit([screenshot] + style_refs, prompt, concept_path,
                             args.model, args.size, args.quality, args.timeout)
        index.append({
            "panel": panel,
            "prompt": str(prompt_path),
            "screenshot": str(screenshot),
            "concept": str(concept_path) if args.call else "",
        })
        print(f"{'generated' if args.call else 'prepared'}: {panel}")

    (out_dir / "index.json").write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    print(f"output: {_repo_rel(out_dir)}")
    if not args.call:
        print("dry-run/job-prep only; pass --call after OPENAI_API_KEY is visible")
    return 0


def cmd_asset_prompts(args: argparse.Namespace) -> int:
    panel = args.panel.strip().lower().replace("_", "-")
    concept = _repo_path(args.concept)
    style_refs = [_repo_path(path) for path in (args.style_ref or [])]
    out_dir = args.out_dir or (DEFAULT_MENU_DIR / "assets" / panel / _timestamp())
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.common_only:
        assets = list(COMMON_ASSETS)
    else:
        assets = [] if args.no_common else list(COMMON_ASSETS)
        assets.extend(PANEL_ASSETS.get(panel, []))
    if args.only_asset:
        wanted = {_asset_slug(asset) for asset in args.only_asset}
        assets = [asset for asset in assets if _asset_slug(asset) in wanted]
    index: list[dict[str, str]] = []
    for asset in assets:
        slug = _asset_slug(asset)
        prompt = _asset_prompt(panel, asset)
        asset_dir = out_dir / slug
        asset_dir.mkdir(parents=True, exist_ok=True)
        prompt_path = asset_dir / "asset-prompt.txt"
        prompt_path.write_text(prompt, encoding="utf-8")
        image_path = asset_dir / "asset-green.png"
        if args.call:
            _call_image_edit([concept] + style_refs, prompt, image_path,
                             args.model, args.size, args.quality, args.timeout)
        index.append({
            "asset": asset,
            "prompt": str(prompt_path),
            "image": str(image_path) if args.call else "",
        })
        print(f"{'generated' if args.call else 'prepared'}: {asset}")

    index_path = out_dir / "index.json"
    if args.only_asset and index_path.exists():
        existing = json.loads(index_path.read_text(encoding="utf-8-sig"))
        merged_by_slug = {_asset_slug(str(entry.get("asset", ""))): entry for entry in existing}
        for entry in index:
            merged_by_slug[_asset_slug(str(entry.get("asset", "")))] = entry
        ordered_slugs = _asset_index_order(panel, args.common_only, args.no_common)
        index = [merged_by_slug[slug] for slug in ordered_slugs if slug in merged_by_slug]
        index.extend(entry for slug, entry in merged_by_slug.items() if slug not in ordered_slugs)
    index_path.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    print(f"output: {_repo_rel(out_dir)}")
    if not args.call:
        print("dry-run/job-prep only; pass --call after OPENAI_API_KEY is visible")
    return 0


def add_ui_concept_subcommands(sub: argparse._SubParsersAction[argparse.ArgumentParser]) -> None:
    p_capture = sub.add_parser("capture-panels", help="capture every visible top-menu panel through the PC workbench")
    p_capture.add_argument("--workbench", default=DEFAULT_WORKBENCH)
    p_capture.add_argument("--panels", default="", help="comma-separated panel keys; default captures the visible core set")
    p_capture.add_argument("--out", type=Path, default=DEFAULT_MENU_DIR / "captures" / "latest.json")
    p_capture.add_argument("--timeout", type=int, default=180)
    p_capture.set_defaults(func=cmd_capture_panels)

    p_concepts = sub.add_parser("concepts", help="prepare or call ChatGPT Images API concept jobs for captured panels")
    p_concepts.add_argument("--manifest", type=Path, required=True, help="JSON written by ui capture-panels")
    p_concepts.add_argument("--out-dir", type=Path, default=None)
    p_concepts.add_argument("--style-ref", type=Path, action="append", default=[])
    p_concepts.add_argument("--only", action="append", default=[], help="panel key to include; repeatable")
    p_concepts.add_argument("--call", action="store_true", help="make live OpenAI Images API calls")
    p_concepts.add_argument("--model", default="gpt-image-2")
    p_concepts.add_argument("--size", default="1536x1024")
    p_concepts.add_argument("--quality", default="medium")
    p_concepts.add_argument("--timeout", type=int, default=420)
    p_concepts.set_defaults(func=cmd_concepts)

    p_assets = sub.add_parser("asset-prompts", help="prepare or call per-asset prompts from an approved concept")
    p_assets.add_argument("--panel", required=True)
    p_assets.add_argument("--concept", type=Path, required=True)
    p_assets.add_argument("--out-dir", type=Path, default=None)
    p_assets.add_argument("--style-ref", type=Path, action="append", default=[])
    p_assets.add_argument("--common-only", action="store_true", help="only prepare shared assets")
    p_assets.add_argument("--no-common", action="store_true", help="only prepare panel-specific assets")
    p_assets.add_argument("--only-asset", action="append", default=[], help="asset name or slug to include; repeatable")
    p_assets.add_argument("--call", action="store_true", help="make live OpenAI Images API calls")
    p_assets.add_argument("--model", default="gpt-image-2")
    p_assets.add_argument("--size", default="1024x1024")
    p_assets.add_argument("--quality", default="medium")
    p_assets.add_argument("--timeout", type=int, default=420)
    p_assets.set_defaults(func=cmd_asset_prompts)
