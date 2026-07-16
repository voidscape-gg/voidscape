from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from PIL import Image

from appearance_studio.paths import REPO_ROOT
from appearance_studio.studio_server import StudioWorkspace
from appearance_studio.template import load_template


class StudioWorkspaceTest(unittest.TestCase):
    def setUp(self):
        self.template = load_template(REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml")

    def test_v2_production_authoring_fails_closed_until_pose_mask_editor(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "disabled for geometry v2.*pose-mask editor R4"):
                StudioWorkspace(self.template, Path(temporary))

    def test_cowboy_comparison_refuses_writes(self):
        frames = REPO_ROOT / "content/custom/cowboy_hat/art/final/worn"
        workspace = StudioWorkspace(self.template, frames, read_only=True, cowboy_frames=frames)
        self.assertIsNotNone(workspace.image("north").getbbox())
        with self.assertRaises(PermissionError): workspace.save("north", workspace.image("north"))


if __name__ == "__main__": unittest.main()
