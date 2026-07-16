import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
VOID_PATH_SOURCE = REPO_ROOT / "server/src/com/openrsc/server/content/VoidPath.java"


class OnboardingPersistenceContractTests(unittest.TestCase):
    def test_successful_path_choice_teleports_before_forced_save(self):
        source = VOID_PATH_SOURCE.read_text(encoding="utf-8")
        method_start = source.index("public static boolean openPathChoice")
        method_end = source.index("public static boolean grantStarterKit", method_start)
        method = source[method_start:method_end]

        self.assertEqual(2, method.count("player.save(false, true);"))
        successful_save = method.rindex("player.save(false, true);")
        spawn_teleport = method.index(
            "player.teleport(player.getConfig().RESPAWN_LOCATION_X, "
            "player.getConfig().RESPAWN_LOCATION_Y, true);"
        )

        self.assertLess(
            spawn_teleport,
            successful_save,
            "chosen path state must only be force-saved after the player reaches spawn",
        )


if __name__ == "__main__":
    unittest.main()
