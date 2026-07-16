from __future__ import annotations

from pathlib import Path

from .model import AppearanceEntry, Registry
from .paths import REPO_ROOT


CLIENT_TARGET = Path("Client_Base/src/com/openrsc/client/entityhandling/GeneratedAppearanceRegistry.java")
SERVER_TARGET = Path("server/src/com/openrsc/server/appearance/GeneratedAppearanceRegistry.java")


def _entries(registry: Registry) -> list[AppearanceEntry]:
    return sorted(
        (entry for entry in registry.entries if entry.state in {"active", "adopted"}),
        key=lambda entry: (entry.appearance_id, entry.key),
    )


def _constant(key: str) -> str:
    return "".join(character.upper() if character.isalnum() else "_" for character in key)


def _boolean(value: bool) -> str:
    return "true" if value else "false"


def render_client(registry: Registry) -> str:
    entries = _entries(registry)
    constants = "\n".join(
        f"\tpublic static final int {_constant(entry.key)}_APPEARANCE_ID = {entry.appearance_id};"
        for entry in entries
    )
    definitions = "\n".join(
        f'\tprivate static final Entry {_constant(entry.key)} = new Entry({entry.appearance_id}, "{entry.key}", '
        f'new AnimationDef("{entry.animation_name}", "{entry.category}", {entry.char_colour}, {entry.blue_mask}, '
        f'{entry.gender_model}, {_boolean(entry.has_a)}, {_boolean(entry.has_f)}, {entry.sprite_base}), '
        f'{entry.frame_count}, {entry.paperdoll_slot});'
        for entry in entries
    )
    id_cases = "\n".join(
        f"\t\t\tcase {_constant(entry.key)}_APPEARANCE_ID: return {_constant(entry.key)};"
        for entry in entries
    )
    name_cases = "\n".join(
        f'\t\tif ("{entry.animation_name}".equalsIgnoreCase(animationName)) return {_constant(entry.key)};'
        for entry in entries
    )
    array = ", ".join(_constant(entry.key) for entry in entries)
    adopted_ranges = " || ".join(
        f"(base >= {entry.sprite_base} && base <= {entry.reserved_end})" for entry in entries
    ) or "false"
    return f'''// Generated from content/appearance/registry.yaml. Do not edit by hand.
package com.openrsc.client.entityhandling;

import com.openrsc.client.entityhandling.defs.extras.AnimationDef;

public final class GeneratedAppearanceRegistry {{
{constants}
\tpublic static final int MANAGED_SPRITE_BASE = {registry.managed_namespace["base"]};
\tpublic static final int MANAGED_SPRITE_END = {registry.managed_namespace["end"]};
\tpublic static final int REQUIRED_SPRITE_CAPACITY = {registry.managed_namespace["capacity_required"]};

{definitions}

\tprivate GeneratedAppearanceRegistry() {{}}

\tpublic static Entry findAuthentic(int appearanceId) {{
\t\tswitch (appearanceId) {{
{id_cases}
\t\t\tdefault: return null;
\t\t}}
\t}}

\tpublic static Entry findAuthenticByLegacyName(String animationName) {{
\t\tif (animationName == null) return null;
{name_cases}
\t\treturn null;
\t}}

\tpublic static boolean isManaged(int appearanceId) {{ return findAuthentic(appearanceId) != null; }}
\tpublic static boolean isReservedBase(int base) {{
\t\treturn (base >= MANAGED_SPRITE_BASE && base <= MANAGED_SPRITE_END) || {adopted_ranges};
\t}}
\tpublic static int requiredSpriteCapacity(int legacyMinimum) {{ return Math.max(legacyMinimum, REQUIRED_SPRITE_CAPACITY); }}
\tpublic static Entry[] authenticEntries() {{ return new Entry[] {{{array}}}; }}

\tpublic static final class Entry {{
\t\tpublic final int appearanceId;
\t\tpublic final String key;
\t\tpublic final AnimationDef definition;
\t\tpublic final int frameCount;
\t\tpublic final int paperdollSlot;

\t\tprivate Entry(int appearanceId, String key, AnimationDef definition, int frameCount, int paperdollSlot) {{
\t\t\tthis.appearanceId = appearanceId;
\t\t\tthis.key = key;
\t\t\tthis.definition = definition;
\t\t\tthis.frameCount = frameCount;
\t\t\tthis.paperdollSlot = paperdollSlot;
\t\t}}
\t}}
}}
'''


def render_server(registry: Registry) -> str:
    entries = _entries(registry)
    constants = "\n".join(
        f"\tpublic static final int {_constant(entry.key)}_APPEARANCE_ID = {entry.appearance_id};"
        for entry in entries
    )
    definitions = "\n".join(
        f'\tprivate static final Entry {_constant(entry.key)} = new Entry({entry.appearance_id}, "{entry.key}", '
        f'new AnimationDef("{entry.animation_name}", "{entry.category}", {entry.char_colour}, {entry.blue_mask}, '
        f'{entry.gender_model}, {_boolean(entry.has_a)}, {_boolean(entry.has_f)}, {entry.sprite_base}), '
        f'{entry.frame_count}, {entry.paperdoll_slot});'
        for entry in entries
    )
    cases = "\n".join(
        f"\t\t\tcase {_constant(entry.key)}_APPEARANCE_ID: return {_constant(entry.key)};"
        for entry in entries
    )
    array = ", ".join(_constant(entry.key) for entry in entries)
    return f'''// Generated from content/appearance/registry.yaml. Do not edit by hand.
package com.openrsc.server.appearance;

import com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef;

public final class GeneratedAppearanceRegistry {{
{constants}

{definitions}

\tprivate GeneratedAppearanceRegistry() {{}}

\tpublic static Entry findAuthentic(int appearanceId) {{
\t\tswitch (appearanceId) {{
{cases}
\t\t\tdefault: return null;
\t\t}}
\t}}

\tpublic static boolean isManaged(int appearanceId) {{ return findAuthentic(appearanceId) != null; }}
\tpublic static Entry[] authenticEntries() {{ return new Entry[] {{{array}}}; }}

\tpublic static final class Entry {{
\t\tpublic final int appearanceId;
\t\tpublic final String key;
\t\tpublic final AnimationDef definition;
\t\tpublic final int frameCount;
\t\tpublic final int paperdollSlot;

\t\tprivate Entry(int appearanceId, String key, AnimationDef definition, int frameCount, int paperdollSlot) {{
\t\t\tthis.appearanceId = appearanceId;
\t\t\tthis.key = key;
\t\t\tthis.definition = definition;
\t\t\tthis.frameCount = frameCount;
\t\t\tthis.paperdollSlot = paperdollSlot;
\t\t}}
\t}}
}}
'''


def rendered_sources(registry: Registry) -> dict[Path, str]:
    return {CLIENT_TARGET: render_client(registry), SERVER_TARGET: render_server(registry)}


def check_sources(registry: Registry, *, repo_root: Path = REPO_ROOT) -> list[str]:
    errors: list[str] = []
    for relative, expected in rendered_sources(registry).items():
        path = repo_root / relative
        if not path.exists():
            errors.append(f"missing generated source: {relative}")
        elif path.read_text() != expected:
            errors.append(f"stale generated source: {relative}")
    return errors
