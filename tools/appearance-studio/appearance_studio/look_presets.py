from __future__ import annotations

from pathlib import Path

import yaml

from .model import AppearanceEntry, Registry
from .paths import REPO_ROOT


CLIENT_TARGET = Path("Client_Base/src/com/openrsc/client/entityhandling/GeneratedLookPresets.java")
SERVER_TARGET = Path("server/src/com/openrsc/server/appearance/GeneratedLookPresets.java")
LEGACY_SELECTABLE_HEAD_IDS = (1, 4, 6, 7, 8)


def _managed_looks(registry: Registry) -> list[tuple[AppearanceEntry, str, bool, int]]:
    result: list[tuple[AppearanceEntry, str, bool, int]] = []
    for entry in sorted(registry.entries, key=lambda value: (value.appearance_id, value.key)):
        if entry.kind != "look" or entry.state not in {"active", "reserved"}:
            continue
        if entry.paperdoll_slot != 0:
            raise ValueError(f"Look {entry.key!r} must compile to paperdoll slot 0")
        if entry.manifest is None:
            raise ValueError(f"Look {entry.key!r} must have a manifest")
        try:
            manifest = yaml.safe_load(entry.manifest.read_text())
        except (OSError, yaml.YAMLError) as exc:
            raise ValueError(f"could not read Look manifest for {entry.key!r}: {exc}") from exc
        if not isinstance(manifest, dict) or manifest.get("key") != entry.key:
            raise ValueError(f"Look manifest key does not match registry entry {entry.key!r}")
        name = manifest.get("name")
        if not isinstance(name, str) or not name.strip():
            raise ValueError(f"Look {entry.key!r} must have a display name")
        compatibility = manifest.get("compatibility")
        fallback = compatibility.get("retro_fallback_appearance_id") if isinstance(compatibility, dict) else None
        if fallback not in LEGACY_SELECTABLE_HEAD_IDS:
            raise ValueError(
                f"Look {entry.key!r} compatibility.retro_fallback_appearance_id must be a legacy selectable head ID"
            )
        result.append((entry, name.strip(), entry.state == "active", fallback))
    return result


def _java_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def _render(registry: Registry, package: str) -> str:
    managed = _managed_looks(registry)
    legacy = ", ".join(str(value) for value in LEGACY_SELECTABLE_HEAD_IDS)
    entries = ", ".join(
        f'new Entry({entry.appearance_id}, "{_java_string(entry.key)}", '
        f'"{_java_string(name)}", {str(selectable).lower()}, {fallback})'
        for entry, name, selectable, fallback in managed
    )
    return f'''// Generated from content/appearance/registry.yaml and active/reserved Look manifests. Do not edit by hand.
package {package};

public final class GeneratedLookPresets {{
\tprivate static final int[] LEGACY_SELECTABLE_HEAD_IDS = new int[] {{{legacy}}};
\tprivate static final Entry[] MANAGED_LOOKS = new Entry[] {{{entries}}};

\tprivate GeneratedLookPresets() {{}}

\tpublic static boolean isSelectableHead(int appearanceId) {{
\t\tfor (int legacyId : LEGACY_SELECTABLE_HEAD_IDS) {{
\t\t\tif (legacyId == appearanceId) return true;
\t\t}}
\t\tEntry managed = findManaged(appearanceId);
\t\treturn managed != null && managed.selectable;
\t}}

\tpublic static Entry findManaged(int appearanceId) {{
\t\tfor (Entry entry : MANAGED_LOOKS) {{
\t\t\tif (entry.appearanceId == appearanceId) return entry;
\t\t}}
\t\treturn null;
\t}}

\tpublic static int[] selectableHeadIds() {{
\t\tint managedCount = 0;
\t\tfor (Entry entry : MANAGED_LOOKS) {{
\t\t\tif (entry.selectable) managedCount++;
\t\t}}
\t\tint[] result = new int[LEGACY_SELECTABLE_HEAD_IDS.length + managedCount];
\t\tSystem.arraycopy(LEGACY_SELECTABLE_HEAD_IDS, 0, result, 0, LEGACY_SELECTABLE_HEAD_IDS.length);
\t\tint offset = LEGACY_SELECTABLE_HEAD_IDS.length;
\t\tfor (Entry entry : MANAGED_LOOKS) {{
\t\t\tif (entry.selectable) result[offset++] = entry.appearanceId;
\t\t}}
\t\treturn result;
\t}}

\tpublic static Entry[] managedLooks() {{ return MANAGED_LOOKS.clone(); }}

\tpublic static final class Entry {{
\t\tpublic final int appearanceId;
\t\tpublic final String key;
\t\tpublic final String name;
\t\tpublic final boolean selectable;
\t\tpublic final int retroFallbackAppearanceId;

\t\tprivate Entry(int appearanceId, String key, String name, boolean selectable, int retroFallbackAppearanceId) {{
\t\t\tthis.appearanceId = appearanceId;
\t\t\tthis.key = key;
\t\t\tthis.name = name;
\t\t\tthis.selectable = selectable;
\t\t\tthis.retroFallbackAppearanceId = retroFallbackAppearanceId;
\t\t}}
\t}}
}}
'''


def rendered_preset_sources(registry: Registry) -> dict[Path, str]:
    return {
        CLIENT_TARGET: _render(registry, "com.openrsc.client.entityhandling"),
        SERVER_TARGET: _render(registry, "com.openrsc.server.appearance"),
    }


def check_preset_sources(registry: Registry, *, repo_root: Path = REPO_ROOT) -> list[str]:
    errors: list[str] = []
    for relative, expected in rendered_preset_sources(registry).items():
        path = repo_root / relative
        if not path.exists():
            errors.append(f"missing generated source: {relative}")
        elif path.read_text() != expected:
            errors.append(f"stale generated source: {relative}")
    return errors
