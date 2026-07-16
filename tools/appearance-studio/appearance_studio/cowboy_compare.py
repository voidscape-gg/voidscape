from __future__ import annotations

import json
import zipfile
from pathlib import Path

from PIL import Image

from voidscim.sprite_io import encode

from .paths import CLIENT_ARCHIVE, SERVER_ARCHIVE


def compare_cowboy(frames_dir: Path, client_archive: Path = CLIENT_ARCHIVE,
                   server_archive: Path = SERVER_ARCHIVE) -> dict:
    changed: list[int] = []
    with zipfile.ZipFile(client_archive) as client, zipfile.ZipFile(server_archive) as server:
        for offset in range(18):
            png = frames_dir / f"frame_{offset:02d}.png"
            sidecar = json.loads((frames_dir / f"frame_{offset:02d}.png.json").read_text())
            data = encode(Image.open(png).convert("RGBA"), sidecar)
            index = 1890 + offset
            if data != client.read(str(index)) or data != server.read(str(index)):
                changed.append(offset)
    return {"schema": "voidscape-cowboy-comparison/v1", "changedFrames": changed,
            "changedSidecars": [], "changedArchiveEntries": [], "readOnly": True}
