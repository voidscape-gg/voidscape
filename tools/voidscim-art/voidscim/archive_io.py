"""Thin wrapper around the sprite archive for reading single entries."""
from __future__ import annotations
import zipfile

from .paths import ARCHIVE_PATH


def read_entry(archive_index: int) -> bytes:
    with zipfile.ZipFile(ARCHIVE_PATH, "r") as zf:
        return zf.read(str(archive_index))
