from __future__ import annotations

import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from appearance_studio.look_presets import (
    LEGACY_SELECTABLE_HEAD_IDS,
    check_preset_sources,
    rendered_preset_sources,
)
from appearance_studio.paths import REGISTRY_PATH, REPO_ROOT
from appearance_studio.registry import load_registry


class LookPresetTest(unittest.TestCase):
    def registry(self):
        registry, findings = load_registry(REGISTRY_PATH)
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        return registry

    def test_checked_in_tables_record_reserved_look_but_keep_it_nonselectable(self):
        registry = self.registry()
        self.assertEqual((1, 4, 6, 7, 8), LEGACY_SELECTABLE_HEAD_IDS)
        self.assertFalse(check_preset_sources(registry))
        for source in rendered_preset_sources(registry).values():
            self.assertIn("new int[] {1, 4, 6, 7, 8}", source)
            self.assertIn(
                'new Entry(247, "future_mullet_mustache", "Mullet + mustache", false, 8)',
                source,
            )
            self.assertNotIn(
                'new Entry(247, "future_mullet_mustache", "Mullet + mustache", true, 8)',
                source,
            )
            self.assertIn("public static int[] selectableHeadIds()", source)
            self.assertIn("int[] result = new int[LEGACY_SELECTABLE_HEAD_IDS.length + managedCount]", source)

    def test_active_look_is_selectable_but_reserved_look_is_not(self):
        registry = self.registry()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            active_manifest = root / "active.yaml"
            reserved_manifest = root / "reserved.yaml"
            active_manifest.write_text(
                "key: active_look\nname: Active Look\ncompatibility:\n  retro_fallback_appearance_id: 4\n"
            )
            reserved_manifest.write_text(
                "key: reserved_look\nname: Reserved Look\ncompatibility:\n  retro_fallback_appearance_id: 8\n"
            )
            base = registry.entries[0]
            active = replace(base, key="active_look", kind="look", state="active",
                             appearance_id=200, paperdoll_slot=0, manifest=active_manifest)
            reserved = replace(base, key="reserved_look", kind="look", state="reserved",
                               appearance_id=201, paperdoll_slot=0, manifest=reserved_manifest)
            synthetic = replace(registry, entries=(active, reserved))
            for source in rendered_preset_sources(synthetic).values():
                self.assertIn('new Entry(200, "active_look", "Active Look", true, 4)', source)
                self.assertIn('new Entry(201, "reserved_look", "Reserved Look", false, 8)', source)
                self.assertIn("if (entry.selectable) result[offset++] = entry.appearanceId", source)

    def test_managed_look_requires_an_executable_retro_fallback(self):
        registry = self.registry()
        with tempfile.TemporaryDirectory() as temporary:
            manifest = Path(temporary) / "look.yaml"
            manifest.write_text("key: active_look\nname: Active Look\n")
            look = replace(registry.entries[0], key="active_look", kind="look", state="active",
                           appearance_id=200, paperdoll_slot=0, manifest=manifest)
            with self.assertRaisesRegex(ValueError, "retro_fallback_appearance_id"):
                rendered_preset_sources(replace(registry, entries=(look,)))

    def test_server_validation_uses_generated_table_and_unsigned_existing_byte(self):
        appearance = (REPO_ROOT / "server/src/com/openrsc/server/model/PlayerAppearance.java").read_text()
        updater = (REPO_ROOT / "server/src/com/openrsc/server/net/rsc/handlers/PlayerAppearanceUpdater.java").read_text()
        struct = (REPO_ROOT / "server/src/com/openrsc/server/net/rsc/struct/incoming/PlayerAppearanceStruct.java").read_text()
        parser = (REPO_ROOT / "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java").read_text()
        self.assertIn("GeneratedLookPresets.isSelectableHead(getHead())", appearance)
        self.assertNotIn("headSprites", appearance)
        self.assertIn("return (encodedAppearanceId & 0xFF) + 1", updater)
        self.assertIn("int headSprite = decodeUnsignedAppearanceId(headType)", updater)
        self.assertIn("public byte headType;", struct)
        self.assertIn("pl.headType = packet.readByte();", parser)

    def test_character_designer_cycles_stable_ids_and_keeps_the_existing_wire_byte(self):
        client = (REPO_ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
        self.assertIn("GeneratedLookPresets.selectableHeadIds()", client)
        self.assertIn("EntityHandler.getPlayerAppearanceDef(this.appearanceHeadType + 1)", client)
        self.assertIn("this.appearanceHeadType = appearanceId - 1", client)
        self.assertIn("bufferBits.putByte(this.appearanceHeadType)", client)

    def test_existing_persistence_multiplayer_avatar_and_workbench_fields_are_sufficient(self):
        database = (REPO_ROOT / "server/src/com/openrsc/server/database/GameDatabase.java").read_text()
        service = (REPO_ROOT / "server/src/com/openrsc/server/service/PlayerService.java").read_text()
        multiplayer = (REPO_ROOT / "server/src/com/openrsc/server/GameStateUpdater.java").read_text()
        avatar = (REPO_ROOT / "server/src/com/openrsc/server/avatargenerator/AvatarGenerator.java").read_text()
        retro = (REPO_ROOT / "server/src/com/openrsc/server/util/rsc/AppearanceRetroConverter.java").read_text()
        workbench = (REPO_ROOT / "PC_Client/src/orsc/WorkbenchServer.java").read_text()
        self.assertIn("playerData.headSprite = player.getSettings().getAppearance().getHead()", database)
        self.assertIn("playerData.headSprite", service)
        self.assertIn("setWornItems(player.getSettings().getAppearance().getSprites())", service)
        self.assertIn("updatesMain.add((short) i)", multiplayer)
        self.assertIn("GeneratedAppearanceRegistry.findAuthentic(appearanceId)", avatar)
        self.assertIn("GeneratedLookPresets.findManaged(modernId)", retro)
        self.assertIn("look.retroFallbackAppearanceId", retro)
        self.assertIn("GeneratedAppearanceRegistry.findAuthentic(appearanceId)", workbench)
        self.assertIn("player.layerAnimation[paperdollSlot] = appearanceId", workbench)


if __name__ == "__main__":
    unittest.main()
