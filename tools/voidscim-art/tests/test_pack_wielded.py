from __future__ import annotations

import unittest

from voidscim.pack_wielded_cmd import (
    _appearance_id_from_runtime_index,
    _runtime_index_from_appearance_id,
    parse_animations,
    simulate,
)
from voidscim.register_cmd import ENTITY_HANDLER


class PackWieldedAnimationLayoutTest(unittest.TestCase):
    def test_parser_selects_one_bearded_ladies_branch_and_keeps_custom_gating(self) -> None:
        source = """
private static void loadAnimationDefinitions() {
    animations.add(new AnimationDef("head1", "player", 1, 13, true, false, 0));
    if (Config.S_ALLOW_BEARDED_LADIES) {
        animations.add(new AnimationDef("head3", "player", 1, 13, true, false, 0));
    } else {
        animations.add(new AnimationDef("head3", "player", 1, 5, true, false, 0));
    }
    if (Config.S_WANT_CUSTOM_SPRITES) {
        animations.add(new AnimationDef("custom", "equipment", 0, 0, true, false, 0));
    }
    animations.add(new AnimationDef("always", "equipment", 0, 0, true, false, 0));
}
"""

        animations, _ = parse_animations(source, allow_bearded_ladies=False)

        self.assertEqual(["head1", "head3", "custom", "always"], [a.name for a in animations])
        self.assertIn(", 5, true, false, 0", animations[1].args_raw)
        self.assertFalse(animations[1].gated)
        self.assertTrue(animations[2].gated)
        self.assertEqual(81, simulate(animations))

        bearded_animations, _ = parse_animations(source, allow_bearded_ladies=True)
        self.assertEqual(["head1", "head3", "custom", "always"], [a.name for a in bearded_animations])
        self.assertIn(", 13, true, false, 0", bearded_animations[1].args_raw)
        self.assertTrue(bearded_animations[2].gated)

    def test_active_layout_keeps_cowboy_runtime_contract(self) -> None:
        animations, _ = parse_animations(
            ENTITY_HANDLER.read_text(),
            allow_bearded_ladies=False,
        )
        simulate(animations)
        visible = [animation for animation in animations if not animation.gated]
        cowboy_index = next(
            index for index, animation in enumerate(visible) if animation.name == "cowboyhat"
        )
        cowboy = visible[cowboy_index]

        self.assertEqual(244, cowboy_index)
        self.assertEqual(245, _appearance_id_from_runtime_index(cowboy_index))
        self.assertEqual(1890, cowboy.number)
        self.assertEqual(cowboy_index, _runtime_index_from_appearance_id(245))


if __name__ == "__main__":
    unittest.main()
