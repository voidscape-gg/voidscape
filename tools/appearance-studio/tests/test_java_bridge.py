from __future__ import annotations

import unittest
from pathlib import Path

from appearance_studio.java_bridge import check_sources, rendered_sources
from appearance_studio.paths import REGISTRY_PATH
from appearance_studio.registry import load_registry


class JavaBridgeTest(unittest.TestCase):
    def test_generated_sources_are_current_and_pin_cowboy_without_a_list_index(self):
        registry, findings = load_registry(REGISTRY_PATH)
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        self.assertFalse(check_sources(registry))
        for source in rendered_sources(registry).values():
            self.assertIn("COWBOY_HAT_APPEARANCE_ID = 245", source)
            self.assertIn("true, false, 1890", source)
            self.assertNotIn("animationIndex", source)
            self.assertNotIn("FUTURE_MULLET_MUSTACHE", source)
            self.assertNotIn("mulletmustache", source)

    def test_runtime_uses_managed_first_and_preserves_legacy_fallback(self):
        root = Path(__file__).resolve().parents[3]
        entity_handler = (root / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text()
        client = (root / "Client_Base/src/orsc/mudclient.java").read_text()
        avatar = (root / "server/src/com/openrsc/server/avatargenerator/AvatarGenerator.java").read_text()
        self.assertIn("GeneratedAppearanceRegistry.findAuthentic(appearanceId)", entity_handler)
        self.assertIn("Config.S_WANT_CUSTOM_SPRITES ? null : managed.definition", entity_handler)
        self.assertIn("getAnimationDef(appearanceId - 1)", entity_handler)
        self.assertIn("EntityHandler.getPlayerAppearanceDef(appearanceId)", client)
        self.assertIn("GeneratedAppearanceRegistry.authenticEntries()", client)
        self.assertIn("GeneratedAppearanceRegistry.findAuthentic(appearanceId)", avatar)
        self.assertIn("animations.get(animationIndex) : null", avatar)
        self.assertIn("Managed authentic appearances are unsupported with custom sprites", avatar)


if __name__ == "__main__":
    unittest.main()
